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
package jdk.vm.ci.runtime;

/**
 * Factory for a JVMCI compiler.
 */
public interface JVMCICompilerFactory {

    /**
     * Get the name of this compiler.
     */
    String getCompilerName();

    /**
     * Create a new instance of the {@link JVMCICompiler}.
     */
    JVMCICompiler createCompiler(JVMCIRuntime runtime);

    /**
     * In a tiered system it might be advantageous for startup to keep the JVMCI compiler from
     * compiling itself so provide a hook to request that certain packages are compiled only by an
     * optimizing first tier. The prefixes should class or package names using / as the separator,
     * i.e. jdk/vm/ci for instance.
     *
     * @return 0 or more Strings identifying packages that should by compiled by the first tier
     *         only.
     */
    default String[] getTrivialPrefixes() {
        return null;
    }
}
