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

import com.sun.xml.internal.ws.policy.privateutil.LocalizationMessages;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.xml.namespace.QName;

/**
 * A PolicyMap holds all policies for a scope.
 *
 * This map is modeled around WSDL 1.1 policy scopes according to WS-PolicyAttachment. The map holds an information about
 * every scope for service, endpoint, operation, and input/output/fault message. It also provide accessibility methods for
 * computing and obtaining effective policy on each scope.
 *
 * TODO: rename createWsdlMessageScopeKey to createWsdlInputOutputMessageScopeKey
 *
 * @author Fabian Ritzmann
 */
public final class PolicyMap implements Iterable<Policy> {
   private static final PolicyLogger LOGGER = PolicyLogger.getLogger(PolicyMap.class);

    private static final PolicyMapKeyHandler serviceKeyHandler = new PolicyMapKeyHandler() {
        public boolean areEqual(final PolicyMapKey key1, final PolicyMapKey key2) {
            return key1.getService().equals(key2.getService());
        }

        public int generateHashCode(final PolicyMapKey key) {
            int result = 17;

            result = 37 * result + key.getService().hashCode();

            return result;
        }
    };

    private static final PolicyMapKeyHandler endpointKeyHandler = new PolicyMapKeyHandler() {
        public boolean areEqual(final PolicyMapKey key1, final PolicyMapKey key2) {
            boolean retVal = true;

            retVal = retVal && key1.getService().equals(key2.getService());
            retVal = retVal && ((key1.getPort() == null) ? key2.getPort() == null : key1.getPort().equals(key2.getPort()));

            return retVal;
        }

        public int generateHashCode(final PolicyMapKey key) {
            int result = 17;

            result = 37 * result + key.getService().hashCode();
            result = 37 * result + ((key.getPort() == null) ? 0 : key.getPort().hashCode());

            return result;
        }
    };

    private static final PolicyMapKeyHandler operationAndInputOutputMessageKeyHandler = new PolicyMapKeyHandler() {
        // we use the same algorithm to handle operation and input/output message keys

        public boolean areEqual(final PolicyMapKey key1, final PolicyMapKey key2) {
            boolean retVal = true;

            retVal = retVal && key1.getService().equals(key2.getService());
            retVal = retVal && ((key1.getPort() == null) ? key2.getPort() == null : key1.getPort().equals(key2.getPort()));
            retVal = retVal && ((key1.getOperation() == null) ? key2.getOperation() == null : key1.getOperation().equals(key2.getOperation()));

            return retVal;
        }

        public int generateHashCode(final PolicyMapKey key) {
            int result = 17;

            result = 37 * result + key.getService().hashCode();
            result = 37 * result + ((key.getPort() == null) ? 0 : key.getPort().hashCode());
            result = 37 * result + ((key.getOperation() == null) ? 0 : key.getOperation().hashCode());

            return result;
        }
    };

    private static final PolicyMapKeyHandler faultMessageHandler = new PolicyMapKeyHandler() {
        public boolean areEqual(final PolicyMapKey key1, final PolicyMapKey key2) {
            boolean retVal = true;

            retVal = retVal && key1.getService().equals(key2.getService());
            retVal = retVal && ((key1.getPort() == null) ? key2.getPort() == null : key1.getPort().equals(key2.getPort()));
            retVal = retVal && ((key1.getOperation() == null) ? key2.getOperation() == null : key1.getOperation().equals(key2.getOperation()));
            retVal = retVal && ((key1.getFaultMessage() == null) ? key2.getFaultMessage() == null : key1.getFaultMessage().equals(key2.getFaultMessage()));

            return retVal;
        }

        public int generateHashCode(final PolicyMapKey key) {
            int result = 17;

            result = 37 * result + key.getService().hashCode();
            result = 37 * result + ((key.getPort() == null) ? 0 : key.getPort().hashCode());
            result = 37 * result + ((key.getOperation() == null) ? 0 : key.getOperation().hashCode());
            result = 37 * result + ((key.getFaultMessage() == null) ? 0 : key.getFaultMessage().hashCode());

            return result;
        }
    };


