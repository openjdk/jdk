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
public final class WsdlMessages {

    private final static String BUNDLE_NAME = "com.sun.tools.internal.ws.resources.wsdl";
    private final static LocalizableMessageFactory MESSAGE_FACTORY = new LocalizableMessageFactory(BUNDLE_NAME, new WsdlMessages.BundleSupplier());
    private final static Localizer LOCALIZER = new Localizer();

    public static Localizable localizablePARSING_NOT_AWSDL(Object arg0) {
        return MESSAGE_FACTORY.getMessage("Parsing.NotAWSDL", arg0);
    }

    /**
     * Failed to get WSDL components, probably {0} is not a valid WSDL file.
     *
     */
    public static String PARSING_NOT_AWSDL(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_NOT_AWSDL(arg0));
    }

    public static Localizable localizablePARSER_NOT_A_BINDING_FILE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("Parser.NotABindingFile", arg0, arg1);
    }

    /**
     *  not an external binding file. The root element must be '{'http://java.sun.com/xml/ns/jaxws'}'bindings but it is '{'{0}'}'{1}
     *
     */
    public static String PARSER_NOT_A_BINDING_FILE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizablePARSER_NOT_A_BINDING_FILE(arg0, arg1));
    }

    public static Localizable localizablePARSING_UNKNOWN_EXTENSIBILITY_ELEMENT_OR_ATTRIBUTE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("parsing.unknownExtensibilityElementOrAttribute", arg0, arg1);
    }

    /**
     * unknown extensibility element or attribute "{0}" (in namespace "{1}")
     *
     */
    public static String PARSING_UNKNOWN_EXTENSIBILITY_ELEMENT_OR_ATTRIBUTE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizablePARSING_UNKNOWN_EXTENSIBILITY_ELEMENT_OR_ATTRIBUTE(arg0, arg1));
    }

    public static Localizable localizablePARSING_UNABLE_TO_GET_METADATA(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("parsing.unableToGetMetadata", arg0, arg1);
    }

    /**
     * {0}
     *
     * {1}
     *
     */
    public static String PARSING_UNABLE_TO_GET_METADATA(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizablePARSING_UNABLE_TO_GET_METADATA(arg0, arg1));
    }

    public static Localizable localizablePARSING_ONLY_ONE_TYPES_ALLOWED(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.onlyOneTypesAllowed", arg0);
    }

    /**
     * only one "types" element allowed in "{0}"
     *
     */
    public static String PARSING_ONLY_ONE_TYPES_ALLOWED(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_ONLY_ONE_TYPES_ALLOWED(arg0));
    }

    public static Localizable localizableVALIDATION_SHOULD_NOT_HAPPEN(Object arg0) {
        return MESSAGE_FACTORY.getMessage("validation.shouldNotHappen", arg0);
    }

    /**
     * internal error ("{0}")
     *
     */
    public static String VALIDATION_SHOULD_NOT_HAPPEN(Object arg0) {
        return LOCALIZER.localize(localizableVALIDATION_SHOULD_NOT_HAPPEN(arg0));
    }

    public static Localizable localizableWARNING_WSI_R_2003() {
        return MESSAGE_FACTORY.getMessage("warning.wsi.r2003");
    }

    /**
     * Not a WSI-BP compliant WSDL (R2003). xsd:import must only be used inside xsd:schema elements.
     *
     */
    public static String WARNING_WSI_R_2003() {
        return LOCALIZER.localize(localizableWARNING_WSI_R_2003());
    }

    public static Localizable localizableWARNING_WSI_R_2004() {
        return MESSAGE_FACTORY.getMessage("warning.wsi.r2004");
    }

    /**
     * Not a WSI-BP compliant WSDL (R2001, R2004). xsd:import must not import XML Schema definitions embedded inline within the WSDL document.
     *
     */
    public static String WARNING_WSI_R_2004() {
        return LOCALIZER.localize(localizableWARNING_WSI_R_2004());
    }

    public static Localizable localizableVALIDATION_INVALID_ATTRIBUTE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("validation.invalidAttribute", arg0, arg1);
    }

    /**
     * invalid attribute "{0}" of element "{1}"
     *
     */
    public static String VALIDATION_INVALID_ATTRIBUTE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableVALIDATION_INVALID_ATTRIBUTE(arg0, arg1));
    }

    public static Localizable localizableVALIDATION_INVALID_ATTRIBUTE_VALUE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("validation.invalidAttributeValue", arg0, arg1);
    }

    /**
     * invalid value "{1}" for attribute "{0}"
     *
     */
    public static String VALIDATION_INVALID_ATTRIBUTE_VALUE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableVALIDATION_INVALID_ATTRIBUTE_VALUE(arg0, arg1));
    }

    public static Localizable localizablePARSING_IO_EXCEPTION_WITH_SYSTEM_ID(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.ioExceptionWithSystemId", arg0);
    }

    /**
     * failed to parse document at "{0}"
     *
     */
    public static String PARSING_IO_EXCEPTION_WITH_SYSTEM_ID(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_IO_EXCEPTION_WITH_SYSTEM_ID(arg0));
    }

    public static Localizable localizablePARSING_PARSE_FAILED() {
        return MESSAGE_FACTORY.getMessage("Parsing.ParseFailed");
    }

    /**
     *  Failed to parse the WSDL.
     *
     */
    public static String PARSING_PARSE_FAILED() {
        return LOCALIZER.localize(localizablePARSING_PARSE_FAILED());
    }

    public static Localizable localizableFAILED_NOSERVICE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("failed.noservice", arg0);
    }

    /**
     * Could not find wsdl:service in the provided WSDL(s):
     *
     * {0} At least one WSDL with at least one service definition needs to be provided.
     *
     */
    public static String FAILED_NOSERVICE(Object arg0) {
        return LOCALIZER.localize(localizableFAILED_NOSERVICE(arg0));
    }

    public static Localizable localizableENTITY_DUPLICATE_WITH_TYPE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("entity.duplicateWithType", arg0, arg1);
    }

    /**
     * duplicate "{0}" entity: "{1}"
     *
     */
    public static String ENTITY_DUPLICATE_WITH_TYPE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableENTITY_DUPLICATE_WITH_TYPE(arg0, arg1));
    }

    public static Localizable localizableVALIDATION_DUPLICATE_PART_NAME(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("validation.duplicatePartName", arg0, arg1);
    }

    /**
     * Invalid WSDL, duplicate parts in a wsdl:message is not allowed.
     * wsdl:message {0} has a duplicated part name: "{1}"
     *
     */
    public static String VALIDATION_DUPLICATE_PART_NAME(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableVALIDATION_DUPLICATE_PART_NAME(arg0, arg1));
    }

    public static Localizable localizablePARSING_ONLY_ONE_OF_ELEMENT_OR_TYPE_REQUIRED(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.onlyOneOfElementOrTypeRequired", arg0);
    }

    /**
     * only one of the "element" or "type" attributes is allowed in part "{0}"
     *
     */
    public static String PARSING_ONLY_ONE_OF_ELEMENT_OR_TYPE_REQUIRED(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_ONLY_ONE_OF_ELEMENT_OR_TYPE_REQUIRED(arg0));
    }

    public static Localizable localizablePARSING_INCORRECT_ROOT_ELEMENT(Object arg0, Object arg1, Object arg2, Object arg3) {
        return MESSAGE_FACTORY.getMessage("parsing.incorrectRootElement", arg0, arg1, arg2, arg3);
    }

    /**
     * expected root element "{2}" (in namespace "{3}"), found element "{0}" (in namespace "{1}")
     *
     */
    public static String PARSING_INCORRECT_ROOT_ELEMENT(Object arg0, Object arg1, Object arg2, Object arg3) {
        return LOCALIZER.localize(localizablePARSING_INCORRECT_ROOT_ELEMENT(arg0, arg1, arg2, arg3));
    }

    public static Localizable localizableINVALID_WSDL_WITH_DOOC(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("invalid.wsdl.with.dooc", arg0, arg1);
    }

    /**
     * "Not a WSDL document: {0}, it gives "{1}", retrying with MEX..."
     *
     */
    public static String INVALID_WSDL_WITH_DOOC(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableINVALID_WSDL_WITH_DOOC(arg0, arg1));
    }

    public static Localizable localizableINTERNALIZER_X_PATH_EVAULATES_TO_TOO_MANY_TARGETS(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("internalizer.XPathEvaulatesToTooManyTargets", arg0, arg1);
    }

    /**
     * XPath evaluation of "{0}" results in too many ({1}) target nodes
     *
     */
    public static String INTERNALIZER_X_PATH_EVAULATES_TO_TOO_MANY_TARGETS(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableINTERNALIZER_X_PATH_EVAULATES_TO_TOO_MANY_TARGETS(arg0, arg1));
    }

    public static Localizable localizablePARSING_ELEMENT_EXPECTED() {
        return MESSAGE_FACTORY.getMessage("parsing.elementExpected");
    }

    /**
     * unexpected non-element found
     *
     */
    public static String PARSING_ELEMENT_EXPECTED() {
        return LOCALIZER.localize(localizablePARSING_ELEMENT_EXPECTED());
    }

    public static Localizable localizableFILE_NOT_FOUND(Object arg0) {
        return MESSAGE_FACTORY.getMessage("file.not.found", arg0);
    }

    /**
     * {0} is unreachable
     *
     */
    public static String FILE_NOT_FOUND(Object arg0) {
        return LOCALIZER.localize(localizableFILE_NOT_FOUND(arg0));
    }

    public static Localizable localizableVALIDATION_INVALID_SIMPLE_TYPE_IN_ELEMENT(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("validation.invalidSimpleTypeInElement", arg0, arg1);
    }

    /**
     * invalid element: "{1}", has named simpleType: "{0}"
     *
     */
    public static String VALIDATION_INVALID_SIMPLE_TYPE_IN_ELEMENT(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableVALIDATION_INVALID_SIMPLE_TYPE_IN_ELEMENT(arg0, arg1));
    }

    public static Localizable localizablePARSING_TOO_MANY_ELEMENTS(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("parsing.tooManyElements", arg0, arg1, arg2);
    }

    /**
     * too many "{0}" elements under "{1}" element "{2}"
     *
     */
    public static String PARSING_TOO_MANY_ELEMENTS(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizablePARSING_TOO_MANY_ELEMENTS(arg0, arg1, arg2));
    }

    public static Localizable localizableLOCALIZED_ERROR(Object arg0) {
        return MESSAGE_FACTORY.getMessage("localized.error", arg0);
    }

    /**
     * {0}
     *
     */
    public static String LOCALIZED_ERROR(Object arg0) {
        return LOCALIZER.localize(localizableLOCALIZED_ERROR(arg0));
    }

    public static Localizable localizablePARSING_FACTORY_CONFIG_EXCEPTION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.factoryConfigException", arg0);
    }

    /**
     * invalid WSDL file! parsing failed: {0}
     *
     */
    public static String PARSING_FACTORY_CONFIG_EXCEPTION(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_FACTORY_CONFIG_EXCEPTION(arg0));
    }

    public static Localizable localizablePARSING_UNKNOWN_IMPORTED_DOCUMENT_TYPE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.unknownImportedDocumentType", arg0);
    }

    /**
     * imported document is of unknown type: {0}
     *
     */
    public static String PARSING_UNKNOWN_IMPORTED_DOCUMENT_TYPE(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_UNKNOWN_IMPORTED_DOCUMENT_TYPE(arg0));
    }

    public static Localizable localizableVALIDATION_DUPLICATED_ELEMENT(Object arg0) {
        return MESSAGE_FACTORY.getMessage("validation.duplicatedElement", arg0);
    }

    /**
     * duplicated element: "{0}"
     *
     */
    public static String VALIDATION_DUPLICATED_ELEMENT(Object arg0) {
        return LOCALIZER.localize(localizableVALIDATION_DUPLICATED_ELEMENT(arg0));
    }

    public static Localizable localizablePARSING_INVALID_URI(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.invalidURI", arg0);
    }

    /**
     * invalid URI: {0}
     *
     */
    public static String PARSING_INVALID_URI(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_INVALID_URI(arg0));
    }

    public static Localizable localizablePARSING_SAX_EXCEPTION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.saxException", arg0);
    }

    /**
     * invalid WSDL file! parsing failed: {0}
     *
     */
    public static String PARSING_SAX_EXCEPTION(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_SAX_EXCEPTION(arg0));
    }

    public static Localizable localizableINTERNALIZER_INCORRECT_VERSION() {
        return MESSAGE_FACTORY.getMessage("Internalizer.IncorrectVersion");
    }

    /**
     *  JAXWS version attribute must be "2.0"
     *
     */
    public static String INTERNALIZER_INCORRECT_VERSION() {
        return LOCALIZER.localize(localizableINTERNALIZER_INCORRECT_VERSION());
    }

    public static Localizable localizablePARSING_NON_WHITESPACE_TEXT_FOUND(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.nonWhitespaceTextFound", arg0);
    }

    /**
     * found unexpected non-whitespace text: "{0}"
     *
     */
    public static String PARSING_NON_WHITESPACE_TEXT_FOUND(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_NON_WHITESPACE_TEXT_FOUND(arg0));
    }

    public static Localizable localizableENTITY_NOT_FOUND_BY_Q_NAME(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("entity.notFoundByQName", arg0, arg1, arg2);
    }

    /**
     * {0} "{1}" not found in the wsdl: {2}
     *
     */
    public static String ENTITY_NOT_FOUND_BY_Q_NAME(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableENTITY_NOT_FOUND_BY_Q_NAME(arg0, arg1, arg2));
    }

    public static Localizable localizableVALIDATION_MISSING_REQUIRED_ATTRIBUTE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("validation.missingRequiredAttribute", arg0, arg1);
    }

    /**
     * missing required attribute "{0}" of element "{1}"
     *
     */
    public static String VALIDATION_MISSING_REQUIRED_ATTRIBUTE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableVALIDATION_MISSING_REQUIRED_ATTRIBUTE(arg0, arg1));
    }

    public static Localizable localizableWARNING_FAULT_EMPTY_ACTION(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("warning.faultEmptyAction", arg0, arg1, arg2);
    }

    /**
     * ignoring empty Action in "{0}" {1} element of "{2}" operation, using default instead
     *
     */
    public static String WARNING_FAULT_EMPTY_ACTION(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWARNING_FAULT_EMPTY_ACTION(arg0, arg1, arg2));
    }

    public static Localizable localizablePARSING_INVALID_ATTRIBUTE_VALUE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("parsing.invalidAttributeValue", arg0, arg1);
    }

    /**
     * invalid value "{1}" for attribute "{0}"
     *
     */
    public static String PARSING_INVALID_ATTRIBUTE_VALUE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizablePARSING_INVALID_ATTRIBUTE_VALUE(arg0, arg1));
    }

    public static Localizable localizableABSTRACT_REFERENCE_FINDER_IMPL_UNABLE_TO_PARSE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("AbstractReferenceFinderImpl.UnableToParse", arg0, arg1);
    }

    /**
     *  Unable to parse "{0}" : {1}
     *
     */
    public static String ABSTRACT_REFERENCE_FINDER_IMPL_UNABLE_TO_PARSE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableABSTRACT_REFERENCE_FINDER_IMPL_UNABLE_TO_PARSE(arg0, arg1));
    }

    public static Localizable localizableENTITY_DUPLICATE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("entity.duplicate", arg0);
    }

    /**
     * duplicate entity: "{0}"
     *
     */
    public static String ENTITY_DUPLICATE(Object arg0) {
        return LOCALIZER.localize(localizableENTITY_DUPLICATE(arg0));
    }

    public static Localizable localizableVALIDATION_MISSING_REQUIRED_PROPERTY(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("validation.missingRequiredProperty", arg0, arg1);
    }

    /**
     * missing required property "{0}" of element "{1}"
     *
     */
    public static String VALIDATION_MISSING_REQUIRED_PROPERTY(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableVALIDATION_MISSING_REQUIRED_PROPERTY(arg0, arg1));
    }

    public static Localizable localizableINVALID_CUSTOMIZATION_NAMESPACE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("invalid.customization.namespace", arg0);
    }

    /**
     * Ignoring customization: "{0}", because it has no namespace. It must belong to the customization namespace.
     *
     */
    public static String INVALID_CUSTOMIZATION_NAMESPACE(Object arg0) {
        return LOCALIZER.localize(localizableINVALID_CUSTOMIZATION_NAMESPACE(arg0));
    }

    public static Localizable localizableTRY_WITH_MEX(Object arg0) {
        return MESSAGE_FACTORY.getMessage("try.with.mex", arg0);
    }

    /**
     * {0}
     *
     * retrying with MEX...
     *
     */
    public static String TRY_WITH_MEX(Object arg0) {
        return LOCALIZER.localize(localizableTRY_WITH_MEX(arg0));
    }

    public static Localizable localizableINVALID_WSDL(Object arg0, Object arg1, Object arg2, Object arg3) {
        return MESSAGE_FACTORY.getMessage("invalid.wsdl", arg0, arg1, arg2, arg3);
    }

    /**
     * Invalid WSDL {0}, expected {1} found {2} at (line {3})
     *
     */
    public static String INVALID_WSDL(Object arg0, Object arg1, Object arg2, Object arg3) {
        return LOCALIZER.localize(localizableINVALID_WSDL(arg0, arg1, arg2, arg3));
    }

    public static Localizable localizableENTITY_NOT_FOUND_BY_ID(Object arg0) {
        return MESSAGE_FACTORY.getMessage("entity.notFoundByID", arg0);
    }

    /**
     * invalid entity id: "{0}"
     *
     */
    public static String ENTITY_NOT_FOUND_BY_ID(Object arg0) {
        return LOCALIZER.localize(localizableENTITY_NOT_FOUND_BY_ID(arg0));
    }

    public static Localizable localizableINTERNALIZER_INCORRECT_SCHEMA_REFERENCE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("Internalizer.IncorrectSchemaReference", arg0, arg1);
    }

    /**
     *  "{0}" is not a part of this compilation. Is this a mistake for "{1}"?
     *
     */
    public static String INTERNALIZER_INCORRECT_SCHEMA_REFERENCE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableINTERNALIZER_INCORRECT_SCHEMA_REFERENCE(arg0, arg1));
    }

    public static Localizable localizablePARSING_INVALID_EXTENSION_ELEMENT(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("parsing.invalidExtensionElement", arg0, arg1);
    }

    /**
     * invalid extension element: "{0}" (in namespace "{1}")
     *
     */
    public static String PARSING_INVALID_EXTENSION_ELEMENT(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizablePARSING_INVALID_EXTENSION_ELEMENT(arg0, arg1));
    }

    public static Localizable localizableVALIDATION_EXCLUSIVE_ATTRIBUTES(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("validation.exclusiveAttributes", arg0, arg1);
    }

    /**
     * exclusive attributes: "{0}", "{1}"
     *
     */
    public static String VALIDATION_EXCLUSIVE_ATTRIBUTES(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableVALIDATION_EXCLUSIVE_ATTRIBUTES(arg0, arg1));
    }

    public static Localizable localizableVALIDATION_INCORRECT_TARGET_NAMESPACE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("validation.incorrectTargetNamespace", arg0, arg1);
    }

    /**
     * target namespace is incorrect (expected: {1}, found: {0})
     *
     */
    public static String VALIDATION_INCORRECT_TARGET_NAMESPACE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableVALIDATION_INCORRECT_TARGET_NAMESPACE(arg0, arg1));
    }

    public static Localizable localizableINTERNALIZER_TWO_VERSION_ATTRIBUTES() {
        return MESSAGE_FACTORY.getMessage("Internalizer.TwoVersionAttributes");
    }

    /**
     *  Both jaxws:version and version are present
     *
     */
    public static String INTERNALIZER_TWO_VERSION_ATTRIBUTES() {
        return LOCALIZER.localize(localizableINTERNALIZER_TWO_VERSION_ATTRIBUTES());
    }

    public static Localizable localizableENTITY_NOT_FOUND_BINDING(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("entity.notFound.binding", arg0, arg1);
    }

    /**
     * wsdl:binding "{0}" referenced by wsdl:port "{1}", but it's not found in the wsdl
     *
     */
    public static String ENTITY_NOT_FOUND_BINDING(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableENTITY_NOT_FOUND_BINDING(arg0, arg1));
    }

    public static Localizable localizableVALIDATION_INVALID_SUB_ENTITY(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("validation.invalidSubEntity", arg0, arg1);
    }

    /**
     * invalid sub-element "{0}" of element "{1}"
     *
     */
    public static String VALIDATION_INVALID_SUB_ENTITY(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableVALIDATION_INVALID_SUB_ENTITY(arg0, arg1));
    }

    public static Localizable localizablePARSING_REQUIRED_EXTENSIBILITY_ELEMENT(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("parsing.requiredExtensibilityElement", arg0, arg1);
    }

    /**
     * unknown required extensibility element "{0}" (in namespace "{1}")
     *
     */
    public static String PARSING_REQUIRED_EXTENSIBILITY_ELEMENT(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizablePARSING_REQUIRED_EXTENSIBILITY_ELEMENT(arg0, arg1));
    }

    public static Localizable localizablePARSING_IO_EXCEPTION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.ioException", arg0);
    }

    /**
     * parsing failed: {0}
     *
     */
    public static String PARSING_IO_EXCEPTION(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_IO_EXCEPTION(arg0));
    }

    public static Localizable localizableINTERNALIZER_VERSION_NOT_PRESENT() {
        return MESSAGE_FACTORY.getMessage("Internalizer.VersionNotPresent");
    }

    /**
     *  JAXWS version attribute must be present
     *
     */
    public static String INTERNALIZER_VERSION_NOT_PRESENT() {
        return LOCALIZER.localize(localizableINTERNALIZER_VERSION_NOT_PRESENT());
    }

    public static Localizable localizableINTERNALIZER_X_PATH_EVALUATION_ERROR(Object arg0) {
        return MESSAGE_FACTORY.getMessage("internalizer.XPathEvaluationError", arg0);
    }

    /**
     * XPath error: {0}
     *
     */
    public static String INTERNALIZER_X_PATH_EVALUATION_ERROR(Object arg0) {
        return LOCALIZER.localize(localizableINTERNALIZER_X_PATH_EVALUATION_ERROR(arg0));
    }

    public static Localizable localizablePARSING_INVALID_WSDL_ELEMENT(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.invalidWsdlElement", arg0);
    }

    /**
     * invalid WSDL element: "{0}"
     *
     */
    public static String PARSING_INVALID_WSDL_ELEMENT(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_INVALID_WSDL_ELEMENT(arg0));
    }

    public static Localizable localizableINTERNALIZER_TARGET_NOT_AN_ELEMENT() {
        return MESSAGE_FACTORY.getMessage("internalizer.targetNotAnElement");
    }

    /**
     *  Target node is not an element
     *
     */
    public static String INTERNALIZER_TARGET_NOT_AN_ELEMENT() {
        return LOCALIZER.localize(localizableINTERNALIZER_TARGET_NOT_AN_ELEMENT());
    }

    public static Localizable localizableWARNING_INPUT_OUTPUT_EMPTY_ACTION(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("warning.inputOutputEmptyAction", arg0, arg1);
    }

    /**
     * ignoring empty Action in {0} element of "{1}" operation, using default instead
     *
     */
    public static String WARNING_INPUT_OUTPUT_EMPTY_ACTION(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWARNING_INPUT_OUTPUT_EMPTY_ACTION(arg0, arg1));
    }

    public static Localizable localizableINTERNALIZER_X_PATH_EVALUATES_TO_NO_TARGET(Object arg0) {
        return MESSAGE_FACTORY.getMessage("internalizer.XPathEvaluatesToNoTarget", arg0);
    }

    /**
     * XPath evaluation of "{0}" results in an empty target node
     *
     */
    public static String INTERNALIZER_X_PATH_EVALUATES_TO_NO_TARGET(Object arg0) {
        return LOCALIZER.localize(localizableINTERNALIZER_X_PATH_EVALUATES_TO_NO_TARGET(arg0));
    }

    public static Localizable localizablePARSING_INVALID_TAG_NS(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        return MESSAGE_FACTORY.getMessage("parsing.invalidTagNS", arg0, arg1, arg2, arg3, arg4);
    }

    /**
     * Invalid WSDL at {4}: expected element "{2}" (in namespace "{3}"), found element "{0}" (in namespace "{1}")
     *
     */
    public static String PARSING_INVALID_TAG_NS(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        return LOCALIZER.localize(localizablePARSING_INVALID_TAG_NS(arg0, arg1, arg2, arg3, arg4));
    }

    public static Localizable localizablePARSING_UNKNOWN_NAMESPACE_PREFIX(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.unknownNamespacePrefix", arg0);
    }

    /**
     * undeclared namespace prefix: "{0}"
     *
     */
    public static String PARSING_UNKNOWN_NAMESPACE_PREFIX(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_UNKNOWN_NAMESPACE_PREFIX(arg0));
    }

    public static Localizable localizablePARSING_INVALID_ELEMENT(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("parsing.invalidElement", arg0, arg1);
    }

    /**
     * invalid element: "{0}" (in namespace "{1}")
     *
     */
    public static String PARSING_INVALID_ELEMENT(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizablePARSING_INVALID_ELEMENT(arg0, arg1));
    }

    public static Localizable localizablePARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.onlyOneDocumentationAllowed", arg0);
    }

    /**
     * only one "documentation" element allowed in "{0}"
     *
     */
    public static String PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(arg0));
    }

    public static Localizable localizablePARSING_PARSER_CONFIG_EXCEPTION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.parserConfigException", arg0);
    }

    /**
     * invalid WSDL file! parsing failed: {0}
     *
     */
    public static String PARSING_PARSER_CONFIG_EXCEPTION(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_PARSER_CONFIG_EXCEPTION(arg0));
    }

    public static Localizable localizablePARSING_MISSING_REQUIRED_ATTRIBUTE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("parsing.missingRequiredAttribute", arg0, arg1);
    }

    /**
     * missing required attribute "{1}" of element "{0}"
     *
     */
    public static String PARSING_MISSING_REQUIRED_ATTRIBUTE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizablePARSING_MISSING_REQUIRED_ATTRIBUTE(arg0, arg1));
    }

    public static Localizable localizableVALIDATION_MISSING_REQUIRED_SUB_ENTITY(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("validation.missingRequiredSubEntity", arg0, arg1);
    }

    /**
     * missing required sub-entity "{0}" of element "{1}"
     *
     */
    public static String VALIDATION_MISSING_REQUIRED_SUB_ENTITY(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableVALIDATION_MISSING_REQUIRED_SUB_ENTITY(arg0, arg1));
    }

    public static Localizable localizableVALIDATION_INVALID_ELEMENT(Object arg0) {
        return MESSAGE_FACTORY.getMessage("validation.invalidElement", arg0);
    }

    /**
     * invalid element: "{0}"
     *
     */
    public static String VALIDATION_INVALID_ELEMENT(Object arg0) {
        return LOCALIZER.localize(localizableVALIDATION_INVALID_ELEMENT(arg0));
    }

    public static Localizable localizableVALIDATION_AMBIGUOUS_NAME(Object arg0) {
        return MESSAGE_FACTORY.getMessage("validation.ambiguousName", arg0);
    }

    /**
     * ambiguous operation name: "{0}"
     *
     */
    public static String VALIDATION_AMBIGUOUS_NAME(Object arg0) {
        return LOCALIZER.localize(localizableVALIDATION_AMBIGUOUS_NAME(arg0));
    }

    public static Localizable localizablePARSING_SAX_EXCEPTION_WITH_SYSTEM_ID(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.saxExceptionWithSystemId", arg0);
    }

    /**
     * invalid WSDL file! failed to parse document at "{0}"
     *
     */
    public static String PARSING_SAX_EXCEPTION_WITH_SYSTEM_ID(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_SAX_EXCEPTION_WITH_SYSTEM_ID(arg0));
    }

    public static Localizable localizablePARSING_WSDL_NOT_DEFAULT_NAMESPACE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.wsdlNotDefaultNamespace", arg0);
    }

    /**
     * default namespace must be "{0}"
     *
     */
    public static String PARSING_WSDL_NOT_DEFAULT_NAMESPACE(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_WSDL_NOT_DEFAULT_NAMESPACE(arg0));
    }

    public static Localizable localizablePARSING_INVALID_OPERATION_STYLE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.invalidOperationStyle", arg0);
    }

    /**
     * operation "{0}" has an invalid style
     *
     */
    public static String PARSING_INVALID_OPERATION_STYLE(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_INVALID_OPERATION_STYLE(arg0));
    }

    public static Localizable localizableWARNING_WSI_R_2001() {
        return MESSAGE_FACTORY.getMessage("warning.wsi.r2001");
    }

    /**
     * Not a WSI-BP compliant WSDL (R2001, R2002). wsdl:import must import only WSDL documents. It's trying to import: "{0}"
     *
     */
    public static String WARNING_WSI_R_2001() {
        return LOCALIZER.localize(localizableWARNING_WSI_R_2001());
    }

    public static Localizable localizableWARNING_WSI_R_2002(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("warning.wsi.r2002", arg0, arg1);
    }

    /**
     * Not a WSI-BP compliant WSDL (R2002). wsdl:import must not be used to import XML Schema embedded in the WSDL document. Expected WSDL namespace: {0}, found: {1}
     *
     */
    public static String WARNING_WSI_R_2002(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWARNING_WSI_R_2002(arg0, arg1));
    }

    public static Localizable localizablePARSING_INVALID_TAG(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("parsing.invalidTag", arg0, arg1);
    }

    /**
     * expected element "{1}", found "{0}"
     *
     */
    public static String PARSING_INVALID_TAG(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizablePARSING_INVALID_TAG(arg0, arg1));
    }

    public static Localizable localizableINTERNALIZER_TARGET_NOT_FOUND(Object arg0) {
        return MESSAGE_FACTORY.getMessage("internalizer.targetNotFound", arg0);
    }

    /**
     *  No target found for the wsdlLocation: {0}
     *
     */
    public static String INTERNALIZER_TARGET_NOT_FOUND(Object arg0) {
        return LOCALIZER.localize(localizableINTERNALIZER_TARGET_NOT_FOUND(arg0));
    }

    public static Localizable localizableINTERNALIZER_X_PATH_EVALUATES_TO_NON_ELEMENT(Object arg0) {
        return MESSAGE_FACTORY.getMessage("internalizer.XPathEvaluatesToNonElement", arg0);
    }

    /**
     * XPath evaluation of "{0}" needs to result in an element.
     *
     */
    public static String INTERNALIZER_X_PATH_EVALUATES_TO_NON_ELEMENT(Object arg0) {
        return LOCALIZER.localize(localizableINTERNALIZER_X_PATH_EVALUATES_TO_NON_ELEMENT(arg0));
    }

    public static Localizable localizableVALIDATION_UNSUPPORTED_USE_ENCODED(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("validation.unsupportedUse.encoded", arg0, arg1);
    }

    /**
     * "Use of SOAP Encoding is not supported.
     * SOAP extension element on line {0} in {1} has use="encoded" "
     *
     */
    public static String VALIDATION_UNSUPPORTED_USE_ENCODED(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableVALIDATION_UNSUPPORTED_USE_ENCODED(arg0, arg1));
    }

    public static Localizable localizablePARSING_ELEMENT_OR_TYPE_REQUIRED(Object arg0) {
        return MESSAGE_FACTORY.getMessage("parsing.elementOrTypeRequired", arg0);
    }

    /**
     * warning: part {0} is ignored, either the "element" or the "type" attribute is required in part "{0}"
     *
     */
    public static String PARSING_ELEMENT_OR_TYPE_REQUIRED(Object arg0) {
        return LOCALIZER.localize(localizablePARSING_ELEMENT_OR_TYPE_REQUIRED(arg0));
    }

    public static Localizable localizableENTITY_NOT_FOUND_PORT_TYPE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("entity.notFound.portType", arg0, arg1);
    }

    /**
     * wsdl:portType "{0}" referenced by wsdl:binding "{1}", but it's not found in the wsdl
     *
     */
    public static String ENTITY_NOT_FOUND_PORT_TYPE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableENTITY_NOT_FOUND_PORT_TYPE(arg0, arg1));
    }

    private static class BundleSupplier
        implements ResourceBundleSupplier
    {


        public ResourceBundle getResourceBundle(Locale locale) {
            return ResourceBundle.getBundle(BUNDLE_NAME, locale);
        }

    }

}
