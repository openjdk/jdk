/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 */
public class XSLTErrorResources_zh_TW extends ListResourceBundle
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
      "錯誤: 表示式中不可有 '{'"},

    { ER_ILLEGAL_ATTRIBUTE ,
     "{0} 具有無效屬性: {1}"},

  {ER_NULL_SOURCENODE_APPLYIMPORTS ,
      "sourceNode 在 xsl:apply-imports 中是空值！"},

  {ER_CANNOT_ADD,
      "無法新增 {0} 至 {1}"},

    { ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES,
      "sourceNode 在 handleApplyTemplatesInstruction 中是空值！"},

    { ER_NO_NAME_ATTRIB,
     "{0} 必須有名稱屬性。"},

    {ER_TEMPLATE_NOT_FOUND,
     "找不到下列名稱的樣板: {0}"},

    {ER_CANT_RESOLVE_NAME_AVT,
      "無法解析 xsl:call-template 中的名稱 AVT。"},

    {ER_REQUIRES_ATTRIB,
     "{0} 需要屬性: {1}"},

    { ER_MUST_HAVE_TEST_ATTRIB,
      "{0} 必須有 ''test'' 屬性。"},

    {ER_BAD_VAL_ON_LEVEL_ATTRIB,
      "錯誤的值位於層次屬性: {0}"},

    {ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "processing-instruction 名稱不可為 'xml'"},

    { ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "processing-instruction 名稱必須是有效的 NCName: {0}"},

    { ER_NEED_MATCH_ATTRIB,
      "{0} 若具有模式，則必須有配對屬性。"},

    { ER_NEED_NAME_OR_MATCH_ATTRIB,
      "{0} 需要名稱或配對屬性。"},

    {ER_CANT_RESOLVE_NSPREFIX,
      "無法解析命名空間前置碼: {0}"},

    { ER_ILLEGAL_VALUE,
     "xml:space 具有無效值: {0}"},

    { ER_NO_OWNERDOC,
      "子項節點不具有擁有者文件！"},

    { ER_ELEMTEMPLATEELEM_ERR,
     "ElemTemplateElement 錯誤: {0}"},

    { ER_NULL_CHILD,
     "嘗試新增空值子項！"},

    { ER_NEED_SELECT_ATTRIB,
     "{0} 需要選取屬性。"},

    { ER_NEED_TEST_ATTRIB ,
      "xsl:when 必須具有 'test' 屬性。"},

    { ER_NEED_NAME_ATTRIB,
      "xsl:with-param 必須具有 'name' 屬性。"},

    { ER_NO_CONTEXT_OWNERDOC,
      "相關資訊環境不具有擁有者文件！"},

    {ER_COULD_NOT_CREATE_XML_PROC_LIAISON,
      "無法建立 XML TransformerFactory Liaison: {0}"},

    {ER_PROCESS_NOT_SUCCESSFUL,
      "Xalan: 處理作業失敗。"},

    { ER_NOT_SUCCESSFUL,
     "Xalan: 失敗！"},

    { ER_ENCODING_NOT_SUPPORTED,
     "不支援編碼: {0}"},

    {ER_COULD_NOT_CREATE_TRACELISTENER,
      "無法建立 TraceListener: {0}"},

    {ER_KEY_REQUIRES_NAME_ATTRIB,
      "xsl:key 需要 'name' 屬性！"},

    { ER_KEY_REQUIRES_MATCH_ATTRIB,
      "xsl:key 需要 'match' 屬性！"},

    { ER_KEY_REQUIRES_USE_ATTRIB,
      "xsl:key 需要 'use' 屬性！"},

    { ER_REQUIRES_ELEMENTS_ATTRIB,
      "(StylesheetHandler) {0} 需要 ''elements'' 屬性！"},

    { ER_MISSING_PREFIX_ATTRIB,
      "(StylesheetHandler) 遺漏 {0} 屬性 ''prefix''"},

    { ER_BAD_STYLESHEET_URL,
     "樣式表 URL 錯誤: {0}"},

    { ER_FILE_NOT_FOUND,
     "找不到樣式表檔案: {0}"},

    { ER_IOEXCEPTION,
      "樣式表檔案發生 IO 異常狀況: {0}"},

    { ER_NO_HREF_ATTRIB,
      "(StylesheetHandler) 找不到 {0} 的 href 屬性"},

    { ER_STYLESHEET_INCLUDES_ITSELF,
      "(StylesheetHandler) {0} 直接或間接地包含本身！"},

    { ER_PROCESSINCLUDE_ERROR,
      "StylesheetHandler.processInclude 錯誤，{0}"},

    { ER_MISSING_LANG_ATTRIB,
      "(StylesheetHandler) 遺漏 {0} 屬性 ''lang''"},

    { ER_MISSING_CONTAINER_ELEMENT_COMPONENT,
      "(StylesheetHandler) {0} 元素的位置錯誤？遺漏容器元素 ''component''"},

    { ER_CAN_ONLY_OUTPUT_TO_ELEMENT,
      "只能輸出至 Element、DocumentFragment、Document 或 PrintWriter。"},

    { ER_PROCESS_ERROR,
     "StylesheetRoot.process 錯誤"},

    { ER_UNIMPLNODE_ERROR,
     "UnImplNode 錯誤: {0}"},

    { ER_NO_SELECT_EXPRESSION,
      "錯誤！找不到 xpath 選取表示式 (-select)。"},

    { ER_CANNOT_SERIALIZE_XSLPROCESSOR,
      "無法序列化 XSLProcessor！"},

    { ER_NO_INPUT_STYLESHEET,
      "未指定樣式表輸入！"},

    { ER_FAILED_PROCESS_STYLESHEET,
      "無法處理樣式表！"},

    { ER_COULDNT_PARSE_DOC,
     "無法剖析 {0} 文件！"},

    { ER_COULDNT_FIND_FRAGMENT,
     "找不到片段: {0}"},

    { ER_NODE_NOT_ELEMENT,
      "片段 ID 指向的節點不是元素: {0}"},

    { ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB,
      "for-each 必須有配對或名稱屬性"},

    { ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB,
      "樣板必須有配對或名稱屬性"},

    { ER_NO_CLONE_OF_DOCUMENT_FRAG,
      "沒有文件片段的複製！"},

    { ER_CANT_CREATE_ITEM,
      "無法在結果樹狀結構中建立項目: {0}"},

    { ER_XMLSPACE_ILLEGAL_VALUE,
      "來源 XML 中的 xml:space 具有無效值: {0}"},

    { ER_NO_XSLKEY_DECLARATION,
      "{0} 沒有 xsl:key 宣告！"},

    { ER_CANT_CREATE_URL,
     "錯誤！無法為 {0} 建立 url"},

    { ER_XSLFUNCTIONS_UNSUPPORTED,
     "不支援 xsl:functions"},

    { ER_PROCESSOR_ERROR,
     "XSLT TransformerFactory 錯誤"},

    { ER_NOT_ALLOWED_INSIDE_STYLESHEET,
      "(StylesheetHandler) 樣式表內不允許 {0}！"},

    { ER_RESULTNS_NOT_SUPPORTED,
      "不再支援 result-ns！請改用 xsl:output。"},

    { ER_DEFAULTSPACE_NOT_SUPPORTED,
      "不再支援 default-space！請改用 xsl:strip-space 或 xsl:preserve-space。"},

    { ER_INDENTRESULT_NOT_SUPPORTED,
      "不再支援 indent-result！請改用 xsl:output。"},

    { ER_ILLEGAL_ATTRIB,
      "(StylesheetHandler) {0} 具有無效屬性: {1}"},

    { ER_UNKNOWN_XSL_ELEM,
     "不明的 XSL 元素: {0}"},

    { ER_BAD_XSLSORT_USE,
      "(StylesheetHandler) xsl:sort 只能與 xsl:apply-templates 或 xsl:for-each 一起使用。"},

    { ER_MISPLACED_XSLWHEN,
      "(StylesheetHandler) xsl:when 位置錯誤！"},

    { ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:when 的父項不是 xsl:choose！"},

    { ER_MISPLACED_XSLOTHERWISE,
      "(StylesheetHandler) xsl:otherwise 位置錯誤！"},

    { ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:otherwise 的父項不是 xsl:choose！"},

    { ER_NOT_ALLOWED_INSIDE_TEMPLATE,
      "(StylesheetHandler) 樣板內不允許 {0}！"},

    { ER_UNKNOWN_EXT_NS_PREFIX,
      "(StylesheetHandler) 不明的 {0} 擴充套件命名空間前置碼 {1}"},

    { ER_IMPORTS_AS_FIRST_ELEM,
      "(StylesheetHandler) 匯入只能發生於樣式表中的第一個元素！"},

    { ER_IMPORTING_ITSELF,
      "(StylesheetHandler) {0} 直接或間接地匯入本身！"},

    { ER_XMLSPACE_ILLEGAL_VAL,
      "(StylesheetHandler) xml:space 具有無效值: {0}"},

    { ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL,
      "processStylesheet 失敗！"},

    { ER_SAX_EXCEPTION,
     "SAX 異常狀況"},

//  add this message to fix bug 21478
    { ER_FUNCTION_NOT_SUPPORTED,
     "不支援函數！"},

    { ER_XSLT_ERROR,
     "XSLT 錯誤"},

    { ER_CURRENCY_SIGN_ILLEGAL,
      "格式樣式字串中不允許貨幣符號"},

    { ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM,
      "Stylesheet DOM 中不支援文件函數！"},

    { ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER,
      "無法解析非前置碼解析器的前置碼！"},

    { ER_REDIRECT_COULDNT_GET_FILENAME,
      "重導擴充套件: 無法取得檔案名稱 - 檔案或選取屬性必須傳回有效字串。"},

    { ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT,
      "無法在重導擴充套件中建立 FormatterListener！"},

    { ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX,
      "exclude-result-prefixes 中的前置碼無效: {0}"},

    { ER_MISSING_NS_URI,
      "遺漏指定前置碼的命名空間 URI"},

    { ER_MISSING_ARG_FOR_OPTION,
      "遺漏選項的引數: {0}"},

    { ER_INVALID_OPTION,
     "無效的選項: {0}"},

    { ER_MALFORMED_FORMAT_STRING,
     "格式錯誤的格式字串: {0}"},

    { ER_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet 需要 'version' 屬性！"},

    { ER_ILLEGAL_ATTRIBUTE_VALUE,
      "屬性: {0} 具有無效值: {1}"},

    { ER_CHOOSE_REQUIRES_WHEN,
     "xsl:choose 需要 xsl:when"},

    { ER_NO_APPLY_IMPORT_IN_FOR_EACH,
      "xsl:for-each 中不允許 xsl:apply-imports"},

    { ER_CANT_USE_DTM_FOR_OUTPUT,
      "DTMLiaison 無法用於輸出 DOM 節點。請改為傳送 com.sun.org.apache.xpath.internal.DOM2Helper！"},

    { ER_CANT_USE_DTM_FOR_INPUT,
      "DTMLiaison 無法用於輸入 DOM 節點。請改為傳送 com.sun.org.apache.xpath.internal.DOM2Helper！"},

    { ER_CALL_TO_EXT_FAILED,
      "呼叫擴充套件元素失敗: {0}"},

    { ER_PREFIX_MUST_RESOLVE,
      "前置碼必須解析為命名空間: {0}"},

    { ER_INVALID_UTF16_SURROGATE,
      "偵測到無效的 UTF-16 代理: {0}？"},

    { ER_XSLATTRSET_USED_ITSELF,
      "xsl:attribute-set {0} 使用本身，如此將造成無限迴圈。"},

    { ER_CANNOT_MIX_XERCESDOM,
      "無法混合非 Xerces-DOM 輸入與 Xerces-DOM 輸出！"},

    { ER_TOO_MANY_LISTENERS,
      "addTraceListenersToStylesheet - TooManyListenersException"},

    { ER_IN_ELEMTEMPLATEELEM_READOBJECT,
      "在 ElemTemplateElement.readObject 中: {0}"},

    { ER_DUPLICATE_NAMED_TEMPLATE,
      "找到超過一個下列名稱的樣板: {0}"},

    { ER_INVALID_KEY_CALL,
      "無效的函數呼叫: 不允許遞迴 key() 呼叫"},

    { ER_REFERENCING_ITSELF,
      "變數 {0} 直接或間接地參照本身！"},

    { ER_ILLEGAL_DOMSOURCE_INPUT,
      "newTemplates 之 DOMSource 的輸入節點不可為空值！"},

    { ER_CLASS_NOT_FOUND_FOR_OPTION,
        "找不到選項 {0} 的類別檔案"},

    { ER_REQUIRED_ELEM_NOT_FOUND,
        "找不到需要的元素: {0}"},

    { ER_INPUT_CANNOT_BE_NULL,
        "InputStream 不可為空值"},

    { ER_URI_CANNOT_BE_NULL,
        "URI 不可為空值"},

    { ER_FILE_CANNOT_BE_NULL,
        "File 不可為空值"},

    { ER_SOURCE_CANNOT_BE_NULL,
                "InputSource 不可為空值"},

    { ER_CANNOT_INIT_BSFMGR,
                "無法起始 BSF 管理程式"},

    { ER_CANNOT_CMPL_EXTENSN,
                "無法編譯擴充套件"},

    { ER_CANNOT_CREATE_EXTENSN,
      "無法建立擴充套件: {0}，因為: {1}"},

    { ER_INSTANCE_MTHD_CALL_REQUIRES,
      "執行處理方法呼叫方法 {0} 時，需要 Object 執行處理作為第一個引數"},

    { ER_INVALID_ELEMENT_NAME,
      "指定了無效的元素名稱 {0}"},

    { ER_ELEMENT_NAME_METHOD_STATIC,
      "元素名稱方法必須是靜態 {0}"},

    { ER_EXTENSION_FUNC_UNKNOWN,
             "擴充套件函數 {0} : {1} 不明"},

    { ER_MORE_MATCH_CONSTRUCTOR,
             "{0} 的建構子有超過一個以上的最佳配對"},

    { ER_MORE_MATCH_METHOD,
             "方法 {0} 有超過一個以上的最佳配對"},

    { ER_MORE_MATCH_ELEMENT,
             "元素方法 {0} 有超過一個以上的最佳配對"},

    { ER_INVALID_CONTEXT_PASSED,
             "傳送了無效的相關資訊環境來評估 {0}"},

    { ER_POOL_EXISTS,
             "集區已經存在"},

    { ER_NO_DRIVER_NAME,
             "未指定驅動程式名稱"},

    { ER_NO_URL,
             "未指定 URL"},

    { ER_POOL_SIZE_LESSTHAN_ONE,
             "集區大小小於一！"},

    { ER_INVALID_DRIVER,
             "指定了無效的驅動程式名稱！"},

    { ER_NO_STYLESHEETROOT,
             "找不到樣式表根！"},

    { ER_ILLEGAL_XMLSPACE_VALUE,
         "xml:space 的值無效"},

    { ER_PROCESSFROMNODE_FAILED,
         "processFromNode 失敗"},

    { ER_RESOURCE_COULD_NOT_LOAD,
        "無法載入資源 [ {0} ]: {1} \n {2} \t {3}"},

    { ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "緩衝區大小 <=0"},

    { ER_UNKNOWN_ERROR_CALLING_EXTENSION,
        "呼叫擴充套件時，發生不明的錯誤"},

    { ER_NO_NAMESPACE_DECL,
        "前置碼 {0} 沒有對應的命名空間宣告"},

    { ER_ELEM_CONTENT_NOT_ALLOWED,
        "元素內容不允許 lang=javaclass {0}"},

    { ER_STYLESHEET_DIRECTED_TERMINATION,
        "樣式表導向的終止"},

    { ER_ONE_OR_TWO,
        "1 或 2"},

    { ER_TWO_OR_THREE,
        "2 或 3"},

    { ER_COULD_NOT_LOAD_RESOURCE,
        "無法載入 {0} (檢查 CLASSPATH)，目前只使用預設值"},

    { ER_CANNOT_INIT_DEFAULT_TEMPLATES,
        "無法起始預設樣板"},

    { ER_RESULT_NULL,
        "結果不應為空值"},

    { ER_RESULT_COULD_NOT_BE_SET,
        "無法設定結果"},

    { ER_NO_OUTPUT_SPECIFIED,
        "未指定輸出"},

    { ER_CANNOT_TRANSFORM_TO_RESULT_TYPE,
        "無法轉換為類型 {0} 的結果"},

    { ER_CANNOT_TRANSFORM_SOURCE_TYPE,
        "無法轉換類型 {0} 的來源"},

    { ER_NULL_CONTENT_HANDLER,
        "空值內容處理程式"},

    { ER_NULL_ERROR_HANDLER,
        "空值錯誤處理程式"},

    { ER_CANNOT_CALL_PARSE,
        "若未設定 ContentHandler，則無法呼叫剖析"},

    { ER_NO_PARENT_FOR_FILTER,
        "篩選沒有父項"},

    { ER_NO_STYLESHEET_IN_MEDIA,
         "在 {0} 中找不到樣式表，媒體 = {1}"},

    { ER_NO_STYLESHEET_PI,
         "在 {0} 中找不到 xml-stylesheet PI"},

    { ER_NOT_SUPPORTED,
       "不支援: {0}"},

    { ER_PROPERTY_VALUE_BOOLEAN,
       "屬性 {0} 的值應為布林執行處理"},

    { ER_COULD_NOT_FIND_EXTERN_SCRIPT,
         "無法在 {0} 取得外部命令檔"},

    { ER_RESOURCE_COULD_NOT_FIND,
        "找不到資源 [ {0} ]。\n{1}"},

    { ER_OUTPUT_PROPERTY_NOT_RECOGNIZED,
        "無法辨識的輸出屬性: {0}"},

    { ER_FAILED_CREATING_ELEMLITRSLT,
        "無法建立 ElemLiteralResult 執行處理"},

  //Earlier (JDK 1.4 XALAN 2.2-D11) at key code '204' the key name was ER_PRIORITY_NOT_PARSABLE
  // In latest Xalan code base key name is  ER_VALUE_SHOULD_BE_NUMBER. This should also be taken care
  //in locale specific files like XSLTErrorResources_de.java, XSLTErrorResources_fr.java etc.
  //NOTE: Not only the key name but message has also been changed.
    { ER_VALUE_SHOULD_BE_NUMBER,
        "{0} 的值應包含可剖析的數字"},

    { ER_VALUE_SHOULD_EQUAL,
        "{0} 的值應等於 yes 或 no"},

    { ER_FAILED_CALLING_METHOD,
        "無法呼叫 {0} 方法"},

    { ER_FAILED_CREATING_ELEMTMPL,
        "無法建立 ElemTemplateElement 執行處理"},

    { ER_CHARS_NOT_ALLOWED,
        "文件此處不允許字元"},

    { ER_ATTR_NOT_ALLOWED,
        "{1} 元素不允許 \"{0}\" 屬性！"},

    { ER_BAD_VALUE,
     "{0} 無效值 {1} "},

    { ER_ATTRIB_VALUE_NOT_FOUND,
     "找不到 {0} 屬性值"},

    { ER_ATTRIB_VALUE_NOT_RECOGNIZED,
     "{0} 屬性值無法辨識 "},

    { ER_NULL_URI_NAMESPACE,
     "嘗試以空值 URI 產生命名空間前置碼"},

    { ER_NUMBER_TOO_BIG,
     "嘗試格式化大於最大長整數的數字"},

    { ER_CANNOT_FIND_SAX1_DRIVER,
     "找不到 SAX1 驅動程式類別 {0}"},

    { ER_SAX1_DRIVER_NOT_LOADED,
     "找到 SAX1 驅動程式類別 {0}，但無法載入"},

    { ER_SAX1_DRIVER_NOT_INSTANTIATED,
     "已載入 SAX1 驅動程式類別 {0}，但無法建立"},

    { ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER,
     "SAX1 驅動程式類別 {0} 未實行 org.xml.sax.Parser"},

    { ER_PARSER_PROPERTY_NOT_SPECIFIED,
     "未指定系統屬性 org.xml.sax.parser"},

    { ER_PARSER_ARG_CANNOT_BE_NULL,
     "剖析器引數不可為空值"},

    { ER_FEATURE,
     "功能: {0}"},

    { ER_PROPERTY,
     "屬性: {0}"},

    { ER_NULL_ENTITY_RESOLVER,
     "空值實體解析器"},

    { ER_NULL_DTD_HANDLER,
     "空值 DTD 處理程式"},

    { ER_NO_DRIVER_NAME_SPECIFIED,
     "未指定驅動程式名稱！"},

    { ER_NO_URL_SPECIFIED,
     "未指定 URL！"},

    { ER_POOLSIZE_LESS_THAN_ONE,
     "集區大小小於 1！"},

    { ER_INVALID_DRIVER_NAME,
     "指定了無效的驅動程式名稱！"},

    { ER_ERRORLISTENER,
     "ErrorListener"},


// Note to translators:  The following message should not normally be displayed
//   to users.  It describes a situation in which the processor has detected
//   an internal consistency problem in itself, and it provides this message
//   for the developer to help diagnose the problem.  The name
//   'ElemTemplateElement' is the name of a class, and should not be
//   translated.
    { ER_ASSERT_NO_TEMPLATE_PARENT,
     "程式設計人員的錯誤！表示式沒有 ElemTemplateElement 父項！"},


// Note to translators:  The following message should not normally be displayed
//   to users.  It describes a situation in which the processor has detected
//   an internal consistency problem in itself, and it provides this message
//   for the developer to help diagnose the problem.  The substitution text
//   provides further information in order to diagnose the problem.  The name
//   'RedundentExprEliminator' is the name of a class, and should not be
//   translated.
    { ER_ASSERT_REDUNDENT_EXPR_ELIMINATOR,
     "程式設計人員在 RedundentExprEliminator 中的宣告: {0}"},

    { ER_NOT_ALLOWED_IN_POSITION,
     "樣式表此位置不允許 {0}！"},

    { ER_NONWHITESPACE_NOT_ALLOWED_IN_POSITION,
     "樣式表此位置不允許非空格文字！"},

  // This code is shared with warning codes.
  // SystemId Unknown
    { INVALID_TCHAR,
     "無效值: {1} 用於 CHAR 屬性: {0}。類型 CHAR 的屬性必須僅為 1 個字元！"},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "QNAME" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value and {0} is the attribute name.
  //The following codes are shared with the warning codes...
    { INVALID_QNAME,
     "無效值: {1} 用於 QNAME 屬性: {0}"},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "ENUM" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value, {0} is the attribute name, and {2} is a list of valid
    // values.
    { INVALID_ENUM,
     "無效值: {1} 用於 ENUM 屬性: {0}。有效值為: {2}。"},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NMTOKEN" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_NMTOKEN,
     "無效值: {1} 用於 NMTOKEN 屬性: {0}"},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NCNAME" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_NCNAME,
     "無效值: {1} 用於 NCNAME 屬性: {0}"},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "boolean" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_BOOLEAN,
     "無效值: {1} 用於布林屬性: {0}"},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "number" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
     { INVALID_NUMBER,
     "無效值: {1} 用於數字屬性: {0}"},


  // End of shared codes...

