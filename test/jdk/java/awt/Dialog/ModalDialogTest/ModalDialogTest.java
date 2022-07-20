/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.TextArea;
import java.awt.Window;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @bug 4124096 4183412 6234295
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test dialog's modality with a series of Window, Frame and Dialog
 * For bug 4183412, verify that the Menu on any Frame cannot be popped up
 * when there is a modal dialog up.
 * @run main/manual ModalDialogTest
 */

class TestPanel extends Panel {
    private static MouseListener mouseListener;
    private static MouseMotionListener mouseMotionListener;
    private static FocusListener focusListener;
    static TextArea ta;

    public TestPanel() {
        if (mouseListener == null) {
            mouseListener = new MouseListener() {
                public void mouseEntered(MouseEvent e) {
                    ta.append(e.getComponent().getName()+":mouseEntered\n");
                }
                public void mouseExited(MouseEvent e) {
                    ta.append(e.getComponent().getName()+":mouseExited\n");
                }
                public void mousePressed(MouseEvent e) {
                    ta.append(e.getComponent().getName()+":mousePressed\n");
                }

                public void mouseReleased(MouseEvent e) {
                    ta.append(e.getComponent().getName()+":mouseReleased\n");
                }
                public void mouseClicked(MouseEvent e) {
                    ta.append(e.getComponent().getName()+":mouseClicked\n");
                }
            };
        }

        if (mouseMotionListener == null) {
            mouseMotionListener = new MouseMotionListener() {
                public void mouseMoved(MouseEvent e) {
                    ta.append(e.getComponent().getName()+":mouseMoved\n");
                }
                public void mouseDragged(MouseEvent e) {
                    ta.append(e.getComponent().getName()+":mouseDragged\n");
                }
            };
        }

        if (focusListener == null) {
           focusListener = new FocusListener() {
              public void focusGained(FocusEvent e) {
                  ta.append(e.getComponent().getName()+":focusGained\n");
              }
              public void focusLost(FocusEvent e) {
                  ta.append(e.getComponent().getName()+":focusLost\n");
              }
          };
       }

        Button b = new Button("Heavy Button");
        b.setName("HeavyButton");
        b.addMouseListener(mouseListener);
        b.addMouseMotionListener(mouseMotionListener);
        b.addFocusListener(focusListener);
        add(b);

        Component c = new Container() {
            public Dimension getPreferredSize() {
                return new Dimension(50,50);
            }
            public void paint(Graphics g) {
                Dimension d = getSize();
                g.setColor(Color.blue);
                g.fillRect(0, 0, d.width, d.height);
            }
        };
        c.setName("Lightweight");
        c.setBackground(Color.blue);
        c.addMouseListener(mouseListener);
        c.addMouseMotionListener(mouseMotionListener);
        c.addFocusListener(focusListener);
        add(c);
    }

    public TestPanel(TextArea t) {
        this();
        ta = t;
        add(ta);
    }
}

class WindowPanel extends Panel {
    static int windows = 0;
    static int dialogs = 0;
    static int frames = 1;
    static int modalDialogs = 0;

    private static WindowListener winListener;
    private static FocusListener focusListener;

    private final Button windowButton;
    private final Button dialogButton;
    private final Button frameButton;
    private final Button modalDialogButton;

    public static void buildAndShowWindow(Window win, Component top,
                                          TestPanel center, Component bottom) {
        final TextArea ta = TestPanel.ta;

        if (winListener == null) {
            winListener = new WindowListener() {
                public void windowOpened(WindowEvent e) {
                    ta.append(e.getWindow().getName()+":windowOpened\n");
                }
                public void windowClosing(WindowEvent e) {
                    ta.append(e.getWindow().getName()+":windowClosing\n");
                    e.getWindow().setVisible(false);
                }
                public void windowClosed(WindowEvent e) {
                    ta.append(e.getWindow().getName()+":windowClosed\n");
                }
                public void windowIconified(WindowEvent e) {
                    ta.append(e.getWindow().getName()+":windowIconified\n");
                }
                public void windowDeiconified(WindowEvent e) {
                    ta.append(e.getWindow().getName()+":windowDeiconified\n");
                }
                public void windowActivated(WindowEvent e) {
                    ta.append(e.getWindow().getName()+":windowActivated\n");
                }
                public void windowDeactivated(WindowEvent e) {
                    ta.append(e.getWindow().getName()+":windowDeactivated\n");
                }
            };
         }

        if (focusListener == null) {
            focusListener = new FocusListener() {
                public void focusGained(FocusEvent e) {
                    ta.append(e.getComponent().getName()+":focusGained\n");
                }
                public void focusLost(FocusEvent e) {
                    ta.append(e.getComponent().getName()+":focusLost\n");
                }
            };
        }

        win.addWindowListener(winListener);
        win.addFocusListener(focusListener);

        if (!(win instanceof Frame)) {
            Rectangle pBounds = win.getOwner().getBounds();
            win.setLocation(pBounds.x, pBounds.y + pBounds.height);
        }

        win.add(top, BorderLayout.NORTH);
        win.add(center, BorderLayout.CENTER);
        win.add(bottom, BorderLayout.SOUTH);
        win.pack();
        win.setVisible(true);

        PassFailJFrame.addTestWindow(win);
    }

