/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
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

package com.sun.xml.internal.org.jvnet.ws.databinding;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceFeature;

import com.sun.xml.internal.org.jvnet.ws.message.MessageContext;
import org.xml.sax.EntityResolver;

/**
 * {@code Databinding} is the entry point for all the WebService Databinding
 * functionality. Primarily, a Databinding is to serialize/deserialize an
 * XML(SOAP) message to/from a JAVA method invocation and return which are
 * represented as <code>JavaCallInfo</code> instances. A WSDLGenerator can
 * be created from a Databinding object to genreate WSDL representation of
 * a JAVA service endpoint interface.
 * <p>
 * </p>
 * The supported databinding modes(flavors) are:
 * <ul>
 * <li>"toplink.jaxb"</li>
 * <li>"glassfish.jaxb"</li>
 * </ul>
 * <blockquote> Following is an example that creates a {@code Databinding} which
 * provides the operations to serialize/deserialize a JavaCallInfo to/from a
 * SOAP message:<br/>
 *
 * <pre>
 * DatabindingFactory factory = DatabindingFactory.newInstance();
 * Databinding.Builder builder = factory.createBuilder(seiClass, endpointClass);
 * Databinding databinding = builder.build();
 * </pre>
 *
 * </blockquote>
 *
 * @see com.sun.xml.internal.org.jvnet.ws.databinding.DatabindingFactory
 *
 * @author shih-chang.chen@oracle.com
 */
public interface Databinding {

        /**
         * Creates a new instance of a <code>JavaCallInfo</code>.
         *
     * @param method The JAVA method
     * @param args The parameter objects
         *
         * @return New instance of a <code>JavaCallInfo</code>
         */
        JavaCallInfo createJavaCallInfo(Method method, Object[] args);

        /**
         * Serializes a JavaCallInfo instance representing a JAVA method call to a
         * request XML(SOAP) message.
         *
         * @param call The JavaCallInfo representing a method call
         *
         * @return The request XML(SOAP) message
         */
        MessageContext serializeRequest(JavaCallInfo call);

        /**
         * Deserializes a response XML(SOAP) message to a JavaCallInfo instance
         * representing the return value or exception of a JAVA method call.
         *
         * @param soap The response message
         * @param call The JavaCallInfo instance to be updated
         *
         * @return The JavaCallInfo updated with the return value or exception of a
         *         JAVA method call
         */
        JavaCallInfo deserializeResponse(MessageContext message, JavaCallInfo call);

        /**
         * Deserializes a request XML(SOAP) message to a JavaCallInfo instance
         * representing a JAVA method call.
         *
         * @param message The request message
         *
         * @return The JavaCallInfo representing a method call
         */
        JavaCallInfo deserializeRequest(MessageContext message);

        /**
         * Serializes a JavaCallInfo instance representing the return value or
         * exception of a JAVA method call to a response XML(SOAP) message.
         *
         * @param call The JavaCallInfo representing the return value or exception
         *             of a JAVA method call
         *
         * @return The response XML(SOAP) message
         */
        MessageContext serializeResponse(JavaCallInfo call);

        /**
         * {@code Databinding.Builder}, created from the DatabindingFactory, is used to
         * configure how a Databinding instance is to be built from this builder.
         *
     * @see com.sun.xml.internal.org.jvnet.ws.databinding.DatabindingFactory
         * @author shih-chang.chen@oracle.com
         */
        public interface Builder {

                /**
                 * Sets the targetNamespace of the WSDL
                 *
                 * @param targetNamespace The targetNamespace to set
                 *
         * @return this Builder instance
                 */
                Builder targetNamespace(String targetNamespace);

                /**
                 * Sets the service name of the WSDL
                 *
                 * @param serviceName The serviceName to set
                 *
         * @return this Builder instance
                 */
                Builder serviceName(QName serviceName);

                /**
                 * Sets the port name of the WSDL
                 *
                 * @param portName The portName to set
                 *
         * @return this Builder instance
                 */
                Builder portName(QName portName);

                /**
                 * Sets the WSDL URL where the WSDL can be read from
                 *
                 * @param wsdlURL The wsdlURL to set
                 *
         * @return this Builder instance
                 */
                Builder wsdlURL(URL wsdlURL);

                /**
                 * Sets the WSDL Source where the WSDL can be read from
                 *
                 * @param wsdlSource The wsdlSource to set
                 *
         * @return this Builder instance
                 */
                Builder wsdlSource(Source wsdlSource);