    static enum ScopeType {
        SERVICE,
        ENDPOINT,
        OPERATION,
        INPUT_MESSAGE,
        OUTPUT_MESSAGE,
        FAULT_MESSAGE
    }

    private static final class ScopeMap implements Iterable<Policy> {
        private final Map<PolicyMapKey, PolicyScope> internalMap = new HashMap<PolicyMapKey, PolicyScope>();
        private final PolicyMapKeyHandler scopeKeyHandler;
        private final PolicyMerger merger;

        ScopeMap(final PolicyMerger merger, final PolicyMapKeyHandler scopeKeyHandler) {
            this.merger = merger;
            this.scopeKeyHandler = scopeKeyHandler;
        }

        Policy getEffectivePolicy(final PolicyMapKey key) throws PolicyException {
            final PolicyScope scope = internalMap.get(createLocalCopy(key));
            return (scope == null) ? null : scope.getEffectivePolicy(merger);
        }

        void putSubject(final PolicyMapKey key, final PolicySubject subject) {
            final PolicyMapKey localKey = createLocalCopy(key);
            final PolicyScope scope = internalMap.get(localKey);
            if (scope == null) {
                final List<PolicySubject> list = new LinkedList<PolicySubject>();
                list.add(subject);
                internalMap.put(localKey, new PolicyScope(list));
            } else {
                scope.attach(subject);
            }
        }

        void setNewEffectivePolicy(final PolicyMapKey key, final Policy newEffectivePolicy) {
            // we add this policy map as a subject, because there is nothing reasonable we could add there, since
            // this is an artificial policy subject
            final PolicySubject subject = new PolicySubject(key, newEffectivePolicy);

            final PolicyMapKey localKey = createLocalCopy(key);
            final PolicyScope scope = internalMap.get(localKey);
            if (scope == null) {
                final List<PolicySubject> list = new LinkedList<PolicySubject>();
                list.add(subject);
                internalMap.put(localKey, new PolicyScope(list));
            } else {
                scope.dettachAllSubjects();
                scope.attach(subject);
            }
        }

        Collection<PolicyScope> getStoredScopes() {
            return internalMap.values();
        }

        Set<PolicyMapKey> getAllKeys() {
            return internalMap.keySet();
        }

        private PolicyMapKey createLocalCopy(final PolicyMapKey key) {
            if (key == null) {
                throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0045_POLICY_MAP_KEY_MUST_NOT_BE_NULL()));
            }

            final PolicyMapKey localKeyCopy = new PolicyMapKey(key);
            localKeyCopy.setHandler(scopeKeyHandler);

