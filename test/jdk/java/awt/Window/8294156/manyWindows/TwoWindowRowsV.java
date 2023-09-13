/*
 * @test
 * @bug 8294156
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Demonstrates adding and positioning several test windows
 *          vertically in rows
 * @run main/manual TwoWindowRowsV
 */
public class TwoWindowRowsV {
    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(TwoWindowColumnsH.INSTRUCTIONS)
                .rows(5)
                .columns(30)
                .testUI(TwoWindowColumnsH::createTestUI)
                .positionTestUI(TwoWindowColumnsH::positionTestUI)
                .position(PassFailJFrame.Position.VERTICAL)
                .build()
                .awaitAndCheck();
    }
}
