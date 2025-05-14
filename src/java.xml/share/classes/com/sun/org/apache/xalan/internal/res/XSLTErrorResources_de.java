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
public class XSLTErrorResources_de extends ListResourceBundle
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
      "Fehler: \"{\" darf nicht im Ausdruck enthalten sein"},

    { ER_ILLEGAL_ATTRIBUTE ,
     "{0} hat ein unzulässiges Attribut: {1}"},

  {ER_NULL_SOURCENODE_APPLYIMPORTS ,
      "sourceNode ist null in xsl:apply-imports."},

  {ER_CANNOT_ADD,
      "{0} kann nicht zu {1} hinzugefügt werden"},

    { ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES,
      "sourceNode ist null in handleApplyTemplatesInstruction."},

    { ER_NO_NAME_ATTRIB,
     "{0} muss über ein \"name\"-Attribut verfügen."},

    {ER_TEMPLATE_NOT_FOUND,
     "Vorlage mit Namen {0} konnte nicht gefunden werden"},

    {ER_CANT_RESOLVE_NAME_AVT,
      "Namens-AVT in xsl:call-template konnte nicht aufgelöst werden."},

    {ER_REQUIRES_ATTRIB,
     "{0} erfordert Attribut: {1}"},

    { ER_MUST_HAVE_TEST_ATTRIB,
      "{0} muss über ein \"test\"-Attribut verfügen."},

    {ER_BAD_VAL_ON_LEVEL_ATTRIB,
      "Ungültiger Wert bei Ebenenattribut: {0}"},

    {ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "processing-instruction-Name darf nicht \"xml\" lauten"},

    { ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "processing-instruction-Name muss ein gültiger NCName sein: {0}"},

    { ER_NEED_MATCH_ATTRIB,
      "{0} muss über ein \"match\"-Attribut verfügen, wenn ein Modus vorhanden ist."},

    { ER_NEED_NAME_OR_MATCH_ATTRIB,
      "{0} erfordert entweder ein \"name\"- oder ein \"match\"-Attribut."},

    {ER_CANT_RESOLVE_NSPREFIX,
      "Namespace-Präfix kann nicht aufgelöst werden: {0}"},

    { ER_ILLEGAL_VALUE,
     "xml:space hat einen unzulässigen Wert: {0}"},

    { ER_NO_OWNERDOC,
      "Der untergeordnete Knoten hat kein Eigentümerdokument."},

    { ER_ELEMTEMPLATEELEM_ERR,
     "ElemTemplateElement-Fehler: {0}"},

    { ER_NULL_CHILD,
     "Es wird versucht, ein leeres untergeordnetes Element hinzuzufügen."},

    { ER_NEED_SELECT_ATTRIB,
     "{0} erfordert ein \"select\"-Attribut."},

    { ER_NEED_TEST_ATTRIB ,
      "xsl:when muss über ein \"test\"-Attribut verfügen."},

    { ER_NEED_NAME_ATTRIB,
      "xsl:with-param muss über ein \"name\"-Attribut verfügen."},

    { ER_NO_CONTEXT_OWNERDOC,
      "Kontext hat kein Eigentümerdokument."},

    {ER_COULD_NOT_CREATE_XML_PROC_LIAISON,
      "XML-TransformerFactory-Liaison konnte nicht erstellt werden: {0}"},

    {ER_PROCESS_NOT_SUCCESSFUL,
      "Xalan: Prozess war nicht erfolgreich."},

    { ER_NOT_SUCCESSFUL,
     "Xalan: War nicht erfolgreich."},

    { ER_ENCODING_NOT_SUPPORTED,
     "Codierung nicht unterstützt: {0}"},

    {ER_COULD_NOT_CREATE_TRACELISTENER,
      "TraceListener konnte nicht erstellt werden: {0}"},

    {ER_KEY_REQUIRES_NAME_ATTRIB,
      "xsl:key erfordert ein \"name\"-Attribut."},

    { ER_KEY_REQUIRES_MATCH_ATTRIB,
      "xsl:key erfordert ein \"match\"-Attribut."},

    { ER_KEY_REQUIRES_USE_ATTRIB,
      "xsl:key erfordert ein \"use\"-Attribut."},

    { ER_REQUIRES_ELEMENTS_ATTRIB,
      "(StylesheetHandler) {0} erfordert ein \"elements\"-Attribut."},

    { ER_MISSING_PREFIX_ATTRIB,
      "(StylesheetHandler) {0} Attribut \"prefix\" fehlt"},

    { ER_BAD_STYLESHEET_URL,
     "Stylesheet-URL ist ungültig: {0}"},

    { ER_FILE_NOT_FOUND,
     "Stylesheet-Datei wurde nicht gefunden: {0}"},

    { ER_IOEXCEPTION,
      "IO-Ausnahme bei Stylesheet-Datei: {0}"},

    { ER_NO_HREF_ATTRIB,
      "(StylesheetHandler) \"href\"-Attribut für {0} konnte nicht gefunden werden"},

    { ER_STYLESHEET_INCLUDES_ITSELF,
      "(StylesheetHandler) {0} schließt sich direkt oder indirekt selbst mit ein."},

    { ER_PROCESSINCLUDE_ERROR,
      "StylesheetHandler.processInclude-Fehler, {0}"},

    { ER_MISSING_LANG_ATTRIB,
      "(StylesheetHandler) {0}: Das Attribut \"lang\" fehlt"},

    { ER_MISSING_CONTAINER_ELEMENT_COMPONENT,
      "(StylesheetHandler) Element {0} an falscher Position?? Fehlendes Containerelement ''component''"},

    { ER_CAN_ONLY_OUTPUT_TO_ELEMENT,
      "Ausgabe kann nur an ein Element, DocumentFragment, Dokument oder PrintWriter erfolgen."},

    { ER_PROCESS_ERROR,
     "StylesheetRoot.process-Fehler"},

    { ER_UNIMPLNODE_ERROR,
     "UnImplNode-Fehler: {0}"},

    { ER_NO_SELECT_EXPRESSION,
      "Fehler. xpath-Auswahlausdruck (-select) nicht gefunden."},

    { ER_CANNOT_SERIALIZE_XSLPROCESSOR,
      "XSLProcessor kann nicht serialisiert werden."},

    { ER_NO_INPUT_STYLESHEET,
      "Stylesheet-Eingabe wurde nicht angegeben."},

    { ER_FAILED_PROCESS_STYLESHEET,
      "Verarbeitung des Stylesheet nicht erfolgreich."},

    { ER_COULDNT_PARSE_DOC,
     "{0}-Dokument konnte nicht geparst werden."},

    { ER_COULDNT_FIND_FRAGMENT,
     "Fragment konnte nicht gefunden werden: {0}"},

    { ER_NODE_NOT_ELEMENT,
      "Fragment-ID verwies auf einen Knoten, der kein Element war: {0}"},

    { ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB,
      "for-each muss entweder ein \"match\"- oder ein \"name\"-Attribut haben"},

    { ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB,
      "Vorlagen müssen entweder ein \"match\"- oder ein \"name\"-Attribut haben"},

    { ER_NO_CLONE_OF_DOCUMENT_FRAG,
      "Kein Clone eines Dokumentfragments."},

    { ER_CANT_CREATE_ITEM,
      "Element in Ergebnisbaum kann nicht erstellt werden: {0}"},

    { ER_XMLSPACE_ILLEGAL_VALUE,
      "xml:space in Quell-XML hat einen unzulässigen Wert: {0}"},

    { ER_NO_XSLKEY_DECLARATION,
      "Keine xsl:key-Deklaration für {0} vorhanden."},

    { ER_CANT_CREATE_URL,
     "Fehler. URL für {0} kann nicht erstellt werden"},

    { ER_XSLFUNCTIONS_UNSUPPORTED,
     "xsl:functions nicht unterstützt"},

    { ER_PROCESSOR_ERROR,
     "XSLT-TransformerFactory-Fehler"},

    { ER_NOT_ALLOWED_INSIDE_STYLESHEET,
      "(StylesheetHandler) {0} nicht zulässig in einem Stylesheet."},

    { ER_RESULTNS_NOT_SUPPORTED,
      "result-ns wird nicht mehr unterstützt. Verwenden Sie stattdessen xsl:output."},

    { ER_DEFAULTSPACE_NOT_SUPPORTED,
      "default-space wird nicht mehr unterstützt. Verwenden Sie stattdessen xsl:strip-space oder xsl:preserve-space."},

    { ER_INDENTRESULT_NOT_SUPPORTED,
      "indent-result wird nicht mehr unterstützt. Verwenden Sie stattdessen xsl:output."},

    { ER_ILLEGAL_ATTRIB,
      "(StylesheetHandler) {0} hat ein unzulässiges Attribut: {1}"},

    { ER_UNKNOWN_XSL_ELEM,
     "Unbekanntes XSL-Element: {0}"},

    { ER_BAD_XSLSORT_USE,
      "(StylesheetHandler) xsl:sort kann nur mit xsl:apply-templates oder xsl:for-each verwendet werden."},

    { ER_MISPLACED_XSLWHEN,
      "(StylesheetHandler) xsl:when steht an der falschen Position."},

    { ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:when hat nicht das übergeordnete Element xsl:choose."},

    { ER_MISPLACED_XSLOTHERWISE,
      "(StylesheetHandler) xsl:otherwise steht an der falschen Position."},

    { ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:otherwise hat nicht das übergeordnete Element xsl:choose."},

    { ER_NOT_ALLOWED_INSIDE_TEMPLATE,
      "(StylesheetHandler) {0} nicht zulässig in einer Vorlage."},

    { ER_UNKNOWN_EXT_NS_PREFIX,
      "(StylesheetHandler) {0}: Erweiterung des Namespace-Präfixes {1} ist unbekannt"},

    { ER_IMPORTS_AS_FIRST_ELEM,
      "(StylesheetHandler) Importe können nur als erste Elemente in einem Stylesheet auftreten."},

    { ER_IMPORTING_ITSELF,
      "(StylesheetHandler) {0} importiert sich direkt oder indirekt selbst."},

    { ER_XMLSPACE_ILLEGAL_VAL,
      "(StylesheetHandler) xml:space hat einen unzulässigen Wert: {0}"},

    { ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL,
      "processStylesheet nicht erfolgreich."},

    { ER_SAX_EXCEPTION,
     "SAX-Ausnahme"},

//  add this message to fix bug 21478
    { ER_FUNCTION_NOT_SUPPORTED,
     "Funktion nicht unterstützt."},

    { ER_XSLT_ERROR,
     "XSLT-Fehler"},

    { ER_CURRENCY_SIGN_ILLEGAL,
      "Währungssymbol nicht zulässig in Formatmuster-Zeichenfolge"},

    { ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM,
      "Dokumentfunktion nicht unterstützt in DOM-Stylesheet."},

    { ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER,
      "Präfix eines Non-Präfix-Resolver kann nicht aufgelöst werden."},

    { ER_REDIRECT_COULDNT_GET_FILENAME,
      "Umleitungserweiterung: Dateiname konnte nicht abgerufen werden. \"file\"- oder \"select\"-Attribut muss eine gültige Zeichenfolge zurückgeben."},

    { ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT,
      "FormatterListener kann nicht in Umleitungserweiterung erstellt werden."},

    { ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX,
      "Präfix in exclude-result-prefixes ist nicht gültig: {0}"},

    { ER_MISSING_NS_URI,
      "Fehlende Namespace-URI für angegebenes Präfix"},

    { ER_MISSING_ARG_FOR_OPTION,
      "Fehlendes Argument für Option: {0}"},

    { ER_INVALID_OPTION,
     "Ungültige Option: {0}"},

    { ER_MALFORMED_FORMAT_STRING,
     "Fehlerhafte Formatzeichenfolge: {0}"},

    { ER_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet erfordert ein \"version\"-Attribut."},

    { ER_ILLEGAL_ATTRIBUTE_VALUE,
      "Attribut {0} hat einen ungültigen Wert: {1}"},

    { ER_CHOOSE_REQUIRES_WHEN,
     "xsl:choose erfordert xsl:when"},

    { ER_NO_APPLY_IMPORT_IN_FOR_EACH,
      "xsl:apply-imports nicht zulässig in xsl:for-each"},

    { ER_CANT_USE_DTM_FOR_OUTPUT,
      "DTMLiaison kann nicht für einen Ausgabe-DOM-Knoten verwendet werden. Übergeben Sie stattdessen einen com.sun.org.apache.xpath.internal.DOM2Helper."},

    { ER_CANT_USE_DTM_FOR_INPUT,
      "DTMLiaison kann nicht für einen Eingabe-DOM-Knoten verwendet werden. Übergeben Sie stattdessen einen com.sun.org.apache.xpath.internal.DOM2Helper."},

    { ER_CALL_TO_EXT_FAILED,
      "Aufruf von Erweiterungselement nicht erfolgreich: {0}"},

    { ER_PREFIX_MUST_RESOLVE,
      "Präfix muss in einen Namespace aufgelöst werden: {0}"},

    { ER_INVALID_UTF16_SURROGATE,
      "Ungültige UTF-16-Ersetzung festgestellt: {0}?"},

    { ER_XSLATTRSET_USED_ITSELF,
      "xsl:attribute-set {0} hat sich selbst verwendet. Dies führt zu einer Endlosschleife."},

    { ER_CANNOT_MIX_XERCESDOM,
      "Nicht-Xerces-DOM-Eingabe kann nicht mit Xerces-DOM-Ausgabe gemischt werden."},

    { ER_TOO_MANY_LISTENERS,
      "addTraceListenersToStylesheet - TooManyListenersException"},

    { ER_IN_ELEMTEMPLATEELEM_READOBJECT,
      "In ElemTemplateElement.readObject: {0}"},

    { ER_DUPLICATE_NAMED_TEMPLATE,
      "Mehrere Vorlagen mit den Namen {0} gefunden"},

    { ER_INVALID_KEY_CALL,
      "Ungültiger Funktionsaufruf: Rekursive key()-Aufrufe sind nicht zulässig"},

    { ER_REFERENCING_ITSELF,
      "Variable {0} verweist direkt oder indirekt auf sich selbst."},

    { ER_ILLEGAL_DOMSOURCE_INPUT,
      "Der Eingabeknoten darf nicht null sein für eine DOMSource für newTemplates."},

    { ER_CLASS_NOT_FOUND_FOR_OPTION,
        "Klassendatei nicht gefunden für Option {0}"},

    { ER_REQUIRED_ELEM_NOT_FOUND,
        "Erforderliches Element nicht gefunden: {0}"},

    { ER_INPUT_CANNOT_BE_NULL,
        "InputStream darf nicht null sein"},

    { ER_URI_CANNOT_BE_NULL,
        "URI darf nicht null sein"},

    { ER_FILE_CANNOT_BE_NULL,
        "Datei darf nicht null sein"},

    { ER_SOURCE_CANNOT_BE_NULL,
                "InputSource darf nicht null sein"},

    { ER_CANNOT_INIT_BSFMGR,
                "BSF-Manager konnte nicht initialisiert werden"},

    { ER_CANNOT_CMPL_EXTENSN,
                "Erweiterung konnte nicht kompiliert werden"},

    { ER_CANNOT_CREATE_EXTENSN,
      "Erweiterung {0} konnte nicht erstellt werden; Grund: {1}"},

    { ER_INSTANCE_MTHD_CALL_REQUIRES,
      "Der Aufruf einer Instanzmethode von Methode {0} erfordert eine Objektinstanz als erstes Argument"},

    { ER_INVALID_ELEMENT_NAME,
      "Ungültiger Elementname angegeben {0}"},

    { ER_ELEMENT_NAME_METHOD_STATIC,
      "Elementnamenmethode muss statisch sein {0}"},

    { ER_EXTENSION_FUNC_UNKNOWN,
             "Erweiterungsfunktion {0} : {1} ist unbekannt"},

    { ER_MORE_MATCH_CONSTRUCTOR,
             "Mehrere passende Übereinstimmungen für Constructor für {0}"},

    { ER_MORE_MATCH_METHOD,
             "Mehrere passende Übereinstimmungen für Methode {0}"},

    { ER_MORE_MATCH_ELEMENT,
             "Mehrere passende Übereinstimmungen für Elementmethode {0}"},

    { ER_INVALID_CONTEXT_PASSED,
             "Ungültiger Kontext zur Auswertung von {0} übergeben"},

    { ER_POOL_EXISTS,
             "Pool ist bereits vorhanden"},

    { ER_NO_DRIVER_NAME,
             "Kein Treibername angegeben"},

    { ER_NO_URL,
             "Keine URL angegeben"},

    { ER_POOL_SIZE_LESSTHAN_ONE,
             "Poolgröße ist kleiner als eins."},

    { ER_INVALID_DRIVER,
             "Ungültiger Treibername angegeben."},

    { ER_NO_STYLESHEETROOT,
             "Stylesheet-Root wurde nicht gefunden."},

    { ER_ILLEGAL_XMLSPACE_VALUE,
         "Unzulässiger Wert für xml:space"},

    { ER_PROCESSFROMNODE_FAILED,
         "processFromNode nicht erfolgreich"},

    { ER_RESOURCE_COULD_NOT_LOAD,
        "Ressource [ {0} ] konnte nicht geladen werden: {1} \n {2} \t {3}"},

    { ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "Puffergröße <=0"},

    { ER_UNKNOWN_ERROR_CALLING_EXTENSION,
        "Unbekannter Fehler bei Aufruf von Erweiterung"},

    { ER_NO_NAMESPACE_DECL,
        "Präfix {0} hat keine entsprechende Namespace-Deklaration"},

    { ER_ELEM_CONTENT_NOT_ALLOWED,
        "Element-Content nicht zulässig für lang=javaclass {0}"},

    { ER_STYLESHEET_DIRECTED_TERMINATION,
        "Stylesheet führte zu Abbruch"},

    { ER_ONE_OR_TWO,
        "1 oder 2"},

    { ER_TWO_OR_THREE,
        "2 oder 3"},

    { ER_COULD_NOT_LOAD_RESOURCE,
        "{0} konnte nicht geladen werden (CLASSPATH prüfen); die Standardwerte werden verwendet"},

    { ER_CANNOT_INIT_DEFAULT_TEMPLATES,
        "Standardvorlagen können nicht initialisiert werden"},

    { ER_RESULT_NULL,
        "Ergebnis darf nicht null sein"},

    { ER_RESULT_COULD_NOT_BE_SET,
        "Ergebnis konnte nicht festgelegt werden"},

    { ER_NO_OUTPUT_SPECIFIED,
        "Keine Ausgabe angegeben"},

    { ER_CANNOT_TRANSFORM_TO_RESULT_TYPE,
        "Transformation in ein Ergebnis mit Typ {0} nicht möglich"},

    { ER_CANNOT_TRANSFORM_SOURCE_TYPE,
        "Transformation einer Quelle mit Typ {0} nicht möglich"},

    { ER_NULL_CONTENT_HANDLER,
        "Null-Content-Handler"},

    { ER_NULL_ERROR_HANDLER,
        "Null-Error Handler"},

    { ER_CANNOT_CALL_PARSE,
        "Parsen kann nicht aufgerufen werden, wenn der ContentHandler nicht festgelegt wurde"},

    { ER_NO_PARENT_FOR_FILTER,
        "Kein übergeordnetes Objekt für Filter"},

    { ER_NO_STYLESHEET_IN_MEDIA,
         "Kein Stylesheet gefunden in: {0}, Datenträger = {1}"},

    { ER_NO_STYLESHEET_PI,
         "Keine Verarbeitungsanweisung für xml-stylesheet gefunden in: {0}"},

    { ER_NOT_SUPPORTED,
       "Nicht unterstützt: {0}"},

    { ER_PROPERTY_VALUE_BOOLEAN,
       "Wert für Eigenschaft {0} muss eine boolesche Instanz sein"},

    { ER_COULD_NOT_FIND_EXTERN_SCRIPT,
         "Externes Skript bei {0} konnte nicht abgerufen werden"},

    { ER_RESOURCE_COULD_NOT_FIND,
        "Ressource [ {0} ] konnte nicht gefunden werden.\n {1}"},

    { ER_OUTPUT_PROPERTY_NOT_RECOGNIZED,
        "Ausgabeeigenschaft nicht erkannt: {0}"},

    { ER_FAILED_CREATING_ELEMLITRSLT,
        "ElemLiteralResult-Instanz konnte nicht erstellt werden"},

  //Earlier (JDK 1.4 XALAN 2.2-D11) at key code '204' the key name was ER_PRIORITY_NOT_PARSABLE
  // In latest Xalan code base key name is  ER_VALUE_SHOULD_BE_NUMBER. This should also be taken care
  //in locale specific files like XSLTErrorResources_de.java, XSLTErrorResources_fr.java etc.
  //NOTE: Not only the key name but message has also been changed.
    { ER_VALUE_SHOULD_BE_NUMBER,
        "Wert für {0} sollte eine parsefähige Zahl enthalten"},

    { ER_VALUE_SHOULD_EQUAL,
        "Wert für {0} muss \"Ja\" oder \"Nein\" entsprechen"},

    { ER_FAILED_CALLING_METHOD,
        "{0}-Methode konnte nicht aufgerufen werden"},

    { ER_FAILED_CREATING_ELEMTMPL,
        "ElemTemplateElement-Instanz konnte nicht erstellt werden"},

    { ER_CHARS_NOT_ALLOWED,
        "An dieser Stelle im Dokument sind keine Zeichen zulässig"},

    { ER_ATTR_NOT_ALLOWED,
        "\"{0}\"-Attribut ist nicht zulässig beim {1}-Element."},

    { ER_BAD_VALUE,
     "{0} ungültiger Wert {1} "},

    { ER_ATTRIB_VALUE_NOT_FOUND,
     "{0}-Attributwert nicht gefunden "},

    { ER_ATTRIB_VALUE_NOT_RECOGNIZED,
     "{0}-Attributwert nicht erkannt "},

    { ER_NULL_URI_NAMESPACE,
     "Versuch, ein Namespace-Präfix mit einer Null-URI zu generieren"},

    { ER_NUMBER_TOO_BIG,
     "Versuch, eine Zahl zu formatieren, die größer als die größte Long-Ganzzahl ist"},

    { ER_CANNOT_FIND_SAX1_DRIVER,
     "SAX1-Treiberklasse {0} kann nicht gefunden werden"},

    { ER_SAX1_DRIVER_NOT_LOADED,
     "SAX1-Treiberklasse {0} gefunden, kann aber nicht geladen werden"},

    { ER_SAX1_DRIVER_NOT_INSTANTIATED,
     "SAX1-Treiberklasse {0} geladen, kann aber nicht instanziiert werden"},

    { ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER,
     "SAX1-Treiberklasse {0} implementiert org.xml.sax.Parser nicht"},

    { ER_PARSER_PROPERTY_NOT_SPECIFIED,
     "Systemeigenschaft \"org.xml.sax.parser\" nicht angegeben"},

    { ER_PARSER_ARG_CANNOT_BE_NULL,
     "Parserargument darf nicht null sein"},

    { ER_FEATURE,
     "Feature: {0}"},

    { ER_PROPERTY,
     "Eigenschaft: {0}"},

    { ER_NULL_ENTITY_RESOLVER,
     "Null-Entity-Resolver"},

    { ER_NULL_DTD_HANDLER,
     "Null-DTD-Handler"},

    { ER_NO_DRIVER_NAME_SPECIFIED,
     "Kein Treibername angegeben."},

    { ER_NO_URL_SPECIFIED,
     "Keine URL angegeben."},

    { ER_POOLSIZE_LESS_THAN_ONE,
     "Poolgröße ist kleiner als 1."},

    { ER_INVALID_DRIVER_NAME,
     "Ungültiger Treibername angegeben."},

    { ER_ERRORLISTENER,
     "ErrorListener"},


// Note to translators:  The following message should not normally be displayed
//   to users.  It describes a situation in which the processor has detected
//   an internal consistency problem in itself, and it provides this message
//   for the developer to help diagnose the problem.  The name
//   'ElemTemplateElement' is the name of a class, and should not be
//   translated.
    { ER_ASSERT_NO_TEMPLATE_PARENT,
     "Programmiererfehler. Der Ausdruck hat kein übergeordnetes ElemTemplateElement-Objekt."},


// Note to translators:  The following message should not normally be displayed
//   to users.  It describes a situation in which the processor has detected
//   an internal consistency problem in itself, and it provides this message
//   for the developer to help diagnose the problem.  The substitution text
//   provides further information in order to diagnose the problem.  The name
//   'RedundentExprEliminator' is the name of a class, and should not be
//   translated.
    { ER_ASSERT_REDUNDENT_EXPR_ELIMINATOR,
     "Programmierer-Assertion in RedundentExprEliminator: {0}"},

    { ER_NOT_ALLOWED_IN_POSITION,
     "{0} ist an dieser Position im Stylesheet nicht zulässig."},

    { ER_NONWHITESPACE_NOT_ALLOWED_IN_POSITION,
     "Anderer Text als Leerstellen ist an dieser Position im Stylesheet nicht zulässig."},

  // This code is shared with warning codes.
  // SystemId Unknown
    { INVALID_TCHAR,
     "Ungültiger Wert {1} für CHAR-Attribut {0} verwendet. Ein Attribut des Typs CHAR darf nur 1 Zeichen enthalten."},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "QNAME" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value and {0} is the attribute name.
  //The following codes are shared with the warning codes...
    { INVALID_QNAME,
     "Ungültiger Wert {1} für QNAME-Attribut {0} verwendet"},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "ENUM" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value, {0} is the attribute name, and {2} is a list of valid
    // values.
    { INVALID_ENUM,
     "Ungültiger Wert {1} für ENUM-Attribut {0} verwendet. Gültige Werte sind: {2}."},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NMTOKEN" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_NMTOKEN,
     "Ungültiger Wert {1} für NMTOKEN-Attribut {0} verwendet "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NCNAME" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_NCNAME,
     "Ungültiger Wert {1} für NCNAME-Attribut {0} verwendet "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "boolean" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
    { INVALID_BOOLEAN,
     "Ungültiger Wert {1} für \"Boolean\"-Attribut {0} verwendet "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "number" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
     { INVALID_NUMBER,
     "Ungültiger Wert {1} für \"Number\"-Attribut {0} verwendet "},


  // End of shared codes...

// Note to translators:  A "match pattern" is a special form of XPath expression
// that is used for matching patterns.  The substitution text is the name of
// a function.  The message indicates that when this function is referenced in
// a match pattern, its argument must be a string literal (or constant.)
// ER_ARG_LITERAL - new error message for bugzilla //5202
    { ER_ARG_LITERAL,
     "Argument für {0} in Vergleichsmuster muss ein Literal sein."},

// Note to translators:  The following message indicates that two definitions of
// a variable.  A "global variable" is a variable that is accessible everywher
// in the stylesheet.
// ER_DUPLICATE_GLOBAL_VAR - new error message for bugzilla #790
    { ER_DUPLICATE_GLOBAL_VAR,
     "Doppelte Deklaration einer globalen Variable."},


// Note to translators:  The following message indicates that two definitions of
// a variable were encountered.
// ER_DUPLICATE_VAR - new error message for bugzilla #790
    { ER_DUPLICATE_VAR,
     "Doppelte Variablendeklaration."},

    // Note to translators:  "xsl:template, "name" and "match" are XSLT keywords
    // which must not be translated.
    // ER_TEMPLATE_NAME_MATCH - new error message for bugzilla #789
    { ER_TEMPLATE_NAME_MATCH,
     "xsl:template muss ein \"name\"- oder \"match\"-Attribut (oder beides) haben"},

    // Note to translators:  "exclude-result-prefixes" is an XSLT keyword which
    // should not be translated.  The message indicates that a namespace prefix
    // encountered as part of the value of the exclude-result-prefixes attribute
    // was in error.
    // ER_INVALID_PREFIX - new error message for bugzilla #788
    { ER_INVALID_PREFIX,
     "Präfix in exclude-result-prefixes ist nicht gültig: {0}"},

    // Note to translators:  An "attribute set" is a set of attributes that can
    // be added to an element in the output document as a group.  The message
    // indicates that there was a reference to an attribute set named {0} that
    // was never defined.
    // ER_NO_ATTRIB_SET - new error message for bugzilla #782
    { ER_NO_ATTRIB_SET,
     "attribute-set mit Namen {0} ist nicht vorhanden"},

    // Note to translators:  This message indicates that there was a reference
    // to a function named {0} for which no function definition could be found.
    { ER_FUNCTION_NOT_FOUND,
     "Funktion mit Namen {0} ist nicht vorhanden"},

    // Note to translators:  This message indicates that the XSLT instruction
    // that is named by the substitution text {0} must not contain other XSLT
    // instructions (content) or a "select" attribute.  The word "select" is
    // an XSLT keyword in this case and must not be translated.
    { ER_CANT_HAVE_CONTENT_AND_SELECT,
     "{0}-Element darf weder Content noch ein \"select\"-Attribut enthalten."},

    // Note to translators:  This message indicates that the value argument
    // of setParameter must be a valid Java Object.
    { ER_INVALID_SET_PARAM_VALUE,
     "Wert von Parameter {0} muss ein gültiges Java-Objekt sein"},

    { ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX_FOR_DEFAULT,
      "Das result-prefix-Attribut eines xsl:namespace-alias-Elements hat den Wert \"#default\", es ist aber keine Deklaration des Standard-Namespace im Gültigkeitsbereich für das Element vorhanden"},

    { ER_INVALID_NAMESPACE_URI_VALUE_FOR_RESULT_PREFIX,
      "Das result-prefix-Attribut eines xsl:namespace-alias-Elements hat den Wert \"{0}\", es ist aber keine Namespace-Deklaration für das Präfix \"{0}\" im Gültigkeitsbereich für das Element vorhanden."},

    { ER_SET_FEATURE_NULL_NAME,
      "Der Featurename darf nicht null in TransformerFactory.setFeature(Zeichenfolgenname, boolescher Wert) sein."},

    { ER_GET_FEATURE_NULL_NAME,
      "Der Featurename darf nicht null in TransformerFactory.getFeature(Zeichenfolgenname) sein."},

    { ER_UNSUPPORTED_FEATURE,
      "Das Feature \"{0}\" kann nicht für diese TransformerFactory festgelegt werden."},

    { ER_EXTENSION_ELEMENT_NOT_ALLOWED_IN_SECURE_PROCESSING,
        "Verwendung der Erweiterungsfunktion \"{0}\" ist nicht zulässig, wenn die Erweiterungsfunktionen vom Feature für die sichere Verarbeitung oder der Eigenschaft \"jdk.xml.enableExtensionFunctions\" deaktiviert wurden. Setzen Sie \"jdk.xml.enableExtensionFunctions\" auf \"true\", um die Erweiterungsfunktionen zu aktivieren."},

    { ER_NAMESPACE_CONTEXT_NULL_NAMESPACE,
      "Präfix für Null-Namespace-URI kann nicht abgerufen werden."},

    { ER_NAMESPACE_CONTEXT_NULL_PREFIX,
      "Namespace-URI für Nullpräfix kann nicht abgerufen werden."},

    { ER_XPATH_RESOLVER_NULL_QNAME,
      "Funktionsname darf nicht null sein."},

    { ER_XPATH_RESOLVER_NEGATIVE_ARITY,
      "Argumentanzahl darf nicht negativ sein."},
  // Warnings...

    { WG_FOUND_CURLYBRACE,
      "\"}\" gefunden, aber keine Attributvorlage ist geöffnet."},

    { WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR,
      "Warnung: \"count\"-Attribut entspricht keinem Vorgänger in xsl:number. Ziel = {0}"},

    { WG_EXPR_ATTRIB_CHANGED_TO_SELECT,
      "Alte Syntax: Der Name des \"expr\"-Attributs wurde in \"select\" geändert."},

    { WG_NO_LOCALE_IN_FORMATNUMBER,
      "Xalan verarbeitet noch nicht den Gebietsschemanamen in der format-number-Funktion."},

    { WG_LOCALE_NOT_FOUND,
      "Warnung: Gebietsschema für xml:lang={0} konnte nicht gefunden werden"},

    { WG_CANNOT_MAKE_URL_FROM,
      "URL kann nicht erstellt werden aus: {0}"},

    { WG_CANNOT_LOAD_REQUESTED_DOC,
      "Angefordertes Dokument kann nicht geladen werden: {0}"},

    { WG_CANNOT_FIND_COLLATOR,
      "Collator für <sort xml:lang={0} konnte nicht gefunden werden"},

    { WG_FUNCTIONS_SHOULD_USE_URL,
      "Alte Syntax: Die Funktionsanweisung muss eine URL von {0} sein"},

    { WG_ENCODING_NOT_SUPPORTED_USING_UTF8,
      "Codierung nicht unterstützt: {0}. UTF-8 wird verwendet"},

    { WG_ENCODING_NOT_SUPPORTED_USING_JAVA,
      "Codierung nicht unterstützt: {0}. Java {1} wird verwendet"},

    { WG_SPECIFICITY_CONFLICTS,
      "Genauigkeitskonflikte gefunden: {0} Letzte in Stylesheet gefundene Angabe wird verwendet."},

    { WG_PARSING_AND_PREPARING,
      "========= {0} wird geparst und vorbereitet =========="},

    { WG_ATTR_TEMPLATE,
     "Attributvorlage {0}"},

    { WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESPACE,
      "Übereinstimmungskonflikt zwischen xsl:strip-space und xsl:preserve-space"},

    { WG_ATTRIB_NOT_HANDLED,
      "Xalan verarbeitet noch nicht das {0}-Attribut."},

    { WG_NO_DECIMALFORMAT_DECLARATION,
      "Keine Deklaration für Dezimalformat gefunden: {0}"},

    { WG_OLD_XSLT_NS,
     "Fehlender oder falscher XSLT-Namespace. "},

    { WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED,
      "Nur eine Standard-xsl:decimal-format-Deklaration ist zulässig."},

    { WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE,
      "xsl:decimal-format-Namen müssen eindeutig sein. Name \"{0}\" wurde dupliziert."},

    { WG_ILLEGAL_ATTRIBUTE,
      "{0} hat ein unzulässiges Attribut: {1}"},

    { WG_COULD_NOT_RESOLVE_PREFIX,
      "Namespace-Präfix konnte nicht aufgelöst werden: {0}. Der Knoten wird ignoriert."},

    { WG_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet erfordert ein \"version\"-Attribut."},

    { WG_ILLEGAL_ATTRIBUTE_NAME,
      "Unzulässiger Attributname: {0}"},

    { WG_ILLEGAL_ATTRIBUTE_VALUE,
      "Unzulässiger Wert für Attribut {0}: {1}"},

    { WG_EMPTY_SECOND_ARG,
      "Resultierendes NodeSet aus zweitem Argument von Dokumentfunktion ist leer. Geben Sie ein leeres NodeSet zurück."},

  //Following are the new WARNING keys added in XALAN code base after Jdk 1.4 (Xalan 2.2-D11)

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.
    { WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "Der Wert des \"name\"-Attributs des xsl:processing-instruction-Namens darf nicht \"xml\" sein"},

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.  "NCName" is an XML data-type and must not be
    // translated.
    { WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "Der Wert des \"name\"-Attributs von xsl:processing-instruction muss ein gültiger NCName sein: {0}"},

    // Note to translators:  This message is reported if the stylesheet that is
    // being processed attempted to construct an XML document with an attribute in a
    // place other than on an element.  The substitution text specifies the name of
    // the attribute.
    { WG_ILLEGAL_ATTRIBUTE_POSITION,
      "Attribut {0} kann nicht nach untergeordneten Knoten oder vor dem Erstellen eines Elements hinzugefügt werden. Attribut wird ignoriert."},

    { NO_MODIFICATION_ALLOWED_ERR,
      "Es wurde versucht, ein Objekt zu ändern, bei dem Änderungen nicht zulässig sind."
    },

    //Check: WHY THERE IS A GAP B/W NUMBERS in the XSLTErrorResources properties file?

  // Other miscellaneous text used inside the code...
  { "ui_language", "de"},
  {  "help_language",  "de" },
  {  "language",  "de" },
  { "BAD_CODE", "Parameter für createMessage war außerhalb des gültigen Bereichs"},
  {  "FORMAT_FAILED", "Ausnahme bei messageFormat-Aufruf ausgelöst"},
  {  "version", ">>>>>>> Xalan-Version "},
  {  "version2",  "<<<<<<<"},
  {  "yes", "Ja"},
  { "line", "Zeilennummer"},
  { "column","Spaltennummer"},
  { "xsldone", "XSLProcessor: Fertig"},


  // Note to translators:  The following messages provide usage information
  // for the Xalan Process command line.  "Process" is the name of a Java class,
  // and should not be translated.
  { "xslProc_option", "Xalan-J-Befehlszeile - \"Process\"-Klassenoptionen:"},
  { "xslProc_invalid_xsltc_option", "Option {0} wird im XSLTC-Modus nicht unterstützt."},
  { "xslProc_invalid_xalan_option", "Option {0} kann nur mit -XSLTC verwendet werden."},
  { "xslProc_no_input", "Fehler: Kein Stylesheet und keine Eingabe-XML angegeben. Führen Sie diesen Befehl ohne Optionen für Verwendungsanweisungen aus."},
  { "xslProc_common_options", "-Allgemeine Optionen-"},
  { "xslProc_xalan_options", "-Optionen für Xalan-"},
  { "xslProc_xsltc_options", "-Optionen für XSLTC-"},
  { "xslProc_return_to_continue", "(drücken Sie die <Eingabetaste>, um fortzufahren)"},

   // Note to translators: The option name and the parameter name do not need to
   // be translated. Only translate the messages in parentheses.  Note also that
   // leading whitespace in the messages is used to indent the usage information
   // for each option in the English messages.
   // Do not translate the keywords: XSLTC, SAX, DOM and DTM.
  { "optionXSLTC", "   [-XSLTC (XSLTC für Transformation verwenden)]"},
  { "optionIN", "   [-IN inputXMLURL]"},
  { "optionXSL", "   [-XSL XSLTransformationURL]"},
  { "optionOUT",  "   [-OUT outputFileName]"},
  { "optionLXCIN", "   [-LXCIN compiledStylesheetFileNameIn]"},
  { "optionLXCOUT", "   [-LXCOUT compiledStylesheetFileNameOutOut]"},
  { "optionPARSER", "   [-PARSER fully qualified class name of parser liaison]"},
  {  "optionE", "   [-E (Entityreferenzen nicht einblenden)]"},
  {  "optionV",  "   [-E (Entityreferenzen nicht einblenden)]"},
  {  "optionQC", "   [-QC (Stille Musterkonfliktwarnungen)]"},
  {  "optionQ", "   [-Q  (Silent-Modus)]"},
  {  "optionLF", "   [-LF (Nur Zeilenvorschübe bei Ausgabe verwenden {Standard ist CR/LF})]"},
  {  "optionCR", "   [-CR (Nur Zeilenschaltungen bei Ausgabe verwenden {Standard ist CR/LF})]"},
  { "optionESCAPE", "   [-ESCAPE (Escapezeichen {Standard ist <>&\"'\r\n}]"},
  { "optionINDENT", "   [-INDENT (Steuern, wie viele Leerzeichen der Einzug enthalten soll {Standard ist 0})]"},
  { "optionTT", "   [-TT (Vorlagen verfolgen, wenn diese aufgerufen werden.)]"},
  { "optionTG", "   [-TG (Jedes Generierungsereignis verfolgen.)]"},
  { "optionTS", "   [-TS (Jedes Auswahlereignis verfolgen.)]"},
  {  "optionTTC", "   [-TTC (Untergeordnete Vorlagen verfolgen, wenn diese verarbeitet werden.)]"},
  { "optionTCLASS", "   [-TCLASS (TraceListener-Klasse für Traceerweiterungen.)]"},
  { "optionVALIDATE", "   [-VALIDATE (Festlegen, ob die Validierung ausgeführt wird. Validierung ist standardmäßig ausgeschaltet.)]"},
  { "optionEDUMP", "   [-EDUMP {optionaler Dateiname} (Stack Dump bei Fehler vornehmen.)]"},
  {  "optionXML", "   [-XML (XML-Formatter verwenden und XML-Header hinzufügen.)]"},
  {  "optionTEXT", "   [-TEXT (Einfachen Text-Formatter verwenden.)]"},
  {  "optionHTML", "   [-HTML (HTML-Formatter verwenden.)]"},
  {  "optionPARAM", "   [-PARAM name expression (Stylesheet-Parameter festlegen)]"},
  {  "noParsermsg1", "XSL-Prozess war nicht erfolgreich."},
  {  "noParsermsg2", "** Parser konnte nicht gefunden werden **"},
  { "noParsermsg3",  "Prüfen Sie den Classpath."},
  { "noParsermsg4", "Wenn Sie nicht über den XML-Parser für Java von IBM verfügen, können Sie ihn hier herunterladen:"},
  { "noParsermsg5", "IBMs AlphaWorks: http://www.alphaworks.ibm.com/formula/xml"},
  { "optionURIRESOLVER", "   [-URIRESOLVER full class name (URIResolver für die Auflösung von URIs)]"},
  { "optionENTITYRESOLVER",  "   [-ENTITYRESOLVER full class name (EntityResolver für die Auflösung von Entitys)]"},
  { "optionCONTENTHANDLER",  "   [-CONTENTHANDLER full class name (ContentHandler für die Serialisierung der Ausgabe)]"},
  {  "optionLINENUMBERS",  "   [-L use line numbers for source document]"},
  { "optionSECUREPROCESSING", "   [-SECURE (Feature für die sichere Verarbeitung auf \"true\" setzen.)]"},

    // Following are the new options added in XSLTErrorResources.properties files after Jdk 1.4 (Xalan 2.2-D11)


  {  "optionMEDIA",  "   [-MEDIA mediaType (\"media\"-Attribut verwenden, um mit einem Dokument verknüpftes Stylesheet zu finden.)]"},
  {  "optionFLAVOR",  "   [-FLAVOR flavorName (s2s=SAX oder d2d=DOM explizit für Transformation verwenden.)] "}, // Added by sboag/scurcuru; experimental
  { "optionDIAG", "   [-DIAG (Gesamtdauer der Transformation in Millisekunden drucken.)]"},
  { "optionINCREMENTAL",  "   [-INCREMENTAL (inkrementelle DTM-Konstruktion anfordern, indem http://xml.apache.org/xalan/features/incremental auf \"true\" gesetzt wird.)]"},
  {  "optionNOOPTIMIMIZE",  "   [-NOOPTIMIMIZE (keine Stylesheet-Optimierungsverarbeitung anfordern, indem http://xml.apache.org/xalan/features/optimize auf \"false\" gesetzt wird.)]"},
  { "optionRL",  "   [-RL recursionlimit (numerischen Grenzwert für Stylesheet-Rekursionstiefe bekannt machen.)]"},
  {   "optionXO",  "   [-XO [transletName] (Name dem generierten Translet zuweisen)]"},
  {  "optionXD", "   [-XD destinationDirectory (Zielverzeichnis für Translet angeben)]"},
  {  "optionXJ",  "   [-XJ jarfile (verpackt Translet-Klassen in einer JAR-Datei mit dem Namen <jarfile>)]"},
  {   "optionXP",  "   [-XP package (gibt ein Packagenamenspräfix für alle generierten Translet-Klassen an)]"},

  //AddITIONAL  STRINGS that need L10n
  // Note to translators:  The following message describes usage of a particular
  // command-line option that is used to enable the "template inlining"
  // optimization.  The optimization involves making a copy of the code
  // generated for a template in another template that refers to it.
  { "optionXN",  "   [-XN (aktiviert Vorlagen-Inlining)]" },
  { "optionXX",  "   [-XX (schaltet die zusätzliche Debugging-Meldungsausgabe ein)]"},
  { "optionXT" , "   [-XT (wenn möglich, Translet für Transformation verwenden)]"},
  { "diagTiming"," --------- Transformation von {0} über {1} dauerte {2} ms" },
  { "recursionTooDeep","Vorlagenverschachtelung zu tief. Verschachtelung = {0}, Vorlage {1} {2}" },
  { "nameIs", "Name ist" },
  { "matchPatternIs", "Vergleichsmuster ist" }

  };

  }
  // ================= INFRASTRUCTURE ======================

  /** String for use when a bad error code was encountered.    */
  public static final String BAD_CODE = "BAD_CODE";

  /** String for use when formatting of the error string failed.   */
  public static final String FORMAT_FAILED = "FORMAT_FAILED";

    }
