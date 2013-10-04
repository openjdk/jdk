/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.policy.jaxws;

import com.sun.xml.internal.ws.api.model.wsdl.WSDLObject;
import com.sun.xml.internal.ws.api.model.wsdl.editable.*;
import com.sun.xml.internal.ws.api.wsdl.parser.WSDLParserExtension;
import com.sun.xml.internal.ws.api.wsdl.parser.WSDLParserExtensionContext;
import com.sun.xml.internal.ws.api.policy.PolicyResolver;
import com.sun.xml.internal.ws.resources.PolicyMessages;
import com.sun.xml.internal.ws.policy.jaxws.SafePolicyReader.PolicyRecord;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import com.sun.xml.internal.ws.policy.sourcemodel.PolicySourceModel;
import com.sun.xml.internal.ws.policy.sourcemodel.PolicySourceModelContext;
import com.sun.xml.internal.ws.policy.sourcemodel.wspolicy.NamespaceVersion;
import com.sun.xml.internal.ws.policy.sourcemodel.wspolicy.XmlToken;
import com.sun.xml.internal.ws.policy.PolicyException;
import com.sun.xml.internal.ws.policy.PolicyMap;
import com.sun.xml.internal.ws.util.xml.XmlUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.WebServiceException;

/**
 * This class parses the Policy Attachments in the WSDL and creates a PolicyMap thaty captures the policies configured on
 * different PolicySubjects in the wsdl.
 *
 * After, it is finished it sets the PolicyMap on the WSDLModel.
 *
 * @author Jakub Podlesak (jakub.podlesak at sun.com)
 * @author Fabian Ritzmann
 * @author Rama Pulavarthi
 */
final public class PolicyWSDLParserExtension extends WSDLParserExtension {

    enum HandlerType {
        PolicyUri, AnonymousPolicyId
    }

    final static class PolicyRecordHandler {
        String handler;
        HandlerType type;

        PolicyRecordHandler(HandlerType type, String handler) {
            this.type = type;
            this.handler = handler;
        }

        HandlerType getType() {
            return type;
        }

        String getHandler() {
            return handler;
        }
    }

    private static final PolicyLogger LOGGER = PolicyLogger.getLogger(PolicyWSDLParserExtension.class);

    //anonymous policy id prefix
    private static final StringBuffer AnonymnousPolicyIdPrefix = new StringBuffer("#__anonymousPolicy__ID");

    // anonymous policies count
    private int anonymousPoliciesCount;

    private final SafePolicyReader policyReader = new SafePolicyReader();

    // policy queue -- needed for evaluating the right order policy of policy models expansion
    private PolicyRecord expandQueueHead = null;

    // storage for policy models with an id passed by
    private Map<String,PolicyRecord> policyRecordsPassedBy = null;
    // storage for anonymous policies defined within given WSDL
    private Map<String,PolicySourceModel> anonymousPolicyModels = null;

    // container for URIs of policies referenced
    private List<String> unresolvedUris = null;

    // structures for policies really needed to build a map
    private final LinkedList<String> urisNeeded = new LinkedList<String>();
    private final Map<String, PolicySourceModel> modelsNeeded = new HashMap<String, PolicySourceModel>();

    // lookup tables for Policy attachments found
    private Map<WSDLObject, Collection<PolicyRecordHandler>> handlers4ServiceMap = null;
    private Map<WSDLObject, Collection<PolicyRecordHandler>> handlers4PortMap = null;
    private Map<WSDLObject, Collection<PolicyRecordHandler>> handlers4PortTypeMap = null;
    private Map<WSDLObject, Collection<PolicyRecordHandler>> handlers4BindingMap = null;
    private Map<WSDLObject, Collection<PolicyRecordHandler>> handlers4BoundOperationMap = null;
    private Map<WSDLObject, Collection<PolicyRecordHandler>> handlers4OperationMap = null;
    private Map<WSDLObject, Collection<PolicyRecordHandler>> handlers4MessageMap = null;
    private Map<WSDLObject, Collection<PolicyRecordHandler>> handlers4InputMap = null;
    private Map<WSDLObject, Collection<PolicyRecordHandler>> handlers4OutputMap = null;
    private Map<WSDLObject, Collection<PolicyRecordHandler>> handlers4FaultMap = null;
    private Map<WSDLObject, Collection<PolicyRecordHandler>> handlers4BindingInputOpMap = null;
    private Map<WSDLObject, Collection<PolicyRecordHandler>> handlers4BindingOutputOpMap = null;
    private Map<WSDLObject, Collection<PolicyRecordHandler>> handlers4BindingFaultOpMap = null;

    private PolicyMapBuilder policyBuilder = new PolicyMapBuilder();

    private boolean isPolicyProcessed(final String policyUri) {
        return modelsNeeded.containsKey(policyUri);
    }

