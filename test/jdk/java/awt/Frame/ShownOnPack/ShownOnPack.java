/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.Frame;

/*
 * @test
 * @bug 6525850
 * @summary Iconified frame gets shown after pack()
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ShownOnPack
*/

public class ShownOnPack {

    private static final String INSTRUCTIONS = """
            This test creates an invisible and iconified frame that should not become visible.

            If you observe the window titled 'Should NOT BE SHOWN' in the taskbar,
            press FAIL, otherwise press PASS
            """;

    static Frame frame;

    public static void main(String[] args) throws Exception {
        PassFailJFrame shownOnPackInstructions = PassFailJFrame
                .builder()
                .title("ShownOnPack Instructions")
                .instructions(INSTRUCTIONS)
                .rows(5)
                .columns(50)
                .build();

        EventQueue.invokeAndWait(() -> {
            frame = new Frame("Should NOT BE SHOWN");
            frame.setExtendedState(Frame.ICONIFIED);
            frame.pack();
        });

        try {
            shownOnPackInstructions.awaitAndCheck();
        } finally {
            EventQueue.invokeAndWait(() -> frame.dispose());
        }
    }
}
