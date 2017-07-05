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

package com.sun.xml.internal.ws.api.addressing;

import com.sun.xml.internal.ws.api.FeatureConstructor;

import javax.xml.ws.WebServiceFeature;

/**
 * Unsupported RI extension to work around an issue in WSIT.
 *
 * <p>
 * <b>This feature is not meant to be used by a common Web service developer</b> as there
 * is no need to send the above mentioned header for a one-way operation. But these
 * properties may need to be sent in certain middleware Web services.
 *
 * <p>
 * This feature allows ReplyTo, From and RelatesTo Message Addressing Properties
 * to be added for all messages that are sent from the port configured with
 * this annotation. All operations are assumed to be one-way, and
 * this feature should be used for one-way
 * operations only.
 *
 * If a non-null ReplyTo is specified, then MessageID property is also added.
 *
 * @author Arun Gupta
 */
public class OneWayFeature extends WebServiceFeature {
    /**
     * Constant value identifying the {@link OneWayFeature}
     */
    public static final String ID = "http://java.sun.com/xml/ns/jaxws/addressing/oneway";

    private WSEndpointReference replyTo;
    private WSEndpointReference from;
    private String relatesToID;

    /**
     * Create an {@link OneWayFeature}. The instance created will be enabled.
     */
    public OneWayFeature() {
        this.enabled = true;
    }

    /**
     * Create an {@link OneWayFeature}
     *
     * @param enabled specifies whether this feature should
     *                be enabled or not.
     */
    public OneWayFeature(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Create an {@link OneWayFeature}
     *
     * @param enabled specifies whether this feature should be enabled or not.
     * @param replyTo specifies the {@link WSEndpointReference} of wsa:ReplyTo header.
     */
    public OneWayFeature(boolean enabled, WSEndpointReference replyTo) {
        this.enabled = enabled;
        this.replyTo = replyTo;
    }

    /**
     * Create an {@link OneWayFeature}
     *
     * @param enabled specifies whether this feature should be enabled or not.
     * @param replyTo specifies the {@link WSEndpointReference} of wsa:ReplyTo header.
     * @param from specifies the {@link WSEndpointReference} of wsa:From header.
     * @param relatesTo specifies the MessageID to be used for wsa:RelatesTo header.
     */
    @FeatureConstructor({"enabled","replyTo","from","relatesTo"})
    public OneWayFeature(boolean enabled, WSEndpointReference replyTo, WSEndpointReference from, String relatesTo) {
        this.enabled = enabled;
        this.replyTo = replyTo;
        this.from = from;
        this.relatesToID = relatesTo;
    }

    /**
     * {@inheritDoc}
     */
    public String getID() {
        return ID;
    }

    /**
     * Getter for wsa:ReplyTo header {@link WSEndpointReference} .
     *
     * @return address of the wsa:ReplyTo header
     */
    public WSEndpointReference getReplyTo() {
        return replyTo;
    }

    /**
     * Setter for wsa:ReplyTo header {@link WSEndpointReference}.
     *
     * @param address
     */
    public void setReplyTo(WSEndpointReference address) {
        this.replyTo = address;
    }

    /**
     * Getter for wsa:From header {@link WSEndpointReference}.
     *
     * @return address of the wsa:From header
     */
    public WSEndpointReference getFrom() {
        return from;
    }

    /**
     * Setter for wsa:From header {@link WSEndpointReference}.
     *
     * @param address of the wsa:From header
     */
    public void setFrom(WSEndpointReference address) {
        this.from = address;
    }

    /**
     * Getter for MessageID for wsa:RelatesTo header.
     *
     * @return address of the wsa:FaultTo header
     */
    public String getRelatesToID() {
        return relatesToID;
    }

    /**
     * Setter for MessageID for wsa:RelatesTo header.
     *
     * @param id
     */
    public void setRelatesToID(String id) {
        this.relatesToID = id;
    }
}
