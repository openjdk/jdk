import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @key headful
 * @bug 8280913
 * @summary Check whether the default button is honoured when <Enter> key is
 * pressed when the focus is on the frame.
 * @run main DefaultButtonTest
 */
public class DefaultButtonTest {
    static JFrame frame = new JFrame();
    static volatile boolean buttonPressed = false;
    boolean testFailed = false;
    JButton button1;
    Robot robot;
    private JPanel panel;
    private JButton button2;

    public static void main(String[] s) throws Exception {
        DefaultButtonTest test = new DefaultButtonTest();
        try {
            test.runTest();
        } finally {
            if (frame != null) {
                frame.dispose();
                frame = null;
            }
        }

    }

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported L&F: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void createUI() {
//        frame = new JFrame();
        panel = new JPanel();
        panel.setLayout(new FlowLayout());
        button1 = new JButton("butt1");
        button1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                synchronized (this) {
                    buttonPressed = true;
                    notifyAll();
                }
            }
        });
        panel.add(button1);

        button2 = new JButton("butt2");
        panel.add(button2);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new FlowLayout());

        frame.add(panel);

        frame.setSize(200, 300);
        frame.setLocationRelativeTo(null);
        frame.getRootPane().setDefaultButton(button1);
        frame.setVisible(true);
    }

    public void runTest() throws InvocationTargetException, InterruptedException {
        try {
            robot = new Robot();
        } catch (Exception e) {
            System.err.print("Error creating robot");
            e.printStackTrace();
            System.exit(1);
        }
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            buttonPressed = false;
            System.out.println("Testing L&F: " + laf);
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        setLookAndFeel(laf);
                        createUI();
                        frame.getRootPane().requestFocus();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            robot.waitForIdle();
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.delay(100);
            robot.keyRelease(KeyEvent.VK_ENTER);
            robot.waitForIdle();

            if (buttonPressed) {
                System.out.println("Test Passed for laf " + laf);
            } else {
                testFailed = true;
                System.out.println("Test Failed, button not pressed for laf " + laf);
            }
            //frame.dispose();
           // frame = null;
        }
        if (testFailed) {
            throw new RuntimeException("Test Failed, button not pressed in one or more LAFs");
        } else {
            System.out.println("Test Passed for all supported LAFs ");
        }
    }

}

