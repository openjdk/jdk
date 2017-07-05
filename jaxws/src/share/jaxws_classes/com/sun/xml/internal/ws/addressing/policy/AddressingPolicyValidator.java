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

package com.sun.xml.internal.ws.addressing.policy;

import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.policy.PolicyAssertion;
import com.sun.xml.internal.ws.policy.NestedPolicy;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;
import com.sun.xml.internal.ws.policy.spi.PolicyAssertionValidator;
import com.sun.xml.internal.ws.addressing.W3CAddressingMetadataConstants;

import java.util.ArrayList;
import javax.xml.namespace.QName;


/**
 * This class validates the Addressing assertions.
 * If the assertion is wsam:Addressing, it makes sure that only valid assertions are nested.
 *
 * @author japod
 * @author Rama Pulavarthi
 */
public class AddressingPolicyValidator implements PolicyAssertionValidator {

    private static final ArrayList<QName> supportedAssertions = new ArrayList<QName>();

    static {
        supportedAssertions.add(new QName(AddressingVersion.MEMBER.policyNsUri, "UsingAddressing"));
        supportedAssertions.add(W3CAddressingMetadataConstants.WSAM_ADDRESSING_ASSERTION);
        supportedAssertions.add(W3CAddressingMetadataConstants.WSAM_ANONYMOUS_NESTED_ASSERTION);
        supportedAssertions.add(W3CAddressingMetadataConstants.WSAM_NONANONYMOUS_NESTED_ASSERTION);
    }

    /**
     * Creates a new instance of AddressingPolicyValidator
     */
    public AddressingPolicyValidator() {
    }

    public Fitness validateClientSide(PolicyAssertion assertion) {
        return supportedAssertions.contains(assertion.getName()) ? Fitness.SUPPORTED : Fitness.UNKNOWN;
    }

    public Fitness validateServerSide(PolicyAssertion assertion) {
        if (!supportedAssertions.contains(assertion.getName()))
            return Fitness.UNKNOWN;

        //Make sure wsam:Addressing contains only one of the allowed nested assertions.
        if (assertion.getName().equals(W3CAddressingMetadataConstants.WSAM_ADDRESSING_ASSERTION)) {
            NestedPolicy nestedPolicy = assertion.getNestedPolicy();
            if (nestedPolicy != null) {
                boolean requiresAnonymousResponses = false;
                boolean requiresNonAnonymousResponses = false;
                for (PolicyAssertion nestedAsser : nestedPolicy.getAssertionSet()) {
                    if (nestedAsser.getName().equals(W3CAddressingMetadataConstants.WSAM_ANONYMOUS_NESTED_ASSERTION)) {
                        requiresAnonymousResponses = true;
                    } else if (nestedAsser.getName().equals(W3CAddressingMetadataConstants.WSAM_NONANONYMOUS_NESTED_ASSERTION)) {
                        requiresNonAnonymousResponses = true;
                    } else {
                        LOGGER.warning("Found unsupported assertion:\n" + nestedAsser + "\nnested into assertion:\n" + assertion);
                        return Fitness.UNSUPPORTED;
                    }
                }

                if (requiresAnonymousResponses && requiresNonAnonymousResponses) {
                    LOGGER.warning("Only one among AnonymousResponses and NonAnonymousResponses can be nested in an Addressing assertion");
                    return Fitness.INVALID;
                }
            }
        }

        return Fitness.SUPPORTED;
    }

    public String[] declareSupportedDomains() {
        return new String[]{AddressingVersion.MEMBER.policyNsUri, AddressingVersion.W3C.policyNsUri, W3CAddressingMetadataConstants.WSAM_NAMESPACE_NAME};
    }

    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(AddressingPolicyValidator.class);
}
