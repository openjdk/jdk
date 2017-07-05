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

package com.sun.xml.internal.ws.policy.sourcemodel;

import com.sun.xml.internal.ws.policy.PolicyConstants;
import com.sun.xml.internal.ws.policy.PolicyException;
import com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;
import com.sun.xml.internal.ws.policy.sourcemodel.wspolicy.NamespaceVersion;
import com.sun.xml.internal.ws.policy.sourcemodel.wspolicy.XmlToken;

import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * Unmarshal XML policy expressions.
 *
 * @author Marek Potociar
 * @author Fabian Ritzmann
 */
public class XmlPolicyModelUnmarshaller extends PolicyModelUnmarshaller {

    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(XmlPolicyModelUnmarshaller.class);

    /**
     * Creates a new instance of XmlPolicyModelUnmarshaller
     */
    protected XmlPolicyModelUnmarshaller() {
        // nothing to initialize
    }

    /**
     * See {@link PolicyModelUnmarshaller#unmarshalModel(Object) base method documentation}.
     */
    public PolicySourceModel unmarshalModel(final Object storage) throws PolicyException {
        final XMLEventReader reader = createXMLEventReader(storage);
        PolicySourceModel model = null;

        loop:
        while (reader.hasNext()) {
            try {
                final XMLEvent event = reader.peek();
                switch (event.getEventType()) {
                    case XMLStreamConstants.START_DOCUMENT:
                    case XMLStreamConstants.COMMENT:
                        reader.nextEvent();
                        break; // skipping the comments and start document events
                    case XMLStreamConstants.CHARACTERS:
                        processCharacters(ModelNode.Type.POLICY, event.asCharacters(), null);
                        // we advance the reader only if there is no exception thrown from
                        // the processCharacters(...) call. Otherwise we don't modify the stream
                        reader.nextEvent();
                        break;
                    case XMLStreamConstants.START_ELEMENT:
                        if (NamespaceVersion.resolveAsToken(event.asStartElement().getName()) == XmlToken.Policy) {
                            StartElement rootElement = reader.nextEvent().asStartElement();

                            model = initializeNewModel(rootElement);
                            unmarshalNodeContent(model.getNamespaceVersion(), model.getRootNode(), rootElement.getName(), reader);

                            break loop;
                        } else {
                            throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0048_POLICY_ELEMENT_EXPECTED_FIRST()));
                        }
                    default:
                        throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0048_POLICY_ELEMENT_EXPECTED_FIRST()));
                }
            } catch (XMLStreamException e) {
                throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0068_FAILED_TO_UNMARSHALL_POLICY_EXPRESSION(), e));
            }
        }
        return model;
    }

    /**
     * Allow derived classes to pass in a custom instance of PolicySourceModel.
     *
     * @param nsVersion
     * @param id
     * @param name
     * @return
     */
    protected PolicySourceModel createSourceModel(NamespaceVersion nsVersion, String id, String name) {
        return PolicySourceModel.createPolicySourceModel(nsVersion, id, name);
    }

    private PolicySourceModel initializeNewModel(final StartElement element) throws PolicyException, XMLStreamException {
        PolicySourceModel model;

        final NamespaceVersion nsVersion = NamespaceVersion.resolveVersion(element.getName().getNamespaceURI());

        final Attribute policyName = getAttributeByName(element, nsVersion.asQName(XmlToken.Name));
        final Attribute xmlId = getAttributeByName(element, PolicyConstants.XML_ID);
        Attribute policyId = getAttributeByName(element, PolicyConstants.WSU_ID);

        if (policyId == null) {
            policyId = xmlId;
        } else if (xmlId != null) {
            throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0058_MULTIPLE_POLICY_IDS_NOT_ALLOWED()));
        }

        model = createSourceModel(nsVersion,
                (policyId == null) ? null : policyId.getValue(),
                (policyName == null) ? null : policyName.getValue());

        return model;
    }

    private ModelNode addNewChildNode(final NamespaceVersion nsVersion, final ModelNode parentNode, final StartElement childElement) throws PolicyException {
        ModelNode childNode;
        final QName childElementName = childElement.getName();
        if (parentNode.getType() == ModelNode.Type.ASSERTION_PARAMETER_NODE) {
            childNode = parentNode.createChildAssertionParameterNode();
        } else {
            XmlToken token = NamespaceVersion.resolveAsToken(childElementName);

            switch (token) {
                case Policy:
                    childNode = parentNode.createChildPolicyNode();
                    break;
                case All:
                    childNode = parentNode.createChildAllNode();
                    break;
                case ExactlyOne:
                    childNode = parentNode.createChildExactlyOneNode();
                    break;
                case PolicyReference:
                    final Attribute uri = getAttributeByName(childElement, nsVersion.asQName(XmlToken.Uri));
                    if (uri == null) {
                        throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0040_POLICY_REFERENCE_URI_ATTR_NOT_FOUND()));
                    } else {
                        try {
                            final URI reference = new URI(uri.getValue());
                            final Attribute digest = getAttributeByName(childElement, nsVersion.asQName(XmlToken.Digest));
                            PolicyReferenceData refData;
                            if (digest == null) {
                                refData = new PolicyReferenceData(reference);
                            } else {
                                final Attribute digestAlgorithm = getAttributeByName(childElement, nsVersion.asQName(XmlToken.DigestAlgorithm));
                                URI algorithmRef = null;
                                if (digestAlgorithm != null) {
                                    algorithmRef = new URI(digestAlgorithm.getValue());
                                }
                                refData = new PolicyReferenceData(reference, digest.getValue(), algorithmRef);
                            }
                            childNode = parentNode.createChildPolicyReferenceNode(refData);
                        } catch (URISyntaxException e) {
                            throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0012_UNABLE_TO_UNMARSHALL_POLICY_MALFORMED_URI(), e));
                        }
                    }
                    break;
                default:
                    if (parentNode.isDomainSpecific()) {
                        childNode = parentNode.createChildAssertionParameterNode();
                    } else {
                        childNode = parentNode.createChildAssertionNode();
                    }
            }
        }

        return childNode;
    }

    private void parseAssertionData(NamespaceVersion nsVersion, String value, ModelNode childNode, final StartElement childElement) throws IllegalArgumentException, PolicyException {
        // finish assertion node processing: create and set assertion data...
        final Map<QName, String> attributeMap = new HashMap<QName, String>();
        boolean optional = false;
        boolean ignorable = false;

        final Iterator iterator = childElement.getAttributes();
        while (iterator.hasNext()) {
            final Attribute nextAttribute = (Attribute) iterator.next();
            final QName name = nextAttribute.getName();
            if (attributeMap.containsKey(name)) {
                throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0059_MULTIPLE_ATTRS_WITH_SAME_NAME_DETECTED_FOR_ASSERTION(nextAttribute.getName(), childElement.getName())));
            } else {
                if (nsVersion.asQName(XmlToken.Optional).equals(name)) {
                    optional = parseBooleanValue(nextAttribute.getValue());
                } else if (nsVersion.asQName(XmlToken.Ignorable).equals(name)) {
                    ignorable = parseBooleanValue(nextAttribute.getValue());
                } else {
                    attributeMap.put(name, nextAttribute.getValue());
                }
            }
        }
        final AssertionData nodeData = new AssertionData(childElement.getName(), value, attributeMap, childNode.getType(), optional, ignorable);

        // check visibility value syntax if present...
        if (nodeData.containsAttribute(PolicyConstants.VISIBILITY_ATTRIBUTE)) {
            final String visibilityValue = nodeData.getAttributeValue(PolicyConstants.VISIBILITY_ATTRIBUTE);
            if (!PolicyConstants.VISIBILITY_VALUE_PRIVATE.equals(visibilityValue)) {
                throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0004_UNEXPECTED_VISIBILITY_ATTR_VALUE(visibilityValue)));
            }
        }

        childNode.setOrReplaceNodeData(nodeData);
    }

    private Attribute getAttributeByName(final StartElement element,
            final QName attributeName) {
        // call standard API method to retrieve the attribute by name
        Attribute attribute = element.getAttributeByName(attributeName);

        // try to find the attribute without a prefix.
        if (attribute == null) {
            final String localAttributeName = attributeName.getLocalPart();
            final Iterator iterator = element.getAttributes();
            while (iterator.hasNext()) {
                final Attribute nextAttribute = (Attribute) iterator.next();
                final QName aName = nextAttribute.getName();
                final boolean attributeFoundByWorkaround = aName.equals(attributeName) || (aName.getLocalPart().equals(localAttributeName) && (aName.getPrefix() == null || "".equals(aName.getPrefix())));
                if (attributeFoundByWorkaround) {
                    attribute = nextAttribute;
                    break;
                }

            }
        }

        return attribute;
    }

    private String unmarshalNodeContent(final NamespaceVersion nsVersion, final ModelNode node, final QName nodeElementName, final XMLEventReader reader) throws PolicyException {
        StringBuilder valueBuffer = null;

        loop:
        while (reader.hasNext()) {
            try {
                final XMLEvent xmlParserEvent = reader.nextEvent();
                switch (xmlParserEvent.getEventType()) {
                    case XMLStreamConstants.COMMENT:
                        break; // skipping the comments
                    case XMLStreamConstants.CHARACTERS:
                        valueBuffer = processCharacters(node.getType(), xmlParserEvent.asCharacters(), valueBuffer);
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        checkEndTagName(nodeElementName, xmlParserEvent.asEndElement());
                        break loop; // data exctraction for currently processed policy node is done
                    case XMLStreamConstants.START_ELEMENT:
                        final StartElement childElement = xmlParserEvent.asStartElement();

                        ModelNode childNode = addNewChildNode(nsVersion, node, childElement);
                        String value = unmarshalNodeContent(nsVersion, childNode, childElement.getName(), reader);

                        if (childNode.isDomainSpecific()) {
                            parseAssertionData(nsVersion, value, childNode, childElement);
                        }
                        break;
                    default:
                        throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0011_UNABLE_TO_UNMARSHALL_POLICY_XML_ELEM_EXPECTED()));
                }
            } catch (XMLStreamException e) {
                throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0068_FAILED_TO_UNMARSHALL_POLICY_EXPRESSION(), e));
            }
        }

        return (valueBuffer == null) ? null : valueBuffer.toString().trim();
    }

    /**
     * Method checks if the storage type is supported and transforms it to XMLEventReader instance which is then returned.
     * Throws PolicyException if the transformation is not succesfull or if the storage type is not supported.
     *
     * @param storage An XMLEventReader instance.
     * @return The storage cast to an XMLEventReader.
     * @throws PolicyException If the XMLEventReader cast failed.
     */
    private XMLEventReader createXMLEventReader(final Object storage)
            throws PolicyException {
        if (storage instanceof XMLEventReader) {
            return (XMLEventReader) storage;
        }
        else if (!(storage instanceof Reader)) {
            throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0022_STORAGE_TYPE_NOT_SUPPORTED(storage.getClass().getName())));
        }

        try {
            return XMLInputFactory.newInstance().createXMLEventReader((Reader) storage);
        } catch (XMLStreamException e) {
            throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0014_UNABLE_TO_INSTANTIATE_READER_FOR_STORAGE(), e));
        }

    }

    /**
     * Method checks whether the actual name of the end tag is equal to the expected name - the name of currently unmarshalled
     * XML policy model element. Throws exception, if the two FQNs are not equal as expected.
     *
     * @param expected The expected element name.
     * @param element The actual element.
     * @throws PolicyException If the actual element name did not match the expected element.
     */
    private void checkEndTagName(final QName expected, final EndElement element) throws PolicyException {
        final QName actual = element.getName();
        if (!expected.equals(actual)) {
            throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0003_UNMARSHALLING_FAILED_END_TAG_DOES_NOT_MATCH(expected, actual)));
        }

    }

    private StringBuilder processCharacters(final ModelNode.Type currentNodeType, final Characters characters,
            final StringBuilder currentValueBuffer)
            throws PolicyException {
        if (characters.isWhiteSpace()) {
            return currentValueBuffer;
        } else {
            final StringBuilder buffer = (currentValueBuffer == null) ? new StringBuilder() : currentValueBuffer;
            final String data = characters.getData();
            if (currentNodeType == ModelNode.Type.ASSERTION || currentNodeType == ModelNode.Type.ASSERTION_PARAMETER_NODE) {
                return buffer.append(data);
            } else {
                throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0009_UNEXPECTED_CDATA_ON_SOURCE_MODEL_NODE(currentNodeType, data)));
            }

        }
    }

    /**
     * Return true if the value is "true" or "1". Return false if the value is
     * "false" or "0". Throw an exception otherwise. The test is case sensitive.
     *
     * @param value The String representation of the value. Must not be null.
     * @return True if the value is "true" or "1". False if the value is
     *   "false" or "0".
     * @throws PolicyException If the value is not "true", "false", "0" or "1".
     */
    private boolean parseBooleanValue(String value) throws PolicyException {
        if ("true".equals(value) || "1".equals(value)) {
            return true;
        }
        else if ("false".equals(value) || "0".equals(value)) {
            return false;
        }
        throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0095_INVALID_BOOLEAN_VALUE(value)));
    }

}