    private void addNewPolicyNeeded(final String policyUri, final PolicySourceModel policyModel) {
        if (!modelsNeeded.containsKey(policyUri)) {
            modelsNeeded.put(policyUri, policyModel);
            urisNeeded.addFirst(policyUri);
        }
    }

    private Map<String, PolicySourceModel> getPolicyModels() {
        return modelsNeeded;
    }

    private Map<String,PolicyRecord> getPolicyRecordsPassedBy() {
        if (null==policyRecordsPassedBy) {
            policyRecordsPassedBy = new HashMap<String,PolicyRecord>();
        }
        return policyRecordsPassedBy;
    }

    private Map<String,PolicySourceModel> getAnonymousPolicyModels() {
        if (null==anonymousPolicyModels) {
            anonymousPolicyModels = new HashMap<String,PolicySourceModel>();
        }
        return anonymousPolicyModels;
    }

    private Map<WSDLObject, Collection<PolicyRecordHandler>> getHandlers4ServiceMap() {
        if (null==handlers4ServiceMap) {
            handlers4ServiceMap = new HashMap<WSDLObject,Collection<PolicyRecordHandler>>();
        }
        return handlers4ServiceMap;
    }

    private Map<WSDLObject, Collection<PolicyRecordHandler>> getHandlers4PortMap() {
        if (null==handlers4PortMap) {
            handlers4PortMap = new HashMap<WSDLObject,Collection<PolicyRecordHandler>>();
        }
        return handlers4PortMap;
    }

    private Map<WSDLObject, Collection<PolicyRecordHandler>> getHandlers4PortTypeMap() {
        if (null==handlers4PortTypeMap) {
            handlers4PortTypeMap = new HashMap<WSDLObject,Collection<PolicyRecordHandler>>();
        }
        return handlers4PortTypeMap;
    }

    private Map<WSDLObject, Collection<PolicyRecordHandler>> getHandlers4BindingMap() {
        if (null==handlers4BindingMap) {
            handlers4BindingMap = new HashMap<WSDLObject,Collection<PolicyRecordHandler>>();
        }
        return handlers4BindingMap;
    }

    private Map<WSDLObject, Collection<PolicyRecordHandler>> getHandlers4OperationMap() {
        if (null==handlers4OperationMap) {
            handlers4OperationMap = new HashMap<WSDLObject,Collection<PolicyRecordHandler>>();
        }
        return handlers4OperationMap;
    }

    private Map<WSDLObject, Collection<PolicyRecordHandler>> getHandlers4BoundOperationMap() {
        if (null==handlers4BoundOperationMap) {
            handlers4BoundOperationMap = new HashMap<WSDLObject,Collection<PolicyRecordHandler>>();
        }
        return handlers4BoundOperationMap;
    }

    private Map<WSDLObject, Collection<PolicyRecordHandler>> getHandlers4MessageMap() {
        if (null==handlers4MessageMap) {
            handlers4MessageMap = new HashMap<WSDLObject,Collection<PolicyRecordHandler>>();
        }
        return handlers4MessageMap;
    }

    private Map<WSDLObject, Collection<PolicyRecordHandler>> getHandlers4InputMap() {
        if (null==handlers4InputMap) {
            handlers4InputMap = new HashMap<WSDLObject,Collection<PolicyRecordHandler>>();
        }
        return handlers4InputMap;
    }

    private Map<WSDLObject, Collection<PolicyRecordHandler>> getHandlers4OutputMap() {
        if (null==handlers4OutputMap) {
            handlers4OutputMap = new HashMap<WSDLObject,Collection<PolicyRecordHandler>>();
        }
        return handlers4OutputMap;
    }

    private Map<WSDLObject, Collection<PolicyRecordHandler>> getHandlers4FaultMap() {
        if (null==handlers4FaultMap) {
            handlers4FaultMap = new HashMap<WSDLObject,Collection<PolicyRecordHandler>>();
        }
        return handlers4FaultMap;
    }

    private Map<WSDLObject, Collection<PolicyRecordHandler>> getHandlers4BindingInputOpMap() {
        if (null==handlers4BindingInputOpMap) {
            handlers4BindingInputOpMap = new HashMap<WSDLObject,Collection<PolicyRecordHandler>>();
        }
        return handlers4BindingInputOpMap;
    }

    private Map<WSDLObject, Collection<PolicyRecordHandler>> getHandlers4BindingOutputOpMap() {
        if (null==handlers4BindingOutputOpMap) {
            handlers4BindingOutputOpMap = new HashMap<WSDLObject,Collection<PolicyRecordHandler>>();
        }
        return handlers4BindingOutputOpMap;
    }

    private Map<WSDLObject, Collection<PolicyRecordHandler>> getHandlers4BindingFaultOpMap() {
        if (null==handlers4BindingFaultOpMap) {
            handlers4BindingFaultOpMap = new HashMap<WSDLObject,Collection<PolicyRecordHandler>>();
        }
        return handlers4BindingFaultOpMap;
    }

