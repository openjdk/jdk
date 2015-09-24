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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;
import javax.xml.transform.Result;

import com.sun.xml.internal.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.internal.bind.v2.model.core.BuiltinLeafInfo;
import com.sun.xml.internal.bind.v2.model.core.ClassInfo;
import com.sun.xml.internal.bind.v2.model.core.LeafInfo;
import com.sun.xml.internal.bind.v2.model.core.NonElement;
import com.sun.xml.internal.bind.v2.model.core.Ref;
import com.sun.xml.internal.bind.v2.model.core.TypeInfo;
import com.sun.xml.internal.bind.v2.model.core.TypeInfoSet;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;
import com.sun.xml.internal.bind.v2.runtime.RuntimeUtil;
import com.sun.xml.internal.bind.v2.util.FlattenIterator;

/**
 * Set of {@link TypeInfo}s.
 *
 * <p>
 * This contains a fixed set of {@link LeafInfo}s and arbitrary set of {@link ClassInfo}s.
 *
 * <p>
 * Members are annotated with JAXB annotations so that we can dump it easily.
 *
 * @author Kohsuke Kawaguchi
 */
class TypeInfoSetImpl<T,C,F,M> implements TypeInfoSet<T,C,F,M> {

    @XmlTransient
    public final Navigator<T,C,F,M> nav;

    @XmlTransient
    public final AnnotationReader<T,C,F,M> reader;

    /**
     * All the leaves.
     */
    private final Map<T,BuiltinLeafInfo<T,C>> builtins =
            new LinkedHashMap<T,BuiltinLeafInfo<T,C>>();

    /** All {@link EnumLeafInfoImpl}s. */
    private final Map<C,EnumLeafInfoImpl<T,C,F,M>> enums =
            new LinkedHashMap<C,EnumLeafInfoImpl<T,C,F,M>>();

    /** All {@link ArrayInfoImpl}s. */
    private final Map<T,ArrayInfoImpl<T,C,F,M>> arrays =
            new LinkedHashMap<T,ArrayInfoImpl<T,C,F,M>>();

    /**
     * All the user-defined classes.
     *
     * Using {@link LinkedHashMap} allows us to process classes
     * in the order they are given to us. When the user incorrectly
     * puts an unexpected class into a reference graph, this causes
     * an error to be reported on a class closer to the user's code.
     */
    @XmlJavaTypeAdapter(RuntimeUtil.ToStringAdapter.class)
    private final Map<C,ClassInfoImpl<T,C,F,M>> beans
            = new LinkedHashMap<C,ClassInfoImpl<T,C,F,M>>();

    @XmlTransient
    private final Map<C,ClassInfoImpl<T,C,F,M>> beansView =
        Collections.unmodifiableMap(beans);

    /**
     * The element mapping.
     */
    private final Map<C,Map<QName,ElementInfoImpl<T,C,F,M>>> elementMappings =
        new LinkedHashMap<C,Map<QName,ElementInfoImpl<T,C,F,M>>>();

    private final Iterable<? extends ElementInfoImpl<T,C,F,M>> allElements =
        new Iterable<ElementInfoImpl<T,C,F,M>>() {
            public Iterator<ElementInfoImpl<T,C,F,M>> iterator() {
                return new FlattenIterator<ElementInfoImpl<T,C,F,M>>(elementMappings.values());
            }
        };

    /**
     * {@link TypeInfo} for {@code xs:anyType}.
     *
     * anyType is the only {@link TypeInfo} that works with an interface,
     * and accordingly it requires a lot of special casing.
     */
    private final NonElement<T,C> anyType;

    /**
     * Lazily parsed set of {@link XmlNs}s.
     *
     * @see #getXmlNs(String)
     */
    private Map<String,Map<String,String>> xmlNsCache;

