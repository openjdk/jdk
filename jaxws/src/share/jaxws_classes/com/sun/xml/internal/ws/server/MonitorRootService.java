/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.server;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.WSFeatureList;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundPortType;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLService;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.Engine;
import com.sun.xml.internal.ws.api.pipe.Fiber;
import com.sun.xml.internal.ws.api.pipe.FiberContextSwitchInterceptor;
import com.sun.xml.internal.ws.api.pipe.ServerPipeAssemblerContext;
import com.sun.xml.internal.ws.api.pipe.ServerTubeAssemblerContext;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubeCloner;
import com.sun.xml.internal.ws.api.pipe.TubelineAssembler;
import com.sun.xml.internal.ws.api.pipe.TubelineAssemblerFactory;
import com.sun.xml.internal.ws.api.server.*;
import com.sun.xml.internal.ws.fault.SOAPFaultBuilder;
import com.sun.xml.internal.ws.model.wsdl.WSDLProperties;
import com.sun.xml.internal.ws.model.wsdl.WSDLPortImpl;
import com.sun.xml.internal.ws.resources.HandlerMessages;
import com.sun.xml.internal.ws.transport.http.HttpAdapter;
import com.sun.xml.internal.ws.util.RuntimeVersion;
import com.sun.org.glassfish.external.amx.AMXGlassfish;
import com.sun.org.glassfish.gmbal.AMXMetadata;
import com.sun.org.glassfish.gmbal.Description;
import com.sun.org.glassfish.gmbal.InheritedAttribute;
import com.sun.org.glassfish.gmbal.InheritedAttributes;
import com.sun.org.glassfish.gmbal.ManagedAttribute;
import com.sun.org.glassfish.gmbal.ManagedData;
import com.sun.org.glassfish.gmbal.ManagedObject;
import com.sun.org.glassfish.gmbal.ManagedObjectManager;
import com.sun.org.glassfish.gmbal.ManagedObjectManagerFactory;
import java.net.URL;
import javax.management.ObjectName;


import javax.annotation.PreDestroy;
import javax.xml.namespace.QName;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.Handler;
import javax.xml.stream.XMLStreamException;
import javax.management.InstanceAlreadyExistsException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Harold Carr
 */
@ManagedObject
@Description("Metro Web Service endpoint")
@AMXMetadata(type="WSEndpoint")
public final class MonitorRootService extends MonitorBase {

    private final WSEndpoint endpoint;

    MonitorRootService(final WSEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    //
    // Items from WSEndpoint
    //

    @ManagedAttribute
    @Description("Policy associated with Endpoint")
    public String policy() {
        return endpoint.getPolicyMap() != null ?
               endpoint.getPolicyMap().toString() : null;
    }

    @ManagedAttribute
    @Description("Container")
    public @NotNull Container container() {
        return endpoint.getContainer();
    }


    @ManagedAttribute
    @Description("Port name")
    public @NotNull QName portName() {
        return endpoint.getPortName();
    }

    @ManagedAttribute
    @Description("Service name")
    public @NotNull QName serviceName() {
        return endpoint.getServiceName();
    }

    //
    // Items from assembler context
    //
    /*  NOTE:  These are not ready when the AMX Validator runs so NPE.
    @ManagedAttribute
    @Description("The last tube in the dispatch chain")
    public @NotNull Tube terminalTube() {
        return endpoint.getAssemblerContext().getTerminalTube();
    }

    @ManagedAttribute
    @Description("True if tubeline is known to be used for serving synchronous transport")
    public boolean synchronous() {
        return endpoint.getAssemblerContext().isSynchronous();
    }

    @ManagedAttribute
    @Description("")
    public String codecMimeType() {
        return endpoint.getAssemblerContext().getCodec().getMimeType();
    }
    */
    //
    // Items from WSBinding
    //

    @ManagedAttribute
    @Description("Binding SOAP Version")
    public String soapVersionHttpBindingId() {
        return endpoint.getBinding().getSOAPVersion().httpBindingId;
    }

    @ManagedAttribute
    @Description("Binding Addressing Version")
    public AddressingVersion addressingVersion() {
        return endpoint.getBinding().getAddressingVersion();
    }

    @ManagedAttribute
    @Description("Binding Identifier")
    public @NotNull BindingID bindingID() {
        return endpoint.getBinding().getBindingId();
    }

    @ManagedAttribute
    @Description("Binding features")
    public @NotNull WSFeatureList features() {
        return endpoint.getBinding().getFeatures();
    }

    //
    // Items from WSDLPort
    //

    @ManagedAttribute
    @Description("WSDLPort bound port type")
    public QName wsdlPortTypeName() {
        return endpoint.getPort() != null ?
               endpoint.getPort().getBinding().getPortTypeName() : null;
    }

    @ManagedAttribute
    @Description("Endpoint address")
    public EndpointAddress wsdlEndpointAddress() {
        return endpoint.getPort() != null ?
               endpoint.getPort().getAddress() : null;
    }

    //
    // Items from ServiceDefinition
    //

    @ManagedAttribute
    @Description("Documents referenced")
    public Set<String> serviceDefinitionImports() {
        return endpoint.getServiceDefinition() != null ?
               endpoint.getServiceDefinition().getPrimary().getImports() : null;
    }

    @ManagedAttribute
    @Description("System ID where document is taken from")
    public URL serviceDefinitionURL() {
        return endpoint.getServiceDefinition() != null ?
               endpoint.getServiceDefinition().getPrimary().getURL() : null;
    }

    //
    // Items from SEIModel
    //

    @ManagedAttribute
    @Description("SEI model WSDL location")
    public String seiModelWSDLLocation() {
        return endpoint.getSEIModel() != null ?
               endpoint.getSEIModel().getWSDLLocation() : null;
    }

    //
    // Items from RuntimeVersion
    //

    @ManagedAttribute
    @Description("JAX-WS runtime version")
    public String jaxwsRuntimeVersion() {
        return RuntimeVersion.VERSION.toString();
    }

    //
    // Items from HttpAdapter
    //

    @ManagedAttribute
    @Description("If true: show what goes across HTTP transport")
    public boolean dumpHTTPMessages() { return HttpAdapter.dump; }


    @ManagedAttribute
    @Description("Show what goes across HTTP transport")
    public void dumpHTTPMessages(final boolean x) { HttpAdapter.dump = x; }

}

// End of file.