    private List<String> getUnresolvedUris(final boolean emptyListNeeded) {
        if ((null == unresolvedUris) || emptyListNeeded) {
            unresolvedUris = new LinkedList<String>();
        }
        return unresolvedUris;
    }



    private void policyRecToExpandQueue(final PolicyRecord policyRec) {
        if (null==expandQueueHead) {
            expandQueueHead = policyRec;
        } else {
            expandQueueHead = expandQueueHead.insert(policyRec);
        }
    }

    /**
     * Creates a new instance of PolicyWSDLParserExtension
     */
    public PolicyWSDLParserExtension() {

    }


    private PolicyRecordHandler readSinglePolicy(final PolicyRecord policyRec, final boolean inner) {
        PolicyRecordHandler handler = null;
        String policyId = policyRec.policyModel.getPolicyId();
        if (policyId == null) {
            policyId = policyRec.policyModel.getPolicyName();
        }
        if (policyId != null) {           // policy id defined, keep the policy
            handler = new PolicyRecordHandler(HandlerType.PolicyUri, policyRec.getUri());
            getPolicyRecordsPassedBy().put(policyRec.getUri(), policyRec);
            policyRecToExpandQueue(policyRec);
        } else if (inner) { // no id given to the policy --> keep as an annonymous policy model
            final String anonymousId = AnonymnousPolicyIdPrefix.append(anonymousPoliciesCount++).toString();
            handler = new PolicyRecordHandler(HandlerType.AnonymousPolicyId,anonymousId);
            getAnonymousPolicyModels().put(anonymousId, policyRec.policyModel);
            if (null != policyRec.unresolvedURIs) {
                getUnresolvedUris(false).addAll(policyRec.unresolvedURIs);
            }
        }
        return handler;
    }


    private void addHandlerToMap(
            final Map<WSDLObject, Collection<PolicyRecordHandler>> map, final WSDLObject key, final PolicyRecordHandler handler) {
        if (map.containsKey(key)) {
            map.get(key).add(handler);
        } else {
            final Collection<PolicyRecordHandler> newSet = new LinkedList<PolicyRecordHandler>();
            newSet.add(handler);
            map.put(key,newSet);
        }
    }

    private String getBaseUrl(final String policyUri) {
        if (null == policyUri) {
            return null;
        }
        // TODO: encoded urls (escaped characters) might be a problem ?
        final int fragmentIdx = policyUri.indexOf('#');
        return (fragmentIdx == -1) ? policyUri : policyUri.substring(0, fragmentIdx);
    }

    // adding current url even to locally referenced policies
    // in order to distinguish imported policies
    private void processReferenceUri(
            final String policyUri,
            final WSDLObject element,
            final XMLStreamReader reader,
            final Map<WSDLObject, Collection<PolicyRecordHandler>> map) {

        if (null == policyUri || policyUri.length() == 0) {
            return;
        }
        if ('#' != policyUri.charAt(0)) { // external uri (already)
            getUnresolvedUris(false).add(policyUri);
        }

        addHandlerToMap(map, element,
                new PolicyRecordHandler(
                HandlerType.PolicyUri,
                SafePolicyReader.relativeToAbsoluteUrl(policyUri, reader.getLocation().getSystemId())));
    }

    private boolean processSubelement(
            final WSDLObject element, final XMLStreamReader reader, final Map<WSDLObject, Collection<PolicyRecordHandler>> map) {
        if (NamespaceVersion.resolveAsToken(reader.getName()) == XmlToken.PolicyReference) {     // "PolicyReference" element interests us
            processReferenceUri(policyReader.readPolicyReferenceElement(reader), element, reader, map);
            return true;
        } else if (NamespaceVersion.resolveAsToken(reader.getName()) == XmlToken.Policy) {   // policy could be defined here
            final PolicyRecordHandler handler =
                    readSinglePolicy(
                    policyReader.readPolicyElement(
                    reader,
                    (null == reader.getLocation().getSystemId()) ? // baseUrl
                        "" : reader.getLocation().getSystemId()),
                    true);
            if (null != handler) {           // only policies with an Id can work for us
                addHandlerToMap(map, element, handler);
            } // endif null != handler
            return true; // element consumed
        }//end if Policy element found
        return false;
    }

    private void processAttributes(final WSDLObject element, final XMLStreamReader reader, final Map<WSDLObject, Collection<PolicyRecordHandler>> map) {
        final String[] uriArray = getPolicyURIsFromAttr(reader);
        if (null != uriArray) {
            for (String policyUri : uriArray) {
                processReferenceUri(policyUri, element, reader, map);
            }
        }
    }

    @Override
    public boolean portElements(final EditableWSDLPort port, final XMLStreamReader reader) {
        LOGGER.entering();
        final boolean result = processSubelement(port, reader, getHandlers4PortMap());
        LOGGER.exiting();
        return result;
    }

