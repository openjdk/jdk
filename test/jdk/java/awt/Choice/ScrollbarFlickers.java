/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6405707
 * @summary Choice popup & scrollbar gets Flickering when mouse is pressed & drag on the scrollbar
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ScrollbarFlickers
 */

import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Frame;
import java.lang.reflect.InvocationTargetException;

public class ScrollbarFlickers extends Frame {
    static final String INSTRUCTIONS = """
            Open the choice popup. Select any item in it and
            drag it with the mouse above or below the choice.
            Keep the choice opened.
            Continue dragging the mouse outside of the choice
            making content of the popup scroll.
            If you see that scrollbar flickers press Fail.
            Otherwise press Pass.
            """;

    public ScrollbarFlickers() {
        super("Scrollbar Flickering Test");
        Choice ch = new Choice();
        setLayout(new BorderLayout());
        ch.add("Praveen");
        ch.add("Mohan");
        ch.add("Rakesh");
        ch.add("Menon");
        ch.add("Girish");
        ch.add("Ramachandran");
        ch.add("Elancheran");
        ch.add("Subramanian");
        ch.add("Raju");
        ch.add("Pallath");
        ch.add("Mayank");
        ch.add("Joshi");
        ch.add("Sundar");
        ch.add("Srinivas");
        ch.add("Mandalika");
        ch.add("Suresh");
        ch.add("Chandar");
        add(ch);
        setSize(200, 200);
        validate();
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Test Instructions")
                .testUI(ScrollbarFlickers::new)
                .instructions(INSTRUCTIONS)
                .columns(40)
                .build()
                .awaitAndCheck();
    }
}
