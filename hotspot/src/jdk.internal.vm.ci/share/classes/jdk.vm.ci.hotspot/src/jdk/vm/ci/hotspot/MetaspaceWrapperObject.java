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

/**
 * A tag interface indicating that this type is a wrapper around a HotSpot metaspace object that
 * requires GC interaction to keep alive.
 *
 * It would preferable if this were the base class containing the pointer but that would require
 * mixins since most of the wrapper types have complex supertype hierarchies.
 */
interface MetaspaceWrapperObject {

    long getMetaspacePointer();

    /**
     * Check if this object is properly registered for metadata tracking. All classes which
     * implement this interface must be registered with the
     * {@link HotSpotJVMCIMetaAccessContext#add} unless they are kept alive through other means.
     * Currently the only type which doesn't require explicit registration is
     * {@link HotSpotResolvedObjectTypeImpl} since it's kept alive by references to the
     * {@link Class}.
     *
     * @return true if this object is properly registered for meta data tracking.
     */
    default boolean isRegistered() {
        return HotSpotJVMCIRuntime.runtime().metaAccessContext.isRegistered(this);
    }
}
