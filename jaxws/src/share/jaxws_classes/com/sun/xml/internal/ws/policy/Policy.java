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

package com.sun.xml.internal.ws.policy;

import com.sun.xml.internal.ws.policy.sourcemodel.wspolicy.NamespaceVersion;
import com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.namespace.QName;

/**
 * A policy represents normalized policy as a wrapper of available policy alternatives represented by
 * child {@link AssertionSet AssertionSets}.
 *
 * @author Fabian Ritzmann, Marek Potociar
 */
public class Policy implements Iterable<AssertionSet> {
    /**
     * A string constant used in package private constructor to customize the object's toString() method output.
     */
    private static final String POLICY_TOSTRING_NAME = "policy";

    /**
     * Constant represents empty list of assertion sets. This represents the content of a 'NULL' policy - a policy with
     * no alternatives. The constant supports memory effective creation of 'NULL' policy objects.
     */
    private static final List<AssertionSet> NULL_POLICY_ASSERTION_SETS = Collections.unmodifiableList(new LinkedList<AssertionSet>());

    /**
     * Constant represents list of assertion sets with single empty assertion set. This represents the content of
     * an 'EMPTY' policy - a policy with a single empty alternative. The constant supports memory effective creation
     * of 'EMPTY' policy objects.
     */
    private static final List<AssertionSet> EMPTY_POLICY_ASSERTION_SETS = Collections.unmodifiableList(new LinkedList<AssertionSet>(Arrays.asList(new AssertionSet[] {AssertionSet.emptyAssertionSet()})));

    /**
     * Constant represents empty vocabulary of a 'NULL' or 'EMPTY' policies. The constant supports memory effective
     * creation of 'NULL' and 'EMPTY' policy objects.
     */
    private static final Set<QName> EMPTY_VOCABULARY = Collections.unmodifiableSet(new TreeSet<QName>(PolicyUtils.Comparison.QNAME_COMPARATOR));

    /**
     * Constant representation of all 'NULL' policies returned by createNullPolicy() factory method. This is to optimize
     * the memory footprint.
     */
    private static final Policy ANONYMOUS_NULL_POLICY = new Policy(null, null, NULL_POLICY_ASSERTION_SETS, EMPTY_VOCABULARY);

    /**
     * Constant representation of all 'EMPTY' policies returned by createEmptyPolicy() factory method. This constant is
     * to optimize the memory footprint.
     */
    private static final Policy ANONYMOUS_EMPTY_POLICY = new Policy(null, null, EMPTY_POLICY_ASSERTION_SETS, EMPTY_VOCABULARY);

    /**
     * Policy ID holder
     */
    private String policyId;

    /**
     * Policy name holder
     */
    private String name;

    /**
     * Namespace version holder
     */
    private NamespaceVersion nsVersion;

    /**
     * internal collection of policy alternatives
     */
    private final List<AssertionSet> assertionSets;

    /**
     * internal collection of policy vocabulary entries (qualified names of all assertion types present in the policy expression)
     */
    private final Set<QName> vocabulary;

    /**
     * immutable version of policy vocabulary that is made available to clients via getter method
     */
    private final Collection<QName> immutableVocabulary;

    /**
     * policy object name used in a toString() method. This ensures that Policy class children can customize
     * (via package private Policy constructors) the toString() method without having to override it.
     */
    private final String toStringName;

    /**
     * The factory method creates an <b>immutable</b> policy instance which represents a <emph>'nothing allowed'</emph>
     * policy expression. The policy is created using the latest namespace version supported.
     *
     * @return policy instance which represents a <emph>'nothing allowed'</emph> (no policy alternatives).
     */
    public static Policy createNullPolicy() {
        return ANONYMOUS_NULL_POLICY;
    }

    /**
     * The factory method creates an <b>immutable</b> policy instance which represents a <emph>'anything allowed'</emph>
     * policy expression. The policy is created using the latest namespace version supported.
     *
     * @return policy instance which represents a <emph>'anything allowed'</emph> (empty policy alternative with no plicy
     * assertions prescribed).
     */
    public static Policy createEmptyPolicy() {
        return ANONYMOUS_EMPTY_POLICY;
    }

