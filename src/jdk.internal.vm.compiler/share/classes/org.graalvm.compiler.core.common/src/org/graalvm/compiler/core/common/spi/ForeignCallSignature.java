/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

/**
 * The name and signature of a {@link ForeignCallDescriptor foreign call}.
 */
public final class ForeignCallSignature {

    private final String name;
    private final Class<?> resultType;
    private final Class<?>[] argumentTypes;

    public ForeignCallSignature(String name, Class<?> resultType, Class<?>... argumentTypes) {
        this.name = name;
        this.resultType = resultType;
        this.argumentTypes = argumentTypes;
    }

    /**
     * Gets the name of this foreign call.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the return type of this foreign call.
     */
    public Class<?> getResultType() {
        return resultType;
    }

    /**
     * Gets the argument types of this foreign call.
     */
    public Class<?>[] getArgumentTypes() {
        return argumentTypes.clone();
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ForeignCallSignature) {
            ForeignCallSignature other = (ForeignCallSignature) obj;
            return other.name.equals(name) && other.resultType.equals(resultType) && Arrays.equals(other.argumentTypes, argumentTypes);
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name).append('(');
        String sep = "";
        for (Class<?> arg : argumentTypes) {
            sb.append(sep).append(arg.getSimpleName());
            sep = ",";
        }
        return sb.append(')').append(resultType.getSimpleName()).toString();
    }
}
