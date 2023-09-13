import java.awt.Component;
import java.awt.Point;
import java.awt.Window;
import java.util.List;

/*
 * @test
 * @bug 8294156
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Demonstrates adding and positioning two test windows horizontally
 * @run main/manual TwoWindowsHV
 */
public class TwoWindowsHV {
    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(TwoWindowsHH.INSTRUCTIONS)
                      .rows(15)
                      .columns(30)
                      .testUI(TwoWindowsHH::createTestUI)
                      .positionTestUI(TwoWindowsHV::positionTestUI)
                      .build()
                      .awaitAndCheck();
    }

    public static void positionTestUI(List<? extends Window> windows,
                                       PassFailJFrame.InstructionUI instructionUI) {
        final Point center = TwoWindowsHH.getScreenCenter();

        int y;
        switch (instructionUI.getPosition()) {
            case HORIZONTAL:
                final int height = windows.stream()
                                          .mapToInt(Component::getHeight)
                                          .sum()
                                   + TwoWindowsHH.GAP;
                final int x = instructionUI.getLocation().x
                              + instructionUI.getSize().width
                              + TwoWindowsHH.GAP;

                y = center.y - height / 2;
                for (Window w : windows) {
                    w.setLocation(x, y);
                    y += w.getHeight() + TwoWindowsHH.GAP;
                }
                break;

            case VERTICAL:
                y = instructionUI.getLocation().y
                    + instructionUI.getSize().height
                    + TwoWindowsHH.GAP;
                for (Window w : windows) {
                    w.setLocation(center.x - w.getWidth() / 2, y);
                    y += w.getHeight() + TwoWindowsHH.GAP;
                }
                break;

            default:
                throw new IllegalStateException("Unexpected position value: "
                                                + instructionUI.getPosition());
        }
    }
}
