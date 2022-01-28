import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Color;
import java.awt.Robot;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.SwingConstants;
import javax.swing.UnsupportedLookAndFeelException;

/*
 * @test
 * @bug 8015854
 * @summary JButton text set as html image had additional unwanted padding
 * @run main Bug8015854
 */
public final class Bug8015854 {
    private static JFrame frame;
    private static JButton button;
    private static Point point;

    public static final int BUTTON_HEIGHT = 37;
    public static final int BUTTON_WIDTH = 37;
    public static final int SQUARE_HEIGHT = 19;
    public static final int SQUARE_WIDTH = 19;

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Unsupported LookAndFeel: " + e);
        }

        Robot robot = new Robot();
        robot.setAutoDelay(2000);
        robot.setAutoWaitForIdle(true);

        SwingUtilities.invokeAndWait(() -> {
            createAndShowGUI();
            setupCenterCoord();
        });

        robot.mouseMove(point.x, point.y);

        Color leftClr = robot.getPixelColor(point.x - (SQUARE_WIDTH/2), point.y);
        Color rightClr = robot.getPixelColor(point.x + (SQUARE_WIDTH/2) - 1, point.y);
        Color topClr = robot.getPixelColor(point.x, point.y - (SQUARE_HEIGHT/2));
        Color botClr = robot.getPixelColor(point.x, point.y + (SQUARE_HEIGHT/2) - 1);

        if(!leftClr.equals(Color.RED) || !rightClr.equals(Color.RED)
                || !topClr.equals(Color.RED) || !botClr.equals(Color.RED)) {
            throw new RuntimeException("HTML image not centered in button");
        }
        else {
            System.out.println("Test passed");
        }

    }

    private static void createAndShowGUI()
    {
        frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new FlowLayout());

        // create JButton of size 37x37 text set to a 19x19 image of a red square loaded through html tags
        button = new JButton();
        button.setText("<html><img src='file:red_square.png'></html>");
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));

        frame.add(button);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void setupCenterCoord() {
        point = button.getLocationOnScreen();

        // offset to get correct coordinates for button
        point.x += 16;

        point.x += BUTTON_WIDTH / 2;
        point.y += BUTTON_HEIGHT / 2;
    }
}