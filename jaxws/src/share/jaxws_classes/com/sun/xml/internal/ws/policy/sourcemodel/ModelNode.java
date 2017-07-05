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

import com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import com.sun.xml.internal.ws.policy.sourcemodel.wspolicy.XmlToken;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * The general representation of a single node within a {@link com.sun.xml.internal.ws.policy.sourcemodel.PolicySourceModel} instance.
 * The model node is created via factory methods of the {@link com.sun.xml.internal.ws.policy.sourcemodel.PolicySourceModel} instance.
 * It may also hold {@link com.sun.xml.internal.ws.policy.sourcemodel.AssertionData} instance in case its type is {@code ModelNode.Type.ASSERTION}.
 *
 * @author Marek Potociar
 */
public final class ModelNode implements Iterable<ModelNode>, Cloneable {
    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(ModelNode.class);

    /**
     * Policy source model node type enumeration
     */
    public static enum Type {
        POLICY(XmlToken.Policy),
        ALL(XmlToken.All),
        EXACTLY_ONE(XmlToken.ExactlyOne),
        POLICY_REFERENCE(XmlToken.PolicyReference),
        ASSERTION(XmlToken.UNKNOWN),
        ASSERTION_PARAMETER_NODE(XmlToken.UNKNOWN);

        private XmlToken token;

        Type(XmlToken token) {
            this.token = token;
        }

        public XmlToken getXmlToken() {
            return token;
        }

        /**
         * Method checks the PSM state machine if the creation of new child of given type is plausible for a node element
         * with type set to this type instance.
         *
         * @param childType The type.
         * @return True if the type is supported, false otherwise
         */
        private boolean isChildTypeSupported(final Type childType) {
            switch (this) {
                case POLICY:
                case ALL:
                case EXACTLY_ONE:
                    switch (childType) {
                        case ASSERTION_PARAMETER_NODE:
                            return false;
                        default:
                            return true;
                    }
                case POLICY_REFERENCE:
                    return false;
                case ASSERTION:
                    switch (childType) {
                        case POLICY:
                        case POLICY_REFERENCE:
                        case ASSERTION_PARAMETER_NODE:
                            return true;
                        default:
                            return false;
                    }
                case ASSERTION_PARAMETER_NODE:
                    switch (childType) {
                        case ASSERTION_PARAMETER_NODE:
                            return true;
                        default:
                            return false;
                    }
                default:
                    throw LOGGER.logSevereException(new IllegalStateException(
                            LocalizationMessages.WSP_0060_POLICY_ELEMENT_TYPE_UNKNOWN(this)));
            }
        }
    }

    // comon model node attributes
    private LinkedList<ModelNode> children;
    private Collection<ModelNode> unmodifiableViewOnContent;
    private final ModelNode.Type type;
    private ModelNode parentNode;
    private PolicySourceModel parentModel;

    // attributes used only in 'POLICY_REFERENCE' model node
    private PolicyReferenceData referenceData;
    private PolicySourceModel referencedModel;

    // attibutes used only in 'ASSERTION' or 'ASSERTION_PARAMETER_NODE' model node
    private AssertionData nodeData;

