/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
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
