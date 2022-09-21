/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/*
 * @test
 * @bug 8289539
 * @key headful
 * @requires (os.family == "windows")
 * @summary Test to check if CheckBox.InteriorBackground returns null
 *          in WindowsLookAndFeel and return default value in WindowsClassicLookAndFeel
 * @run main InteriorBackgroundTest
 */
public class InteriorBackgroundTest{
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> new testFrame().runTest());
        System.out.println("Test Pass!");
    }
}

class testFrame extends JFrame {
    void runTest() {
        setLookAndFeelAndTest("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        setLookAndFeelAndTest("com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel");
    }

    private void setLookAndFeelAndTest(String laf) {
        try {
            UIManager.setLookAndFeel(laf);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported L&F: " + laf);
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        SwingUtilities.updateComponentTreeUI(this);
        Color color = UIManager.getColor("CheckBox.interiorBackground");

        if(laf.contains("WindowsLookAndFeel") && color != null) {
            throw new RuntimeException("CheckBox.interiorBackground not Null for " +
                    laf);
        } else if(laf.contains("WindowsClassicLookAndFeel") && color == null) {
            throw new RuntimeException("CheckBox.interiorBackground Null for " +
                    laf);
        }
    }
}