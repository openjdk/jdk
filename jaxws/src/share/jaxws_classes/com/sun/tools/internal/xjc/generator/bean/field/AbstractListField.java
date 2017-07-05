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

import java.util.List;

import com.sun.codemodel.internal.JBlock;
import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JExpression;
import com.sun.codemodel.internal.JFieldRef;
import com.sun.codemodel.internal.JFieldVar;
import com.sun.codemodel.internal.JMethod;
import com.sun.codemodel.internal.JMod;
import com.sun.codemodel.internal.JOp;
import com.sun.codemodel.internal.JPrimitiveType;
import com.sun.codemodel.internal.JType;
import com.sun.tools.internal.xjc.generator.bean.ClassOutlineImpl;
import com.sun.tools.internal.xjc.model.CPropertyInfo;

/**
 * Common code for property renderer that generates a List as
 * its underlying data structure.
 *
 * <p>
 * For performance reasons, the actual list object used to store
 * data is lazily created.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
abstract class AbstractListField extends AbstractField {
    /** The field that stores the list. */
    protected JFieldVar field;

    /**
     * a method that lazily initializes a List.
     * Lazily created.
     *
     * [RESULT]
     * List _getFoo() {
     *   if(field==null)
     *     field = create new list;
     *   return field;
     * }
     */
    private JMethod internalGetter;

    /**
     * If this collection property is a collection of a primitive type,
     * this variable refers to that primitive type.
     * Otherwise null.
     */
    protected final JPrimitiveType primitiveType;

    protected final JClass listT = codeModel.ref(List.class).narrow(exposedType.boxify());

    /**
     * True to create a new instance of List eagerly in the constructor.
     * False otherwise.
     *
     * <p>
     * Setting it to true makes the generated code slower (as more list instances need to be
     * allocated), but it works correctly if the user specifies the custom type of a list.
     */
    private final boolean eagerInstanciation;

    /**
     * Call {@link #generate()} method right after this.
     */
    protected AbstractListField(ClassOutlineImpl outline, CPropertyInfo prop, boolean eagerInstanciation) {
        super(outline,prop);
        this.eagerInstanciation = eagerInstanciation;

        if( implType instanceof JPrimitiveType ) {
            // primitive types don't have this tricky distinction
            assert implType==exposedType;
            primitiveType = (JPrimitiveType)implType;
        } else
            primitiveType = null;
    }

    protected final void generate() {

        // for the collectionType customization to take effect, the field needs to be strongly typed,
        // not just List<Foo>.
        field = outline.implClass.field( JMod.PROTECTED, listT, prop.getName(false) );
        if(eagerInstanciation)
            field.init(newCoreList());

        annotate(field);

        // generate the rest of accessors
        generateAccessors();
    }

    private void generateInternalGetter() {
        internalGetter = outline.implClass.method(JMod.PROTECTED,listT,"_get"+prop.getName(true));
        if(!eagerInstanciation) {
            // if eagerly instanciated, the field can't be null
            fixNullRef(internalGetter.body());
        }
        internalGetter.body()._return(field);
    }

    /**
     * Generates statement(s) so that the successive {@link Accessor#ref(boolean)} with
     * true will always return a non-null list.
     *
     * This is useful to avoid generating redundant internal getter.
     */
    protected final void fixNullRef(JBlock block) {
        block._if(field.eq(JExpr._null()))._then()
            .assign(field,newCoreList());
    }

    public JType getRawType() {
        return codeModel.ref(List.class).narrow(exposedType.boxify());
    }

    private JExpression newCoreList() {
        return JExpr._new(getCoreListType());
    }

    /**
     * Concrete class that implements the List interface.
     * Used as the actual data storage.
     */
    protected abstract JClass getCoreListType();


    /** Generates accessor methods. */
    protected abstract void generateAccessors();



    /**
     *
     *
     * @author
     *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
     */
    protected abstract class Accessor extends AbstractField.Accessor {

        /**
         * Reference to the {@link AbstractListField#field}
         * of the target object.
         */
        protected final JFieldRef field;

        protected Accessor( JExpression $target ) {
            super($target);
            field = $target.ref(AbstractListField.this.field);
        }


        protected final JExpression unbox( JExpression exp ) {
            if(primitiveType==null) return exp;
            else                    return primitiveType.unwrap(exp);
        }
        protected final JExpression box( JExpression exp ) {
            if(primitiveType==null) return exp;
            else                    return primitiveType.wrap(exp);
        }

        /**
         * Returns a reference to the List field that stores the data.
         * <p>
         * Using this method hides the fact that the list is lazily
         * created.
         *
         * @param canBeNull
         *      if true, the returned expression may be null (this is
         *      when the list is still not constructed.) This could be
         *      useful when the caller can deal with null more efficiently.
         *      When the list is null, it should be treated as if the list
         *      is empty.
         *
         *      if false, the returned expression will never be null.
         *      This is the behavior users would see.
         */
        protected final JExpression ref(boolean canBeNull) {
            if(canBeNull)
                return field;
            if(internalGetter==null)
                generateInternalGetter();
            return $target.invoke(internalGetter);
        }

        public JExpression count() {
            return JOp.cond( field.eq(JExpr._null()), JExpr.lit(0), field.invoke("size") );
        }

        public void unsetValues( JBlock body ) {
            body.assign(field,JExpr._null());
        }
        public JExpression hasSetValue() {
            return field.ne(JExpr._null()).cand(field.invoke("isEmpty").not());
        }
    }

}
