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
 * @bug 4244616
 * @summary Tests that debug output in BasicMenuUI private inner classes
 *          is commented out
 */

import java.awt.event.ActionEvent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JMenu;
import javax.swing.plaf.basic.BasicMenuUI;

public class bug4244616 {
    public static void main(String[] argv) throws Exception {
        JMenu menu = new JMenu();
        BasicMenuUI ui = new BasicMenuUI();
        ui.installUI(menu);
        ActionMap amap = menu.getActionMap();

        String[] names = {"selectMenu", "cancel",
                "selectNext", "selectPrevious"};
        ActionEvent ev = new ActionEvent(menu,
                ActionEvent.ACTION_PERFORMED, "test event");

        // Stream redirection
        final PrintStream oldOut = System.out;
        final PrintStream oldErr = System.err;
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream();
             ByteArrayOutputStream berr = new ByteArrayOutputStream();
             PrintStream out = new PrintStream(bout);
             PrintStream err = new PrintStream(berr)) {
            for (int i = 0; i < names.length; i++) {
                Action action = amap.get(names[i]);
                try {
                    action.actionPerformed(ev);
                } catch (Exception ignored) {
                }
            }

            if (bout.size() != 0 || berr.size() != 0) {
                System.out.println("bout: " + bout);
                System.out.println("berr: " + berr);
                throw new RuntimeException("Failed: some debug output occurred");
            }
        } finally {
            // Restore streams
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
    }
}
