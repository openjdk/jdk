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

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.activation.MimeType;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAttachmentRef;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlInlineBinaryData;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;

import com.sun.istack.internal.FinalArrayList;
import com.sun.xml.internal.bind.v2.TODO;
import com.sun.xml.internal.bind.v2.model.annotation.AnnotationSource;
import com.sun.xml.internal.bind.v2.model.annotation.Locatable;
import com.sun.xml.internal.bind.v2.model.core.Adapter;
import com.sun.xml.internal.bind.v2.model.core.ClassInfo;
import com.sun.xml.internal.bind.v2.model.core.ElementInfo;
import com.sun.xml.internal.bind.v2.model.core.ElementPropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.ID;
import com.sun.xml.internal.bind.v2.model.core.NonElement;
import com.sun.xml.internal.bind.v2.model.core.PropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.PropertyKind;
import com.sun.xml.internal.bind.v2.model.core.TypeInfo;
import com.sun.xml.internal.bind.v2.model.core.TypeRef;
import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;
import com.sun.xml.internal.bind.v2.runtime.Location;
import com.sun.xml.internal.bind.v2.runtime.SwaRefAdapter;

/**
 * {@link ElementInfo} implementation.
 *
 * @author Kohsuke Kawaguchi
 */
class ElementInfoImpl<T,C,F,M> extends TypeInfoImpl<T,C,F,M> implements ElementInfo<T,C> {

    private final QName tagName;

    private final NonElement<T,C> contentType;

    private final T tOfJAXBElementT;

    private final T elementType;

    private final ClassInfo<T,C> scope;

    /**
     * Annotation that controls the binding.
     */
    private final XmlElementDecl anno;

    /**
     * If this element can substitute another element, the element name.
     * @see #link()
     */
    private ElementInfoImpl<T,C,F,M> substitutionHead;

    /**
     * Lazily constructed list of {@link ElementInfo}s that can substitute this element.
     * This could be null.
     * @see #link()
     */
    private FinalArrayList<ElementInfoImpl<T,C,F,M>> substitutionMembers;

    /**
     * The factory method from which this mapping was created.
     */
    private final M method;

    /**
     * If the content type is adapter, return that adapter.
     */
    private final Adapter<T,C> adapter;

    private final boolean isCollection;

    private final ID id;

    private final PropertyImpl property;
    private final MimeType expectedMimeType;
    private final boolean inlineBinary;
    private final QName schemaType;

