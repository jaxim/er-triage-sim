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

                int currentSeverity = patient.getSeverityScore();

                int hrBias;
                int bpBias;
                double tempBias;
                int spo2Bias;
                int[] symptomProfile = buildSymptomProfile();
                hrBias = symptomProfile[0];
                bpBias = symptomProfile[1];
                tempBias = symptomProfile[2] / 10.0;
                spo2Bias = symptomProfile[3];

                // Low-acuity patients should mostly stabilize; only true deterioration should worsen them.
                if (currentSeverity <= 3) {
                    int hrDelta = random.nextInt(4) - 2 + Math.max(-4, Math.min(4, hrBias / 4));
                    int bpDelta = random.nextInt(3) - 1 + Math.max(-3, Math.min(3, bpBias / 5));
                    double tempDelta = ((random.nextDouble() - 0.5) * 0.12) + (tempBias / 20.0);
                    int spo2Delta = random.nextInt(2) - 1 + Math.max(-2, Math.min(2, spo2Bias / 6));
                    patient.getVitals().drift(hrDelta, bpDelta, tempDelta, spo2Delta);

                    if (random.nextDouble() < 0.18 && !deteriorated()) {
                        patient.setSeverityScore(Math.max(1, currentSeverity - 1));
                        engine.onSeverityChanged(patient);
                    }
                    continue;
                }

                // Moderate-to-severe patients drift according to symptom burden and illness progression.
                int bias = currentSeverity >= 6 ? -2 : 0;
                int hrDelta = bias + random.nextInt(8) - 3 + hrBias;
                int bpDelta = random.nextInt(6) - 2 + bpBias;
                double tempDelta = (random.nextDouble() - 0.5) * 0.24 + tempBias;
                int spo2Delta = (currentSeverity >= 7)
                        ? random.nextInt(4) - 2 + spo2Bias
                        : random.nextInt(3) - 1 + spo2Bias;

                patient.getVitals().drift(hrDelta, bpDelta, tempDelta, spo2Delta);

                // Deterioration check: only a subset of moderate-to-severe patients progress to ICU.
                if (deteriorated() && currentSeverity < Patient.MAX_SEVERITY && random.nextDouble() < (currentSeverity >= 7 ? 0.85 : 0.55)) {
                    patient.setSeverityScore(Math.min(Patient.MAX_SEVERITY, currentSeverity + 1));
                    engine.onSeverityChanged(patient);
                } else if (currentSeverity >= 7 && random.nextDouble() < 0.28) {
                    patient.setSeverityScore(Math.max(6, currentSeverity - 1));
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

    private int[] buildSymptomProfile() {
        int hrBias = 0;
        int bpBias = 0;
        int tempBias = 0;
        int spo2Bias = 0;

        for (String symptom : patient.getMedicalRecord().getSymptomChecklist()) {
            String s = symptom == null ? "" : symptom.toLowerCase();
            if (s.isBlank()) {
                continue;
            }

            if (s.contains("general complaint") || s.contains("mild pain") || s.contains("minor pain")
                    || s.contains("fatigue") || s.contains("checkup") || s.contains("routine")
                    || s.contains("follow-up") || s.contains("monitoring")) {
                continue;
            }

            if (s.contains("shortness of breath") || s.contains("difficulty breathing") || s.contains("breathless")
                    || s.contains("wheezing") || s.contains("asthma") || s.contains("respiratory")) {
                hrBias += 8;
                spo2Bias -= 6;
                bpBias -= 2;
            }
            if (s.contains("chest pain") || s.contains("palpitations") || s.contains("angina") || s.contains("cardiac")) {
                hrBias += 7;
                bpBias += 3;
                spo2Bias -= 3;
            }
            if (s.contains("fever") || s.contains("infection") || s.contains("sepsis") || s.contains("flu")) {
                hrBias += 5;
                tempBias += 10;
                spo2Bias -= 1;
            }
            if (s.contains("head injury") || s.contains("confusion") || s.contains("dizziness") || s.contains("stroke")
                    || s.contains("loss of consciousness") || s.contains("seizure")) {
                hrBias += 6;
                bpBias -= 3;
                spo2Bias -= 4;
            }
            if (s.contains("bleeding") || s.contains("hemorrhage") || s.contains("shock") || s.contains("hypotension")) {
                hrBias += 10;
                bpBias -= 8;
                spo2Bias -= 6;
                tempBias += 1;
            }
            if (s.contains("abdominal pain") || s.contains("vomiting") || s.contains("nausea") || s.contains("dehydration")) {
                hrBias += 4;
                tempBias += 4;
                bpBias -= 2;
            }
            if (s.contains("broken bone") || s.contains("fracture") || s.contains("trauma") || s.contains("injury")) {
                hrBias += 5;
                bpBias += 2;
            }
            if (s.contains("anxiety") || s.contains("panic") || s.contains("stress")) {
                hrBias += 5;
                bpBias += 4;
            }
            if (s.contains("cold") || s.contains("shivering") || s.contains("hypothermia")) {
                tempBias -= 8;
                hrBias -= 2;
            }
        }

        int severity = patient.getSeverityScore();
        hrBias += severity >= 8 ? 6 : severity >= 5 ? 4 : severity >= 3 ? 2 : 0;
        bpBias += severity >= 8 ? -3 : severity >= 5 ? -2 : 0;
        spo2Bias -= severity >= 7 ? 3 : severity >= 4 ? 1 : 0;
        tempBias += severity >= 6 ? 2 : 0;

        return new int[] {hrBias, bpBias, tempBias, spo2Bias};
    }

    private boolean deteriorated() {
        Patient.VitalSigns v = patient.getVitals();
        int severity = patient.getSeverityScore();

        if (severity <= 3) {
            return v.getHeartRate() > 170
                    || v.getOxygenSaturation() < 78
                    || v.getSystolicBp() < 70;
        }
        if (severity <= 6) {
            return v.getHeartRate() > 160
                    || v.getOxygenSaturation() < 82
                    || v.getSystolicBp() < 78;
        }

        return v.getHeartRate() > 150
                || v.getHeartRate() < 48
                || v.getOxygenSaturation() < 86
                || v.getSystolicBp() < 82;
    }
}
