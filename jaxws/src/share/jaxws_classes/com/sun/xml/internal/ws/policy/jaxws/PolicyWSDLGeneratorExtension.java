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

package com.sun.xml.internal.ws.policy.jaxws;

import com.sun.xml.internal.txw2.TypedXmlWriter;
import com.sun.xml.internal.ws.addressing.policy.AddressingPolicyMapConfigurator;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.policy.PolicyResolverFactory;
import com.sun.xml.internal.ws.api.policy.PolicyResolver;
import com.sun.xml.internal.ws.api.model.CheckedException;
import com.sun.xml.internal.ws.api.model.JavaMethod;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundFault;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundPortType;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLFault;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLInput;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLMessage;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLOutput;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPortType;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLService;
import com.sun.xml.internal.ws.api.policy.ModelGenerator;
import com.sun.xml.internal.ws.api.wsdl.writer.WSDLGeneratorExtension;
import com.sun.xml.internal.ws.api.wsdl.writer.WSDLGenExtnContext;
import com.sun.xml.internal.ws.encoding.policy.MtomPolicyMapConfigurator;
import com.sun.xml.internal.ws.policy.Policy;
import com.sun.xml.internal.ws.policy.PolicyConstants;
import com.sun.xml.internal.ws.policy.PolicyException;
import com.sun.xml.internal.ws.policy.PolicyMap;
import com.sun.xml.internal.ws.policy.PolicyMapExtender;
import com.sun.xml.internal.ws.policy.PolicyMapUtil;
import com.sun.xml.internal.ws.policy.PolicyMerger;
import com.sun.xml.internal.ws.policy.PolicySubject;
import com.sun.xml.internal.ws.policy.jaxws.spi.PolicyMapConfigurator;
import com.sun.xml.internal.ws.policy.privateutil.PolicyLogger;
import com.sun.xml.internal.ws.policy.sourcemodel.PolicyModelGenerator;
import com.sun.xml.internal.ws.policy.sourcemodel.PolicyModelMarshaller;
import com.sun.xml.internal.ws.policy.sourcemodel.PolicySourceModel;
import com.sun.xml.internal.ws.policy.sourcemodel.wspolicy.XmlToken;
import com.sun.xml.internal.ws.policy.sourcemodel.wspolicy.NamespaceVersion;
import com.sun.xml.internal.ws.policy.subject.WsdlBindingSubject;
import com.sun.xml.internal.ws.resources.PolicyMessages;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceException;

/**
 * Marshals the contents of a policy map to WSDL.
 *
 * @author Jakub Podlesak (jakub.podlesak at sun.com)
 * @author Fabian Ritzmann
 */
public class PolicyWSDLGeneratorExtension extends WSDLGeneratorExtension {

    static enum ScopeType {

        SERVICE,
        ENDPOINT,
        OPERATION,
        INPUT_MESSAGE,
        OUTPUT_MESSAGE,
        FAULT_MESSAGE
    }
    private final static PolicyLogger LOGGER = PolicyLogger.getLogger(PolicyWSDLGeneratorExtension.class);
    private PolicyMap policyMap;
    private SEIModel seiModel;
    private final Collection<PolicySubject> subjects = new LinkedList<PolicySubject>();
    private final PolicyModelMarshaller marshaller = PolicyModelMarshaller.getXmlMarshaller(true);
    private final PolicyMerger merger = PolicyMerger.getMerger();

