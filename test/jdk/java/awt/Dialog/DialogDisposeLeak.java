/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTEvent;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;

/*
 * @test
 * @bug 4193022
 * @summary Test for bug(s): 4193022, disposing dialog leaks memory
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DialogDisposeLeak
 */

public class DialogDisposeLeak {
    private static final String INSTRUCTIONS = """
            Click on the Dialog... button in the frame that appears.
            Now dismiss the dialog by clicking on the label in the dialog.

            Repeat this around 10 times. At some point the label in the frame should change
            to indicated that the dialog has been garbage collected and the test passed.
            """;

    public static void main(String args[]) throws Exception {
        Frame frame = new DisposeFrame();
        PassFailJFrame.builder()
                .title("DialogDisposeLeak")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(frame)
                .build()
                .awaitAndCheck();
    }
}

class DisposeFrame extends Frame {
    Label label = new Label("Test not passed yet");

    DisposeFrame() {
        super("DisposeLeak test");
        setLayout(new FlowLayout());
        Button btn = new Button("Dialog...");
        add(btn);
        btn.addActionListener(ev -> {
                    Dialog dlg = new DisposeDialog(DisposeFrame.this);
                    dlg.setVisible(true);
                }
        );
        add(label);
        pack();
    }

    public void testOK() {
        label.setText("Test has passed. Dialog finalized.");
    }
}

class DisposeDialog extends Dialog {
    DisposeDialog(Frame frame) {
        super(frame, "DisposeDialog", true);
        setLocation(frame.getX(), frame.getY());

        setLayout(new FlowLayout());
        LightweightComp lw = new LightweightComp("Click here to dispose");
        lw.addMouseListener(
                new MouseAdapter() {
                    public void mouseEntered(MouseEvent ev) {
                        System.out.println("Entered lw");
                    }

                    public void mouseExited(MouseEvent ev) {
                        System.out.println("Exited lw");
                    }

                    public void mouseReleased(MouseEvent ev) {
                        System.out.println("Released lw");
                        DisposeDialog.this.dispose();
                        // try to force GC and finalization
                        for (int n = 0; n < 100; n++) {
                            byte[] bytes = new byte[1024 * 1024 * 8];
                            System.gc();
                        }
                    }
                }
        );
        add(lw);
        pack();
    }

    public void finalize() {
        ((DisposeFrame) getParent()).testOK();
    }
}

// simple lightweight component, focus traversable, highlights upon focus
class LightweightComp extends Component {
    FontMetrics fm;
    String label;
    private static final int FOCUS_GONE = 0;
    private static final int FOCUS_TEMP = 1;
    private static final int FOCUS_HAVE = 2;
    int focusLevel = FOCUS_GONE;
    public static int nameCounter = 0;

    public LightweightComp(String lwLabel) {
        label = lwLabel;
        enableEvents(AWTEvent.FOCUS_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
        setName("lw" + nameCounter++);
    }

    public Dimension getPreferredSize() {
        if (fm == null) fm = Toolkit.getDefaultToolkit().getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(label) + 2, fm.getHeight() + 2);
    }

    public void paint(Graphics g) {
        Dimension s = getSize();

        // erase the background
        g.setColor(getBackground());
        g.fillRect(0, 0, s.width, s.height);

        g.setColor(getForeground());

        // draw the string
        g.drawString(label, 2, fm.getHeight());

        // draw a focus rectangle
        if (focusLevel > FOCUS_GONE) {
            if (focusLevel == FOCUS_TEMP) {
                g.setColor(Color.gray);
            } else {
                g.setColor(Color.blue);
            }
        } else {
            g.setColor(Color.black);
        }
        g.drawRect(1, 1, s.width - 2, s.height - 2);
    }

    public boolean isFocusTraversable() {
        return true;
    }

    protected void processFocusEvent(FocusEvent e) {
        super.processFocusEvent(e);
        if (e.getID() == FocusEvent.FOCUS_GAINED) {
            focusLevel = FOCUS_HAVE;
        } else {
            if (e.isTemporary()) {
                focusLevel = FOCUS_TEMP;
            } else {
                focusLevel = FOCUS_GONE;
            }
        }
        repaint();
    }

    protected void processMouseEvent(MouseEvent e) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            requestFocus();
        }
        super.processMouseEvent(e);
    }
}