    public TypeInfoSetImpl(Navigator<T,C,F,M> nav,
                           AnnotationReader<T,C,F,M> reader,
                           Map<T,? extends BuiltinLeafInfoImpl<T,C>> leaves) {
        this.nav = nav;
        this.reader = reader;
        this.builtins.putAll(leaves);

        this.anyType = createAnyType();

        // register primitive types.
        for (Map.Entry<Class, Class> e : RuntimeUtil.primitiveToBox.entrySet()) {
            this.builtins.put( nav.getPrimitive(e.getKey()), leaves.get(nav.ref(e.getValue())) );
        }

        // make sure at lease we got a map for global ones.
        elementMappings.put(null,new LinkedHashMap<QName,ElementInfoImpl<T,C,F,M>>());
    }

    protected NonElement<T,C> createAnyType() {
        return new AnyTypeImpl<T,C>(nav);
    }

    public Navigator<T,C,F,M> getNavigator() {
        return nav;
    }

    /**
     * Adds a new {@link ClassInfo} to the set.
     */
    public void add( ClassInfoImpl<T,C,F,M> ci ) {
        beans.put( ci.getClazz(), ci );
    }

    /**
     * Adds a new {@link LeafInfo} to the set.
     */
    public void add( EnumLeafInfoImpl<T,C,F,M> li ) {
        enums.put( li.clazz,  li );
    }

    public void add(ArrayInfoImpl<T, C, F, M> ai) {
        arrays.put( ai.getType(), ai );
    }

    /**
     * Returns a {@link TypeInfo} for the given type.
     *
     * @return
     *      null if the specified type cannot be bound by JAXB, or
     *      not known to this set.
     */
    public NonElement<T,C> getTypeInfo( T type ) {
        type = nav.erasure(type);   // replace type variables by their bounds

        LeafInfo<T,C> l = builtins.get(type);
        if(l!=null)     return l;

        if( nav.isArray(type) ) {
            return arrays.get(type);
        }

        C d = nav.asDecl(type);
        if(d==null)     return null;
        return getClassInfo(d);
    }

    public NonElement<T,C> getAnyTypeInfo() {
        return anyType;
    }

    /**
     * This method is used to add a root reference to a model.
     */
    public NonElement<T,C> getTypeInfo(Ref<T,C> ref) {
        // TODO: handle XmlValueList
        assert !ref.valueList;
        C c = nav.asDecl(ref.type);
        if(c!=null && reader.getClassAnnotation(XmlRegistry.class,c,null/*TODO: is this right?*/)!=null) {
            return null;    // TODO: is this correct?
        } else
            return getTypeInfo(ref.type);
    }

    /**
     * Returns all the {@link ClassInfo}s known to this set.
     */
    public Map<C,? extends ClassInfoImpl<T,C,F,M>> beans() {
        return beansView;
    }

    public Map<T, ? extends BuiltinLeafInfo<T,C>> builtins() {
        return builtins;
    }

    public Map<C, ? extends EnumLeafInfoImpl<T,C,F,M>> enums() {
        return enums;
    }

    public Map<? extends T, ? extends ArrayInfoImpl<T,C,F,M>> arrays() {
        return arrays;
    }

    /**
     * Returns a {@link ClassInfo} for the given bean.
     *
     * <p>
     * This method is almost like refinement of {@link #getTypeInfo(Object)} except
     * our C cannot derive from T.
     *
     * @return
     *      null if the specified type is not bound by JAXB or otherwise
     *      unknown to this set.
     */
    public NonElement<T,C> getClassInfo( C type ) {
        LeafInfo<T,C> l = builtins.get(nav.use(type));
        if(l!=null)     return l;

        l = enums.get(type);
        if(l!=null)     return l;

        if(nav.asDecl(Object.class).equals(type))
            return anyType;

        return beans.get(type);
    }

    public ElementInfoImpl<T,C,F,M> getElementInfo( C scope, QName name ) {
        while(scope!=null) {
            Map<QName,ElementInfoImpl<T,C,F,M>> m = elementMappings.get(scope);
            if(m!=null) {
                ElementInfoImpl<T,C,F,M> r = m.get(name);
                if(r!=null)     return r;
            }
            scope = nav.getSuperClass(scope);
        }
        return elementMappings.get(null).get(name);
    }

