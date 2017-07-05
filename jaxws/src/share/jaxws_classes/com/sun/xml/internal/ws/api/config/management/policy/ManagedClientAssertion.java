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

package com.sun.xml.internal.ws.api.config.management.policy;

import com.sun.istack.internal.logging.Logger;
import com.sun.xml.internal.ws.api.client.WSPortInfo;
import com.sun.xml.internal.ws.policy.PolicyAssertion;
import com.sun.xml.internal.ws.policy.PolicyMap;
import com.sun.xml.internal.ws.policy.PolicyConstants;
import com.sun.xml.internal.ws.policy.sourcemodel.AssertionData;
import com.sun.xml.internal.ws.policy.spi.AssertionCreationException;
import com.sun.xml.internal.ws.resources.ManagementMessages;

import java.util.Collection;
import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceException;

/**
 * The client-side ManagedClient policy assertion.
 *
 * @author Fabian Ritzmann
 */
public class ManagedClientAssertion extends ManagementAssertion {

    public static final QName MANAGED_CLIENT_QNAME =
            new QName(PolicyConstants.SUN_MANAGEMENT_NAMESPACE, "ManagedClient");

    private static final Logger LOGGER = Logger.getLogger(ManagedClientAssertion.class);

    /**
     * Return ManagedClient assertion if there is one associated with the client.
     *
     * @param portInfo The client PortInfo.
     * @return The policy assertion if found. Null otherwise.
     * @throws WebServiceException If computing the effective policy of the port failed.
     */
    public static ManagedClientAssertion getAssertion(WSPortInfo portInfo) throws WebServiceException {
        if (portInfo == null)
                return null;

        LOGGER.entering(portInfo);
        // getPolicyMap is deprecated because it is only supposed to be used by Metro code
        // and not by other clients.
        @SuppressWarnings("deprecation")
        final PolicyMap policyMap = portInfo.getPolicyMap();
        final ManagedClientAssertion assertion = ManagementAssertion.getAssertion(MANAGED_CLIENT_QNAME,
                policyMap, portInfo.getServiceName(), portInfo.getPortName(), ManagedClientAssertion.class);
        LOGGER.exiting(assertion);
        return assertion;
    }

    public ManagedClientAssertion(AssertionData data, Collection<PolicyAssertion> assertionParameters)
            throws AssertionCreationException {
        super(MANAGED_CLIENT_QNAME, data, assertionParameters);
    }

    /**
     * Clients cannot be managed.
     *
     * @return False.
     */
    public boolean isManagementEnabled() {
        final String management = this.getAttributeValue(MANAGEMENT_ATTRIBUTE_QNAME);
        if (management != null) {
            if (management.trim().toLowerCase().equals("on") || Boolean.parseBoolean(management)) {
                LOGGER.warning(ManagementMessages.WSM_1006_CLIENT_MANAGEMENT_ENABLED());
            }
        }
        return false;
    }

}
