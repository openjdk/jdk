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

public class MainMethodFinder {
    /**
     * {@return true if the method meets the requirements of a main method}
     *
     * @param method    method to test
     * @param argCount  number of expected arguments (0 or 1)
     */
    private static boolean isMainMethod(Method method, int argCount) {
        return !Modifier.isPrivate(method.getModifiers()) &&
                method.getParameterCount() == argCount &&
                (argCount == 0 || method.getParameterTypes()[0] == String[].class) &&
                method.getReturnType() == void.class &&
                "main".equals(method.getName());
    }

    /**
     * Search up the superclass hierarchy for a qualified main method.
     *
     * @param mainClass    Main class to start search from.
     * @param noArgs       true if no argument otherwise expects one String[] argument
     *
     * @return main method meeting requirements
     */
    private static Method findMainMethod(Class<?> mainClass, boolean noArgs) {
        int argCount = noArgs ? 0 : 1;
        for ( ; mainClass != null && mainClass != Object.class; mainClass = mainClass.getSuperclass()) {
            for (Method method : mainClass.getDeclaredMethods()) {
                if (isMainMethod(method, argCount)) {
                    return method;
                }
            }
        }

        return null;
    }

    /**
     * {@return qualified main method}
     *
     * @param mainClass main class
     *
     * @throws NoSuchMethodException when no main method found
     */
    public static Method findMainMethod(Class<?> mainClass) throws NoSuchMethodException {
        boolean useTraditionMain = !PreviewFeatures.isEnabled();
        boolean isTraditionalMain = false;

        Method mainMethod = findMainMethod(mainClass, false);

        if (mainMethod == null) {
            mainMethod = findMainMethod(mainClass, true);
        } else {
            int mods = mainMethod.getModifiers();
            isTraditionalMain = Modifier.isStatic(mods) && Modifier.isPublic(mods);
        }

        if (mainMethod == null || useTraditionMain && !isTraditionalMain) {
            throw new NoSuchMethodException(mainClass.getName() + ".main(String[])");
        }

        return mainMethod;
    }
}
