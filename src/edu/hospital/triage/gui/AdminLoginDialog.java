package edu.hospital.triage.gui;

import edu.hospital.triage.db.DatabaseManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;

public class AdminLoginDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final DatabaseManager db;
    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private boolean authenticated = false;

    public AdminLoginDialog(DatabaseManager db) {
        super((JDialog) null, "Admin Access", true);
        this.db = db;

        setLayout(new BorderLayout(12, 12));
        setSize(new Dimension(420, 220));
        setLocationRelativeTo(null);
        setResizable(false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel form = new JPanel(new GridLayout(2, 2, 10, 10));
        form.setBorder(BorderFactory.createTitledBorder("Secure Administrator Login"));
        form.add(new JLabel("Username:"));
        form.add(usernameField);
        form.add(new JLabel("Password:"));
        form.add(passwordField);

        JButton loginBtn = new JButton("Login");
        JButton cancelBtn = new JButton("Cancel");

        loginBtn.addActionListener(e -> doLogin());
        cancelBtn.addActionListener(e -> dispose());

        JPanel actions = new JPanel(new GridLayout(1, 2, 10, 10));
        actions.add(loginBtn);
        actions.add(cancelBtn);

        add(form, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        usernameField.setText("admin");
    }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (db.validateAdminLogin(username, password)) {
            authenticated = true;
            dispose();
            return;
        }

        JOptionPane.showMessageDialog(this,
                "Invalid username or password.",
                "Authentication failed",
                JOptionPane.ERROR_MESSAGE);
        passwordField.setText("");
    }

    public boolean authenticate() {
        setVisible(true);
        return authenticated;
    }
}
