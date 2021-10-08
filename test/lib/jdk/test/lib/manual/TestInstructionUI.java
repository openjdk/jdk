package jdk.test.lib.manual;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.TextArea;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.net.URL;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Timer;

public class TestInstructionUI extends JFrame implements WindowListener, FocusListener {

	private static final long serialVersionUID = 1L;
	private JEditorPane instructionsText;
	private TextArea messageText;
	private int maxStringLength = 80;

	private JButton btnPass = new JButton("Pass");
	private JButton btnFail = new JButton("Fail");
	private JButton btnCap = new JButton("Capture & Close");
	private JTextField txtTime = new JTextField("0", 5);

	private AbstractManualTest testClass;
	private StatusBar sBar = StatusBar.getStatusBar();
	private Timer timer;
	private int count = 0;

	public TestInstructionUI(String name, AbstractManualTest testClass) {
		super(name);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.testClass = testClass;

		createInstructionDialogUI();
		addWindowListener(this);
		btnPass.addFocusListener(this);
		btnFail.addFocusListener(this);
		btnCap.addFocusListener(this);
		messageText.addFocusListener(this);
		setPreferredSize(new Dimension(475, 350));
		pack();
		btnPass.requestFocusInWindow();
		setVisible(true);
	}

	private void createInstructionDialogUI() {
		JPanel btnPanel = new JPanel();
		btnCap.setEnabled(false);
		txtTime.addFocusListener(this);
		txtTime.setEnabled(false);

		int scrollBoth = TextArea.SCROLLBARS_BOTH;
		instructionsText = new JEditorPane();
		instructionsText.setFocusable(false);

		messageText = new TextArea("Enter failure details.", 5, maxStringLength, scrollBoth);
		messageText.setEnabled(false);
		add("Center", messageText);

		btnPanel.add(btnPass);
		btnPanel.add(btnFail);
		btnPanel.add(txtTime);
		btnPanel.add(btnCap);

		btnPass.addActionListener((e) -> {
			testClass.pass();
			testClass.closeSwingSetDemo();
			testClass.saveAndClose();
		});

		btnFail.addActionListener((e) -> {
			testClass.fail();
			btnPass.setEnabled(false);
			btnFail.setEnabled(false);
			btnCap.setEnabled(false);
			messageText.setEnabled(true);
			messageText.requestFocus();
			messageText.selectAll();
		});

		messageText.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_TAB) {
					e.consume();
					txtTime.requestFocus();
				}
			}
		});

		ActionListener capListener = (e -> {
			btnPass.setEnabled(false);
			btnFail.setEnabled(false);
			btnCap.setEnabled(false);
			txtTime.setEnabled(false);
			sBar.setStatus(count + " second(s) left to take screenshot.");
			if (count == 0) {
				setState(JFrame.ICONIFIED);
				testClass.comment(messageText.getText());
				btnCap.setText("Capturing...");
				testClass.captureScreenShot();
				btnCap.setText("Captured");
				btnCap.setEnabled(true);
				timer.stop();
				btnCap.setText("Closing...");
				testClass.closeSwingSetDemo();
				testClass.saveAndClose();
			}
			count--;
		});

		timer = new Timer(1000, capListener);
		timer.setRepeats(true);

		btnCap.addActionListener((e) -> {
			messageText.setEnabled(false);
			timer.start();
			try {
				count = Integer.valueOf(txtTime.getText());
			} catch (NumberFormatException ex) {
				System.out.println(ex.getMessage() + " Incorrect time entered.");
				count = 0;
			}
		});

		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbsBtn = new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER,
				GridBagConstraints.HORIZONTAL, new Insets(0, 5, 0, 5), 0, 0);
		panel.add(btnPanel, gbsBtn);

		GridBagConstraints gbsStatusBar = new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START,
				GridBagConstraints.HORIZONTAL, new Insets(0, 5, 0, 5), 0, 0);
		panel.add(sBar, gbsStatusBar);
		add("South", panel);
	}

	public void printInstructions(URL url) throws IOException {
		instructionsText.setContentType("text/html");
		instructionsText.setPage(url);
		instructionsText.setEditable(false);
		JScrollPane esp = new JScrollPane(instructionsText);
		esp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		esp.setPreferredSize(new Dimension(250, 150));
		esp.setMinimumSize(new Dimension(10, 10));
		add(BorderLayout.NORTH, esp);
	}

	public void displayMessage(String messageIn) {
		messageText.append(messageIn + "\n");
	}

	@Override
	public void windowClosing(WindowEvent e) {
		testClass.comment(messageText.getText());
		testClass.closeSwingSetDemo();
		testClass.saveAndClose();
	}

	@Override
	public void windowOpened(WindowEvent e) {
	}

	@Override
	public void windowClosed(WindowEvent e) {
	}

	@Override
	public void windowIconified(WindowEvent e) {
	}

	@Override
	public void windowDeiconified(WindowEvent e) {
	}

	@Override
	public void windowActivated(WindowEvent e) {
	}

	@Override
	public void windowDeactivated(WindowEvent e) {
	}

	@Override
	public void focusGained(FocusEvent e) {
		if (e.getSource().equals(btnPass)) {
			btnCap.setEnabled(false);
			sBar.setStatus("Press SPACE to mark the test as passed and close all windows or press TAB.");
		} else if (e.getSource().equals(btnCap)) {
			sBar.setStatus("Press SPACE to capture screen shot and close the windows. ");
		} else if (e.getSource().equals(btnFail)) {
			sBar.setStatus("Press SPACE to enter comments.");
		} else if (e.getSource().equals(messageText)) {
			btnCap.setEnabled(false);
			btnFail.setEnabled(false);
			sBar.setStatus("Enter the comments to save and Press TAB.");
		} else if (e.getSource().equals(txtTime)) {
			sBar.setStatus("Enter wait time in second(s) and Press TAB.");
		}
	}

	@Override
	public void focusLost(FocusEvent e) {
		if (e.getSource().equals(messageText)) {
			txtTime.setEnabled(true);
			btnCap.setEnabled(true);
			txtTime.selectAll();
			sBar.setStatus("Press TAB to capture the screen shot and close the windows.");
		} else if (e.getSource().equals(txtTime)) {
			btnCap.setEnabled(true);
		}
	}
}