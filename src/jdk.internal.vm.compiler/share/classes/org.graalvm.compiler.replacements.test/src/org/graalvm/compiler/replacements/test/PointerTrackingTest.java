/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.word.Word;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class PointerTrackingTest extends ReplacementsTest implements Snippets {

    @Test
    public void testTracking() {
        Result result = executeActual(getResolvedJavaMethod("trackingSnippet"), null, new Object());
        assertEquals(new Result("OK", null), result);
    }

    public static String trackingSnippet(Object obj) {
        long trackedBeforeGC = getTrackedPointer(obj);
        long untrackedBeforeGC = getUntrackedPointer(obj);

        int i = 0;
        while (untrackedBeforeGC == getTrackedPointer(obj)) {
            System.gc();
            if (i++ > 100) {
                return "Timeout! Object didn't move after 100 GCs.";
            }
        }

        long trackedAfterGC = getTrackedPointer(obj);
        long untrackedAfterGC = getUntrackedPointer(obj);

        if (untrackedBeforeGC == untrackedAfterGC) {
            /*
             * The untracked pointer isn't supposed to be updated, so it should be different before
             * and after GC.
             */
            return "untrackedBeforeGC == untrackedAfterGC";
        }
        if (trackedBeforeGC != trackedAfterGC) {
            /*
             * The trackedBeforeGC variable should be updated to the new location by the GC, so it
             * should be equal to trackedAfterGC.
             */
            return "trackedBeforeGC != trackedAfterGC";
        }

        return "OK";
    }

    @Test(expected = GraalError.class)
    @SuppressWarnings("try")
    public void testVerification() {
        DebugContext debug = getDebugContext();
        try (DebugCloseable d = debug.disableIntercept(); DebugContext.Scope s = debug.scope("PointerTrackingTest")) {
            compile(getResolvedJavaMethod("verificationSnippet"), null);
        }
    }

    public static long verificationSnippet(Object obj) {
        long value = getTrackedPointer(obj) * 7 + 3;

        /*
         * Ensure usage before and after GC to force the value to be alive across the safepoint.
         * This should lead to a compiler error, since value can not be tracked in the reference
         * map.
         */
        GraalDirectives.blackhole(value);
        System.gc();
        return value;
    }

    static long getTrackedPointer(@SuppressWarnings("unused") Object obj) {
        throw GraalError.shouldNotReachHere("should be intrinsified");
    }

    static long getUntrackedPointer(@SuppressWarnings("unused") Object obj) {
        throw GraalError.shouldNotReachHere("should be intrinsified");
    }

    static long getTrackedPointerIntrinsic(Object obj) {
        return Word.objectToTrackedPointer(obj).rawValue();
    }

    static long getUntrackedPointerIntrinsic(Object obj) {
        return Word.objectToUntrackedPointer(obj).rawValue();
    }

    private void register(Registration r, String fnName) {
        ResolvedJavaMethod intrinsic = getResolvedJavaMethod(fnName + "Intrinsic");
        BytecodeProvider bytecodeProvider = getSystemClassLoaderBytecodeProvider();
        r.register1(fnName, Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                return b.intrinsify(bytecodeProvider, targetMethod, intrinsic, receiver, new ValueNode[]{arg});
            }
        });
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        Registration r = new Registration(invocationPlugins, PointerTrackingTest.class);

        register(r, "getTrackedPointer");
        register(r, "getUntrackedPointer");
    }
}
