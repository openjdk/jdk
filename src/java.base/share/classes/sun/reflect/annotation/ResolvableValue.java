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

package sun.reflect.annotation;

/**
 * Denotes a parsed annotation element value that is not fully resolved to the
 * value returned by the annotation interface method for the element.
 * This is used for example to defer resolving enum constants which is important
 * in contexts where class initialization of the enum types should not be
 * triggered by annotation parsing.
 */
public interface ResolvableValue {

    /**
     * Gets resolved value, performing resolution first if necessary.
     */
    @SuppressWarnings("unchecked")
    Object get();

    /**
     * Determines if this value has been resolved.
     */
    boolean isResolved();

    /**
     * Gets the resolved value of {@code memberValue}, performing
     * resolution first if necessary.
     */
    static Object resolved(Object memberValue) {
        if (memberValue instanceof ResolvableValue rv) {
            return rv.get();
        }
        return memberValue;
    }
}
