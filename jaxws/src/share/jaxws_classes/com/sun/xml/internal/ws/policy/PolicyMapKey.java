/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.policy;

import com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;

import javax.xml.namespace.QName;

/**
 * This class provides implementation of PolicyMapKey interface to be used in connection with {@link PolicyMap}.
 * Instances of the class are created by a call to one of {@link PolicyMap} <code>createWsdl<emph>XXX</emph>PolicyMapKey(...)</code>
 * methods.
 * <p/>
 * The class wraps scope information and adds a package setter method to allow injection of actual equality comparator/tester. This injection
 * is made within a <code>get...</code> call on {@link PolicyMap}, before the actual scope map search is performed.
 *
 *
 * @author Marek Potociar (marek.potociar at sun.com)
 * @author Fabian Ritzmann
 */
final public class PolicyMapKey  {
    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(PolicyMapKey.class);

    private final QName service;
    private final QName port;
    private final QName operation;
    private final QName faultMessage;

    private PolicyMapKeyHandler handler;

    PolicyMapKey(final QName service, final QName port, final QName operation, final PolicyMapKeyHandler handler) {
        this(service, port, operation, null, handler);
    }

    PolicyMapKey(final QName service, final QName port, final QName operation, final QName faultMessage, final PolicyMapKeyHandler handler) {
        if (handler == null) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0046_POLICY_MAP_KEY_HANDLER_NOT_SET()));
        }

        this.service = service;
        this.port = port;
        this.operation = operation;
        this.faultMessage = faultMessage;
        this.handler = handler;
    }

    PolicyMapKey(final PolicyMapKey that) {
        this.service = that.service;
        this.port = that.port;
        this.operation = that.operation;
        this.faultMessage = that.faultMessage;
        this.handler = that.handler;
    }

    public QName getOperation() {
        return operation;
    }

    public QName getPort() {
        return port;
    }

    public QName getService() {
        return service;
    }

    void setHandler(PolicyMapKeyHandler handler) {
        if (handler == null) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0046_POLICY_MAP_KEY_HANDLER_NOT_SET()));
        }

        this.handler = handler;
    }

    public QName getFaultMessage() {
        return faultMessage;
    }

    @Override
    public boolean equals(final Object that) {
        if (this == that) {
            return true; // we are lucky here => no special handling is required
        }

        if (that == null) {
            return false;
        }

        if (that instanceof PolicyMapKey) {
            return handler.areEqual(this, (PolicyMapKey) that);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return handler.generateHashCode(this);
    }

    @Override
    public String toString() {
        final StringBuffer result = new StringBuffer("PolicyMapKey(");
        result.append(this.service).append(", ").append(port).append(", ").append(operation).append(", ").append(faultMessage);
        return result.append(")").toString();
    }
}
