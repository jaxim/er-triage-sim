package edu.hospital.triage.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Robust Patient class containing deeply nested inner classes representing
 * complex medical records and continuous vital-sign telemetry.
 *
 * Access control is used heavily: the medical history is strictly private
 * and only reachable through an authenticated getter, simulating HIPAA
 * compliance.
 */
public class Patient implements Comparable<Patient> {

    /** Final, constant threshold: below this HR a CardiacAnomalyException fires. */
    public static final int CARDIAC_ARREST_THRESHOLD = 40;

    public static final int MAX_SEVERITY = 10;
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1000);

    private final String patientId;
    private final String historyRecordId;
    private final String name;
    private final int age;
    private final LocalDateTime admissionTime;

    /** Strictly private — HIPAA-style protection. */
    private final MedicalRecord medicalRecord;

    /** Live telemetry, mutated by the patient's background thread. */
    private final VitalSigns vitals;

    private volatile PatientState state;
    private volatile int severityScore; // 1 (minor) .. 10 (life-threatening)
    private volatile MedicalStaff assignedStaff;

    public Patient(String name, int age, List<String> symptoms) {
        this(name, age, symptoms, null);
    }

    public Patient(String name, int age, List<String> symptoms, String historyRecordId) {
        this(null, historyRecordId, name, age, symptoms, 1);
    }

    public Patient(String patientId, String historyRecordId, String name, int age,
                   List<String> symptoms, int severityScore) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Patient name is required.");
        }
        if (age < 0 || age > 130) {
            throw new IllegalArgumentException("Patient age must be between 0 and 130.");
        }
        if (symptoms == null) {
            throw new IllegalArgumentException("Patient symptoms must not be null.");
        }
        this.patientId = patientId == null || patientId.isBlank()
                ? "P-" + ID_COUNTER.getAndIncrement()
                : patientId;
        advanceIdCounter(this.patientId);
        this.historyRecordId = historyRecordId == null || historyRecordId.isBlank()
            ? this.patientId
            : historyRecordId;
        this.name = name;
        this.age = age;
        this.admissionTime = LocalDateTime.now();
        this.medicalRecord = new MedicalRecord(symptoms);
        this.vitals = new VitalSigns();
        this.state = PatientState.ADMITTED;
        this.severityScore = Math.max(1, Math.min(MAX_SEVERITY, severityScore));
    }

    private static void advanceIdCounter(String patientId) {
        if (!patientId.startsWith("P-")) return;
        try {
            int nextId = Integer.parseInt(patientId.substring(2)) + 1;
            ID_COUNTER.accumulateAndGet(nextId, Math::max);
        } catch (NumberFormatException ignored) {
            // Imported IDs that are not numeric do not affect generated IDs.
        }
    }

    /* =====================================================================
     * NESTED INNER CLASS 1: MedicalRecord (with its own nested Entry class)
     * ===================================================================== */

    /**
     * Complex medical record. Non-static inner class: each record is bound
     * to exactly one enclosing Patient instance.
     */
    public class MedicalRecord {

        /** Deeply nested inner class: one timestamped clinical entry. */
        public class Entry {
            private final LocalDateTime timestamp;
            private final String author;
            private final String note;

            private Entry(String author, String note) {
                this.timestamp = LocalDateTime.now();
                this.author = author;
                this.note = note;
            }

            @Override
            public String toString() {
                return "[" + timestamp + "] " + author + ": " + note;
            }
        }

        private final List<String> symptomChecklist;
        private final List<Entry> history = new ArrayList<>();
        private final List<String> prescriptions = new ArrayList<>();

        private MedicalRecord(List<String> symptoms) {
            this.symptomChecklist = new ArrayList<>(symptoms);
        }

        public List<String> getSymptomChecklist() {
            return Collections.unmodifiableList(symptomChecklist);
        }

        public synchronized void addEntry(String author, String note) {
            history.add(new Entry(author, note));
        }

        public synchronized void addPrescription(String drug) {
            prescriptions.add(drug);
        }

        public synchronized List<String> getPrescriptions() {
            return new ArrayList<>(prescriptions);
        }

        /** Only exposed via Patient.getMedicalHistory(...) authentication. */
        private synchronized List<Entry> readHistory() {
            return new ArrayList<>(history);
        }
    }

    /* =====================================================================
     * NESTED INNER CLASS 2: VitalSigns (continuous telemetry)
     * ===================================================================== */

    /**
     * Continuous vital-sign telemetry, updated by the patient's dedicated
     * background simulation thread. All access is synchronized because the
     * simulator thread writes while the GUI thread reads.
     */
    public class VitalSigns {
        private int heartRate = 60 + (int) (Math.random() * 40);        // bpm
        private int systolicBp = 105 + (int) (Math.random() * 30);      // mmHg
        private int diastolicBp = 65 + (int) (Math.random() * 20);      // mmHg
        private double temperature = 36.4 + Math.random() * 1.4;        // Celsius
        private int oxygenSaturation = 94 + (int) (Math.random() * 6);  // SpO2 %

        public synchronized int getHeartRate() { return heartRate; }
        public synchronized int getSystolicBp() { return systolicBp; }
        public synchronized int getDiastolicBp() { return diastolicBp; }
        public synchronized double getTemperature() { return temperature; }
        public synchronized int getOxygenSaturation() { return oxygenSaturation; }

        /**
         * Applies one simulation tick of biological drift. Throws the custom
         * exception if the heart rate collapses below the final threshold.
         */
        public synchronized void drift(int hrDelta, int bpDelta, double tempDelta, int spo2Delta)
                throws CardiacAnomalyException {
            heartRate = clamp(heartRate + hrDelta, 0, 220);
            systolicBp = clamp(systolicBp + bpDelta, 40, 260);
            diastolicBp = clamp(diastolicBp + bpDelta / 2, 20, 160);
            temperature = Math.max(30.0, Math.min(43.0, temperature + tempDelta));
            oxygenSaturation = clamp(oxygenSaturation + spo2Delta, 40, 100);

            if (heartRate < CARDIAC_ARREST_THRESHOLD) {
                throw new CardiacAnomalyException(patientId, heartRate);
            }
        }

        /** Treatment feedback loop: staff interventions improve telemetry. */
        public synchronized void stabilize() {
            heartRate = clamp(heartRate + (75 - heartRate) / 2, 45, 180);
            systolicBp = clamp(systolicBp + (120 - systolicBp) / 2, 80, 200);
            diastolicBp = clamp(diastolicBp + (80 - diastolicBp) / 2, 50, 120);
            temperature = temperature + (36.8 - temperature) / 2;
            oxygenSaturation = clamp(oxygenSaturation + 3, 40, 100);
        }

        private int clamp(int v, int lo, int hi) {
            return Math.max(lo, Math.min(hi, v));
        }

        @Override
        public synchronized String toString() {
            return String.format("HR %d bpm | BP %d/%d | %.1f°C | SpO2 %d%%",
                    heartRate, systolicBp, diastolicBp, temperature, oxygenSaturation);
        }
    }

    /* =====================================================================
     * HIPAA-style authenticated access
     * ===================================================================== */

    /**
     * Medical history is strictly private; this authenticated getter is the
     * ONLY access path. Unauthenticated callers receive a SecurityException.
     */
    public List<MedicalRecord.Entry> getMedicalHistory(MedicalStaff requester) {
        if (requester == null || !requester.isAuthenticated()) {
            throw new SecurityException(
                    "HIPAA VIOLATION BLOCKED: unauthenticated access to medical history of " + patientId);
        }
        return medicalRecord.readHistory();
    }

    /* =====================================================================
     * State machine
     * ===================================================================== */

    public synchronized void transitionTo(PatientState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException(
                    patientId + ": illegal transition " + state + " -> " + next);
        }
        state = next;
    }

    /** Restores a persisted state without replaying the full state-machine path. */
    public synchronized void setState(PatientState next) {
        if (next == null) {
            return;
        }
        this.state = next;
    }

    /** Emergency protocols may force CRITICAL_CARE from any live state. */
    public synchronized void forceCriticalCare() {
        if (state != PatientState.DISCHARGED && state != PatientState.DECEASED) {
            state = PatientState.CRITICAL_CARE;
        }
    }

    /* =====================================================================
     * Priority ordering — higher severity first, then earlier arrival
     * ===================================================================== */

    @Override
    public int compareTo(Patient other) {
        int bySeverity = Integer.compare(other.severityScore, this.severityScore);
        if (bySeverity != 0) {
            return bySeverity;
        }
        return this.admissionTime.compareTo(other.admissionTime);
    }

    /* ===================================================================== */

    public String getPatientId() { return patientId; }
    public String getHistoryRecordId() { return historyRecordId; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public LocalDateTime getAdmissionTime() { return admissionTime; }
    public MedicalRecord getMedicalRecord() { return medicalRecord; }
    public VitalSigns getVitals() { return vitals; }
    public PatientState getState() { return state; }

    public int getSeverityScore() { return severityScore; }

    public void setSeverityScore(int score) {
        this.severityScore = Math.max(1, Math.min(MAX_SEVERITY, score));
    }

    public MedicalStaff getAssignedStaff() { return assignedStaff; }
    public void setAssignedStaff(MedicalStaff staff) { this.assignedStaff = staff; }

    @Override
    public String toString() {
        return patientId + " " + name + " (sev " + severityScore + ", " + state + ")";
    }
}
