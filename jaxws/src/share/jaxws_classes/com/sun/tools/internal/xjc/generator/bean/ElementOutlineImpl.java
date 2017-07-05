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

package com.sun.tools.internal.xjc.generator.bean;


import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JExpression;
import com.sun.codemodel.internal.JFieldVar;
import com.sun.codemodel.internal.JInvocation;
import com.sun.codemodel.internal.JMethod;
import com.sun.codemodel.internal.JMod;
import com.sun.codemodel.internal.JType;
import com.sun.tools.internal.xjc.model.CElementInfo;
import com.sun.tools.internal.xjc.outline.Aspect;
import com.sun.tools.internal.xjc.outline.ElementOutline;

/**
 * {@link ElementOutline} implementation.
 *
 * @author Kohsuke Kawaguchi
 */
final class ElementOutlineImpl extends ElementOutline {
    private final BeanGenerator parent;

    public BeanGenerator parent() {
        return parent;
    }

    /*package*/ ElementOutlineImpl(BeanGenerator parent, CElementInfo ei) {
        super(ei,
              parent.getClassFactory().createClass(
                      parent.getContainer( ei.parent, Aspect.EXPOSED ), ei.shortName(), ei.getLocator() ));
        this.parent = parent;
        parent.elements.put(ei,this);

        JCodeModel cm = parent.getCodeModel();

        implClass._extends(
            cm.ref(JAXBElement.class).narrow(
                target.getContentInMemoryType().toType(parent,Aspect.EXPOSED).boxify()));

        if(ei.hasClass()) {
            JType implType = ei.getContentInMemoryType().toType(parent,Aspect.IMPLEMENTATION);
            JExpression declaredType = JExpr.cast(cm.ref(Class.class),implType.boxify().dotclass()); // why do we have to cast?
            JClass scope=null;
            if(ei.getScope()!=null)
                scope = parent.getClazz(ei.getScope()).implRef;
            JExpression scopeClass = scope==null?JExpr._null():scope.dotclass();
            JFieldVar valField = implClass.field(JMod.PROTECTED|JMod.FINAL|JMod.STATIC,QName.class,"NAME",createQName(cm,ei.getElementName()));

            // take this opportunity to generate a constructor in the element class
            JMethod cons = implClass.constructor(JMod.PUBLIC);
            cons.body().invoke("super")
                .arg(valField)
                .arg(declaredType)
                .arg(scopeClass)
                .arg(cons.param(implType,"value"));

            // generate no-arg constructor in the element class (bug #391; section 5.6.2 in JAXB spec 2.1)
            JMethod noArgCons = implClass.constructor(JMod.PUBLIC);
            noArgCons.body().invoke("super")
                .arg(valField)
                .arg(declaredType)
                .arg(scopeClass)
                .arg(JExpr._null());

        }
    }

    /**
     * Generates an expression that evaluates to "new QName(...)"
     */
    private JInvocation createQName(JCodeModel codeModel,QName name) {
        return JExpr._new(codeModel.ref(QName.class)).arg(name.getNamespaceURI()).arg(name.getLocalPart());
    }
}
