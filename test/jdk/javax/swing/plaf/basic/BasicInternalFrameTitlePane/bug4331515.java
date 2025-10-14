/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4331515
 * @requires (os.family == "windows")
 * @summary System menu of an internal frame shouldn't have duplicated items in Win L&F
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4331515
 */

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.UIManager;

public class bug4331515 {
    static final String INSTRUCTIONS = """
        Open the system menu of internal frame "JIF" placed in the frame "Test".
        If this menu contains duplicates of some items then test FAILS, else
        test PASSES.
    """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        PassFailJFrame.builder()
                .title("bug4331515 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4331515::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame fr = new JFrame("System Menu in JIF Test");
        JDesktopPane dp = new JDesktopPane();
        fr.setContentPane(dp);
        JInternalFrame jif = new JInternalFrame("JIF", true, true, true, true);
        dp.add(jif);
        jif.setBounds(20, 20, 120, 100);
        jif.setVisible(true);
        fr.setSize(200, 200);
        return fr;
    }
}
