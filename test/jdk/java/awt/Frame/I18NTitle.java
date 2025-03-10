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

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Window;

/*
 * @test
 * @bug 6269884 4929291
 * @summary Tests that title which contains mix of non-English characters is displayed correctly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual I18NTitle
 */

public class I18NTitle {
    private static final String INSTRUCTIONS = """
            You will see a frame with some title (S. Chinese, Cyrillic and German).
            Please check if non-English characters are visible and compare
            the visible title with the same string shown in the label
            (it should not look worse than the label).
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("I18NTitle Instructions")
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(I18NTitle::createAndShowGUI)
                .build()
                .awaitAndCheck();
    }

    private static Window createAndShowGUI() {
        String s = "\u4e2d\u6587\u6d4b\u8bd5 \u0420\u0443\u0441\u0441\u043a\u0438\u0439 Zur\u00FCck";
        Frame frame = new Frame(s);
        frame.setLayout(new BorderLayout());
        Label l = new Label(s);
        frame.add(l);
        frame.setSize(400, 100);
        return frame;
    }
}
