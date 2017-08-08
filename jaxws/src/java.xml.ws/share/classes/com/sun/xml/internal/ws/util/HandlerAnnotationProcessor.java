/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.util;

import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.databinding.MetadataReader;
import com.sun.xml.internal.ws.api.server.AsyncProvider;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.handler.HandlerChainsModel;
import com.sun.xml.internal.ws.model.ReflectAnnotationReader;
import com.sun.xml.internal.ws.server.EndpointFactory;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.istack.internal.NotNull;

import javax.jws.HandlerChain;
import javax.jws.WebService;
import javax.jws.soap.SOAPMessageHandlers;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.Provider;
import javax.xml.ws.Service;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.logging.Logger;

/**
 * <p>Used by client and server side to create handler information
 * from annotated class. The public methods all return a
 * HandlerChainInfo that contains the handlers and role information
 * needed at runtime.
 *
 * <p>All of the handler chain descriptors follow the same schema,
 * whether they are wsdl customizations, handler files specified
 * by an annotation, or are included in the sun-jaxws.xml file.
 * So this class is used for all handler xml information. The
 * two public entry points are
 * {@link HandlerAnnotationProcessor#buildHandlerInfo}, called
 * when you have an annotated class that points to a file.
 *
 * <p>The methods in the class are static so that it may called
 * from the runtime statically.
 *
 * @see com.sun.xml.internal.ws.util.HandlerAnnotationInfo
 *
 * @author JAX-WS Development Team
 */
public class HandlerAnnotationProcessor {

    private static final Logger logger = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".util");

    /**
     * <p>This method is called by
     * {@link EndpointFactory} when
     * they have an annotated class.
     *
     * <p>If there is no handler chain annotation on the class,
     * this method will return null. Otherwise it will load the
     * class and call the parseHandlerFile method to read the
     * information.
     *
     * @return A HandlerAnnotationInfo object that stores the
     * handlers and roles. Will return null if the class passed
     * in has no handler chain annotation.
     */
    public static HandlerAnnotationInfo buildHandlerInfo(@NotNull
        Class<?> clazz, QName serviceName, QName portName, WSBinding binding) {

        MetadataReader metadataReader = EndpointFactory.getExternalMetadatReader(clazz, binding);
        if (metadataReader == null) {
            metadataReader = new ReflectAnnotationReader();
        }

//        clazz = checkClass(clazz);
        HandlerChain handlerChain = metadataReader.getAnnotation(HandlerChain.class, clazz);
        if (handlerChain == null) {
            clazz = getSEI(clazz, metadataReader);
            if (clazz != null)
            handlerChain = metadataReader.getAnnotation(HandlerChain.class, clazz);
            if (handlerChain == null)
                return null;
        }

        if (clazz.getAnnotation(SOAPMessageHandlers.class) != null) {
            throw new UtilException(
                "util.handler.cannot.combine.soapmessagehandlers");
        }
        InputStream iStream = getFileAsStream(clazz, handlerChain);
        XMLStreamReader reader =
            XMLStreamReaderFactory.create(null,iStream, true);
        XMLStreamReaderUtil.nextElementContent(reader);
        HandlerAnnotationInfo handlerAnnInfo = HandlerChainsModel.parseHandlerFile(reader, clazz.getClassLoader(),
            serviceName, portName, binding);
        try {
            reader.close();
            iStream.close();
        } catch (XMLStreamException e) {
            e.printStackTrace();
            throw new UtilException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new UtilException(e.getMessage());
        }
        return handlerAnnInfo;
    }

    public static HandlerChainsModel buildHandlerChainsModel(final Class<?> clazz) {
        if(clazz == null) {
            return null;
        }
        HandlerChain handlerChain =
            clazz.getAnnotation(HandlerChain.class);
        if(handlerChain == null)
            return null;
        InputStream iStream = getFileAsStream(clazz, handlerChain);
        XMLStreamReader reader =
            XMLStreamReaderFactory.create(null,iStream, true);
        XMLStreamReaderUtil.nextElementContent(reader);
        HandlerChainsModel handlerChainsModel = HandlerChainsModel.parseHandlerConfigFile(clazz, reader);
        try {
            reader.close();
            iStream.close();
        } catch (XMLStreamException e) {
            e.printStackTrace();
            throw new UtilException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new UtilException(e.getMessage());
        }
        return handlerChainsModel;
    }

    static Class getClass(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(
                className);
        } catch (ClassNotFoundException e) {
            throw new UtilException("util.handler.class.not.found",
                className);
        }
    }

    static Class getSEI(Class<?> clazz, MetadataReader metadataReader) {
        if (metadataReader == null) {
            metadataReader = new ReflectAnnotationReader();
        }

        if (Provider.class.isAssignableFrom(clazz) || AsyncProvider.class.isAssignableFrom(clazz)) {
            //No SEI for Provider Implementation
            return null;
        }
        if (Service.class.isAssignableFrom(clazz)) {
            //No SEI for Service class
            return null;
        }

        WebService webService = metadataReader.getAnnotation(WebService.class, clazz);
        if (webService == null) {
            throw new UtilException("util.handler.no.webservice.annotation", clazz.getCanonicalName());
        }

        String ei = webService.endpointInterface();
        if (ei.length() > 0) {
            clazz = getClass(webService.endpointInterface());
            WebService ws = metadataReader.getAnnotation(WebService.class, clazz);
            if (ws == null) {
                throw new UtilException("util.handler.endpoint.interface.no.webservice",
                    webService.endpointInterface());
            }
            return clazz;
        }
        return null;
    }

    static InputStream getFileAsStream(Class clazz, HandlerChain chain) {
        Package pkg = clazz.getPackage();
        String filename = chain.file();
        String fullpath = addPackagePath(filename, pkg);
        InputStream is;

        is = moduleResource(clazz, filename);
        if (is != null) return is;

        is = moduleResource(clazz, fullpath);
        if (is != null) return is;

        URL url = cpResource(clazz, filename);
        if (url == null) url = cpResource(clazz, fullpath);

        if (url == null) {
            throw new UtilException("util.failed.to.find.handlerchain.file",
                    clazz.getName(), filename);
        }
        try {
            return url.openStream();
        } catch (IOException e) {
            throw new UtilException("util.failed.to.parse.handlerchain.file",
                    clazz.getName(), filename);
        }
    }

    private static URL cpResource(Class clazz, String name) {
        URL url = clazz.getResource(name);
        if (url == null) {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            url = tccl.getResource(name);
        }
        return url;
    }

    private static InputStream moduleResource(Class resolvingClass, String name) {
        Module module = resolvingClass.getModule();
        try {
            InputStream stream = module.getResourceAsStream(name);
            if (stream != null) {
                return stream;
            }
        } catch(IOException e) {
            throw new UtilException("util.failed.to.find.handlerchain.file",
                    resolvingClass.getName(), name);
        }
        return null;
    }

    private static String addPackagePath(String file, Package pkg) {
        String tmp = pkg.getName();
        tmp = tmp.replace('.', '/');
        tmp += "/" + file;
        return tmp;
    }
}