    /**
     * Singleton instance of {@link ElementPropertyInfo} for this element.
     */
    protected class PropertyImpl implements
            ElementPropertyInfo<T,C>,
            TypeRef<T,C>,
            AnnotationSource {
        //
        // TypeRef impl
        //
        public NonElement<T,C> getTarget() {
            return contentType;
        }
        public QName getTagName() {
            return tagName;
        }

        public List<? extends TypeRef<T,C>> getTypes() {
            return Collections.singletonList(this);
        }

        public List<? extends NonElement<T,C>> ref() {
            return Collections.singletonList(contentType);
        }

        public QName getXmlName() {
            return tagName;
        }

        public boolean isCollectionRequired() {
            return false;
        }

        public boolean isCollectionNillable() {
            return true;
        }

        public boolean isNillable() {
            return true;
        }

        public String getDefaultValue() {
            String v = anno.defaultValue();
            if(v.equals("\u0000"))
                return null;
            else
                return v;
        }

        public ElementInfoImpl<T,C,F,M> parent() {
            return ElementInfoImpl.this;
        }

        public String getName() {
            return "value";
        }

        public String displayName() {
            return "JAXBElement#value";
        }

        public boolean isCollection() {
            return isCollection;
        }

        /**
         * For {@link ElementInfo}s, a collection always means a list of values.
         */
        public boolean isValueList() {
            return isCollection;
        }

        public boolean isRequired() {
            return true;
        }

        public PropertyKind kind() {
            return PropertyKind.ELEMENT;
        }

        public Adapter<T,C> getAdapter() {
            return adapter;
        }

        public ID id() {
            return id;
        }

        public MimeType getExpectedMimeType() {
            return expectedMimeType;
        }

        public QName getSchemaType() {
            return schemaType;
        }

        public boolean inlineBinaryData() {
            return inlineBinary;
        }

        public PropertyInfo<T,C> getSource() {
            return this;
        }

        //
        //
        // AnnotationSource impl
        //
        //
        public <A extends Annotation> A readAnnotation(Class<A> annotationType) {
            return reader().getMethodAnnotation(annotationType,method,ElementInfoImpl.this);
        }

        public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
            return reader().hasMethodAnnotation(annotationType,method);
        }
    }

    /**
     * @param m
     *      The factory method on ObjectFactory that comes with {@link XmlElementDecl}.
     */
    public ElementInfoImpl(ModelBuilder<T,C,F,M> builder,
                           RegistryInfoImpl<T,C,F,M> registry, M m ) throws IllegalAnnotationException {
        super(builder,registry);

        this.method = m;
        anno = reader().getMethodAnnotation( XmlElementDecl.class, m, this );
        assert anno!=null;  // the caller should check this
        assert anno instanceof Locatable;

        elementType = nav().getReturnType(m);
        T baseClass = nav().getBaseClass(elementType,nav().asDecl(JAXBElement.class));
        if(baseClass==null)
            throw new IllegalAnnotationException(
                Messages.XML_ELEMENT_MAPPING_ON_NON_IXMLELEMENT_METHOD.format(nav().getMethodName(m)),
                anno );

        tagName = parseElementName(anno);
        T[] methodParams = nav().getMethodParameters(m);

        // adapter
        Adapter<T,C> a = null;
        if(methodParams.length>0) {
            XmlJavaTypeAdapter adapter = reader().getMethodAnnotation(XmlJavaTypeAdapter.class,m,this);
            if(adapter!=null)
                a = new Adapter<T,C>(adapter,reader(),nav());
            else {
                XmlAttachmentRef xsa = reader().getMethodAnnotation(XmlAttachmentRef.class,m,this);
                if(xsa!=null) {
                    TODO.prototype("in APT swaRefAdapter isn't avaialble, so this returns null");
                    a = new Adapter<T,C>(owner.nav.asDecl(SwaRefAdapter.class),owner.nav);
                }
            }
        }
        this.adapter = a;

        // T of JAXBElement<T>
        tOfJAXBElementT =
            methodParams.length>0 ? methodParams[0] // this is more reliable, as it works even for ObjectFactory that sometimes have to return public types
            : nav().getTypeArgument(baseClass,0); // fall back to infer from the return type if no parameter.

        if(adapter==null) {
            T list = nav().getBaseClass(tOfJAXBElementT,nav().asDecl(List.class));
            if(list==null) {
                isCollection = false;
                contentType = builder.getTypeInfo(tOfJAXBElementT,this);  // suck this type into the current set.
            } else {
                isCollection = true;
                contentType = builder.getTypeInfo(nav().getTypeArgument(list,0),this);
            }
        } else {
            // but if adapted, use the adapted type
            contentType = builder.getTypeInfo(this.adapter.defaultType,this);
            isCollection = false;
        }

        // scope
        T s = reader().getClassValue(anno,"scope");
        if(s.equals(nav().ref(XmlElementDecl.GLOBAL.class)))
            scope = null;
        else {
            // TODO: what happens if there's an error?
            NonElement<T,C> scp = builder.getClassInfo(nav().asDecl(s),this);
            if(!(scp instanceof ClassInfo)) {
                throw new IllegalAnnotationException(
                    Messages.SCOPE_IS_NOT_COMPLEXTYPE.format(nav().getTypeName(s)),
                    anno );
            }
            scope = (ClassInfo<T,C>)scp;
        }

        id = calcId();

        property = createPropertyImpl();

        this.expectedMimeType = Util.calcExpectedMediaType(property,builder);
        this.inlineBinary = reader().hasMethodAnnotation(XmlInlineBinaryData.class,method);
        this.schemaType = Util.calcSchemaType(reader(),property,registry.registryClass,
                getContentInMemoryType(),this);
    }

    final QName parseElementName(XmlElementDecl e) {
        String local = e.name();
        String nsUri = e.namespace();
        if(nsUri.equals("##default")) {
            // if defaulted ...
            XmlSchema xs = reader().getPackageAnnotation(XmlSchema.class,
                nav().getDeclaringClassForMethod(method),this);
            if(xs!=null)
                nsUri = xs.namespace();
            else {
                nsUri = builder.defaultNsUri;
            }
        }

        return new QName(nsUri.intern(),local.intern());
    }

    protected PropertyImpl createPropertyImpl() {
        return new PropertyImpl();
    }

    public ElementPropertyInfo<T,C> getProperty() {
        return property;
    }

    public NonElement<T,C> getContentType() {
        return contentType;
    }

    public T getContentInMemoryType() {
        if(adapter==null) {
            return tOfJAXBElementT;
        } else {
            return adapter.customType;
        }
    }

    public QName getElementName() {
        return tagName;
    }

    public T getType() {
        return elementType;
    }

    /**
     * Leaf-type cannot be referenced from IDREF.
     *
     * @deprecated
     *      why are you calling a method whose return value is always known?
     */
    public final boolean canBeReferencedByIDREF() {
        return false;
    }

    private ID calcId() {
        // TODO: share code with PropertyInfoImpl
        if(reader().hasMethodAnnotation(XmlID.class,method)) {
            return ID.ID;
        } else
        if(reader().hasMethodAnnotation(XmlIDREF.class,method)) {
            return ID.IDREF;
        } else {
            return ID.NONE;
        }
    }

    public ClassInfo<T, C> getScope() {
        return scope;
    }

    public ElementInfo<T,C> getSubstitutionHead() {
        return substitutionHead;
    }

    public Collection<? extends ElementInfoImpl<T,C,F,M>> getSubstitutionMembers() {
        if(substitutionMembers==null)
            return Collections.emptyList();
        else
            return substitutionMembers;
    }

    /**
     * Called after all the {@link TypeInfo}s are collected into the {@link #owner}.
     */
    /*package*/ void link() {
        // substitution head
        if(anno.substitutionHeadName().length()!=0) {
            QName name = new QName(
                anno.substitutionHeadNamespace(), anno.substitutionHeadName() );
            substitutionHead = owner.getElementInfo(null,name);
            if(substitutionHead==null) {
                builder.reportError(
                    new IllegalAnnotationException(Messages.NON_EXISTENT_ELEMENT_MAPPING.format(
                        name.getNamespaceURI(),name.getLocalPart()), anno));
                // recover by ignoring this substitution declaration
            } else
                substitutionHead.addSubstitutionMember(this);
        } else
            substitutionHead = null;
        super.link();
    }

    private void addSubstitutionMember(ElementInfoImpl<T,C,F,M> child) {
        if(substitutionMembers==null)
            substitutionMembers = new FinalArrayList<ElementInfoImpl<T,C,F,M>>();
        substitutionMembers.add(child);
    }

    public Location getLocation() {
        return nav().getMethodLocation(method);
    }
}
