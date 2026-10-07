package edu.hospital.triage.model;

/**
 * State Chart Diagram states for the patient journey.
 *
 * ADMITTED -> TRIAGE_ASSESSMENT -> WAITING -> STABILIZING -> DISCHARGED
 *                                     \-> CRITICAL_CARE -> (DISCHARGED | DECEASED)
 */
public enum PatientState {
    ADMITTED("Admitted at desk"),
    TRIAGE_ASSESSMENT("Under triage assessment"),
    WAITING("In waiting room queue"),
    STABILIZING("Being stabilized by staff"),
    CRITICAL_CARE("In intensive care"),
    DISCHARGED("Discharged"),
    DECEASED("Deceased");

    private final String description;

    PatientState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** Valid transitions enforced by the state machine (advanced switch usage). */
    public boolean canTransitionTo(PatientState next) {
        switch (this) {
            case ADMITTED:
                return next == TRIAGE_ASSESSMENT;
            case TRIAGE_ASSESSMENT:
                return next == WAITING || next == CRITICAL_CARE;
            case WAITING:
                return next == STABILIZING || next == CRITICAL_CARE;
            case STABILIZING:
                return next == DISCHARGED || next == CRITICAL_CARE || next == WAITING;
            case CRITICAL_CARE:
                return next == STABILIZING || next == WAITING || next == DISCHARGED || next == DECEASED;
            case DISCHARGED:
                return next == WAITING;
            default:
                return false; // DECEASED is terminal
        }
    }
}
