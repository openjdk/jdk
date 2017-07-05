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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.xml.namespace.QName;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import com.sun.xml.internal.ws.policy.sourcemodel.AssertionData;
import com.sun.xml.internal.ws.policy.sourcemodel.ModelNode;

/**
 * Base class for any policy assertion implementations. It defines the common
 * interface and provides some default implentation for common policy assertion
 * functionality.
 * <p/>
 * NOTE: Assertion implementers should not extend this class directly. {@link SimpleAssertion}
 * or {@link ComplexAssertion} should be used as a base class instead.
 *
 * @author Marek Potociar (marek.potociar at sun.com)
 * @author Fabian Ritzmann
 */
public abstract class PolicyAssertion {

    private final AssertionData data;
    private AssertionSet parameters;
    private NestedPolicy nestedPolicy; // TODO: remove

    protected PolicyAssertion() {
        this.data = AssertionData.createAssertionData(null);
        this.parameters =  AssertionSet.createAssertionSet(null);
    }

    /**
     * Creates generic assertionand stores the data specified in input parameters
     *
     * @param assertionData assertion creation data specifying the details of newly created assertion. May be {@code null}.
     * @param assertionParameters collection of assertions parameters of this policy assertion. May be {@code null}.
     * @param nestedAlternative assertion set specifying nested policy alternative. May be {@code null}.
     *
     * @deprecated Non-abstract assertion types should derive from {@link SimpleAssertion}
     * or {@link ComplexAssertion} instead. {@link Policy} class will not provide support for
     * nested policy alternatives in the future. This responsibility is delegated to
     * {@link ComplexAssertion} class instead.
     */
    @Deprecated
    protected PolicyAssertion(final AssertionData assertionData, final Collection<? extends PolicyAssertion> assertionParameters, final AssertionSet nestedAlternative) {
        this.data = assertionData;
        if (nestedAlternative != null) {
            this.nestedPolicy = NestedPolicy.createNestedPolicy(nestedAlternative);
        }

        this.parameters = AssertionSet.createAssertionSet(assertionParameters);
    }

    /**
     * Creates generic assertionand stores the data specified in input parameters
     *
     * @param assertionData assertion creation data specifying the details of newly created assertion
     * @param assertionParameters collection of assertions parameters of this policy assertion. May be {@code null}.
     */
    protected PolicyAssertion(final AssertionData assertionData, final Collection<? extends PolicyAssertion> assertionParameters) {
        if (assertionData == null) {
            this.data = AssertionData.createAssertionData(null);
        } else {
            this.data = assertionData;
        }
        this.parameters = AssertionSet.createAssertionSet(assertionParameters);
    }

    /**
     * Returns the fully qualified name of the assertion.
     *
     * @return assertion's fully qualified name.
     */
    public final QName getName() {
        return data.getName();
    }

    /**
     * Returns the value of the assertion - the character data content contained in the assertion element representation.
     *
     * @return assertion's value. May return {@code null} if there is no value set for the assertion.
     */
    public final String getValue() {
        return data.getValue();
    }

    /**
     * Method specifies whether the assertion is otpional or not.
     * <p/>
     * This is a default implementation that may be overriden. The method returns {@code true} if the {@code wsp:optional} attribute
     * is present on the assertion and its value is {@code 'true'}. Otherwise the method returns {@code false}.
     *
     * @return {@code 'true'} if the assertion is optional. Returns {@code false} otherwise.
     */
    public boolean isOptional() {
        return data.isOptionalAttributeSet();
    }

    /**
     * Method specifies whether the assertion is ignorable or not.
     * <p/>
     * This is a default implementation that may be overriden. The method returns {@code true} if the {@code wsp:Ignorable} attribute
     * is present on the assertion and its value is {@code 'true'}. Otherwise the method returns {@code false}.
     *
     * @return {@code 'true'} if the assertion is ignorable. Returns {@code false} otherwise.
     */
    public boolean isIgnorable() {
        return data.isIgnorableAttributeSet();
    }

    /**
     * Method specifies whether the assertion is private or not. This is specified by our proprietary visibility element.
     *
     * @return {@code 'true'} if the assertion is marked as private (i.e. should not be marshalled int generated WSDL documents). Returns {@code false} otherwise.
     */
    public final boolean isPrivate() {
        return data.isPrivateAttributeSet();
    }

    /**
     * Returns the disconnected set of attributes attached to the assertion. Each attribute is represented as a single
     * {@code Map.Entry<attributeName, attributeValue>} element.
     * <p/>
     * 'Disconnected' means, that the result of this method will not be synchronized with any consequent assertion's attribute modification. It is
     * also important to notice that a manipulation with returned set of attributes will not have any effect on the actual assertion's
     * attributes.
     *
     * @return disconected set of attributes attached to the assertion.
     */
    public final Set<Map.Entry<QName, String>> getAttributesSet() {
        return data.getAttributesSet();
    }

    /**
     * Returns the disconnected map of attributes attached to the assertion.
     * <p/>
     * 'Disconnected' means, that the result of this method will not be synchronized with any consequent assertion's attribute modification. It is
     * also important to notice that a manipulation with returned set of attributes will not have any effect on the actual assertion's
     * attributes.
     *
     * @return disconnected map of attributes attached to the assertion.
     */
    public final Map<QName, String> getAttributes() {
        return data.getAttributes();
    }

