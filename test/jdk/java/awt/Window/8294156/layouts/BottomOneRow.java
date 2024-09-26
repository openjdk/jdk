/*
 * @test
 * @bug 8294156 8317116
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Position test windows in a row to the bottom of the instructions
 * @run main/manual BottomOneRow
 */
public class BottomOneRow {
    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(INSTRUCTIONS)
                      .rows(15)
                      .columns(30)
                      .testUI(() -> WindowCreator.createTestWindows(3))
                      .positionTestUIBottomRow()
                      .build()
                      .awaitAndCheck();
    }

    private static final String INSTRUCTIONS = """
            A simple demo with 3 test windows positioned to
            the bottom of the instruction frame in one row.
            The left of the first test window is aligned to
            that of the instruction frame.

            Layout: WindowLayouts::bottomOneRow
            """;
}
