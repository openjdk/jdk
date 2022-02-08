import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.Robot;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 8279614
 * @summary The left line of the TitledBorder is not painted on 150 scale factor
 * @requires (os.family == "windows")
 * @run main TitledBorderTest
 */
public class TitledBorderTest {

  public static JFrame frame;

  public static void main(String[] args) throws Exception {
    LookAndFeelInfo laf = UIManager.getInstalledLookAndFeels()[3];
    System.out.println(laf);
    SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));

    createAndShowGUI();

    Robot robot = new Robot();
    robot.waitForIdle();

    Point loc = frame.getLocationOnScreen();
    robot.mouseMove(loc.x + 10, loc.y + 50);

    while(frame.isVisible()) {}
  }

  private static void test() throws Exception {
    
  }

  private static void createAndShowGUI() throws Exception {
    SwingUtilities.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        frame = new JFrame("Swing Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        JPanel parentPanel = new JPanel(new BorderLayout());
        parentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel childPanel = new JPanel(new BorderLayout());
        childPanel.setBorder(BorderFactory.createTitledBorder("Title"));
        childPanel.add(new JCheckBox(), BorderLayout.CENTER);

        parentPanel.add(childPanel, BorderLayout.CENTER);

        frame.getContentPane().add(parentPanel, BorderLayout.CENTER);

        frame.pack();
        frame.setLocationRelativeTo(null);
      }});
  }

  private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
    try {
      UIManager.setLookAndFeel(laf.getClassName());
      System.out.println(laf.getName());
    } catch (UnsupportedLookAndFeelException ignored){
      System.out.println("Unsupported LookAndFeel: " + laf.getClassName());
    } catch (ClassNotFoundException | InstantiationException |
            IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
} 