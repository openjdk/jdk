/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
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
