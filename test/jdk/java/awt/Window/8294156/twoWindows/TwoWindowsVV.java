/*
 * @test
 * @bug 8294156
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Demonstrates adding and positioning two test windows horizontally
 * @run main/manual TwoWindowsVV
 */
public class TwoWindowsVV {
    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(TwoWindowsHH.INSTRUCTIONS)
                      .rows(15)
                      .columns(30)
                      .testUI(TwoWindowsHH::createTestUI)
                      .positionTestUI(TwoWindowsHV::positionTestUI)
                      .position(PassFailJFrame.Position.VERTICAL)
                      .build()
                      .awaitAndCheck();
    }
}