// Note to translators:  A "match pattern" is a special form of XPath expression
// that is used for matching patterns.  The substitution text is the name of
// a function.  The message indicates that when this function is referenced in
// a match pattern, its argument must be a string literal (or constant.)
// ER_ARG_LITERAL - new error message for bugzilla //5202
    { ER_ARG_LITERAL,
     "配對樣式中 {0} 的引數必須是文字。"},

// Note to translators:  The following message indicates that two definitions of
// a variable.  A "global variable" is a variable that is accessible everywher
// in the stylesheet.
// ER_DUPLICATE_GLOBAL_VAR - new error message for bugzilla #790
    { ER_DUPLICATE_GLOBAL_VAR,
     "重複的全域變數宣告。"},


// Note to translators:  The following message indicates that two definitions of
// a variable were encountered.
// ER_DUPLICATE_VAR - new error message for bugzilla #790
    { ER_DUPLICATE_VAR,
     "重複的變數宣告。"},

    // Note to translators:  "xsl:template, "name" and "match" are XSLT keywords
    // which must not be translated.
    // ER_TEMPLATE_NAME_MATCH - new error message for bugzilla #789
    { ER_TEMPLATE_NAME_MATCH,
     "xsl:template 必須有名稱或配對屬性 (或具有兩者)"},

    // Note to translators:  "exclude-result-prefixes" is an XSLT keyword which
    // should not be translated.  The message indicates that a namespace prefix
    // encountered as part of the value of the exclude-result-prefixes attribute
    // was in error.
    // ER_INVALID_PREFIX - new error message for bugzilla #788
    { ER_INVALID_PREFIX,
     "exclude-result-prefixes 中的前置碼無效: {0}"},

    // Note to translators:  An "attribute set" is a set of attributes that can
    // be added to an element in the output document as a group.  The message
    // indicates that there was a reference to an attribute set named {0} that
    // was never defined.
    // ER_NO_ATTRIB_SET - new error message for bugzilla #782
    { ER_NO_ATTRIB_SET,
     "不存在名稱為 {0} 的 attribute-set"},

    // Note to translators:  This message indicates that there was a reference
    // to a function named {0} for which no function definition could be found.
    { ER_FUNCTION_NOT_FOUND,
     "不存在名稱為 {0} 的函數"},

    // Note to translators:  This message indicates that the XSLT instruction
    // that is named by the substitution text {0} must not contain other XSLT
    // instructions (content) or a "select" attribute.  The word "select" is
    // an XSLT keyword in this case and must not be translated.
    { ER_CANT_HAVE_CONTENT_AND_SELECT,
     "{0} 元素不可同時具有內容與選取屬性。"},

    // Note to translators:  This message indicates that the value argument
    // of setParameter must be a valid Java Object.
    { ER_INVALID_SET_PARAM_VALUE,
     "參數 {0} 的值必須是有效的 Java 物件"},

    { ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX_FOR_DEFAULT,
      "xsl:namespace-alias 元素的 result-prefix 屬性具有值 '#default'，但是元素範圍中沒有預設命名空間的宣告"},

    { ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX,
      "xsl:namespace-alias 元素的 result-prefix 屬性具有值 ''{0}''，但是元素範圍中沒有前置碼 ''{0}'' 的命名空間宣告。"},

    { ER_SET_FEATURE_NULL_NAME,
      "TransformerFactory.setFeature(字串名稱, 布林值) 中的功能名稱不可為空值。"},

    { ER_GET_FEATURE_NULL_NAME,
      "TransformerFactory.getFeature(字串名稱) 中的功能名稱不可為空值。"},

    { ER_UNSUPPORTED_FEATURE,
      "無法在此 TransformerFactory 上設定功能 ''{0}''。"},

    { ER_EXTENSION_ELEMENT_NOT_ALLOWED_IN_SECURE_PROCESSING,
          "當安全處理功能設為真時，不允許使用擴充套件元素 ''{0}''。"},

    { ER_NAMESPACE_CONTEXT_NULL_NAMESPACE,
      "無法取得空值命名空間 uri 的前置碼。"},

    { ER_NAMESPACE_CONTEXT_NULL_PREFIX,
      "無法取得空值前置碼的命名空間 uri。"},

    { ER_XPATH_RESOLVER_NULL_QNAME,
      "函數名稱不可為空值。"},

    { ER_XPATH_RESOLVER_NEGATIVE_ARITY,
      "Arity 不可為負值。"},
  // Warnings...

    { WG_FOUND_CURLYBRACE,
      "找到 '}'，但沒有開啟的屬性樣板！"},

    { WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR,
      "警告: 計數屬性不符合 xsl:number 中的祖系！目標 = {0}"},

    { WG_EXPR_ATTRIB_CHANGED_TO_SELECT,
      "舊語法: 'expr' 屬性的名稱已變更為 'select'。"},

    { WG_NO_LOCALE_IN_FORMATNUMBER,
      "Xalan 尚未處理 format-number 函數中的地區設定名稱。"},

    { WG_LOCALE_NOT_FOUND,
      "警告: 找不到 xml:lang={0} 的地區設定"},

    { WG_CANNOT_MAKE_URL_FROM,
      "無法從 {0} 建立 URL"},

    { WG_CANNOT_LOAD_REQUESTED_DOC,
      "無法載入要求的文件: {0}"},

    { WG_CANNOT_FIND_COLLATOR,
      "找不到 <sort xml:lang={0} 的 Collator"},

    { WG_FUNCTIONS_SHOULD_USE_URL,
      "舊語法: 函數指示應使用 {0} 的 url"},

    { WG_ENCODING_NOT_SUPPORTED_USING_UTF8,
      "不支援編碼: {0}，使用 UTF-8"},

    { WG_ENCODING_NOT_SUPPORTED_USING_JAVA,
      "不支援編碼: {0}，使用 Java {1}"},

    { WG_SPECIFICITY_CONFLICTS,
      "發現指定衝突: {0} 將使用樣式表中最後找到的項目。"},

    { WG_PARSING_AND_PREPARING,
      "========= 剖析與準備 {0} =========="},

    { WG_ATTR_TEMPLATE,
     "屬性樣板，{0}"},

    { WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESPACE,
      "xsl:strip-space 與 xsl:preserve-space 之間配對衝突"},

    { WG_ATTRIB_NOT_HANDLED,
      "Xalan 尚未處理 {0} 屬性！"},

    { WG_NO_DECIMALFORMAT_DECLARATION,
      "找不到十進位格式的宣告: {0}"},

    { WG_OLD_XSLT_NS,
     "遺漏或不正確的 XSLT 命名空間。"},

    { WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED,
      "只允許一個預設的 xsl:decimal-format 宣告。"},

    { WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE,
      "xsl:decimal-format 名稱必須是唯一的名稱。名稱 \"{0}\" 重複。"},

    { WG_ILLEGAL_ATTRIBUTE,
      "{0} 具有無效屬性: {1}"},

    { WG_COULD_NOT_RESOLVE_PREFIX,
      "無法解析命名空間前置碼: {0}。將忽略此節點。"},

    { WG_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet 需要 'version' 屬性！"},

    { WG_ILLEGAL_ATTRIBUTE_NAME,
      "無效的屬性名稱: {0}"},

    { WG_ILLEGAL_ATTRIBUTE_VALUE,
      "用於屬性 {0} 的無效值: {1}"},

    { WG_EMPTY_SECOND_ARG,
      "文件函數第二個引數產生的節點集為空白。傳回空白的 node-set。"},

  //Following are the new WARNING keys added in XALAN code base after Jdk 1.4 (Xalan 2.2-D11)

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.
    { WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "xsl:processing-instruction 名稱的 'name' 屬性值不可為 'xml'"},

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.  "NCName" is an XML data-type and must not be
    // translated.
    { WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "xsl:processing-instruction 的 ''name'' 屬性值必須是有效的 NCName: {0}"},

    // Note to translators:  This message is reported if the stylesheet that is
    // being processed attempted to construct an XML document with an attribute in a
    // place other than on an element.  The substitution text specifies the name of
    // the attribute.
    { WG_ILLEGAL_ATTRIBUTE_POSITION,
      "在產生子項節點之後，或在產生元素之前，不可新增屬性 {0}。屬性會被忽略。"},

    { NO_MODIFICATION_ALLOWED_ERR,
      "嘗試修改不允許修改的物件。"
    },

    //Check: WHY THERE IS A GAP B/W NUMBERS in the XSLTErrorResources properties file?

  // Other miscellaneous text used inside the code...
  { "ui_language", "tw"},
  {  "help_language",  "tw" },
  {  "language",  "tw" },
  { "BAD_CODE", "createMessage 的參數超出範圍"},
  {  "FORMAT_FAILED", "messageFormat 呼叫期間發生異常狀況"},
  {  "version", ">>>>>>> Xalan 版本 "},
  {  "version2",  "<<<<<<<"},
  {  "yes", "是"},
  { "line", "行號"},
  { "column","資料欄編號"},
  { "xsldone", "XSLProcessor: 完成"},


  // Note to translators:  The following messages provide usage information
  // for the Xalan Process command line.  "Process" is the name of a Java class,
  // and should not be translated.
  { "xslProc_option", "Xalan-J 命令行處理作業類別選項:"},
  { "xslProc_option", "Xalan-J 命令行處理作業類別選項:"},
  { "xslProc_invalid_xsltc_option", "XSLTC 模式中不支援選項 {0}。"},
  { "xslProc_invalid_xalan_option", "選項 {0} 只能與 -XSLTC 一起使用。"},
  { "xslProc_no_input", "錯誤: 未指定樣式表或輸入 xml。不使用任何選項來執行此命令，可取得用法指示。"},
  { "xslProc_common_options", "-一般選項-"},
  { "xslProc_xalan_options", "-Xalan 的選項-"},
  { "xslProc_xsltc_options", "-XSLTC 的選項-"},
  { "xslProc_return_to_continue", "(按 <return> 以繼續)"},

   // Note to translators: The option name and the parameter name do not need to
   // be translated. Only translate the messages in parentheses.  Note also that
   // leading whitespace in the messages is used to indent the usage information
   // for each option in the English messages.
   // Do not translate the keywords: XSLTC, SAX, DOM and DTM.
  { "optionXSLTC", "   [-XSLTC (使用 XSLTC 進行轉換)]"},
  { "optionIN", "   [-IN inputXMLURL]"},
  { "optionXSL", "   [-XSL XSLTransformationURL]"},
  { "optionOUT",  "   [-OUT outputFileName]"},
  { "optionLXCIN", "   [-LXCIN compiledStylesheetFileNameIn]"},
  { "optionLXCOUT", "   [-LXCOUT compiledStylesheetFileNameOutOut]"},
  { "optionPARSER", "   [-PARSER 剖析器聯絡的完整類別名稱]"},
  {  "optionE", "   [-E (勿展開實體參照)]"},
  {  "optionV",  "   [-E (勿展開實體參照)]"},
  {  "optionQC", "   [-QC (靜音樣式衝突警告)]"},
  {  "optionQ", "   [-Q  (靜音模式)]"},
  {  "optionLF", "   [-LF (輸出上僅使用換行字元 {預設為 CR/LF})]"},
  {  "optionCR", "   [-CR (輸出上僅使用歸位字元 {預設為 CR/LF})]"},
  { "optionESCAPE", "   [-ESCAPE (要遁離的字元 {預設為 <>&\"'\\r\\n}]"},
  { "optionINDENT", "   [-INDENT (控制要縮排的空間 {預設為 0})]"},
  { "optionTT", "   [-TT (追蹤呼叫的樣板。)]"},
  { "optionTG", "   [-TG (追蹤每個產生事件。)]"},
  { "optionTS", "   [-TS (追蹤每個選取事件。)]"},
  {  "optionTTC", "   [-TTC (追蹤處理的樣板子項。)]"},
  { "optionTCLASS", "   [-TCLASS (追蹤擴充套件的 TraceListener 類別。)]"},
  { "optionVALIDATE", "   [-VALIDATE (設定是否執行驗證。預設不會執行驗證。)]"},
  { "optionEDUMP", "   [-EDUMP {選擇性檔案名稱} (發生錯誤時會執行堆疊傾印。)]"},
  {  "optionXML", "   [-XML (使用 XML 格式器並新增 XML 標頭。)]"},
  {  "optionTEXT", "   [-TEXT (使用簡單 Text 格式器。)]"},
  {  "optionHTML", "   [-HTML (使用 HTML 格式器。)]"},
  {  "optionPARAM", "   [-PARAM 名稱表示式 (設定樣式表參數)]"},
  {  "noParsermsg1", "XSL 處理作業失敗。"},
  {  "noParsermsg2", "** 找不到剖析器 **"},
  { "noParsermsg3",  "請檢查類別路徑。"},
  { "noParsermsg4", "若無 IBM 的 XML Parser for Java，可下載自"},
  { "noParsermsg5", "IBM 的 AlphaWorks: http://www.alphaworks.ibm.com/formula/xml"},
  { "optionURIRESOLVER", "   [-URIRESOLVER 完整類別名稱 (用來解析 URI 的 URIResolver)]"},
  { "optionENTITYRESOLVER",  "   [-ENTITYRESOLVER 完整類別名稱 (用來解析實體的 EntityResolver )]"},
  { "optionCONTENTHANDLER",  "   [-CONTENTHANDLER 完整類別名稱 (用來序列化輸出的 ContentHandler)]"},
  {  "optionLINENUMBERS",  "   [-L 使用行號於來源文件]"},
  { "optionSECUREPROCESSING", "   [-SECURE (將安全處理功能設為真。)]"},

    // Following are the new options added in XSLTErrorResources.properties files after Jdk 1.4 (Xalan 2.2-D11)


  {  "optionMEDIA",  "   [-MEDIA mediaType (使用媒體屬性來尋找與文件關聯的樣式表。)]"},
  {  "optionFLAVOR",  "   [-FLAVOR flavorName (明確使用 s2s=SAX 或 d2d=DOM 來執行轉換。)] "}, // Added by sboag/scurcuru; experimental
  { "optionDIAG", "   [-DIAG (列印轉換所需要的全部毫秒。)]"},
  { "optionINCREMENTAL",  "   [-INCREMENTAL (設定 http://xml.apache.org/xalan/features/incremental 為真，以要求漸進 DTM 建構。)]"},
  {  "optionNOOPTIMIMIZE",  "   [-NOOPTIMIMIZE (設定 http://xml.apache.org/xalan/features/optimize 為偽，以要求無樣式表最佳化處理。)]"},
  { "optionRL",  "   [-RL recursionlimit (宣告樣式表遞迴深度的數字限制。)]"},
  {   "optionXO",  "   [-XO [transletName] (指派所產生 translet 的名稱)]"},
  {  "optionXD", "   [-XD destinationDirectory (指定 translet 的目的地目錄)]"},
  {  "optionXJ",  "   [-XJ jarfile (封裝 translet 類別成為名稱為 <jarfile> 的 jar 檔案)]"},
  {   "optionXP",  "   [-XP 套裝程式 (指定所有產生的 translet 類別的套裝程式名稱前置碼)]"},

  //AddITIONAL  STRINGS that need L10n
  // Note to translators:  The following message describes usage of a particular
  // command-line option that is used to enable the "template inlining"
  // optimization.  The optimization involves making a copy of the code
  // generated for a template in another template that refers to it.
  { "optionXN",  "   [-XN (啟用樣板內嵌)]" },
  { "optionXX",  "   [-XX (開啟額外的除錯訊息輸出)]"},
  { "optionXT" , "   [-XT (若有可能，使用 translet 來轉換)]"},
  { "diagTiming"," --------- 經由 {1} 的 {0} 轉換歷時 {2} 毫秒" },
  { "recursionTooDeep","樣板巢狀結構過深。巢狀結構 = {0}，樣板 {1} {2}" },
  { "nameIs", "名稱為" },
  { "matchPatternIs", "配對樣式為" }

  };

  }
  // ================= INFRASTRUCTURE ======================

  /** String for use when a bad error code was encountered.    */
  public static final String BAD_CODE = "BAD_CODE";

  /** String for use when formatting of the error string failed.   */
  public static final String FORMAT_FAILED = "FORMAT_FAILED";

    }
