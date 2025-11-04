/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4186347
 * @summary Tests changing Slider.horizontalThumbIcon UIResource
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4186347
 */

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JSlider;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.IconUIResource;

public class bug4186347 {
    static final String INSTRUCTIONS = """
        If the slider's thumb icon is painted correctly
        (that is centered vertically relative to slider
        channel) then test passed, otherwise it failed.
    """;

    public static void main(String[] args) throws Exception {
        // Set Metal L&F
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        PassFailJFrame.builder()
                .title("bug4186347 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4186347::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("Metal JSlider Icon Test");
        String a = System.getProperty("test.src", ".")
                + System.getProperty("file.separator")
                + "duke.gif";
        Icon icon = new ImageIcon(a);
        IconUIResource iconResource = new IconUIResource(icon);
        UIDefaults defaults = UIManager.getDefaults();
        defaults.put("Slider.horizontalThumbIcon", iconResource);
        JSlider s = new JSlider();
        frame.getContentPane().add(s);
        frame.setSize(250, 150);
        return frame;
    }
}
