/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Window;
import java.util.List;

/*
 * @test
 * @bug 4240766 8259023
 * @summary Frame Icon is wrong - should be Coffee Cup or Duke image icon
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DefaultFrameIconTest
*/

public class DefaultFrameIconTest {

    private static final String INSTRUCTIONS = """
            You should see a dialog and a frame.
            If both have Coffee Cup or Duke image icon in the upper left corner,
            the test passes, otherwise it fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("DefaultFrameIconTest Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(DefaultFrameIconTest::createAndShowUI)
                .positionTestUIRightRow()
                .build()
                .awaitAndCheck();
    }

    private static List<Window> createAndShowUI() {
        Frame testFrame = new Frame("Frame DefaultFrameIconTest");
        Dialog testDialog = new Dialog(testFrame, "Dialog DefaultFrameIconTest");

        testDialog.setSize(250, 100);

        testFrame.setSize(250, 100);
        return List.of(testFrame, testDialog);
    }
}
