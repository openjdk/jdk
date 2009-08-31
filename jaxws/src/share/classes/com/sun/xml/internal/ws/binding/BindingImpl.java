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

package com.sun.xml.internal.ws.binding;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.client.HandlerConfiguration;
import com.sun.xml.internal.ws.developer.MemberSubmissionAddressingFeature;
import com.sun.xml.internal.ws.developer.BindingTypeFeature;

import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.soap.AddressingFeature;
import javax.xml.ws.handler.Handler;
import java.util.Collections;
import java.util.List;

/**
 * Instances are created by the service, which then
 * sets the handler chain on the binding impl.
 *
 * <p>
 * This class is made abstract as we don't see a situation when
 * a BindingImpl has much meaning without binding id.
 * IOW, for a specific binding there will be a class
 * extending BindingImpl, for example SOAPBindingImpl.
 *
 * <p>
 * The spi Binding interface extends Binding.
 *
 * @author WS Development Team
 */
public abstract class BindingImpl implements WSBinding {
    private HandlerConfiguration handlerConfig;
    private final BindingID bindingId;
    // Features that are set(enabled/disabled) on the binding
    protected final WebServiceFeatureList features = new WebServiceFeatureList();

    protected BindingImpl(BindingID bindingId) {
        this.bindingId = bindingId;
        setHandlerConfig(createHandlerConfig(Collections.<Handler>emptyList()));
    }

    public
    @NotNull
    List<Handler> getHandlerChain() {
        return handlerConfig.getHandlerChain();
    }

    public HandlerConfiguration getHandlerConfig() {
        return handlerConfig;
    }


    /**
     * Sets the handlers on the binding and then
     * sorts the handlers in to logical and protocol handlers.
     * Creates a new HandlerConfiguration object and sets it on the BindingImpl.
     */
    public void setHandlerChain(List<Handler> chain) {
        setHandlerConfig(createHandlerConfig(chain));
    }

    /**
     * This is called when ever Binding.setHandlerChain() or SOAPBinding.setRoles()
     * is called.
     * This sorts out the Handlers into Logical and SOAP Handlers and
     * sets the HandlerConfiguration.
     */
    protected void setHandlerConfig(HandlerConfiguration handlerConfig) {
        this.handlerConfig = handlerConfig;
    }

    protected abstract HandlerConfiguration createHandlerConfig(List<Handler> handlerChain);

    public
    @NotNull
    BindingID getBindingId() {
        return bindingId;
    }

    public final SOAPVersion getSOAPVersion() {
        return bindingId.getSOAPVersion();
    }

    public AddressingVersion getAddressingVersion() {
        AddressingVersion addressingVersion;
        if (features.isEnabled(AddressingFeature.class))
            addressingVersion = AddressingVersion.W3C;
        else if (features.isEnabled(MemberSubmissionAddressingFeature.class))
            addressingVersion = AddressingVersion.MEMBER;
        else
            addressingVersion = null;
        return addressingVersion;
    }

    public final
    @NotNull
    Codec createCodec() {
        return bindingId.createEncoder(this);
    }

    public static BindingImpl create(@NotNull BindingID bindingId) {
        if (bindingId.equals(BindingID.XML_HTTP))
            return new HTTPBindingImpl();
        else
            return new SOAPBindingImpl(bindingId);
    }

    public static BindingImpl create(@NotNull BindingID bindingId, WebServiceFeature[] features) {
        // Override the BindingID from the features
        for(WebServiceFeature feature : features) {
            if (feature instanceof BindingTypeFeature) {
                BindingTypeFeature f = (BindingTypeFeature)feature;
                bindingId = BindingID.parse(f.getBindingId());
            }
        }
        if (bindingId.equals(BindingID.XML_HTTP))
            return new HTTPBindingImpl();
        else
            return new SOAPBindingImpl(bindingId, features);
    }

    public static WSBinding getDefaultBinding() {
        return new SOAPBindingImpl(BindingID.SOAP11_HTTP);
    }

    public String getBindingID() {
        return bindingId.toString();
    }

    public @Nullable <F extends WebServiceFeature> F getFeature(@NotNull Class<F> featureType){
        return features.get(featureType);
    }

    public boolean isFeatureEnabled(@NotNull Class<? extends WebServiceFeature> feature){
        return features.isEnabled(feature);
    }

    @NotNull
    public WebServiceFeatureList getFeatures() {
        return features;
    }


    public void setFeatures(WebServiceFeature... newFeatures) {
        if (newFeatures != null) {
            for (WebServiceFeature f : newFeatures) {
                features.add(f);
            }
        }
    }

    public void addFeature(@NotNull WebServiceFeature newFeature) {
        features.add(newFeature);
    }
}
