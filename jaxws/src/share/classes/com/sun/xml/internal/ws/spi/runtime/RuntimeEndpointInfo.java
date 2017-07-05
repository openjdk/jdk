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
package com.sun.xml.internal.ws.spi.runtime;

import java.net.URL;
import javax.xml.namespace.QName;
import org.xml.sax.EntityResolver;

/**
 * This captures all the required information (e.g: handlers, binding, endpoint
 * object, proxy for endpoint object etc.) about the endpoint.
 */
public interface RuntimeEndpointInfo {

    /**
     * Returns the name of the endpoint
     * @return name of the endpoint
     */
    public String getName();

    /**
     * sets the name of the endpoint
     */
    public void setName(String name);

    /**
     * Builds runtime model from implementor object.
     */
    public void init();

    /**
     * Destroys any state in this object
     */
    public void destroy();

    /**
     * This object is used for method invocations. It could be actual
     * implementor or a proxy object. This must be set before calling deploy().
     */
    public void setImplementor(Object implementor);

    /**
     * implementorClass should have <code>@WebService</code> or
     * <code>@WebServiceProvider</code> annotation.
     * Dynamic model is created using this object. If this is not set, implementor's
     * class is used to create the model.
     */
    public void setImplementorClass(Class implementorClass);

    /**
     * Returns actual Endpoint Object where method invocation is done
     *
     * @return Object Gets the endpoint implementation object or a proxy
     */
    public Object getImplementor();

    /**
     * Returns the set implementorClass
     *
     * @return implementor's class that has the annotations
     */
    public Class getImplementorClass();

    /**
     * Returns the binding for this endpoint
     *
     * @return Binding Returns the binding for this endpoint.
     */
    public Binding getBinding();

    /**
     * sets the binding for this endpoint. If there are handlers, set them on
     * the binding object.
     */
    public void setBinding(Binding binding);

    /**
     * Returns the WebServiceContext of this endpoint
     *
     * @return WebServiceContext Returns the WebServiceContext of this endpoint.
     */
    public WebServiceContext getWebServiceContext();

    /**
     * sets the WebServiceContext for this endpoint.
     */
    public void setWebServiceContext(WebServiceContext wsContext);

    /**
     * set the URL for primary WSDL, and a resolver to resolve entities like
     * WSDL, imports/references. A resolver for XML catalog can be created using
     * WSRtObjectFactory.createResolver(URL catalogURL).
     */
    public void setWsdlInfo(URL wsdlUrl, EntityResolver resolver);

    /**
     * Set service name from DD. If it is null, @WebService, @WebServiceProvider
     * annotations are used to get service name
     */
    public void setServiceName(QName name);

    /**
     * Set port name from DD. If it is null, @WebService, @WebServiceProvider
     * annotations are used to get port name
     */
    public void setPortName(QName name);

}
