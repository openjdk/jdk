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
package com.sun.xml.internal.bind.v2.model.core;

import java.util.Map;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.namespace.QName;
import javax.xml.transform.Result;

import com.sun.xml.internal.bind.v2.model.nav.Navigator;

/**
 * Root of models.&nbsp;Set of {@link TypeInfo}s.
 *
 * @author Kohsuke Kawaguchi
 */
public interface TypeInfoSet<T,C,F,M> {

    /**
     * {@link Navigator} for this model.
     */
    Navigator<T,C,F,M> getNavigator();

//  turns out we can't have AnnotationReader in XJC, so it's impossible to have this here.
//  perhaps we should revisit this in the future.
//    /**
//     * {@link AnnotationReader} for this model.
//     */
//    AnnotationReader<T,C,F,M> getReader();

    /**
     * Returns a {@link TypeInfo} for the given type.
     *
     * @return
     *      null if the specified type cannot be bound by JAXB, or
     *      not known to this set.
     */
    NonElement<T,C> getTypeInfo( T type );

    /**
     * Gets the {@link TypeInfo} for the any type.
     */
    NonElement<T,C> getAnyTypeInfo();

    /**
     * Returns a {@link ClassInfo}, {@link ArrayInfo}, or {@link LeafInfo}
     * for the given bean.
     *
     * <p>
     * This method is almost like refinement of {@link #getTypeInfo(Object)} except
     * our C cannot derive from T.
     *
     * @return
     *      null if the specified type is not bound by JAXB or otherwise
     *      unknown to this set.
     */
    NonElement<T,C> getClassInfo( C type );

    /**
     * Returns all the {@link ArrayInfo}s known to this set.
     */
    Map<? extends T,? extends ArrayInfo<T,C>> arrays();

    /**
     * Returns all the {@link ClassInfo}s known to this set.
     */
    Map<C,? extends ClassInfo<T,C>> beans();

    /**
     * Returns all the {@link BuiltinLeafInfo}s known to this set.
     */
    Map<T,? extends BuiltinLeafInfo<T,C>> builtins();

    /**
     * Returns all the {@link EnumLeafInfo}s known to this set.
     */
    Map<C,? extends EnumLeafInfo<T,C>> enums();

    /**
     * Returns a {@link ElementInfo} for the given element.
     *
     * @param scope
     *      if null, return the info about a global element.
     *      Otherwise return a local element in the given scope if available,
     *      then look for a global element next.
     */
    ElementInfo<T,C> getElementInfo( C scope, QName name );

    /**
     * Returns a type information for the given reference.
     */
    NonElement<T,C> getTypeInfo(Ref<T,C> ref);

    /**
     * Returns all  {@link ElementInfo}s in the given scope.
     *
     * @param scope
     *      if non-null, this method only returns the local element mapping.
     */
    Map<QName,? extends ElementInfo<T,C>> getElementMappings( C scope );

    /**
     * Returns all the {@link ElementInfo} known to this set.
     */
    Iterable<? extends ElementInfo<T,C>> getAllElements();


    /**
     * Gets all {@link XmlSchema#xmlns()} found in this context for the given namespace URI.
     *
     * <p>
     * This operation is expected to be only used in schema generator, so it can be slow.
     *
     * @return
     *      A map from prefixes to namespace URIs, which should be declared when generating a schema.
     *      Could be empty but never null.
     */
    Map<String,String> getXmlNs(String namespaceUri);

    /**
     * Gets {@link XmlSchema#location()} found in this context.
     *
     * <p>
     * This operation is expected to be only used in schema generator, so it can be slow.
     *
     * @return
     *      A map from namespace URI to the value of the location.
     *      If the entry is missing, that means a schema should be generated for that namespace.
     *      If the value is "", that means the schema location is implied
     *      (&lt;xs:schema namespace="..."/> w/o schemaLocation.)
     */
    Map<String,String> getSchemaLocations();

    /**
     * Gets the reasonable {@link XmlNsForm} for the given namespace URI.
     *
     * <p>
     * The spec doesn't define very precisely what the {@link XmlNsForm} value
     * for the given namespace would be, so this method is implemented in rather
     * ad-hoc way. It should work as what most people expect for simple cases.
     *
     * @return never null.
     */
    XmlNsForm getElementFormDefault(String nsUri);

    /**
     * Gets the reasonable {@link XmlNsForm} for the given namespace URI.
     *
     * <p>
     * The spec doesn't define very precisely what the {@link XmlNsForm} value
     * for the given namespace would be, so this method is implemented in rather
     * ad-hoc way. It should work as what most people expect for simple cases.
     *
     * @return never null.
     */
    XmlNsForm getAttributeFormDefault(String nsUri);

    /**
     * Dumps this model into XML.
     *
     * For debug only.
     *
     * TODO: not sure if this actually works. We don't really know what are T,C.
     */
    public void dump( Result out ) throws JAXBException;
}
