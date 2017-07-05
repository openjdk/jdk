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
package com.sun.xml.internal.bind.v2.model.impl;

import java.util.Collection;
import java.lang.annotation.Annotation;

import javax.activation.MimeType;
import javax.xml.bind.annotation.XmlAttachmentRef;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlInlineBinaryData;
import javax.xml.bind.annotation.XmlMimeType;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapters;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.v2.TODO;
import com.sun.xml.internal.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.internal.bind.v2.model.annotation.Locatable;
import com.sun.xml.internal.bind.v2.model.core.Adapter;
import com.sun.xml.internal.bind.v2.model.core.ID;
import com.sun.xml.internal.bind.v2.model.core.PropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.TypeInfo;
import com.sun.xml.internal.bind.v2.model.core.TypeInfoSet;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;
import com.sun.xml.internal.bind.v2.runtime.Location;
import com.sun.xml.internal.bind.v2.runtime.SwaRefAdapter;

/**
 * Default partial implementation for {@link PropertyInfo}.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class PropertyInfoImpl<T,C,F,M>
    implements PropertyInfo<T,C>, Locatable, Comparable<PropertyInfoImpl> /*by their names*/ {

    /**
     * Object that reads annotations.
     */
    protected final PropertySeed<T,C,F,M> seed;

    private final boolean isCollection;

    private final ID id;

    private final MimeType expectedMimeType;
    private final boolean inlineBinary;
    private final QName schemaType;

    protected final ClassInfoImpl<T,C,F,M> parent;

    private final Adapter<T,C> adapter;

    protected PropertyInfoImpl(ClassInfoImpl<T,C,F,M> parent, PropertySeed<T,C,F,M> spi) {
        this.seed = spi;
        this.parent = parent;

        if(parent==null)
            /*
                Various people reported a bug where this parameter is somehow null.
                In an attempt to catch the error better, let's do an explicit check here.

                http://forums.java.net/jive/thread.jspa?threadID=18479
                http://forums.java.net/jive/thread.jspa?messageID=165946
             */
            throw new AssertionError();

        MimeType mt = Util.calcExpectedMediaType(seed,parent.builder);
        if(mt!=null && !kind().canHaveXmlMimeType) {
            parent.builder.reportError(new IllegalAnnotationException(
                Messages.ILLEGAL_ANNOTATION.format(XmlMimeType.class.getName()),
                seed.readAnnotation(XmlMimeType.class)
            ));
            mt = null;
        }
        this.expectedMimeType = mt;
        this.inlineBinary = seed.hasAnnotation(XmlInlineBinaryData.class);
        this.schemaType = Util.calcSchemaType(reader(),seed,parent.clazz,
                getIndividualType(),this);

        T t = seed.getRawType();

        // check if there's an adapter applicable to the whole property
        XmlJavaTypeAdapter xjta = getApplicableAdapter(t);
        if(xjta!=null) {
            isCollection = false;
            adapter = new Adapter<T,C>(xjta,reader(),nav());
        } else {
            // check if the adapter is applicable to the individual item in the property

            this.isCollection = nav().isSubClassOf(t, nav().ref(Collection.class))
                             || nav().isArrayButNotByteArray(t);

            xjta = getApplicableAdapter(getIndividualType());
            if(xjta==null) {
                // ugly ugly hack, but we implement swaRef as adapter
                XmlAttachmentRef xsa = seed.readAnnotation(XmlAttachmentRef.class);
                if(xsa!=null) {
                    parent.builder.hasSwaRef = true;
                    adapter = new Adapter<T,C>(nav().asDecl(SwaRefAdapter.class),nav());
                } else {
                    adapter = null;

                    // if this field has adapter annotation but not applicable,
                    // that must be an error of the user
                    xjta = seed.readAnnotation(XmlJavaTypeAdapter.class);
                    if(xjta!=null) {
                        T adapter = reader().getClassValue(xjta,"value");
                        parent.builder.reportError(new IllegalAnnotationException(
                            Messages.UNMATCHABLE_ADAPTER.format(
                                    nav().getTypeName(adapter), nav().getTypeName(t)),
                            xjta
                        ));
                    }
                }
            } else {
                adapter = new Adapter<T,C>(xjta,reader(),nav());
            }
        }

        this.id = calcId();
    }


    public ClassInfoImpl<T,C,F,M> parent() {
        return parent;
    }

    protected final Navigator<T,C,F,M> nav() {
        return parent.nav();
    }
    protected final AnnotationReader<T,C,F,M> reader() {
        return parent.reader();
    }

    public T getRawType() {
        return seed.getRawType();
    }

    public T getIndividualType() {
        if(adapter!=null)
            return adapter.defaultType;
        T raw = getRawType();
        if(!isCollection()) {
            return raw;
        } else {
            if(nav().isArrayButNotByteArray(raw))
                return nav().getComponentType(raw);

            T bt = nav().getBaseClass(raw, nav().asDecl(Collection.class) );
            if(nav().isParameterizedType(bt))
                return nav().getTypeArgument(bt,0);
            else
                return nav().ref(Object.class);
        }
    }

    public final String getName() {
        return seed.getName();
    }

    /**
     * Checks if the given adapter is applicable to the declared property type.
     */
    private boolean isApplicable(XmlJavaTypeAdapter jta, T declaredType ) {
        if(jta==null)   return false;

        T type = reader().getClassValue(jta,"type");
        if(declaredType.equals(type))
            return true;    // for types explicitly marked in XmlJavaTypeAdapter.type()

        T adapter = reader().getClassValue(jta,"value");
        T ba = nav().getBaseClass(adapter, nav().asDecl(XmlAdapter.class));
        if(!nav().isParameterizedType(ba))
            return true;   // can't check type applicability. assume Object, which means applicable to any.
        T inMemType = nav().getTypeArgument(ba, 1);

        return nav().isSubClassOf(declaredType,inMemType);
    }

    private XmlJavaTypeAdapter getApplicableAdapter(T type) {
        XmlJavaTypeAdapter jta = seed.readAnnotation(XmlJavaTypeAdapter.class);
        if(jta!=null && isApplicable(jta,type))
            return jta;

        // check the applicable adapters on the package
        XmlJavaTypeAdapters jtas = reader().getPackageAnnotation(XmlJavaTypeAdapters.class, parent.clazz, seed );
        if(jtas!=null) {
            for (XmlJavaTypeAdapter xjta : jtas.value()) {
                if(isApplicable(xjta,type))
                    return xjta;
            }
        }
        jta = reader().getPackageAnnotation(XmlJavaTypeAdapter.class, parent.clazz, seed );
        if(isApplicable(jta,type))
            return jta;

        // then on the target class
        C refType = nav().asDecl(type);
        if(refType!=null) {
            jta = reader().getClassAnnotation(XmlJavaTypeAdapter.class, refType, seed );
            if(jta!=null && isApplicable(jta,type)) // the one on the type always apply.
                return jta;
        }

        return null;
    }

    /**
     * This is the default implementation of the getAdapter method
     * defined on many of the {@link PropertyInfo}-derived classes.
     */
    public Adapter<T,C> getAdapter() {
        return adapter;
    }


    public final String displayName() {
        return nav().getClassName(parent.getClazz())+'#'+getName();
    }

    public final ID id() {
        return id;
    }

    private ID calcId() {
        if(seed.hasAnnotation(XmlID.class)) {
            // check the type
            if(!getIndividualType().equals(nav().ref(String.class)))
                parent.builder.reportError(new IllegalAnnotationException(
                    Messages.ID_MUST_BE_STRING.format(getName()), seed )
                );
            return ID.ID;
        } else
        if(seed.hasAnnotation(XmlIDREF.class)) {
            return ID.IDREF;
        } else {
            return ID.NONE;
        }
    }

    public final MimeType getExpectedMimeType() {
        return expectedMimeType;
    }

    public final boolean inlineBinaryData() {
        return inlineBinary;
    }

    public final QName getSchemaType() {
        return schemaType;
    }

    public final boolean isCollection() {
        return isCollection;
    }

    /**
     * Called after all the {@link TypeInfo}s are collected into the governing {@link TypeInfoSet}.
     *
     * Derived class can do additional actions to complete the model.
     */
    protected void link() {
        if(id==ID.IDREF) {
            // make sure that the refereced type has ID
            for (TypeInfo<T,C> ti : ref()) {
                if(!ti.canBeReferencedByIDREF())
                    parent.builder.reportError(new IllegalAnnotationException(
                    Messages.INVALID_IDREF.format(
                        parent.builder.nav.getTypeName(ti.getType())), this ));
            }
        }
    }

    /**
     * A {@link PropertyInfoImpl} is always referenced by its enclosing class,
     * so return that as the upstream.
     */
    public Locatable getUpstream() {
        return parent;
    }

    public Location getLocation() {
        return seed.getLocation();
    }


//
//
// convenience methods for derived classes
//
//


    /**
     * Computes the tag name from a {@link XmlElement} by taking the defaulting into account.
     */
    protected final QName calcXmlName(XmlElement e) {
        if(e!=null)
            return calcXmlName(e.namespace(),e.name());
        else
            return calcXmlName("##default","##default");
    }

    /**
     * Computes the tag name from a {@link XmlElementWrapper} by taking the defaulting into account.
     */
    protected final QName calcXmlName(XmlElementWrapper e) {
        if(e!=null)
            return calcXmlName(e.namespace(),e.name());
        else
            return calcXmlName("##default","##default");
    }

    private QName calcXmlName(String uri,String local) {
        // compute the default
        TODO.checkSpec();
        if(local.length()==0 || local.equals("##default"))
            local = seed.getName();
        if(uri.equals("##default")) {
            XmlSchema xs = reader().getPackageAnnotation( XmlSchema.class, parent.getClazz(), this );
            // JAX-RPC doesn't want the default namespace URI swapping to take effect to
            // local "unqualified" elements. UGLY.
            if(xs!=null) {
                switch(xs.elementFormDefault()) {
                case QUALIFIED:
                    QName typeName = parent.getTypeName();
                    if(typeName!=null)
                        uri = typeName.getNamespaceURI();
                    else
                        uri = xs.namespace();
                    if(uri.length()==0)
                        uri = parent.builder.defaultNsUri;
                    break;
                case UNQUALIFIED:
                case UNSET:
                    uri = "";
                }
            } else {
                uri = "";
            }
        }
        return new QName(uri.intern(),local.intern());
    }

    public int compareTo(PropertyInfoImpl that) {
        return this.getName().compareTo(that.getName());
    }

    public final <A extends Annotation> A readAnnotation(Class<A> annotationType) {
        return seed.readAnnotation(annotationType);
    }

    public final boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return seed.hasAnnotation(annotationType);
    }
}
