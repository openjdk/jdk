/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;

/*
 * @test
 * @bug 4116029
 * @summary drawString does not honor clipping regions for lightweight components
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual LightweightCliprect
 */

public class LightweightCliprect {

    private static final String INSTRUCTIONS = """
            If some text is drawn outside the red rectangle, press "Fail" button.
            Otherwise, press "Pass" button.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                .title("LightweightCliprect Instructions Frame")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(10)
                .columns(45)
                .build();

        EventQueue.invokeAndWait(() -> {
            Frame frame = new Frame("DefaultSize");

            Container panel = new MyContainer();
            MyComponent c = new MyComponent();
            panel.add(c);

            frame.add(panel);
            frame.setSize(400, 300);

            PassFailJFrame.addTestWindow(frame);
            PassFailJFrame
                    .positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);

            frame.setVisible(true);
        });

        passFailJFrame.awaitAndCheck();
    }
}

class MyComponent extends Component {

    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(Color.red);
        g.fillRect(20, 20, 400, 200);
        Shape clip = g.getClip();
        g.setClip(20, 20, 400, 200);
        //draw the current java version in the component
        g.setColor(Color.black);
        String version = System.getProperty("java.version");
        String vendor = System.getProperty("java.vendor");
        int y = 10;
        for(int i = 0; i < 30; i++) {
            g.drawString("Lightweight: Java version: " + version +
                         ", Vendor: " + vendor, 10, y += 20);
        }
        g.setColor(c);
        g.setClip(clip);
        super.paint(g);
    }

    public Dimension getPreferredSize() {
        return new Dimension(300, 300);
    }
}

class MyContainer extends Container {
    public MyContainer() {
        super();
        setLayout(new FlowLayout());
    }

    public void paint(Graphics g) {
        Rectangle bounds = new Rectangle(getSize());
        g.setColor(Color.cyan);
        g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
        super.paint(g);
    }
}