    /**
     * The factory method creates an <b>immutable</b> policy instance which represents a <emph>'nothing allowed'</emph>
     * policy expression. The policy is created using the latest namespace version supported.
     *
     * @param name global URI of the policy. May be {@code null}.
     * @param policyId local URI of the policy. May be {@code null}.
     * @return policy instance which represents a <emph>'nothing allowed'</emph> (no policy alternatives).
     */
    public static Policy createNullPolicy(final String name, final String policyId) {
        if (name == null && policyId == null) {
            return ANONYMOUS_NULL_POLICY;
        } else {
            return new Policy(name, policyId, NULL_POLICY_ASSERTION_SETS, EMPTY_VOCABULARY);
        }
    }

    /**
     * The factory method creates an <b>immutable</b> policy instance which represents a <emph>'nothing allowed'</emph>
     * policy expression. The policy is created using the latest namespace version supported.
     *
     * @param nsVersion Policy namespace version to be used when marshalling the policy expression
     * @param name global URI of the policy. May be {@code null}.
     * @param policyId local URI of the policy. May be {@code null}.
     * @return policy instance which represents a <emph>'nothing allowed'</emph> (no policy alternatives).
     */
    public static Policy createNullPolicy(final NamespaceVersion nsVersion, final String name, final String policyId) {
        if ((nsVersion == null || nsVersion == NamespaceVersion.getLatestVersion()) && name == null && policyId == null) {
            return ANONYMOUS_NULL_POLICY;
        } else {
            return new Policy(nsVersion, name, policyId, NULL_POLICY_ASSERTION_SETS, EMPTY_VOCABULARY);
        }
    }

    /**
     * The factory method creates an <b>immutable</b> policy instance which represents a <emph>'anything allowed'</emph>
     * policy expression. The policy is created using the latest namespace version supported.
     *
     * @param name global URI of the policy. May be {@code null}.
     * @param policyId local URI of the policy. May be {@code null}.
     *
     * @return policy instance which represents a <emph>'anything allowed'</emph> (empty policy alternative with no plicy
     * assertions prescribed).
     */
    public static Policy createEmptyPolicy(final String name, final String policyId) {
        if (name == null && policyId == null) {
            return ANONYMOUS_EMPTY_POLICY;
        } else {
            return new Policy(name, policyId, EMPTY_POLICY_ASSERTION_SETS, EMPTY_VOCABULARY);
        }
    }

    /**
     * The factory method creates an <b>immutable</b> policy instance which represents a <emph>'anything allowed'</emph>
     * policy expression. The policy is created using the latest namespace version supported.
     *
     * @param nsVersion Policy namespace version to be used when marshalling the policy expression
     * @param name global URI of the policy. May be {@code null}.
     * @param policyId local URI of the policy. May be {@code null}.
     *
     * @return policy instance which represents a <emph>'anything allowed'</emph> (empty policy alternative with no plicy
     * assertions prescribed).
     */
    public static Policy createEmptyPolicy(final NamespaceVersion nsVersion, final String name, final String policyId) {
        if ((nsVersion == null || nsVersion == NamespaceVersion.getLatestVersion()) && name == null && policyId == null) {
            return ANONYMOUS_EMPTY_POLICY;
        } else {
            return new Policy(nsVersion, name, policyId, EMPTY_POLICY_ASSERTION_SETS, EMPTY_VOCABULARY);
        }
    }

    /**
     * The factory method creates an <b>immutable</b> policy instance which represents a policy expression with
     * alternatives specified by {@code sets} input parameter. If the collection of policy alternatives is null or empty
     * an object representing a 'NULL' policy expression is returned. However, in such case it is better to use
     * {@link #createNullPolicy()} factory method directly. The policy is created using the latest namespace version supported.
     *
     * @param sets represents the collection of policy alternatives of the policy object created. During the creation of
     * the new policy object, the content of the alternatives collection is copied into an internal policy object structure,
     * thus any subsequent operations on the collection will have no impact on the newly constructed policy object.
     *
     * @return policy instance which represents the policy with given alternatives.
     */
    public static Policy createPolicy(final Collection<AssertionSet> sets) {
        if (sets == null || sets.isEmpty()) {
            return createNullPolicy();
        } else {
            return new Policy(POLICY_TOSTRING_NAME, sets);
        }
    }

