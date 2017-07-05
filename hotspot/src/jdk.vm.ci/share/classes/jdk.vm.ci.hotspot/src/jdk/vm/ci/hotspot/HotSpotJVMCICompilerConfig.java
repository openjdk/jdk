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
package jdk.vm.ci.hotspot;

import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompilationRequestResult;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.runtime.JVMCICompiler;
import jdk.vm.ci.runtime.JVMCICompilerFactory;
import jdk.vm.ci.runtime.JVMCIRuntime;
import jdk.vm.ci.services.Services;

final class HotSpotJVMCICompilerConfig {

    private static class DummyCompilerFactory implements JVMCICompilerFactory, JVMCICompiler {

        public CompilationRequestResult compileMethod(CompilationRequest request) {
            throw new JVMCIError("no JVMCI compiler selected");
        }

        public String getCompilerName() {
            return "<none>";
        }

        public JVMCICompiler createCompiler(JVMCIRuntime runtime) {
            return this;
        }
    }

    private static JVMCICompilerFactory compilerFactory;

    /**
     * Selects the system compiler.
     *
     * Called from VM. This method has an object return type to allow it to be called with a VM
     * utility function used to call other static initialization methods.
     */
    static Boolean selectCompiler(String compilerName) {
        assert compilerFactory == null;
        for (JVMCICompilerFactory factory : Services.load(JVMCICompilerFactory.class)) {
            if (factory.getCompilerName().equals(compilerName)) {
                compilerFactory = factory;
                return Boolean.TRUE;
            }
        }

        throw new JVMCIError("JVMCI compiler '%s' not found", compilerName);
    }

    static JVMCICompilerFactory getCompilerFactory() {
        if (compilerFactory == null) {
            compilerFactory = new DummyCompilerFactory();
        }
        return compilerFactory;
    }
}
