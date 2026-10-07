package edu.hospital.triage.tests;

import edu.hospital.triage.engine.SimulationEngine;
import edu.hospital.triage.engine.VitalSignSimulator;
import edu.hospital.triage.model.Patient;

import java.lang.reflect.Method;
import java.util.List;

public class VitalSignSimulatorRegressionTest {
    public static void main(String[] args) throws Exception {
        Patient lowAcuity = new Patient("Low Acuity", 31, List.of("anxiety", "general complaint"));
        lowAcuity.setSeverityScore(2);

        VitalSignSimulator simulator = new VitalSignSimulator(lowAcuity, new SimulationEngine(null));

        Method buildProfile = VitalSignSimulator.class.getDeclaredMethod("buildSymptomProfile");
        buildProfile.setAccessible(true);
        int[] profile = (int[]) buildProfile.invoke(simulator);

        if (Math.abs(profile[0]) > 6) {
            throw new AssertionError("Low-acuity symptom burden is too aggressive: HR bias=" + profile[0]);
        }

        if (profile[3] < -4) {
            throw new AssertionError("Low-acuity oxygen bias is too harsh: SpO2 bias=" + profile[3]);
        }

        Method deteriorated = VitalSignSimulator.class.getDeclaredMethod("deteriorated");
        deteriorated.setAccessible(true);
        if ((boolean) deteriorated.invoke(simulator)) {
            throw new AssertionError("Low-acuity patient was flagged as critical with minor symptoms.");
        }

        System.out.println("VitalSignSimulator regression check passed.");
    }
}
