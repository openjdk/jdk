/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JButton;
import java.awt.Frame;
import java.awt.FileDialog;

/*
 * Motif file dialogs let the user specify a filter that controls the files that
 * are displayed in the dialog. This filter is generally specified as a regular
 * expression. The test verifies that Motif-like filtering works fine using
 * XAWT-toolkit also.
 */

/*
 * @test
 * @bug 4934185
 * @summary JCK1.5-runtime-interactive: XToolkit FileDialog does not work as expected
 * @requires (os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/othervm/manual -Dsun.awt.disableGtkFileDialogs=true RegexpFilterTest
*/
public class RegexpFilterTest {

    private static final String INSTRUCTIONS =
            """
            0. The test is only for X platforms
            1. Press the 'Show' button and a file dialog will appear,
            2. Input any string into the 'Filter' text field,
                This filter is generally specified as a regular expression
                (e.g., * matches all files, while *.c matches all files that end in .c)
            3. Press 'Enter' to refresh the displayed files with the filter,
            4. If the list of the files contains all actual files matched the filter,
                then the test passed; otherwise it failed.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame
            .builder()
            .title("RegexpFilterTest Instructions")
            .instructions(INSTRUCTIONS)
            .splitUIRight(() -> {
                JButton show = new JButton("show");
                show.addActionListener(e ->
                        new FileDialog((Frame) null).setVisible(true));
                return show;
            })
            .rows(15)
            .columns(40)
            .build()
            .awaitAndCheck();
    }
}
