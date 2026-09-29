/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.FlowLayout;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

import javax.swing.JFrame;
import javax.swing.JTextField;

import jtreg.SkippedException;

/*
 * @test
 * @bug 4805862
 * @key multimon
 * @requires (os.family == "windows")
 * @summary Tests IM candidate window is positioned correctly for the
 *          text components inside a window in multiscreen configurations, if
 *          this window has negative coordinates
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame
 * @run main/manual IMCandidateWindowTest
 */

public class IMCandidateWindowTest {
    static GraphicsConfiguration gc;

    public static void main(String[] args) throws Exception {
        GraphicsDevice[] gds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                               .getScreenDevices();
        if (gds.length < 2) {
            throw new SkippedException("You have only one monitor in your system" +
                                       " - test skipped");
        }

        GraphicsDevice gd = null;

        for (int i = 0; i < gds.length; i++) {
            gc = gds[i].getDefaultConfiguration();
            if ((gc.getBounds().x < 0) || (gc.getBounds().y < 0)) {
                gd = gds[i];
                break;
            }
        }

        if (gd == null) {
            // no screens with negative coords
            throw new SkippedException("No screens with negative coords - test skipped");
        }

        String INSTRUCTIONS = """
                This test is for windows
                Test requirements: installed support for asian languages
                Chinese (PRC) w/ Chinese QuanPing input method.
                Multiscreen environment where one of the monitors has negative coords
                Go to the text field in the opened Frame. Switch to Chinese language and
                start typing "ka".
                Note, that IM helper window is appeared.
                If this window is appeared near the text field, press PASS button.
                If this window is appeared at the edge of the screen or on another
                screen, press FAIL button""";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(IMCandidateWindowTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createUI() {
        Rectangle b = gc.getBounds();

        JFrame f = new JFrame("Frame", gc);
        f.setBounds(b.x + b.width / 2 - 150, b.y + b.height / 2 - 100, 300, 200);
        f.getContentPane().setLayout(new FlowLayout());
        JTextField tf = new JTextField(10);
        f.getContentPane().add(tf);
        return f;
    }
}
