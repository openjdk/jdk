/*
  @bug       7125044
  @summary   Tests default focus traversal policy in Swing toplevel windows.
  @author    anton.tarasov@sun.com: area=awt.focus
*/

import java.awt.FlowLayout;
import java.awt.FocusTraversalPolicy;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.LayoutFocusTraversalPolicy;

public class InitialFTP_Swing {
    public static void main(String[] args) {
        SwingFrame f0 = new SwingFrame("frame0");
        f0.setVisible(true);

        InitialFTP.test(f0, LayoutFocusTraversalPolicy.class);

        SwingFrame f1 = new SwingFrame("frame1");
        f1.setVisible(true);

        InitialFTP.test(f1, LayoutFocusTraversalPolicy.class);

        System.out.println("Test passed.");
    }
}

class SwingFrame extends JFrame {
    JButton button = new JButton("button");
    JTextArea text = new JTextArea("qwerty");
    JList list = new JList(new String[] {"one", "two", "three"});

    public SwingFrame(String title) {
        super(title);

        this.setLayout(new FlowLayout());
        this.add(button);
        this.add(text);
        this.add(list);
        this.pack();
    }
}
