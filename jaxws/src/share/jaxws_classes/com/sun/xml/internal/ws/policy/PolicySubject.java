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
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * A PolicySubject is an entity (e.g., a port, operation, binding,
 * service) with which a policy can be associated.
 *
 * @author Fabian Ritzmann
 */
public final class PolicySubject {
    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(PolicySubject.class);

    private final List<Policy> policies = new LinkedList<Policy>();
    private final Object subject;

    /**
     * Constructs a policy subject instance.
     *
     * @param subject object to which the policies are attached. Must not be {@code null}.
     * @param policy first policy attached to the subject. Must not be {@code null}.
     *
     * @throws IllegalArgumentException in case any of the arguments is {@code null}.
     */
    public PolicySubject(Object subject, Policy policy) throws IllegalArgumentException {
        if (subject == null || policy == null) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0021_SUBJECT_AND_POLICY_PARAM_MUST_NOT_BE_NULL(subject, policy)));
        }

        this.subject = subject;
        this.attach(policy);
    }

    /**
     * Constructs a policy subject instance.
     *
     * @param subject object to which the policies are attached. Must not be {@code null}.
     * @param policies first policy attached to the subject. Must not be {@code null}.
     *
     * @throws IllegalArgumentException in case any of the arguments is {@code null} or
     *         in case {@code policies} argument represents empty collection.
     */
    public PolicySubject(Object subject, Collection<Policy> policies) throws IllegalArgumentException {
        if (subject == null || policies == null) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0062_INPUT_PARAMS_MUST_NOT_BE_NULL()));
        }

        if (policies.isEmpty()) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0064_INITIAL_POLICY_COLLECTION_MUST_NOT_BE_EMPTY()));
        }

        this.subject = subject;
        this.policies.addAll(policies);
    }

    /**
     * Attaches another Policy instance to the policy subject.
     *
     * @param policy new policy instance to be attached to this subject
     *
     * @throws IllegalArgumentException in case {@code policy} argument is {@code null}.
     */
    public void attach(final Policy policy) {
        if (policy == null) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0038_POLICY_TO_ATTACH_MUST_NOT_BE_NULL()));
        }
        this.policies.add(policy);
    }

    /**
     * Returns the effective policy of the subject, i.e. all policies of the subject
     * merged together.
     *
     * @return effective policy of the subject
     */
    public Policy getEffectivePolicy(final PolicyMerger merger) throws PolicyException {
        return merger.merge(policies);
    }

    /**
     * A unique identifier of the subject
     *
     * Subjects may not always be uniquely identifiable. Also, once the subject is
     * assigned to a scope, its identity may not matter anymore. Therefore this
     * may be null.
     */
    public Object getSubject() {
        return this.subject;
    }

    /**
     * An {@code Object.toString()} method override.
     */
    @Override
    public String toString() {
        return toString(0, new StringBuffer()).toString();
    }

    /**
     * A helper method that appends indented string representation of this instance to the input string buffer.
     *
     * @param indentLevel indentation level to be used.
     * @param buffer buffer to be used for appending string representation of this instance
     * @return modified buffer containing new string representation of the instance
     */
    StringBuffer toString(final int indentLevel, final StringBuffer buffer) {
        final String indent = PolicyUtils.Text.createIndent(indentLevel);
        final String innerIndent = PolicyUtils.Text.createIndent(indentLevel + 1);

        buffer.append(indent).append("policy subject {").append(PolicyUtils.Text.NEW_LINE);
        buffer.append(innerIndent).append("subject = '").append(subject).append('\'').append(PolicyUtils.Text.NEW_LINE);
        for (Policy policy : policies) {
            policy.toString(indentLevel + 1, buffer).append(PolicyUtils.Text.NEW_LINE);
        }
        buffer.append(indent).append('}');

        return buffer;
    }
}
