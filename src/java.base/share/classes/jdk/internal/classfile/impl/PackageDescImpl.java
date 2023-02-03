/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;

import jdk.internal.classfile.jdktypes.PackageDesc;

public record PackageDescImpl(String packageInternalName) implements PackageDesc {

    /**
     * Validates the correctness of a binary package name. In particular checks for the presence of
     * invalid characters in the name.
     *
     * @param name the package name
     * @return the package name passed if valid
     * @throws IllegalArgumentException if the package name is invalid
     */
    public static String validateBinaryPackageName(String name) {
        for (int i=0; i<name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == ';' || ch == '[' || ch == '/')
                throw new IllegalArgumentException("Invalid package name: " + name);
        }
        return name;
    }

    /**
     * Validates the correctness of an internal package name.
     * In particular checks for the presence of invalid characters in the name.
     *
     * @param name the package name
     * @return the package name passed if valid
     * @throws IllegalArgumentException if the package name is invalid
     */
    public static String validateInternalPackageName(String name) {
        for (int i=0; i<name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == ';' || ch == '[' || ch == '.')
                throw new IllegalArgumentException("Invalid package name: " + name);
        }
        return name;
    }

    public static String internalToBinary(String name) {
        return name.replace('/', '.');
    }

    public static String binaryToInternal(String name) {
        return name.replace('.', '/');
    }
}
