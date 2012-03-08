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

import java.util.ArrayList;

import com.sun.tools.internal.xjc.generator.bean.ClassOutlineImpl;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.model.CReferencePropertyInfo;
import com.sun.tools.internal.xjc.outline.FieldOutline;
import java.io.Serializable;

/**
 * Default implementation of the FieldRendererFactory
 * that faithfully implements the semantics demanded by the JAXB spec.
 *
 * <p>
 * This class is just a facade --- it just determines which
 * {@link FieldRenderer} to use and just delegate the work.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
final class DefaultFieldRenderer implements FieldRenderer {

    private final FieldRendererFactory frf;

    /**
     * Use {@link FieldRendererFactory#getDefault()}.
     */
    DefaultFieldRenderer(FieldRendererFactory frf) {
        this.frf = frf;
    }

    public DefaultFieldRenderer(FieldRendererFactory frf, FieldRenderer defaultCollectionFieldRenderer ) {
        this.frf = frf;
        this.defaultCollectionFieldRenderer = defaultCollectionFieldRenderer;
    }

    private FieldRenderer defaultCollectionFieldRenderer;


    public FieldOutline generate(ClassOutlineImpl outline, CPropertyInfo prop) {
        return decideRenderer(outline,prop).generate(outline,prop);
    }

    private FieldRenderer decideRenderer(ClassOutlineImpl outline, CPropertyInfo prop) {

        if (prop instanceof CReferencePropertyInfo) {
            CReferencePropertyInfo p = (CReferencePropertyInfo)prop;
            if (p.isDummy()) {
                return frf.getDummyList(outline.parent().getCodeModel().ref(ArrayList.class));
            }
            if (p.isContent() && (p.isMixedExtendedCust())) {
                return frf.getContentList(outline.parent().getCodeModel().ref(ArrayList.class).narrow(Serializable.class));
            }
        }

        if(!prop.isCollection()) {
            // non-collection field

            // TODO: check for bidning info for optionalPrimitiveType=boxed or
            // noHasMethod=false and noDeletedMethod=false
            if(prop.isUnboxable())
                // this one uses a primitive type as much as possible
                return frf.getRequiredUnboxed();
            else
                // otherwise use the default non-collection field
                return frf.getSingle();
        }

        if( defaultCollectionFieldRenderer==null ) {
            return frf.getList(outline.parent().getCodeModel().ref(ArrayList.class));
        }

        // this field is a collection field.
        // use untyped list as the default. This is consistent
        // to the JAXB spec.
        return defaultCollectionFieldRenderer;
    }
}
