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
package com.sun.xml.internal.xsom.impl;

import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSIdentityConstraint;
import com.sun.xml.internal.xsom.XSXPath;
import com.sun.xml.internal.xsom.impl.parser.SchemaDocumentImpl;
import com.sun.xml.internal.xsom.visitor.XSFunction;
import com.sun.xml.internal.xsom.visitor.XSVisitor;
import org.xml.sax.Locator;

import java.util.Collections;
import java.util.List;

/**
 * {@link XSIdentityConstraint} implementation.
 *
 * @author Kohsuke Kawaguchi
 */
public class IdentityConstraintImpl extends ComponentImpl implements XSIdentityConstraint, Ref.IdentityConstraint {

    private XSElementDecl parent;
    private final short category;
    private final String name;
    private final XSXPath selector;
    private final List<XSXPath> fields;
    private final Ref.IdentityConstraint refer;

    public IdentityConstraintImpl(SchemaDocumentImpl _owner, AnnotationImpl _annon, Locator _loc,
        ForeignAttributesImpl fa, short category, String name, XPathImpl selector,
        List<XPathImpl> fields, Ref.IdentityConstraint refer) {

        super(_owner, _annon, _loc, fa);
        this.category = category;
        this.name = name;
        this.selector = selector;
        selector.setParent(this);
        this.fields = Collections.unmodifiableList((List<? extends XSXPath>)fields);
        for( XPathImpl xp : fields )
            xp.setParent(this);
        this.refer = refer;
    }


    public void visit(XSVisitor visitor) {
        visitor.identityConstraint(this);
    }

    public <T> T apply(XSFunction<T> function) {
        return function.identityConstraint(this);
    }

    public void setParent(ElementDecl parent) {
        this.parent = parent;
        parent.getOwnerSchema().addIdentityConstraint(this);
    }

    public XSElementDecl getParent() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public String getTargetNamespace() {
        return getParent().getTargetNamespace();
    }

    public short getCategory() {
        return category;
    }

    public XSXPath getSelector() {
        return selector;
    }

    public List<XSXPath> getFields() {
        return fields;
    }

    public XSIdentityConstraint getReferencedKey() {
        if(category==KEYREF)
            return refer.get();
        else
            throw new IllegalStateException("not a keyref");
    }

    public XSIdentityConstraint get() {
        return this;
    }
}
