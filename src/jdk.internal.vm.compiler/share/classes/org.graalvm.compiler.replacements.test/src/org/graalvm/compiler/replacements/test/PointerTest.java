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

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.word.Word;
import org.graalvm.compiler.word.WordCastNode;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

/**
 * Tests for the {@link Pointer} read and write operations.
 */
public class PointerTest extends SnippetsTest {

    private static final LocationIdentity ID = NamedLocationIdentity.mutable("ID");
    private static final JavaKind[] KINDS = new JavaKind[]{JavaKind.Byte, JavaKind.Char, JavaKind.Short, JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double, JavaKind.Object};
    private final TargetDescription target;

    public PointerTest() {
        target = getCodeCache().getTarget();
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

    private void assertRead(StructuredGraph graph, JavaKind kind, boolean indexConvert, LocationIdentity locationIdentity) {
        WordCastNode cast = (WordCastNode) graph.start().next();

        JavaReadNode read = (JavaReadNode) cast.next();
        Assert.assertEquals(kind.getStackKind(), read.stamp(NodeView.DEFAULT).getStackKind());

        OffsetAddressNode address = (OffsetAddressNode) read.getAddress();
        Assert.assertEquals(cast, address.getBase());
        Assert.assertEquals(graph.getParameter(0), cast.getInput());
        Assert.assertEquals(target.wordJavaKind, cast.stamp(NodeView.DEFAULT).getStackKind());

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

    private void assertWrite(StructuredGraph graph, boolean indexConvert, LocationIdentity locationIdentity) {
        WordCastNode cast = (WordCastNode) graph.start().next();

        JavaWriteNode write = (JavaWriteNode) cast.next();
        Assert.assertEquals(graph.getParameter(2), write.value());
        Assert.assertEquals(BytecodeFrame.AFTER_BCI, write.stateAfter().bci);

        OffsetAddressNode address = (OffsetAddressNode) write.getAddress();
        Assert.assertEquals(cast, address.getBase());
        Assert.assertEquals(graph.getParameter(0), cast.getInput());
        Assert.assertEquals(target.wordJavaKind, cast.stamp(NodeView.DEFAULT).getStackKind());

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
        return Word.objectToTrackedPointer(o).readByte(offset, ID);
    }

    @Snippet
    public static byte readByte2(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readByte(WordFactory.signed(offset), ID);
    }

    @Snippet
    public static byte readByte3(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readByte(offset);
    }

    @Snippet
    public static void writeByte1(Object o, int offset, byte value) {
        Word.objectToTrackedPointer(o).writeByte(offset, value, ID);
    }

    @Snippet
    public static void writeByte2(Object o, int offset, byte value) {
        Word.objectToTrackedPointer(o).writeByte(WordFactory.signed(offset), value, ID);
    }

    @Snippet
    public static void writeByte3(Object o, int offset, byte value) {
        Word.objectToTrackedPointer(o).writeByte(offset, value);
    }

    @Snippet
    public static char readChar1(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readChar(offset, ID);
    }

    @Snippet
    public static char readChar2(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readChar(WordFactory.signed(offset), ID);
    }

    @Snippet
    public static char readChar3(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readChar(offset);
    }

    @Snippet
    public static void writeChar1(Object o, int offset, char value) {
        Word.objectToTrackedPointer(o).writeChar(offset, value, ID);
    }

    @Snippet
    public static void writeChar2(Object o, int offset, char value) {
        Word.objectToTrackedPointer(o).writeChar(WordFactory.signed(offset), value, ID);
    }

    @Snippet
    public static void writeChar3(Object o, int offset, char value) {
        Word.objectToTrackedPointer(o).writeChar(offset, value);
    }

    @Snippet
    public static short readShort1(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readShort(offset, ID);
    }

    @Snippet
    public static short readShort2(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readShort(WordFactory.signed(offset), ID);
    }

    @Snippet
    public static short readShort3(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readShort(offset);
    }

    @Snippet
    public static void writeShort1(Object o, int offset, short value) {
        Word.objectToTrackedPointer(o).writeShort(offset, value, ID);
    }

    @Snippet
    public static void writeShort2(Object o, int offset, short value) {
        Word.objectToTrackedPointer(o).writeShort(WordFactory.signed(offset), value, ID);
    }

    @Snippet
    public static void writeShort3(Object o, int offset, short value) {
        Word.objectToTrackedPointer(o).writeShort(offset, value);
    }

    @Snippet
    public static int readInt1(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readInt(offset, ID);
    }

    @Snippet
    public static int readInt2(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readInt(WordFactory.signed(offset), ID);
    }

    @Snippet
    public static int readInt3(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readInt(offset);
    }

    @Snippet
    public static void writeInt1(Object o, int offset, int value) {
        Word.objectToTrackedPointer(o).writeInt(offset, value, ID);
    }

    @Snippet
    public static void writeInt2(Object o, int offset, int value) {
        Word.objectToTrackedPointer(o).writeInt(WordFactory.signed(offset), value, ID);
    }

    @Snippet
    public static void writeInt3(Object o, int offset, int value) {
        Word.objectToTrackedPointer(o).writeInt(offset, value);
    }

    @Snippet
    public static long readLong1(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readLong(offset, ID);
    }

    @Snippet
    public static long readLong2(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readLong(WordFactory.signed(offset), ID);
    }

    @Snippet
    public static long readLong3(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readLong(offset);
    }

    @Snippet
    public static void writeLong1(Object o, int offset, long value) {
        Word.objectToTrackedPointer(o).writeLong(offset, value, ID);
    }

    @Snippet
    public static void writeLong2(Object o, int offset, long value) {
        Word.objectToTrackedPointer(o).writeLong(WordFactory.signed(offset), value, ID);
    }

    @Snippet
    public static void writeLong3(Object o, int offset, long value) {
        Word.objectToTrackedPointer(o).writeLong(offset, value);
    }

    @Snippet
    public static float readFloat1(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readFloat(offset, ID);
    }

    @Snippet
    public static float readFloat2(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readFloat(WordFactory.signed(offset), ID);
    }

    @Snippet
    public static float readFloat3(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readFloat(offset);
    }

    @Snippet
    public static void writeFloat1(Object o, int offset, float value) {
        Word.objectToTrackedPointer(o).writeFloat(offset, value, ID);
    }

    @Snippet
    public static void writeFloat2(Object o, int offset, float value) {
        Word.objectToTrackedPointer(o).writeFloat(WordFactory.signed(offset), value, ID);
    }

    @Snippet
    public static void writeFloat3(Object o, int offset, float value) {
        Word.objectToTrackedPointer(o).writeFloat(offset, value);
    }

    @Snippet
    public static double readDouble1(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readDouble(offset, ID);
    }

    @Snippet
    public static double readDouble2(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readDouble(WordFactory.signed(offset), ID);
    }

    @Snippet
    public static double readDouble3(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readDouble(offset);
    }

    @Snippet
    public static void writeDouble1(Object o, int offset, double value) {
        Word.objectToTrackedPointer(o).writeDouble(offset, value, ID);
    }

    @Snippet
    public static void writeDouble2(Object o, int offset, double value) {
        Word.objectToTrackedPointer(o).writeDouble(WordFactory.signed(offset), value, ID);
    }

    @Snippet
    public static void writeDouble3(Object o, int offset, double value) {
        Word.objectToTrackedPointer(o).writeDouble(offset, value);
    }

    @Snippet
    public static Object readObject1(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readObject(offset, ID);
    }

    @Snippet
    public static Object readObject2(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readObject(WordFactory.signed(offset), ID);
    }

    @Snippet
    public static Object readObject3(Object o, int offset) {
        return Word.objectToTrackedPointer(o).readObject(offset);
    }

    @Snippet
    public static void writeObject1(Object o, int offset, Object value) {
        Word.objectToTrackedPointer(o).writeObject(offset, value, ID);
    }

    @Snippet
    public static void writeObject2(Object o, int offset, Object value) {
        Word.objectToTrackedPointer(o).writeObject(WordFactory.signed(offset), value, ID);
    }

    @Snippet
    public static void writeObject3(Object o, int offset, Object value) {
        Word.objectToTrackedPointer(o).writeObject(offset, value);
    }

    private void assertNumWordCasts(String snippetName, int expectedWordCasts) {
        HighTierContext context = new HighTierContext(getProviders(), null, OptimisticOptimizations.ALL);

        StructuredGraph graph = parseEager(snippetName, AllowAssumptions.YES);
        new CanonicalizerPhase().apply(graph, context);
        Assert.assertEquals(expectedWordCasts, graph.getNodes().filter(WordCastNode.class).count());
    }

    @Test
    public void testUnusedFromObject() {
        assertNumWordCasts("unusedFromObject", 0);
    }

    @Snippet
    public static void unusedFromObject(Object o) {
        Word.objectToTrackedPointer(o);
    }

    @Test
    public void testUnusedRawValue() {
        assertNumWordCasts("unusedRawValue", 0);
    }

    @Snippet
    public static void unusedRawValue(Object o) {
        Word.objectToTrackedPointer(o).rawValue();
    }

    @Test
    public void testUsedRawValue() {
        assertNumWordCasts("usedRawValue", 1);
    }

    @Snippet
    public static long usedRawValue(Object o) {
        return Word.objectToTrackedPointer(o).rawValue();
    }

    @Test
    public void testUnusedToObject() {
        assertNumWordCasts("unusedToObject", 0);
    }

    @Snippet
    public static void unusedToObject(Word w) {
        w.toObject();
    }

    @Test
    public void testUsedToObject() {
        assertNumWordCasts("usedToObject", 1);
    }

    @Snippet
    public static Object usedToObject(Word w) {
        return w.toObject();
    }
}
