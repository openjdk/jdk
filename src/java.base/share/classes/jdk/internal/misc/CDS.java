/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.misc;

public class CDS {
    /**
     * Initialize archived static fields in the given Class using archived
     * values from CDS dump time. Also initialize the classes of objects in
     * the archived graph referenced by those fields.
     *
     * Those static fields remain as uninitialized if there is no mapped CDS
     * java heap data or there is any error during initialization of the
     * object class in the archived graph.
     */
    public static native void initializeFromArchive(Class<?> c);

    /**
     * Ensure that the native representation of all archived java.lang.Module objects
     * are properly restored.
     */
    public static native void defineArchivedModules(ClassLoader platformLoader, ClassLoader systemLoader);

    /**
     * Returns a predictable "random" seed derived from the VM's build ID and version,
     * to be used by java.util.ImmutableCollections to ensure that archived
     * ImmutableCollections are always sorted the same order for the same VM build.
     */
    public static native long getRandomSeedForDumping();

    /**
     * Check if dynamic dumping is enabled via the DynamicDumpSharedSpaces flag.
     */
    public static native boolean isDynamicDumpingEnabled(); // will return false for static dumping.

    /**
     * Check if sharing is enabled via the UseSharedSpaces flag.
     */
    public static native boolean isSharingEnabled();
}
