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

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Bundling environment. Defines available bundling operations.
 */
public interface BundlingEnvironment {

    /**
     * Returns descriptor of the default bundling operation if there is such or an
     * empty {@link Optional} instance otherwise.
     *
     * @return the default bundling operation or an empty {@link Optional} instance
     *         if there is no such
     */
    Optional<BundlingOperationDescriptor> defaultOperation();

    /**
     * Returns configuration errors or an empty list if there are no such errors for
     * the target bundling operation.
     *
     * @param op the descriptor of the target bundling operation
     * @return the list of configuration errors or an empty list if there are no
     *         such errors for the specified bundling operation
     * @throws NoSuchElementException if the specified descriptor denotes an
     *                                unsupported bundling operation
     */
    default Collection<? extends Exception> configurationErrors(BundlingOperationDescriptor op) {
        return List.of();
    }
}
