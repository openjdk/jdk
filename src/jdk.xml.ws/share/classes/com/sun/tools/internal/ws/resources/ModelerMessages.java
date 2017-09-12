/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.resources;

import java.util.Locale;
import java.util.ResourceBundle;
import com.sun.istack.internal.localization.Localizable;
import com.sun.istack.internal.localization.LocalizableMessageFactory;
import com.sun.istack.internal.localization.LocalizableMessageFactory.ResourceBundleSupplier;
import com.sun.istack.internal.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
public final class ModelerMessages {

    private final static String BUNDLE_NAME = "com.sun.tools.internal.ws.resources.modeler";
    private final static LocalizableMessageFactory MESSAGE_FACTORY = new LocalizableMessageFactory(BUNDLE_NAME, new ModelerMessages.BundleSupplier());
    private final static Localizer LOCALIZER = new Localizer();

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_FAULT_NOT_LITERAL(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringFault.notLiteral", arg0, arg1);
    }

    /**
     * ignoring encoded fault "{0}" of binding operation "{1}"
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_FAULT_NOT_LITERAL(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_FAULT_NOT_LITERAL(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_OPERATION_MULTIPLE_PART_BINDING(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingOperation.multiplePartBinding", arg0, arg1);
    }

    /**
     * abstract operation "{0}" binding, part "{1}" has multiple binding.
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_OPERATION_MULTIPLE_PART_BINDING(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_OPERATION_MULTIPLE_PART_BINDING(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_HEADER_FAULT_NOT_LITERAL(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringHeaderFault.notLiteral", arg0, arg1, arg2);
    }

    /**
     * ignoring header fault part="{0}" message="{1}" of operation {2}, use attribute must be "literal"
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_HEADER_FAULT_NOT_LITERAL(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_HEADER_FAULT_NOT_LITERAL(arg0, arg1, arg2));
    }

    public static Localizable localizableWSDLMODELER_INVALID_HEADER_NOT_LITERAL(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.header.notLiteral", arg0, arg1);
    }

    /**
     * Invalid header "{0}" of binding operation "{1}": not literal
     *
     */
    public static String WSDLMODELER_INVALID_HEADER_NOT_LITERAL(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_HEADER_NOT_LITERAL(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_NOT_SUPPORTED_STYLE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.notSupportedStyle", arg0);
    }

    /**
     * ignoring operation "{0}": not request-response or one-way
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_NOT_SUPPORTED_STYLE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_NOT_SUPPORTED_STYLE(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_PARAMETER_ORDER_TOO_MANY_UNMENTIONED_PARTS(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.parameterOrder.tooManyUnmentionedParts", arg0);
    }

    /**
     * more than one part left out in the parameterOrder attribute of operation "{0}"
     *
     */
    public static String WSDLMODELER_INVALID_PARAMETER_ORDER_TOO_MANY_UNMENTIONED_PARTS(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_PARAMETER_ORDER_TOO_MANY_UNMENTIONED_PARTS(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOMIZED_OPERATION_NAME(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.javaReservedWordNotAllowed.customizedOperationName", arg0, arg1);
    }

    /**
     * Ignoring operation "{0}", can''t generate java method ,customized name "{1}" of the wsdl:operation is a java keyword.
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOMIZED_OPERATION_NAME(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOMIZED_OPERATION_NAME(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_OPERATION_NOT_SUPPORTED_STYLE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.operation.notSupportedStyle", arg0, arg1);
    }

    /**
     * Invalid WSDL, wsdl:operation "{0}" in wsdl:portType "{1}": not request-response or one-way
     *
     */
    public static String WSDLMODELER_INVALID_OPERATION_NOT_SUPPORTED_STYLE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_OPERATION_NOT_SUPPORTED_STYLE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_NONCONFORMING_WSDL_TYPES() {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.nonconforming.wsdl.types");
    }

    /**
     * Non conforming WS-I WSDL used for wsdl:types
     *
     */
    public static String WSDLMODELER_WARNING_NONCONFORMING_WSDL_TYPES() {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_NONCONFORMING_WSDL_TYPES());
    }

    public static Localizable localizableWSDLMODELER_INVALID_HEADER_NOT_FOUND(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.header.notFound", arg0, arg1);
    }

    /**
     * header "{0}" of binding operation "{1}": not found
     *
     */
    public static String WSDLMODELER_INVALID_HEADER_NOT_FOUND(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_HEADER_NOT_FOUND(arg0, arg1));
    }

    public static Localizable localizableMIMEMODELER_WARNING_IGNORINGINVALID_HEADER_PART_NOT_DECLARED_IN_ROOT_PART(Object arg0) {
        return MESSAGE_FACTORY.getMessage("mimemodeler.warning.IgnoringinvalidHeaderPart.notDeclaredInRootPart", arg0);
    }

    /**
     * Headers not in root mime:part with soap:body, ignoring headers in operation "{0}"
     *
     */
    public static String MIMEMODELER_WARNING_IGNORINGINVALID_HEADER_PART_NOT_DECLARED_IN_ROOT_PART(Object arg0) {
        return LOCALIZER.localize(localizableMIMEMODELER_WARNING_IGNORINGINVALID_HEADER_PART_NOT_DECLARED_IN_ROOT_PART(arg0));
    }

    public static Localizable localizableWSDLMODELER_UNSUPPORTED_BINDING_MIME() {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.unsupportedBinding.mime");
    }

    /**
     * WSDL MIME binding is not currently supported!
     *
     */
    public static String WSDLMODELER_UNSUPPORTED_BINDING_MIME() {
        return LOCALIZER.localize(localizableWSDLMODELER_UNSUPPORTED_BINDING_MIME());
    }

    public static Localizable localizableWSDLMODELER_NON_UNIQUE_BODY_ERROR(Object arg0, Object arg1, Object arg2, Object arg3) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.nonUnique.body.error", arg0, arg1, arg2, arg3);
    }

    /**
     * Non unique body parts! In a port, as per BP 1.1 R2710 operations must have unique operation signature on the wire for successful dispatch. In port {0}, Operations "{1}" and "{2}" have the same request body block {3}. Try running wsimport with -extension switch, runtime will try to dispatch using SOAPAction
     *
     */
    public static String WSDLMODELER_NON_UNIQUE_BODY_ERROR(Object arg0, Object arg1, Object arg2, Object arg3) {
        return LOCALIZER.localize(localizableWSDLMODELER_NON_UNIQUE_BODY_ERROR(arg0, arg1, arg2, arg3));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_FAULT_MISSING_NAMESPACE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingFault.missingNamespace", arg0, arg1);
    }

