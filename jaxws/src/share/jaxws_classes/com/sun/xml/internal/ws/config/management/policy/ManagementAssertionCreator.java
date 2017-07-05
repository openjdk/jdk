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

package com.sun.xml.internal.ws.config.management.policy;

import com.sun.xml.internal.ws.api.config.management.policy.ManagedClientAssertion;
import com.sun.xml.internal.ws.api.config.management.policy.ManagedServiceAssertion;
import com.sun.xml.internal.ws.policy.AssertionSet;
import com.sun.xml.internal.ws.policy.PolicyAssertion;
import com.sun.xml.internal.ws.policy.PolicyConstants;
import com.sun.xml.internal.ws.policy.sourcemodel.AssertionData;
import com.sun.xml.internal.ws.policy.spi.AssertionCreationException;
import com.sun.xml.internal.ws.policy.spi.PolicyAssertionCreator;

import java.util.Collection;
import javax.xml.namespace.QName;

/**
 * Instantiates a PolicyAssertion of type ManagedServiceAssertion or ManagedClientAssertion.
 *
 * @author Fabian Ritzmann
 */
public class ManagementAssertionCreator implements PolicyAssertionCreator {

    public String[] getSupportedDomainNamespaceURIs() {
        return new String[] { PolicyConstants.SUN_MANAGEMENT_NAMESPACE };
    }

    public PolicyAssertion createAssertion(AssertionData data, Collection<PolicyAssertion> assertionParameters,
            AssertionSet nestedAlternative, PolicyAssertionCreator defaultCreator) throws AssertionCreationException {
        final QName name = data.getName();
        if (ManagedServiceAssertion.MANAGED_SERVICE_QNAME.equals(name)) {
            return new ManagedServiceAssertion(data, assertionParameters);
        }
        else if (ManagedClientAssertion.MANAGED_CLIENT_QNAME.equals(name)) {
            return new ManagedClientAssertion(data, assertionParameters);
        }
        else {
            return defaultCreator.createAssertion(data, assertionParameters, nestedAlternative, null);
        }
    }

}
