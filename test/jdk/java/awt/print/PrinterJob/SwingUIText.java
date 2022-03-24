/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 6488219 6560738 7158350 8017469
 * @summary Test that text printed in Swing UI measures and looks OK.
 * @run main/manual SwingUIText
 */

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class SwingUIText implements Printable {

    private static JFrame frame;
    private static JFrame testInstructionFrame;
    private static final CountDownLatch resultCountDownLatch =
            new CountDownLatch(1);
    private static volatile boolean testResult = false;
    private static volatile String failureReason;

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            createTestInstructionUI();
            createSwingTestUI();
        });

        // Giving max 10 minutes since sometime printer has to be ready to
        // print and user has to compare the UI on screen and the print out.
        if (!resultCountDownLatch.await(10, TimeUnit.MINUTES)) {
            throw new RuntimeException("Timeout: User did not press either " +
                    "Pass or Fail button");
        }

        if (!testResult) {
            dispose();
            throw new RuntimeException("Test Failed: " + failureReason);
        } else {
            System.out.println("Test Passed.");
        }
    }

    private static void dispose() throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            if (frame != null) {
                frame.dispose();
            }
            if (testInstructionFrame != null) {
                testInstructionFrame.dispose();
            }
        });
    }

    private static void createTestInstructionUI() {
        testInstructionFrame = new JFrame("Test Instruction Frame");
        final String INSTRUCTION = """
                Note: This tests that when a Swing UI is printed, that the text,
                in each component properly matches the length of the component,
                as seen on-screen, and that the spacing of the text is of,
                reasonable even-ness. This latter part is very subjective and,
                the comparison has to be with JDK1.5 GA, or JDK 1.6 GA.
                Steps:
                1) You should see two JFrame & a Print Dialog
                a) First JFrame with title "Test Instruction Frame", which is the instruction frame.
                b) Second JFrame with title "Swing UI Text Printing Test". which contains components
                with different texts that needs to be compared with the printout.
                2) Click "Print" or OK button on the Print dialog to the print content of "Swing UI
                Text Printing Test" JFrame.
                3) Compare printout with content of "Swing UI Text Printing Test" JFrame.
                If they match then press "Pass" button else press "Fail" button.
                """;
        JTextArea instructionTextArea = new JTextArea(INSTRUCTION, 16, 45);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(instructionTextArea), BorderLayout.CENTER);

        JPanel ctrlPanel = new JPanel();
        JButton passBtn = new JButton("Pass");
        passBtn.addActionListener((ae) -> {
            testResult = true;
            resultCountDownLatch.countDown();
            frame.dispose();
            testInstructionFrame.dispose();
        });

        JButton failBtn = new JButton("Fail");
        failBtn.addActionListener((ae) -> {
            getFailureReason();
            frame.dispose();
            testInstructionFrame.dispose();
        });

        ctrlPanel.add(passBtn);
        ctrlPanel.add(failBtn);

        panel.add(ctrlPanel, BorderLayout.SOUTH);
        testInstructionFrame.add(panel);
        testInstructionFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        testInstructionFrame.pack();
        testInstructionFrame.setVisible(true);
    }

    public static void createSwingTestUI() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(4, 1));

        String text = "marvelous suspicious solving";
        displayText(panel, text);

        String itext = "\u0641\u0642\u0643 \u0644\u0627\u064b";
        StringBuilder iTextBuilder = new StringBuilder(itext);
        displayText(panel,
                iTextBuilder.append(itext).append(itext).append(itext).append(itext).append(itext).append(itext).append(itext).toString());

        String itext2 = "\u0641" + text;
        displayText(panel, itext2);

        JEditorPane editor = new JEditorPane();
        editor.setContentType("text/html");
        String CELL = "<TD align=\"center\"><font style=\"font-size: 18;\">Text</font></TD>";
        String TABLE_BEGIN = "<TABLE BORDER=1 cellpadding=1 cellspacing=0 width=100%>";
        String TABLE_END = "</TABLE>";
        StringBuilder buffer = new StringBuilder();
        buffer.append("<html><body>").append(TABLE_BEGIN);
        for (int j = 0; j < 15; j++) {
            buffer.append(CELL);
        }
        buffer.append("</tr>");
        buffer.append(TABLE_END).append("</body></html>");
        editor.setText(buffer.toString());

        panel.add(editor);

        frame = new JFrame("Swing UI Text Printing Test");
        frame.getContentPane().add(panel);
        frame.setLocationRelativeTo(null);
        frame.pack();
        frame.setVisible(true);

        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat pf = job.defaultPage();
        job.setPrintable(new SwingUIText(), pf);
        if (job.printDialog()) {
            try {
                job.print();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    static void displayText(JPanel p, String text) {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(2, 1));
        JPanel row = new JPanel();
        Font font = new Font("Dialog", Font.PLAIN, 12);

        JLabel label = new JLabel(text);
        label.setFont(font);
        row.add(label);

        JButton button = new JButton("Print " + text);
        button.setMnemonic('P');
        button.setFont(font);
        row.add(button);

        panel.add(row);

        row = new JPanel();
        JTextField textField = new JTextField(text);
        row.add(textField);

        JTextArea textArea = new JTextArea();
        textArea.setText(text);
        row.add(textArea);

        panel.add(row);
        p.add(panel);
    }

    public static void getFailureReason() {
        final JDialog dialog = new JDialog();
        dialog.setTitle("Read testcase failure reason");
        JPanel jPanel = new JPanel(new BorderLayout());
        JTextArea jTextArea = new JTextArea(5, 20);

        JButton okButton = new JButton("Ok");
        okButton.addActionListener((ae) -> {
            failureReason = jTextArea.getText();
            testResult = false;
            resultCountDownLatch.countDown();
            dialog.dispose();
        });

        jPanel.add(new JLabel("Enter the testcase failed reason below and " +
                "click OK button", JLabel.CENTER), BorderLayout.NORTH);
        jPanel.add(jTextArea, BorderLayout.CENTER);

        JPanel okayBtnPanel = new JPanel();
        okayBtnPanel.add(okButton);

        jPanel.add(okayBtnPanel, BorderLayout.SOUTH);
        dialog.add(jPanel);
        dialog.setLocationRelativeTo(null);
        dialog.pack();
        dialog.setVisible(true);
    }

    @Override
    public int print(Graphics g, PageFormat pf, int pageIndex)
            throws PrinterException {
        if (pageIndex >= 1) {
            return Printable.NO_SUCH_PAGE;
        }
        g.translate((int) pf.getImageableX(), (int) pf.getImageableY());
        frame.printAll(g);
        return Printable.PAGE_EXISTS;
    }
}

