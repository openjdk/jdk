
import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.awt.EventQueue;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.awt.event.ActionEvent;
import javax.swing.SwingUtilities;
import javax.swing.KeyStroke;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

/*
 * @test
 * @key headful
 * @bug 8296275
 * @summary To verify the setAccelerator method of JMenuItem.
 * @requires (os.family=="mac")
 * @run main JMenuItemSetAcceleratorTest
 */

public class JMenuItemSetAcceleratorTest {
    private static JFrame frame = new JFrame();
    private volatile static CountDownLatch actionPerformLatch;
    private static Robot robot;

    private static void createAndShow() {
        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem menuItem = new JMenuItem("Menu Item");
        menuItem.setAccelerator(
            KeyStroke.getKeyStroke(KeyEvent.VK_M, ActionEvent.META_MASK));
        menuItem.addActionListener(e -> {
            System.out.println("menu item action.");
            actionPerformLatch.countDown();
        });
        menu.add(menuItem);
        bar.add(menu);

        frame.setJMenuBar(bar);
        frame.setBounds(200, 200, 200, 200);
        frame.setVisible(true);
    }

    public static void main(String args[]) throws Exception {
        try {
            if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Action.APP_MENU_BAR)) {
                System.out.println(
                    "Test passed as Desktop or Action.APP_MENU_BAR is not supported.");
                return;
            }

            actionPerformLatch = new CountDownLatch(1);
            SwingUtilities
                .invokeAndWait(JMenuItemSetAcceleratorTest::createAndShow);

            robot = new Robot();
            robot.setAutoDelay(50);
            robot.setAutoWaitForIdle(true);

            robot.keyPress(KeyEvent.VK_META);
            robot.keyPress(KeyEvent.VK_M);
            robot.keyRelease(KeyEvent.VK_M);
            robot.keyRelease(KeyEvent.VK_META);

            if (!actionPerformLatch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException(
                    "Hasn't received the JMenuItem action event by pressing "
                        + "accelerator keys, test fails.");
            }
            System.out
                .println("Test passed, received action event on menu item.");
        } finally {
            EventQueue.invokeAndWait(JMenuItemSetAcceleratorTest::disposeFrame);
        }
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }
}