    /**
     * @param builder
     *      used for reporting errors.
     */
    public final void add( ElementInfoImpl<T,C,F,M> ei, ModelBuilder<T,C,F,M> builder ) {
        C scope = null;
        if(ei.getScope()!=null)
            scope = ei.getScope().getClazz();

        Map<QName,ElementInfoImpl<T,C,F,M>> m = elementMappings.get(scope);
        if(m==null)
            elementMappings.put(scope,m=new LinkedHashMap<QName,ElementInfoImpl<T,C,F,M>>());

        ElementInfoImpl<T,C,F,M> existing = m.put(ei.getElementName(),ei);

        if(existing!=null) {
            QName en = ei.getElementName();
            builder.reportError(
                new IllegalAnnotationException(
                    Messages.CONFLICTING_XML_ELEMENT_MAPPING.format(en.getNamespaceURI(),en.getLocalPart()),
                    ei, existing ));
        }
    }

    public Map<QName,? extends ElementInfoImpl<T,C,F,M>> getElementMappings( C scope ) {
        return elementMappings.get(scope);
    }

    public Iterable<? extends ElementInfoImpl<T,C,F,M>> getAllElements() {
        return allElements;
    }

    public Map<String,String> getXmlNs(String namespaceUri) {
        if(xmlNsCache==null) {
            xmlNsCache = new HashMap<String,Map<String,String>>();

            for (ClassInfoImpl<T, C, F, M> ci : beans().values()) {
                XmlSchema xs = reader.getPackageAnnotation( XmlSchema.class, ci.getClazz(), null );
                if(xs==null)
                    continue;

                String uri = xs.namespace();
                Map<String,String> m = xmlNsCache.get(uri);
                if(m==null)
                    xmlNsCache.put(uri,m=new HashMap<String, String>());

                for( XmlNs xns : xs.xmlns() ) {
                    m.put(xns.prefix(),xns.namespaceURI());
                }
            }
        }

        Map<String,String> r = xmlNsCache.get(namespaceUri);
        if(r!=null)     return r;
        else            return Collections.emptyMap();
    }

    public Map<String,String> getSchemaLocations() {
        Map<String, String> r = new HashMap<String,String>();
        for (ClassInfoImpl<T, C, F, M> ci : beans().values()) {
            XmlSchema xs = reader.getPackageAnnotation( XmlSchema.class, ci.getClazz(), null );
            if(xs==null)
                continue;

            String loc = xs.location();
            if(loc.equals(XmlSchema.NO_LOCATION))
                continue;   // unspecified

            r.put(xs.namespace(),loc);
        }
        return r;
    }

    public final XmlNsForm getElementFormDefault(String nsUri) {
        for (ClassInfoImpl<T, C, F, M> ci : beans().values()) {
            XmlSchema xs = reader.getPackageAnnotation( XmlSchema.class, ci.getClazz(), null );
            if(xs==null)
                continue;

            if(!xs.namespace().equals(nsUri))
                continue;

            XmlNsForm xnf = xs.elementFormDefault();
            if(xnf!=XmlNsForm.UNSET)
                return xnf;
        }
        return XmlNsForm.UNSET;
    }

    public final XmlNsForm getAttributeFormDefault(String nsUri) {
        for (ClassInfoImpl<T,C,F,M> ci : beans().values()) {
            XmlSchema xs = reader.getPackageAnnotation( XmlSchema.class, ci.getClazz(), null );
            if(xs==null)
                continue;

            if(!xs.namespace().equals(nsUri))
                continue;

            XmlNsForm xnf = xs.attributeFormDefault();
            if(xnf!=XmlNsForm.UNSET)
                return xnf;
        }
        return XmlNsForm.UNSET;
    }

    /**
     * Dumps this model into XML.
     *
     * For debug only.
     *
     * TODO: not sure if this actually works. We don't really know what are T,C.
     */
    public void dump( Result out ) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(this.getClass());
        Marshaller m = context.createMarshaller();
        m.marshal(this,out);
    }
}
