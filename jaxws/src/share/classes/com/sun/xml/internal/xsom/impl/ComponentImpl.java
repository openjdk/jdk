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

import com.sun.xml.internal.xsom.SCD;
import com.sun.xml.internal.xsom.XSAnnotation;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XSSchemaSet;
import com.sun.xml.internal.xsom.util.ComponentNameFunction;
import com.sun.xml.internal.xsom.impl.parser.SchemaDocumentImpl;
import com.sun.xml.internal.xsom.parser.SchemaDocument;
import org.xml.sax.Locator;
import org.xml.sax.helpers.LocatorImpl;

import javax.xml.namespace.NamespaceContext;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class ComponentImpl implements XSComponent
{
    protected ComponentImpl( SchemaDocumentImpl _owner, AnnotationImpl _annon, Locator _loc, ForeignAttributesImpl fa ) {
        this.ownerDocument = _owner;
        this.annotation = _annon;
        this.locator = _loc;
        this.foreignAttributes = fa;
    }

    protected final SchemaDocumentImpl ownerDocument;
    public SchemaImpl getOwnerSchema() {
        if(ownerDocument==null)
            return null;
        else
            return ownerDocument.getSchema();
    }

    public XSSchemaSet getRoot() {
        if(ownerDocument==null)
            return null;
        else
            return getOwnerSchema().getRoot();
    }

    public SchemaDocument getSourceDocument() {
        return ownerDocument;
    }

    private AnnotationImpl annotation;
    public final XSAnnotation getAnnotation() { return annotation; }

    public XSAnnotation getAnnotation(boolean createIfNotExist) {
        if(createIfNotExist && annotation==null) {
            annotation = new AnnotationImpl();
        }
        return annotation;
    }

    private final Locator locator;
    public final Locator getLocator() { return locator; }

    /**
     * Either {@link ForeignAttributesImpl} or {@link List}.
     *
     * Initially it's {@link ForeignAttributesImpl}, but it's lazily turned into
     * a list when necessary.
     */
    private Object foreignAttributes;

    public List<ForeignAttributesImpl> getForeignAttributes() {
        Object t = foreignAttributes;

        if(t==null)
            return Collections.EMPTY_LIST;

        if(t instanceof List)
            return (List)t;

        t = foreignAttributes = convertToList((ForeignAttributesImpl)t);
        return (List)t;
    }

    public String getForeignAttribute(String nsUri, String localName) {
        for( ForeignAttributesImpl fa : getForeignAttributes() ) {
            String v = fa.getValue(nsUri,localName);
            if(v!=null) return v;
        }
        return null;
    }

    private List<ForeignAttributesImpl> convertToList(ForeignAttributesImpl fa) {
        List<ForeignAttributesImpl> lst = new ArrayList<ForeignAttributesImpl>();
        while(fa!=null) {
            lst.add(fa);
            fa = fa.next;
        }
        return Collections.unmodifiableList(lst);
    }

    public Collection<XSComponent> select(String scd, NamespaceContext nsContext) {
        try {
            return SCD.create(scd,nsContext).select(this);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public XSComponent selectSingle(String scd, NamespaceContext nsContext) {
        try {
            return SCD.create(scd,nsContext).selectSingle(this);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String toString() {
        return apply(new ComponentNameFunction());
    }
}
