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
package jdk.vm.ci.compiler;

import jdk.vm.ci.meta.*;
import jdk.vm.ci.options.*;

public interface Compiler {
    int INVOCATION_ENTRY_BCI = -1;

    @Option(help = "", type = OptionType.Debug) OptionValue<String> PrintFilter = new OptionValue<>(null);
    @Option(help = "", type = OptionType.Debug) OptionValue<Boolean> PrintCompilation = new OptionValue<>(false);
    @Option(help = "", type = OptionType.Debug) OptionValue<Boolean> PrintAfterCompilation = new OptionValue<>(false);
    @Option(help = "", type = OptionType.Debug) OptionValue<Boolean> PrintBailout = new OptionValue<>(false);
    @Option(help = "", type = OptionType.Debug) OptionValue<Boolean> ExitVMOnBailout = new OptionValue<>(false);
    @Option(help = "", type = OptionType.Debug) OptionValue<Boolean> ExitVMOnException = new OptionValue<>(true);
    @Option(help = "", type = OptionType.Debug) OptionValue<Boolean> PrintStackTraceOnException = new OptionValue<>(false);

    /**
     * Request the compilation of a method by this JVMCI compiler. The compiler should compile the
     * method to machine code and install it in the code cache if the compilation is successful.
     *
     * @param method the method that should be compiled
     * @param entryBCI the BCI at which to start compiling where -1 denotes a non-OSR compilation
     *            request and all other values denote an OSR compilation request
     * @param jvmciEnv pointer to native {@code JVMCIEnv} object
     * @param id a unique identifier for this compilation
     */
    void compileMethod(ResolvedJavaMethod method, int entryBCI, long jvmciEnv, int id);
}
