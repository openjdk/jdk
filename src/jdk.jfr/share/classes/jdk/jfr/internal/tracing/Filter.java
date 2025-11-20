/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.tracing;

import jdk.internal.module.Checks;

/**
 * Class that represents the filter a user can specify for the MethodTrace and
 * MethodTiming event.
 */
public record Filter(String className, String methodName, String annotationName, Modification modification) {

    public static boolean isValid(String filter) {
        return of(filter, Modification.NONE) != null;
    }

    static Filter of(String filter, Modification modification) {
        if (filter.startsWith("@")) {
            return ofAnnotation(filter, modification);
        }
        if (filter.contains("::")) {
            return ofMethod(filter, modification);
        }
        return ofClass(filter, modification);
    }

    private static Filter ofAnnotation(String filter, Modification modification) {
        String annotation = filter.substring(1);
        if (Checks.isClassName(annotation)) {
            return new Filter(null, null, annotation, modification);
        }
        return null;
    }

    private static Filter ofMethod(String filter, Modification modification) {
        int index = filter.indexOf("::");
        String classPart = filter.substring(0, index);
        String methodPart = filter.substring(index + 2);
        if (methodPart.isEmpty()) {
            // Don't allow "foo.Bar::". User should specify "foo.Bar".
            return null;
        }

        if (isMethod(methodPart)) {
            // Method name only, i.e. "::baz"
            if (classPart.isEmpty()) {
                return new Filter(null, methodPart, null, modification);
            }
            // Fully qualified method name, i.e. "foo.Bar::baz"
            if (isValidClassName(classPart)) {
                return new Filter(classPart, methodPart, null, modification);
            }
        }
        return null;
    }

    private static boolean isMethod(String methodName) {
        if (methodName.equals("<clinit>") || methodName.equals("<init>")) {
            return true;
        }
        return Checks.isJavaIdentifier(methodName);
    }

    private static Filter ofClass(String filter, Modification modification) {
        if (isValidClassName(filter)) {
            return new Filter(filter, null, null, modification);
        }
        return null;
    }

    private static boolean isValidClassName(String text) {
        return Checks.isClassName(text);
    }
}
