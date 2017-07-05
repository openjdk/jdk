/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import jdk.vm.ci.code.*;
import jdk.vm.ci.common.*;
import jdk.vm.ci.compiler.*;
import jdk.vm.ci.compiler.Compiler;
import jdk.vm.ci.meta.*;
import jdk.vm.ci.runtime.*;
import jdk.vm.ci.service.*;

final class HotSpotJVMCICompilerConfig {

    private static class DummyCompilerFactory implements CompilerFactory, Compiler {

        public void compileMethod(ResolvedJavaMethod method, int entryBCI, long jvmciEnv, int id) {
            throw new JVMCIError("no JVMCI compiler selected");
        }

        public String getCompilerName() {
            return "<none>";
        }

        public Architecture initializeArchitecture(Architecture arch) {
            return arch;
        }

        public Compiler createCompiler(JVMCIRuntime runtime) {
            return this;
        }
    }

    private static CompilerFactory compilerFactory;

    /**
     * Selects the system compiler.
     *
     * Called from VM. This method has an object return type to allow it to be called with a VM
     * utility function used to call other static initialization methods.
     */
    static Boolean selectCompiler(String compilerName) {
        assert compilerFactory == null;
        for (CompilerFactory factory : Services.load(CompilerFactory.class)) {
            if (factory.getCompilerName().equals(compilerName)) {
                compilerFactory = factory;
                return Boolean.TRUE;
            }
        }

        throw new JVMCIError("JVMCI compiler '%s' not found", compilerName);
    }

    static CompilerFactory getCompilerFactory() {
        if (compilerFactory == null) {
            compilerFactory = new DummyCompilerFactory();
        }
        return compilerFactory;
    }
}
