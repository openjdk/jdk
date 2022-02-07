import java.awt.ComponentOrientation;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;


import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4515999
 * @summary Check whether incrementing dates via the keyboard (up/down) gives
 * the same results as using mouse press on the arrow buttons in a JSpinner.
 * @run main JSpinnerMouseAndKeyPressTest
 */
public class JSpinnerMouseAndKeyPressTest {
    //2 days in milliseconds
    private static final int EXPECTED_VALUE_2_DAYS = 2 * 24 * 60 * 60 * 1000;
    private static JFrame frame;
    private static JSpinner spinner;

    public static void main(String[] s) throws Exception {
        runTest();
    }

    private static void setLookAndFeel(final String laf) {
        try {
            UIManager.setLookAndFeel(laf);
            System.out.println("LookAndFeel: " + laf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void createUI() {
        frame = new JFrame();
        JPanel panel = new JPanel();
        spinner = new JSpinner();
        spinner.setModel(new DateModel());
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "dd/MM/yy");
        spinner.setEditor(editor);
        spinner.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        panel.add(spinner);
        frame.add(panel);
        frame.setUndecorated(true);
        frame.pack();
        frame.setAlwaysOnTop(true);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }


    public static void runTest() throws Exception {
        Robot robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(100);
        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                                  .map(UIManager.LookAndFeelInfo::getClassName)
                                  .collect(Collectors.toList());
        for (final String laf : lafs) {
            SwingUtilities.invokeAndWait(() -> {
                setLookAndFeel(laf);
                createUI();
            });
            try {
                final Point spinnerLocationOnScreen = spinner.getLocationOnScreen();
                final int spinnerEditorWidth = spinner.getEditor().getWidth();
                final int spinnerButtonWidth = spinner.getWidth() -
                        spinnerEditorWidth;
                final int spinnerQuarterHeight = spinner.getHeight() / 4;

                Point spinnerUpButtonCenter = new Point();
                spinnerUpButtonCenter.x = spinnerLocationOnScreen.x + spinnerEditorWidth +
                        (spinnerButtonWidth / 2);
                spinnerUpButtonCenter.y = spinnerLocationOnScreen.y +
                        spinnerQuarterHeight;

                Point spinnerDownButtonCenter = new Point();
                spinnerDownButtonCenter.x = spinnerUpButtonCenter.x;
                spinnerDownButtonCenter.y = spinnerLocationOnScreen.y +
                        3 * spinnerQuarterHeight;

                //Mouse press use-case
                // Move Mouse pointer to UP button center and click it
                robot.mouseMove(spinnerUpButtonCenter.x, spinnerUpButtonCenter.y);
                robot.mousePress(InputEvent.BUTTON1_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_MASK);

                long upValue = ((Date) spinner.getValue()).getTime();

                // Move Mouse pointer to DOWN button center and click it
                robot.mouseMove(spinnerDownButtonCenter.x, spinnerDownButtonCenter.y);
                robot.mousePress(InputEvent.BUTTON1_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_MASK);

                long downValue = ((Date) spinner.getValue()).getTime();

                long mouseIncrement = upValue - downValue;

                //Key press use-case
                //Up Key press
                robot.keyPress(KeyEvent.VK_UP);
                robot.keyRelease(KeyEvent.VK_UP);

                upValue = ((Date) spinner.getValue()).getTime();

                //Down Key press
                robot.keyPress(KeyEvent.VK_DOWN);
                robot.keyRelease(KeyEvent.VK_DOWN);

                downValue = ((Date) spinner.getValue()).getTime();

                long keyIncrement = upValue - downValue;

                if ((keyIncrement == EXPECTED_VALUE_2_DAYS) &&
                        (mouseIncrement == EXPECTED_VALUE_2_DAYS)) {
                    System.out.println("Test passed");
                } else {
                    throw new RuntimeException("Test failed because keyIncrement: " +
                            keyIncrement + " and mouseIncrement: " +
                            mouseIncrement + " should match with the expected value " +
                            EXPECTED_VALUE_2_DAYS + " for LnF " + laf);
                }

            } finally {
                SwingUtilities.invokeAndWait(JSpinnerMouseAndKeyPressTest::disposeFrame);
            }
        }
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    private static class DateModel extends SpinnerDateModel {

        private final Calendar cal = Calendar.getInstance();

        @Override
        public Object getNextValue() {
            cal.setTime(getDate());
            cal.add(Calendar.DAY_OF_MONTH, 2); // Increment two days
            return cal.getTime();
        }

        @Override
        public Object getPreviousValue() {
            cal.setTime(getDate());
            cal.add(Calendar.DAY_OF_MONTH, -2); // Decrement two days
            return cal.getTime();
        }
    }
}
