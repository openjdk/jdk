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
 * @test
 * @bug 4859570
 * @summary SwingUtilities.sharedOwnerFrame is never disposed
 * @key headful
 */

import java.awt.Robot;
import java.awt.Window;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

public class bug4859570 {
    static Window owner;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JDialog dialog = new JDialog();
            dialog.setTitle("bug4859570");
            dialog.setBounds(100, 100, 100, 100);
            dialog.setVisible(true);

            owner = dialog.getOwner();
            dialog.dispose();
        });

        Robot r = new Robot();
        r.waitForIdle();
        r.delay(1000);

        SwingUtilities.invokeAndWait(() -> {
            if (owner.isDisplayable()) {
                throw new RuntimeException("The shared owner frame should be disposed.");
            }
        });
    }
}