    @Override
    public void portAttributes(final EditableWSDLPort port, final XMLStreamReader reader) {
        LOGGER.entering();
        processAttributes(port, reader, getHandlers4PortMap());
        LOGGER.exiting();
    }

    @Override
    public boolean serviceElements(final EditableWSDLService service, final XMLStreamReader reader) {
        LOGGER.entering();
        final boolean result = processSubelement(service, reader, getHandlers4ServiceMap());
        LOGGER.exiting();
        return result;
    }

    @Override
    public void serviceAttributes(final EditableWSDLService service, final XMLStreamReader reader) {
        LOGGER.entering();
        processAttributes(service, reader, getHandlers4ServiceMap());
        LOGGER.exiting();
    }


    @Override
    public boolean definitionsElements(final XMLStreamReader reader){
        LOGGER.entering();
        if (NamespaceVersion.resolveAsToken(reader.getName()) == XmlToken.Policy) {     // Only "Policy" element interests me
            readSinglePolicy(
                    policyReader.readPolicyElement(
                    reader,
                    (null == reader.getLocation().getSystemId()) ? // baseUrl
                        "" : reader.getLocation().getSystemId()),
                    false);
            LOGGER.exiting();
            return true;
        }
        LOGGER.exiting();
        return false;
    }

    @Override
    public boolean bindingElements(final EditableWSDLBoundPortType binding, final XMLStreamReader reader) {
        LOGGER.entering();
        final boolean result = processSubelement(binding, reader, getHandlers4BindingMap());
        LOGGER.exiting();
        return result;
    }

    @Override
    public void bindingAttributes(final EditableWSDLBoundPortType binding, final XMLStreamReader reader) {
        LOGGER.entering();
        processAttributes(binding, reader, getHandlers4BindingMap());
        LOGGER.exiting();
    }

    @Override
    public boolean portTypeElements(final EditableWSDLPortType portType, final XMLStreamReader reader) {
        LOGGER.entering();
        final boolean result = processSubelement(portType, reader, getHandlers4PortTypeMap());
        LOGGER.exiting();
        return result;
    }

    @Override
    public void portTypeAttributes(final EditableWSDLPortType portType, final XMLStreamReader reader) {
        LOGGER.entering();
        processAttributes(portType, reader, getHandlers4PortTypeMap());
        LOGGER.exiting();
    }

    @Override
    public boolean portTypeOperationElements(final EditableWSDLOperation operation, final XMLStreamReader reader) {
        LOGGER.entering();
        final boolean result = processSubelement(operation, reader, getHandlers4OperationMap());
        LOGGER.exiting();
        return result;
    }

    @Override
    public void portTypeOperationAttributes(final EditableWSDLOperation operation, final XMLStreamReader reader) {
        LOGGER.entering();
        processAttributes(operation, reader, getHandlers4OperationMap());
        LOGGER.exiting();
    }

    @Override
    public boolean bindingOperationElements(final EditableWSDLBoundOperation boundOperation, final XMLStreamReader reader) {
        LOGGER.entering();
        final boolean result = processSubelement(boundOperation, reader, getHandlers4BoundOperationMap());
        LOGGER.exiting();
        return result;
    }

    @Override
    public void bindingOperationAttributes(final EditableWSDLBoundOperation boundOperation, final XMLStreamReader reader) {
        LOGGER.entering();
        processAttributes(boundOperation, reader, getHandlers4BoundOperationMap());
        LOGGER.exiting();
    }

    @Override
    public boolean messageElements(final EditableWSDLMessage msg, final XMLStreamReader reader) {
        LOGGER.entering();
        final boolean result = processSubelement(msg, reader, getHandlers4MessageMap());
        LOGGER.exiting();
        return result;
    }

    @Override
    public void messageAttributes(final EditableWSDLMessage msg, final XMLStreamReader reader) {
        LOGGER.entering();
        processAttributes(msg, reader, getHandlers4MessageMap());
        LOGGER.exiting();
    }

    @Override
    public boolean portTypeOperationInputElements(final EditableWSDLInput input, final XMLStreamReader reader) {
        LOGGER.entering();
        final boolean result = processSubelement(input, reader, getHandlers4InputMap());
        LOGGER.exiting();
        return result;
    }

    @Override
    public void portTypeOperationInputAttributes(final EditableWSDLInput input, final XMLStreamReader reader) {
        LOGGER.entering();
        processAttributes(input, reader, getHandlers4InputMap());
        LOGGER.exiting();
    }


    @Override
    public boolean portTypeOperationOutputElements(final EditableWSDLOutput output, final XMLStreamReader reader) {
        LOGGER.entering();
        final boolean result = processSubelement(output, reader, getHandlers4OutputMap());
        LOGGER.exiting();
        return result;
    }

    @Override
    public void portTypeOperationOutputAttributes(final EditableWSDLOutput output, final XMLStreamReader reader) {
        LOGGER.entering();
        processAttributes(output, reader, getHandlers4OutputMap());
        LOGGER.exiting();
    }


