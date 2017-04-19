/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ByteBufferTest extends GraalCompilerTest {

    class Ret {

        byte byteValue = 0;
        short shortValue = 0;
        int intValue = 0;
        float floatValue = 0.0f;
        double doubleValue = 0.0d;

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Ret)) {
                return false;
            }

            Ret other = (Ret) obj;
            if (this.byteValue != other.byteValue) {
                return false;
            }
            if (this.shortValue != other.shortValue) {
                return false;
            }
            if (this.intValue != other.intValue) {
                return false;
            }
            if (Float.floatToRawIntBits(this.floatValue) != Float.floatToRawIntBits(other.floatValue)) {
                return false;
            }
            if (Double.doubleToRawLongBits(this.doubleValue) != Double.doubleToRawLongBits(other.doubleValue)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return String.format("0x%02x, 0x%04x, 0x%08x, 0x%04x, 0x%08x", byteValue, shortValue, intValue, Float.floatToRawIntBits(floatValue), Double.doubleToRawLongBits(doubleValue));
        }
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        ret.add(new Object[]{ByteOrder.BIG_ENDIAN});
        ret.add(new Object[]{ByteOrder.LITTLE_ENDIAN});
        return ret;
    }

    @Parameter public ByteOrder byteOrder;

    Ret alignedReadSnippet(byte[] arg) {
        ByteBuffer buffer = ByteBuffer.wrap(arg).order(byteOrder);

        Ret ret = new Ret();
        ret.byteValue = buffer.get();
        ret.byteValue += buffer.get();
        ret.shortValue = buffer.getShort();
        ret.intValue = buffer.getInt();
        ret.doubleValue = buffer.getDouble();
        ret.floatValue = buffer.getFloat();

        return ret;
    }

    @Test
    public void testReadAligned() {
        byte[] input = new byte[20];
        for (int i = 0; i < 20; i++) {
            input[i] = (byte) (7 * (i + 42));
        }
        test("alignedReadSnippet", input);
    }

    byte[] alignedWriteSnippet(byte a, byte b, short c, int d, double e, float f) {
        byte[] ret = new byte[20];
        ByteBuffer buffer = ByteBuffer.wrap(ret).order(byteOrder);

        buffer.put(a);
        buffer.put(b);
        buffer.putShort(c);
        buffer.putInt(d);
        buffer.putDouble(e);
        buffer.putFloat(f);

        return ret;
    }

    @Test
    public void testWriteAligned() {
        test("alignedWriteSnippet", (byte) 5, (byte) -3, (short) 17, 42, 84.72, 1.23f);
    }

    Ret unalignedReadSnippet(byte[] arg) {
        ByteBuffer buffer = ByteBuffer.wrap(arg).order(byteOrder);

        Ret ret = new Ret();
        ret.byteValue = buffer.get();
        ret.shortValue = buffer.getShort();
        ret.intValue = buffer.getInt();
        ret.doubleValue = buffer.getDouble();
        ret.floatValue = buffer.getFloat();

        return ret;
    }

    @Test
    public void testReadUnaligned() {
        byte[] input = new byte[19];
        for (int i = 0; i < 19; i++) {
            input[i] = (byte) (7 * (i + 42));
        }
        test("unalignedReadSnippet", input);
    }

    byte[] unalignedWriteSnippet(byte a, short b, int c, double d, float e) {
        byte[] ret = new byte[20];
        ByteBuffer buffer = ByteBuffer.wrap(ret).order(byteOrder);

        buffer.put(a);
        buffer.putShort(b);
        buffer.putInt(c);
        buffer.putDouble(d);
        buffer.putFloat(e);

        return ret;
    }

    @Test
    public void testWriteUnaligned() {
        test("unalignedWriteSnippet", (byte) -3, (short) 17, 42, 84.72, 1.23f);
    }
}
