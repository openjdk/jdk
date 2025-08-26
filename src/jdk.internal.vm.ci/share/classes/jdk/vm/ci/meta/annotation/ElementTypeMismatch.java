/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.meta.annotation;

import java.lang.annotation.AnnotationTypeMismatchException;

/**
 * Represents a deferred {@link AnnotationTypeMismatchException} for an element
 * within an {@link AnnotationValue}.
 * <p>
 * Similar to {@code AnnotationTypeMismatchExceptionProxy}.
 */
public final class ElementTypeMismatch extends ErrorElement {
    private final String foundType;

    /**
     * @param foundType see {@link AnnotationTypeMismatchException#foundType()}
     */
    public ElementTypeMismatch(String foundType) {
        this.foundType = foundType;
    }

    @Override
    public String toString() {
        // Same value as AnnotationTypeMismatchExceptionProxy.toString()
        return "/* Warning type mismatch! \"" + foundType + "\" */";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ElementTypeMismatch that) {
            return this.foundType.equals(that.foundType);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return foundType.hashCode();
    }

    /**
     * @see AnnotationTypeMismatchException#foundType()
     */
    public String getFoundType() {
        return foundType;
    }
}
