package edu.hospital.triage.tests;

import edu.hospital.triage.db.DatabaseManager;
import edu.hospital.triage.engine.SimulationEngine;
import edu.hospital.triage.model.Patient;
import edu.hospital.triage.model.PatientState;

import java.nio.file.Files;
import java.nio.file.Path;

public class HistoricalPatientRequeueRegressionTest {
    public static void main(String[] args) throws Exception {
        Path databaseFile = Files.createTempFile("triage-requeue-test", ".db");
        DatabaseManager database = new DatabaseManager(databaseFile.toString());
        if (!database.isAvailable()) {
            throw new AssertionError("SQLite is required for this regression check.");
        }

        SimulationEngine engine = new SimulationEngine(database);
        try {
            database.upsertPatientRecord("P-7777", "Returning Patient", 45, 4, "DISCHARGED", "");
            database.upsertPatientRecord("P-7778", "Deceased Patient", 52, 10, "DECEASED", "");

            String result = engine.returnPatientToWaitingRoom("P-7777");
            Patient restoredPatient = engine.getAllPatients().stream()
                    .filter(patient -> patient.getPatientId().equals("P-7777"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Historical patient was not restored to memory."));

            if (!result.contains("returned to the waiting room")
                    || restoredPatient.getState() != PatientState.WAITING
                    || engine.getWaitingRoom().snapshot().size() != 1) {
                throw new AssertionError("Historical patient was not added to the live waiting room.");
            }

            if (!"P-7777".equals(restoredPatient.getHistoryRecordId())) {
                throw new AssertionError("Historical patient profile ID was not preserved.");
            }

            String deceasedResult = engine.returnPatientToWaitingRoom("P-7778");
            if (!deceasedResult.contains("Deceased patients cannot")
                    || engine.getWaitingRoom().snapshot().size() != 1) {
                throw new AssertionError("A deceased patient was incorrectly added to the waiting room.");
            }

            DatabaseManager.PatientRecord updatedRecord = database.getPatientRecord("P-7777");
            if (updatedRecord == null || !PatientState.WAITING.name().equals(updatedRecord.getState())) {
                throw new AssertionError("Historical patient state was not persisted as WAITING.");
            }
        } finally {
            engine.shutdown();
            Files.deleteIfExists(databaseFile);
        }

        System.out.println("Historical patient requeue regression check passed.");
    }
}