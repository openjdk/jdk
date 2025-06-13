/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.model;

import java.util.Set;

/**
 * Bundling environment. Defines available bundling operations.
 */
public interface BundlingEnvironment {

    /**
     * Returns the default bundling operation.
     * <p>
     * The returned value should be one of the elements in the collection returned by {@link #enabledOperations()} method.
     * @return the default bundling operation
     * @throws ConfigException in not a single bundling operation can be performed.
     */
    BundlingOperation defaultOperation() throws ConfigException;

    /**
     * Returns supported bundling operations.
     * @return the supported bundling operations
     */
    Set<BundlingOperation> supportedOperations();

    /**
     * Returns enabled bundling operations.
     * <p>
     * The returned value should be a subset of the set returned by {@link #supportedOperations()} method.
     * @return the enabled bundling operations
     */
    default Set<BundlingOperation> enabledOperations() {
        return supportedOperations();
    }

    /**
     * Returns a bundle creator corresponding to the given bundling operation in this bundling environment.
     * @param op the bundling operation
     * @return bundle creator corresponding to the given bundling operation in this bundling environment
     * @throws IllegalArgumentException if the given bundling operation is not enabled in this bundling environment
     */
    BundleCreator<?> getBundleCreator(BundlingOperation op);
}
