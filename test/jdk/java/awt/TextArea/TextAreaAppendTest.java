/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;
import java.awt.TextArea;

/*
 * @test
 * @bug 4118915
 * @summary Test appending to a TextArea after the peer is created
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TextAreaAppendTest
 */

public class TextAreaAppendTest {
    private static final String INSTRUCTIONS = """
            If all four lines are visible in TextArea, the test passed.
            If the last two lines have only one character visible, the test failed.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("TextAreaAppendTest")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(TextAreaAppendTest::createGUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createGUI() {
        Frame f = new Frame("TextAreaAppendTest");
        TextArea ta = new TextArea();
        f.add(ta);
        ta.append("line 1 (added before drawing)\n");
        ta.append("line 2 (added before drawing)\n");

        f.pack();
        f.show();

        ta.append("line 3 (added after drawing)\n");
        ta.append("line 4 (added after drawing)\n");

        return f;
    }
}
