/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

/**
 * A collection of static methods that return specific method objects of interest.
 */
public class MethodFinder {

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private MethodFinder() {
        throw new AssertionError("private constructor");
    }

    /**
     * Return the first method that meets the requirements of an application main method
     * {@jls 12.1.4}. The method must:
     * <ul>
     * <li>be declared in this class's hierarchy</li>
     * <li>have the name "main"</li>
     * <li>have a single argument of type {@code String[]}, {@code String...} or no argument</li>
     * <li>have the return type of void</li>
     * <li>be public, protected or package private</li>
     *</ul>
     *
     * The method returned would be used by a launcher to initiate the execution of an
     * application.
     *
     * Searching continues until a main method is found or the search is exhausted. The
     * primary search occurs in two phases, once for a main method with a {@code
     * String[]} or {@code String...} argument and failing that, once for a main method
     * with a no arguments. The search itself uses recursion to first look at methods
     * in this class, then default methods in this class's interface hierarchy and
     * then repeating these steps with the class's super class.
     *
     * @apiNote The method returned may be declared in this class, a super class
     * or as a default method of an interface that the class or super class
     * implements.
     * <p>It is not possible to declare a static main method and instance main
     * method with the same signature in the same class. {@jls 8.4.2} states that
     * "It is a compile-time error to declare two methods with override-equivalent
     * signatures in a class."
     * <p>{@link SecurityException SecurityExceptions} can halt
     * the search. In this case, a null is returned.
     *
     * @return the main method if a method found or null if no method is found
     *
     * @jls 8.2 Class Members
     * @jls 8.4 Method Declarations
     * @jls 8.4.2 Method Signature
     * @jls 12.1.4 Invoke a main method
     */
    public static Method findMainMethod(Class<?> cls) {
        Method mainMethod = JLA.findMethod(cls, true, "main", String[].class);

        if (mainMethod == null) {
            //if not public method, try to lookup a non-public one
            mainMethod = JLA.findMethod(cls, false, "main", String[].class);
        }

        if (mainMethod == null || !isValidMainMethod(mainMethod)) {
            mainMethod = JLA.findMethod(cls, false, "main");
        }

        if (mainMethod == null || !isValidMainMethod(mainMethod)) {
            return null;
        }

        return mainMethod;
    }

    private static boolean isValidMainMethod(Method mainMethodCandidate) {
        return mainMethodCandidate.getReturnType() == void.class &&
               !Modifier.isPrivate(mainMethodCandidate.getModifiers());

    }

}
