/*
  @bug       7125044
  @summary   Tests default focus traversal policy in AWT toplevel windows.
  @author    anton.tarasov@sun.com: area=awt.focus
*/

import java.awt.Button;
import java.awt.DefaultFocusTraversalPolicy;
import java.awt.FlowLayout;
import java.awt.FocusTraversalPolicy;
import java.awt.Frame;
import java.awt.List;
import java.awt.TextArea;
import java.awt.Window;

public class InitialFTP_AWT {
    public static void main(String[] args) {
        AWTFrame f0 = new AWTFrame("frame0");
        f0.setVisible(true);

        InitialFTP.test(f0, DefaultFocusTraversalPolicy.class);

        AWTFrame f1 = new AWTFrame("frame1");
        f1.setVisible(true);

        InitialFTP.test(f1, DefaultFocusTraversalPolicy.class);

        System.out.println("Test passed.");
    }
}

class AWTFrame extends Frame {
    Button button = new Button("button");
    TextArea text = new TextArea("qwerty");
    List list = new List();

    public AWTFrame(String title) {
        super(title);

        list.add("one");
        list.add("two");
        list.add("three");

        this.setLayout(new FlowLayout());
        this.add(button);
        this.add(text);
        this.add(list);
        this.pack();
    }
}