    @Override
    public boolean portTypeOperationFaultElements(final EditableWSDLFault fault, final XMLStreamReader reader) {
        LOGGER.entering();
        final boolean result = processSubelement(fault, reader, getHandlers4FaultMap());
        LOGGER.exiting();
        return result;
    }

    @Override
    public void portTypeOperationFaultAttributes(final EditableWSDLFault fault, final XMLStreamReader reader) {
        LOGGER.entering();
        processAttributes(fault, reader, getHandlers4FaultMap());
        LOGGER.exiting();
    }

    @Override
    public boolean bindingOperationInputElements(final EditableWSDLBoundOperation operation, final XMLStreamReader reader) {
        LOGGER.entering();
        final boolean result = processSubelement(operation, reader, getHandlers4BindingInputOpMap());
        LOGGER.exiting();
        return result;
    }

    @Override
    public void bindingOperationInputAttributes(final EditableWSDLBoundOperation operation, final XMLStreamReader reader) {
        LOGGER.entering();
        processAttributes(operation, reader, getHandlers4BindingInputOpMap());
        LOGGER.exiting();
    }


    @Override
    public boolean bindingOperationOutputElements(final EditableWSDLBoundOperation operation, final XMLStreamReader reader) {
        LOGGER.entering();
        final boolean result = processSubelement(operation, reader, getHandlers4BindingOutputOpMap());
        LOGGER.exiting();
        return result;
    }

    @Override
    public void bindingOperationOutputAttributes(final EditableWSDLBoundOperation operation, final XMLStreamReader reader) {
        LOGGER.entering();
        processAttributes(operation, reader, getHandlers4BindingOutputOpMap());
        LOGGER.exiting();
    }

    @Override
    public boolean bindingOperationFaultElements(final EditableWSDLBoundFault fault, final XMLStreamReader reader) {
        LOGGER.entering();
        final boolean result = processSubelement(fault, reader, getHandlers4BindingFaultOpMap());
        LOGGER.exiting(result);
        return result;
    }

    @Override
    public void bindingOperationFaultAttributes(final EditableWSDLBoundFault fault, final XMLStreamReader reader) {
        LOGGER.entering();
        processAttributes(fault, reader, getHandlers4BindingFaultOpMap());
        LOGGER.exiting();
    }


    private PolicyMapBuilder getPolicyMapBuilder() {
        if (null == policyBuilder) {
            policyBuilder = new PolicyMapBuilder();
        }
        return policyBuilder;
    }

    private Collection<String> getPolicyURIs(
            final Collection<PolicyRecordHandler> handlers, final PolicySourceModelContext modelContext) throws PolicyException{
        final Collection<String> result = new ArrayList<String>(handlers.size());
        String policyUri;
        for (PolicyRecordHandler handler : handlers) {
            policyUri = handler.handler;
            if (HandlerType.AnonymousPolicyId == handler.type) {
                final PolicySourceModel policyModel = getAnonymousPolicyModels().get(policyUri);
                policyModel.expand(modelContext);
                while (getPolicyModels().containsKey(policyUri)) {
                    policyUri = AnonymnousPolicyIdPrefix.append(anonymousPoliciesCount++).toString();
                }
                getPolicyModels().put(policyUri,policyModel);
            }
            result.add(policyUri);
        }
        return result;
    }

    private boolean readExternalFile(final String fileUrl) {
        InputStream ios = null;
        XMLStreamReader reader = null;
        try {
            final URL xmlURL = new URL(fileUrl);
            ios = xmlURL.openStream();
            reader = XmlUtil.newXMLInputFactory(true).createXMLStreamReader(ios);
            while (reader.hasNext()) {
                if (reader.isStartElement() && NamespaceVersion.resolveAsToken(reader.getName()) == XmlToken.Policy) {
                    readSinglePolicy(policyReader.readPolicyElement(reader, fileUrl), false);
                }
                reader.next();
            }
            return true;
        } catch (IOException ioe) {
            return false;
        } catch (XMLStreamException xmlse) {
            return false;
        } finally {
            PolicyUtils.IO.closeResource(reader);
            PolicyUtils.IO.closeResource(ios);
        }
    }

