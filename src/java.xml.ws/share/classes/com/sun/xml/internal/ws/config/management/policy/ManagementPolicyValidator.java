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

package com.sun.xml.internal.ws.config.management.policy;

import com.sun.xml.internal.ws.api.config.management.policy.ManagedClientAssertion;
import com.sun.xml.internal.ws.api.config.management.policy.ManagedServiceAssertion;
import com.sun.xml.internal.ws.policy.PolicyAssertion;
import com.sun.xml.internal.ws.policy.PolicyConstants;
import com.sun.xml.internal.ws.policy.spi.PolicyAssertionValidator;
import com.sun.xml.internal.ws.policy.spi.PolicyAssertionValidator.Fitness;

import javax.xml.namespace.QName;

/**
 * Validate the ManagedService and ManagedClient policy assertions.
 *
 * @author Fabian Ritzmann
 */
public class ManagementPolicyValidator implements PolicyAssertionValidator {

    public Fitness validateClientSide(PolicyAssertion assertion) {
        final QName assertionName = assertion.getName();
        if (ManagedClientAssertion.MANAGED_CLIENT_QNAME.equals(assertionName)) {
            return Fitness.SUPPORTED;
        }
        else if (ManagedServiceAssertion.MANAGED_SERVICE_QNAME.equals(assertionName)) {
            return Fitness.UNSUPPORTED;
        }
        else {
            return Fitness.UNKNOWN;
        }
    }

    public Fitness validateServerSide(PolicyAssertion assertion) {
        final QName assertionName = assertion.getName();
        if (ManagedServiceAssertion.MANAGED_SERVICE_QNAME.equals(assertionName)) {
            return Fitness.SUPPORTED;
        }
        else if (ManagedClientAssertion.MANAGED_CLIENT_QNAME.equals(assertionName)) {
            return Fitness.UNSUPPORTED;
        }
        else {
            return Fitness.UNKNOWN;
        }
    }

    public String[] declareSupportedDomains() {
        return new String[] { PolicyConstants.SUN_MANAGEMENT_NAMESPACE };
    }

}
