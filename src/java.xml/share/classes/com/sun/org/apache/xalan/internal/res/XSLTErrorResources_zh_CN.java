/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xalan.internal.res;

import java.util.ListResourceBundle;

/**
 * Set up error messages.
 * We build a two dimensional array of message keys and
 * message strings. In order to add a new message here,
 * you need to first add a String constant. And
 *  you need to enter key , value pair as part of contents
 * Array. You also need to update MAX_CODE for error strings
 * and MAX_WARNING for warnings ( Needed for only information
 * purpose )
 * @LastModified: Dec 2024
 */
public class XSLTErrorResources_zh_CN extends ListResourceBundle
{

/*
 * This file contains error and warning messages related to Xalan Error
 * Handling.
 *
 *  General notes to translators:
 *
 *  1) Xalan (or more properly, Xalan-interpretive) and XSLTC are names of
 *     components.
 *     XSLT is an acronym for "XML Stylesheet Language: Transformations".
 *     XSLTC is an acronym for XSLT Compiler.
 *
 *  2) A stylesheet is a description of how to transform an input XML document
 *     into a resultant XML document (or HTML document or text).  The
 *     stylesheet itself is described in the form of an XML document.
 *
 *  3) A template is a component of a stylesheet that is used to match a
 *     particular portion of an input document and specifies the form of the
 *     corresponding portion of the output document.
 *
 *  4) An element is a mark-up tag in an XML document; an attribute is a
 *     modifier on the tag.  For example, in <elem attr='val' attr2='val2'>
 *     "elem" is an element name, "attr" and "attr2" are attribute names with
 *     the values "val" and "val2", respectively.
 *
 *  5) A namespace declaration is a special attribute that is used to associate
 *     a prefix with a URI (the namespace).  The meanings of element names and
 *     attribute names that use that prefix are defined with respect to that
 *     namespace.
 *
 *  6) "Translet" is an invented term that describes the class file that
 *     results from compiling an XML stylesheet into a Java class.
 *
 *  7) XPath is a specification that describes a notation for identifying
 *     nodes in a tree-structured representation of an XML document.  An
 *     instance of that notation is referred to as an XPath expression.
 *
 */

  /*
   * Static variables
   */
  public static final String ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX =
        "ER_INVALID_SET_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX";

  public static final String ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX_FOR_DEFAULT =
        "ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX_FOR_DEFAULT";

