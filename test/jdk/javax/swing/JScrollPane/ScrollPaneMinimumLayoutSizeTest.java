/* @test
   @bug 4688907
   @summary ScrollPaneLayout.minimumLayoutSize incorrectly compares hsbPolicy
   @author Andrey Pikalev
   @key headful
   @run applet bug4688907.html
*/

import javax.swing.*;
import java.awt.Dimension;


public class bug4688907 extends JApplet {

    public void init() {
        JScrollPane sp = new JScrollPane();
        Dimension d1 = sp.getMinimumSize();
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        Dimension d2 = sp.getMinimumSize();
        if ( d1.height == d2.height ) {
            throw new Error("The scrollbar minimum size doesn't take into account horizontal scrollbar policy");
        }
    }

    public static void main(String[] argv) {
        bug4688907 b = new bug4688907();
        b.init();
    }
}