    @Override
    public void start(final WSDLGenExtnContext context) {
        LOGGER.entering();
        try {
            this.seiModel = context.getModel();

            final PolicyMapConfigurator[] policyMapConfigurators = loadConfigurators();
            final PolicyMapExtender[] extenders = new PolicyMapExtender[policyMapConfigurators.length];
            for (int i = 0; i < policyMapConfigurators.length; i++) {
                extenders[i] = PolicyMapExtender.createPolicyMapExtender();
            }
            // Read policy config file
            policyMap = PolicyResolverFactory.create().resolve(
                    new PolicyResolver.ServerContext(policyMap, context.getContainer(), context.getEndpointClass(), false, extenders));

            if (policyMap == null) {
                LOGGER.fine(PolicyMessages.WSP_1019_CREATE_EMPTY_POLICY_MAP());
                policyMap = PolicyMap.createPolicyMap(Arrays.asList(extenders));
            }

            final WSBinding binding = context.getBinding();
            try {
                final Collection<PolicySubject> policySubjects = new LinkedList<PolicySubject>();
                for (int i = 0; i < policyMapConfigurators.length; i++) {
                    policySubjects.addAll(policyMapConfigurators[i].update(policyMap, seiModel, binding));
                    extenders[i].disconnect();
                }
                PolicyMapUtil.insertPolicies(policyMap, policySubjects, this.seiModel.getServiceQName(), this.seiModel.getPortName());
            } catch (PolicyException e) {
                throw LOGGER.logSevereException(new WebServiceException(PolicyMessages.WSP_1017_MAP_UPDATE_FAILED(), e));
            }
            final TypedXmlWriter root = context.getRoot();
            root._namespace(NamespaceVersion.v1_2.toString(), NamespaceVersion.v1_2.getDefaultNamespacePrefix());
            root._namespace(NamespaceVersion.v1_5.toString(), NamespaceVersion.v1_5.getDefaultNamespacePrefix());
            root._namespace(PolicyConstants.WSU_NAMESPACE_URI, PolicyConstants.WSU_NAMESPACE_PREFIX);

        } finally {
            LOGGER.exiting();
        }
    }

    @Override
    public void addDefinitionsExtension(final TypedXmlWriter definitions) {
        try {
            LOGGER.entering();
            if (policyMap == null) {
                LOGGER.fine(PolicyMessages.WSP_1009_NOT_MARSHALLING_ANY_POLICIES_POLICY_MAP_IS_NULL());
            } else {
                subjects.addAll(policyMap.getPolicySubjects());
                final PolicyModelGenerator generator = ModelGenerator.getGenerator();
                Set<String> policyIDsOrNamesWritten = new HashSet<String>();
                for (PolicySubject subject : subjects) {
                    if (subject.getSubject() == null) {
                        LOGGER.fine(PolicyMessages.WSP_1008_NOT_MARSHALLING_WSDL_SUBJ_NULL(subject));
                    } else {
                        final Policy policy;
                        try {
                            policy = subject.getEffectivePolicy(merger);
                        } catch (PolicyException e) {
                            throw LOGGER.logSevereException(new WebServiceException(PolicyMessages.WSP_1011_FAILED_TO_RETRIEVE_EFFECTIVE_POLICY_FOR_SUBJECT(subject.toString()), e));
                        }
                        if ((null == policy.getIdOrName()) || (policyIDsOrNamesWritten.contains(policy.getIdOrName()))) {
                            LOGGER.fine(PolicyMessages.WSP_1016_POLICY_ID_NULL_OR_DUPLICATE(policy));
                        } else {
                            try {
                                final PolicySourceModel policyInfoset = generator.translate(policy);
                                marshaller.marshal(policyInfoset, definitions);
                            } catch (PolicyException e) {
                                throw LOGGER.logSevereException(new WebServiceException(PolicyMessages.WSP_1018_FAILED_TO_MARSHALL_POLICY(policy.getIdOrName()), e));
                            }
                            policyIDsOrNamesWritten.add(policy.getIdOrName());
                        }
                    }
                }
            }
        } finally {
            LOGGER.exiting();
        }
    }

    @Override
    public void addServiceExtension(final TypedXmlWriter service) {
        LOGGER.entering();
        final String serviceName = (null == seiModel) ? null : seiModel.getServiceQName().getLocalPart();
        selectAndProcessSubject(service, WSDLService.class, ScopeType.SERVICE, serviceName);
        LOGGER.exiting();
    }

    @Override
    public void addPortExtension(final TypedXmlWriter port) {
        LOGGER.entering();
        final String portName = (null == seiModel) ? null : seiModel.getPortName().getLocalPart();
        selectAndProcessSubject(port, WSDLPort.class, ScopeType.ENDPOINT, portName);
        LOGGER.exiting();
    }

    @Override
    public void addPortTypeExtension(final TypedXmlWriter portType) {
        LOGGER.entering();
        final String portTypeName = (null == seiModel) ? null : seiModel.getPortTypeName().getLocalPart();
        selectAndProcessSubject(portType, WSDLPortType.class, ScopeType.ENDPOINT, portTypeName);
        LOGGER.exiting();
    }

