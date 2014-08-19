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

package com.sun.xml.internal.ws.api.addressing;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.ws.addressing.W3CAddressingConstants;
import com.sun.xml.internal.ws.addressing.WsaTubeHelper;
import com.sun.xml.internal.ws.addressing.v200408.MemberSubmissionAddressingConstants;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.developer.MemberSubmissionAddressingFeature;
import com.sun.xml.internal.ws.developer.MemberSubmissionEndpointReference;
import com.sun.xml.internal.ws.message.stream.OutboundStreamHeader;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.soap.AddressingFeature;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;

/**
 * 'Traits' object that absorbs differences of WS-Addressing versions.
 *
 * @author Arun Gupta
 */
public enum AddressingVersion {

    W3C("http://www.w3.org/2005/08/addressing",
        "wsa",
        W3CAddressingConstants.ANONYMOUS_EPR,
        "http://www.w3.org/2006/05/addressing/wsdl",
        "http://www.w3.org/2006/05/addressing/wsdl",
        "http://www.w3.org/2005/08/addressing/anonymous",
        "http://www.w3.org/2005/08/addressing/none",
        new EPR(W3CEndpointReference.class,
                    "Address",
                    "ServiceName",
                    "EndpointName",
                    "InterfaceName",
                    new QName("http://www.w3.org/2005/08/addressing","Metadata","wsa"),
                    "ReferenceParameters",
                    null )) {

        /* package */  String getActionMismatchLocalName() {
            return "ActionMismatch";
        }
        @Override
        public boolean isReferenceParameter(String localName) {
            return localName.equals("ReferenceParameters");
        }

        @Override
        public WsaTubeHelper getWsaHelper(WSDLPort wsdlPort, SEIModel seiModel, WSBinding binding) {
            return new com.sun.xml.internal.ws.addressing.WsaTubeHelperImpl(wsdlPort, seiModel, binding);
        }

        @Override
        /* package */ String getMapRequiredLocalName() {
            return "MessageAddressingHeaderRequired";
        }

        @Override
        public String getMapRequiredText() {
            return "A required header representing a Message Addressing Property is not present";
        }

        /* package */ String getInvalidAddressLocalName() {
            return "InvalidAddress";
        }

        @Override
        /* package */ String getInvalidMapLocalName() {
            return "InvalidAddressingHeader";
        }

        @Override
        public String getInvalidMapText() {
            return "A header representing a Message Addressing Property is not valid and the message cannot be processed";
        }

        @Override
        /* package */ String getInvalidCardinalityLocalName() {
            return "InvalidCardinality";
        }

        /*package*/ Header createReferenceParameterHeader(XMLStreamBuffer mark, String nsUri, String localName) {
            return new OutboundReferenceParameterHeader(mark,nsUri,localName);
        }

        /*package*/ String getIsReferenceParameterLocalName() {
            return "IsReferenceParameter";
        }

        /* package */ String getWsdlAnonymousLocalName() {
            return "Anonymous";
        }

        public String getPrefix() {
            return "wsa";
        }

        public String getWsdlPrefix() {
            return "wsaw";
        }

        public Class<? extends WebServiceFeature> getFeatureClass() {
            return AddressingFeature.class;
        }
    },
    MEMBER("http://schemas.xmlsoap.org/ws/2004/08/addressing",
           "wsa",
           MemberSubmissionAddressingConstants.ANONYMOUS_EPR,
           "http://schemas.xmlsoap.org/ws/2004/08/addressing",
           "http://schemas.xmlsoap.org/ws/2004/08/addressing/policy",
           "http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous",
            "",
           new EPR(MemberSubmissionEndpointReference.class,
                    "Address",
                    "ServiceName",
                    "PortName",
                    "PortType",
                    MemberSubmissionAddressingConstants.MEX_METADATA,
                    "ReferenceParameters",
                    "ReferenceProperties")) {
        /* package */  String getActionMismatchLocalName() {
            return "InvalidMessageInformationHeader";
        }
        @Override
        public boolean isReferenceParameter(String localName) {
            return localName.equals("ReferenceParameters") || localName.equals("ReferenceProperties");
        }

        @Override
        public WsaTubeHelper getWsaHelper(WSDLPort wsdlPort, SEIModel seiModel, WSBinding binding) {
            return new com.sun.xml.internal.ws.addressing.v200408.WsaTubeHelperImpl(wsdlPort, seiModel, binding);
        }

        @Override
        /* package */ String getMapRequiredLocalName() {
            return "MessageInformationHeaderRequired";
        }

        @Override
        public String getMapRequiredText() {
            return "A required message information header, To, MessageID, or Action, is not present.";
        }

        /* package */ String getInvalidAddressLocalName() {
            return getInvalidMapLocalName();
        }

        @Override
        /* package */ String getInvalidMapLocalName() {
            return "InvalidMessageInformationHeader";
        }

        @Override
        public String getInvalidMapText() {
            return "A message information header is not valid and the message cannot be processed.";
        }

        @Override
        /* package */ String getInvalidCardinalityLocalName() {
            return getInvalidMapLocalName();
        }

        /*package*/ Header createReferenceParameterHeader(XMLStreamBuffer mark, String nsUri, String localName) {
            return new OutboundStreamHeader(mark,nsUri,localName);
        }

        /*package*/ String getIsReferenceParameterLocalName() {
            return "";
        }

        /* package */ String getWsdlAnonymousLocalName() {
            return "";
        }

        public String getPrefix() {
            return "wsa";
        }

        public String getWsdlPrefix() {
            return "wsaw";
        }

        public Class<? extends WebServiceFeature> getFeatureClass() {
            return MemberSubmissionAddressingFeature.class;
        }
    };

