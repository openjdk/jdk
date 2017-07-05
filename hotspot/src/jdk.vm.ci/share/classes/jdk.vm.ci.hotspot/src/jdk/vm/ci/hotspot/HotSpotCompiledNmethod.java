/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.inittimer.SuppressFBWarnings;

/**
 * {@link HotSpotCompiledCode} destined for installation as an nmethod.
 */
public final class HotSpotCompiledNmethod extends HotSpotCompiledCode {

    public final HotSpotResolvedJavaMethod method;
    public final int entryBCI;

    /**
     * Compilation identifier.
     */
    public final int id;

    /**
     * Address of a native {@code JVMCIEnv} object or 0L if no such object exists.
     */
    public final long jvmciEnv;

    public final boolean hasUnsafeAccess;

    /**
     * May be set by VM if code installation fails. It will describe in more detail why installation
     * failed (e.g., exactly which dependency failed).
     */
    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD", justification = "set by the VM") private String installationFailureMessage;

    public HotSpotCompiledNmethod(HotSpotResolvedJavaMethod method, CompilationResult compResult, int id, long jvmciEnv) {
        super(compResult);
        this.method = method;
        this.entryBCI = compResult.getEntryBCI();
        this.id = id;
        this.jvmciEnv = jvmciEnv;
        this.hasUnsafeAccess = compResult.hasUnsafeAccess();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + ":" + method.format("%H.%n(%p)%r@") + entryBCI + "]";
    }

    public String getInstallationFailureMessage() {
        return installationFailureMessage;
    }
}
