/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.TextField;

/*
 * @test
 * @bug 4097744
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary packing a frame twice stops it resizing
 * @run main/manual PackTwiceTest
 */

public class PackTwiceTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. You would see a Frame titled 'TestFrame'
                2. The Frame displays a text as below:
                    'I am a lengthy sentence...can you see me?'
                3. If you can see the full text without resizing the frame
                   using mouse, press 'Pass' else press 'Fail'.""";

        PassFailJFrame.builder()
                .title("PackTwiceTest Instruction")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(PackTwiceTest::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame f = new Frame("PackTwiceTest TestFrame");
        TextField tf = new TextField();
        f.add(tf, "Center");
        tf.setText("I am a short sentence");
        f.pack();
        f.pack();
        tf.setText("I am a lengthy sentence...can you see me?");
        f.pack();
        f.requestFocus();
        return f;
    }
}
