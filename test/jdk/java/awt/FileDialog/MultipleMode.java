/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextArea;
import java.io.File;

/*
 * @test
 * @bug 6467204
 * @summary Need to implement "extended" native FileDialog for JFileChooser
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MultipleMode
*/

public class MultipleMode {

    private static final String INSTRUCTIONS =
            """
             1. Verify that the 'multiple' checkbox is off and press the 'open' button
             2. Verify that the file dialog doesn't allow the multiple file selection
             3. Select any file and close the file dialog
             4. The results will be displayed, verify the results
             5. Turn the 'multiple' checkbox on and press the 'open' button
             6. Verify that the file dialog allows the multiple file selection
             7. Select several files and close the file dialog
             8. The results will be displayed, verify the results.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame
            .builder()
            .title("MultipleMode test instructions")
            .instructions(INSTRUCTIONS)
            .rows(15)
            .columns(40)
            .position(PassFailJFrame.Position.TOP_LEFT_CORNER)
            .testUI(MultipleMode::init)
            .build()
            .awaitAndCheck();
    }

    private static Frame init() {
        Frame frame = new Frame("MultipleMode");
        TextArea sysout = new TextArea("", 20, 70);
        sysout.setEditable(false);

        final Checkbox mode = new Checkbox("multiple", false);

        Button open = new Button("open");
        open.addActionListener(e -> {
            FileDialog d = new FileDialog(frame);
            d.setMultipleMode(mode.getState());
            d.setVisible(true);

            // print the results
            sysout.append("DIR:\n");
            sysout.append("  %s\n".formatted(d.getDirectory()));
            sysout.append("FILE:\n");
            sysout.append("  %s\n".formatted(d.getFile()));
            sysout.append("FILES:\n");
            for (File f : d.getFiles()) {
                sysout.append("  %s\n".formatted(f));
            }
        });

        Panel panel = new Panel(new FlowLayout());
        panel.add(mode);
        panel.add(open);

        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.NORTH);
        frame.add(sysout, BorderLayout.CENTER);

        frame.pack();

        return frame;
    }
}
