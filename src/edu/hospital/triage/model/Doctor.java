package edu.hospital.triage.model;

/**
 * AttendingPhysician actor. Overrides prescribeTreatment() and provides
 * the polymorphic attend() implementation.
 */
public class Doctor extends MedicalStaff {

    private final String specialty;

    public Doctor(String name, String specialty) {
        super(name, "AttendingPhysician");
        this.specialty = specialty;
    }

    /**
     * Overridden treatment logic. Uses an advanced switch on severity bands
     * to select an intervention, then feeds back into the patient's
     * biological simulation by stabilizing the vitals.
     */
    public String prescribeTreatment(Patient patient) {
        String treatment;
        int sev = patient.getSeverityScore();
        switch (sev) {
            case 10:
            case 9:
                treatment = "Emergency resuscitation protocol + epinephrine";
                break;
            case 8:
            case 7:
                treatment = "IV fluids, cardiac monitoring, stat labs";
                break;
            case 6:
            case 5:
                treatment = "Analgesics, oxygen therapy, observation";
                break;
            case 4:
            case 3:
                treatment = "Oral medication and scheduled re-assessment";
                break;
            default:
                treatment = "Rest, hydration, discharge instructions";
        }
        patient.getMedicalRecord().addPrescription(treatment);
        patient.getMedicalRecord().addEntry(getName(), "Prescribed: " + treatment);
        patient.getVitals().stabilize();
        patient.setSeverityScore(patient.getSeverityScore() - 2);
        return treatment;
    }

    @Override
    public String attend(Patient patient) {
        return prescribeTreatment(patient);
    }

    public String getSpecialty() {
        return specialty;
    }
}
