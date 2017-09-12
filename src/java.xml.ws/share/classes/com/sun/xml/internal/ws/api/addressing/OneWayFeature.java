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

package com.sun.xml.internal.ws.api.addressing;

import java.net.URL;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.FeatureConstructor;

import javax.xml.ws.WebServiceFeature;

import com.sun.org.glassfish.gmbal.ManagedAttribute;
import com.sun.org.glassfish.gmbal.ManagedData;

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
@ManagedData
public class OneWayFeature extends WebServiceFeature {
    /**
     * Constant value identifying the {@link OneWayFeature}
     */
    public static final String ID = "http://java.sun.com/xml/ns/jaxws/addressing/oneway";

    private String messageId;
    private WSEndpointReference replyTo;
    private WSEndpointReference sslReplyTo;
    private WSEndpointReference from;
    private WSEndpointReference faultTo;
    private WSEndpointReference sslFaultTo;
    private String relatesToID;
    private boolean useAsyncWithSyncInvoke = false;

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

    public OneWayFeature(final AddressingPropertySet a, AddressingVersion v) {
        this.enabled     = true;
        this.messageId   = a.getMessageId();
        this.relatesToID = a.getRelatesTo();
        this.replyTo     = makeEPR(a.getReplyTo(), v);
        this.faultTo     = makeEPR(a.getFaultTo(), v);
    }

    private WSEndpointReference makeEPR(final String x, final AddressingVersion v) {
        if (x == null) { return null; }
        return new WSEndpointReference(x, v);
    }

    public String getMessageId() {
        return messageId;
    }

    /**
     * {@inheritDoc}
     */
    @ManagedAttribute
    public String getID() {
        return ID;
    }

    public boolean
    hasSslEprs() {
      return sslReplyTo != null || sslFaultTo != null;
    }

    /**
     * Getter for wsa:ReplyTo header {@link WSEndpointReference} .
     *
     * @return address of the wsa:ReplyTo header
     */
    @ManagedAttribute
    public WSEndpointReference getReplyTo() {
        return replyTo;
    }

    public WSEndpointReference getReplyTo(boolean ssl) {
        return (ssl && sslReplyTo != null) ? sslReplyTo : replyTo;
    }

    /**
     * Setter for wsa:ReplyTo header {@link WSEndpointReference}.
     *
     * @param address
     */
    public void setReplyTo(WSEndpointReference address) {
        this.replyTo = address;
    }

    public WSEndpointReference getSslReplyTo() {
      return sslReplyTo;
    }

    public void setSslReplyTo(WSEndpointReference sslReplyTo) {
      this.sslReplyTo = sslReplyTo;
    }

  /**
     * Getter for wsa:From header {@link WSEndpointReference}.
     *
     * @return address of the wsa:From header
     */
    @ManagedAttribute
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
    @ManagedAttribute
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

  /**
     * Getter for wsa:FaultTo header {@link WSEndpointReference}.
     *
     * @return address of the wsa:FaultTo header
     */
    public WSEndpointReference getFaultTo() {
        return faultTo;
    }

    public WSEndpointReference getFaultTo(boolean ssl) {
        return (ssl && sslFaultTo != null) ? sslFaultTo : faultTo;
    }

    /**
     * Setter for wsa:FaultTo header {@link WSEndpointReference}.
     *
     * @param address of the wsa:FaultTo header
     */
    public void setFaultTo(WSEndpointReference address) {
        this.faultTo = address;
    }

    public WSEndpointReference getSslFaultTo() {
      return sslFaultTo;
    }

    public void setSslFaultTo(WSEndpointReference sslFaultTo) {
      this.sslFaultTo = sslFaultTo;
    }

    /**
     * Getter for whether async is to be used with sync invoke
     *
     * @return whether async is to be used with sync invoke
     */
    public boolean isUseAsyncWithSyncInvoke() {
        return useAsyncWithSyncInvoke;
    }

    /**
     *  Setter for whether async is to be used with sync invoke
     *
     * @param useAsyncWithSyncInvoke whether async is to be used with sync invoke
     */
    public void setUseAsyncWithSyncInvoke(boolean useAsyncWithSyncInvoke) {
        this.useAsyncWithSyncInvoke = useAsyncWithSyncInvoke;
    }

    /**
     * Calculate a new EPR using an existing one and substituting SSL specific
     * host and port values.
     * @param epr Existing EPR that will be the starting point for the SSL
     *        version
     * @param sslHost New SSL host or null if the existing host should be used
     * @param sslPort New SSL port or -1 if the existing port should be used
     * @return
     */
    public static WSEndpointReference
    enableSslForEpr(@NotNull WSEndpointReference epr,
                    @Nullable String sslHost,
                    int sslPort) {
        if (!epr.isAnonymous()) {
            String address = epr.getAddress();
            URL url;
            try {
              url = new URL(address);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
            String protocol = url.getProtocol();
            if (!protocol.equalsIgnoreCase("https")) {
              protocol = "https";
              String host = url.getHost();
              if (sslHost != null) {
                host = sslHost;
              }
              int port = url.getPort();
              if (sslPort > 0) {
                port = sslPort;
              }
              try {
                url = new URL(protocol, host, port, url.getFile());
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
              address = url.toExternalForm();
              return
                new WSEndpointReference(address, epr.getVersion());
            }
        }

        return epr;
    }

}
