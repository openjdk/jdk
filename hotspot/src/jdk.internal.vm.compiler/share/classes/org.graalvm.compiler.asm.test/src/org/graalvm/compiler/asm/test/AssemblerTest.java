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
package org.graalvm.compiler.asm.test;

import static org.graalvm.compiler.core.common.CompilationRequestIdentifier.asCompilationRequest;

import java.lang.reflect.Method;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.DisassemblerProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.test.GraalTest;
import org.junit.Assert;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;

public abstract class AssemblerTest extends GraalTest {

    private final MetaAccessProvider metaAccess;
    protected final CodeCacheProvider codeCache;
    private final Backend backend;

    public interface CodeGenTest {
        byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc);
    }

    /**
     * Gets the initial option values provided by the Graal runtime. These are option values
     * typically parsed from the command line.
     */
    public static OptionValues getInitialOptions() {
        return Graal.getRequiredCapability(OptionValues.class);
    }

    public AssemblerTest() {
        JVMCIBackend providers = JVMCI.getRuntime().getHostJVMCIBackend();
        this.metaAccess = providers.getMetaAccess();
        this.codeCache = providers.getCodeCache();
        this.backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    @SuppressWarnings("try")
    protected InstalledCode assembleMethod(Method m, CodeGenTest test) {
        ResolvedJavaMethod method = getMetaAccess().lookupJavaMethod(m);
        OptionValues options = getInitialOptions();
        DebugContext debug = getDebugContext(options);
        try (DebugContext.Scope s = debug.scope("assembleMethod", method, codeCache)) {
            RegisterConfig registerConfig = codeCache.getRegisterConfig();
            CompilationIdentifier compilationId = backend.getCompilationIdentifier(method);
            StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(method).compilationId(compilationId).build();
            CallingConvention cc = backend.newLIRGenerationResult(compilationId, null, null, graph, null).getCallingConvention();

            CompilationResult compResult = new CompilationResult(graph.compilationId());
            byte[] targetCode = test.generateCode(compResult, codeCache.getTarget(), registerConfig, cc);
            compResult.setTargetCode(targetCode, targetCode.length);
            compResult.setTotalFrameSize(0);
            compResult.close();

            InstalledCode code = backend.addInstalledCode(debug, method, asCompilationRequest(compilationId), compResult);

            for (DisassemblerProvider dis : GraalServices.load(DisassemblerProvider.class)) {
                String disasm1 = dis.disassembleCompiledCode(codeCache, compResult);
                Assert.assertTrue(compResult.toString(), disasm1 == null || disasm1.length() > 0);
                String disasm2 = dis.disassembleInstalledCode(codeCache, compResult, code);
                Assert.assertTrue(code.toString(), disasm2 == null || disasm2.length() > 0);
            }
            return code;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected Object runTest(String methodName, CodeGenTest test, Object... args) {
        Method method = getMethod(methodName);
        InstalledCode code = assembleMethod(method, test);
        try {
            return code.executeVarargs(args);
        } catch (InvalidInstalledCodeException e) {
            throw new RuntimeException(e);
        }
    }

    protected void assertReturn(String methodName, CodeGenTest test, Object expected, Object... args) {
        Object actual = runTest(methodName, test, args);
        Assert.assertEquals("unexpected return value", expected, actual);
    }
}
