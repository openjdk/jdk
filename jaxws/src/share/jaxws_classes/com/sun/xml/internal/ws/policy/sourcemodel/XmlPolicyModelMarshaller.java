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

import com.sun.xml.internal.txw2.TXW;
import com.sun.xml.internal.txw2.TypedXmlWriter;
import com.sun.xml.internal.txw2.output.StaxSerializer;
import com.sun.xml.internal.ws.policy.sourcemodel.wspolicy.XmlToken;
import com.sun.xml.internal.ws.policy.sourcemodel.wspolicy.NamespaceVersion;
import com.sun.xml.internal.ws.policy.PolicyConstants;
import com.sun.xml.internal.ws.policy.PolicyException;
import com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamWriter;


public final class XmlPolicyModelMarshaller extends PolicyModelMarshaller {

    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(XmlPolicyModelMarshaller.class);

    private final boolean marshallInvisible;


    XmlPolicyModelMarshaller(boolean marshallInvisible) {
        this.marshallInvisible = marshallInvisible;
    }

    public void marshal(final PolicySourceModel model, final Object storage) throws PolicyException {
        if (storage instanceof StaxSerializer) {
            marshal(model, (StaxSerializer) storage);
        } else if (storage instanceof TypedXmlWriter) {
            marshal(model, (TypedXmlWriter) storage);
        } else if (storage instanceof XMLStreamWriter) {
            marshal(model, (XMLStreamWriter) storage);
        } else {
            throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0022_STORAGE_TYPE_NOT_SUPPORTED(storage.getClass().getName())));
        }
    }

    public void marshal(final Collection<PolicySourceModel> models, final Object storage) throws PolicyException {
        for (PolicySourceModel model : models) {
            marshal(model, storage);
        }
    }

    /**
     * Marshal a policy onto the given StaxSerializer.
     *
     * @param model A policy source model.
     * @param writer A Stax serializer.
     * @throws PolicyException If marshalling failed.
     */
    private void marshal(final PolicySourceModel model, final StaxSerializer writer) throws PolicyException {
        final TypedXmlWriter policy = TXW.create(model.getNamespaceVersion().asQName(XmlToken.Policy), TypedXmlWriter.class, writer);

        marshalDefaultPrefixes(model, policy);
        marshalPolicyAttributes(model, policy);
        marshal(model.getNamespaceVersion(), model.getRootNode(), policy);
        policy.commit();
    }

    /**
     * Marshal a policy onto the given TypedXmlWriter.
     *
     * @param model A policy source model.
     * @param writer A typed XML writer.
     * @throws PolicyException If marshalling failed.
     */
    private void marshal(final PolicySourceModel model, final TypedXmlWriter writer) throws PolicyException {
        final TypedXmlWriter policy = writer._element(model.getNamespaceVersion().asQName(XmlToken.Policy), TypedXmlWriter.class);

        marshalDefaultPrefixes(model, policy);
        marshalPolicyAttributes(model, policy);
        marshal(model.getNamespaceVersion(), model.getRootNode(), policy);
    }

    /**
     * Marshal a policy onto the given XMLStreamWriter.
     *
     * @param model A policy source model.
     * @param writer An XML stream writer.
     * @throws PolicyException If marshalling failed.
     */
    private void marshal(final PolicySourceModel model, final XMLStreamWriter writer) throws PolicyException {
        final StaxSerializer serializer = new StaxSerializer(writer);
        final TypedXmlWriter policy = TXW.create(model.getNamespaceVersion().asQName(XmlToken.Policy), TypedXmlWriter.class, serializer);

        marshalDefaultPrefixes(model, policy);
        marshalPolicyAttributes(model, policy);
        marshal(model.getNamespaceVersion(), model.getRootNode(), policy);
        policy.commit();
        serializer.flush();
    }

    /**
     * Marshal the Policy root element attributes onto the TypedXmlWriter.
     *
     * @param model The policy source model.
     * @param writer The typed XML writer.
     */
    private static void marshalPolicyAttributes(final PolicySourceModel model, final TypedXmlWriter writer) {
        final String policyId = model.getPolicyId();
        if (policyId != null) {
            writer._attribute(PolicyConstants.WSU_ID, policyId);
        }

        final String policyName = model.getPolicyName();
        if (policyName != null) {
            writer._attribute(model.getNamespaceVersion().asQName(XmlToken.Name), policyName);
        }
    }

    /**
     * Marshal given ModelNode and child elements on given TypedXmlWriter.
     *
     * @param nsVersion The WS-Policy version.
     * @param rootNode The ModelNode that is marshalled.
     * @param writer The TypedXmlWriter onto which the content of the rootNode is marshalled.
     */
    private void marshal(final NamespaceVersion nsVersion, final ModelNode rootNode, final TypedXmlWriter writer) {
        for (ModelNode node : rootNode) {
            final AssertionData data = node.getNodeData();
            if (marshallInvisible || data == null || !data.isPrivateAttributeSet()) {
                TypedXmlWriter child = null;
                if (data == null) {
                    child = writer._element(nsVersion.asQName(node.getType().getXmlToken()), TypedXmlWriter.class);
                } else {
                    child = writer._element(data.getName(), TypedXmlWriter.class);
                    final String value = data.getValue();
                    if (value != null) {
                        child._pcdata(value);
                    }
                    if (data.isOptionalAttributeSet()) {
                        child._attribute(nsVersion.asQName(XmlToken.Optional), Boolean.TRUE);
                    }
                    if (data.isIgnorableAttributeSet()) {
                        child._attribute(nsVersion.asQName(XmlToken.Ignorable), Boolean.TRUE);
                    }
                    for (Entry<QName, String> entry : data.getAttributesSet()) {
                        child._attribute(entry.getKey(), entry.getValue());
                    }
                }
                marshal(nsVersion, node, child);
            }
        }
    }

    /**
     * Write default prefixes onto the given TypedXmlWriter
     *
     * @param model The policy source model. May not be null.
     * @param writer The TypedXmlWriter. May not be null.
     * @throws PolicyException If the creation of the prefix to namespace URI map failed.
     */
    private void marshalDefaultPrefixes(final PolicySourceModel model, final TypedXmlWriter writer) throws PolicyException {
        final Map<String, String> nsMap = model.getNamespaceToPrefixMapping();
        if (!marshallInvisible && nsMap.containsKey(PolicyConstants.SUN_POLICY_NAMESPACE_URI)) {
            nsMap.remove(PolicyConstants.SUN_POLICY_NAMESPACE_URI);
        }
        for (Map.Entry<String, String> nsMappingEntry : nsMap.entrySet()) {
            writer._namespace(nsMappingEntry.getKey(), nsMappingEntry.getValue());
        }
    }

}
