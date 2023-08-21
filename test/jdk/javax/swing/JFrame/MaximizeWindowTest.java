import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Robot;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @key headful
 * @summary setExtendedFrame not executed immediately
 * @run main MaximizeWindowTest
 */
@SuppressWarnings("serial")
public class MaximizeWindowTest extends JFrame
{
    public static void main(String[] arguments) throws Exception {
        Robot robot = new Robot();

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                JSplitPane splitPane = new JSplitPane();

                JFrame frame = new JFrame();
                frame.setDefaultCloseOperation(EXIT_ON_CLOSE);
                frame.setSize(200, 200);
                frame.setLocation(400, 400);
                frame.add(splitPane);
                frame.setExtendedState(MAXIMIZED_BOTH);

                frame.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        System.out.println("Component size: " + e.getComponent().getSize());
                        if (e.getComponent().getSize().equals(new Dimension(200, 200))) {
                            throw new RuntimeException("Test Failed! " +
                                    "Frame was visible at original size before maximizing");
                        }
                    }
                });

                splitPane.setDividerLocation(1000);

                frame.setVisible(true);

                robot.delay(1000);
            }
        });
    }
}
