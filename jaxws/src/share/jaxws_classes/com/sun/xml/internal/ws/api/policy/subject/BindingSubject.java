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

package com.sun.xml.internal.ws.api.policy.subject;

import javax.xml.namespace.QName;

import com.sun.istack.internal.logging.Logger;
import com.sun.xml.internal.ws.resources.BindingApiMessages;

/**
 * Experimental: This class identifies WSDL scopes. That allows to attach and
 * retrieve elements to and from WSDL scopes.
 *
 * @author Fabian Ritzmann
 */
public class BindingSubject {

    /**
     * For message subjects, this needs to be set to one of the values INPUT, OUTPUT
     * or FAULT. Any other subject has the message type NO_MESSAGE.
     */
    private enum WsdlMessageType {
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
    private enum WsdlNameScope {
        SERVICE,
        ENDPOINT,
        OPERATION,
        MESSAGE
    }

    private static final Logger LOGGER = Logger.getLogger(BindingSubject.class);

    private final QName name;
    private final WsdlMessageType messageType;
    private final WsdlNameScope nameScope;
    private final BindingSubject parent;

    BindingSubject(final QName name, final WsdlNameScope scope, final BindingSubject parent) {
        this(name, WsdlMessageType.NO_MESSAGE, scope, parent);
    }

    BindingSubject(final QName name, final WsdlMessageType messageType, final WsdlNameScope scope, final BindingSubject parent) {
        this.name = name;
        this.messageType = messageType;
        this.nameScope = scope;
        this.parent = parent;
    }

    public static BindingSubject createBindingSubject(QName bindingName) {
        return new BindingSubject(bindingName, WsdlNameScope.ENDPOINT, null);
    }

    public static BindingSubject createOperationSubject(QName bindingName, QName operationName) {
        final BindingSubject bindingSubject = createBindingSubject(bindingName);
        return new BindingSubject(operationName, WsdlNameScope.OPERATION, bindingSubject);
    }

    public static BindingSubject createInputMessageSubject(QName bindingName, QName operationName, QName messageName) {
        final BindingSubject operationSubject = createOperationSubject(bindingName, operationName);
        return new BindingSubject(messageName, WsdlMessageType.INPUT, WsdlNameScope.MESSAGE, operationSubject);
    }

    public static BindingSubject createOutputMessageSubject(QName bindingName, QName operationName, QName messageName) {
        final BindingSubject operationSubject = createOperationSubject(bindingName, operationName);
        return new BindingSubject(messageName, WsdlMessageType.OUTPUT, WsdlNameScope.MESSAGE, operationSubject);
    }

    public static BindingSubject createFaultMessageSubject(QName bindingName, QName operationName, QName messageName) {
        if (messageName == null) {
            throw LOGGER.logSevereException(new IllegalArgumentException(BindingApiMessages.BINDING_API_NO_FAULT_MESSAGE_NAME()));
        }
        final BindingSubject operationSubject = createOperationSubject(bindingName, operationName);
        return new BindingSubject(messageName, WsdlMessageType.FAULT, WsdlNameScope.MESSAGE, operationSubject);
    }

    public QName getName() {
        return this.name;
    }

    public BindingSubject getParent() {
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

    public boolean isOperationSubject() {
        if (this.nameScope == WsdlNameScope.OPERATION) {
            if (this.parent != null) {
                return this.parent.isBindingSubject();
            }
        }
        return false;
    }

    public boolean isMessageSubject() {
        if (this.nameScope == WsdlNameScope.MESSAGE) {
            if (this.parent != null) {
                return this.parent.isOperationSubject();
            }
        }
        return false;
    }

    public boolean isInputMessageSubject() {
        return isMessageSubject() && (this.messageType == WsdlMessageType.INPUT);
    }

    public boolean isOutputMessageSubject() {
        return isMessageSubject() && (this.messageType == WsdlMessageType.OUTPUT);
    }

    public boolean isFaultMessageSubject() {
        return isMessageSubject() && (this.messageType == WsdlMessageType.FAULT);
    }

    @Override
    public boolean equals(final Object that) {
        if (this == that) {
            return true;
        }

        if (that == null || !(that instanceof BindingSubject)) {
            return false;
        }

        final BindingSubject thatSubject = (BindingSubject) that;
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

        result = 29 * result + ((this.name == null) ? 0 : this.name.hashCode());
        result = 29 * result + this.messageType.hashCode();
        result = 29 * result + this.nameScope.hashCode();
        result = 29 * result + ((this.parent == null) ? 0 : this.parent.hashCode());

        return result;
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder("BindingSubject[");
        result.append(this.name).append(", ").append(this.messageType);
        result.append(", ").append(this.nameScope).append(", ").append(this.parent);
        return result.append("]").toString();
    }

}
