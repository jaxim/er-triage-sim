package edu.hospital.triage.engine;

import edu.hospital.triage.db.DatabaseManager;
import edu.hospital.triage.model.CardiacAnomalyException;
import edu.hospital.triage.model.Doctor;
import edu.hospital.triage.model.MedicalStaff;
import edu.hospital.triage.model.Nurse;
import edu.hospital.triage.model.Patient;
import edu.hospital.triage.model.PatientState;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Central coordinator: admissions, triage, dispatch, emergency protocols.
 *
 * Synchronization note: dispatchLock guarantees that two critically ill
 * patients can never be assigned the same available physician at the same
 * moment — the check-busy / set-busy pair executes atomically.
 */
public class SimulationEngine {

    private final TriageQueue waitingRoom = new TriageQueue();
    private final List<Patient> criticalCare = new CopyOnWriteArrayList<>();
    private final List<Patient> allPatients = new CopyOnWriteArrayList<>();

    private final List<Doctor> doctors = new ArrayList<>();
    private final List<Nurse> nurses = new ArrayList<>();

    private final Map<String, VitalSignSimulator> simulators = new HashMap<>();
    private final Map<String, Thread> simulatorThreads = new HashMap<>();
    private int nextNurseIndex = 0;

    /** Lock protecting doctor assignment (prevents logic collisions). */
    private final ReentrantLock dispatchLock = new ReentrantLock();

    private final DatabaseManager db;

    /** GUI hooks — the engine never imports Swing. */
    private volatile Consumer<String> eventLog = s -> { };
    private volatile Runnable refreshCallback = () -> { };

    public SimulationEngine(DatabaseManager db) {
        this.db = db;
    }

    /* ------------------------------------------------------------------ */

    public void hireDefaultStaff() {
        Doctor d1 = new Doctor("Dr. Ada Okafor", "Emergency Medicine");
        Doctor d2 = new Doctor("Dr. Ravi Menon", "Cardiology");
        Nurse n1 = new Nurse("Nurse Farida Bello");
        Nurse n2 = new Nurse("Nurse Tom Iwu");
        for (MedicalStaff s : List.of(d1, d2, n1, n2)) {
            s.login("1234"); // authenticate badges for HIPAA-gated getters
            db.upsertStaffRecord(s);
        }
        doctors.add(d1);
        doctors.add(d2);
        nurses.add(n1);
        nurses.add(n2);
    }

    public void addStaffFromAdmin(String name, String role, String specialty) {
        if (name == null || name.isBlank()) {
            return;
        }
        MedicalStaff staff;
        if ("Doctor".equalsIgnoreCase(role)) {
            staff = new Doctor(name, specialty == null || specialty.isBlank() ? "General Medicine" : specialty);
            doctors.add((Doctor) staff);
        } else {
            staff = new Nurse(name);
            nurses.add((Nurse) staff);
        }
        staff.login("1234");
        db.upsertStaffRecord(staff);
        db.addAuditEvent("New staff added: " + name + " (" + role + ")");
        refreshCallback.run();
    }

