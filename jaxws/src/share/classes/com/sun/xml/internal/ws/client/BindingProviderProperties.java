/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.client;

import com.sun.xml.internal.ws.developer.JAXWSProperties;

public interface BindingProviderProperties extends JAXWSProperties{

    //legacy properties
    public static final String SERVICEIMPL_NAME = "serviceImplementationName";
    public static final String HOSTNAME_VERIFICATION_PROPERTY =
        "com.sun.xml.internal.ws.client.http.HostnameVerificationProperty";
    public static final String HTTP_COOKIE_JAR =
        "com.sun.xml.internal.ws.client.http.CookieJar";
    public static final String SECURITY_CONTEXT =
        "com.sun.xml.internal.ws.security.context";
    public static final String HTTP_STATUS_CODE =
        "com.sun.xml.internal.ws.client.http.HTTPStatusCode";

    public static final String REDIRECT_REQUEST_PROPERTY =
        "com.sun.xml.internal.ws.client.http.RedirectRequestProperty";
    public static final String SET_ATTACHMENT_PROPERTY =
        "com.sun.xml.internal.ws.attachment.SetAttachmentContext";
    public static final String GET_ATTACHMENT_PROPERTY =
        "com.sun.xml.internal.ws.attachment.GetAttachmentContext";
    public static final String ONE_WAY_OPERATION =
        "com.sun.xml.internal.ws.server.OneWayOperation";


    // Proprietary
    public static final String REQUEST_TIMEOUT =
        "com.sun.xml.internal.ws.request.timeout";

    //JAXWS 2.0
    public static final String JAXWS_RUNTIME_CONTEXT =
        "com.sun.xml.internal.ws.runtime.context";
    public static final String JAXWS_CONTEXT_PROPERTY =
        "com.sun.xml.internal.ws.context.request";
    public static final String JAXWS_HANDLER_CONTEXT_PROPERTY =
        "com.sun.xml.internal.ws.handler.context";
    public static final String JAXWS_RESPONSE_CONTEXT_PROPERTY =
        "com.sun.xml.internal.ws.context.response";
    public static final String JAXWS_CLIENT_ASYNC_HANDLER =
        "com.sun.xml.internal.ws.client.dispatch.asynchandler";
    public static final String JAXWS_CLIENT_ASYNC_RESPONSE_CONTEXT =
        "com.sun.xml.internal.ws.client.dispatch.async.response.context";
    public static final String JAXWS_CLIENT_HANDLE_PROPERTY =
        "com.sun.xml.internal.ws.client.handle";
    public static final String JAXB_CONTEXT_PROPERTY =
        "com.sun.xml.internal.ws.jaxbcontext";

    public static final String CLIENT_TRANSPORT_FACTORY =
        "com.sun.xml.internal.ws.client.ClientTransportFactory";

    public static final String JAXB_OUTPUTSTREAM =
        "com.sun.xml.internal.bind.api.Bridge.outputStream";

    public static final String XML_ENCODING_VALUE = "xml.encoding";                 // deprecated
    public static final String ACCEPT_ENCODING_PROPERTY = "accept.encoding";

    public static final String CONTENT_TYPE_PROPERTY = "Content-Type";
    public static final String SOAP_ACTION_PROPERTY = "SOAPAction";
    public static final String ACCEPT_PROPERTY = "Accept";

    // FI + SOAP 1.1
    public static final String FAST_INFOSET_TYPE_SOAP11 =
        "application/fastinfoset";

    // FI + SOAP 1.2
    public static final String FAST_INFOSET_TYPE_SOAP12 =
        "application/soap+fastinfoset";

    // XML + XOP + SOAP 1.1
    public static final String XOP_SOAP11_XML_TYPE_VALUE =
        "application/xop+xml;type=\"text/xml\"";

    // XML + XOP + SOAP 1.2
    public static final String XOP_SOAP12_XML_TYPE_VALUE =
        "application/xop+xml;type=\"application/soap+xml\"";

    public static final String XML_CONTENT_TYPE_VALUE = "text/xml";

    public static final String SOAP12_XML_CONTENT_TYPE_VALUE = "application/soap+xml";

    public static final String STANDARD_ACCEPT_VALUE =
        "application/xop+xml, text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2";

    public static final String SOAP12_XML_ACCEPT_VALUE =
        "application/soap+xml" + ", " + STANDARD_ACCEPT_VALUE;

    public static final String XML_ACCEPT_VALUE =
        XML_CONTENT_TYPE_VALUE + ", " + STANDARD_ACCEPT_VALUE;

    public static final String XML_FI_ACCEPT_VALUE =
        FAST_INFOSET_TYPE_SOAP11 + ", " + XML_ACCEPT_VALUE;

    public static final String SOAP12_XML_FI_ACCEPT_VALUE =
        FAST_INFOSET_TYPE_SOAP12 + ", " + SOAP12_XML_ACCEPT_VALUE;

    public String DISPATCH_CONTEXT = "com.sun.xml.internal.ws.client.dispatch.context";
    public String DISPATCH_MARSHALLER = "com.sun.xml.internal.ws.client.dispatch.marshaller";
    public String DISPATCH_UNMARSHALLER = "com.sun.xml.internal.ws.client.dispatch.unmarshaller";
    public static final String BINDING_ID_PROPERTY = "com.sun.xml.internal.ws.binding";

//    // Content negotiation property: values "none", "pessimistic" and "optimistic"
//    public static final String CONTENT_NEGOTIATION_PROPERTY =
//        "com.sun.xml.internal.ws.client.ContentNegotiation";

}