    /**
     * Namespace URI
     */
    public final String nsUri;

    /**
     * Namespace URI for the WSDL Binding
     */
    public final String wsdlNsUri;

    /**
     * Representing either {@link W3CEndpointReference} or
     * {@link MemberSubmissionEndpointReference}.
     */
    public final EPR eprType;

    /**
     * Namespace URI for the WSDL Binding
     */
    public final String policyNsUri;

    /**
     * Gets the anonymous URI value associated with this WS-Addressing version.
     */
    public final @NotNull String anonymousUri;

    /**
     * Gets the none URI value associated with this WS-Addressing version.
     */
    public final @NotNull String noneUri;

    /**
     * Represents the anonymous EPR.
     */
    public final WSEndpointReference anonymousEpr;

    /**
     * Represents the To QName in the SOAP message for a specific WS-Addressing Version.
     */
    public final QName toTag;

    /**
     * Represents the From QName in the SOAP message for a specific WS-Addressing Version.
     */
    public final QName fromTag;

    /**
     * Represents the ReplyTo QName in the SOAP message for a specific WS-Addressing Version.
     */
    public final QName replyToTag;

    /**
     * Represents the FaultTo QName for a specific WS-Addressing Version.
     */
    public final QName faultToTag;

    /**
     * Represents the Action QName in the SOAP message for a specific WS-Addressing Version.
     */
    public final QName actionTag;

    /**
     * Represents the MessageID QName in the SOAP message for a specific WS-Addressing Version.
     */
    public final QName messageIDTag;

    /**
     * Represents the RelatesTo QName in the SOAP message for a specific WS-Addressing Version.
     */
    public final QName relatesToTag;

    /**
     * Represents the QName of the fault code when a required header representing a
     * WS-Addressing Message Addressing Property is not present.
     */
    public final QName mapRequiredTag;

    /**
     * Represents the QName of the fault code when Action is not supported at this endpoint.
     */
    public final QName actionMismatchTag;

    /**
     * Represents the QName of the fault code when Action is not supported at this endpoint.
     */
    public final QName actionNotSupportedTag;

    /**
     * Represents the text of the fault when Action is not supported at this endpoint.
     */
    public final String actionNotSupportedText;

    /**
     * Represents the QName of the fault code when a header representing a
     * WS-Addressing Message Addressing Property is invalid and cannot be processed.
     */
    public final QName invalidMapTag;

    /**
     * Represents the QName of the fault code when a header representing a
     * WS-Addressing Message Addressing Property occurs greater than expected number.
     */
    public final QName invalidCardinalityTag;

    /**
     * Represents the QName of the fault code when a header representing an
     * address is not valid.
     */
    public final QName invalidAddressTag;

    /**
     * Represents the QName of the element that conveys additional information
     * on the pre-defined WS-Addressing faults.
     */
    public final QName problemHeaderQNameTag;

    /**
     * Represents the QName of the element that conveys additional information
     * if Action is not matching with that expected.
     */
    public final QName problemActionTag;

    /**
     * Represents the QName of the header element that is used to capture the fault detail
     * if there is a fault processing WS-Addressing Message Addressing Property. This is
     * only used for SOAP 1.1.
     */
    public final QName faultDetailTag;

    /**
     * Fault sub-sub-code that represents
     * "Specifies that the invalid header was expected to be an EPR but did not contain an [address]."
     */
    public final QName fault_missingAddressInEpr;

    /**
     * Represents the Action QName in the WSDL for a specific WS-Addressing Version.
     */
    public final QName wsdlActionTag;

