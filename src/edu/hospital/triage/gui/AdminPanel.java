package edu.hospital.triage.gui;

import edu.hospital.triage.engine.SimulationEngine;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

public class AdminPanel extends JFrame {

    private static final long serialVersionUID = 1L;

    private final SimulationEngine engine;

    private final DefaultTableModel staffModel = new DefaultTableModel(
            new String[]{"Staff ID", "Name", "Role", "Specialty", "Status"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final DefaultTableModel patientModel = new DefaultTableModel(
            new String[]{"Patient ID", "Name", "Age", "Severity", "State", "Assigned Staff"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable staffTable = new JTable(staffModel);
    private final JTable patientTable = new JTable(patientModel);

    private final JTextField staffIdField = new JTextField();
    private final JTextField staffNameField = new JTextField();
    private final JTextField staffRoleField = new JTextField();
    private final JTextField staffSpecialtyField = new JTextField();

    private final JTextField patientIdField = new JTextField();
    private final JTextField patientNameField = new JTextField();
    private final JTextField patientAgeField = new JTextField();
    private final JTextField patientSeverityField = new JTextField();
    private final JTextField patientStateField = new JTextField();
    private final JTextField patientAssignedStaffField = new JTextField();

    private final JTextArea analyticsArea = new JTextArea(18, 55);

    public AdminPanel(SimulationEngine engine) {
        super("Master Control Panel");
        this.engine = engine;

        setMinimumSize(new Dimension(900, 620));
        setSize(1100, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(new Color(9, 14, 25));
        for (JTextField field : List.of(staffIdField, staffNameField, staffRoleField, staffSpecialtyField,
                patientIdField, patientNameField, patientAgeField, patientSeverityField,
                patientStateField, patientAssignedStaffField)) {
            styleField(field);
        }

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(15, 23, 42));
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(51, 65, 85)),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)));

        JLabel header = new JLabel("Hospital Administration Console", SwingConstants.LEFT);
        header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        header.setForeground(new Color(241, 245, 249));

        JLabel status = new JLabel("System Online");
        status.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        status.setForeground(new Color(134, 239, 172));
        status.setHorizontalAlignment(SwingConstants.RIGHT);

        topBar.add(header, BorderLayout.WEST);
        topBar.add(status, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        tabs.setBackground(new Color(15, 23, 42));
        tabs.addTab("Staff Management", buildStaffTab());
        tabs.addTab("Patient Records", buildPatientTab());
        tabs.addTab("Reports & Analytics", buildAnalyticsTab());

        add(tabs, BorderLayout.CENTER);
        refreshAll();
    }

    private JPanel buildStaffTab() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(new Color(11, 17, 32));

        JPanel form = new JPanel(new GridLayout(4, 2, 12, 12));
        form.setOpaque(false);
        form.setBorder(sectionBorder("Staff record"));
        form.add(new JLabel("Staff ID:"));
        form.add(staffIdField);
        form.add(new JLabel("Name:"));
        form.add(staffNameField);
        form.add(new JLabel("Role:"));
        form.add(staffRoleField);
        form.add(new JLabel("Specialty:"));
        form.add(staffSpecialtyField);
        styleFormLabels(form);

        JPanel actions = new JPanel(new GridLayout(1, 4, 10, 10));
        actions.setOpaque(false);
        JButton addBtn = new JButton("Add / Save");
        JButton selectBtn = new JButton("Load Selected");
        JButton deleteBtn = new JButton("Delete");
        JButton refreshBtn = new JButton("Refresh");

        styleButton(addBtn);
        styleButton(selectBtn);
        styleButton(deleteBtn);
        styleButton(refreshBtn);

        addBtn.addActionListener(e -> saveStaff());
        selectBtn.addActionListener(e -> loadSelectedStaffRow());
        deleteBtn.addActionListener(e -> deleteSelectedStaff());
        refreshBtn.addActionListener(e -> refreshAll());

        actions.add(addBtn);
        actions.add(selectBtn);
        actions.add(deleteBtn);
        actions.add(refreshBtn);

        styleTable(staffTable);
        staffTable.setFillsViewportHeight(true);
        staffTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedStaffRow();
            }
        });

        panel.add(form, BorderLayout.NORTH);
        panel.add(styleScrollPane(new JScrollPane(staffTable)), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPatientTab() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setBackground(new Color(11, 17, 32));

        JPanel form = new JPanel(new GridLayout(6, 2, 12, 12));
        form.setOpaque(false);
        form.setBorder(sectionBorder("Patient record"));
        form.add(new JLabel("Patient ID:"));
        form.add(patientIdField);
        form.add(new JLabel("Name:"));
        form.add(patientNameField);
        form.add(new JLabel("Age:"));
        form.add(patientAgeField);
        form.add(new JLabel("Severity:"));
        form.add(patientSeverityField);
        form.add(new JLabel("State:"));
        form.add(patientStateField);
        form.add(new JLabel("Assigned Staff:"));
        form.add(patientAssignedStaffField);
        styleFormLabels(form);

        JPanel actions = new JPanel(new GridLayout(1, 5, 10, 10));
        actions.setOpaque(false);
        JButton addBtn = new JButton("Add / Save");
        JButton selectBtn = new JButton("Load Selected");
        JButton waitingBtn = new JButton("Return to Waiting");
        JButton deleteBtn = new JButton("Delete");
        JButton refreshBtn = new JButton("Refresh");

        styleButton(addBtn);
        styleButton(selectBtn);
        styleButton(waitingBtn);
        styleButton(deleteBtn);
        styleButton(refreshBtn);

        addBtn.addActionListener(e -> savePatient());
        selectBtn.addActionListener(e -> loadSelectedPatientRow());
        waitingBtn.addActionListener(e -> returnSelectedPatientToWaitingRoom());
        deleteBtn.addActionListener(e -> deleteSelectedPatient());
        refreshBtn.addActionListener(e -> refreshAll());

        actions.add(addBtn);
        actions.add(selectBtn);
        actions.add(waitingBtn);
        actions.add(deleteBtn);
        actions.add(refreshBtn);

        styleTable(patientTable);
        patientTable.setFillsViewportHeight(true);
        patientTable.getColumnModel().getColumn(3).setCellRenderer(new SeverityRenderer());
        patientTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedPatientRow();
            }
        });

        panel.add(form, BorderLayout.NORTH);
        panel.add(styleScrollPane(new JScrollPane(patientTable)), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildAnalyticsTab() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(new Color(11, 17, 32));
        panel.setBorder(sectionBorder("Hospital analytics"));

        analyticsArea.setEditable(false);
        analyticsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        analyticsArea.setBackground(new Color(15, 23, 42));
        analyticsArea.setForeground(new Color(226, 232, 240));
        analyticsArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(71, 85, 105)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JButton refreshBtn = new JButton("Refresh analytics");
        styleButton(refreshBtn, new Color(37, 99, 235), Color.WHITE);
        refreshBtn.addActionListener(e -> refreshAll());

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(refreshBtn, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        panel.add(styleScrollPane(new JScrollPane(analyticsArea)), BorderLayout.CENTER);
        return panel;
    }

    private void styleField(JTextField field) {
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        field.setBackground(new Color(15, 23, 42));
        field.setForeground(new Color(241, 245, 249));
        field.setCaretColor(new Color(148, 163, 184));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(71, 85, 105)),
                BorderFactory.createEmptyBorder(7, 9, 7, 9)));
    }

    private Border sectionBorder(String title) {
        TitledBorder titledBorder = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(71, 85, 105)), title);
        titledBorder.setTitleColor(new Color(148, 163, 184));
        titledBorder.setTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        return BorderFactory.createCompoundBorder(titledBorder, BorderFactory.createEmptyBorder(12, 12, 12, 12));
    }

    private void styleFormLabels(JPanel form) {
        for (Component component : form.getComponents()) {
            if (component instanceof JLabel) {
                component.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            }
        }
    }

    private void styleTable(JTable table) {
        table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        table.setRowHeight(34);
        table.setBackground(new Color(15, 23, 42));
        table.setForeground(new Color(226, 232, 240));
        table.setGridColor(new Color(51, 65, 85));
        table.setSelectionBackground(new Color(37, 99, 235));
        table.setSelectionForeground(Color.WHITE);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer());
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
        table.getTableHeader().setBackground(new Color(15, 23, 42));
        table.getTableHeader().setForeground(new Color(148, 163, 184));
        table.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
    }

    private JScrollPane styleScrollPane(JScrollPane scrollPane) {
        return scrollPane;
    }

    private void styleButton(JButton button) {
        styleButton(button, new Color(37, 99, 235), Color.WHITE);
    }

    private void styleButton(JButton button, Color bg, Color fg) {
        button.setFocusPainted(false);
        button.setBackground(bg);
        button.setForeground(fg);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.brighter().brighter()),
                BorderFactory.createEmptyBorder(9, 14, 9, 14)));
        button.setPreferredSize(new Dimension(170, 40));
    }

    private static class SeverityRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Component cell = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(table.getFont().deriveFont(Font.BOLD));
            return cell;
        }
    }

    private void refreshAll() {
        loadStaffTable();
        loadPatientTable();
        analyticsArea.setText(engine.getDb().analyticsSummary());
    }

    private void loadStaffTable() {
        staffModel.setRowCount(0);
        List<String> rows = engine.getDb().getStaffRows();
        for (String row : rows) {
            String[] parts = row.split("\\|", -1);
            if (parts.length >= 5) {
                staffModel.addRow(new Object[]{parts[0], parts[1], parts[2], parts[3], parts[4]});
            }
        }
    }

    private void loadPatientTable() {
        patientModel.setRowCount(0);
        List<String> rows = engine.getDb().getPatientRows();
        for (String row : rows) {
            String[] parts = row.split("\\|", -1);
            if (parts.length >= 6) {
                patientModel.addRow(new Object[]{parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]});
            }
        }
    }

    private void saveStaff() {
        String staffId = staffIdField.getText().trim();
        String name = staffNameField.getText().trim();
        String role = staffRoleField.getText().trim();
        String specialty = staffSpecialtyField.getText().trim();

        if (name.isEmpty() || role.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name and role are required.", "Staff validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (staffId.isEmpty()) {
            staffId = "S-" + System.currentTimeMillis();
        }

        engine.getDb().upsertStaffRecord(staffId, name, role, specialty, false, true);
        engine.reloadStaffFromDatabase();
        refreshAll();
        clearStaffForm();
    }

    private void savePatient() {
        String patientId = patientIdField.getText().trim();
        String name = patientNameField.getText().trim();
        String ageText = patientAgeField.getText().trim();
        String severityText = patientSeverityField.getText().trim();
        String state = patientStateField.getText().trim();
        String assignedStaff = patientAssignedStaffField.getText().trim();

        if (name.isEmpty() || ageText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name and age are required.", "Patient validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int age = Integer.parseInt(ageText);
            int severity = severityText.isEmpty() ? 1 : Integer.parseInt(severityText);

            if (age < 0 || age > 130) {
                JOptionPane.showMessageDialog(this, "Age must be between 0 and 130.", "Patient validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (severity < 1 || severity > 10) {
                JOptionPane.showMessageDialog(this, "Severity must be between 1 and 10.", "Patient validation", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (patientId.isEmpty()) {
                patientId = "P-" + System.currentTimeMillis();
            }

            engine.getDb().upsertPatientRecord(patientId, name, age, severity, state, assignedStaff);
            refreshAll();
            clearPatientForm();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Age and severity must be numbers.", "Patient validation", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedStaff() {
        int row = staffTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a staff row first.", "Delete staff", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String staffId = (String) staffModel.getValueAt(row, 0);
        engine.getDb().deleteStaffRecord(staffId);
        engine.reloadStaffFromDatabase();
        refreshAll();
        clearStaffForm();
    }

    private void deleteSelectedPatient() {
        int row = patientTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a patient row first.", "Delete patient", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String patientId = (String) patientModel.getValueAt(row, 0);
        engine.getDb().deletePatientRecord(patientId);
        refreshAll();
        clearPatientForm();
    }

    private void returnSelectedPatientToWaitingRoom() {
        int row = patientTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a patient row first.", "Return patient", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String patientId = (String) patientModel.getValueAt(row, 0);
        String result = engine.returnPatientToWaitingRoom(patientId);
        JOptionPane.showMessageDialog(this, result, "Return patient", JOptionPane.INFORMATION_MESSAGE);
        refreshAll();
    }

    private void loadSelectedStaffRow() {
        int row = staffTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        staffIdField.setText((String) staffModel.getValueAt(row, 0));
        staffNameField.setText((String) staffModel.getValueAt(row, 1));
        staffRoleField.setText((String) staffModel.getValueAt(row, 2));
        staffSpecialtyField.setText((String) staffModel.getValueAt(row, 3));
    }

    private void loadSelectedPatientRow() {
        int row = patientTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        patientIdField.setText((String) patientModel.getValueAt(row, 0));
        patientNameField.setText((String) patientModel.getValueAt(row, 1));
        patientAgeField.setText(String.valueOf(patientModel.getValueAt(row, 2)));
        patientSeverityField.setText(String.valueOf(patientModel.getValueAt(row, 3)));
        patientStateField.setText((String) patientModel.getValueAt(row, 4));
        patientAssignedStaffField.setText((String) patientModel.getValueAt(row, 5));
    }

    private void clearStaffForm() {
        staffIdField.setText("");
        staffNameField.setText("");
        staffRoleField.setText("");
        staffSpecialtyField.setText("");
    }

    private void clearPatientForm() {
        patientIdField.setText("");
        patientNameField.setText("");
        patientAgeField.setText("");
        patientSeverityField.setText("");
        patientStateField.setText("");
        patientAssignedStaffField.setText("");
    }
}
