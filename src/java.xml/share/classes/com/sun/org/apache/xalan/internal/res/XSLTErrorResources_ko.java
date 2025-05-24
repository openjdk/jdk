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
public class XSLTErrorResources_ko extends ListResourceBundle
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
      "오류: 표현식에는 '{'가 포함될 수 없습니다."},

    { ER_ILLEGAL_ATTRIBUTE ,
     "{0}에 잘못된 속성이 있음: {1}"},

  {ER_NULL_SOURCENODE_APPLYIMPORTS ,
      "xsl:apply-imports의 sourceNode가 널입니다!"},

  {ER_CANNOT_ADD,
      "{1}에 {0}을(를) 추가할 수 없습니다."},

    { ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES,
      "handleApplyTemplatesInstruction의 sourceNode가 널입니다!"},

    { ER_NO_NAME_ATTRIB,
     "{0}에는 name 속성이 있어야 합니다."},

    {ER_TEMPLATE_NOT_FOUND,
     "명명된 템플리트를 찾을 수 없음: {0}"},

    {ER_CANT_RESOLVE_NAME_AVT,
      "xsl:call-template에서 이름 AVT를 분석할 수 없습니다."},

    {ER_REQUIRES_ATTRIB,
     "{0}에 속성이 필요함: {1}"},

    { ER_MUST_HAVE_TEST_ATTRIB,
      "{0}에는 ''test'' 속성이 있어야 합니다."},

    {ER_BAD_VAL_ON_LEVEL_ATTRIB,
      "level 속성에 잘못된 값이 있음: {0}"},

    {ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "processing-instruction 이름은 'xml'일 수 없습니다."},

    { ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "processing-instruction 이름은 적합한 NCName이어야 함: {0}"},

    { ER_NEED_MATCH_ATTRIB,
      "{0}에 모드가 있을 경우 match 속성이 있어야 합니다."},

    { ER_NEED_NAME_OR_MATCH_ATTRIB,
      "{0}에는 name 또는 match 속성이 필요합니다."},

    {ER_CANT_RESOLVE_NSPREFIX,
      "네임스페이스 접두어를 분석할 수 없음: {0}"},

    { ER_ILLEGAL_VALUE,
     "xml:space에 잘못된 값이 있음: {0}"},

    { ER_NO_OWNERDOC,
      "하위 노드에 소유자 문서가 없습니다!"},

    { ER_ELEMTEMPLATEELEM_ERR,
     "ElemTemplateElement 오류: {0}"},

    { ER_NULL_CHILD,
     "널 하위를 추가하려고 시도하는 중입니다!"},

    { ER_NEED_SELECT_ATTRIB,
     "{0}에는 select 속성이 필요합니다."},

    { ER_NEED_TEST_ATTRIB ,
      "xsl:when에는 'test' 속성이 있어야 합니다."},

    { ER_NEED_NAME_ATTRIB,
      "xsl:with-param에는 'name' 속성이 있어야 합니다."},

    { ER_NO_CONTEXT_OWNERDOC,
      "컨텍스트에 소유자 문서가 없습니다!"},

    {ER_COULD_NOT_CREATE_XML_PROC_LIAISON,
      "XML TransformerFactory 연결을 생성할 수 없음: {0}"},

    {ER_PROCESS_NOT_SUCCESSFUL,
      "Xalan: 프로세스를 실패했습니다."},

    { ER_NOT_SUCCESSFUL,
     "Xalan: 실패했습니다."},

    { ER_ENCODING_NOT_SUPPORTED,
     "인코딩이 지원되지 않음: {0}"},

    {ER_COULD_NOT_CREATE_TRACELISTENER,
      "TraceListener를 생성할 수 없음: {0}"},

    {ER_KEY_REQUIRES_NAME_ATTRIB,
      "xsl:key에는 'name' 속성이 필요합니다!"},

    { ER_KEY_REQUIRES_MATCH_ATTRIB,
      "xsl:key에는 'match' 속성이 필요합니다!"},

    { ER_KEY_REQUIRES_USE_ATTRIB,
      "xsl:key에는 'use' 속성이 필요합니다!"},

    { ER_REQUIRES_ELEMENTS_ATTRIB,
      "(StylesheetHandler) {0}에는 ''elements'' 속성이 필요합니다!"},

    { ER_MISSING_PREFIX_ATTRIB,
      "(StylesheetHandler) {0} 속성 ''prefix''가 누락되었습니다."},

    { ER_BAD_STYLESHEET_URL,
     "스타일시트 URL이 잘못됨: {0}"},

    { ER_FILE_NOT_FOUND,
     "스타일시트 파일을 찾을 수 없음: {0}"},

    { ER_IOEXCEPTION,
      "스타일시트 파일에 IO 예외사항 발생: {0}"},

    { ER_NO_HREF_ATTRIB,
      "(StylesheetHandler) {0}에 대한 href 속성을 찾을 수 없습니다."},

    { ER_STYLESHEET_INCLUDES_ITSELF,
      "(StylesheetHandler) {0}에 직접 또는 간접적으로 자신이 포함되어 있습니다!"},

    { ER_PROCESSINCLUDE_ERROR,
      "StylesheetHandler.processInclude 오류, {0}"},

    { ER_MISSING_LANG_ATTRIB,
      "(StylesheetHandler) {0} 속성 ''lang''가 누락되었습니다."},

    { ER_MISSING_CONTAINER_ELEMENT_COMPONENT,
      "(StylesheetHandler) {0} 요소의 위치가 잘못된 것 같습니다. 컨테이너 요소 ''component''가 누락되었습니다."},

    { ER_CAN_ONLY_OUTPUT_TO_ELEMENT,
      "Element, DocumentFragment, Document 또는 PrintWriter에만 출력할 수 있습니다."},

    { ER_PROCESS_ERROR,
     "StylesheetRoot.process 오류"},

    { ER_UNIMPLNODE_ERROR,
     "UnImplNode 오류: {0}"},

    { ER_NO_SELECT_EXPRESSION,
      "오류: xpath select 표현식(-select)을 찾을 수 없습니다."},

    { ER_CANNOT_SERIALIZE_XSLPROCESSOR,
      "XSLProcessor를 직렬화할 수 없습니다!"},

    { ER_NO_INPUT_STYLESHEET,
      "스타일시트 입력값이 지정되지 않았습니다!"},

    { ER_FAILED_PROCESS_STYLESHEET,
      "스타일시트 처리를 실패했습니다!"},

    { ER_COULDNT_PARSE_DOC,
     "{0} 문서의 구문을 분석할 수 없습니다!"},

    { ER_COULDNT_FIND_FRAGMENT,
     "부분을 찾을 수 없음: {0}"},

    { ER_NODE_NOT_ELEMENT,
      "부분 식별자가 가리킨 노드는 요소가 아님: {0}"},

    { ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB,
      "for-each에는 match 또는 name 속성이 있어야 합니다."},

    { ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB,
      "templates에는 match 또는 name 속성이 있어야 합니다."},

    { ER_NO_CLONE_OF_DOCUMENT_FRAG,
      "문서 부분의 복제본이 없습니다!"},

    { ER_CANT_CREATE_ITEM,
      "결과 트리에 항목을 생성할 수 없음: {0}"},

    { ER_XMLSPACE_ILLEGAL_VALUE,
      "소스 XML의 xml:space에 잘못된 값이 있음: {0}"},

    { ER_NO_XSLKEY_DECLARATION,
      "{0}에 대한 xsl:key 선언이 없습니다!"},

    { ER_CANT_CREATE_URL,
     "오류: {0}에 대한 URL을 생성할 수 없습니다."},

    { ER_XSLFUNCTIONS_UNSUPPORTED,
     "xsl:functions는 지원되지 않습니다."},

    { ER_PROCESSOR_ERROR,
     "XSLT TransformerFactory 오류"},

    { ER_NOT_ALLOWED_INSIDE_STYLESHEET,
      "(StylesheetHandler) 스타일시트에서는 {0}이(가) 허용되지 않습니다!"},

    { ER_RESULTNS_NOT_SUPPORTED,
      "result-ns는 더 이상 지원되지 않습니다! 대신 xsl:output을 사용하십시오."},

    { ER_DEFAULTSPACE_NOT_SUPPORTED,
      "default-space는 더 이상 지원되지 않습니다! 대신 xsl:strip-space 또는 xsl:preserve-space를 사용하십시오."},

    { ER_INDENTRESULT_NOT_SUPPORTED,
      "indent-result는 더 이상 지원되지 않습니다! 대신 xsl:output을 사용하십시오."},

    { ER_ILLEGAL_ATTRIB,
      "(StylesheetHandler) {0}에 잘못된 속성이 있음: {1}"},

    { ER_UNKNOWN_XSL_ELEM,
     "알 수 없는 XSL 요소: {0}"},

    { ER_BAD_XSLSORT_USE,
      "(StylesheetHandler) xsl:sort는 xsl:apply-templates 또는 xsl:for-each와 함께만 사용할 수 있습니다."},

    { ER_MISPLACED_XSLWHEN,
      "(StylesheetHandler) xsl:when의 위치가 잘못되었습니다!"},

    { ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:when이 xsl:choose에 의해 상위로 지정되지 않았습니다!"},

    { ER_MISPLACED_XSLOTHERWISE,
      "(StylesheetHandler) xsl:otherwise의 위치가 잘못되었습니다!"},

    { ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:otherwise가 xsl:choose에 의해 상위로 지정되지 않았습니다!"},

    { ER_NOT_ALLOWED_INSIDE_TEMPLATE,
      "(StylesheetHandler) 템플리트에서는 {0}이(가) 허용되지 않습니다!"},

    { ER_UNKNOWN_EXT_NS_PREFIX,
      "(StylesheetHandler) {0} 확장 네임스페이스 접두어 {1}을(를) 알 수 없습니다."},

    { ER_IMPORTS_AS_FIRST_ELEM,
      "(StylesheetHandler) 스타일시트의 첫번째 요소로만 임포트를 수행할 수 있습니다!"},

    { ER_IMPORTING_ITSELF,
      "(StylesheetHandler) {0}이(가) 직접 또는 간접적으로 자신을 임포트하고 있습니다!"},

    { ER_XMLSPACE_ILLEGAL_VAL,
      "(StylesheetHandler) xml:space에 잘못된 값이 있음: {0}"},

    { ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL,
      "processStylesheet를 실패했습니다!"},

    { ER_SAX_EXCEPTION,
     "SAX 예외사항"},

//  add this message to fix bug 21478
    { ER_FUNCTION_NOT_SUPPORTED,
     "함수가 지원되지 않습니다!"},

    { ER_XSLT_ERROR,
     "XSLT 오류"},

    { ER_CURRENCY_SIGN_ILLEGAL,
      "형식 패턴 문자열에서는 통화 기호가 허용되지 않습니다."},

    { ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM,
      "Document 함수는 스타일시트 DOM에서 지원되지 않습니다!"},

    { ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER,
      "비접두어 분석기의 접두어를 분석할 수 없습니다!"},

    { ER_REDIRECT_COULDNT_GET_FILENAME,
      "재지정 확장: 파일 이름을 가져올 수 없습니다. file 또는 select 속성은 적합한 문자열을 반환해야 합니다."},

    { ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT,
      "재지정 확장에 FormatterListener를 생성할 수 없습니다!"},

    { ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX,
      "exclude-result-prefixes의 접두어가 부적합함: {0}"},

    { ER_MISSING_NS_URI,
      "지정된 접두어에 대한 네임스페이스 URI가 누락되었습니다."},

    { ER_MISSING_ARG_FOR_OPTION,
      "옵션에 대한 인수가 누락됨: {0}"},

    { ER_INVALID_OPTION,
     "부적합한 옵션: {0}"},

    { ER_MALFORMED_FORMAT_STRING,
     "잘못된 형식 문자열: {0}"},

    { ER_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet에는 'version' 속성이 필요합니다!"},

    { ER_ILLEGAL_ATTRIBUTE_VALUE,
      "{0} 속성에 잘못된 값이 있음: {1}"},

    { ER_CHOOSE_REQUIRES_WHEN,
     "xsl:choose에는 xsl:when이 필요합니다."},

    { ER_NO_APPLY_IMPORT_IN_FOR_EACH,
      "xsl:for-each에서는 xsl:apply-imports가 허용되지 않습니다."},

    { ER_CANT_USE_DTM_FOR_OUTPUT,
      "출력 DOM 노드에 DTMLiaison을 사용할 수 없습니다. 대신 com.sun.org.apache.xpath.internal.DOM2Helper를 전달하십시오!"},

    { ER_CANT_USE_DTM_FOR_INPUT,
      "입력 DOM 노드에 DTMLiaison을 사용할 수 없습니다. 대신 com.sun.org.apache.xpath.internal.DOM2Helper를 전달하십시오!"},

    { ER_CALL_TO_EXT_FAILED,
      "확장 요소에 대한 호출 실패: {0}"},

    { ER_PREFIX_MUST_RESOLVE,
      "접두어는 네임스페이스로 분석되어야 함: {0}"},

    { ER_INVALID_UTF16_SURROGATE,
      "부적합한 UTF-16 대리 요소가 감지됨: {0}"},

    { ER_XSLATTRSET_USED_ITSELF,
      "xsl:attribute-set {0}이(가) 자신을 사용했습니다. 이 경우 무한 루프가 발생합니다."},

    { ER_CANNOT_MIX_XERCESDOM,
      "비Xerces-DOM 입력과 Xerces-DOM 출력을 함께 사용할 수 없습니다!"},

    { ER_TOO_MANY_LISTENERS,
      "addTraceListenersToStylesheet - TooManyListenersException"},

    { ER_IN_ELEMTEMPLATEELEM_READOBJECT,
      "ElemTemplateElement.readObject에 오류 발생: {0}"},

    { ER_DUPLICATE_NAMED_TEMPLATE,
      "명명된 템플리트를 두 개 이상 찾음: {0}"},

    { ER_INVALID_KEY_CALL,
      "부적합한 함수 호출: recursive key() 호출은 허용되지 않습니다."},

    { ER_REFERENCING_ITSELF,
      "{0} 변수가 직접 또는 간접적으로 자신을 참조하고 있습니다!"},

    { ER_ILLEGAL_DOMSOURCE_INPUT,
      "newTemplates의 DOMSource에 대한 입력 노드는 널일 수 없습니다!"},

    { ER_CLASS_NOT_FOUND_FOR_OPTION,
        "{0} 옵션에 대한 클래스 파일을 찾을 수 없습니다."},

    { ER_REQUIRED_ELEM_NOT_FOUND,
        "필수 요소를 찾을 수 없음: {0}"},

    { ER_INPUT_CANNOT_BE_NULL,
        "InputStream은 널일 수 없습니다."},

    { ER_URI_CANNOT_BE_NULL,
        "URI는 널일 수 없습니다."},

    { ER_FILE_CANNOT_BE_NULL,
        "파일은 널일 수 없습니다."},

    { ER_SOURCE_CANNOT_BE_NULL,
                "InputSource는 널일 수 없습니다."},

    { ER_CANNOT_INIT_BSFMGR,
                "BSF 관리자를 초기화할 수 없습니다."},

    { ER_CANNOT_CMPL_EXTENSN,
                "확장을 컴파일할 수 없습니다."},

    { ER_CANNOT_CREATE_EXTENSN,
      "{0} 확장을 생성할 수 없는 원인: {1}"},

    { ER_INSTANCE_MTHD_CALL_REQUIRES,
      "{0} 메소드에 대한 인스턴스 메소드에는 객체 인스턴스가 첫번째 인수로 필요합니다."},

    { ER_INVALID_ELEMENT_NAME,
      "부적합한 요소 이름이 지정됨: {0}"},

    { ER_ELEMENT_NAME_METHOD_STATIC,
      "요소 이름 메소드는 정적 {0}이어야 합니다."},

    { ER_EXTENSION_FUNC_UNKNOWN,
             "확장 함수 {0}: {1}을(를) 알 수 없습니다."},

    { ER_MORE_MATCH_CONSTRUCTOR,
             "{0}에 대한 생성자와 가장 잘 일치하는 항목이 두 개 이상 있습니다."},

    { ER_MORE_MATCH_METHOD,
             "{0} 메소드와 가장 잘 일치하는 항목이 두 개 이상 있습니다."},

    { ER_MORE_MATCH_ELEMENT,
             "요소 메소드 {0}과(와) 가장 잘 일치하는 항목이 두 개 이상 있습니다."},

    { ER_INVALID_CONTEXT_PASSED,
             "{0} 평가를 위해 부적합한 컨텍스트가 전달되었습니다."},

    { ER_POOL_EXISTS,
             "풀이 존재합니다."},

    { ER_NO_DRIVER_NAME,
             "지정된 드라이버 이름이 없습니다."},

    { ER_NO_URL,
             "지정된 URL이 없습니다."},

    { ER_POOL_SIZE_LESSTHAN_ONE,
             "풀 크기가 1보다 작습니다!"},

    { ER_INVALID_DRIVER,
             "부적합한 드라이버 이름이 지정되었습니다!"},

    { ER_NO_STYLESHEETROOT,
             "스타일시트 루트를 찾을 수 없습니다!"},

    { ER_ILLEGAL_XMLSPACE_VALUE,
         "xml:space에 대한 값이 잘못되었습니다."},

    { ER_PROCESSFROMNODE_FAILED,
         "processFromNode를 실패했습니다."},

    { ER_RESOURCE_COULD_NOT_LOAD,
        "[{0}] 리소스가 다음을 로드할 수 없음: {1} \n {2} \t {3}"},

    { ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "버퍼 크기 <=0"},

    { ER_UNKNOWN_ERROR_CALLING_EXTENSION,
        "확장을 호출하는 중 알 수 없는 오류가 발생했습니다."},

    { ER_NO_NAMESPACE_DECL,
        "{0} 접두어에 해당하는 네임스페이스 선언이 없습니다."},

    { ER_ELEM_CONTENT_NOT_ALLOWED,
        "lang=javaclass {0}에 대해서는 요소 콘텐츠가 허용되지 않습니다."},

    { ER_STYLESHEET_DIRECTED_TERMINATION,
        "스타일시트가 종료를 지정했습니다."},

    { ER_ONE_OR_TWO,
        "1 또는 2"},

    { ER_TWO_OR_THREE,
        "2 또는 3"},

    { ER_COULD_NOT_LOAD_RESOURCE,
        "{0}을(를) 로드할 수 없습니다. CLASSPATH를 확인하십시오. 현재 기본값만 사용하는 중입니다."},

    { ER_CANNOT_INIT_DEFAULT_TEMPLATES,
        "기본 템플리트를 초기화할 수 없습니다."},

    { ER_RESULT_NULL,
        "결과는 널이 아니어야 합니다."},

    { ER_RESULT_COULD_NOT_BE_SET,
        "결과를 설정할 수 없습니다."},

    { ER_NO_OUTPUT_SPECIFIED,
        "지정된 출력이 없습니다."},

    { ER_CANNOT_TRANSFORM_TO_RESULT_TYPE,
        "{0} 유형의 결과로 변환할 수 없습니다."},

    { ER_CANNOT_TRANSFORM_SOURCE_TYPE,
        "{0} 유형의 소스를 변환할 수 없습니다."},

    { ER_NULL_CONTENT_HANDLER,
        "널 콘텐츠 처리기"},

    { ER_NULL_ERROR_HANDLER,
        "널 오류 처리기"},

    { ER_CANNOT_CALL_PARSE,
        "ContentHandler가 설정되지 않은 경우 parse를 호출할 수 없습니다."},

    { ER_NO_PARENT_FOR_FILTER,
        "필터에 대한 상위가 없습니다."},

    { ER_NO_STYLESHEET_IN_MEDIA,
         "{0}에서 스타일시트를 찾을 수 없습니다. 매체 = {1}"},

    { ER_NO_STYLESHEET_PI,
         "{0}에서 xml-stylesheet PI를 찾을 수 없습니다."},

    { ER_NOT_SUPPORTED,
       "지원되지 않음: {0}"},

    { ER_PROPERTY_VALUE_BOOLEAN,
       "{0} 속성에 대한 값은 부울 인스턴스여야 합니다."},

    { ER_COULD_NOT_FIND_EXTERN_SCRIPT,
         "{0}에 있는 외부 스크립트로 가져올 수 없습니다."},

    { ER_RESOURCE_COULD_NOT_FIND,
        "[{0}] 리소스를 찾을 수 없습니다.\n {1}"},

    { ER_OUTPUT_PROPERTY_NOT_RECOGNIZED,
        "출력 속성을 인식할 수 없음: {0}"},

    { ER_FAILED_CREATING_ELEMLITRSLT,
        "ElemLiteralResult 인스턴스 생성을 실패했습니다."},

  //Earlier (JDK 1.4 XALAN 2.2-D11) at key code '204' the key name was ER_PRIORITY_NOT_PARSABLE
  // In latest Xalan code base key name is  ER_VALUE_SHOULD_BE_NUMBER. This should also be taken care
  //in locale specific files like XSLTErrorResources_de.java, XSLTErrorResources_fr.java etc.
  //NOTE: Not only the key name but message has also been changed.
    { ER_VALUE_SHOULD_BE_NUMBER,
        "{0}에 대한 값에는 구문을 분석할 수 있는 숫자가 포함되어야 합니다."},

    { ER_VALUE_SHOULD_EQUAL,
        "{0}에 대한 값은 yes 또는 no여야 합니다."},

    { ER_FAILED_CALLING_METHOD,
        "{0} 메소드 호출을 실패했습니다."},

    { ER_FAILED_CREATING_ELEMTMPL,
        "ElemTemplateElement 인스턴스 생성을 실패했습니다."},

    { ER_CHARS_NOT_ALLOWED,
        "문서의 이 지점에서는 문자가 허용되지 않습니다."},

    { ER_ATTR_NOT_ALLOWED,
        "{1} 요소에서는 \"{0}\" 속성이 허용되지 않습니다!"},

    { ER_BAD_VALUE,
     "{0}: 잘못된 값 {1} "},

    { ER_ATTRIB_VALUE_NOT_FOUND,
     "{0} 속성값을 찾을 수 없습니다. "},

    { ER_ATTRIB_VALUE_NOT_RECOGNIZED,
     "{0} 속성값을 인식할 수 없습니다. "},

    { ER_NULL_URI_NAMESPACE,
     "널 URI를 사용하여 네임스페이스 접두어를 생성하려고 시도하는 중"},

    { ER_NUMBER_TOO_BIG,
     "가장 큰 Long 정수보다 큰 숫자의 형식을 지정하려고 시도하는 중"},

    { ER_CANNOT_FIND_SAX1_DRIVER,
     "SAX1 드라이버 클래스 {0}을(를) 찾을 수 없습니다."},

    { ER_SAX1_DRIVER_NOT_LOADED,
     "SAX1 드라이버 클래스 {0}이(가) 발견되었지만 해당 클래스를 로드할 수 없습니다."},

    { ER_SAX1_DRIVER_NOT_INSTANTIATED,
     "SAX1 드라이버 클래스 {0}이(가) 로드되었지만 해당 클래스를 인스턴스화할 수 없습니다."},

    { ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER,
     "SAX1 드라이버 클래스 {0}이(가) org.xml.sax.Parser를 구현하지 않았습니다."},

    { ER_PARSER_PROPERTY_NOT_SPECIFIED,
     "시스템 속성 org.xml.sax.parser가 지정되지 않았습니다."},

    { ER_PARSER_ARG_CANNOT_BE_NULL,
     "구문 분석기 인수는 널이 아니어야 합니다."},

    { ER_FEATURE,
     "기능: {0}"},

    { ER_PROPERTY,
     "속성: {0}"},

    { ER_NULL_ENTITY_RESOLVER,
     "널 엔티티 분석기"},

    { ER_NULL_DTD_HANDLER,
     "널 DTD 처리기"},

    { ER_NO_DRIVER_NAME_SPECIFIED,
     "지정된 드라이버 이름이 없습니다!"},

    { ER_NO_URL_SPECIFIED,
     "지정된 URL이 없습니다!"},

    { ER_POOLSIZE_LESS_THAN_ONE,
     "풀 크기가 1 미만입니다!"},

    { ER_INVALID_DRIVER_NAME,
     "부적합한 드라이버 이름이 지정되었습니다!"},

    { ER_ERRORLISTENER,
     "ErrorListener"},


// Note to translators:  The following message should not normally be displayed
//   to users.  It describes a situation in which the processor has detected
//   an internal consistency problem in itself, and it provides this message
//   for the developer to help diagnose the problem.  The name
//   'ElemTemplateElement' is the name of a class, and should not be
//   translated.
    { ER_ASSERT_NO_TEMPLATE_PARENT,
     "프로그래머 오류입니다! 표현식에 ElemTemplateElement 상위가 없습니다!"},


// Note to translators:  The following message should not normally be displayed
//   to users.  It describes a situation in which the processor has detected
//   an internal consistency problem in itself, and it provides this message
//   for the developer to help diagnose the problem.  The substitution text
//   provides further information in order to diagnose the problem.  The name
//   'RedundentExprEliminator' is the name of a class, and should not be
//   translated.
    { ER_ASSERT_REDUNDENT_EXPR_ELIMINATOR,
     "RedundentExprEliminator에 프로그래머 검증이 있음: {0}"},

    { ER_NOT_ALLOWED_IN_POSITION,
     "스타일시트의 이 위치에는 {0}이(가) 허용되지 않습니다!"},

    { ER_NONWHITESPACE_NOT_ALLOWED_IN_POSITION,
     "스타일시트의 이 위치에는 공백이 아닌 텍스트는 허용되지 않습니다!"},

  // This code is shared with warning codes.
  // SystemId Unknown
    { INVALID_TCHAR,
     "잘못된 값: {1}이(가) CHAR 속성에 사용됨: {0}. CHAR 유형의 속성은 1자여야 합니다!"},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "QNAME" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value and {0} is the attribute name.
  //The following codes are shared with the warning codes...
    { INVALID_QNAME,
     "잘못된 값: {1}이(가) QNAME 속성에 사용됨: {0}"},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "ENUM" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value, {0} is the attribute name, and {2} is a list of valid
    // values.
    { INVALID_ENUM,
     "잘못된 값: {1}이(가) ENUM 속성에 사용됨: {0}. 적합한 값: {2}."},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NMTOKEN" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_NMTOKEN,
     "잘못된 값: {1}이(가) NMTOKEN 속성에 사용됨: {0} "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NCNAME" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_NCNAME,
     "잘못된 값: {1}이(가) NCNAME 속성에 사용됨: {0} "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "boolean" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_BOOLEAN,
     "잘못된 값: {1}이(가) boolean 속성에 사용됨: {0} "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "number" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
     { INVALID_NUMBER,
     "잘못된 값: {1}이(가) number 속성에 사용됨: {0} "},


  // End of shared codes...

// Note to translators:  A "match pattern" is a special form of XPath expression
// that is used for matching patterns.  The substitution text is the name of
// a function.  The message indicates that when this function is referenced in
// a match pattern, its argument must be a string literal (or constant.)
// ER_ARG_LITERAL - new error message for bugzilla //5202
    { ER_ARG_LITERAL,
     "일치 패턴의 {0}에 대한 인수는 리터럴이어야 합니다."},

// Note to translators:  The following message indicates that two definitions of
// a variable.  A "global variable" is a variable that is accessible everywher
// in the stylesheet.
// ER_DUPLICATE_GLOBAL_VAR - new error message for bugzilla #790
    { ER_DUPLICATE_GLOBAL_VAR,
     "전역 변수 선언이 중복됩니다."},


// Note to translators:  The following message indicates that two definitions of
// a variable were encountered.
// ER_DUPLICATE_VAR - new error message for bugzilla #790
    { ER_DUPLICATE_VAR,
     "변수 선언이 중복됩니다."},

    // Note to translators:  "xsl:template, "name" and "match" are XSLT keywords
    // which must not be translated.
    // ER_TEMPLATE_NAME_MATCH - new error message for bugzilla #789
    { ER_TEMPLATE_NAME_MATCH,
     "xsl:template에는 name 또는 match 속성 중 하나가 있거나 모두 있어야 합니다."},

    // Note to translators:  "exclude-result-prefixes" is an XSLT keyword which
    // should not be translated.  The message indicates that a namespace prefix
    // encountered as part of the value of the exclude-result-prefixes attribute
    // was in error.
    // ER_INVALID_PREFIX - new error message for bugzilla #788
    { ER_INVALID_PREFIX,
     "exclude-result-prefixes의 접두어가 부적합함: {0}"},

    // Note to translators:  An "attribute set" is a set of attributes that can
    // be added to an element in the output document as a group.  The message
    // indicates that there was a reference to an attribute set named {0} that
    // was never defined.
    // ER_NO_ATTRIB_SET - new error message for bugzilla #782
    { ER_NO_ATTRIB_SET,
     "이름이 {0}인 attribute-set가 존재하지 않습니다."},

    // Note to translators:  This message indicates that there was a reference
    // to a function named {0} for which no function definition could be found.
    { ER_FUNCTION_NOT_FOUND,
     "이름이 {0}인 함수가 존재하지 않습니다."},

    // Note to translators:  This message indicates that the XSLT instruction
    // that is named by the substitution text {0} must not contain other XSLT
    // instructions (content) or a "select" attribute.  The word "select" is
    // an XSLT keyword in this case and must not be translated.
    { ER_CANT_HAVE_CONTENT_AND_SELECT,
     "{0} 요소에는 content 속성과 select 속성이 함께 포함되지 않아야 합니다."},

    // Note to translators:  This message indicates that the value argument
    // of setParameter must be a valid Java Object.
    { ER_INVALID_SET_PARAM_VALUE,
     "{0} 매개변수의 값은 적합한 Java 객체여야 합니다."},

    { ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX_FOR_DEFAULT,
      "xsl:namespace-alias 요소의 result-prefix 속성에 대한 값은 '#default'이지만 요소에 대한 범위에서 기본 네임스페이스가 선언되지 않았습니다."},

    { ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX,
      "xsl:namespace-alias 요소의 result-prefix 속성에 대한 값은 ''{0}''이지만 요소에 대한 범위에서 ''{0}'' 접두어의 네임스페이스가 선언되지 않았습니다."},

    { ER_SET_FEATURE_NULL_NAME,
      "기능 이름은 TransformerFactory.setFeature(문자열 이름, 부울 값)에서 널일 수 없습니다."},

    { ER_GET_FEATURE_NULL_NAME,
      "기능 이름은 TransformerFactory.getFeature(문자열 이름)에서 널일 수 없습니다."},

    { ER_UNSUPPORTED_FEATURE,
      "이 TransformerFactory에서 ''{0}'' 기능을 설정할 수 없습니다."},

    { ER_EXTENSION_ELEMENT_NOT_ALLOWED_IN_SECURE_PROCESSING,
          "보안 처리 기능이 true로 설정된 경우 확장 요소 ''{0}''을(를) 사용할 수 없습니다."},

    { ER_NAMESPACE_CONTEXT_NULL_NAMESPACE,
      "널 네임스페이스 URI에 대한 접두어를 가져올 수 없습니다."},

    { ER_NAMESPACE_CONTEXT_NULL_PREFIX,
      "널 접두어에 대한 네임스페이스 URI를 가져올 수 없습니다."},

    { ER_XPATH_RESOLVER_NULL_QNAME,
      "함수 이름은 널일 수 없습니다."},

    { ER_XPATH_RESOLVER_NEGATIVE_ARITY,
      "인자 수는 음수일 수 없습니다."},
  // Warnings...

    { WG_FOUND_CURLYBRACE,
      "'}'를 찾았지만 열려 있는 속성 템플리트가 없습니다!"},

    { WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR,
      "경고: count 속성이 xsl:number의 조상과 일치하지 않습니다! 대상 = {0}"},

    { WG_EXPR_ATTRIB_CHANGED_TO_SELECT,
      "이전 구문: 'expr' 속성의 이름이 'select'로 변경되었습니다."},

    { WG_NO_LOCALE_IN_FORMATNUMBER,
      "Xalan이 format-number 함수에서 로케일 이름을 아직 처리하지 않았습니다."},

    { WG_LOCALE_NOT_FOUND,
      "경고: xml:lang={0}에 대한 로케일을 찾을 수 없습니다."},

    { WG_CANNOT_MAKE_URL_FROM,
      "{0}에서 URL을 생성할 수 없습니다."},

    { WG_CANNOT_LOAD_REQUESTED_DOC,
      "요청된 문서를 로드할 수 없음: {0}"},

    { WG_CANNOT_FIND_COLLATOR,
      "<sort xml:lang={0}에 대한 병합기를 찾을 수 없습니다."},

    { WG_FUNCTIONS_SHOULD_USE_URL,
      "이전 구문: 함수 명령에 {0} URL이 사용되어야 합니다."},

    { WG_ENCODING_NOT_SUPPORTED_USING_UTF8,
      "인코딩이 지원되지 않음: {0}. UTF-8을 사용하는 중입니다."},

    { WG_ENCODING_NOT_SUPPORTED_USING_JAVA,
      "인코딩이 지원되지 않음: {0}. Java {1}을(를) 사용하는 중입니다."},

    { WG_SPECIFICITY_CONFLICTS,
      "특수 충돌이 발견됨: {0}. 스타일시트에서 발견된 마지막 항목이 사용됩니다."},

    { WG_PARSING_AND_PREPARING,
      "========= 구문 분석 후 {0} 준비 중 =========="},

    { WG_ATTR_TEMPLATE,
     "속성 템플리트, {0}"},

    { WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESPACE,
      "xsl:strip-space와 xsl:preserve-space 간의 일치 충돌"},

    { WG_ATTRIB_NOT_HANDLED,
      "Xalan이 {0} 속성을 아직 처리하지 않았습니다!"},

    { WG_NO_DECIMALFORMAT_DECLARATION,
      "십진수 형식에 대한 선언을 찾을 수 없음: {0}"},

    { WG_OLD_XSLT_NS,
     "XSLT 네임스페이스가 누락되거나 올바르지 않습니다. "},

    { WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED,
      "기본 xsl:decimal-format 선언은 하나만 허용됩니다."},

    { WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE,
      "xsl:decimal-format 이름은 고유해야 합니다. \"{0}\" 이름이 중복되었습니다."},

    { WG_ILLEGAL_ATTRIBUTE,
      "{0}에 잘못된 속성이 있음: {1}"},

    { WG_COULD_NOT_RESOLVE_PREFIX,
      "네임스페이스 접두어를 분석할 수 없음: {0}. 노드가 무시됩니다."},

    { WG_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet에는 'version' 속성이 필요합니다!"},

    { WG_ILLEGAL_ATTRIBUTE_NAME,
      "잘못된 속성 이름: {0}"},

    { WG_ILLEGAL_ATTRIBUTE_VALUE,
      "{0} 속성에 잘못된 값이 사용됨: {1}"},

    { WG_EMPTY_SECOND_ARG,
      "document 함수의 두번째 인수에서 결과로 나타난 nodeset가 비어 있습니다. 빈 node-set가 반환됩니다."},

  //Following are the new WARNING keys added in XALAN code base after Jdk 1.4 (Xalan 2.2-D11)

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.
    { WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "xsl:processing-instruction 이름의 'name' 속성값은 'xml'이 아니어야 합니다."},

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.  "NCName" is an XML data-type and must not be
    // translated.
    { WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "xsl:processing-instruction의 ''name'' 속성값은 적합한 NCName이어야 함: {0}"},

    // Note to translators:  This message is reported if the stylesheet that is
    // being processed attempted to construct an XML document with an attribute in a
    // place other than on an element.  The substitution text specifies the name of
    // the attribute.
    { WG_ILLEGAL_ATTRIBUTE_POSITION,
      "하위 노드가 생성된 후 또는 요소가 생성되기 전에 {0} 속성을 추가할 수 없습니다. 속성이 무시됩니다."},

    { NO_MODIFICATION_ALLOWED_ERR,
      "수정이 허용되지 않는 객체를 수정하려고 시도했습니다."
    },

    //Check: WHY THERE IS A GAP B/W NUMBERS in the XSLTErrorResources properties file?

  // Other miscellaneous text used inside the code...
  { "ui_language", "ko"},
  {  "help_language",  "ko" },
  {  "language",  "ko" },
  { "BAD_CODE", "createMessage에 대한 매개변수가 범위를 벗어났습니다."},
  {  "FORMAT_FAILED", "messageFormat 호출 중 예외사항이 발생했습니다."},
  {  "version", ">>>>>>> Xalan 버전 "},
  {  "version2",  "<<<<<<<"},
  {  "yes", "예"},
  { "line", "행 번호"},
  { "column","열 번호"},
  { "xsldone", "XSLProcessor: 완료"},


  // Note to translators:  The following messages provide usage information
  // for the Xalan Process command line.  "Process" is the name of a Java class,
  // and should not be translated.
  { "xslProc_option", "Xalan-J 명령행 Process 클래스 옵션:"},
  { "xslProc_option", "Xalan-J 명령행 Process 클래스 옵션:"},
  { "xslProc_invalid_xsltc_option", "XSLTC 모드에서는 {0} 옵션이 지원되지 않습니다."},
  { "xslProc_invalid_xalan_option", "{0} 옵션은 -XSLTC에만 사용할 수 있습니다."},
  { "xslProc_no_input", "오류: 지정된 스타일시트 또는 입력 xml이 없습니다. 사용법 지침에 대한 옵션 없이 이 명령을 실행하십시오."},
  { "xslProc_common_options", "-일반 옵션-"},
  { "xslProc_xalan_options", "-Xalan 옵션-"},
  { "xslProc_xsltc_options", "-XSLTC 옵션-"},
  { "xslProc_return_to_continue", "(계속하려면 <Return> 키를 누르십시오.)"},

   // Note to translators: The option name and the parameter name do not need to
   // be translated. Only translate the messages in parentheses.  Note also that
   // leading whitespace in the messages is used to indent the usage information
   // for each option in the English messages.
   // Do not translate the keywords: XSLTC, SAX, DOM and DTM.
  { "optionXSLTC", "   [-XSLTC(변환에 XSLTC 사용)]"},
  { "optionIN", "   [-IN inputXMLURL]"},
  { "optionXSL", "   [-XSL XSLTransformationURL]"},
  { "optionOUT",  "   [-OUT outputFileName]"},
  { "optionLXCIN", "   [-LXCIN compiledStylesheetFileNameIn]"},
  { "optionLXCOUT", "   [-LXCOUT compiledStylesheetFileNameOutOut]"},
  { "optionPARSER", "   [-PARSER 구문 분석기 연결의 전체 클래스 이름]"},
  {  "optionE", "   [-E(엔티티 참조 확장 안함)]"},
  {  "optionV",  "   [-E(엔티티 참조 확장 안함)]"},
  {  "optionQC", "   [-QC(자동 패턴 충돌 경고)]"},
  {  "optionQ", "   [-Q(자동 모드)]"},
  {  "optionLF", "   [-LF(출력에만 줄 바꿈 사용 {기본값: CR/LF})]"},
  {  "optionCR", "   [-CR(출력에만 캐리지 리턴 사용 {기본값: CR/LF})]"},
  { "optionESCAPE", "   [-ESCAPE(이스케이프 문자 {기본값: <>&\"'\\r\\n}]"},
  { "optionINDENT", "   [-INDENT(들여 쓸 공백 수 제어 {기본값: 0})]"},
  { "optionTT", "   [-TT(템플리트 호출 시 추적)]"},
  { "optionTG", "   [-TG(각 생성 이벤트 추적)]"},
  { "optionTS", "   [-TS(각 선택 이벤트 추적)]"},
  {  "optionTTC", "   [-TTC(템플리트 하위 항목 처리 시 추적)]"},
  { "optionTCLASS", "   [-TCLASS(추적 확장에 대한 TraceListener 클래스)]"},
  { "optionVALIDATE", "   [-VALIDATE(검증 여부 설정. 기본적으로 검증은 해제되어 있음)]"},
  { "optionEDUMP", "   [-EDUMP {선택적 파일 이름}(오류 발생 시 스택 덤프)]"},
  {  "optionXML", "   [-XML(XML 포맷터 사용 및 XML 헤더 추가)]"},
  {  "optionTEXT", "   [-TEXT(간단한 텍스트 포맷터 사용)]"},
  {  "optionHTML", "   [-HTML(HTML 포맷터 사용)]"},
  {  "optionPARAM", "   [-PARAM 이름 표현식(스타일시트 매개변수 설정)]"},
  {  "noParsermsg1", "XSL 프로세스를 실패했습니다."},
  {  "noParsermsg2", "** 구문 분석기를 찾을 수 없음 **"},
  { "noParsermsg3",  "클래스 경로를 확인하십시오."},
  { "noParsermsg4", "IBM의 Java용 XML 구문 분석기가 없을 경우 다음 위치에서 다운로드할 수 있습니다."},
  { "noParsermsg5", "IBM AlphaWorks: http://www.alphaworks.ibm.com/formula/xml"},
  { "optionURIRESOLVER", "   [-URIRESOLVER 전체 클래스 이름(URI 분석에 사용할 URIResolver)]"},
  { "optionENTITYRESOLVER",  "   [-ENTITYRESOLVER 전체 클래스 이름(엔티티 분석에 사용할 EntityResolver)]"},
  { "optionCONTENTHANDLER",  "   [-CONTENTHANDLER 전체 클래스 이름(출력 직렬화에 사용할 ContentHandler)]"},
  {  "optionLINENUMBERS",  "   [-L(소스 문서에 행 번호 사용)]"},
  { "optionSECUREPROCESSING", "   [-SECURE(보안 처리 기능을 true로 설정)]"},

    // Following are the new options added in XSLTErrorResources.properties files after Jdk 1.4 (Xalan 2.2-D11)


  {  "optionMEDIA",  "   [-MEDIA mediaType(media 속성을 사용하여 문서와 연관된 스타일시트 찾기)]"},
  {  "optionFLAVOR",  "   [-FLAVOR flavorName(변환에 명시적으로 s2s=SAX 또는 d2d=DOM 사용)] "}, // Added by sboag/scurcuru; experimental
  { "optionDIAG", "   [-DIAG(변환에 걸린 총 시간(밀리초) 인쇄)]"},
  { "optionINCREMENTAL",  "   [-INCREMENTAL(http://xml.apache.org/xalan/features/incremental을 true로 설정하여 증분적 DTM 생성 요청)]"},
  {  "optionNOOPTIMIMIZE",  "   [-NOOPTIMIMIZE(http://xml.apache.org/xalan/features/optimize를 false로 설정하여 스타일시트 최적화 처리 안함 요청)]"},
  { "optionRL",  "   [-RL recursionlimit(스타일시트 순환 깊이에 대한 숫자 제한 검증)]"},
  {   "optionXO",  "   [-XO [transletName](생성된 translet에 이름 지정)]"},
  {  "optionXD", "   [-XD destinationDirectory(translet에 대한 대상 디렉토리 지정)]"},
  {  "optionXJ",  "   [-XJ jarfile(translet 클래스를 <jarfile> 이름의 jar 파일로 패키지화)]"},
  {   "optionXP",  "   [-XP package(생성된 모든 translet 클래스에 대한 패키지 이름 접두어 지정)]"},

  //AddITIONAL  STRINGS that need L10n
  // Note to translators:  The following message describes usage of a particular
  // command-line option that is used to enable the "template inlining"
  // optimization.  The optimization involves making a copy of the code
  // generated for a template in another template that refers to it.
  { "optionXN",  "   [-XN(템플리트 인라인을 사용으로 설정)]" },
  { "optionXX",  "   [-XX(추가 디버깅 메시지 출력 설정)]"},
  { "optionXT" , "   [-XT(가능한 경우 변환에 translet 사용)]"},
  { "diagTiming"," --------- {1}을(를) 통한 {0} 변환에 {2}밀리초가 걸렸습니다." },
  { "recursionTooDeep","템플리트가 너무 깊게 중첩되었습니다. 중첩 = {0}, 템플리트: {1} {2}" },
  { "nameIs", "이름:" },
  { "matchPatternIs", "일치 패턴:" }

  };

  }
  // ================= INFRASTRUCTURE ======================

  /** String for use when a bad error code was encountered.    */
  public static final String BAD_CODE = "BAD_CODE";

  /** String for use when formatting of the error string failed.   */
  public static final String FORMAT_FAILED = "FORMAT_FAILED";

    }
