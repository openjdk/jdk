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


import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;

/*
 * @test
 * @bug 4023385
 * @summary resizing a frame causes too many repaints
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FramePaintTest
*/

public class FramePaintTest {
    private static final String INSTRUCTIONS = """
            You should see a Frame titled "Repaint Test", filled with colored blocks.

            Resize the frame several times, both inward as well as outward.

            The blocks should move to fill the window without any flashes or
            glitches which ensures that repaint is not done excessively
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("FramePaintTest Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(ResizeLW::new)
                .build()
                .awaitAndCheck();
    }

    static class ResizeLW extends Frame {

        public ResizeLW() {
            super("Repaint Test");
            setBackground(Color.red);
            setLayout(new FlowLayout());
            setSize(300, 300);

            for (int i = 0; i < 10; i++) {
                add(new ColorComp(Color.blue));
                add(new ColorComp(Color.green));
            }
        }

        private static class ColorComp extends Component {
            public ColorComp(Color c) {
                super();
                setBackground(c);
            }

            public void paint(Graphics g) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }

            public Dimension getPreferredSize() {
                return new Dimension(50, 50);
            }
        }
    }
}
