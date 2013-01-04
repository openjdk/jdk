/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.objects.annotations;

/**
 * Attributes for JavaScript properties. The negative logic "NOT_xxx" is because the
 * common case is to be writable, enumerable and configurable
 */

public interface Attribute {
    /** flag for non writable objects */
    public static final int NOT_WRITABLE     = jdk.nashorn.internal.runtime.Property.NOT_WRITABLE;

    /** flag for non enumerable objects */
    public static final int NOT_ENUMERABLE   = jdk.nashorn.internal.runtime.Property.NOT_ENUMERABLE;

    /** flag for non configurable objects */
    public static final int NOT_CONFIGURABLE = jdk.nashorn.internal.runtime.Property.NOT_CONFIGURABLE;

    /** read-only, non-configurable property */
    public static final int CONSTANT = NOT_WRITABLE | NOT_CONFIGURABLE;

    /** non-enumerable, read-only, non-configurable property */
    public static final int NON_ENUMERABLE_CONSTANT = NOT_ENUMERABLE | CONSTANT;

    /** by default properties are writable, enumerable and configurable */
    public static final int DEFAULT_ATTRIBUTES = 0;
}
