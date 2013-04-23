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

package com.oracle.webservices.internal.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.xml.ws.http.HTTPBinding;
import javax.xml.ws.soap.SOAPBinding;
import javax.xml.ws.spi.WebServiceFeatureAnnotation;

/**
 * The EnvelopeStyle annotation is used to specify the message envelope style(s)
 * for a web service endpoint implementation class. To smooth the migration from
 * the BindingType annotation to this EnvelopeStyle annotation, each of the
 * styles is mapped to a binding identifier defined in JAX-WS specification.
 * Though a binding identifier includes both the envelope style and transport,
 * an envelope style defined herein does NOT imply or mandate any transport protocol
 * to be use together; HTTP is the default transport. An implementation may
 * chose to support other transport with any of the envelope styles.
 *
 * This annotation may be overriden programmatically or via deployment
 * descriptors, depending on the platform in use.
 *
 * @author shih-chang.chen@oracle.com
 */
@WebServiceFeatureAnnotation(id="", bean=com.oracle.webservices.internal.api.EnvelopeStyleFeature.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnvelopeStyle {

    /**
     * The envelope styles. If not specified, the default is the SOAP 1.1.
     *
     * @return The enveloping styles
     */
    Style[] style() default { Style.SOAP11 };

    public enum Style {

        /**
         * SOAP1.1. For JAX-WS, this is mapped from:
         * javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_BINDING
         */
        SOAP11(SOAPBinding.SOAP11HTTP_BINDING),

        /**
         * SOAP1.2. For JAX-WS, this is mapped from:
         * javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING
         */
        SOAP12(SOAPBinding.SOAP12HTTP_BINDING),

        /**
         * The raw XML. For JAX-WS, this is mapped from:
         * javax.xml.ws.http.HTTPBinding.HTTP_BINDING
         */
        XML(HTTPBinding.HTTP_BINDING);

        /**
         * The BindingID used by the BindingType annotation.
         */
        public final String bindingId;

        private Style(String id) {
            bindingId = id;
        }

        /**
         * Checks if the style is SOAP 1.1.
         *
         * @return true if the style is SOAP 1.1.
         */
        public boolean isSOAP11() { return this.equals(SOAP11); }

        /**
         * Checks if the style is SOAP 1.2.
         *
         * @return true if the style is SOAP 1.2.
         */
        public boolean isSOAP12() { return this.equals(SOAP12); }

        /**
         * Checks if the style is XML.
         *
         * @return true if the style is XML.
         */
        public boolean isXML() { return this.equals(XML); }
    }
}
