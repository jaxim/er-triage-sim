package edu.hospital.triage.model;

/**
 * TriageNurse actor. Implements monitorVitals() and performs the initial
 * triage assessment by parsing the symptom checklist.
 */
public class Nurse extends MedicalStaff {

    public Nurse(String name) {
        super(name, "TriageNurse");
    }

    /**
     * Reads live telemetry and derives a severity contribution from it.
     */
    public String monitorVitals(Patient patient) {
        Patient.VitalSigns v = patient.getVitals();
        StringBuilder findings = new StringBuilder();

        if (v.getHeartRate() > 130 || v.getHeartRate() < 50) {
            findings.append("Abnormal heart rate. ");
        }
        if (v.getOxygenSaturation() < 90) {
            findings.append("Hypoxia detected. ");
        }
        if (v.getSystolicBp() > 180 || v.getSystolicBp() < 90) {
            findings.append("Blood pressure out of range. ");
        }
        if (v.getTemperature() > 38.5) {
            findings.append("High fever. ");
        }
        if (findings.length() == 0) {
            findings.append("Vitals within tolerance. ");
        }
        patient.getMedicalRecord().addEntry(getName(), "Vitals check: " + v + " — " + findings);
        return findings.toString().trim();
    }

    /**
     * Triage assessment: advanced control and iteration statements parse the
     * complex symptom checklist. Uses a labeled loop with break/continue and
     * a nested switch to accumulate a severity score.
     */
    public int assessSeverity(Patient patient) {
        int score = 1;
        Patient.VitalSigns v = patient.getVitals();

        checklist:
        for (String rawSymptom : patient.getMedicalRecord().getSymptomChecklist()) {
            if (rawSymptom == null || rawSymptom.isBlank()) {
                continue checklist; // skip malformed checklist rows
            }
            String symptom = rawSymptom.trim().toLowerCase();

            switch (symptom) {
                case "chest pain":
                case "cardiac arrest":
                    score = Patient.MAX_SEVERITY;
                    break checklist; // nothing outranks this — stop parsing
                case "shortness of breath":
                case "severe bleeding":
                    score += 4;
                    break;
                case "stroke symptoms":
                case "unconscious":
                    score += 5;
                    break;
                case "broken bone":
                case "high fever":
                    score += 3;
                    break;
                case "abdominal pain":
                case "head injury":
                    score += 2;
                    break;
                default:
                    score += 1; // minor complaint
            }
        }

        // Telemetry modifiers layered on top of the checklist parse.
        if (v.getOxygenSaturation() < 88) {
            score += 3;
        } else if (v.getOxygenSaturation() < 93) {
            score += 1;
        }
        if (v.getHeartRate() > 140) {
            score += 2;
        }

        int finalScore = Math.min(Patient.MAX_SEVERITY, score);
        patient.setSeverityScore(finalScore);
        patient.getMedicalRecord().addEntry(getName(), "Triage assessment complete, severity " + finalScore);
        return finalScore;
    }

    @Override
    public String attend(Patient patient) {
        String report = monitorVitals(patient);
        patient.getVitals().stabilize();
        patient.setSeverityScore(patient.getSeverityScore() - 1);
        return report;
    }
}