    /**
     * The factory method creates an <b>immutable</b> policy instance which represents a policy expression with
     * alternatives specified by {@code sets} input parameter. If the collection of policy alternatives is null or empty
     * an object representing a 'NULL' policy expression is returned. However, in such case it is better to use
     * {@link #createNullPolicy(String, String)} factory method directly. The policy is created using the latest namespace version supported.
     *
     * @param name global URI of the policy. May be {@code null}.
     * @param policyId local URI of the policy. May be {@code null}.
     * @param sets represents the collection of policy alternatives of the policy object created. During the creation of
     * the new policy object, the content of the alternatives collection is copied into an internal policy object structure,
     * thus any subsequent operations on the collection will have no impact on the newly constructed policy object.
     *
     * @return policy instance which represents the policy with given alternatives.
     */
    public static Policy createPolicy(final String name, final String policyId, final Collection<AssertionSet> sets) {
        if (sets == null || sets.isEmpty()) {
            return createNullPolicy(name, policyId);
        } else {
            return new Policy(POLICY_TOSTRING_NAME, name, policyId, sets);
        }
    }

    /**
     * The factory method creates an <b>immutable</b> policy instance which represents a policy expression with
     * alternatives specified by {@code sets} input parameter. If the collection of policy alternatives is null or empty
     * an object representing a 'NULL' policy expression is returned. However, in such case it is better to use
     * {@link #createNullPolicy(String, String)} factory method directly. The policy is created using the latest namespace version supported.
     *
     * @param nsVersion Policy namespace version to be used when marshalling the policy expression
     * @param name global URI of the policy. May be {@code null}.
     * @param policyId local URI of the policy. May be {@code null}.
     * @param sets represents the collection of policy alternatives of the policy object created. During the creation of
     * the new policy object, the content of the alternatives collection is copied into an internal policy object structure,
     * thus any subsequent operations on the collection will have no impact on the newly constructed policy object.
     *
     * @return policy instance which represents the policy with given alternatives.
     */
    public static Policy createPolicy(NamespaceVersion nsVersion, final String name, final String policyId, final Collection<AssertionSet> sets) {
        if (sets == null || sets.isEmpty()) {
            return createNullPolicy(nsVersion, name, policyId);
        } else {
            return new Policy(nsVersion, POLICY_TOSTRING_NAME, name, policyId, sets);
        }
    }

    /**
     * A most flexible policy object constructor that allows private creation of policy objects and direct setting
     * of all its attributes.
     *
     * @param name global URI of the policy. May be {@code null}.
     * @param policyId local URI of the policy. May be {@code null}.
     * @param assertionSets represents the collection of policy alternatives of the policy object created. The list is directly
     * assigned to the policy object internal attribute. Subsequent manipulations on the collection must be handled with
     * care.
     * @param vocabulary represents the vocabulary of the policy object. Subsequent manipulations on the collection
     * must be handled with care.
     */
    private Policy(final String name, final String policyId, final List<AssertionSet> assertionSets, final Set<QName> vocabulary) {
        this.nsVersion = NamespaceVersion.getLatestVersion();
        this.toStringName = POLICY_TOSTRING_NAME;
        this.name = name;
        this.policyId = policyId;
        this.assertionSets = assertionSets;
        this.vocabulary = vocabulary;
        this.immutableVocabulary = Collections.unmodifiableCollection(this.vocabulary);
    }

