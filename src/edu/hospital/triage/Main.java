package edu.hospital.triage;

import edu.hospital.triage.db.DatabaseManager;
import edu.hospital.triage.engine.SimulationEngine;
import edu.hospital.triage.gui.ERDashboard;

import javax.swing.SwingUtilities;
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
        DatabaseManager db = new DatabaseManager("er_triage.db");
        SimulationEngine engine = new SimulationEngine(db);
        engine.hireDefaultStaff();

        Runtime.getRuntime().addShutdownHook(new Thread(engine::shutdown));

        SwingUtilities.invokeLater(() -> {
            ERDashboard dashboard = new ERDashboard(engine);
            dashboard.setVisible(true);

            // Seed the shift with a few walk-ins so the board is alive.
            engine.admitPatient("Amina Yusuf", 34, List.of("abdominal pain", "high fever"));
            engine.admitPatient("John Peters", 67, List.of("chest pain"));
            engine.admitPatient("Lila Chen", 25, List.of("broken bone"));
            engine.admitPatient("Musa Adamu", 51, List.of("shortness of breath", "head injury"));
        });
    }
}
