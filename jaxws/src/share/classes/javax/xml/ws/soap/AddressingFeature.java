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

package javax.xml.ws.soap;

import javax.xml.ws.Action;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.FaultAction;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.spi.Provider;

/**
 * This feature represents the use of WS-Addressing with either
 * the SOAP 1.1/HTTP or SOAP 1.2/HTTP binding.  Using this feature
 * with any other binding is NOT required.
 * <p>
 * Enabling this feature on the client will cause the JAX-WS runtime
 * to include WS-Addressing headers in SOAP messages.
 * <p>
 * If the web service developer has not explicitly enabled this feature,
 * it MAY be automatically enabled if the associated WSDL enables
 * WS-Addressing with an implementation recognized WSDL extension element.
 * However, in this version of JAX-WS, there is no standard WSDL
 * extension that a client can rely on to automatically enable WS-Addressing,
 * nor is there a standard default value specified for WS-Addressing
 * <code>Action</code> headers.
 * <p>
 * To write a portable endpoint and its corresponding client with this
 * version of JAX-WS, an endpoint MUST explicitly specify what WS-Addressing
 * <code>Actions</code> are to be used via the {@link Action} and
 * {@link FaultAction} annotations.  The client MUST explicitly enable
 * addresssing via this <code>AddressingFeature</code>, and for each invocation,
 * the client MUST explicitly set the {@link BindingProvider#SOAPACTION_URI_PROPERTY}.
 * After the W3C WG on WS-Addressing has specified how the use of WS-Addressing
 * is specified in the WSDL, and what the default value must be for Action headers,
 * a future version of JAX-WS will remove these requirements.
 * <p>
 * See {@link javax.xml.ws.RespectBindingFeature} for more information
 * on required WSDL extensions.
 * <p>
 * The following describes the effects of this feature with respect
 * to be enabled or disabled:
 * <ul>
 *  <li> ENABLED: In this Mode, WS-Addressing will be enabled.
 *       At runtime, WS-Addressing headers
 *       MUST be consumed by the receiver and produced by the
 *       sender even if the WSDL declares otherwise. The
 *       mustUnderstand="0" attribute MUST be used on the WS-Addressing
 *       headers.
 *  <li> DISABLED: In this Mode, WS-Addressing will be disabled
 *       even if an associated WSDL specifies otherwise. At runtime,
 *       WS-Addressing headers MUST NOT be used.  WS-Addressing may be explicitly
 *       disabled to prevent a JAX-WS implementation from consuming and producing
 *       WS-Addressing headers. If an application
 *       has implemented WS-Addressing itself, it MUST explicitly disable this feature.
*        Not doing so may break compatibility with future versions of JAX-WS.
 * </ul>
 * <p>
 * The {@link #required} property can be used to
 * specify if WS-Addressing headers MUST be present
 * on incoming messages.  This property only has meaning when used on the
 * endpoint and has no affect when used on the client.
 * By default the
 * <code>required</code> property is <code>false</code>.
  <p>

 * See <a href="http://www.w3.org/TR/2006/REC-ws-addr-core-20060509/">Web Services Addressing - Core</a>
 * and <a href="http://www.w3.org/TR/2006/REC-ws-addr-soap-20060509/">Web Services Addressing 1.0 - SOAP Binding</a>
 * for more information on WS-Addressing.
 *
 * @since JAX-WS 2.1
 */
public final class AddressingFeature extends WebServiceFeature {
    /**
     * Constant value identifying the AddressingFeature
     */
    public static final String ID = "http://www.w3.org/2005/08/addressing/module";

    /**
     * Property for the <code>required</code> feature parameter. When WS-Addressing
     * is enabled, the value of this property will be used
     * to specify if WS-Addressing headers MUST be present on incoming messages.  This
     * property only has meaning on the endpoint and has no
     * affect when used on the client.
     */
    protected boolean required = false;

    /**
     * Create an <code>AddressingFeature</code>.
     * The instance created will be enabled.
     */
    public AddressingFeature() {
        this.enabled = true;
    }

    /**
     * Create an <code>AddressingFeature</code>
     *
     * @param enabled specifies whether this feature should
     * be enabled or not.
     */
    public AddressingFeature(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Create an <code>AddressingFeature</code>
     *
     * @param enabled specifies whether this feature should
     * be enabled or not.
     * @param required specifies whether
     * WS-Addressing headers MUST be present on incoming messages. This property
     * only has meaning on the endpoint and has no affect when
     * used on the client.
     */
    public AddressingFeature(boolean enabled, boolean required) {
        this.enabled = enabled;
        this.required = required;
    }

    /**
     * {@inheritDoc}
     */
    public String getID() {
        return ID;
    }

    /**
     * Gets the boolean value used to determine if WS-Addressing
     * headers MUST be present on incoming messages.  This property
     * only has meaning on the endpoint, and has no affect
     * when used on the client.
     *
     * @return the current required value
     */
    public boolean isRequired() {
        return required;
    }
}
