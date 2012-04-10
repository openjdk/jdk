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

import com.sun.xml.internal.ws.policy.PolicyMap.ScopeType;
import com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;
import com.sun.xml.internal.ws.policy.subject.PolicyMapKeyConverter;
import com.sun.xml.internal.ws.policy.subject.WsdlBindingSubject;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import javax.xml.namespace.QName;

/**
 * Utility methods that operate on a PolicyMap.
 *
 * @author Fabian Ritzmann
 */
public class PolicyMapUtil {

    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(PolicyMapUtil.class);

    private static final PolicyMerger MERGER = PolicyMerger.getMerger();

    /**
     * Prevent instantiation.
     */
    private PolicyMapUtil() {
    }

    /**
     * Throw an exception if the policy map contains any policy with at least two
     * policy alternatives.
     *
     * Optional assertions are not considered (unless they have been normalized into
     * two policy alternatives).
     *
     * @param map policy map to be processed
     * @throws PolicyException Thrown if the policy map contains at least one policy
     * with more than one policy alternative
     */
    public static void rejectAlternatives(final PolicyMap map) throws PolicyException {
        for (Policy policy : map) {
            if (policy.getNumberOfAssertionSets() > 1) {
                throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0035_RECONFIGURE_ALTERNATIVES(policy.getIdOrName())));
            }
        }
    }

    /**
     * Inserts all PolicySubjects of type WsdlBindingSubject into the given policy map.
     *
     * @param policyMap The policy map
     * @param policySubjects The policy subjects. The actual subject must have the
     *   type WsdlBindingSubject, otherwise it will not be processed.
     * @param serviceName The name of the current WSDL service
     * @param portName The name of the current WSDL port
     * @throws PolicyException Thrown if the effective policy of a policy subject
     *   could not be computed
     */
    public static void insertPolicies(final PolicyMap policyMap, final Collection<PolicySubject> policySubjects, QName serviceName, QName portName)
            throws PolicyException {
        LOGGER.entering(policyMap, policySubjects, serviceName, portName);

        final HashMap<WsdlBindingSubject, Collection<Policy>> subjectToPolicies = new HashMap<WsdlBindingSubject, Collection<Policy>>();
        for (PolicySubject subject: policySubjects) {
            final Object actualSubject = subject.getSubject();
            if (actualSubject instanceof WsdlBindingSubject) {
                final WsdlBindingSubject wsdlSubject = (WsdlBindingSubject) actualSubject;
                final Collection<Policy> subjectPolicies = new LinkedList<Policy>();
                subjectPolicies.add(subject.getEffectivePolicy(MERGER));
                final Collection<Policy> existingPolicies = subjectToPolicies.put(wsdlSubject, subjectPolicies);
                if (existingPolicies != null) {
                    subjectPolicies.addAll(existingPolicies);
                }
            }
        }

        final PolicyMapKeyConverter converter = new PolicyMapKeyConverter(serviceName, portName);
        for (WsdlBindingSubject wsdlSubject : subjectToPolicies.keySet()) {
            final PolicySubject newSubject = new PolicySubject(wsdlSubject, subjectToPolicies.get(wsdlSubject));
            PolicyMapKey mapKey = converter.getPolicyMapKey(wsdlSubject);

            if (wsdlSubject.isBindingSubject()) {
                policyMap.putSubject(ScopeType.ENDPOINT, mapKey, newSubject);
            }
            else if (wsdlSubject.isBindingOperationSubject()) {
                policyMap.putSubject(ScopeType.OPERATION, mapKey, newSubject);
            }
            else if (wsdlSubject.isBindingMessageSubject()) {
                switch (wsdlSubject.getMessageType()) {
                    case INPUT:
                        policyMap.putSubject(ScopeType.INPUT_MESSAGE, mapKey, newSubject);
                        break;
                    case OUTPUT:
                        policyMap.putSubject(ScopeType.OUTPUT_MESSAGE, mapKey, newSubject);
                        break;
                    case FAULT:
                        policyMap.putSubject(ScopeType.FAULT_MESSAGE, mapKey, newSubject);
                        break;
                }
            }
        }

        LOGGER.exiting();
    }

}
