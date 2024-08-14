/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4078566 6658398
 * @requires (os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test for a memory leak in Image.
 * @run main/manual MemoryLeakTest
 */

import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

public class MemoryLeakTest {
    private static final String INSTRUCTIONS =
        """
         Do the following steps on Unix platforms.
         Maximize and minimize the Memory Leak Test window.
         Execute the following after minimize.
             ps -al | egrep -i 'java|PPID'
         Examine the size of the process under SZ.
         Maximize and minimize the Memory Leak Test window again.
         Execute the following after minimize.
             ps -al | egrep -i 'java|PPID'
         Examine the size of the process under SZ.
         If the two SZ values are the same, plus or minus one,
         then click Pass, else click Fail.
        """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame
            .builder()
            .title("MemoryLeakTest Instructions")
            .instructions(INSTRUCTIONS)
            .rows(15)
            .columns(40)
            .testUI(MemoryLeak::new)
            .build()
            .awaitAndCheck();
    }
}

class MemoryLeak extends Frame implements ComponentListener {
    private Image osImage;

    public MemoryLeak() {
        super("Memory Leak Test");
        setSize(200, 200);
        addComponentListener(this);
    }

    public static void main(String[] args) {
        new MemoryLeak().start();
    }

    public void start() {
        setVisible(true);
    }

    public void paint(Graphics g) {
        if (osImage != null) {
            g.drawImage(osImage, 0, 0, this);
        }
    }

    public void update(Graphics g) {
        paint(g);
    }

    public void componentResized(ComponentEvent e) {
        Image oldimage = osImage;
        osImage = createImage(getSize().width, getSize().height);
        Graphics g = osImage.getGraphics();
        if (oldimage != null) {
            g.drawImage(oldimage, 0, 0, getSize().width, getSize().height, this);
            oldimage.flush();
        } else {
            g.setColor(Color.blue);
            g.drawLine(0, 0, getSize().width, getSize().height);
        }
        g.dispose();
    }

    public void componentMoved(ComponentEvent e) {}

    public void componentShown(ComponentEvent e) {
        osImage = createImage(getSize().width, getSize().height);
        Graphics g = osImage.getGraphics();
        g.setColor(Color.blue);
        g.drawLine(0, 0, getSize().width, getSize().height);
        g.dispose();
    }

    public void componentHidden(ComponentEvent e) {}

}
