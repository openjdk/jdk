/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @requires (os.family == "mac")
 * @bug 8269951
 * @summary Test checks that focus is painted on JButton even
 *          when borders turned off
 * @library ../../regtesthelpers
 * @build Util
 * @run main AquaButtonFocusTest
 */


import javax.swing.JButton;
import javax.swing.UIManager;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

public class AquaButtonFocusTest {
    public static void main(String[] args) {
        new AquaButtonFocusTest().performTest();
    }

    public void performTest() {
        try {
            UIManager.setLookAndFeel("com.apple.laf.AquaLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException("Can not initialize Aqua L&F");
        }

        FocusableButton one = new FocusableButton("One");
        one.setSize(100, 100);
        one.setBorderPainted(false);
        BufferedImage noFocus = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics g = noFocus.createGraphics();
        one.paint(g);
        g.dispose();
        BufferedImage focus = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        one.setFocusOwner(true);
        g = focus.createGraphics();
        one.paint(g);
        g.dispose();
        if (Util.compareBufferedImages(noFocus, focus)) {
            throw new RuntimeException("Focus is not painted on JButton");
        }
    }

    class FocusableButton extends JButton {
        private boolean focusOwner = false;

        public FocusableButton(String label) {
            super(label);
        }

        public void setFocusOwner(boolean focused) {
            this.focusOwner = focused;
        }

        @Override
        public boolean isFocusOwner() {
            return focusOwner;
        }

        @Override
        public boolean hasFocus() {
            return this.focusOwner;
        }
    }
}
