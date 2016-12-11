/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.api.directives.test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.google.monitoring.runtime.instrumentation.common.com.google.common.base.Objects;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionValue.OverrideScope;
import org.graalvm.compiler.printer.IdealGraphPrinter;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class RootNameDirectiveTest extends GraalCompilerTest {

    public RootNameDirectiveTest() {
        HotSpotResolvedJavaMethod rootNameAtCalleeSnippet = (HotSpotResolvedJavaMethod) getResolvedJavaMethod(RootNameDirectiveTest.class, "rootNameAtCalleeSnippet");
        rootNameAtCalleeSnippet.shouldBeInlined();

        HotSpotResolvedJavaMethod rootNameWithinInstrumentationSnippet = (HotSpotResolvedJavaMethod) getResolvedJavaMethod(RootNameDirectiveTest.class, "rootNameWithinInstrumentationSnippet");
        rootNameWithinInstrumentationSnippet.shouldBeInlined();
    }

    private static String toString(ResolvedJavaMethod method) {
        return method.getDeclaringClass().toJavaName() + "." + method.getName() + method.getSignature().toMethodDescriptor();
    }

    public static String rootNameSnippet() {
        return GraalDirectives.rootName();
    }

    @Test
    public void testRootName() {
        ResolvedJavaMethod method = getResolvedJavaMethod("rootNameSnippet");
        executeExpected(method, null); // ensure the method is fully resolved
        // The target snippet is already the root method. We expect the name of the target snippet
        // is returned.
        InstalledCode code = getCode(method);
        try {
            Result result = new Result(code.executeVarargs(), null);
            assertEquals(new Result(toString(method), null), result);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    public static String rootNameAtCalleeSnippet() {
        return GraalDirectives.rootName();
    }

    public static String callerSnippet() {
        return rootNameAtCalleeSnippet();
    }

    @Test
    public void testRootNameAtCallee() {
        ResolvedJavaMethod method = getResolvedJavaMethod("callerSnippet");
        executeExpected(method, null); // ensure the method is fully resolved
        // The target snippet is the compilation root of rootNameAtCalleeSnippet() because the later
        // will be inlined. We expect the name of the target snippet is returned.
        InstalledCode code = getCode(method);
        try {
            Result result = new Result(code.executeVarargs(), null);
            assertEquals(new Result(toString(method), null), result);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    static String rootNameInCallee;
    static String rootNameInCaller;

    @BytecodeParserForceInline
    public static void rootNameWithinInstrumentationSnippet() {
        GraalDirectives.instrumentationBegin();
        rootNameInCallee = GraalDirectives.rootName();
        GraalDirectives.instrumentationEnd();
    }

    public static void callerSnippet1() {
        rootNameWithinInstrumentationSnippet();

        GraalDirectives.instrumentationBegin();
        rootNameInCaller = GraalDirectives.rootName();
        GraalDirectives.instrumentationEnd();
    }

    @SuppressWarnings("try")
    private void assertEquals(StructuredGraph graph, InstalledCode code, Object expected, Object actual) {
        if (!Objects.equal(expected, actual)) {
            Formatter buf = new Formatter();

            try (Scope s = Debug.sandbox("PrintingGraph", null)) {
                Map<Object, Object> properties = new HashMap<>();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                IdealGraphPrinter printer = new IdealGraphPrinter(baos, true, getSnippetReflection());
                printer.beginGroup("RootNameDirectiveTest", "RootNameDirectiveTest", graph.method(), -1, null);
                properties.put("graph", graph.toString());
                properties.put("scope", Debug.currentScope());
                printer.print(graph, graph.method().format("%H.%n(%p)"), properties);
                printer.endGroup();
                printer.close();
                buf.format("-- Graph -- %n%s", baos.toString());
            } catch (Throwable e) {
                buf.format("%nError printing graph: %s", e);
            }
            try {
                CodeCacheProvider codeCache = getCodeCache();
                Method disassemble = codeCache.getClass().getMethod("disassemble", InstalledCode.class);
                buf.format("%n-- Code -- %n%s", disassemble.invoke(codeCache, code));
            } catch (NoSuchMethodException e) {
                // Not a HotSpotCodeCacheProvider
            } catch (Exception e) {
                buf.format("%nError disassembling code: %s", e);
            }
            Assert.assertEquals(buf.toString(), expected, actual);
        }
    }

    @SuppressWarnings("try")
    @Test
    public void testRootNameWithinInstrumentationAtCallee() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            ResolvedJavaMethod method = getResolvedJavaMethod("callerSnippet1");
            executeExpected(method, null); // ensure the method is fully resolved
            rootNameInCallee = null;
            rootNameInCaller = null;
            // We expect both rootName1 and rootName2 are set to the name of the target snippet.
            StructuredGraph graph = parseForCompile(method);
            InstalledCode code = getCode(method, graph);
            code.executeVarargs();
            assertEquals(graph, code, toString(method), rootNameInCallee);
            assertEquals(graph, code, rootNameInCallee, rootNameInCaller);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

}