  public static final String ER_NO_CURLYBRACE = "ER_NO_CURLYBRACE";
  public static final String ER_FUNCTION_NOT_SUPPORTED = "ER_FUNCTION_NOT_SUPPORTED";
  public static final String ER_ILLEGAL_ATTRIBUTE = "ER_ILLEGAL_ATTRIBUTE";
  public static final String ER_NULL_SOURCENODE_APPLYIMPORTS = "ER_NULL_SOURCENODE_APPLYIMPORTS";
  public static final String ER_CANNOT_ADD = "ER_CANNOT_ADD";
  public static final String ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES="ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES";
  public static final String ER_NO_NAME_ATTRIB = "ER_NO_NAME_ATTRIB";
  public static final String ER_TEMPLATE_NOT_FOUND = "ER_TEMPLATE_NOT_FOUND";
  public static final String ER_CANT_RESOLVE_NAME_AVT = "ER_CANT_RESOLVE_NAME_AVT";
  public static final String ER_REQUIRES_ATTRIB = "ER_REQUIRES_ATTRIB";
  public static final String ER_MUST_HAVE_TEST_ATTRIB = "ER_MUST_HAVE_TEST_ATTRIB";
  public static final String ER_BAD_VAL_ON_LEVEL_ATTRIB =
         "ER_BAD_VAL_ON_LEVEL_ATTRIB";
  public static final String ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML =
         "ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML";
  public static final String ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME =
         "ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME";
  public static final String ER_NEED_MATCH_ATTRIB = "ER_NEED_MATCH_ATTRIB";
  public static final String ER_NEED_NAME_OR_MATCH_ATTRIB =
         "ER_NEED_NAME_OR_MATCH_ATTRIB";
  public static final String ER_CANT_RESOLVE_NSPREFIX =
         "ER_CANT_RESOLVE_NSPREFIX";
  public static final String ER_ILLEGAL_VALUE = "ER_ILLEGAL_VALUE";
  public static final String ER_NO_OWNERDOC = "ER_NO_OWNERDOC";
  public static final String ER_ELEMTEMPLATEELEM_ERR ="ER_ELEMTEMPLATEELEM_ERR";
  public static final String ER_NULL_CHILD = "ER_NULL_CHILD";
  public static final String ER_NEED_SELECT_ATTRIB = "ER_NEED_SELECT_ATTRIB";
  public static final String ER_NEED_TEST_ATTRIB = "ER_NEED_TEST_ATTRIB";
  public static final String ER_NEED_NAME_ATTRIB = "ER_NEED_NAME_ATTRIB";
  public static final String ER_NO_CONTEXT_OWNERDOC = "ER_NO_CONTEXT_OWNERDOC";
  public static final String ER_COULD_NOT_CREATE_XML_PROC_LIAISON =
         "ER_COULD_NOT_CREATE_XML_PROC_LIAISON";
  public static final String ER_PROCESS_NOT_SUCCESSFUL =
         "ER_PROCESS_NOT_SUCCESSFUL";
  public static final String ER_NOT_SUCCESSFUL = "ER_NOT_SUCCESSFUL";
  public static final String ER_ENCODING_NOT_SUPPORTED =
         "ER_ENCODING_NOT_SUPPORTED";
  public static final String ER_COULD_NOT_CREATE_TRACELISTENER =
         "ER_COULD_NOT_CREATE_TRACELISTENER";
  public static final String ER_KEY_REQUIRES_NAME_ATTRIB =
         "ER_KEY_REQUIRES_NAME_ATTRIB";
  public static final String ER_KEY_REQUIRES_MATCH_ATTRIB =
         "ER_KEY_REQUIRES_MATCH_ATTRIB";
  public static final String ER_KEY_REQUIRES_USE_ATTRIB =
         "ER_KEY_REQUIRES_USE_ATTRIB";
  public static final String ER_REQUIRES_ELEMENTS_ATTRIB =
         "ER_REQUIRES_ELEMENTS_ATTRIB";
  public static final String ER_MISSING_PREFIX_ATTRIB =
         "ER_MISSING_PREFIX_ATTRIB";
  public static final String ER_BAD_STYLESHEET_URL = "ER_BAD_STYLESHEET_URL";
  public static final String ER_FILE_NOT_FOUND = "ER_FILE_NOT_FOUND";
  public static final String ER_IOEXCEPTION = "ER_IOEXCEPTION";
  public static final String ER_NO_HREF_ATTRIB = "ER_NO_HREF_ATTRIB";
  public static final String ER_STYLESHEET_INCLUDES_ITSELF =
         "ER_STYLESHEET_INCLUDES_ITSELF";
  public static final String ER_PROCESSINCLUDE_ERROR ="ER_PROCESSINCLUDE_ERROR";
  public static final String ER_MISSING_LANG_ATTRIB = "ER_MISSING_LANG_ATTRIB";
  public static final String ER_MISSING_CONTAINER_ELEMENT_COMPONENT =
         "ER_MISSING_CONTAINER_ELEMENT_COMPONENT";
  public static final String ER_CAN_ONLY_OUTPUT_TO_ELEMENT =
         "ER_CAN_ONLY_OUTPUT_TO_ELEMENT";
  public static final String ER_PROCESS_ERROR = "ER_PROCESS_ERROR";
  public static final String ER_UNIMPLNODE_ERROR = "ER_UNIMPLNODE_ERROR";
  public static final String ER_NO_SELECT_EXPRESSION ="ER_NO_SELECT_EXPRESSION";
  public static final String ER_CANNOT_SERIALIZE_XSLPROCESSOR =
         "ER_CANNOT_SERIALIZE_XSLPROCESSOR";
  public static final String ER_NO_INPUT_STYLESHEET = "ER_NO_INPUT_STYLESHEET";
  public static final String ER_FAILED_PROCESS_STYLESHEET =
         "ER_FAILED_PROCESS_STYLESHEET";
  public static final String ER_COULDNT_PARSE_DOC = "ER_COULDNT_PARSE_DOC";
  public static final String ER_COULDNT_FIND_FRAGMENT =
         "ER_COULDNT_FIND_FRAGMENT";
  public static final String ER_NODE_NOT_ELEMENT = "ER_NODE_NOT_ELEMENT";
  public static final String ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB =
         "ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB";
  public static final String ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB =
         "ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB";
  public static final String ER_NO_CLONE_OF_DOCUMENT_FRAG =
         "ER_NO_CLONE_OF_DOCUMENT_FRAG";
  public static final String ER_CANT_CREATE_ITEM = "ER_CANT_CREATE_ITEM";
  public static final String ER_XMLSPACE_ILLEGAL_VALUE =
         "ER_XMLSPACE_ILLEGAL_VALUE";
  public static final String ER_NO_XSLKEY_DECLARATION =
         "ER_NO_XSLKEY_DECLARATION";
  public static final String ER_CANT_CREATE_URL = "ER_CANT_CREATE_URL";
  public static final String ER_XSLFUNCTIONS_UNSUPPORTED =
         "ER_XSLFUNCTIONS_UNSUPPORTED";
  public static final String ER_PROCESSOR_ERROR = "ER_PROCESSOR_ERROR";
  public static final String ER_NOT_ALLOWED_INSIDE_STYLESHEET =
         "ER_NOT_ALLOWED_INSIDE_STYLESHEET";
  public static final String ER_RESULTNS_NOT_SUPPORTED =
         "ER_RESULTNS_NOT_SUPPORTED";
  public static final String ER_DEFAULTSPACE_NOT_SUPPORTED =
         "ER_DEFAULTSPACE_NOT_SUPPORTED";
  public static final String ER_INDENTRESULT_NOT_SUPPORTED =
         "ER_INDENTRESULT_NOT_SUPPORTED";
  public static final String ER_ILLEGAL_ATTRIB = "ER_ILLEGAL_ATTRIB";
  public static final String ER_UNKNOWN_XSL_ELEM = "ER_UNKNOWN_XSL_ELEM";
  public static final String ER_BAD_XSLSORT_USE = "ER_BAD_XSLSORT_USE";
  public static final String ER_MISPLACED_XSLWHEN = "ER_MISPLACED_XSLWHEN";
  public static final String ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE =
         "ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE";
  public static final String ER_MISPLACED_XSLOTHERWISE =
         "ER_MISPLACED_XSLOTHERWISE";
  public static final String ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE =
         "ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE";
  public static final String ER_NOT_ALLOWED_INSIDE_TEMPLATE =
         "ER_NOT_ALLOWED_INSIDE_TEMPLATE";
  public static final String ER_UNKNOWN_EXT_NS_PREFIX =
         "ER_UNKNOWN_EXT_NS_PREFIX";
  public static final String ER_IMPORTS_AS_FIRST_ELEM =
         "ER_IMPORTS_AS_FIRST_ELEM";
  public static final String ER_IMPORTING_ITSELF = "ER_IMPORTING_ITSELF";
  public static final String ER_XMLSPACE_ILLEGAL_VAL ="ER_XMLSPACE_ILLEGAL_VAL";
  public static final String ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL =
         "ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL";
  public static final String ER_SAX_EXCEPTION = "ER_SAX_EXCEPTION";
  public static final String ER_XSLT_ERROR = "ER_XSLT_ERROR";
  public static final String ER_CURRENCY_SIGN_ILLEGAL=
         "ER_CURRENCY_SIGN_ILLEGAL";
  public static final String ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM =
         "ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM";
  public static final String ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER =
         "ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER";
  public static final String ER_REDIRECT_COULDNT_GET_FILENAME =
         "ER_REDIRECT_COULDNT_GET_FILENAME";
  public static final String ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT =
         "ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT";
  public static final String ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX =
         "ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX";
  public static final String ER_MISSING_NS_URI = "ER_MISSING_NS_URI";
  public static final String ER_MISSING_ARG_FOR_OPTION =
         "ER_MISSING_ARG_FOR_OPTION";
  public static final String ER_INVALID_OPTION = "ER_INVALID_OPTION";
  public static final String ER_MALFORMED_FORMAT_STRING =
         "ER_MALFORMED_FORMAT_STRING";
  public static final String ER_STYLESHEET_REQUIRES_VERSION_ATTRIB =
         "ER_STYLESHEET_REQUIRES_VERSION_ATTRIB";
  public static final String ER_ILLEGAL_ATTRIBUTE_VALUE =
         "ER_ILLEGAL_ATTRIBUTE_VALUE";
  public static final String ER_CHOOSE_REQUIRES_WHEN ="ER_CHOOSE_REQUIRES_WHEN";
  public static final String ER_NO_APPLY_IMPORT_IN_FOR_EACH =
         "ER_NO_APPLY_IMPORT_IN_FOR_EACH";
  public static final String ER_CANT_USE_DTM_FOR_OUTPUT =
         "ER_CANT_USE_DTM_FOR_OUTPUT";
  public static final String ER_CANT_USE_DTM_FOR_INPUT =
         "ER_CANT_USE_DTM_FOR_INPUT";
  public static final String ER_CALL_TO_EXT_FAILED = "ER_CALL_TO_EXT_FAILED";
  public static final String ER_PREFIX_MUST_RESOLVE = "ER_PREFIX_MUST_RESOLVE";
  public static final String ER_INVALID_UTF16_SURROGATE =
         "ER_INVALID_UTF16_SURROGATE";
  public static final String ER_XSLATTRSET_USED_ITSELF =
         "ER_XSLATTRSET_USED_ITSELF";
  public static final String ER_CANNOT_MIX_XERCESDOM ="ER_CANNOT_MIX_XERCESDOM";
  public static final String ER_TOO_MANY_LISTENERS = "ER_TOO_MANY_LISTENERS";
  public static final String ER_IN_ELEMTEMPLATEELEM_READOBJECT =
         "ER_IN_ELEMTEMPLATEELEM_READOBJECT";
  public static final String ER_DUPLICATE_NAMED_TEMPLATE =
         "ER_DUPLICATE_NAMED_TEMPLATE";
  public static final String ER_INVALID_KEY_CALL = "ER_INVALID_KEY_CALL";
  public static final String ER_REFERENCING_ITSELF = "ER_REFERENCING_ITSELF";
  public static final String ER_ILLEGAL_DOMSOURCE_INPUT =
         "ER_ILLEGAL_DOMSOURCE_INPUT";
  public static final String ER_CLASS_NOT_FOUND_FOR_OPTION =
         "ER_CLASS_NOT_FOUND_FOR_OPTION";
  public static final String ER_REQUIRED_ELEM_NOT_FOUND =
         "ER_REQUIRED_ELEM_NOT_FOUND";
  public static final String ER_INPUT_CANNOT_BE_NULL ="ER_INPUT_CANNOT_BE_NULL";
  public static final String ER_URI_CANNOT_BE_NULL = "ER_URI_CANNOT_BE_NULL";
  public static final String ER_FILE_CANNOT_BE_NULL = "ER_FILE_CANNOT_BE_NULL";
  public static final String ER_SOURCE_CANNOT_BE_NULL =
         "ER_SOURCE_CANNOT_BE_NULL";
  public static final String ER_CANNOT_INIT_BSFMGR = "ER_CANNOT_INIT_BSFMGR";
  public static final String ER_CANNOT_CMPL_EXTENSN = "ER_CANNOT_CMPL_EXTENSN";
  public static final String ER_CANNOT_CREATE_EXTENSN =
         "ER_CANNOT_CREATE_EXTENSN";
  public static final String ER_INSTANCE_MTHD_CALL_REQUIRES =
         "ER_INSTANCE_MTHD_CALL_REQUIRES";
  public static final String ER_INVALID_ELEMENT_NAME ="ER_INVALID_ELEMENT_NAME";
  public static final String ER_ELEMENT_NAME_METHOD_STATIC =
         "ER_ELEMENT_NAME_METHOD_STATIC";
  public static final String ER_EXTENSION_FUNC_UNKNOWN =
         "ER_EXTENSION_FUNC_UNKNOWN";
  public static final String ER_MORE_MATCH_CONSTRUCTOR =
         "ER_MORE_MATCH_CONSTRUCTOR";
  public static final String ER_MORE_MATCH_METHOD = "ER_MORE_MATCH_METHOD";
  public static final String ER_MORE_MATCH_ELEMENT = "ER_MORE_MATCH_ELEMENT";
  public static final String ER_INVALID_CONTEXT_PASSED =
         "ER_INVALID_CONTEXT_PASSED";
  public static final String ER_POOL_EXISTS = "ER_POOL_EXISTS";
  public static final String ER_NO_DRIVER_NAME = "ER_NO_DRIVER_NAME";
  public static final String ER_NO_URL = "ER_NO_URL";
  public static final String ER_POOL_SIZE_LESSTHAN_ONE =
         "ER_POOL_SIZE_LESSTHAN_ONE";
  public static final String ER_INVALID_DRIVER = "ER_INVALID_DRIVER";
  public static final String ER_NO_STYLESHEETROOT = "ER_NO_STYLESHEETROOT";
  public static final String ER_ILLEGAL_XMLSPACE_VALUE =
         "ER_ILLEGAL_XMLSPACE_VALUE";
  public static final String ER_PROCESSFROMNODE_FAILED =
         "ER_PROCESSFROMNODE_FAILED";
  public static final String ER_RESOURCE_COULD_NOT_LOAD =
         "ER_RESOURCE_COULD_NOT_LOAD";
  public static final String ER_BUFFER_SIZE_LESSTHAN_ZERO =
         "ER_BUFFER_SIZE_LESSTHAN_ZERO";
  public static final String ER_UNKNOWN_ERROR_CALLING_EXTENSION =
         "ER_UNKNOWN_ERROR_CALLING_EXTENSION";
  public static final String ER_NO_NAMESPACE_DECL = "ER_NO_NAMESPACE_DECL";
  public static final String ER_ELEM_CONTENT_NOT_ALLOWED =
         "ER_ELEM_CONTENT_NOT_ALLOWED";
  public static final String ER_STYLESHEET_DIRECTED_TERMINATION =
         "ER_STYLESHEET_DIRECTED_TERMINATION";
  public static final String ER_ONE_OR_TWO = "ER_ONE_OR_TWO";
  public static final String ER_TWO_OR_THREE = "ER_TWO_OR_THREE";
  public static final String ER_COULD_NOT_LOAD_RESOURCE =
         "ER_COULD_NOT_LOAD_RESOURCE";
  public static final String ER_CANNOT_INIT_DEFAULT_TEMPLATES =
         "ER_CANNOT_INIT_DEFAULT_TEMPLATES";
  public static final String ER_RESULT_NULL = "ER_RESULT_NULL";
  public static final String ER_RESULT_COULD_NOT_BE_SET =
         "ER_RESULT_COULD_NOT_BE_SET";
  public static final String ER_NO_OUTPUT_SPECIFIED = "ER_NO_OUTPUT_SPECIFIED";
  public static final String ER_CANNOT_TRANSFORM_TO_RESULT_TYPE =
         "ER_CANNOT_TRANSFORM_TO_RESULT_TYPE";
  public static final String ER_CANNOT_TRANSFORM_SOURCE_TYPE =
         "ER_CANNOT_TRANSFORM_SOURCE_TYPE";
  public static final String ER_NULL_CONTENT_HANDLER ="ER_NULL_CONTENT_HANDLER";
  public static final String ER_NULL_ERROR_HANDLER = "ER_NULL_ERROR_HANDLER";
  public static final String ER_CANNOT_CALL_PARSE = "ER_CANNOT_CALL_PARSE";
  public static final String ER_NO_PARENT_FOR_FILTER ="ER_NO_PARENT_FOR_FILTER";
  public static final String ER_NO_STYLESHEET_IN_MEDIA =
         "ER_NO_STYLESHEET_IN_MEDIA";
  public static final String ER_NO_STYLESHEET_PI = "ER_NO_STYLESHEET_PI";
  public static final String ER_NOT_SUPPORTED = "ER_NOT_SUPPORTED";
  public static final String ER_PROPERTY_VALUE_BOOLEAN =
         "ER_PROPERTY_VALUE_BOOLEAN";
  public static final String ER_COULD_NOT_FIND_EXTERN_SCRIPT =
         "ER_COULD_NOT_FIND_EXTERN_SCRIPT";
  public static final String ER_RESOURCE_COULD_NOT_FIND =
         "ER_RESOURCE_COULD_NOT_FIND";
  public static final String ER_OUTPUT_PROPERTY_NOT_RECOGNIZED =
         "ER_OUTPUT_PROPERTY_NOT_RECOGNIZED";
  public static final String ER_FAILED_CREATING_ELEMLITRSLT =
         "ER_FAILED_CREATING_ELEMLITRSLT";
  public static final String ER_VALUE_SHOULD_BE_NUMBER =
         "ER_VALUE_SHOULD_BE_NUMBER";
  public static final String ER_VALUE_SHOULD_EQUAL = "ER_VALUE_SHOULD_EQUAL";
  public static final String ER_FAILED_CALLING_METHOD =
         "ER_FAILED_CALLING_METHOD";
  public static final String ER_FAILED_CREATING_ELEMTMPL =
         "ER_FAILED_CREATING_ELEMTMPL";
  public static final String ER_CHARS_NOT_ALLOWED = "ER_CHARS_NOT_ALLOWED";
  public static final String ER_ATTR_NOT_ALLOWED = "ER_ATTR_NOT_ALLOWED";
  public static final String ER_BAD_VALUE = "ER_BAD_VALUE";
  public static final String ER_ATTRIB_VALUE_NOT_FOUND =
         "ER_ATTRIB_VALUE_NOT_FOUND";
  public static final String ER_ATTRIB_VALUE_NOT_RECOGNIZED =
         "ER_ATTRIB_VALUE_NOT_RECOGNIZED";
  public static final String ER_NULL_URI_NAMESPACE = "ER_NULL_URI_NAMESPACE";
  public static final String ER_NUMBER_TOO_BIG = "ER_NUMBER_TOO_BIG";
  public static final String  ER_CANNOT_FIND_SAX1_DRIVER =
         "ER_CANNOT_FIND_SAX1_DRIVER";
  public static final String  ER_SAX1_DRIVER_NOT_LOADED =
         "ER_SAX1_DRIVER_NOT_LOADED";
  public static final String  ER_SAX1_DRIVER_NOT_INSTANTIATED =
         "ER_SAX1_DRIVER_NOT_INSTANTIATED" ;
  public static final String ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER =
         "ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER";
  public static final String  ER_PARSER_PROPERTY_NOT_SPECIFIED =
         "ER_PARSER_PROPERTY_NOT_SPECIFIED";
  public static final String  ER_PARSER_ARG_CANNOT_BE_NULL =
         "ER_PARSER_ARG_CANNOT_BE_NULL" ;
  public static final String  ER_FEATURE = "ER_FEATURE";
  public static final String ER_PROPERTY = "ER_PROPERTY" ;
  public static final String ER_NULL_ENTITY_RESOLVER ="ER_NULL_ENTITY_RESOLVER";
  public static final String  ER_NULL_DTD_HANDLER = "ER_NULL_DTD_HANDLER" ;
  public static final String ER_NO_DRIVER_NAME_SPECIFIED =
         "ER_NO_DRIVER_NAME_SPECIFIED";
  public static final String ER_NO_URL_SPECIFIED = "ER_NO_URL_SPECIFIED";
  public static final String ER_POOLSIZE_LESS_THAN_ONE =
         "ER_POOLSIZE_LESS_THAN_ONE";
  public static final String ER_INVALID_DRIVER_NAME = "ER_INVALID_DRIVER_NAME";
  public static final String ER_ERRORLISTENER = "ER_ERRORLISTENER";
  public static final String ER_ASSERT_NO_TEMPLATE_PARENT =
         "ER_ASSERT_NO_TEMPLATE_PARENT";
  public static final String ER_ASSERT_REDUNDENT_EXPR_ELIMINATOR =
         "ER_ASSERT_REDUNDENT_EXPR_ELIMINATOR";
  public static final String ER_NOT_ALLOWED_IN_POSITION =
         "ER_NOT_ALLOWED_IN_POSITION";
  public static final String ER_NONWHITESPACE_NOT_ALLOWED_IN_POSITION =
         "ER_NONWHITESPACE_NOT_ALLOWED_IN_POSITION";
  public static final String ER_NAMESPACE_CONTEXT_NULL_NAMESPACE =
         "ER_NAMESPACE_CONTEXT_NULL_NAMESPACE";
  public static final String ER_NAMESPACE_CONTEXT_NULL_PREFIX =
         "ER_NAMESPACE_CONTEXT_NULL_PREFIX";
  public static final String ER_XPATH_RESOLVER_NULL_QNAME =
         "ER_XPATH_RESOLVER_NULL_QNAME";
  public static final String ER_XPATH_RESOLVER_NEGATIVE_ARITY =
         "ER_XPATH_RESOLVER_NEGATIVE_ARITY";
  public static final String INVALID_TCHAR = "INVALID_TCHAR";
  public static final String INVALID_QNAME = "INVALID_QNAME";
  public static final String INVALID_ENUM = "INVALID_ENUM";
  public static final String INVALID_NMTOKEN = "INVALID_NMTOKEN";
  public static final String INVALID_NCNAME = "INVALID_NCNAME";
  public static final String INVALID_BOOLEAN = "INVALID_BOOLEAN";
  public static final String INVALID_NUMBER = "INVALID_NUMBER";
  public static final String ER_ARG_LITERAL = "ER_ARG_LITERAL";
  public static final String ER_DUPLICATE_GLOBAL_VAR ="ER_DUPLICATE_GLOBAL_VAR";
  public static final String ER_DUPLICATE_VAR = "ER_DUPLICATE_VAR";
  public static final String ER_TEMPLATE_NAME_MATCH = "ER_TEMPLATE_NAME_MATCH";
  public static final String ER_INVALID_PREFIX = "ER_INVALID_PREFIX";
  public static final String ER_NO_ATTRIB_SET = "ER_NO_ATTRIB_SET";
  public static final String ER_FUNCTION_NOT_FOUND =
         "ER_FUNCTION_NOT_FOUND";
  public static final String ER_CANT_HAVE_CONTENT_AND_SELECT =
     "ER_CANT_HAVE_CONTENT_AND_SELECT";
  public static final String ER_INVALID_SET_PARAM_VALUE = "ER_INVALID_SET_PARAM_VALUE";
  public static final String ER_SET_FEATURE_NULL_NAME =
        "ER_SET_FEATURE_NULL_NAME";
  public static final String ER_GET_FEATURE_NULL_NAME =
        "ER_GET_FEATURE_NULL_NAME";
  public static final String ER_UNSUPPORTED_FEATURE =
        "ER_UNSUPPORTED_FEATURE";
  public static final String ER_EXTENSION_ELEMENT_NOT_ALLOWED_IN_SECURE_PROCESSING =
        "ER_EXTENSION_ELEMENT_NOT_ALLOWED_IN_SECURE_PROCESSING";

