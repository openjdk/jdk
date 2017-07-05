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

/**
 * Content negotiation enum.
 * <p>
 * A value of {@link #none} means no content negotation at level of the
 * client transport will be performed to negotiate the encoding of XML infoset.
 * The default encoding will always be used.
 * <p>
 * A value of {@link #pessimistic} means the client transport will assume
 * the default encoding of XML infoset for an outbound message unless informed
 * otherwise by a previously received inbound message.
 * (The client transport initially and pessimistically assumes that a service
 * does not support anything other than the default encoding of XML infoset.)
 * <p>
 * A value of {@link #optimistic} means the client transport will assume
 * a non-default encoding of XML infoset for an outbound message.
 * (The client transport optimistically assumes that a service
 * supports the non-default encoding of XML infoset.)
 *
 * @author Paul.Sandoz@Sun.Com
 */
public enum ContentNegotiation {
    none,
    pessimistic,
    optimistic;

    /**
     * Property name for content negotiation on {@link RequestContext}.
     */
    public static final String PROPERTY = "com.sun.xml.internal.ws.client.ContentNegotiation";

    /**
     * Obtain the content negotiation value from a system property.
     * <p>
     * This method will never throw a runtime exception.
     *
     * @return the content negotiation value.
     */
    public static ContentNegotiation obtainFromSystemProperty() {
        try {
            String value = System.getProperty(PROPERTY);

            if (value == null) {
                return none;
            }

            return valueOf(value);
        } catch (Exception e) {
            // Default to none for any unrecognized value or any other
            // runtime exception thrown
            return none;
        }
    }
}
