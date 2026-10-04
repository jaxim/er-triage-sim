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
        doctors.clear();
        nurses.clear();
        for (String row : db.listStaffMembers()) {
            String[] fields = row.split(" \\| ");
            if (fields.length < 4) continue;
            String name = fields[1].trim();
            String role = fields[2].trim();
            if ("AttendingPhysician".equalsIgnoreCase(role) || "Doctor".equalsIgnoreCase(role)) {
                doctors.add(new Doctor(name, fields[3].trim()));
            } else if ("TriageNurse".equalsIgnoreCase(role) || "Nurse".equalsIgnoreCase(role)) {
                nurses.add(new Nurse(name));
            }
        }
        if (doctors.isEmpty() && nurses.isEmpty()) {
            hireDefaultStaff();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Admission + triage                                                  */
    /* ------------------------------------------------------------------ */

    public Patient admitPatient(String name, int age, List<String> symptoms) {
        Patient p = new Patient(name, age, symptoms);
        allPatients.add(p);

        p.transitionTo(PatientState.TRIAGE_ASSESSMENT);
        Nurse triageNurse = nurses.get(0);
        int severity = triageNurse.assessSeverity(p);

        db.insertAdmission(p);
        db.addAuditEvent("Admission recorded: " + p.getPatientId() + " - " + name);

        if (severity >= 9) {
            // Straight to critical care — never enters the standard queue.
            p.transitionTo(PatientState.CRITICAL_CARE);
            criticalCare.add(p);
            log("EMERGENCY ADMIT: " + p + " sent directly to critical care");
        } else {
            p.transitionTo(PatientState.WAITING);
            waitingRoom.enqueue(p);
            log("Admitted " + p + " to waiting room");
        }

        // Assign the lightweight biological-simulation thread.
        VitalSignSimulator sim = new VitalSignSimulator(p, this);
        Thread t = new Thread(sim, "vitals-" + p.getPatientId());
        t.setDaemon(true);
        simulators.put(p.getPatientId(), sim);
        simulatorThreads.put(p.getPatientId(), t);
        t.start();

        refreshCallback.run();
        return p;
    }

    /** Called by simulator threads when severity rises — dynamic re-sort. */
    public void onSeverityChanged(Patient p) {
        waitingRoom.resort();
        log("Deterioration: " + p.getPatientId() + " severity now " + p.getSeverityScore());
        refreshCallback.run();
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
    public void triggerEmergencyProtocol(Patient p, CardiacAnomalyException cause) {
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
    public String treatNextPatient() {
        Patient p = waitingRoom.peek();
        if (p == null) {
            return "Waiting room is empty.";
        }
        Doctor doctor = acquireAvailableDoctor(p);
        if (doctor == null) {
            return "No physician is available right now.";
        }
        waitingRoom.dequeue();
        p.transitionTo(PatientState.STABILIZING);
        String treatment = doctor.prescribeTreatment(p);
        db.recordTreatment(p, doctor, treatment);
        db.upsertPatientRecord(p);

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
        releaseDoctor(doctor);
        log(doctor.getName() + " -> " + p.getPatientId() + ": " + treatment + " | " + outcome);
        refreshCallback.run();
        return outcome;
    }

    /** GUI action: nurse walks the room and monitors everyone's vitals. */
    public String nurseRounds() {
        Nurse nurse = nurses.get(1 % nurses.size());
        StringBuilder sb = new StringBuilder();
        for (Patient p : waitingRoom.snapshot()) {
            sb.append(p.getPatientId()).append(": ").append(nurse.monitorVitals(p)).append('\n');
        }
        waitingRoom.resort();
        refreshCallback.run();
        return sb.length() == 0 ? "No patients to monitor." : sb.toString();
    }

    /** GUI action: ICU team treats a critical-care patient. */
    public String treatCriticalPatient() {
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
        db.upsertPatientRecord(p);
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
