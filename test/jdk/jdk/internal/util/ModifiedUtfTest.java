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

/*
 * @test
 * @bug 8366421
 * @summary Test for ModifiedUtf.utfLen() return type change from int to long to avoid overflow
 * @modules java.base/jdk.internal.classfile.impl:+open
 *          java.base/jdk.internal.util
 * @run main/othervm -Xmx4g ModifiedUtfTest
 */

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;

import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.ClassFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jdk.internal.classfile.impl.BufWriterImpl;
import jdk.internal.classfile.impl.ClassFileImpl;
import jdk.internal.util.ModifiedUtf;

public class ModifiedUtfTest {

    static class HeaderCapturedException extends RuntimeException {
    }
    /**
     * Keep only a fixed-length output and stop writing further data
     * by throwing an exception when the limit is exceeded.
     * For testing purposes only.
     */
    static class HeaderCaptureOutputStream extends OutputStream {
        private byte[] head;
        private int count;

        public HeaderCaptureOutputStream(int headSize) {
            this.head = new byte[headSize];
        }

        @Override
        public void write(int b) {
            if (count >= head.length) {
                // Only reserve a fixed-length header and throw an exception to stop writing.
                throw new HeaderCapturedException();
            }
            head[count++] = (byte) b;
        }
        public byte[] get(){
            return head;
        }
    }

    private static final String THREE_BYTE = "\u2600";   // 3-byte UTF-8

    public static void main(String[] args) throws Exception{
        int count = Integer.MAX_VALUE / 3 + 1;
        long expected = 3L * count;
        String largeString = THREE_BYTE.repeat(count);

        long total = ModifiedUtf.utfLen(largeString, 0);
        if (total != expected) {
            throw new RuntimeException("Expected total=" + expected + " but got " + total);
        }

        /**
         * Verifies that the following three methods that call ModifiedUtf.utfLen()
         * correctly handle overflow:
         * - DataOutputStream.writeUTF(String)
         * - BufWriterImpl.writeUtfEntry(String)
         * - ObjectOutputStream.writeUTF(String)
         */
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             DataOutputStream dataOut = new DataOutputStream(byteOut)) {
            dataOut.writeUTF(largeString);
            throw new RuntimeException("Expected UTFDataFormatException was not thrown.");
        } catch (UTFDataFormatException e) {
        }

        /**
         * In the writeUTF function, utfLen is used to calculate the length of the string to be written
         * and store it in the stream header. This test uses the HeaderCaptureOutputStream inner class
         * to capture the header bytes and compare them with the expected length,
         * verifying that utfLen returns the correct value.
         */
        int lengthFieldSize = 8;
        // Offset to UTF length field: 2 bytes STREAM_MAGIC + 2 bytes STREAM_VERSION + 5 bytes block data header
        int lengthFieldOffset = 9;
        int headerSize = 20; // greater than lengthFieldSize + lengthFieldOffset
        HeaderCaptureOutputStream headerOut = new HeaderCaptureOutputStream(headerSize);
        try (ObjectOutputStream objOut = new ObjectOutputStream(headerOut)) {
            objOut.writeUTF(largeString);
        } catch (HeaderCapturedException  e) {
        }
        byte[] header = headerOut.get();
        ByteBuffer bf = ByteBuffer.wrap(header, lengthFieldOffset, lengthFieldSize);
        bf.order(ByteOrder.BIG_ENDIAN);
        long lenInHeader = bf.getLong();
        if ( lenInHeader != expected ) {
            throw new RuntimeException("Header length mismatch: expected=" + expected + ", found=" + lenInHeader);
        }

        System.out.println("PASSED");
    }
}