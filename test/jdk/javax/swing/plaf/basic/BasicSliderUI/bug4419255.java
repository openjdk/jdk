/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
import javax.swing.JColorChooser;
import javax.swing.UIManager;

import jtreg.SkippedException;

/*
 * @test
 * @bug 4419255
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame
 * @summary Tests if Metal Slider's thumb isn't clipped
 * @run main/manual bug4419255
 */

public class bug4419255 {

    public static void main(String[] args) throws Exception {

        // ColorChooser UI design is different for GTK L&F.
        // There is no RGB tab available for GTK L&F, skip the testing.
        if (UIManager.getLookAndFeel().getName().contains("GTK")) {
            throw new SkippedException("Test not applicable for GTK L&F");
        }
        String instructions = """
                Choose RGB tab. If sliders' thumbs are painted correctly
                (top is not clipped, black line is visible),
                then test passed. Otherwise it failed.""";

        PassFailJFrame.builder()
                .title("bug4419255")
                .instructions(instructions)
                .columns(40)
                .testUI(bug4419255::createColorChooser)
                .build()
                .awaitAndCheck();
    }

    private static JColorChooser createColorChooser() {
        return new JColorChooser(Color.BLUE);
    }
}
