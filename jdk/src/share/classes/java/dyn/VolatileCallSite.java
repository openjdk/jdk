/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.dyn;

import java.util.List;

/**
 * A {@code VolatileCallSite} is a {@link CallSite} whose target acts like a volatile variable.
 * An {@code invokedynamic} instruction linked to a {@code VolatileCallSite} sees updates
 * to its call site target immediately, even if the update occurs in another thread.
 * There may be a performance penalty for such tight coupling between threads.
 * <p>
 * Unlike {@code MutableCallSite}, there is no
 * {@linkplain MutableCallSite#sync sync operation} on volatile
 * call sites, since every write to a volatile variable is implicitly
 * synchronized with reader threads.
 * <p>
 * In other respects, a {@code VolatileCallSite} is interchangeable
 * with {@code MutableCallSite}.
 * @see MutableCallSite
 * @author John Rose, JSR 292 EG
 */
public class VolatileCallSite extends CallSite {
    /** Create a call site with a volatile target.
     *  The initial target is set to a method handle
     *  of the given type which will throw {@code IllegalStateException}.
     * @throws NullPointerException if the proposed type is null
     */
    public VolatileCallSite(MethodType type) {
        super(type);
    }

    /** Create a call site with a volatile target.
     *  The target is set to the given value.
     * @throws NullPointerException if the proposed target is null
     */
    public VolatileCallSite(MethodHandle target) {
        super(target);
    }

    /** Internal override to nominally final getTarget. */
    @Override
    MethodHandle getTarget0() {
        return getTargetVolatile();
    }

    /**
     * Set the target method of this call site, as a volatile variable.
     * Has the same effect as {@link CallSite#setTarget CallSite.setTarget}, with the additional
     * effects associated with volatiles, in the Java Memory Model.
     */
    @Override public void setTarget(MethodHandle newTarget) {
        checkTargetChange(getTargetVolatile(), newTarget);
        setTargetVolatile(newTarget);
    }
}
