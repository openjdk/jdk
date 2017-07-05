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

package com.sun.xml.internal.ws.addressing.policy;

import com.sun.xml.internal.ws.addressing.W3CAddressingMetadataConstants;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.policy.AssertionSet;
import com.sun.xml.internal.ws.policy.Policy;
import com.sun.xml.internal.ws.policy.PolicyAssertion;
import com.sun.xml.internal.ws.policy.PolicyException;
import com.sun.xml.internal.ws.policy.PolicyMap;
import com.sun.xml.internal.ws.policy.PolicySubject;
import com.sun.xml.internal.ws.policy.jaxws.spi.PolicyMapConfigurator;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;
import com.sun.xml.internal.ws.policy.sourcemodel.AssertionData;
import com.sun.xml.internal.ws.policy.subject.WsdlBindingSubject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import javax.xml.namespace.QName;
import javax.xml.ws.soap.AddressingFeature;

/**
 * Generate an addressing policy and updates the PolicyMap if AddressingFeature is enabled.
 *
 * @author Fabian Ritzmann
 * @author Rama Pulavarthi
 */
public class AddressingPolicyMapConfigurator implements PolicyMapConfigurator {

    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(AddressingPolicyMapConfigurator.class);

    private static final class AddressingAssertion extends PolicyAssertion {
        /**
         * Creates an assertion with nested alternatives.
         *
         * @param assertionData
         * @param nestedAlternative
         */
        AddressingAssertion(AssertionData assertionData, final AssertionSet nestedAlternative) {
            super(assertionData, null, nestedAlternative);
        }

        /**
         * Creates an assertion with no nested alternatives.
         *
         * @param assertionData
         */
        AddressingAssertion(AssertionData assertionData) {
            super(assertionData, null, null);
        }
    }


    /**
     * Puts an addressing policy into the PolicyMap if the addressing feature was set.
     */
    public Collection<PolicySubject> update(final PolicyMap policyMap, final SEIModel model, final WSBinding wsBinding)
            throws PolicyException {
        LOGGER.entering(policyMap, model, wsBinding);

        Collection<PolicySubject> subjects = new ArrayList<PolicySubject>();
        if (policyMap != null) {
            final AddressingFeature addressingFeature = wsBinding.getFeature(AddressingFeature.class);
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("addressingFeature = " + addressingFeature);
            }
            if ((addressingFeature != null) && addressingFeature.isEnabled()) {
                //add wsam:Addrressing assertion if not exists.
                addWsamAddressing(subjects, policyMap, model, addressingFeature);
            }
        } // endif policy map not null
        LOGGER.exiting(subjects);
        return subjects;
    }

    private void addWsamAddressing(Collection<PolicySubject> subjects, PolicyMap policyMap, SEIModel model, AddressingFeature addressingFeature)
            throws PolicyException {
        final QName bindingName = model.getBoundPortTypeName();
        final WsdlBindingSubject wsdlSubject = WsdlBindingSubject.createBindingSubject(bindingName);
        final Policy addressingPolicy = createWsamAddressingPolicy(bindingName, addressingFeature);
        final PolicySubject addressingPolicySubject = new PolicySubject(wsdlSubject, addressingPolicy);
        subjects.add(addressingPolicySubject);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Added addressing policy with ID \"" + addressingPolicy.getIdOrName() + "\" to binding element \"" + bindingName + "\"");
        }
    }

    /**
     * Create a policy with an WSAM Addressing assertion.
     */
    private Policy createWsamAddressingPolicy(final QName bindingName, AddressingFeature af) {
        final ArrayList<AssertionSet> assertionSets = new ArrayList<AssertionSet>(1);
        final ArrayList<PolicyAssertion> assertions = new ArrayList<PolicyAssertion>(1);
        final AssertionData addressingData =
                AssertionData.createAssertionData(W3CAddressingMetadataConstants.WSAM_ADDRESSING_ASSERTION);
        if (!af.isRequired()) {
            addressingData.setOptionalAttribute(true);
        }
        try {
            AddressingFeature.Responses responses = af.getResponses();
            if (responses == AddressingFeature.Responses.ANONYMOUS) {
                AssertionData nestedAsserData = AssertionData.createAssertionData(W3CAddressingMetadataConstants.WSAM_ANONYMOUS_NESTED_ASSERTION);
                PolicyAssertion nestedAsser = new AddressingAssertion(nestedAsserData, null);
                assertions.add(new AddressingAssertion(addressingData, AssertionSet.createAssertionSet(Collections.singleton(nestedAsser))));
            } else if (responses == AddressingFeature.Responses.NON_ANONYMOUS) {
                final AssertionData nestedAsserData = AssertionData.createAssertionData(W3CAddressingMetadataConstants.WSAM_NONANONYMOUS_NESTED_ASSERTION);
                PolicyAssertion nestedAsser = new AddressingAssertion(nestedAsserData, null);
                assertions.add(new AddressingAssertion(addressingData, AssertionSet.createAssertionSet(Collections.singleton(nestedAsser))));
            } else {
                assertions.add(new AddressingAssertion(addressingData, AssertionSet.createAssertionSet(null)));
            }
        } catch (NoSuchMethodError e) {
            //If JAX-WS 2.2 API is really required, it would been reported in @Addressing or wsam:Addressing processing
            //Don't add any nested assertion to mimic the 2.1 behavior
            assertions.add(new AddressingAssertion(addressingData, AssertionSet.createAssertionSet(null)));
        }
        assertionSets.add(AssertionSet.createAssertionSet(assertions));
        return Policy.createPolicy(null, bindingName.getLocalPart() + "_WSAM_Addressing_Policy", assertionSets);
    }
}