    /**
     * Represents the WSDL extension QName for a specific WS-Addressing Version.
     */
    public final QName wsdlExtensionTag;

    /**
     * Represents the WSDL anonymous QName for a specific WS-Addressing Version.
     */
    public final QName wsdlAnonymousTag;

    /**
     * Represents the QName of the reference parameter in a SOAP message. This is
     * only valid for W3C WS-Addressing.
     */
    public final QName isReferenceParameterTag;

    private static final String EXTENDED_FAULT_NAMESPACE = "http://jax-ws.dev.java.net/addressing/fault";
    public static final String UNSET_OUTPUT_ACTION = "http://jax-ws.dev.java.net/addressing/output-action-not-set";
    public static final String UNSET_INPUT_ACTION = "http://jax-ws.dev.java.net/addressing/input-action-not-set";

    /**
     * Fault sub-sub-code that represents duplicate &lt;Address> element in EPR.
     * This is a fault code not defined in the spec.
     */
    public static final QName fault_duplicateAddressInEpr = new QName(
        EXTENDED_FAULT_NAMESPACE, "DuplicateAddressInEpr", "wsa"
    );

    private AddressingVersion(String nsUri, String prefix, String anonymousEprString, String wsdlNsUri, String policyNsUri,
                              String anonymousUri, String noneUri,
                              EPR eprType ) {
        this.nsUri = nsUri;
        this.wsdlNsUri = wsdlNsUri;
        this.policyNsUri = policyNsUri;
        this.anonymousUri = anonymousUri;
        this.noneUri = noneUri;
        toTag = new QName(nsUri,"To", prefix);
        fromTag = new QName(nsUri,"From", prefix);
        replyToTag = new QName(nsUri,"ReplyTo", prefix);
        faultToTag = new QName(nsUri,"FaultTo", prefix);
        actionTag = new QName(nsUri,"Action", prefix);
        messageIDTag = new QName(nsUri,"MessageID", prefix);
        relatesToTag = new QName(nsUri,"RelatesTo", prefix);

        mapRequiredTag = new QName(nsUri,getMapRequiredLocalName(), prefix);
        actionMismatchTag = new QName(nsUri,getActionMismatchLocalName(), prefix);
        actionNotSupportedTag = new QName(nsUri,"ActionNotSupported", prefix);
        actionNotSupportedText = "The \"%s\" cannot be processed at the receiver";
        invalidMapTag = new QName(nsUri,getInvalidMapLocalName(), prefix);
        invalidAddressTag = new QName(nsUri,getInvalidAddressLocalName(), prefix);
        invalidCardinalityTag = new QName(nsUri,getInvalidCardinalityLocalName(), prefix);
        faultDetailTag = new QName(nsUri,"FaultDetail", prefix);

        problemHeaderQNameTag = new QName(nsUri,"ProblemHeaderQName", prefix);
        problemActionTag = new QName(nsUri, "ProblemAction", prefix);

        fault_missingAddressInEpr = new QName(nsUri,"MissingAddressInEPR", prefix);
        isReferenceParameterTag = new QName(nsUri,getIsReferenceParameterLocalName(), prefix);

        wsdlActionTag = new QName(wsdlNsUri,"Action", prefix);
        wsdlExtensionTag = new QName(wsdlNsUri, "UsingAddressing", prefix);
        wsdlAnonymousTag = new QName(wsdlNsUri, getWsdlAnonymousLocalName(), prefix);

        // create stock anonymous EPR
        try {
            this.anonymousEpr = new WSEndpointReference(new ByteArrayInputStream(anonymousEprString.getBytes("UTF-8")),this);
        } catch (XMLStreamException e) {
            throw new Error(e); // bug in our code as EPR should parse.
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }
        this.eprType = eprType;
    }

    /**
     * Gets the local name of the fault when a header representing a WS-Addressing Action is not same as SOAPAction
     *
     * @return local name
     */
    /* package */ abstract String getActionMismatchLocalName();

    /**
     * Returns {@link AddressingVersion} whose {@link #nsUri} equals to
     * the given string.
     *
     * This method does not perform input string validation.
     *
     * @param nsUri
     *      must not be null.
     * @return always non-null.
     */
    public static AddressingVersion fromNsUri(String nsUri) {
        if (nsUri.equals(W3C.nsUri))
            return W3C;

        if (nsUri.equals(MEMBER.nsUri))
            return MEMBER;

        return null;
    }

