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

package com.sun.xml.internal.ws.api;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.Tube;

import javax.xml.namespace.QName;
import javax.xml.ws.Binding;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.handler.Handler;
import java.util.List;
import java.util.Set;


/**
 * JAX-WS implementation of {@link Binding}.
 *
 * <p>
 * This object can be created by {@link BindingID#createBinding()}.
 *
 * <p>
 * Binding conceptually includes the on-the-wire format of the message,
 * this this object owns {@link Codec}.
 *
 * @author Kohsuke Kawaguchi
 */
public interface WSBinding extends Binding {
    /**
     * Gets the SOAP version of this binding.
     *
     * TODO: clarify what to do with XML/HTTP binding
     *
     * <p>
     * This is just a short-cut for  {@code getBindingID().getSOAPVersion()}
     *
     * @return
     *      If the binding is using SOAP, this method returns
     *      a {@link SOAPVersion} constant.
     *
     *      If the binding is not based on SOAP, this method
     *      returns null. See {@link Message} for how a non-SOAP
     *      binding shall be handled by {@link Tube}s.
     */
    SOAPVersion getSOAPVersion();
    /**
     * Gets the WS-Addressing version of this binding.
     * <p/>
     * TODO: clarify what to do with XML/HTTP binding
     *
     * @return If the binding is using SOAP and WS-Addressing is enabled,
     *         this method returns a {@link AddressingVersion} constant.
     *         If binding is not using SOAP or WS-Addressing is not enabled,
     *         this method returns null.
     *
     *          This might be little slow as it has to go over all the features on binding.
     *          Its advisable to cache the addressingVersion wherever possible and reuse it.
     */
    AddressingVersion getAddressingVersion();

    /**
     * Gets the binding ID, which uniquely identifies the binding.
     *
     * <p>
     * The relevant specs define the binding IDs and what they mean.
     * The ID is used in many places to identify the kind of binding
     * (such as SOAP1.1, SOAP1.2, REST, ...)
     *
     * @return
     *      Always non-null same value.
     */
    @NotNull BindingID getBindingId();

    @NotNull@Override
    List<Handler> getHandlerChain();

    /**
     * Checks if a particular {@link WebServiceFeature} is enabled.
     *
     * @return
     *      true if enabled.
     */
    boolean isFeatureEnabled(@NotNull Class<? extends WebServiceFeature> feature);

    /**
     * Experimental: Checks if a particular {@link WebServiceFeature} on an operation is enabled.
     *
     * @param operationName
     *      The WSDL name of the operation.
     * @return
     *      true if enabled.
     */
    boolean isOperationFeatureEnabled(@NotNull Class<? extends WebServiceFeature> feature,
            @NotNull final QName operationName);

    /**
     * Gets a {@link WebServiceFeature} of the specific type.
     *
     * @param featureType
     *      The type of the feature to retrieve.
     * @return
     *      If the feature is present and enabled, return a non-null instance.
     *      Otherwise null.
     */
    @Nullable <F extends WebServiceFeature> F getFeature(@NotNull Class<F> featureType);

    /**
     * Experimental: Gets a {@link WebServiceFeature} of the specific type that applies to an operation.
     *
     * @param featureType
     *      The type of the feature to retrieve.
     * @param operationName
     *      The WSDL name of the operation.
     * @return
     *      If the feature is present and enabled, return a non-null instance.
     *      Otherwise null.
     */
    @Nullable <F extends WebServiceFeature> F getOperationFeature(@NotNull Class<F> featureType,
            @NotNull final QName operationName);

    /**
     * Returns a list of features associated with {@link WSBinding}.
     */
    @NotNull WSFeatureList getFeatures();

    /**
     * Experimental: Returns a list of features associated with {@link WSBinding} that apply to
     * a particular operation.
     *
     * @param operationName
     *      The WSDL name of the operation.
     */
    @NotNull WSFeatureList getOperationFeatures(@NotNull final QName operationName);

    /**
     * Experimental: Returns a list of features associated with {@link WSBinding} that apply to
     * the input message of an operation.
     *
     * @param operationName
     *      The WSDL name of the operation.
     */
    @NotNull WSFeatureList getInputMessageFeatures(@NotNull final QName operationName);

    /**
     * Experimental: Returns a list of features associated with {@link WSBinding} that apply to
     * the output message of an operation.
     *
     * @param operationName
     *      The WSDL name of the operation.
     */
    @NotNull WSFeatureList getOutputMessageFeatures(@NotNull final QName operationName);

    /**
     * Experimental: Returns a list of features associated with {@link WSBinding} that apply to
     * one of the fault messages of an operation.
     *
     * @param operationName
     *      The WSDL name of the operation.
     * @param messageName
     *      The WSDL name of the fault message.
     */
    @NotNull WSFeatureList getFaultMessageFeatures(@NotNull final QName operationName,
            @NotNull final QName messageName);

    /**
     * Returns set of header QNames known to be supported by this binding.
     * @return Set of known QNames
     */
    @NotNull Set<QName> getKnownHeaders();

    /**
     * Adds header QName to set known to be supported by this binding
     * @param knownHeader Known header QName
     * @return true, if new entry was added; false, if known header QName was already known
     */
    boolean addKnownHeader(QName knownHeader);

    /**
     * @return A MessageContextFactory configured according to the binding's features.
     */
    @NotNull com.oracle.webservices.internal.api.message.MessageContextFactory getMessageContextFactory();
}
