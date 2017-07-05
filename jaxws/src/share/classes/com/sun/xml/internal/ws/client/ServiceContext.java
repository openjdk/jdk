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

import com.sun.xml.internal.ws.handler.HandlerResolverImpl;
import com.sun.xml.internal.ws.wsdl.WSDLContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import org.xml.sax.EntityResolver;


/**
 * $author: WS Development Team
 */
public class ServiceContext {
    private WSDLContext wsdlContext; //from wsdlParsing

    private Class serviceClass;
    private HandlerResolverImpl handlerResolver;
    private QName serviceName; //supplied on creation of service
    private final HashSet<EndpointIFContext> seiContext = new HashSet<EndpointIFContext>();
    /**
     * To be used to resolve WSDL resources.
     */
    private final EntityResolver entityResolver;
    private HashMap<QName,Set<String>> rolesMap = new HashMap<QName,Set<String>>();
    public ServiceContext(EntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }

    public ServiceContext(Class serviceClass, QName serviceName, EntityResolver entityResolver) {
        this.serviceClass = serviceClass;
        this.serviceName = serviceName;
        this.entityResolver = entityResolver;
    }

    public WSDLContext getWsdlContext() {
        return wsdlContext;
    }

    public void setWsdlContext(WSDLContext wsdlContext) {
        this.wsdlContext = wsdlContext;
    }

    public HandlerResolverImpl getHandlerResolver() {
        return handlerResolver;
    }

    public void setHandlerResolver(HandlerResolverImpl resolver) {
        this.handlerResolver = resolver;
    }

    public Set<String> getRoles(QName portName) {
        return rolesMap.get(portName);
    }

    public void setRoles(QName portName,Set<String> roles) {
        rolesMap.put(portName,roles);
    }

    public EndpointIFContext getEndpointIFContext(String className) {
        for (EndpointIFContext eif: seiContext){
            if (eif.getSei().getName().equals(className)){
                //this is the one
                return eif;
            }
        }
        return null;
    }

    public HashSet<EndpointIFContext> getEndpointIFContext() {
            return seiContext;
        }

    public void addEndpointIFContext(EndpointIFContext eifContext) {
        this.seiContext.add(eifContext);
    }

     public void addEndpointIFContext(List<EndpointIFContext> eifContexts) {
        this.seiContext.addAll(eifContexts);
    }

    public Class getServiceClass() {
        return serviceClass;
    }

    public void setServiceClass(Class serviceClass) {
        this.serviceClass = serviceClass;
    }

    public QName getServiceName() {
        if (serviceName == null) {
            if (wsdlContext != null) {
                setServiceName(wsdlContext.getFirstServiceName());
            }
        }
        return serviceName;
    }

    public void setServiceName(QName serviceName) {
        assert(serviceName != null);
        this.serviceName = serviceName;
    }

    public EntityResolver getEntityResolver() {
        return entityResolver;
    }

    public String toString() {
        return "ServiceContext{" +
            "wsdlContext=" + wsdlContext +
            ", handleResolver=" + handlerResolver +
            ", serviceClass=" + serviceClass +
            ", serviceName=" + serviceName +
            ", seiContext=" + seiContext +
            ", entityResolver=" + entityResolver +
            "}";
    }
}
