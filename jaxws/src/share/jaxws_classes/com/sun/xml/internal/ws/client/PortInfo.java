/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.client;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.WSService;
import com.sun.xml.internal.ws.api.policy.PolicyResolverFactory;
import com.sun.xml.internal.ws.api.policy.PolicyResolver;
import com.sun.xml.internal.ws.api.client.WSPortInfo;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.binding.WebServiceFeatureList;
import com.sun.xml.internal.ws.policy.PolicyMap;
import com.sun.xml.internal.ws.policy.jaxws.PolicyUtil;

import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceFeature;

/**
 * Information about a port.
 * <p/>
 * This object is owned by {@link WSServiceDelegate} to keep track of a port,
 * since a port maybe added dynamically.
 *
 * @author JAXWS Development Team
 */
public class PortInfo implements WSPortInfo {
    private final @NotNull WSServiceDelegate owner;

    public final @NotNull QName portName;
    public final @NotNull EndpointAddress targetEndpoint;
    public final @NotNull BindingID bindingId;

    public final @NotNull PolicyMap policyMap;
    /**
     * If a port is known statically to a WSDL, {@link PortInfo} may
     * have the corresponding WSDL model. This would occur when the
     * service was created with the WSDL location and the port is defined
     * in the WSDL.
     * <p/>
     * If this is a {@link SEIPortInfo}, then this is always non-null.
     */
    public final @Nullable WSDLPort portModel;

    public PortInfo(WSServiceDelegate owner, EndpointAddress targetEndpoint, QName name, BindingID bindingId) {
        this.owner = owner;
        this.targetEndpoint = targetEndpoint;
        this.portName = name;
        this.bindingId = bindingId;
        this.portModel = getPortModel(owner, name);
        this.policyMap = createPolicyMap();

    }

    public PortInfo(@NotNull WSServiceDelegate owner, @NotNull WSDLPort port) {
        this.owner = owner;
        this.targetEndpoint = port.getAddress();
        this.portName = port.getName();
        this.bindingId = port.getBinding().getBindingId();
        this.portModel = port;
        this.policyMap = createPolicyMap();
    }

    public PolicyMap getPolicyMap() {
        return policyMap;
    }

    public PolicyMap createPolicyMap() {
       PolicyMap map;
       if(portModel != null) {
            map = portModel.getOwner().getParent().getPolicyMap();
       } else {
           map = PolicyResolverFactory.create().resolve(new PolicyResolver.ClientContext(null,owner.getContainer()));
       }
       //still map is null, create a empty map
       if(map == null)
           map = PolicyMap.createPolicyMap(null);
       return map;
    }
    /**
     * Creates {@link BindingImpl} for this {@link PortInfo}.
     *
     * @param webServiceFeatures
     *      User-specified features.
     * @param portInterface
     *      Null if this is for dispatch. Otherwise the interface the proxy is going to implement
     * @return
     *      The initialized BindingImpl
     */
    public BindingImpl createBinding(WebServiceFeature[] webServiceFeatures, Class<?> portInterface) {
        return createBinding(new WebServiceFeatureList(webServiceFeatures), portInterface, null);
    }

    public BindingImpl createBinding(WebServiceFeatureList webServiceFeatures, Class<?> portInterface,
                BindingImpl existingBinding) {
                if (existingBinding != null) {
                        webServiceFeatures.addAll(existingBinding.getFeatures());
                }

        Iterable<WebServiceFeature> configFeatures;
        //TODO incase of Dispatch, provide a way to User for complete control of the message processing by giving
        // ability to turn off the WSDL/Policy based features and its associated tubes.

        //Even in case of Dispatch, merge all features configured via WSDL/Policy or deployment configuration
        if (portModel != null) {
            // could have merged features from this.policyMap, but some features are set in WSDLModel which are not there in PolicyMap
            // for ex: <wsaw:UsingAddressing> wsdl extn., and since the policyMap features are merged into WSDLModel anyway during postFinished(),
            // So, using here WsdlModel for merging is right.

            // merge features from WSDL
            configFeatures = portModel.getFeatures();
        } else {
            configFeatures = PolicyUtil.getPortScopedFeatures(policyMap, owner.getServiceName(),portName);
        }
        webServiceFeatures.mergeFeatures(configFeatures, false);

        // merge features from interceptor
        webServiceFeatures.mergeFeatures(owner.serviceInterceptor.preCreateBinding(this, portInterface, webServiceFeatures), false);

        BindingImpl bindingImpl = BindingImpl.create(bindingId, webServiceFeatures.toArray());
        owner.getHandlerConfigurator().configureHandlers(this,bindingImpl);
        return bindingImpl;
    }

    //This method is used for Dispatch client only
    private WSDLPort getPortModel(WSServiceDelegate owner, QName portName) {

        if (owner.getWsdlService() != null){
            Iterable<? extends WSDLPort> ports = owner.getWsdlService().getPorts();
            for (WSDLPort port : ports){
                if (port.getName().equals(portName))
                    return port;
            }
        }
        return null;
    }

//
// implementation of API PortInfo interface
//

    @Nullable
    public WSDLPort getPort() {
        return portModel;
    }

    @NotNull
    public WSService getOwner() {
        return owner;
    }

    @NotNull
    public BindingID getBindingId() {
        return bindingId;
    }

    @NotNull
    public EndpointAddress getEndpointAddress() {
        return targetEndpoint;
    }

    /**
     * @deprecated
     *      Only meant to be used via {@link javax.xml.ws.handler.PortInfo}.
     *      Use {@link WSServiceDelegate#getServiceName()}.
     */
    public QName getServiceName() {
        return owner.getServiceName();
    }

    /**
     *      Only meant to be used via {@link javax.xml.ws.handler.PortInfo}.
     *      Use {@link #portName}.
     */
    public QName getPortName() {
        return portName;
    }

    /**
     * @deprecated
     *      Only meant to be used via {@link javax.xml.ws.handler.PortInfo}.
     *      Use {@link #bindingId}.
     */
    public String getBindingID() {
        return bindingId.toString();
    }
}
