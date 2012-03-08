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

import com.sun.xml.internal.ws.policy.PolicyConstants;
import com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.namespace.QName;

/**
 * Wrapper class for possible data that each "assertion" and "assertion parameter content" policy source model node may
 * have attached.
 * <p/>
 * This data, when stored in an 'assertion' model node, is intended to be used as input parameter when creating
 * {@link com.sun.xml.internal.ws.policy.PolicyAssertion} objects via {@link com.sun.xml.internal.ws.policy.spi.PolicyAssertionCreator}
 * implementations.
 *
 * @author Marek Potociar (marek.potociar@sun.com)
 * @author Fabian Ritzmann
 */
public final class AssertionData implements Cloneable, Serializable {
    private static final long serialVersionUID = 4416256070795526315L;
    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(AssertionData.class);

    private final QName name;
    private final String value;
    private final Map<QName, String> attributes;
    private ModelNode.Type type;

    private boolean optional;
    private boolean ignorable;

    /**
     * Constructs assertion data wrapper instance for an assertion that does not
     * contain any value nor any attributes.
     *
     * @param name the FQN of the assertion
     *
     * @throws IllegalArgumentException in case the {@code type} parameter is not
     * {@link ModelNode.Type#ASSERTION ASSERTION} or
     * {@link ModelNode.Type#ASSERTION_PARAMETER_NODE ASSERTION_PARAMETER_NODE}
     */
    public static AssertionData createAssertionData(final QName name) throws IllegalArgumentException {
        return new AssertionData(name, null, null, ModelNode.Type.ASSERTION, false, false);
    }

    /**
     * Constructs assertion data wrapper instance for an assertion parameter that
     * does not contain any value nor any attributes.
     *
     * @param name the FQN of the assertion parameter
     *
     * @throws IllegalArgumentException in case the {@code type} parameter is not
     * {@link ModelNode.Type#ASSERTION ASSERTION} or
     * {@link ModelNode.Type#ASSERTION_PARAMETER_NODE ASSERTION_PARAMETER_NODE}
     */
    public static AssertionData createAssertionParameterData(final QName name) throws IllegalArgumentException {
        return new AssertionData(name, null, null, ModelNode.Type.ASSERTION_PARAMETER_NODE, false, false);
    }

    /**
     * Constructs assertion data wrapper instance for an assertion that does
     * contain a value or attributes.
     *
     * @param name the FQN of the assertion
     * @param value a {@link String} representation of model node value
     * @param attributes map of model node's &lt;attribute name, attribute value&gt; pairs
     * @param optional flag indicating whether the assertion is optional or not
     * @param ignorable flag indicating whether the assertion is ignorable or not
     *
     * @throws IllegalArgumentException in case the {@code type} parameter is not
     * {@link ModelNode.Type#ASSERTION ASSERTION} or
     * {@link ModelNode.Type#ASSERTION_PARAMETER_NODE ASSERTION_PARAMETER_NODE}
     */
    public static AssertionData createAssertionData(final QName name, final String value, final Map<QName, String> attributes, boolean optional, boolean ignorable) throws IllegalArgumentException {
        return new AssertionData(name, value, attributes, ModelNode.Type.ASSERTION, optional, ignorable);
    }

    /**
     * Constructs assertion data wrapper instance for an assertion parameter that
     * contains a value or attributes
     *
     * @param name the FQN of the assertion parameter
     * @param value a {@link String} representation of model node value
     * @param attributes map of model node's &lt;attribute name, attribute value&gt; pairs
     *
     * @throws IllegalArgumentException in case the {@code type} parameter is not
     * {@link ModelNode.Type#ASSERTION ASSERTION} or
     * {@link ModelNode.Type#ASSERTION_PARAMETER_NODE ASSERTION_PARAMETER_NODE}
     */
    public static AssertionData createAssertionParameterData(final QName name, final String value, final Map<QName, String> attributes) throws IllegalArgumentException {
        return new AssertionData(name, value, attributes, ModelNode.Type.ASSERTION_PARAMETER_NODE, false, false);
    }

