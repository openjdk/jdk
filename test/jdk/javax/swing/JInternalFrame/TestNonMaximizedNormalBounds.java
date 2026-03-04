/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
  * @key headful
  * @bug 8267961
  * @summary Verify JInternalFrame.getNormalBounds() 
  *          returns getBounds() value in non-maximized state
  * @run main TestNonMaximizedNormalBounds 
 */

import java.awt.Rectangle;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;
import java.beans.PropertyVetoException;

public class TestNonMaximizedNormalBounds {

    private static volatile Rectangle bounds;
    private static volatile Rectangle normalBounds;
    private static JInternalFrame jif;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Rectangle bounds = new Rectangle(96, 97, 98, 99);
            Rectangle nbounds = new Rectangle(196, 197, 198, 199);
            JDesktopPane p = new JDesktopPane();
            jif = new JInternalFrame();
            p.add(jif);
            jif.setBounds(bounds);
            jif.setNormalBounds(nbounds);
        });
        Thread.sleep(100);
        SwingUtilities.invokeAndWait(() -> {
            try {
                jif.setMaximum(false);
            } catch (PropertyVetoException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(100);
        SwingUtilities.invokeAndWait(() -> {
            normalBounds = jif.getNormalBounds();
            bounds = jif.getBounds();
        });
        if (!normalBounds.equals(bounds)) {
            System.out.println("normalBounds " + normalBounds + " getBounds " + bounds);
            throw new RuntimeException("normalBounds not equal to getBounds in non-maximized state");
        } 
    }
}
