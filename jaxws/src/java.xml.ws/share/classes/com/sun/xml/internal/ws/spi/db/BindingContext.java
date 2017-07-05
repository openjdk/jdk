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

package com.sun.xml.internal.ws.spi.db;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttachmentRef;
import javax.xml.namespace.QName;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

/**
 * {@link JAXBContext} enhanced with JAXB RI specific functionalities.
 *
 * <p>
 * <b>Subject to change without notice</b>.
 *
 * @since 2.0 EA1
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 *
 * @author shih-chang.chen@oracle.com
 */
public interface BindingContext {

        //following are found in JAXBContext used by jaxws
        abstract public Marshaller createMarshaller() throws JAXBException;
        abstract public Unmarshaller createUnmarshaller() throws JAXBException;
        abstract public JAXBContext getJAXBContext();
    abstract public Object newWrapperInstace(Class<?> wrapperType)
            throws InstantiationException, IllegalAccessException;





    /**
     * Returns true if this context includes a class
     * that has {@link XmlAttachmentRef}.
     *
     * @since 2.1
     */
    public abstract boolean hasSwaRef();

    /**
     * If the given object is bound to an element in XML by JAXB,
     * returns the element name.
     *
     * @return null
     *      if the object is not bound to an element.
     * @throws JAXBException
     *      if the object is not known to this context.
     *
     * @since 2.0 EA1
     */
    public abstract @Nullable QName getElementName(@NotNull Object o) throws JAXBException;

    /**
     * Allows to retrieve the element name based on Class.
     * @param o
     * @return
     * @throws javax.xml.bind.JAXBException
     * @since 2.1.10
     */
    public abstract @Nullable QName getElementName(@NotNull Class o) throws JAXBException;

    /**
     * Creates a mini-marshaller/unmarshaller that can process a {@link TypeInfo}.
     *
     * @return
     *      null if the specified reference is not given to {@link BindingContext#newWrapperInstace(Class)}.
     *
     * @since 2.0 EA1
     */
    public abstract XMLBridge createBridge(@NotNull TypeInfo ref);
    public abstract XMLBridge createFragmentBridge();

    /**
     * Creates a new {@link BridgeContext} instance.
     *
     * @return
     *      always a valid non-null instance.
     *
     * @since 2.0 EA1
     */
//    public abstract @NotNull BridgeContext createBridgeContext();

    /**
     * Gets a {@link PropertyAccessor} for the specified element property of the specified wrapper bean class.
     *
     * <p>
     * This method is designed to assist the JAX-RPC RI fill in a wrapper bean (in the doc/lit/wrap mode.)
     * In the said mode, a wrapper bean is supposed to only have properties that match elements,
     * and for each element that appear in the content model there's one property.
     *
     * <p>
     * Therefore, this method takes a wrapper bean and a tag name that identifies a property
     * on the given wrapper bean, then returns a {@link PropertyAccessor} that allows the caller
     * to set/get a value from the property of the bean.
     *
     * <p>
     * This method is not designed for a performance. The caller is expected to cache the result.
     *
     * @param <B>
     *      type of the wrapper bean
     * @param <V>
     *      type of the property of the bean
     * @return
     *      always return non-null valid accessor object.
     * @throws JAXBException
     *      if the specified wrapper bean is not bound by JAXB, or if it doesn't have an element property
     *      of the given name.
     *
     * @since 2.0 EA1
     */
    public abstract <B,V> PropertyAccessor<B,V> getElementPropertyAccessor( Class<B> wrapperBean, String nsUri, String localName )
            throws JAXBException;

    /**
     * Gets the namespace URIs statically known to this {@link JAXBContext}.
     *
     * <p>
     * When JAXB is used to marshal into sub-trees, it declares
     * these namespace URIs at each top-level element that it marshals.
     *
     * To avoid repeated namespace declarations at sub-elements, the application
     * may declare those namespaces at a higher level.
     *
     * @return
     *      always non-null.
     *
     * @since 2.0 EA2
     */
    public abstract @NotNull List<String> getKnownNamespaceURIs();


