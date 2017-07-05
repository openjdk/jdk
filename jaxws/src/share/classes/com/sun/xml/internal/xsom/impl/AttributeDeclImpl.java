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

import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XmlString;
import com.sun.xml.internal.xsom.impl.parser.SchemaDocumentImpl;
import com.sun.xml.internal.xsom.visitor.XSFunction;
import com.sun.xml.internal.xsom.visitor.XSVisitor;
import org.xml.sax.Locator;

public class AttributeDeclImpl extends DeclarationImpl implements XSAttributeDecl, Ref.Attribute
{
    public AttributeDeclImpl( SchemaDocumentImpl owner,
        String _targetNamespace, String _name,
        AnnotationImpl _annon, Locator _loc, ForeignAttributesImpl _fa, boolean _anonymous,
        XmlString _defValue, XmlString _fixedValue,
        Ref.SimpleType _type ) {

        super(owner,_annon,_loc,_fa,_targetNamespace,_name,_anonymous);

        if(_name==null) // assertion failed.
            throw new IllegalArgumentException();

        this.defaultValue = _defValue;
        this.fixedValue = _fixedValue;
        this.type = _type;
    }

    private final Ref.SimpleType type;
    public XSSimpleType getType() { return type.getType(); }

    private final XmlString defaultValue;
    public XmlString getDefaultValue() { return defaultValue; }

    private final XmlString fixedValue;
    public XmlString getFixedValue() { return fixedValue; }

    public void visit( XSVisitor visitor ) {
        visitor.attributeDecl(this);
    }
    public Object apply( XSFunction function ) {
        return function.attributeDecl(this);
    }


    // Ref.Attribute implementation
    public XSAttributeDecl getAttribute() { return this; }
 }