    /**
     * Gets the {@link AddressingVersion} from a {@link WSBinding}
     *
     * @param binding WSDL binding
     * @return
     *     addresing version enabled, or null if none is enabled.
     */
    public static @Nullable
    AddressingVersion fromBinding(WSBinding binding) {
        // TODO: who is responsible for reporting an error if both versions
        // are on?
        if (binding.isFeatureEnabled(AddressingFeature.class))
            return W3C;

        if (binding.isFeatureEnabled(MemberSubmissionAddressingFeature.class))
            return MEMBER;

        return null;
    }

    /**
     * Gets the {@link AddressingVersion} from a {@link WSDLPort}
     *
     * @param port WSDL port
     * @return addresing version
     */
    public static AddressingVersion fromPort(WSDLPort port) {
        if (port == null)
            return null;

        WebServiceFeature wsf = port.getFeature(AddressingFeature.class);
        if (wsf == null) {
            wsf = port.getFeature(MemberSubmissionAddressingFeature.class);
        }
        if (wsf == null)
            return null;

        return fromFeature(wsf);
    }

    /**
     * Returns {@link #nsUri} associated with this {@link AddressingVersion}
     *
     * @return namespace URI
     * @deprecated
     *      Use {@link #nsUri}.
     */
    public String getNsUri() {
        return nsUri;
    }

    /**
     * Returns true if the given local name is considered as
     * a reference parameter in EPR.
     *
     * For W3C, this means "ReferenceParameters",
     * and for the member submission version, this means
     * either "ReferenceParameters" or "ReferenceProperties".
     */
    public abstract boolean isReferenceParameter(String localName);

    /**
     * Returns WsaTubeHelper for the WS-Addressing version identified by <code>binding</code>
     * {@link WSBinding} and for the {@link WSDLPort} port.
     *
     * @return WS-A version specific helper
     *
     * @deprecated
     *     TODO  why are we exposing implementation specificc class through api?
     *     TODO  Remove it if no one elase uses it.
     */
    public abstract WsaTubeHelper getWsaHelper(WSDLPort wsdlPort, SEIModel seiModel, WSBinding binding);

    /**
     * Gets the none URI value associated with this WS-Addressing version.
     *
     * @return none URI value
     * @deprecated
     *      Use {@link #noneUri}.
     */
    public final String getNoneUri() {
        return noneUri;
    }

    /**
     * Gets the anonymous URI value associated with this WS-Addressing version.
     *
     * @deprecated
     *      Use {@link #anonymousUri}
     */
    public final String getAnonymousUri() {
        return anonymousUri;
    }

    /**
     * Gets the default fault Action value associated with this WS-Addressing version.
     *
     * @return default fault Action value
     */
    public String getDefaultFaultAction() {
        return nsUri + "/fault";
    }

    /**
     * Gets the local name of the fault when a header representing a WS-Addressing Message
     * Addresing Property is absent.
     *
     * @return local name
     */
    /* package */ abstract String getMapRequiredLocalName();

    /**
     * Gets the description text when a required WS-Addressing header representing a
     * Message Addressing Property is absent.
     *
     * @return description text
     */
    public abstract String getMapRequiredText();

    /**
         * Gets the local name of the fault when a header representing anaddress is invalid.
         * @return local name
         */
    /* package */ abstract String getInvalidAddressLocalName();


    /**
     * Gets the local name of the fault when a header representing a WS-Addressing Message
     * Addresing Property is invalid and cannot be processed.
     *
     * @return local name
     */
    /* package */ abstract String getInvalidMapLocalName();

    /**
     * Gets the description text when a header representing a WS-Addressing
     * Message Addressing Property is invalid and cannot be processed.
     *
     * @return description text
     */
    public abstract String getInvalidMapText();

    /**
     * Gets the local name of the fault when a header representing a WS-Addressing Message
     * Addresing Property occurs greater than expected number.
     *
     * @return local name
     */
    /* package */ abstract String getInvalidCardinalityLocalName();

    /* package */ abstract String getWsdlAnonymousLocalName();

    public abstract String getPrefix();

    public abstract String getWsdlPrefix();

    public abstract Class<? extends WebServiceFeature> getFeatureClass();
    /**
     * Creates an outbound {@link Header} from a reference parameter.
     */
    /*package*/ abstract Header createReferenceParameterHeader(XMLStreamBuffer mark, String nsUri, String localName);

    /**
     * Gets the local name for wsa:IsReferenceParameter. This method will return a valid
     * value only valid for W3C WS-Addressing. For Member Submission WS-Addressing, this method
     * returns null.
     */
    /*package*/ abstract String getIsReferenceParameterLocalName();

