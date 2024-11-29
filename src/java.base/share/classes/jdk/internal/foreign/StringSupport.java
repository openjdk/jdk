/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.foreign;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.MemorySegment;
import java.nio.charset.Charset;

import static java.lang.foreign.ValueLayout.*;

/**
 * Miscellaneous functions to read and write strings, in various charsets.
 */
public final class StringSupport {

    static final JavaLangAccess JAVA_LANG_ACCESS = SharedSecrets.getJavaLangAccess();

    private StringSupport() {}

    @ForceInline
    public static String read(AbstractMemorySegmentImpl segment, long offset, Charset charset) {
        return switch (CharsetKind.of(charset)) {
            case SINGLE_BYTE -> readByte(segment, offset, charset);
            case DOUBLE_BYTE -> readShort(segment, offset, charset);
            case QUAD_BYTE -> readInt(segment, offset, charset);
        };
    }

    @ForceInline
    public static void write(AbstractMemorySegmentImpl segment, long offset, Charset charset, String string) {
        switch (CharsetKind.of(charset)) {
            case SINGLE_BYTE -> writeByte(segment, offset, charset, string);
            case DOUBLE_BYTE -> writeShort(segment, offset, charset, string);
            case QUAD_BYTE -> writeInt(segment, offset, charset, string);
        }
    }

    @ForceInline
    private static String readByte(AbstractMemorySegmentImpl segment, long offset, Charset charset) {
        final int len = SegmentBulkOperations.strlenByte(segment, offset, segment.byteSize());
        final byte[] bytes = new byte[len];
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, len);
        return new String(bytes, charset);
    }

    @ForceInline
    private static void writeByte(AbstractMemorySegmentImpl segment, long offset, Charset charset, String string) {
        int bytes = copyBytes(string, segment, charset, offset);
        segment.set(JAVA_BYTE, offset + bytes, (byte)0);
    }

    @ForceInline
    private static String readShort(AbstractMemorySegmentImpl segment, long offset, Charset charset) {
        int len = SegmentBulkOperations.strlenShort(segment, offset, segment.byteSize());
        byte[] bytes = new byte[len];
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, len);
        return new String(bytes, charset);
    }

    @ForceInline
    private static void writeShort(AbstractMemorySegmentImpl segment, long offset, Charset charset, String string) {
        int bytes = copyBytes(string, segment, charset, offset);
        segment.set(JAVA_SHORT_UNALIGNED, offset + bytes, (short)0);
    }

    @ForceInline
    private static String readInt(AbstractMemorySegmentImpl segment, long offset, Charset charset) {
        int len = SegmentBulkOperations.strlenInt(segment, offset, segment.byteSize());
        byte[] bytes = new byte[len];
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, len);
        return new String(bytes, charset);
    }

    @ForceInline
    private static void writeInt(AbstractMemorySegmentImpl segment, long offset, Charset charset, String string) {
        int bytes = copyBytes(string, segment, charset, offset);
        segment.set(JAVA_INT_UNALIGNED, offset + bytes, 0);
    }

    public enum CharsetKind {
        SINGLE_BYTE(1),
        DOUBLE_BYTE(2),
        QUAD_BYTE(4);

        final int terminatorCharSize;

        CharsetKind(int terminatorCharSize) {
            this.terminatorCharSize = terminatorCharSize;
        }

        public int terminatorCharSize() {
            return terminatorCharSize;
        }

        public static CharsetKind of(Charset charset) {
            // Comparing the charset to specific internal implementations avoids loading the class `StandardCharsets`
            if        (charset == sun.nio.cs.UTF_8.INSTANCE ||
                       charset == sun.nio.cs.ISO_8859_1.INSTANCE ||
                       charset == sun.nio.cs.US_ASCII.INSTANCE) {
                return SINGLE_BYTE;
            } else if (charset instanceof sun.nio.cs.UTF_16LE ||
                       charset instanceof sun.nio.cs.UTF_16BE ||
                       charset instanceof sun.nio.cs.UTF_16) {
                return DOUBLE_BYTE;
            } else if (charset instanceof sun.nio.cs.UTF_32LE ||
                       charset instanceof sun.nio.cs.UTF_32BE ||
                       charset instanceof sun.nio.cs.UTF_32) {
                return QUAD_BYTE;
            } else {
                throw new IllegalArgumentException("Unsupported charset: " + charset);
            }
        }
    }

    public static boolean bytesCompatible(String string, Charset charset) {
        return JAVA_LANG_ACCESS.bytesCompatible(string, charset);
    }

    public static int copyBytes(String string, MemorySegment segment, Charset charset, long offset) {
        if (bytesCompatible(string, charset)) {
            copyToSegmentRaw(string, segment, offset);
            return string.length();
        } else {
            byte[] bytes = string.getBytes(charset);
            MemorySegment.copy(bytes, 0, segment, JAVA_BYTE, offset, bytes.length);
            return bytes.length;
        }
    }

    public static void copyToSegmentRaw(String string, MemorySegment segment, long offset) {
        JAVA_LANG_ACCESS.copyToSegmentRaw(string, segment, offset);
    }
}
