/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4846162 4626092
  @summary (Key|Window|Focus)Events should not be retargeted when dispatchEvent() is called directly.
  @run main EventRetargetTest
*/

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class EventRetargetTest {
    boolean isKEProcessed1;
    boolean isKEProcessed2;
    boolean isKEProcessed3;
    boolean isFEProcessed1;
    boolean isFEProcessed2;
    boolean isFEProcessed3;

    public void start () {
        final Component comp = new Component() {
                public boolean isShowing() {
                    return true;
                }

                public boolean isVisible() {
                    return true;
                }

                public boolean isDisplayable() {
                    return true;
                }

                protected void processKeyEvent(KeyEvent e) {
                    System.err.println("processKeyEvent >> " + e);
                    isKEProcessed1 = true;
                    super.processKeyEvent(e);
                }

                protected void processFocusEvent(FocusEvent e) {
                    System.err.println("processFocusEvent >> " + e);
                    isFEProcessed1 = true;
                    super.processFocusEvent(e);
                }
            };
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
                public void eventDispatched(AWTEvent e) {
                    if (e instanceof KeyEvent) {
                        isKEProcessed2 = (e.getSource() == comp);
                    }
                    else if (e instanceof FocusEvent) {
                        isFEProcessed2 = (e.getSource() == comp);
                    }
                    System.err.println("Toolkit >> " + e);
                }
            }, AWTEvent.KEY_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK);

        comp.addKeyListener(new KeyAdapter() {
                public void keyTyped(KeyEvent e) {
                    isKEProcessed3 = true;
                    System.err.println("Listener >> " + e);
                }
            });
        comp.addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) {
                    isFEProcessed3 = true;
                    System.err.println("Listener >> " + e);
                }
            });

        KeyEvent ke = new KeyEvent(comp, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
                                   KeyEvent.VK_UNDEFINED, 'a');
        comp.dispatchEvent(ke);

        if (!(isKEProcessed1 && isKEProcessed2 && isKEProcessed3)) {
            System.err.println("(" + isKEProcessed1 + "," + isKEProcessed2
                    + "," + isKEProcessed3 + ")");
            throw new RuntimeException("KeyEvent is not correctly retargeted.");
        }

        FocusEvent fe = new FocusEvent(comp, FocusEvent.FOCUS_GAINED,
                                       false, null);
        comp.dispatchEvent(fe);

        if (!(isFEProcessed1 && isFEProcessed2 && isFEProcessed3)) {
            System.err.println("(" + isFEProcessed1 + ","
                    + isFEProcessed2 + "," + isFEProcessed3 + ")");
            throw new RuntimeException("FocusEvent is not correctly retargeted.");
        }
    }

    public static void main(String[] args) {
        EventRetargetTest test = new EventRetargetTest();
        test.start();
    }
}
