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

package com.sun.xml.internal.ws.handler;

import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.transport.http.DeploymentDescriptorParser;
import com.sun.xml.internal.ws.util.HandlerAnnotationInfo;
import com.sun.xml.internal.ws.util.JAXWSUtils;
import com.sun.xml.internal.ws.util.UtilException;

import javax.annotation.PostConstruct;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.PortInfo;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Logger;


public class HandlerChainsModel {
    private static final Logger logger = Logger.getLogger(
            com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".util");

    private Class annotatedClass;
    private List<HandlerChainType> handlerChains;
    private String id;
    /** Creates a new instance of HandlerChains */
    private HandlerChainsModel(Class annotatedClass) {
        this.annotatedClass = annotatedClass;
    }

    private List<HandlerChainType> getHandlerChain() {
        if (handlerChains == null) {
            handlerChains = new ArrayList<HandlerChainType>();
        }
        return handlerChains;
    }

    public String getId() {
        return id;
    }

    public void setId(String value) {
        this.id = value;
    }
    /**
     * reader should be on <handler-chains> element
     */
    public static HandlerChainsModel parseHandlerConfigFile(Class annotatedClass, XMLStreamReader reader) {
        ensureProperName(reader,QNAME_HANDLER_CHAINS);
        HandlerChainsModel handlerModel = new HandlerChainsModel(annotatedClass);
        List<HandlerChainType> hChains = handlerModel.getHandlerChain();
        XMLStreamReaderUtil.nextElementContent(reader);

        while (reader.getName().equals(QNAME_HANDLER_CHAIN)) {
            HandlerChainType hChain = new HandlerChainType();
            XMLStreamReaderUtil.nextElementContent(reader);

            if (reader.getName().equals(QNAME_CHAIN_PORT_PATTERN)) {
                QName portNamePattern = XMLStreamReaderUtil.getElementQName(reader);
                hChain.setPortNamePattern(portNamePattern);
                XMLStreamReaderUtil.nextElementContent(reader);
            } else if (reader.getName().equals(QNAME_CHAIN_PROTOCOL_BINDING)) {
                String bindingList = XMLStreamReaderUtil.getElementText(reader);
                StringTokenizer stk = new StringTokenizer(bindingList);
                while(stk.hasMoreTokens()) {
                    String token = stk.nextToken();
                    // This will convert tokens into Binding URI
                    hChain.addProtocolBinding(token);
                }
                XMLStreamReaderUtil.nextElementContent(reader);
            } else if (reader.getName().equals(QNAME_CHAIN_SERVICE_PATTERN)) {
                QName serviceNamepattern = XMLStreamReaderUtil.getElementQName(reader);
                hChain.setServiceNamePattern(serviceNamepattern);
                XMLStreamReaderUtil.nextElementContent(reader);
            }
            List<HandlerType> handlers = hChain.getHandlers();
            // process all <handler> elements
            while (reader.getName().equals(QNAME_HANDLER)) {
                HandlerType handler = new HandlerType();

                XMLStreamReaderUtil.nextContent(reader);
                if (reader.getName().equals(QNAME_HANDLER_NAME)) {
                    String handlerName =
                            XMLStreamReaderUtil.getElementText(reader).trim();
                    handler.setHandlerName(handlerName);
                    XMLStreamReaderUtil.nextContent(reader);
                }

                // handler class
                ensureProperName(reader, QNAME_HANDLER_CLASS);
                String handlerClass =
                        XMLStreamReaderUtil.getElementText(reader).trim();
                handler.setHandlerClass(handlerClass);
                XMLStreamReaderUtil.nextContent(reader);

                // init params (ignored)
                while (reader.getName().equals(QNAME_HANDLER_PARAM)) {
                    skipInitParamElement(reader);
                }

                // headers (ignored)
                while (reader.getName().equals(QNAME_HANDLER_HEADER)) {
                    skipTextElement(reader);
                }

                // roles (not stored per handler)
                while (reader.getName().equals(QNAME_HANDLER_ROLE)) {
                    List<String> soapRoles = handler.getSoapRoles();
                    soapRoles.add(XMLStreamReaderUtil.getElementText(reader));
                    XMLStreamReaderUtil.nextContent(reader);
                }

                handlers.add(handler);

                // move past </handler>
                ensureProperName(reader, QNAME_HANDLER);
                XMLStreamReaderUtil.nextContent(reader);
            }

            // move past </handler-chain>
            ensureProperName(reader, QNAME_HANDLER_CHAIN);
            hChains.add(hChain);
            XMLStreamReaderUtil.nextContent(reader);
        }

        return handlerModel;
    }

