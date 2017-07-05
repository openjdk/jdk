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

import com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;

/**
 * The class serves as a base for specific policy map mutator implementations. It provides common methods that allow
 * concrete mutator implementations to connect and disconnect to/from a policy map instance.
 *
 * @author Marek Potociar (marek.potociar@sun.com)
 */
public abstract class PolicyMapMutator {

    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(PolicyMapMutator.class);

    private PolicyMap map = null;

    /**
     * Creates a new instance of PolicyMapMutator. This class cannot be extended from outside of this package.
     */
    PolicyMapMutator() {
        // nothing to instantiate
    }

    /**
     * The method is used to connect the policy map mutator instance to the map it should mutate.
     *
     * @param map the policy map instance that will be mutable by this mutator.
     * @throws IllegalStateException in case this mutator object is already connected to a policy map.
     */
    public void connect(final PolicyMap map) {
        if (isConnected()) {
            throw LOGGER.logSevereException(new IllegalStateException(LocalizationMessages.WSP_0044_POLICY_MAP_MUTATOR_ALREADY_CONNECTED()));
        }

        this.map = map;
    }

    /**
     * Can be used to retrieve the policy map currently connected to this mutator. Will return {@code null} if not connected.
     *
     * @return policy map currently connected to this mutator. May return {@code null} if the mutator is not connected.
     *
     * @see #isConnected()
     * @see #disconnect()
     */
    public PolicyMap getMap() {
        return this.map;
    }

    /**
     * Disconnects the mutator from the policy map object it is connected to. Method must be called prior to connecting this
     * mutator instance to another policy map.
     * <p/>
     * This operation is irreversible: you cannot connect the mutator to the same policy map instance once you disconnect from it.
     * Multiple consequent calls of this method will have no effect.
     */
    public void disconnect() {
        this.map = null;
    }

    /**
     * This method provides connection status information of the policy map mutator instance.
     *
     * @return {@code true} if the mutator instance is connected to a policy map, otherwise returns {@code false}.
     */
    public boolean isConnected() {
        return this.map != null;
    }
}
