/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.swing.ImageIcon;

/*
 * @test
 * @bug 8377924
 * @summary Verifies XBM decoder parses width and height in backward-compatible
 *          way: `-h` corresponds to width; `-ht` corresponds to height
 * @run main XBMDecoderWidthHeight
 */
public final class XBMDecoderWidthHeight {
    private static final PrintStream originalErr = System.err;

    private static final String dir = System.getProperty("test.src", ".");

    private static final int WIDTH = 8;
    private static final int HEIGHT = 1;

    private static final Pattern glob =
            Pattern.compile("valid_WH.?-.*\\.xbm");

    private static boolean matchesGlob(final Path file) {
        return glob.matcher(file.getFileName()
                                .toString())
                   .matches();
    }

    public static void main(String[] args) throws Exception {
        final List<Throwable> errors;

        try (Stream<Path> files = Files.list(Paths.get(dir))) {
            errors = files.filter(XBMDecoderWidthHeight::matchesGlob)
                          .map(XBMDecoderWidthHeight::testFileWrapper)
                          .filter(Objects::nonNull)
                          .toList();
        }

        if (!errors.isEmpty()) {
            errors.stream()
                  .map(Throwable::getMessage)
                  .forEach(System.err::println);
            throw new RuntimeException("Errors found: " + errors.size() + ". "
                                       + errors.get(0).getMessage());
        }
    }

    private static Throwable testFileWrapper(final Path file) {
        try {
            testFile(file);
        } catch (Throwable t) {
            return t;
        }
        return null;
    }

    private static void testFile(final Path file) throws IOException, Error {
        try (ByteArrayOutputStream errContent = new ByteArrayOutputStream()) {
            System.setErr(new PrintStream(errContent));

            ImageIcon icon = new ImageIcon(Files.readAllBytes(file));

            if (errContent.size() != 0) {
                throw new Error(file.getFileName() + " "
                                + errContent.toString().split("\\n")[0]);
            }

            if (icon.getIconWidth() != WIDTH || icon.getIconHeight() != HEIGHT) {
                throw new Error(file.getFileName()
                                + " Unexpected size: "
                                + formatSize(icon.getIconWidth(),
                                             icon.getIconHeight())
                                + " vs " + formatSize(WIDTH, HEIGHT));
            }
        } finally {
            System.setErr(originalErr);
        }
    }

    private static String formatSize(int width, int height) {
        return width + " x " + height;
    }
}