    /**
     * fault "{0}" in operation "{1}" must specify a value for the "namespace" attribute
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_FAULT_MISSING_NAMESPACE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_FAULT_MISSING_NAMESPACE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODLER_WARNING_OPERATION_USE() {
        return MESSAGE_FACTORY.getMessage("wsdlmodler.warning.operation.use");
    }

    /**
     * The WSDL used has operations with literal and encoded use. -f:searchschema is not supported for this scenario.
     *
     */
    public static String WSDLMODLER_WARNING_OPERATION_USE() {
        return LOCALIZER.localize(localizableWSDLMODLER_WARNING_OPERATION_USE());
    }

    public static Localizable localizableWSDLMODELER_ERROR_PARTS_NOT_FOUND(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.error.partsNotFound", arg0, arg1);
    }

    /**
     * parts "{0}" not found in the message "{1}", wrong WSDL
     *
     */
    public static String WSDLMODELER_ERROR_PARTS_NOT_FOUND(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_ERROR_PARTS_NOT_FOUND(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_JAXB_JAVATYPE_NOTFOUND(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.jaxb.javatype.notfound", arg0, arg1);
    }

    /**
     * Schema descriptor {0} in message part "{1}" is not defined and could not be bound to Java. Perhaps the schema descriptor {0} is not defined in the schema imported/included in the WSDL. You can either add such imports/includes or run wsimport and provide the schema location using -b switch.
     *
     */
    public static String WSDLMODELER_JAXB_JAVATYPE_NOTFOUND(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_JAXB_JAVATYPE_NOTFOUND(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_HEADERFAULT_NOT_LITERAL(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.headerfault.notLiteral", arg0, arg1);
    }

    /**
     * Invalid headerfault "{0}" of binding operation "{1}": not literal
     *
     */
    public static String WSDLMODELER_INVALID_HEADERFAULT_NOT_LITERAL(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_HEADERFAULT_NOT_LITERAL(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_20_RPCENC_NOT_SUPPORTED() {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler20.rpcenc.not.supported");
    }

    /**
     * rpc/encoded wsdl's are not supported in JAXWS 2.0.
     *
     */
    public static String WSDLMODELER_20_RPCENC_NOT_SUPPORTED() {
        return LOCALIZER.localize(localizableWSDLMODELER_20_RPCENC_NOT_SUPPORTED());
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_FAULT_NOT_FOUND(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingFault.notFound", arg0, arg1);
    }

    /**
     * fault "{0}" in operation "{1}" does not match any fault in the corresponding port type operation
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_FAULT_NOT_FOUND(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_FAULT_NOT_FOUND(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_DOCLITOPERATION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.doclitoperation", arg0);
    }

    /**
     * Invalid wsdl:operation "{0}": its a document-literal operation,  message part must refer to a schema element declaration
     *
     */
    public static String WSDLMODELER_INVALID_DOCLITOPERATION(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_DOCLITOPERATION(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_NOT_NC_NAME(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.notNCName", arg0, arg1);
    }

    /**
     * Ignoring operation "{0}", it has illegal character ''{1}'' in its name. Its rpc-literal operation - jaxws won't be able to serialize it!
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_NOT_NC_NAME(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_NOT_NC_NAME(arg0, arg1));
    }

    public static Localizable localizableMIMEMODELER_INVALID_MIME_PART_NAME_NOT_ALLOWED(Object arg0) {
        return MESSAGE_FACTORY.getMessage("mimemodeler.invalidMimePart.nameNotAllowed", arg0);
    }

    /**
     * name attribute on wsdl:part in Operation "{0}" is ignored. Its not allowed as per WS-I AP 1.0.
     *
     */
    public static String MIMEMODELER_INVALID_MIME_PART_NAME_NOT_ALLOWED(Object arg0) {
        return LOCALIZER.localize(localizableMIMEMODELER_INVALID_MIME_PART_NAME_NOT_ALLOWED(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_NOT_LITERAL(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.notLiteral", arg0);
    }

    /**
     * ignoring document-style operation "{0}": parameters are not literal
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_NOT_LITERAL(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_NOT_LITERAL(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_FAULT_DOCUMENT_OPERATION(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringFault.documentOperation", arg0, arg1);
    }

    /**
     * ignoring fault "{0}" of document-style operation "{1}"
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_FAULT_DOCUMENT_OPERATION(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_FAULT_DOCUMENT_OPERATION(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_FAULT_NOT_UNIQUE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingFault.notUnique", arg0, arg1);
    }

    /**
     * fault "{0}" in operation "{1}" matches more than one fault in the corresponding port type operation
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_FAULT_NOT_UNIQUE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_FAULT_NOT_UNIQUE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_OPERATION_MISSING_INPUT_NAME(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingOperation.missingInputName", arg0);
    }

    /**
     * binding operation "{0}" must specify a name for its input message
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_OPERATION_MISSING_INPUT_NAME(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_OPERATION_MISSING_INPUT_NAME(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_WRAPPER_STYLE(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.operation.javaReservedWordNotAllowed.wrapperStyle", arg0, arg1, arg2);
    }

    /**
     * Invalid operation "{0}", can''t generate java method parameter. Local name of the wrapper child "{1}" in the global element "{2}" is a java keyword. Use customization to change the parameter name.
     *
     */
    public static String WSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_WRAPPER_STYLE(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_WRAPPER_STYLE(arg0, arg1, arg2));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_SOAP_BINDING_12(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringSOAPBinding12", arg0);
    }

    /**
     * Ignoring SOAP port "{0}": it uses non-standard SOAP 1.2 binding.
     * You must specify the "-extension" option to use this binding.
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_SOAP_BINDING_12(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_SOAP_BINDING_12(arg0));
    }

    public static Localizable localizableMIMEMODELER_INVALID_MIME_CONTENT_MESAGE_PART_ELEMENT_KIND(Object arg0) {
        return MESSAGE_FACTORY.getMessage("mimemodeler.invalidMimeContent.mesagePartElementKind", arg0);
    }

    /**
     * wsdl:part element referenced by mime:content part attribute: {0} must be defined using type attribute!
     *
     */
    public static String MIMEMODELER_INVALID_MIME_CONTENT_MESAGE_PART_ELEMENT_KIND(Object arg0) {
        return LOCALIZER.localize(localizableMIMEMODELER_INVALID_MIME_CONTENT_MESAGE_PART_ELEMENT_KIND(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_SOAP_BINDING_NON_HTTP_TRANSPORT(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringSOAPBinding.nonHTTPTransport", arg0);
    }

    /**
     * ignoring SOAP port "{0}": unrecognized transport. try running wsimport with -extension switch.
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_SOAP_BINDING_NON_HTTP_TRANSPORT(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_SOAP_BINDING_NON_HTTP_TRANSPORT(arg0));
    }

    public static Localizable localizableWSDLMODELER_DUPLICATE_FAULT_SOAP_NAME(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.duplicate.fault.soap.name", arg0, arg1, arg2);
    }

    /**
     * ignoring fault "{0}" of operation "{1}", soap:fault name "{2}" is not unique
     *
     */
    public static String WSDLMODELER_DUPLICATE_FAULT_SOAP_NAME(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSDLMODELER_DUPLICATE_FAULT_SOAP_NAME(arg0, arg1, arg2));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_EMPTY_OUTPUT_MESSAGE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.cannotHandleEmptyOutputMessage", arg0);
    }

    /**
     * ignoring operation "{0}": output message is empty
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_EMPTY_OUTPUT_MESSAGE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_EMPTY_OUTPUT_MESSAGE(arg0));
    }

    public static Localizable localizableMIMEMODELER_INVALID_MIME_PART_MORE_THAN_ONE_SOAP_BODY(Object arg0) {
        return MESSAGE_FACTORY.getMessage("mimemodeler.invalidMimePart.moreThanOneSOAPBody", arg0);
    }

    /**
     * Ignoring operation "{0}". The Multipart/Related structure has invalid root part: more than one soap:body parts found
     *
     */
    public static String MIMEMODELER_INVALID_MIME_PART_MORE_THAN_ONE_SOAP_BODY(Object arg0) {
        return LOCALIZER.localize(localizableMIMEMODELER_INVALID_MIME_PART_MORE_THAN_ONE_SOAP_BODY(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_FAULT_MESSAGE_HAS_MORE_THAN_ONE_PART(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingFault.messageHasMoreThanOnePart", arg0, arg1);
    }

    /**
     * fault "{0}" refers to message "{1}", but the message has more than one parts
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_FAULT_MESSAGE_HAS_MORE_THAN_ONE_PART(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_FAULT_MESSAGE_HAS_MORE_THAN_ONE_PART(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_TYPE_MESSAGE_PART(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.cannotHandleTypeMessagePart", arg0);
    }

    /**
     * ignoring operation "{0}": message part does not refer to a schema element declaration
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_TYPE_MESSAGE_PART(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_TYPE_MESSAGE_PART(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_FAULTS(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringFaults", arg0);
    }

    /**
     * ignoring faults declared by operation "{0}"
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_FAULTS(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_FAULTS(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_OPERATION_INPUT_SOAP_BODY_MISSING_NAMESPACE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingOperation.inputSoapBody.missingNamespace", arg0);
    }

    /**
     * input message of binding operation "{0}" must specify a value for the "namespace" attribute
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_OPERATION_INPUT_SOAP_BODY_MISSING_NAMESPACE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_OPERATION_INPUT_SOAP_BODY_MISSING_NAMESPACE(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_ELEMENT_MESSAGE_PART(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.cannotHandleElementMessagePart", arg0);
    }

    /**
     * ignoring operation "{0}": message part does not refer to a schema type declaration
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_ELEMENT_MESSAGE_PART(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_ELEMENT_MESSAGE_PART(arg0));
    }

    public static Localizable localizableMODELER_NESTED_MODEL_ERROR(Object arg0) {
        return MESSAGE_FACTORY.getMessage("modeler.nestedModelError", arg0);
    }

    /**
     * modeler error: {0}
     *
     */
    public static String MODELER_NESTED_MODEL_ERROR(Object arg0) {
        return LOCALIZER.localize(localizableMODELER_NESTED_MODEL_ERROR(arg0));
    }

    public static Localizable localizableMIMEMODELER_INVALID_MIME_CONTENT_DIFFERENT_PART() {
        return MESSAGE_FACTORY.getMessage("mimemodeler.invalidMimeContent.differentPart");
    }

    /**
     * Ignoring the mime:part. Invalid mime:part, the mime:content has different part attribute.
     *
     */
    public static String MIMEMODELER_INVALID_MIME_CONTENT_DIFFERENT_PART() {
        return LOCALIZER.localize(localizableMIMEMODELER_INVALID_MIME_CONTENT_DIFFERENT_PART());
    }

    public static Localizable localizableWSDLMODELER_INVALID_IGNORING_MEMBER_SUBMISSION_ADDRESSING(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.ignoringMemberSubmissionAddressing", arg0, arg1);
    }

    /**
     * ignoring wsa:Action attribute since obsolete addressing version 08-2004:"{0}" used; expecting addressing version "{1}". To use version 08-2004 anyway run wsimport with -extension switch.
     *
     */
    public static String WSDLMODELER_INVALID_IGNORING_MEMBER_SUBMISSION_ADDRESSING(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_IGNORING_MEMBER_SUBMISSION_ADDRESSING(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_BINDING_OPERATION_MULTIPLE_PART_BINDING(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.bindingOperation.multiplePartBinding", arg0, arg1);
    }

    /**
     * Check the abstract operation "{0}" binding, part "{1}" has multiple binding. Will try to generated artifacts anyway...
     *
     */
    public static String WSDLMODELER_WARNING_BINDING_OPERATION_MULTIPLE_PART_BINDING(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_BINDING_OPERATION_MULTIPLE_PART_BINDING(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_NON_SOAP_PORT_NO_ADDRESS(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringNonSOAPPort.noAddress", arg0);
    }

    /**
     * ignoring port "{0}": no SOAP address specified. try running wsimport with -extension switch.
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_NON_SOAP_PORT_NO_ADDRESS(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_NON_SOAP_PORT_NO_ADDRESS(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_PORT_SOAP_BINDING_12(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.port.SOAPBinding12", arg0);
    }

    /**
     * SOAP port "{0}": uses a non-standard SOAP 1.2 binding.
     *
     */
    public static String WSDLMODELER_WARNING_PORT_SOAP_BINDING_12(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_PORT_SOAP_BINDING_12(arg0));
    }

    public static Localizable localizableMIMEMODELER_INVALID_MIME_CONTENT_INVALID_SCHEMA_TYPE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("mimemodeler.invalidMimeContent.invalidSchemaType", arg0, arg1);
    }

    /**
     * Ignoring the mime:part. mime part: {0} can not be mapped to schema type: {1}
     *
     */
    public static String MIMEMODELER_INVALID_MIME_CONTENT_INVALID_SCHEMA_TYPE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableMIMEMODELER_INVALID_MIME_CONTENT_INVALID_SCHEMA_TYPE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_NOT_ENCODED(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.notEncoded", arg0);
    }

    /**
     * ignoring RPC-style operation "{0}": parameters are not encoded
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_NOT_ENCODED(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_NOT_ENCODED(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_HEADER_MESSAGE_PART_MUST_HAVE_ELEMENT_DESCRIPTOR(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.header.message.partMustHaveElementDescriptor", arg0, arg1);
    }

    /**
     * Invalid header "{0}" in operation {1}: part must specify a "element" attribute
     *
     */
    public static String WSDLMODELER_INVALID_HEADER_MESSAGE_PART_MUST_HAVE_ELEMENT_DESCRIPTOR(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_HEADER_MESSAGE_PART_MUST_HAVE_ELEMENT_DESCRIPTOR(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_MESSAGE_PART_MUST_HAVE_ELEMENT_DESCRIPTOR(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.message.partMustHaveElementDescriptor", arg0, arg1);
    }

    /**
     * in message "{0}", part "{1}" must specify a "element" attribute
     *
     */
    public static String WSDLMODELER_INVALID_MESSAGE_PART_MUST_HAVE_ELEMENT_DESCRIPTOR(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_MESSAGE_PART_MUST_HAVE_ELEMENT_DESCRIPTOR(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_ERROR_PART_NOT_FOUND(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.error.partNotFound", arg0, arg1);
    }

    /**
     * part "{1}" of operation "{0}" could not be resolved!
     *
     */
    public static String WSDLMODELER_ERROR_PART_NOT_FOUND(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_ERROR_PART_NOT_FOUND(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_OPERATION_NAME(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.operation.javaReservedWordNotAllowed.operationName", arg0);
    }

    /**
     * Invalid operation "{0}", it''s java reserved word, can''t generate java method. Use customization to change the operation name.
     *
     */
    public static String WSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_OPERATION_NAME(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_OPERATION_NAME(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_OPERATION_NOT_IN_PORT_TYPE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingOperation.notInPortType", arg0, arg1);
    }

    /**
     * in binding "{1}", operation "{0}" does not appear in the corresponding port type
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_OPERATION_NOT_IN_PORT_TYPE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_OPERATION_NOT_IN_PORT_TYPE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_OPERATION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalidOperation", arg0);
    }

    /**
     * invalid operation: {0}
     *
     */
    public static String WSDLMODELER_INVALID_OPERATION(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_OPERATION(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_OPERATION_OUTPUT_HEADER_MISSING_NAMESPACE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingOperation.outputHeader.missingNamespace", arg0, arg1);
    }

    /**
     * output header "{1}" of binding operation "{0}" must specify a value for the "namespace" attribute
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_OPERATION_OUTPUT_HEADER_MISSING_NAMESPACE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_OPERATION_OUTPUT_HEADER_MISSING_NAMESPACE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_MORE_THAN_ONE_PART_IN_INPUT_MESSAGE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.cannotHandleMoreThanOnePartInInputMessage", arg0);
    }

    /**
     * ignoring operation "{0}": more than one part in input message
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_MORE_THAN_ONE_PART_IN_INPUT_MESSAGE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_MORE_THAN_ONE_PART_IN_INPUT_MESSAGE(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_OPERATION_MISSING_OUTPUT_NAME(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingOperation.missingOutputName", arg0);
    }

    /**
     * binding operation "{0}" must specify a name for its output message
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_OPERATION_MISSING_OUTPUT_NAME(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_OPERATION_MISSING_OUTPUT_NAME(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_FAULT_MISSING_NAME(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingFault.missingName", arg0, arg1);
    }

    /**
     * fault "{0}" in operation "{1}" must specify a value for the "name" attribute
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_FAULT_MISSING_NAME(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_FAULT_MISSING_NAME(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_OPERATION_NOT_FOUND(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingOperation.notFound", arg0, arg1);
    }

    /**
     * in binding "{1}", operation "{0}" does not match any operation in the corresponding port type
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_OPERATION_NOT_FOUND(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_OPERATION_NOT_FOUND(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_BODY_PARTS_ATTRIBUTE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.cannotHandleBodyPartsAttribute", arg0);
    }

    /**
     * ignoring operation "{0}": cannot handle "parts" attribute of "soap:body" element
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_BODY_PARTS_ATTRIBUTE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_BODY_PARTS_ATTRIBUTE(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_NON_WRAPPER_STYLE(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.javaReservedWordNotAllowed.nonWrapperStyle", arg0, arg1, arg2);
    }

    /**
     * Ignoring operation "{0}", can''t generate java method. Parameter: part "{2}" in wsdl:message "{1}", is a java keyword. Use customization to change the parameter name or change the wsdl:part name.
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_NON_WRAPPER_STYLE(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_NON_WRAPPER_STYLE(arg0, arg1, arg2));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CONFLICT_STYLE_IN_WSI_MODE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.conflictStyleInWSIMode", arg0);
    }

    /**
     * ignoring operation "{0}": binding style and operation style are conflicting
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_CONFLICT_STYLE_IN_WSI_MODE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CONFLICT_STYLE_IN_WSI_MODE(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_HEADER_NOT_FOUND(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringHeader.notFound", arg0, arg1);
    }

    /**
     * ignoring header "{0}" of binding operation "{1}": not found
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_HEADER_NOT_FOUND(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_HEADER_NOT_FOUND(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_MEMBER_SUBMISSION_ADDRESSING_USED(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.memberSubmissionAddressingUsed", arg0, arg1);
    }

    /**
     * obsolete addressing version 08-2004:"{0}" used; version "{1}" should be used instead.
     *
     */
    public static String WSDLMODELER_WARNING_MEMBER_SUBMISSION_ADDRESSING_USED(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_MEMBER_SUBMISSION_ADDRESSING_USED(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_NO_SOAP_ADDRESS(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.noSOAPAddress", arg0);
    }

    /**
     * Port "{0}" is not a SOAP port, it has no soap:address
     *
     */
    public static String WSDLMODELER_WARNING_NO_SOAP_ADDRESS(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_NO_SOAP_ADDRESS(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_OPERATION_NAME(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.javaReservedWordNotAllowed.operationName", arg0);
    }

    /**
     * Ignoring operation "{0}", it''s java reserved word, can''t generate java method. Use customization to change the operation name.
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_OPERATION_NAME(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_OPERATION_NAME(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_HEADER_NOT_LITERAL(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringHeader.notLiteral", arg0, arg1);
    }

    /**
     * ignoring header "{0}" of binding operation "{1}": not literal
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_HEADER_NOT_LITERAL(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_HEADER_NOT_LITERAL(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_FAULT_OUTPUT_MISSING_SOAP_FAULT(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingFault.outputMissingSoapFault", arg0, arg1);
    }

    /**
     * fault "{0}" in operation "{1}" does not have a SOAP fault extension
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_FAULT_OUTPUT_MISSING_SOAP_FAULT(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_FAULT_OUTPUT_MISSING_SOAP_FAULT(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_NON_UNIQUE_BODY_WARNING(Object arg0, Object arg1, Object arg2, Object arg3) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.nonUnique.body.warning", arg0, arg1, arg2, arg3);
    }

    /**
     * Non unique body parts! In a port, as per BP 1.1 R2710 operations must have unique operation signature on the wire for successful dispatch. In port {0}, Operations "{1}" and "{2}" have the same request body block {3}. Method dispatching may fail, runtime will try to dispatch using SOAPAction
     *
     */
    public static String WSDLMODELER_NON_UNIQUE_BODY_WARNING(Object arg0, Object arg1, Object arg2, Object arg3) {
        return LOCALIZER.localize(localizableWSDLMODELER_NON_UNIQUE_BODY_WARNING(arg0, arg1, arg2, arg3));
    }

    public static Localizable localizableWSDLMODELER_WARNING_PORT_SOAP_BINDING_MIXED_STYLE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.port.SOAPBinding.mixedStyle", arg0);
    }

    /**
     * not a WS-I BP1.1 compliant SOAP port "{0}": the WSDL binding has mixed style, it must be rpc-literal or document-literal operation!
     *
     */
    public static String WSDLMODELER_WARNING_PORT_SOAP_BINDING_MIXED_STYLE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_PORT_SOAP_BINDING_MIXED_STYLE(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_FAULT_NO_SOAP_FAULT_NAME(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingFault.noSoapFaultName", arg0, arg1);
    }

    /**
     * soap:fault name not specified, wsdl:fault "{0}" in operation "{1}"
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_FAULT_NO_SOAP_FAULT_NAME(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_FAULT_NO_SOAP_FAULT_NAME(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_HEADERFAULT_MESSAGE_PART_MUST_HAVE_ELEMENT_DESCRIPTOR(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.headerfault.message.partMustHaveElementDescriptor", arg0, arg1, arg2);
    }

    /**
     * Invalid headerfault "{0}" for header {1} in operation {2}: part must specify an "element" attribute
     *
     */
    public static String WSDLMODELER_INVALID_HEADERFAULT_MESSAGE_PART_MUST_HAVE_ELEMENT_DESCRIPTOR(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_HEADERFAULT_MESSAGE_PART_MUST_HAVE_ELEMENT_DESCRIPTOR(arg0, arg1, arg2));
    }

    public static Localizable localizableWSDLMODELER_RPCLIT_UNKOWNSCHEMATYPE(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.rpclit.unkownschematype", arg0, arg1, arg2);
    }

    /**
     * XML type "{0}" could not be resolved, XML to JAVA binding failed! Please check the wsdl:part "{1}" in the wsdl:message "{2}".
     *
     */
    public static String WSDLMODELER_RPCLIT_UNKOWNSCHEMATYPE(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSDLMODELER_RPCLIT_UNKOWNSCHEMATYPE(arg0, arg1, arg2));
    }

    public static Localizable localizableWSDLMODELER_WARNING_R_2716(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.r2716", arg0, arg1);
    }

    /**
     * R2716 WSI-BasicProfile ver. 1.0, namespace attribute not allowed in doc/lit for {0}: "{1}"
     *
     */
    public static String WSDLMODELER_WARNING_R_2716(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_R_2716(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_WRAPPER_STYLE(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.javaReservedWordNotAllowed.wrapperStyle", arg0, arg1, arg2);
    }

    /**
     * Ignoring operation "{0}", can''t generate java method parameter. Local name of the wrapper child "{1}" in the global element "{2}" is a java keyword. Use customization to change the parameter name.
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_WRAPPER_STYLE(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_WRAPPER_STYLE(arg0, arg1, arg2));
    }

    public static Localizable localizableWSDLMODELER_INVALID_OPERATION_FAULT_NOT_LITERAL(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.operation.fault.notLiteral", arg0, arg1);
    }

    /**
     * ignoring encoded fault "{0}" in literal binding operation "{1}"
     *
     */
    public static String WSDLMODELER_INVALID_OPERATION_FAULT_NOT_LITERAL(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_OPERATION_FAULT_NOT_LITERAL(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_OPERATION_OUTPUT_SOAP_BODY_MISSING_NAMESPACE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingOperation.outputSoapBody.missingNamespace", arg0);
    }

    /**
     * output message of binding operation "{0}" must specify a value for the "namespace" attribute
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_OPERATION_OUTPUT_SOAP_BODY_MISSING_NAMESPACE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_OPERATION_OUTPUT_SOAP_BODY_MISSING_NAMESPACE(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_HEADER_CANT_RESOLVE_MESSAGE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.header.cant.resolve.message", arg0, arg1);
    }

    /**
     * header "{0}" of binding operation "{1}": cannot resolve message
     *
     */
    public static String WSDLMODELER_INVALID_HEADER_CANT_RESOLVE_MESSAGE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_HEADER_CANT_RESOLVE_MESSAGE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_SEARCH_SCHEMA_UNRECOGNIZED_TYPES(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.searchSchema.unrecognizedTypes", arg0);
    }

    /**
     * encountered {0} unrecognized type(s)
     *
     */
    public static String WSDLMODELER_WARNING_SEARCH_SCHEMA_UNRECOGNIZED_TYPES(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_SEARCH_SCHEMA_UNRECOGNIZED_TYPES(arg0));
    }

    public static Localizable localizableWSDLMODELER_RESPONSEBEAN_NOTFOUND(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.responsebean.notfound", arg0);
    }

    /**
     * wsimport failed to generate async response bean for operation: {0}
     *
     */
    public static String WSDLMODELER_RESPONSEBEAN_NOTFOUND(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_RESPONSEBEAN_NOTFOUND(arg0));
    }

    public static Localizable localizableWSDLMODELER_UNSOLVABLE_NAMING_CONFLICTS(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.unsolvableNamingConflicts", arg0);
    }

    /**
     * the following naming conflicts occurred: {0}
     *
     */
    public static String WSDLMODELER_UNSOLVABLE_NAMING_CONFLICTS(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_UNSOLVABLE_NAMING_CONFLICTS(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_NO_PORTS_IN_SERVICE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.noPortsInService", arg0);
    }

    /**
     * Service "{0}" does not contain any usable ports. try running wsimport with -extension switch.
     *
     */
    public static String WSDLMODELER_WARNING_NO_PORTS_IN_SERVICE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_NO_PORTS_IN_SERVICE(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_FAULT_CANT_RESOLVE_MESSAGE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringFault.cant.resolve.message", arg0, arg1);
    }

    /**
     * ignoring fault "{0}" of binding operation "{1}": cannot resolve message
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_FAULT_CANT_RESOLVE_MESSAGE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_FAULT_CANT_RESOLVE_MESSAGE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_PART_NOT_FOUND(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.partNotFound", arg0, arg1);
    }

    /**
     * ignoring operation "{0}": part "{1}" not found
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_PART_NOT_FOUND(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_PART_NOT_FOUND(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_HEADER_FAULT_NO_ELEMENT_ATTRIBUTE(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringHeaderFault.noElementAttribute", arg0, arg1, arg2);
    }

    /**
     * ignoring header fault part="{0}" message="{1}" of operation {2}
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_HEADER_FAULT_NO_ELEMENT_ATTRIBUTE(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_HEADER_FAULT_NO_ELEMENT_ATTRIBUTE(arg0, arg1, arg2));
    }

    public static Localizable localizableWSDLMODELER_WARNING_R_2716_R_2726(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.r2716r2726", arg0, arg1);
    }

    /**
     * R2716/R2726 WSI-BasicProfile ver. 1.0, namespace attribute not allowed in doc/lit or rpc/lit for {0}: "{1}"
     *
     */
    public static String WSDLMODELER_WARNING_R_2716_R_2726(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_R_2716_R_2726(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_OPERATION_MULTIPLE_MATCHING_OPERATIONS(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingOperation.multipleMatchingOperations", arg0, arg1);
    }

    /**
     * in binding "{1}", operation "{0}" does not reference a unique operation in the corresponding port type
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_OPERATION_MULTIPLE_MATCHING_OPERATIONS(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_OPERATION_MULTIPLE_MATCHING_OPERATIONS(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_HEADER_NOT_ENCODED(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringHeader.notEncoded", arg0, arg1);
    }

    /**
     * ignoring header "{0}" of binding operation "{1}": not SOAP-encoded
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_HEADER_NOT_ENCODED(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_HEADER_NOT_ENCODED(arg0, arg1));
    }

    public static Localizable localizableMIMEMODELER_ELEMENT_PART_INVALID_ELEMENT_MIME_TYPE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("mimemodeler.elementPart.invalidElementMimeType", arg0, arg1);
    }

    /**
     * The mime:content part refers to wsdl:part "{0}", defined using element attribute. Please make sure the mime type: "{1}" is appropriate to serialize XML.
     *
     */
    public static String MIMEMODELER_ELEMENT_PART_INVALID_ELEMENT_MIME_TYPE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableMIMEMODELER_ELEMENT_PART_INVALID_ELEMENT_MIME_TYPE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_PORT_TYPE_FAULT_NOT_FOUND(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.portTypeFault.notFound", arg0, arg1);
    }

    /**
     * fault "{0}" in portType operation "{1}" does not match any fault in the corresponding binding operation
     *
     */
    public static String WSDLMODELER_INVALID_PORT_TYPE_FAULT_NOT_FOUND(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_PORT_TYPE_FAULT_NOT_FOUND(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_RESULT_IS_IN_OUT_PARAMETER(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.resultIsInOutParameter", arg0);
    }

    /**
     * result is "inout" parameter in operation: {0}
     *
     */
    public static String WSDLMODELER_RESULT_IS_IN_OUT_PARAMETER(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_RESULT_IS_IN_OUT_PARAMETER(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_OPERATION_MORE_THAN_ONE_PART_IN_MESSAGE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.operation.MoreThanOnePartInMessage", arg0);
    }

    /**
     * Ignoring operation "{0}": more than one part bound to body
     *
     */
    public static String WSDLMODELER_WARNING_OPERATION_MORE_THAN_ONE_PART_IN_MESSAGE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_OPERATION_MORE_THAN_ONE_PART_IN_MESSAGE(arg0));
    }

    public static Localizable localizableMIMEMODELER_INVALID_MIME_CONTENT_ERROR_LOADING_JAVA_CLASS() {
        return MESSAGE_FACTORY.getMessage("mimemodeler.invalidMimeContent.errorLoadingJavaClass");
    }

    /**
     * Couldn't find class "{0}" for mime type "{1}"
     *
     */
    public static String MIMEMODELER_INVALID_MIME_CONTENT_ERROR_LOADING_JAVA_CLASS() {
        return LOCALIZER.localize(localizableMIMEMODELER_INVALID_MIME_CONTENT_ERROR_LOADING_JAVA_CLASS());
    }

    public static Localizable localizableWSDLMODELER_INVALID_RPCLITOPERATION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.rpclitoperation", arg0);
    }

    /**
     * Invalid wsdl:operation "{0}": its a rpc-literal operation,  message part must refer to a schema type declaration
     *
     */
    public static String WSDLMODELER_INVALID_RPCLITOPERATION(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_RPCLITOPERATION(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_HEADER_PART_FROM_BODY(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringHeader.partFromBody", arg0);
    }

    /**
     * header part: "{0}" already bound by soapbind:body, illegal to bind the part twice
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_HEADER_PART_FROM_BODY(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_HEADER_PART_FROM_BODY(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_NO_SERVICE_DEFINITIONS_FOUND() {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.noServiceDefinitionsFound");
    }

    /**
     * WSDL document does not define any services
     *
     */
    public static String WSDLMODELER_WARNING_NO_SERVICE_DEFINITIONS_FOUND() {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_NO_SERVICE_DEFINITIONS_FOUND());
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_FAULT_EMPTY_MESSAGE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingFault.emptyMessage", arg0, arg1);
    }

    /**
     * fault "{0}" refers to message "{1}", but the message has no parts
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_FAULT_EMPTY_MESSAGE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_FAULT_EMPTY_MESSAGE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_PARAMETERORDER_PARAMETER(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.parameterorder.parameter", arg0, arg1);
    }

    /**
     * "{0}" specified in the parameterOrder attribute of operation "{1}" is not a valid part of the message.
     *
     */
    public static String WSDLMODELER_INVALID_PARAMETERORDER_PARAMETER(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_PARAMETERORDER_PARAMETER(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_DUPLICATE_FAULT_PART_NAME(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.duplicate.fault.part.name", arg0, arg1, arg2);
    }

    /**
     * ignoring fault "{0}" of operation "{1}", part name "{2}" is not unique
     *
     */
    public static String WSDLMODELER_DUPLICATE_FAULT_PART_NAME(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSDLMODELER_DUPLICATE_FAULT_PART_NAME(arg0, arg1, arg2));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_NON_SOAP_PORT(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringNonSOAPPort", arg0);
    }

    /**
     * ignoring port "{0}": not a standard SOAP port. try running wsimport with -extension switch.
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_NON_SOAP_PORT(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_NON_SOAP_PORT(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOMIZED_OPERATION_NAME(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.operation.javaReservedWordNotAllowed.customizedOperationName", arg0, arg1);
    }

    /**
     * Invalid operation "{0}", can''t generate java method ,customized name "{1}" of the wsdl:operation is a java keyword.
     *
     */
    public static String WSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOMIZED_OPERATION_NAME(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOMIZED_OPERATION_NAME(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_NON_WRAPPER_STYLE(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.operation.javaReservedWordNotAllowed.nonWrapperStyle", arg0, arg1, arg2);
    }

    /**
     * Invalid operation "{0}", can''t generate java method. Parameter: part "{2}" in wsdl:message "{1}", is a java keyword. Use customization to change the parameter name or change the wsdl:part name.
     *
     */
    public static String WSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_NON_WRAPPER_STYLE(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_NON_WRAPPER_STYLE(arg0, arg1, arg2));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_HEADER_CANT_RESOLVE_MESSAGE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringHeader.cant.resolve.message", arg0, arg1);
    }

    /**
     * ignoring header "{0}" of binding operation "{1}": cannot resolve message
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_HEADER_CANT_RESOLVE_MESSAGE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_HEADER_CANT_RESOLVE_MESSAGE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_OPERATION_INPUT_MISSING_SOAP_BODY(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingOperation.inputMissingSoapBody", arg0);
    }

    /**
     * input message of binding operation "{0}" does not have a SOAP body extension
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_OPERATION_INPUT_MISSING_SOAP_BODY(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_OPERATION_INPUT_MISSING_SOAP_BODY(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_PARAMETER_ORDER_INVALID_PARAMETER_ORDER(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.parameterOrder.invalidParameterOrder", arg0);
    }

    /**
     * parameterOrder attribute on operation "{0}" is invalid, ignoring parameterOrder hint
     *
     */
    public static String WSDLMODELER_INVALID_PARAMETER_ORDER_INVALID_PARAMETER_ORDER(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_PARAMETER_ORDER_INVALID_PARAMETER_ORDER(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_NO_OPERATIONS_IN_PORT(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.noOperationsInPort", arg0);
    }

    /**
     * Port "{0}" does not contain any usable operations
     *
     */
    public static String WSDLMODELER_WARNING_NO_OPERATIONS_IN_PORT(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_NO_OPERATIONS_IN_PORT(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_NONCONFORMING_WSDL_IMPORT() {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.nonconforming.wsdl.import");
    }

    /**
     * Non conforming WS-I WSDL used for wsdl:import
     *
     */
    public static String WSDLMODELER_WARNING_NONCONFORMING_WSDL_IMPORT() {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_NONCONFORMING_WSDL_IMPORT());
    }

    public static Localizable localizableMIMEMODELER_INVALID_MIME_CONTENT_MISSING_TYPE_ATTRIBUTE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("mimemodeler.invalidMimeContent.missingTypeAttribute", arg0);
    }

    /**
     * Missing type attribute in mime:content in operation "{0}". part attribute must be present in mime:content declaration.
     *
     */
    public static String MIMEMODELER_INVALID_MIME_CONTENT_MISSING_TYPE_ATTRIBUTE(Object arg0) {
        return LOCALIZER.localize(localizableMIMEMODELER_INVALID_MIME_CONTENT_MISSING_TYPE_ATTRIBUTE(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_OPERATION_INPUT_HEADER_MISSING_NAMESPACE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingOperation.inputHeader.missingNamespace", arg0, arg1);
    }

    /**
     * input header "{1}" of binding operation "{0}" must specify a value for the "namespace" attribute
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_OPERATION_INPUT_HEADER_MISSING_NAMESPACE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_OPERATION_INPUT_HEADER_MISSING_NAMESPACE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_PARAMETER_DIFFERENT_TYPES(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.parameter.differentTypes", arg0, arg1);
    }

    /**
     * parameter "{0}" of operation "{1}" appears with different types in the input and output messages
     *
     */
    public static String WSDLMODELER_INVALID_PARAMETER_DIFFERENT_TYPES(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_PARAMETER_DIFFERENT_TYPES(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_MORE_THAN_ONE_PART_IN_OUTPUT_MESSAGE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.cannotHandleMoreThanOnePartInOutputMessage", arg0);
    }

    /**
     * ignoring operation "{0}": more than one part in output message
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_MORE_THAN_ONE_PART_IN_OUTPUT_MESSAGE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_MORE_THAN_ONE_PART_IN_OUTPUT_MESSAGE(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_NONCONFORMING_WSDL_USE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.nonconforming.wsdl.use", arg0);
    }

    /**
     * Processing WS-I non conforming operation "{0}" with RPC-Style and SOAP-encoded
     *
     */
    public static String WSDLMODELER_WARNING_NONCONFORMING_WSDL_USE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_NONCONFORMING_WSDL_USE(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_DOCUMENT_STYLE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.cannotHandleDocumentStyle", arg0);
    }

    /**
     * ignoring operation "{0}": cannot handle document-style operations
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_DOCUMENT_STYLE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_DOCUMENT_STYLE(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_FAULT_WRONG_SOAP_FAULT_NAME(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingFault.wrongSoapFaultName", arg0, arg1, arg2);
    }

    /**
     * name of soap:fault "{0}" doesn''t match the name of wsdl:fault "{1}" in operation "{2}"
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_FAULT_WRONG_SOAP_FAULT_NAME(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_FAULT_WRONG_SOAP_FAULT_NAME(arg0, arg1, arg2));
    }

    public static Localizable localizableWSDLMODELER_INVALID_OPERATION_MORE_THAN_ONE_PART_IN_MESSAGE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.operation.MoreThanOnePartInMessage", arg0);
    }

    /**
     * operation "{0}": more than one part bound to body
     *
     */
    public static String WSDLMODELER_INVALID_OPERATION_MORE_THAN_ONE_PART_IN_MESSAGE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_OPERATION_MORE_THAN_ONE_PART_IN_MESSAGE(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_NON_SOAP_PORT(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.nonSOAPPort", arg0);
    }

    /**
     * port "{0}": not a standard SOAP port. The generated artifacts may not work with JAX-WS runtime.
     *
     */
    public static String WSDLMODELER_WARNING_NON_SOAP_PORT(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_NON_SOAP_PORT(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_STATE_MODELING_OPERATION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalidState.modelingOperation", arg0);
    }

    /**
     * invalid state while modeling operation: {0}
     *
     */
    public static String WSDLMODELER_INVALID_STATE_MODELING_OPERATION(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_STATE_MODELING_OPERATION(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_EMPTY_INPUT_MESSAGE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.cannotHandleEmptyInputMessage", arg0);
    }

    /**
     * ignoring operation "{0}": input message is empty
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_EMPTY_INPUT_MESSAGE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_EMPTY_INPUT_MESSAGE(arg0));
    }

    public static Localizable localizableWSDLMODELER_INVALID_BINDING_OPERATION_OUTPUT_MISSING_SOAP_BODY(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.bindingOperation.outputMissingSoapBody", arg0);
    }

    /**
     * output message of binding operation "{0}" does not have a SOAP body extension
     *
     */
    public static String WSDLMODELER_INVALID_BINDING_OPERATION_OUTPUT_MISSING_SOAP_BODY(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_BINDING_OPERATION_OUTPUT_MISSING_SOAP_BODY(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_HEADER_FAULT_NOT_FOUND(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringHeaderFault.notFound", arg0, arg1, arg2);
    }

    /**
     * ignoring header fault "{0}", cannot find part "{1}" in binding "{2}"
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_HEADER_FAULT_NOT_FOUND(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_HEADER_FAULT_NOT_FOUND(arg0, arg1, arg2));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_HEADER(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringHeader", arg0, arg1);
    }

    /**
     * ignoring header "{0}" of binding operation "{1}"
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_HEADER(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_HEADER(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_FAULT_CANT_RESOLVE_MESSAGE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.fault.cant.resolve.message", arg0, arg1);
    }

    /**
     * fault message "{0}" in binding operation "{1}" could not be resolved
     *
     */
    public static String WSDLMODELER_INVALID_FAULT_CANT_RESOLVE_MESSAGE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_FAULT_CANT_RESOLVE_MESSAGE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_MIME_PART_NOT_FOUND(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringMimePart.notFound", arg0, arg1);
    }

    /**
     * ignoring mime:part, cannot find part "{0}" referenced by the mime:content in the wsdl:operation "{1}"
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_MIME_PART_NOT_FOUND(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_MIME_PART_NOT_FOUND(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_UNRECOGNIZED_SCHEMA_EXTENSION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringUnrecognizedSchemaExtension", arg0);
    }

    /**
     * ignoring schema element (unsupported version): {0}
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_UNRECOGNIZED_SCHEMA_EXTENSION(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_UNRECOGNIZED_SCHEMA_EXTENSION(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_SOAP_BINDING_MIXED_STYLE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringSOAPBinding.mixedStyle", arg0);
    }

    /**
     * ignoring port "{0}", its not WS-I BP 1.1 compliant: the wsdl binding has mixed style, it must be rpc-literal or document-literal operation. try running wsimport with -extension switch.
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_SOAP_BINDING_MIXED_STYLE(Object arg0) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_SOAP_BINDING_MIXED_STYLE(arg0));
    }

    public static Localizable localizableMIMEMODELER_INVALID_MIME_CONTENT_UNKNOWN_SCHEMA_TYPE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("mimemodeler.invalidMimeContent.unknownSchemaType", arg0, arg1);
    }

    /**
     * Unknown schema type: {1} for mime:content part: {0}
     *
     */
    public static String MIMEMODELER_INVALID_MIME_CONTENT_UNKNOWN_SCHEMA_TYPE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableMIMEMODELER_INVALID_MIME_CONTENT_UNKNOWN_SCHEMA_TYPE(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_FAULT_NOT_ENCODED(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringFault.notEncoded", arg0, arg1);
    }

    /**
     * ignoring literal fault "{0}" of binding operation "{1}"
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_FAULT_NOT_ENCODED(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_FAULT_NOT_ENCODED(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOM_NAME(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringOperation.javaReservedWordNotAllowed.customName", arg0, arg1);
    }

    /**
     * Ignoring operation "{0}", can''t generate java method. Parameter,customized name "{1}" is a java keyword.
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOM_NAME(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOM_NAME(arg0, arg1));
    }

    public static Localizable localizableMIMEMODELER_INVALID_MIME_CONTENT_MISSING_PART_ATTRIBUTE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("mimemodeler.invalidMimeContent.missingPartAttribute", arg0);
    }

    /**
     * Ignoring operation "{0}", missing part attribute in mime:content. part attribute must be present in mime:content declaration.
     *
     */
    public static String MIMEMODELER_INVALID_MIME_CONTENT_MISSING_PART_ATTRIBUTE(Object arg0) {
        return LOCALIZER.localize(localizableMIMEMODELER_INVALID_MIME_CONTENT_MISSING_PART_ATTRIBUTE(arg0));
    }

    public static Localizable localizableWSDLMODELER_WARNING_IGNORING_HEADER_INCONSISTENT_DEFINITION(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.warning.ignoringHeader.inconsistentDefinition", arg0, arg1);
    }

    /**
     * ignoring header "{0}" of operation "{1}": part not found
     *
     */
    public static String WSDLMODELER_WARNING_IGNORING_HEADER_INCONSISTENT_DEFINITION(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_WARNING_IGNORING_HEADER_INCONSISTENT_DEFINITION(arg0, arg1));
    }

    public static Localizable localizableWSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOM_NAME(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("wsdlmodeler.invalid.operation.javaReservedWordNotAllowed.customName", arg0, arg1);
    }

    /**
     * Invalid operation "{0}", can''t generate java method. Parameter,customized name "{1}"  is a java keyword.
     *
     */
    public static String WSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOM_NAME(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOM_NAME(arg0, arg1));
    }

    private static class BundleSupplier
        implements ResourceBundleSupplier
    {


        public ResourceBundle getResourceBundle(Locale locale) {
            return ResourceBundle.getBundle(BUNDLE_NAME, locale);
        }

    }

}
