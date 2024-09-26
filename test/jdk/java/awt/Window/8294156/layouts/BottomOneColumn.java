/*
 * @test
 * @bug 8294156 8317116
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Position test windows in a column to the bottom of the instructions
 * @run main/manual BottomOneColumn
 */
public class BottomOneColumn {
    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(INSTRUCTIONS)
                      .rows(7)
                      .columns(30)
                      .testUI(() -> WindowCreator.createTestWindows(2))
                      .positionTestUIBottomColumn()
                      .build()
                      .awaitAndCheck();
    }

    private static final String INSTRUCTIONS = """
            A simple demo with 2 test windows positioned to
            the bottom of the instruction frame in one column.
            The test windows are aligned to the left of the
            instruction frame.

            Layout: WindowLayouts::bottomOneColumn
            """;
}
