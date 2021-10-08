package jdk.test.lib.manual;

import java.awt.GridLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;

public class StatusBar extends JPanel {

	private static final long serialVersionUID = 1L;

	private static StatusBar sBar = new StatusBar();

	private JLabel lblStatus;

	private StatusBar() {
		setLayout(new GridLayout());
		lblStatus = new JLabel();
		lblStatus.setHorizontalAlignment(JLabel.LEFT);
		add(lblStatus);
	}

	public static StatusBar getStatusBar() {
		return sBar;
	}

	public void setStatus(String msg) {
		lblStatus.setText(msg);
	}
}
