/*
  test %W% %E%
  @bug 4411534 4517274
  @summary ensures that user's requestFocus() during applet initialization
           is not ignored.
  @author  prs@sparc.spb.su area=appletviewer
  @run shell AppletInitialFocusTest1.sh
*/

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;

public class AppletInitialFocusTest1 extends Applet implements FocusListener {

    Button button1 = new Button("Button1");
    Button button2 = new Button("Button2");

    Object lock = new Object();

    public void init() {

        Component parent = this;
        while (parent != null && !(parent instanceof Window)) {
            parent = parent.getParent();
        }
        /*
         * This applet is designed to be run only with appletviewer,
         * so there always should be a toplevel frame.
         */
        if (parent == null) {
            synchronized (lock) {
                System.err.println("appletviewer not running");
                System.exit(3);
            }
        }
        button1.addFocusListener(this);
        button2.addFocusListener(this);
        add(button1);
        add(button2);
        button2.requestFocus();
    }

    public void focusGained(FocusEvent e) {
        if (e.getSource() == button1) {
            synchronized (lock) {
                System.err.println("failed: focus on the wrong button");
                System.exit(2);
            }
        }
    }

    public void focusLost(FocusEvent e) {
    }

    public void start() {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(10000);
                    synchronized (lock) {
                        System.err.println("passed");
                        System.exit(0);
                    }
                } catch(InterruptedException e) {
                }
            }
        });
        thread.start();
    }
}
