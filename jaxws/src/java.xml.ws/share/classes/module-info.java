/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Defines the Java API for XML-Based Web Services (JAX-WS), and
 * the Web Services Metadata API.
 *
 * <p> This module is upgradeable.
 *
 * @uses javax.xml.soap.MessageFactory
 * @uses javax.xml.soap.SAAJMetaFactory
 * @uses javax.xml.soap.SOAPConnectionFactory
 * @uses javax.xml.soap.SOAPFactory
 * @uses javax.xml.ws.spi.Provider
 *
 * @moduleGraph
 * @since 9
 */
@Deprecated(since="9", forRemoval=true)
module java.xml.ws {
    requires java.desktop;
    requires java.logging;
    requires java.management;
    requires java.xml.ws.annotation;
    requires jdk.httpserver;
    requires jdk.unsupported;

    requires transitive java.activation;
    requires transitive java.xml;
    requires transitive java.xml.bind;

    exports javax.jws;
    exports javax.jws.soap;
    exports javax.xml.soap;
    exports javax.xml.ws;
    exports javax.xml.ws.handler;
    exports javax.xml.ws.handler.soap;
    exports javax.xml.ws.http;
    exports javax.xml.ws.soap;
    exports javax.xml.ws.spi;
    exports javax.xml.ws.spi.http;
    exports javax.xml.ws.wsaddressing;

    exports com.oracle.webservices.internal.api.databinding to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.addressing to
        jdk.xml.ws,
        java.xml.bind;
    exports com.sun.xml.internal.ws.addressing.v200408 to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.api to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.api.addressing to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.api.databinding to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.api.model to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.api.server to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.api.streaming to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.api.wsdl.parser to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.api.wsdl.writer to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.binding to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.db to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.model to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.policy.sourcemodel.wspolicy to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.spi.db to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.streaming to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.util to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.util.exception to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.util.xml to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.wsdl.parser to
        jdk.xml.ws;
    exports com.sun.xml.internal.ws.wsdl.writer to
        jdk.xml.ws;
    // JAF data handlers
    exports com.sun.xml.internal.messaging.saaj.soap to
        java.activation;
    exports com.sun.xml.internal.ws.encoding to
        java.activation;

    opens javax.xml.ws.wsaddressing to java.xml.bind;

    // XML document content needs to be exported
    opens com.sun.xml.internal.ws.runtime.config to java.xml.bind;

    // com.sun.xml.internal.ws.fault.SOAPFaultBuilder uses JAXBContext.newInstance
    opens com.sun.xml.internal.ws.fault to java.xml.bind;

    // com.sun.xml.internal.ws.addressing.WsaTubeHelperImpl uses JAXBContext.newInstance
    opens com.sun.xml.internal.ws.addressing to java.xml.bind;

    // com.sun.xml.internal.ws.addressing.v200408.WsaTubeHelperImpl uses JAXBContext.newInstance
    opens com.sun.xml.internal.ws.addressing.v200408 to java.xml.bind;

    // com.sun.xml.ws.developer.MemberSubmissionEndpointReference uses JAXBContext.newInstance
    opens com.sun.xml.internal.ws.developer to java.xml.bind;

    // com.sun.xml.ws.model.ExternalMetadataReader uses JAXBContext.newInstance
    opens com.oracle.xmlns.internal.webservices.jaxws_databinding to java.xml.bind;


    uses javax.xml.ws.spi.Provider;
    uses javax.xml.soap.MessageFactory;
    uses javax.xml.soap.SAAJMetaFactory;
    uses javax.xml.soap.SOAPConnectionFactory;
    uses javax.xml.soap.SOAPFactory;
}