    /**
     * Generates the schema documents from the model.
     *
     * <p>
     * The caller can use the additionalElementDecls parameter to
     * add element declarations to the generate schema.
     * For example, if the JAX-RPC passes in the following entry:
     *
     * {foo}bar -> DeclaredType for java.lang.String
     *
     * then JAXB generates the following element declaration (in the schema
     * document for the namespace "foo")"
     *
     * &lt;xs:element name="bar" type="xs:string" />
     *
     * This can be used for generating schema components necessary for WSDL.
     *
     * @param outputResolver
     *      this object controls the output to which schemas
     *      will be sent.
     *
     * @throws IOException
     *      if {@link SchemaOutputResolver} throws an {@link IOException}.
     */
    public abstract void generateSchema(@NotNull SchemaOutputResolver outputResolver) throws IOException;

    /**
     * Returns the name of the XML Type bound to the
     * specified Java type.
     *
     * @param tr
     *      must not be null. This must be one of the {@link TypeInfo}s specified
     *      in the {@link BindingContext#newInstance} method.
     *
     * @throws IllegalArgumentException
     *      if the parameter is null or not a part of the {@link TypeInfo}s specified
     *      in the {@link BindingContext#newWrapperInstace(Class)} method.
     *
     * @return null
     *      if the referenced type is an anonymous and therefore doesn't have a name.
     */
    public abstract QName getTypeName(@NotNull TypeInfo tr);

    /**
     * Gets the build information of the JAXB runtime.
     *
     * @return
     *      may be null, if the runtime is loaded by a class loader that doesn't support
     *      the access to the manifest informatino.
     */
    public abstract @NotNull String getBuildId();

    /**
     * The property that you can specify to {@link JAXBContext#newInstance}
     * to reassign the default namespace URI to something else at the runtime.
     *
     * <p>
     * The value of the property is {@link String}, and it is used as the namespace URI
     * that succeeds the default namespace URI.
     *
     * @since 2.0 EA1
     */
    public static final String DEFAULT_NAMESPACE_REMAP = "com.sun.xml.internal.bind.defaultNamespaceRemap";

    /**
     * The property that you can specify to {@link JAXBContext#newInstance}
     * to put additional JAXB type references into the {@link JAXBContext}.
     *
     * <p>
     * The value of the property is {@link Collection}&lt;{@link TypeInfo}>.
     * Those {@link TypeInfo}s can then be used to create {@link XMLBridge}s.
     *
     * <p>
     * This mechanism allows additional element declarations that were not a part of
     * the schema into the created {@link JAXBContext}.
     *
     * @since 2.0 EA1
     */
    public static final String TYPE_REFERENCES = "com.sun.xml.internal.bind.typeReferences";

    /**
     * The property that you can specify to {@link JAXBContext#newInstance}
     * and {@link Marshaller#setProperty(String, Object)}
     * to enable the c14n marshalling support in the {@link JAXBContext}.
     *
     * @since 2.0 EA2
     */
    public static final String CANONICALIZATION_SUPPORT = "com.sun.xml.internal.bind.c14n";

    /**
     * The property that you can specify to {@link JAXBContext#newInstance}
     * to allow unmarshaller to honor {@code xsi:nil} anywhere, even if they are
     * not specifically allowed by the schema.
     *
     * @since 2.1.3
     */
    public static final String TREAT_EVERYTHING_NILLABLE = "com.sun.xml.internal.bind.treatEverythingNillable";

    /**
     * The property that you can specify to {@link JAXBContext#newInstance}
     * to use alternative {@link RuntimeAnnotationReader} implementation.
     *
     * @since 2.1 EA2
     */
//    public static final String ANNOTATION_READER = RuntimeAnnotationReader.class.getName();

    /**
     * Marshaller/Unmarshaller property to enable XOP processing.
     *
     * @since 2.0 EA2
     */
    public static final String ENABLE_XOP = "com.sun.xml.internal.bind.XOP";

    /**
     * The property that you can specify to {@link JAXBContext#newInstance}
     * to specify specific classes that replace the reference to generic classes.
     *
     * <p>
     * See the release notes for more details about this feature.
     *
     * @since 2.1 EA2
     */
    public static final String SUBCLASS_REPLACEMENTS = "com.sun.xml.internal.bind.subclassReplacements";

    /**
     * The property that you can specify to {@link JAXBContext#newInstance}
     * enable support of XmlAccessorFactory annotation in the {@link JAXBContext}.
     *
     * @since 2.1 EA2
     */
    public static final String XMLACCESSORFACTORY_SUPPORT = "com.sun.xml.internal.bind.XmlAccessorFactory";

    /**
     * Retains references to PropertyInfos.
     *
     * @since 2.1.10
     */
    public static final String RETAIN_REFERENCE_TO_INFO = "retainReferenceToInfo";

}
