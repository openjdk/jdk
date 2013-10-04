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

package com.sun.xml.internal.ws.api.model.wsdl;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.model.ParameterBinding;

import javax.jws.WebParam.Mode;
import javax.xml.namespace.QName;

import java.util.Map;

/**
 * Abstracts wsdl:binding/wsdl:operation. It can be used to determine the parts and their binding.
 *
 * @author Vivek Pandey
 */
public interface WSDLBoundOperation extends WSDLObject, WSDLExtensible {
    /**
     * Short-cut for {@code getOperation().getName()}
     */
    @NotNull QName getName();

    /**
     * Gives soapbinding:operation@soapAction value. soapbinding:operation@soapAction is optional attribute.
     * If not present an empty String is returned as per BP 1.1 R2745.
     */
    @NotNull String getSOAPAction();

    /**
     * Gets the wsdl:portType/wsdl:operation model - {@link WSDLOperation},
     * associated with this binding operation.
     *
     * @return always same {@link WSDLOperation}
     */
    @NotNull WSDLOperation getOperation();

    /**
     * Gives the owner {@link WSDLBoundPortType}
     */
    @NotNull WSDLBoundPortType getBoundPortType();

    /**
     * Gets the soapbinding:binding/operation/wsaw:Anonymous. A default value of OPTIONAL is returned.
     *
     * @return Anonymous value of the operation
     */
    ANONYMOUS getAnonymous();

    enum ANONYMOUS { optional, required, prohibited }

    /**
     * Gets {@link WSDLPart} for the given wsdl:input or wsdl:output part
     *
     * @return null if no part is found
     */
    @Nullable WSDLPart getPart(@NotNull String partName, @NotNull Mode mode);

    /**
     * Gets {@link ParameterBinding} for a given wsdl part in wsdl:input
     *
     * @param part Name of wsdl:part, must be non-null
     * @return null if the part is not found.
     */
    public ParameterBinding getInputBinding(String part);

    /**
     * Gets {@link ParameterBinding} for a given wsdl part in wsdl:output
     *
     * @param part Name of wsdl:part, must be non-null
     * @return null if the part is not found.
     */
    public ParameterBinding getOutputBinding(String part);

    /**
     * Gets {@link ParameterBinding} for a given wsdl part in wsdl:fault
     *
     * @param part Name of wsdl:part, must be non-null
     * @return null if the part is not found.
     */
    public ParameterBinding getFaultBinding(String part);

    /**
     * Gets the MIME type for a given wsdl part in wsdl:input
     *
     * @param part Name of wsdl:part, must be non-null
     * @return null if the part is not found.
     */
    public String getMimeTypeForInputPart(String part);

    /**
     * Gets the MIME type for a given wsdl part in wsdl:output
     *
     * @param part Name of wsdl:part, must be non-null
     * @return null if the part is not found.
     */
    public String getMimeTypeForOutputPart(String part);

    /**
     * Gets the MIME type for a given wsdl part in wsdl:fault
     *
     * @param part Name of wsdl:part, must be non-null
     * @return null if the part is not found.
     */
    public String getMimeTypeForFaultPart(String part);

    /**
     * Gets all inbound {@link WSDLPart} by its {@link WSDLPart#getName() name}.
     */
    @NotNull Map<String,? extends WSDLPart> getInParts();

    /**
     * Gets all outbound {@link WSDLPart} by its {@link WSDLPart#getName() name}.
     */
    @NotNull Map<String,? extends WSDLPart> getOutParts();

    /**
     * Gets all the {@link WSDLFault} bound to this operation.
     */
    @NotNull Iterable<? extends WSDLBoundFault> getFaults();

    /**
     * Map of wsdl:input part name and the binding as {@link ParameterBinding}
     *
     * @return empty Map if there is no parts
     */
    public Map<String, ParameterBinding> getInputParts();

    /**
     * Map of wsdl:output part name and the binding as {@link ParameterBinding}
     *
     * @return empty Map if there is no parts
     */
    public Map<String, ParameterBinding> getOutputParts();

    /**
     * Map of wsdl:fault part name and the binding as {@link ParameterBinding}
     *
     * @return empty Map if there is no parts
     */
    public Map<String, ParameterBinding> getFaultParts();

    /**
     * Gets the payload QName of the request message.
     *
     * <p>
     * It's possible for an operation to define no body part, in which case
     * this method returns null.
     */
    @Nullable QName getRequestPayloadName();

    /**
     * Gets the payload QName of the response message.
     *
     * <p>
     * It's possible for an operation to define no body part, in which case
     * this method returns null.
     */
    @Nullable QName getResponsePayloadName();

    /**
     * Gets the namespace of request payload.
     */
    String getRequestNamespace();

    /**
     * Gets the namespace of response payload.
     */
    String getResponseNamespace();

}
