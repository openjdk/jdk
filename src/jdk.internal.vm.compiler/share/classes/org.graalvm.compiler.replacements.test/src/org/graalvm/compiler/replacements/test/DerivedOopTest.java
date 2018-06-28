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


package org.graalvm.compiler.replacements.test;

import java.util.Objects;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecodeProvider;
import org.graalvm.compiler.word.Word;
import org.graalvm.compiler.word.WordCastNode;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests for derived oops in reference maps.
 */
public class DerivedOopTest extends ReplacementsTest implements Snippets {

    private static class Pointers {
        public long basePointer;
        public long internalPointer;

        public long delta() {
            return internalPointer - basePointer;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Pointers)) {
                return false;
            }

            Pointers other = (Pointers) obj;
            return this.delta() == other.delta();
        }

        @Override
        public int hashCode() {
            return (int) delta();
        }
    }

    private static class Result {
        public Pointers beforeGC;
        public Pointers afterGC;

        Result() {
            beforeGC = new Pointers();
            afterGC = new Pointers();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((afterGC == null) ? 0 : afterGC.hashCode());
            result = prime * result + ((beforeGC == null) ? 0 : beforeGC.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Result)) {
                return false;
            }
            Result other = (Result) obj;
            return Objects.equals(this.beforeGC, other.beforeGC) && Objects.equals(this.afterGC, other.afterGC);
        }
    }

    @Test
    public void testFieldOffset() {
        // Run a couple times to encourage objects to move
        for (int i = 0; i < 4; i++) {
            Result r = new Result();
            test("fieldOffsetSnippet", r, 16L);

            Assert.assertEquals(r.beforeGC.delta(), r.afterGC.delta());
        }
    }

    static long getRawPointer(Object obj) {
        // fake implementation for interpreter
        return obj.hashCode();
    }

    static long getRawPointerIntrinsic(Object obj) {
        return Word.objectToTrackedPointer(obj).rawValue();
    }

    public static Result fieldOffsetSnippet(Result obj, long offset) {
        long internalPointer = getRawPointer(obj) + offset;

        // make sure the internal pointer is computed before the safepoint
        GraalDirectives.blackhole(internalPointer);

        obj.beforeGC.basePointer = getRawPointer(obj);
        obj.beforeGC.internalPointer = internalPointer;

        System.gc();

        obj.afterGC.basePointer = getRawPointer(obj);
        obj.afterGC.internalPointer = internalPointer;

        return obj;
    }

    @Rule public final ExpectedException thrown = ExpectedException.none();
    private static final String UNKNOWN_REFERENCE_AT_SAFEPOINT_MSG = "should not reach here: unknown reference alive across safepoint";

    @Test
    @SuppressWarnings("try")
    public void testFieldOffsetMergeNonLiveBasePointer() {
        thrown.expect(GraalError.class);
        thrown.expectMessage(UNKNOWN_REFERENCE_AT_SAFEPOINT_MSG);
        DebugContext debug = getDebugContext();
        try (Scope s = debug.disable()) {
            // Run a couple times to encourage objects to move
            for (int i = 0; i < 4; i++) {
                Result r = new Result();
                test("fieldOffsetMergeSnippet01", r, 8L, 16L);
                Assert.assertEquals(r.beforeGC.delta(), r.afterGC.delta());
            }
        }
    }

    @Test
    public void testFieldOffsetMergeNonLiveBasePointerNotAccrossSafepoint() {
        // Run a couple times to encourage objects to move
        for (int i = 0; i < 4; i++) {
            Result r = new Result();
            test("fieldOffsetMergeSnippet02", r, 8L, 16L);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testFieldOffsetMergeLiveBasePointer() {
        thrown.expect(GraalError.class);
        thrown.expectMessage(UNKNOWN_REFERENCE_AT_SAFEPOINT_MSG);
        DebugContext debug = getDebugContext();
        try (Scope s = debug.disable()) {
            // Run a couple times to encourage objects to move
            for (int i = 0; i < 4; i++) {
                Result r = new Result();
                test("fieldOffsetMergeSnippet03", r, new Result(), new Result(), 8L, 16L);
                Assert.assertEquals(r.beforeGC.delta(), r.afterGC.delta());
            }
        }
    }

    public static boolean SideEffectB;
    public static long SideEffect1 = 16;
    public static long SideEffect2 = 16;
    public static Object o1 = new Result();
    public static Object o2 = o1;

    public static Result fieldOffsetMergeSnippet01(Result objResult, long offsetA, long offsetB) {
        long internalPointer;
        if (SideEffectB) {
            internalPointer = getRawPointer(o1) + offsetA;
            SideEffect1 = internalPointer;
        } else {
            internalPointer = getRawPointer(o2) + offsetB;
            SideEffect2 = internalPointer;
        }
        GraalDirectives.controlFlowAnchor();
        // make sure the internal pointer is computed before the safepoint
        GraalDirectives.blackhole(internalPointer);
        objResult.beforeGC.basePointer = getRawPointer(objResult);
        objResult.beforeGC.internalPointer = internalPointer;
        System.gc();
        objResult.afterGC.basePointer = getRawPointer(objResult);
        objResult.afterGC.internalPointer = internalPointer;
        return objResult;
    }

    public static Result fieldOffsetMergeSnippet02(Result objResult, long offsetA, long offsetB) {
        long internalPointer;
        if (SideEffectB) {
            internalPointer = getRawPointer(o1) + offsetA;
            SideEffect1 = internalPointer;
        } else {
            internalPointer = getRawPointer(o2) + offsetB;
            SideEffect2 = internalPointer;
        }
        GraalDirectives.controlFlowAnchor();
        // make sure the internal pointer is computed before the safepoint
        GraalDirectives.blackhole(internalPointer);
        objResult.beforeGC.basePointer = getRawPointer(objResult);
        objResult.beforeGC.internalPointer = internalPointer;
        objResult.afterGC.basePointer = getRawPointer(objResult);
        objResult.afterGC.internalPointer = internalPointer;
        return objResult;
    }

    public static Result fieldOffsetMergeSnippet03(Result objResult, Result a, Result b, long offsetA, long offsetB) {
        long internalPointer;
        if (SideEffectB) {
            internalPointer = getRawPointer(a) + offsetA;
            SideEffect1 = internalPointer;
        } else {
            internalPointer = getRawPointer(b) + offsetB;
            SideEffect2 = internalPointer;
        }
        GraalDirectives.controlFlowAnchor();
        // make sure the internal pointer is computed before the safepoint
        GraalDirectives.blackhole(internalPointer);
        objResult.beforeGC.basePointer = getRawPointer(objResult);
        objResult.beforeGC.internalPointer = internalPointer;
        System.gc();
        objResult.afterGC.basePointer = getRawPointer(objResult);
        objResult.afterGC.internalPointer = internalPointer;
        return objResult;
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        Registration r = new Registration(invocationPlugins, DerivedOopTest.class);
        ClassfileBytecodeProvider bytecodeProvider = getSystemClassLoaderBytecodeProvider();

        ResolvedJavaMethod intrinsic = getResolvedJavaMethod("getRawPointerIntrinsic");
        r.register1("getRawPointer", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                return b.intrinsify(bytecodeProvider, targetMethod, intrinsic, receiver, new ValueNode[]{arg});
            }
        });
        super.registerInvocationPlugins(invocationPlugins);
    }

    @Override
    protected boolean checkHighTierGraph(StructuredGraph graph) {
        assert graph.getNodes().filter(WordCastNode.class).count() > 0 : "DerivedOopTest.toLong should be intrinsified";
        return super.checkHighTierGraph(graph);
    }
}
