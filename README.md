# Blueprint V — Intelligent Healthcare Triage and Patient Monitoring System

A Java Swing simulation of a hospital emergency department: patient influx, automatic
triage from simulated vital signs, severity-based priority queuing, and staff dispatch.

## Project structure

```
src/edu/hospital/triage/
├── Main.java                        entry point (seeds 4 walk-in patients)
├── model/
│   ├── Patient.java                 nested inner classes MedicalRecord (with nested Entry)
│   │                                and VitalSigns (live telemetry); Comparable by severity;
│   │                                private medical history behind an authenticated getter (HIPAA)
│   ├── PatientState.java            state chart: ADMITTED → TRIAGE_ASSESSMENT → WAITING →
│   │                                STABILIZING → DISCHARGED / CRITICAL_CARE
│   ├── MedicalStaff.java            abstract base (actors: TriageNurse, AttendingPhysician)
│   ├── Doctor.java                  overrides prescribeTreatment()
│   ├── Nurse.java                   monitorVitals() + assessSeverity() with labeled
│   │                                loops, continue/break, nested switch over symptom checklist
│   └── CardiacAnomalyException.java custom checked exception (HR < CARDIAC_ARREST_THRESHOLD = 40)
├── engine/
│   ├── TriageQueue.java             priority queue via sorted ArrayList + Iterator removal
│   ├── VitalSignSimulator.java      one lightweight background thread per admitted patient
│   └── SimulationEngine.java        admissions, dispatch, emergency protocol;
│                                    ReentrantLock prevents two patients claiming one doctor
├── db/
│   └── DatabaseManager.java         SQLite JDBC: INSERT admissions/treatments/discharges,
│                                    SELECT end-of-shift report (avg wait, triage efficiency)
└── gui/
    └── ERDashboard.java             JFrame zones: admissions desk (JTextFields), waiting room
                                     (dynamic JLabels), ICU monitor, event log + JButton actions
```

## How the key requirements map to code

- **Collections**: `TriageQueue` — sorted `ArrayList` re-sorted on every severity change,
  explicit `Iterator` used for safe mid-traversal removal.
- **Multithreading**: `VitalSignSimulator` runs per patient, drifting vitals every 1.5s.
  Deterioration raises severity → engine re-sorts the queue → GUI re-renders.
- **Synchronization**: `SimulationEngine.acquireAvailableDoctor()` holds a `ReentrantLock`
  so the busy-check and claim are atomic — no two patients get the same physician.
- **Exception handling**: `VitalSigns.drift()` throws `CardiacAnomalyException` when HR < 40;
  the simulator thread catches it and calls `triggerEmergencyProtocol()`, which pulls the
  patient out of the standard queue and dispatches a doctor immediately.
- **Access control / HIPAA**: `Patient.getMedicalHistory(MedicalStaff)` throws
  `SecurityException` unless the requester passed `login()` authentication.
- **GUI event handling**: `ActionListener`s on admit/treat/rounds/report buttons; a Swing
  `Timer` + `SwingUtilities.invokeLater` keep all rendering on the EDT.

## Build & run

Requires JDK 11+.

```
javac -d out $(find src -name '*.java')          # Linux/macOS
java  -cp "out:lib/*" edu.hospital.triage.Main
```

Windows (PowerShell):

```
javac -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
java  -cp "out;lib\*" edu.hospital.triage.Main
```

### SQLite driver and logging jars

The project expects the full library set in `lib/`, including SQLite JDBC and SLF4J.
Using `lib/*` ensures both are on the classpath. Without them the simulation still runs,
but persistence is disabled and the app logs a warning instead of saving/loading data.
The database file `er_triage.db` is created in the working directory.

## Using the dashboard

1. Enter name, age and comma-separated symptoms at the Admissions Desk → **Admit Patient**.
   Recognized high-acuity symptoms: `chest pain`, `cardiac arrest`, `unconscious`,
   `stroke symptoms`, `shortness of breath`, `severe bleeding`, `broken bone`,
   `high fever`, `abdominal pain`, `head injury`.
2. Watch the waiting room re-order itself as background threads change vitals.
3. **Doctor: Treat Next Patient** — dispatches a free physician to the top of the queue.
4. **Nurse: Monitor Vitals** — rounds through the waiting room, logging telemetry.
5. **ICU: Treat Critical Patient** — works the critical-care list.
6. **End-of-Shift Report** — SELECT-driven summary of admissions, wait times and treatments.
