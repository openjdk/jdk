/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.generator.bean.field;

import com.sun.tools.internal.xjc.generator.bean.ClassOutlineImpl;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.outline.FieldOutline;


/**
 * Abstract model of one field in a generated class.
 *
 * <p>
 * Responsible for "realizing" a Java property by actually generating
 * members(s) to store the property value and a set of methods
 * to manipulate them.
 *
 * <p>
 * Objects that implement this interface also encapsulates the
 * <b>internal</b> access to the field.
 *
 * <p>
 * For discussion of the model this interface is representing, see
 * the "field meta model" design document.
 *
 * REVISIT:
 *  refactor this to two interfaces that provide
 *  (1) internal access and (2) external access.
 *
 * @author
 *  Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public interface FieldRenderer {
    /**
     * Generates accesssors and fields for the given implementation
     * class, then return {@link FieldOutline} for accessing
     * the generated field.
     */
    public FieldOutline generate( ClassOutlineImpl context, CPropertyInfo prop);

//    //
//    // field renderers
//    //
//    public static final FieldRenderer DEFAULT
//        = new DefaultFieldRenderer();
//
//    public static final FieldRenderer ARRAY
//        = new GenericFieldRenderer(ArrayField.class);
//
//    public static final FieldRenderer REQUIRED_UNBOXED
//        = new GenericFieldRenderer(UnboxedField.class);
//
//    public static final FieldRenderer SINGLE
//        = new GenericFieldRenderer(SingleField.class);
//
//    public static final FieldRenderer SINGLE_PRIMITIVE_ACCESS
//        = new GenericFieldRenderer(SinglePrimitiveAccessField.class);
//
//    public static final FieldRenderer JAXB_DEFAULT
//        = new DefaultFieldRenderer();
}
