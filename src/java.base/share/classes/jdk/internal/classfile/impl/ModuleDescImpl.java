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

import jdk.internal.classfile.jdktypes.ModuleDesc;

public record ModuleDescImpl(String moduleName) implements ModuleDesc {

    /**
     * Validates the correctness of a module name. In particular checks for the presence of
     * invalid characters in the name.
     *
     * JVMS: 4.2.3. Module and Package Names
     *
     * @param name the module name
     * @return the module name passed if valid
     * @throws IllegalArgumentException if the module name is invalid
     */
    public static String validateModuleName(String name) {
        for (int i=name.length() - 1; i >= 0; i--) {
            char ch = name.charAt(i);
            if ((ch >= '\u0000' && ch <= '\u001F')
            || ((ch == '\\' || ch == ':' || ch =='@') && (i == 0 || name.charAt(--i) != '\\')))
                throw new IllegalArgumentException("Invalid module name: " + name);
        }
        return name;
    }
}
