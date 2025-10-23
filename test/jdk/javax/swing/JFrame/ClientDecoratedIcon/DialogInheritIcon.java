/*
 * Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6425606
 * @requires (os.family == "windows")
 * @summary Test JDialog's inheritance of icons set with JFrame.setIconImages()
 * @library ../../regtesthelpers
 * @run main/manual DialogInheritIcon
 */

import javax.swing.JDialog;

public class DialogInheritIcon extends ClientDecoratedIconTest {
    JDialog dialog1;
    JDialog dialog2;

    /*
     * @Override
     */
    protected String getInstructions() {
        StringBuilder instructionsStr = new StringBuilder();
        instructionsStr.append("This tests the functionality of JDialog-inherited icons set using the setIconImages() API.\n");
        instructionsStr.append("You will see two JFrames with custom icons, each with a child JDialog below it.\n");
        instructionsStr.append("Both JDialogs should have the same icon: a colored box.\n");
        instructionsStr.append("If either of the JDialogs has the default, coffe-cup icon, the test fails.\n");
        instructionsStr.append("If the JDialogs DO NOT both have the same colored box as their icon, the test fails.\n");
        instructionsStr.append("If both JDialogs DO have the same colored box as their icon, then the test passes.\n");
        instructionsStr.append("Note: If the JDialog icons don't match the icons of the parent JFrame, that is OK.");
        return instructionsStr.toString();
    }

    public void onEDT15() {
        createDialogs();
        dialog1.setVisible(true);
        dialog2.setVisible(true);
    }

    protected void createDialogs() {
        dialog1 = new JDialog(frame1, "Child JDialog 1", false);
        dialog1.setBounds(frame1.getLocation().x, frame1.getLocation().y + frame1.getSize().height + 5, 200, 200);
        dialog2 = new JDialog(frame2, "Child JDialog 2", false);
        dialog2.setBounds(frame2.getLocation().x, frame2.getLocation().y + frame2.getSize().height + 5, 200, 200);
    }

    public static void main(String[] args) throws Throwable {
        new DialogInheritIcon().run(args);
        System.out.println("end of main()");
    }
}
