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

import java.util.Arrays;
import java.util.Iterator;

/**
 * A special policy implementation that assures that only no or single policy alternative is possible within this type of policy.
 *
 * @author Marek Potociar
 */
public final class NestedPolicy extends Policy {
    private static final String NESTED_POLICY_TOSTRING_NAME = "nested policy";

    private NestedPolicy(final AssertionSet set) {
        super(NESTED_POLICY_TOSTRING_NAME, Arrays.asList(new AssertionSet[] { set }));
    }

    private NestedPolicy(final String name, final String policyId, final AssertionSet set) {
        super(NESTED_POLICY_TOSTRING_NAME, name, policyId, Arrays.asList(new AssertionSet[] { set }));
    }

    static NestedPolicy createNestedPolicy(final AssertionSet set) {
        return new NestedPolicy(set);
    }

    static NestedPolicy createNestedPolicy(final String name, final String policyId, final AssertionSet set) {
        return new NestedPolicy(name, policyId, set);
    }

    /**
     * Returns the AssertionSet instance representing a single policy alterantive held wihtin this nested policy object.
     * If the nested policy represents a policy with no alternatives (i.e. nothing is allowed) the method returns {@code null}.
     *
     * @return nested policy alternative represented by AssertionSet object. May return {@code null} in case the nested policy
     * represents 'nothing allowed' policy.
     */
    public AssertionSet getAssertionSet() {
        final Iterator<AssertionSet> iterator = iterator();
        if (iterator.hasNext()) {
            return iterator.next();
        } else {
            return null;
        }
    }

    /**
     * An {@code Object.equals(Object obj)} method override.
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof NestedPolicy)) {
            return false;
        }

        final NestedPolicy that = (NestedPolicy) obj;

        return super.equals(that);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
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
    @Override
    StringBuffer toString(final int indentLevel, final StringBuffer buffer) {
        return super.toString(indentLevel, buffer);
    }
}
