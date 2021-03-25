/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.*;

/**
 *  @test
 *  @bug 8253266
 *  @summary setUIProperty should work when opaque property is not set by
 *  client
 *  @key headful
 *  @run main TestOpaqueListTable
 */

public class TestOpaqueListTable {

    public static void main(String[] args) throws Exception {
        UIManager.LookAndFeelInfo[] installedLookAndFeels;
        installedLookAndFeels = UIManager.getInstalledLookAndFeels();
        for (UIManager.LookAndFeelInfo LF : installedLookAndFeels) {
            try {
                UIManager.setLookAndFeel(LF.getClassName());
                SwingUtilities.invokeAndWait(() -> {
                    JList list = new JList();
                    if (list.isOpaque() == false) {
                        throw new RuntimeException("Default value of " +
                                "\"opaque\" property for JList is changed ");
                    }

                    LookAndFeel.installProperty(list, "opaque", false);
                    if (list.isOpaque()) {
                        throw new RuntimeException(
                                "setUIProperty failed to clear JList opaque" +
                                        " when opaque is not set by client");
                    }

                    JTable table = new JTable();
                    if (table.isOpaque() == false) {
                        throw new RuntimeException("Default value of " +
                                "\"opaque\" property for JTable is changed ");
                    }

                    LookAndFeel.installProperty(table, "opaque", false);
                    if (table.isOpaque()) {
                        throw new RuntimeException(
                                "setUIProperty failed to clear JTable opaque" +
                                        " when opaque is not set by client");
                    }

                    list.setOpaque(true);
                    LookAndFeel.installProperty(list,"opaque",false);
                    if (!list.isOpaque()) {
                        throw new RuntimeException(
                                "setUIProperty cleared the JList Opaque" +
                                        " when opaque is set by client");
                    }

                    table.setOpaque(true);
                    LookAndFeel.installProperty(table, "opaque", false);
                    if (!table.isOpaque()) {
                        throw new RuntimeException("" +
                                "setUIProperty cleared the JTable Opaque" +
                                " when opaque is set by client");
                    }
                });
            } catch (UnsupportedLookAndFeelException e) {
                System.out.println("Note: LookAndFeel " + LF.getClassName()
                        + " is not supported on this configuration");
            }
        }
    }
}
