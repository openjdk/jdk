/*
 * Copyright (c) 2023, BELLSOFT. All rights reserved.
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

import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.debugger.*;

/**
 * @test
 * @library /test/lib
 * @requires vm.hasSA
 * @modules jdk.hotspot.agent/sun.jvm.hotspot.debugger
 *          jdk.hotspot.agent/sun.jvm.hotspot.code
 * @run main/othervm -Xbootclasspath/a:. TestCompressedReadStream
 */
public class TestCompressedReadStream {

    public static void testReadInt() {
        byte data[] = { 
            (byte)0, (byte)4, // zero zequence of four values
            (byte)33, // UNSIGNED5(32) = 33
            (byte)0, (byte)4  // zero zequence of four values
        };
        CompressedReadStream in = new CompressedReadStream(new Addr(data), 0);
        assertEquals(in.readInt(), 0);
        assertEquals(in.readInt(), 0);
        assertEquals(in.readInt(), 0);
        assertEquals(in.readInt(), 0);
        assertEquals(in.readInt(), 32);
        assertEquals(in.readInt(), 0);
        assertEquals(in.readInt(), 0);
        assertEquals(in.readInt(), 0);
        assertEquals(in.readInt(), 0);

        in.setPosition(2); // rollback and read once again
        assertEquals(in.readInt(), 32);
    }
    
    public static void main(String[] args) {
        testReadInt();
    }

    private static void assertEquals(int a, int b) {
        if (a != b) throw new RuntimeException("assert failed: " + a + " != " + b);
    }
}

class DummyAddr implements sun.jvm.hotspot.debugger.Address {
    public boolean    equals(Object arg)                { return false; }
    public int        hashCode()                        { return 0; }
    public long       getCIntegerAt      (long offset, long numBytes, boolean isUnsigned) { return 0; }
    public Address    getAddressAt       (long offset)  { return null; }
    public Address    getCompOopAddressAt (long offset) { return null; }
    public Address    getCompKlassAddressAt (long offset) { return null; }
    public boolean    getJBooleanAt      (long offset)  { return false; }
    public byte       getJByteAt         (long offset)  { return 0; }
    public char       getJCharAt         (long offset)  { return 0; }
    public double     getJDoubleAt       (long offset)  { return 0; }
    public float      getJFloatAt        (long offset)  { return 0; }
    public int        getJIntAt          (long offset)  { return 0; }
    public long       getJLongAt         (long offset)  { return 0; }
    public short      getJShortAt        (long offset)  { return 0; }
    public OopHandle  getOopHandleAt     (long offset)  { return null; }
    public OopHandle  getCompOopHandleAt (long offset)  { return null; }
    public void       setCIntegerAt      (long offset, long numBytes, long value) {}
    public void       setAddressAt       (long offset, Address value) {}
    public void       setJBooleanAt      (long offset, boolean value) {}
    public void       setJByteAt         (long offset, byte value)    {}
    public void       setJCharAt         (long offset, char value)    {}
    public void       setJDoubleAt       (long offset, double value)  {}
    public void       setJFloatAt        (long offset, float value)   {}
    public void       setJIntAt          (long offset, int value)     {}
    public void       setJLongAt         (long offset, long value)    {}
    public void       setJShortAt        (long offset, short value)   {}
    public void       setOopHandleAt     (long offset, OopHandle value) {}
    public Address    addOffsetTo        (long offset)  { return null; }
    public OopHandle  addOffsetToAsOopHandle(long offset)  { return null; }
    public long       minus              (Address arg)  { return 0; }
    public boolean    lessThan           (Address arg)  { return false; }
    public boolean    lessThanOrEqual    (Address arg)  { return false; }
    public boolean    greaterThan        (Address arg)  { return false; }
    public boolean    greaterThanOrEqual (Address arg)  { return false; }
    public Address    andWithMask        (long mask)    { return null; }
    public Address    orWithMask         (long mask)    { return null; }
    public Address    xorWithMask        (long mask)    { return null; }
    public long       asLongValue        ()             { return 0; }
}

class Addr extends DummyAddr {
    byte data[];
    public Addr(byte data[]) {
        this.data = data;
    }
    public long getCIntegerAt(long offset, long numBytes, boolean isUnsigned) {
        return data[(int)offset];
    }
}
