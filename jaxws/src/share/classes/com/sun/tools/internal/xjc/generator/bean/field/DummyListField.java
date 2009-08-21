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

import com.sun.codemodel.internal.JAnnotatable;
import java.util.ArrayList;
import java.util.List;

import com.sun.codemodel.internal.JBlock;
import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JExpression;
import com.sun.codemodel.internal.JMethod;
import com.sun.codemodel.internal.JVar;
import com.sun.tools.internal.xjc.generator.bean.ClassOutlineImpl;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.model.CReferencePropertyInfo;
import com.sun.xml.internal.bind.annotation.OverrideAnnotationOf;

/**
 * Realizes a property as an untyped {@link List}.
 *
 * <pre>
 * List getXXX();
 * </pre>
 *
 * <h2>Default value handling</h2>
 * <p>
 * Since unmarshaller just adds new values into the storage,
 * we can't fill the storage by default values at the time of
 * instanciation. (or oherwise values found in the document will
 * be appended to default values, where it should overwrite them.)
 * <p>
 * Therefore, when the object is created, the storage will be empty.
 * When the getXXX method is called, we'll check if the storage is
 * modified in anyway. If it is modified, it must mean that the values
 * are found in the document, so we just return it.
 *
 * Otherwise we will fill in default values and return it to the user.
 *
 * <p>
 * When a list has default values, its dirty flag is set to true.
 * Marshaller will check this and treat it appropriately.
 *
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class DummyListField extends AbstractListField {

    /**
     * A concrete class that imp    lements the List interface.
     * An instance of this class will be used to store data
     * for this field.
     */
    private final JClass coreList;


    /** List getFIELD() method. */
    private JMethod $get;

    /**
     * @param coreList
     *      A concrete class that implements the List interface.
     *      An instance of this class will be used to store data
     *      for this field.
     */
    protected DummyListField(ClassOutlineImpl context, CPropertyInfo prop, JClass coreList) {
        // the JAXB runtime picks ArrayList if the signature is List,
        // so don't do eager allocation if it's ArrayList.
        // otherwise we need to do eager allocation so that the collection type specified by the user
        // will be used.
        super(context, prop, !coreList.fullName().equals("java.util.ArrayList"));
        this.coreList = coreList.narrow(exposedType.boxify());
        generate();
    }

    /**
     * Annotate the field according to the recipes given as {@link CPropertyInfo}.
     */
    @Override
    protected void annotate( JAnnotatable field ) {
        super.annotate(field);

        if (prop instanceof CReferencePropertyInfo) {
            CReferencePropertyInfo pref = (CReferencePropertyInfo)prop;
            if (pref.isDummy()) {
                annotateDummy(field);
            }
        }

    }

    private void annotateDummy(JAnnotatable field) {
        field.annotate(OverrideAnnotationOf.class);
    }

    protected final JClass getCoreListType() {
        return coreList;
    }

    @Override
    public void generateAccessors() { }

    public Accessor create(JExpression targetObject) {
        return new Accessor(targetObject);
    }

    class Accessor extends AbstractListField.Accessor {
        protected Accessor( JExpression $target ) {
            super($target);
        }

        public void toRawValue(JBlock block, JVar $var) {
            // [RESULT]
            // $<var>.addAll(bean.getLIST());
            // list.toArray( array );
            block.assign($var,JExpr._new(codeModel.ref(ArrayList.class).narrow(exposedType.boxify())).arg(
                $target.invoke($get)
            ));
        }

        public void fromRawValue(JBlock block, String uniqueName, JExpression $var) {
            // [RESULT]
            // bean.getLIST().addAll($<var>);
            JVar $list = block.decl(listT,uniqueName+'l',$target.invoke($get));
            block.invoke($list,"addAll").arg($var);
        }
    }
}
