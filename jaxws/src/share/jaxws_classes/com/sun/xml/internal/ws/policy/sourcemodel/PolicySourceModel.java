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

import com.sun.xml.internal.ws.policy.sourcemodel.wspolicy.NamespaceVersion;
import com.sun.xml.internal.ws.policy.PolicyConstants;
import com.sun.xml.internal.ws.policy.PolicyException;
import com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import com.sun.xml.internal.ws.policy.spi.PrefixMapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import javax.xml.namespace.QName;

/**
 * This class is a root of unmarshaled policy source structure. Each instance of the class contains factory method
 * to create new {@link com.sun.xml.internal.ws.policy.sourcemodel.ModelNode} instances associated with the actual model instance.
 *
 * @author Marek Potociar
 * @author Fabian Ritzmann
 */
public class PolicySourceModel implements Cloneable {

    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(PolicySourceModel.class);

    private static final Map<String, String> DEFAULT_NAMESPACE_TO_PREFIX = new HashMap<String, String>();
    static {
        PrefixMapper[] prefixMappers = PolicyUtils.ServiceProvider.load(PrefixMapper.class);
        if (prefixMappers != null) {
            for (PrefixMapper mapper: prefixMappers) {
                DEFAULT_NAMESPACE_TO_PREFIX.putAll(mapper.getPrefixMap());
            }
        }

        for (NamespaceVersion version : NamespaceVersion.values()) {
            DEFAULT_NAMESPACE_TO_PREFIX.put(version.toString(), version.getDefaultNamespacePrefix());
        }
        DEFAULT_NAMESPACE_TO_PREFIX.put(PolicyConstants.WSU_NAMESPACE_URI,
                PolicyConstants.WSU_NAMESPACE_PREFIX);
        DEFAULT_NAMESPACE_TO_PREFIX.put(PolicyConstants.SUN_POLICY_NAMESPACE_URI,
                PolicyConstants.SUN_POLICY_NAMESPACE_PREFIX);
    }

    // Map namespaces to prefixes
    private final Map<String, String> namespaceToPrefix =
            new HashMap<String, String>(DEFAULT_NAMESPACE_TO_PREFIX);

    private ModelNode rootNode;
    private final String policyId;
    private final String policyName;
    private final NamespaceVersion nsVersion;
    private final List<ModelNode> references = new LinkedList<ModelNode>(); // links to policy reference nodes
    private boolean expanded = false;


    /**
     * Factory method that creates new policy source model instance.
     *
     * This method is only intended to be used by code that has no dependencies on
     * JAX-WS. Otherwise use com.sun.xml.internal.ws.policy.api.SourceModel.
     *
     * @param nsVersion The policy version
     * @return Newly created policy source model instance.
     */
    public static PolicySourceModel createPolicySourceModel(final NamespaceVersion nsVersion) {
        return new PolicySourceModel(nsVersion);
    }

    /**
     * Factory method that creates new policy source model instance and initializes it according to parameters provided.
     *
     * This method is only intended to be used by code that has no dependencies on
     * JAX-WS. Otherwise use com.sun.xml.internal.ws.policy.api.SourceModel.
     *
     * @param nsVersion The policy version
     * @param policyId local policy identifier - relative URI. May be {@code null}.
     * @param policyName global policy identifier - absolute policy expression URI. May be {@code null}.
     * @return Newly created policy source model instance with its name and id properly set.
     */
    public static PolicySourceModel createPolicySourceModel(final NamespaceVersion nsVersion, final String policyId, final String policyName) {
        return new PolicySourceModel(nsVersion, policyId, policyName);
    }

    /**
     * Constructor that creates a new policy source model instance without any
     * id or name identifier. The namespace-to-prefix map is initialized with mapping
     * of policy namespace to the default value set by
     * {@link PolicyConstants#POLICY_NAMESPACE_PREFIX POLICY_NAMESPACE_PREFIX constant}.
     *
     * @param nsVersion The WS-Policy version.
     */
    private PolicySourceModel(NamespaceVersion nsVersion) {
        this(nsVersion, null, null);
    }

    /**
     * Constructor that creates a new policy source model instance with given
     * id or name identifier.
     *
     * @param nsVersion The WS-Policy version.
     * @param policyId Relative policy reference within an XML document. May be {@code null}.
     * @param policyName Absolute IRI of policy expression. May be {@code null}.
     */
    private PolicySourceModel(NamespaceVersion nsVersion, String policyId, String policyName) {
        this(nsVersion, policyId, policyName, null);
    }

