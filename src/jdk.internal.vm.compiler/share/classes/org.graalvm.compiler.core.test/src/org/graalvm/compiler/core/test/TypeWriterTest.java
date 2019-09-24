/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.TypeReader;
import org.graalvm.compiler.core.common.util.TypeWriter;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeReader;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;

public class TypeWriterTest extends GraalCompilerTest {

    private static void putValue(TypeWriter writer, long value) {
        if (TypeConversion.isS1(value)) {
            writer.putS1(value);
        }
        if (TypeConversion.isU1(value)) {
            writer.putU1(value);
        }
        if (TypeConversion.isS2(value)) {
            writer.putS2(value);
        }
        if (TypeConversion.isU2(value)) {
            writer.putU2(value);
        }
        if (TypeConversion.isS4(value)) {
            writer.putS4(value);
        }
        if (TypeConversion.isU4(value)) {
            writer.putU4(value);
        }
        writer.putS8(value);
        writer.putSV(value);
        if (value >= 0) {
            writer.putUV(value);
        }
    }

    private static void checkValue(TypeReader reader, long value) {
        if (TypeConversion.isS1(value)) {
            Assert.assertEquals(value, reader.getS1());
        }
        if (TypeConversion.isU1(value)) {
            Assert.assertEquals(value, reader.getU1());
        }
        if (TypeConversion.isS2(value)) {
            Assert.assertEquals(value, reader.getS2());
        }
        if (TypeConversion.isU2(value)) {
            Assert.assertEquals(value, reader.getU2());
        }
        if (TypeConversion.isS4(value)) {
            Assert.assertEquals(value, reader.getS4());
        }
        if (TypeConversion.isU4(value)) {
            Assert.assertEquals(value, reader.getU4());
        }
        Assert.assertEquals(value, reader.getS8());
        Assert.assertEquals(value, reader.getSV());
        if (value >= 0) {
            Assert.assertEquals(value, reader.getUV());
        }
    }

    private static void putValues(TypeWriter writer) {
        for (int i = 0; i < 64; i++) {
            long value = 1L << i;
            putValue(writer, value - 2);
            putValue(writer, value - 1);
            putValue(writer, value);
            putValue(writer, value + 1);
            putValue(writer, value + 2);

            putValue(writer, -value - 2);
            putValue(writer, -value - 1);
            putValue(writer, -value);
            putValue(writer, -value + 1);
            putValue(writer, -value + 2);
        }
    }

    private static void checkValues(TypeReader reader) {
        for (int i = 0; i < 64; i++) {
            long value = 1L << i;
            checkValue(reader, value - 2);
            checkValue(reader, value - 1);
            checkValue(reader, value);
            checkValue(reader, value + 1);
            checkValue(reader, value + 2);

            checkValue(reader, -value - 2);
            checkValue(reader, -value - 1);
            checkValue(reader, -value);
            checkValue(reader, -value + 1);
            checkValue(reader, -value + 2);
        }
    }

    private static void test01(boolean supportsUnalignedMemoryAccess) {
        UnsafeArrayTypeWriter writer = UnsafeArrayTypeWriter.create(supportsUnalignedMemoryAccess);
        putValues(writer);

        byte[] array = new byte[TypeConversion.asU4(writer.getBytesWritten())];
        writer.toArray(array);
        UnsafeArrayTypeReader reader = UnsafeArrayTypeReader.create(array, 0, supportsUnalignedMemoryAccess);
        checkValues(reader);
    }

    @Test
    public void test01a() {
        test01(getTarget().arch.supportsUnalignedMemoryAccess());
    }

    @Test
    public void test01b() {
        test01(false);
    }

    private static void checkSignedSize(TypeWriter writer, long value, long expectedSize) {
        long sizeBefore = writer.getBytesWritten();
        writer.putSV(value);
        Assert.assertEquals(expectedSize, writer.getBytesWritten() - sizeBefore);
    }

    private static void checkUnsignedSize(TypeWriter writer, long value, long expectedSize) {
        long sizeBefore = writer.getBytesWritten();
        writer.putUV(value);
        Assert.assertEquals(expectedSize, writer.getBytesWritten() - sizeBefore);
    }

    private static void checkSizes(TypeWriter writer) {
        checkSignedSize(writer, 0, 1);
        checkSignedSize(writer, 95, 1);
        checkSignedSize(writer, -96, 1);
        checkSignedSize(writer, 96, 2);
        checkSignedSize(writer, -97, 2);
        checkSignedSize(writer, 6239, 2);
        checkSignedSize(writer, -6240, 2);
        checkSignedSize(writer, 8192, 3);
        checkSignedSize(writer, -8193, 3);
        checkSignedSize(writer, Long.MAX_VALUE, UnsafeArrayTypeWriter.MAX_BYTES);
        checkSignedSize(writer, Long.MIN_VALUE, UnsafeArrayTypeWriter.MAX_BYTES);

        checkUnsignedSize(writer, 0, 1);
        checkUnsignedSize(writer, 191, 1);
        checkUnsignedSize(writer, 192, 2);
        checkUnsignedSize(writer, 12479, 2);
        checkUnsignedSize(writer, 12480, 3);
        checkUnsignedSize(writer, Long.MAX_VALUE, UnsafeArrayTypeWriter.MAX_BYTES);
        checkUnsignedSize(writer, Long.MIN_VALUE, UnsafeArrayTypeWriter.MAX_BYTES);
    }

    @Test
    public void test02a() {
        checkSizes(UnsafeArrayTypeWriter.create(getTarget().arch.supportsUnalignedMemoryAccess()));
    }

    @Test
    public void test02b() {
        checkSizes(UnsafeArrayTypeWriter.create(false));
    }
}
