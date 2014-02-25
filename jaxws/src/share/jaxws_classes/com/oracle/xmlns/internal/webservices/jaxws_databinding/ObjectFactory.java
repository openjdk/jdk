/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.xmlns.internal.webservices.jaxws_databinding;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each
 * Java content interface and Java element interface
 * generated in the com.sun.xml.internal.ws.ext2.java_wsdl package.
 * <p>An ObjectFactory allows you to programatically
 * construct new instances of the Java representation
 * for XML content. The Java representation of XML
 * content can consist of schema derived interfaces
 * and classes representing the binding of schema
 * type definitions, element declarations and model
 * groups.  Factory methods for each of these are
 * provided in this class.
 *
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _JavaWsdlMapping_QNAME = new QName("http://xmlns.oracle.com/webservices/jaxws-databinding", "java-wsdl-mapping");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.sun.xml.internal.ws.ext2.java_wsdl
     *
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link JavaMethod }
     *
     */
    public JavaMethod createJavaMethod() {
        return new JavaMethod();
    }

    /**
     * Create an instance of {@link JavaWsdlMappingType }
     *
     */
    public JavaWsdlMappingType createJavaWsdlMappingType() {
        return new JavaWsdlMappingType();
    }

    /**
     * Create an instance of {@link XmlWebEndpoint }
     *
     */
    public XmlWebEndpoint createWebEndpoint() {
        return new XmlWebEndpoint();
    }

    /**
     * Create an instance of {@link XmlMTOM }
     *
     */
    public XmlMTOM createMtom() {
        return new XmlMTOM();
    }

    /**
     * Create an instance of {@link XmlWebServiceClient }
     *
     */
    public XmlWebServiceClient createWebServiceClient() {
        return new XmlWebServiceClient();
    }

    /**
     * Create an instance of {@link XmlServiceMode }
     *
     */
    public XmlServiceMode createServiceMode() {
        return new XmlServiceMode();
    }

    /**
     * Create an instance of {@link XmlBindingType }
     *
     */
    public XmlBindingType createBindingType() {
        return new XmlBindingType();
    }

    /**
     * Create an instance of {@link XmlWebServiceRef }
     *
     */
    public XmlWebServiceRef createWebServiceRef() {
        return new XmlWebServiceRef();
    }

    /**
     * Create an instance of {@link JavaParam }
     *
     */
    public JavaParam createJavaParam() {
        return new JavaParam();
    }

    /**
     * Create an instance of {@link XmlWebParam }
     *
     */
    public XmlWebParam createWebParam() {
        return new XmlWebParam();
    }

    /**
     * Create an instance of {@link XmlWebMethod }
     *
     */
    public XmlWebMethod createWebMethod() {
        return new XmlWebMethod();
    }

    /**
     * Create an instance of {@link XmlWebResult }
     *
     */
    public XmlWebResult createWebResult() {
        return new XmlWebResult();
    }

    /**
     * Create an instance of {@link XmlOneway }
     *
     */
    public XmlOneway createOneway() {
        return new XmlOneway();
    }

    /**
     * Create an instance of {@link XmlSOAPBinding }
     *
     */
    public XmlSOAPBinding createSoapBinding() {
        return new XmlSOAPBinding();
    }

    /**
     * Create an instance of {@link XmlAction }
     *
     */
    public XmlAction createAction() {
        return new XmlAction();
    }

    /**
     * Create an instance of {@link XmlFaultAction }
     *
     */
    public XmlFaultAction createFaultAction() {
        return new XmlFaultAction();
    }

    /**
     * Create an instance of {@link JavaMethod.JavaParams }
     *
     */
    public JavaMethod.JavaParams createJavaMethodJavaParams() {
        return new JavaMethod.JavaParams();
    }

    /**
     * Create an instance of {@link XmlHandlerChain }
     *
     */
    public XmlHandlerChain createHandlerChain() {
        return new XmlHandlerChain();
    }

    /**
     * Create an instance of {@link XmlWebServiceProvider }
     *
     */
    public XmlWebServiceProvider createWebServiceProvider() {
        return new XmlWebServiceProvider();
    }

    /**
     * Create an instance of {@link XmlWebFault }
     *
     */
    public XmlWebFault createWebFault() {
        return new XmlWebFault();
    }

    /**
     * Create an instance of {@link XmlResponseWrapper }
     *
     */
    public XmlResponseWrapper createResponseWrapper() {
        return new XmlResponseWrapper();
    }

    /**
     * Create an instance of {@link XmlWebService }
     *
     */
    public XmlWebService createWebService() {
        return new XmlWebService();
    }

    /**
     * Create an instance of {@link XmlRequestWrapper }
     *
     */
    public XmlRequestWrapper createRequestWrapper() {
        return new XmlRequestWrapper();
    }

    /**
     * Create an instance of {@link JavaWsdlMappingType.XmlSchemaMapping }
     *
     */
    public JavaWsdlMappingType.XmlSchemaMapping createJavaWsdlMappingTypeXmlSchemaMapping() {
        return new JavaWsdlMappingType.XmlSchemaMapping();
    }

    /**
     * Create an instance of {@link JavaWsdlMappingType.JavaMethods }
     *
     */
    public JavaWsdlMappingType.JavaMethods createJavaWsdlMappingTypeJavaMethods() {
        return new JavaWsdlMappingType.JavaMethods();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link JavaWsdlMappingType }{@code >}}
     *
     */
    @XmlElementDecl(namespace = "http://xmlns.oracle.com/webservices/jaxws-databinding", name = "java-wsdl-mapping")
    public JAXBElement<JavaWsdlMappingType> createJavaWsdlMapping(JavaWsdlMappingType value) {
        return new JAXBElement<JavaWsdlMappingType>(_JavaWsdlMapping_QNAME, JavaWsdlMappingType.class, null, value);
    }

}
