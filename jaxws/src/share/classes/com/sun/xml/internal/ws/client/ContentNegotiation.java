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

import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import static com.sun.xml.internal.ws.client.BindingProviderProperties.CONTENT_NEGOTIATION_PROPERTY;

import java.util.Map;

public class ContentNegotiation {

    /**
     * Initializes content negotiation property in <code>MessageStruct</code>
     * based on request context and system properties.
     */
    static public void initialize(Map context, MessageStruct messageStruct) {
        String value = (String) context.get(CONTENT_NEGOTIATION_PROPERTY);
        if (value != null) {
            if (value.equals("none") || value.equals("pessimistic") || value.equals("optimistic")) {
                messageStruct.setMetaData(CONTENT_NEGOTIATION_PROPERTY, value.intern());
            } else {
                throw new SenderException("sender.request.illegalValueForContentNegotiation", value);
            }
        } else {
            initFromSystemProperties(context, messageStruct);
        }
    }

    /**
     * Initializes content negotiation property in <code>MessageStruct</code>
     * based on system property of the same name.
     */
    static public void initFromSystemProperties(Map context, MessageStruct messageStruct)
        throws SenderException {
        String value = System.getProperty(CONTENT_NEGOTIATION_PROPERTY);

        if (value == null) {
            messageStruct.setMetaData(
                CONTENT_NEGOTIATION_PROPERTY, "none");      // FI is off by default
        } else if (value.equals("none") || value.equals("pessimistic") || value.equals("optimistic")) {
            messageStruct.setMetaData(CONTENT_NEGOTIATION_PROPERTY, value.intern());
            context.put(CONTENT_NEGOTIATION_PROPERTY, value.intern());
        } else {
            throw new SenderException("sender.request.illegalValueForContentNegotiation", value);
        }
    }
}
