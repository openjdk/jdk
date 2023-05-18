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

package jdk.internal.misc;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class MainMethodFinder {
    private static boolean correctArgs(Method method) {
        int argc = method.getParameterCount();

        return argc == 0 || argc == 1 && method.getParameterTypes()[0] == String[].class;
    }

    /**
     * Gather all the "main" methods in the class hierarchy.
     *
     * @param declc  the top level declaring class
     * @param refc   the declaring class or super class
     * @param mains  accumulated main methods
     */
    private static void gatherMains(Class<?> declc, Class<?> refc, List<Method> mains) {
        if (refc != null && refc != Object.class) {
            for (Method method : refc.getDeclaredMethods()) {
                // Must be named "main", public|protected|package-private, not synthetic (bridge) and either
                // no arguments or one string array argument.
                if ("main".equals(method.getName()) &&
                        !method.isSynthetic() &&
                        !Modifier.isPrivate(method.getModifiers()) &&
                        correctArgs(method))
                {
                    mains.add(method);
                }
            }

            gatherMains(declc, refc.getSuperclass(), mains);
        }
    }

    /**
     * Comparator for two methods.
     * Priority order is;
     * sub-class < super-class.
     * static < non-static,
     * public < non-public,
     * string arg < no arg and
     *
     * @param a  first method
     * @param b  second method
     *
     * @return -1, 0 or 1 to represent higher priority. equals priority or lesser priority.
     */
    private static int compareMethods(Method a, Method b) {
        Class<?> aClass = a.getDeclaringClass();
        Class<?> bClass = b.getDeclaringClass();

        if (aClass != bClass) {
            if (bClass.isAssignableFrom(aClass)) {
                return -1;
            } else {
                return 1;
            }
        }

        int aMods = a.getModifiers();
        int bMods = b.getModifiers();
        boolean aIsStatic = Modifier.isStatic(aMods);
        boolean bIsStatic = Modifier.isStatic(bMods);

        if (aIsStatic && !bIsStatic) {
            return -1;
        } else if (!aIsStatic && bIsStatic) {
            return 1;
        }

        boolean aIsPublic = Modifier.isPublic(aMods);
        boolean bIsPublic = Modifier.isPublic(bMods);

        if (aIsPublic && !bIsPublic) {
            return -1;
        } else if (bIsPublic && !aIsPublic) {
            return 1;
        }

        int aCount = a.getParameterCount();
        int bCount = b.getParameterCount();

        if (bCount < aCount) {
            return -1;
        } else if (aCount < bCount) {
            return 1;
        }

        return 0;
    }

    /**
     * {@return priority main method if none found}
     *
     * @param mainClass main class
     *
     * @throws NoSuchMethodException when not preview and no method found
     */
    public static Method findMainMethod(Class<?> mainClass) throws NoSuchMethodException {
        boolean traditionalMain = !PreviewFeatures.isEnabled();
        if (traditionalMain) {
            return mainClass.getMethod("main", String[].class);
        }

        List<Method> mains = new ArrayList<>();
        gatherMains(mainClass, mainClass, mains);

        if (mains.isEmpty()) {
            throw new NoSuchMethodException("No main method found");
        }

        if (1 < mains.size()) {
            mains.sort(MainMethodFinder::compareMethods);
        }

        Method mainMethod = mains.get(0);

        return mainMethod;
    }
}
