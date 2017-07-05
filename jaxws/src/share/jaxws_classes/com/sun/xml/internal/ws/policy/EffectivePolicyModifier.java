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

/**
 * The class serves as a policy map mutator that allows for replacement of current effective policies
 * stored in the policy map with new effective policy provided by the mutator user.
 *
 * @author Marek Potociar (marek.potociar@sun.com)
 */
public final class EffectivePolicyModifier extends PolicyMapMutator {
    public static EffectivePolicyModifier createEffectivePolicyModifier() {
        return new EffectivePolicyModifier();
    }

    /**
     * Ensures that direct instantiation is not possible from outside of the class
     */
    private EffectivePolicyModifier() {
        // no initialization required
    }

    /**
     * Replaces current effective policy on the service scope (identified by a {@code key} parameter) with the new efective
     * policy provided as a second input parameter. If no policy was defined for the presented key, the new policy is simply
     * stored with the key.
     *
     * @param key identifier of the scope the effective policy should be replaced with the new one. Must not be {@code null}.
     * @param newEffectivePolicy the new policy to replace the old effective policy of the scope. Must not be {@code null}.
     *
     * @throws IllegalArgumentException in case any of the input parameters is {@code null}
     */
    public void setNewEffectivePolicyForServiceScope(
            final PolicyMapKey key, final Policy newEffectivePolicy) {
        getMap().setNewEffectivePolicyForScope(PolicyMap.ScopeType.SERVICE, key, newEffectivePolicy);
    }

    /**
     * Replaces current effective policy on the endpoint scope (identified by a {@code key} parameter) with the new efective
     * policy provided as a second input parameter.
     *
     * @param key identifier of the scope the effective policy should be replaced with the new one. Must not be {@code null}.
     * @param newEffectivePolicy the new policy to replace the old effective policy of the scope. Must not be {@code null}.
     *
     * @throws IllegalArgumentException in case any of the input parameters is {@code null}
     */
    public void setNewEffectivePolicyForEndpointScope(
            final PolicyMapKey key, final Policy newEffectivePolicy) {
        getMap().setNewEffectivePolicyForScope(PolicyMap.ScopeType.ENDPOINT, key, newEffectivePolicy);
    }

    /**
     * Replaces current effective policy on the operation scope (identified by a {@code key} parameter) with the new efective
     * policy provided as a second input parameter. If no policy was defined for the presented key, the new policy is simply
     * stored with the key.
     *
     * @param key identifier of the scope the effective policy should be replaced with the new one. Must not be {@code null}.
     * @param newEffectivePolicy the new policy to replace the old effective policy of the scope. Must not be {@code null}.
     *
     * @throws IllegalArgumentException in case any of the input parameters is {@code null}
     */
    public void setNewEffectivePolicyForOperationScope(
            final PolicyMapKey key, final Policy newEffectivePolicy) {
        getMap().setNewEffectivePolicyForScope(PolicyMap.ScopeType.OPERATION, key, newEffectivePolicy);
    }

    /**
     * Replaces current effective policy on the input message scope (identified by a {@code key} parameter) with the new efective
     * policy provided as a second input parameter. If no policy was defined for the presented key, the new policy is simply
     * stored with the key.
     *
     * @param key identifier of the scope the effective policy should be replaced with the new one. Must not be {@code null}.
     * @param newEffectivePolicy the new policy to replace the old effective policy of the scope. Must not be {@code null}.
     *
     * @throws IllegalArgumentException in case any of the input parameters is {@code null}
     */
    public void setNewEffectivePolicyForInputMessageScope(
            final PolicyMapKey key, final Policy newEffectivePolicy) {
        getMap().setNewEffectivePolicyForScope(PolicyMap.ScopeType.INPUT_MESSAGE, key, newEffectivePolicy);
    }

    /**
     * Replaces current effective policy on the output message scope (identified by a {@code key} parameter) with the new efective
     * policy provided as a second input parameter. If no policy was defined for the presented key, the new policy is simply
     * stored with the key.
     *
     * @param key identifier of the scope the effective policy should be replaced with the new one. Must not be {@code null}.
     * @param newEffectivePolicy the new policy to replace the old effective policy of the scope. Must not be {@code null}.
     *
     * @throws IllegalArgumentException in case any of the input parameters is {@code null}
     */
    public void setNewEffectivePolicyForOutputMessageScope(
            final PolicyMapKey key, final Policy newEffectivePolicy) {
        getMap().setNewEffectivePolicyForScope(PolicyMap.ScopeType.OUTPUT_MESSAGE, key, newEffectivePolicy);
    }

    /**
     * Replaces current effective policy on the fault message scope (identified by a {@code key} parameter) with the new efective
     * policy provided as a second input parameter. If no policy was defined for the presented key, the new policy is simply
     * stored with the key.
     *
     * @param key identifier of the scope the effective policy should be replaced with the new one. Must not be {@code null}.
     * @param newEffectivePolicy the new policy to replace the old effective policy of the scope. Must not be {@code null}.
     *
     * @throws IllegalArgumentException in case any of the input parameters is {@code null}
     */
    public void setNewEffectivePolicyForFaultMessageScope(
            final PolicyMapKey key, final Policy newEffectivePolicy) {
        getMap().setNewEffectivePolicyForScope(PolicyMap.ScopeType.FAULT_MESSAGE, key, newEffectivePolicy);
    }
}
