/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.generators;

/**
 * A restrictable generator allows the creation of a new generator by restricting the range of its output values.
 * The exact semantics of this restriction depend on the concrete implementation, but it usually means taking the
 * intersection of the old range and the newly requested range of values.
 */
public interface RestrictableGenerator<T> extends Generator<T> {
    /**
     * Returns a new generator where the range of this generator has been restricted to the range of newLo and newHi.
     * Whether newHi is inclusive or exclusive depends on the concrete implementation.
     * @throws EmptyGeneratorException if this restriction would result in an empty generator.
     */
    RestrictableGenerator<T> restricted(T newLo, T newHi);
}