    public static AddressingVersion fromFeature(WebServiceFeature af) {
        if (af.getID().equals(AddressingFeature.ID))
            return W3C;
        else if (af.getID().equals(MemberSubmissionAddressingFeature.ID))
            return MEMBER;
        else
            return null;
    }

    /**
     * Gets the {@link WebServiceFeature} corresponding to the namespace URI of
     * WS-Addressing policy assertion in the WSDL. <code>enabled</code> and
     * <code>required</code> are used to initialize the value of the feature.
     *
     * @param nsUri namespace URI of the WS-Addressing policy assertion in the WSDL
     * @param enabled true if feature is to be enabled, false otherwise
     * @param required true if feature is required, false otherwise. Corresponds
     *          to wsdl:required on the extension/assertion.
     * @return WebServiceFeature corresponding to the assertion namespace URI
     * @throws WebServiceException if an unsupported namespace URI is passed
     */
    public static @NotNull WebServiceFeature getFeature(String nsUri, boolean enabled, boolean required) {
        if (nsUri.equals(W3C.policyNsUri))
            return new AddressingFeature(enabled, required);
        else if (nsUri.equals(MEMBER.policyNsUri))
            return new MemberSubmissionAddressingFeature(enabled, required);
        else
            throw new WebServiceException("Unsupported namespace URI: " + nsUri);
    }

    /**
     * Gets the corresponding {@link AddressingVersion} instance from the
     * EPR class.
     */
    public static @NotNull AddressingVersion fromSpecClass(Class<? extends EndpointReference> eprClass) {
        if(eprClass==W3CEndpointReference.class)
            return W3C;
        if(eprClass==MemberSubmissionEndpointReference.class)
            return MEMBER;
        throw new WebServiceException("Unsupported EPR type: "+eprClass);
    }

    /**
     * Returns true if the WebServiceFeature is either a {@link AddressingFeature} or
     * {@link MemberSubmissionAddressingFeature} and is required.
     *
     * @param wsf The WebServiceFeature encaps
     * @throws WebServiceException if <code>wsf</code> does not contain either {@link AddressingFeature} or
     * {@link MemberSubmissionAddressingFeature}
     * @return true if <code>wsf</code> requires WS-Addressing
     */
    public static boolean isRequired(WebServiceFeature wsf) {
        if (wsf.getID().equals(AddressingFeature.ID)) {
            return ((AddressingFeature)wsf).isRequired();
        } else if (wsf.getID().equals(MemberSubmissionAddressingFeature.ID)) {
            return ((MemberSubmissionAddressingFeature)wsf).isRequired();
        } else
            throw new WebServiceException("WebServiceFeature not an Addressing feature: "+ wsf.getID());
    }

    /**
     * Returns true if <code>binding</code> contains either a {@link AddressingFeature} or
     * {@link MemberSubmissionAddressingFeature} and is required.
     *
     * @param binding The binding
     * @return true if <code>binding</code> requires WS-Addressing
     */
    public static boolean isRequired(WSBinding binding) {
        AddressingFeature af = binding.getFeature(AddressingFeature.class);
        if (af != null)
            return af.isRequired();
        MemberSubmissionAddressingFeature msaf = binding.getFeature(MemberSubmissionAddressingFeature.class);
        if(msaf != null)
            return msaf.isRequired();

        return false;
    }

    /**
     * Returns true if <code>binding</code> contains either a {@link AddressingFeature} or
     * {@link MemberSubmissionAddressingFeature} and is enabled.
     *
     * @param binding The binding
     * @return true if WS-Addressing is enabled for <code>binding</code>.
     */
    public static boolean isEnabled(WSBinding binding) {
        return binding.isFeatureEnabled(MemberSubmissionAddressingFeature.class) ||
                binding.isFeatureEnabled(AddressingFeature.class);
    }

    public final static class EPR {
        public final Class<? extends EndpointReference> eprClass;
        public final String address;
        public final String serviceName;
        public final String portName;
        public final String portTypeName;
        public final String referenceParameters;
        /**
         * Element under which metadata is specified.
         * In W3C, it is wsa:Metadata
         * In Member, it is directly under mex:MetadataSection
         */
        public final QName wsdlMetadata;
        public final String referenceProperties;

        public EPR(Class<? extends EndpointReference> eprClass, String address, String serviceName, String portName,
                    String portTypeName, QName wsdlMetadata,
                    String referenceParameters, String referenceProperties) {
            this.eprClass = eprClass;
            this.address = address;
            this.serviceName = serviceName;
            this.portName = portName;
            this.portTypeName = portTypeName;
            this.referenceParameters = referenceParameters;
            this.referenceProperties = referenceProperties;
            this.wsdlMetadata = wsdlMetadata;

        }
    }

}
