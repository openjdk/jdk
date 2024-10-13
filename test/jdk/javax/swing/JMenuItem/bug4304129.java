/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 4304129
   @summary Tests that ACCELERATOR_KEY and MNEMONIC_KEY properties of
            Action are used by JMenuItem(Action) constructor
   @run main bug4304129
*/

import java.awt.Event;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

public class bug4304129 {
    private static int mnemonic = 102;
    private static KeyStroke accelerator = KeyStroke.getKeyStroke(
                                        KeyEvent.VK_E, Event.CTRL_MASK);

    public static void main(String args[]) throws Exception {
        JMenuItem mi = new JMenuItem(new TestAction("Delete Folder"));

        if (mi.getMnemonic() != mnemonic) {
            throw new RuntimeException("Failed: mnemonic not set from Action");
        }

        if (mi.getAccelerator() == null ||
                ! mi.getAccelerator().equals(accelerator)) {

            throw new RuntimeException("Failed: accelerator not set from Action");
        }
    }

    static class TestAction extends AbstractAction {
        TestAction(String str) {
            super(str);
            putValue(AbstractAction.ACCELERATOR_KEY, accelerator);
            putValue(AbstractAction.MNEMONIC_KEY, new Integer(mnemonic));
        }
        public void actionPerformed(ActionEvent ev) {
        }
    }
}
