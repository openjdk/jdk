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
package jdk.vm.ci.runtime.services;

import jdk.vm.ci.runtime.JVMCICompiler;
import jdk.vm.ci.runtime.JVMCIRuntime;
import jdk.vm.ci.services.JVMCIPermission;

/**
 * Service-provider class for creating JVMCI compilers.
 */
public abstract class JVMCICompilerFactory {

    private static Void checkPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new JVMCIPermission());
        }
        return null;
    }

    @SuppressWarnings("unused")
    private JVMCICompilerFactory(Void ignore) {
    }

    /**
     * Initializes a new instance of this class.
     *
     * @throws SecurityException if a security manager has been installed and it denies
     *             {@link JVMCIPermission}
     */
    protected JVMCICompilerFactory() {
        this(checkPermission());
    }

    /**
     * Get the name of this compiler. The name is used by JVMCI to determine which factory to use.
     */
    public abstract String getCompilerName();

    /**
     * Notifies this object that it has been selected to {@linkplain #createCompiler(JVMCIRuntime)
     * create} a compiler and it should now perform any heavy weight initialization that it deferred
     * during construction.
     */
    public void onSelection() {
    }

    /**
     * Create a new instance of a {@link JVMCICompiler}.
     */
    public abstract JVMCICompiler createCompiler(JVMCIRuntime runtime);

    /**
     * In a tiered system it might be advantageous for startup to keep the JVMCI compiler from
     * compiling itself so provide a hook to request that certain packages are compiled only by an
     * optimizing first tier. The prefixes should class or package names using / as the separator,
     * i.e. jdk/vm/ci for instance.
     *
     * @return 0 or more Strings identifying packages that should by compiled by the first tier only
     *         or null if no redirection to C1 should be performed.
     */
    public String[] getTrivialPrefixes() {
        return null;
    }
}
