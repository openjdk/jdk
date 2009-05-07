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

package com.sun.xml.internal.ws.api;

import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.client.WSServiceDelegate;

import javax.xml.bind.JAXBContext;
import javax.xml.namespace.QName;
import javax.xml.ws.Dispatch;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.Service;
import javax.xml.ws.Service.Mode;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.spi.ServiceDelegate;
import java.lang.reflect.Field;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * JAX-WS implementation of {@link ServiceDelegate}.
 *
 * <p>
 * This abstract class is used only to improve the static type safety
 * of the JAX-WS internal API.
 *
 * <p>
 * The class name intentionally doesn't include "Delegate",
 * because the fact that it's a delegate is a detail of
 * the JSR-224 API, and for the layers above us this object
 * nevertheless represents {@link Service}. We want them
 * to think of this as an internal representation of a service.
 *
 * <p>
 * Only JAX-WS internal code may downcast this to {@link WSServiceDelegate}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class WSService extends ServiceDelegate {
    protected WSService() {
    }

    /**
     * Works like {@link #getPort(EndpointReference, Class, WebServiceFeature...)}
     * but takes {@link WSEndpointReference}.
     */
    public abstract <T> T getPort(WSEndpointReference epr, Class<T> portInterface, WebServiceFeature... features);

    /**
     * Works like {@link #createDispatch(EndpointReference, Class, Mode, WebServiceFeature[])}
     * but it takes the port name separately, so that EPR without embedded metadata can be used.
     */
    public abstract <T> Dispatch<T> createDispatch(QName portName, WSEndpointReference wsepr, Class<T> aClass, Service.Mode mode, WebServiceFeature... features);

    /**
     * Works like {@link #createDispatch(EndpointReference, JAXBContext, Mode, WebServiceFeature[])}
     * but it takes the port name separately, so that EPR without embedded metadata can be used.
     */
    public abstract Dispatch<Object> createDispatch(QName portName, WSEndpointReference wsepr, JAXBContext jaxbContext, Service.Mode mode, WebServiceFeature... features);

    /**
     * Create a <code>Service</code> instance.
     *
     * The specified WSDL document location and service qualified name MUST
     * uniquely identify a <code>wsdl:service</code> element.
     *
     * @param wsdlDocumentLocation URL for the WSDL document location
     *                             for the service
     * @param serviceName QName for the service
     * @throws WebServiceException If any error in creation of the
     *                    specified service.
     **/
    public static WSService create( URL wsdlDocumentLocation, QName serviceName) {
        return new WSServiceDelegate(wsdlDocumentLocation,serviceName,Service.class);
    }

    /**
     * Create a <code>Service</code> instance.
     *
     * @param serviceName QName for the service
     * @throws WebServiceException If any error in creation of the
     *                    specified service
     */
    public static WSService create(QName serviceName) {
        return create(null,serviceName);
    }

    /**
     * Creates a service with a dummy service name.
     */
    public static WSService create() {
        return create(null,new QName(WSService.class.getName(),"dummy"));
    }

    /**
     * Obtains the {@link WSService} that's encapsulated inside a {@link Service}.
     *
     * @throws IllegalArgumentException
     *      if the given service object is not from the JAX-WS RI.
     */
    public static WSService unwrap(final Service svc) {
        return AccessController.doPrivileged(new PrivilegedAction<WSService>() {
            public WSService run() {
                try {
                    Field f = svc.getClass().getField("delegate");
                    f.setAccessible(true);
                    Object delegate = f.get(svc);
                    if(!(delegate instanceof WSService))
                        throw new IllegalArgumentException();
                    return (WSService) delegate;
                } catch (NoSuchFieldException e) {
                    AssertionError x = new AssertionError("Unexpected service API implementation");
                    x.initCause(e);
                    throw x;
                } catch (IllegalAccessException e) {
                    IllegalAccessError x = new IllegalAccessError(e.getMessage());
                    x.initCause(e);
                    throw x;
                }
            }
        });
    }
}
