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

package com.sun.tools.internal.xjc.model;

import javax.xml.namespace.QName;

import com.sun.codemodel.internal.JClass;
import com.sun.tools.internal.xjc.model.nav.NClass;
import com.sun.tools.internal.xjc.model.nav.NType;
import com.sun.tools.internal.xjc.outline.Aspect;
import com.sun.tools.internal.xjc.outline.Outline;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIClass;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIEnum;
import com.sun.xml.internal.xsom.XSComponent;

/**
 * Refernece to an existing class.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CClassRef extends AbstractCElement implements NClass, CClass {

    private final String fullyQualifiedClassName;

    /**
     *
     * @param decl
     *      The {@link BIClass} declaration that has {@link BIClass#getExistingClassRef()}
     */
    public CClassRef(Model model, XSComponent source, BIClass decl, CCustomizations customizations) {
        super(model, source, decl.getLocation(), customizations);
        fullyQualifiedClassName = decl.getExistingClassRef();
        assert fullyQualifiedClassName!=null;
    }

    /**
     *
     * @param decl
     *      The {@link BIClass} declaration that has {@link BIEnum#ref}
     */
    public CClassRef(Model model, XSComponent source, BIEnum decl, CCustomizations customizations) {
        super(model, source, decl.getLocation(), customizations);
        fullyQualifiedClassName = decl.ref;
        assert fullyQualifiedClassName!=null;
    }

    public void setAbstract() {
        // assume that the referenced class is marked as abstract to begin with.
    }

    public boolean isAbstract() {
        // no way to find out for sure
        return false;
    }

    public NType getType() {
        return this;
    }

    /**
     * Cached for both performance and single identity.
     */
    private JClass clazz;

    public JClass toType(Outline o, Aspect aspect) {
        if(clazz==null)
            clazz = o.getCodeModel().ref(fullyQualifiedClassName);
        return clazz;
    }

    public String fullName() {
        return fullyQualifiedClassName;
    }

    public QName getTypeName() {
        return null;
    }

    /**
     * Guaranteed to return this.
     */
    @Deprecated
    public CNonElement getInfo() {
        return this;
    }

// are these going to bite us?
//    we can compute some of them, but not all.

    public CElement getSubstitutionHead() {
        return null;
    }

    public CClassInfo getScope() {
        return null;
    }

    public QName getElementName() {
        return null;
    }

    public boolean isBoxedType() {
        return false;
    }

    public boolean isSimpleType() {
        return false;
    }


}
