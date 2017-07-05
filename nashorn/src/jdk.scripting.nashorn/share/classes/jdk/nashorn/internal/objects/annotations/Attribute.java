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
    /** Flag for non-writable properties */
    public static final int NOT_WRITABLE     = jdk.nashorn.internal.runtime.Property.NOT_WRITABLE;

    /** Flag for non-enumerable properties */
    public static final int NOT_ENUMERABLE   = jdk.nashorn.internal.runtime.Property.NOT_ENUMERABLE;

    /** Flag for non-configurable properties */
    public static final int NOT_CONFIGURABLE = jdk.nashorn.internal.runtime.Property.NOT_CONFIGURABLE;

    /**
     * Flag for accessor (getter/setter) properties as opposed to data properties.
     *
     * <p>This allows nasgen-created properties to behave like user-accessors. it should only be used for
     * properties that are explicitly specified as accessor properties in the ECMAScript specification
     * such as Map.prototype.size in ES6, not value properties that happen to be implemented by getter/setter
     * such as the "length" properties of String or Array objects.</p>
     */
    public static final int IS_ACCESSOR = jdk.nashorn.internal.runtime.Property.IS_ACCESSOR_PROPERTY;

    /** Read-only, non-configurable property */
    public static final int CONSTANT = NOT_WRITABLE | NOT_CONFIGURABLE;

    /** Non-enumerable, read-only, non-configurable property */
    public static final int NON_ENUMERABLE_CONSTANT = NOT_ENUMERABLE | CONSTANT;

    /** By default properties are writable, enumerable and configurable */
    public static final int DEFAULT_ATTRIBUTES = 0;
}
