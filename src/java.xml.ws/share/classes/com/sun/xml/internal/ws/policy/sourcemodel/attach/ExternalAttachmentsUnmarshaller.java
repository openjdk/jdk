/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.policy.sourcemodel.attach;

import com.sun.xml.internal.ws.policy.Policy;
import com.sun.xml.internal.ws.policy.PolicyConstants;
import com.sun.xml.internal.ws.policy.PolicyException;
import com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;
import com.sun.xml.internal.ws.policy.sourcemodel.PolicyModelTranslator;
import com.sun.xml.internal.ws.policy.sourcemodel.PolicyModelUnmarshaller;
import com.sun.xml.internal.ws.policy.sourcemodel.PolicySourceModel;

import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * Unmarshal external policy attachments.
 *
 * @author Fabian Ritzmann
 */
public class ExternalAttachmentsUnmarshaller {

    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(ExternalAttachmentsUnmarshaller.class);

    public static final URI BINDING_ID;
    public static final URI BINDING_OPERATION_ID;
    public static final URI BINDING_OPERATION_INPUT_ID;
    public static final URI BINDING_OPERATION_OUTPUT_ID;
    public static final URI BINDING_OPERATION_FAULT_ID;

    static {
        try {
            BINDING_ID = new URI("urn:uuid:c9bef600-0d7a-11de-abc1-0002a5d5c51b");
            BINDING_OPERATION_ID = new URI("urn:uuid:62e66b60-0d7b-11de-a1a2-0002a5d5c51b");
            BINDING_OPERATION_INPUT_ID = new URI("urn:uuid:730d8d20-0d7b-11de-84e9-0002a5d5c51b");
            BINDING_OPERATION_OUTPUT_ID = new URI("urn:uuid:85b0f980-0d7b-11de-8e9d-0002a5d5c51b");
            BINDING_OPERATION_FAULT_ID = new URI("urn:uuid:917cb060-0d7b-11de-9e80-0002a5d5c51b");
        } catch (URISyntaxException e) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0094_INVALID_URN()), e);
        }
    }

    private static final QName POLICY_ATTACHMENT = new QName("http://www.w3.org/ns/ws-policy", "PolicyAttachment");
    private static final QName APPLIES_TO = new QName("http://www.w3.org/ns/ws-policy", "AppliesTo");
    private static final QName POLICY = new QName("http://www.w3.org/ns/ws-policy", "Policy");
    private static final QName URI = new QName("http://www.w3.org/ns/ws-policy", "URI");
    private static final QName POLICIES = new QName(PolicyConstants.SUN_MANAGEMENT_NAMESPACE, "Policies");
    private static final ContextClassloaderLocal<XMLInputFactory> XML_INPUT_FACTORY = new ContextClassloaderLocal<XMLInputFactory>() {
        @Override
        protected XMLInputFactory initialValue() throws Exception {
            return XMLInputFactory.newInstance();
        }
    };

    private static final PolicyModelUnmarshaller POLICY_UNMARSHALLER = PolicyModelUnmarshaller.getXmlUnmarshaller();

    private final Map<URI, Policy> map = new HashMap<URI, Policy>();
    private URI currentUri = null;
    private Policy currentPolicy = null;

    public static Map<URI, Policy> unmarshal(final Reader source) throws PolicyException {
        LOGGER.entering(source);
        try {
            XMLEventReader reader = XML_INPUT_FACTORY.get().createXMLEventReader(source);
            ExternalAttachmentsUnmarshaller instance = new ExternalAttachmentsUnmarshaller();
            final Map<URI, Policy> map = instance.unmarshal(reader, null);
            LOGGER.exiting(map);
            return Collections.unmodifiableMap(map);
        } catch (XMLStreamException ex) {
            throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0086_FAILED_CREATE_READER(source)), ex);
        }
    }

    private Map<URI, Policy> unmarshal(final XMLEventReader reader, final StartElement parentElement) throws PolicyException {
        XMLEvent event = null;
        while (reader.hasNext()) {
            try {
                event = reader.peek();
                switch (event.getEventType()) {
                    case XMLStreamConstants.START_DOCUMENT:
                    case XMLStreamConstants.COMMENT:
                        reader.nextEvent();
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        processCharacters(event.asCharacters(), parentElement, map);
                        reader.nextEvent();
                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        processEndTag(event.asEndElement(), parentElement);
                        reader.nextEvent();
                        return map;

                    case XMLStreamConstants.START_ELEMENT:
                        final StartElement element = event.asStartElement();
                        processStartTag(element, parentElement, reader, map);
                        break;

                    case XMLStreamConstants.END_DOCUMENT:
                        return map;

                    default:
                        throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0087_UNKNOWN_EVENT(event)));
                }
            } catch (XMLStreamException e) {
                final Location location = event == null ? null : event.getLocation();
                throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0088_FAILED_PARSE(location)), e);
            }
        }
        return map;
    }

    private void processStartTag(final StartElement element, final StartElement parent,
            final XMLEventReader reader, final Map<URI, Policy> map)
            throws PolicyException {
        try {
            final QName name = element.getName();
            if (parent == null) {
                if (!name.equals(POLICIES)) {
                    throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0089_EXPECTED_ELEMENT("<Policies>", name, element.getLocation())));
                }
            } else {
                final QName parentName = parent.getName();
                if (parentName.equals(POLICIES)) {
                    if (!name.equals(POLICY_ATTACHMENT)) {
                        throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0089_EXPECTED_ELEMENT("<PolicyAttachment>", name, element.getLocation())));
                    }
                } else if (parentName.equals(POLICY_ATTACHMENT)) {
                    if (name.equals(POLICY)) {
                        readPolicy(reader);
                        return;
                    } else if (!name.equals(APPLIES_TO)) {
                        throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0089_EXPECTED_ELEMENT("<AppliesTo> or <Policy>", name, element.getLocation())));
                    }
                } else if (parentName.equals(APPLIES_TO)) {
                    if (!name.equals(URI)) {
                        throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0089_EXPECTED_ELEMENT("<URI>", name, element.getLocation())));
                    }
                } else {
                    throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0090_UNEXPECTED_ELEMENT(name, element.getLocation())));
                }
            }
            reader.nextEvent();
            this.unmarshal(reader, element);
        } catch (XMLStreamException e) {
            throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0088_FAILED_PARSE(element.getLocation()), e));
        }
    }

    private void readPolicy(final XMLEventReader reader) throws PolicyException {
        final PolicySourceModel policyModel = POLICY_UNMARSHALLER.unmarshalModel(reader);
        final PolicyModelTranslator translator = PolicyModelTranslator.getTranslator();
        final Policy policy = translator.translate(policyModel);
        if (this.currentUri != null) {
            map.put(this.currentUri, policy);
            this.currentUri = null;
            this.currentPolicy = null;
        }
        else {
            this.currentPolicy = policy;
        }
    }

    private void processEndTag(EndElement element, StartElement startElement) throws PolicyException {
        checkEndTagName(startElement.getName(), element);
    }

    private void checkEndTagName(final QName expectedName, final EndElement element) throws PolicyException {
        final QName actualName = element.getName();
        if (!expectedName.equals(actualName)) {
            throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0091_END_ELEMENT_NO_MATCH(expectedName, element, element.getLocation())));
        }

    }

    private void processCharacters(final Characters chars, final StartElement currentElement, final Map<URI, Policy> map)
            throws PolicyException {
        if (chars.isWhiteSpace()) {
            return;
        }
        else {
            final String data = chars.getData();
            if ((currentElement != null) && URI.equals(currentElement.getName())) {
                processUri(chars, map);
                return;
            } else {
                throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0092_CHARACTER_DATA_UNEXPECTED(currentElement, data, chars.getLocation())));
            }

        }
    }

    private void processUri(final Characters chars, final Map<URI, Policy> map) throws PolicyException {
        final String data = chars.getData().trim();
        try {
            final URI uri = new URI(data);
            if (this.currentPolicy != null) {
                map.put(uri, this.currentPolicy);
                this.currentUri = null;
                this.currentPolicy = null;
            } else {
                this.currentUri = uri;
            }
        } catch (URISyntaxException e) {
            throw LOGGER.logSevereException(new PolicyException(LocalizationMessages.WSP_0093_INVALID_URI(data, chars.getLocation())), e);
        }
    }

}
