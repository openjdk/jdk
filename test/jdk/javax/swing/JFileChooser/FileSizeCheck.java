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

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.Robot;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import javax.swing.AbstractButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/*
 * @test
 * @bug 8288882
 * @key headful
 * @requires (os.family == "linux")
 * @summary Verifies if the size of an empty file is shown as 0.0 KB
 *          as well as checks the displayed file sizes are rounded up
 * @run main FileSizeCheck
 */
public class FileSizeCheck {
    private enum FileSize {
        F0(    0, "0.0 KB"),
        F1(    1, "0.1 KB"),

        F99(  99, "0.1 KB"),
        F100(100, "0.1 KB"),
        F101(101, "0.2 KB"),
        F149(149, "0.2 KB"),
        F150(150, "0.2 KB"),
        F151(151, "0.2 KB"),
        F900(900, "0.9 KB"),
        F901(901, "1.0 KB"),

        F999_000(999_000, "999.0 KB"),
        F999_001(999_001, "999.1 KB"),
        F999_900(999_900, "999.9 KB"),
        F999_901(999_901,   "1.0 MB"),

        F1_000_000(1_000_000, "1.0 MB"),
        F1_000_001(1_000_001, "1.1 MB"),
        F1_000_900(1_000_900, "1.1 MB"),
        F1_001_000(1_001_000, "1.1 MB"),
        F1_100_000(1_100_000, "1.1 MB"),
        F1_100_001(1_100_001, "1.2 MB"),

        F2_800_000(2_800_000, "2.8 MB"),

//        F1_000_000_000(1_000_000_000, "1.0 GB"),
//        F1_000_000_001(1_000_000_001, "1.1 GB"),
        ;

        public final String name;
        public final long size;
        public final String renderedSize;

        private Path path;

        FileSize(long size, String renderedSize) {
            this.name = String.format("%03d-%010d.test", ordinal(), size);
            this.size = size;
            this.renderedSize = renderedSize;
        }

        public void create(final Path parent) {
            path = parent.resolve(name);
            if (!Files.exists(path)) {
                try (var f = new RandomAccessFile(path.toFile(), "rw")) {
                    f.setLength(size);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public void delete() {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                e.printStackTrace();
                // Don't propagate
            }
        }
    }

    private static JFrame frame;
    private static JFileChooser fc;

    private static final AtomicReference<String> error = new AtomicReference<>();

    private static void createUI() {
        // Create temp files
        Path dir = Paths.get(".");
        Arrays.stream(FileSize.values())
              .forEach(f -> f.create(dir));

        fc = new JFileChooser();
        fc.setControlButtonsAreShown(false);
        fc.setCurrentDirectory(dir.toFile());

        frame = new JFrame("JFileChooser File Size test");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.add(fc);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void disposeUI() {
        if (frame != null) {
            frame.dispose();
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        Locale.setDefault(Locale.US);
        try {
            final Robot robot = new Robot();
            SwingUtilities.invokeAndWait(FileSizeCheck::createUI);

            robot.waitForIdle();
            robot.delay(500);
            SwingUtilities.invokeAndWait(FileSizeCheck::clickDetails);

            robot.waitForIdle();
            robot.delay(500);
            SwingUtilities.invokeAndWait(FileSizeCheck::checkFileSizes);

            if (error.get() != null) {
                throw new Error(error.get());
            }
        } finally {
            Arrays.stream(FileSize.values())
                  .forEach(FileSize::delete);

            SwingUtilities.invokeAndWait(FileSizeCheck::disposeUI);
        }
    }

    private static void checkFileSizes() {
        final JTable table = findTable(fc);
        if (table == null) {
            throw new Error("Didn't find JTable in JFileChooser");
        }

        String firstError = null;
        int row = findFirstFileRow(table);
        for (FileSize f : FileSize.values()) {
            String fcSize = getCellRenderedText(table, row++, 1);
            if (!f.renderedSize.equals(fcSize)) {
                String errMsg = "Wrong rendered size for " + f + ": "
                                + fcSize + " vs. " + f.renderedSize;
                if (firstError == null) {
                    firstError = errMsg;
                }
                System.err.println(errMsg);
            }
        }
        if (firstError != null) {
            error.set(firstError);
        }
    }

    private static int findFirstFileRow(final JTable table) {
        for (int i = 0; i < table.getRowCount(); i++) {
            if (FileSize.F0.name.equals(getCellRenderedText(table, i, 0))) {
                return i;
            }
        }
        throw new Error("Didn't find the first file name in the table");
    }

    private static String getCellRenderedText(final JTable table,
                                              final int row,
                                              final int column) {
        Component renderer =
                table.getCellRenderer(row, column)
                     .getTableCellRendererComponent(table,
                                                    table.getValueAt(row, column),
                                                    false, false,
                                                    row, column);
        return ((JLabel) renderer).getText();
    }

    private static void clickDetails() {
        AbstractButton details = findDetailsButton(fc);
        if (details == null) {
            throw new Error("Didn't find 'Details' button in JFileChooser");
        }
        details.doClick();
    }

    private static AbstractButton findDetailsButton(final Container container) {
        Component result = findComponent(container,
                c -> c instanceof JToggleButton button
                     && "Details".equals(button.getToolTipText()));
        return (AbstractButton) result;
    }

    private static JTable findTable(final Container container) {
        Component result = findComponent(container,
                                         c -> c instanceof JTable);
        return (JTable) result;
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
