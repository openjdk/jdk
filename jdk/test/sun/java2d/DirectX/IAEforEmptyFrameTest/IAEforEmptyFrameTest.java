/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6668439
 * @summary Verifies that no exceptions are thrown when frame is resized to 0x0
 * @author Dmitri.Trembovetski@sun.com: area=Graphics
 * @run main/othervm IAEforEmptyFrameTest
 * @run main/othervm -Dsun.java2d.d3d=false IAEforEmptyFrameTest
 */
import javax.swing.JFrame;

public class IAEforEmptyFrameTest {
    public static void main(String[] args) {
        JFrame f = null;
        try {
            f = new JFrame("IAEforEmptyFrameTest");
            f.setUndecorated(true);
            f.setBounds(100, 100, 320, 240);
            f.setVisible(true);
            try { Thread.sleep(1000); } catch (Exception z) {}
            f.setBounds(0, 0, 0, 0);
            try { Thread.sleep(1000); } catch (Exception z) {}
            f.dispose();
        } finally {
            f.dispose();
        };
    }
}
