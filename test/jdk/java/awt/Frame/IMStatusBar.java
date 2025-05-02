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

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;

/*
 * @test
 * @bug 4113040
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Checks that IMStatusBar does not affect Frame layout
 * @run main/manual/othervm -Duser.language=ja -Duser.country=JP IMStatusBar
 */

public class IMStatusBar {

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                If the window appears the right size, but then resizes so that the
                status field overlaps the bottom label, press Fail; otherwise press Pass.
                """;

        PassFailJFrame.builder()
                .title("IMStatusBar Instruction")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(IMStatusBar::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame f = new Frame();
        Panel centerPanel = new Panel();
        f.setSize(200, 200);
        f.setLayout(new BorderLayout());
        f.add(new Label("Top"), BorderLayout.NORTH);
        f.add(centerPanel, BorderLayout.CENTER);
        f.add(new Label("Bottom"), BorderLayout.SOUTH);
        centerPanel.setLayout(new BorderLayout());
        centerPanel.add(new TextField("Middle"), BorderLayout.CENTER);
        centerPanel.validate();
        return f;
    }
}
