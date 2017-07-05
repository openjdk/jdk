/*      @test 1.5 98/07/23
        @bug 4064202 4253466
        @summary Test for Win32 NPE when MenuItem with null label added.
        @author fred.ecks
        @run main/othervm NullMenuLabelTest
*/

import java.awt.*;

public class NullMenuLabelTest {

    public static void main(String[] args) {
        Frame frame = new Frame("Test Frame");
        frame.pack();
        frame.setVisible(true);
        MenuBar menuBar = new MenuBar();
        frame.setMenuBar(menuBar);
        Menu menu = new Menu(null);
        menuBar.add(menu);
        menu.add(new MenuItem(null));
        // If we got this far, the test succeeded
        frame.setVisible(false);
        frame.dispose();
    }

} // class NullMenuLabelTest
