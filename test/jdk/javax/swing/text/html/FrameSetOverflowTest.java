/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8393731
 * @summary Verifies integer overflow in FrameSetView.parseDigits()
 * @run main FrameSetOverflowTest
 */

import java.awt.Rectangle;
import java.awt.Shape;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.html.HTML;

public class FrameSetOverflowTest {
    private static int firstFrameWidth(View view, Shape area) {
        if (view.getElement().getAttributes()
                .getAttribute(StyleConstants.NameAttribute)
            == HTML.Tag.FRAME) {
            return area.getBounds().width;
        }
        for (int i = 0; i < view.getViewCount(); i++) {
            Shape childArea = view.getChildAllocation(i, area);
            if (childArea != null) {
                int width = firstFrameWidth(view.getView(i), childArea);
                if (width >= 0) return width;
            }
        }
        return -1;
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JEditorPane pane = new JEditorPane();
            pane.setEditable(false);
            pane.setContentType("text/html");

            // Set 2 columns,
            // first column overflows parser if bug exists
            // and 2nd column uses * to receive remaining space
            pane.setText("<html><frameset cols='4294967296%,*'>"
                       + "<frame><frame></frameset></html>");

            Rectangle area = new Rectangle(0, 0, 400, 200);
            View root = pane.getUI().getRootView(pane);
            root.setSize(area.width, area.height);

            // Find the first frame and measure its allocation
            int width = firstFrameWidth(root, area);
            System.out.println("First frame width: " + width);
            if (width < 0) {
                throw new RuntimeException("No frame view found");
            }

            // The first frame’s oversized percentage
            // should normalize to 100%, so it should receive
            // most of the available width of 400
            // The test checks that it receives more than half,
            // avoiding dependence on an exact pixel count.
            if (width <= area.width / 2) {
                throw new RuntimeException(
                    "Oversized percentage lost its allocation");
            }
        });
    }
}
