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
package jdk.vm.ci.compiler;

import jdk.vm.ci.code.*;
import jdk.vm.ci.runtime.*;

/**
 * Factory for a JVMCI compiler.
 */
public interface CompilerFactory {

    /**
     * Get the name of this compiler. The compiler will be selected when the jvmci.compiler system
     * property is equal to this name.
     */
    String getCompilerName();

    /**
     * Initialize an {@link Architecture}. The compiler has the opportunity to extend the
     * {@link Architecture} description with a custom subclass.
     */
    Architecture initializeArchitecture(Architecture arch);

    /**
     * Create a new instance of the {@link Compiler}.
     */
    Compiler createCompiler(JVMCIRuntime runtime);
}