    /**
     * Returns the value of an attribute. Returns null if an attribute with the given name does not exist.
     *
     * @param name The fully qualified name of the attribute
     * @return The value of the attribute. Returns {@code null} if there is no such attribute or if it's value is null.
     */
    public final String getAttributeValue(final QName name) {
        return data.getAttributeValue(name);
    }

    /**
     * Returns the boolean information whether this assertion contains any parameters.
     *
     * @return {@code true} if the assertion contains parameters. Returns {@code false} otherwise.
     *
     * @deprecated Use hasParameters() instead
     */
    @Deprecated
    public final boolean hasNestedAssertions() {
        // TODO: remove
        return !parameters.isEmpty();
    }

    /**
     * Returns the boolean information whether this assertion contains any parameters.
     *
     * @return {@code true} if the assertion contains parameters. Returns {@code false} otherwise.
     */
    public final boolean hasParameters() {
        return !parameters.isEmpty();
    }

    /**
     * Returns the assertion's parameter collection iterator.
     *
     * @return the assertion's parameter collection iterator.
     *
     * @deprecated Use getNestedParametersIterator() instead
     */
    @Deprecated
    public final Iterator<PolicyAssertion> getNestedAssertionsIterator() {
        // TODO: remove
        return parameters.iterator();
    }

    /**
     * Returns the assertion's parameter collection iterator.
     *
     * @return the assertion's parameter collection iterator.
     */
    public final Iterator<PolicyAssertion> getParametersIterator() {
        return parameters.iterator();
    }

    boolean isParameter() {
        return data.getNodeType() == ModelNode.Type.ASSERTION_PARAMETER_NODE;
    }

    /**
     * Returns the boolean information whether this assertion contains nested policy.
     *
     * @return {@code true} if the assertion contains child (nested) policy. Returns {@code false} otherwise.
     */
    public boolean hasNestedPolicy() {
        // TODO: make abstract
        return getNestedPolicy() != null;
    }

    /**
     * Returns the nested policy if any.
     *
     * @return the nested policy if the assertion contains a nested policy. Returns {@code null} otherwise.
     */
    public NestedPolicy getNestedPolicy() {
        // TODO: make abstract
        return nestedPolicy;
    }

    /**
     * Casts the assertion to the implementation type. Returns null if that is not
     * possible.
     *
     * @param <T> The implementation type of the assertion.
     * @param type The implementation type of the assertion. May not be null.
     * @return The instance of the implementation type. Null otherwise.
     */
    public <T extends PolicyAssertion> T getImplementation(Class<T> type) {
        if (type.isAssignableFrom(this.getClass())) {
            return type.cast(this);
        }
        else {
            return null;
        }
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
    protected StringBuffer toString(final int indentLevel, final StringBuffer buffer) {
        final String indent = PolicyUtils.Text.createIndent(indentLevel);
        final String innerIndent = PolicyUtils.Text.createIndent(indentLevel + 1);

        buffer.append(indent).append("Assertion[").append(this.getClass().getName()).append("] {").append(PolicyUtils.Text.NEW_LINE);
        data.toString(indentLevel + 1, buffer);
        buffer.append(PolicyUtils.Text.NEW_LINE);

        if (hasParameters()) {
            buffer.append(innerIndent).append("parameters {").append(PolicyUtils.Text.NEW_LINE);
            for (PolicyAssertion parameter : parameters) {
                parameter.toString(indentLevel + 2, buffer).append(PolicyUtils.Text.NEW_LINE);
            }
            buffer.append(innerIndent).append('}').append(PolicyUtils.Text.NEW_LINE);
        } else {
            buffer.append(innerIndent).append("no parameters").append(PolicyUtils.Text.NEW_LINE);
        }

        if (hasNestedPolicy()) {
            getNestedPolicy().toString(indentLevel + 1, buffer).append(PolicyUtils.Text.NEW_LINE);
        } else {
            buffer.append(innerIndent).append("no nested policy").append(PolicyUtils.Text.NEW_LINE);
        }

        buffer.append(indent).append('}');

        return buffer;
    }

    /**
     * Checks whether this policy alternative is compatible with the provided policy alternative.
     *
     * @param assertion policy alternative used for compatibility test
     * @param mode compatibility mode to be used
     * @return {@code true} if the two policy alternatives are compatible, {@code false} otherwise
     */
    boolean isCompatibleWith(final PolicyAssertion assertion, PolicyIntersector.CompatibilityMode mode) {
        boolean result = this.data.getName().equals(assertion.data.getName()) && (this.hasNestedPolicy() == assertion.hasNestedPolicy());

        if (result && this.hasNestedPolicy()) {
            result = this.getNestedPolicy().getAssertionSet().isCompatibleWith(assertion.getNestedPolicy().getAssertionSet(), mode);
        }

        return result;
    }

    /**
     * An {@code Object.equals(Object obj)} method override.
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof PolicyAssertion)) {
            return false;
        }

        final PolicyAssertion that = (PolicyAssertion) obj;
        boolean result = true;

        result = result && this.data.equals(that.data);
        result = result && this.parameters.equals(that.parameters);
        result = result && ((this.getNestedPolicy() == null) ? ((that.getNestedPolicy() == null) ? true : false) : this.getNestedPolicy().equals(that.getNestedPolicy()));

        return result;
    }

    /**
     * An {@code Object.hashCode()} method override.
     */
    @Override
    public int hashCode() {
        int result = 17;

        result = 37 * result + data.hashCode();
        result = 37 * result + ((hasParameters()) ? 17 : 0);
        result = 37 * result + ((hasNestedPolicy()) ? 17 : 0);

        return result;
    }
}
