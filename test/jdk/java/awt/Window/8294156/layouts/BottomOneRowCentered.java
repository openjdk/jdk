/*
 * @test
 * @bug 8294156 8317116
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Position test windows in a centered row to the bottom of the instructions
 * @run main/manual BottomOneRowCentered
 */
public class BottomOneRowCentered {
    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(INSTRUCTIONS)
                      .rows(7)
                      .columns(30)
                      .testUI(() -> WindowCreator.createTestWindows(4))
                      .positionTestUIBottomRowCentered()
                      .build()
                      .awaitAndCheck();
    }

    private static final String INSTRUCTIONS = """
            A simple demo with 4 test windows positioned to
            the bottom of the instruction frame in one row.
            The row of the test windows is centered on the screen.
            
            Layout: WindowLayouts::bottomOneRowCentered
            """;
}
