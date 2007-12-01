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

import com.sun.xml.internal.ws.server.RuntimeContext;

import javax.xml.namespace.QName;
import javax.xml.ws.handler.Handler;

import java.util.ArrayList;
import java.util.List;


/**
 * $author: WS Development Team
 */
public class EndpointIFContext {

    private RuntimeContext runtimeContext; //from annotationPro ess
    private Class serviceInterface;    //prop can take out
    private QName serviceName;
    private Class sei;
    private QName portName;
    private ArrayList<Handler> handlers;
    private String endpointAddress;
    private String bindingId;


    public EndpointIFContext(Class sei) {
        this.sei = sei;
        handlers = new ArrayList();
    }

    public RuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public void setRuntimeContext(RuntimeContext runtimeContext) {
        this.runtimeContext = runtimeContext;
    }

    public Class getServiceInterface() {
        return serviceInterface;
    }

    public void setServiceInterface(Class serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    public Class getSei() {
        return sei;
    }

    public void setSei(Class sei) {
        this.sei = sei;
    }

    public QName getPortName() {
        if (portName == null){
        if ((runtimeContext != null) && (runtimeContext.getModel() != null))
            portName = runtimeContext.getModel().getPortName();
        }
        return portName;
    }

    public String getEndpointAddress() {
        return endpointAddress;
    }

    public void setPortInfo(QName portQName, String endpoint, String bindingID) {
        portName = portQName;
        endpointAddress = endpoint;
        this.bindingId = bindingID;
    }

    public String getBindingID() {
        return bindingId;
    }

    public QName getServiceName() {
        return serviceName;
    }

    public boolean contains(QName serviceName) {
        if (serviceName.equals(this.serviceName))
            return true;
        return false;
    }

    public void setServiceName(QName serviceName) {
        this.serviceName = serviceName;
    }

    public void setPortName(QName portName) {
        this.portName = portName;
    }

    public void setBindingID(String bindingId) {
        this.bindingId = bindingId;
    }
}
