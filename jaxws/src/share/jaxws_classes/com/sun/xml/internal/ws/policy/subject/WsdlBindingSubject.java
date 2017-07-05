/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.policy.subject;

import com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;
import javax.xml.namespace.QName;

/**
 * Provides objects for use as WSDL 1.0/1.1 policy subjects.
 *
 * An instance of this class may represent a wsdl:binding element or a wsdl:binding/operation
 * element or a wsdl:binding/operation/message element.
 *
 * @author Fabian Ritzmann
 */
public class WsdlBindingSubject {

    /**
     * For message subjects, this needs to be set to one of the values INPUT, OUTPUT
     * or FAULT. Any other subject has the message type NO_MESSAGE.
     */
    public enum WsdlMessageType {
        NO_MESSAGE,
        INPUT,
        OUTPUT,
        FAULT
    }

    /**
     * Identifies the scope to which this subject belongs. See WS-PolicyAttachment
     * for an explanation on WSDL scopes.
     *
     * The SERVICE scope is not actually used and only listed here for completeness
     * sake.
     */
    public enum WsdlNameScope {
        SERVICE,
        ENDPOINT,
        OPERATION,
        MESSAGE
    }

    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(WsdlBindingSubject.class);

    private final QName name;
    private final WsdlMessageType messageType;
    private final WsdlNameScope nameScope;
    private final WsdlBindingSubject parent;

    WsdlBindingSubject(final QName name, final WsdlNameScope scope, final WsdlBindingSubject parent) {
        this(name, WsdlMessageType.NO_MESSAGE, scope, parent);
    }

    WsdlBindingSubject(final QName name, final WsdlMessageType messageType, final WsdlNameScope scope, final WsdlBindingSubject parent) {
        this.name = name;
        this.messageType = messageType;
        this.nameScope = scope;
        this.parent = parent;
    }

    public static WsdlBindingSubject createBindingSubject(QName bindingName) {
        return new WsdlBindingSubject(bindingName, WsdlNameScope.ENDPOINT, null);
    }

    public static WsdlBindingSubject createBindingOperationSubject(QName bindingName, QName operationName) {
        final WsdlBindingSubject bindingSubject = createBindingSubject(bindingName);
        return new WsdlBindingSubject(operationName, WsdlNameScope.OPERATION, bindingSubject);
    }

    public static WsdlBindingSubject createBindingMessageSubject(QName bindingName, QName operationName, QName messageName, WsdlMessageType messageType) {
        if (messageType == null) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0083_MESSAGE_TYPE_NULL()));
        }
        if (messageType == WsdlMessageType.NO_MESSAGE) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0084_MESSAGE_TYPE_NO_MESSAGE()));
        }
        if ((messageType == WsdlMessageType.FAULT) && (messageName == null)) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0085_MESSAGE_FAULT_NO_NAME()));
        }
        final WsdlBindingSubject operationSubject = createBindingOperationSubject(bindingName, operationName);
        return new WsdlBindingSubject(messageName, messageType, WsdlNameScope.MESSAGE, operationSubject);
    }

    public QName getName() {
        return this.name;
    }

    public WsdlMessageType getMessageType() {
        return this.messageType;
    }

    public WsdlBindingSubject getParent() {
        return this.parent;
    }

    public boolean isBindingSubject() {
        if (this.nameScope == WsdlNameScope.ENDPOINT) {
            return this.parent == null;
        }
        else {
            return false;
        }
    }

    public boolean isBindingOperationSubject() {
        if (this.nameScope == WsdlNameScope.OPERATION) {
            if (this.parent != null) {
                return this.parent.isBindingSubject();
            }
        }
        return false;
    }

    public boolean isBindingMessageSubject() {
        if (this.nameScope == WsdlNameScope.MESSAGE) {
            if (this.parent != null) {
                return this.parent.isBindingOperationSubject();
            }
        }
        return false;
    }

    @Override
    public boolean equals(final Object that) {
        if (this == that) {
            return true;
        }

        if (that == null || !(that instanceof WsdlBindingSubject)) {
            return false;
        }

        final WsdlBindingSubject thatSubject = (WsdlBindingSubject) that;
        boolean isEqual = true;

        isEqual = isEqual && ((this.name == null) ? thatSubject.name == null : this.name.equals(thatSubject.name));
        isEqual = isEqual && this.messageType.equals(thatSubject.messageType);
        isEqual = isEqual && this.nameScope.equals(thatSubject.nameScope);
        isEqual = isEqual && ((this.parent == null) ? thatSubject.parent == null : this.parent.equals(thatSubject.parent));

        return isEqual;
    }

    @Override
    public int hashCode() {
        int result = 23;

        result = 31 * result + ((this.name == null) ? 0 : this.name.hashCode());
        result = 31 * result + this.messageType.hashCode();
        result = 31 * result + this.nameScope.hashCode();
        result = 31 * result + ((this.parent == null) ? 0 : this.parent.hashCode());

        return result;
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder("WsdlBindingSubject[");
        result.append(this.name).append(", ").append(this.messageType);
        result.append(", ").append(this.nameScope).append(", ").append(this.parent);
        return result.append("]").toString();
    }

}
