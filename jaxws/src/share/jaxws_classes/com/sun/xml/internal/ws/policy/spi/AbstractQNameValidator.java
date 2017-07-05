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

package com.sun.xml.internal.ws.policy.spi;

import com.sun.xml.internal.ws.policy.PolicyAssertion;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.xml.namespace.QName;

/**
 * This abstract policy assertion validator validates assertions by their qualified
 * name. Server and client side validation methods return {@link Fitness} based on
 * following schema:
 *
 * <ul>
 * <li>{@link Fitness#SUPPORTED} - if the assertion qualified name is in the list of
 * supported assertion names on the server/client side</li>
 * <li>{@link Fitness#UNSUPPORTED} - if the assertion qualified name is not in the list of
 * supported assertion names on the server/client side, however it is in the list of
 * assertion names supported on the other side</li>
 * <li>{@link Fitness#UNKNOWN} - if the assertion qualified name is not present in the any of
 * the lists of supported assertion names</li>
 * </ul>
 *
 * For some domains such validation may be sufficient enough. Other domains may
 * use functionality of this base class as a first step validation before any attempts
 * to validate content of the assertion. To do this one needs to override and reuse
 * the default behavior of {@link #validateClientSide(PolicyAssertion)} and
 * {@link #validateServerSide(PolicyAssertion)} methods.
 *
 * @author Marek Potociar (marek.potociar at sun.com)
 */
public abstract class AbstractQNameValidator implements PolicyAssertionValidator {
    private final Set<String> supportedDomains = new HashSet<String>();
    private final Collection<QName> serverAssertions;
    private final Collection<QName> clientAssertions;

    /**
     * Constructor that takes two collections specifying qualified names of assertions
     * supported on either server or client side. The set of all assertion namespaces
     * defines list of all domains supported  by the assertion validator
     * (see {@link PolicyAssertionValidator#declareSupportedDomains}).
     *
     * @param serverSideAssertions The server-side assertions.
     * @param clientSideAssertions The client-side assertions.
     */
    protected AbstractQNameValidator(Collection<QName> serverSideAssertions, Collection<QName> clientSideAssertions) {
        if (serverSideAssertions != null) {
            this.serverAssertions = new HashSet<QName>(serverSideAssertions);
            for (QName assertion : this.serverAssertions) {
                supportedDomains.add(assertion.getNamespaceURI());
            }
        } else {
            this.serverAssertions = new HashSet<QName>(0);
        }

        if (clientSideAssertions != null) {
            this.clientAssertions = new HashSet<QName>(clientSideAssertions);
            for (QName assertion : this.clientAssertions) {
                supportedDomains.add(assertion.getNamespaceURI());
            }
        } else {
            this.clientAssertions = new HashSet<QName>(0);
        }
    }

    public String[] declareSupportedDomains() {
        return supportedDomains.toArray(new String[supportedDomains.size()]);
    }

    public Fitness validateClientSide(PolicyAssertion assertion) {
        return validateAssertion(assertion, clientAssertions, serverAssertions);
    }

    public Fitness validateServerSide(PolicyAssertion assertion) {
        return validateAssertion(assertion, serverAssertions, clientAssertions);
    }

    private Fitness validateAssertion(PolicyAssertion assertion, Collection<QName> thisSideAssertions, Collection<QName> otherSideAssertions) {
        QName assertionName = assertion.getName();
        if (thisSideAssertions.contains(assertionName)) {
            return Fitness.SUPPORTED;
        } else if (otherSideAssertions.contains(assertionName)) {
            return Fitness.UNSUPPORTED;
        } else {
            return Fitness.UNKNOWN;
        }
    }
}
