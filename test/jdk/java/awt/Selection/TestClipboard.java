/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.io.Serializable;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/*
 * @test
 * @bug 4139552
 * @summary Checks to see if 'isDataFlavorSupported' throws exception.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestClipboard
 */

public class TestClipboard {

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                This test has two steps:

                1. you need to place some text onto the system clipboard,
                for example,
                    on Windows, you could highlight some text in notepad, and do a Ctrl-C
                    or select menu Edit->Copy;

                    on Linux or Mac, you can do the same with any Terminal or Console or
                    Text application.

                2. After you copy to system clipboard, press "Click Me" button.

                Test will fail if any exception is thrown.

                Press Pass if you see "Test Passed" in log area.""";

        PassFailJFrame.builder()
                .title("TestClipboard Instruction")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(TestClipboard::createUI)
                .logArea(4)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JFrame f = new JFrame("ChildFrameIconTest UI");
        JButton b = new JButton("Click Me");
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    new MyTest();
                } catch (Exception ex) {
                    throw new RuntimeException("Exception Thrown : " + ex);
                }
            }
        });
        f.add(b);
        f.setSize(200, 100);
        return f;
    }

    static class MyFlavor extends Object implements Serializable {
        // Stub class needed in order to define the data flavor type
    }

    static class MyTest {
        public MyTest() throws Exception {
            // Create an arbitrary dataflavor
            DataFlavor myFlavor = new DataFlavor(MyFlavor.class, "TestClipboard");
            // Get the system clipboard
            Clipboard theClipboard =
                    Toolkit.getDefaultToolkit().getSystemClipboard();
            // Get the current contents of the clipboard
            Transferable theTransfer = theClipboard.getContents(this);

            // See if the flavor is supported. This may result in a null
            // pointer exception.
            theTransfer.isDataFlavorSupported(myFlavor);
            PassFailJFrame.log("Test Passed");
        }
    }
}
