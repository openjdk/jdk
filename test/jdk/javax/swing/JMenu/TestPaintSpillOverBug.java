/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6223700
 * @requires (os.family == "windows")
 * @summary Verifies no painting spillover in Win L&F
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestPaintSpillOverBug
 */

import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.UIManager;

public class TestPaintSpillOverBug {

    static final String INSTRUCTIONS = """
        A JMenu "click Me" will be shown. Click on it.
        Position the mouse in the "Slowly Move Mouse Out of This" JMenu
        so that the popup menu appears to the right.
        Slowly move the mouse towards the edge of the item,
        one pixel at a time
        When the mouse hits the edge,
         if the selection background spill over on to the popup
        press Fail else press Pass.""";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");

        PassFailJFrame.builder()
                .title("TestPaintSpillOverBug Instructions")
                .instructions(INSTRUCTIONS)
                .columns(60)
                .testUI(TestPaintSpillOverBug::createUI)
                .position(PassFailJFrame.Position.TOP_LEFT_CORNER)
                .build()
                .awaitAndCheck();

    }

    static JFrame createUI() {
        JFrame f = new JFrame("TestPaintSpillOverBug");
        JMenuBar bar = new JMenuBar();
        JMenu clickMe = new JMenu("Click Me");
        JMenu culprit = new JMenu("Slowly Move Mouse Out of This");
        culprit.add("This item gets partially obscured");
        culprit.add(" ");
        clickMe.add(" ");
        clickMe.addSeparator();
        clickMe.add(culprit);
        clickMe.addSeparator();
        clickMe.add(" ");
        bar.add(clickMe);
        f.setJMenuBar(bar);
        f.setSize(600, 200);
        return f;
    }
}