    /**
     * <p>This method is called internally by HandlerAnnotationProcessor,
     * and by
     * {@link com.sun.xml.internal.ws.transport.http.DeploymentDescriptorParser}
     * directly when it reaches the handler chains element in the
     * descriptor file it is parsing.
     * @param reader should be on <handler-chains> element
     * @return A HandlerAnnotationInfo object that stores the
     * handlers and roles.
     */



    public static HandlerAnnotationInfo parseHandlerFile(XMLStreamReader reader,
            ClassLoader classLoader, QName serviceName, QName portName,
            WSBinding wsbinding) {
        ensureProperName(reader,QNAME_HANDLER_CHAINS);
        String bindingId = wsbinding.getBindingId().toString();
        HandlerAnnotationInfo info = new HandlerAnnotationInfo();

        XMLStreamReaderUtil.nextElementContent(reader);

        List<Handler> handlerChain = new ArrayList<Handler>();
        Set<String> roles = new HashSet<String>();

        while (reader.getName().equals(QNAME_HANDLER_CHAIN)) {

            XMLStreamReaderUtil.nextElementContent(reader);

            if (reader.getName().equals(QNAME_CHAIN_PORT_PATTERN)) {
                if (portName == null) {
                    logger.warning("handler chain sepcified for port " +
                            "but port QName passed to parser is null");
                }
                boolean parseChain = JAXWSUtils.matchQNames(portName,
                        XMLStreamReaderUtil.getElementQName(reader));
                if (!parseChain) {
                    skipChain(reader);
                    continue;
                }
                XMLStreamReaderUtil.nextElementContent(reader);
            } else if (reader.getName().equals(QNAME_CHAIN_PROTOCOL_BINDING)) {
                if (bindingId == null) {
                    logger.warning("handler chain sepcified for bindingId " +
                            "but bindingId passed to parser is null");
                }
                String bindingConstraint = XMLStreamReaderUtil.getElementText(reader);
                boolean skipThisChain = true;
                StringTokenizer stk = new StringTokenizer(bindingConstraint);
                List<String> bindingList = new ArrayList<String>();
                while(stk.hasMoreTokens()) {
                    String tokenOrURI = stk.nextToken();
                    /*
                    Convert short-form tokens to API's binding ids
                    Unknown token, Put it as it is
                    */
                    tokenOrURI = DeploymentDescriptorParser.getBindingIdForToken(tokenOrURI);
                    String binding = BindingID.parse(tokenOrURI).toString();
                    bindingList.add(binding);
                }
                if(bindingList.contains(bindingId)){
                    skipThisChain = false;
                }

                if (skipThisChain) {
                    skipChain(reader);
                    continue;
                }
                XMLStreamReaderUtil.nextElementContent(reader);
            } else if (reader.getName().equals(QNAME_CHAIN_SERVICE_PATTERN)) {
                if (serviceName == null) {
                    logger.warning("handler chain sepcified for service " +
                            "but service QName passed to parser is null");
                }
                boolean parseChain = JAXWSUtils.matchQNames(
                        serviceName,
                        XMLStreamReaderUtil.getElementQName(reader));
                if (!parseChain) {
                    skipChain(reader);
                    continue;
                }
                XMLStreamReaderUtil.nextElementContent(reader);
            }

            // process all <handler> elements
            while (reader.getName().equals(QNAME_HANDLER)) {
                Handler handler;

                XMLStreamReaderUtil.nextContent(reader);
                if (reader.getName().equals(QNAME_HANDLER_NAME)) {
                    skipTextElement(reader);
                }

                // handler class
                ensureProperName(reader, QNAME_HANDLER_CLASS);
                try {
                    handler = (Handler) loadClass(classLoader,
                            XMLStreamReaderUtil.getElementText(reader).trim()).newInstance();
                } catch (InstantiationException ie){
                    throw new RuntimeException(ie);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                XMLStreamReaderUtil.nextContent(reader);

                // init params (ignored)
                while (reader.getName().equals(QNAME_HANDLER_PARAM)) {
                    skipInitParamElement(reader);
                }

                // headers (ignored)
                while (reader.getName().equals(QNAME_HANDLER_HEADER)) {
                    skipTextElement(reader);
                }

                // roles (not stored per handler)
                while (reader.getName().equals(QNAME_HANDLER_ROLE)) {
                    roles.add(XMLStreamReaderUtil.getElementText(reader));
                    XMLStreamReaderUtil.nextContent(reader);
                }

                // call @PostConstruct method on handler if present
                for (Method method : handler.getClass().getMethods()) {
                    if (method.getAnnotation(PostConstruct.class) == null) {
                        continue;
                    }
                    try {
                        method.invoke(handler, new Object [0]);
                        break;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                handlerChain.add(handler);

                // move past </handler>
                ensureProperName(reader, QNAME_HANDLER);
                XMLStreamReaderUtil.nextContent(reader);
            }

            // move past </handler-chain>
            ensureProperName(reader, QNAME_HANDLER_CHAIN);
            XMLStreamReaderUtil.nextContent(reader);
        }

        info.setHandlers(handlerChain);
        info.setRoles(roles);
        return info;
    }

    public HandlerAnnotationInfo getHandlersForPortInfo(PortInfo info){

        HandlerAnnotationInfo handlerInfo = new HandlerAnnotationInfo();
        List<Handler> handlerClassList = new ArrayList<Handler>();
        Set<String> roles = new HashSet<String>();

        for(HandlerChainType hchain : handlerChains) {
            boolean hchainMatched = false;
            if((!hchain.isConstraintSet()) ||
                    JAXWSUtils.matchQNames(info.getServiceName(), hchain.getServiceNamePattern()) ||
                    JAXWSUtils.matchQNames(info.getPortName(), hchain.getPortNamePattern()) ||
                    hchain.getProtocolBindings().contains(info.getBindingID()) ){
                hchainMatched = true;

            }
            if(hchainMatched) {
                for(HandlerType handler : hchain.getHandlers()) {
                    try {
                        Handler handlerClass = (Handler) loadClass(annotatedClass.getClassLoader(),
                                handler.getHandlerClass()).newInstance();
                        callHandlerPostConstruct(handlerClass);
                        handlerClassList.add(handlerClass);
                    } catch (InstantiationException ie){
                        throw new RuntimeException(ie);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }

                    roles.addAll(handler.getSoapRoles());
                }

            }
        }

        handlerInfo.setHandlers(handlerClassList);
        handlerInfo.setRoles(roles);
        return handlerInfo;

    }

    private static Class loadClass(ClassLoader loader, String name) {
        try {
            return Class.forName(name, true, loader);
        } catch (ClassNotFoundException e) {
            throw new UtilException(
                    "util.handler.class.not.found",
                    name);
        }
    }

    private static void callHandlerPostConstruct(Object handlerClass) {
        // call @PostConstruct method on handler if present
        for (Method method : handlerClass.getClass().getMethods()) {
            if (method.getAnnotation(PostConstruct.class) == null) {
                continue;
            }
            try {
                method.invoke(handlerClass, new Object [0]);
                break;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void skipChain(XMLStreamReader reader) {
        while (XMLStreamReaderUtil.nextContent(reader) !=
                XMLStreamConstants.END_ELEMENT ||
                !reader.getName().equals(QNAME_HANDLER_CHAIN)) {}
        XMLStreamReaderUtil.nextElementContent(reader);
    }

    private static void skipTextElement(XMLStreamReader reader) {
        XMLStreamReaderUtil.nextContent(reader);
        XMLStreamReaderUtil.nextElementContent(reader);
        XMLStreamReaderUtil.nextElementContent(reader);
    }

    private static void skipInitParamElement(XMLStreamReader reader) {
        int state;
        do {
            state = XMLStreamReaderUtil.nextContent(reader);
        } while (state != XMLStreamReader.END_ELEMENT ||
                !reader.getName().equals(QNAME_HANDLER_PARAM));
        XMLStreamReaderUtil.nextElementContent(reader);
    }

    private static void ensureProperName(XMLStreamReader reader,
            QName expectedName) {

        if (!reader.getName().equals(expectedName)) {
            failWithLocalName("util.parser.wrong.element", reader,
                    expectedName.getLocalPart());
        }
    }

    static void ensureProperName(XMLStreamReader reader, String expectedName) {
        if (!reader.getLocalName().equals(expectedName)) {
            failWithLocalName("util.parser.wrong.element", reader,
                    expectedName);
        }
    }

    private static void failWithLocalName(String key,
            XMLStreamReader reader, String arg) {
        throw new UtilException(key,
            Integer.toString(reader.getLocation().getLineNumber()),
            reader.getLocalName(),
            arg );
    }

    public static final String PROTOCOL_SOAP11_TOKEN = "##SOAP11_HTTP";
    public static final String PROTOCOL_SOAP12_TOKEN = "##SOAP12_HTTP";
    public static final String PROTOCOL_XML_TOKEN = "##XML_HTTP";

    public static final String NS_109 =
            "http://java.sun.com/xml/ns/javaee";
    public static final QName QNAME_CHAIN_PORT_PATTERN =
            new QName(NS_109, "port-name-pattern");
    public static final QName QNAME_CHAIN_PROTOCOL_BINDING =
            new QName(NS_109, "protocol-bindings");
    public static final QName QNAME_CHAIN_SERVICE_PATTERN =
            new QName(NS_109, "service-name-pattern");
    public static final QName QNAME_HANDLER_CHAIN =
            new QName(NS_109, "handler-chain");
    public static final QName QNAME_HANDLER_CHAINS =
            new QName(NS_109, "handler-chains");
    public static final QName QNAME_HANDLER =
            new QName(NS_109, "handler");
    public static final QName QNAME_HANDLER_NAME =
            new QName(NS_109, "handler-name");
    public static final QName QNAME_HANDLER_CLASS =
            new QName(NS_109, "handler-class");
    public static final QName QNAME_HANDLER_PARAM =
            new QName(NS_109, "init-param");
    public static final QName QNAME_HANDLER_PARAM_NAME =
            new QName(NS_109, "param-name");
    public static final QName QNAME_HANDLER_PARAM_VALUE =
            new QName(NS_109, "param-value");
    public static final QName QNAME_HANDLER_HEADER =
            new QName(NS_109, "soap-header");
    public static final QName QNAME_HANDLER_ROLE =
            new QName(NS_109, "soap-role");

    static class HandlerChainType {
        //constraints
        QName serviceNamePattern;
        QName portNamePattern;
        List<String> protocolBindings;

        // This flag is set if one of the above constraint is set on handler chain
        boolean constraintSet = false;

        List<HandlerType> handlers;
        String id;


        /** Creates a new instance of HandlerChain */
        public HandlerChainType() {
            protocolBindings = new ArrayList<String>();
        }

        public void setServiceNamePattern(QName value) {
            this.serviceNamePattern = value;
            constraintSet = true;
        }

        public QName getServiceNamePattern() {
            return serviceNamePattern;
        }

        public void setPortNamePattern(QName value) {
            this.portNamePattern = value;
            constraintSet = true;
        }

        public QName getPortNamePattern() {
            return portNamePattern;
        }

        public List<java.lang.String> getProtocolBindings() {
            return this.protocolBindings;
        }

        public void addProtocolBinding(String tokenOrURI){
            /*
            Convert short-form tokens to API's binding ids
            Unknown token, Put it as it is
            */
            tokenOrURI = DeploymentDescriptorParser.getBindingIdForToken(tokenOrURI);
            String binding = BindingID.parse(tokenOrURI).toString();
            protocolBindings.add(binding);
            constraintSet = true;
        }

        public boolean isConstraintSet() {
            return constraintSet || !protocolBindings.isEmpty();
        }
        public java.lang.String getId() {
            return id;
        }

        public void setId(java.lang.String value) {
            this.id = value;
        }

        public List<HandlerType> getHandlers() {
            if (handlers == null) {
                handlers = new ArrayList<HandlerType>();
            }
            return this.handlers;
        }
    }

    static class HandlerType {
        String handlerName;
        String handlerClass;
        List<String> soapRoles;

        java.lang.String id;

        /** Creates a new instance of HandlerComponent */
        public HandlerType() {
        }

        public String getHandlerName() {
            return handlerName;
        }

        public void setHandlerName(String value) {
            this.handlerName = value;
        }

        public String getHandlerClass() {
            return handlerClass;
        }

        public void setHandlerClass(String value) {
            this.handlerClass = value;
        }

        public java.lang.String getId() {
            return id;
        }

        public void setId(java.lang.String value) {
            this.id = value;
        }

        public List<String> getSoapRoles() {
            if (soapRoles == null) {
                soapRoles = new ArrayList<String>();
            }
            return this.soapRoles;
        }
    }
}
