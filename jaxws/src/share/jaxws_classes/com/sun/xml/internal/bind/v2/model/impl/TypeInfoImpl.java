/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.model.impl;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.api.impl.NameConverter;
import com.sun.xml.internal.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.internal.bind.v2.model.annotation.Locatable;
import com.sun.xml.internal.bind.v2.model.core.TypeInfo;
import com.sun.xml.internal.bind.v2.model.core.TypeInfoSet;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;

/**
 * Common implementation between {@link ClassInfoImpl} and {@link ElementInfoImpl}.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class TypeInfoImpl<TypeT,ClassDeclT,FieldT,MethodT>
        implements TypeInfo<TypeT,ClassDeclT>, Locatable {

    /**
     * The Java class that caused this Java class to be a part of the JAXB processing.
     *
     * null if it's specified explicitly by the user.
     */
    private final Locatable upstream;

    /**
     * {@link TypeInfoSet} to which this class belongs.
     */
    protected final TypeInfoSetImpl<TypeT,ClassDeclT,FieldT,MethodT> owner;

    /**
     * Reference to the {@link ModelBuilder}, only until we link {@link TypeInfo}s all together,
     * because we don't want to keep {@link ModelBuilder} too long.
     */
    protected ModelBuilder<TypeT,ClassDeclT,FieldT,MethodT> builder;

    protected TypeInfoImpl(
        ModelBuilder<TypeT,ClassDeclT,FieldT,MethodT> builder,
        Locatable upstream) {

        this.builder = builder;
        this.owner = builder.typeInfoSet;
        this.upstream = upstream;
    }

    public Locatable getUpstream() {
        return upstream;
    }

    /*package*/ void link() {
        builder = null;
    }

    protected final Navigator<TypeT,ClassDeclT,FieldT,MethodT> nav() {
        return owner.nav;
    }

    protected final AnnotationReader<TypeT,ClassDeclT,FieldT,MethodT> reader() {
        return owner.reader;
    }

    /**
     * Parses an {@link XmlRootElement} annotation on a class
     * and determine the element name.
     *
     * @return null
     *      if none was found.
     */
    protected final QName parseElementName(ClassDeclT clazz) {
        XmlRootElement e = reader().getClassAnnotation(XmlRootElement.class,clazz,this);
        if(e==null)
            return null;

        String local = e.name();
        if(local.equals("##default")) {
            // if defaulted...
            local = NameConverter.standard.toVariableName(nav().getClassShortName(clazz));
        }
        String nsUri = e.namespace();
        if(nsUri.equals("##default")) {
            // if defaulted ...
            XmlSchema xs = reader().getPackageAnnotation(XmlSchema.class,clazz,this);
            if(xs!=null)
                nsUri = xs.namespace();
            else {
                nsUri = builder.defaultNsUri;
            }
        }

        return new QName(nsUri.intern(),local.intern());
    }

    protected final QName parseTypeName(ClassDeclT clazz) {
        return parseTypeName( clazz, reader().getClassAnnotation(XmlType.class,clazz,this) );
    }

    /**
     * Parses a (potentially-null) {@link XmlType} annotation on a class
     * and determine the actual value.
     *
     * @param clazz
     *      The class on which the XmlType annotation is checked.
     * @param t
     *      The {@link XmlType} annotation on the clazz. This value
     *      is taken as a parameter to improve the performance for the case where
     *      't' is pre-computed.
     */
    protected final QName parseTypeName(ClassDeclT clazz, XmlType t) {
        String nsUri="##default";
        String local="##default";
        if(t!=null) {
            nsUri = t.namespace();
            local = t.name();
        }

        if(local.length()==0)
            return null; // anonymous


        if(local.equals("##default"))
            // if defaulted ...
            local = NameConverter.standard.toVariableName(nav().getClassShortName(clazz));

        if(nsUri.equals("##default")) {
            // if defaulted ...
            XmlSchema xs = reader().getPackageAnnotation(XmlSchema.class,clazz,this);
            if(xs!=null)
                nsUri = xs.namespace();
            else {
                nsUri = builder.defaultNsUri;
            }
        }

        return new QName(nsUri.intern(),local.intern());
    }
}