    /**
     * Constructor that creates a new policy source model instance with given
     * id or name identifier and a set of PrefixMappers.
     *
     * This constructor is intended to be used by the JAX-WS com.sun.xml.internal.ws.policy.api.SourceModel.
     *
     * @param nsVersion The WS-Policy version.
     * @param policyId Relative policy reference within an XML document. May be {@code null}.
     * @param policyName Absolute IRI of policy expression. May be {@code null}.
     * @param prefixMappers A collection of PrefixMappers to be used with this instance. May be {@code null}.
     */
    protected PolicySourceModel(NamespaceVersion nsVersion, String policyId,
            String policyName, Collection<PrefixMapper> prefixMappers) {
        this.rootNode = ModelNode.createRootPolicyNode(this);
        this.nsVersion = nsVersion;
        this.policyId = policyId;
        this.policyName = policyName;
        if (prefixMappers != null) {
            for (PrefixMapper prefixMapper : prefixMappers) {
                this.namespaceToPrefix.putAll(prefixMapper.getPrefixMap());
            }
        }
    }

    /**
     * Returns a root node of this policy source model. It is allways of POLICY type.
     *
     * @return root policy source model node - allways of POLICY type.
     */
    public ModelNode getRootNode() {
        return rootNode;
    }

    /**
     * Returns a policy name of this policy source model.
     *
     * @return policy name.
     */
    public String getPolicyName() {
        return policyName;
    }

    /**
     * Returns a policy ID of this policy source model.
     *
     * @return policy ID.
     */
    public String getPolicyId() {
        return policyId;
    }

    /**
     * Returns an original namespace version of this policy source model.
     *
     * @return namespace version.
     */
    public NamespaceVersion getNamespaceVersion() {
        return nsVersion;
    }

    /**
     * Provides information about how namespaces used in this {@link PolicySourceModel}
     * instance should be mapped to their default prefixes when marshalled.
     *
     * @return immutable map that holds information about namespaces used in the
     *         model and their mapping to prefixes that should be used when marshalling
     *         this model.
     * @throws PolicyException Thrown if one of the prefix mappers threw an exception.
     */
    Map<String, String> getNamespaceToPrefixMapping() throws PolicyException {
        final Map<String, String> nsToPrefixMap = new HashMap<String, String>();

        final Collection<String> namespaces = getUsedNamespaces();
        for (String namespace : namespaces) {
            final String prefix = getDefaultPrefix(namespace);
            if (prefix != null) {
                nsToPrefixMap.put(namespace, prefix);
            }
        }

        return nsToPrefixMap;
    }

    /**
     * An {@code Object.equals(Object obj)} method override.
     * <p/>
     * When child nodes are tested for equality, the parent policy source model is not considered. Thus two different
     * policy source models instances may be equal based on their node content.
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof PolicySourceModel)) {
            return false;
        }

        boolean result = true;
        final PolicySourceModel that = (PolicySourceModel) obj;

        result = result && ((this.policyId == null) ? that.policyId == null : this.policyId.equals(that.policyId));
        result = result && ((this.policyName == null) ? that.policyName == null : this.policyName.equals(that.policyName));
        result = result && this.rootNode.equals(that.rootNode);

        return result;
    }

    /**
     * An {@code Object.hashCode()} method override.
     */
    @Override
    public int hashCode() {
        int result = 17;

        result = 37 * result + ((this.policyId == null) ? 0 : this.policyId.hashCode());
        result = 37 * result + ((this.policyName == null) ? 0 : this.policyName.hashCode());
        result = 37 * result + this.rootNode.hashCode();

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
        final String innerIndent = PolicyUtils.Text.createIndent(1);
        final StringBuffer buffer = new StringBuffer(60);

        buffer.append("Policy source model {").append(PolicyUtils.Text.NEW_LINE);
        buffer.append(innerIndent).append("policy id = '").append(policyId).append('\'').append(PolicyUtils.Text.NEW_LINE);
        buffer.append(innerIndent).append("policy name = '").append(policyName).append('\'').append(PolicyUtils.Text.NEW_LINE);
        rootNode.toString(1, buffer).append(PolicyUtils.Text.NEW_LINE).append('}');

        return buffer.toString();
    }

    @Override
    protected PolicySourceModel clone() throws CloneNotSupportedException {
        final PolicySourceModel clone = (PolicySourceModel) super.clone();

        clone.rootNode = this.rootNode.clone();
        try {
            clone.rootNode.setParentModel(clone);
        } catch (IllegalAccessException e) {
            throw LOGGER.logSevereException(new CloneNotSupportedException(LocalizationMessages.WSP_0013_UNABLE_TO_SET_PARENT_MODEL_ON_ROOT()), e);
        }

        return clone;
    }

    /**
     * Returns a boolean value indicating whether this policy source model contains references to another policy source models.
     * <p/>
     * Every source model that references other policies must be expanded before it can be translated into a Policy objects. See
     * {@link #expand(PolicySourceModelContext)} and {@link #isExpanded()} for more details.
     *
     * @return {@code true} or {code false} depending on whether this policy source model contains references to another policy source models.
     */
    public boolean containsPolicyReferences() {
        return !references.isEmpty();
    }

