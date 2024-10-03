/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6261336
 * @summary Tests that Choice inside ScrollPane opens at the right location
 *          after resize
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual BadConfigure
*/

import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Frame;

public class BadConfigure
{
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
            Please resize the BadConfigure window using the left border.
            Now click on choice. Its popup will be opened.
            Please verify that the popup is opened right under the choice.
            """;

        PassFailJFrame.builder()
            .title("Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(35)
            .testUI(initialize())
            .build()
            .awaitAndCheck();
    }

    private static Frame initialize() {
        Frame f = new Frame("BadConfigure");
        f.setLayout(new BorderLayout());
        Choice ch = new Choice();
        f.add(ch, BorderLayout.NORTH);
        ch.add("One");
        ch.add("One");
        ch.add("One");
        ch.add("One");
        ch.add("One");
        ch.add("One");
        f.setSize(200, 200);
        f.validate();
        return f;
    }
}
