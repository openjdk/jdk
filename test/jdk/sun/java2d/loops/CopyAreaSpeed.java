/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import java.util.Date;

/*
 * @test
 * @bug 4189070
 * @summary This test prints out the time it takes for a certain amount of
 * copyArea calls to be completed. Because the performance measurement is
 * relative, this code only provides a benchmark to run with different releases
 * to compare the outcomes.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CopyAreaSpeed
 */

public class CopyAreaSpeed {
    public static void main(String args[]) throws Exception {
        String instructions = """
                    This test prints out the time it takes for a certain amount
                    of copyArea calls to be completed. Because the performance
                    measurement is relative, this code only provides a benchmark
                    to run with different releases to compare the outcomes.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(instructions)
                .rows(5)
                .columns(35)
                .testUI(CopyAreaSpeed::initialize)
                .build()
                .awaitAndCheck();
    }

    public static JFrame initialize() {
        JFrame frame = new JFrame("Copy Area Test");
        frame.add(new CopyAreaSpeedTest());
        frame.setSize(300, 320);
        frame.setVisible(true);
        return frame;
    }
}

class CopyAreaSpeedTest extends Container implements Runnable {
    int top = 0;
    public static String result;

    public CopyAreaSpeedTest() {
        super();
        (new Thread(this)).start();
    }

    public void update(Graphics g) {
        paint(g);
    }

    public void paint(Graphics g) {
        synchronized (this) {
            Rectangle rct = g.getClipBounds();
            g.setColor(Color.white);
            g.fillRect(rct.x, rct.y, rct.width, rct.height);
            g.setFont(getFont());
            g.setColor(Color.black);

            Dimension dm = getSize();
            for (int y = 0; y <= (dm.height + 10); y += 20) {
                if (y > rct.y) {
                    int z = y / 20 + top;
                    g.drawString("" + z, 10, y);
                }
            }
        }
    }

    static long millsec(Date s, Date e) {
        long ts = s.getTime();
        long te = e.getTime();
        return te - ts;
    }

    public void run() {
        int count = 1000;
        int loops = count;
        Date start;
        Date end;

        start = new Date();
        while (count-- > 0) {
            Dimension dm = getSize();
            if (dm != null && dm.width != 0 && dm.height != 0) {
                synchronized (this) {
                    top++;
                    Graphics g = getGraphics();
                    g.copyArea(0, 20, dm.width, dm.height - 20, 0, -20);
                    g.setClip(0, dm.height - 20, dm.width, 20);
                    paint(g);
                    g.dispose();
                }
            }
            try {
                Thread.sleep(1);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        end = new Date();
        Graphics g = getGraphics();
        g.setFont(getFont());
        g.setColor(Color.black);
        result = "copyArea X " + loops + " = " + millsec(start, end) + " msec";
        JOptionPane.showMessageDialog(null, result);
    }
}
