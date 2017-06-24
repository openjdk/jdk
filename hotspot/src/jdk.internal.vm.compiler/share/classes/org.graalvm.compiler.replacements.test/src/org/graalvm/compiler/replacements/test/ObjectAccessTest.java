/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Pointer;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests for the {@link Pointer} read and write operations.
 */
public class ObjectAccessTest extends GraalCompilerTest implements Snippets {

    private static final LocationIdentity ID = NamedLocationIdentity.mutable("ObjectAccessTestID");
    private static final JavaKind[] KINDS = new JavaKind[]{JavaKind.Byte, JavaKind.Char, JavaKind.Short, JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double, JavaKind.Object};
    private final ReplacementsImpl installer;

    public ObjectAccessTest() {
        installer = (ReplacementsImpl) getReplacements();
    }

    @Override
    protected StructuredGraph parseEager(ResolvedJavaMethod m, AllowAssumptions allowAssumptions, CompilationIdentifier compilationId) {
        return installer.makeGraph(m, null, null);
    }

    @Test
    public void testRead1() {
        for (JavaKind kind : KINDS) {
            assertRead(parseEager("read" + kind.name() + "1", AllowAssumptions.YES), kind, true, ID);
        }
    }

    @Test
    public void testRead2() {
        for (JavaKind kind : KINDS) {
            assertRead(parseEager("read" + kind.name() + "2", AllowAssumptions.YES), kind, true, ID);
        }
    }

    @Test
    public void testRead3() {
        for (JavaKind kind : KINDS) {
            assertRead(parseEager("read" + kind.name() + "3", AllowAssumptions.YES), kind, true, LocationIdentity.any());
        }
    }

    @Test
    public void testWrite1() {
        for (JavaKind kind : KINDS) {
            assertWrite(parseEager("write" + kind.name() + "1", AllowAssumptions.YES), true, ID);
        }
    }

    @Test
    public void testWrite2() {
        for (JavaKind kind : KINDS) {
            assertWrite(parseEager("write" + kind.name() + "2", AllowAssumptions.YES), true, ID);
        }
    }

    @Test
    public void testWrite3() {
        for (JavaKind kind : KINDS) {
            assertWrite(parseEager("write" + kind.name() + "3", AllowAssumptions.YES), true, LocationIdentity.any());
        }
    }

    private static void assertRead(StructuredGraph graph, JavaKind kind, boolean indexConvert, LocationIdentity locationIdentity) {
        JavaReadNode read = (JavaReadNode) graph.start().next();
        Assert.assertEquals(kind.getStackKind(), read.stamp().getStackKind());

        OffsetAddressNode address = (OffsetAddressNode) read.getAddress();
        Assert.assertEquals(graph.getParameter(0), address.getBase());
        Assert.assertEquals(locationIdentity, read.getLocationIdentity());

        if (indexConvert) {
            SignExtendNode convert = (SignExtendNode) address.getOffset();
            Assert.assertEquals(convert.getInputBits(), 32);
            Assert.assertEquals(convert.getResultBits(), 64);
            Assert.assertEquals(graph.getParameter(1), convert.getValue());
        } else {
            Assert.assertEquals(graph.getParameter(1), address.getOffset());
        }

        ReturnNode ret = (ReturnNode) read.next();
        Assert.assertEquals(read, ret.result());
    }

    private static void assertWrite(StructuredGraph graph, boolean indexConvert, LocationIdentity locationIdentity) {
        JavaWriteNode write = (JavaWriteNode) graph.start().next();
        Assert.assertEquals(graph.getParameter(2), write.value());

        OffsetAddressNode address = (OffsetAddressNode) write.getAddress();
        Assert.assertEquals(graph.getParameter(0), address.getBase());
        Assert.assertEquals(BytecodeFrame.AFTER_BCI, write.stateAfter().bci);

        Assert.assertEquals(locationIdentity, write.getLocationIdentity());

        if (indexConvert) {
            SignExtendNode convert = (SignExtendNode) address.getOffset();
            Assert.assertEquals(convert.getInputBits(), 32);
            Assert.assertEquals(convert.getResultBits(), 64);
            Assert.assertEquals(graph.getParameter(1), convert.getValue());
        } else {
            Assert.assertEquals(graph.getParameter(1), address.getOffset());
        }

        ReturnNode ret = (ReturnNode) write.next();
        Assert.assertEquals(null, ret.result());
    }

    @Snippet
    public static byte readByte1(Object o, int offset) {
        return ObjectAccess.readByte(o, offset, ID);
    }

    @Snippet
    public static byte readByte2(Object o, int offset) {
        return ObjectAccess.readByte(o, Word.signed(offset), ID);
    }

    @Snippet
    public static byte readByte3(Object o, int offset) {
        return ObjectAccess.readByte(o, offset);
    }

    @Snippet
    public static void writeByte1(Object o, int offset, byte value) {
        ObjectAccess.writeByte(o, offset, value, ID);
    }

