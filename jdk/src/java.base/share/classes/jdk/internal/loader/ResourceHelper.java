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
package jdk.internal.loader;

import jdk.internal.module.Checks;

/**
 * Helper class for Class#getResource, Module#getResourceAsStream, and other
 * methods that locate a resource in a module.
 */
public final class ResourceHelper {
    private ResourceHelper() { }

    /**
     * Returns the <em>package name</em> for a resource.
     */
    public static String getPackageName(String name) {
        int index = name.lastIndexOf('/');
        if (index != -1) {
            return name.substring(0, index).replace("/", ".");
        } else {
            return "";
        }
    }

    /**
     * Returns true if the resource is a <em>simple resource</em> that can
     * never be encapsulated. Resources ending in "{@code .class}" or where
     * the package name is not a Java identifier are resources that can
     * never be encapsulated.
     */
    public static boolean isSimpleResource(String name) {
        int len = name.length();
        if (len > 6 && name.endsWith(".class")) {
            return true;
        }
        if (!Checks.isJavaIdentifier(getPackageName(name))) {
            return true;
        }
        return false;
    }
}
