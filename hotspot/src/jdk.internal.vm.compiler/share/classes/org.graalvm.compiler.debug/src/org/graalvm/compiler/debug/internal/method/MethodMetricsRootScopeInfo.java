/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug.internal.method;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.internal.DebugScope;
import org.graalvm.compiler.debug.internal.DebugScope.ExtraInfo;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MethodMetricsRootScopeInfo implements ExtraInfo {
    protected final ResolvedJavaMethod rootMethod;

    MethodMetricsRootScopeInfo(ResolvedJavaMethod rootMethod) {
        this.rootMethod = rootMethod;
    }

    public ResolvedJavaMethod getRootMethod() {
        return rootMethod;
    }

    public static MethodMetricsRootScopeInfo create(ResolvedJavaMethod rootMethod) {
        return new MethodMetricsRootScopeInfo(rootMethod);
    }

    /**
     * Creates and returns a {@link org.graalvm.compiler.debug.Debug.Scope scope} iff there is no
     * existing {@linkplain org.graalvm.compiler.debug.internal.DebugScope.ExtraInfo extraInfo}
     * object of type {@link MethodMetricsRootScopeInfo} present in the current {@link DebugScope
     * scope}.
     *
     * @param method
     * @return a new {@link org.graalvm.compiler.debug.Debug.Scope scope} or {@code null} iff there
     *         is already an existing one on the scope
     */
    public static Debug.Scope createRootScopeIfAbsent(ResolvedJavaMethod method) {
        /*
         * if the current compilation is not triggered from JVMCI we need a valid context root
         * method for method metrics
         */
        return DebugScope.getInstance().getExtraInfo() instanceof MethodMetricsRootScopeInfo ? null : Debug.methodMetricsScope("GraalCompilerRoot", MethodMetricsRootScopeInfo.create(method), true);
    }

}