    /**
     * The factory method creates and initializes the POLICY model node and sets it's parent model reference to point to
     * the model supplied as an input parameter. This method is intended to be used ONLY from {@link PolicySourceModel} during
     * the initialization of its own internal structures.
     *
     * @param model policy source model to be used as a parent model of the newly created {@link ModelNode}. Must not be {@code null}
     * @return POLICY model node with the parent model reference initialized to the model supplied as an input parameter
     * @throws IllegalArgumentException if the {@code model} input parameter is {@code null}
     */
    static ModelNode createRootPolicyNode(final PolicySourceModel model) throws IllegalArgumentException {
        if (model == null) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0039_POLICY_SRC_MODEL_INPUT_PARAMETER_MUST_NOT_BE_NULL()));
        }
        return new ModelNode(ModelNode.Type.POLICY, model);
    }

    private ModelNode(Type type, PolicySourceModel parentModel) {
        this.type = type;
        this.parentModel = parentModel;
        this.children = new LinkedList<ModelNode>();
        this.unmodifiableViewOnContent = Collections.unmodifiableCollection(this.children);
    }

    private ModelNode(Type type, PolicySourceModel parentModel, AssertionData data) {
        this(type, parentModel);

        this.nodeData = data;
    }

    private ModelNode(PolicySourceModel parentModel, PolicyReferenceData data) {
        this(Type.POLICY_REFERENCE, parentModel);

        this.referenceData = data;
    }

    private void checkCreateChildOperationSupportForType(final Type type) throws UnsupportedOperationException {
        if (!this.type.isChildTypeSupported(type)) {
            throw LOGGER.logSevereException(new UnsupportedOperationException(LocalizationMessages.WSP_0073_CREATE_CHILD_NODE_OPERATION_NOT_SUPPORTED(type, this.type)));
        }
    }

    /**
     * Factory method that creates new policy source model node as specified by a factory method name and input parameters.
     * Each node is created with respect to its enclosing policy source model.
     *
     * @return A new Policy node.
     */
    public ModelNode createChildPolicyNode() {
        checkCreateChildOperationSupportForType(Type.POLICY);

        final ModelNode node = new ModelNode(ModelNode.Type.POLICY, parentModel);
        this.addChild(node);

        return node;
    }

    /**
     * Factory method that creates new policy source model node as specified by a factory method name and input parameters.
     * Each node is created with respect to its enclosing policy source model.
     *
     * @return A new All node.
     */
    public ModelNode createChildAllNode() {
        checkCreateChildOperationSupportForType(Type.ALL);

        final ModelNode node = new ModelNode(ModelNode.Type.ALL, parentModel);
        this.addChild(node);

        return node;
    }

    /**
     * Factory method that creates new policy source model node as specified by a factory method name and input parameters.
     * Each node is created with respect to its enclosing policy source model.
     *
     * @return A new ExactlyOne node.
     */
    public ModelNode createChildExactlyOneNode() {
        checkCreateChildOperationSupportForType(Type.EXACTLY_ONE);

        final ModelNode node = new ModelNode(ModelNode.Type.EXACTLY_ONE, parentModel);
        this.addChild(node);

        return node;
    }

    /**
     * Factory method that creates new policy source model node as specified by a factory method name and input parameters.
     * Each node is created with respect to its enclosing policy source model.
     *
     * @return A new policy assertion node.
     */
    public ModelNode createChildAssertionNode() {
        checkCreateChildOperationSupportForType(Type.ASSERTION);

        final ModelNode node = new ModelNode(ModelNode.Type.ASSERTION, parentModel);
        this.addChild(node);

        return node;
    }

    /**
     * Factory method that creates new policy source model node as specified by a factory method name and input parameters.
     * Each node is created with respect to its enclosing policy source model.
     *
     * @param nodeData The policy assertion data.
     * @return A new policy assertion node.
     */
    public ModelNode createChildAssertionNode(final AssertionData nodeData) {
        checkCreateChildOperationSupportForType(Type.ASSERTION);

        final ModelNode node = new ModelNode(Type.ASSERTION, parentModel, nodeData);
        this.addChild(node);

        return node;
    }

    /**
     * Factory method that creates new policy source model node as specified by a factory method name and input parameters.
     * Each node is created with respect to its enclosing policy source model.
     *
     * @return A new assertion parameter node.
     */
    public ModelNode createChildAssertionParameterNode() {
        checkCreateChildOperationSupportForType(Type.ASSERTION_PARAMETER_NODE);

        final ModelNode node = new ModelNode(ModelNode.Type.ASSERTION_PARAMETER_NODE, parentModel);
        this.addChild(node);

        return node;
    }

    /**
     * Factory method that creates new policy source model node as specified by a factory method name and input parameters.
     * Each node is created with respect to its enclosing policy source model.
     *
     * @param nodeData The assertion parameter data.
     * @return A new assertion parameter node.
     */
    ModelNode createChildAssertionParameterNode(final AssertionData nodeData) {
        checkCreateChildOperationSupportForType(Type.ASSERTION_PARAMETER_NODE);

        final ModelNode node = new ModelNode(Type.ASSERTION_PARAMETER_NODE, parentModel, nodeData);
        this.addChild(node);

        return node;
    }

    /**
     * Factory method that creates new policy source model node as specified by a factory method name and input parameters.
     * Each node is created with respect to its enclosing policy source model.
     *
     * @param referenceData The PolicyReference data.
     * @return A new PolicyReference node.
     */
    ModelNode createChildPolicyReferenceNode(final PolicyReferenceData referenceData) {
        checkCreateChildOperationSupportForType(Type.POLICY_REFERENCE);

        final ModelNode node = new ModelNode(parentModel, referenceData);
        this.parentModel.addNewPolicyReference(node);
        this.addChild(node);

        return node;
    }

    Collection<ModelNode> getChildren() {
        return unmodifiableViewOnContent;
    }

    /**
     * Sets the parent model reference on the node and its children. The method may be invoked only on the root node
     * of the policy source model (or - in general - on a model node that dose not reference a parent node). Otherwise an
     * exception is thrown.
     *
     * @param model new parent policy source model to be set.
     * @throws IllegalAccessException in case this node references a parent node (i.e. is not a root node of the model).
     */
    void setParentModel(final PolicySourceModel model) throws IllegalAccessException {
        if (parentNode != null) {
            throw LOGGER.logSevereException(new IllegalAccessException(LocalizationMessages.WSP_0049_PARENT_MODEL_CAN_NOT_BE_CHANGED()));
        }

        this.updateParentModelReference(model);
    }

    /**
     * The method updates the parentModel reference on current model node instance and all of it's children
     *
     * @param model new policy source model reference.
     */
    private void updateParentModelReference(final PolicySourceModel model) {
        this.parentModel = model;

        for (ModelNode child : children) {
            child.updateParentModelReference(model);
        }
    }

    /**
     * Returns the parent policy source model that contains this model node.
     *
     * @return the parent policy source model
     */
    public PolicySourceModel getParentModel() {
        return parentModel;
    }

    /**
     * Returns the type of this policy source model node.
     *
     * @return actual type of this policy source model node
     */
    public ModelNode.Type getType() {
        return type;
    }

    /**
     * Returns the parent referenced by this policy source model node.
     *
     * @return current parent of this policy source model node or {@code null} if the node does not have a parent currently.
     */
    public ModelNode getParentNode() {
        return parentNode;
    }

    /**
     * Returns the data for this policy source model node (if any). The model node data are expected to be not {@code null} only in
     * case the type of this node is ASSERTION or ASSERTION_PARAMETER_NODE.
     *
     * @return the data of this policy source model node or {@code null} if the node does not have any data associated to it
     * attached.
     */
    public AssertionData getNodeData() {
        return nodeData;
    }

    /**
     * Returns the policy reference data for this policy source model node. The policy reference data are expected to be not {@code null} only in
     * case the type of this node is POLICY_REFERENCE.
     *
     * @return the policy reference data for this policy source model node or {@code null} if the node does not have any policy reference data
     * attached.
     */
    PolicyReferenceData getPolicyReferenceData() {
        return referenceData;
    }

    /**
     * The method may be used to set or replace assertion data set for this node. If there are assertion data set already,
     * those are replaced by a new reference and eventualy returned from the method.
     * <p/>
     * This method is supported only in case this model node instance's type is {@code ASSERTION} or {@code ASSERTION_PARAMETER_NODE}.
     * If used from other node types, an exception is thrown.
     *
     * @param newData new assertion data to be set.
     * @return old and replaced assertion data if any or {@code null} otherwise.
     *
     * @throws UnsupportedOperationException in case this method is called on nodes of type other than {@code ASSERTION}
     * or {@code ASSERTION_PARAMETER_NODE}
     */
    public AssertionData setOrReplaceNodeData(final AssertionData newData) {
        if (!isDomainSpecific()) {
            throw LOGGER.logSevereException(new UnsupportedOperationException(LocalizationMessages.WSP_0051_OPERATION_NOT_SUPPORTED_FOR_THIS_BUT_ASSERTION_RELATED_NODE_TYPE(type)));
        }

        final AssertionData oldData = this.nodeData;
        this.nodeData = newData;

        return oldData;
    }

    /**
     * The method specifies whether the model node instance represents assertion related node, it means whether its type
     * is 'ASSERTION' or 'ASSERTION_PARAMETER_NODE'. This is, for example, the way to determine whether the node supports
     * setting a {@link AssertionData} object via {@link #setOrReplaceNodeData(AssertionData)} method or not.
     *
     * @return {@code true} or {@code false} according to whether the node instance represents assertion related node or not.
     */
    boolean isDomainSpecific() {
        return type == Type.ASSERTION || type == Type.ASSERTION_PARAMETER_NODE;
    }

    /**
     * Appends the specified child node to the end of the children list of this node and sets it's parent to reference
     * this node.
     *
     * @param child node to be appended to the children list of this node.
     * @return {@code true} (as per the general contract of the {@code Collection.add} method).
     *
     * @throws NullPointerException if the specified node is {@code null}.
     * @throws IllegalArgumentException if child has a parent node set already to point to some node
     */
    private boolean addChild(final ModelNode child) {
        children.add(child);
        child.parentNode = this;

        return true;
    }

    void setReferencedModel(final PolicySourceModel model) {
        if (this.type != Type.POLICY_REFERENCE) {
            throw LOGGER.logSevereException(new UnsupportedOperationException(LocalizationMessages.WSP_0050_OPERATION_NOT_SUPPORTED_FOR_THIS_BUT_POLICY_REFERENCE_NODE_TYPE(type)));
        }

        referencedModel = model;
    }

    PolicySourceModel getReferencedModel() {
        return referencedModel;
    }

    /**
     * Returns the number of child policy source model nodes. If this model node contains
     * more than {@code Integer.MAX_VALUE} children, returns {@code Integer.MAX_VALUE}.
     *
     * @return the number of children of this node.
     */
    public int childrenSize() {
        return children.size();
    }

    /**
     * Returns true if the node has at least one child node.
     *
     * @return true if the node has at least one child node, false otherwise.
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /**
     * Iterates through all child nodes.
     *
     * @return An iterator for the child nodes
     */
    public Iterator<ModelNode> iterator() {
        return children.iterator();
    }

    /**
     * An {@code Object.equals(Object obj)} method override. Method ignores the parent source model. It means that two
     * model nodes may be the same even if they belong to different models.
     * <p/>
     * If parent model comparison is desired, it must be accomplished separately. To perform that, the reference equality
     * test is sufficient ({@code nodeA.getParentModel() == nodeB.getParentModel()}), since all model nodes are created
     * for specific model instances.
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof ModelNode)) {
            return false;
        }

        boolean result = true;
        final ModelNode that = (ModelNode) obj;

        result = result && this.type.equals(that.type);
        // result = result && ((this.parentNode == null) ? that.parentNode == null : this.parentNode.equals(that.parentNode));
        result = result && ((this.nodeData == null) ? that.nodeData == null : this.nodeData.equals(that.nodeData));
        result = result && ((this.children == null) ? that.children == null : this.children.equals(that.children));

        return result;
    }

    /**
     * An {@code Object.hashCode()} method override.
     */
    @Override
    public int hashCode() {
        int result = 17;

        result = 37 * result + this.type.hashCode();
        result = 37 * result + ((this.parentNode == null) ? 0 : this.parentNode.hashCode());
        result = 37 * result + ((this.nodeData == null) ? 0 : this.nodeData.hashCode());
        result = 37 * result + this.children.hashCode();

        return result;
    }

    /**
     * Returns a string representation of the object. In general, the <code>toString</code> method
     * returns a string that "textually represents" this object.
     *
     * @return  a string representation of the object.
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
    public StringBuffer toString(final int indentLevel, final StringBuffer buffer) {
        final String indent = PolicyUtils.Text.createIndent(indentLevel);
        final String innerIndent = PolicyUtils.Text.createIndent(indentLevel + 1);

        buffer.append(indent).append(type).append(" {").append(PolicyUtils.Text.NEW_LINE);
        if (type == Type.ASSERTION) {
            if (nodeData == null) {
                buffer.append(innerIndent).append("no assertion data set");
            } else {
                nodeData.toString(indentLevel + 1, buffer);
            }
            buffer.append(PolicyUtils.Text.NEW_LINE);
        } else if (type == Type.POLICY_REFERENCE) {
            if (referenceData == null) {
                buffer.append(innerIndent).append("no policy reference data set");
            } else {
                referenceData.toString(indentLevel + 1, buffer);
            }
            buffer.append(PolicyUtils.Text.NEW_LINE);
        } else if (type == Type.ASSERTION_PARAMETER_NODE) {
            if (nodeData == null) {
                buffer.append(innerIndent).append("no parameter data set");
            }
            else {
                nodeData.toString(indentLevel + 1, buffer);
            }
            buffer.append(PolicyUtils.Text.NEW_LINE);
        }

        if (children.size() > 0) {
            for (ModelNode child : children) {
                child.toString(indentLevel + 1, buffer).append(PolicyUtils.Text.NEW_LINE);
            }
        } else {
            buffer.append(innerIndent).append("no child nodes").append(PolicyUtils.Text.NEW_LINE);
        }

        buffer.append(indent).append('}');
        return buffer;
    }

    @Override
    protected ModelNode clone() throws CloneNotSupportedException {
        final ModelNode clone = (ModelNode) super.clone();

        if (this.nodeData != null) {
            clone.nodeData = this.nodeData.clone();
        }

        // no need to clone PolicyReferenceData, since those are immutable

        if (this.referencedModel != null) {
            clone.referencedModel = this.referencedModel.clone();
        }


        clone.children = new LinkedList<ModelNode>();
        clone.unmodifiableViewOnContent = Collections.unmodifiableCollection(clone.children);

        for (ModelNode thisChild : this.children) {
            clone.addChild(thisChild.clone());
        }

        return clone;
    }

    PolicyReferenceData getReferenceData() {
        return referenceData;
    }
}
