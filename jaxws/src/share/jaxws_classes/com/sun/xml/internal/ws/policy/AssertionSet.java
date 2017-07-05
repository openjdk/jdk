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

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.namespace.QName;

import com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import java.util.LinkedList;
import java.util.List;

/**
 * The AssertionSet is a set of assertions. It represents a single policy alternative.
 *
 * @author Fabian Ritzmann, Marek Potociar
 */
public final class AssertionSet implements Iterable<PolicyAssertion>, Comparable<AssertionSet> {
    private static final AssertionSet EMPTY_ASSERTION_SET = new AssertionSet(Collections.unmodifiableList(new LinkedList<PolicyAssertion>()));
    /**
     * The comparator comapres policy assertions according to their publicly accessible attributes, in the following
     * order of attributes:
     *
     * 1. namespace (not null String)
     * 2. local name (not null String)
     * 3. value (String): null < "" < "not empty"
     * 4. has nested assertions (boolean): false < true
     * 5. has nested policy (boolean): false < true
     * 6. hashCode comparison
     */
    private static final Comparator<PolicyAssertion> ASSERTION_COMPARATOR = new Comparator<PolicyAssertion>() {
        public int compare(final PolicyAssertion pa1, final PolicyAssertion pa2) {
            if (pa1.equals(pa2)) {
                return 0;
            }

            int result;

            result = PolicyUtils.Comparison.QNAME_COMPARATOR.compare(pa1.getName(), pa2.getName());
            if (result != 0) {
                return result;
            }

            result = PolicyUtils.Comparison.compareNullableStrings(pa1.getValue(), pa2.getValue());
            if (result != 0) {
                return result;
            }

            result = PolicyUtils.Comparison.compareBoolean(pa1.hasNestedAssertions(), pa2.hasNestedAssertions());
            if (result != 0) {
                return result;
            }

            result = PolicyUtils.Comparison.compareBoolean(pa1.hasNestedPolicy(), pa2.hasNestedPolicy());
            if (result != 0) {
                return result;
            }

            return Math.round(Math.signum(pa1.hashCode() - pa2.hashCode()));
        }
    };

    private final List<PolicyAssertion> assertions;
    private final Set<QName> vocabulary = new TreeSet<QName>(PolicyUtils.Comparison.QNAME_COMPARATOR);
    private final Collection<QName> immutableVocabulary = Collections.unmodifiableCollection(vocabulary);

    private AssertionSet(List<PolicyAssertion> list) {
        assert (list != null) : LocalizationMessages.WSP_0037_PRIVATE_CONSTRUCTOR_DOES_NOT_TAKE_NULL();
        this.assertions = list;
    }

    private AssertionSet(final Collection<AssertionSet> alternatives) {
        this.assertions = new LinkedList<PolicyAssertion>();
        for (AssertionSet alternative : alternatives) {
            addAll(alternative.assertions);
        }
    }

    private boolean add(final PolicyAssertion assertion) {
        if (assertion == null) {
            return false;
        }

        if (this.assertions.contains(assertion)) {
            return false;
        } else {
            this.assertions.add(assertion);
            this.vocabulary.add(assertion.getName());
            return true;
        }
    }

    private boolean addAll(final Collection<? extends PolicyAssertion> assertions) {
        boolean result = true;

        if (assertions != null) {
            for (PolicyAssertion assertion : assertions) {
                result &= add(assertion); // this is here to ensure that vocabulary is built correctly as well
            }
        }

        return result;
    }

    /**
     * Return all assertions contained in this assertion set.
     *
     * @return All assertions contained in this assertion set
     */
    Collection<PolicyAssertion> getAssertions() {
        return assertions;
    }

    /**
     * Retrieves the vocabulary of this policy expression. The vocabulary is represented by an immutable collection of
     * unique QName objects. Each of those objects represents single assertion type contained in the assertion set.
     *
     * @return immutable collection of assertion types contained in the assertion set (a policy vocabulary).
     */
    Collection<QName> getVocabulary() {
        return immutableVocabulary;
    }

    /**
     * Checks whether this policy alternative is compatible with the provided policy alternative.
     *
     * @param alternative policy alternative used for compatibility test
     * @param mode compatibility mode to be used
     * @return {@code true} if the two policy alternatives are compatible, {@code false} otherwise
     */
    boolean isCompatibleWith(final AssertionSet alternative, PolicyIntersector.CompatibilityMode mode) {
        boolean result = (mode == PolicyIntersector.CompatibilityMode.LAX) || this.vocabulary.equals(alternative.vocabulary);

        result = result && this.areAssertionsCompatible(alternative, mode);
        result = result && alternative.areAssertionsCompatible(this, mode);

        return result;
    }

