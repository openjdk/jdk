/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4366799
 * @key headful
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary verifies that Windows applications react to palette
 * changes in 8-bit mode correctly.
 * @run main/manual PaletteTester
*/

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.VolatileImage;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import java.io.File;

public class PaletteTester {

    static VImageColors demo;

    private static final String INSTRUCTIONS = """
        This test should be run on any Windows platform in 8-bit
        (256 color) display mode only. To check for errors, run a browser
        application (Firefox or Internet Explorer) at the same time
        and switch between this test and the browser (by clicking on the
        title bars).

        The three panels in this test should look roughly the same (there
        may be some dithering differences if you switch display modes
        during the test, but the overall look should be the same.  If
        completely different colors are being used (either for the orange
        background fill, the text, the image, or the rectangles), then the
        test has failed.
        """;

    private static void init() {

        int width = 300, height = 300;

        demo = new VImageColors();
        Frame f = new Frame("PaletteTester");
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {}
            public void windowDeiconified(WindowEvent e) { demo.start(); }
            public void windowIconified(WindowEvent e) { demo.stop(); }
        });
        f.add(demo);
        f.setSize(new Dimension(width, height));
        f.setLocationRelativeTo(null);

        PassFailJFrame.addTestWindow(f);
        PassFailJFrame.positionTestWindow(f, PassFailJFrame.Position.HORIZONTAL);
        f.setVisible(true);

        demo.start();

    }//End  init()

    public static void main( String args[] ) throws Exception {

        PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                                        .title("PaletteTester Instructions")
                                        .instructions(INSTRUCTIONS)
                                        .testTimeOut(5)
                                        .rows(15)
                                        .columns(40)
                                        .screenCapture()
                                        .build();

        EventQueue.invokeAndWait(PaletteTester::init);


        try {
            passFailJFrame.awaitAndCheck();
        } finally {
            demo.stop();
        }
    }//main
}

//************ Begin classes defined for the test ****************

class VImageColors extends JPanel implements Runnable {

    VolatileImage vImage;
    Image bImage;
    private static int width = 300, height = 300;
    private Thread thread;
    Color fillColor = new Color(240, 188, 136);
    Color textColor = new Color(40, 18, 97);
    Color rectColor = new Color(0, 150, 0);
    File f = new File(System.getProperty("test.src", "."), "duke.gif");
    Image duke = new ImageIcon(f.toString()).getImage();

    public void initOffscreen() {
        vImage = this.createVolatileImage(getWidth()/3, getHeight());
        bImage = this.createImage(getWidth()/3, getHeight());
    }

    public void paint(Graphics g) {
        int width = getWidth();
        int height = getHeight();

        if (vImage == null) {
            initOffscreen();
        }

        // Render the left panel via VolatileImage
        do {
            if (
                vImage.validate(getGraphicsConfiguration()) ==
                VolatileImage.IMAGE_INCOMPATIBLE)
            {
                vImage = createVolatileImage(width/3, height);
            }
            Graphics vg = vImage.createGraphics();
            vg.setColor(fillColor);
            vg.fillRect(0, 0, width/3, height);
            vg.drawImage(duke, 0, 0, null);
            vg.setColor(textColor);
            vg.drawString("Vol Image", 5, height-1);
            vg.setColor(rectColor);
            vg.drawRect(0, 0, width/3-1, height-1);
            vg.dispose();
            g.drawImage(vImage, 0, 0, width/3, height, null);
        } while (vImage.contentsLost());

        // Render the middle panel via BufferedImage
        Graphics bg = bImage.getGraphics();
        bg.setColor(fillColor);
        bg.fillRect(0, 0, width/3, height);
        bg.drawImage(duke, 0, 0, null);
        bg.setColor(textColor);
        bg.drawString("Buff Image", 5, height-1);
        bg.setColor(rectColor);
        bg.drawRect(0, 0, width/3-1, height-1);
        bg.dispose();
        g.drawImage(bImage, width/3, 0, width/3, height, null);

        // Render the right panel directly to the screen
        g.setColor(fillColor);
        g.fillRect(2*(width/3), 0, width/3, height);
        g.drawImage(duke, 2*(width/3), 0, null);
        g.setColor(textColor);
        g.drawString("Screen", 2*(width/3) + 5, height-1);
        g.setColor(rectColor);
        g.drawRect(2*(width/3), 0, width/3-1, height-1);

    }

    public void start() {
        thread = new Thread(this);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    public synchronized void stop() {
        thread = null;
    }

    public void run() {
        Thread me = Thread.currentThread();
        while (thread == me) {
            try {
                thread.sleep(100);
            } catch (InterruptedException e) { break; }
        }
        thread = null;
    }
}

//************** End classes defined for the test *******************
