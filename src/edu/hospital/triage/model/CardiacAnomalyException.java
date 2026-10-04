package edu.hospital.triage.model;

/**
 * Custom checked exception thrown by the multithreaded vital-sign simulator
 * when a patient's heart rate drops below Patient.CARDIAC_ARREST_THRESHOLD.
 *
 * Throwing this exception interrupts normal program flow: the patient
 * bypasses the standard triage queue and emergency protocols are triggered.
 */
public class CardiacAnomalyException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String patientId;
    private final int recordedHeartRate;

    public CardiacAnomalyException(String patientId, int recordedHeartRate) {
        super("CARDIAC ANOMALY: patient " + patientId
                + " heart rate " + recordedHeartRate + " bpm fell below the critical threshold of "
                + Patient.CARDIAC_ARREST_THRESHOLD + " bpm");
        this.patientId = patientId;
        this.recordedHeartRate = recordedHeartRate;
    }

    public String getPatientId() {
        return patientId;
    }

    public int getRecordedHeartRate() {
        return recordedHeartRate;
    }
}