            return localKeyCopy;
        }

        public Iterator<Policy> iterator() {
            return new Iterator<Policy> () {
                private final Iterator<PolicyMapKey> keysIterator = internalMap.keySet().iterator();

                public boolean hasNext() {
                    return keysIterator.hasNext();
                }

                public Policy next() {
                    final PolicyMapKey key = keysIterator.next();
                    try {
                        return getEffectivePolicy(key);
                    } catch (PolicyException e) {
                        throw LOGGER.logSevereException(new IllegalStateException(LocalizationMessages.WSP_0069_EXCEPTION_WHILE_RETRIEVING_EFFECTIVE_POLICY_FOR_KEY(key), e));
                    }
                }

                public void remove() {
                    throw LOGGER.logSevereException(new UnsupportedOperationException(LocalizationMessages.WSP_0034_REMOVE_OPERATION_NOT_SUPPORTED()));
                }
            };
        }

        public boolean isEmpty() {
            return internalMap.isEmpty();
        }

        @Override
        public String toString() {
            return internalMap.toString();
        }
    }

    private static final PolicyMerger merger = PolicyMerger.getMerger();

    private final ScopeMap serviceMap = new ScopeMap(merger, serviceKeyHandler);
    private final ScopeMap endpointMap = new ScopeMap(merger, endpointKeyHandler);
    private final ScopeMap operationMap = new ScopeMap(merger, operationAndInputOutputMessageKeyHandler);
    private final ScopeMap inputMessageMap = new ScopeMap(merger, operationAndInputOutputMessageKeyHandler);
    private final ScopeMap outputMessageMap = new ScopeMap(merger, operationAndInputOutputMessageKeyHandler);
    private final ScopeMap faultMessageMap = new ScopeMap(merger, faultMessageHandler);

    /**
     * This constructor is private to prevent direct instantiation from outside of the class
     */
    private PolicyMap() {
        // nothing to initialize
    }

    /**
     * Creates new policy map instance and connects provided collection of policy map mutators to the created policy map.
     *
     * @param mutators collection of mutators that should be connected to the newly created map.
     * @return new policy map instance (mutable via provided collection of mutators).
     */
    public static PolicyMap createPolicyMap(final Collection<? extends PolicyMapMutator> mutators) {
        final PolicyMap result = new PolicyMap();

        if (mutators != null && !mutators.isEmpty()) {
            for (PolicyMapMutator mutator : mutators) {
                mutator.connect(result);
            }
        }

        return result;
    }

    public Policy getServiceEffectivePolicy(final PolicyMapKey key) throws PolicyException {
        return serviceMap.getEffectivePolicy(key);
    }

    public Policy getEndpointEffectivePolicy(final PolicyMapKey key) throws PolicyException {
        return endpointMap.getEffectivePolicy(key);
    }

    public Policy getOperationEffectivePolicy(final PolicyMapKey key) throws PolicyException {
        return operationMap.getEffectivePolicy(key);
    }

    public Policy getInputMessageEffectivePolicy(final PolicyMapKey key) throws PolicyException {
        return inputMessageMap.getEffectivePolicy(key);
    }

    public Policy getOutputMessageEffectivePolicy(final PolicyMapKey key) throws PolicyException {
        return outputMessageMap.getEffectivePolicy(key);
    }

    public Policy getFaultMessageEffectivePolicy(final PolicyMapKey key) throws PolicyException {
        return faultMessageMap.getEffectivePolicy(key);
    }

    /**
     * Returns all service scope keys stored in this policy map
     *
     * @return collection of service scope policy map keys stored in the map.
     */
    public Collection<PolicyMapKey> getAllServiceScopeKeys() {
        return serviceMap.getAllKeys();
    }

    /**
     * Returns all endpoint scope keys stored in this policy map
     *
     * @return collection of endpoint scope policy map keys stored in the map.
     */
    public Collection<PolicyMapKey> getAllEndpointScopeKeys() {
        return endpointMap.getAllKeys();
    }

    /**
     * Returns all operation scope keys stored in this policy map
     *
     * @return collection of operation scope policy map keys stored in the map.
     */
    public Collection<PolicyMapKey> getAllOperationScopeKeys() {
        return operationMap.getAllKeys();
    }

    /**
     * Returns all input message scope keys stored in this policy map
     *
     * @return collection of input message scope policy map keys stored in the map.
     */
    public Collection<PolicyMapKey> getAllInputMessageScopeKeys() {
        return inputMessageMap.getAllKeys();
    }

    /**
     * Returns all output message scope keys stored in this policy map
     *
     * @return collection of output message scope policy map keys stored in the map.
     */
    public Collection<PolicyMapKey> getAllOutputMessageScopeKeys() {
        return outputMessageMap.getAllKeys();
    }

    /**
     * Returns all fault message scope keys stored in this policy map
     *
     * @return collection of input message scope policy map keys stored in the map.
     */
    public Collection<PolicyMapKey> getAllFaultMessageScopeKeys() {
        return faultMessageMap.getAllKeys();
    }

    /**
     * Places new subject into policy map under the scope identified by it's type and policy map key.
     *
     * @param scopeType the type of the scope the subject belongs to
     * @param key a policy map key to be used to store the subject
     * @param subject actual policy subject to be stored in the policy map
     *
     * @throw IllegalArgumentException in case the scope type is not recognized.
     */
    void putSubject(final ScopeType scopeType, final PolicyMapKey key, final PolicySubject subject) {
        switch (scopeType) {
            case SERVICE:
                serviceMap.putSubject(key, subject);
                break;
            case ENDPOINT:
                endpointMap.putSubject(key, subject);
                break;
            case OPERATION:
                operationMap.putSubject(key, subject);
                break;
            case INPUT_MESSAGE:
                inputMessageMap.putSubject(key, subject);
                break;
            case OUTPUT_MESSAGE:
                outputMessageMap.putSubject(key, subject);
                break;
            case FAULT_MESSAGE:
                faultMessageMap.putSubject(key, subject);
                break;
            default:
                throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0002_UNRECOGNIZED_SCOPE_TYPE(scopeType)));
        }
    }

    /**
     * Replaces current effective policy on given scope (identified by a {@code key} parameter) with the new efective
     * policy provided as a second input parameter. If no policy was defined for the presented key, the new policy is simply
     * stored with the key.
     *
     * @param scopeType the type of the scope the subject belongs to. Must not be {@code null}.
     * @param key identifier of the scope the effective policy should be replaced with the new one. Must not be {@code null}.
     * @param newEffectivePolicy the new policy to replace the old effective policy of the scope. Must not be {@code null}.
     *
     * @throw IllegalArgumentException in case any of the input parameters is {@code null}
     *        or in case the scope type is not recognized.
     */
    void setNewEffectivePolicyForScope(final ScopeType scopeType, final PolicyMapKey key, final Policy newEffectivePolicy) throws IllegalArgumentException {
        if (scopeType == null || key == null || newEffectivePolicy == null) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0062_INPUT_PARAMS_MUST_NOT_BE_NULL()));
        }

        switch (scopeType) {
            case SERVICE :
                serviceMap.setNewEffectivePolicy(key, newEffectivePolicy);
                break;
            case ENDPOINT :
                endpointMap.setNewEffectivePolicy(key, newEffectivePolicy);
                break;
            case OPERATION :
                operationMap.setNewEffectivePolicy(key, newEffectivePolicy);
                break;
            case INPUT_MESSAGE :
                inputMessageMap.setNewEffectivePolicy(key, newEffectivePolicy);
                break;
            case OUTPUT_MESSAGE :
                outputMessageMap.setNewEffectivePolicy(key, newEffectivePolicy);
                break;
            case FAULT_MESSAGE :
                faultMessageMap.setNewEffectivePolicy(key, newEffectivePolicy);
                break;
            default:
                throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0002_UNRECOGNIZED_SCOPE_TYPE(scopeType)));
        }
    }

    /**
     * Returns all policy subjects contained by this map.
     *
     * @return All policy subjects contained by this map
     */
    public Collection<PolicySubject> getPolicySubjects() {
        final List<PolicySubject> subjects = new LinkedList<PolicySubject>();
        addSubjects(subjects, serviceMap);
        addSubjects(subjects, endpointMap);
        addSubjects(subjects, operationMap);
        addSubjects(subjects, inputMessageMap);
        addSubjects(subjects, outputMessageMap);
        addSubjects(subjects, faultMessageMap);
        return subjects;
    }

    /*
     * TODO: reconsider this QUICK HACK
     */
    public boolean isInputMessageSubject(final PolicySubject subject) {
        for (PolicyScope scope : inputMessageMap.getStoredScopes()) {
            if (scope.getPolicySubjects().contains(subject)) {
                return true;
            }
        }
        return false;
    }

    /*
     * TODO: reconsider this QUICK HACK
     */
    public boolean isOutputMessageSubject(final PolicySubject subject) {
        for (PolicyScope scope : outputMessageMap.getStoredScopes()) {
            if (scope.getPolicySubjects().contains(subject)) {
                return true;
            }
        }
        return false;
    }


    /*
     * TODO: reconsider this QUICK HACK
     */
    public boolean isFaultMessageSubject(final PolicySubject subject) {
        for (PolicyScope scope : faultMessageMap.getStoredScopes()) {
            if (scope.getPolicySubjects().contains(subject)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Returns true if this map contains no key - policy pairs
     *
     * A null object key or policy constitutes a non-empty map.
     *
     * @return true if this map contains no key - policy pairs
     */
    public boolean isEmpty() {
        return serviceMap.isEmpty() && endpointMap.isEmpty() &&
                operationMap.isEmpty() && inputMessageMap.isEmpty() &&
                outputMessageMap.isEmpty() && faultMessageMap.isEmpty();
    }


    /**
     * Add all subjects in the given map to the collection
     *
     * @param subjects A collection that should hold subjects. The new subjects are added to the collection. Must not be {@code null}.
     * @param scopeMap A scope map that holds policy scopes. The subjects are retrieved from the scope objects.
     */
    private void addSubjects(final Collection<PolicySubject> subjects, final ScopeMap scopeMap) {
        for (PolicyScope scope : scopeMap.getStoredScopes()) {
            final Collection<PolicySubject> scopedSubjects = scope.getPolicySubjects();
            subjects.addAll(scopedSubjects);
        }
    }

    /**
     * Creates a service policy scope <emph>locator</emph> object, that serves as a access key into
     * a {@code PolicyMap} where actual service policy scope for given service can be retrieved.
     *
     * @param service qualified name of the service. Must not be {@code null}.
     * @throws IllegalArgumentException in case service, port or operation parameter is {@code null}.
     */
    public static PolicyMapKey createWsdlServiceScopeKey(final QName service) throws IllegalArgumentException {
        if (service == null) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0031_SERVICE_PARAM_MUST_NOT_BE_NULL()));
        }
        return new PolicyMapKey(service, null, null, serviceKeyHandler);
    }

    /**
     * Creates an endpoint policy scope <emph>locator</emph> object, that serves as a access key into
     * a {@code PolicyMap} where actual endpoint policy scope for given endpoint can be retrieved.
     *
     * @param service qualified name of the service. Must not be {@code null}.
     * @param port qualified name of the endpoint. Must not be {@code null}.
     * @throws IllegalArgumentException in case service, port or operation parameter is {@code null}.
     */
    public static PolicyMapKey createWsdlEndpointScopeKey(final QName service, final QName port) throws IllegalArgumentException {
        if (service == null || port == null) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0033_SERVICE_AND_PORT_PARAM_MUST_NOT_BE_NULL(service, port)));
        }
        return new PolicyMapKey(service, port, null, endpointKeyHandler);
    }

    /**
     * Creates an operation policy scope <emph>locator</emph> object, that serves as a access key into
     * a {@code PolicyMap} where actual operation policy scope for given bound operation can be retrieved.
     *
     * @param service qualified name of the service. Must not be {@code null}.
     * @param port qualified name of the endpoint. Must not be {@code null}.
     * @param operation qualified name of the operation. Must not be {@code null}.
     * @throws IllegalArgumentException in case service, port or operation parameter is {@code null}.
     */
    public static PolicyMapKey createWsdlOperationScopeKey(final QName service, final QName port, final QName operation) throws IllegalArgumentException {
        return createOperationOrInputOutputMessageKey(service, port, operation);
    }

    /**
     * Creates an input/output message policy scope <emph>locator</emph> object identified by a bound operation, that serves as a
     * access key into {@code PolicyMap} where actual input/output message policy scope for given input message of a bound operation
     * can be retrieved.
     * <p/>
     * The method returns a key that is compliant with <emph>WSDL 1.1 Basic Profile Specification</emph>, according to which there
     * should be no two operations with the same name in a single port type definition.
     *
     * @param service qualified name of the service. Must not be {@code null}.
     * @param port qualified name of the endpoint. Must not be {@code null}.
     * @param operation qualified name of the operation. Must not be {@code null}.
     * @throws IllegalArgumentException in case service, port or operation parameter is {@code null}.
     *
     */
    public static PolicyMapKey createWsdlMessageScopeKey(final QName service, final QName port, final QName operation) throws IllegalArgumentException {
        return createOperationOrInputOutputMessageKey(service, port, operation);
    }

    /**
     * Creates a fault message policy scope <emph>locator</emph> object identified by a bound operation, that serves as a
     * access key into {@code PolicyMap} where the actual fault message policy scope for one of the faults of a bound operation
     * can be retrieved.
     * <p/>
     * The method returns a key that is compliant with the <emph>WSDL 1.1 Basic Profile Specification</emph>, according to which there
     * should be no two operations with the same name in a single port type definition.
     *
     * @param service qualified name of the service. Must not be {@code null}.
     * @param port qualified name of the endpoint. Must not be {@code null}.
     * @param operation qualified name of the operation. Must not be {@code null}.
     * @param fault qualified name of the fault. Do not confuse this with the name of the actual message. This parameter
     * takes the wsdl:binding/wsdl:operation/wsdl:fault name and not the wsdl:message name. Must not be {@code null}.
     * @throws IllegalArgumentException in case service, port or operation parameter is {@code null}.
     *
     */
    public static PolicyMapKey createWsdlFaultMessageScopeKey(final QName service, final QName port, final QName operation, final QName fault) throws IllegalArgumentException {
        if (service == null || port == null || operation == null || fault == null) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0030_SERVICE_PORT_OPERATION_FAULT_MSG_PARAM_MUST_NOT_BE_NULL(service, port, operation, fault)));
        }

        return new PolicyMapKey(service, port, operation, fault, faultMessageHandler);
    }

    private static PolicyMapKey createOperationOrInputOutputMessageKey(final QName service, final QName port, final QName operation) {
        if (service == null || port == null || operation == null) {
            throw LOGGER.logSevereException(new IllegalArgumentException(LocalizationMessages.WSP_0029_SERVICE_PORT_OPERATION_PARAM_MUST_NOT_BE_NULL(service, port, operation)));
        }

        return new PolicyMapKey(service, port, operation, operationAndInputOutputMessageKeyHandler);
    }

    @Override
    public String toString(){
        // TODO
        final StringBuffer result = new StringBuffer();
        if(null!=this.serviceMap) {
            result.append("\nServiceMap=").append(this.serviceMap);
        }
        if(null!=this.endpointMap) {
            result.append("\nEndpointMap=").append(this.endpointMap);
        }
        if(null!=this.operationMap) {
            result.append("\nOperationMap=").append(this.operationMap);
        }
        if(null!=this.inputMessageMap) {
            result.append("\nInputMessageMap=").append(this.inputMessageMap);
        }
        if(null!=this.outputMessageMap) {
            result.append("\nOutputMessageMap=").append(this.outputMessageMap);
        }
        if(null!=this.faultMessageMap) {
            result.append("\nFaultMessageMap=").append(this.faultMessageMap);
        }
        return result.toString();
    }

    public Iterator<Policy> iterator() {
        return new Iterator<Policy> () {
            private final Iterator<Iterator<Policy>> mainIterator;
            private Iterator<Policy> currentScopeIterator;

            { // instance initialization
                final Collection<Iterator<Policy>> scopeIterators = new ArrayList<Iterator<Policy>>(6);
                scopeIterators.add(serviceMap.iterator());
                scopeIterators.add(endpointMap.iterator());
                scopeIterators.add(operationMap.iterator());
                scopeIterators.add(inputMessageMap.iterator());
                scopeIterators.add(outputMessageMap.iterator());
                scopeIterators.add(faultMessageMap.iterator());

                mainIterator = scopeIterators.iterator();
                currentScopeIterator = mainIterator.next();
            }

            public boolean hasNext() {
                while (!currentScopeIterator.hasNext()) {
                    if (mainIterator.hasNext()) {
                        currentScopeIterator = mainIterator.next();
                    } else {
                        return false;
                    }
                }

                return true;
            }

            public Policy next() {
                if (hasNext()) {
                    return currentScopeIterator.next();
                }
                throw LOGGER.logSevereException(new NoSuchElementException(LocalizationMessages.WSP_0054_NO_MORE_ELEMS_IN_POLICY_MAP()));
            }

            public void remove() {
                throw LOGGER.logSevereException(new UnsupportedOperationException(LocalizationMessages.WSP_0034_REMOVE_OPERATION_NOT_SUPPORTED()));
            }
        };
    }

}
