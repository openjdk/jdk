/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jdi;


/**
 * A module in the target VM.
 * <p>
 * Any method on {@code ModuleReference} which directly or
 * indirectly takes {@code ModuleReference} as an parameter may throw
 * {@link com.sun.jdi.VMDisconnectedException} if the target VM is
 * disconnected and the {@link com.sun.jdi.event.VMDisconnectEvent} has been or is
 * available to be read from the {@link com.sun.jdi.event.EventQueue}.
 * <p>
 * Any method on {@code ModuleReference} which directly or
 * indirectly takes {@code ModuleReference} as an parameter may throw
 * {@link com.sun.jdi.VMOutOfMemoryException} if the target VM has run out of memory.
 * <p>
 * Any method on {@code ModuleReference} or which directly or indirectly takes
 * {@code ModuleReference} as parameter may throw
 * {@link com.sun.jdi.InvalidModuleException} if the mirrored module
 * has been unloaded.
 *
 * Not all target virtual machines support this class.
 * Use {@link VirtualMachine#canGetModuleInfo()}
 * to determine if the class is supported.
 *
 * @since  9
 */
public interface ModuleReference extends ObjectReference {

    /**
     * Returns the module name.
     * This method returns {@code null}
     * if this module is an unnamed module.
     *
     * @return the name of this module.
     */
    String name();

    /**
     * Returns the {@link ClassLoaderReference} object for this module.
     *
     * @return the {@link ClassLoaderReference} object for this module.
     */
    ClassLoaderReference classLoader();

    /**
     * Indicates if this module reads another module.
     *
     * @return {@code true} if this module reads {@code other},
     *         {@code false} otherwise
     */
    boolean canRead(ModuleReference other);
}
