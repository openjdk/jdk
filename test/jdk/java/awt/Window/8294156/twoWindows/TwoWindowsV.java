/*
 * @test
 * @bug 8294156
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Demonstrates adding and positioning two test windows vertically
 * @run main/manual TwoWindowsV
 */
public class TwoWindowsV {
    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(TwoWindowsH.INSTRUCTIONS)
                      .rows(15)
                      .columns(30)
                      .testUI(TwoWindowsH::createTestUI)
                      .positionTestUI(TwoWindowsH::positionTestUI)
                      .position(PassFailJFrame.Position.VERTICAL)
                      .build()
                      .awaitAndCheck();
    }
}