                /**
                 * Sets the EntityResolver for reading the WSDL
                 *
                 * @param wsdlURL The wsdlURL to set
                 *
         * @return this Builder instance
                 */
                Builder entityResolver(EntityResolver entityResolver);

                /**
                 * Sets the ClassLoader which is used to load the service endpoint
                 * interface, implementation bean, and all the value types. If this
                 * value is not set, the default it uses contractClass.getClassLoader().
                 *
                 * @param classLoader The classLoader to set
                 *
         * @return this Builder instance
                 */
                Builder classLoader(ClassLoader classLoader);

                /**
                 * Sets A list of WebServiceFeatures
                 *
                 * @param features The list of WebServiceFeatures
                 *
         * @return this Builder instance
                 */
                Builder feature(WebServiceFeature... features);

                /**
                 * Sets A property of the Databinding object to be created
                 *
                 * @param name The name of the property
                 * @param value The value of the property
                 *
         * @return this Builder instance
                 */
                Builder property(String name, Object value);

                /**
                 * Builds a new Databinding instance
                 *
         * @return The Builder instance
                 */
                Databinding build();

            /**
             * Creates the WSDLGenerator which can be used to generate the WSDL
             * representation of the service endpoint interface of this Databinding
             * object.
             *
             * @return WSDLGenerator The WSDLGenerator
             */
            WSDLGenerator createWSDLGenerator();
        }

        /**
         * WSDLGenerator is used to generate the WSDL representation of the service
         * endpoint interface of the parent Databinding object.
         */
        public interface WSDLGenerator {

                /**
                 * Sets the inlineSchema boolean. When the inlineSchema is true, the
                 * generated schema documents are embedded within the type element of
                 * the generated WSDL. When the inlineSchema is false, the generated
                 * schema documents are generated as standalone schema documents and
                 * imported into the generated WSDL.
                 *
                 * @param inline the inlineSchema boolean.
                 * @return
                 */
                WSDLGenerator inlineSchema(boolean inline);

                /**
                 * Sets A property of the WSDLGenerator
                 *
                 * @param name The name of the property
                 * @param value The value of the property
                 *
         * @return this WSDLGenerator instance
                 */
                WSDLGenerator property(String name, Object value);

                /**
                 * Generates the WSDL using the wsdlResolver to output the generated
                 * documents.
                 *
                 * @param wsdlResolver The WSDLResolver
                 */
                void generate(WSDLResolver wsdlResolver);

                /**
                 * Generates the WSDL into the file directory
                 *
                 * @param outputDir The output file directory
                 * @param name The file name of the main WSDL document
                 */
                void generate(File outputDir, String name);

                /**
                 * WSDLResolver is used by WSDLGenerator while generating WSDL and its associated
                 * documents. It is used to control what documents need to be generated and what
                 * documents need to be picked from metadata. If endpont's document metadata
                 * already contains some documents, their systemids may be used for wsdl:import,
                 * and schema:import. The suggested filenames are relative urls(for e.g: EchoSchema1.xsd)
                 * The Result object systemids are also relative urls(for e.g: AbsWsdl.wsdl).
                 *
                 * @author Jitendra Kotamraju
                 */
                public interface WSDLResolver {
                    /**
                     * Create a Result object into which concrete WSDL is to be generated.
                     *
                     * @return Result for the concrete WSDL
                     */
                    public Result getWSDL(String suggestedFilename);

                    /**
                     * Create a Result object into which abstract WSDL is to be generated. If the the
                     * abstract WSDL is already in metadata, it is not generated.
                     *
                     * Update filename if the suggested filename need to be changed in wsdl:import.
                     * This needs to be done if the metadata contains abstract WSDL, and that systemid
                     * needs to be reflected in concrete WSDL's wsdl:import
                     *
                     * @return null if abstract WSDL need not be generated
                     */
                    public Result getAbstractWSDL(Holder<String> filename);

                    /**
                     * Create a Result object into which schema doc is to be generated. Typically if
                     * there is a schema doc for namespace in metadata, then it is not generated.
                     *
                     * Update filename if the suggested filename need to be changed in xsd:import. This
                     * needs to be done if the metadata contains the document, and that systemid
                     * needs to be reflected in some other document's xsd:import
                     *
                     * @return null if schema need not be generated
                     */
                    public Result getSchemaOutput(String namespace, Holder<String> filename);

                }
        }
}
