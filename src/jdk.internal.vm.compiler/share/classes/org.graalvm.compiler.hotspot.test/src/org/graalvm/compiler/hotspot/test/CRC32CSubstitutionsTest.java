/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.test;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.Checksum;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.graalvm.compiler.test.GraalTest;
import org.graalvm.compiler.core.test.GraalCompilerTest;

import org.junit.Test;

import static org.junit.Assume.assumeFalse;

/**
 * Tests compiled calls to {@link java.util.zip.CRC32C}.
 */
@SuppressWarnings("javadoc")
public class CRC32CSubstitutionsTest extends GraalCompilerTest {

    public static long updateBytes(byte[] input, int offset, int end) throws Throwable {
        Class<?> crcClass = Class.forName("java.util.zip.CRC32C");
        MethodHandle newMH = MethodHandles.publicLookup().findConstructor(crcClass, MethodType.methodType(void.class));
        Checksum crc = (Checksum) newMH.invoke();
        crc.update(input, offset, end);
        return crc.getValue();
    }

    @Test
    public void test1() throws Throwable {
        assumeFalse(GraalTest.Java8OrEarlier);
        String classfileName = CRC32CSubstitutionsTest.class.getSimpleName().replace('.', '/') + ".class";
        InputStream s = CRC32CSubstitutionsTest.class.getResourceAsStream(classfileName);
        byte[] buf = new byte[s.available()];
        new DataInputStream(s).readFully(buf);
        int end = buf.length;
        for (int offset = 0; offset < buf.length; offset++) {
            test("updateBytes", buf, offset, end);
        }
    }

    public static long updateByteBuffer(ByteBuffer buffer) throws Throwable {
        Class<?> crcClass = Class.forName("java.util.zip.CRC32C");
        MethodHandle newMH = MethodHandles.publicLookup().findConstructor(crcClass, MethodType.methodType(void.class));
        MethodHandle updateMH = MethodHandles.publicLookup().findVirtual(crcClass, "update", MethodType.methodType(void.class, ByteBuffer.class));
        Checksum crc = (Checksum) newMH.invoke();
        buffer.rewind();
        updateMH.invokeExact(crc, buffer); // Checksum.update(ByteBuffer) is also available since 9
        return crc.getValue();
    }

    @Test
    public void test2() throws Throwable {
        assumeFalse(GraalTest.Java8OrEarlier);
        String classfileName = CRC32CSubstitutionsTest.class.getSimpleName().replace('.', '/') + ".class";
        InputStream s = CRC32CSubstitutionsTest.class.getResourceAsStream(classfileName);
        byte[] buf = new byte[s.available()];
        new DataInputStream(s).readFully(buf);

        ByteBuffer directBuf = ByteBuffer.allocateDirect(buf.length);
        directBuf.put(buf);
        ByteBuffer heapBuf = ByteBuffer.wrap(buf);

        test("updateByteBuffer", directBuf);
        test("updateByteBuffer", heapBuf);
    }

}
