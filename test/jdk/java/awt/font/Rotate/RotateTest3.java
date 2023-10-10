/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 4240228
 * @summary This test is designed to test for a crashing bug in the zh
 *          locale on Solaris. Rotated text should be displayed, but
 *          anything other than a crash passes the specific test.
 *          For example, the missing glyph empty box may be displayed
 *          in some locales, or no text at all.
 */

public class RotateTest3 extends Panel {
    static JFrame frame;

    protected Java2DView java2DView;

    public RotateTest3(){
        this.setLayout(new GridLayout(1, 1));
        this.setSize(300, 300);
        this.java2DView = new Java2DView();
        this.add(this.java2DView);
    }

    public static void main(String[] s) throws Exception {
        try {
            SwingUtilities.invokeAndWait(RotateTest3::initAndShowGui);
            Thread.sleep(1000);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void initAndShowGui() {
        RotateTest3 panel = new RotateTest3();

        frame = new JFrame("RotateTest3");
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                frame.dispose();
            }
        });
        frame.getContentPane().setLayout(new GridLayout(1, 1));
        frame.getContentPane().add("Center", panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    static public class Java2DView extends Component {

        public void paint(Graphics g){
            Graphics2D g2d = (Graphics2D) g;
            Dimension d = this.getSize();
            g.setColor(this.getBackground());
            g.fillRect(0, 0, d.width, d.height);
            g2d.setPaint(Color.black);

            g2d.translate(150,150);
            g2d.rotate(-Math.PI / 3);

            String testString =
             "\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341";
            g2d.drawString(testString, 0, 0);
        }

        public Dimension getMinimumSize(){
            return new Dimension(300, 300);
        }

        public Dimension getPreferredSize(){
            return new Dimension(300, 300);
        }
    }
}