    @Snippet
    public static void writeByte2(Object o, int offset, byte value) {
        ObjectAccess.writeByte(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeByte3(Object o, int offset, byte value) {
        ObjectAccess.writeByte(o, offset, value);
    }

    @Snippet
    public static char readChar1(Object o, int offset) {
        return ObjectAccess.readChar(o, offset, ID);
    }

    @Snippet
    public static char readChar2(Object o, int offset) {
        return ObjectAccess.readChar(o, Word.signed(offset), ID);
    }

    @Snippet
    public static char readChar3(Object o, int offset) {
        return ObjectAccess.readChar(o, offset);
    }

    @Snippet
    public static void writeChar1(Object o, int offset, char value) {
        ObjectAccess.writeChar(o, offset, value, ID);
    }

    @Snippet
    public static void writeChar2(Object o, int offset, char value) {
        ObjectAccess.writeChar(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeChar3(Object o, int offset, char value) {
        ObjectAccess.writeChar(o, offset, value);
    }

    @Snippet
    public static short readShort1(Object o, int offset) {
        return ObjectAccess.readShort(o, offset, ID);
    }

    @Snippet
    public static short readShort2(Object o, int offset) {
        return ObjectAccess.readShort(o, Word.signed(offset), ID);
    }

    @Snippet
    public static short readShort3(Object o, int offset) {
        return ObjectAccess.readShort(o, offset);
    }

    @Snippet
    public static void writeShort1(Object o, int offset, short value) {
        ObjectAccess.writeShort(o, offset, value, ID);
    }

    @Snippet
    public static void writeShort2(Object o, int offset, short value) {
        ObjectAccess.writeShort(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeShort3(Object o, int offset, short value) {
        ObjectAccess.writeShort(o, offset, value);
    }

    @Snippet
    public static int readInt1(Object o, int offset) {
        return ObjectAccess.readInt(o, offset, ID);
    }

    @Snippet
    public static int readInt2(Object o, int offset) {
        return ObjectAccess.readInt(o, Word.signed(offset), ID);
    }

    @Snippet
    public static int readInt3(Object o, int offset) {
        return ObjectAccess.readInt(o, offset);
    }

    @Snippet
    public static void writeInt1(Object o, int offset, int value) {
        ObjectAccess.writeInt(o, offset, value, ID);
    }

    @Snippet
    public static void writeInt2(Object o, int offset, int value) {
        ObjectAccess.writeInt(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeInt3(Object o, int offset, int value) {
        ObjectAccess.writeInt(o, offset, value);
    }

    @Snippet
    public static long readLong1(Object o, int offset) {
        return ObjectAccess.readLong(o, offset, ID);
    }

    @Snippet
    public static long readLong2(Object o, int offset) {
        return ObjectAccess.readLong(o, Word.signed(offset), ID);
    }

    @Snippet
    public static long readLong3(Object o, int offset) {
        return ObjectAccess.readLong(o, offset);
    }

    @Snippet
    public static void writeLong1(Object o, int offset, long value) {
        ObjectAccess.writeLong(o, offset, value, ID);
    }

    @Snippet
    public static void writeLong2(Object o, int offset, long value) {
        ObjectAccess.writeLong(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeLong3(Object o, int offset, long value) {
        ObjectAccess.writeLong(o, offset, value);
    }

    @Snippet
    public static float readFloat1(Object o, int offset) {
        return ObjectAccess.readFloat(o, offset, ID);
    }

    @Snippet
    public static float readFloat2(Object o, int offset) {
        return ObjectAccess.readFloat(o, Word.signed(offset), ID);
    }

    @Snippet
    public static float readFloat3(Object o, int offset) {
        return ObjectAccess.readFloat(o, offset);
    }

    @Snippet
    public static void writeFloat1(Object o, int offset, float value) {
        ObjectAccess.writeFloat(o, offset, value, ID);
    }

    @Snippet
    public static void writeFloat2(Object o, int offset, float value) {
        ObjectAccess.writeFloat(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeFloat3(Object o, int offset, float value) {
        ObjectAccess.writeFloat(o, offset, value);
    }

    @Snippet
    public static double readDouble1(Object o, int offset) {
        return ObjectAccess.readDouble(o, offset, ID);
    }

    @Snippet
    public static double readDouble2(Object o, int offset) {
        return ObjectAccess.readDouble(o, Word.signed(offset), ID);
    }

    @Snippet
    public static double readDouble3(Object o, int offset) {
        return ObjectAccess.readDouble(o, offset);
    }

    @Snippet
    public static void writeDouble1(Object o, int offset, double value) {
        ObjectAccess.writeDouble(o, offset, value, ID);
    }

    @Snippet
    public static void writeDouble2(Object o, int offset, double value) {
        ObjectAccess.writeDouble(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeDouble3(Object o, int offset, double value) {
        ObjectAccess.writeDouble(o, offset, value);
    }

    @Snippet
    public static Object readObject1(Object o, int offset) {
        return ObjectAccess.readObject(o, offset, ID);
    }

    @Snippet
    public static Object readObject2(Object o, int offset) {
        return ObjectAccess.readObject(o, Word.signed(offset), ID);
    }

    @Snippet
    public static Object readObject3(Object o, int offset) {
        return ObjectAccess.readObject(o, offset);
    }

    @Snippet
    public static void writeObject1(Object o, int offset, Object value) {
        ObjectAccess.writeObject(o, offset, value, ID);
    }

    @Snippet
    public static void writeObject2(Object o, int offset, Object value) {
        ObjectAccess.writeObject(o, Word.signed(offset), value, ID);
    }

    @Snippet
    public static void writeObject3(Object o, int offset, Object value) {
        ObjectAccess.writeObject(o, offset, value);
    }
}
