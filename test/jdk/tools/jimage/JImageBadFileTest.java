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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.regex.Pattern.quote;

/*
 * @test
 * @summary Tests to verify behavior for "invalid" jimage files
 * @library /test/lib
 * @modules jdk.jlink/jdk.tools.jimage
 * @build jdk.test.lib.Asserts
 * @run main JImageBadFileTest
 */
public class JImageBadFileTest extends JImageCliTest {
    // src/java.base/share/native/libjimage/imageFile.hpp
    //
    //      31 -------- bits -------- 0
    //  IDX +-------------------------+
    //   0  |   Magic (0xCAFEDADA)    |
    //      +------------+------------+
    //   1  | Major Vers | Minor Vers |
    //      +------------+------------+
    //   2  |          Flags          |
    //      +-------------------------+
    //   3  |      Resource Count     |
    //      +-------------------------+
    //   4  |       Table Length      |
    //      +-------------------------+
    //   5  |      Attributes Size    |
    //      +-------------------------+
    //   6  |       Strings Size      |
    //      +-------------------------+
    private static final int HEADER_SIZE_BYTES = 7 * 4;

    /**
     * Helper to copy the default jimage file for the runtime under test and
     * allow it to be corrupted in various ways.
     *
     * @param label    label for the temporary file (arbitrary debug name)
     * @param maxLen   maximum number of bytes to copy (-1 to copy all)
     * @param headerFn function which may corrupt specific header values
     * @return the path of a temporary jimage file in the test directory containing
     * the possibly corrupted jimage file (caller should delete)
     */
    private Path writeModifiedJimage(String label, int maxLen, Consumer<IntBuffer> headerFn)
            throws IOException {
        int remaining = maxLen >= 0 ? maxLen : Integer.MAX_VALUE;
        Path dst = Files.createTempFile(Path.of("."), "modules-" + label, "");
        try (InputStream rest = Files.newInputStream(Path.of(getImagePath()), READ);
             OutputStream out = Files.newOutputStream(dst, TRUNCATE_EXISTING)) {
            ByteBuffer bytes = ByteBuffer.wrap(rest.readNBytes(HEADER_SIZE_BYTES));
            bytes.order(ByteOrder.nativeOrder());
            headerFn.accept(bytes.asIntBuffer());
            int headerSize = Math.min(remaining, HEADER_SIZE_BYTES);
            out.write(bytes.array(), 0, headerSize);
            remaining -= headerSize;
            if (remaining > 0) {
                byte[] block = new byte[8192];
                do {
                    int copySize = Math.min(remaining, block.length);
                    out.write(block, 0, rest.readNBytes(block, 0, copySize));
                    remaining -= copySize;
                } while (rest.available() > 0 && remaining > 0);
            }
            return dst.toAbsolutePath();
        } catch (IOException e) {
            Files.deleteIfExists(dst);
            throw e;
        }
    }

    public void testBadMagicNumber() throws IOException {
        // Flip some bits in the magic number.
        Path tempJimage = writeModifiedJimage("bad_magic", -1, b -> b.put(0, b.get(1) ^ 0x1010));
        try {
            JImageResult result = jimage("info", tempJimage.toString());
            result.assertShowsError();
            assertMatches(quote("Unable to open"), result.output);
            assertMatches(quote("is not an image file"), result.output);
        } finally {
            Files.delete(tempJimage);
        }
    }

    public void testMismatchedVersion() throws IOException {
        // Add one to minor version (lowest bits).
        Path tempJimage = writeModifiedJimage("bad_version", -1, b -> b.put(1, b.get(1) + 1));
        try {
            JImageResult result = jimage("info", tempJimage.toString());
            result.assertShowsError();
            assertMatches(quote("Unable to open"), result.output);
            assertMatches(quote("<JAVA_HOME>/bin/jimage"), result.output);
            assertMatches(quote("not the correct version"), result.output);
            assertMatches("Major: \\d+", result.output);
            assertMatches("Minor: \\d+", result.output);
        } finally {
            Files.delete(tempJimage);
        }
    }

    public void testTruncatedHeader() throws IOException {
        // Copy less than the header.
        Path tempJimage = writeModifiedJimage("truncated_header", HEADER_SIZE_BYTES - 4, b -> {});
        try {
            JImageResult result = jimage("info", tempJimage.toString());
            result.assertShowsError();
            assertMatches(quote("Unable to open"), result.output);
            assertMatches(quote("is not an image file"), result.output);
        } finally {
            Files.delete(tempJimage);
        }
    }

    public void testTruncatedData() throws IOException {
        // Copy more than the header, but definitely less than the whole file.
        Path tempJimage = writeModifiedJimage("truncated_data", HEADER_SIZE_BYTES + 1024, b -> {});
        try {
            JImageResult result = jimage("info", tempJimage.toString());
            result.assertShowsError();
            assertMatches(quote("Unable to open"), result.output);
            assertMatches("image file \".*\" is corrupted", result.output);
        } finally {
            Files.delete(tempJimage);
        }
    }

    public void testGoodFileCopy() throws IOException {
        // Self test that the file copying isn't itself corrupting anything.
        Path tempJimage = writeModifiedJimage("good_file", -1, b -> {});
        try {
            jimage("info", tempJimage.toString()).assertSuccess();
        } finally {
            Files.delete(tempJimage);
        }
    }

    public static void main(String[] args) throws Throwable {
        new JImageBadFileTest().runTests();
    }
}