  public static final String WG_FOUND_CURLYBRACE = "WG_FOUND_CURLYBRACE";
  public static final String WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR =
         "WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR";
  public static final String WG_EXPR_ATTRIB_CHANGED_TO_SELECT =
         "WG_EXPR_ATTRIB_CHANGED_TO_SELECT";
  public static final String WG_NO_LOCALE_IN_FORMATNUMBER =
         "WG_NO_LOCALE_IN_FORMATNUMBER";
  public static final String WG_LOCALE_NOT_FOUND = "WG_LOCALE_NOT_FOUND";
  public static final String WG_CANNOT_MAKE_URL_FROM ="WG_CANNOT_MAKE_URL_FROM";
  public static final String WG_CANNOT_LOAD_REQUESTED_DOC =
         "WG_CANNOT_LOAD_REQUESTED_DOC";
  public static final String WG_CANNOT_FIND_COLLATOR ="WG_CANNOT_FIND_COLLATOR";
  public static final String WG_FUNCTIONS_SHOULD_USE_URL =
         "WG_FUNCTIONS_SHOULD_USE_URL";
  public static final String WG_ENCODING_NOT_SUPPORTED_USING_UTF8 =
         "WG_ENCODING_NOT_SUPPORTED_USING_UTF8";
  public static final String WG_ENCODING_NOT_SUPPORTED_USING_JAVA =
         "WG_ENCODING_NOT_SUPPORTED_USING_JAVA";
  public static final String WG_SPECIFICITY_CONFLICTS =
         "WG_SPECIFICITY_CONFLICTS";
  public static final String WG_PARSING_AND_PREPARING =
         "WG_PARSING_AND_PREPARING";
  public static final String WG_ATTR_TEMPLATE = "WG_ATTR_TEMPLATE";
  public static final String WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESPACE = "WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESP";
  public static final String WG_ATTRIB_NOT_HANDLED = "WG_ATTRIB_NOT_HANDLED";
  public static final String WG_NO_DECIMALFORMAT_DECLARATION =
         "WG_NO_DECIMALFORMAT_DECLARATION";
  public static final String WG_OLD_XSLT_NS = "WG_OLD_XSLT_NS";
  public static final String WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED =
         "WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED";
  public static final String WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE =
         "WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE";
  public static final String WG_ILLEGAL_ATTRIBUTE = "WG_ILLEGAL_ATTRIBUTE";
  public static final String WG_COULD_NOT_RESOLVE_PREFIX =
         "WG_COULD_NOT_RESOLVE_PREFIX";
  public static final String WG_STYLESHEET_REQUIRES_VERSION_ATTRIB =
         "WG_STYLESHEET_REQUIRES_VERSION_ATTRIB";
  public static final String WG_ILLEGAL_ATTRIBUTE_NAME =
         "WG_ILLEGAL_ATTRIBUTE_NAME";
  public static final String WG_ILLEGAL_ATTRIBUTE_VALUE =
         "WG_ILLEGAL_ATTRIBUTE_VALUE";
  public static final String WG_EMPTY_SECOND_ARG = "WG_EMPTY_SECOND_ARG";
  public static final String WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML =
         "WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML";
  public static final String WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME =
         "WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME";
  public static final String WG_ILLEGAL_ATTRIBUTE_POSITION =
         "WG_ILLEGAL_ATTRIBUTE_POSITION";
  public static final String NO_MODIFICATION_ALLOWED_ERR =
         "NO_MODIFICATION_ALLOWED_ERR";

