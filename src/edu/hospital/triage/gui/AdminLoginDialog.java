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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
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

        setLayout(new BorderLayout(18, 18));
        setSize(new Dimension(440, 300));
        setLocationRelativeTo(null);
        setResizable(false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(new Color(9, 14, 25));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(24, 18, 0, 18));

        JLabel title = new JLabel("Hospital Admin");
        title.setHorizontalAlignment(JLabel.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        title.setForeground(new Color(241, 245, 249));

        JLabel subtitle = new JLabel("Secure access portal");
        subtitle.setHorizontalAlignment(JLabel.CENTER);
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        subtitle.setForeground(new Color(148, 163, 184));

        header.add(title, BorderLayout.CENTER);
        header.add(subtitle, BorderLayout.SOUTH);

        JPanel form = new JPanel(new GridLayout(2, 2, 12, 12));
        form.setOpaque(false);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(71, 85, 105)), "Administrator Login"),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JLabel usernameLabel = new JLabel("Username");
        usernameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        usernameLabel.setForeground(new Color(226, 232, 240));
        JLabel passwordLabel = new JLabel("Password");
        passwordLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        passwordLabel.setForeground(new Color(226, 232, 240));

        usernameField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        passwordField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        styleInput(usernameField);
        styleInput(passwordField);

        form.add(usernameLabel);
        form.add(usernameField);
        form.add(passwordLabel);
        form.add(passwordField);

        JButton loginBtn = new JButton("Login");
        JButton cancelBtn = new JButton("Cancel");
        styleButton(loginBtn, new Color(37, 99, 235), Color.WHITE);
        styleButton(cancelBtn, new Color(255, 255, 255), new Color(15, 23, 42));

        loginBtn.addActionListener(e -> doLogin());
        cancelBtn.addActionListener(e -> dispose());

        JPanel actions = new JPanel(new GridLayout(1, 2, 12, 0));
        actions.setOpaque(false);
        actions.add(loginBtn);
        actions.add(cancelBtn);

        add(header, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        usernameField.setText("admin");
    }

    private void styleButton(JButton button, Color bg, Color fg) {
        button.setFocusPainted(false);
        button.setBackground(bg);
        button.setForeground(fg);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker()),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));
    }

    private void styleInput(javax.swing.text.JTextComponent field) {
        field.setBackground(new Color(15, 23, 42));
        field.setForeground(new Color(241, 245, 249));
        field.setCaretColor(new Color(148, 163, 184));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(71, 85, 105)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
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
