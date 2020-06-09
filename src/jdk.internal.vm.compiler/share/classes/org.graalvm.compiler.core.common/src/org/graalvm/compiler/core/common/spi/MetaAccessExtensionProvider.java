/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.common.spi;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Provides additional meta data about JVMCI objects that is not provided by the VM itself, and
 * therefore does not need to be in JVMCI itself.
 */
public interface MetaAccessExtensionProvider {

    /**
     * The {@link JavaKind} used to store the provided type in a field or array element. This can be
     * different than the {@link JavaType#getJavaKind} for types that are intercepted and
     * transformed by the compiler.
     */
    JavaKind getStorageKind(JavaType type);

    /**
     * Checks if a dynamic allocation of the provided type can be canonicalized to a regular
     * allocation node. If the method returns false, then the dynamic allocation would throw an
     * exception at run time and therefore canonicalization would miss that exception.
     */
    boolean canConstantFoldDynamicAllocation(ResolvedJavaType type);
}
