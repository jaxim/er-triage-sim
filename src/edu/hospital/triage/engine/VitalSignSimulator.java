package edu.hospital.triage.engine;

import edu.hospital.triage.model.CardiacAnomalyException;
import edu.hospital.triage.model.Patient;
import edu.hospital.triage.model.PatientState;

import java.util.Random;

/**
 * Lightweight background thread assigned to every admitted patient.
 * It subtly alters the patient's vital signs over time, simulating
 * biological processes. If the condition deteriorates, it raises the
 * severity score so the triage queue re-sorts; if the heart rate collapses,
 * the drift() call throws CardiacAnomalyException, which interrupts normal
 * flow and triggers emergency protocols that bypass the standard queue.
 */
public class VitalSignSimulator implements Runnable {

    private static final long TICK_MILLIS = 1500;

    private final Patient patient;
    private final SimulationEngine engine;
    private final Random random = new Random();
    private volatile boolean running = true;

    public VitalSignSimulator(Patient patient, SimulationEngine engine) {
        this.patient = patient;
        this.engine = engine;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(TICK_MILLIS);

                PatientState s = patient.getState();
                if (s == PatientState.DISCHARGED || s == PatientState.DECEASED) {
                    break; // terminal state — thread ends naturally
                }

                // Sicker patients trend downward faster; treated ones drift gently.
                int bias = patient.getSeverityScore() >= 6 ? -3 : 0;
                int hrDelta = bias + random.nextInt(9) - 4;      // -4..+4 (+bias)
                int bpDelta = random.nextInt(7) - 3;
                double tempDelta = (random.nextDouble() - 0.5) * 0.2;
                int spo2Delta = (patient.getSeverityScore() >= 7)
                        ? random.nextInt(3) - 2
                        : random.nextInt(3) - 1;

                patient.getVitals().drift(hrDelta, bpDelta, tempDelta, spo2Delta);

                // Deterioration check: escalate severity, then ask the engine
                // to re-sort the waiting room on the fly.
                if (deteriorated()) {
                    patient.setSeverityScore(patient.getSeverityScore() + 1);
                    engine.onSeverityChanged(patient);
                }

            } catch (CardiacAnomalyException anomaly) {
                // Custom exception path: bypass standard queues entirely.
                engine.triggerEmergencyProtocol(patient, anomaly);
                break; // hand-off to critical care ends this simulator thread
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private boolean deteriorated() {
        Patient.VitalSigns v = patient.getVitals();
        return v.getHeartRate() > 135
                || v.getHeartRate() < 52
                || v.getOxygenSaturation() < 90
                || v.getSystolicBp() < 90;
    }
}
