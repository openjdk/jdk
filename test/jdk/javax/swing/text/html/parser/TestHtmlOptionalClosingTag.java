/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8296660
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Verifies Swing HTML table with omitted closing tags parsed correctly
 * @run main/manual TestHtmlOptionalClosingTag
 */

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

public class TestHtmlOptionalClosingTag {

    private static JFrame frame;

    private static final String INSTRUCTIONS =
        " A Swing JLabel with a two-column HTML table is shown\n " +
        " If the table is shown without any alignment issue\n " +
        " then press Pass, otherwise test fails";

    public static void createAndShowGUI() {
        frame = new JFrame();
        String html = "<html><table>" +
            "<tr><th align=right>Name:<td>sync-001.mp4" +
            "<tr><th align=right>Modified:<td>2017-Jul-31, 00:14:55" +
            "<tr><th align=right>File size:<td>3.1 MB" +
            "<tr><th align=right>Duration:<td>1m03s" +
            "<tr><th align=right>Video:<td>854 x 480 - 16:9 - 30.0 fps - 271 kbps - H.264 / AVC" +
            "<tr><th align=right>Audio:<td>Stereo - 44100 Hz - 123 kbps - AAC";

        JLabel label = new JLabel(html);
        frame.add(label);
        frame.setSize(400, 400);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }


    public static void main(String[] argv) throws Exception {
        PassFailJFrame passFailJFrame = new PassFailJFrame(
                "SwingHtmlTable Test", INSTRUCTIONS, 5);
        SwingUtilities.invokeAndWait(() -> createAndShowGUI());
        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(
                frame, PassFailJFrame.Position.HORIZONTAL);
        passFailJFrame.awaitAndCheck();
    }
}

