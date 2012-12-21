/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.runtime.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.SwitchPoint;
import org.dynalang.dynalink.linker.GuardedInvocation;

/**
 * Guarded invocation with Nashorn specific bits.
 */
public class NashornGuardedInvocation extends GuardedInvocation {
    private final int flags;

    /**
     * Constructor
     *
     * @param invocation  invocation target
     * @param switchPoint SwitchPoint that will, when invalidated, require relinking callsite, null if no SwitchPoint
     * @param guard       guard that will, when failed, require relinking callsite, null if no guard
     * @param flags       callsite flags
     */
    public NashornGuardedInvocation(final MethodHandle invocation, final SwitchPoint switchPoint, final MethodHandle guard, final int flags) {
        super(invocation, switchPoint, guard);
        this.flags = flags;

    }

    /**
     * Is this invocation created from a callsite with strict semantics
     * @return true if strict
     */
    public boolean isStrict() {
        return (flags & NashornCallSiteDescriptor.CALLSITE_STRICT) != 0;
    }

    /**
     * Check whether a GuardedInvocation is created from a callsite with strict semantics
     * @param inv guarded invocation
     * @return true if strict
     */
    public static boolean isStrict(final GuardedInvocation inv) {
        return (inv instanceof NashornGuardedInvocation)? ((NashornGuardedInvocation)inv).isStrict() : false;
    }
}
