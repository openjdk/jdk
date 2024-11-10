/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4131642
 * @summary This test shows the ability to create Frames, Windows
 *          and Canvases with a GraphicsConfiguration. The test should show a number
 *          of windows with RGB stripes in according to the number of the
 *          GraphicsConfigurations for each screen. It also displays the size of
 *          the screen and the GraphicsConfiguration.toString().
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual NonDefaultGC
 */

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class NonDefaultGC {

    private static final String INSTRUCTIONS = """
        This test shows the ability to create Frames, Windows and Canvases
        with a GraphicsConfiguration.
        The test should show a number of windows with RGB stripes according
        to the number of the GraphicsConfigurations for each screen.
        The window also contains text which displays the size of the screen
        and the output GraphicsConfiguration.toString().
        The test passes if every screen displays at least one such window.
        """;

    public static void main(String[] argv) throws Exception {
       SwingUtilities.invokeAndWait(NonDefaultGC::createUI);
       PassFailJFrame.builder()
                .title("GraphicsConfigurationTest")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(12)
                .columns(45)
                .build()
                .awaitAndCheck();

    }

    private static void createUI() {

        GraphicsEnvironment ge = GraphicsEnvironment.
                                 getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();
        for (int j = 0; j < gs.length; j++) {
            GraphicsDevice gd = gs[j];
            GraphicsConfiguration[] gc = gd.getConfigurations();
            for (int i=0; i < gc.length; i++) {
                JFrame f = new JFrame(gs[j].getDefaultConfiguration());
                PassFailJFrame.addTestWindow(f); // to ensure it is disposed.
                GCCanvas c = new GCCanvas(gc[i]);
                Rectangle gcBounds = gc[i].getBounds();
                int xoffs = gcBounds.x;
                int yoffs = gcBounds.y;
                f.getContentPane().add(c);
                f.setTitle("Screen# "+ j +", GC# "+ i);
                f.setSize(300, 150);
                f.setLocation((i*50)+xoffs, (i*60)+yoffs);
                f.show();
            }
        }
    }
}

class GCCanvas extends Canvas {

    GraphicsConfiguration gc;
    Rectangle bounds;

    public GCCanvas(GraphicsConfiguration gc) {
        super(gc);
        this.gc = gc;
        bounds = gc.getBounds();
    }

    public Dimension getPreferredSize() {
        return new Dimension(300, 150);
    }

    public void paint(Graphics g) {
        g.setColor(Color.red);
        g.fillRect(0, 0, 100, 150);
        g.setColor(Color.green);
        g.fillRect(100, 0, 100, 150);
        g.setColor(Color.blue);
        g.fillRect(200, 0, 100, 150);
        g.setColor(Color.black);
        g.drawString("ScreenSize="+bounds.width+"X"+ bounds.height, 10, 15);
        g.drawString(gc.toString(), 10, 30);
    }
}