    @Override
    public void addBindingExtension(final TypedXmlWriter binding) {
        LOGGER.entering();
        final QName bindingName = (null == seiModel) ? null : seiModel.getBoundPortTypeName();
        selectAndProcessBindingSubject(binding, WSDLBoundPortType.class, ScopeType.ENDPOINT, bindingName);
        LOGGER.exiting();
    }

    @Override
    public void addOperationExtension(final TypedXmlWriter operation, final JavaMethod method) {
        LOGGER.entering();
        selectAndProcessSubject(operation, WSDLOperation.class, ScopeType.OPERATION, (String)null);
        LOGGER.exiting();
    }

    @Override
    public void addBindingOperationExtension(final TypedXmlWriter operation, final JavaMethod method) {
        LOGGER.entering();
        final QName operationName = (method == null) ? null : new QName(method.getOwner().getTargetNamespace(), method.getOperationName());
        selectAndProcessBindingSubject(operation, WSDLBoundOperation.class, ScopeType.OPERATION, operationName);
        LOGGER.exiting();
    }

    @Override
    public void addInputMessageExtension(final TypedXmlWriter message, final JavaMethod method) {
        LOGGER.entering();
        final String messageName = (null == method) ? null : method.getRequestMessageName();
        selectAndProcessSubject(message, WSDLMessage.class, ScopeType.INPUT_MESSAGE, messageName);
        LOGGER.exiting();
    }

    @Override
    public void addOutputMessageExtension(final TypedXmlWriter message, final JavaMethod method) {
        LOGGER.entering();
        final String messageName = (null == method) ? null : method.getResponseMessageName();
        selectAndProcessSubject(message, WSDLMessage.class, ScopeType.OUTPUT_MESSAGE, messageName);
        LOGGER.exiting();
    }

    @Override
    public void addFaultMessageExtension(final TypedXmlWriter message, final JavaMethod method, final CheckedException exception) {
        LOGGER.entering();
        final String messageName = (null == exception) ? null : exception.getMessageName();
        selectAndProcessSubject(message, WSDLMessage.class, ScopeType.FAULT_MESSAGE, messageName);
        LOGGER.exiting();
    }

    @Override
    public void addOperationInputExtension(final TypedXmlWriter input, final JavaMethod method) {
        LOGGER.entering();
        final String messageName = (null == method) ? null : method.getRequestMessageName();
        selectAndProcessSubject(input, WSDLInput.class, ScopeType.INPUT_MESSAGE, messageName);
        LOGGER.exiting();
    }

    @Override
    public void addOperationOutputExtension(final TypedXmlWriter output, final JavaMethod method) {
        LOGGER.entering();
        final String messageName = (null == method) ? null : method.getResponseMessageName();
        selectAndProcessSubject(output, WSDLOutput.class, ScopeType.OUTPUT_MESSAGE, messageName);
        LOGGER.exiting();
    }

    @Override
    public void addOperationFaultExtension(final TypedXmlWriter fault, final JavaMethod method, final CheckedException exception) {
        LOGGER.entering();
        final String messageName = (null == exception) ? null : exception.getMessageName();
        selectAndProcessSubject(fault, WSDLFault.class, ScopeType.FAULT_MESSAGE, messageName);
        LOGGER.exiting();
    }

    @Override
    public void addBindingOperationInputExtension(final TypedXmlWriter input, final JavaMethod method) {
        LOGGER.entering();
        final QName operationName = new QName(method.getOwner().getTargetNamespace(), method.getOperationName());
        selectAndProcessBindingSubject(input, WSDLBoundOperation.class, ScopeType.INPUT_MESSAGE, operationName);
        LOGGER.exiting();
    }

    @Override
    public void addBindingOperationOutputExtension(final TypedXmlWriter output, final JavaMethod method) {
        LOGGER.entering();
        final QName operationName = new QName(method.getOwner().getTargetNamespace(), method.getOperationName());
        selectAndProcessBindingSubject(output, WSDLBoundOperation.class, ScopeType.OUTPUT_MESSAGE, operationName);
        LOGGER.exiting();
    }

