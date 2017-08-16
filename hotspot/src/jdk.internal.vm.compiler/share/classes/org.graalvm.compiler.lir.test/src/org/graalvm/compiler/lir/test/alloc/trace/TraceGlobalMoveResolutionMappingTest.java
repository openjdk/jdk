/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.test.alloc.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.alloc.trace.ShadowedRegisterValue;
import org.graalvm.compiler.lir.alloc.trace.TraceGlobalMoveResolutionPhase;
import org.graalvm.util.Pair;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

/**
 * Test global move resolver of the trace register allocator.
 *
 * Especially the mapping of LabelOp.incoming and BlockEndOp.outgoing.
 */
public class TraceGlobalMoveResolutionMappingTest {

    private static final class MoveResolverMock extends TraceGlobalMoveResolutionPhase.MoveResolver {

        private final HashSet<Pair<Value, AllocatableValue>> mapping = new HashSet<>();

        @Override
        public void addMapping(Value src, AllocatableValue dst, Value srcStack) {
            mapping.add(Pair.create(src, dst));
        }

        public int size() {
            return mapping.size();
        }

        public boolean contains(Value src, AllocatableValue dst) {
            return mapping.contains(Pair.create(src, dst));
        }

        @Override
        public String toString() {
            return mapping.toString();
        }

    }

    private static final RegisterCategory CPU = new RegisterCategory("CPU");

    private static final Register r0 = new Register(0, 0, "r0", CPU);
    private static final Register r1 = new Register(1, 1, "r1", CPU);

    private enum DummyPlatformKind implements PlatformKind {
        Long;

        private EnumKey<DummyPlatformKind> key = new EnumKey<>(this);

        @Override
        public Key getKey() {
            return key;
        }

        @Override
        public int getSizeInBytes() {
            return 8;
        }

        @Override
        public int getVectorLength() {
            return 1;
        }

        @Override
        public char getTypeChar() {
            return 'l';
        }
    }

    private static final LIRKind kind = LIRKind.value(DummyPlatformKind.Long);

    private MoveResolverMock resolver;

    @Before
    public void setUp() {
        resolver = new MoveResolverMock();
    }

    private void addMapping(Value src, Value dst) {
        TraceGlobalMoveResolutionPhase.addMapping(resolver, src, dst);
    }

    /** Create RegisterValue. */
    private static RegisterValue v(Register r) {
        return r.asValue(kind);
    }

    /** Create StackSlot. */
    private static StackSlot s(int offset) {
        return StackSlot.get(kind, -offset, true);
    }

    /** Create ShadowedRegisterValue. */
    private static ShadowedRegisterValue sd(Register reg, int offset) {
        return new ShadowedRegisterValue(v(reg), s(offset));
    }

    private void assertContains(Value src, AllocatableValue dst) {
        assertTrue(String.format("Expected move from %s to %s. %s", src, dst, resolver), resolver.contains(src, dst));
    }

    private void assertSize(int expected) {
        assertEquals(resolver.toString(), expected, resolver.size());
    }

    @Test
    public void testReg2Reg0() {
        addMapping(v(r0), v(r1));
        assertContains(v(r0), v(r1));
    }

    @Test
    public void testReg2Reg1() {
        addMapping(v(r0), v(r0));
        assertSize(0);
    }

    @Test
    public void testStack2Stack0() {
        addMapping(s(1), s(2));
        assertContains(s(1), s(2));
    }

    @Test
    public void testStack2Stack1() {
        addMapping(s(1), s(1));
        assertSize(0);
    }

    @Test
    public void testStack2Reg() {
        addMapping(s(1), v(r1));
        assertContains(s(1), v(r1));
    }

    @Test
    public void testReg2Stack() {
        addMapping(v(r0), s(1));
        assertContains(v(r0), s(1));
    }

    @Test
    public void testShadowed2Reg() {
        addMapping(sd(r0, 1), v(r1));
        assertContains(v(r0), v(r1));
    }

    @Test
    public void testReg2Shadowed0() {
        addMapping(v(r0), sd(r1, 1));
        assertSize(2);
        assertContains(v(r0), v(r1));
        assertContains(v(r0), s(1));
    }

    @Test
    public void testReg2Shadowed1() {
        addMapping(v(r0), sd(r0, 1));
        assertSize(1);
        assertContains(v(r0), s(1));
    }

    @Test
    @Ignore("Cannot express mapping dependencies (yet)")
    public void testStack2Shadowed0() {
        addMapping(s(2), sd(r1, 1));
        assertSize(2);
        assertContains(s(2), v(r1));
        assertContains(v(r1), s(1));
    }

    @Test
    public void testStack2Shadowed0WorkArount() {
        addMapping(s(2), sd(r1, 1));
        assertSize(2);
        assertContains(s(2), v(r1));
        assertContains(s(2), s(1));
    }

    @Test
    public void testStack2Shadowed1() {
        addMapping(s(1), sd(r1, 1));
        assertSize(1);
        assertContains(s(1), v(r1));
    }

    @Test
    public void testShadowed2Shadowed0() {
        addMapping(sd(r0, 1), sd(r1, 2));
        assertSize(2);
        assertContains(v(r0), v(r1));
        assertContains(v(r0), s(2));
    }

    @Test
    public void testShadowed2Shadowed1() {
        addMapping(sd(r0, 1), sd(r1, 1));
        assertSize(1);
        assertContains(v(r0), v(r1));
    }

    @Test
    public void testShadowed2Shadowed2() {
        addMapping(sd(r0, 1), sd(r0, 1));
        assertSize(0);
    }
}