    /**
     * Constructor that should be overridden by child implementation. The constructor allows for easy toString() output
     * customization.
     *
     * @param toStringName a general name of the object (such as 'policy' or 'nested policy') that will be used in the
     * toString() method to identify the object.
     * @param sets represents the collection of policy alternatives of the policy object created. During the creation of
     * the new policy object, the content of the alternatives collection is copied into an internal policy object structure,
     * thus any subsequent operations on the collection will have no impact on the newly constructed policy object. The
     * collection may be {@code null} or empty. In such case a 'NULL' policy object is constructed.
     */
    Policy(final String toStringName, final Collection<AssertionSet> sets) {
        this.nsVersion = NamespaceVersion.getLatestVersion();
        this.toStringName = toStringName;

        if (sets == null || sets.isEmpty()) {
            this.assertionSets = NULL_POLICY_ASSERTION_SETS;
            this.vocabulary = EMPTY_VOCABULARY;
            this.immutableVocabulary = EMPTY_VOCABULARY;
        } else {
            this.assertionSets = new LinkedList<AssertionSet>();
            this.vocabulary = new TreeSet<QName>(PolicyUtils.Comparison.QNAME_COMPARATOR);
            this.immutableVocabulary = Collections.unmodifiableCollection(this.vocabulary);

            addAll(sets);
        }
    }

    /**
     * Constructor that should be overridden by child implementation. The constructor allows for easy toString() output
     * customization.
     *
     * @param toStringName a general name of the object (such as 'policy' or 'nested policy') that will be used in the
     * toString() method to identify the object.
     * @param name global URI of the policy. May be {@code null}.
     * @param policyId local URI of the policy. May be {@code null}.
     * @param sets represents the collection of policy alternatives of the policy object created. During the creation of
     * the new policy object, the content of the alternatives collection is copied into an internal policy object structure,
     * thus any subsequent operations on the collection will have no impact on the newly constructed policy object. The
     * collection may be {@code null} or empty. In such case a 'NULL' policy object is constructed.
     */
    Policy(final String toStringName, final String name, final String policyId, final Collection<AssertionSet> sets) {
        this(toStringName, sets);
        this.name = name;
        this.policyId = policyId;
    }

    /**
     * A most flexible policy object constructor that allows private creation of policy objects and direct setting
     * of all its attributes.
     *
     * @param nsVersion Policy namespace version to be used when marshalling the policy expression
     * @param name global URI of the policy. May be {@code null}.
     * @param policyId local URI of the policy. May be {@code null}.
     * @param assertionSets represents the collection of policy alternatives of the policy object created. The list is directly
     * assigned to the policy object internal attribute. Subsequent manipulations on the collection must be handled with
     * care.
     * @param vocabulary represents the vocabulary of the policy object. Subsequent manipulations on the collection
     * must be handled with care.
     */
    private Policy(final NamespaceVersion nsVersion, final String name, final String policyId, final List<AssertionSet> assertionSets, final Set<QName> vocabulary) {
        this.nsVersion = nsVersion;
        this.toStringName = POLICY_TOSTRING_NAME;
        this.name = name;
        this.policyId = policyId;
        this.assertionSets = assertionSets;
        this.vocabulary = vocabulary;
        this.immutableVocabulary = Collections.unmodifiableCollection(this.vocabulary);
    }

    /**
     * Constructor that should be overridden by child implementation. The constructor allows for easy toString() output
     * customization.
     *
     * @param nsVersion Policy namespace version to be used when marshalling the policy expression
     * @param toStringName a general name of the object (such as 'policy' or 'nested policy') that will be used in the
     * toString() method to identify the object.
     * @param sets represents the collection of policy alternatives of the policy object created. During the creation of
     * the new policy object, the content of the alternatives collection is copied into an internal policy object structure,
     * thus any subsequent operations on the collection will have no impact on the newly constructed policy object. The
     * collection may be {@code null} or empty. In such case a 'NULL' policy object is constructed.
     */
    Policy(final NamespaceVersion nsVersion, final String toStringName, final Collection<AssertionSet> sets) {
        this.nsVersion = nsVersion;
        this.toStringName = toStringName;

        if (sets == null || sets.isEmpty()) {
            this.assertionSets = NULL_POLICY_ASSERTION_SETS;
            this.vocabulary = EMPTY_VOCABULARY;
            this.immutableVocabulary = EMPTY_VOCABULARY;
        } else {
            this.assertionSets = new LinkedList<AssertionSet>();
            this.vocabulary = new TreeSet<QName>(PolicyUtils.Comparison.QNAME_COMPARATOR);
            this.immutableVocabulary = Collections.unmodifiableCollection(this.vocabulary);

            addAll(sets);
        }
    }

