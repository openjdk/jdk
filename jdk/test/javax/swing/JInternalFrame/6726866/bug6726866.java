/* @test
   @bug 6726866
   @summary Repainting artifacts when resizing or dragging JInternalFrames in non-opaque toplevel
   @author Alexander Potochkin
   @run applet/manual=yesno bug6726866.html
*/

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;

public class bug6726866 extends JApplet {

    public void init() {
        JFrame frame = new JFrame("bug6726866");
        frame.setUndecorated(true);
        setWindowNonOpaque(frame);

        JDesktopPane desktop = new JDesktopPane();
        desktop.setBackground(Color.GREEN);
        JInternalFrame iFrame = new JInternalFrame("Test", true, true, true, true);
        iFrame.add(new JLabel("internal Frame"));
        iFrame.setBounds(10, 10, 300, 200);
        iFrame.setVisible(true);
        desktop.add(iFrame);
        frame.add(desktop);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 400);
        frame.setVisible(true);
        frame.toFront();
    }

    private void setWindowNonOpaque(Window w) {
        try {
            Class<?> c = Class.forName("com.sun.awt.AWTUtilities");
            Method m = c.getMethod("setWindowOpaque", Window.class, boolean.class);
            m.invoke(null, w, false);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