    /**
     * Returns a boolean value indicating whether this policy source model contains is already expanded (i.e. contains no unexpanded
     * policy references) or not. This means that if model does not originally contain any policy references, it is considered as expanded,
     * thus this method returns {@code true} in such case. Also this method does not check whether the references policy source models are expanded
     * as well, so after expanding this model a value of {@code true} is returned even if referenced models are not expanded yet. Thus each model
     * can be considered to be fully expanded only if all policy source models stored in PolicySourceModelContext instance are expanded, provided the
     * PolicySourceModelContext instance contains full set of policy source models.
     * <p/>
     * Every source model that references other policies must be expanded before it can be translated into a Policy object. See
     * {@link #expand(PolicySourceModelContext)} and {@link #containsPolicyReferences()} for more details.
     *
     * @return {@code true} or {@code false} depending on whether this policy source model contains is expanded or not.
     */
    private boolean isExpanded() {
        return references.isEmpty() || expanded;
    }

    /**
     * Expands current policy model. This means, that if this model contains any (unexpanded) policy references, then the method expands those
     * references by placing the content of the referenced policy source models under the policy reference nodes. This operation merely creates
     * a link between this and referenced policy source models. Thus any change in the referenced models will be visible wihtin this model as well.
     * <p/>
     * Please, notice that the method does not check if the referenced models are already expanded nor does the method try to expand unexpanded
     * referenced models. This must be preformed manually within client's code. Consecutive calls of this method will have no effect.
     * <p/>
     * Every source model that references other policies must be expanded before it can be translated into a Policy object. See
     * {@link #isExpanded()} and {@link #containsPolicyReferences()} for more details.
     *
     * @param context a policy source model context holding the set of unmarshalled policy source models within the same context.
     * @throws PolicyException Thrown if a referenced policy could not be resolved
     */
    public synchronized void expand(final PolicySourceModelContext context) throws PolicyException {
        if (!isExpanded()) {
            for (ModelNode reference : references) {
                final PolicyReferenceData refData = reference.getPolicyReferenceData();
                final String digest = refData.getDigest();
                PolicySourceModel referencedModel;
                if (digest == null) {
                    referencedModel = context.retrieveModel(refData.getReferencedModelUri());
                } else {
                    referencedModel = context.retrieveModel(refData.getReferencedModelUri(), refData.getDigestAlgorithmUri(), digest);
                }

                reference.setReferencedModel(referencedModel);
            }
            expanded = true;
        }
    }

    /**
     * Adds new policy reference to the policy source model. The method is used by
     * the ModelNode instances of type POLICY_REFERENCE that need to register themselves
     * as policy references in the model.
     *
     * @param node policy reference model node to be registered as a policy reference
     *        in this model.
     */
    void addNewPolicyReference(final ModelNode node) {
        if (node.getType() != ModelNode.Type.POLICY_REFERENCE) {
            throw new IllegalArgumentException(LocalizationMessages.WSP_0042_POLICY_REFERENCE_NODE_EXPECTED_INSTEAD_OF(node.getType()));
        }

        references.add(node);
    }

    /**
     * Iterates through policy vocabulary and extracts set of namespaces used in
     * the policy expression.
     *
     * @return collection of used namespaces within given policy instance
     * @throws PolicyException Thrown if internal processing failed.
     */
    private Collection<String> getUsedNamespaces() throws PolicyException {
        final Set<String> namespaces = new HashSet<String>();
        namespaces.add(getNamespaceVersion().toString());

        if (this.policyId != null) {
            namespaces.add(PolicyConstants.WSU_NAMESPACE_URI);
        }

        final Queue<ModelNode> nodesToBeProcessed = new LinkedList<ModelNode>();
        nodesToBeProcessed.add(rootNode);

        ModelNode processedNode;
        while ((processedNode = nodesToBeProcessed.poll()) != null) {
            for (ModelNode child : processedNode.getChildren()) {
                if (child.hasChildren()) {
                    if (!nodesToBeProcessed.offer(child)) {
                        throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0081_UNABLE_TO_INSERT_CHILD(nodesToBeProcessed, child)));
                    }
                }

                if (child.isDomainSpecific()) {
                    final AssertionData nodeData = child.getNodeData();
                    namespaces.add(nodeData.getName().getNamespaceURI());
                    if (nodeData.isPrivateAttributeSet()) {
                        namespaces.add(PolicyConstants.SUN_POLICY_NAMESPACE_URI);
                    }

                    for (Entry<QName, String> attribute : nodeData.getAttributesSet()) {
                        namespaces.add(attribute.getKey().getNamespaceURI());
                    }
                }
            }
        }

        return namespaces;
    }

    /**
     * Method retrieves default prefix for given namespace. Method returns null if
     * no default prefix is defined..
     *
     * @param namespace to get default prefix for.
     * @return default prefix for given namespace. May return {@code null} if the
     *         default prefix for given namespace is not defined.
     */
    private String getDefaultPrefix(final String namespace) {
        return namespaceToPrefix.get(namespace);
    }
}