    /**
     * Constructor that should be overridden by child implementation. The constructor allows for easy toString() output
     * customization.
     *
     * @param nsVersion Policy namespace version to be used when marshalling the policy expression
     * @param toStringName a general name of the object (such as 'policy' or 'nested policy') that will be used in the
     * toString() method to identify the object.
     * @param name global URI of the policy. May be {@code null}.
     * @param policyId local URI of the policy. May be {@code null}.
     * @param sets represents the collection of policy alternatives of the policy object created. During the creation of
     * the new policy object, the content of the alternatives collection is copied into an internal policy object structure,
     * thus any subsequent operations on the collection will have no impact on the newly constructed policy object. The
     * collection may be {@code null} or empty. In such case a 'NULL' policy object is constructed.
     */
    Policy(final NamespaceVersion nsVersion, final String toStringName, final String name, final String policyId, final Collection<AssertionSet> sets) {
        this(nsVersion, toStringName, sets);
        this.name = name;
        this.policyId = policyId;
    }

    /**
     * Adds single alternative to the internal alternatives set of the policy object.
     *
     * @param set assertion set (policy alternative) object to be added. May be {@code null}; in such case the method
     * returns false.
     *
     * @return {@code true} or {@code false} depending on whether the new alternative was added to the policy object or not.
     */
    private boolean add(final AssertionSet set) {
        if (set == null) {
            return false;
        }

        if (this.assertionSets.contains(set)) {
            return false;
        } else {
            this.assertionSets.add(set);
            this.vocabulary.addAll(set.getVocabulary());
            return true;
        }
    }

    /**
     * Adds all alternatives from the input collection of assertion sets to the policy object's internal set of alternatives.
     * The policy object's vocabulary structure is updated as well.
     *
     * @param sets collection of new alternatives. Must NOT be {@code null} or empty. The check for null or empty input
     * parameter is performed with help of {@code assert} keyword, thus during the testing and development of this class
     * {@code -ea} switch should be turned on for this class.
     *
     * @return {@code true} if all elements in the input collection were added to internal structure, {@code false} otherwise.
     */
    private boolean addAll(final Collection<AssertionSet> sets) {
        assert (sets != null && !sets.isEmpty()) : LocalizationMessages.WSP_0036_PRIVATE_METHOD_DOES_NOT_ACCEPT_NULL_OR_EMPTY_COLLECTION();

        boolean result = true;
        for (AssertionSet set : sets) {
            result &= add(set); // this is here to ensure that vocabulary is built correctly as well
        }
        Collections.sort(this.assertionSets);

        return result;
    }

    Collection<AssertionSet> getContent() {
        return assertionSets;
    }

    /**
     * Returns the policy identifier that serves as a local relative policy URI.
     *
     * @return policy identifier - a local relative policy URI. If no policy identifier is set, returns {@code null}.
     */
    public String getId() {
        return policyId;
    }

    /**
     * Returns the policy name that serves as a global policy URI.
     *
     * @return policy name - a global policy URI. If no policy name is set, returns {@code null}.
     */
    public String getName() {
        return name;
    }

    public NamespaceVersion getNamespaceVersion() {
        return nsVersion;
    }

    /**
     * Returns the policy ID or if that is null the policy name. May return null
     * if both attributes are null.
     *
     * @see #getId()
     * @see #getName()
     * @return The policy ID if it was set, or the name or null if no attribute was set.
     */
    public String getIdOrName() {
        if (policyId != null) {
            return policyId;
        }
        return name;
    }

    /**
     * Method returns how many policy alternatives this policy instance contains.
     *
     * @return number of policy alternatives contained in this policy instance
     */
    public int getNumberOfAssertionSets() {
        return assertionSets.size();
    }

    /**
     * A policy usually contains one or more assertion sets. Each assertion set
     * corresponds to a policy alternative as defined by WS-Policy.
     *
     * @return An iterator to iterate through all contained assertion sets
     */
    public Iterator<AssertionSet> iterator() {
        return assertionSets.iterator();
    }