  /*
   * Now fill in the message text.
   * Then fill in the message text for that message code in the
   * array. Use the new error code as the index into the array.
   */

  // Error messages...

  /** Get the lookup table for error messages.
    *
    * @return The message lookup table.
    */
  public Object[][] getContents()
  {
      return new Object[][] {

  /** Error message ID that has a null message, but takes in a single object.    */
  {"ER0000" , "{0}" },

    { ER_NO_CURLYBRACE,
      "错误: 表达式中不能包含 '{'"},

    { ER_ILLEGAL_ATTRIBUTE ,
     "{0}具有非法属性: {1}"},

  {ER_NULL_SOURCENODE_APPLYIMPORTS ,
      "sourceNode 在 xsl:apply-imports 中为空值!"},

  {ER_CANNOT_ADD,
      "无法向{1}添加{0}"},

    { ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES,
      "sourceNode 在 handleApplyTemplatesInstruction 中为空值!"},

    { ER_NO_NAME_ATTRIB,
     "{0}必须具有 name 属性。"},

    {ER_TEMPLATE_NOT_FOUND,
     "找不到名为{0}的模板"},

    {ER_CANT_RESOLVE_NAME_AVT,
      "无法解析 xsl:call-template 中的名称 AVT。"},

    {ER_REQUIRES_ATTRIB,
     "{0}需要属性: {1}"},

    { ER_MUST_HAVE_TEST_ATTRIB,
      "{0}必须具有 ''test'' 属性。"},

    {ER_BAD_VAL_ON_LEVEL_ATTRIB,
      "level 属性的值错误: {0}"},

    {ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "processing-instruction 名称不能为 'xml'"},

    { ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "processing-instruction 名称必须是有效的 NCName: {0}"},

    { ER_NEED_MATCH_ATTRIB,
      "如果{0}具有某种模式, 则必须具有 match 属性。"},

    { ER_NEED_NAME_OR_MATCH_ATTRIB,
      "{0}需要 name 或 match 属性。"},

    {ER_CANT_RESOLVE_NSPREFIX,
      "无法解析名称空间前缀: {0}"},

    { ER_ILLEGAL_VALUE,
     "xml:space 具有非法值: {0}"},

    { ER_NO_OWNERDOC,
      "子节点没有所有者文档!"},

    { ER_ELEMTEMPLATEELEM_ERR,
     "ElemTemplateElement 错误: {0}"},

    { ER_NULL_CHILD,
     "正在尝试添加空子级!"},

    { ER_NEED_SELECT_ATTRIB,
     "{0}需要 select 属性。"},

    { ER_NEED_TEST_ATTRIB ,
      "xsl:when 必须具有 'test' 属性。"},

    { ER_NEED_NAME_ATTRIB,
      "xsl:with-param 必须具有 'name' 属性。"},

    { ER_NO_CONTEXT_OWNERDOC,
      "上下文没有所有者文档!"},

    {ER_COULD_NOT_CREATE_XML_PROC_LIAISON,
      "无法创建 XML TransformerFactory Liaison: {0}"},

    {ER_PROCESS_NOT_SUCCESSFUL,
      "Xalan: 进程未成功。"},

    { ER_NOT_SUCCESSFUL,
     "Xalan: 未成功。"},

    { ER_ENCODING_NOT_SUPPORTED,
     "不支持编码: {0}"},

    {ER_COULD_NOT_CREATE_TRACELISTENER,
      "无法创建 TraceListener: {0}"},

    {ER_KEY_REQUIRES_NAME_ATTRIB,
      "xsl:key 需要 'name' 属性!"},

    { ER_KEY_REQUIRES_MATCH_ATTRIB,
      "xsl:key 需要 'match' 属性!"},

    { ER_KEY_REQUIRES_USE_ATTRIB,
      "xsl:key 需要 'use' 属性!"},

    { ER_REQUIRES_ELEMENTS_ATTRIB,
      "(StylesheetHandler) {0}需要 ''elements'' 属性!"},

    { ER_MISSING_PREFIX_ATTRIB,
      "(StylesheetHandler) 缺少{0}属性 ''prefix''"},

    { ER_BAD_STYLESHEET_URL,
     "样式表 URL 错误: {0}"},

    { ER_FILE_NOT_FOUND,
     "找不到样式表文件: {0}"},

    { ER_IOEXCEPTION,
      "样式表文件出现 IO 异常错误: {0}"},

    { ER_NO_HREF_ATTRIB,
      "(StylesheetHandler) 找不到{0}的 href 属性"},

    { ER_STYLESHEET_INCLUDES_ITSELF,
      "(StylesheetHandler) {0}直接或间接包含其自身!"},

    { ER_PROCESSINCLUDE_ERROR,
      "StylesheetHandler.processInclude 错误, {0}"},

    { ER_MISSING_LANG_ATTRIB,
      "(StylesheetHandler) 缺少{0}属性 ''lang''"},

    { ER_MISSING_CONTAINER_ELEMENT_COMPONENT,
      "(StylesheetHandler) {0}元素的放置位置是否错误?? 缺少容器元素 ''component''"},

    { ER_CAN_ONLY_OUTPUT_TO_ELEMENT,
      "只能输出到 Element, DocumentFragment, Document 或 PrintWriter。"},

    { ER_PROCESS_ERROR,
     "StylesheetRoot.process 错误"},

    { ER_UNIMPLNODE_ERROR,
     "UnImplNode 错误: {0}"},

    { ER_NO_SELECT_EXPRESSION,
      "错误! 找不到 xpath 选择表达式 (-select)。"},

    { ER_CANNOT_SERIALIZE_XSLPROCESSOR,
      "无法序列化 XSLProcessor!"},

    { ER_NO_INPUT_STYLESHEET,
      "未指定样式表输入!"},

    { ER_FAILED_PROCESS_STYLESHEET,
      "无法处理样式表!"},

    { ER_COULDNT_PARSE_DOC,
     "无法解析{0}文档!"},

    { ER_COULDNT_FIND_FRAGMENT,
     "找不到片段: {0}"},

    { ER_NODE_NOT_ELEMENT,
      "片段标识符指向的节点不是元素: {0}"},

    { ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB,
      "for-each 必须具有 match 或 name 属性"},

    { ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB,
      "templates 必须具有 match 或 name 属性"},

    { ER_NO_CLONE_OF_DOCUMENT_FRAG,
      "不能克隆文档片段!"},

    { ER_CANT_CREATE_ITEM,
      "无法在结果树中创建项: {0}"},

    { ER_XMLSPACE_ILLEGAL_VALUE,
      "源 XML 中的 xml:space 具有非法值: {0}"},

    { ER_NO_XSLKEY_DECLARATION,
      "{0}没有 xsl:key 声明!"},

    { ER_CANT_CREATE_URL,
     "错误! 无法为{0}创建 url"},

    { ER_XSLFUNCTIONS_UNSUPPORTED,
     "不支持 xsl:functions"},

    { ER_PROCESSOR_ERROR,
     "XSLT TransformerFactory 错误"},

    { ER_NOT_ALLOWED_INSIDE_STYLESHEET,
      "(StylesheetHandler) 样式表中不允许使用{0}!"},

    { ER_RESULTNS_NOT_SUPPORTED,
      "不再支持 result-ns! 请改用 xsl:output。"},

    { ER_DEFAULTSPACE_NOT_SUPPORTED,
      "不再支持 default-space! 请改用 xsl:strip-space 或 xsl:preserve-space。"},

    { ER_INDENTRESULT_NOT_SUPPORTED,
      "不再支持 indent-result! 请改用 xsl:output。"},

    { ER_ILLEGAL_ATTRIB,
      "(StylesheetHandler) {0}具有非法属性: {1}"},

    { ER_UNKNOWN_XSL_ELEM,
     "未知 XSL 元素: {0}"},

    { ER_BAD_XSLSORT_USE,
      "(StylesheetHandler) xsl:sort 只能与 xsl:apply-templates 或 xsl:for-each 一起使用。"},

    { ER_MISPLACED_XSLWHEN,
      "(StylesheetHandler) xsl:when 的放置位置错误!"},

    { ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:when 的父级不是 xsl:choose!"},

    { ER_MISPLACED_XSLOTHERWISE,
      "(StylesheetHandler) xsl:otherwise 的放置位置错误!"},

    { ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:otherwise 的父级不是 xsl:choose!"},

    { ER_NOT_ALLOWED_INSIDE_TEMPLATE,
      "(StylesheetHandler) 模板中不允许使用{0}!"},

    { ER_UNKNOWN_EXT_NS_PREFIX,
      "(StylesheetHandler) {0}扩展名称空间前缀 {1} 未知"},

    { ER_IMPORTS_AS_FIRST_ELEM,
      "(StylesheetHandler) 只能作为样式表中的第一个元素导入!"},

    { ER_IMPORTING_ITSELF,
      "(StylesheetHandler) {0}直接或间接导入其自身!"},

    { ER_XMLSPACE_ILLEGAL_VAL,
      "(StylesheetHandler) xml:space 具有非法值: {0}"},

    { ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL,
      "processStylesheet 失败!"},

    { ER_SAX_EXCEPTION,
     "SAX 异常错误"},

//  add this message to fix bug 21478
    { ER_FUNCTION_NOT_SUPPORTED,
     "不支持该函数!"},

    { ER_XSLT_ERROR,
     "XSLT 错误"},

    { ER_CURRENCY_SIGN_ILLEGAL,
      "格式模式字符串中不允许使用货币符号"},

    { ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM,
      "样式表 DOM 中不支持 Document 函数!"},

    { ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER,
      "无法解析非前缀解析器的前缀!"},

    { ER_REDIRECT_COULDNT_GET_FILENAME,
      "重定向扩展: 无法获取文件名 - file 或 select 属性必须返回有效字符串。"},

    { ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT,
      "无法在重定向扩展中构建 FormatterListener!"},

    { ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX,
      "exclude-result-prefixes 中的前缀无效: {0}"},

    { ER_MISSING_NS_URI,
      "指定前缀缺少名称空间 URI"},

    { ER_MISSING_ARG_FOR_OPTION,
      "选项缺少参数: {0}"},

    { ER_INVALID_OPTION,
     "选项无效: {0}"},

    { ER_MALFORMED_FORMAT_STRING,
     "格式字符串的格式错误: {0}"},

    { ER_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet 需要 'version' 属性!"},

    { ER_ILLEGAL_ATTRIBUTE_VALUE,
      "属性{0}具有非法值: {1}"},

    { ER_CHOOSE_REQUIRES_WHEN,
     "xsl:choose 需要 xsl:when"},

    { ER_NO_APPLY_IMPORT_IN_FOR_EACH,
      "xsl:for-each 中不允许使用 xsl:apply-imports"},

    { ER_CANT_USE_DTM_FOR_OUTPUT,
      "无法将 DTMLiaison 用于输出 DOM 节点... 请改为传递 com.sun.org.apache.xpath.internal.DOM2Helper!"},

    { ER_CANT_USE_DTM_FOR_INPUT,
      "无法将 DTMLiaison 用于输入 DOM 节点... 请改为传递 com.sun.org.apache.xpath.internal.DOM2Helper!"},

    { ER_CALL_TO_EXT_FAILED,
      "未能调用扩展元素: {0}"},

    { ER_PREFIX_MUST_RESOLVE,
      "前缀必须解析为名称空间: {0}"},

    { ER_INVALID_UTF16_SURROGATE,
      "检测到无效的 UTF-16 代理: {0}?"},

    { ER_XSLATTRSET_USED_ITSELF,
      "xsl:attribute-set {0} 使用其自身, 这将导致无限循环。"},

    { ER_CANNOT_MIX_XERCESDOM,
      "无法混合非 Xerces-DOM 输入和 Xerces-DOM 输出!"},

    { ER_TOO_MANY_LISTENERS,
      "addTraceListenersToStylesheet - TooManyListenersException"},

    { ER_IN_ELEMTEMPLATEELEM_READOBJECT,
      "在 ElemTemplateElement.readObject 中: {0}"},

    { ER_DUPLICATE_NAMED_TEMPLATE,
      "找到多个名为{0}的模板"},

    { ER_INVALID_KEY_CALL,
      "函数调用无效: 不允许递归 key() 调用"},

    { ER_REFERENCING_ITSELF,
      "变量 {0} 直接或间接引用其自身!"},

    { ER_ILLEGAL_DOMSOURCE_INPUT,
      "对于 newTemplates 的 DOMSource, 输入节点不能为空值!"},

    { ER_CLASS_NOT_FOUND_FOR_OPTION,
        "找不到选项{0}的类文件"},

    { ER_REQUIRED_ELEM_NOT_FOUND,
        "找不到所需元素: {0}"},

    { ER_INPUT_CANNOT_BE_NULL,
        "InputStream 不能为空值"},

    { ER_URI_CANNOT_BE_NULL,
        "URI 不能为空值"},

    { ER_FILE_CANNOT_BE_NULL,
        "File 不能为空值"},

    { ER_SOURCE_CANNOT_BE_NULL,
                "InputSource 不能为空值"},

    { ER_CANNOT_INIT_BSFMGR,
                "无法初始化 BSF 管理器"},

    { ER_CANNOT_CMPL_EXTENSN,
                "无法编译扩展"},

    { ER_CANNOT_CREATE_EXTENSN,
      "无法创建扩展: {0}, 原因: {1}"},

    { ER_INSTANCE_MTHD_CALL_REQUIRES,
      "对方法{0}的实例方法调用需要将 Object 实例作为第一个参数"},

    { ER_INVALID_ELEMENT_NAME,
      "指定的元素名称{0}无效"},

    { ER_ELEMENT_NAME_METHOD_STATIC,
      "元素名称方法必须是 static {0}"},

    { ER_EXTENSION_FUNC_UNKNOWN,
             "扩展函数 {0}: {1} 未知"},

    { ER_MORE_MATCH_CONSTRUCTOR,
             "{0}的构造器具有多个最佳匹配"},

    { ER_MORE_MATCH_METHOD,
             "方法{0}具有多个最佳匹配"},

    { ER_MORE_MATCH_ELEMENT,
             "元素方法{0}具有多个最佳匹配"},

    { ER_INVALID_CONTEXT_PASSED,
             "传递的用于对{0}求值的上下文无效"},

    { ER_POOL_EXISTS,
             "池已存在"},

    { ER_NO_DRIVER_NAME,
             "未指定驱动程序名称"},

    { ER_NO_URL,
             "未指定 URL"},

    { ER_POOL_SIZE_LESSTHAN_ONE,
             "池大小小于 1!"},

    { ER_INVALID_DRIVER,
             "指定的驱动程序名称无效!"},

    { ER_NO_STYLESHEETROOT,
             "找不到样式表根!"},

    { ER_ILLEGAL_XMLSPACE_VALUE,
         "xml:space 的值非法"},

    { ER_PROCESSFROMNODE_FAILED,
         "processFromNode 失败"},

    { ER_RESOURCE_COULD_NOT_LOAD,
        "资源 [ {0} ] 无法加载: {1} \n {2} \t {3}"},

    { ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "缓冲区大小 <=0"},

    { ER_UNKNOWN_ERROR_CALLING_EXTENSION,
        "调用扩展时出现未知错误"},

    { ER_NO_NAMESPACE_DECL,
        "前缀 {0} 没有对应的名称空间声明"},

    { ER_ELEM_CONTENT_NOT_ALLOWED,
        "lang=javaclass {0}不允许使用元素内容"},

    { ER_STYLESHEET_DIRECTED_TERMINATION,
        "样式表指向终止"},

    { ER_ONE_OR_TWO,
        "1 或 2"},

    { ER_TWO_OR_THREE,
        "2 或 3"},

    { ER_COULD_NOT_LOAD_RESOURCE,
        "无法加载{0} (检查 CLASSPATH), 现在只使用默认值"},

    { ER_CANNOT_INIT_DEFAULT_TEMPLATES,
        "无法初始化默认模板"},

    { ER_RESULT_NULL,
        "Result 不能为空值"},

    { ER_RESULT_COULD_NOT_BE_SET,
        "无法设置 Result"},

    { ER_NO_OUTPUT_SPECIFIED,
        "未指定输出"},

    { ER_CANNOT_TRANSFORM_TO_RESULT_TYPE,
        "无法转换为类型为{0}的 Result"},

    { ER_CANNOT_TRANSFORM_SOURCE_TYPE,
        "无法转换类型为{0}的源"},

    { ER_NULL_CONTENT_HANDLER,
        "空内容处理程序"},

    { ER_NULL_ERROR_HANDLER,
        "空错误处理程序"},

    { ER_CANNOT_CALL_PARSE,
        "如果尚未设置 ContentHandler, 则无法调用 parse"},

    { ER_NO_PARENT_FOR_FILTER,
        "筛选器没有父级"},

    { ER_NO_STYLESHEET_IN_MEDIA,
         "在{0}中找不到样式表, 介质= {1}"},

    { ER_NO_STYLESHEET_PI,
         "在{0}中找不到 xml-stylesheet PI"},

    { ER_NOT_SUPPORTED,
       "不支持: {0}"},

    { ER_PROPERTY_VALUE_BOOLEAN,
       "属性{0}的值应为 Boolean 实例"},

    { ER_COULD_NOT_FIND_EXTERN_SCRIPT,
         "无法在{0}中获取外部脚本"},

    { ER_RESOURCE_COULD_NOT_FIND,
        "找不到资源 [ {0} ]。\n {1}"},

    { ER_OUTPUT_PROPERTY_NOT_RECOGNIZED,
        "无法识别输出属性: {0}"},

    { ER_FAILED_CREATING_ELEMLITRSLT,
        "未能创建 ElemLiteralResult 实例"},

  //Earlier (JDK 1.4 XALAN 2.2-D11) at key code '204' the key name was ER_PRIORITY_NOT_PARSABLE
  // In latest Xalan code base key name is  ER_VALUE_SHOULD_BE_NUMBER. This should also be taken care
  //in locale specific files like XSLTErrorResources_de.java, XSLTErrorResources_fr.java etc.
  //NOTE: Not only the key name but message has also been changed.
    { ER_VALUE_SHOULD_BE_NUMBER,
        "{0}的值应包含可解析的数字"},

    { ER_VALUE_SHOULD_EQUAL,
        "{0}的值应等于“是”或“否”"},

    { ER_FAILED_CALLING_METHOD,
        "未能调用{0}方法"},

    { ER_FAILED_CREATING_ELEMTMPL,
        "未能创建 ElemTemplateElement 实例"},

    { ER_CHARS_NOT_ALLOWED,
        "不允许在文档中的此位置处使用字符"},

    { ER_ATTR_NOT_ALLOWED,
        "{1}元素中不允许使用 \"{0}\" 属性!"},

    { ER_BAD_VALUE,
     "{0}错误值{1} "},

    { ER_ATTRIB_VALUE_NOT_FOUND,
     "找不到{0}属性值 "},

    { ER_ATTRIB_VALUE_NOT_RECOGNIZED,
     "无法识别{0}属性值 "},

    { ER_NULL_URI_NAMESPACE,
     "尝试使用空 URI 生成名称空间前缀"},

    { ER_NUMBER_TOO_BIG,
     "尝试设置超过最大长整型的数字的格式"},

    { ER_CANNOT_FIND_SAX1_DRIVER,
     "找不到 SAX1 驱动程序类{0}"},

    { ER_SAX1_DRIVER_NOT_LOADED,
     "已找到 SAX1 驱动程序类{0}, 但无法进行加载"},

    { ER_SAX1_DRIVER_NOT_INSTANTIATED,
     "已加载 SAX1 驱动程序类{0}, 但无法进行实例化"},

    { ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER,
     "SAX1 驱动程序类 {0} 未实现 org.xml.sax.Parser"},

    { ER_PARSER_PROPERTY_NOT_SPECIFIED,
     "未指定系统属性 org.xml.sax.parser"},

    { ER_PARSER_ARG_CANNOT_BE_NULL,
     "解析器参数不能为空值"},

    { ER_FEATURE,
     "功能: {0}"},

    { ER_PROPERTY,
     "属性: {0}"},

    { ER_NULL_ENTITY_RESOLVER,
     "空实体解析器"},

    { ER_NULL_DTD_HANDLER,
     "空 DTD 处理程序"},

    { ER_NO_DRIVER_NAME_SPECIFIED,
     "未指定驱动程序名称!"},

    { ER_NO_URL_SPECIFIED,
     "未指定 URL!"},

    { ER_POOLSIZE_LESS_THAN_ONE,
     "池大小小于 1!"},

    { ER_INVALID_DRIVER_NAME,
     "指定的驱动程序名称无效!"},

    { ER_ERRORLISTENER,
     "ErrorListener"},


// Note to translators:  The following message should not normally be displayed
//   to users.  It describes a situation in which the processor has detected
//   an internal consistency problem in itself, and it provides this message
//   for the developer to help diagnose the problem.  The name
//   'ElemTemplateElement' is the name of a class, and should not be
//   translated.
    { ER_ASSERT_NO_TEMPLATE_PARENT,
     "程序员错误! 表达式没有 ElemTemplateElement 父级!"},


// Note to translators:  The following message should not normally be displayed
//   to users.  It describes a situation in which the processor has detected
//   an internal consistency problem in itself, and it provides this message
//   for the developer to help diagnose the problem.  The substitution text
//   provides further information in order to diagnose the problem.  The name
//   'RedundentExprEliminator' is the name of a class, and should not be
//   translated.
    { ER_ASSERT_REDUNDENT_EXPR_ELIMINATOR,
     "RedundentExprEliminator 中的程序员断言: {0}"},

    { ER_NOT_ALLOWED_IN_POSITION,
     "不允许在样式表中的此位置使用{0}!"},

    { ER_NONWHITESPACE_NOT_ALLOWED_IN_POSITION,
     "不允许在样式表中的此位置使用非空白文本!"},

  // This code is shared with warning codes.
  // SystemId Unknown
    { INVALID_TCHAR,
     "CHAR 属性{0}使用了非法值{1}。CHAR 类型的属性只能为 1 个字符!"},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "QNAME" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value and {0} is the attribute name.
  //The following codes are shared with the warning codes...
    { INVALID_QNAME,
     "QNAME 属性{0}使用了非法值{1}"},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "ENUM" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value, {0} is the attribute name, and {2} is a list of valid
    // values.
    { INVALID_ENUM,
     "ENUM 属性{0}使用了非法值{1}。有效值为: {2}。"},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NMTOKEN" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_NMTOKEN,
     "NMTOKEN 属性{0}使用了非法值{1} "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NCNAME" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_NCNAME,
     "NCNAME 属性{0}使用了非法值{1} "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "boolean" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_BOOLEAN,
     "Boolean 属性{0}使用了非法值{1} "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "number" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
     { INVALID_NUMBER,
     "Number 属性{0}使用了非法值{1} "},


  // End of shared codes...

// Note to translators:  A "match pattern" is a special form of XPath expression
// that is used for matching patterns.  The substitution text is the name of
// a function.  The message indicates that when this function is referenced in
// a match pattern, its argument must be a string literal (or constant.)
// ER_ARG_LITERAL - new error message for bugzilla //5202
    { ER_ARG_LITERAL,
     "匹配模式中的{0}的参数必须为文字。"},

// Note to translators:  The following message indicates that two definitions of
// a variable.  A "global variable" is a variable that is accessible everywher
// in the stylesheet.
// ER_DUPLICATE_GLOBAL_VAR - new error message for bugzilla #790
    { ER_DUPLICATE_GLOBAL_VAR,
     "全局变量声明重复。"},


// Note to translators:  The following message indicates that two definitions of
// a variable were encountered.
// ER_DUPLICATE_VAR - new error message for bugzilla #790
    { ER_DUPLICATE_VAR,
     "变量声明重复。"},

    // Note to translators:  "xsl:template, "name" and "match" are XSLT keywords
    // which must not be translated.
    // ER_TEMPLATE_NAME_MATCH - new error message for bugzilla #789
    { ER_TEMPLATE_NAME_MATCH,
     "xsl:template 必须具有 name 和/或 match 属性"},

    // Note to translators:  "exclude-result-prefixes" is an XSLT keyword which
    // should not be translated.  The message indicates that a namespace prefix
    // encountered as part of the value of the exclude-result-prefixes attribute
    // was in error.
    // ER_INVALID_PREFIX - new error message for bugzilla #788
    { ER_INVALID_PREFIX,
     "exclude-result-prefixes 中的前缀无效: {0}"},

    // Note to translators:  An "attribute set" is a set of attributes that can
    // be added to an element in the output document as a group.  The message
    // indicates that there was a reference to an attribute set named {0} that
    // was never defined.
    // ER_NO_ATTRIB_SET - new error message for bugzilla #782
    { ER_NO_ATTRIB_SET,
     "名为{0}的属性集不存在"},

    // Note to translators:  This message indicates that there was a reference
    // to a function named {0} for which no function definition could be found.
    { ER_FUNCTION_NOT_FOUND,
     "名为{0}的函数不存在"},

    // Note to translators:  This message indicates that the XSLT instruction
    // that is named by the substitution text {0} must not contain other XSLT
    // instructions (content) or a "select" attribute.  The word "select" is
    // an XSLT keyword in this case and must not be translated.
    { ER_CANT_HAVE_CONTENT_AND_SELECT,
     "{0}元素不能同时具有内容和 select 属性。"},

    // Note to translators:  This message indicates that the value argument
    // of setParameter must be a valid Java Object.
    { ER_INVALID_SET_PARAM_VALUE,
     "参数 {0} 的值必须是有效 Java 对象"},

    { ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX_FOR_DEFAULT,
      "xsl:namespace-alias 元素的 result-prefix 属性具有值 '#default', 但该元素的作用域中没有默认名称空间的声明"},

    { ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX,
      "xsl:namespace-alias 元素的 result-prefix 属性具有值 ''{0}'', 但该元素的作用域中没有前缀 ''{0}'' 的名称空间声明。"},

    { ER_SET_FEATURE_NULL_NAME,
      "TransformerFactory.setFeature(String name, boolean value) 中的功能名称不能为空值。"},

    { ER_GET_FEATURE_NULL_NAME,
      "TransformerFactory.getFeature(String name) 中的功能名称不能为空值。"},

    { ER_UNSUPPORTED_FEATURE,
      "无法对此 TransformerFactory 设置功能 ''{0}''。"},

    { ER_EXTENSION_ELEMENT_NOT_ALLOWED_IN_SECURE_PROCESSING,
        "当扩展函数被安全处理功能或属性 ''jdk.xml.enableExtensionFunctions'' 禁用时，不允许使用扩展函数 ''{0}''。要启用扩展函数，请将 ''jdk.xml.enableExtensionFunctions'' 设置为 ''true''。"},

    { ER_NAMESPACE_CONTEXT_NULL_NAMESPACE,
      "无法获取空名称空间 uri 的前缀。"},

    { ER_NAMESPACE_CONTEXT_NULL_PREFIX,
      "无法获取空前缀的名称空间 uri。"},

    { ER_XPATH_RESOLVER_NULL_QNAME,
      "函数名称不能为空值。"},

    { ER_XPATH_RESOLVER_NEGATIVE_ARITY,
      "元数不能为负数。"},
  // Warnings...

    { WG_FOUND_CURLYBRACE,
      "已找到 '}', 但未打开属性模板!"},

    { WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR,
      "警告: count 属性与 xsl:number 中的 ancestor 不匹配! 目标 = {0}"},

    { WG_EXPR_ATTRIB_CHANGED_TO_SELECT,
      "旧语法: 'expr' 属性的名称已更改为 'select'。"},

    { WG_NO_LOCALE_IN_FORMATNUMBER,
      "Xalan 尚未处理 format-number 函数中的区域设置名称。"},

    { WG_LOCALE_NOT_FOUND,
      "警告: 找不到 xml:lang={0} 的区域设置"},

    { WG_CANNOT_MAKE_URL_FROM,
      "无法根据{0}生成 URL"},

    { WG_CANNOT_LOAD_REQUESTED_DOC,
      "无法加载请求的文档: {0}"},

    { WG_CANNOT_FIND_COLLATOR,
      "找不到 <sort xml:lang={0} 的 Collator"},

    { WG_FUNCTIONS_SHOULD_USE_URL,
      "旧语法: 函数指令应使用{0}的 url"},

    { WG_ENCODING_NOT_SUPPORTED_USING_UTF8,
      "不支持编码: {0}, 使用 UTF-8"},

    { WG_ENCODING_NOT_SUPPORTED_USING_JAVA,
      "不支持编码: {0}, 使用 Java {1}"},

    { WG_SPECIFICITY_CONFLICTS,
      "发现特征冲突: 将使用上次在样式表中找到的{0}。"},

    { WG_PARSING_AND_PREPARING,
      "========= 解析和准备{0} =========="},

    { WG_ATTR_TEMPLATE,
     "属性模板{0}"},

    { WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESPACE,
      "xsl:strip-space 和 xsl:preserve-space 之间存在匹配冲突"},

    { WG_ATTRIB_NOT_HANDLED,
      "Xalan 尚未处理{0}属性!"},

    { WG_NO_DECIMALFORMAT_DECLARATION,
      "找不到十进制格式的声明: {0}"},

    { WG_OLD_XSLT_NS,
     "缺少 XSLT 名称空间或 XSLT 名称空间错误。"},

    { WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED,
      "仅允许使用一个默认的 xsl:decimal-format 声明。"},

    { WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE,
      "xsl:decimal-format 名称必须唯一。名称 \"{0}\" 重复。"},

    { WG_ILLEGAL_ATTRIBUTE,
      "{0}具有非法属性: {1}"},

    { WG_COULD_NOT_RESOLVE_PREFIX,
      "无法解析名称空间前缀: {0}。将忽略节点。"},

    { WG_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet 需要 'version' 属性!"},

    { WG_ILLEGAL_ATTRIBUTE_NAME,
      "非法属性名称: {0}"},

    { WG_ILLEGAL_ATTRIBUTE_VALUE,
      "属性{0}使用了非法值{1}"},

    { WG_EMPTY_SECOND_ARG,
      "根据 document 函数的第二个参数得到的节点集为空。返回空 node-set。"},

  //Following are the new WARNING keys added in XALAN code base after Jdk 1.4 (Xalan 2.2-D11)

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.
    { WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "xsl:processing-instruction 名称的 'name' 属性的值不能为 'xml'"},

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.  "NCName" is an XML data-type and must not be
    // translated.
    { WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "xsl:processing-instruction 的 ''name'' 属性的值必须是有效的 NCName: {0}"},

    // Note to translators:  This message is reported if the stylesheet that is
    // being processed attempted to construct an XML document with an attribute in a
    // place other than on an element.  The substitution text specifies the name of
    // the attribute.
    { WG_ILLEGAL_ATTRIBUTE_POSITION,
      "在生成子节点之后或在生成元素之前无法添加属性 {0}。将忽略属性。"},

    { NO_MODIFICATION_ALLOWED_ERR,
      "尝试修改不允许修改的对象。"
    },

    //Check: WHY THERE IS A GAP B/W NUMBERS in the XSLTErrorResources properties file?

  // Other miscellaneous text used inside the code...
  { "ui_language", "en"},
  {  "help_language",  "en" },
  {  "language",  "en" },
  { "BAD_CODE", "createMessage 的参数超出范围"},
  {  "FORMAT_FAILED", "调用 messageFormat 时抛出异常错误"},
  {  "version", ">>>>>>> Xalan 版本 "},
  {  "version2",  "<<<<<<<"},
  {  "yes", "是"},
  { "line", "行号"},
  { "column","列号"},
  { "xsldone", "XSLProcessor: 完成"},


  // Note to translators:  The following messages provide usage information
  // for the Xalan Process command line.  "Process" is the name of a Java class,
  // and should not be translated.
  { "xslProc_option", "Xalan-J 命令行 Process 类选项:"},
  { "xslProc_invalid_xsltc_option", "XSLTC 模式下不支持选项{0}。"},
  { "xslProc_invalid_xalan_option", "选项{0}只能与 -XSLTC 一起使用。"},
  { "xslProc_no_input", "错误: 未指定样式表或输入 xml。运行此命令时, 用法指令不带任何选项。"},
  { "xslProc_common_options", "-公用选项-"},
  { "xslProc_xalan_options", "-Xalan 的选项-"},
  { "xslProc_xsltc_options", "-XSLTC 的选项-"},
  { "xslProc_return_to_continue", "(按 <return> 以继续)"},

   // Note to translators: The option name and the parameter name do not need to
   // be translated. Only translate the messages in parentheses.  Note also that
   // leading whitespace in the messages is used to indent the usage information
   // for each option in the English messages.
   // Do not translate the keywords: XSLTC, SAX, DOM and DTM.
  { "optionXSLTC", "   [-XSLTC (使用 XSLTC 进行转换)]"},
  { "optionIN", "   [-IN inputXMLURL]"},
  { "optionXSL", "   [-XSL XSLTransformationURL]"},
  { "optionOUT",  "   [-OUT outputFileName]"},
  { "optionLXCIN", "   [-LXCIN compiledStylesheetFileNameIn]"},
  { "optionLXCOUT", "   [-LXCOUT compiledStylesheetFileNameOutOut]"},
  { "optionPARSER", "   [-PARSER fully qualified class name of parser liaison]"},
  {  "optionE", "   [-E (不展开实体引用)]"},
  {  "optionV",  "   [-E (不展开实体引用)]"},
  {  "optionQC", "   [-QC (无提示模式冲突警告)]"},
  {  "optionQ", "   [-Q (无提示模式)]"},
  {  "optionLF", "   [-LF (仅在输出时使用换行符 {默认值为 CR/LF})]"},
  {  "optionCR", "   [-CR (仅在输出时使用回车 {默认值为 CR/LF})]"},
  { "optionESCAPE", "   [-ESCAPE (要逃逸 的字符 {默认值为 <>&\"'\\r\\n}]"},
  { "optionINDENT", "   [-INDENT (控制要缩进的空格数 {默认值为 0})]"},
  { "optionTT", "   [-TT (在调用模板时跟踪模板。)]"},
  { "optionTG", "   [-TG (跟踪每个生成事件。)]"},
  { "optionTS", "   [-TS (跟踪每个选择事件。)]"},
  {  "optionTTC", "   [-TTC (在处理模板子级时跟踪模板子级。)]"},
  { "optionTCLASS", "   [-TCLASS (用于跟踪扩展的 TraceListener 类。)]"},
  { "optionVALIDATE", "   [-VALIDATE (设置是否进行验证。默认情况下, 将禁止验证。)]"},
  { "optionEDUMP", "   [-EDUMP {optional filename} (在出错时执行堆栈转储。)]"},
  {  "optionXML", "   [-XML (使用 XML 格式设置工具并添加 XML 标头。)]"},
  {  "optionTEXT", "   [-TEXT (使用简单文本格式设置工具。)]"},
  {  "optionHTML", "   [-HTML (使用 HTML 格式设置工具。)]"},
  {  "optionPARAM", "   [-PARAM 名称表达式 (设置样式表参数)]"},
  {  "noParsermsg1", "XSL 进程未成功。"},
  {  "noParsermsg2", "** 找不到解析器 **"},
  { "noParsermsg3",  "请检查您的类路径。"},
  { "noParsermsg4", "如果没有 IBM 提供的 XML Parser for Java, 则可以从"},
  { "noParsermsg5", "IBM AlphaWorks 进行下载, 网址为: http://www.alphaworks.ibm.com/formula/xml"},
  { "optionURIRESOLVER", "   [-URIRESOLVER 完整类名 (使用 URIResolver 解析 URI)]"},
  { "optionENTITYRESOLVER",  "   [-ENTITYRESOLVER 完整类名 (使用 EntityResolver 解析实体)]"},
  { "optionCONTENTHANDLER",  "   [-CONTENTHANDLER 完整类名 (使用 ContentHandler 序列化输出)]"},
  {  "optionLINENUMBERS",  "   [-L 使用源文档的行号]"},
  { "optionSECUREPROCESSING", "   [-SECURE (将安全处理功能设置为“真”。)]"},

    // Following are the new options added in XSLTErrorResources.properties files after Jdk 1.4 (Xalan 2.2-D11)


  {  "optionMEDIA",  "   [-MEDIA mediaType (使用 media 属性查找与文档关联的样式表。)]"},
  {  "optionFLAVOR",  "   [-FLAVOR flavorName (明确使用 s2s=SAX 或 d2d=DOM 执行转换。)] "}, // Added by sboag/scurcuru; experimental
  { "optionDIAG", "   [-DIAG (输出全部转换时间 (毫秒)。)]"},
  { "optionINCREMENTAL",  "   [-INCREMENTAL (通过将 http://xml.apache.org/xalan/features/incremental 设置为“真”来请求增量 DTM 构建。)]"},
  {  "optionNOOPTIMIMIZE",  "   [-NOOPTIMIMIZE (通过将 http://xml.apache.org/xalan/features/optimize 设置为“假”来请求不执行样式表优化处理。)]"},
  { "optionRL",  "   [-RL recursionlimit (声明样式表递归深度的数字限制。)]"},
  {   "optionXO",  "   [-XO [transletName] (为生成的 translet 分配名称)]"},
  {  "optionXD", "   [-XD destinationDirectory (指定 translet 的目标目录)]"},
  {  "optionXJ",  "   [-XJ jarfile (将 translet 类打包到名为 <jarfile> 的 jar 文件中)]"},
  {   "optionXP",  "   [-XP package (为生成的所有 translet 类指定程序包名称前缀)]"},

  //AddITIONAL  STRINGS that need L10n
  // Note to translators:  The following message describes usage of a particular
  // command-line option that is used to enable the "template inlining"
  // optimization.  The optimization involves making a copy of the code
  // generated for a template in another template that refers to it.
  { "optionXN",  "   [-XN (启用模板内嵌)]" },
  { "optionXX",  "   [-XX (启用附加调试消息输出)]"},
  { "optionXT" , "   [-XT (如果可能, 使用 translet 进行转换)]"},
  { "diagTiming"," --------- 通过{1}转换{0}花费了 {2} 毫秒的时间" },
  { "recursionTooDeep","模板嵌套太深。嵌套 = {0}, 模板{1} {2}" },
  { "nameIs", "名称为" },
  { "matchPatternIs", "匹配模式为" }

  };

  }
  // ================= INFRASTRUCTURE ======================

  /** String for use when a bad error code was encountered.    */
  public static final String BAD_CODE = "BAD_CODE";

  /** String for use when formatting of the error string failed.   */
  public static final String FORMAT_FAILED = "FORMAT_FAILED";

    }
