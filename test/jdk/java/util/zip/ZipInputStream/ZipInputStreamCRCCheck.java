/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8354799
 * @summary verifies that when using java.util.zip.ZipInputStream any incorrect CRC values for
 *          entries results in an exception when reading those entries
 * @run junit ZipInputStreamCRCCheck
 */
public class ZipInputStreamCRCCheck {

    static List<Arguments> args() {
        final int[] entrySizes = {10, 0, 1024};
        final int[] methods = new int[]{ZipOutputStream.STORED, ZipOutputStream.DEFLATED};
        final List<Arguments> args = new ArrayList<>();
        for (int method : methods) {
            for (int size : entrySizes) {
                args.add(Arguments.of(method, size));
            }
        }
        return args;
    }

    /*
     * Creates a ZIP file containing an entry whose CRC value is set to an incorrect
     * value. The test then uses java.util.zip.ZipInputStream to read that ZIP file
     * and verifies that reading the entry's contents results in a ZipException due
     * to the incorrect CRC value.
     */
    @ParameterizedTest
    @MethodSource("args")
    void test(final int entryMethod, final int entrySize) throws Exception {
        final String entryName = entryMethod == ZipOutputStream.STORED
                ? "stored-entry-size=" + entrySize
                : "deflated-entry-size=" + entrySize;
        System.out.println("creating ZIP with entry " + entryName);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final ZipOutputStream zos = new ZipOutputStream(baos)) {

            final ZipEntry entry = new ZipEntry(entryName);
            entry.setSize(entrySize);
            entry.setMethod(entryMethod);

            final byte[] entryContent = new byte[entrySize];
            final CRC32 crc = new CRC32();
            crc.update(entryContent);
            entry.setCrc(crc.getValue());

            zos.putNextEntry(entry);
            zos.write(entryContent);
            zos.closeEntry();
        }
        final byte[] zipContent = baos.toByteArray();
        // now change the CRC value of the entry to an incorrect value
        changeEntryCRCValue(zipContent);
        // use ZipInputStream to read the entry from that ZIP containing the incorrect CRC value
        try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            final ZipEntry entry = zis.getNextEntry();
            assertEquals(entryName, entry.getName(), "unexpected entry name");
            try {
                zis.readAllBytes();
                zis.closeEntry();
                fail(entryName + " entry was expected to fail with a CRC error,"
                        + " but didn't. computed crc=" + entry.getCrc());
            } catch (ZipException zex) {
                final String msg = zex.getMessage();
                if (msg != null && msg.contains("invalid entry CRC")) {
                    System.out.println("got expected CRC failure for entry " + entryName);
                } else {
                    throw zex;
                }
            }
        }
    }

    /*
     * Updates the CRC value of the (sole) entry in the ZIP to an incorrect value
     */
    private static void changeEntryCRCValue(final byte[] zipContent) {
        final ByteBuffer bb = ByteBuffer.wrap(zipContent).order(ByteOrder.LITTLE_ENDIAN);
        final boolean hasDataDescriptor = (bb.position(6).getShort() & 8) != 0;
        // the offset corresponding to either the entry's LOC or the entry's data descriptor
        // where the CRC value should be updated
        final int crcOffset;
        // the CRC value that's currently present at the determined CRC offset
        final int currentCRC;
        if (hasDataDescriptor) {
            final int ddSigStart = findDataDescriptor(bb);
            // CRC value resides 4 bytes after the data descriptor signature
            crcOffset = ddSigStart + 4;
        } else {
            // CRC value resides 14 bytes after the start of the LOC (which starts at the
            // beginning of the ZIP content)
            crcOffset = 14;
        }
        currentCRC = bb.position(crcOffset).getInt();
        // update the CRC value to an arbitrary value
        bb.position(crcOffset).putInt(currentCRC + 1);
        System.out.println("tampered the crc value"
                + " from 0x" + Integer.toHexString(currentCRC)
                + " to 0x" + Integer.toHexString(bb.position(crcOffset).getInt()));
    }

    /*
     * Finds and returns the offset where the data descriptor starts within the ZIP content
     */
    private static int findDataDescriptor(final ByteBuffer zipContent) {
        final int DD_SIG = 0x08074b50; // (standard) data descriptor signature
        zipContent.rewind();
        while (zipContent.hasRemaining()) {
            final int pos = zipContent.position();
            if (zipContent.remaining() < 4) {
                break; // not enough content
            }
            final int fourBytes = zipContent.getInt(pos);
            if (fourBytes == DD_SIG) {
                return pos;
            }
            zipContent.position(pos + 1);
        }
        throw new RuntimeException("Missing data descriptor");
    }
}