    public void reloadStaffFromDatabase() {
        dispatchLock.lock();
        try {
            doctors.clear();
            nurses.clear();
            for (String row : db.listStaffMembers()) {
                String[] fields = row.split("\\|", -1);
                if (fields.length < 4) continue;
                String name = fields[1].trim();
                String role = fields[2].trim();
                MedicalStaff staff;
                if ("AttendingPhysician".equalsIgnoreCase(role) || "Doctor".equalsIgnoreCase(role)) {
                    staff = new Doctor(name, fields[3].trim());
                    doctors.add((Doctor) staff);
                } else if ("TriageNurse".equalsIgnoreCase(role) || "Nurse".equalsIgnoreCase(role)) {
                    staff = new Nurse(name);
                    nurses.add((Nurse) staff);
                } else {
                    continue;
                }
                staff.login("1234");
            }
        } finally {
            dispatchLock.unlock();
        }
        if (doctors.isEmpty() && nurses.isEmpty()) {
            hireDefaultStaff();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Admission + triage                                                  */
    /* ------------------------------------------------------------------ */

    public synchronized Patient admitPatient(String name, int age, List<String> symptoms) {
        return admitPatient(name, age, symptoms, null);
    }

    public synchronized Patient admitPatient(String name, int age, List<String> symptoms, String historyRecordId) {
        Patient p = new Patient(name, age, symptoms, historyRecordId);
        allPatients.add(p);

        p.transitionTo(PatientState.TRIAGE_ASSESSMENT);
        if (nurses.isEmpty()) {
            allPatients.remove(p);
            throw new IllegalStateException("No triage nurse is available. Add a nurse before admitting patients.");
        }
        Nurse triageNurse = nurses.get(0);
        int severity = triageNurse.assessSeverity(p);

        db.insertAdmission(p);
        db.addHistoryEvent(p.getHistoryRecordId(), "ADMISSION", "Patient admitted to ER", "Triage");
        db.recordObservation(p, "triage", "Initial triage observation");
        recordRiskSnapshot(p, "Admission");
        db.addAuditEvent("Admission recorded: " + p.getPatientId() + " - " + name);

        if (severity >= 9 || autoEscalateIfNeeded(p, "Initial triage severity threshold")) {
            // Straight to critical care — never enters the standard queue.
            if (p.getState() != PatientState.CRITICAL_CARE) {
                p.transitionTo(PatientState.CRITICAL_CARE);
            }
            if (!criticalCare.contains(p)) {
                criticalCare.add(p);
            }
            log("EMERGENCY ADMIT: " + p + " sent directly to critical care");
        } else {
            p.transitionTo(PatientState.WAITING);
            waitingRoom.enqueue(p);
            log("Admitted " + p + " to waiting room");
        }
        db.upsertPatientRecord(p);

        startSimulator(p);

        refreshCallback.run();
        return p;
    }

    /** Called by simulator threads when severity rises — dynamic re-sort. */
    public synchronized void onSeverityChanged(Patient p) {
        if (p.getState() == PatientState.DISCHARGED || p.getState() == PatientState.DECEASED) {
            return;
        }
        waitingRoom.resort();
        recordRiskSnapshot(p, "Severity drift");
        db.addHistoryEvent(p.getHistoryRecordId(), "SEVERITY_CHANGE", "Severity escalated to " + p.getSeverityScore(), "System");
        autoEscalateIfNeeded(p, "Severity threshold crossed");
        db.upsertPatientRecord(p);
        log("Deterioration: " + p.getPatientId() + " severity now " + p.getSeverityScore());
        refreshCallback.run();
    }

    public synchronized String returnPatientToWaitingRoom(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return "Select a patient first.";
        }
        Patient patient = null;
        for (Patient candidate : allPatients) {
            if (candidate.getPatientId().equals(patientId)) {
                patient = candidate;
                break;
            }
        }
        if (patient == null) {
            DatabaseManager.PatientRecord previousRecord = db.getPatientRecord(patientId);
            if (previousRecord == null) {
                return "Patient record could not be loaded from the database.";
            }
            if (PatientState.DECEASED.name().equalsIgnoreCase(previousRecord.getState())) {
                return "Deceased patients cannot be returned to the waiting room.";
            }
            patient = new Patient(previousRecord.getPatientId(), previousRecord.getProfileId(),
                    previousRecord.getName(), previousRecord.getAge(), List.of(), previousRecord.getSeverity());
            patient.transitionTo(PatientState.TRIAGE_ASSESSMENT);
            allPatients.add(patient);
        }
        if (patient.getState() == PatientState.WAITING) {
            return patient.getPatientId() + " is already in the waiting room.";
        }
        if (patient.getState() != PatientState.DISCHARGED
                && patient.getState() != PatientState.CRITICAL_CARE
                && patient.getState() != PatientState.TRIAGE_ASSESSMENT) {
            return "Only previous-session, discharged, or critical-care patients can be returned to the waiting room.";
        }

        criticalCare.remove(patient);
        patient.setAssignedStaff(null);
        patient.transitionTo(PatientState.WAITING);
        waitingRoom.enqueue(patient);
        startSimulator(patient);
        db.upsertPatientRecord(patient);
        db.addHistoryEvent(patient.getHistoryRecordId(), "RETURN_TO_WAITING",
                "Patient returned to the waiting room by an administrator", "Admin");
        db.addAuditEvent("Patient returned to waiting room: " + patient.getPatientId());
        recordRiskSnapshot(patient, "Returned to waiting room");
        log("ADMIN: " + patient.getPatientId() + " returned to the waiting room");
        refreshCallback.run();
        return patient.getPatientId() + " returned to the waiting room.";
    }

    private void startSimulator(Patient patient) {
        Thread existingThread = simulatorThreads.get(patient.getPatientId());
        if (existingThread != null && existingThread.isAlive()) {
            return;
        }

        VitalSignSimulator simulator = new VitalSignSimulator(patient, this);
        Thread thread = new Thread(simulator, "vitals-" + patient.getPatientId());
        thread.setDaemon(true);
        simulators.put(patient.getPatientId(), simulator);
        simulatorThreads.put(patient.getPatientId(), thread);
        thread.start();
    }

    /* ------------------------------------------------------------------ */
    /* Emergency protocol (CardiacAnomalyException path)                   */
    /* ------------------------------------------------------------------ */

    /**
     * Invoked from a patient's background thread when a CardiacAnomalyException
     * is thrown. The patient bypasses the standard queue: they are yanked out
     * of the waiting room, forced into CRITICAL_CARE, and a physician is
     * dispatched immediately.
     */
    public synchronized void triggerEmergencyProtocol(Patient p, CardiacAnomalyException cause) {
        if (p.getState() == PatientState.DISCHARGED || p.getState() == PatientState.DECEASED) {
            return;
        }
        log("!!! " + cause.getMessage());
        waitingRoom.remove(p);          // bypass standard queue
        p.forceCriticalCare();
        if (!criticalCare.contains(p)) {
            criticalCare.add(p);
        }
        p.setSeverityScore(Patient.MAX_SEVERITY);

        Doctor doctor = acquireAvailableDoctor(p);
        if (doctor != null) {
            String treatment = doctor.prescribeTreatment(p);
            log("CODE BLUE response by " + doctor.getName() + ": " + treatment);
            db.recordTreatment(p, doctor, treatment);
            db.upsertPatientRecord(p);
            releaseDoctor(doctor);
        } else {
            log("CODE BLUE: no physician free for " + p.getPatientId() + " — queued for ICU team");
        }
        refreshCallback.run();
    }

    /* ------------------------------------------------------------------ */
    /* Dispatch — synchronized doctor assignment                            */
    /* ------------------------------------------------------------------ */

    /**
     * Atomically finds and claims a free doctor. The lock ensures two
     * threads cannot claim the same physician simultaneously.
     */
    public Doctor acquireAvailableDoctor(Patient forPatient) {
        dispatchLock.lock();
        try {
            for (Doctor d : doctors) {
                if (!d.isBusy()) {
                    d.setBusy(true);
                    forPatient.setAssignedStaff(d);
                    return d;
                }
            }
            return null;
        } finally {
            dispatchLock.unlock();
        }
    }

    public void releaseDoctor(Doctor d) {
        dispatchLock.lock();
        try {
            d.setBusy(false);
        } finally {
            dispatchLock.unlock();
        }
    }

    /** GUI action: treat the highest-priority patient in the waiting room. */
    public synchronized String treatNextPatient() {
        Patient p = waitingRoom.peek();
        if (p == null) {
            return "Waiting room is empty.";
        }
        if (p.getSeverityScore() >= 8 || computeRiskScore(p) >= 75) {
            autoEscalateIfNeeded(p, "Treatment dispatch threshold crossed");
            return p.getPatientId() + " escalated to critical care before treatment.";
        }
        Doctor doctor = acquireAvailableDoctor(p);
        if (doctor == null) {
            return "No physician is available right now.";
        }
        if (!waitingRoom.remove(p)) {
            releaseDoctor(doctor);
            return "Patient status changed before treatment could begin. Refresh and try again.";
        }
        p.transitionTo(PatientState.STABILIZING);
        String treatment = doctor.prescribeTreatment(p);
        db.recordTreatment(p, doctor, treatment);

        String outcome;
        if (p.getSeverityScore() <= 2) {
            p.transitionTo(PatientState.DISCHARGED);
            stopSimulator(p);
            db.recordDischarge(p, waitMinutes(p));
            outcome = p.getName() + " treated and DISCHARGED";
        } else if (p.getSeverityScore() >= 8) {
            p.transitionTo(PatientState.CRITICAL_CARE);
            criticalCare.add(p);
            outcome = p.getName() + " escalated to CRITICAL CARE";
        } else {
            p.transitionTo(PatientState.WAITING);
            waitingRoom.enqueue(p);
            outcome = p.getName() + " stabilized, returned to waiting room";
        }
        db.upsertPatientRecord(p);
        releaseDoctor(doctor);
        log(doctor.getName() + " -> " + p.getPatientId() + ": " + treatment + " | " + outcome);
        refreshCallback.run();
        return outcome;
    }

    public int computeRiskScore(Patient p) {
        if (p == null) return 0;
        int score = p.getSeverityScore() * 8;
        score += p.getMedicalRecord().getSymptomChecklist().size() * 6;
        if (p.getVitals().getOxygenSaturation() < 88) score += 25;
        else if (p.getVitals().getOxygenSaturation() < 93) score += 10;
        if (p.getVitals().getHeartRate() > 130 || p.getVitals().getHeartRate() < 52) score += 20;
        if (p.getVitals().getSystolicBp() < 90 || p.getVitals().getSystolicBp() > 180) score += 15;
        if (p.getVitals().getTemperature() > 38.5) score += 10;
        score = Math.min(100, Math.max(0, score));
        return score;
    }

    public String determineRiskBand(int riskScore) {
        if (riskScore >= 80) return "CRITICAL";
        if (riskScore >= 60) return "HIGH";
        if (riskScore >= 35) return "MODERATE";
        return "LOW";
    }

    public void recordRiskSnapshot(Patient p, String reason) {
        int riskScore = computeRiskScore(p);
        String riskBand = determineRiskBand(riskScore);
        db.recordRiskSnapshot(p, riskScore, riskBand);
        db.recordSeverityTrend(p, reason);
        db.addHistoryEvent(p.getHistoryRecordId(), "RISK_UPDATE", "Risk score " + riskScore + " (" + riskBand + ")", "System");
    }

    public synchronized boolean autoEscalateIfNeeded(Patient p, String reason) {
        if (p == null || p.getState() == PatientState.DISCHARGED || p.getState() == PatientState.DECEASED) {
            return false;
        }
        int riskScore = computeRiskScore(p);
        boolean shouldEscalate = riskScore >= 75 || p.getSeverityScore() >= 8;
        if (shouldEscalate) {
            if (p.getState() == PatientState.CRITICAL_CARE || criticalCare.contains(p)) {
                return true;
            }
            if (!criticalCare.contains(p)) {
                criticalCare.add(p);
            }
            p.forceCriticalCare();
            db.upsertPatientRecord(p);
            if (waitingRoom.remove(p)) {
                log("AUTOMATIC ICU ESCALATION: " + p.getPatientId() + " due to " + reason);
            }
            db.recordAlert(p, "ICU_ESCALATION", reason + " — risk score " + riskScore, "HIGH");
            db.addHistoryEvent(p.getHistoryRecordId(), "ICU_ESCALATION", reason + " — risk score " + riskScore, "System");
            refreshCallback.run();
            return true;
        }
        return false;
    }

    /** GUI action: nurse walks the room and monitors everyone's vitals. */
    public synchronized String nurseRounds() {
        if (nurses.isEmpty()) {
            return "No nurse is available for rounds.";
        }
        Nurse nurse = nurses.get(nextNurseIndex % nurses.size());
        nextNurseIndex = (nextNurseIndex + 1) % nurses.size();
        StringBuilder sb = new StringBuilder();
        for (Patient p : waitingRoom.snapshot()) {
            String note = nurse.monitorVitals(p);
            db.recordObservation(p, nurse.getName(), note);
            recordRiskSnapshot(p, "nurse round");
            autoEscalateIfNeeded(p, "Nurse round abnormal trend");
            sb.append(p.getPatientId()).append(": ").append(note).append('\n');
        }
        waitingRoom.resort();
        refreshCallback.run();
        return sb.length() == 0 ? "No patients to monitor." : sb.toString();
    }

    /** GUI action: ICU team treats a critical-care patient. */
    public synchronized String treatCriticalPatient() {
        if (criticalCare.isEmpty()) {
            return "No patients in critical care.";
        }
        Patient p = criticalCare.get(0);
        Doctor doctor = acquireAvailableDoctor(p);
        if (doctor == null) {
            return "No physician available for ICU.";
        }
        String treatment = doctor.prescribeTreatment(p);
        db.recordTreatment(p, doctor, treatment);
        String outcome;
        if (p.getSeverityScore() <= 3) {
            criticalCare.remove(p);
            p.transitionTo(PatientState.DISCHARGED);
            stopSimulator(p);
            db.recordDischarge(p, waitMinutes(p));
            outcome = p.getName() + " recovered from ICU and DISCHARGED";
        } else {
            outcome = p.getName() + " remains in ICU (sev " + p.getSeverityScore() + ")";
        }
        db.upsertPatientRecord(p);
        releaseDoctor(doctor);
        log("ICU: " + outcome);
        refreshCallback.run();
        return outcome;
    }

    private void stopSimulator(Patient p) {
        VitalSignSimulator sim = simulators.remove(p.getPatientId());
        if (sim != null) {
            sim.stop();
        }
        Thread t = simulatorThreads.remove(p.getPatientId());
        if (t != null) {
            t.interrupt();
        }
    }

    private long waitMinutes(Patient p) {
        return Math.max(0, Duration.between(p.getAdmissionTime(), LocalDateTime.now()).toMinutes());
    }

    /* ------------------------------------------------------------------ */

    public TriageQueue getWaitingRoom() { return waitingRoom; }
    public List<Patient> getCriticalCare() { return new ArrayList<>(criticalCare); }
    public List<Patient> getAllPatients() { return new ArrayList<>(allPatients); }
    public List<Doctor> getDoctors() { return new ArrayList<>(doctors); }
    public List<Nurse> getNurses() { return new ArrayList<>(nurses); }
    public DatabaseManager getDb() { return db; }

    public void setEventLog(Consumer<String> sink) { this.eventLog = sink; }
    public void setRefreshCallback(Runnable r) { this.refreshCallback = r; }

    private void log(String msg) {
        eventLog.accept(msg);
    }

    public void shutdown() {
        for (VitalSignSimulator sim : simulators.values()) {
            sim.stop();
        }
        for (Thread t : simulatorThreads.values()) {
            t.interrupt();
        }
        db.close();
    }
}
