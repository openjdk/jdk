/* @test
   @bug 5047379
   @summary Checks that tooltips are rendered properly
   @author Shannon Hickey
   @library ../../regtesthelpers
   @run main/manual bug5047379
*/
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import java.util.concurrent.atomic.AtomicBoolean;

public class bug5047379 {
    private static final AtomicBoolean testCompleted = new AtomicBoolean(false);
    private static final long TIMEOUT =  5 * 60 * 1000;
    static JFrame frame;

    public static void main(String[] args) throws Throwable {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                runTest();
            }
        });
        waitForCompleting();
    }

    private static void runTest() {
        frame = new JFrame();
        JTextArea area = new JTextArea();
        JPanel p = new JPanel();
        JPanel resPanel = new JPanel(new FlowLayout());

        String text  = "Place your mouse over each button (A, B, C, D) in sequence\n";
        text += "and wait for the tooltip to appear. Here is what should\n";
        text += "be shown for each button:\n";
        text += "\n";
        text += "    A: The word \"TEXT\"\n";
        text += "    B: The word \"TEXT\" and then in a different size/color, \"CTRL B\"\n";
        text += "    C: The word \"TEXT\"\n";
        text += "    D: The word \"TEXT\" and then in a different size/color, \"CTRL D\"\n";
        text += "\n";
        text += "If this is the case, hit PASS. If you see anything else,\n";
        text += "including extra space to the right (as if the tooltip is too large\n";
        text += "or something is missing), hit FAIL.";

        area.setText(text);
        area.setEditable(false);
        area.setFocusable(false);
        frame.add(area, BorderLayout.CENTER);

        p.setLayout(new GridLayout(1, 5));

        JButton a = new JButton("A");
        a.setMnemonic(java.awt.event.KeyEvent.VK_A);
        a.setToolTipText("TEXT");
        p.add(a);

        JButton b = new JButton("B");
        b.setMnemonic(java.awt.event.KeyEvent.VK_B);
        b.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl B"), "foo");
        b.setToolTipText("TEXT");
        p.add(b);

        JButton c = new JButton("C");
        c.setMnemonic(java.awt.event.KeyEvent.VK_C);
        c.setToolTipText("<html>TEXT");
        p.add(c);

        JButton d = new JButton("D");
        d.setMnemonic(java.awt.event.KeyEvent.VK_D);
        d.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl D"), "foo");
        d.setToolTipText("<html>TEXT");
        p.add(d);

        final JButton passButton = new JButton("PASS");
        passButton.setEnabled(true);
        passButton.addActionListener((e) -> {
            frame.dispose();
            testCompleted.set(true);
        });
        final JButton failButton = new JButton("FAIL");
        failButton.setEnabled(true);
        failButton.addActionListener((e) -> {
            frame.dispose();
            testCompleted.set(true);
            throw new RuntimeException("Test Case Failed");
        });

        resPanel.add(passButton);
        resPanel.add(failButton);
        frame.add(p, BorderLayout.NORTH);
        frame.add(resPanel,BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    private static void waitForCompleting() throws Exception {

        synchronized (testCompleted) {
            long startTime = System.currentTimeMillis();
            while (!testCompleted.get()) {
                testCompleted.wait(TIMEOUT);
                if (System.currentTimeMillis() - startTime >= TIMEOUT) {
                    frame.dispose();
                    throw new RuntimeException("Test Case Failed");
                }
            }
        }
    }
}
