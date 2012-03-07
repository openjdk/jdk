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

import com.sun.codemodel.internal.JBlock;
import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JExpression;
import com.sun.codemodel.internal.JFieldVar;
import com.sun.codemodel.internal.JMod;
import com.sun.codemodel.internal.JPrimitiveType;
import com.sun.codemodel.internal.JType;
import com.sun.codemodel.internal.JVar;
import com.sun.tools.internal.xjc.generator.bean.ClassOutlineImpl;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.outline.FieldAccessor;

/**
 * Realizes a property as a "public static final" property on the interface.
 * This class can handle both boxed/unboxed types and both
 * single/colllection.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
final class ConstField extends AbstractField {
//    /**
//     * Number of items in this property, when
//     * {@link #isCollection}==true.
//     */
//    private final int count=1;

    /** Generated constant property on the interface. */
    private final JFieldVar $ref;

    ConstField( ClassOutlineImpl outline, CPropertyInfo prop ) {
        super(outline,prop);

        // we only support value constraints for a single-value property.
        assert !prop.isCollection();

        JPrimitiveType ptype = implType.boxify().getPrimitiveType();

        // generate the constant
        JExpression defaultValue = null;
        if(prop.defaultValue!=null)
            defaultValue = prop.defaultValue.compute(outline.parent());

        $ref = outline.ref.field(JMod.PUBLIC|JMod.STATIC|JMod.FINAL,
            ptype!=null?ptype:implType, prop.getName(true), defaultValue );
        $ref.javadoc().append(prop.javadoc);

        annotate($ref);
    }

    public JType getRawType() {
//        if( isCollection )      return getInfo().array();
        return exposedType;
    }


    public FieldAccessor create(JExpression target) {
        return new Accessor(target);
    }

    private class Accessor extends AbstractField.Accessor {

        Accessor( JExpression $target ) {
            super($target);
        }

        public void unsetValues( JBlock body ) {
            ;   // can't unset values
        }
        public JExpression hasSetValue() {
            return null;    // can't generate the isSet/unset methods
        }
        public void toRawValue(JBlock block, JVar $var) {
            // TODO: rethink abstraction. Those constant fields
            // don't have "access" to them.
            throw new UnsupportedOperationException();
        }

        public void fromRawValue(JBlock block, String uniqueName, JExpression $var) {
            throw new UnsupportedOperationException();
        }
    }
}
