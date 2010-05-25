/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.mirror.declaration;


/**
 * Represents a modifier on the declaration of a program element such
 * as a class, method, or field.
 *
 * <p> Not all modifiers are applicable to all kinds of declarations.
 * When two or more modifiers appear in the source code of a declaration,
 * then it is customary, though not required, that they appear in the same
 * order as the constants listed in the detail section below.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this enum is {@link javax.lang.model.element.Modifier}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public enum Modifier {

    // See JLS2 sections 8.1.1, 8.3.1, 8.4.3, 8.8.3, and 9.1.1.
    // java.lang.reflect.Modifier includes INTERFACE, but that's a VMism.

    /** The modifier <tt>public</tt> */         PUBLIC,
    /** The modifier <tt>protected</tt> */      PROTECTED,
    /** The modifier <tt>private</tt> */        PRIVATE,
    /** The modifier <tt>abstract</tt> */       ABSTRACT,
    /** The modifier <tt>static</tt> */         STATIC,
    /** The modifier <tt>final</tt> */          FINAL,
    /** The modifier <tt>transient</tt> */      TRANSIENT,
    /** The modifier <tt>volatile</tt> */       VOLATILE,
    /** The modifier <tt>synchronized</tt> */   SYNCHRONIZED,
    /** The modifier <tt>native</tt> */         NATIVE,
    /** The modifier <tt>strictfp</tt> */       STRICTFP;


    private String lowercase = null;    // modifier name in lowercase

    /**
     * Returns this modifier's name in lowercase.
     */
    public String toString() {
        if (lowercase == null) {
           lowercase = name().toLowerCase(java.util.Locale.US);
        }
        return lowercase;
    }
}
