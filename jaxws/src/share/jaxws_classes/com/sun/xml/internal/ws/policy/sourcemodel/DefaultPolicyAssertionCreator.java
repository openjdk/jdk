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

package com.sun.xml.internal.ws.policy.sourcemodel;

import com.sun.xml.internal.ws.policy.*;
import com.sun.xml.internal.ws.policy.spi.AssertionCreationException;
import com.sun.xml.internal.ws.policy.spi.PolicyAssertionCreator;
import java.util.Collection;

/**
 * Default implementation of a policy assertion creator. This implementation is used to create policy assertions in case
 * no domain specific policy assertion creator is registered for the namespace of the policy assertion.
 *
 * This is the only PolicyAssertionCreator implementation that is allowed to break general contract, claiming that
 * {@code getSupportedDomainNamespaceUri()} must not return empty String without causing PolicyAssertionCreator registration
 * fail.
 *
 * @author Marek Potociar (marek.potociar at sun.com)
 */
class DefaultPolicyAssertionCreator implements PolicyAssertionCreator {
    private static final class DefaultPolicyAssertion extends PolicyAssertion {
        DefaultPolicyAssertion(AssertionData data, Collection<PolicyAssertion> assertionParameters, AssertionSet nestedAlternative) {
            super (data, assertionParameters, nestedAlternative);
        }
    }

    /**
     * Creates a new instance of DefaultPolicyAssertionCreator
     */
    DefaultPolicyAssertionCreator() {
        // nothing to initialize
    }

    /**
     * See {@link PolicyAssertionCreator#getSupportedDomainNamespaceURIs() method documentation in interface}
     */
    public String[] getSupportedDomainNamespaceURIs() {
        return null;
    }

    /**
     * See {@link PolicyAssertionCreator#createAssertion(AssertionData, Collection, AssertionSet, PolicyAssertionCreator) method documentation in interface}
     */
    public PolicyAssertion createAssertion(final AssertionData data, final Collection<PolicyAssertion> assertionParameters, final AssertionSet nestedAlternative, final PolicyAssertionCreator defaultCreator) throws AssertionCreationException {
        return new DefaultPolicyAssertion(data, assertionParameters, nestedAlternative);
    }
}
