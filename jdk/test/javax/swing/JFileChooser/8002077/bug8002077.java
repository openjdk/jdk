/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import sun.awt.SunToolkit;

/**
 * @test
 * @bug 8002077
 * @author Alexander Scherbatiy
 * @summary Possible mnemonic issue on JFileChooser Save button on nimbus L&F
 * @library ../../regtesthelpers/
 * @build Util
 * @run main bug8002077
 */
public class bug8002077 {

    private static volatile int fileChooserState = JFileChooser.ERROR_OPTION;

    public static void main(String[] args) throws Exception {
        for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                UIManager.setLookAndFeel(info.getClassName());
                runTest();
                break;
            }
        }
    }

    private static void runTest() throws Exception {
        SunToolkit toolkit = (SunToolkit) Toolkit.getDefaultToolkit();
        Robot robot = new Robot();
        robot.setAutoDelay(50);

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                fileChooserState = new JFileChooser().showSaveDialog(null);
            }
        });
        toolkit.realSync();

        Util.hitMnemonics(robot, KeyEvent.VK_N);
        toolkit.realSync();

        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
        toolkit.realSync();

        Util.hitMnemonics(robot, KeyEvent.VK_S);
        toolkit.realSync();

        if (fileChooserState != JFileChooser.APPROVE_OPTION) {
            throw new RuntimeException("Save button is not pressed!");
        }
    }
}