    private boolean areAssertionsCompatible(final AssertionSet alternative, PolicyIntersector.CompatibilityMode mode) {
        nextAssertion: for (PolicyAssertion thisAssertion : this.assertions) {
            if ((mode == PolicyIntersector.CompatibilityMode.STRICT) || !thisAssertion.isIgnorable()) {
                for (PolicyAssertion thatAssertion : alternative.assertions) {
                    if (thisAssertion.isCompatibleWith(thatAssertion, mode)) {
                        continue nextAssertion;
                    }
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Creates and returns new assertion set holding content of all provided policy assertion sets.
     * <p/>
     * This method should not be used to perform a merge of general Policy instances. A client should be aware of the
     * method's result meaning and the difference between merge of Policy instances and merge of AssertionSet instances.
     *
     *
     * @param alternatives collection of provided policy assertion sets which content is to be stored in the assertion set.
     *        May be {@code null} - empty assertion set is returned in such case.
     * @return new instance of assertion set holding the content of all provided policy assertion sets.
     */
    public static AssertionSet createMergedAssertionSet(final Collection<AssertionSet> alternatives) {
        if (alternatives == null || alternatives.isEmpty()) {
            return EMPTY_ASSERTION_SET;
        }

        final AssertionSet result = new AssertionSet(alternatives);
        Collections.sort(result.assertions, ASSERTION_COMPARATOR);

        return result;
    }

    /**
     * Creates and returns new assertion set holding a set of provided policy assertions.
     *
     * @param assertions collection of provided policy assertions to be stored in the assertion set. May be {@code null}.
     * @return new instance of assertion set holding the provided policy assertions
     */
    public static AssertionSet createAssertionSet(final Collection<? extends PolicyAssertion> assertions) {
        if (assertions == null || assertions.isEmpty()) {
            return EMPTY_ASSERTION_SET;
        }

        final AssertionSet result = new AssertionSet(new LinkedList<PolicyAssertion>());
        result.addAll(assertions);
        Collections.sort(result.assertions, ASSERTION_COMPARATOR);

        return result;
    }

    public static AssertionSet emptyAssertionSet() {
        return EMPTY_ASSERTION_SET;
    }
    /**
     * Returns an iterator over a set of child policy assertion objects.
     *
     * @return policy assertion Iterator.
     */
    public Iterator<PolicyAssertion> iterator() {
        return this.assertions.iterator();
    }

    /**
     * Searches for assertions with given name. Only assertions that are contained as immediate children of the assertion set are
     * searched, i.e. nested policies are not searched.
     *
     * @param name The fully qualified name of searched assertion
     * @return List of all assertions matching the requested name. If no assertions are found, the returned list is empty
     * (i.e. {@code null} value is never returned).
     */
    public Collection<PolicyAssertion> get(final QName name) {
        final List<PolicyAssertion> matched = new LinkedList<PolicyAssertion>();

        if (vocabulary.contains(name)) {
            // we iterate the assertion set only if we are sure we contain such assertion name in our vocabulary
            for (PolicyAssertion assertion : assertions) {
                if (assertion.getName().equals(name)) {
                    matched.add(assertion);
                }
            }
        }

        return matched;
    }

    /**
     * Returns {@code true} if this assertion set contains no assertions.
     *
     * @return {@code true} if this assertion set contains no assertions.
     */
    public boolean isEmpty() {
        return assertions.isEmpty();
    }

    /**
     * Returns true if the assertion set contains the assertion name specified in its vocabulary
     *
     * @param assertionName the fully qualified name of the assertion
     * @return {@code true}, if an assertion with the given name could be found in the assertion set vocabulary {@code false} otherwise.
     */
    public boolean contains(final QName assertionName) {
        return vocabulary.contains(assertionName);
    }

    /**
     * An {@code Comparable<T>.compareTo(T o)} interface method implementation.
     * @param that other alternative to compare with
     */
    public int compareTo(final AssertionSet that) {
        if (this.equals(that)) {
            return 0;
        }

        // comparing vocabularies
        final Iterator<QName> vIterator1 = this.getVocabulary().iterator();
        final Iterator<QName> vIterator2 = that.getVocabulary().iterator();
        while (vIterator1.hasNext()) {
            final QName entry1 = vIterator1.next();
            if (vIterator2.hasNext()) {
                final QName entry2 = vIterator2.next();
                final int result = PolicyUtils.Comparison.QNAME_COMPARATOR.compare(entry1, entry2);
                if (result != 0) {
                    return result;
                }
            } else {
                return 1; // we have more entries in this vocabulary
            }
        }

        if (vIterator2.hasNext()) {
            return -1;  // we have more entries in that vocabulary
        }

        // vocabularies are equal => comparing assertions
        final Iterator<PolicyAssertion> pIterator1 = this.getAssertions().iterator();
        final Iterator<PolicyAssertion> pIterator2 = that.getAssertions().iterator();
        while (pIterator1.hasNext()) {
            final PolicyAssertion pa1 = pIterator1.next();
            if (pIterator2.hasNext()) {
                final PolicyAssertion pa2 = pIterator2.next();
                final int result = ASSERTION_COMPARATOR.compare(pa1, pa2);
                if (result != 0) {
                    return result;
                }
            } else {
                return 1; // we have more entries in this assertion set
            }
        }

        if (pIterator2.hasNext()) {
            return -1;  // we have more entries in that assertion set
        }

        // seems like objects are very simmilar although not equal => we must not return 0 otherwise the TreeSet
        // holding this element would discard the newly added element. Thus we return that the first argument is
        // greater than second (just because it is first...)
        return 1;
    }

    /**
     * An {@code Object.equals(Object obj)} method override.
     */
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof AssertionSet)) {
            return false;
        }

        final AssertionSet that = (AssertionSet) obj;
        boolean result = true;

        result = result && this.vocabulary.equals(that.vocabulary);
        result = result && this.assertions.size() == that.assertions.size() && this.assertions.containsAll(that.assertions);

        return result;
    }

    /**
     * An {@code Object.hashCode()} method override.
     */
    public int hashCode() {
        int result = 17;

        result = 37 * result + vocabulary.hashCode();
        result = 37 * result + assertions.hashCode();

        return result;
    }

    /**
     * An {@code Object.toString()} method override.
     */
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

        buffer.append(indent).append("assertion set {").append(PolicyUtils.Text.NEW_LINE);

        if (assertions.isEmpty()) {
            buffer.append(innerIndent).append("no assertions").append(PolicyUtils.Text.NEW_LINE);
        } else {
            for (PolicyAssertion assertion : assertions) {
                assertion.toString(indentLevel + 1, buffer).append(PolicyUtils.Text.NEW_LINE);
            }
        }

        buffer.append(indent).append('}');

        return buffer;
    }
}