    @Override
    public void addBindingOperationFaultExtension(final TypedXmlWriter writer, final JavaMethod method, final CheckedException exception) {
        LOGGER.entering(writer, method, exception);
        if (subjects != null) {
            for (PolicySubject subject : subjects) { // iterate over all subjects in policy map
                if (this.policyMap.isFaultMessageSubject(subject)) {
                    final Object concreteSubject = subject.getSubject();
                    if (concreteSubject != null) {
                        final String exceptionName = exception == null ? null : exception.getMessageName();
                        if (exceptionName == null) { // no name provided to check
                            writePolicyOrReferenceIt(subject, writer);
                        }
                        if (WSDLBoundFaultContainer.class.isInstance(concreteSubject)) { // is it our class?
                            WSDLBoundFaultContainer faultContainer = (WSDLBoundFaultContainer) concreteSubject;
                            WSDLBoundFault fault = faultContainer.getBoundFault();
                            WSDLBoundOperation operation = faultContainer.getBoundOperation();
                            if (exceptionName.equals(fault.getName()) &&
                                    operation.getName().getLocalPart().equals(method.getOperationName())) {
                                writePolicyOrReferenceIt(subject, writer);
                            }
                        }
                        else if (WsdlBindingSubject.class.isInstance(concreteSubject)) {
                            WsdlBindingSubject wsdlSubject = (WsdlBindingSubject) concreteSubject;
                            if ((wsdlSubject.getMessageType() == WsdlBindingSubject.WsdlMessageType.FAULT) &&
                                exception.getOwner().getTargetNamespace().equals(wsdlSubject.getName().getNamespaceURI()) &&
                                exceptionName.equals(wsdlSubject.getName().getLocalPart())) {
                                writePolicyOrReferenceIt(subject, writer);
                            }
                        }
                    }
                }
            }
        }
        LOGGER.exiting();
    }

    /**
     * This method should only be invoked by interface methods that deal with WSDL binding because they
     * may use the QName of the WSDL binding element as PolicySubject instead of a WSDL object.
     *
     * @param xmlWriter A TypedXmlWriter.
     * @param clazz The policy subject.
     * @param scopeType The WSDL scope.
     * @param bindingName The WSDL binding name.
     */
    private void selectAndProcessSubject(final TypedXmlWriter xmlWriter, final Class clazz, final ScopeType scopeType, final QName bindingName) {
        LOGGER.entering(xmlWriter, clazz, scopeType, bindingName);
        if (bindingName == null) {
            selectAndProcessSubject(xmlWriter, clazz, scopeType, (String) null);
        } else {
            if (subjects != null) {
                for (PolicySubject subject : subjects) {
                    if (bindingName.equals(subject.getSubject())) {
                        writePolicyOrReferenceIt(subject, xmlWriter);
                    }
                }
            }
            selectAndProcessSubject(xmlWriter, clazz, scopeType, bindingName.getLocalPart());
        }
        LOGGER.exiting();
    }

    private void selectAndProcessBindingSubject(final TypedXmlWriter xmlWriter, final Class clazz, final ScopeType scopeType, final QName bindingName) {
        LOGGER.entering(xmlWriter, clazz, scopeType, bindingName);
        if ((subjects != null) && (bindingName != null)) {
            for (PolicySubject subject : subjects) {
                if (subject.getSubject() instanceof WsdlBindingSubject) {
                    final WsdlBindingSubject wsdlSubject = (WsdlBindingSubject) subject.getSubject();
                    if (bindingName.equals(wsdlSubject.getName())) {
                        writePolicyOrReferenceIt(subject, xmlWriter);
                    }
                }
            }
        }
        selectAndProcessSubject(xmlWriter, clazz, scopeType, bindingName);
        LOGGER.exiting();
    }

