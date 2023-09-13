/*
 * @test
 * @bug 8294156
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Demonstrates adding and positioning two test windows vertically
 * @run main/manual TwoWindowsVH
 */
public class TwoWindowsVH {
    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(TwoWindowsHH.INSTRUCTIONS)
                      .rows(15)
                      .columns(30)
                      .testUI(TwoWindowsHH::createTestUI)
                      .positionTestUI(TwoWindowsHH::positionTestUI)
                      .position(PassFailJFrame.Position.VERTICAL)
                      .build()
                      .awaitAndCheck();
    }
}
