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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Robot;
import java.io.File;
import java.util.function.Predicate;

import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileView;
import javax.swing.plaf.metal.MetalLookAndFeel;

/*
 * @test
 * @bug 6616245
 * @key headful
 * @summary Test to check if NPE occurs when using custom FileView.
 * @run main FileViewNPETest
 */
public class FileViewNPETest {

    private static JFrame frame;
    private static JFileChooser jfc;
    private static File path;

    public static void main(String[] args) throws Exception {
        final Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(FileViewNPETest::initialize);
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(() -> {
                jfc.setCurrentDirectory(path.getParentFile());
                if (null != jfc.getCurrentDirectory()) {
                    // The current directory to become null because
                    // the parent directory is not traversable
                    throw new RuntimeException("Current directory is not null");
                }
            });
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(() -> {
                JComboBox<?> dirs = findDirectoryComboBox(jfc);
                // No NPE is expected
                dirs.setSelectedIndex(dirs.getSelectedIndex());
                if (!jfc.getCurrentDirectory().equals(path)) {
                    throw new RuntimeException("The current directory is not restored");
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void initialize() {
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (UnsupportedLookAndFeelException e) {
            throw new RuntimeException(e);
        }

        String userHome = System.getProperty("user.home");
        String docs = userHome + File.separator + "Documents";
        path = new File((new File(docs).exists()) ? docs : userHome);

        jfc = new JFileChooser();
        jfc.setCurrentDirectory(path);
        jfc.setFileView(new CustomFileView(path.getPath()));
        jfc.setControlButtonsAreShown(false);

        frame = new JFrame("JFileChooser FileView NPE test");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(jfc, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JComboBox<?> findDirectoryComboBox(final Container container) {
        Component result = findComponent(container,
                                         c -> c instanceof JComboBox<?>);
        return (JComboBox<?>) result;
    }

    private static Component findComponent(final Container container,
                                           final Predicate<Component> predicate) {
        for (Component child : container.getComponents()) {
            if (predicate.test(child)) {
                return child;
            }
            if (child instanceof Container cont && cont.getComponentCount() > 0) {
                Component result = findComponent(cont, predicate);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}

class CustomFileView extends FileView {
    private final String basePath;

    public CustomFileView(String path) {
        basePath = path;
    }

    @Override
    public Boolean isTraversable(File filePath) {
        return ((filePath != null) && (filePath.isDirectory()))
                && filePath.getAbsolutePath().startsWith(basePath);
    }
}
