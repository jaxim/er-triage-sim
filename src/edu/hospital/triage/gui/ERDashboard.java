package edu.hospital.triage.gui;

import edu.hospital.triage.engine.SimulationEngine;
import edu.hospital.triage.model.Patient;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.List;

/**
 * High-stress ER dashboard. Three distinct zones built with layout managers:
 *   NORTH  — admissions desk (JTextFields + admit JButton)
 *   CENTER — active waiting room (dynamic JLabels, severity-sorted)
 *   EAST   — intensive care monitor
 *   SOUTH  — event log + shift-report controls
 *
 * A Swing Timer refreshes the display so the EDT safely re-renders state
 * mutated by the background vital-sign threads.
 */
public class ERDashboard extends JFrame {

    private static final long serialVersionUID = 1L;

    private final SimulationEngine engine;

    private final JTextField nameField = new JTextField(12);
    private final JTextField ageField = new JTextField(3);
    private final JTextField symptomsField = new JTextField(24);

    private final JPanel waitingRoomPanel = new JPanel();
    private final JPanel icuPanel = new JPanel();
    private final JTextArea eventLog = new JTextArea(8, 60);

    public ERDashboard(SimulationEngine engine) {
        super("ER Triage & Patient Monitoring — Blueprint V");
        this.engine = engine;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        add(buildAdmissionsDesk(), BorderLayout.NORTH);
        add(buildWaitingRoom(), BorderLayout.CENTER);
        add(buildIcuMonitor(), BorderLayout.EAST);
        add(buildLogAndControls(), BorderLayout.SOUTH);

        // Engine -> GUI hooks. Always hop onto the EDT.
        engine.setEventLog(msg -> SwingUtilities.invokeLater(() -> appendLog(msg)));
        engine.setRefreshCallback(() -> SwingUtilities.invokeLater(this::refreshBoards));

        // Heartbeat: re-render telemetry every second even without events.
        new Timer(1000, e -> refreshBoards()).start();

        setSize(1100, 700);
        setLocationRelativeTo(null);
    }

    /* ------------------------- admissions desk ------------------------- */

    private JPanel buildAdmissionsDesk() {
        JPanel desk = new JPanel();
        desk.setBorder(BorderFactory.createTitledBorder("Admissions Desk"));
        desk.add(new JLabel("Name:"));
        desk.add(nameField);
        desk.add(new JLabel("Age:"));
        desk.add(ageField);
        desk.add(new JLabel("Symptoms (comma-separated):"));
        desk.add(symptomsField);

        JButton admitBtn = new JButton("Admit Patient");
        admitBtn.addActionListener(e -> onAdmit());
        desk.add(admitBtn);
        return desk;
    }

    private void onAdmit() {
        String name = nameField.getText().trim();
        String ageText = ageField.getText().trim();
        String symptomsText = symptomsField.getText().trim();

        if (name.isEmpty() || ageText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name and age are required.",
                    "Admission error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int age;
        try {
            age = Integer.parseInt(ageText);
        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "Age must be a number.",
                    "Admission error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> symptoms = symptomsText.isEmpty()
                ? List.of("general complaint")
                : Arrays.asList(symptomsText.split("\\s*,\\s*"));

        engine.admitPatient(name, age, symptoms);
        nameField.setText("");
        ageField.setText("");
        symptomsField.setText("");
    }

    /* -------------------------- waiting room --------------------------- */

    private JScrollPane buildWaitingRoom() {
        waitingRoomPanel.setLayout(new BoxLayout(waitingRoomPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(waitingRoomPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("Active Waiting Room (priority order)"));
        return scroll;
    }

    /* --------------------------- ICU monitor --------------------------- */

    private JScrollPane buildIcuMonitor() {
        icuPanel.setLayout(new BoxLayout(icuPanel, BoxLayout.Y_AXIS));
        icuPanel.setBackground(new Color(35, 0, 0));
        JScrollPane scroll = new JScrollPane(icuPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("Intensive Care Monitor"));
        scroll.setPreferredSize(new java.awt.Dimension(340, 0));
        return scroll;
    }

    /* ------------------------ log + control strip ---------------------- */

    private JPanel buildLogAndControls() {
        JPanel south = new JPanel(new BorderLayout(4, 4));

        JPanel controls = new JPanel(new GridLayout(1, 0, 6, 0));
        JButton treatBtn = new JButton("Doctor: Treat Next Patient");
        JButton roundsBtn = new JButton("Nurse: Monitor Vitals (Rounds)");
        JButton icuBtn = new JButton("ICU: Treat Critical Patient");
        JButton reportBtn = new JButton("End-of-Shift Report");
        JButton adminBtn = new JButton("Admin Panel");

        treatBtn.addActionListener(e -> appendLog(engine.treatNextPatient()));
        roundsBtn.addActionListener(e -> appendLog(engine.nurseRounds()));
        icuBtn.addActionListener(e -> appendLog(engine.treatCriticalPatient()));
        reportBtn.addActionListener(e ->
                JOptionPane.showMessageDialog(this, engine.getDb().endOfShiftReport(),
                        "End-of-Shift Report", JOptionPane.INFORMATION_MESSAGE));
        adminBtn.addActionListener(e -> {
            AdminPanel adminPanel = new AdminPanel(engine);
            adminPanel.setVisible(true);
        });

        controls.add(treatBtn);
        controls.add(roundsBtn);
        controls.add(icuBtn);
        controls.add(reportBtn);
        controls.add(adminBtn);

        eventLog.setEditable(false);
        eventLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        south.add(controls, BorderLayout.NORTH);
        south.add(new JScrollPane(eventLog), BorderLayout.CENTER);
        south.setBorder(BorderFactory.createTitledBorder("Event Log & Staff Actions"));
        return south;
    }

    /* --------------------------- rendering ----------------------------- */

    private void refreshBoards() {
        waitingRoomPanel.removeAll();
        for (Patient p : engine.getWaitingRoom().snapshot()) {
            waitingRoomPanel.add(patientLabel(p, false));
        }
        waitingRoomPanel.revalidate();
        waitingRoomPanel.repaint();

        icuPanel.removeAll();
        for (Patient p : engine.getCriticalCare()) {
            icuPanel.add(patientLabel(p, true));
        }
        icuPanel.revalidate();
        icuPanel.repaint();
    }

    /** Dynamic JLabel rendering one patient's live telemetry. */
    private JLabel patientLabel(Patient p, boolean icu) {
        JLabel label = new JLabel(String.format("  [sev %d] %s %s — %s  ",
                p.getSeverityScore(), p.getPatientId(), p.getName(), p.getVitals()));
        label.setOpaque(true);
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        if (icu) {
            label.setBackground(new Color(70, 0, 0));
            label.setForeground(Color.RED);
        } else if (p.getSeverityScore() >= 7) {
            label.setBackground(new Color(255, 205, 205));
        } else if (p.getSeverityScore() >= 4) {
            label.setBackground(new Color(255, 240, 200));
        } else {
            label.setBackground(new Color(215, 245, 215));
        }
        return label;
    }

    private void appendLog(String msg) {
        eventLog.append(msg + "\n");
        eventLog.setCaretPosition(eventLog.getDocument().getLength());
    }
}