    @Override
    public void finished(final WSDLParserExtensionContext context) {
        LOGGER.entering(context);
        // need to make sure proper beginning order of internal policies within unresolvedUris list
        if (null != expandQueueHead) { // any policies found
            final List<String> externalUris = getUnresolvedUris(false); // protect list of possible external policies
            getUnresolvedUris(true); // cleaning up the list only
            final LinkedList<String> baseUnresolvedUris = new LinkedList<String>();
            for (PolicyRecord currentRec = expandQueueHead ; null != currentRec ; currentRec = currentRec.next) {
                baseUnresolvedUris.addFirst(currentRec.getUri());
            }
            getUnresolvedUris(false).addAll(baseUnresolvedUris);
            expandQueueHead = null; // cut the queue off
            getUnresolvedUris(false).addAll(externalUris);
        }

        while (!getUnresolvedUris(false).isEmpty()) {
            final List<String> urisToBeSolvedList = getUnresolvedUris(false);
            getUnresolvedUris(true); // just cleaning up the list
            for (String currentUri : urisToBeSolvedList) {
                if (!isPolicyProcessed(currentUri)) {
                    final PolicyRecord prefetchedRecord = getPolicyRecordsPassedBy().get(currentUri);
                    if (null == prefetchedRecord) {
                        if (policyReader.getUrlsRead().contains(getBaseUrl(currentUri))) { // --> unresolvable policy
                            LOGGER.logSevereException(new PolicyException(PolicyMessages.WSP_1014_CAN_NOT_FIND_POLICY(currentUri)));
                        } else {
                            if (readExternalFile(getBaseUrl(currentUri))) {
                                getUnresolvedUris(false).add(currentUri);
                            }
                        }
                    } else { // policy has not been yet passed by
                        if (null != prefetchedRecord.unresolvedURIs) {
                            getUnresolvedUris(false).addAll(prefetchedRecord.unresolvedURIs);
                        } // end-if null != prefetchedRecord.unresolvedURIs
                        addNewPolicyNeeded(currentUri, prefetchedRecord.policyModel);
                    }
                } // end-if policy already processed
            } // end-foreach unresolved uris
        }
        final PolicySourceModelContext modelContext = PolicySourceModelContext.createContext();
        for (String policyUri : urisNeeded) {
            final PolicySourceModel sourceModel = modelsNeeded.get(policyUri);
            try {
                sourceModel.expand(modelContext);
                modelContext.addModel(new URI(policyUri), sourceModel);
            } catch (URISyntaxException e) {
                LOGGER.logSevereException(e);
            } catch (PolicyException e) {
                LOGGER.logSevereException(e);
            }
        }

        // Start-preparation of policy map builder
        // iterating over all services and binding all the policies read before
        try {
            // messageSet holds the handlers for all wsdl:message elements. There
            // may otherwise be multiple entries for policies that are contained
            // by fault messages.
            HashSet<BuilderHandlerMessageScope> messageSet = new HashSet<BuilderHandlerMessageScope>();
            for (EditableWSDLService service : context.getWSDLModel().getServices().values()) {
                if (getHandlers4ServiceMap().containsKey(service)) {
                    getPolicyMapBuilder().registerHandler(new BuilderHandlerServiceScope(
                            getPolicyURIs(getHandlers4ServiceMap().get(service),modelContext)
                            ,getPolicyModels()
                            ,service
                            ,service.getName()));
                }
                // end service scope

                for (EditableWSDLPort port : service.getPorts()) {
                    if (getHandlers4PortMap().containsKey(port)) {
                        getPolicyMapBuilder().registerHandler(
                                new BuilderHandlerEndpointScope(
                                getPolicyURIs(getHandlers4PortMap().get(port),modelContext)
                                ,getPolicyModels()
                                ,port
                                ,port.getOwner().getName()
                                ,port.getName()));
                    }
                    if ( // port.getBinding may not be null, but in case ...
                            null != port.getBinding()) {
                        if ( // handler for binding
                                getHandlers4BindingMap().containsKey(port.getBinding())) {
                            getPolicyMapBuilder()
                            .registerHandler(
                                    new BuilderHandlerEndpointScope(
                                    getPolicyURIs(getHandlers4BindingMap().get(port.getBinding()),modelContext)
                                    ,getPolicyModels()
                                    ,port.getBinding()
                                    ,service.getName()
                                    ,port.getName()));
                        } // endif handler for binding
                        if ( // handler for port type
                                getHandlers4PortTypeMap().containsKey(port.getBinding().getPortType())) {
                            getPolicyMapBuilder()
                            .registerHandler(
                                    new BuilderHandlerEndpointScope(
                                    getPolicyURIs(getHandlers4PortTypeMap().get(port.getBinding().getPortType()),modelContext)
                                    ,getPolicyModels()
                                    ,port.getBinding().getPortType()
                                    ,service.getName()
                                    ,port.getName()));
                        } // endif handler for port type
                        // end endpoint scope

                        for (EditableWSDLBoundOperation boundOperation : port.getBinding().getBindingOperations()) {

                            final EditableWSDLOperation operation = boundOperation.getOperation();
                            final QName operationName = new QName(boundOperation.getBoundPortType().getName().getNamespaceURI(), boundOperation.getName().getLocalPart());
                            // We store the message and portType/operation under the same namespace as the binding/operation so that we can match them up later
                            if ( // handler for operation scope -- by boundOperation
                                    getHandlers4BoundOperationMap().containsKey(boundOperation)) {
                                getPolicyMapBuilder()
                                .registerHandler(
                                        new BuilderHandlerOperationScope(
                                        getPolicyURIs(getHandlers4BoundOperationMap().get(boundOperation),modelContext)
                                        ,getPolicyModels()
                                        ,boundOperation
                                        ,service.getName()
                                        ,port.getName()
                                        ,operationName));
                            } // endif handler for binding:operation scope
                            if ( // handler for operation scope -- by operation map
                                    getHandlers4OperationMap().containsKey(operation)) {
                                getPolicyMapBuilder()
                                .registerHandler(
                                        new BuilderHandlerOperationScope(
                                        getPolicyURIs(getHandlers4OperationMap().get(operation),modelContext)
                                        ,getPolicyModels()
                                        ,operation
                                        ,service.getName()
                                        ,port.getName()
                                        ,operationName));
                            } // endif for portType:operation scope
                            // end operation scope

                            final EditableWSDLInput input = operation.getInput();
                            if (null!=input) {
                                EditableWSDLMessage inputMsg = input.getMessage();
                                if (inputMsg != null && getHandlers4MessageMap().containsKey(inputMsg)) {
                                    messageSet.add(new BuilderHandlerMessageScope(
                                        getPolicyURIs(
                                            getHandlers4MessageMap().get(inputMsg), modelContext)
                                            ,getPolicyModels()
                                            ,inputMsg
                                            ,BuilderHandlerMessageScope.Scope.InputMessageScope
                                            ,service.getName()
                                            ,port.getName()
                                            ,operationName
                                            ,null)
                                    );
                                }
                            }
                            if ( // binding op input msg
                                    getHandlers4BindingInputOpMap().containsKey(boundOperation)) {
                                getPolicyMapBuilder()
                                .registerHandler(
                                        new BuilderHandlerMessageScope(
                                        getPolicyURIs(getHandlers4BindingInputOpMap().get(boundOperation),modelContext)
                                        ,getPolicyModels()
                                        ,boundOperation
                                        ,BuilderHandlerMessageScope.Scope.InputMessageScope
                                        ,service.getName()
                                        ,port.getName()
                                        ,operationName
                                        ,null));
                            } // endif binding op input msg
                            if ( null != input    // portType op input msg
                                    && getHandlers4InputMap().containsKey(input)) {
                                getPolicyMapBuilder()
                                .registerHandler(
                                        new BuilderHandlerMessageScope(
                                        getPolicyURIs(getHandlers4InputMap().get(input),modelContext)
                                        ,getPolicyModels()
                                        ,input
                                        ,BuilderHandlerMessageScope.Scope.InputMessageScope
                                        ,service.getName()
                                        ,port.getName()
                                        ,operationName
                                        ,null));
                            } // endif portType op input msg
                            // end input message scope

                            final EditableWSDLOutput output = operation.getOutput();
                            if (null!=output) {
                                EditableWSDLMessage outputMsg = output.getMessage();
                                if (outputMsg != null && getHandlers4MessageMap().containsKey(outputMsg)) {
                                    messageSet.add(new BuilderHandlerMessageScope(
                                        getPolicyURIs(
                                            getHandlers4MessageMap().get(outputMsg),modelContext)
                                            ,getPolicyModels()
                                            ,outputMsg
                                            ,BuilderHandlerMessageScope.Scope.OutputMessageScope
                                            ,service.getName()
                                            ,port.getName()
                                            ,operationName
                                            ,null)
                                    );
                                }
                            }
                            if ( // binding op output msg
                                    getHandlers4BindingOutputOpMap().containsKey(boundOperation)) {
                                getPolicyMapBuilder()
                                .registerHandler(
                                        new BuilderHandlerMessageScope(
                                        getPolicyURIs(getHandlers4BindingOutputOpMap().get(boundOperation),modelContext)
                                        ,getPolicyModels()
                                        ,boundOperation
                                        ,BuilderHandlerMessageScope.Scope.OutputMessageScope
                                        ,service.getName()
                                        ,port.getName()
                                        ,operationName
                                        ,null));
                            } // endif binding op output msg
                            if ( null != output // portType op output msg
                                    && getHandlers4OutputMap().containsKey(output)) {
                                getPolicyMapBuilder()
                                .registerHandler(
                                        new BuilderHandlerMessageScope(
                                        getPolicyURIs(getHandlers4OutputMap().get(output),modelContext)
                                        ,getPolicyModels()
                                        ,output
                                        ,BuilderHandlerMessageScope.Scope.OutputMessageScope
                                        ,service.getName()
                                        ,port.getName()
                                        ,operationName
                                        ,null));
                            } // endif portType op output msg
                            // end output message scope

                            for (EditableWSDLBoundFault boundFault : boundOperation.getFaults()) {
                                final EditableWSDLFault fault = boundFault.getFault();

                                // this shouldn't happen ususally,
                                // but since this scenario tested in lagacy tests, dont' fail here
                                if (fault == null) {
                                    LOGGER.warning(PolicyMessages.WSP_1021_FAULT_NOT_BOUND(boundFault.getName()));
                                    continue;
                                }

                                final EditableWSDLMessage faultMessage = fault.getMessage();
                                final QName faultName = new QName(boundOperation.getBoundPortType().getName().getNamespaceURI(), boundFault.getName());
                                // We store the message and portType/fault under the same namespace as the binding/fault so that we can match them up later
                                if (faultMessage != null && getHandlers4MessageMap().containsKey(faultMessage)) {
                                    messageSet.add(
                                        new BuilderHandlerMessageScope(
                                            getPolicyURIs(getHandlers4MessageMap().get(faultMessage), modelContext)
                                            ,getPolicyModels()
                                            ,new WSDLBoundFaultContainer(boundFault, boundOperation)
                                            ,BuilderHandlerMessageScope.Scope.FaultMessageScope
                                            ,service.getName()
                                            ,port.getName()
                                            ,operationName
                                            ,faultName)
                                        );
                                }
                                if (getHandlers4FaultMap().containsKey(fault)) {
                                    messageSet.add(
                                        new BuilderHandlerMessageScope(
                                            getPolicyURIs(getHandlers4FaultMap().get(fault), modelContext)
                                            ,getPolicyModels()
                                            ,new WSDLBoundFaultContainer(boundFault, boundOperation)
                                            ,BuilderHandlerMessageScope.Scope.FaultMessageScope
                                            ,service.getName()
                                            ,port.getName()
                                            ,operationName
                                            ,faultName)
                                        );
                                }
                                if (getHandlers4BindingFaultOpMap().containsKey(boundFault)) {
                                    messageSet.add(
                                        new BuilderHandlerMessageScope(
                                            getPolicyURIs(getHandlers4BindingFaultOpMap().get(boundFault), modelContext)
                                            ,getPolicyModels()
                                            ,new WSDLBoundFaultContainer(boundFault, boundOperation)
                                            ,BuilderHandlerMessageScope.Scope.FaultMessageScope
                                            ,service.getName()
                                            ,port.getName()
                                            ,operationName
                                            ,faultName)
                                        );
                                }
                            } // end foreach binding operation fault msg
                            // end fault message scope

                        } // end foreach boundOperation in port
                    } // endif port.getBinding() != null
                } // end foreach port in service
            } // end foreach service in wsdl
            // Add handlers for wsdl:message elements
            for (BuilderHandlerMessageScope scopeHandler : messageSet) {
                getPolicyMapBuilder().registerHandler(scopeHandler);
            }
        } catch(PolicyException e) {
            LOGGER.logSevereException(e);
        }
        // End-preparation of policy map builder

        LOGGER.exiting();
    }


