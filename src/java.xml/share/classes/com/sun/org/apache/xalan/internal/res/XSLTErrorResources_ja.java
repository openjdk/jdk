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
public class XSLTErrorResources_ja extends ListResourceBundle
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
      "エラー: 式内に'{'を持つことはできません"},

    { ER_ILLEGAL_ATTRIBUTE ,
     "{0}に不正な属性があります: {1}"},

  {ER_NULL_SOURCENODE_APPLYIMPORTS ,
      "sourceNodeはxsl:apply-imports内でnullです。"},

  {ER_CANNOT_ADD,
      "{0}を{1}に追加できません"},

    { ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES,
      "sourceNodeはhandleApplyTemplatesInstruction内でnullです。"},

    { ER_NO_NAME_ATTRIB,
     "{0}にはname属性が必要です。"},

    {ER_TEMPLATE_NOT_FOUND,
     "名前{0}のテンプレートが見つかりませんでした"},

    {ER_CANT_RESOLVE_NAME_AVT,
      "xsl:call-templateの名前AVTを解決できませんでした。"},

    {ER_REQUIRES_ATTRIB,
     "{0}は属性{1}が必要です"},

    { ER_MUST_HAVE_TEST_ATTRIB,
      "{0}は''test''属性を持つ必要があります。"},

    {ER_BAD_VAL_ON_LEVEL_ATTRIB,
      "level属性の値が不正です: {0}"},

    {ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "processing-instruction名は'xml'にできません"},

    { ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "processing-instruction名は有効なNCNameである必要があります: {0}"},

    { ER_NEED_MATCH_ATTRIB,
      "モードがある場合、{0}にはmatch属性が必要です。"},

    { ER_NEED_NAME_OR_MATCH_ATTRIB,
      "{0}にはnameまたはmatch属性が必要です。"},

    {ER_CANT_RESOLVE_NSPREFIX,
      "ネームスペースの接頭辞を解決できません: {0}"},

    { ER_ILLEGAL_VALUE,
     "xml:spaceの値が不正です: {0}"},

    { ER_NO_OWNERDOC,
      "子ノードに所有者ドキュメントがありません。"},

    { ER_ELEMTEMPLATEELEM_ERR,
     "ElemTemplateElementエラー: {0}"},

    { ER_NULL_CHILD,
     "nullの子を追加しようとしました。"},

    { ER_NEED_SELECT_ATTRIB,
     "{0}にはselect属性が必要です。"},

    { ER_NEED_TEST_ATTRIB ,
      "xsl:whenには'test'属性が必要です。"},

    { ER_NEED_NAME_ATTRIB,
      "xsl:with-paramには'name'属性が必要です。"},

    { ER_NO_CONTEXT_OWNERDOC,
      "コンテキストに所有者ドキュメントがありません。"},

    {ER_COULD_NOT_CREATE_XML_PROC_LIAISON,
      "XML TransformerFactory Liaisonを作成できませんでした: {0}"},

    {ER_PROCESS_NOT_SUCCESSFUL,
      "Xalan: プロセスは成功しませんでした。"},

    { ER_NOT_SUCCESSFUL,
     "Xalan: は成功しませんでした。"},

    { ER_ENCODING_NOT_SUPPORTED,
     "エンコーディング{0}はサポートされていません"},

    {ER_COULD_NOT_CREATE_TRACELISTENER,
      "TraceListenerを作成できませんでした: {0}"},

    {ER_KEY_REQUIRES_NAME_ATTRIB,
      "xsl:keyには'name'属性が必要です。"},

    { ER_KEY_REQUIRES_MATCH_ATTRIB,
      "xsl:keyには'match'属性が必要です。"},

    { ER_KEY_REQUIRES_USE_ATTRIB,
      "xsl:keyには'use'属性が必要です。"},

    { ER_REQUIRES_ELEMENTS_ATTRIB,
      "(StylesheetHandler) {0}には''elements''属性が必要です。"},

    { ER_MISSING_PREFIX_ATTRIB,
      "(StylesheetHandler) {0}属性''prefix''がありません"},

    { ER_BAD_STYLESHEET_URL,
     "スタイルシートURLが不正です: {0}"},

    { ER_FILE_NOT_FOUND,
     "スタイルシート・ファイルが見つかりませんでした: {0}"},

    { ER_IOEXCEPTION,
      "スタイルシート・ファイルに入出力例外があります: {0}"},

    { ER_NO_HREF_ATTRIB,
      "(StylesheetHandler) {0}のhref属性が見つかりませんでした"},

    { ER_STYLESHEET_INCLUDES_ITSELF,
      "(StylesheetHandler) {0}はそれ自体を直接的または間接的に含んでいます。"},

    { ER_PROCESSINCLUDE_ERROR,
      "StylesheetHandler.processIncludeエラー、{0}"},

    { ER_MISSING_LANG_ATTRIB,
      "(StylesheetHandler) {0}属性''lang''がありません"},

    { ER_MISSING_CONTAINER_ELEMENT_COMPONENT,
      "(StylesheetHandler) {0}要素の配置が不正です。コンテナ要素''component''がありません"},

    { ER_CAN_ONLY_OUTPUT_TO_ELEMENT,
      "Element、DocumentFragment、DocumentまたはPrintWriterにのみ出力できます。"},

    { ER_PROCESS_ERROR,
     "StylesheetRoot.processエラー"},

    { ER_UNIMPLNODE_ERROR,
     "UnImplNodeエラー: {0}"},

    { ER_NO_SELECT_EXPRESSION,
      "エラー。xpath選択式(-select)が見つかりませんでした。"},

    { ER_CANNOT_SERIALIZE_XSLPROCESSOR,
      "XSLProcessorをシリアライズできません。"},

    { ER_NO_INPUT_STYLESHEET,
      "スタイルシート入力が指定されませんでした。"},

    { ER_FAILED_PROCESS_STYLESHEET,
      "スタイルシートの処理に失敗しました。"},

    { ER_COULDNT_PARSE_DOC,
     "{0}ドキュメントを解析できませんでした。"},

    { ER_COULDNT_FIND_FRAGMENT,
     "フラグメントが見つかりませんでした: {0}"},

    { ER_NODE_NOT_ELEMENT,
      "フラグメント識別子によって指示されたノードは要素ではありませんでした: {0}"},

    { ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB,
      "for-eachはmatchまたはname属性を持つ必要があります"},

    { ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB,
      "テンプレートはmatchまたはname属性を持つ必要があります"},

    { ER_NO_CLONE_OF_DOCUMENT_FRAG,
      "ドキュメント・フラグメントのクローンを作成できません。"},

    { ER_CANT_CREATE_ITEM,
      "結果ツリーに項目を作成できません: {0}"},

    { ER_XMLSPACE_ILLEGAL_VALUE,
      "ソースXMLのxml:spaceの値が不正です: {0}"},

    { ER_NO_XSLKEY_DECLARATION,
      "{0}のxsl:key宣言がありません。"},

    { ER_CANT_CREATE_URL,
     "エラー。{0}のURLを作成できません"},

    { ER_XSLFUNCTIONS_UNSUPPORTED,
     "xsl:functionsはサポートされていません"},

    { ER_PROCESSOR_ERROR,
     "XSLT TransformerFactoryエラー"},

    { ER_NOT_ALLOWED_INSIDE_STYLESHEET,
      "(StylesheetHandler) {0}はスタイルシート内で許可されません。"},

    { ER_RESULTNS_NOT_SUPPORTED,
      "result-nsは現在はサポートされていません。かわりにxsl:outputを使用してください。"},

    { ER_DEFAULTSPACE_NOT_SUPPORTED,
      "default-spaceは現在はサポートされていません。かわりにxsl:strip-spaceまたはxsl:preserve-spaceを使用してください。"},

    { ER_INDENTRESULT_NOT_SUPPORTED,
      "indent-resultは現在はサポートされていません。かわりにxsl:outputを使用してください。"},

    { ER_ILLEGAL_ATTRIB,
      "(StylesheetHandler) {0}には不正な属性があります: {1}"},

    { ER_UNKNOWN_XSL_ELEM,
     "不明なXSL要素: {0}"},

    { ER_BAD_XSLSORT_USE,
      "(StylesheetHandler) xsl:sortは、xsl:apply-templatesまたはxsl:for-eachとともにのみ使用できます。"},

    { ER_MISPLACED_XSLWHEN,
      "(StylesheetHandler) xsl:whenの配置が不正です。"},

    { ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:whenの親がxsl:chooseではありません。"},

    { ER_MISPLACED_XSLOTHERWISE,
      "(StylesheetHandler) xsl:otherwiseの配置が不正です。"},

    { ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:otherwiseの親がxsl:chooseではありません。"},

    { ER_NOT_ALLOWED_INSIDE_TEMPLATE,
      "(StylesheetHandler) {0}はテンプレート内で許可されません。"},

    { ER_UNKNOWN_EXT_NS_PREFIX,
      "(StylesheetHandler) 不明な{0}拡張ネームスペースの接頭辞{1}です"},

    { ER_IMPORTS_AS_FIRST_ELEM,
      "(StylesheetHandler) インポートはスタイルシートの最初の要素としてのみ使用できます。"},

    { ER_IMPORTING_ITSELF,
      "(StylesheetHandler) {0}はそれ自体を直接または間接的にインポートしています。"},

    { ER_XMLSPACE_ILLEGAL_VAL,
      "(StylesheetHandler) xml:spaceの値が不正です: {0}"},

    { ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL,
      "processStylesheetは失敗しました。"},

    { ER_SAX_EXCEPTION,
     "SAX例外"},

//  add this message to fix bug 21478
    { ER_FUNCTION_NOT_SUPPORTED,
     "関数がサポートされていません。"},

    { ER_XSLT_ERROR,
     "XSLTエラー"},

    { ER_CURRENCY_SIGN_ILLEGAL,
      "通貨記号はフォーマット・パターン文字列内で許可されません"},

    { ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM,
      "ドキュメント関数はスタイルシートDOMでサポートされていません。"},

    { ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER,
      "非接頭辞リゾルバの接頭辞を解決できません。"},

    { ER_REDIRECT_COULDNT_GET_FILENAME,
      "リダイレクト拡張: ファイル名を取得できませんでした - fileまたはselect属性が有効な文字列を返す必要があります。"},

    { ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT,
      "リダイレクト拡張でFormatterListenerを作成できません。"},

    { ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX,
      "exclude-result-prefixesの接頭辞が無効です: {0}"},

    { ER_MISSING_NS_URI,
      "指定した接頭辞のネームスペースURIがありません"},

    { ER_MISSING_ARG_FOR_OPTION,
      "オプション{0}の引数がありません"},

    { ER_INVALID_OPTION,
     "無効なオプション: {0}"},

    { ER_MALFORMED_FORMAT_STRING,
     "不正なフォーマットの文字列です: {0}"},

    { ER_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheetは'version'属性が必要です。"},

    { ER_ILLEGAL_ATTRIBUTE_VALUE,
      "属性{0}の値が不正です: {1}"},

    { ER_CHOOSE_REQUIRES_WHEN,
     "xsl:chooseはxsl:whenが必要です"},

    { ER_NO_APPLY_IMPORT_IN_FOR_EACH,
      "xsl:apply-importsはxsl:for-each内で許可されません"},

    { ER_CANT_USE_DTM_FOR_OUTPUT,
      "出力DOMノードにDTMLiaisonを使用できません...かわりにcom.sun.org.apache.xpath.internal.DOM2Helperを渡してください。"},

    { ER_CANT_USE_DTM_FOR_INPUT,
      "入力DOMノードにDTMLiaisonを使用できません...かわりにcom.sun.org.apache.xpath.internal.DOM2Helperを渡してください。"},

    { ER_CALL_TO_EXT_FAILED,
      "拡張要素の呼出しが失敗しました: {0}"},

    { ER_PREFIX_MUST_RESOLVE,
      "接頭辞はネームスペースに解決される必要があります: {0}"},

    { ER_INVALID_UTF16_SURROGATE,
      "無効なUTF-16サロゲートが検出されました: {0}。"},

    { ER_XSLATTRSET_USED_ITSELF,
      "xsl:attribute-set {0}がそれ自体を使用し、無限ループが発生します。"},

    { ER_CANNOT_MIX_XERCESDOM,
      "非Xerces-DOM入力とXerces-DOM出力を同時に使用することはできません。"},

    { ER_TOO_MANY_LISTENERS,
      "addTraceListenersToStylesheet - TooManyListenersException"},

    { ER_IN_ELEMTEMPLATEELEM_READOBJECT,
      "ElemTemplateElement.readObject内: {0}"},

    { ER_DUPLICATE_NAMED_TEMPLATE,
      "名前{0}のテンプレートが複数見つかりました"},

    { ER_INVALID_KEY_CALL,
      "無効な関数呼出し: 再帰的なkey()の呼出しは許可されません"},

    { ER_REFERENCING_ITSELF,
      "変数{0}はそれ自体を直接または間接的に参照しています。"},

    { ER_ILLEGAL_DOMSOURCE_INPUT,
      "newTemplatesのDOMSourceについて入力ノードをnullにすることはできません。"},

    { ER_CLASS_NOT_FOUND_FOR_OPTION,
        "オプション{0}についてクラス・ファイルが見つかりません"},

    { ER_REQUIRED_ELEM_NOT_FOUND,
        "必須要素が見つかりません: {0}"},

    { ER_INPUT_CANNOT_BE_NULL,
        "InputStreamをnullにすることはできません"},

    { ER_URI_CANNOT_BE_NULL,
        "URIをnullにすることはできません"},

    { ER_FILE_CANNOT_BE_NULL,
        "ファイルをnullにすることはできません"},

    { ER_SOURCE_CANNOT_BE_NULL,
                "InputSourceをnullにすることはできません"},

    { ER_CANNOT_INIT_BSFMGR,
                "BSFマネージャを初期化できませんでした"},

    { ER_CANNOT_CMPL_EXTENSN,
                "拡張をコンパイルできませんでした"},

    { ER_CANNOT_CREATE_EXTENSN,
      "{1}が原因で拡張{0}を作成できませんでした"},

    { ER_INSTANCE_MTHD_CALL_REQUIRES,
      "メソッド{0}のインスタンス・メソッド呼出しでは、最初の引数としてオブジェクト・インスタンスが必要です"},

    { ER_INVALID_ELEMENT_NAME,
      "無効な要素名が指定されました{0}"},

    { ER_ELEMENT_NAME_METHOD_STATIC,
      "要素名メソッドはstatic {0}である必要があります"},

    { ER_EXTENSION_FUNC_UNKNOWN,
             "拡張関数{0} : {1}が不明です"},

    { ER_MORE_MATCH_CONSTRUCTOR,
             "{0}のコンストラクタに複数の最適一致があります"},

    { ER_MORE_MATCH_METHOD,
             "メソッド{0}に複数の最適一致があります"},

    { ER_MORE_MATCH_ELEMENT,
             "要素メソッド{0}に複数の最適一致があります"},

    { ER_INVALID_CONTEXT_PASSED,
             "{0}を評価するために無効なコンテキストが渡されました"},

    { ER_POOL_EXISTS,
             "プールはすでに存在します"},

    { ER_NO_DRIVER_NAME,
             "ドライバ名が指定されていません"},

    { ER_NO_URL,
             "URLが指定されていません"},

    { ER_POOL_SIZE_LESSTHAN_ONE,
             "プール・サイズが1より小さいです。"},

    { ER_INVALID_DRIVER,
             "無効なドライバ名が指定されました。"},

    { ER_NO_STYLESHEETROOT,
             "スタイルシート・ルートが見つかりませんでした。"},

    { ER_ILLEGAL_XMLSPACE_VALUE,
         "xml:spaceの値が不正です"},

    { ER_PROCESSFROMNODE_FAILED,
         "processFromNodeが失敗しました"},

    { ER_RESOURCE_COULD_NOT_LOAD,
        "リソース[ {0} ]をロードできませんでした: {1} \n {2} \t {3}"},

    { ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "バッファ・サイズ<=0"},

    { ER_UNKNOWN_ERROR_CALLING_EXTENSION,
        "拡張を呼び出すときに不明なエラーが発生しました"},

    { ER_NO_NAMESPACE_DECL,
        "接頭辞{0}には、対応するネームスペース宣言がありません"},

    { ER_ELEM_CONTENT_NOT_ALLOWED,
        "要素の内容はlang=javaclass {0}について許可されません"},

    { ER_STYLESHEET_DIRECTED_TERMINATION,
        "スタイルシートにより終了が指示されました"},

    { ER_ONE_OR_TWO,
        "1または2"},

    { ER_TWO_OR_THREE,
        "2または3"},

    { ER_COULD_NOT_LOAD_RESOURCE,
        "{0}をロードできませんでした(CLASSPATHを確認してください)。現在は単にデフォルトを使用しています"},

    { ER_CANNOT_INIT_DEFAULT_TEMPLATES,
        "デフォルト・テンプレートを初期化できません"},

    { ER_RESULT_NULL,
        "結果はnullにできません"},

    { ER_RESULT_COULD_NOT_BE_SET,
        "結果を設定できませんでした"},

    { ER_NO_OUTPUT_SPECIFIED,
        "出力が指定されていません"},

    { ER_CANNOT_TRANSFORM_TO_RESULT_TYPE,
        "タイプ{0}の結果に変換できません"},

    { ER_CANNOT_TRANSFORM_SOURCE_TYPE,
        "タイプ{0}のソースに変換できません"},

    { ER_NULL_CONTENT_HANDLER,
        "Nullのコンテンツ・ハンドラ"},

    { ER_NULL_ERROR_HANDLER,
        "Nullのエラー・ハンドラ"},

    { ER_CANNOT_CALL_PARSE,
        "ContentHandlerが設定されていない場合、解析を呼び出すことができません"},

    { ER_NO_PARENT_FOR_FILTER,
        "フィルタの親がありません"},

    { ER_NO_STYLESHEET_IN_MEDIA,
         "スタイルシートが{0}にありません。メディア= {1}"},

    { ER_NO_STYLESHEET_PI,
         "xml-stylesheet PIが{0}に見つかりません"},

    { ER_NOT_SUPPORTED,
       "サポートされていません: {0}"},

    { ER_PROPERTY_VALUE_BOOLEAN,
       "プロパティ{0}の値はBooleanインスタンスである必要があります"},

    { ER_COULD_NOT_FIND_EXTERN_SCRIPT,
         "{0}の外部スクリプトに到達できませんでした"},

    { ER_RESOURCE_COULD_NOT_FIND,
        "リソース[ {0} ]は見つかりませんでした。\n {1}"},

    { ER_OUTPUT_PROPERTY_NOT_RECOGNIZED,
        "出力プロパティが認識されません: {0}"},

    { ER_FAILED_CREATING_ELEMLITRSLT,
        "ElemLiteralResultインスタンスの作成に失敗しました"},

  //Earlier (JDK 1.4 XALAN 2.2-D11) at key code '204' the key name was ER_PRIORITY_NOT_PARSABLE
  // In latest Xalan code base key name is  ER_VALUE_SHOULD_BE_NUMBER. This should also be taken care
  //in locale specific files like XSLTErrorResources_de.java, XSLTErrorResources_fr.java etc.
  //NOTE: Not only the key name but message has also been changed.
    { ER_VALUE_SHOULD_BE_NUMBER,
        "{0}の値には解析可能な数値が含まれる必要があります"},

    { ER_VALUE_SHOULD_EQUAL,
        "{0}の値はyesまたはnoに等しい必要があります"},

    { ER_FAILED_CALLING_METHOD,
        "{0}メソッドの呼出しに失敗しました"},

    { ER_FAILED_CREATING_ELEMTMPL,
        "ElemTemplateElementインスタンスの作成に失敗しました"},

    { ER_CHARS_NOT_ALLOWED,
        "文字はドキュメントのこのポイントでは許可されません"},

    { ER_ATTR_NOT_ALLOWED,
        "\"{0}\"属性は{1}要素では許可されません。"},

    { ER_BAD_VALUE,
     "{0}の不正な値{1} "},

    { ER_ATTRIB_VALUE_NOT_FOUND,
     "{0}属性値が見つかりません "},

    { ER_ATTRIB_VALUE_NOT_RECOGNIZED,
     "{0}属性値が認識されません "},

    { ER_NULL_URI_NAMESPACE,
     "nullのURIを持つネームスペースの接頭辞を生成しようとしました"},

    { ER_NUMBER_TOO_BIG,
     "最大のLong整数よりも大きい数値をフォーマットしようとしました"},

    { ER_CANNOT_FIND_SAX1_DRIVER,
     "SAX1ドライバ・クラス{0}が見つかりません"},

    { ER_SAX1_DRIVER_NOT_LOADED,
     "SAX1ドライバ・クラス{0}が見つかりましたがロードできません"},

    { ER_SAX1_DRIVER_NOT_INSTANTIATED,
     "SAX1ドライバ・クラス{0}がロードされましたがインスタンス化できません"},

    { ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER,
     "SAX1ドライバ・クラス{0}はorg.xml.sax.Parserを実装できません"},

    { ER_PARSER_PROPERTY_NOT_SPECIFIED,
     "システム・プロパティorg.xml.sax.parserが指定されていません"},

    { ER_PARSER_ARG_CANNOT_BE_NULL,
     "パーサー引数はnullでない必要があります"},

    { ER_FEATURE,
     "機能: {0}"},

    { ER_PROPERTY,
     "プロパティ: {0}"},

    { ER_NULL_ENTITY_RESOLVER,
     "Nullエンティティ・リゾルバ"},

    { ER_NULL_DTD_HANDLER,
     "Null DTDハンドラ"},

    { ER_NO_DRIVER_NAME_SPECIFIED,
     "ドライバ名が指定されていません。"},

    { ER_NO_URL_SPECIFIED,
     "URLが指定されていません。"},

    { ER_POOLSIZE_LESS_THAN_ONE,
     "プール・サイズが1より小さいです。"},

    { ER_INVALID_DRIVER_NAME,
     "無効なドライバ名が指定されました。"},

    { ER_ERRORLISTENER,
     "ErrorListener"},


// Note to translators:  The following message should not normally be displayed
//   to users.  It describes a situation in which the processor has detected
//   an internal consistency problem in itself, and it provides this message
//   for the developer to help diagnose the problem.  The name
//   'ElemTemplateElement' is the name of a class, and should not be
//   translated.
    { ER_ASSERT_NO_TEMPLATE_PARENT,
     "プログラマのエラー。式にElemTemplateElementの親がありません。"},


// Note to translators:  The following message should not normally be displayed
//   to users.  It describes a situation in which the processor has detected
//   an internal consistency problem in itself, and it provides this message
//   for the developer to help diagnose the problem.  The substitution text
//   provides further information in order to diagnose the problem.  The name
//   'RedundentExprEliminator' is the name of a class, and should not be
//   translated.
    { ER_ASSERT_REDUNDENT_EXPR_ELIMINATOR,
     "RedundentExprEliminatorでのプログラマのアサーション: {0}"},

    { ER_NOT_ALLOWED_IN_POSITION,
     "{0}はスタイルシートのこの位置では許可されません。"},

    { ER_NONWHITESPACE_NOT_ALLOWED_IN_POSITION,
     "空白以外のテキストはスタイルシートのこの位置では許可されません。"},

  // This code is shared with warning codes.
  // SystemId Unknown
    { INVALID_TCHAR,
     "不正な値: {1}がCHAR属性{0}に使用されました。CHAR型の属性は1文字のみである必要があります。"},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "QNAME" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value and {0} is the attribute name.
  //The following codes are shared with the warning codes...
    { INVALID_QNAME,
     "不正な値: {1}がQNAME属性{0}に使用されました"},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "ENUM" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value, {0} is the attribute name, and {2} is a list of valid
    // values.
    { INVALID_ENUM,
     "不正な値: {1}がENUM属性{0}に使用されました。有効な値は{2}です。"},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NMTOKEN" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_NMTOKEN,
     "不正な値: {1}がNMTOKEN属性{0}に使用されました "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NCNAME" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_NCNAME,
     "不正な値: {1}がNCNAME属性{0}に使用されました "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "boolean" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_BOOLEAN,
     "不正な値: {1}がboolean属性{0}に使用されました "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "number" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
     { INVALID_NUMBER,
     "不正な値: {1}が数値属性{0}に使用されました "},


  // End of shared codes...

// Note to translators:  A "match pattern" is a special form of XPath expression
// that is used for matching patterns.  The substitution text is the name of
// a function.  The message indicates that when this function is referenced in
// a match pattern, its argument must be a string literal (or constant.)
// ER_ARG_LITERAL - new error message for bugzilla //5202
    { ER_ARG_LITERAL,
     "一致パターンにおける{0}の引数はリテラルである必要があります。"},

// Note to translators:  The following message indicates that two definitions of
// a variable.  A "global variable" is a variable that is accessible everywher
// in the stylesheet.
// ER_DUPLICATE_GLOBAL_VAR - new error message for bugzilla #790
    { ER_DUPLICATE_GLOBAL_VAR,
     "グローバル変数宣言が重複しています。"},


// Note to translators:  The following message indicates that two definitions of
// a variable were encountered.
// ER_DUPLICATE_VAR - new error message for bugzilla #790
    { ER_DUPLICATE_VAR,
     "変数宣言が重複しています。"},

    // Note to translators:  "xsl:template, "name" and "match" are XSLT keywords
    // which must not be translated.
    // ER_TEMPLATE_NAME_MATCH - new error message for bugzilla #789
    { ER_TEMPLATE_NAME_MATCH,
     "xsl:templateにはname属性またはmatch属性(あるいは両方)が必要です"},

    // Note to translators:  "exclude-result-prefixes" is an XSLT keyword which
    // should not be translated.  The message indicates that a namespace prefix
    // encountered as part of the value of the exclude-result-prefixes attribute
    // was in error.
    // ER_INVALID_PREFIX - new error message for bugzilla #788
    { ER_INVALID_PREFIX,
     "exclude-result-prefixesの接頭辞が無効です: {0}"},

    // Note to translators:  An "attribute set" is a set of attributes that can
    // be added to an element in the output document as a group.  The message
    // indicates that there was a reference to an attribute set named {0} that
    // was never defined.
    // ER_NO_ATTRIB_SET - new error message for bugzilla #782
    { ER_NO_ATTRIB_SET,
     "{0}という名前のattribute-setは存在しません"},

    // Note to translators:  This message indicates that there was a reference
    // to a function named {0} for which no function definition could be found.
    { ER_FUNCTION_NOT_FOUND,
     "{0}という名前の機能は存在しません"},

    // Note to translators:  This message indicates that the XSLT instruction
    // that is named by the substitution text {0} must not contain other XSLT
    // instructions (content) or a "select" attribute.  The word "select" is
    // an XSLT keyword in this case and must not be translated.
    { ER_CANT_HAVE_CONTENT_AND_SELECT,
     "{0}要素にはコンテンツとselect属性の両方を含めないでください。"},

    // Note to translators:  This message indicates that the value argument
    // of setParameter must be a valid Java Object.
    { ER_INVALID_SET_PARAM_VALUE,
     "パラメータ{0}は有効なJavaオブジェクトである必要があります"},

    { ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX_FOR_DEFAULT,
      "xsl:namespace-alias要素のresult-prefix属性に値'#default'がありますが、要素のスコープ内にデフォルトのネームスペースの宣言がありません"},

    { ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX,
      "xsl:namespace-alias要素のresult-prefix属性に値''{0}''がありますが、要素のスコープ内に接頭辞''{0}''のネームスペース宣言がありません。"},

    { ER_SET_FEATURE_NULL_NAME,
      "機能名はTransformerFactory.setFeature(String name, boolean value)内でnullにできません。"},

    { ER_GET_FEATURE_NULL_NAME,
      "機能名はTransformerFactory.getFeature(String name)内でnullにできません。"},

    { ER_UNSUPPORTED_FEATURE,
      "機能''{0}''をこのTransformerFactoryに設定できません。"},

    { ER_EXTENSION_ELEMENT_NOT_ALLOWED_IN_SECURE_PROCESSING,
        "セキュア処理機能またはプロパティ''jdk.xml.enableExtensionFunctions''によって拡張関数が無効になっているとき、拡張関数''{0}''の使用は許可されません。拡張関数を有効にするには、''jdk.xml.enableExtensionFunctions''を''true''に設定します。"},

    { ER_NAMESPACE_CONTEXT_NULL_NAMESPACE,
      "nullのネームスペースURIについて接頭辞を取得できません。"},

    { ER_NAMESPACE_CONTEXT_NULL_PREFIX,
      "nullの接頭辞についてネームスペースURIを取得できません。"},

    { ER_XPATH_RESOLVER_NULL_QNAME,
      "機能名をnullにすることはできません。"},

    { ER_XPATH_RESOLVER_NEGATIVE_ARITY,
      "arityを負にすることはできません。"},
  // Warnings...

    { WG_FOUND_CURLYBRACE,
      "'}'が見つかりましたが属性テンプレートが開いていません。"},

    { WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR,
      "警告: count属性がxsl:number内の祖先と一致しません。ターゲット= {0}"},

    { WG_EXPR_ATTRIB_CHANGED_TO_SELECT,
      "古い構文: 'expr'属性の名前が'select'に変更されました。"},

    { WG_NO_LOCALE_IN_FORMATNUMBER,
      "Xalanはformat-number関数内のロケール名をまだ処理できません。"},

    { WG_LOCALE_NOT_FOUND,
      "警告: xml:lang={0}のロケールが見つかりませんでした"},

    { WG_CANNOT_MAKE_URL_FROM,
      "{0}からURLを作成できません"},

    { WG_CANNOT_LOAD_REQUESTED_DOC,
      "リクエストされたドキュメント{0}をロードできません"},

    { WG_CANNOT_FIND_COLLATOR,
      "<sort xml:lang={0}のコレータが見つかりませんでした"},

    { WG_FUNCTIONS_SHOULD_USE_URL,
      "古い構文: 関数命令は{0}のURLを使用する必要があります"},

    { WG_ENCODING_NOT_SUPPORTED_USING_UTF8,
      "エンコーディング{0}はサポートされていません。UTF-8を使用します"},

    { WG_ENCODING_NOT_SUPPORTED_USING_JAVA,
      "エンコーディング{0}はサポートされていません。Java {1}を使用します"},

    { WG_SPECIFICITY_CONFLICTS,
      "特異性の競合が見つかりました: {0}。スタイルシート内で最後に見つかったものが使用されます。"},

    { WG_PARSING_AND_PREPARING,
      "========= {0}の解析および準備中 =========="},

    { WG_ATTR_TEMPLATE,
     "属性テンプレート、{0}"},

    { WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESPACE,
      "xsl:strip-spaceとxsl:preserve-spaceの間で一致が競合しています"},

    { WG_ATTRIB_NOT_HANDLED,
      "Xalanは{0}属性をまだ処理しません。"},

    { WG_NO_DECIMALFORMAT_DECLARATION,
      "10進数フォーマット{0}の宣言が見つかりません"},

    { WG_OLD_XSLT_NS,
     "XSLTのネームスペースがないか不正です。 "},

    { WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED,
      "デフォルトのxsl:decimal-format宣言は1つのみ許可されます。"},

    { WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE,
      "xsl:decimal-format名は固有である必要があります。名前\"{0}\"は重複しています。"},

    { WG_ILLEGAL_ATTRIBUTE,
      "{0}に不正な属性があります: {1}"},

    { WG_COULD_NOT_RESOLVE_PREFIX,
      "ネームスペースの接頭辞{0}を解決できませんでした。ノードは無視されます。"},

    { WG_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheetは'version'属性が必要です。"},

    { WG_ILLEGAL_ATTRIBUTE_NAME,
      "不正な属性名: {0}"},

    { WG_ILLEGAL_ATTRIBUTE_VALUE,
      "無効な値が属性{0}に使用されました: {1}"},

    { WG_EMPTY_SECOND_ARG,
      "ドキュメント関数の2番目の引数からの結果ノードセットが空です。空のノードセットを返します。"},

  //Following are the new WARNING keys added in XALAN code base after Jdk 1.4 (Xalan 2.2-D11)

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.
    { WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "xsl:processing-instruction名の'name'属性の値は'xml'でない必要があります"},

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.  "NCName" is an XML data-type and must not be
    // translated.
    { WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "xsl:processing-instructionの''name''属性の値は有効なNCNameである必要があります: {0}"},

    // Note to translators:  This message is reported if the stylesheet that is
    // being processed attempted to construct an XML document with an attribute in a
    // place other than on an element.  The substitution text specifies the name of
    // the attribute.
    { WG_ILLEGAL_ATTRIBUTE_POSITION,
      "子ノードの後または要素が生成される前に属性{0}を追加できません。属性は無視されます。"},

    { NO_MODIFICATION_ALLOWED_ERR,
      "変更が許可されていないオブジェクトを変更しようとしました。"
    },

    //Check: WHY THERE IS A GAP B/W NUMBERS in the XSLTErrorResources properties file?

  // Other miscellaneous text used inside the code...
  { "ui_language", "ja"},
  {  "help_language",  "ja" },
  {  "language",  "ja" },
  { "BAD_CODE", "createMessageのパラメータが範囲外です"},
  {  "FORMAT_FAILED", "messageFormatの呼出し中に例外がスローされました"},
  {  "version", ">>>>>>> Xalanバージョン "},
  {  "version2",  "<<<<<<<"},
  {  "yes", "yes"},
  { "line", "行番号"},
  { "column","列番号"},
  { "xsldone", "XSLProcessor: 完了しました"},


  // Note to translators:  The following messages provide usage information
  // for the Xalan Process command line.  "Process" is the name of a Java class,
  // and should not be translated.
  { "xslProc_option", "Xalan-Jコマンド行プロセス・クラスのオプション:"},
  { "xslProc_invalid_xsltc_option", "オプション{0}はXSLTCモードでサポートされていません。"},
  { "xslProc_invalid_xalan_option", "オプション{0}は-XSLTCとともにのみ使用できます。"},
  { "xslProc_no_input", "エラー: スタイルシートまたは入力xmlが指定されていません。使用方法の指示についてはオプションを付けずにこのコマンドを実行してください。"},
  { "xslProc_common_options", "-共通オプション-"},
  { "xslProc_xalan_options", "-Xalan用オプション-"},
  { "xslProc_xsltc_options", "-XSLTC用オプション-"},
  { "xslProc_return_to_continue", "(続行するには<return>を押してください)"},

   // Note to translators: The option name and the parameter name do not need to
   // be translated. Only translate the messages in parentheses.  Note also that
   // leading whitespace in the messages is used to indent the usage information
   // for each option in the English messages.
   // Do not translate the keywords: XSLTC, SAX, DOM and DTM.
  { "optionXSLTC", "   [-XSLTC (変換にXSLTCを使用)]"},
  { "optionIN", "   [-IN inputXMLURL]"},
  { "optionXSL", "   [-XSL XSLTransformationURL]"},
  { "optionOUT",  "   [-OUT outputFileName]"},
  { "optionLXCIN", "   [-LXCIN compiledStylesheetFileNameIn]"},
  { "optionLXCOUT", "   [-LXCOUT compiledStylesheetFileNameOutOut]"},
  { "optionPARSER", "   [-PARSER パーサー・リエゾンの完全修飾クラス名]"},
  {  "optionE", "   [-E (実体参照を拡張しない)]"},
  {  "optionV",  "   [-E (実体参照を拡張しない)]"},
  {  "optionQC", "   [-QC (抑制パターン競合の警告)]"},
  {  "optionQ", "   [-Q  (抑制モード)]"},
  {  "optionLF", "   [-LF (出力でのみ改行を使用{デフォルトはCR/LF})]"},
  {  "optionCR", "   [-CR (出力でのみ改行を使用{デフォルトはCR/LF})]"},
  { "optionESCAPE", "   [-ESCAPE (エスケープする文字{デフォルトは<>&\"'\\r\\n}]"},
  { "optionINDENT", "   [-INDENT (インデントする空白文字数を制御{デフォルトは0})]"},
  { "optionTT", "   [-TT (テンプレートが呼び出されたときにトレースする。)]"},
  { "optionTG", "   [-TG (各生成イベントをトレースする。)]"},
  { "optionTS", "   [-TS (各選択イベントをトレースする。)]"},
  {  "optionTTC", "   [-TTC (テンプレートの子が処理されるときにトレースする。)]"},
  { "optionTCLASS", "   [-TCLASS (トレース拡張用のTraceListenerクラス。)]"},
  { "optionVALIDATE", "   [-VALIDATE (検証を実行するかどうかを設定する。検証はデフォルトではオフ。)]"},
  { "optionEDUMP", "   [-EDUMP {optional filename} (エラー時にstackdumpを実行する。)]"},
  {  "optionXML", "   [-XML (XMLフォーマッタを使用してXMLヘッダーを追加する。)]"},
  {  "optionTEXT", "   [-TEXT (シンプル・テキスト・フォーマッタを使用する。)]"},
  {  "optionHTML", "   [-HTML (HTMLフォーマッタを使用する。)]"},
  {  "optionPARAM", "   [-PARAM name expression (スタイルシート・パラメータを設定する)]"},
  {  "noParsermsg1", "XSLプロセスは成功しませんでした。"},
  {  "noParsermsg2", "** パーサーが見つかりませんでした **"},
  { "noParsermsg3",  "クラスパスを確認してください。"},
  { "noParsermsg4", "IBMのJava用XMLパーサーがない場合、次のサイトからダウンロードできます"},
  { "noParsermsg5", "IBMのAlphaWorks: http://www.alphaworks.ibm.com/formula/xml"},
  { "optionURIRESOLVER", "   [-URIRESOLVER full class name (URIの解決に使用されるURIResolver)]"},
  { "optionENTITYRESOLVER",  "   [-ENTITYRESOLVER full class name (エンティティの解決に使用されるEntityResolver)]"},
  { "optionCONTENTHANDLER",  "   [-CONTENTHANDLER full class name (出力のシリアライズに使用されるContentHandler)]"},
  {  "optionLINENUMBERS",  "   [-L ソース・ドキュメントの行番号を使用]"},
  { "optionSECUREPROCESSING", "   [-SECURE (セキュア処理機能をtrueに設定する。)]"},

    // Following are the new options added in XSLTErrorResources.properties files after Jdk 1.4 (Xalan 2.2-D11)


  {  "optionMEDIA",  "   [-MEDIA mediaType (ドキュメントに関連付けられたスタイルシートを見つけるためにメディア属性を使用する。)]"},
  {  "optionFLAVOR",  "   [-FLAVOR flavorName (変換を行うためにs2s=SAXまたはd2d=DOMを明示的に使用する。)] "}, // Added by sboag/scurcuru; experimental
  { "optionDIAG", "   [-DIAG (変換にかかった合計ミリ秒数を出力する。)]"},
  { "optionINCREMENTAL",  "   [-INCREMENTAL (http://xml.apache.org/xalan/features/incrementalをtrueに設定することによって増分DTM構築をリクエストする。)]"},
  {  "optionNOOPTIMIMIZE",  "   [-NOOPTIMIMIZE (http://xml.apache.org/xalan/features/optimizeをfalseに設定することによってスタイルシート最適化処理をリクエストしない。)]"},
  { "optionRL",  "   [-RL recursionlimit (スタイルシートの再帰の深さについて数値上の制限をアサートする。)]"},
  {   "optionXO",  "   [-XO [transletName] (生成済transletに名前を割り当てる)]"},
  {  "optionXD", "   [-XD destinationDirectory (transletの宛先ディレクトリを指定する)]"},
  {  "optionXJ",  "   [-XJ jarfile (transletクラスを名前<jarfile>のjarファイルにパッケージする)]"},
  {   "optionXP",  "   [-XP package (すべての生成済transletクラス用にパッケージ名接頭辞を指定する)]"},

  //AddITIONAL  STRINGS that need L10n
  // Note to translators:  The following message describes usage of a particular
  // command-line option that is used to enable the "template inlining"
  // optimization.  The optimization involves making a copy of the code
  // generated for a template in another template that refers to it.
  { "optionXN",  "   [-XN (テンプレートのインライン化を有効にする)]" },
  { "optionXX",  "   [-XX (追加のデバッグ・メッセージ出力をオンにする)]"},
  { "optionXT" , "   [-XT (可能な場合は変換のためにtransletを使用する)]"},
  { "diagTiming"," --------- {1}による{0}の変換に{2}ミリ秒かかりました" },
  { "recursionTooDeep","テンプレートのネストが深すぎます。ネスト= {0}、テンプレート{1} {2}" },
  { "nameIs", "名前:" },
  { "matchPatternIs", "一致パターン:" }

  };

  }
  // ================= INFRASTRUCTURE ======================

  /** String for use when a bad error code was encountered.    */
  public static final String BAD_CODE = "BAD_CODE";

  /** String for use when formatting of the error string failed.   */
  public static final String FORMAT_FAILED = "FORMAT_FAILED";

    }