    public Window getParentWindow() {
        Container p = getParent();
        while (p != null && !(p instanceof Window)) {
            p = p.getParent();
        }
        return (Window)p;
    }

    public WindowPanel() {

        windowButton = new Button("New Window...");
        windowButton.addActionListener(e -> {
            Window owner = getParentWindow();
            Window window = new Window(owner);
            window.setName("Window "+ windows++);

            Panel p = new Panel();
            p.setLayout(new GridLayout(0, 1));
            p.add(new Label("Title: "+ window.getName()));
            p.add(new Label("Owner: "+ owner.getName()));

            buildAndShowWindow(window, p, new TestPanel(), new WindowPanel());
        });
        add(windowButton);

        frameButton = new Button("New Frame...");
        frameButton.addActionListener(e -> {
            Frame frame = new Frame("Frame "+ frames++);
            frame.setName(frame.getTitle());
            MenuBar mb=new MenuBar();
            Menu m=new Menu("Menu");
            m.add(new MenuItem("Dummy menu item"));
            frame.setMenuBar(mb);
            mb.add(m);

            buildAndShowWindow(frame, new Label("Owner: none"),
                               new TestPanel(), new WindowPanel());
        });
        add(frameButton);

        dialogButton = new Button("New Dialog...");
        dialogButton.addActionListener(e -> {
            Window owner = getParentWindow();
            Dialog dialog;
            if (owner instanceof Dialog) {
                dialog = new Dialog((Dialog)owner, "Dialog "+ dialogs++, false);
            } else {
                dialog = new Dialog((Frame)owner, "Dialog "+ dialogs++, false);
            }
            dialog.setName(dialog.getTitle());

            buildAndShowWindow(dialog, new Label("Owner: "+ owner.getName()),
                               new TestPanel(), new WindowPanel());
        });
        add(dialogButton);

        modalDialogButton = new Button("New Modal Dialog...");
        modalDialogButton.addActionListener(e -> {
            Window owner = getParentWindow();
            Dialog dialog;
            if (owner instanceof Dialog) {
                dialog = new Dialog((Dialog)owner, "ModalDialog "+ modalDialogs++,
                                    true);
            } else {
                dialog = new Dialog((Frame)owner, "ModalDialog "+ modalDialogs++,
                                    true);
            }
            dialog.setName(dialog.getTitle());
            buildAndShowWindow(dialog, new Label("Owner: "+ owner.getName()),
                               new TestPanel(), new WindowPanel());
        });
        add(modalDialogButton);
    }

    public void addNotify() {
        super.addNotify();
        Window owner = getParentWindow();
        if (!(owner instanceof Frame) && !(owner instanceof Dialog)) {
            dialogButton.setEnabled(false);
            modalDialogButton.setEnabled(false);
        }
    }
}

public class ModalDialogTest {
    private static Frame frame= new Frame("RootFrame");
    private static final boolean isMacOS = System.getProperty("os.name")
                                          .contains("OS X");

    private static String getInstructions() {
        StringBuilder sb = new StringBuilder();

        sb.append("""
            When the test is ready, one Root Frame is shown. The Frame has a
            "Heavy button", a blue lightweight component and a TextArea to
            display message. The Root Frame has no owner.

            \t *. Click button "New Frame" to show a new Frame, notice that this
            \t Frame 1 has a Menu added. Verify that Menu is accessible.

            \t *. Now click button "New Modal Dialog" to bring up a modal dialog.
            """);

        if (!isMacOS) { //We do not test screen menu bar on macOS
            sb.append("""
                  \t Verify that the Menu in Frame 1 is not accessible anymore.
                  \t That tests the fix for 4183412 on Solaris and
                  \t 6234295 on XToolkit.\n
                  """);
        }

        sb.append("""
            \t *. You can click different buttons several times, but verify that
            \t whenever a Modal dialog is up, no mouse event can be generated for
            \t other windows.
            \t (All the events are printed in the TextArea in Root Window).
            \t This tests the fix for 4124096.

            Close the modal dialog before pressing fail/pass button.
            """);

        return sb.toString();
    }

    public static void main(String[] args) throws InterruptedException,
                                                  InvocationTargetException {
        PassFailJFrame passFailJFrame = new PassFailJFrame("ModalDialogTest " +
        "Instructions", getInstructions(), 10, 20, 60);

        WindowPanel.buildAndShowWindow(
            frame,
            new Label("Owner: none"),
            new TestPanel(new TextArea(10, 30)),
            new WindowPanel()
        );

        // adding only the root frame to be positioned
        // w.r.t instruction frame
        passFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);
        passFailJFrame.awaitAndCheck();
    }
}