    /**
     * Constructs assertion data wrapper instance for an assertion or assertion parameter that contains a value or
     * some attributes. Whether the data wrapper is constructed for assertion or assertion parameter node is distinguished by
     * the supplied {@code type} parameter.
     *
     * @param name the FQN of the assertion or assertion parameter
     * @param value a {@link String} representation of model node value
     * @param attributes map of model node's &lt;attribute name, attribute value&gt; pairs
     * @param type specifies whether the data will belong to the assertion or assertion parameter node. This is
     *             a workaround solution that allows us to transfer this information about the owner node to
     *             a policy assertion instance factory without actualy having to touch the {@link PolicyAssertionCreator}
     *             interface and protected {@link PolicyAssertion} constructors.
     *
     * @throws IllegalArgumentException in case the {@code type} parameter is not
     * {@link ModelNode.Type#ASSERTION ASSERTION} or
     * {@link ModelNode.Type#ASSERTION_PARAMETER_NODE ASSERTION_PARAMETER_NODE}
     */
    AssertionData(QName name, String value, Map<QName, String> attributes, ModelNode.Type type, boolean optional, boolean ignorable) throws IllegalArgumentException {
        this.name = name;
        this.value = value;
        this.optional = optional;
        this.ignorable = ignorable;

        this.attributes = new HashMap<QName, String>();
        if (attributes != null && !attributes.isEmpty()) {
            this.attributes.putAll(attributes);
        }
        setModelNodeType(type);
    }

    private void setModelNodeType(final ModelNode.Type type) throws IllegalArgumentException {
        if (type == ModelNode.Type.ASSERTION || type == ModelNode.Type.ASSERTION_PARAMETER_NODE) {
            this.type = type;
        } else {
            throw LOGGER.logSevereException(new IllegalArgumentException(
                    LocalizationMessages.WSP_0074_CANNOT_CREATE_ASSERTION_BAD_TYPE(type, ModelNode.Type.ASSERTION, ModelNode.Type.ASSERTION_PARAMETER_NODE)));
        }
    }

    /**
     * Copy constructor.
     *
     * @param data The instance that is to be copied.
     */
    AssertionData(final AssertionData data) {
        this.name = data.name;
        this.value = data.value;
        this.attributes = new HashMap<QName, String>();
        if (!data.attributes.isEmpty()) {
            this.attributes.putAll(data.attributes);
        }
        this.type = data.type;
    }

    @Override
    protected AssertionData clone() throws CloneNotSupportedException {
        return (AssertionData) super.clone();
    }

