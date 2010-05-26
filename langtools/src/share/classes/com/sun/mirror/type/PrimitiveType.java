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

package com.sun.mirror.type;


/**
 * Represents a primitive type.  These include
 * <tt>boolean</tt>, <tt>byte</tt>, <tt>short</tt>, <tt>int</tt>,
 * <tt>long</tt>, <tt>char</tt>, <tt>float</tt>, and <tt>double</tt>.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is {@link
 * javax.lang.model.type.PrimitiveType}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface PrimitiveType extends TypeMirror {

    /**
     * Returns the kind of primitive type that this object represents.
     *
     * @return the kind of primitive type that this object represents
     */
    Kind getKind();

    /**
     * An enumeration of the different kinds of primitive types.
     *
     * @deprecated All components of this API have been superseded by
     * the standardized annotation processing API.  The replacement
     * for the functionality of this enum is {@link
     * javax.lang.model.type.TypeKind}.
     */
    @Deprecated
    enum Kind {
        /** The primitive type <tt>boolean</tt> */      BOOLEAN,
        /** The primitive type <tt>byte</tt> */         BYTE,
        /** The primitive type <tt>short</tt> */        SHORT,
        /** The primitive type <tt>int</tt> */          INT,
        /** The primitive type <tt>long</tt> */         LONG,
        /** The primitive type <tt>char</tt> */         CHAR,
        /** The primitive type <tt>float</tt> */        FLOAT,
        /** The primitive type <tt>double</tt> */       DOUBLE
    }
}
