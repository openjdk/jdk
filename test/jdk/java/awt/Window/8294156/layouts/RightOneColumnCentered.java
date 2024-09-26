/*
 * @test
 * @bug 8294156 8317116
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Position test windows in a centered column to the right of the instructions
 * @run main/manual RightOneColumnCentered
 */
public class RightOneColumnCentered {
    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(INSTRUCTIONS)
                      .rows(15)
                      .columns(30)
                      .testUI(() -> WindowCreator.createTestWindows(5))
                      .positionTestUIRightColumnCentered()
                      .build()
                      .awaitAndCheck();
    }

    private static final String INSTRUCTIONS = """
            A simple demo with 5 test windows positioned to
            the right of the instruction frame in one column.
            The column of the windows is centered vertically
            on the screen.

            Layout: WindowLayouts::rightOneColumnCentered
            """;
}
