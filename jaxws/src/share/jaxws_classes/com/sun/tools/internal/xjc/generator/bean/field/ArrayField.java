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

import com.sun.codemodel.internal.JAssignmentTarget;
import java.util.List;
import com.sun.codemodel.internal.JBlock;
import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JExpression;
import com.sun.codemodel.internal.JForLoop;
import com.sun.codemodel.internal.JMethod;
import com.sun.codemodel.internal.JMod;
import com.sun.codemodel.internal.JOp;
import com.sun.codemodel.internal.JType;
import com.sun.codemodel.internal.JVar;
import com.sun.tools.internal.xjc.generator.bean.ClassOutlineImpl;
import com.sun.tools.internal.xjc.generator.bean.MethodWriter;
import com.sun.tools.internal.xjc.model.CPropertyInfo;

/**
 * Realizes a property as an "indexed property"
 * as specified in the JAXB spec.
 *
 * <p>
 * We will generate the following set of methods:
 * <pre>
 * T[] getX();
 * T getX( int idx );
 * void setX(T[] values);
 * void setX( int idx, T value );
 * </pre>
 *
 * We still use List as our back storage.
 * This renderer also handles boxing/unboxing if
 * T is a boxed type.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
final class ArrayField extends AbstractListField {

    class Accessor extends AbstractListField.Accessor {
        protected Accessor( JExpression $target ) {
            super($target);
        }

        public void toRawValue(JBlock block, JVar $var) {
            block.assign($var,$target.invoke($getAll));
        }

        public void fromRawValue(JBlock block, String uniqueName, JExpression $var) {
            block.invoke($target,$setAll).arg($var);
        }

        @Override
        public JExpression hasSetValue() {
            return field.ne(JExpr._null()).cand(field.ref("length").gt(JExpr.lit(0)));
        }

    }

    private JMethod $setAll;

    private JMethod $getAll;

    ArrayField(ClassOutlineImpl context, CPropertyInfo prop) {
        super(context,prop,false);
        generateArray();
    }

    protected final void generateArray() {
        field = outline.implClass.field( JMod.PROTECTED, getCoreListType(), prop.getName(false) );
        annotate(field);

        // generate the rest of accessors
        generateAccessors();
    }

    public void generateAccessors() {

        MethodWriter writer = outline.createMethodWriter();
        Accessor acc = create(JExpr._this());
        JVar $idx,$value; JBlock body;

        // [RESULT] T[] getX() {
        //     if( <var>==null )    return new T[0];
        //     T[] retVal = new T[this._return.length] ;
        //     System.arraycopy(this._return, 0, "retVal", 0, this._return.length);
        //     return (retVal);
        // }
        $getAll = writer.declareMethod( exposedType.array(),"get"+prop.getName(true));
        writer.javadoc().append(prop.javadoc);
        body = $getAll.body();

        body._if( acc.ref(true).eq(JExpr._null()) )._then()
            ._return(JExpr.newArray(exposedType,0));
        JVar var = body.decl(exposedType.array(), "retVal", JExpr.newArray(implType,acc.ref(true).ref("length")));
        body.add(codeModel.ref(System.class).staticInvoke("arraycopy")
                        .arg(acc.ref(true)).arg(JExpr.lit(0))
                        .arg(var)
                        .arg(JExpr.lit(0)).arg(acc.ref(true).ref("length")));
        body._return(JExpr.direct("retVal"));

        List<Object> returnTypes = listPossibleTypes(prop);
        writer.javadoc().addReturn().append("array of\n").append(returnTypes);

        // [RESULT]
        // ET getX(int idx) {
        //     if( <var>==null )    throw new IndexOutOfBoundsException();
        //     return unbox(<var>.get(idx));
        // }
        JMethod $get = writer.declareMethod(exposedType,"get"+prop.getName(true));
        $idx = writer.addParameter(codeModel.INT,"idx");

        $get.body()._if(acc.ref(true).eq(JExpr._null()))._then()
            ._throw(JExpr._new(codeModel.ref(IndexOutOfBoundsException.class)));

        writer.javadoc().append(prop.javadoc);
        $get.body()._return(acc.ref(true).component($idx));

        writer.javadoc().addReturn().append("one of\n").append(returnTypes);

        // [RESULT] int getXLength() {
        //     if( <var>==null )    throw new IndexOutOfBoundsException();
        //     return <ref>.length;
        // }
        JMethod $getLength = writer.declareMethod(codeModel.INT,"get"+prop.getName(true)+"Length");
        $getLength.body()._if(acc.ref(true).eq(JExpr._null()))._then()
                ._return(JExpr.lit(0));
        $getLength.body()._return(acc.ref(true).ref("length"));

        // [RESULT] void setX(ET[] values) {
        //     int len = values.length;
        //     for( int i=0; i<len; i++ )
        //         <ref>[i] = values[i];
        // }
        $setAll = writer.declareMethod(
            codeModel.VOID,
            "set"+prop.getName(true));

        writer.javadoc().append(prop.javadoc);
        $value = writer.addParameter(exposedType.array(),"values");
        JVar $len = $setAll.body().decl(codeModel.INT,"len", $value.ref("length"));

        $setAll.body().assign(
                (JAssignmentTarget) acc.ref(true),
                castToImplTypeArray(JExpr.newArray(
                    codeModel.ref(exposedType.erasure().fullName()),
                    $len)));

        JForLoop _for = $setAll.body()._for();
        JVar $i = _for.init( codeModel.INT, "i", JExpr.lit(0) );
        _for.test( JOp.lt($i,$len) );
        _for.update( $i.incr() );
        _for.body().assign(acc.ref(true).component($i), castToImplType(acc.box($value.component($i))));

        writer.javadoc().addParam($value)
            .append("allowed objects are\n")
            .append(returnTypes);

        // [RESULT] ET setX(int idx, ET value)
        // <ref>[idx] = value
        JMethod $set = writer.declareMethod(
            exposedType,
            "set"+prop.getName(true));
        $idx = writer.addParameter( codeModel.INT, "idx" );
        $value = writer.addParameter( exposedType, "value" );

        writer.javadoc().append(prop.javadoc);

        body = $set.body();
        body._return( JExpr.assign(acc.ref(true).component($idx),
                                   castToImplType(acc.box($value))));

        writer.javadoc().addParam($value)
            .append("allowed object is\n")
            .append(returnTypes);

    }

    @Override
    public JType getRawType() {
        return exposedType.array();
    }

    protected JClass getCoreListType() {
        return exposedType.array();
    }

    public Accessor create(JExpression targetObject) {
        return new Accessor(targetObject);
    }

    /**
     * Case from {@link #exposedType} to array of {@link #implType} .
     */
    protected final JExpression castToImplTypeArray( JExpression exp ) {
        return JExpr.cast(implType.array(), exp);
    }

}
