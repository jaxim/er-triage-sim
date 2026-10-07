package edu.hospital.triage.db;

import edu.hospital.triage.model.Doctor;
import edu.hospital.triage.model.MedicalStaff;
import edu.hospital.triage.model.Patient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public static final class PatientRecord {
        private final String patientId;
        private final String profileId;
        private final String name;
        private final int age;
        private final int severity;
        private final String state;

        private PatientRecord(String patientId, String profileId, String name, int age, int severity, String state) {
            this.patientId = patientId;
            this.profileId = profileId;
            this.name = name;
            this.age = age;
            this.severity = severity;
            this.state = state;
        }

        public String getPatientId() { return patientId; }
        public String getProfileId() { return profileId; }
        public String getName() { return name; }
        public int getAge() { return age; }
        public int getSeverity() { return severity; }
        public String getState() { return state; }
    }

    public static final class PatientHistoryMatch {
        private final String profileId;
        private final String name;
        private final int age;

        private PatientHistoryMatch(String profileId, String name, int age) {
            this.profileId = profileId;
            this.name = name;
            this.age = age;
        }

        public String getProfileId() { return profileId; }

        @Override
        public String toString() {
            return name + " (age " + age + ") | History ID " + profileId;
        }
    }

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final int SALT_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();
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

    public synchronized List<PatientHistoryMatch> findPreviousPatientHistory(String name, int age) {
        List<PatientHistoryMatch> matches = new ArrayList<>();
        if (!available || name == null || name.isBlank()) {
            return matches;
        }
        String sql = "SELECT profile_id, name, age FROM patient_profiles "
                + "WHERE normalized_name = ? AND age = ? ORDER BY created_at DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizePatientName(name));
            statement.setInt(2, age);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    matches.add(new PatientHistoryMatch(result.getString("profile_id"),
                            result.getString("name"), result.getInt("age")));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] findPreviousPatientHistory failed: " + e.getMessage());
        }
        return matches;
    }

    private String normalizePatientName(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public boolean isAvailable() {
        return available;
    }

    public synchronized void initializeDefaultAdmin() {
        if (!available) {
            return;
        }
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT 1 FROM admin_users LIMIT 1")) {
            if (result.next()) {
                return;
            }
        } catch (SQLException e) {
            System.err.println("[DB] initializeDefaultAdmin failed: " + e.getMessage());
            return;
        }
        createAdminUser(DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD);
    }

    public synchronized boolean validateAdminLogin(String username, String password) {
        if (!available || username == null || password == null) {
            return false;
        }
        String sql = "SELECT password_hash, salt FROM admin_users WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String salt = rs.getString("salt");
                String providedHash = hashPassword(password, salt);
                return providedHash.equals(rs.getString("password_hash"));
            }
        } catch (SQLException e) {
            System.err.println("[DB] validateAdminLogin failed: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean createAdminUser(String username, String password) {
        if (!available || username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        String sql = "INSERT INTO admin_users (username, password_hash, salt) VALUES (?, ?, ?) " +
                "ON CONFLICT(username) DO UPDATE SET password_hash = excluded.password_hash, salt = excluded.salt";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[DB] createAdminUser failed: " + e.getMessage());
            return false;
        }
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
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS staff_members (" +
                " staff_id TEXT PRIMARY KEY," +
                " name TEXT NOT NULL," +
                " role TEXT NOT NULL," +
                " specialty TEXT," +
                " busy INTEGER DEFAULT 0," +
                " authenticated INTEGER DEFAULT 0," +
                " created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS patient_registry (" +
                " patient_id TEXT PRIMARY KEY," +
                " name TEXT NOT NULL," +
                " age INTEGER," +
                " severity INTEGER DEFAULT 1," +
                " state TEXT," +
                " assigned_staff TEXT," +
                " profile_id TEXT," +
                " admitted_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS patient_profiles (" +
                " profile_id TEXT PRIMARY KEY," +
                " name TEXT NOT NULL," +
                " age INTEGER NOT NULL," +
                " normalized_name TEXT NOT NULL," +
                " created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS patient_history (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " patient_id TEXT NOT NULL," +
                " event_type TEXT NOT NULL," +
                " description TEXT NOT NULL," +
                " actor TEXT," +
                " occurred_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS patient_observations (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " patient_id TEXT NOT NULL," +
                " observer_id TEXT," +
                " heart_rate INTEGER," +
                " blood_pressure TEXT," +
                " temperature REAL," +
                " oxygen_saturation INTEGER," +
                " notes TEXT," +
                " observed_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS severity_trends (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " patient_id TEXT NOT NULL," +
                " severity INTEGER NOT NULL," +
                " risk_score INTEGER NOT NULL," +
                " reason TEXT," +
                " recorded_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS patient_risk_snapshot (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " patient_id TEXT NOT NULL," +
                " risk_score INTEGER NOT NULL," +
                " risk_band TEXT NOT NULL," +
                " recorded_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS alerts (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " patient_id TEXT NOT NULL," +
                " alert_type TEXT NOT NULL," +
                " message TEXT NOT NULL," +
                " severity TEXT NOT NULL," +
                " is_resolved INTEGER DEFAULT 0," +
                " created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS admin_users (" +
                " username TEXT PRIMARY KEY," +
                " password_hash TEXT NOT NULL," +
                " salt TEXT NOT NULL," +
                " created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS admin_audit_log (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " event TEXT NOT NULL," +
                " created_at TEXT DEFAULT CURRENT_TIMESTAMP)");

            boolean hasProfileId = false;
            try (ResultSet columns = st.executeQuery("PRAGMA table_info(patient_registry)")) {
                while (columns.next()) {
                    if ("profile_id".equalsIgnoreCase(columns.getString("name"))) {
                        hasProfileId = true;
                        break;
                    }
                }
            }
            if (!hasProfileId) {
                st.executeUpdate("ALTER TABLE patient_registry ADD COLUMN profile_id TEXT");
            }
            st.executeUpdate("UPDATE patient_registry SET profile_id = patient_id " +
                    "WHERE profile_id IS NULL OR TRIM(profile_id) = ''");
            st.executeUpdate("INSERT OR IGNORE INTO patient_profiles (profile_id, name, age, normalized_name) " +
                    "SELECT profile_id, name, age, lower(trim(name)) FROM patient_registry");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_patient_profiles_match " +
                    "ON patient_profiles (normalized_name, age)");
        }
    }

    private String generateSalt() {
        byte[] buffer = new byte[SALT_LENGTH];
        secureRandom.nextBytes(buffer);
        return bytesToHex(buffer);
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing failed", e);
        }
    }

    private String bytesToHex(byte[] input) {
        StringBuilder sb = new StringBuilder(input.length * 2);
        for (byte b : input) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public synchronized void insertAdmission(Patient p) {
        if (!available) return;
        String sql = "INSERT OR REPLACE INTO admissions (patient_id, name, age, severity) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, p.getPatientId());
            ps.setString(2, p.getName());
            ps.setInt(3, p.getAge());
            ps.setInt(4, p.getSeverityScore());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] insertAdmission failed: " + e.getMessage());
        }
        upsertPatientRecord(p);
    }

    public synchronized void addHistoryEvent(String patientId, String eventType, String description, String actor) {
        if (!available || patientId == null || patientId.isBlank()) return;
        String sql = "INSERT INTO patient_history (patient_id, event_type, description, actor) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, patientId);
            ps.setString(2, eventType);
            ps.setString(3, description);
            ps.setString(4, actor);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] addHistoryEvent failed: " + e.getMessage());
        }
    }

    public synchronized void recordObservation(Patient p, String observerId, String note) {
        if (!available || p == null) return;
        String sql = "INSERT INTO patient_observations (patient_id, observer_id, heart_rate, blood_pressure, temperature, oxygen_saturation, notes) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            Patient.VitalSigns v = p.getVitals();
            ps.setString(1, p.getPatientId());
            ps.setString(2, observerId);
            ps.setInt(3, v.getHeartRate());
            ps.setString(4, v.getSystolicBp() + "/" + v.getDiastolicBp());
            ps.setDouble(5, v.getTemperature());
            ps.setInt(6, v.getOxygenSaturation());
            ps.setString(7, note);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] recordObservation failed: " + e.getMessage());
        }
    }

    public synchronized void recordSeverityTrend(Patient p, String reason) {
        if (!available || p == null) return;
        String sql = "INSERT INTO severity_trends (patient_id, severity, risk_score, reason) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int riskScore = p.getSeverityScore() * 8 + (p.getVitals().getHeartRate() / 4);
            ps.setString(1, p.getPatientId());
            ps.setInt(2, p.getSeverityScore());
            ps.setInt(3, riskScore);
            ps.setString(4, reason);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] recordSeverityTrend failed: " + e.getMessage());
        }
    }

    public synchronized void recordRiskSnapshot(Patient p, int riskScore, String riskBand) {
        if (!available || p == null) return;
        String sql = "INSERT INTO patient_risk_snapshot (patient_id, risk_score, risk_band) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, p.getPatientId());
            ps.setInt(2, riskScore);
            ps.setString(3, riskBand);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] recordRiskSnapshot failed: " + e.getMessage());
        }
    }

    public synchronized void recordAlert(Patient p, String alertType, String message, String severity) {
        if (!available || p == null) return;
        String sql = "INSERT INTO alerts (patient_id, alert_type, message, severity) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, p.getPatientId());
            ps.setString(2, alertType);
            ps.setString(3, message);
            ps.setString(4, severity);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] recordAlert failed: " + e.getMessage());
        }
    }

    public synchronized String patientTimelineReport(String patientId) {
        if (!available || patientId == null || patientId.isBlank()) return "No patient timeline available.";
        StringBuilder report = new StringBuilder("=== PATIENT TIMELINE ===\n");
        String sql = "SELECT event_type, description, actor, occurred_at FROM patient_history "
                + "WHERE patient_id = ? ORDER BY occurred_at DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    report.append(rs.getString("occurred_at")).append(" | ")
                          .append(rs.getString("event_type")).append(" | ")
                          .append(rs.getString("actor") == null ? "system" : rs.getString("actor")).append(" | ")
                          .append(rs.getString("description")).append("\n");
                }
            }
        } catch (SQLException e) {
            report.append("Timeline query failed: ").append(e.getMessage());
        }
        return report.toString();
    }

    public synchronized String riskSummaryReport() {
        if (!available) return "Database unavailable — risk report not available.";
        StringBuilder report = new StringBuilder("=== RISK SUMMARY ===\n");
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT patient_id, risk_score, risk_band, recorded_at FROM patient_risk_snapshot ORDER BY recorded_at DESC LIMIT 20")) {
                while (rs.next()) {
                    report.append(rs.getString("patient_id")).append(" | risk ")
                          .append(rs.getInt("risk_score")).append(" (")
                          .append(rs.getString("risk_band")).append(") | ")
                          .append(rs.getString("recorded_at")).append("\n");
                }
            }
        } catch (SQLException e) {
            report.append("Risk summary query failed: ").append(e.getMessage());
        }
        return report.toString();
    }

    public synchronized void upsertStaffRecord(MedicalStaff staff) {
        if (!available) return;
        upsertStaffRecord(staff.getStaffId(), staff.getName(), staff.getRole(),
                (staff instanceof Doctor) ? ((Doctor) staff).getSpecialty() : null,
                staff.isBusy(), staff.isAuthenticated());
    }

    public synchronized void upsertStaffRecord(String staffId, String name, String role, String specialty,
                                              boolean busy, boolean authenticated) {
        if (!available) return;
        String sql = "INSERT INTO staff_members (staff_id, name, role, specialty, busy, authenticated) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(staff_id) DO UPDATE SET " +
                "name = excluded.name, role = excluded.role, specialty = excluded.specialty, " +
                "busy = excluded.busy, authenticated = excluded.authenticated";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, staffId);
            ps.setString(2, name);
            ps.setString(3, role);
            ps.setString(4, specialty);
            ps.setInt(5, busy ? 1 : 0);
            ps.setInt(6, authenticated ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] upsertStaffRecord failed: " + e.getMessage());
        }
    }

    public synchronized void deleteStaffRecord(String staffId) {
        if (!available || staffId == null || staffId.isBlank()) return;
        String sql = "DELETE FROM staff_members WHERE staff_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, staffId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] deleteStaffRecord failed: " + e.getMessage());
        }
    }

    public synchronized void upsertPatientRecord(Patient p) {
        if (!available) return;
        upsertPatientProfile(p.getHistoryRecordId(), p.getName(), p.getAge());
        upsertPatientRecord(p.getPatientId(), p.getHistoryRecordId(), p.getName(), p.getAge(), p.getSeverityScore(),
                p.getState() != null ? p.getState().name() : null,
                p.getAssignedStaff() != null ? p.getAssignedStaff().getName() : null);
    }

    public synchronized void upsertPatientRecord(String patientId, String name, int age, int severity,
                                               String state, String assignedStaff) {
        if (!available) return;
        upsertPatientProfile(patientId, name, age);
        upsertPatientRecord(patientId, patientId, name, age, severity, state, assignedStaff);
        }

        private void upsertPatientRecord(String patientId, String profileId, String name, int age, int severity,
                        String state, String assignedStaff) {
        String sql = "INSERT INTO patient_registry (patient_id, name, age, severity, state, assigned_staff, admitted_at, profile_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?) ON CONFLICT(patient_id) DO UPDATE SET " +
                "name = excluded.name, age = excluded.age, severity = excluded.severity, " +
            "state = excluded.state, assigned_staff = excluded.assigned_staff, profile_id = excluded.profile_id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, patientId);
            ps.setString(2, name);
            ps.setInt(3, age);
            ps.setInt(4, severity);
            ps.setString(5, state);
            ps.setString(6, assignedStaff);
            ps.setString(7, profileId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] upsertPatientRecord failed: " + e.getMessage());
        }
    }

    private void upsertPatientProfile(String profileId, String name, int age) {
        String sql = "INSERT INTO patient_profiles (profile_id, name, age, normalized_name) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(profile_id) DO UPDATE SET name = excluded.name, age = excluded.age, "
                + "normalized_name = excluded.normalized_name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profileId);
            statement.setString(2, name);
            statement.setInt(3, age);
            statement.setString(4, normalizePatientName(name));
            statement.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] upsertPatientProfile failed: " + e.getMessage());
        }
    }

    public synchronized void deletePatientRecord(String patientId) {
        if (!available || patientId == null || patientId.isBlank()) return;
        String sql = "DELETE FROM patient_registry WHERE patient_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, patientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] deletePatientRecord failed: " + e.getMessage());
        }
    }

    public synchronized void addAuditEvent(String event) {
        if (!available) return;
        String sql = "INSERT INTO admin_audit_log (event) VALUES (?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, event);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] addAuditEvent failed: " + e.getMessage());
        }
    }

    public synchronized List<String> getStaffRows() {
        List<String> rows = new ArrayList<>();
        if (!available) return rows;
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT staff_id, name, role, specialty, busy FROM staff_members ORDER BY name")) {
                while (rs.next()) {
                    rows.add(rs.getString("staff_id") + "|" + rs.getString("name") + "|" + rs.getString("role") + "|"
                            + (rs.getString("specialty") == null ? "" : rs.getString("specialty")) + "|"
                            + (rs.getInt("busy") == 1 ? "busy" : "available"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] getStaffRows failed: " + e.getMessage());
        }
        return rows;
    }

    public synchronized List<String> getPatientRows() {
        List<String> rows = new ArrayList<>();
        if (!available) return rows;
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT patient_id, name, age, severity, state, assigned_staff FROM patient_registry ORDER BY severity DESC, name")) {
                while (rs.next()) {
                    rows.add(rs.getString("patient_id") + "|" + rs.getString("name") + "|" + rs.getInt("age") + "|"
                            + rs.getInt("severity") + "|" + (rs.getString("state") == null ? "" : rs.getString("state")) + "|"
                            + (rs.getString("assigned_staff") == null ? "" : rs.getString("assigned_staff")));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] getPatientRows failed: " + e.getMessage());
        }
        return rows;
    }

    public synchronized PatientRecord getPatientRecord(String patientId) {
        if (!available || patientId == null || patientId.isBlank()) return null;
        String sql = "SELECT patient_id, profile_id, name, age, severity, state "
                + "FROM patient_registry WHERE patient_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patientId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    String profileId = result.getString("profile_id");
                    return new PatientRecord(result.getString("patient_id"),
                            profileId == null || profileId.isBlank() ? patientId : profileId,
                            result.getString("name"), result.getInt("age"), result.getInt("severity"),
                            result.getString("state"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] getPatientRecord failed: " + e.getMessage());
        }
        return null;
    }

    public synchronized String staffRosterReport() {
        if (!available) {
            return "Database unavailable — staff roster cannot be loaded.";
        }
        StringBuilder report = new StringBuilder("=== STAFF ROSTER ===\n");
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT staff_id, name, role, specialty, busy, authenticated FROM staff_members ORDER BY role, name")) {
                while (rs.next()) {
                    report.append(rs.getString("staff_id")).append(" | ")
                          .append(rs.getString("name")).append(" | ")
                          .append(rs.getString("role")).append(" | ")
                          .append(rs.getString("specialty") == null ? "-" : rs.getString("specialty")).append(" | ")
                          .append(rs.getInt("busy") == 1 ? "busy" : "available").append(" | ")
                          .append(rs.getInt("authenticated") == 1 ? "auth" : "not-auth").append("\n");
                }
            }
        } catch (SQLException e) {
            report.append("Roster query failed: ").append(e.getMessage());
        }
        return report.toString();
    }

    public synchronized String patientRegistryReport() {
        if (!available) {
            return "Database unavailable — patient registry unavailable.";
        }
        StringBuilder report = new StringBuilder("=== PATIENT REGISTRY ===\n");
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT patient_id, name, age, severity, state, assigned_staff FROM patient_registry ORDER BY severity DESC, admitted_at DESC")) {
                while (rs.next()) {
                    report.append(rs.getString("patient_id")).append(" | ")
                          .append(rs.getString("name")).append(" | age ")
                          .append(rs.getInt("age")).append(" | sev ")
                          .append(rs.getInt("severity")).append(" | ")
                          .append(rs.getString("state")).append(" | ")
                          .append(rs.getString("assigned_staff") == null ? "unassigned" : rs.getString("assigned_staff")).append("\n");
                }
            }
        } catch (SQLException e) {
            report.append("Patient registry query failed: ").append(e.getMessage());
        }
        return report.toString();
    }

    public synchronized String monthlyMetricsReport() {
        if (!available) {
            return "Database unavailable — monthly metrics cannot be loaded.";
        }
        StringBuilder report = new StringBuilder("=== MONTHLY METRICS ===\n");
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT strftime('%Y-%m', admitted_at) AS month, COUNT(*) AS total, AVG(severity) AS avg_sev " +
                            "FROM admissions GROUP BY month ORDER BY month DESC")) {
                while (rs.next()) {
                    report.append(rs.getString("month")).append(" | total=")
                          .append(rs.getInt("total")).append(" | avg severity=")
                          .append(String.format("%.1f", rs.getDouble("avg_sev"))).append("\n");
                }
            }
        } catch (SQLException e) {
            report.append("Monthly metrics query failed: ").append(e.getMessage());
        }
        return report.toString();
    }

    public synchronized String shiftMetricsReport() {
        if (!available) {
            return "Database unavailable — shift metrics cannot be loaded.";
        }
        StringBuilder report = new StringBuilder("=== SHIFT METRICS ===\n");
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS admissions, AVG(severity) AS avg_severity FROM admissions")) {
                if (rs.next()) {
                    report.append("Admissions: ").append(rs.getInt("admissions")).append("\n");
                    report.append("Average triage severity: ").append(String.format("%.1f", rs.getDouble("avg_severity"))).append("\n");
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS treatments FROM treatments")) {
                if (rs.next()) {
                    report.append("Treatments recorded: ").append(rs.getInt("treatments")).append("\n");
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT AVG(wait_minutes) AS avg_wait FROM discharges")) {
                if (rs.next()) {
                    report.append("Average discharge wait: ").append(String.format("%.1f", rs.getDouble("avg_wait"))).append(" minutes\n");
                }
            }
        } catch (SQLException e) {
            report.append("Shift metrics query failed: ").append(e.getMessage());
        }
        return report.toString();
    }

    public synchronized String analyticsSummary() {
        return monthlyMetricsReport() + "\n" + shiftMetricsReport();
    }

    public synchronized List<String> listStaffMembers() {
        return getStaffRows();
    }

    public synchronized List<String> listPatientRegistry() {
        return getPatientRows();
    }

    public synchronized void resetDatabase() {
        if (!available) return;
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("PRAGMA foreign_keys = OFF");
            st.executeUpdate("DELETE FROM alerts");
            st.executeUpdate("DELETE FROM patient_risk_snapshot");
            st.executeUpdate("DELETE FROM severity_trends");
            st.executeUpdate("DELETE FROM patient_observations");
            st.executeUpdate("DELETE FROM patient_history");
            st.executeUpdate("DELETE FROM admin_audit_log");
            st.executeUpdate("DELETE FROM admin_users");
            st.executeUpdate("DELETE FROM patient_registry");
            st.executeUpdate("DELETE FROM patient_profiles");
            st.executeUpdate("DELETE FROM staff_members");
            st.executeUpdate("DELETE FROM discharges");
            st.executeUpdate("DELETE FROM treatments");
            st.executeUpdate("DELETE FROM admissions");
            st.executeUpdate("DELETE FROM sqlite_sequence WHERE name IN ('admissions', 'treatments', 'discharges', 'staff_members', 'patient_profiles', 'patient_registry', 'admin_users')");
            st.executeUpdate("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            System.err.println("[DB] resetDatabase failed: " + e.getMessage());
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
