/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementRefs;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.internal.bind.v2.model.core.ClassInfo;
import com.sun.xml.internal.bind.v2.model.core.Element;
import com.sun.xml.internal.bind.v2.model.core.ElementInfo;
import com.sun.xml.internal.bind.v2.model.core.NonElement;
import com.sun.xml.internal.bind.v2.model.core.PropertyKind;
import com.sun.xml.internal.bind.v2.model.core.ReferencePropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.WildcardMode;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;
import java.util.Iterator;

/**
 * Implementation of {@link ReferencePropertyInfo}.
 *
 * @author Kohsuke Kawaguchi
 */
class ReferencePropertyInfoImpl<T,C,F,M>
    extends ERPropertyInfoImpl<T,C,F,M>
    implements ReferencePropertyInfo<T,C>, DummyPropertyInfo<T, C, F, M>
{
    /**
     * Lazily computed.
     * @see #getElements()
     */
    private Set<Element<T,C>> types;
    private Set<ReferencePropertyInfoImpl<T,C,F,M>> subTypes = new LinkedHashSet<ReferencePropertyInfoImpl<T,C,F,M>>();

    private final boolean isMixed;

    private final WildcardMode wildcard;
    private final C domHandler;
    /**
     * Lazily computed.
     * @see #isRequired()
     */
    private Boolean isRequired;

    public ReferencePropertyInfoImpl(
        ClassInfoImpl<T,C,F,M> classInfo,
        PropertySeed<T,C,F,M> seed) {

        super(classInfo, seed);

        isMixed = seed.readAnnotation(XmlMixed.class) != null;

        XmlAnyElement xae = seed.readAnnotation(XmlAnyElement.class);
        if(xae==null) {
            wildcard = null;
            domHandler = null;
        } else {
            wildcard = xae.lax()?WildcardMode.LAX:WildcardMode.SKIP;
            domHandler = nav().asDecl(reader().getClassValue(xae,"value"));
        }
    }

    public Set<? extends Element<T,C>> ref() {
        return getElements();
    }

    public PropertyKind kind() {
        return PropertyKind.REFERENCE;
    }

    public Set<? extends Element<T,C>> getElements() {
        if(types==null)
            calcTypes(false);
        assert types!=null;
        return types;
    }

    /**
     * Compute {@link #types}.
     *
     * @param last
     *      if true, every {@link XmlElementRef} must yield at least one type.
     */
    private void calcTypes(boolean last) {
        XmlElementRef[] ann;
        types = new LinkedHashSet<Element<T,C>>();
        XmlElementRefs refs = seed.readAnnotation(XmlElementRefs.class);
        XmlElementRef ref = seed.readAnnotation(XmlElementRef.class);

        if(refs!=null && ref!=null) {
            parent.builder.reportError(new IllegalAnnotationException(
                    Messages.MUTUALLY_EXCLUSIVE_ANNOTATIONS.format(
                    nav().getClassName(parent.getClazz())+'#'+seed.getName(),
                    ref.annotationType().getName(), refs.annotationType().getName()),
                    ref, refs ));
        }

        if(refs!=null)
            ann = refs.value();
        else {
            if(ref!=null)
                ann = new XmlElementRef[]{ref};
            else
                ann = null;
        }

        isRequired = !isCollection();  // this is by default, to remain compatible with 2.1

        if(ann!=null) {
            Navigator<T,C,F,M> nav = nav();
            AnnotationReader<T,C,F,M> reader = reader();

            final T defaultType = nav.ref(XmlElementRef.DEFAULT.class);
            final C je = nav.asDecl(JAXBElement.class);

            for( XmlElementRef r : ann ) {
                boolean yield;
                T type = reader.getClassValue(r,"type");
                if(nav().isSameType(type, defaultType))
                    type = nav.erasure(getIndividualType());
                if(nav.getBaseClass(type,je)!=null)
                    yield = addGenericElement(r);
                else
                    yield = addAllSubtypes(type);

                // essentially "isRequired &= isRequired(r)" except that we'd like to skip evaluating isRequird(r)
                // if the value is already false.
                if(isRequired && !isRequired(r))
                    isRequired = false;

                if(last && !yield) {
                    // a reference didn't produce any type.
                    // diagnose the problem
                    if(nav().isSameType(type, nav.ref(JAXBElement.class))) {
                        // no XmlElementDecl
                        parent.builder.reportError(new IllegalAnnotationException(
                            Messages.NO_XML_ELEMENT_DECL.format(
                                getEffectiveNamespaceFor(r), r.name()),
                            this
                        ));
                    } else {
                        parent.builder.reportError(new IllegalAnnotationException(
                            Messages.INVALID_XML_ELEMENT_REF.format(type),this));
                    }

                    // reporting one error would do.
                    // often the element ref field is using @XmlElementRefs
                    // to point to multiple JAXBElements.
                    // reporting one error for each @XmlElemetnRef is thus often redundant.
                    return;
                }
            }
        }

        for (ReferencePropertyInfoImpl<T, C, F, M> info : subTypes) {
            PropertySeed sd = info.seed;
            refs = sd.readAnnotation(XmlElementRefs.class);
            ref = sd.readAnnotation(XmlElementRef.class);

            if (refs != null && ref != null) {
                parent.builder.reportError(new IllegalAnnotationException(
                        Messages.MUTUALLY_EXCLUSIVE_ANNOTATIONS.format(
                        nav().getClassName(parent.getClazz())+'#'+seed.getName(),
                        ref.annotationType().getName(), refs.annotationType().getName()),
                        ref, refs ));
            }

            if (refs != null) {
                ann = refs.value();
            } else {
                if (ref != null) {
                    ann = new XmlElementRef[]{ref};
                } else {
                    ann = null;
                }
            }

            if (ann != null) {
                Navigator<T,C,F,M> nav = nav();
                AnnotationReader<T,C,F,M> reader = reader();

                final T defaultType = nav.ref(XmlElementRef.DEFAULT.class);
                final C je = nav.asDecl(JAXBElement.class);

                for( XmlElementRef r : ann ) {
                    boolean yield;
                    T type = reader.getClassValue(r,"type");
                    if (nav().isSameType(type, defaultType)) {
                        type = nav.erasure(getIndividualType());
                    }
                    if (nav.getBaseClass(type,je) != null) {
                        yield = addGenericElement(r, info);

                    } else {
                        yield = addAllSubtypes(type);
                    }

                    if(last && !yield) {
                        // a reference didn't produce any type.
                        // diagnose the problem
                        if(nav().isSameType(type, nav.ref(JAXBElement.class))) {
                            // no XmlElementDecl
                            parent.builder.reportError(new IllegalAnnotationException(
                                Messages.NO_XML_ELEMENT_DECL.format(
                                    getEffectiveNamespaceFor(r), r.name()),
                                this
                            ));
                        } else {
                            parent.builder.reportError(new IllegalAnnotationException(
                                Messages.INVALID_XML_ELEMENT_REF.format(),this));
                        }

                        // reporting one error would do.
                        // often the element ref field is using @XmlElementRefs
                        // to point to multiple JAXBElements.
                        // reporting one error for each @XmlElemetnRef is thus often redundant.
                        return;
                    }
                }
            }
        }

        types = Collections.unmodifiableSet(types);
    }

    public boolean isRequired() {
        if(isRequired==null)
            calcTypes(false);
        return isRequired;
    }

    /**
     * If we find out that we are working with 2.1 API, remember the fact so that
     * we don't waste time generating exceptions every time we call {@link #isRequired(XmlElementRef)}.
     */
    private static boolean is2_2 = true;

    /**
     * Reads the value of {@code XmlElementRef.required()}.
     *
     * If we are working as 2.1 RI, this defaults to true.
     */
    private boolean isRequired(XmlElementRef ref) {
        if(!is2_2)  return true;

        try {
            return ref.required();
        } catch(LinkageError e) {
            is2_2 = false;
            return true;    // the value defaults to true
        }
    }

    /**
     * @return
     *      true if the reference yields at least one type
     */
    private boolean addGenericElement(XmlElementRef r) {
        String nsUri = getEffectiveNamespaceFor(r);
        // TODO: check spec. defaulting of localName.
        return addGenericElement(parent.owner.getElementInfo(parent.getClazz(),new QName(nsUri,r.name())));
    }

    private boolean addGenericElement(XmlElementRef r, ReferencePropertyInfoImpl<T,C,F,M> info) {
        String nsUri = info.getEffectiveNamespaceFor(r);
        ElementInfo ei = parent.owner.getElementInfo(info.parent.getClazz(), new QName(nsUri, r.name()));
        types.add(ei);
        return true;
    }

    private String getEffectiveNamespaceFor(XmlElementRef r) {
        String nsUri = r.namespace();

        XmlSchema xs = reader().getPackageAnnotation( XmlSchema.class, parent.getClazz(), this );
        if(xs!=null && xs.attributeFormDefault()== XmlNsForm.QUALIFIED) {
            // JAX-RPC doesn't want the default namespace URI swapping to take effect to
            // local "unqualified" elements. UGLY.
            if(nsUri.length()==0)
                nsUri = parent.builder.defaultNsUri;
        }

        return nsUri;
    }

    private boolean addGenericElement(ElementInfo<T,C> ei) {
        if(ei==null)
            return false;
        types.add(ei);
        for( ElementInfo<T,C> subst : ei.getSubstitutionMembers() )
            addGenericElement(subst);
        return true;
    }

    private boolean addAllSubtypes(T type) {
        Navigator<T,C,F,M> nav = nav();

        // this allows the explicitly referenced type to be sucked in to the model
        NonElement<T,C> t = parent.builder.getClassInfo(nav.asDecl(type),this);
        if(!(t instanceof ClassInfo))
            // this is leaf.
            return false;

        boolean result = false;

        ClassInfo<T,C> c = (ClassInfo<T,C>) t;
        if(c.isElement()) {
            types.add(c.asElement());
            result = true;
        }

        // look for other possible types
        for( ClassInfo<T,C> ci : parent.owner.beans().values() ) {
            if(ci.isElement() && nav.isSubClassOf(ci.getType(),type)) {
                types.add(ci.asElement());
                result = true;
            }
        }

        // don't allow local elements to substitute.
        for( ElementInfo<T,C> ei : parent.owner.getElementMappings(null).values()) {
            if(nav.isSubClassOf(ei.getType(),type)) {
                types.add(ei);
                result = true;
            }
        }

        return result;
    }


    @Override
    protected void link() {
        super.link();

        // until we get the whole thing into TypeInfoSet,
        // we never really know what are all the possible types that can be assigned on this field.
        // so recompute this value when we have all the information.
        calcTypes(true);

    }

    public final void addType(PropertyInfoImpl<T,C,F,M> info) {
        //noinspection unchecked
        subTypes.add((ReferencePropertyInfoImpl)info);
    }

    public final boolean isMixed() {
        return isMixed;
    }

    public final WildcardMode getWildcard() {
        return wildcard;
    }

    public final C getDOMHandler() {
        return domHandler;
    }
}
