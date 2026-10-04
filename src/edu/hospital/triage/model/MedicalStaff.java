package edu.hospital.triage.model;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base of the inheritance hierarchy. Extended by Doctor and Nurse.
 * Also models the UML actors TriageNurse and AttendingPhysician via roles.
 */
public abstract class MedicalStaff {

    private static final AtomicInteger ID_SEQUENCE = new AtomicInteger(100);

    private final String staffId;
    private final String name;
    private final String role;
    private boolean authenticated;
    private volatile boolean busy;

    protected MedicalStaff(String name, String role) {
        this.staffId = "S-" + ID_SEQUENCE.getAndIncrement();
        this.name = name;
        this.role = role;
        this.authenticated = false;
        this.busy = false;
    }

    /** Simulated credential check — gate for HIPAA-protected getters. */
    public final void login(String badgePin) {
        // In the simulation any 4-digit pin authenticates the badge.
        this.authenticated = badgePin != null && badgePin.matches("\\d{4}");
    }

    public final boolean isAuthenticated() {
        return authenticated;
    }

    /** Each concrete staff type performs its duty differently (polymorphism). */
    public abstract String attend(Patient patient);

    public String getStaffId() { return staffId; }
    public String getName() { return name; }
    public String getRole() { return role; }

    public boolean isBusy() { return busy; }
    public void setBusy(boolean busy) { this.busy = busy; }

    @Override
    public String toString() {
        return role + " " + name + " (" + staffId + (busy ? ", busy" : ", available") + ")";
    }
}
