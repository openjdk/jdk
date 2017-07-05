/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.tools.internal.ws.resources;

import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.internal.ws.util.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
public final class WsdlMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.tools.internal.ws.resources.wsdl");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizablePARSING_ELEMENT_EXPECTED() {
        return messageFactory.getMessage("parsing.elementExpected");
    }

    /**
     * unexpected non-element found
     *
     */
    public static String PARSING_ELEMENT_EXPECTED() {
        return localizer.localize(localizablePARSING_ELEMENT_EXPECTED());
    }

    public static Localizable localizableENTITY_NOT_FOUND_BINDING(Object arg0, Object arg1) {
        return messageFactory.getMessage("entity.notFound.binding", arg0, arg1);
    }

    /**
     * wsdl:binding "{0}" referenced by wsdl:port "{1}", but its not found in the wsdl
     *
     */
    public static String ENTITY_NOT_FOUND_BINDING(Object arg0, Object arg1) {
        return localizer.localize(localizableENTITY_NOT_FOUND_BINDING(arg0, arg1));
    }

    public static Localizable localizablePARSING_PARSE_FAILED() {
        return messageFactory.getMessage("Parsing.ParseFailed");
    }

    /**
     * Failed to parse the WSDL.
     *
     */
    public static String PARSING_PARSE_FAILED() {
        return localizer.localize(localizablePARSING_PARSE_FAILED());
    }

    public static Localizable localizablePARSING_UNABLE_TO_GET_METADATA(Object arg0) {
        return messageFactory.getMessage("parsing.unableToGetMetadata", arg0);
    }

    /**
     * Unable to get Metadata from: {0}
     *
     */
    public static String PARSING_UNABLE_TO_GET_METADATA(Object arg0) {
        return localizer.localize(localizablePARSING_UNABLE_TO_GET_METADATA(arg0));
    }

    public static Localizable localizableVALIDATION_INVALID_PREFIX(Object arg0) {
        return messageFactory.getMessage("validation.invalidPrefix", arg0);
    }

    /**
     * undeclared namespace prefix: "{0}"
     *
     */
    public static String VALIDATION_INVALID_PREFIX(Object arg0) {
        return localizer.localize(localizableVALIDATION_INVALID_PREFIX(arg0));
    }

    public static Localizable localizablePARSING_INVALID_ATTRIBUTE_VALUE(Object arg0, Object arg1) {
        return messageFactory.getMessage("parsing.invalidAttributeValue", arg0, arg1);
    }

    /**
     * invalid value "{1}" for attribute "{0}"
     *
     */
    public static String PARSING_INVALID_ATTRIBUTE_VALUE(Object arg0, Object arg1) {
        return localizer.localize(localizablePARSING_INVALID_ATTRIBUTE_VALUE(arg0, arg1));
    }

    public static Localizable localizableVALIDATION_INVALID_ATTRIBUTE_VALUE(Object arg0, Object arg1) {
        return messageFactory.getMessage("validation.invalidAttributeValue", arg0, arg1);
    }

    /**
     * invalid value "{1}" for attribute "{0}"
     *
     */
    public static String VALIDATION_INVALID_ATTRIBUTE_VALUE(Object arg0, Object arg1) {
        return localizer.localize(localizableVALIDATION_INVALID_ATTRIBUTE_VALUE(arg0, arg1));
    }

    public static Localizable localizableVALIDATION_INVALID_RANGE(Object arg0, Object arg1) {
        return messageFactory.getMessage("validation.invalidRange", arg0, arg1);
    }

    /**
     * invalid range found (min: {0}, max: {1})
     *
     */
    public static String VALIDATION_INVALID_RANGE(Object arg0, Object arg1) {
        return localizer.localize(localizableVALIDATION_INVALID_RANGE(arg0, arg1));
    }

    public static Localizable localizablePARSING_INVALID_TAG(Object arg0, Object arg1) {
        return messageFactory.getMessage("parsing.invalidTag", arg0, arg1);
    }

    /**
     * expected element "{1}", found "{0}"
     *
     */
    public static String PARSING_INVALID_TAG(Object arg0, Object arg1) {
        return localizer.localize(localizablePARSING_INVALID_TAG(arg0, arg1));
    }

    public static Localizable localizableENTITY_NOT_FOUND_PORT_TYPE(Object arg0, Object arg1) {
        return messageFactory.getMessage("entity.notFound.portType", arg0, arg1);
    }

    /**
     * wsdl:portType "{0}" referenced by wsdl:binding "{1}", but its not found in the wsdl
     *
     */
    public static String ENTITY_NOT_FOUND_PORT_TYPE(Object arg0, Object arg1) {
        return localizer.localize(localizableENTITY_NOT_FOUND_PORT_TYPE(arg0, arg1));
    }

    public static Localizable localizablePARSING_MISSING_REQUIRED_ATTRIBUTE(Object arg0, Object arg1) {
        return messageFactory.getMessage("parsing.missingRequiredAttribute", arg0, arg1);
    }

    /**
     * missing required attribute "{1}" of element "{0}"
     *
     */
    public static String PARSING_MISSING_REQUIRED_ATTRIBUTE(Object arg0, Object arg1) {
        return localizer.localize(localizablePARSING_MISSING_REQUIRED_ATTRIBUTE(arg0, arg1));
    }

    public static Localizable localizablePARSING_INVALID_ELEMENT(Object arg0, Object arg1) {
        return messageFactory.getMessage("parsing.invalidElement", arg0, arg1);
    }

    /**
     * invalid element: "{0}" (in namespace "{1}")
     *
     */
    public static String PARSING_INVALID_ELEMENT(Object arg0, Object arg1) {
        return localizer.localize(localizablePARSING_INVALID_ELEMENT(arg0, arg1));
    }

    public static Localizable localizableVALIDATION_INVALID_ELEMENT(Object arg0) {
        return messageFactory.getMessage("validation.invalidElement", arg0);
    }

    /**
     * invalid element: "{0}"
     *
     */
    public static String VALIDATION_INVALID_ELEMENT(Object arg0) {
        return localizer.localize(localizableVALIDATION_INVALID_ELEMENT(arg0));
    }

    public static Localizable localizableINTERNALIZER_TWO_VERSION_ATTRIBUTES() {
        return messageFactory.getMessage("Internalizer.TwoVersionAttributes");
    }

    /**
     * Both jaxws:version and version are present
     *
     */
    public static String INTERNALIZER_TWO_VERSION_ATTRIBUTES() {
        return localizer.localize(localizableINTERNALIZER_TWO_VERSION_ATTRIBUTES());
    }

    public static Localizable localizableVALIDATION_DUPLICATE_PART_NAME(Object arg0, Object arg1) {
        return messageFactory.getMessage("validation.duplicatePartName", arg0, arg1);
    }

    /**
     * Invalid WSDL, duplicate parts in a wsdl:message is not allowed.
     * wsdl:message {0} has duplicated part name: "{1}"
     *
     */
    public static String VALIDATION_DUPLICATE_PART_NAME(Object arg0, Object arg1) {
        return localizer.localize(localizableVALIDATION_DUPLICATE_PART_NAME(arg0, arg1));
    }

    public static Localizable localizablePARSING_INVALID_WSDL_ELEMENT(Object arg0) {
        return messageFactory.getMessage("parsing.invalidWsdlElement", arg0);
    }

    /**
     * invalid WSDL element: "{0}"
     *
     */
    public static String PARSING_INVALID_WSDL_ELEMENT(Object arg0) {
        return localizer.localize(localizablePARSING_INVALID_WSDL_ELEMENT(arg0));
    }

    public static Localizable localizableVALIDATION_INVALID_COMPLEX_TYPE_IN_ELEMENT(Object arg0, Object arg1) {
        return messageFactory.getMessage("validation.invalidComplexTypeInElement", arg0, arg1);
    }

    /**
     * invalid element: "{1}", has named complexType: "{0}"
     *
     */
    public static String VALIDATION_INVALID_COMPLEX_TYPE_IN_ELEMENT(Object arg0, Object arg1) {
        return localizer.localize(localizableVALIDATION_INVALID_COMPLEX_TYPE_IN_ELEMENT(arg0, arg1));
    }

    public static Localizable localizablePARSING_NON_WHITESPACE_TEXT_FOUND(Object arg0) {
        return messageFactory.getMessage("parsing.nonWhitespaceTextFound", arg0);
    }

    /**
     * found unexpected non whitespace text: "{0}"
     *
     */
    public static String PARSING_NON_WHITESPACE_TEXT_FOUND(Object arg0) {
        return localizer.localize(localizablePARSING_NON_WHITESPACE_TEXT_FOUND(arg0));
    }

    public static Localizable localizableINTERNALIZER_TARGET_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("internalizer.targetNotFound", arg0);
    }

    /**
     * No target found for the wsdlLocation: {0}
     *
     */
    public static String INTERNALIZER_TARGET_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableINTERNALIZER_TARGET_NOT_FOUND(arg0));
    }

    public static Localizable localizableVALIDATION_NOT_SIMPLE_TYPE(Object arg0) {
        return messageFactory.getMessage("validation.notSimpleType", arg0);
    }

    /**
     * not a simple type: "{0}"
     *
     */
    public static String VALIDATION_NOT_SIMPLE_TYPE(Object arg0) {
        return localizer.localize(localizableVALIDATION_NOT_SIMPLE_TYPE(arg0));
    }

    public static Localizable localizablePARSING_SAX_EXCEPTION_WITH_SYSTEM_ID(Object arg0) {
        return messageFactory.getMessage("parsing.saxExceptionWithSystemId", arg0);
    }

    /**
     * invalid WSDL file! failed to parse document at "{0}"
     *
     */
    public static String PARSING_SAX_EXCEPTION_WITH_SYSTEM_ID(Object arg0) {
        return localizer.localize(localizablePARSING_SAX_EXCEPTION_WITH_SYSTEM_ID(arg0));
    }

    public static Localizable localizablePARSING_REQUIRED_EXTENSIBILITY_ELEMENT(Object arg0, Object arg1) {
        return messageFactory.getMessage("parsing.requiredExtensibilityElement", arg0, arg1);
    }

    /**
     * unknown required extensibility element "{0}" (in namespace "{1}")
     *
     */
    public static String PARSING_REQUIRED_EXTENSIBILITY_ELEMENT(Object arg0, Object arg1) {
        return localizer.localize(localizablePARSING_REQUIRED_EXTENSIBILITY_ELEMENT(arg0, arg1));
    }

    public static Localizable localizableENTITY_NOT_FOUND_BY_ID(Object arg0) {
        return messageFactory.getMessage("entity.notFoundByID", arg0);
    }

    /**
     * invalid entity id: "{0}"
     *
     */
    public static String ENTITY_NOT_FOUND_BY_ID(Object arg0) {
        return localizer.localize(localizableENTITY_NOT_FOUND_BY_ID(arg0));
    }

    public static Localizable localizableVALIDATION_EXCLUSIVE_ATTRIBUTES(Object arg0, Object arg1) {
        return messageFactory.getMessage("validation.exclusiveAttributes", arg0, arg1);
    }

    /**
     * exclusive attributes: "{0}", "{1}"
     *
     */
    public static String VALIDATION_EXCLUSIVE_ATTRIBUTES(Object arg0, Object arg1) {
        return localizer.localize(localizableVALIDATION_EXCLUSIVE_ATTRIBUTES(arg0, arg1));
    }

    public static Localizable localizableVALIDATION_MISSING_REQUIRED_SUB_ENTITY(Object arg0, Object arg1) {
        return messageFactory.getMessage("validation.missingRequiredSubEntity", arg0, arg1);
    }

    /**
     * missing required sub-entity "{0}" of element "{1}"
     *
     */
    public static String VALIDATION_MISSING_REQUIRED_SUB_ENTITY(Object arg0, Object arg1) {
        return localizer.localize(localizableVALIDATION_MISSING_REQUIRED_SUB_ENTITY(arg0, arg1));
    }

    public static Localizable localizableINTERNALIZER_INCORRECT_VERSION() {
        return messageFactory.getMessage("Internalizer.IncorrectVersion");
    }

    /**
     * JAXWS version attribute must be "2.0"
     *
     */
    public static String INTERNALIZER_INCORRECT_VERSION() {
        return localizer.localize(localizableINTERNALIZER_INCORRECT_VERSION());
    }

    public static Localizable localizableLOCALIZED_ERROR(Object arg0) {
        return messageFactory.getMessage("localized.error", arg0);
    }

    /**
     * {0}
     *
     */
    public static String LOCALIZED_ERROR(Object arg0) {
        return localizer.localize(localizableLOCALIZED_ERROR(arg0));
    }

    public static Localizable localizableENTITY_DUPLICATE_WITH_TYPE(Object arg0, Object arg1) {
        return messageFactory.getMessage("entity.duplicateWithType", arg0, arg1);
    }

    /**
     * duplicate "{0}" entity: "{1}"
     *
     */
    public static String ENTITY_DUPLICATE_WITH_TYPE(Object arg0, Object arg1) {
        return localizer.localize(localizableENTITY_DUPLICATE_WITH_TYPE(arg0, arg1));
    }

    public static Localizable localizablePARSING_ONLY_ONE_OF_ELEMENT_OR_TYPE_REQUIRED(Object arg0) {
        return messageFactory.getMessage("parsing.onlyOneOfElementOrTypeRequired", arg0);
    }

    /**
     * only one of the "element" or "type" attributes is allowed in part "{0}"
     *
     */
    public static String PARSING_ONLY_ONE_OF_ELEMENT_OR_TYPE_REQUIRED(Object arg0) {
        return localizer.localize(localizablePARSING_ONLY_ONE_OF_ELEMENT_OR_TYPE_REQUIRED(arg0));
    }

    public static Localizable localizablePARSING_INCORRECT_ROOT_ELEMENT(Object arg0, Object arg1, Object arg2, Object arg3) {
        return messageFactory.getMessage("parsing.incorrectRootElement", arg0, arg1, arg2, arg3);
    }

    /**
     * expected root element "{2}" (in namespace "{3}"), found element "{0}" (in namespace "{1}")
     *
     */
    public static String PARSING_INCORRECT_ROOT_ELEMENT(Object arg0, Object arg1, Object arg2, Object arg3) {
        return localizer.localize(localizablePARSING_INCORRECT_ROOT_ELEMENT(arg0, arg1, arg2, arg3));
    }

    public static Localizable localizableVALIDATION_MISSING_REQUIRED_ATTRIBUTE(Object arg0, Object arg1) {
        return messageFactory.getMessage("validation.missingRequiredAttribute", arg0, arg1);
    }

    /**
     * missing required attribute "{0}" of element "{1}"
     *
     */
    public static String VALIDATION_MISSING_REQUIRED_ATTRIBUTE(Object arg0, Object arg1) {
        return localizer.localize(localizableVALIDATION_MISSING_REQUIRED_ATTRIBUTE(arg0, arg1));
    }

    public static Localizable localizableINTERNALIZER_X_PATH_EVAULATES_TO_TOO_MANY_TARGETS(Object arg0, Object arg1) {
        return messageFactory.getMessage("internalizer.XPathEvaulatesToTooManyTargets", arg0, arg1);
    }

    /**
     * XPath evaluation of "{0}" results in too many ({1}) target nodes
     *
     */
    public static String INTERNALIZER_X_PATH_EVAULATES_TO_TOO_MANY_TARGETS(Object arg0, Object arg1) {
        return localizer.localize(localizableINTERNALIZER_X_PATH_EVAULATES_TO_TOO_MANY_TARGETS(arg0, arg1));
    }

    public static Localizable localizablePARSING_IO_EXCEPTION(Object arg0) {
        return messageFactory.getMessage("parsing.ioException", arg0);
    }

    /**
     * parsing failed: {0}
     *
     */
    public static String PARSING_IO_EXCEPTION(Object arg0) {
        return localizer.localize(localizablePARSING_IO_EXCEPTION(arg0));
    }

    public static Localizable localizablePARSER_NOT_A_BINDING_FILE(Object arg0, Object arg1) {
        return messageFactory.getMessage("Parser.NotABindingFile", arg0, arg1);
    }

    /**
     * not an external binding file. The root element must be '{'http://java.sun.com/xml/ns/jaxws'}'bindings but it is '{'{0}'}'{1}
     *
     */
    public static String PARSER_NOT_A_BINDING_FILE(Object arg0, Object arg1) {
        return localizer.localize(localizablePARSER_NOT_A_BINDING_FILE(arg0, arg1));
    }

    public static Localizable localizablePARSING_UNKNOWN_NAMESPACE_PREFIX(Object arg0) {
        return messageFactory.getMessage("parsing.unknownNamespacePrefix", arg0);
    }

    /**
     * undeclared namespace prefix: "{0}"
     *
     */
    public static String PARSING_UNKNOWN_NAMESPACE_PREFIX(Object arg0) {
        return localizer.localize(localizablePARSING_UNKNOWN_NAMESPACE_PREFIX(arg0));
    }

    public static Localizable localizablePARSING_FACTORY_CONFIG_EXCEPTION(Object arg0) {
        return messageFactory.getMessage("parsing.factoryConfigException", arg0);
    }

    /**
     * invalid WSDL file! parsing failed: {0}
     *
     */
    public static String PARSING_FACTORY_CONFIG_EXCEPTION(Object arg0) {
        return localizer.localize(localizablePARSING_FACTORY_CONFIG_EXCEPTION(arg0));
    }

    public static Localizable localizableVALIDATION_MISSING_REQUIRED_PROPERTY(Object arg0, Object arg1) {
        return messageFactory.getMessage("validation.missingRequiredProperty", arg0, arg1);
    }

    /**
     * missing required property "{0}" of element "{1}"
     *
     */
    public static String VALIDATION_MISSING_REQUIRED_PROPERTY(Object arg0, Object arg1) {
        return localizer.localize(localizableVALIDATION_MISSING_REQUIRED_PROPERTY(arg0, arg1));
    }

    public static Localizable localizablePARSING_INVALID_OPERATION_STYLE(Object arg0) {
        return messageFactory.getMessage("parsing.invalidOperationStyle", arg0);
    }

    /**
     * operation "{0}" has an invalid style
     *
     */
    public static String PARSING_INVALID_OPERATION_STYLE(Object arg0) {
        return localizer.localize(localizablePARSING_INVALID_OPERATION_STYLE(arg0));
    }

    public static Localizable localizableINTERNALIZER_X_PATH_EVALUATION_ERROR(Object arg0) {
        return messageFactory.getMessage("internalizer.XPathEvaluationError", arg0);
    }

    /**
     * XPath error: {0}
     *
     */
    public static String INTERNALIZER_X_PATH_EVALUATION_ERROR(Object arg0) {
        return localizer.localize(localizableINTERNALIZER_X_PATH_EVALUATION_ERROR(arg0));
    }

    public static Localizable localizableVALIDATION_INVALID_TOKEN(Object arg0) {
        return messageFactory.getMessage("validation.invalidToken", arg0);
    }

    /**
     * invalid token "{0}"
     *
     */
    public static String VALIDATION_INVALID_TOKEN(Object arg0) {
        return localizer.localize(localizableVALIDATION_INVALID_TOKEN(arg0));
    }

    public static Localizable localizableVALIDATION_INVALID_SUB_ENTITY(Object arg0, Object arg1) {
        return messageFactory.getMessage("validation.invalidSubEntity", arg0, arg1);
    }

    /**
     * invalid sub-element "{0}" of element "{1}"
     *
     */
    public static String VALIDATION_INVALID_SUB_ENTITY(Object arg0, Object arg1) {
        return localizer.localize(localizableVALIDATION_INVALID_SUB_ENTITY(arg0, arg1));
    }

    public static Localizable localizableVALIDATION_SHOULD_NOT_HAPPEN(Object arg0) {
        return messageFactory.getMessage("validation.shouldNotHappen", arg0);
    }

    /**
     * internal error ("{0}")
     *
     */
    public static String VALIDATION_SHOULD_NOT_HAPPEN(Object arg0) {
        return localizer.localize(localizableVALIDATION_SHOULD_NOT_HAPPEN(arg0));
    }

    public static Localizable localizableABSTRACT_REFERENCE_FINDER_IMPL_UNABLE_TO_PARSE(Object arg0, Object arg1) {
        return messageFactory.getMessage("AbstractReferenceFinderImpl.UnableToParse", arg0, arg1);
    }

    /**
     * Unable to parse "{0}" : {1}
     *
     */
    public static String ABSTRACT_REFERENCE_FINDER_IMPL_UNABLE_TO_PARSE(Object arg0, Object arg1) {
        return localizer.localize(localizableABSTRACT_REFERENCE_FINDER_IMPL_UNABLE_TO_PARSE(arg0, arg1));
    }

    public static Localizable localizableWARNING_FAULT_EMPTY_ACTION(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("warning.faultEmptyAction", arg0, arg1, arg2);
    }

    /**
     * ignoring empty Action in "{0}" {1} element of "{2}" operation, using default instead
     *
     */
    public static String WARNING_FAULT_EMPTY_ACTION(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWARNING_FAULT_EMPTY_ACTION(arg0, arg1, arg2));
    }

    public static Localizable localizablePARSING_INVALID_EXTENSION_ELEMENT(Object arg0, Object arg1) {
        return messageFactory.getMessage("parsing.invalidExtensionElement", arg0, arg1);
    }

    /**
     * invalid extension element: "{0}" (in namespace "{1}")
     *
     */
    public static String PARSING_INVALID_EXTENSION_ELEMENT(Object arg0, Object arg1) {
        return localizer.localize(localizablePARSING_INVALID_EXTENSION_ELEMENT(arg0, arg1));
    }

    public static Localizable localizableINTERNALIZER_X_PATH_EVALUATES_TO_NON_ELEMENT(Object arg0) {
        return messageFactory.getMessage("internalizer.XPathEvaluatesToNonElement", arg0);
    }

    /**
     * XPath evaluation of "{0}" needs to result in an element.
     *
     */
    public static String INTERNALIZER_X_PATH_EVALUATES_TO_NON_ELEMENT(Object arg0) {
        return localizer.localize(localizableINTERNALIZER_X_PATH_EVALUATES_TO_NON_ELEMENT(arg0));
    }

    public static Localizable localizableINTERNALIZER_X_PATH_EVALUATES_TO_NO_TARGET(Object arg0) {
        return messageFactory.getMessage("internalizer.XPathEvaluatesToNoTarget", arg0);
    }

    /**
     * XPath evaluation of "{0}" results in empty target node
     *
     */
    public static String INTERNALIZER_X_PATH_EVALUATES_TO_NO_TARGET(Object arg0) {
        return localizer.localize(localizableINTERNALIZER_X_PATH_EVALUATES_TO_NO_TARGET(arg0));
    }

    public static Localizable localizablePARSING_SAX_EXCEPTION(Object arg0) {
        return messageFactory.getMessage("parsing.saxException", arg0);
    }

    /**
     * invalid WSDL file! parsing failed: {0}
     *
     */
    public static String PARSING_SAX_EXCEPTION(Object arg0) {
        return localizer.localize(localizablePARSING_SAX_EXCEPTION(arg0));
    }

    public static Localizable localizableINVALID_CUSTOMIZATION_NAMESPACE(Object arg0) {
        return messageFactory.getMessage("invalid.customization.namespace", arg0);
    }

    /**
     * Ignoring customization: "{0}", it has no namespace. It must belong to the customization namespace.
     *
     */
    public static String INVALID_CUSTOMIZATION_NAMESPACE(Object arg0) {
        return localizer.localize(localizableINVALID_CUSTOMIZATION_NAMESPACE(arg0));
    }

    public static Localizable localizableVALIDATION_INVALID_ATTRIBUTE(Object arg0, Object arg1) {
        return messageFactory.getMessage("validation.invalidAttribute", arg0, arg1);
    }

    /**
     * invalid attribute "{0}" of element "{1}"
     *
     */
    public static String VALIDATION_INVALID_ATTRIBUTE(Object arg0, Object arg1) {
        return localizer.localize(localizableVALIDATION_INVALID_ATTRIBUTE(arg0, arg1));
    }

    public static Localizable localizablePARSING_PARSER_CONFIG_EXCEPTION(Object arg0) {
        return messageFactory.getMessage("parsing.parserConfigException", arg0);
    }

    /**
     * invalid WSDL file! parsing failed: {0}
     *
     */
    public static String PARSING_PARSER_CONFIG_EXCEPTION(Object arg0) {
        return localizer.localize(localizablePARSING_PARSER_CONFIG_EXCEPTION(arg0));
    }

    public static Localizable localizablePARSING_ONLY_ONE_TYPES_ALLOWED(Object arg0) {
        return messageFactory.getMessage("parsing.onlyOneTypesAllowed", arg0);
    }

    /**
     * only one "types" element allowed in "{0}"
     *
     */
    public static String PARSING_ONLY_ONE_TYPES_ALLOWED(Object arg0) {
        return localizer.localize(localizablePARSING_ONLY_ONE_TYPES_ALLOWED(arg0));
    }

    public static Localizable localizablePARSING_INVALID_URI(Object arg0) {
        return messageFactory.getMessage("parsing.invalidURI", arg0);
    }

    /**
     * invalid URI: {0}
     *
     */
    public static String PARSING_INVALID_URI(Object arg0) {
        return localizer.localize(localizablePARSING_INVALID_URI(arg0));
    }

    public static Localizable localizableVALIDATION_INCORRECT_TARGET_NAMESPACE(Object arg0, Object arg1) {
        return messageFactory.getMessage("validation.incorrectTargetNamespace", arg0, arg1);
    }

    /**
     * target namespace is incorrect (expected: {1}, found: {0})
     *
     */
    public static String VALIDATION_INCORRECT_TARGET_NAMESPACE(Object arg0, Object arg1) {
        return localizer.localize(localizableVALIDATION_INCORRECT_TARGET_NAMESPACE(arg0, arg1));
    }

    public static Localizable localizableENTITY_NOT_FOUND_BY_Q_NAME(Object arg0, Object arg1) {
        return messageFactory.getMessage("entity.notFoundByQName", arg0, arg1);
    }

    /**
     * invalid entity name: "{0}" (in namespace: "{1}")
     *
     */
    public static String ENTITY_NOT_FOUND_BY_Q_NAME(Object arg0, Object arg1) {
        return localizer.localize(localizableENTITY_NOT_FOUND_BY_Q_NAME(arg0, arg1));
    }

    public static Localizable localizableINVALID_WSDL(Object arg0) {
        return messageFactory.getMessage("invalid.wsdl", arg0);
    }

    /**
     * "{0} does not look like a WSDL document, retrying with MEX..."
     *
     */
    public static String INVALID_WSDL(Object arg0) {
        return localizer.localize(localizableINVALID_WSDL(arg0));
    }

    public static Localizable localizableVALIDATION_UNSUPPORTED_SCHEMA_FEATURE(Object arg0) {
        return messageFactory.getMessage("validation.unsupportedSchemaFeature", arg0);
    }

    /**
     * unsupported XML Schema feature: "{0}"
     *
     */
    public static String VALIDATION_UNSUPPORTED_SCHEMA_FEATURE(Object arg0) {
        return localizer.localize(localizableVALIDATION_UNSUPPORTED_SCHEMA_FEATURE(arg0));
    }

    public static Localizable localizablePARSING_UNKNOWN_IMPORTED_DOCUMENT_TYPE(Object arg0) {
        return messageFactory.getMessage("parsing.unknownImportedDocumentType", arg0);
    }

    /**
     * imported document is of unknown type: {0}
     *
     */
    public static String PARSING_UNKNOWN_IMPORTED_DOCUMENT_TYPE(Object arg0) {
        return localizer.localize(localizablePARSING_UNKNOWN_IMPORTED_DOCUMENT_TYPE(arg0));
    }

    public static Localizable localizablePARSING_IO_EXCEPTION_WITH_SYSTEM_ID(Object arg0) {
        return messageFactory.getMessage("parsing.ioExceptionWithSystemId", arg0);
    }

    /**
     * failed to parse document at "{0}"
     *
     */
    public static String PARSING_IO_EXCEPTION_WITH_SYSTEM_ID(Object arg0) {
        return localizer.localize(localizablePARSING_IO_EXCEPTION_WITH_SYSTEM_ID(arg0));
    }

    public static Localizable localizableVALIDATION_AMBIGUOUS_NAME(Object arg0) {
        return messageFactory.getMessage("validation.ambiguousName", arg0);
    }

    /**
     * ambiguous operation name: "{0}"
     *
     */
    public static String VALIDATION_AMBIGUOUS_NAME(Object arg0) {
        return localizer.localize(localizableVALIDATION_AMBIGUOUS_NAME(arg0));
    }

    public static Localizable localizablePARSING_WSDL_NOT_DEFAULT_NAMESPACE(Object arg0) {
        return messageFactory.getMessage("parsing.wsdlNotDefaultNamespace", arg0);
    }

    /**
     * default namespace must be "{0}"
     *
     */
    public static String PARSING_WSDL_NOT_DEFAULT_NAMESPACE(Object arg0) {
        return localizer.localize(localizablePARSING_WSDL_NOT_DEFAULT_NAMESPACE(arg0));
    }

    public static Localizable localizableVALIDATION_DUPLICATED_ELEMENT(Object arg0) {
        return messageFactory.getMessage("validation.duplicatedElement", arg0);
    }

    /**
     * duplicated element: "{0}"
     *
     */
    public static String VALIDATION_DUPLICATED_ELEMENT(Object arg0) {
        return localizer.localize(localizableVALIDATION_DUPLICATED_ELEMENT(arg0));
    }

    public static Localizable localizableINTERNALIZER_TARGET_NOT_AN_ELEMENT() {
        return messageFactory.getMessage("internalizer.targetNotAnElement");
    }

    /**
     * Target node is not an element
     *
     */
    public static String INTERNALIZER_TARGET_NOT_AN_ELEMENT() {
        return localizer.localize(localizableINTERNALIZER_TARGET_NOT_AN_ELEMENT());
    }

    public static Localizable localizableWARNING_INPUT_OUTPUT_EMPTY_ACTION(Object arg0, Object arg1) {
        return messageFactory.getMessage("warning.inputOutputEmptyAction", arg0, arg1);
    }

    /**
     * ignoring empty Action in {0} element of "{1}" operation, using default instead
     *
     */
    public static String WARNING_INPUT_OUTPUT_EMPTY_ACTION(Object arg0, Object arg1) {
        return localizer.localize(localizableWARNING_INPUT_OUTPUT_EMPTY_ACTION(arg0, arg1));
    }

    public static Localizable localizablePARSING_INVALID_TAG_NS(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        return messageFactory.getMessage("parsing.invalidTagNS", arg0, arg1, arg2, arg3, arg4);
    }

    /**
     * Invalid WSDL at {4}: expected element "{2}" (in namespace "{3}"), found element "{0}" (in namespace "{1}")
     *
     */
    public static String PARSING_INVALID_TAG_NS(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        return localizer.localize(localizablePARSING_INVALID_TAG_NS(arg0, arg1, arg2, arg3, arg4));
    }

    public static Localizable localizableINVALID_WSDL_WITH_DOOC(Object arg0, Object arg1) {
        return messageFactory.getMessage("invalid.wsdl.with.dooc", arg0, arg1);
    }

    /**
     * "Not a WSDL document: {0}, it gives "{1}", retrying with MEX..."
     *
     */
    public static String INVALID_WSDL_WITH_DOOC(Object arg0, Object arg1) {
        return localizer.localize(localizableINVALID_WSDL_WITH_DOOC(arg0, arg1));
    }

    public static Localizable localizablePARSING_NOT_AWSDL(Object arg0) {
        return messageFactory.getMessage("Parsing.NotAWSDL", arg0);
    }

    /**
     * Failed to get WSDL components, probably {0} is not a valid WSDL file.
     *
     */
    public static String PARSING_NOT_AWSDL(Object arg0) {
        return localizer.localize(localizablePARSING_NOT_AWSDL(arg0));
    }

    public static Localizable localizableENTITY_DUPLICATE(Object arg0) {
        return messageFactory.getMessage("entity.duplicate", arg0);
    }

    /**
     * duplicate entity: "{0}"
     *
     */
    public static String ENTITY_DUPLICATE(Object arg0) {
        return localizer.localize(localizableENTITY_DUPLICATE(arg0));
    }

    public static Localizable localizableWARNING_WSI_R_2004() {
        return messageFactory.getMessage("warning.wsi.r2004");
    }

    /**
     * Not a WSI-BP compliant WSDL (R2001, R2004). xsd:import must not import XML Schema definition emmbedded inline within WSDLDocument.
     *
     */
    public static String WARNING_WSI_R_2004() {
        return localizer.localize(localizableWARNING_WSI_R_2004());
    }

    public static Localizable localizableWARNING_WSI_R_2003() {
        return messageFactory.getMessage("warning.wsi.r2003");
    }

    /**
     * Not a WSI-BP compliant WSDL (R2003). xsd:import must only be used inside xsd:schema element.
     *
     */
    public static String WARNING_WSI_R_2003() {
        return localizer.localize(localizableWARNING_WSI_R_2003());
    }

    public static Localizable localizableWARNING_WSI_R_2002(Object arg0, Object arg1) {
        return messageFactory.getMessage("warning.wsi.r2002", arg0, arg1);
    }

    /**
     * Not a WSI-BP compliant WSDL (R2002). wsdl:import must not be used to import XML Schema embedded in the WSDL document. Expected wsdl namesapce: {0}, found: {1}
     *
     */
    public static String WARNING_WSI_R_2002(Object arg0, Object arg1) {
        return localizer.localize(localizableWARNING_WSI_R_2002(arg0, arg1));
    }

    public static Localizable localizablePARSING_ELEMENT_OR_TYPE_REQUIRED(Object arg0) {
        return messageFactory.getMessage("parsing.elementOrTypeRequired", arg0);
    }

    /**
     * warning: part {0} is ignored, either the "element" or the "type" attribute is required in part "{0}"
     *
     */
    public static String PARSING_ELEMENT_OR_TYPE_REQUIRED(Object arg0) {
        return localizer.localize(localizablePARSING_ELEMENT_OR_TYPE_REQUIRED(arg0));
    }

    public static Localizable localizableWARNING_WSI_R_2001(Object arg0) {
        return messageFactory.getMessage("warning.wsi.r2001", arg0);
    }

    /**
     * Not a WSI-BP compliant WSDL (R2001, R2002). wsdl:import must only import WSDL document. Its trying to import: "{0}"
     *
     */
    public static String WARNING_WSI_R_2001(Object arg0) {
        return localizer.localize(localizableWARNING_WSI_R_2001(arg0));
    }

    public static Localizable localizableVALIDATION_INVALID_SIMPLE_TYPE_IN_ELEMENT(Object arg0, Object arg1) {
        return messageFactory.getMessage("validation.invalidSimpleTypeInElement", arg0, arg1);
    }

    /**
     * invalid element: "{1}", has named simpleType: "{0}"
     *
     */
    public static String VALIDATION_INVALID_SIMPLE_TYPE_IN_ELEMENT(Object arg0, Object arg1) {
        return localizer.localize(localizableVALIDATION_INVALID_SIMPLE_TYPE_IN_ELEMENT(arg0, arg1));
    }

    public static Localizable localizablePARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(Object arg0) {
        return messageFactory.getMessage("parsing.onlyOneDocumentationAllowed", arg0);
    }

    /**
     * only one "documentation" element allowed in "{0}"
     *
     */
    public static String PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(Object arg0) {
        return localizer.localize(localizablePARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(arg0));
    }

    public static Localizable localizableINTERNALIZER_VERSION_NOT_PRESENT() {
        return messageFactory.getMessage("Internalizer.VersionNotPresent");
    }

    /**
     * JAXWS version attribute must be present
     *
     */
    public static String INTERNALIZER_VERSION_NOT_PRESENT() {
        return localizer.localize(localizableINTERNALIZER_VERSION_NOT_PRESENT());
    }

    public static Localizable localizablePARSING_TOO_MANY_ELEMENTS(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("parsing.tooManyElements", arg0, arg1, arg2);
    }

    /**
     * too many "{0}" elements under "{1}" element "{2}"
     *
     */
    public static String PARSING_TOO_MANY_ELEMENTS(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizablePARSING_TOO_MANY_ELEMENTS(arg0, arg1, arg2));
    }

    public static Localizable localizableINTERNALIZER_INCORRECT_SCHEMA_REFERENCE(Object arg0, Object arg1) {
        return messageFactory.getMessage("Internalizer.IncorrectSchemaReference", arg0, arg1);
    }

    /**
     * "{0}" is not a part of this compilation. Is this a mistake for "{1}"?
     *
     */
    public static String INTERNALIZER_INCORRECT_SCHEMA_REFERENCE(Object arg0, Object arg1) {
        return localizer.localize(localizableINTERNALIZER_INCORRECT_SCHEMA_REFERENCE(arg0, arg1));
    }

}
