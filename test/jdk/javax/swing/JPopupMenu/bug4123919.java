/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4123919
 * @requires (os.family == "windows")
 * @summary JPopupMenu.isPopupTrigger() under a different L&F.
 * @key headful
 * @run main bug4123919
 */

import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;
import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.util.Date;

public class bug4123919 {

    public static void main(String[] args) throws Exception {
        JPopupMenu popup = new JPopupMenu("Test");
        JLabel lb = new JLabel();
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        SwingUtilities.updateComponentTreeUI(lb);
        SwingUtilities.updateComponentTreeUI(popup);
        if (!popup.isPopupTrigger(new MouseEvent(lb, MouseEvent.MOUSE_PRESSED,
                (new Date()).getTime(), MouseEvent.BUTTON3_MASK, 10, 10, 1, true))) {
            throw new RuntimeException("JPopupMenu.isPopupTrigger() fails on" +
                    " MotifLookAndFeel when mouse pressed...");
        }
        if (popup.isPopupTrigger(new MouseEvent(lb, MouseEvent.MOUSE_RELEASED,
                (new Date()).getTime(), MouseEvent.BUTTON3_MASK, 10, 10, 1, true))) {
            throw new RuntimeException("JPopupMenu.isPopupTrigger() fails on" +
                    " MotifLookAndFeel when mouse released...");
        }

        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        SwingUtilities.updateComponentTreeUI(lb);
        SwingUtilities.updateComponentTreeUI(popup);

        if (popup.isPopupTrigger(new MouseEvent(lb, MouseEvent.MOUSE_PRESSED,
                (new Date()).getTime(), MouseEvent.BUTTON3_MASK, 10, 10, 1, true))) {
            throw new RuntimeException("JPopupMenu.isPopupTrigger() fails on" +
                    " WindowsLookAndFeel when mouse pressed...");
        }
        if (!popup.isPopupTrigger(new MouseEvent(lb, MouseEvent.MOUSE_RELEASED,
                (new Date()).getTime(), MouseEvent.BUTTON3_MASK, 10, 10, 1, true))) {
            throw new RuntimeException("JPopupMenu.isPopupTrigger() fails on" +
                    " WindowsLookAndFeel when mouse released...");
        }
    }
}