    // time to read possible config file and do alternative selection (on client side)
    @Override
    public void postFinished(final WSDLParserExtensionContext context) {
        // finally register the PolicyMap on the WSDLModel
        EditableWSDLModel wsdlModel = context.getWSDLModel();
        PolicyMap effectiveMap;
        try {
            if(context.isClientSide())
                effectiveMap = context.getPolicyResolver().resolve(new PolicyResolver.ClientContext(policyBuilder.getPolicyMap(),context.getContainer()));
            else
                effectiveMap = context.getPolicyResolver().resolve(new PolicyResolver.ServerContext(policyBuilder.getPolicyMap(), context.getContainer(),null));
            wsdlModel.setPolicyMap(effectiveMap);
        } catch (PolicyException e) {
            LOGGER.logSevereException(e);
            throw LOGGER.logSevereException(new WebServiceException(PolicyMessages.WSP_1007_POLICY_EXCEPTION_WHILE_FINISHING_PARSING_WSDL(), e));
        }
        try {
            PolicyUtil.configureModel(wsdlModel,effectiveMap);
        } catch (PolicyException e) {
            LOGGER.logSevereException(e);
            throw LOGGER.logSevereException(new WebServiceException(PolicyMessages.WSP_1012_FAILED_CONFIGURE_WSDL_MODEL(), e));
        }
        LOGGER.exiting();
    }


    /**
     * Reads policy reference URIs from PolicyURIs attribute and returns them
     * as a String array returns null if there is no such attribute. This method
     * will attempt to check for the attribute in every supported policy namespace.
     * Resulting array of URIs is concatenation of URIs defined in all found
     * PolicyURIs attribute version.
     */
    private String[] getPolicyURIsFromAttr(final XMLStreamReader reader) {
        final StringBuilder policyUriBuffer = new StringBuilder();
        for (NamespaceVersion version : NamespaceVersion.values()) {
            final String value = reader.getAttributeValue(version.toString(), XmlToken.PolicyUris.toString());
            if (value != null) {
                policyUriBuffer.append(value).append(" ");
            }
        }
        return (policyUriBuffer.length() > 0) ? policyUriBuffer.toString().split("[\\n ]+") : null;
    }

}
