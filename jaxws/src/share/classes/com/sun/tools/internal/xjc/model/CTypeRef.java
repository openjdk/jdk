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

package com.sun.tools.internal.xjc.model;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;

import com.sun.tools.internal.xjc.model.nav.NClass;
import com.sun.tools.internal.xjc.model.nav.NType;
import com.sun.tools.internal.xjc.reader.xmlschema.BGMBuilder;
import com.sun.xml.internal.bind.v2.model.core.PropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.TypeRef;
import com.sun.xml.internal.bind.v2.runtime.RuntimeUtil;
import com.sun.xml.internal.xsom.XmlString;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.istack.internal.Nullable;

/**
 * {@link TypeRef} for XJC.
 *
 * TODO: do we need the source schema component support here?
 *
 * @author Kohsuke Kawaguchi
 */
public final class CTypeRef implements TypeRef<NType,NClass> {
    /**
     * In-memory type.
     *
     * This is the type used when
     */
    @XmlJavaTypeAdapter(RuntimeUtil.ToStringAdapter.class)
    private final CNonElement type;

    private final QName elementName;

    /**
     * XML Schema type name of {@link #type}, if available.
     */
    /*package*/ final @Nullable QName typeName;

    private final boolean nillable;
    public final XmlString defaultValue;

    public CTypeRef(CNonElement type, XSElementDecl decl) {
        this(type, BGMBuilder.getName(decl),getSimpleTypeName(decl), decl.isNillable(), decl.getDefaultValue() );

    }

    public static QName getSimpleTypeName(XSElementDecl decl) {
        if(decl==null)  return null;
        QName typeName = null;
        if(decl.getType().isSimpleType())
            typeName = BGMBuilder.getName(decl.getType());
        return typeName;
    }

    public CTypeRef(CNonElement type, QName elementName, QName typeName, boolean nillable, XmlString defaultValue) {
        assert type!=null;
        assert elementName!=null;

        this.type = type;
        this.elementName = elementName;
        this.typeName = typeName;
        this.nillable = nillable;
        this.defaultValue = defaultValue;
    }

    public CNonElement getTarget() {
        return type;
    }

    public QName getTagName() {
        return elementName;
    }

    public boolean isNillable() {
        return nillable;
    }

    /**
     * Inside XJC, use {@link #defaultValue} that has context information.
     * This method is to override the one defined in the runtime model.
     *
     * @see #defaultValue
     */
    public String getDefaultValue() {
        if(defaultValue!=null)
            return defaultValue.value;
        else
            return null;
    }

    public boolean isLeaf() {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }

    public PropertyInfo<NType, NClass> getSource() {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }
}
