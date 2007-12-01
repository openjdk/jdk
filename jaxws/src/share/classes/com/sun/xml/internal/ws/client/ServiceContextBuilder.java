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
import com.sun.xml.internal.ws.handler.PortInfoImpl;
import com.sun.xml.internal.ws.model.RuntimeModel;
import com.sun.xml.internal.ws.modeler.RuntimeModeler;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.util.HandlerAnnotationInfo;
import com.sun.xml.internal.ws.util.HandlerAnnotationProcessor;
import com.sun.xml.internal.ws.util.JAXWSUtils;
import com.sun.xml.internal.ws.wsdl.WSDLContext;
import org.xml.sax.EntityResolver;
import javax.jws.HandlerChain;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceException;
import javax.jws.WebService;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;

/**
 * $author: WS Development Team
 */
public abstract class ServiceContextBuilder {
    private ServiceContextBuilder() {
    }  // no instantication please

    /**
     * Creates a new {@link ServiceContext}.
     */
    public static ServiceContext build(URL wsdlLocation, QName serviceName, final Class service, EntityResolver er) throws WebServiceException {
        ServiceContext serviceContext = new ServiceContext(service, serviceName, er);

        if (wsdlLocation != null){
            WSDLContext wsCtx = new WSDLContext(wsdlLocation, er);

            //check if the serviceName is a valid one, if its not in the given WSDL fail
            if(!wsCtx.contains(serviceName))
                throw new ClientConfigurationException("service.invalidServiceName", serviceName, wsdlLocation);

            serviceContext.setWsdlContext(wsCtx);
        }

        //if @HandlerChain present, set HandlerResolver on service context
        HandlerChain handlerChain = (HandlerChain)
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return service.getAnnotation(HandlerChain.class);
            }
        });
        if(handlerChain != null) {
            HandlerResolverImpl hresolver = new HandlerResolverImpl(serviceContext);
            serviceContext.setHandlerResolver(hresolver);
        }
        return serviceContext;
    }

    public static void completeServiceContext(QName portName, ServiceContext serviceContext, Class portInterface) {
        if (portInterface != null)
            processAnnotations(portName, serviceContext, portInterface);
    }

    private static void processAnnotations(QName portName, ServiceContext serviceContext, Class portInterface) throws WebServiceException {
        WSDLContext wsdlContext = serviceContext.getWsdlContext();
        EndpointIFContext eifc = serviceContext.getEndpointIFContext(portInterface.getName());
        if ((eifc != null) && (eifc.getRuntimeContext() != null)) {
            return;
        }
        if (eifc == null) {
            eifc = new EndpointIFContext(portInterface);
            serviceContext.addEndpointIFContext(eifc);
        }

        QName serviceName = serviceContext.getServiceName();

        //if portName is null get it from the WSDL
        if (portName == null) {
            //get the first port corresponding to the SEI
            QName portTypeName = RuntimeModeler.getPortTypeName(portInterface);
            portName = wsdlContext.getWsdlDocument().getPortName(serviceContext.getServiceName(), portTypeName);
        }

        //still no portName, fail
        if(portName == null)
            throw new ClientConfigurationException("service.noPortName", portInterface.getName(), wsdlContext.getWsdlLocation().toString());

        eifc.setPortName(portName);
        String bindingId = wsdlContext.getBindingID(serviceName, portName);
        RuntimeModeler modeler = new RuntimeModeler(portInterface,
            serviceName, bindingId);
        modeler.setPortName(portName);
        RuntimeModel model = modeler.buildRuntimeModel();

        eifc.setRuntimeContext(new RuntimeContext(model));
    }

    private ArrayList<Class<?>> getSEI(final Class sc) {

        if (sc == null) {
            throw new WebServiceException();
        }

        //check to make sure this is a service
        if (!Service.class.isAssignableFrom(sc)) {
            throw new WebServiceException("service.interface.required" +
                sc.getName());
        }

        final ArrayList<Class<?>> classes = new ArrayList<Class<?>>();
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                Method[] methods = sc.getDeclaredMethods();
                for (final Method method : methods) {
                    method.setAccessible(true);
                    Class<?> seiClazz = method.getReturnType();
                    if ((seiClazz != null) && (!seiClazz.equals("void")))
                        classes.add(seiClazz);

                }
                return null;
            }
        });

        return classes;
    }

}
