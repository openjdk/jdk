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
 * @bug 4587721
 * @summary Tests if JFileChooser details view chops off text
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4587721
 */

import java.awt.Font;
import java.util.Enumeration;

import javax.swing.JFileChooser;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.MetalLookAndFeel;

public class bug4587721 {

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(new MetalLookAndFeel());

        String instructions = """
                Click on the Details button in JFileChooser Window.
                If the filename text is chopped off by height,
                then Press FAIL else Press PASS.
                """;

        PassFailJFrame.builder()
                .title("bug4587721")
                .instructions(instructions)
                .columns(40)
                .testUI(bug4587721::createUI)
                .build()
                .awaitAndCheck();
    }

    public static JFileChooser createUI() {
        setFonts();
        JFileChooser fc = new JFileChooser();
        return fc;
    }

    public static void setFonts() {
        UIDefaults defaults = UIManager.getDefaults();
        Enumeration keys = defaults.keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (defaults.get(key) instanceof Font)
                UIManager.put(key, new FontUIResource(new Font("Courier", Font.BOLD, 30)));
        }
    }
}
