/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.awt.BorderLayout;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileFilter;

/*
 * @test
 * @bug 8029536
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FileFilterDescription
 */
public final class FileFilterDescription {

     private static final String INSTRUCTIONS = """
         1) Check that current filter in the opened JFileChooser is a "CustomFileFilter".
         2) Close the JFileChooser.
         3) Test will repeat steps 1 - 2 for all supported look and feels.
         4) If it's true for all look and feels then click Pass else click Fail.  """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .title("JFileChooser Filefilter Instructions")
                .instructions(INSTRUCTIONS)
                .rows(10)
                .columns(35)
                .position(PassFailJFrame.Position.TOP_LEFT_CORNER)
                .build();

        final UIManager.LookAndFeelInfo[] infos = UIManager
                .getInstalledLookAndFeels();
        for (final UIManager.LookAndFeelInfo info : infos) {
            SwingUtilities.invokeAndWait(() -> {
                setLookAndFeel(info);
                JFrame frame = new JFrame("JFileChooser FileFilter test");
                final JFileChooser chooser = new JFileChooser();
                chooser.setAcceptAllFileFilterUsed(false);
                chooser.setFileFilter(new CustomFileFilter());
                SwingUtilities.updateComponentTreeUI(chooser);
                frame.add(chooser, BorderLayout.CENTER);
                frame.pack();
                PassFailJFrame.addTestWindow(frame);
                PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.TOP_LEFT_CORNER);
                chooser.showDialog(null, "Open");
            });
        }
        passFailJFrame.awaitAndCheck();
    }

    private static void setLookAndFeel(final UIManager.LookAndFeelInfo info) {
        try {
            UIManager.setLookAndFeel(info.getClassName());
        } catch (ClassNotFoundException | InstantiationException |
                UnsupportedLookAndFeelException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static class CustomFileFilter extends FileFilter {

        @Override
        public boolean accept(final File f) {
            return false;
        }

        @Override
        public String getDescription() {
            return "CustomFileFilter";
        }
    }
}
