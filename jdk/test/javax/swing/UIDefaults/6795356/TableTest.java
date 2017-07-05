/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6795356
 * @summary Checks that SwingLazyValue class correclty works
 * @author Alexander Potochkin
 * @run main/othervm TableTest
 */

import sun.applet.AppletSecurity;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;

public class TableTest {

    public static void main(String[] args) throws Exception {

        KeyboardFocusManager.getCurrentKeyboardFocusManager();
        System.setSecurityManager(new AppletSecurity());

        JTable table = new JTable();
        TableCellEditor de = table.getDefaultEditor(Double.class);
        if (de == null) {
            throw new RuntimeException("Table default editor is null");
        }
    }
}

