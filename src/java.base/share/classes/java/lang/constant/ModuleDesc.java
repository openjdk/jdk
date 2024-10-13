/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.constant;

import jdk.internal.constant.ConstantUtils;
import jdk.internal.constant.ModuleDescImpl;

import static java.util.Objects.requireNonNull;

/**
 * A nominal descriptor for a {@code Module} constant.
 *
 * <p>
 * To create a {@link ModuleDesc} for a module, use the {@link #of(String)}
 * method.
 *
 * @jvms 4.4.11 The CONSTANT_Module_info Structure
 * @since 21
 */
public sealed interface ModuleDesc
        permits ModuleDescImpl {

    /**
     * Returns a {@link ModuleDesc} for a module,
     * given the name of the module.
     *
     * @param name the module name
     * @return a {@link ModuleDesc} describing the desired module
     * @throws NullPointerException if the argument is {@code null}
     * @throws IllegalArgumentException if the name string is not in the
     * correct format
     * @jvms 4.2.3 Module and Package Names
     */
    static ModuleDesc of(String name) {
        ConstantUtils.validateModuleName(requireNonNull(name));
        return new ModuleDescImpl(name);
    }

    /**
     * Returns the module name of this {@link ModuleDesc}.
     *
     * @return the module name
     */
    String name();

    /**
     * Compare the specified object with this descriptor for equality.
     * Returns {@code true} if and only if the specified object is
     * also a {@link ModuleDesc} and both describe the same module.
     *
     * @param o the other object
     * @return whether this descriptor is equal to the other object
     */
    @Override
    boolean equals(Object o);
}
