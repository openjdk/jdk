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

package com.sun.xml.internal.ws.policy.privateutil;

import java.util.Locale;
import java.util.ResourceBundle;
import javax.annotation.Generated;
import com.sun.istack.internal.localization.Localizable;
import com.sun.istack.internal.localization.LocalizableMessageFactory;
import com.sun.istack.internal.localization.LocalizableMessageFactory.ResourceBundleSupplier;
import com.sun.istack.internal.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
@Generated("com.sun.istack.internal.maven.ResourceGenMojo")
public final class LocalizationMessages {

    private final static String BUNDLE_NAME = "com.sun.xml.internal.ws.policy.privateutil.Localization";
    private final static LocalizableMessageFactory MESSAGE_FACTORY = new LocalizableMessageFactory(BUNDLE_NAME, new LocalizationMessages.BundleSupplier());
    private final static Localizer LOCALIZER = new Localizer();

    public static Localizable localizableWSP_0017_UNABLE_TO_ACCESS_POLICY_SOURCE_MODEL_PLUS_REASON(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0017_UNABLE_TO_ACCESS_POLICY_SOURCE_MODEL_PLUS_REASON", arg0, arg1);
    }

    /**
     * WSP0017: Unable to access policy source model identified by URI: {0}
     * Detailed reason: {1}
     *
     */
    public static String WSP_0017_UNABLE_TO_ACCESS_POLICY_SOURCE_MODEL_PLUS_REASON(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0017_UNABLE_TO_ACCESS_POLICY_SOURCE_MODEL_PLUS_REASON(arg0, arg1));
    }

    public static Localizable localizableWSP_0028_SERVICE_PROVIDER_COULD_NOT_BE_INSTANTIATED(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0028_SERVICE_PROVIDER_COULD_NOT_BE_INSTANTIATED", arg0);
    }

    /**
     * WSP0028: Service provider {0} could not be instantiated
     *
     */
    public static String WSP_0028_SERVICE_PROVIDER_COULD_NOT_BE_INSTANTIATED(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0028_SERVICE_PROVIDER_COULD_NOT_BE_INSTANTIATED(arg0));
    }

    public static Localizable localizableWSP_0081_UNABLE_TO_INSERT_CHILD(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0081_UNABLE_TO_INSERT_CHILD", arg0, arg1);
    }

    /**
     * WSP0081: Failed to insert child node ''{1}'' into queue ''{0}''
     *
     */
    public static String WSP_0081_UNABLE_TO_INSERT_CHILD(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0081_UNABLE_TO_INSERT_CHILD(arg0, arg1));
    }

    public static Localizable localizableWSP_0096_ERROR_WHILE_COMBINE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0096_ERROR_WHILE_COMBINE", arg0);
    }

    /**
     * WSP0096: Error while combining {0}.
     *
     */
    public static String WSP_0096_ERROR_WHILE_COMBINE(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0096_ERROR_WHILE_COMBINE(arg0));
    }

    public static Localizable localizableWSP_0018_UNABLE_TO_ACCESS_POLICY_SOURCE_MODEL(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0018_UNABLE_TO_ACCESS_POLICY_SOURCE_MODEL", arg0);
    }

    /**
     * WSP0018: Unable to access policy source model identified by URI: {0}
     *
     */
    public static String WSP_0018_UNABLE_TO_ACCESS_POLICY_SOURCE_MODEL(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0018_UNABLE_TO_ACCESS_POLICY_SOURCE_MODEL(arg0));
    }

    public static Localizable localizableWSP_0090_UNEXPECTED_ELEMENT(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0090_UNEXPECTED_ELEMENT", arg0, arg1);
    }

    /**
     * WSP0090: Unexpected element <{0}> at location {1}
     *
     */
    public static String WSP_0090_UNEXPECTED_ELEMENT(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0090_UNEXPECTED_ELEMENT(arg0, arg1));
    }

    public static Localizable localizableWSP_0043_POLICY_MODEL_TRANSLATION_ERROR_INPUT_PARAM_NULL() {
        return MESSAGE_FACTORY.getMessage("WSP_0043_POLICY_MODEL_TRANSLATION_ERROR_INPUT_PARAM_NULL");
    }

    /**
     * WSP0043: Policy model translation error:  Input policy source model parameter is null
     *
     */
    public static String WSP_0043_POLICY_MODEL_TRANSLATION_ERROR_INPUT_PARAM_NULL() {
        return LOCALIZER.localize(localizableWSP_0043_POLICY_MODEL_TRANSLATION_ERROR_INPUT_PARAM_NULL());
    }

    public static Localizable localizableWSP_0055_NO_ALTERNATIVE_COMBINATIONS_CREATED() {
        return MESSAGE_FACTORY.getMessage("WSP_0055_NO_ALTERNATIVE_COMBINATIONS_CREATED");
    }

    /**
     * WSP0055: No alternative combinations created: Returning "nothing allowed" policy
     *
     */
    public static String WSP_0055_NO_ALTERNATIVE_COMBINATIONS_CREATED() {
        return LOCALIZER.localize(localizableWSP_0055_NO_ALTERNATIVE_COMBINATIONS_CREATED());
    }

    public static Localizable localizableWSP_0072_DIGEST_MUST_NOT_BE_NULL_WHEN_ALG_DEFINED() {
        return MESSAGE_FACTORY.getMessage("WSP_0072_DIGEST_MUST_NOT_BE_NULL_WHEN_ALG_DEFINED");
    }

    /**
     * WSP0072: Digest must not be null if the digest algorithm is defined
     *
     */
    public static String WSP_0072_DIGEST_MUST_NOT_BE_NULL_WHEN_ALG_DEFINED() {
        return LOCALIZER.localize(localizableWSP_0072_DIGEST_MUST_NOT_BE_NULL_WHEN_ALG_DEFINED());
    }

    public static Localizable localizableWSP_0016_UNABLE_TO_CLONE_POLICY_SOURCE_MODEL() {
        return MESSAGE_FACTORY.getMessage("WSP_0016_UNABLE_TO_CLONE_POLICY_SOURCE_MODEL");
    }

    /**
     * WSP0016: Unable to clone input policy source model
     *
     */
    public static String WSP_0016_UNABLE_TO_CLONE_POLICY_SOURCE_MODEL() {
        return LOCALIZER.localize(localizableWSP_0016_UNABLE_TO_CLONE_POLICY_SOURCE_MODEL());
    }

    public static Localizable localizableWSP_0058_MULTIPLE_POLICY_IDS_NOT_ALLOWED() {
        return MESSAGE_FACTORY.getMessage("WSP_0058_MULTIPLE_POLICY_IDS_NOT_ALLOWED");
    }

    /**
     * WSP0058: Multiple identifiers of policy expression detected. Single policy expression must not contain both wsu:Id and xml:id identifiers at once
     *
     */
    public static String WSP_0058_MULTIPLE_POLICY_IDS_NOT_ALLOWED() {
        return LOCALIZER.localize(localizableWSP_0058_MULTIPLE_POLICY_IDS_NOT_ALLOWED());
    }

    public static Localizable localizableWSP_0061_METHOD_INVOCATION_FAILED(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("WSP_0061_METHOD_INVOCATION_FAILED", arg0, arg1, arg2);
    }

    /**
     * WSP0061: Method invocation failed (class={0}, method={1}, parameters={2})
     *
     */
    public static String WSP_0061_METHOD_INVOCATION_FAILED(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSP_0061_METHOD_INVOCATION_FAILED(arg0, arg1, arg2));
    }

    public static Localizable localizableWSP_0048_POLICY_ELEMENT_EXPECTED_FIRST() {
        return MESSAGE_FACTORY.getMessage("WSP_0048_POLICY_ELEMENT_EXPECTED_FIRST");
    }

    /**
     * WSP0048: Failed to unmarshal policy expression. Expected 'Policy' as a first XML element
     *
     */
    public static String WSP_0048_POLICY_ELEMENT_EXPECTED_FIRST() {
        return LOCALIZER.localize(localizableWSP_0048_POLICY_ELEMENT_EXPECTED_FIRST());
    }

    public static Localizable localizableWSP_0068_FAILED_TO_UNMARSHALL_POLICY_EXPRESSION() {
        return MESSAGE_FACTORY.getMessage("WSP_0068_FAILED_TO_UNMARSHALL_POLICY_EXPRESSION");
    }

    /**
     * WSP0068: Failed to unmarshal policy expression
     *
     */
    public static String WSP_0068_FAILED_TO_UNMARSHALL_POLICY_EXPRESSION() {
        return LOCALIZER.localize(localizableWSP_0068_FAILED_TO_UNMARSHALL_POLICY_EXPRESSION());
    }

    public static Localizable localizableWSP_0029_SERVICE_PORT_OPERATION_PARAM_MUST_NOT_BE_NULL(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("WSP_0029_SERVICE_PORT_OPERATION_PARAM_MUST_NOT_BE_NULL", arg0, arg1, arg2);
    }

    /**
     * WSP0029: Parameters "service", "port" and "operation" must not be null. (service={0}, port={1}, operation={2})
     *
     */
    public static String WSP_0029_SERVICE_PORT_OPERATION_PARAM_MUST_NOT_BE_NULL(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSP_0029_SERVICE_PORT_OPERATION_PARAM_MUST_NOT_BE_NULL(arg0, arg1, arg2));
    }

    public static Localizable localizableWSP_0080_IMPLEMENTATION_EXPECTED_NOT_NULL() {
        return MESSAGE_FACTORY.getMessage("WSP_0080_IMPLEMENTATION_EXPECTED_NOT_NULL");
    }

    /**
     * WSP0080: Expected config file identifier, got null instead. Implementation fault.
     *
     */
    public static String WSP_0080_IMPLEMENTATION_EXPECTED_NOT_NULL() {
        return LOCALIZER.localize(localizableWSP_0080_IMPLEMENTATION_EXPECTED_NOT_NULL());
    }

    public static Localizable localizableWSP_0051_OPERATION_NOT_SUPPORTED_FOR_THIS_BUT_ASSERTION_RELATED_NODE_TYPE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0051_OPERATION_NOT_SUPPORTED_FOR_THIS_BUT_ASSERTION_RELATED_NODE_TYPE", arg0);
    }

    /**
     * WSP0051: This operation is supported only for 'ASSERTION' and 'ASSERTION_PARAMETER_NODE' node types. It is not supported for the node type ''{0}''
     *
     */
    public static String WSP_0051_OPERATION_NOT_SUPPORTED_FOR_THIS_BUT_ASSERTION_RELATED_NODE_TYPE(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0051_OPERATION_NOT_SUPPORTED_FOR_THIS_BUT_ASSERTION_RELATED_NODE_TYPE(arg0));
    }

    public static Localizable localizableWSP_0008_UNEXPECTED_CHILD_MODEL_TYPE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0008_UNEXPECTED_CHILD_MODEL_TYPE", arg0);
    }

    /**
     * WSP0008: Unexpected type of child model node nested in an 'ASSERTION' node: ''{0}''
     *
     */
    public static String WSP_0008_UNEXPECTED_CHILD_MODEL_TYPE(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0008_UNEXPECTED_CHILD_MODEL_TYPE(arg0));
    }

    public static Localizable localizableWSP_0023_UNEXPECTED_ERROR_WHILE_CLOSING_RESOURCE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0023_UNEXPECTED_ERROR_WHILE_CLOSING_RESOURCE", arg0);
    }

    /**
     * WSP0023: Unexpected error occured while closing resource "{0}".
     *
     */
    public static String WSP_0023_UNEXPECTED_ERROR_WHILE_CLOSING_RESOURCE(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0023_UNEXPECTED_ERROR_WHILE_CLOSING_RESOURCE(arg0));
    }

    public static Localizable localizableWSP_0091_END_ELEMENT_NO_MATCH(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("WSP_0091_END_ELEMENT_NO_MATCH", arg0, arg1, arg2);
    }

    /**
     * WSP0091: Expected end element {0} but read <{1}> at location {2}
     *
     */
    public static String WSP_0091_END_ELEMENT_NO_MATCH(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSP_0091_END_ELEMENT_NO_MATCH(arg0, arg1, arg2));
    }

    public static Localizable localizableWSP_0001_UNSUPPORTED_MODEL_NODE_TYPE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0001_UNSUPPORTED_MODEL_NODE_TYPE", arg0);
    }

    /**
     * WSP0001: Unsupported model node type: ''{0}''
     *
     */
    public static String WSP_0001_UNSUPPORTED_MODEL_NODE_TYPE(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0001_UNSUPPORTED_MODEL_NODE_TYPE(arg0));
    }

    public static Localizable localizableWSP_0053_INVALID_CLIENT_SIDE_ALTERNATIVE() {
        return MESSAGE_FACTORY.getMessage("WSP_0053_INVALID_CLIENT_SIDE_ALTERNATIVE");
    }

    /**
     * WSP0053: Client cannot proceed to call the web service - invalid policy alternative found. For more information see "WSP0075" warning messages in the log file.
     *
     */
    public static String WSP_0053_INVALID_CLIENT_SIDE_ALTERNATIVE() {
        return LOCALIZER.localize(localizableWSP_0053_INVALID_CLIENT_SIDE_ALTERNATIVE());
    }

    public static Localizable localizableWSP_0087_UNKNOWN_EVENT(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0087_UNKNOWN_EVENT", arg0);
    }

    /**
     * WSP0087: Received unknown event {0}
     *
     */
    public static String WSP_0087_UNKNOWN_EVENT(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0087_UNKNOWN_EVENT(arg0));
    }

    public static Localizable localizableWSP_0065_INCONSISTENCY_IN_POLICY_SOURCE_MODEL(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0065_INCONSISTENCY_IN_POLICY_SOURCE_MODEL", arg0);
    }

    /**
     * WSP0065: Inconsistency in policy source model detected: Cannot create policy assertion parameter from a model node of type: ''{0}''
     *
     */
    public static String WSP_0065_INCONSISTENCY_IN_POLICY_SOURCE_MODEL(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0065_INCONSISTENCY_IN_POLICY_SOURCE_MODEL(arg0));
    }

    public static Localizable localizableWSP_0032_SERVICE_CAN_NOT_BE_NULL() {
        return MESSAGE_FACTORY.getMessage("WSP_0032_SERVICE_CAN_NOT_BE_NULL");
    }

    /**
     * WSP0032: Service can not be null
     *
     */
    public static String WSP_0032_SERVICE_CAN_NOT_BE_NULL() {
        return LOCALIZER.localize(localizableWSP_0032_SERVICE_CAN_NOT_BE_NULL());
    }

    public static Localizable localizableWSP_0093_INVALID_URI(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0093_INVALID_URI", arg0, arg1);
    }

    /**
     * WSP0093: Invalid URI "{0}" at location {1}
     *
     */
    public static String WSP_0093_INVALID_URI(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0093_INVALID_URI(arg0, arg1));
    }

    public static Localizable localizableWSP_0045_POLICY_MAP_KEY_MUST_NOT_BE_NULL() {
        return MESSAGE_FACTORY.getMessage("WSP_0045_POLICY_MAP_KEY_MUST_NOT_BE_NULL");
    }

    /**
     * WSP0045: Provided policy map key must not be null! Create a proper policy map key by calling one of PolicyMap's  createXxxScopeKey(...) methods first
     *
     */
    public static String WSP_0045_POLICY_MAP_KEY_MUST_NOT_BE_NULL() {
        return LOCALIZER.localize(localizableWSP_0045_POLICY_MAP_KEY_MUST_NOT_BE_NULL());
    }

    public static Localizable localizableWSP_0079_ERROR_WHILE_RFC_2396_UNESCAPING(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0079_ERROR_WHILE_RFC2396_UNESCAPING", arg0);
    }

    /**
     * WSP0079: Error while unescaping ''{0}'' by RFC2396
     *
     */
    public static String WSP_0079_ERROR_WHILE_RFC_2396_UNESCAPING(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0079_ERROR_WHILE_RFC_2396_UNESCAPING(arg0));
    }

    public static Localizable localizableWSP_0064_INITIAL_POLICY_COLLECTION_MUST_NOT_BE_EMPTY() {
        return MESSAGE_FACTORY.getMessage("WSP_0064_INITIAL_POLICY_COLLECTION_MUST_NOT_BE_EMPTY");
    }

    /**
     * WSP0064: Initial collection of policies must not be empty
     *
     */
    public static String WSP_0064_INITIAL_POLICY_COLLECTION_MUST_NOT_BE_EMPTY() {
        return LOCALIZER.localize(localizableWSP_0064_INITIAL_POLICY_COLLECTION_MUST_NOT_BE_EMPTY());
    }

    public static Localizable localizableWSP_0044_POLICY_MAP_MUTATOR_ALREADY_CONNECTED() {
        return MESSAGE_FACTORY.getMessage("WSP_0044_POLICY_MAP_MUTATOR_ALREADY_CONNECTED");
    }

    /**
     * WSP0044: This policy map mutator is already connected to a policy map. Please, disconnect it first, before connecting to another policy map
     *
     */
    public static String WSP_0044_POLICY_MAP_MUTATOR_ALREADY_CONNECTED() {
        return LOCALIZER.localize(localizableWSP_0044_POLICY_MAP_MUTATOR_ALREADY_CONNECTED());
    }

    public static Localizable localizableWSP_0015_UNABLE_TO_INSTANTIATE_DIGEST_ALG_URI_FIELD() {
        return MESSAGE_FACTORY.getMessage("WSP_0015_UNABLE_TO_INSTANTIATE_DIGEST_ALG_URI_FIELD");
    }

    /**
     * WSP0015: Unable to instantiate static constant field 'DEFAULT_DIGEST_ALGORITHM_URI'
     *
     */
    public static String WSP_0015_UNABLE_TO_INSTANTIATE_DIGEST_ALG_URI_FIELD() {
        return LOCALIZER.localize(localizableWSP_0015_UNABLE_TO_INSTANTIATE_DIGEST_ALG_URI_FIELD());
    }

    public static Localizable localizableWSP_0046_POLICY_MAP_KEY_HANDLER_NOT_SET() {
        return MESSAGE_FACTORY.getMessage("WSP_0046_POLICY_MAP_KEY_HANDLER_NOT_SET");
    }

    /**
     * WSP0046: Policy map key handler is not set
     *
     */
    public static String WSP_0046_POLICY_MAP_KEY_HANDLER_NOT_SET() {
        return LOCALIZER.localize(localizableWSP_0046_POLICY_MAP_KEY_HANDLER_NOT_SET());
    }

    public static Localizable localizableWSP_0012_UNABLE_TO_UNMARSHALL_POLICY_MALFORMED_URI() {
        return MESSAGE_FACTORY.getMessage("WSP_0012_UNABLE_TO_UNMARSHALL_POLICY_MALFORMED_URI");
    }

    /**
     * WSP0012: Unable to unmarshall policy referenced due to malformed URI value in attribute
     *
     */
    public static String WSP_0012_UNABLE_TO_UNMARSHALL_POLICY_MALFORMED_URI() {
        return LOCALIZER.localize(localizableWSP_0012_UNABLE_TO_UNMARSHALL_POLICY_MALFORMED_URI());
    }

    public static Localizable localizableWSP_0003_UNMARSHALLING_FAILED_END_TAG_DOES_NOT_MATCH(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0003_UNMARSHALLING_FAILED_END_TAG_DOES_NOT_MATCH", arg0, arg1);
    }

    /**
     * WSP0003: Policy model unmarshalling failed: Actual XML end tag does not match current element. Expected tag FQN: "{0}", actual tag FQN: "{1}"
     *
     */
    public static String WSP_0003_UNMARSHALLING_FAILED_END_TAG_DOES_NOT_MATCH(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0003_UNMARSHALLING_FAILED_END_TAG_DOES_NOT_MATCH(arg0, arg1));
    }

    public static Localizable localizableWSP_0007_UNEXPECTED_MODEL_NODE_TYPE_FOUND(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0007_UNEXPECTED_MODEL_NODE_TYPE_FOUND", arg0);
    }

    /**
     * WSP0007: Unexpected model node type ({0})found during policy expression content decomposition
     *
     */
    public static String WSP_0007_UNEXPECTED_MODEL_NODE_TYPE_FOUND(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0007_UNEXPECTED_MODEL_NODE_TYPE_FOUND(arg0));
    }

    public static Localizable localizableWSP_0086_FAILED_CREATE_READER(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0086_FAILED_CREATE_READER", arg0);
    }

    /**
     * WSP0086: Failed to create XMLEventReader for source {0}
     *
     */
    public static String WSP_0086_FAILED_CREATE_READER(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0086_FAILED_CREATE_READER(arg0));
    }

    public static Localizable localizableWSP_0077_ASSERTION_CREATOR_DOES_NOT_SUPPORT_ANY_URI(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0077_ASSERTION_CREATOR_DOES_NOT_SUPPORT_ANY_URI", arg0);
    }

    /**
     * WSP0077: Discovered policy assertion creator of class=''{0}'' does not support any URI
     *
     */
    public static String WSP_0077_ASSERTION_CREATOR_DOES_NOT_SUPPORT_ANY_URI(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0077_ASSERTION_CREATOR_DOES_NOT_SUPPORT_ANY_URI(arg0));
    }

    public static Localizable localizableWSP_0082_NO_SUBJECT_TYPE() {
        return MESSAGE_FACTORY.getMessage("WSP_0082_NO_SUBJECT_TYPE");
    }

    /**
     * WSP0082: Implementation fault. Failed to determine subject type.
     *
     */
    public static String WSP_0082_NO_SUBJECT_TYPE() {
        return LOCALIZER.localize(localizableWSP_0082_NO_SUBJECT_TYPE());
    }

    public static Localizable localizableWSP_0089_EXPECTED_ELEMENT(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("WSP_0089_EXPECTED_ELEMENT", arg0, arg1, arg2);
    }

    /**
     * WSP0089: Expected tag {0}, but read <{1}> at location {2}
     *
     */
    public static String WSP_0089_EXPECTED_ELEMENT(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSP_0089_EXPECTED_ELEMENT(arg0, arg1, arg2));
    }

    public static Localizable localizableWSP_0031_SERVICE_PARAM_MUST_NOT_BE_NULL() {
        return MESSAGE_FACTORY.getMessage("WSP_0031_SERVICE_PARAM_MUST_NOT_BE_NULL");
    }

    /**
     * WSP0031: Parameter 'service' must not be null
     *
     */
    public static String WSP_0031_SERVICE_PARAM_MUST_NOT_BE_NULL() {
        return LOCALIZER.localize(localizableWSP_0031_SERVICE_PARAM_MUST_NOT_BE_NULL());
    }

    public static Localizable localizableWSP_0030_SERVICE_PORT_OPERATION_FAULT_MSG_PARAM_MUST_NOT_BE_NULL(Object arg0, Object arg1, Object arg2, Object arg3) {
        return MESSAGE_FACTORY.getMessage("WSP_0030_SERVICE_PORT_OPERATION_FAULT_MSG_PARAM_MUST_NOT_BE_NULL", arg0, arg1, arg2, arg3);
    }

    /**
     * WSP0030: Parameters "service", "port", "operation" and "faultMessage" must not be null. (service={0}, port={1}, operation={2}, faultMessage={3})
     *
     */
    public static String WSP_0030_SERVICE_PORT_OPERATION_FAULT_MSG_PARAM_MUST_NOT_BE_NULL(Object arg0, Object arg1, Object arg2, Object arg3) {
        return LOCALIZER.localize(localizableWSP_0030_SERVICE_PORT_OPERATION_FAULT_MSG_PARAM_MUST_NOT_BE_NULL(arg0, arg1, arg2, arg3));
    }

    public static Localizable localizableWSP_0040_POLICY_REFERENCE_URI_ATTR_NOT_FOUND() {
        return MESSAGE_FACTORY.getMessage("WSP_0040_POLICY_REFERENCE_URI_ATTR_NOT_FOUND");
    }

    /**
     * WSP0040: Policy reference 'URI' attribute not found
     *
     */
    public static String WSP_0040_POLICY_REFERENCE_URI_ATTR_NOT_FOUND() {
        return LOCALIZER.localize(localizableWSP_0040_POLICY_REFERENCE_URI_ATTR_NOT_FOUND());
    }

    public static Localizable localizableWSP_0034_REMOVE_OPERATION_NOT_SUPPORTED() {
        return MESSAGE_FACTORY.getMessage("WSP_0034_REMOVE_OPERATION_NOT_SUPPORTED");
    }

    /**
     * WSP0034: Remove operation not supported by this iterator
     *
     */
    public static String WSP_0034_REMOVE_OPERATION_NOT_SUPPORTED() {
        return LOCALIZER.localize(localizableWSP_0034_REMOVE_OPERATION_NOT_SUPPORTED());
    }

    public static Localizable localizableWSP_0084_MESSAGE_TYPE_NO_MESSAGE() {
        return MESSAGE_FACTORY.getMessage("WSP_0084_MESSAGE_TYPE_NO_MESSAGE");
    }

    /**
     * WSP0084: The message type may not be NO_MESSAGE.
     *
     */
    public static String WSP_0084_MESSAGE_TYPE_NO_MESSAGE() {
        return LOCALIZER.localize(localizableWSP_0084_MESSAGE_TYPE_NO_MESSAGE());
    }

    public static Localizable localizableWSP_0004_UNEXPECTED_VISIBILITY_ATTR_VALUE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0004_UNEXPECTED_VISIBILITY_ATTR_VALUE", arg0);
    }

    /**
     * WSP0004: Unexpected visibility attribute value: {0}
     *
     */
    public static String WSP_0004_UNEXPECTED_VISIBILITY_ATTR_VALUE(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0004_UNEXPECTED_VISIBILITY_ATTR_VALUE(arg0));
    }

    public static Localizable localizableWSP_0074_CANNOT_CREATE_ASSERTION_BAD_TYPE(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("WSP_0074_CANNOT_CREATE_ASSERTION_BAD_TYPE", arg0, arg1, arg2);
    }

    /**
     * WSP0074: Cannot create AssertionData instance for this type of ModelNode: "{0}"; Supported types are "{1}" and "{2}"
     *
     */
    public static String WSP_0074_CANNOT_CREATE_ASSERTION_BAD_TYPE(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSP_0074_CANNOT_CREATE_ASSERTION_BAD_TYPE(arg0, arg1, arg2));
    }

    public static Localizable localizableWSP_0052_NUMBER_OF_ALTERNATIVE_COMBINATIONS_CREATED(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0052_NUMBER_OF_ALTERNATIVE_COMBINATIONS_CREATED", arg0);
    }

    /**
     * WSP0052: Number of policy alternative combinations created: {0}
     *
     */
    public static String WSP_0052_NUMBER_OF_ALTERNATIVE_COMBINATIONS_CREATED(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0052_NUMBER_OF_ALTERNATIVE_COMBINATIONS_CREATED(arg0));
    }

    public static Localizable localizableWSP_0037_PRIVATE_CONSTRUCTOR_DOES_NOT_TAKE_NULL() {
        return MESSAGE_FACTORY.getMessage("WSP_0037_PRIVATE_CONSTRUCTOR_DOES_NOT_TAKE_NULL");
    }

    /**
     * WSP0037: Private constructor must not receive 'null' argument as a initial policy assertion list
     *
     */
    public static String WSP_0037_PRIVATE_CONSTRUCTOR_DOES_NOT_TAKE_NULL() {
        return LOCALIZER.localize(localizableWSP_0037_PRIVATE_CONSTRUCTOR_DOES_NOT_TAKE_NULL());
    }

    public static Localizable localizableWSP_0067_ILLEGAL_CFG_FILE_SYNTAX() {
        return MESSAGE_FACTORY.getMessage("WSP_0067_ILLEGAL_CFG_FILE_SYNTAX");
    }

    /**
     * WSP0067: Illegal configuration-file syntax
     *
     */
    public static String WSP_0067_ILLEGAL_CFG_FILE_SYNTAX() {
        return LOCALIZER.localize(localizableWSP_0067_ILLEGAL_CFG_FILE_SYNTAX());
    }

    public static Localizable localizableWSP_0085_MESSAGE_FAULT_NO_NAME() {
        return MESSAGE_FACTORY.getMessage("WSP_0085_MESSAGE_FAULT_NO_NAME");
    }

    /**
     * WSP0085: Messages of type fault must have a name.
     *
     */
    public static String WSP_0085_MESSAGE_FAULT_NO_NAME() {
        return LOCALIZER.localize(localizableWSP_0085_MESSAGE_FAULT_NO_NAME());
    }

    public static Localizable localizableWSP_0050_OPERATION_NOT_SUPPORTED_FOR_THIS_BUT_POLICY_REFERENCE_NODE_TYPE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0050_OPERATION_NOT_SUPPORTED_FOR_THIS_BUT_POLICY_REFERENCE_NODE_TYPE", arg0);
    }

    /**
     * WSP0050: This operation is supported only for 'POLICY_REFERENCE' node type. It is not supported for the node type ''{0}''
     *
     */
    public static String WSP_0050_OPERATION_NOT_SUPPORTED_FOR_THIS_BUT_POLICY_REFERENCE_NODE_TYPE(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0050_OPERATION_NOT_SUPPORTED_FOR_THIS_BUT_POLICY_REFERENCE_NODE_TYPE(arg0));
    }

    public static Localizable localizableWSP_0042_POLICY_REFERENCE_NODE_EXPECTED_INSTEAD_OF(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0042_POLICY_REFERENCE_NODE_EXPECTED_INSTEAD_OF", arg0);
    }

    /**
     * WSP0042: Input model node argument is not a policy reference. Real node type: {0}
     *
     */
    public static String WSP_0042_POLICY_REFERENCE_NODE_EXPECTED_INSTEAD_OF(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0042_POLICY_REFERENCE_NODE_EXPECTED_INSTEAD_OF(arg0));
    }

    public static Localizable localizableWSP_0014_UNABLE_TO_INSTANTIATE_READER_FOR_STORAGE() {
        return MESSAGE_FACTORY.getMessage("WSP_0014_UNABLE_TO_INSTANTIATE_READER_FOR_STORAGE");
    }

    /**
     * WSP0014: Unable to instantiate XMLEventReader for given storage
     *
     */
    public static String WSP_0014_UNABLE_TO_INSTANTIATE_READER_FOR_STORAGE() {
        return LOCALIZER.localize(localizableWSP_0014_UNABLE_TO_INSTANTIATE_READER_FOR_STORAGE());
    }

    public static Localizable localizableWSP_0054_NO_MORE_ELEMS_IN_POLICY_MAP() {
        return MESSAGE_FACTORY.getMessage("WSP_0054_NO_MORE_ELEMS_IN_POLICY_MAP");
    }

    /**
     * WSP0054: There are no more elements in the policy map
     *
     */
    public static String WSP_0054_NO_MORE_ELEMS_IN_POLICY_MAP() {
        return LOCALIZER.localize(localizableWSP_0054_NO_MORE_ELEMS_IN_POLICY_MAP());
    }

    public static Localizable localizableWSP_0083_MESSAGE_TYPE_NULL() {
        return MESSAGE_FACTORY.getMessage("WSP_0083_MESSAGE_TYPE_NULL");
    }

    /**
     * WSP0083: The message type may not be null.
     *
     */
    public static String WSP_0083_MESSAGE_TYPE_NULL() {
        return LOCALIZER.localize(localizableWSP_0083_MESSAGE_TYPE_NULL());
    }

    public static Localizable localizableWSP_0011_UNABLE_TO_UNMARSHALL_POLICY_XML_ELEM_EXPECTED() {
        return MESSAGE_FACTORY.getMessage("WSP_0011_UNABLE_TO_UNMARSHALL_POLICY_XML_ELEM_EXPECTED");
    }

    /**
     * WSP0011: Failed to unmarshal policy expression. Expected XML element
     *
     */
    public static String WSP_0011_UNABLE_TO_UNMARSHALL_POLICY_XML_ELEM_EXPECTED() {
        return LOCALIZER.localize(localizableWSP_0011_UNABLE_TO_UNMARSHALL_POLICY_XML_ELEM_EXPECTED());
    }

    public static Localizable localizableWSP_0025_SPI_FAIL_SERVICE_MSG(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0025_SPI_FAIL_SERVICE_MSG", arg0, arg1);
    }

    /**
     * WSP0025: {0}: {1}
     *
     */
    public static String WSP_0025_SPI_FAIL_SERVICE_MSG(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0025_SPI_FAIL_SERVICE_MSG(arg0, arg1));
    }

    public static Localizable localizableWSP_0094_INVALID_URN() {
        return MESSAGE_FACTORY.getMessage("WSP_0094_INVALID_URN");
    }

    /**
     * WSP0094: Internal implementation error. Apparently failed to pass valid URN.
     *
     */
    public static String WSP_0094_INVALID_URN() {
        return LOCALIZER.localize(localizableWSP_0094_INVALID_URN());
    }

    public static Localizable localizableWSP_0026_SINGLE_EMPTY_ALTERNATIVE_COMBINATION_CREATED() {
        return MESSAGE_FACTORY.getMessage("WSP_0026_SINGLE_EMPTY_ALTERNATIVE_COMBINATION_CREATED");
    }

    /**
     * WSP0026: Single empty alternative combination created: Returning "anything allowed" policy
     *
     */
    public static String WSP_0026_SINGLE_EMPTY_ALTERNATIVE_COMBINATION_CREATED() {
        return LOCALIZER.localize(localizableWSP_0026_SINGLE_EMPTY_ALTERNATIVE_COMBINATION_CREATED());
    }

    public static Localizable localizableWSP_0078_ASSERTION_CREATOR_DISCOVERED(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0078_ASSERTION_CREATOR_DISCOVERED", arg0, arg1);
    }

    /**
     * WSP0078: Policy assertion creator discovered: class=''{0}'', supported namespace=''{1}''
     *
     */
    public static String WSP_0078_ASSERTION_CREATOR_DISCOVERED(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0078_ASSERTION_CREATOR_DISCOVERED(arg0, arg1));
    }

    public static Localizable localizableWSP_0041_POLICY_REFERENCE_NODE_FOUND_WITH_NO_POLICY_REFERENCE_IN_IT() {
        return MESSAGE_FACTORY.getMessage("WSP_0041_POLICY_REFERENCE_NODE_FOUND_WITH_NO_POLICY_REFERENCE_IN_IT");
    }

    /**
     * WSP0041: Unexpanded "POLICY_REFERENCE" node found containing no policy reference data
     *
     */
    public static String WSP_0041_POLICY_REFERENCE_NODE_FOUND_WITH_NO_POLICY_REFERENCE_IN_IT() {
        return LOCALIZER.localize(localizableWSP_0041_POLICY_REFERENCE_NODE_FOUND_WITH_NO_POLICY_REFERENCE_IN_IT());
    }

    public static Localizable localizableWSP_0039_POLICY_SRC_MODEL_INPUT_PARAMETER_MUST_NOT_BE_NULL() {
        return MESSAGE_FACTORY.getMessage("WSP_0039_POLICY_SRC_MODEL_INPUT_PARAMETER_MUST_NOT_BE_NULL");
    }

    /**
     * WSP0039: Policy source model input parameter must not be null
     *
     */
    public static String WSP_0039_POLICY_SRC_MODEL_INPUT_PARAMETER_MUST_NOT_BE_NULL() {
        return LOCALIZER.localize(localizableWSP_0039_POLICY_SRC_MODEL_INPUT_PARAMETER_MUST_NOT_BE_NULL());
    }

    public static Localizable localizableWSP_0070_ERROR_REGISTERING_ASSERTION_CREATOR(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0070_ERROR_REGISTERING_ASSERTION_CREATOR", arg0);
    }

    /**
     * WSP0070: Error registering policy assertion creator of class ''{0}'''. Supported domain nemaspace URI string must not be neither null nor empty!"
     *
     */
    public static String WSP_0070_ERROR_REGISTERING_ASSERTION_CREATOR(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0070_ERROR_REGISTERING_ASSERTION_CREATOR(arg0));
    }

    public static Localizable localizableWSP_0036_PRIVATE_METHOD_DOES_NOT_ACCEPT_NULL_OR_EMPTY_COLLECTION() {
        return MESSAGE_FACTORY.getMessage("WSP_0036_PRIVATE_METHOD_DOES_NOT_ACCEPT_NULL_OR_EMPTY_COLLECTION");
    }

    /**
     * WSP0036: This private method does not accept null or empty collection
     *
     */
    public static String WSP_0036_PRIVATE_METHOD_DOES_NOT_ACCEPT_NULL_OR_EMPTY_COLLECTION() {
        return LOCALIZER.localize(localizableWSP_0036_PRIVATE_METHOD_DOES_NOT_ACCEPT_NULL_OR_EMPTY_COLLECTION());
    }

    public static Localizable localizableWSP_0027_SERVICE_PROVIDER_NOT_FOUND(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0027_SERVICE_PROVIDER_NOT_FOUND", arg0);
    }

    /**
     * WSP0027: Service provider {0} not found
     *
     */
    public static String WSP_0027_SERVICE_PROVIDER_NOT_FOUND(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0027_SERVICE_PROVIDER_NOT_FOUND(arg0));
    }

    public static Localizable localizableWSP_0056_NEITHER_NULL_NOR_EMPTY_POLICY_COLLECTION_EXPECTED() {
        return MESSAGE_FACTORY.getMessage("WSP_0056_NEITHER_NULL_NOR_EMPTY_POLICY_COLLECTION_EXPECTED");
    }

    /**
     * WSP0056: Input policy collection is expected not to be null nor empty collection
     *
     */
    public static String WSP_0056_NEITHER_NULL_NOR_EMPTY_POLICY_COLLECTION_EXPECTED() {
        return LOCALIZER.localize(localizableWSP_0056_NEITHER_NULL_NOR_EMPTY_POLICY_COLLECTION_EXPECTED());
    }

    public static Localizable localizableWSP_0022_STORAGE_TYPE_NOT_SUPPORTED(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0022_STORAGE_TYPE_NOT_SUPPORTED", arg0);
    }

    /**
     * WSP0022: Storage type "{0}" is not supported
     *
     */
    public static String WSP_0022_STORAGE_TYPE_NOT_SUPPORTED(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0022_STORAGE_TYPE_NOT_SUPPORTED(arg0));
    }

    public static Localizable localizableWSP_0095_INVALID_BOOLEAN_VALUE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0095_INVALID_BOOLEAN_VALUE", arg0);
    }

    /**
     * WSP0095: A value of boolean type may have one of the values "true", "false", "1", "0". This value was "{0}".
     *
     */
    public static String WSP_0095_INVALID_BOOLEAN_VALUE(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0095_INVALID_BOOLEAN_VALUE(arg0));
    }

    public static Localizable localizableWSP_0059_MULTIPLE_ATTRS_WITH_SAME_NAME_DETECTED_FOR_ASSERTION(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0059_MULTIPLE_ATTRS_WITH_SAME_NAME_DETECTED_FOR_ASSERTION", arg0, arg1);
    }

    /**
     * WSP0059: Multiple attributes with the same name "{0}" detected for assertion "{1}"
     *
     */
    public static String WSP_0059_MULTIPLE_ATTRS_WITH_SAME_NAME_DETECTED_FOR_ASSERTION(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0059_MULTIPLE_ATTRS_WITH_SAME_NAME_DETECTED_FOR_ASSERTION(arg0, arg1));
    }

    public static Localizable localizableWSP_0047_POLICY_IS_NULL_RETURNING() {
        return MESSAGE_FACTORY.getMessage("WSP_0047_POLICY_IS_NULL_RETURNING");
    }

    /**
     * WSP0047: Policy is null, returning
     *
     */
    public static String WSP_0047_POLICY_IS_NULL_RETURNING() {
        return LOCALIZER.localize(localizableWSP_0047_POLICY_IS_NULL_RETURNING());
    }

    public static Localizable localizableWSP_0088_FAILED_PARSE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0088_FAILED_PARSE", arg0);
    }

    /**
     * WSP0088: Failed to parse XML document at location {0}
     *
     */
    public static String WSP_0088_FAILED_PARSE(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0088_FAILED_PARSE(arg0));
    }

    public static Localizable localizableWSP_0005_UNEXPECTED_POLICY_ELEMENT_FOUND_IN_ASSERTION_PARAM(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0005_UNEXPECTED_POLICY_ELEMENT_FOUND_IN_ASSERTION_PARAM", arg0);
    }

    /**
     * WSP0005: Unexpected nested policy element found in assertion parameter: {0}
     *
     */
    public static String WSP_0005_UNEXPECTED_POLICY_ELEMENT_FOUND_IN_ASSERTION_PARAM(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0005_UNEXPECTED_POLICY_ELEMENT_FOUND_IN_ASSERTION_PARAM(arg0));
    }

    public static Localizable localizableWSP_0009_UNEXPECTED_CDATA_ON_SOURCE_MODEL_NODE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0009_UNEXPECTED_CDATA_ON_SOURCE_MODEL_NODE", arg0, arg1);
    }

    /**
     * WSP0009: Unexpected character data on current policy source model node "{0}" : data = "{1}"
     *
     */
    public static String WSP_0009_UNEXPECTED_CDATA_ON_SOURCE_MODEL_NODE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0009_UNEXPECTED_CDATA_ON_SOURCE_MODEL_NODE(arg0, arg1));
    }

    public static Localizable localizableWSP_0024_SPI_FAIL_SERVICE_URL_LINE_MSG(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("WSP_0024_SPI_FAIL_SERVICE_URL_LINE_MSG", arg0, arg1, arg2);
    }

    /**
     * WSP0024: {0}:{1}: {2}
     *
     */
    public static String WSP_0024_SPI_FAIL_SERVICE_URL_LINE_MSG(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSP_0024_SPI_FAIL_SERVICE_URL_LINE_MSG(arg0, arg1, arg2));
    }

    public static Localizable localizableWSP_0057_N_ALTERNATIVE_COMBINATIONS_M_POLICY_ALTERNATIVES_CREATED(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0057_N_ALTERNATIVE_COMBINATIONS_M_POLICY_ALTERNATIVES_CREATED", arg0, arg1);
    }

    /**
     * WSP0057: {0} policy alternative combinations created: Returning created policy with {1} inequal policy alternatives
     *
     */
    public static String WSP_0057_N_ALTERNATIVE_COMBINATIONS_M_POLICY_ALTERNATIVES_CREATED(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0057_N_ALTERNATIVE_COMBINATIONS_M_POLICY_ALTERNATIVES_CREATED(arg0, arg1));
    }

    public static Localizable localizableWSP_0020_SUBJECT_PARAM_MUST_NOT_BE_NULL() {
        return MESSAGE_FACTORY.getMessage("WSP_0020_SUBJECT_PARAM_MUST_NOT_BE_NULL");
    }

    /**
     * WSP0020: Parameter subject must not be null
     *
     */
    public static String WSP_0020_SUBJECT_PARAM_MUST_NOT_BE_NULL() {
        return LOCALIZER.localize(localizableWSP_0020_SUBJECT_PARAM_MUST_NOT_BE_NULL());
    }

    public static Localizable localizableWSP_0071_ERROR_MULTIPLE_ASSERTION_CREATORS_FOR_NAMESPACE(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("WSP_0071_ERROR_MULTIPLE_ASSERTION_CREATORS_FOR_NAMESPACE", arg0, arg1, arg2);
    }

    /**
     * WSP0071: Multiple policy assertion creators try to register for namespace ''{0}''. Old creator`s class: ''{1}'', new creator`s class: ''{2}''.
     *
     */
    public static String WSP_0071_ERROR_MULTIPLE_ASSERTION_CREATORS_FOR_NAMESPACE(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSP_0071_ERROR_MULTIPLE_ASSERTION_CREATORS_FOR_NAMESPACE(arg0, arg1, arg2));
    }

    public static Localizable localizableWSP_0038_POLICY_TO_ATTACH_MUST_NOT_BE_NULL() {
        return MESSAGE_FACTORY.getMessage("WSP_0038_POLICY_TO_ATTACH_MUST_NOT_BE_NULL");
    }

    /**
     * WSP0038: Policy to be attached must not be null
     *
     */
    public static String WSP_0038_POLICY_TO_ATTACH_MUST_NOT_BE_NULL() {
        return LOCALIZER.localize(localizableWSP_0038_POLICY_TO_ATTACH_MUST_NOT_BE_NULL());
    }

    public static Localizable localizableWSP_0033_SERVICE_AND_PORT_PARAM_MUST_NOT_BE_NULL(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0033_SERVICE_AND_PORT_PARAM_MUST_NOT_BE_NULL", arg0, arg1);
    }

    /**
     * WSP0033: Parameters "service" and "port" must not be null. (service={0}, port={1})
     *
     */
    public static String WSP_0033_SERVICE_AND_PORT_PARAM_MUST_NOT_BE_NULL(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0033_SERVICE_AND_PORT_PARAM_MUST_NOT_BE_NULL(arg0, arg1));
    }

    public static Localizable localizableWSP_0060_POLICY_ELEMENT_TYPE_UNKNOWN(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0060_POLICY_ELEMENT_TYPE_UNKNOWN", arg0);
    }

    /**
     * WSP0060: Unknown policy element type "{0}"
     *
     */
    public static String WSP_0060_POLICY_ELEMENT_TYPE_UNKNOWN(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0060_POLICY_ELEMENT_TYPE_UNKNOWN(arg0));
    }

    public static Localizable localizableWSP_0013_UNABLE_TO_SET_PARENT_MODEL_ON_ROOT() {
        return MESSAGE_FACTORY.getMessage("WSP_0013_UNABLE_TO_SET_PARENT_MODEL_ON_ROOT");
    }

    /**
     * WSP0013: Unable to set parent model on root model node
     *
     */
    public static String WSP_0013_UNABLE_TO_SET_PARENT_MODEL_ON_ROOT() {
        return LOCALIZER.localize(localizableWSP_0013_UNABLE_TO_SET_PARENT_MODEL_ON_ROOT());
    }

    public static Localizable localizableWSP_0019_SUBOPTIMAL_ALTERNATIVE_SELECTED(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0019_SUBOPTIMAL_ALTERNATIVE_SELECTED", arg0);
    }

    /**
     * WSP0019: Suboptimal policy alternative selected on the client side with fitness "{0}".
     *
     */
    public static String WSP_0019_SUBOPTIMAL_ALTERNATIVE_SELECTED(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0019_SUBOPTIMAL_ALTERNATIVE_SELECTED(arg0));
    }

    public static Localizable localizableWSP_0073_CREATE_CHILD_NODE_OPERATION_NOT_SUPPORTED(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0073_CREATE_CHILD_NODE_OPERATION_NOT_SUPPORTED", arg0, arg1);
    }

    /**
     * WSP0073: Cannot create child node of type ''{0}'' in the node of type ''{1}''. Create operation is not supported for this combination of node types.
     *
     */
    public static String WSP_0073_CREATE_CHILD_NODE_OPERATION_NOT_SUPPORTED(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0073_CREATE_CHILD_NODE_OPERATION_NOT_SUPPORTED(arg0, arg1));
    }

    public static Localizable localizableWSP_0002_UNRECOGNIZED_SCOPE_TYPE(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0002_UNRECOGNIZED_SCOPE_TYPE", arg0);
    }

    /**
     * WSP0002: Unrecoginzed scope type: "{0}"
     *
     */
    public static String WSP_0002_UNRECOGNIZED_SCOPE_TYPE(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0002_UNRECOGNIZED_SCOPE_TYPE(arg0));
    }

    public static Localizable localizableWSP_0062_INPUT_PARAMS_MUST_NOT_BE_NULL() {
        return MESSAGE_FACTORY.getMessage("WSP_0062_INPUT_PARAMS_MUST_NOT_BE_NULL");
    }

    /**
     * WSP0062: Input parameters must not be 'null'
     *
     */
    public static String WSP_0062_INPUT_PARAMS_MUST_NOT_BE_NULL() {
        return LOCALIZER.localize(localizableWSP_0062_INPUT_PARAMS_MUST_NOT_BE_NULL());
    }

    public static Localizable localizableWSP_0063_ERROR_WHILE_CONSTRUCTING_EXCEPTION(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0063_ERROR_WHILE_CONSTRUCTING_EXCEPTION", arg0);
    }

    /**
     * WSP0063: Unexpected exception occured while constructing exception of class "{0}".
     *
     */
    public static String WSP_0063_ERROR_WHILE_CONSTRUCTING_EXCEPTION(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0063_ERROR_WHILE_CONSTRUCTING_EXCEPTION(arg0));
    }

    public static Localizable localizableWSP_0021_SUBJECT_AND_POLICY_PARAM_MUST_NOT_BE_NULL(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0021_SUBJECT_AND_POLICY_PARAM_MUST_NOT_BE_NULL", arg0, arg1);
    }

    /**
     * WSP0021: Parameters "subject" and "policy" must not be null. (subject={0}, policy={1})
     *
     */
    public static String WSP_0021_SUBJECT_AND_POLICY_PARAM_MUST_NOT_BE_NULL(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0021_SUBJECT_AND_POLICY_PARAM_MUST_NOT_BE_NULL(arg0, arg1));
    }

    public static Localizable localizableWSP_0075_PROBLEMATIC_ASSERTION_STATE(Object arg0, Object arg1) {
        return MESSAGE_FACTORY.getMessage("WSP_0075_PROBLEMATIC_ASSERTION_STATE", arg0, arg1);
    }

    /**
     * WSP0075: Policy assertion "{0}" was evaluated as "{1}".
     *
     */
    public static String WSP_0075_PROBLEMATIC_ASSERTION_STATE(Object arg0, Object arg1) {
        return LOCALIZER.localize(localizableWSP_0075_PROBLEMATIC_ASSERTION_STATE(arg0, arg1));
    }

    public static Localizable localizableWSP_0006_UNEXPECTED_MULTIPLE_POLICY_NODES() {
        return MESSAGE_FACTORY.getMessage("WSP_0006_UNEXPECTED_MULTIPLE_POLICY_NODES");
    }

    /**
     * WSP0006: Unexpected multiple nested policy nodes within a single assertion
     *
     */
    public static String WSP_0006_UNEXPECTED_MULTIPLE_POLICY_NODES() {
        return LOCALIZER.localize(localizableWSP_0006_UNEXPECTED_MULTIPLE_POLICY_NODES());
    }

    public static Localizable localizableWSP_0092_CHARACTER_DATA_UNEXPECTED(Object arg0, Object arg1, Object arg2) {
        return MESSAGE_FACTORY.getMessage("WSP_0092_CHARACTER_DATA_UNEXPECTED", arg0, arg1, arg2);
    }

    /**
     * WSP0092: Character data in unexpected element {0}, character data = {1}, location = {2}
     *
     */
    public static String WSP_0092_CHARACTER_DATA_UNEXPECTED(Object arg0, Object arg1, Object arg2) {
        return LOCALIZER.localize(localizableWSP_0092_CHARACTER_DATA_UNEXPECTED(arg0, arg1, arg2));
    }

    public static Localizable localizableWSP_0069_EXCEPTION_WHILE_RETRIEVING_EFFECTIVE_POLICY_FOR_KEY(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0069_EXCEPTION_WHILE_RETRIEVING_EFFECTIVE_POLICY_FOR_KEY", arg0);
    }

    /**
     * WSP0069: Exception occured while retrieving effective policy for given key {0}
     *
     */
    public static String WSP_0069_EXCEPTION_WHILE_RETRIEVING_EFFECTIVE_POLICY_FOR_KEY(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0069_EXCEPTION_WHILE_RETRIEVING_EFFECTIVE_POLICY_FOR_KEY(arg0));
    }

    public static Localizable localizableWSP_0010_UNEXPANDED_POLICY_REFERENCE_NODE_FOUND_REFERENCING(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0010_UNEXPANDED_POLICY_REFERENCE_NODE_FOUND_REFERENCING", arg0);
    }

    /**
     * WSP0010: Unexpanded "POLICY_REFERENCE" node found referencing {0}
     *
     */
    public static String WSP_0010_UNEXPANDED_POLICY_REFERENCE_NODE_FOUND_REFERENCING(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0010_UNEXPANDED_POLICY_REFERENCE_NODE_FOUND_REFERENCING(arg0));
    }

    public static Localizable localizableWSP_0035_RECONFIGURE_ALTERNATIVES(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0035_RECONFIGURE_ALTERNATIVES", arg0);
    }

    /**
     * WSP0035: Policy "{0}" contains more than one policy alternative. Please reconfigure the service with only one policy alternative.
     *
     */
    public static String WSP_0035_RECONFIGURE_ALTERNATIVES(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0035_RECONFIGURE_ALTERNATIVES(arg0));
    }

    public static Localizable localizableWSP_0066_ILLEGAL_PROVIDER_CLASSNAME(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0066_ILLEGAL_PROVIDER_CLASSNAME", arg0);
    }

    /**
     * WSP0066: Illegal provider-class name: {0}
     *
     */
    public static String WSP_0066_ILLEGAL_PROVIDER_CLASSNAME(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0066_ILLEGAL_PROVIDER_CLASSNAME(arg0));
    }

    public static Localizable localizableWSP_0076_NO_SERVICE_PROVIDERS_FOUND(Object arg0) {
        return MESSAGE_FACTORY.getMessage("WSP_0076_NO_SERVICE_PROVIDERS_FOUND", arg0);
    }

    /**
     * WSP0076: Policy engine could not locate any service providers implementing "{0}" interface. Please, check "META-INF/services" directory in your "webservices-rt.jar".
     *
     */
    public static String WSP_0076_NO_SERVICE_PROVIDERS_FOUND(Object arg0) {
        return LOCALIZER.localize(localizableWSP_0076_NO_SERVICE_PROVIDERS_FOUND(arg0));
    }

    public static Localizable localizableWSP_0049_PARENT_MODEL_CAN_NOT_BE_CHANGED() {
        return MESSAGE_FACTORY.getMessage("WSP_0049_PARENT_MODEL_CAN_NOT_BE_CHANGED");
    }

    /**
     * WSP0049: The parent model may not be changed on a child node which is not a root of the policy source model tree
     *
     */
    public static String WSP_0049_PARENT_MODEL_CAN_NOT_BE_CHANGED() {
        return LOCALIZER.localize(localizableWSP_0049_PARENT_MODEL_CAN_NOT_BE_CHANGED());
    }

    private static class BundleSupplier
        implements ResourceBundleSupplier
    {


        public ResourceBundle getResourceBundle(Locale locale) {
            return ResourceBundle.getBundle(BUNDLE_NAME, locale);
        }

    }

}
