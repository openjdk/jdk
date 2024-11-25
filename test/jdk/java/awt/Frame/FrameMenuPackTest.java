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

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.ScrollPane;
import java.awt.Window;
import java.util.List;

/*
 * @test
 * @bug 4084766
 * @summary Test for bug(s): 4084766
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FrameMenuPackTest
 */

public class FrameMenuPackTest {
    private static final String INSTRUCTIONS = """
            Check that both frames that appear are properly packed with
            the scrollpane visible.
            """;

    public static void main(String[] argv) throws Exception {
        PassFailJFrame.builder()
                .title("FrameMenuPackTest Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(FrameMenuPackTest::createAndShowUI)
                .positionTestUIRightRow()
                .build()
                .awaitAndCheck();
    }

    private static List<Window> createAndShowUI() {
        // Frame without menu, packs correctly
        PackedFrame f1 = new PackedFrame(false);
        f1.pack();

        // Frame with menu, doesn't pack right
        PackedFrame f2 = new PackedFrame(true);
        f2.pack();

        return List.of(f1, f2);
    }

    private static class PackedFrame extends Frame {
        public PackedFrame(boolean withMenu) {
            super("PackedFrame");

            MenuBar menubar;
            Menu fileMenu;
            MenuItem foo;
            ScrollPane sp;

            sp = new ScrollPane();
            sp.add(new Label("Label in ScrollPane"));
            System.out.println(sp.getMinimumSize());

            this.setLayout(new BorderLayout());
            this.add(sp, "Center");
            this.add(new Label("Label in Frame"), "South");

            if (withMenu) {
                menubar = new MenuBar();
                fileMenu = new Menu("File");
                foo = new MenuItem("foo");
                fileMenu.add(foo);
                menubar.add(fileMenu);
                this.setMenuBar(menubar);
            }
        }
    }
}