    /**
     * Returns true if the given attribute exists, false otherwise.
     *
     * @param name The name of the attribute. Must not be null.
     * @return True if the given attribute exists, false otherwise.
     */
    public boolean containsAttribute(final QName name) {
        synchronized (attributes) {
            return attributes.containsKey(name);
        }
    }


    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof AssertionData)) {
            return false;
        }

        boolean result = true;
        final AssertionData that = (AssertionData) obj;

        result = result && this.name.equals(that.name);
        result = result && ((this.value == null) ? that.value == null : this.value.equals(that.value));
        synchronized (attributes) {
            result = result && this.attributes.equals(that.attributes);
        }

        return result;
    }


    /**
     * Returns the value of the given attribute. Returns null if the attribute
     * does not exist.
     *
     * @param name The name of the attribute. Must not be null.
     * @return The value of the given attribute. Returns null if the attribute
     *   does not exist.
     */
    public String getAttributeValue(final QName name) {
        synchronized (attributes) {
            return attributes.get(name);
        }
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
    public Map<QName, String> getAttributes() {
        synchronized (attributes) {
            return new HashMap<QName, String>(attributes);
        }
    }


    /**
     * Returns the disconnected set of attributes attached to the assertion. Each attribute is represented as a single
     * {@code Map.Entry<attributeName, attributeValue>} element.
     * <p/>
     * 'Disconnected' means, that the result of this method will not be synchronized with any consequent assertion's attribute modification. It is
     * also important to notice that a manipulation with returned set of attributes will not have any effect on the actual assertion's
     * attributes.
     *
     * @return disconnected set of attributes attached to the assertion.
     */
    public Set<Map.Entry<QName, String>> getAttributesSet() {
        synchronized (attributes) {
            return new HashSet<Map.Entry<QName, String>>(attributes.entrySet());
        }
    }


    /**
     * Returns the name of the assertion.
     *
     * @return assertion's name
     */
    public QName getName() {
        return name;
    }


    /**
     * Returns the value of the assertion.
     *
     * @return assertion's value
     */
    public String getValue() {
        return value;
    }


    /**
     * An {@code Object.hashCode()} method override.
     */
    @Override
    public int hashCode() {
        int result = 17;

        result = 37 * result + this.name.hashCode();
        result = 37 * result + ((this.value == null) ? 0 : this.value.hashCode());
        synchronized (attributes) {
            result = 37 * result + this.attributes.hashCode();
        }
        return result;
    }


    /**
     * Method specifies whether the assertion data contain proprietary visibility element set to "private" value.
     *
     * @return {@code 'true'} if the attribute is present and set properly (i.e. the node containing this assertion data instance should
     * not be marshaled into generated WSDL documents). Returns {@code false} otherwise.
     */
    public boolean isPrivateAttributeSet() {
        return PolicyConstants.VISIBILITY_VALUE_PRIVATE.equals(getAttributeValue(PolicyConstants.VISIBILITY_ATTRIBUTE));
    }

    /**
     * Removes the given attribute from the assertion data.
     *
     * @param name The name of the attribute. Must not be null
     * @return The value of the removed attribute.
     */
    public String removeAttribute(final QName name) {
        synchronized (attributes) {
            return attributes.remove(name);
        }
    }

    /**
     * Adds or overwrites an attribute.
     *
     * @param name The name of the attribute.
     * @param value The value of the attribute.
     */
    public void setAttribute(final QName name, final String value) {
        synchronized (attributes) {
            attributes.put(name, value);
        }
    }

    /**
     * Sets the optional attribute.
     *
     * @param value The value of the optional attribute.
     */
    public void setOptionalAttribute(final boolean value) {
        optional = value;
    }

    /**
     * Tests if the optional attribute is set.
     *
     * @return True if optional is set and is true. False otherwise.
     */
    public boolean isOptionalAttributeSet() {
        return optional;
    }

    /**
     * Sets the ignorable attribute.
     *
     * @param value The value of the ignorable attribute.
     */
    public void setIgnorableAttribute(final boolean value) {
        ignorable = value;
    }

    /**
     * Tests if the ignorable attribute is set.
     *
     * @return True if ignorable is set and is true. False otherwise.
     */
    public boolean isIgnorableAttributeSet() {
        return ignorable;
    }

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
    public StringBuffer toString(final int indentLevel, final StringBuffer buffer) {
        final String indent = PolicyUtils.Text.createIndent(indentLevel);
        final String innerIndent = PolicyUtils.Text.createIndent(indentLevel + 1);
        final String innerDoubleIndent = PolicyUtils.Text.createIndent(indentLevel + 2);

        buffer.append(indent);
        if (type == ModelNode.Type.ASSERTION) {
            buffer.append("assertion data {");
        } else {
            buffer.append("assertion parameter data {");
        }
        buffer.append(PolicyUtils.Text.NEW_LINE);

        buffer.append(innerIndent).append("namespace = '").append(name.getNamespaceURI()).append('\'').append(PolicyUtils.Text.NEW_LINE);
        buffer.append(innerIndent).append("prefix = '").append(name.getPrefix()).append('\'').append(PolicyUtils.Text.NEW_LINE);
        buffer.append(innerIndent).append("local name = '").append(name.getLocalPart()).append('\'').append(PolicyUtils.Text.NEW_LINE);
        buffer.append(innerIndent).append("value = '").append(value).append('\'').append(PolicyUtils.Text.NEW_LINE);
        buffer.append(innerIndent).append("optional = '").append(optional).append('\'').append(PolicyUtils.Text.NEW_LINE);
        buffer.append(innerIndent).append("ignorable = '").append(ignorable).append('\'').append(PolicyUtils.Text.NEW_LINE);
        synchronized (attributes) {
            if (attributes.isEmpty()) {
                buffer.append(innerIndent).append("no attributes");
            } else {

                buffer.append(innerIndent).append("attributes {").append(PolicyUtils.Text.NEW_LINE);
                for(Map.Entry<QName, String> entry : attributes.entrySet()) {
                    final QName aName = entry.getKey();
                    buffer.append(innerDoubleIndent).append("name = '").append(aName.getNamespaceURI()).append(':').append(aName.getLocalPart());
                    buffer.append("', value = '").append(entry.getValue()).append('\'').append(PolicyUtils.Text.NEW_LINE);
                }
                buffer.append(innerIndent).append('}');
            }
        }

        buffer.append(PolicyUtils.Text.NEW_LINE).append(indent).append('}');

        return buffer;
    }

    public ModelNode.Type getNodeType() {
        return type;
    }

}
