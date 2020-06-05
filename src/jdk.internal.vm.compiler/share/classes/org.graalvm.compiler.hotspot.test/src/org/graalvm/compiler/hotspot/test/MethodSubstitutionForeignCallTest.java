/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.debug.DebugOptions.DumpOnError;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecodeProvider;
import org.graalvm.compiler.runtime.RuntimeProvider;
import jdk.internal.vm.compiler.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MethodSubstitutionForeignCallTest extends GraalCompilerTest {
    public static final ForeignCallDescriptor TEST_CALL_DEOPT = new ForeignCallDescriptor("test", int.class, new Class<?>[]{int.class}, false, new LocationIdentity[0], true, true);
    public static final ForeignCallDescriptor TEST_CALL_REEXECUTABLE = new ForeignCallDescriptor("test", int.class, new Class<?>[]{int.class}, true, new LocationIdentity[0], false, false);
    public static final ForeignCallDescriptor TEST_CALL_NON_DEOPT = new ForeignCallDescriptor("test", int.class, new Class<?>[]{int.class}, false, new LocationIdentity[0], false, false);

    public static class A {
        static void invalidConsecutiveForeignCall1(@SuppressWarnings("unused") int phi) {

        }

        static void invalidConsecutiveForeignCall2(@SuppressWarnings("unused") int phi) {

        }

        static void validConsecutiveForeignCallReexecutable(@SuppressWarnings("unused") int phi) {

        }

        static void splitForeignCallInvalid(@SuppressWarnings("unused") int phi) {

        }
    }

    @ClassSubstitution(A.class)
    public static class ASubstitutions {

        /*
         * Invalid: two consecutive states, deopt could float in between.
         */
        @MethodSubstitution
        static void invalidConsecutiveForeignCall1(int phi) {
            testDeopt(phi);
            // invalid two consecutive calls
            testDeopt(phi);
        }

        /*
         * Invalid: two consecutive states, deopt could float in between. Same applies for
         * non-deopting framestates if they are not re-executable. If they are, we are good.
         */
        @MethodSubstitution
        static void invalidConsecutiveForeignCall2(int phi) {
            testNonDeopting(phi);
            testNonDeopting(phi);
        }

        /*
         * Valid, the foreign calls are re-executable and non-deopting (thus completely side-effect
         * free), they do not need a state.
         */
        @MethodSubstitution
        static void validConsecutiveForeignCallReexecutable(int phi) {
            testPureReexectuable(phi);
            testPureReexectuable(phi);
        }

        /**
         * Invalid: Splitting effect in a method substitution is allowed as long as it is just one
         * effect per call. This is not the case here.
         */
        @MethodSubstitution
        static void splitForeignCallInvalid(int phi) {
            if (SideEffect == 0) {
                testDeopt(phi);
            } else {
                CurrentJavaThreadNode.get().writeByte(0, (byte) 0);
                testDeopt(phi);
            }
        }
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {

        invocationPlugins.register(new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {

                ForeignCallNode node = new ForeignCallNode(TEST_CALL_DEOPT, arg);
                node.setBci(b.bci());
                b.addPush(JavaKind.Int, node);
                return true;
            }
        }, MethodSubstitutionForeignCallTest.class, "testDeopt", int.class);
        invocationPlugins.register(new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                ForeignCallNode node = new ForeignCallNode(TEST_CALL_NON_DEOPT, arg);
                node.setBci(b.bci());
                b.addPush(JavaKind.Int, node);
                return true;
            }
        }, MethodSubstitutionForeignCallTest.class, "testNonDeopting", int.class);
        invocationPlugins.register(new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                ForeignCallNode node = new ForeignCallNode(TEST_CALL_REEXECUTABLE, arg);
                node.setBci(b.bci());
                b.addPush(JavaKind.Int, node);
                return true;
            }
        }, MethodSubstitutionForeignCallTest.class, "testPureReexectuable", int.class);
        ClassfileBytecodeProvider bytecodeProvider = getSystemClassLoaderBytecodeProvider();
        Registration r = new Registration(invocationPlugins, A.class, getReplacements(), bytecodeProvider);
        r.registerMethodSubstitution(ASubstitutions.class, "invalidConsecutiveForeignCall1", int.class);
        r.registerMethodSubstitution(ASubstitutions.class, "invalidConsecutiveForeignCall2", int.class);
        r.registerMethodSubstitution(ASubstitutions.class, "validConsecutiveForeignCallReexecutable", int.class);
        r.registerMethodSubstitution(ASubstitutions.class, "splitForeignCallInvalid", int.class);
        super.registerInvocationPlugins(invocationPlugins);
    }

    private ClassfileBytecodeProvider getSystemClassLoaderBytecodeProvider() {
        ReplacementsImpl d = (ReplacementsImpl) getReplacements();
        MetaAccessProvider metaAccess = d.getProviders().getMetaAccess();
        ClassfileBytecodeProvider bytecodeProvider = new ClassfileBytecodeProvider(metaAccess, d.snippetReflection, ClassLoader.getSystemClassLoader());
        return bytecodeProvider;
    }

    public static int SideEffect;

    public static int testDeopt(int value) {
        SideEffect = value;
        return value;
    }

    public static int testNonDeopting(int value) {
        return value;
    }

    public static int testPureReexectuable(int value) {
        return value;
    }

    public static void testSnippetInvalidSequential() {
        A.invalidConsecutiveForeignCall1(SideEffect);
        if (SideEffect == 1) {
            GraalDirectives.deoptimize();
        }
    }

    public static void testNonDeoptingInvalid() {
        A.invalidConsecutiveForeignCall2(SideEffect);
        if (SideEffect == 1) {
            GraalDirectives.deoptimize();
        }
    }

    public static void testNonDeoptingSplit() {
        A.splitForeignCallInvalid(SideEffect);
        if (SideEffect == 1) {
            GraalDirectives.deoptimize();
        }
    }

    public static void testNonDeoptingReexectuable() {
        A.validConsecutiveForeignCallReexecutable(SideEffect);
        if (SideEffect == 1) {
            GraalDirectives.deoptimize();
        }
    }

    @Test
    @SuppressWarnings("try")
    public void test1() {
        try (AutoCloseable c = new TTY.Filter()) {
            OptionValues options = new OptionValues(getInitialOptions(), DumpOnError, false);
            StructuredGraph g = parseEager(getResolvedJavaMethod("testSnippetInvalidSequential"), AllowAssumptions.NO, options);
            Suites s = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites().getDefaultSuites(getInitialOptions());
            s.getHighTier().apply(g, getDefaultHighTierContext());
            s.getMidTier().apply(g, getDefaultMidTierContext());
            Assert.fail("Compilation should not reach this point, must throw an exception before");
        } catch (Throwable t) {
            if ((t.getCause() instanceof GraalError || t instanceof GraalError) && t.getMessage().contains("invalid framestate")) {
                return;
            }
            throw new AssertionError(t);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void test2() {
        try (AutoCloseable c = new TTY.Filter()) {
            OptionValues options = new OptionValues(getInitialOptions(), DumpOnError, false);
            StructuredGraph g = parseEager(getResolvedJavaMethod("testSnippetInvalidSequential"), AllowAssumptions.NO, options);
            Suites s = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites().getDefaultSuites(getInitialOptions());
            s.getHighTier().apply(g, getDefaultHighTierContext());
            s.getMidTier().apply(g, getDefaultMidTierContext());
            Assert.fail("Compilation should not reach this point, must throw an exception before");
        } catch (Throwable t) {
            if ((t.getCause() instanceof GraalError || t instanceof GraalError) && t.getMessage().contains("invalid framestate")) {
                return;
            }
            throw new AssertionError(t);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void test3() {
        try (AutoCloseable c = new TTY.Filter()) {
            OptionValues options = new OptionValues(getInitialOptions(), DumpOnError, false);
            StructuredGraph g = parseEager(getResolvedJavaMethod("testNonDeoptingSplit"), AllowAssumptions.NO, options);
            Suites s = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites().getDefaultSuites(getInitialOptions());
            s.getHighTier().apply(g, getDefaultHighTierContext());
            s.getMidTier().apply(g, getDefaultMidTierContext());
            Assert.fail("Compilation should not reach this point, must throw an exception before");
        } catch (Throwable t) {
            if ((t.getCause() instanceof GraalError || t instanceof GraalError) && t.getMessage().contains("invalid framestate")) {
                return;
            }
            throw new AssertionError(t);
        }
    }

    @Test
    public void test4() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("testNonDeoptingReexectuable"), AllowAssumptions.NO);
        Suites s = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites().getDefaultSuites(getInitialOptions());
        s.getHighTier().apply(g, getDefaultHighTierContext());
        s.getMidTier().apply(g, getDefaultMidTierContext());
    }

}
