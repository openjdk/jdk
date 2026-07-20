/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Label;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.List;

/*
 * @test
 * @bug 4779641
 * @summary Test to verify that Non-resizable dialogs should not show icons
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DialogIconTest
 */

public class DialogIconTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. This is a Windows-only test of Dialog icons
                2. You can see a frame with a swing icon and two dialogs that it
                   owns. The resizable dialog should have the same icon as the
                   frame. The non-resizable dialog should have no icon at all
                3. Press PASS if this is true, press FAIL otherwise
                 """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .build()
                .awaitAndCheck();
    }

    public static List<Window> initialize() {
        Frame f = new Frame("Parent frame");
        f.setBounds(50, 50, 200, 200);

        Dialog dr = new Dialog(f, "Resizable Dialog");
        dr.setLocation(100, 100);
        dr.add(new Label("Should inherit icon from parent"));
        dr.pack();

        Dialog dn = new Dialog(f, "NON Resizable Dialog");
        dn.setLocation(150, 150);
        dn.add(new Label("Should have no icon"));
        dn.pack();
        dn.setResizable(false);

        String fileName = System.getProperty("test.src") +
                System.getProperty("file.separator") + "swing.small.gif";

        Image icon = Toolkit.getDefaultToolkit().createImage(fileName);
        MediaTracker tracker = new MediaTracker(f);
        tracker.addImage(icon, 0);
        try {
            tracker.waitForAll();
        } catch (InterruptedException ie) {
            throw new RuntimeException("MediaTracker addImage Interrupted!");
        }
        f.setIconImage(icon);
        return List.of(f, dn, dr);
    }
}
