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
public class XSLTErrorResources_pt_BR extends ListResourceBundle
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
      "Erro: Não é possível utilizar ''{'' na expressão"},

    { ER_ILLEGAL_ATTRIBUTE ,
     "{0} tem um atributo inválido: {1}"},

  {ER_NULL_SOURCENODE_APPLYIMPORTS ,
      "sourceNode é nulo em xsl:apply-imports!"},

  {ER_CANNOT_ADD,
      "Não é possível adicionar {0} a {1}"},

    { ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES,
      "sourceNode é nulo em handleApplyTemplatesInstruction!"},

    { ER_NO_NAME_ATTRIB,
     "{0} deve ter um atributo de nome."},

    {ER_TEMPLATE_NOT_FOUND,
     "Não foi possível localizar o modelo com o nome: {0}"},

    {ER_CANT_RESOLVE_NAME_AVT,
      "Não foi possível resolver o nome AVT em xsl:call-template."},

    {ER_REQUIRES_ATTRIB,
     "{0} requer o atributo: {1}"},

    { ER_MUST_HAVE_TEST_ATTRIB,
      "{0} deve ter um atributo ''test''."},

    {ER_BAD_VAL_ON_LEVEL_ATTRIB,
      "Valor inválido no atributo de nível: {0}"},

    {ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "o nome da instrução de processamento não pode ser 'xml'"},

    { ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "o nome da instrução de processamento deve ser um NCName válido: {0}"},

    { ER_NEED_MATCH_ATTRIB,
      "{0} deve ter um atributo de correspondência se tiver um modo."},

    { ER_NEED_NAME_OR_MATCH_ATTRIB,
      "{0} requer um atributo de nome ou de correspondência."},

    {ER_CANT_RESOLVE_NSPREFIX,
      "Não é possível resolver o prefixo do namespace: {0}"},

    { ER_ILLEGAL_VALUE,
     "xml:space tem um valor inválido: {0}"},

    { ER_NO_OWNERDOC,
      "O nó filho não tem um documento de proprietário!"},

    { ER_ELEMTEMPLATEELEM_ERR,
     "Erro de ElemTemplateElement: {0}"},

    { ER_NULL_CHILD,
     "Tentativa de adicionar um filho nulo!"},

    { ER_NEED_SELECT_ATTRIB,
     "{0} requer um atributo de seleção."},

    { ER_NEED_TEST_ATTRIB ,
      "xsl:when deve ter um atributo 'test'."},

    { ER_NEED_NAME_ATTRIB,
      "xsl:with-param deve ter um atributo 'name'."},

    { ER_NO_CONTEXT_OWNERDOC,
      "o contexto não tem um documento de proprietário!"},

    {ER_COULD_NOT_CREATE_XML_PROC_LIAISON,
      "Não foi possível criar a Ligação TransformerFactory XML: {0}"},

    {ER_PROCESS_NOT_SUCCESSFUL,
      "Xalan: O processo não foi bem-sucedido."},

    { ER_NOT_SUCCESSFUL,
     "Xalan: Não foi bem-sucedido."},

    { ER_ENCODING_NOT_SUPPORTED,
     "Codificação não suportada: {0}"},

    {ER_COULD_NOT_CREATE_TRACELISTENER,
      "Não foi possível criar TraceListener: {0}"},

    {ER_KEY_REQUIRES_NAME_ATTRIB,
      "xsl:key requer um atributo 'name'!"},

    { ER_KEY_REQUIRES_MATCH_ATTRIB,
      "xsl:key requer um atributo 'match'!"},

    { ER_KEY_REQUIRES_USE_ATTRIB,
      "xsl:key requer um atributo 'use'!"},

    { ER_REQUIRES_ELEMENTS_ATTRIB,
      "(StylesheetHandler) {0} requer um atributo ''elements''!"},

    { ER_MISSING_PREFIX_ATTRIB,
      "(StylesheetHandler) o atributo ''prefix'' de {0} não foi encontrado"},

    { ER_BAD_STYLESHEET_URL,
     "O URL da Folha de Estilos está incorreto: {0}"},

    { ER_FILE_NOT_FOUND,
     "O arquivo da folha de estilos não foi encontrado: {0}"},

    { ER_IOEXCEPTION,
      "Exceção de E/S com o arquivo de folha de estilos: {0}"},

    { ER_NO_HREF_ATTRIB,
      "(StylesheetHandler) Não foi possível encontrar o atributo href para {0}"},

    { ER_STYLESHEET_INCLUDES_ITSELF,
      "(StylesheetHandler) A folha de estilos {0} está incluindo a si mesma direta ou indiretamente!"},

    { ER_PROCESSINCLUDE_ERROR,
      "Erro de StylesheetHandler.processInclude: {0}"},

    { ER_MISSING_LANG_ATTRIB,
      "(StylesheetHandler) O atributo ''lang'' de {0} não foi encontrado"},

    { ER_MISSING_CONTAINER_ELEMENT_COMPONENT,
      "(StylesheetHandler) elemento {0} incorretamente posicionado?? Elemento ''component'' do container não encontrado"},

    { ER_CAN_ONLY_OUTPUT_TO_ELEMENT,
      "Saída permitida somente para Element, DocumentFragment, Document ou PrintWriter."},

    { ER_PROCESS_ERROR,
     "Erro de StylesheetRoot.process"},

    { ER_UNIMPLNODE_ERROR,
     "Erro de UnImplNode: {0}"},

    { ER_NO_SELECT_EXPRESSION,
      "Erro! Não foi possível localizar a expressão de seleção xpath (-select)."},

    { ER_CANNOT_SERIALIZE_XSLPROCESSOR,
      "Não é possível serializar um XSLProcessor!"},

    { ER_NO_INPUT_STYLESHEET,
      "A entrada da folha de estilos não foi especificada!"},

    { ER_FAILED_PROCESS_STYLESHEET,
      "Falha ao processar a folha de estilos!"},

    { ER_COULDNT_PARSE_DOC,
     "Não foi possível fazer parsing do documento {0}!"},

    { ER_COULDNT_FIND_FRAGMENT,
     "Não foi possível localizar o fragmento: {0}"},

    { ER_NODE_NOT_ELEMENT,
      "O nó indicado pelo identificador de fragmento não era um elemento: {0}"},

    { ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB,
      "for-each deve ter um atributo de correspondência ou de nome"},

    { ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB,
      "os modelos devem ter um atributo de correspondência ou de nome"},

    { ER_NO_CLONE_OF_DOCUMENT_FRAG,
      "Não há clone de um fragmento de documento!"},

    { ER_CANT_CREATE_ITEM,
      "Não é possível criar um item em uma árvore de resultados: {0}"},

    { ER_XMLSPACE_ILLEGAL_VALUE,
      "xml:space no XML de origem tem um valor inválido: {0}"},

    { ER_NO_XSLKEY_DECLARATION,
      "Não há uma declaração de xsl:key para {0}!"},

    { ER_CANT_CREATE_URL,
     "Erro! Não é possível criar o url para: {0}"},

    { ER_XSLFUNCTIONS_UNSUPPORTED,
     "xsl:functions não é suportado"},

    { ER_PROCESSOR_ERROR,
     "Erro de TransformerFactory XSLT"},

    { ER_NOT_ALLOWED_INSIDE_STYLESHEET,
      "(StylesheetHandler) {0} não é permitido em uma folha de estilos!"},

    { ER_RESULTNS_NOT_SUPPORTED,
      "result-ns não é mais suportado! Em vez disso, use xsl:output."},

    { ER_DEFAULTSPACE_NOT_SUPPORTED,
      "padrão-space não é mais suportado! Em vez disso, use xsl:strip-space ou xsl:preserve-space."},

    { ER_INDENTRESULT_NOT_SUPPORTED,
      "indent-result não é mais suportado! Em vez disso, use xsl:output."},

    { ER_ILLEGAL_ATTRIB,
      "(StylesheetHandler) {0} tem um atributo inválido: {1}"},

    { ER_UNKNOWN_XSL_ELEM,
     "Elemento XSL desconhecido: {0}"},

    { ER_BAD_XSLSORT_USE,
      "(StylesheetHandler) xsl:sort só pode ser usado com xsl:apply-templates ou xsl:for-each."},

    { ER_MISPLACED_XSLWHEN,
      "(StylesheetHandler) xsl:when posicionado incorretamente!"},

    { ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:when não relacionado a xsl:choose!"},

    { ER_MISPLACED_XSLOTHERWISE,
      "(StylesheetHandler) xsl:otherwise posicionado incorretamente!"},

    { ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:otherwise não relacionado a xsl:choose!"},

    { ER_NOT_ALLOWED_INSIDE_TEMPLATE,
      "(StylesheetHandler) {0} não é permitido em um modelo!"},

    { ER_UNKNOWN_EXT_NS_PREFIX,
      "(StylesheetHandler) prefixo {1} de namespace da extensão de {0} desconhecido"},

    { ER_IMPORTS_AS_FIRST_ELEM,
      "(StylesheetHandler) As importações só podem ocorrer como os primeiros elementos na folha de estilos!"},

    { ER_IMPORTING_ITSELF,
      "(StylesheetHandler) A folha de estilos {0} está importando a si mesmo(a) direta ou indiretamente!"},

    { ER_XMLSPACE_ILLEGAL_VAL,
      "(StylesheetHandler) xml:space tem um valor inválido: {0}"},

    { ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL,
      "processStylesheet malsucedido!"},

    { ER_SAX_EXCEPTION,
     "Exceção de SAX"},

//  add this message to fix bug 21478
    { ER_FUNCTION_NOT_SUPPORTED,
     "Função não suportada!"},

    { ER_XSLT_ERROR,
     "Erro de XSLT"},

    { ER_CURRENCY_SIGN_ILLEGAL,
      "sinal de moeda não permitido na string de padrão de formato"},

    { ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM,
      "Função do documento não suportada no DOM da Folha de estilos!"},

    { ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER,
      "Não é possível resolver o prefixo de um resolvedor sem Prefixo!"},

    { ER_REDIRECT_COULDNT_GET_FILENAME,
      "Redirecionar extensão: Não foi possível obter o nome do arquivo - o arquivo ou o atributo de seleção deve retornar uma string válida."},

    { ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT,
      "Não é possível criar FormatterListener na extensão de Redirecionamento!"},

    { ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX,
      "O prefixo em exclude-result-prefixes não é válido: {0}"},

    { ER_MISSING_NS_URI,
      "URI do namespace não encontrado para o prefixo especificado"},

    { ER_MISSING_ARG_FOR_OPTION,
      "Argumento não encontrado para a opção: {0}"},

    { ER_INVALID_OPTION,
     "Opção inválida: {0}"},

    { ER_MALFORMED_FORMAT_STRING,
     "String de formato incorreta: {0}"},

    { ER_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet requer um atributo 'version'!"},

    { ER_ILLEGAL_ATTRIBUTE_VALUE,
      "Atributo: {0} tem um valor inválido: {1}"},

    { ER_CHOOSE_REQUIRES_WHEN,
     "xsl:choose requer um xsl:when"},

    { ER_NO_APPLY_IMPORT_IN_FOR_EACH,
      "xsl:apply-imports não permitido em um xsl:for-each"},

    { ER_CANT_USE_DTM_FOR_OUTPUT,
      "Não é possível usar um DTMLiaison para um nó DOM de saída... em vez disso, especifique um com.sun.org.apache.xpath.internal.DOM2Helper!"},

    { ER_CANT_USE_DTM_FOR_INPUT,
      "Não é possível usar um DTMLiaison para um nó DOM de entrada... em vez disso, especifique um com.sun.org.apache.xpath.internal.DOM2Helper!"},

    { ER_CALL_TO_EXT_FAILED,
      "Falha ao chamar o elemento da extensão: {0}"},

    { ER_PREFIX_MUST_RESOLVE,
      "O prefixo deve ser resolvido para um namespace: {0}"},

    { ER_INVALID_UTF16_SURROGATE,
      "Foi detectado um substituto de UTF-16 inválido: {0} ?"},

    { ER_XSLATTRSET_USED_ITSELF,
      "xsl:attribute-set {0} usou ele mesmo, o que causará um loop infinito."},

    { ER_CANNOT_MIX_XERCESDOM,
      "Não é possível misturar entrada não Xerces-DOM com saída Xerces-DOM!"},

    { ER_TOO_MANY_LISTENERS,
      "addTraceListenersToStylesheet - TooManyListenersException"},

    { ER_IN_ELEMTEMPLATEELEM_READOBJECT,
      "No ElemTemplateElement.readObject: {0}"},

    { ER_DUPLICATE_NAMED_TEMPLATE,
      "Foi encontrado mais de um modelo com o nome: {0}"},

    { ER_INVALID_KEY_CALL,
      "Chamada de função inválida: chamadas recursivas de key() não são permitidas"},

    { ER_REFERENCING_ITSELF,
      "A variável {0} está importando ela mesma de forma direta ou indireta!"},

    { ER_ILLEGAL_DOMSOURCE_INPUT,
      "O nó de entrada não pode ser nulo para um DOMSource para newTemplates!"},

    { ER_CLASS_NOT_FOUND_FOR_OPTION,
        "O arquivo de classe não foi encontrado para a opção {0}"},

    { ER_REQUIRED_ELEM_NOT_FOUND,
        "Elemento Obrigatório não encontrado: {0}"},

    { ER_INPUT_CANNOT_BE_NULL,
        "InputStream não pode ser nulo"},

    { ER_URI_CANNOT_BE_NULL,
        "O URI não pode ser nulo"},

    { ER_FILE_CANNOT_BE_NULL,
        "O arquivo não pode ser nulo"},

    { ER_SOURCE_CANNOT_BE_NULL,
                "InputSource não pode ser nulo"},

    { ER_CANNOT_INIT_BSFMGR,
                "Não foi possível inicializar o Gerenciador de BSF"},

    { ER_CANNOT_CMPL_EXTENSN,
                "Não foi possível compilar a extensão"},

    { ER_CANNOT_CREATE_EXTENSN,
      "Não foi possível criar a extensão: {0} em decorrência de: {1}"},

    { ER_INSTANCE_MTHD_CALL_REQUIRES,
      "A chamada do método da instância para o método {0} exige uma instância do Objeto como primeiro argumento"},

    { ER_INVALID_ELEMENT_NAME,
      "Nome de elemento inválido especificado {0}"},

    { ER_ELEMENT_NAME_METHOD_STATIC,
      "O método do nome do elemento deve ser estático {0}"},

    { ER_EXTENSION_FUNC_UNKNOWN,
             "Função da extensão {0} : {1} desconhecido"},

    { ER_MORE_MATCH_CONSTRUCTOR,
             "Há mais de uma melhor correspondência para o construtor em relação a {0}"},

    { ER_MORE_MATCH_METHOD,
             "Há mais de uma melhor correspondência para o método {0}"},

    { ER_MORE_MATCH_ELEMENT,
             "Há mais de uma melhor correspondência para o método do elemento {0}"},

    { ER_INVALID_CONTEXT_PASSED,
             "Contexto inválido especificado para avaliar {0}"},

    { ER_POOL_EXISTS,
             "O pool já existe"},

    { ER_NO_DRIVER_NAME,
             "Nenhum Nome do driver especificado"},

    { ER_NO_URL,
             "Nenhum URL especificado"},

    { ER_POOL_SIZE_LESSTHAN_ONE,
             "O tamanho do pool é menor que um!"},

    { ER_INVALID_DRIVER,
             "Nome do driver inválido especificado!"},

    { ER_NO_STYLESHEETROOT,
             "A raiz da folha de estilos não foi encontrada!"},

    { ER_ILLEGAL_XMLSPACE_VALUE,
         "Valor inválido para xml:space"},

    { ER_PROCESSFROMNODE_FAILED,
         "Falha em processFromNode"},

    { ER_RESOURCE_COULD_NOT_LOAD,
        "O recurso [ {0} ] não foi carregado: {1} \n {2} \t {3}"},

    { ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "Tamanho do buffer <=0"},

    { ER_UNKNOWN_ERROR_CALLING_EXTENSION,
        "Erro desconhecido ao chamar a extensão"},

    { ER_NO_NAMESPACE_DECL,
        "O prefixo {0} não tem uma declaração de namespace correspondente"},

    { ER_ELEM_CONTENT_NOT_ALLOWED,
        "Conteúdo do elemento não permitido para lang=javaclass {0}"},

    { ER_STYLESHEET_DIRECTED_TERMINATION,
        "Término direcionado da folha de estilos"},

    { ER_ONE_OR_TWO,
        "1 ou 2"},

    { ER_TWO_OR_THREE,
        "2 ou 3"},

    { ER_COULD_NOT_LOAD_RESOURCE,
        "Não foi possível carregar {0} (verificar CLASSPATH); usando agora apenas os padrões"},

    { ER_CANNOT_INIT_DEFAULT_TEMPLATES,
        "Não é possível inicializar os modelos padrão"},

    { ER_RESULT_NULL,
        "O resultado não deve ser nulo"},

    { ER_RESULT_COULD_NOT_BE_SET,
        "Não foi possível definir o resultado"},

    { ER_NO_OUTPUT_SPECIFIED,
        "Nenhuma saída especificada"},

    { ER_CANNOT_TRANSFORM_TO_RESULT_TYPE,
        "Não é possível transformar um Resultado do tipo {0}"},

    { ER_CANNOT_TRANSFORM_SOURCE_TYPE,
        "Não é possível transformar uma Origem do tipo {0}"},

    { ER_NULL_CONTENT_HANDLER,
        "Handler de conteúdo nulo"},

    { ER_NULL_ERROR_HANDLER,
        "Handler de erro nulo"},

    { ER_CANNOT_CALL_PARSE,
        "o parsing não poderá ser chamado se o ContentHandler não tiver sido definido"},

    { ER_NO_PARENT_FOR_FILTER,
        "Nenhum pai para o filtro"},

    { ER_NO_STYLESHEET_IN_MEDIA,
         "Nenhuma folha de estilos encontrada em: {0}, mídia= {1}"},

    { ER_NO_STYLESHEET_PI,
         "Nenhum PI de xml-stylesheet encontrado em: {0}"},

    { ER_NOT_SUPPORTED,
       "Não suportado: {0}"},

    { ER_PROPERTY_VALUE_BOOLEAN,
       "O valor da propriedade {0} deve ser uma instância Booliana"},

    { ER_COULD_NOT_FIND_EXTERN_SCRIPT,
         "Não foi possível obter um script externo em {0}"},

    { ER_RESOURCE_COULD_NOT_FIND,
        "Não foi possível encontrar o recurso [ {0} ].\n {1}"},

    { ER_OUTPUT_PROPERTY_NOT_RECOGNIZED,
        "Propriedade de saída não reconhecida: {0}"},

    { ER_FAILED_CREATING_ELEMLITRSLT,
        "Falha ao criar a instância ElemLiteralResult"},

  //Earlier (JDK 1.4 XALAN 2.2-D11) at key code '204' the key name was ER_PRIORITY_NOT_PARSABLE
  // In latest Xalan code base key name is  ER_VALUE_SHOULD_BE_NUMBER. This should also be taken care
  //in locale specific files like XSLTErrorResources_de.java, XSLTErrorResources_fr.java etc.
  //NOTE: Not only the key name but message has also been changed.
    { ER_VALUE_SHOULD_BE_NUMBER,
        "O valor para {0} deve conter um número passível de parsing"},

    { ER_VALUE_SHOULD_EQUAL,
        "O valor para {0} deve ser igual a sim ou não"},

    { ER_FAILED_CALLING_METHOD,
        "Falha ao chamar o método {0}"},

    { ER_FAILED_CREATING_ELEMTMPL,
        "Falha ao criar a instância ElemTemplateElement"},

    { ER_CHARS_NOT_ALLOWED,
        "Os caracteres não são permitidos neste ponto do documento"},

    { ER_ATTR_NOT_ALLOWED,
        "O atributo \"{0}\" não é permitido no elemento {1}!"},

    { ER_BAD_VALUE,
     "{0} valor incorreto {1} "},

    { ER_ATTRIB_VALUE_NOT_FOUND,
     "valor do atributo {0} não encontrado "},

    { ER_ATTRIB_VALUE_NOT_RECOGNIZED,
     "Valor do atributo {0} não reconhecido "},

    { ER_NULL_URI_NAMESPACE,
     "Tentativa de gerar um prefixo do namespace com um URI nulo"},

    { ER_NUMBER_TOO_BIG,
     "Tentativa de formatar um número maior que o número inteiro Longo maior"},

    { ER_CANNOT_FIND_SAX1_DRIVER,
     "Não é possível localizar a classe do driver SAX1 {0}"},

    { ER_SAX1_DRIVER_NOT_LOADED,
     "A classe do driver SAX1 {0} foi encontrada, mas não pode ser carregada"},

    { ER_SAX1_DRIVER_NOT_INSTANTIATED,
     "A classe do driver SAX1 {0} foi carregada, mas não pode ser instanciada"},

    { ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER,
     "A classe do driver SAX1 {0} não implementa org.xml.sax.Parser"},

    { ER_PARSER_PROPERTY_NOT_SPECIFIED,
     "A propriedade do sistema org.xml.sax.parser não foi especificada"},

    { ER_PARSER_ARG_CANNOT_BE_NULL,
     "O argumento de parser não pode ser nulo"},

    { ER_FEATURE,
     "Recurso: {0}"},

    { ER_PROPERTY,
     "Propriedade: {0}"},

    { ER_NULL_ENTITY_RESOLVER,
     "Resolvedor da entidade nulo"},

    { ER_NULL_DTD_HANDLER,
     "Handler de DTD nulo"},

    { ER_NO_DRIVER_NAME_SPECIFIED,
     "Nenhum Nome do Driver Especificado!"},

    { ER_NO_URL_SPECIFIED,
     "Nenhum URL Especificado!"},

    { ER_POOLSIZE_LESS_THAN_ONE,
     "O tamanho do pool é menor que 1!"},

    { ER_INVALID_DRIVER_NAME,
     "Nome do Driver Especificado Inválido!"},

    { ER_ERRORLISTENER,
     "ErrorListener"},


// Note to translators:  The following message should not normally be displayed
//   to users.  It describes a situation in which the processor has detected
//   an internal consistency problem in itself, and it provides this message
//   for the developer to help diagnose the problem.  The name
//   'ElemTemplateElement' is the name of a class, and should not be
//   translated.
    { ER_ASSERT_NO_TEMPLATE_PARENT,
     "Erro do programador! A expressão não tem ElemTemplateElement pai!"},


// Note to translators:  The following message should not normally be displayed
//   to users.  It describes a situation in which the processor has detected
//   an internal consistency problem in itself, and it provides this message
//   for the developer to help diagnose the problem.  The substitution text
//   provides further information in order to diagnose the problem.  The name
//   'RedundentExprEliminator' is the name of a class, and should not be
//   translated.
    { ER_ASSERT_REDUNDENT_EXPR_ELIMINATOR,
     "Asserção do Programador no RedundentExprEliminator: {0}"},

    { ER_NOT_ALLOWED_IN_POSITION,
     "{0} não é permitido(a) nesta posição na folha de estilos!"},

    { ER_NONWHITESPACE_NOT_ALLOWED_IN_POSITION,
     "Texto sem espaço em branco não permitido nesta posição na folha de estilos!"},

  // This code is shared with warning codes.
  // SystemId Unknown
    { INVALID_TCHAR,
     "Valor inválido: {1} usado para o atributo CHAR: {0}. Um atributo do tipo CHAR deve ter somente 1 caractere!"},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "QNAME" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value and {0} is the attribute name.
  //The following codes are shared with the warning codes...
    { INVALID_QNAME,
     "Valor inválido: {1} usado para o atributo QNAME: {0}"},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "ENUM" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value, {0} is the attribute name, and {2} is a list of valid
    // values.
    { INVALID_ENUM,
     "Valor inválido: {1} usado para o atributo ENUM: {0}. Os valores válidos são: {2}."},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NMTOKEN" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_NMTOKEN,
     "Valor inválido: {1} usado para o atributo NMTOKEN: {0} "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NCNAME" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_NCNAME,
     "Valor inválido: {1} usado para o atributo NCNAME: {0} "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "boolean" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_BOOLEAN,
     "Valor inválido: {1} usado para o atributo boolean: {0} "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "number" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
     { INVALID_NUMBER,
     "Valor inválido: {1} usado para o atributo do número: {0} "},


  // End of shared codes...

// Note to translators:  A "match pattern" is a special form of XPath expression
// that is used for matching patterns.  The substitution text is the name of
// a function.  The message indicates that when this function is referenced in
// a match pattern, its argument must be a string literal (or constant.)
// ER_ARG_LITERAL - new error message for bugzilla //5202
    { ER_ARG_LITERAL,
     "O argumento para {0} no padrão de correspondência deve ser um literal."},

// Note to translators:  The following message indicates that two definitions of
// a variable.  A "global variable" is a variable that is accessible everywher
// in the stylesheet.
// ER_DUPLICATE_GLOBAL_VAR - new error message for bugzilla #790
    { ER_DUPLICATE_GLOBAL_VAR,
     "Declaração de variável global duplicada."},


// Note to translators:  The following message indicates that two definitions of
// a variable were encountered.
// ER_DUPLICATE_VAR - new error message for bugzilla #790
    { ER_DUPLICATE_VAR,
     "Declaração de variável duplicada."},

    // Note to translators:  "xsl:template, "name" and "match" are XSLT keywords
    // which must not be translated.
    // ER_TEMPLATE_NAME_MATCH - new error message for bugzilla #789
    { ER_TEMPLATE_NAME_MATCH,
     "xsl:template deve ter um atributo name ou match (ou ambos)"},

    // Note to translators:  "exclude-result-prefixes" is an XSLT keyword which
    // should not be translated.  The message indicates that a namespace prefix
    // encountered as part of the value of the exclude-result-prefixes attribute
    // was in error.
    // ER_INVALID_PREFIX - new error message for bugzilla #788
    { ER_INVALID_PREFIX,
     "O prefixo em exclude-result-prefixes não é válido: {0}"},

    // Note to translators:  An "attribute set" is a set of attributes that can
    // be added to an element in the output document as a group.  The message
    // indicates that there was a reference to an attribute set named {0} that
    // was never defined.
    // ER_NO_ATTRIB_SET - new error message for bugzilla #782
    { ER_NO_ATTRIB_SET,
     "o conjunto de atributos com o nome {0} não existe"},

    // Note to translators:  This message indicates that there was a reference
    // to a function named {0} for which no function definition could be found.
    { ER_FUNCTION_NOT_FOUND,
     "A função com o nome {0} não existe"},

    // Note to translators:  This message indicates that the XSLT instruction
    // that is named by the substitution text {0} must not contain other XSLT
    // instructions (content) or a "select" attribute.  The word "select" is
    // an XSLT keyword in this case and must not be translated.
    { ER_CANT_HAVE_CONTENT_AND_SELECT,
     "O elemento {0} não deve ter um conteúdo e um atributo select."},

    // Note to translators:  This message indicates that the value argument
    // of setParameter must be a valid Java Object.
    { ER_INVALID_SET_PARAM_VALUE,
     "O valor do parâmetro {0} deve ser um Objeto Java válido"},

    { ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX_FOR_DEFAULT,
      "O atributo result-prefix de um elemento xsl:namespace-alias tem o valor '#padrão', mas não há declaração do namespace padrão no escopo do elemento"},

    { ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX,
      "O atributo result-prefix de um elemento xsl:namespace-alias tem o valor ''{0}'', mas não há declaração de namespace para o prefixo ''{0}'' no escopo do elemento."},

    { ER_SET_FEATURE_NULL_NAME,
      "O nome do recurso não pode ser nulo em TransformerFactory.setFeature(Nome da string, valor booliano)."},

    { ER_GET_FEATURE_NULL_NAME,
      "O nome do recurso não pode ser nulo em TransformerFactory.getFeature(Nome da string)."},

    { ER_UNSUPPORTED_FEATURE,
      "Não é possível definir o recurso ''{0}'' nesta TransformerFactory."},

    { ER_EXTENSION_ELEMENT_NOT_ALLOWED_IN_SECURE_PROCESSING,
          "O uso do elemento da extensão ''{0}'' não será permitido quando o recurso de processamento seguro for definido como verdadeiro."},

    { ER_NAMESPACE_CONTEXT_NULL_NAMESPACE,
      "Não é possível obter o prefixo de um uri de namespace nulo."},

    { ER_NAMESPACE_CONTEXT_NULL_PREFIX,
      "Não é possível obter o uri do namespace do prefixo nulo."},

    { ER_XPATH_RESOLVER_NULL_QNAME,
      "O nome da função não pode ser nulo."},

    { ER_XPATH_RESOLVER_NEGATIVE_ARITY,
      "A aridade não pode ser negativa."},
  // Warnings...

    { WG_FOUND_CURLYBRACE,
      "Encontrou '}', mas nenhum modelo do atributo estava aberto!"},

    { WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR,
      "Advertência: o atributo de contagem não corresponde a um ancestral no xsl:number! Alvo = {0}"},

    { WG_EXPR_ATTRIB_CHANGED_TO_SELECT,
      "Sintaxe antiga: O nome do atributo 'expr' foi alterado para 'select'."},

    { WG_NO_LOCALE_IN_FORMATNUMBER,
      "O Xalan ainda não trata o nome das configurações regionais na função format-number."},

    { WG_LOCALE_NOT_FOUND,
      "Advertência: Não foi possível encontrar o nome das configurações regionais de xml:lang={0}"},

    { WG_CANNOT_MAKE_URL_FROM,
      "Não é possível criar o URL de: {0}"},

    { WG_CANNOT_LOAD_REQUESTED_DOC,
      "Não é possível carregar o doc solicitado: {0}"},

    { WG_CANNOT_FIND_COLLATOR,
      "Não foi possível localizar o Agrupador para <sort xml:lang={0}"},

    { WG_FUNCTIONS_SHOULD_USE_URL,
      "Sintaxe antiga: a instrução das funções deve usar um url de {0}"},

    { WG_ENCODING_NOT_SUPPORTED_USING_UTF8,
      "codificação não suportada: {0}, usando UTF-8"},

    { WG_ENCODING_NOT_SUPPORTED_USING_JAVA,
      "codificação não suportada: {0}, usando Java {1}"},

    { WG_SPECIFICITY_CONFLICTS,
      "Conflitos de especificidade encontrados: {0} Será usado o último encontrado na folha de estilos."},

    { WG_PARSING_AND_PREPARING,
      "========= Fazendo parsing e preparando {0} =========="},

    { WG_ATTR_TEMPLATE,
     "Modelo do Atributo, {0}"},

    { WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESPACE,
      "Conflito correspondente entre xsl:strip-space e xsl:preserve-space"},

    { WG_ATTRIB_NOT_HANDLED,
      "O Xalan ainda não trata o atributo {0}!"},

    { WG_NO_DECIMALFORMAT_DECLARATION,
      "Nenhuma declaração encontrada para o formato decimal: {0}"},

    { WG_OLD_XSLT_NS,
     "Namespace de XSLT não encontrado ou incorreto. "},

    { WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED,
      "É permitida somente uma declaração de xsl:decimal-format padrão."},

    { WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE,
      "os nomes de xsl:decimal-format devem ser exclusivos. O nome \"{0}\" foi duplicado."},

    { WG_ILLEGAL_ATTRIBUTE,
      "{0} tem um atributo inválido: {1}"},

    { WG_COULD_NOT_RESOLVE_PREFIX,
      "Não foi possível resolver o prefixo do namespace: {0}. O nó será ignorado."},

    { WG_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet requer um atributo 'version'!"},

    { WG_ILLEGAL_ATTRIBUTE_NAME,
      "Nome do atributo inválido: {0}"},

    { WG_ILLEGAL_ATTRIBUTE_VALUE,
      "Valor inválido usado para o atributo {0}: {1}"},

    { WG_EMPTY_SECOND_ARG,
      "O conjunto de nós resultante do segundo argumento da função do documento está vazio. Retorne um conjunto de nós vazio."},

  //Following are the new WARNING keys added in XALAN code base after Jdk 1.4 (Xalan 2.2-D11)

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.
    { WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "O valor do atributo 'name' do nome de xsl:processing-instruction não deve ser 'xml'"},

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.  "NCName" is an XML data-type and must not be
    // translated.
    { WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "O valor do atributo ''name'' de xsl:processing-instruction deve ser um NCName válido: {0}"},

    // Note to translators:  This message is reported if the stylesheet that is
    // being processed attempted to construct an XML document with an attribute in a
    // place other than on an element.  The substitution text specifies the name of
    // the attribute.
    { WG_ILLEGAL_ATTRIBUTE_POSITION,
      "Não é possível adicionar o atributo {0} depois dos nós filhos ou antes que um elemento seja produzido. O atributo será ignorado."},

    { NO_MODIFICATION_ALLOWED_ERR,
      "Foi feita uma tentativa de modificar um objeto no qual não são permitidas modificações."
    },

    //Check: WHY THERE IS A GAP B/W NUMBERS in the XSLTErrorResources properties file?

  // Other miscellaneous text used inside the code...
  { "ui_language", "pt-BR"},
  {  "help_language",  "pt-BR" },
  {  "language",  "pt-BR" },
  { "BAD_CODE", "O parâmetro para createMessage estava fora dos limites"},
  {  "FORMAT_FAILED", "Exceção gerada durante a chamada messageFormat"},
  {  "version", ">>>>>>> Versão do Xalan "},
  {  "version2",  "<<<<<<<"},
  {  "yes", "sim"},
  { "line", "N° da Linha"},
  { "column","N° da Coluna"},
  { "xsldone", "XSLProcessor: concluído"},


  // Note to translators:  The following messages provide usage information
  // for the Xalan Process command line.  "Process" is the name of a Java class,
  // and should not be translated.
  { "xslProc_option", "Opções da classe Process da linha de comandos do Xalan-J:"},
  { "xslProc_option", "Opções da classe Process da linha de comandos do Xalan-J:"},
  { "xslProc_invalid_xsltc_option", "A opção {0} não é suportada no modo XSLTC."},
  { "xslProc_invalid_xalan_option", "A opção {0} só pode ser usada com -XSLTC."},
  { "xslProc_no_input", "Erro: Não foi especificada uma folha de estilos ou um xml de entrada . Execute este comando sem nenhuma opção para instruções de uso."},
  { "xslProc_common_options", "-Opções Comuns-"},
  { "xslProc_xalan_options", "-Opções para Xalan-"},
  { "xslProc_xsltc_options", "-Opções para XSLTC-"},
  { "xslProc_return_to_continue", "(pressione <return> para continuar)"},

   // Note to translators: The option name and the parameter name do not need to
   // be translated. Only translate the messages in parentheses.  Note also that
   // leading whitespace in the messages is used to indent the usage information
   // for each option in the English messages.
   // Do not translate the keywords: XSLTC, SAX, DOM and DTM.
  { "optionXSLTC", "   [-XSLTC (use XSLTC para transformação)]"},
  { "optionIN", "   [-IN inputXMLURL]"},
  { "optionXSL", "   [-XSL XSLTransformationURL]"},
  { "optionOUT",  "   [-OUT outputFileName]"},
  { "optionLXCIN", "   [-LXCIN compiledStylesheetFileNameIn]"},
  { "optionLXCOUT", "   [-LXCOUT compiledStylesheetFileNameOutOut]"},
  { "optionPARSER", "   [-PARSER nome da classe totalmente qualificado de liaison de parser]"},
  {  "optionE", "   [-E (Não expandir referências da entidade)]"},
  {  "optionV",  "   [-E (Não expandir referências da entidade)]"},
  {  "optionQC", "   [-QC (Advertências de Conflitos do Padrão Silencioso)]"},
  {  "optionQ", "   [-Q  (Modo Silencioso)]"},
  {  "optionLF", "   [-LF (Usar alimentações de linha somente na saída {o padrão é CR/LF})]"},
  {  "optionCR", "   [-CR (Use retornos de carro somente na saída {o padrão é CR/LF})]"},
  { "optionESCAPE", "   [-ESCAPE (Quais caracteres devem ser identificados como escape {o padrão é <>&\"'\\r\\n}]"},
  { "optionINDENT", "   [-INDENT (Controla quantos espaços devem ser recuados {o padrão é 0})]"},
  { "optionTT", "   [-TT (Rastreia os modelos à medida que são chamados.)]"},
  { "optionTG", "   [-TG (Rastreia cada evento de geração.)]"},
  { "optionTS", "   [-TS (Rastreia cada evento de seleção.)]"},
  {  "optionTTC", "   [-TTC (Rastreia os filhos do modelo à medida que são processados.)]"},
  { "optionTCLASS", "   [-TCLASS (Classe TraceListener para extensões de rastreamento.)]"},
  { "optionVALIDATE", "   [-VALIDATE (Define se ocorre validação. Por padrão, a validação fica desativada.)]"},
  { "optionEDUMP", "   [-EDUMP {nome do arquivo opcional} (Execute um dump de pilha em caso de erro.)]"},
  {  "optionXML", "   [-XML (Use o formatador XML e adicione o cabeçalho XML.)]"},
  {  "optionTEXT", "   [-TEXT (Use o formatador de Texto simples.)]"},
  {  "optionHTML", "   [-HTML (Use o formatador HTML.)]"},
  {  "optionPARAM", "   [-PARAM expressão do nome (Defina um parâmetro da folha de estilos)]"},
  {  "noParsermsg1", "Processo XSL malsucedido."},
  {  "noParsermsg2", "** Não foi possível localizar o parser **"},
  { "noParsermsg3",  "Verifique seu classpath."},
  { "noParsermsg4", "Se você não tiver um Parser XML da IBM para Java, poderá fazer download dele em"},
  { "noParsermsg5", "AlphaWorks da IBM: http://www.alphaworks.ibm.com/formula/xml"},
  { "optionURIRESOLVER", "   [-URIRESOLVER nome completo da classe (URIResolver a ser usado para resolver URIs)]"},
  { "optionENTITYRESOLVER",  "   [-ENTITYRESOLVER nome completo da classe (EntityResolver a ser usado para resolver entidades)]"},
  { "optionCONTENTHANDLER",  "   [-CONTENTHANDLER nome completo da classe (ContentHandler a ser usado para serializar a saída)]"},
  {  "optionLINENUMBERS",  "   [-L usa os números de linha dos documentos de origem]"},
  { "optionSECUREPROCESSING", "   [-SECURE (define o recurso de processamento seguro como verdadeiro.)]"},

    // Following are the new options added in XSLTErrorResources.properties files after Jdk 1.4 (Xalan 2.2-D11)


  {  "optionMEDIA",  "   [-MEDIA mediaType (use o atributo de mídia para localizar a folha de estilos associada a um documento.)]"},
  {  "optionFLAVOR",  "   [-FLAVOR flavorName (Use explicitamente s2s=SAX ou d2d=DOM para fazer a transformação.)] "}, // Added by sboag/scurcuru; experimental
  { "optionDIAG", "   [-DIAG (Imprimir transformação geral de milissegundos detectada.)]"},
  { "optionINCREMENTAL",  "   [-INCREMENTAL (solicitar a construção de DTM incremental, definindo http://xml.apache.org/xalan/features/incremental como verdadeiro.)]"},
  {  "optionNOOPTIMIMIZE",  "   [-NOOPTIMIMIZE (solicite o não processamento de otimização da folha de estilos definindo http://xml.apache.org/xalan/features/optimize como falso.)]"},
  { "optionRL",  "   [-RL recursionlimit (limite numérico de asserção na profundidade de recursão da folha de estilos.)]"},
  {   "optionXO",  "   [-XO [transletName] (atribui o nome ao translet gerado)]"},
  {  "optionXD", "   [-XD destinationDirectory (especificar um diretório de destino para translet)]"},
  {  "optionXJ",  "   [-XJ jarfile (empacotar classes do translet em um arquivo jar com o nome <jarfile>)]"},
  {   "optionXP",  "   [-XP pacote (especifica um prefixo de nome do pacote para todas as classes translet geradas)]"},

  //AddITIONAL  STRINGS that need L10n
  // Note to translators:  The following message describes usage of a particular
  // command-line option that is used to enable the "template inlining"
  // optimization.  The optimization involves making a copy of the code
  // generated for a template in another template that refers to it.
  { "optionXN",  "   [-XN (ativa a inserção do modelo)]" },
  { "optionXX",  "   [-XX (ativa a saída da mensagem de depuração adicional)]"},
  { "optionXT" , "   [-XT (usar o translet para transformar, se possível)]"},
  { "diagTiming"," --------- A transformação de {0} por meio de {1} levou {2} ms" },
  { "recursionTooDeep","Aninhamento do modelo muito profundo. aninhamento = {0}, modelo {1} {2}" },
  { "nameIs", "o nome é" },
  { "matchPatternIs", "o padrão de correspondência é" }

  };

  }
  // ================= INFRASTRUCTURE ======================

  /** String for use when a bad error code was encountered.    */
  public static final String BAD_CODE = "BAD_CODE";

  /** String for use when formatting of the error string failed.   */
  public static final String FORMAT_FAILED = "FORMAT_FAILED";

    }
