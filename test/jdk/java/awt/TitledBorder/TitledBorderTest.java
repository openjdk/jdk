import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
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
  public static JPanel parentPanel;
  public static JPanel childPanel;

  public static void main(String[] args) throws Exception {
    LookAndFeelInfo laf = UIManager.getInstalledLookAndFeels()[3];
    System.out.println(laf);
    SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
    SwingUtilities.invokeAndWait(() -> createAndShowGUI());

    BufferedImage buff = new BufferedImage(frame.getWidth(), frame.getHeight(),
            BufferedImage.TYPE_INT_ARGB);
    Graphics2D graph = buff.createGraphics();
    childPanel.paint(graph);
    graph.dispose();

    if (buff.getRGB(2,20) != -6250336) {
      saveImage(buff, "test.png");
      throw new RuntimeException("Border was clipped or overdrawn.");
    }

    frame.dispose();
  }

  private static void createAndShowGUI() {
    frame = new JFrame("Swing Test");

    parentPanel = new JPanel(new BorderLayout());
    parentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    childPanel = new JPanel(new BorderLayout());
    childPanel.setBorder(BorderFactory.createTitledBorder("Title"));
    childPanel.add(new JCheckBox(), BorderLayout.CENTER);

    parentPanel.add(childPanel, BorderLayout.CENTER);

    frame.getContentPane().add(parentPanel, BorderLayout.CENTER);

    frame.pack();
    frame.setLocationRelativeTo(null);
  }

  private static void saveImage(BufferedImage image, String filename) {
    try {
      ImageIO.write(image, "png", new File(filename));
    } catch (IOException e) {
      // Don’t propagate the exception
      e.printStackTrace();
    }
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