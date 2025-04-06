/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug  4098290 4140890
 * @summary Using non-opaque windows - popups are initially not painted correctly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MixedWeightFocus
*/

import java.util.List;
import java.awt.AWTEvent;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MixedWeightFocus {

    static FocusFrame f;

    private static final String INSTRUCTIONS = """
        This tests that permanent FOCUS_LOST messages are sent to lightweight
        components when the focus shifts to a heavyweight component. It also
        tests that components retain the focus when their parent window is
        deactivated and activated.

        1. Tab or mouse between the light and heavyweight buttons in this test
           and verify that the focus rectangle on the lightweight components
           disappears when focus shifts to a heavyweight button.

        2. Activate another application then reactivate the test frame window.
           Verify that the component that had the focus (light or heavy) when
           the frame was deactivated regains the focus when it's reactivated. Do
           the same thing for the modeless dialog. Also test this by moving the
           activation between the dialog and the frame.

        3. Verify that lightweight components with the focus in a deactivated
           window draw their focus rectangles in gray instead of blue-- this indicates
           they received temporary instead of permanent FOCUS_LOST events.

        NOTE: There is currently another problem with lightweight components
           where if you click on one to activate its frame window, the lightweight
           that previously had the focus will not get a FOCUS_LOST event. This
           may manifest itself with a gray focus rectangle not getting erased.
           Ignore this for now (Win32 only).""";

    public static void main(String[] argv) throws Exception {
        PassFailJFrame.builder()
                .title("MixedWeightFocus Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 5)
                .columns(45)
                .testUI(MixedWeightFocus::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static List<Window> createTestUI() {
        FocusFrame f = new FocusFrame();
        ModelessDialog dlg = new ModelessDialog(f);

        return List.of(f, dlg);
    }
}

class FocusFrame extends Frame {
    public FocusFrame() {
        super("FocusFrame");
        Panel p = new Panel();

        p.add(new Button("button 1"));
        p.add(new LightweightComp("lw 1"));
        p.add(new Button("button 2"));
        p.add(new LightweightComp("lw 2"));
        add(p);

        pack();
        setLocation(100, 100);

        addWindowListener(new WindowAdapter() {
                              public void windowClosing(WindowEvent ev) {
                                  dispose();
                              }
                         });
    }

}

class ModelessDialog extends Dialog {
    public ModelessDialog(Frame frame) {
        super(frame, "ModelessDialog", false);
        setLayout( new FlowLayout() );
        add(new Button("button 1"));
        add(new LightweightComp("lw 1"));
        pack();
        setLocation(100, 400);
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

    public LightweightComp(String lwLabel ) {
        label = lwLabel;
        enableEvents(AWTEvent.FOCUS_EVENT_MASK|AWTEvent.MOUSE_EVENT_MASK);
        setName("lw"+Integer.toString(nameCounter++));
    }

    public Dimension getPreferredSize() {
        if (fm == null) {
            fm = Toolkit.getDefaultToolkit().getFontMetrics(getFont());
        }
        return new Dimension(fm.stringWidth(label) + 2, fm.getHeight() + 2);
    }

    public void paint(Graphics g) {
        Dimension s=getSize();

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
            g.drawRect(1,1,s.width-2,s.height-2);
        }
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

        if (e.getID()==MouseEvent.MOUSE_PRESSED) {
            requestFocus();
        }
        super.processMouseEvent(e);
    }
}