    private void selectAndProcessSubject(final TypedXmlWriter xmlWriter, final Class clazz, final ScopeType scopeType, final String wsdlName) {
        LOGGER.entering(xmlWriter, clazz, scopeType, wsdlName);
        if (subjects != null) {
            for (PolicySubject subject : subjects) { // iterate over all subjects in policy map
                if (isCorrectType(policyMap, subject, scopeType)) {
                    final Object concreteSubject = subject.getSubject();
                    if (concreteSubject != null && clazz.isInstance(concreteSubject)) { // is it our class?
                        if (null == wsdlName) { // no name provided to check
                            writePolicyOrReferenceIt(subject, xmlWriter);
                        } else {
                            try {
                                final Method getNameMethod = clazz.getDeclaredMethod("getName");
                                if (stringEqualsToStringOrQName(wsdlName, getNameMethod.invoke(concreteSubject))) {
                                    writePolicyOrReferenceIt(subject, xmlWriter);
                                }
                            } catch (NoSuchMethodException e) {
                                throw LOGGER.logSevereException(new WebServiceException(PolicyMessages.WSP_1003_UNABLE_TO_CHECK_ELEMENT_NAME(clazz.getName(), wsdlName), e));
                            } catch (IllegalAccessException e) {
                                throw LOGGER.logSevereException(new WebServiceException(PolicyMessages.WSP_1003_UNABLE_TO_CHECK_ELEMENT_NAME(clazz.getName(), wsdlName), e));
                            } catch (InvocationTargetException e) {
                                throw LOGGER.logSevereException(new WebServiceException(PolicyMessages.WSP_1003_UNABLE_TO_CHECK_ELEMENT_NAME(clazz.getName(), wsdlName), e));
                            }
                        }
                    }
                }
            }
        }
        LOGGER.exiting();
    }

    private static boolean isCorrectType(final PolicyMap map, final PolicySubject subject, final ScopeType type) {
        switch (type) {
            case OPERATION:
                return !(map.isInputMessageSubject(subject) || map.isOutputMessageSubject(subject) || map.isFaultMessageSubject(subject));
            case INPUT_MESSAGE:
                return map.isInputMessageSubject(subject);
            case OUTPUT_MESSAGE:
                return map.isOutputMessageSubject(subject);
            case FAULT_MESSAGE:
                return map.isFaultMessageSubject(subject);
            default:
                return true;
        }
    }

    private boolean stringEqualsToStringOrQName(final String first, final Object second) {
        return (second instanceof QName) ? first.equals(((QName) second).getLocalPart()) : first.equals(second);
    }

    /**
     * Adds a PolicyReference element that points to the policy of the element,
     * if the policy does not have any id or name. Writes policy inside the element otherwise.
     *
     * @param subject
     *      PolicySubject to be referenced or marshalled
     * @param writer
     *      A TXW on to which we shall add the PolicyReference
     */
    private void writePolicyOrReferenceIt(final PolicySubject subject, final TypedXmlWriter writer) {
        final Policy policy;
        try {
            policy = subject.getEffectivePolicy(merger);
        } catch (PolicyException e) {
            throw LOGGER.logSevereException(new WebServiceException(PolicyMessages.WSP_1011_FAILED_TO_RETRIEVE_EFFECTIVE_POLICY_FOR_SUBJECT(subject.toString()), e));
        }
        if (policy != null) {
            if (null == policy.getIdOrName()) {
                final PolicyModelGenerator generator = ModelGenerator.getGenerator();
                try {
                    final PolicySourceModel policyInfoset = generator.translate(policy);
                    marshaller.marshal(policyInfoset, writer);
                } catch (PolicyException pe) {
                    throw LOGGER.logSevereException(new WebServiceException(PolicyMessages.WSP_1002_UNABLE_TO_MARSHALL_POLICY_OR_POLICY_REFERENCE(), pe));
                }
            } else {
                final TypedXmlWriter policyReference = writer._element(policy.getNamespaceVersion().asQName(XmlToken.PolicyReference), TypedXmlWriter.class);
                policyReference._attribute(XmlToken.Uri.toString(), '#' + policy.getIdOrName());
            }
        }
    }

    private PolicyMapConfigurator[] loadConfigurators() {
        final Collection<PolicyMapConfigurator> configurators = new LinkedList<PolicyMapConfigurator>();

        // Add map configurators that are already built into JAX-WS
        configurators.add(new AddressingPolicyMapConfigurator());
        configurators.add(new MtomPolicyMapConfigurator());

        // Dynamically discover remaining map configurators
        PolicyUtil.addServiceProviders(configurators, PolicyMapConfigurator.class);

        return configurators.toArray(new PolicyMapConfigurator[configurators.size()]);
    }

}
