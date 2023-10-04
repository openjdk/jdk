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

/* @test
 * @bug 4138694
 * @summary When adding an Action object to a toolbar, the Action object's
 * SHORT_DESCRIPTION property (if present) should be automatically used
 * for toolTip text.
 * @run main bug4138694
 */

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

public class bug4138694 {
    public static final String actionName = "Action";

    private static class MyAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {}
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JToolBar jtb = new JToolBar();
            MyAction aa = new MyAction();
            aa.putValue(Action.SHORT_DESCRIPTION, actionName);
            jtb.add(aa);
            JComponent c = (JComponent)jtb.getComponentAtIndex(0);
            if (!c.getToolTipText().equals(actionName)) {
                throw new RuntimeException("ToolTip not set automatically...");
            }
        });
    }
}
