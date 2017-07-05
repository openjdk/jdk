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

package com.sun.xml.internal.ws.api.databinding;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import com.sun.xml.internal.ws.api.message.MessageContextFactory;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.wsdl.DispatchException;

/**
 * {@code Databinding} is the entry point for all the WebService databinding
 * runtime functionality. Primarily, a Databinding is to serialize/deserialize an
 * XML(SOAP) message to/from a JAVA method invocation and return value which
 * are represented as <code>JavaCallInfo</code> instances.
 * <p>
 * </p>
 * Each Databinding is associated with a <code>MessageFactory</code> instance
 * which can be used to create <code>Message</code> instances that can be
 * deserialized by the Databinding. The <code>MessageFactory</code> also supports
 * the conversion of Oracle Fabric Normalized messages.
 * <p>
 * </p>
 * <blockquote> Following is an example that creates a {@code Databinding} which
 * provides the operations to serialize/deserialize a JavaCallInfo to/from a
 * SOAP message:<br />
 *
 * <pre>
 * DatabindingFactory wsfac = DatabindingFactory();
 * Databinding rt = wsfac.createDatabinding(DatabindingConfig);
 * </pre>
 *
 * </blockquote>
 *
 * @author shih-chang.chen@oracle.com
 */
public interface Databinding extends com.oracle.webservices.internal.api.databinding.Databinding {

        /**
         * Gets the MessageFactory instance associated with this WsRuntime
         *
         * @return the MessageFactory instance associated with this WsRuntime
         */
//      MessageFactory getMessageFactory();

        /**
         * Deserializes a request XML(SOAP) message to a JavaCallInfo instance
         * representing a JAVA method call.
         *
         * @param soap
         *            the request message
         *
         * @return the JavaCallInfo representing a method call
         */
//      JavaCallInfo deserializeRequest(Packet req);

        EndpointCallBridge getEndpointBridge(Packet soap) throws DispatchException;

        ClientCallBridge getClientBridge(Method method);

        /**
         * Serializes a JavaCallInfo instance representing a JAVA method call to a
         * request XML(SOAP) message.
         *
         * @param call
         *            the JavaCallInfo representing a method call
         *
         * @return the request XML(SOAP) message
         */
//      Packet serializeRequest(JavaCallInfo call);

        /**
         * Serializes a JavaCallInfo instance representing the return value or
         * exception of a JAVA method call to a response XML(SOAP) message.
         *
         * @param call
         *            the JavaCallInfo representing the return value or exception of
         *            a JAVA method call
         *
         * @return the response XML(SOAP) message
         */
//      Packet serializeResponse(JavaCallInfo call);

        /**
         * Deserializes a response XML(SOAP) message to a JavaCallInfo instance
         * representing the return value or exception of a JAVA method call.
         *
         * @param soap
         *            the response message
         *
         * @param call
         *            the JavaCallInfo instance to be updated
         *
         * @return the JavaCallInfo updated with the return value or exception of a
         *         JAVA method call
         */
//      JavaCallInfo deserializeResponse(Packet res, JavaCallInfo call);

        /**
         * Gets the WSDL operation metadata of the specified JAVA method.
         *
         * @param method
         *            the JAVA method
         * @return the operationMetadata
         */
//      OperationMetadata getOperationMetadata(java.lang.reflect.Method method);

        /**
         * Gets the WebServiceFeatures of this webservice endpoint.
         *
         * @return the features
         */
//      WebServiceFeature[] getFeatures();

        void generateWSDL(WSDLGenInfo info);

        /**
         * @deprecated use MessageContextFactory
         */
        public ContentType encode( Packet packet, OutputStream out ) throws IOException ;

    /**
     * @deprecated use MessageContextFactory
     */
        public void decode( InputStream in, String ct, Packet packet ) throws IOException;

        public MessageContextFactory getMessageContextFactory();
}
