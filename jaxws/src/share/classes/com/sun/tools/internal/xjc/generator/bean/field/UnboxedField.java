/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.tools.internal.xjc.generator.bean.field;

import com.sun.codemodel.internal.JBlock;
import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JExpression;
import com.sun.codemodel.internal.JMethod;
import com.sun.codemodel.internal.JPrimitiveType;
import com.sun.codemodel.internal.JType;
import com.sun.codemodel.internal.JVar;
import com.sun.tools.internal.xjc.generator.bean.ClassOutlineImpl;
import com.sun.tools.internal.xjc.generator.bean.MethodWriter;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.outline.Aspect;
import com.sun.tools.internal.xjc.outline.FieldAccessor;
import com.sun.xml.internal.bind.api.impl.NameConverter;

/**
 * A required primitive property.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class UnboxedField extends AbstractFieldWithVar {

    /**
     * The primitive version of {@link #implType} and {@link #exposedType}.
     */
    private final JPrimitiveType ptype;


    protected UnboxedField( ClassOutlineImpl outline, CPropertyInfo prop ) {
        super(outline,prop);
        // primitive types don't have this distintion
        assert implType==exposedType;

        ptype = (JPrimitiveType) implType;
        assert ptype!=null;

        createField();

        // apparently a required attribute can be still defaulted.
        // so this assertion is incorrect.
        // assert prop.defaultValue==null;

        MethodWriter writer = outline.createMethodWriter();
        NameConverter nc = outline.parent().getModel().getNameConverter();

        JBlock body;

        // [RESULT]
        // Type getXXX() {
        //     return value;
        // }
        JMethod $get = writer.declareMethod( ptype, getGetterMethod() );
        String javadoc = prop.javadoc;
        if(javadoc.length()==0)
            javadoc = Messages.DEFAULT_GETTER_JAVADOC.format(nc.toVariableName(prop.getName(true)));
        writer.javadoc().append(javadoc);

        $get.body()._return(ref());


        // [RESULT]
        // void setXXX( Type value ) {
        //     this.value = value;
        // }
        JMethod $set = writer.declareMethod( codeModel.VOID, "set"+prop.getName(true) );
        JVar $value = writer.addParameter( ptype, "value" );
        body = $set.body();
        body.assign(JExpr._this().ref(ref()),$value);
        // setter always get the default javadoc. See issue #381
        writer.javadoc().append(Messages.DEFAULT_SETTER_JAVADOC.format(nc.toVariableName(prop.getName(true))));

    }

    protected JType getType(Aspect aspect) {
        return super.getType(aspect).boxify().getPrimitiveType();
    }

    protected JType getFieldType() {
        return ptype;
    }

    public FieldAccessor create(JExpression targetObject) {
        return new Accessor(targetObject) {

            public void unsetValues( JBlock body ) {
                // you can't unset a value
            }

            public JExpression hasSetValue() {
                return JExpr.TRUE;
            }
        };
    }
}
