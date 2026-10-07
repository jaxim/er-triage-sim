package edu.hospital.triage;

import edu.hospital.triage.db.DatabaseManager;
import edu.hospital.triage.engine.SimulationEngine;
import edu.hospital.triage.gui.AdminLoginDialog;
import edu.hospital.triage.gui.ERDashboard;

import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.List;

/**
 * Entry point for Blueprint V: Intelligent Healthcare Triage and Patient
 * Monitoring System.
 *
 * Run with the SQLite JDBC driver on the classpath:
 *   java -cp "out:lib/sqlite-jdbc.jar" edu.hospital.triage.Main
 * (use ; instead of : on Windows)
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            UIManager.put("Panel.background", new java.awt.Color(11, 17, 32));
            UIManager.put("Button.background", new java.awt.Color(30, 41, 59));
            UIManager.put("Button.foreground", new java.awt.Color(226, 232, 240));
            UIManager.put("TextField.background", new java.awt.Color(15, 23, 42));
            UIManager.put("TextField.foreground", new java.awt.Color(226, 232, 240));
            UIManager.put("TextField.border", BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new java.awt.Color(71, 85, 105)),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)));
            UIManager.put("Label.foreground", new java.awt.Color(226, 232, 240));
            UIManager.put("TitledBorder.titleColor", new java.awt.Color(148, 163, 184));
        } catch (Exception ignored) {
            // Fall back to the platform default if the Java LAF cannot be initialized.
        }

        DatabaseManager db = new DatabaseManager("er_triage.db");
        db.initializeDefaultAdmin();

        SimulationEngine engine = new SimulationEngine(db);
        engine.hireDefaultStaff();

        Runtime.getRuntime().addShutdownHook(new Thread(engine::shutdown));

        SwingUtilities.invokeLater(() -> {
            AdminLoginDialog loginDialog = new AdminLoginDialog(db);
            boolean authenticated = loginDialog.authenticate();
            if (!authenticated) {
                System.exit(0);
                return;
            }

            ERDashboard dashboard = new ERDashboard(engine);
            dashboard.setVisible(true);

            seedPatientIfNew(db, engine, "Amina Yusuf", 34, List.of("abdominal pain", "high fever"));
            seedPatientIfNew(db, engine, "John Peters", 67, List.of("chest pain"));
            seedPatientIfNew(db, engine, "Lila Chen", 25, List.of("broken bone"));
            seedPatientIfNew(db, engine, "Musa Adamu", 51, List.of("shortness of breath", "head injury"));
        });
    }

    private static void seedPatientIfNew(DatabaseManager db, SimulationEngine engine,
                                         String name, int age, List<String> symptoms) {
        List<DatabaseManager.PatientHistoryMatch> matches = db.findPreviousPatientHistory(name, age);
        String historyRecordId = matches.isEmpty() ? null : matches.get(0).getProfileId();
        engine.admitPatient(name, age, symptoms, historyRecordId);
    }
}
