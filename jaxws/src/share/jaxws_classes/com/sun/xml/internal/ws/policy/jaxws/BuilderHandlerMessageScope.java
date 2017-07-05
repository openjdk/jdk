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

package com.sun.xml.internal.ws.policy.jaxws;

import com.sun.xml.internal.ws.policy.PolicyException;
import com.sun.xml.internal.ws.policy.PolicyMap;
import com.sun.xml.internal.ws.policy.PolicyMapExtender;
import com.sun.xml.internal.ws.policy.PolicyMapKey;
import com.sun.xml.internal.ws.policy.PolicySubject;
import com.sun.xml.internal.ws.policy.sourcemodel.PolicySourceModel;

import java.util.Collection;
import java.util.Map;
import javax.xml.namespace.QName;

/**
 *
 * @author Jakub Podlesak (jakub.podlesak at sun.com)
 */
final class BuilderHandlerMessageScope extends BuilderHandler{
    private final QName service;
    private final QName port;
    private final QName operation;
    private final QName message;
    private final Scope scope;

    enum Scope{
        InputMessageScope,
        OutputMessageScope,
        FaultMessageScope,
    };


    /** Creates a new instance of WSDLServiceScopeBuilderHandler */
    BuilderHandlerMessageScope(
            Collection<String> policyURIs
            , Map<String,PolicySourceModel> policyStore
            , Object policySubject
            , Scope scope
            , QName service, QName port, QName operation, QName message) {

        super(policyURIs, policyStore, policySubject);
        this.service = service;
        this.port = port;
        this.operation = operation;
        this.scope = scope;
        this.message = message;
    }

    /**
     * Multiple bound operations may refer to the same fault messages. This would result
     * in multiple builder handlers referring to the same policies. This method allows
     * to sort out these duplicate handlers.
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof BuilderHandlerMessageScope)) {
            return false;
        }

        final BuilderHandlerMessageScope that = (BuilderHandlerMessageScope) obj;
        boolean result = true;

        result = result && ((this.policySubject == null) ? ((that.policySubject == null) ? true : false) :this.policySubject.equals(that.policySubject));
        result = result && ((this.scope == null) ? ((that.scope == null) ? true : false) :this.scope.equals(that.scope));
        result = result && ((this.message == null) ? ((that.message == null) ? true : false) :this.message.equals(that.message));
        if (this.scope != Scope.FaultMessageScope) {
            result = result && ((this.service == null) ? ((that.service == null) ? true : false) :this.service.equals(that.service));
            result = result && ((this.port == null) ? ((that.port == null) ? true : false) :this.port.equals(that.port));
            result = result && ((this.operation == null) ? ((that.operation == null) ? true : false) :this.operation.equals(that.operation));
        }

        return result;
    }

    @Override
    public int hashCode() {
        int hashCode = 19;
        hashCode = 31 * hashCode + (policySubject == null ? 0 : policySubject.hashCode());
        hashCode = 31 * hashCode + (message == null ? 0 : message.hashCode());
        hashCode = 31 * hashCode + (scope == null ? 0 : scope.hashCode());
        if (scope != Scope.FaultMessageScope) {
            hashCode = 31 * hashCode + (service == null ? 0 : service.hashCode());
            hashCode = 31 * hashCode + (port == null ? 0 : port.hashCode());
            hashCode = 31 * hashCode + (operation == null ? 0 : operation.hashCode());
        }
        return hashCode;
    }

    protected void doPopulate(final PolicyMapExtender policyMapExtender) throws PolicyException{
        PolicyMapKey mapKey;

        if (Scope.FaultMessageScope == scope) {
            mapKey = PolicyMap.createWsdlFaultMessageScopeKey(service, port, operation, message);
        } else { // in|out msg scope
            mapKey = PolicyMap.createWsdlMessageScopeKey(service, port, operation);
        }

        if (Scope.InputMessageScope == scope) {
            for (PolicySubject subject:getPolicySubjects()) {
                policyMapExtender.putInputMessageSubject(mapKey, subject);
            }
        } else if (Scope.OutputMessageScope == scope) {
            for (PolicySubject subject:getPolicySubjects()) {
                policyMapExtender.putOutputMessageSubject(mapKey, subject);
            }
        } else if (Scope.FaultMessageScope == scope) {
            for (PolicySubject subject : getPolicySubjects()) {
                policyMapExtender.putFaultMessageSubject(mapKey, subject);
            }
        }
    }
}
