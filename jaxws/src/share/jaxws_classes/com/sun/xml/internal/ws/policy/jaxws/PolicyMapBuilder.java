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
import com.sun.xml.internal.ws.policy.PolicyMapMutator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Used for populating changes into PolicyMap. Once a PolicyMap is created
 * PolicyMapBuilder notifies all the registered WSPolicyBuilderHandler to populate
 * changes to the PolicyMap.
 *
 *
 * @author Jakub Podlesak (jakub.podlesak at sun.com)
 */
class PolicyMapBuilder {
    /**
     * policyBuilders should contain list of registered PolicyBuilders
     */
    private List<BuilderHandler> policyBuilders = new LinkedList<BuilderHandler>();

    /**
     * Creates a new instance of PolicyMapBuilder
     */
    PolicyMapBuilder() {
        // nothing to initialize
    }

    /**
     *     Registers another builder, which has to be notified after a new
     *     PolicyMap is created in order to populate it's changes.
     *
     */
    void registerHandler(final BuilderHandler builder){
        if (null != builder) {
            policyBuilders.add(builder);
        }
    }

    /**
     * Iterates all the registered PolicyBuilders and lets them populate
     * their changes into PolicyMap. Registers mutators given as a parameter
     * with the newly created map.
     */
    PolicyMap getPolicyMap(final PolicyMapMutator... externalMutators) throws PolicyException{
        return getNewPolicyMap(externalMutators);
    }


    /**
     * Iterates all the registered PolicyBuilders and lets them populate
     * their changes into PolicyMap. Registers mutators from collection given as a parameter
     * with the newly created map.
     */
    private PolicyMap getNewPolicyMap(final PolicyMapMutator... externalMutators) throws PolicyException{
        final HashSet<PolicyMapMutator> mutators = new HashSet<PolicyMapMutator>();
        final PolicyMapExtender myExtender = PolicyMapExtender.createPolicyMapExtender();
        mutators.add(myExtender);
        if (null != externalMutators) {
            mutators.addAll(Arrays.asList(externalMutators));
        }
        final PolicyMap policyMap = PolicyMap.createPolicyMap(mutators);
        for(BuilderHandler builder : policyBuilders){
            builder.populate(myExtender);
        }
        return policyMap;
    }

    void unregisterAll() {
        this.policyBuilders = null;
    }
}
