package edu.hospital.triage.db;

import edu.hospital.triage.model.Doctor;
import edu.hospital.triage.model.Patient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * JDBC persistence layer backed by a local SQLite database (er_triage.db).
 *
 * INSERT queries record patient admissions and treatments; SELECT queries
 * generate end-of-shift reports on average wait times and triage efficiency.
 *
 * Requires sqlite-jdbc on the classpath (see README). If the driver is
 * missing, the manager degrades gracefully so the simulation still runs.
 */
public class DatabaseManager {

    private Connection connection;
    private boolean available;

    public DatabaseManager(String dbFilePath) {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFilePath);
            createSchema();
            available = true;
        } catch (ClassNotFoundException | SQLException e) {
            available = false;
            System.err.println("[DB] SQLite unavailable, running without persistence: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS admissions (" +
                " patient_id TEXT PRIMARY KEY," +
                " name TEXT NOT NULL," +
                " age INTEGER," +
                " severity INTEGER," +
                " admitted_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS treatments (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " patient_id TEXT," +
                " doctor TEXT," +
                " treatment TEXT," +
                " treated_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS discharges (" +
                " patient_id TEXT PRIMARY KEY," +
                " wait_minutes INTEGER," +
                " discharged_at TEXT DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    /** INSERT query recording a patient admission. */
    public synchronized void insertAdmission(Patient p) {
        if (!available) return;
        String sql = "INSERT INTO admissions (patient_id, name, age, severity) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, p.getPatientId());
            ps.setString(2, p.getName());
            ps.setInt(3, p.getAge());
            ps.setInt(4, p.getSeverityScore());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] insertAdmission failed: " + e.getMessage());
        }
    }

    public synchronized void recordTreatment(Patient p, Doctor d, String treatment) {
        if (!available) return;
        String sql = "INSERT INTO treatments (patient_id, doctor, treatment) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, p.getPatientId());
            ps.setString(2, d.getName());
            ps.setString(3, treatment);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] recordTreatment failed: " + e.getMessage());
        }
    }

    public synchronized void recordDischarge(Patient p, long waitMinutes) {
        if (!available) return;
        String sql = "INSERT OR REPLACE INTO discharges (patient_id, wait_minutes) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, p.getPatientId());
            ps.setLong(2, waitMinutes);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] recordDischarge failed: " + e.getMessage());
        }
    }

    /** SELECT queries generating the end-of-shift report. */
    public synchronized String endOfShiftReport() {
        if (!available) {
            return "Database unavailable — no persisted shift data.";
        }
        StringBuilder report = new StringBuilder("=== END OF SHIFT REPORT ===\n");
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS n, AVG(severity) AS avg_sev FROM admissions")) {
                if (rs.next()) {
                    report.append(String.format("Total admissions: %d (avg triage severity %.1f)%n",
                            rs.getInt("n"), rs.getDouble("avg_sev")));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS n, AVG(wait_minutes) AS avg_wait FROM discharges")) {
                if (rs.next()) {
                    report.append(String.format("Discharges: %d | Average wait: %.1f minutes%n",
                            rs.getInt("n"), rs.getDouble("avg_wait")));
                }
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT doctor, COUNT(*) AS treated FROM treatments GROUP BY doctor ORDER BY treated DESC")) {
                report.append("Treatments by physician:\n");
                while (rs.next()) {
                    report.append("  ").append(rs.getString("doctor"))
                          .append(": ").append(rs.getInt("treated")).append('\n');
                }
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT severity, COUNT(*) AS n FROM admissions GROUP BY severity ORDER BY severity DESC")) {
                report.append("Triage efficiency (admissions per severity band):\n");
                while (rs.next()) {
                    report.append("  Severity ").append(rs.getInt("severity"))
                          .append(": ").append(rs.getInt("n")).append(" patient(s)\n");
                }
            }
        } catch (SQLException e) {
            report.append("Report query failed: ").append(e.getMessage());
        }
        return report.toString();
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
