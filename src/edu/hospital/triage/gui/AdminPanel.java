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
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Arrays;
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

        setSize(1100, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Staff Management", buildStaffTab());
        tabs.addTab("Patient Records", buildPatientTab());
        tabs.addTab("Reports & Analytics", buildAnalyticsTab());

        add(tabs, BorderLayout.CENTER);
        refreshAll();
    }

    private JPanel buildStaffTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.setBorder(BorderFactory.createTitledBorder("Staff record"));
        form.add(new JLabel("Staff ID:"));
        form.add(staffIdField);
        form.add(new JLabel("Name:"));
        form.add(staffNameField);
        form.add(new JLabel("Role:"));
        form.add(staffRoleField);
        form.add(new JLabel("Specialty:"));
        form.add(staffSpecialtyField);

        JPanel actions = new JPanel(new GridLayout(1, 4, 10, 10));
        JButton addBtn = new JButton("Add / Save");
        JButton selectBtn = new JButton("Load Selected");
        JButton deleteBtn = new JButton("Delete");
        JButton refreshBtn = new JButton("Refresh");

        addBtn.addActionListener(e -> saveStaff());
        selectBtn.addActionListener(e -> loadSelectedStaffRow());
        deleteBtn.addActionListener(e -> deleteSelectedStaff());
        refreshBtn.addActionListener(e -> refreshAll());

        actions.add(addBtn);
        actions.add(selectBtn);
        actions.add(deleteBtn);
        actions.add(refreshBtn);

        staffTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedStaffRow();
            }
        });

        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(staffTable), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPatientTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridLayout(6, 2, 8, 8));
        form.setBorder(BorderFactory.createTitledBorder("Patient record"));
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

        JPanel actions = new JPanel(new GridLayout(1, 4, 10, 10));
        JButton addBtn = new JButton("Add / Save");
        JButton selectBtn = new JButton("Load Selected");
        JButton deleteBtn = new JButton("Delete");
        JButton refreshBtn = new JButton("Refresh");

        addBtn.addActionListener(e -> savePatient());
        selectBtn.addActionListener(e -> loadSelectedPatientRow());
        deleteBtn.addActionListener(e -> deleteSelectedPatient());
        refreshBtn.addActionListener(e -> refreshAll());

        actions.add(addBtn);
        actions.add(selectBtn);
        actions.add(deleteBtn);
        actions.add(refreshBtn);

        patientTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedPatientRow();
            }
        });

        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(patientTable), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildAnalyticsTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Hospital analytics"));

        analyticsArea.setEditable(false);

        JButton refreshBtn = new JButton("Refresh analytics");
        refreshBtn.addActionListener(e -> refreshAll());

        JPanel top = new JPanel(new BorderLayout());
        top.add(refreshBtn, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(analyticsArea), BorderLayout.CENTER);
        return panel;
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
            String[] parts = row.split("\\|");
            if (parts.length >= 5) {
                staffModel.addRow(new Object[]{parts[0], parts[1], parts[2], parts[3], parts[4]});
            }
        }
    }

    private void loadPatientTable() {
        patientModel.setRowCount(0);
        List<String> rows = engine.getDb().getPatientRows();
        for (String row : rows) {
            String[] parts = row.split("\\|");
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
