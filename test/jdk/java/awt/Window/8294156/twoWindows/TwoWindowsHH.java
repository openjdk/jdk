import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.JFrame;
import javax.swing.JPanel;

import static java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment;
import static java.awt.Toolkit.getDefaultToolkit;

/*
 * @test
 * @bug 8294156
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Demonstrates adding and positioning two test windows horizontally
 * @run main/manual TwoWindowsHH
 */
public class TwoWindowsHH {
    public static final String INSTRUCTIONS = """
            A simple demo to position two test windows
            side by side horizontally or vertically.
            """;
    private static final Dimension SIZE = new Dimension(300, 200);

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(INSTRUCTIONS)
                      .rows(15)
                      .columns(30)
                      .testUI(TwoWindowsHH::createTestUI)
                      .positionTestUI(TwoWindowsHH::positionTestUI)
                      .build()
                      .awaitAndCheck();
    }

    public static final int GAP = 8;

    public static Point getScreenCenter() {
        GraphicsConfiguration gc = getLocalGraphicsEnvironment()
                                   .getDefaultScreenDevice()
                                   .getDefaultConfiguration();
        Dimension size = gc.getBounds()
                           .getSize();
        Insets insets = getDefaultToolkit()
                        .getScreenInsets(gc);

        return new Point((size.width - insets.left - insets.right) / 2,
                         (size.height - insets.top - insets.bottom) / 2);
    }

    public static void positionTestUI(List<? extends Window> windows,
                                      PassFailJFrame.InstructionUI instructionUI) {
        final Point center = getScreenCenter();

        int x;
        switch (instructionUI.getPosition()) {
            case HORIZONTAL:
                x = instructionUI.getLocation().x + instructionUI.getSize().width + GAP;
                for (Window w : windows) {
                    w.setLocation(x, center.y - w.getHeight() / 2);
                    x += w.getWidth() + GAP;
                }
                break;

            case VERTICAL:
                final int width = windows.stream()
                                         .mapToInt(Component::getWidth)
                                         .sum()
                                  + GAP;
                final int y = instructionUI.getLocation().y
                              + instructionUI.getSize().height + GAP;
                x = center.x - width / 2;
                for (Window w : windows) {
                    w.setLocation(x, y);
                    x += w.getWidth() + GAP;
                }
                break;

            default:
                throw new IllegalStateException("Unexpected position value: "
                                                + instructionUI.getPosition());
        }
    }

    public static List<? extends Window> createTestUI() {
        return Stream.of(new Color(240, 200, 240),
                         new Color(200, 240, 200))
                     .map(TwoWindowsHH::createFrame)
                     .toList();
    }

    private static int counter;

    private static JFrame createFrame(Color color) {
        JFrame frame = new JFrame("Test window " + (++counter));
        frame.add(createPanel(color));
        frame.pack();
        return frame;
    }

    private static JPanel createPanel(Color color) {
        JPanel panel = new JPanel();
        panel.setPreferredSize(SIZE);
        panel.setBackground(color);
        return panel;
    }
}
