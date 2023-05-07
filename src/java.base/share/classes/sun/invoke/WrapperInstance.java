/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.invoke;

import sun.invoke.empty.Empty;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * Private API used inside of java.lang.invoke.MethodHandles.
 * Interface implemented by every object which is produced by
 * {@link java.lang.invoke.MethodHandleProxies#asInterfaceInstance
 * MethodHandleProxies.asInterfaceInstance}.
 * The methods of this interface allow a caller to recover the parameters
 * to {@code asInstance}.
 * This allows applications to repeatedly convert between method handles
 * and SAM objects, without the risk of creating unbounded delegation chains.
 * The methods have an empty parameter to avoid accidental clashes with
 * the implemented SAM methods.
 */
public interface WrapperInstance {
    /**
     * Called by proxies implementation to ensure the constructor is called by proper callers.
     */
    static void ensureOriginalLookup(MethodHandles.Lookup lookup, Class<?> lookupClass) throws IllegalAccessException {
        if (lookup.lookupClass() != lookupClass || (lookup.lookupModes() & MethodHandles.Lookup.ORIGINAL) == 0) {
            throw new IllegalAccessException("Illegal caller to " + lookupClass + ": " + lookup);
        }
    }

    /** Produce or recover a target method handle which is behaviorally
     *  equivalent to the SAM method of this object.
     */
    MethodHandle getWrapperInstanceTarget(Empty empty);
    /** Recover the SAM type for which this object was created.
     */
    Class<?> getWrapperInstanceType(Empty empty);
}