    /**
     * Returns {@code true} if the policy instance represents "nothing allowed" policy expression
     *
     * @return {@code true} if the policy instance represents "nothing allowed" policy expression, {@code false} otherwise.
     */
    public boolean isNull() {
        return assertionSets.size() == 0;
    }

    /**
     * Returns {@code true} if the policy instance represents "anything allowed" policy expression
     *
     * @return {@code true} if the policy instance represents "anything allowed" policy expression, {@code false} otherwise.
     */
    public boolean isEmpty() {
        return assertionSets.size() == 1 && assertionSets.get(0).isEmpty();
    }

    /**
     * Returns true if the policy contains the assertion names with specified namespace in its vocabulary
     *
     * @param namespaceUri the assertion namespace URI (identifying assertion domain)
     * @return {@code true}, if an assertion with the given name could be found in the policy vocabulary {@code false} otherwise.
     */
    public boolean contains(final String namespaceUri) {
        for (QName entry : vocabulary) {
            if (entry.getNamespaceURI().equals(namespaceUri)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Retrieves the vocabulary of this policy expression. The vocabulary is represented by an immutable collection of
     * unique QName objects. Each of those objects represents single assertion type contained in the policy.
     *
     * @return immutable collection of assertion types contained in the policy (a policy vocabulary).
     */
    public Collection<QName> getVocabulary() {
        return immutableVocabulary;
    }

    /**
     * Determines if the policy instance contains the assertion with the name specified in its vocabulary.
     *
     * @param assertionName the name of the assertion to be tested.
     *
     * @return {@code true} if the assertion with the specified name is part of the policy instance's vocabulary,
     * {@code false} otherwise.
     */
    public boolean contains(final QName assertionName) {
        return vocabulary.contains(assertionName);
    }

    /**
     * An {@code Object.equals(Object obj)} method override.
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Policy)) {
            return false;
        }

        final Policy that = (Policy) obj;

        boolean result = true;

        result = result && this.vocabulary.equals(that.vocabulary);
        result = result && this.assertionSets.size() == that.assertionSets.size() && this.assertionSets.containsAll(that.assertionSets);

        return result;
    }

    /**
     * An {@code Object.hashCode()} method override.
     */
    @Override
    public int hashCode() {
        int result = 17;

        result = 37 * result + vocabulary.hashCode();
        result = 37 * result + assertionSets.hashCode();

        return result;
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
        final String innerDoubleIndent = PolicyUtils.Text.createIndent(indentLevel + 2);

        buffer.append(indent).append(toStringName).append(" {").append(PolicyUtils.Text.NEW_LINE);
        buffer.append(innerIndent).append("namespace version = '").append(nsVersion.name()).append('\'').append(PolicyUtils.Text.NEW_LINE);
        buffer.append(innerIndent).append("id = '").append(policyId).append('\'').append(PolicyUtils.Text.NEW_LINE);
        buffer.append(innerIndent).append("name = '").append(name).append('\'').append(PolicyUtils.Text.NEW_LINE);

        buffer.append(innerIndent).append("vocabulary {").append(PolicyUtils.Text.NEW_LINE);
        if (vocabulary.isEmpty()) {
            buffer.append(innerDoubleIndent).append("no entries").append(PolicyUtils.Text.NEW_LINE);
        } else {
            int index = 1;
            for (QName entry : vocabulary) {
                buffer.append(innerDoubleIndent).append(index++).append(". entry = '").append(entry.getNamespaceURI()).append(':').append(entry.getLocalPart()).append('\'').append(PolicyUtils.Text.NEW_LINE);
            }
        }
        buffer.append(innerIndent).append('}').append(PolicyUtils.Text.NEW_LINE);

        if (assertionSets.isEmpty()) {
            buffer.append(innerIndent).append("no assertion sets").append(PolicyUtils.Text.NEW_LINE);
        } else {
            for (AssertionSet set : assertionSets) {
                set.toString(indentLevel + 1, buffer).append(PolicyUtils.Text.NEW_LINE);
            }
        }

        buffer.append(indent).append('}');

        return buffer;
    }
}
