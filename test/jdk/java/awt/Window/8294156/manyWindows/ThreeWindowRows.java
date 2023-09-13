import java.awt.Window;
import java.util.List;

/*
 * @test
 * @bug 8294156
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Demonstrates adding and positioning several test windows
 *          vertically in multiple rows
 * @run main/manual ThreeWindowRows
 */
public class ThreeWindowRows {
    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(TwoWindowColumnsH.INSTRUCTIONS)
                      .rows(5)
                      .columns(30)
                      .testUI(ThreeWindowRows::createTestUI)
                      .positionTestUI(TwoWindowColumnsH::positionTestUI)
                      .position(PassFailJFrame.Position.VERTICAL)
                      .build()
                      .awaitAndCheck();
    }

    private static List<? extends Window> createTestUI() {
        return TwoWindowColumnsH.createTestUI(8);
    }
}
