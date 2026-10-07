package edu.hospital.triage.gui;

import edu.hospital.triage.engine.SimulationEngine;
import edu.hospital.triage.model.Patient;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyleConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ERDashboard extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final String CANCEL_ADMISSION = "__CANCEL_ADMISSION__";

    private final SimulationEngine engine;

    private final JTextField nameField = new JTextField(12);
    private final JTextField ageField = new JTextField(3);
    private final JTextField symptomsField = new JTextField(24);

    private final JPanel waitingRoomPanel = new JPanel();
    private final JPanel icuPanel = new JPanel();
    private final JTextPane eventLog = new JTextPane();
    private final Map<String, LogEntry> loggedEvents = new LinkedHashMap<>();

    public ERDashboard(SimulationEngine engine) {
        super("ER Triage & Patient Monitoring");
        this.engine = engine;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(new Color(9, 14, 25));

        add(buildDashboardShell(), BorderLayout.CENTER);

        engine.setEventLog(msg -> SwingUtilities.invokeLater(() -> appendLog(msg)));
        engine.setRefreshCallback(() -> SwingUtilities.invokeLater(this::refreshBoards));

        new Timer(1000, e -> refreshBoards()).start();

        setMinimumSize(new Dimension(900, 620));
        setSize(1220, 740);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private JPanel buildDashboardShell() {
        JPanel shell = new JPanel(new BorderLayout(16, 16));
        shell.setOpaque(false);
        shell.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        shell.add(buildHeaderBar(), BorderLayout.NORTH);
        shell.add(buildMainArea(), BorderLayout.CENTER);
        shell.add(buildLogAndControls(), BorderLayout.SOUTH);
        return shell;
    }

    private JPanel buildHeaderBar() {
        JPanel bar = new JPanel(new BorderLayout(18, 18));
        bar.setBackground(new Color(15, 23, 42));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(51, 65, 85)),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));

        JLabel title = new JLabel("ER Command Center");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        title.setForeground(new Color(241, 245, 249));

        JLabel statusPill = new JLabel("Live monitoring");
        statusPill.setOpaque(true);
        statusPill.setBackground(new Color(37, 99, 235));
        statusPill.setForeground(Color.WHITE);
        statusPill.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        statusPill.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.setOpaque(false);
        right.add(statusPill);

        bar.add(title, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildMainArea() {
        JPanel main = new JPanel(new BorderLayout(0, 16));
        main.setOpaque(false);
        JPanel patientPanels = new JPanel(new GridLayout(1, 2, 16, 0));
        patientPanels.setOpaque(false);
        patientPanels.add(buildWaitingRoom());
        patientPanels.add(buildIcuMonitor());
        main.add(buildAdmissionsDesk(), BorderLayout.NORTH);
        main.add(patientPanels, BorderLayout.CENTER);
        return main;
    }

    private JPanel buildAdmissionsDesk() {
        JPanel desk = new JPanel(new BorderLayout(16, 12));
        desk.setBackground(new Color(17, 24, 39));
        desk.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        JLabel nameLabel = new JLabel("Name");
        JLabel ageLabel = new JLabel("Age");
        JLabel symptomsLabel = new JLabel("Symptoms");
        nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        ageLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        symptomsLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        nameLabel.setForeground(new Color(226, 232, 240));
        ageLabel.setForeground(new Color(226, 232, 240));
        symptomsLabel.setForeground(new Color(226, 232, 240));

        styleInput(nameField);
        styleInput(ageField);
        styleInput(symptomsField);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 6, 5, 6);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridy = 0;
        constraints.gridx = 0;
        constraints.weightx = 0;
        form.add(nameLabel, constraints);
        constraints.gridx = 1;
        constraints.weightx = 0.55;
        form.add(nameField, constraints);
        constraints.gridx = 2;
        constraints.weightx = 0;
        form.add(ageLabel, constraints);
        constraints.gridx = 3;
        constraints.weightx = 0.2;
        form.add(ageField, constraints);
        constraints.gridy = 1;
        constraints.gridx = 0;
        constraints.weightx = 0;
        form.add(symptomsLabel, constraints);
        constraints.gridx = 1;
        constraints.gridwidth = 3;
        constraints.weightx = 1;
        form.add(symptomsField, constraints);

        JButton admitBtn = new JButton("Admit Patient");
        styleActionButton(admitBtn, new Color(37, 99, 235), Color.WHITE);
        admitBtn.addActionListener(e -> onAdmit());

        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.add(admitBtn, BorderLayout.CENTER);

        desk.add(form, BorderLayout.CENTER);
        desk.add(right, BorderLayout.EAST);
        return desk;
    }

    private void styleInput(JTextField field) {
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        field.setBackground(new Color(15, 23, 42));
        field.setForeground(new Color(241, 245, 249));
        field.setCaretColor(new Color(148, 163, 184));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(71, 85, 105)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    }

    private void styleActionButton(JButton button) {
        styleActionButton(button, new Color(37, 99, 235), Color.WHITE);
    }

    private void styleActionButton(JButton button, Color bg, Color fg) {
        button.setFocusPainted(false);
        button.setBackground(bg);
        button.setForeground(fg);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.brighter().brighter()),
                BorderFactory.createEmptyBorder(11, 18, 11, 18)));
        button.setPreferredSize(new Dimension(180, 44));
    }

    private void onAdmit() {
        String name = nameField.getText().trim();
        String ageText = ageField.getText().trim();
        String symptomsText = symptomsField.getText().trim();

        if (name.isEmpty() || ageText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name and age are required.", "Admission error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int age;
        try {
            age = Integer.parseInt(ageText);
        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "Age must be a number.", "Admission error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (age < 0 || age > 130) {
            JOptionPane.showMessageDialog(this, "Age must be between 0 and 130.", "Admission error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> symptoms = symptomsText.isEmpty() ? List.of("general complaint") : Arrays.asList(symptomsText.split("\\s*,\\s*"));
        String historyRecordId = selectHistoryProfile(name, age);
        if (CANCEL_ADMISSION.equals(historyRecordId)) {
            return;
        }

        try {
            engine.admitPatient(name, age, symptoms, historyRecordId);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Admission unavailable", JOptionPane.WARNING_MESSAGE);
            return;
        }
        nameField.setText("");
        ageField.setText("");
        symptomsField.setText("");
    }

    private String selectHistoryProfile(String name, int age) {
        List<edu.hospital.triage.db.DatabaseManager.PatientHistoryMatch> matches =
                engine.getDb().findPreviousPatientHistory(name, age);
        if (matches.isEmpty()) {
            return null;
        }

        String caution = "Possible record matches name and age. Verify identity before linking history.";
        if (matches.size() == 1) {
            Object[] choices = {"Link previous history", "Create separate profile", "Cancel admission"};
            int choice = JOptionPane.showOptionDialog(this,
                    caution + "\n" + matches.get(0), "Previous patient history",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
            if (choice == 0) {
                return matches.get(0).getProfileId();
            }
            return choice == 1 ? null : CANCEL_ADMISSION;
        }

        Object createNew = "Create separate profile";
        Object[] choices = new Object[matches.size() + 2];
        for (int index = 0; index < matches.size(); index++) {
            choices[index] = matches.get(index);
        }
        choices[matches.size()] = createNew;
        choices[matches.size() + 1] = "Cancel admission";
        Object selected = JOptionPane.showInputDialog(this,
                caution + "\nSelect the verified history profile:", "Previous patient history",
                JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
        if (selected instanceof edu.hospital.triage.db.DatabaseManager.PatientHistoryMatch) {
            return ((edu.hospital.triage.db.DatabaseManager.PatientHistoryMatch) selected).getProfileId();
        }
        return createNew.equals(selected) ? null : CANCEL_ADMISSION;
    }

    private JScrollPane buildWaitingRoom() {
        waitingRoomPanel.setLayout(new BoxLayout(waitingRoomPanel, BoxLayout.Y_AXIS));
        waitingRoomPanel.setBackground(new Color(15, 23, 42));
        waitingRoomPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(waitingRoomPanel);
        javax.swing.border.TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(71, 85, 105)), "WAITING ROOM");
        titledBorder.setTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        titledBorder.setTitleColor(new Color(148, 163, 184));
        scroll.setBorder(BorderFactory.createCompoundBorder(
            titledBorder,
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JScrollPane buildIcuMonitor() {
        icuPanel.setLayout(new BoxLayout(icuPanel, BoxLayout.Y_AXIS));
        icuPanel.setBackground(new Color(15, 23, 42));
        icuPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(icuPanel);
        javax.swing.border.TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(71, 85, 105)), "ICU MONITOR");
        titledBorder.setTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        titledBorder.setTitleColor(new Color(148, 163, 184));
        scroll.setBorder(BorderFactory.createCompoundBorder(
            titledBorder,
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildLogAndControls() {
        JPanel south = new JPanel(new BorderLayout(10, 10));
        south.setBackground(new Color(17, 24, 39));
        south.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        south.setPreferredSize(new Dimension(900, 220));

        JPanel controls = new JPanel(new GridLayout(1, 0, 8, 0));
        controls.setOpaque(false);

        JButton treatBtn = new JButton("Doctor: Treat Next Patient");
        JButton roundsBtn = new JButton("Nurse: Monitor Vitals");
        JButton icuBtn = new JButton("ICU: Treat Critical Patient");
        JButton reportBtn = new JButton("End-of-Shift Report");
        JButton adminBtn = new JButton("Admin Panel");

        styleActionButton(treatBtn, new Color(37, 99, 235), Color.WHITE);
        styleActionButton(roundsBtn, new Color(255, 255, 255), new Color(15, 23, 42));
        styleActionButton(icuBtn, new Color(255, 255, 255), new Color(15, 23, 42));
        styleActionButton(reportBtn, new Color(255, 255, 255), new Color(15, 23, 42));
        styleActionButton(adminBtn, new Color(255, 255, 255), new Color(15, 23, 42));

        treatBtn.addActionListener(e -> appendLog(engine.treatNextPatient()));
        roundsBtn.addActionListener(e -> appendLog(engine.nurseRounds()));
        icuBtn.addActionListener(e -> appendLog(engine.treatCriticalPatient()));
        reportBtn.addActionListener(e -> JOptionPane.showMessageDialog(this, engine.getDb().endOfShiftReport(), "End-of-Shift Report", JOptionPane.INFORMATION_MESSAGE));
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
        eventLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        eventLog.setBackground(new Color(15, 23, 42));
        eventLog.setForeground(new Color(226, 232, 240));
        eventLog.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(71, 85, 105)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        south.add(controls, BorderLayout.NORTH);
        JScrollPane logScroll = new JScrollPane(eventLog);
        logScroll.setPreferredSize(new Dimension(800, 132));
        south.add(logScroll, BorderLayout.CENTER);
        return south;
    }

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

    private JLabel patientLabel(Patient p, boolean icu) {
        String summary = String.format("[sev %d] %s %s — %s",
            p.getSeverityScore(), p.getPatientId(), p.getName(), p.getVitals());
        Color accent = severityColor(p.getSeverityScore());
        JLabel label = new JLabel(String.format(
                "<html><b><font color='#f8fafc' size='+1'>%s</font></b><br><font color='#cbd5e1'>%s</font> | <font color='%s'>Severity %d</font><br>HR %d bpm | BP %d/%d<br>Temp %.1f C | SpO2 %d%%</html>",
                escapeHtml(p.getName()), escapeHtml(p.getPatientId()), toHex(accent), p.getSeverityScore(),
                p.getVitals().getHeartRate(), p.getVitals().getSystolicBp(), p.getVitals().getDiastolicBp(),
                p.getVitals().getTemperature(), p.getVitals().getOxygenSaturation()));
        label.setOpaque(true);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        label.setBackground(new Color(15, 23, 42));
        label.setForeground(new Color(241, 245, 249));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accent),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        label.setToolTipText(summary);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showPatientDetail(p);
            }
        });
        return label;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private Color severityColor(int severity) {
        if (severity >= 8) {
            return new Color(239, 68, 68);
        }
        if (severity >= 5) {
            return new Color(245, 158, 11);
        }
        if (severity >= 3) {
            return new Color(59, 130, 246);
        }
        return new Color(34, 197, 94);
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private void showPatientDetail(Patient patient) {
        JDialog dialog = new JDialog(this, "Patient Detail: " + patient.getPatientId(), true);
        dialog.setSize(560, 520);
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBackground(new Color(9, 14, 25));
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JLabel title = new JLabel(patient.getName() + " — " + patient.getPatientId());
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        title.setForeground(new Color(241, 245, 249));

        JTextArea info = new JTextArea();
        info.setEditable(false);
        info.setBackground(new Color(15, 23, 42));
        info.setForeground(new Color(226, 232, 240));
        info.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(71, 85, 105)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        info.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

        info.setText(
                "Age: " + patient.getAge() + "\n" +
                "State: " + patient.getState() + "\n" +
                "Severity: " + patient.getSeverityScore() + "\n" +
                "Vitals: " + patient.getVitals() + "\n" +
                "History profile: " + patient.getHistoryRecordId() + "\n" +
                "Assigned Staff: " + (patient.getAssignedStaff() == null ? "Unassigned" : patient.getAssignedStaff().getName()) + "\n\n" +
                "Symptoms: " + patient.getMedicalRecord().getSymptomChecklist() + "\n\n" +
                "History:\n" + engine.getDb().patientTimelineReport(patient.getHistoryRecordId())
        );

        JButton closeBtn = new JButton("Close");
        styleActionButton(closeBtn);
        closeBtn.addActionListener(e -> dialog.dispose());

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);
        bottom.add(closeBtn);

        content.add(title, BorderLayout.NORTH);
        JScrollPane infoScroll = new JScrollPane(info);
        content.add(infoScroll, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setVisible(true);
    }

    private void appendLog(String msg) {
        StyledDocument document = eventLog.getStyledDocument();
        String key = msg.startsWith("Deterioration: ")
                ? msg.replaceFirst(" severity now \\d+$", "")
                : msg;
        try {
            LogEntry entry = loggedEvents.get(key);
            if (entry == null) {
                entry = new LogEntry(document.createPosition(document.getLength()));
                loggedEvents.put(key, entry);
                insertLogLine(document, entry.start.getOffset(), msg, entry.repeatCount);
            } else {
                int start = entry.start.getOffset();
                int end = document.getParagraphElement(start).getEndOffset();
                document.remove(start, Math.min(end, document.getLength()) - start);
                entry.repeatCount++;
                insertLogLine(document, start, msg, entry.repeatCount);
            }

            Element root = document.getDefaultRootElement();
            if (root.getElementCount() > 250) {
                int removedLength = root.getElement(0).getEndOffset();
                int cutoff = removedLength;
                loggedEvents.entrySet().removeIf(item -> item.getValue().start.getOffset() < cutoff);
                document.remove(0, removedLength);
            }
            eventLog.setCaretPosition(document.getLength());
        } catch (BadLocationException ignored) {
        }
    }

    private void insertLogLine(StyledDocument document, int offset, String message, int repeatCount)
            throws BadLocationException {
        String suffix = repeatCount > 1 ? " (x" + repeatCount + ")" : "";
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, logColor(repeatCount));
        document.insertString(offset, message + suffix + "\n", attributes);
    }

    private Color logColor(int repeatCount) {
        if (repeatCount > 1) {
            return new Color(96, 165, 250);
        }
        return new Color(148, 163, 184);
    }

    private static class LogEntry {
        private final Position start;
        private int repeatCount = 1;

        LogEntry(Position start) {
            this.start = start;
        }
    }

}
