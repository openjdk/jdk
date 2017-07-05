/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: XSLTErrorResources_sv.java,v 1.2.4.1 2005/09/13 11:12:11 pvedula Exp $
 */
package com.sun.org.apache.xalan.internal.res;


import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

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
public class XSLTErrorResources_sv extends ListResourceBundle
{

  /** Maximum error messages, this is needed to keep track of the number of messages.    */
  public static final int MAX_CODE = 201;

  /** Maximum warnings, this is needed to keep track of the number of warnings.          */
  public static final int MAX_WARNING = 29;

  /** Maximum misc strings.   */
  public static final int MAX_OTHERS = 55;

  /** Maximum total warnings and error messages.          */
  public static final int MAX_MESSAGES = MAX_CODE + MAX_WARNING + 1;

    /*
   * Static variables
   */
  public static final String ER_NO_CURLYBRACE = "ER_NO_CURLYBRACE";;
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


  /** Get the lookup table for error messages.
   *
   * @return The int to message lookup table.
   */
  public Object[][] getContents()
  {
    return new Object[][] {

  /** Error message ID that has a null message, but takes in a single object.    */
  //public static final int ERROR0000 = 0;


  {
    "ERROR0000", "{0}"},


  /** ER_NO_CURLYBRACE          */
  //public static final int ER_NO_CURLYBRACE = 1;


  {
    ER_NO_CURLYBRACE,
      "Fel: Kan inte ha '{' inuti uttryck"},


  /** ER_ILLEGAL_ATTRIBUTE          */
  //public static final int ER_ILLEGAL_ATTRIBUTE = 2;


  {
    ER_ILLEGAL_ATTRIBUTE, "{0} har ett otill\u00e5tet attribut: {1}"},


  /** ER_NULL_SOURCENODE_APPLYIMPORTS          */
  //public static final int ER_NULL_SOURCENODE_APPLYIMPORTS = 3;


  {
    ER_NULL_SOURCENODE_APPLYIMPORTS,
      "sourceNode \u00e4r null i xsl:apply-imports!"},


  /** ER_CANNOT_ADD          */
  //public static final int ER_CANNOT_ADD = 4;


  {
    ER_CANNOT_ADD, "Kan inte l\u00e4gga {0} till {1}"},


  /** ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES          */
  //public static final int ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES = 5;


  {
    ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES,
      "sourceNode \u00e4r null i handleApplyTemplatesInstruction!"},


  /** ER_NO_NAME_ATTRIB          */
  //public static final int ER_NO_NAME_ATTRIB = 6;


  {
    ER_NO_NAME_ATTRIB, "{0} m\u00e5ste ha ett namn-attribut."},


  /** ER_TEMPLATE_NOT_FOUND          */
  //public static final int ER_TEMPLATE_NOT_FOUND = 7;


  {
    ER_TEMPLATE_NOT_FOUND, "Hittade inte mallen med namn: {0}"},


  /** ER_CANT_RESOLVE_NAME_AVT          */
  //public static final int ER_CANT_RESOLVE_NAME_AVT = 8;


  {
    ER_CANT_RESOLVE_NAME_AVT,
      "Kunde inte l\u00f6sa namn-AVT i xsl:call-template."},


  /** ER_REQUIRES_ATTRIB          */
  //public static final int ER_REQUIRES_ATTRIB = 9;


  {
    ER_REQUIRES_ATTRIB, "{0} kr\u00e4ver attribut: {1}"},


  /** ER_MUST_HAVE_TEST_ATTRIB          */
  //public static final int ER_MUST_HAVE_TEST_ATTRIB = 10;


  {
    ER_MUST_HAVE_TEST_ATTRIB,
      "{0} m\u00e5ste ha ett ''test''-attribut."},


  /** ER_BAD_VAL_ON_LEVEL_ATTRIB          */
  //public static final int ER_BAD_VAL_ON_LEVEL_ATTRIB = 11;


  {
    ER_BAD_VAL_ON_LEVEL_ATTRIB,
      "D\u00e5ligt v\u00e4rde p\u00e5 niv\u00e5-attribut: {0}"},


  /** ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML          */
  //public static final int ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML = 12;


  {
    ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "Namn p\u00e5 behandlande instruktion f\u00e5r inte vara 'xml'"},


  /** ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME          */
  //public static final int ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME = 13;


  {
    ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "Namn p\u00e5 behandlande instruktion m\u00e5ste vara ett giltigt NCNamn: {0}"},


  /** ER_NEED_MATCH_ATTRIB          */
  //public static final int ER_NEED_MATCH_ATTRIB = 14;


  {
    ER_NEED_MATCH_ATTRIB,
      "{0} m\u00e5ste ha ett matchningsattribut om det har ett tillst\u00e5nd."},


  /** ER_NEED_NAME_OR_MATCH_ATTRIB          */
  //public static final int ER_NEED_NAME_OR_MATCH_ATTRIB = 15;


  {
    ER_NEED_NAME_OR_MATCH_ATTRIB,
      "{0} kr\u00e4ver antingen ett namn eller ett matchningsattribut."},


  /** ER_CANT_RESOLVE_NSPREFIX          */
  //public static final int ER_CANT_RESOLVE_NSPREFIX = 16;


  {
    ER_CANT_RESOLVE_NSPREFIX,
      "Kan inte l\u00f6sa namnrymdsprefix: {0}"},


  /** ER_ILLEGAL_VALUE          */
  //public static final int ER_ILLEGAL_VALUE = 17;


  {
    ER_ILLEGAL_VALUE, "xml:space har ett otill\u00e5tet v\u00e4rde: {0}"},


  /** ER_NO_OWNERDOC          */
  //public static final int ER_NO_OWNERDOC = 18;


  {
    ER_NO_OWNERDOC,
      "Barnnod saknar \u00e4gardokument!"},


  /** ER_ELEMTEMPLATEELEM_ERR          */
  //public static final int ER_ELEMTEMPLATEELEM_ERR = 19;


  {
    ER_ELEMTEMPLATEELEM_ERR, "ElemTemplateElement-fel: {0}"},


  /** ER_NULL_CHILD          */
  //public static final int ER_NULL_CHILD = 20;


  {
    ER_NULL_CHILD, "F\u00f6rs\u00f6ker l\u00e4gga till ett null-barn!"},


  /** ER_NEED_SELECT_ATTRIB          */
  //public static final int ER_NEED_SELECT_ATTRIB = 21;


  {
    ER_NEED_SELECT_ATTRIB, "{0} kr\u00e4ver ett valattribut."},


  /** ER_NEED_TEST_ATTRIB          */
  //public static final int ER_NEED_TEST_ATTRIB = 22;


  {
    ER_NEED_TEST_ATTRIB,
      "xsl:when m\u00e5ste ha ett 'test'-attribut."},


  /** ER_NEED_NAME_ATTRIB          */
  //public static final int ER_NEED_NAME_ATTRIB = 23;


  {
    ER_NEED_NAME_ATTRIB,
      "xsl:with-param m\u00e5ste ha ett 'namn'-attribut."},


  /** ER_NO_CONTEXT_OWNERDOC          */
  //public static final int ER_NO_CONTEXT_OWNERDOC = 24;


  {
    ER_NO_CONTEXT_OWNERDOC,
      "Kontext saknar \u00e4gardokument!"},


  /** ER_COULD_NOT_CREATE_XML_PROC_LIAISON          */
  //public static final int ER_COULD_NOT_CREATE_XML_PROC_LIAISON = 25;


  {
    ER_COULD_NOT_CREATE_XML_PROC_LIAISON,
      "Kunde inte skapa XML TransformerFactory Liaison: {0}"},


  /** ER_PROCESS_NOT_SUCCESSFUL          */
  //public static final int ER_PROCESS_NOT_SUCCESSFUL = 26;


  {
    ER_PROCESS_NOT_SUCCESSFUL,
      "Xalan: Process misslyckades."},


  /** ER_NOT_SUCCESSFUL          */
  //public static final int ER_NOT_SUCCESSFUL = 27;


  {
    ER_NOT_SUCCESSFUL, "Xalan: misslyckades."},


  /** ER_ENCODING_NOT_SUPPORTED          */
  //public static final int ER_ENCODING_NOT_SUPPORTED = 28;


  {
    ER_ENCODING_NOT_SUPPORTED, "Kodning inte underst\u00f6dd: {0}"},


  /** ER_COULD_NOT_CREATE_TRACELISTENER          */
  //public static final int ER_COULD_NOT_CREATE_TRACELISTENER = 29;


  {
    ER_COULD_NOT_CREATE_TRACELISTENER,
      "Kunde inte skapa TraceListener: {0}"},


  /** ER_KEY_REQUIRES_NAME_ATTRIB          */
  //public static final int ER_KEY_REQUIRES_NAME_ATTRIB = 30;


  {
    ER_KEY_REQUIRES_NAME_ATTRIB,
      "xsl:key m\u00e5ste ha ett 'namn'-attribut."},


  /** ER_KEY_REQUIRES_MATCH_ATTRIB          */
  //public static final int ER_KEY_REQUIRES_MATCH_ATTRIB = 31;


  {
    ER_KEY_REQUIRES_MATCH_ATTRIB,
      "xsl:key m\u00e5ste ha ett 'matcha'-attribut."},


  /** ER_KEY_REQUIRES_USE_ATTRIB          */
  //public static final int ER_KEY_REQUIRES_USE_ATTRIB = 32;


  {
    ER_KEY_REQUIRES_USE_ATTRIB,
      "xsl:key m\u00e5ste ha ett 'anv\u00e4nd'-attribut."},


  /** ER_REQUIRES_ELEMENTS_ATTRIB          */
  //public static final int ER_REQUIRES_ELEMENTS_ATTRIB = 33;


  {
    ER_REQUIRES_ELEMENTS_ATTRIB,
      "(StylesheetHandler) {0} kr\u00e4ver ett ''element''-attribut!"},


  /** ER_MISSING_PREFIX_ATTRIB          */
  //public static final int ER_MISSING_PREFIX_ATTRIB = 34;


  {
    ER_MISSING_PREFIX_ATTRIB,
      "(StylesheetHandler) {0} ''prefix''-attribut saknas"},


  /** ER_BAD_STYLESHEET_URL          */
  //public static final int ER_BAD_STYLESHEET_URL = 35;


  {
    ER_BAD_STYLESHEET_URL, "Stylesheet URL \u00e4r d\u00e5lig: {0}"},


  /** ER_FILE_NOT_FOUND          */
  //public static final int ER_FILE_NOT_FOUND = 36;


  {
    ER_FILE_NOT_FOUND, "Stylesheet-fil saknas: {0}"},


  /** ER_IOEXCEPTION          */
  //public static final int ER_IOEXCEPTION = 37;


  {
    ER_IOEXCEPTION,
      "Fick IO-Undantag med stylesheet-fil: {0}"},


  /** ER_NO_HREF_ATTRIB          */
  //public static final int ER_NO_HREF_ATTRIB = 38;


  {
    ER_NO_HREF_ATTRIB,
      "(StylesheetHandler) Hittade inte href-attribute f\u00f6r {0}"},


  /** ER_STYLESHEET_INCLUDES_ITSELF          */
  //public static final int ER_STYLESHEET_INCLUDES_ITSELF = 39;


  {
    ER_STYLESHEET_INCLUDES_ITSELF,
      "(StylesheetHandler) {0} inkluderar, direkt eller indirekt, sig sj\u00e4lv!"},


  /** ER_PROCESSINCLUDE_ERROR          */
  //public static final int ER_PROCESSINCLUDE_ERROR = 40;


  {
    ER_PROCESSINCLUDE_ERROR,
      "StylesheetHandler.processInclude-fel, {0}"},


  /** ER_MISSING_LANG_ATTRIB          */
  //public static final int ER_MISSING_LANG_ATTRIB = 41;


  {
    ER_MISSING_LANG_ATTRIB,
      "(StylesheetHandler) {0} ''lang''-attribut' saknas"},


  /** ER_MISSING_CONTAINER_ELEMENT_COMPONENT          */
  //public static final int ER_MISSING_CONTAINER_ELEMENT_COMPONENT = 42;


  {
    ER_MISSING_CONTAINER_ELEMENT_COMPONENT,
      "(StylesheetHandler) felplacerade {0} element?? Saknar beh\u00e5llarelement  ''komponent''"},


  /** ER_CAN_ONLY_OUTPUT_TO_ELEMENT          */
  //public static final int ER_CAN_ONLY_OUTPUT_TO_ELEMENT = 43;


  {
    ER_CAN_ONLY_OUTPUT_TO_ELEMENT,
      "Kan endast skicka utdata till ett Element, ett DocumentFragment, ett Document, eller en PrintWriter."},


  /** ER_PROCESS_ERROR          */
  //public static final int ER_PROCESS_ERROR = 44;


  {
    ER_PROCESS_ERROR, "StylesheetRoot.process-fel"},


  /** ER_UNIMPLNODE_ERROR          */
  //public static final int ER_UNIMPLNODE_ERROR = 45;


  {
    ER_UNIMPLNODE_ERROR, "UnImplNode-fel: {0}"},


  /** ER_NO_SELECT_EXPRESSION          */
  //public static final int ER_NO_SELECT_EXPRESSION = 46;


  {
    ER_NO_SELECT_EXPRESSION,
      "Fel! Hittade inte xpath select-uttryck (-select)."},


  /** ER_CANNOT_SERIALIZE_XSLPROCESSOR          */
  //public static final int ER_CANNOT_SERIALIZE_XSLPROCESSOR = 47;


  {
    ER_CANNOT_SERIALIZE_XSLPROCESSOR,
      "Kan inte serialisera en XSLProcessor!"},


  /** ER_NO_INPUT_STYLESHEET          */
  //public static final int ER_NO_INPUT_STYLESHEET = 48;


  {
    ER_NO_INPUT_STYLESHEET,
      "Stylesheet-indata ej angiven!"},


  /** ER_FAILED_PROCESS_STYLESHEET          */
  //public static final int ER_FAILED_PROCESS_STYLESHEET = 49;


  {
    ER_FAILED_PROCESS_STYLESHEET,
      "Kunde inte behandla stylesheet!"},


  /** ER_COULDNT_PARSE_DOC          */
  //public static final int ER_COULDNT_PARSE_DOC = 50;


  {
    ER_COULDNT_PARSE_DOC, "Kunde inte tolka {0} dokument!"},


  /** ER_COULDNT_FIND_FRAGMENT          */
  //public static final int ER_COULDNT_FIND_FRAGMENT = 51;


  {
    ER_COULDNT_FIND_FRAGMENT, "Hittade inte fragment: {0}"},


  /** ER_NODE_NOT_ELEMENT          */
  //public static final int ER_NODE_NOT_ELEMENT = 52;


  {
    ER_NODE_NOT_ELEMENT,
      "Nod som pekades p\u00e5 av fragment-identifierare var inte ett element: {0}"},


  /** ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB          */
  //public static final int ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB = 53;


  {
    ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB,
      "for-each kr\u00e4ver antingen en matchning eller ett namnattribut."},


  /** ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB          */
  //public static final int ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB = 54;


  {
    ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB,
      "mallar kr\u00e4ver antingen en matchning eller ett namnattribut."},


  /** ER_NO_CLONE_OF_DOCUMENT_FRAG          */
  //public static final int ER_NO_CLONE_OF_DOCUMENT_FRAG = 55;


  {
    ER_NO_CLONE_OF_DOCUMENT_FRAG,
      "Ingen klon av ett dokumentfragment!"},


  /** ER_CANT_CREATE_ITEM          */
  //public static final int ER_CANT_CREATE_ITEM = 56;


  {
    ER_CANT_CREATE_ITEM,
      "Kan inte skapa element i resultattr\u00e4d: {0}"},


  /** ER_XMLSPACE_ILLEGAL_VALUE          */
  //public static final int ER_XMLSPACE_ILLEGAL_VALUE = 57;


  {
    ER_XMLSPACE_ILLEGAL_VALUE,
      "xml:space i k\u00e4ll-XML har ett otill\u00e5tet v\u00e4rde: {0}"},


  /** ER_NO_XSLKEY_DECLARATION          */
  //public static final int ER_NO_XSLKEY_DECLARATION = 58;


  {
    ER_NO_XSLKEY_DECLARATION,
      "Det finns ingen xsl:key-deklaration f\u00f6r {0}!"},


  /** ER_CANT_CREATE_URL          */
  //public static final int ER_CANT_CREATE_URL = 59;


  {
    ER_CANT_CREATE_URL, "Fel! Kan inte skapa url f\u00f6r: {0}"},


  /** ER_XSLFUNCTIONS_UNSUPPORTED          */
  //public static final int ER_XSLFUNCTIONS_UNSUPPORTED = 60;


  {
    ER_XSLFUNCTIONS_UNSUPPORTED, "xsl:functions \u00e4r inte underst\u00f6dd"},


  /** ER_PROCESSOR_ERROR          */
  //public static final int ER_PROCESSOR_ERROR = 61;


  {
    ER_PROCESSOR_ERROR, "XSLT TransformerFactory-Fel"},


  /** ER_NOT_ALLOWED_INSIDE_STYLESHEET          */
  //public static final int ER_NOT_ALLOWED_INSIDE_STYLESHEET = 62;


  {
    ER_NOT_ALLOWED_INSIDE_STYLESHEET,
      "(StylesheetHandler) {0} \u00e4r inte till\u00e5ten inne i ett stylesheet!"},


  /** ER_RESULTNS_NOT_SUPPORTED          */
  //public static final int ER_RESULTNS_NOT_SUPPORTED = 63;


  {
    ER_RESULTNS_NOT_SUPPORTED,
      "result-ns inte l\u00e4ngre underst\u00f6dd!  Anv\u00e4nd xsl:output ist\u00e4llet."},


  /** ER_DEFAULTSPACE_NOT_SUPPORTED          */
  //public static final int ER_DEFAULTSPACE_NOT_SUPPORTED = 64;


  {
    ER_DEFAULTSPACE_NOT_SUPPORTED,
      "default-space inte l\u00e4ngre underst\u00f6dd!  Anv\u00e4nd xsl:strip-space eller xsl:preserve-space ist\u00e4llet."},


  /** ER_INDENTRESULT_NOT_SUPPORTED          */
  //public static final int ER_INDENTRESULT_NOT_SUPPORTED = 65;


  {
    ER_INDENTRESULT_NOT_SUPPORTED,
      "indent-result inte l\u00e4ngre underst\u00f6dd!  Anv\u00e4nd xsl:output ist\u00e4llet."},


  /** ER_ILLEGAL_ATTRIB          */
  //public static final int ER_ILLEGAL_ATTRIB = 66;


  {
    ER_ILLEGAL_ATTRIB,
      "(StylesheetHandler) {0} har ett otill\u00e5tet attribut: {1}"},


  /** ER_UNKNOWN_XSL_ELEM          */
  //public static final int ER_UNKNOWN_XSL_ELEM = 67;


  {
    ER_UNKNOWN_XSL_ELEM, "Ok\u00e4nt XSL-element: {0}"},


  /** ER_BAD_XSLSORT_USE          */
  //public static final int ER_BAD_XSLSORT_USE = 68;


  {
    ER_BAD_XSLSORT_USE,
      "(StylesheetHandler) xsl:sort kan endast anv\u00e4ndas med xsl:apply-templates eller xsl:for-each."},


  /** ER_MISPLACED_XSLWHEN          */
  //public static final int ER_MISPLACED_XSLWHEN = 69;


  {
    ER_MISPLACED_XSLWHEN,
      "(StylesheetHandler) felplacerade xsl:when!"},


  /** ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE          */
  //public static final int ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE = 70;


  {
    ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:when h\u00e4rstammar inte fr\u00e5n xsl:choose!"},


  /** ER_MISPLACED_XSLOTHERWISE          */
  //public static final int ER_MISPLACED_XSLOTHERWISE = 71;


  {
    ER_MISPLACED_XSLOTHERWISE,
      "(StylesheetHandler) felplacerade xsl:otherwise!"},


  /** ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE          */
  //public static final int ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE = 72;


  {
    ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:otherwise h\u00e4rstammar inte fr\u00e5n xsl:choose!"},


  /** ER_NOT_ALLOWED_INSIDE_TEMPLATE          */
  //public static final int ER_NOT_ALLOWED_INSIDE_TEMPLATE = 73;


  {
    ER_NOT_ALLOWED_INSIDE_TEMPLATE,
      "(StylesheetHandler) {0} \u00e4r inte till\u00e5ten inne i en mall!"},


  /** ER_UNKNOWN_EXT_NS_PREFIX          */
  //public static final int ER_UNKNOWN_EXT_NS_PREFIX = 74;


  {
    ER_UNKNOWN_EXT_NS_PREFIX,
      "(StylesheetHandler) {0} utbyggnadsnamnrymdsprefix {1} ok\u00e4nt"},


  /** ER_IMPORTS_AS_FIRST_ELEM          */
  //public static final int ER_IMPORTS_AS_FIRST_ELEM = 75;


  {
    ER_IMPORTS_AS_FIRST_ELEM,
      "(StylesheetHandler) Imports kan endast f\u00f6rekomma som de f\u00f6rsta elementen i ett stylesheet!"},


  /** ER_IMPORTING_ITSELF          */
  //public static final int ER_IMPORTING_ITSELF = 76;


  {
    ER_IMPORTING_ITSELF,
      "(StylesheetHandler) {0} importerar, direkt eller indirekt, sig sj\u00e4lv!"},


  /** ER_XMLSPACE_ILLEGAL_VAL          */
  //public static final int ER_XMLSPACE_ILLEGAL_VAL = 77;


  {
    ER_XMLSPACE_ILLEGAL_VAL,
      "(StylesheetHandler) " + "xml:space har ett otill\u00e5tet v\u00e4rde: {0}"},


  /** ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL          */
  //public static final int ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL = 78;


  {
    ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL,
      "processStylesheet misslyckades!"},


  /** ER_SAX_EXCEPTION          */
  //public static final int ER_SAX_EXCEPTION = 79;


  {
    ER_SAX_EXCEPTION, "SAX-Undantag"},



  /** ER_XSLT_ERROR          */
  //public static final int ER_XSLT_ERROR = 81;


  {
    ER_XSLT_ERROR, "XSLT-fel"},


  /** ER_CURRENCY_SIGN_ILLEGAL          */
  //public static final int ER_CURRENCY_SIGN_ILLEGAL = 82;


  {
    ER_CURRENCY_SIGN_ILLEGAL,
      "valutatecken \u00e4r inte till\u00e5tet i formatm\u00f6nsterstr\u00e4ng"},


  /** ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM          */
  //public static final int ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM = 83;


  {
    ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM,
      "Dokumentfunktion inte underst\u00f6dd i Stylesheet DOM!"},


  /** ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER          */
  //public static final int ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER = 84;


  {
    ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER,
      "Kan inte l\u00f6sa prefix i icke-Prefixl\u00f6sare!"},


  /** ER_REDIRECT_COULDNT_GET_FILENAME          */
  //public static final int ER_REDIRECT_COULDNT_GET_FILENAME = 85;


  {
    ER_REDIRECT_COULDNT_GET_FILENAME,
      "Redirect extension: Hittade inte filnamn - fil eller valattribut m\u00e5ste returnera vald  str\u00e4ng."},


  /** ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT          */
  //public static final int ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT = 86;


  {
    ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT,
      "Kan inte bygga FormatterListener i Redirect extension!"},


  /** ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX          */
  //public static final int ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX = 87;


  {
    ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX,
      "Prefix i exkludera-resultat-prefix \u00e4r inte giltig: {0}"},


  /** ER_MISSING_NS_URI          */
  //public static final int ER_MISSING_NS_URI = 88;


  {
    ER_MISSING_NS_URI,
      "Namnrymds-URI saknas f\u00f6r angivna prefix"},


  /** ER_MISSING_ARG_FOR_OPTION          */
  //public static final int ER_MISSING_ARG_FOR_OPTION = 89;


  {
    ER_MISSING_ARG_FOR_OPTION,
      "Argument saknas f\u00f6r alternativ: {0}"},


  /** ER_INVALID_OPTION          */
  //public static final int ER_INVALID_OPTION = 90;


  {
    ER_INVALID_OPTION, "Ogiltigt alternativ: {0}"},


  /** ER_MALFORMED_FORMAT_STRING          */
  //public static final int ER_MALFORMED_FORMAT_STRING = 91;


  {
    ER_MALFORMED_FORMAT_STRING, "Fel format p\u00e5 formatstr\u00e4ng: {0}"},


  /** ER_STYLESHEET_REQUIRES_VERSION_ATTRIB          */
  //public static final int ER_STYLESHEET_REQUIRES_VERSION_ATTRIB = 92;


  {
    ER_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet m\u00e5ste ha ett 'version'-attribut!"},


  /** ER_ILLEGAL_ATTRIBUTE_VALUE          */
  //public static final int ER_ILLEGAL_ATTRIBUTE_VALUE = 93;


  {
    ER_ILLEGAL_ATTRIBUTE_VALUE,
      "Attribut: {0} har ett otill\u00e5tet v\u00e4rde: {1}"},


  /** ER_CHOOSE_REQUIRES_WHEN          */
  //public static final int ER_CHOOSE_REQUIRES_WHEN = 94;


  {
    ER_CHOOSE_REQUIRES_WHEN, "xsl:choose kr\u00e4ver ett xsl:when"},


  /** ER_NO_APPLY_IMPORT_IN_FOR_EACH          */
  //public static final int ER_NO_APPLY_IMPORT_IN_FOR_EACH = 95;


  {
    ER_NO_APPLY_IMPORT_IN_FOR_EACH,
      "xsl:apply-imports inte till\u00e5tet i ett xsl:for-each"},


  /** ER_CANT_USE_DTM_FOR_OUTPUT          */
  //public static final int ER_CANT_USE_DTM_FOR_OUTPUT = 96;


  {
    ER_CANT_USE_DTM_FOR_OUTPUT,
      "Kan inte anv\u00e4nda DTMLiaison till en DOM utdatanod... skicka en com.sun.org.apache.xpath.internal.DOM2Helper ist\u00e4llet!"},


  /** ER_CANT_USE_DTM_FOR_INPUT          */
  //public static final int ER_CANT_USE_DTM_FOR_INPUT = 97;


  {
    ER_CANT_USE_DTM_FOR_INPUT,
      "Kan inte anv\u00e4nda DTMLiaison till en DOM indatanod... skicka en com.sun.org.apache.xpath.internal.DOM2Helper ist\u00e4llet!"},


  /** ER_CALL_TO_EXT_FAILED          */
  //public static final int ER_CALL_TO_EXT_FAILED = 98;


  {
    ER_CALL_TO_EXT_FAILED,
      "Anrop till anslutningselement misslyckades: {0}"},


  /** ER_PREFIX_MUST_RESOLVE          */
  //public static final int ER_PREFIX_MUST_RESOLVE = 99;


  {
    ER_PREFIX_MUST_RESOLVE,
      "Prefix m\u00e5ste l\u00f6sa till en mamnrymd: {0}"},


  /** ER_INVALID_UTF16_SURROGATE          */
  //public static final int ER_INVALID_UTF16_SURROGATE = 100;


  {
    ER_INVALID_UTF16_SURROGATE,
      "Ogiltigt UTF-16-surrogat uppt\u00e4ckt: {0} ?"},


  /** ER_XSLATTRSET_USED_ITSELF          */
  //public static final int ER_XSLATTRSET_USED_ITSELF = 101;


  {
    ER_XSLATTRSET_USED_ITSELF,
      "xsl:attribute-set {0} anv\u00e4nde sig sj\u00e4lvt, vilket kommer att orsaka en  o\u00e4ndlig loop."},


  /** ER_CANNOT_MIX_XERCESDOM          */
  //public static final int ER_CANNOT_MIX_XERCESDOM = 102;


  {
    ER_CANNOT_MIX_XERCESDOM,
      "Kan inte blanda icke-Xerces-DOM-indata med Xerces-DOM-utdata!"},


  /** ER_TOO_MANY_LISTENERS          */
  //public static final int ER_TOO_MANY_LISTENERS = 103;


  {
    ER_TOO_MANY_LISTENERS,
      "addTraceListenersToStylesheet - TooManyListenersException"},


  /** ER_IN_ELEMTEMPLATEELEM_READOBJECT          */
  //public static final int ER_IN_ELEMTEMPLATEELEM_READOBJECT = 104;


  {
    ER_IN_ELEMTEMPLATEELEM_READOBJECT,
      "I ElemTemplateElement.readObject: {0}"},


  /** ER_DUPLICATE_NAMED_TEMPLATE          */
  //public static final int ER_DUPLICATE_NAMED_TEMPLATE = 105;


  {
    ER_DUPLICATE_NAMED_TEMPLATE,
      "Hittade mer \u00e4n en mall med namnet: {0}"},


  /** ER_INVALID_KEY_CALL          */
  //public static final int ER_INVALID_KEY_CALL = 106;


  {
    ER_INVALID_KEY_CALL,
      "Ogiltigt funktionsanrop: rekursiva key()-anrop \u00e4r inte till\u00e5tna"},


  /** Variable is referencing itself          */
  //public static final int ER_REFERENCING_ITSELF = 107;


  {
    ER_REFERENCING_ITSELF,
      "Variabel {0} h\u00e4nvisar, direkt eller indirekt, till sig sj\u00e4lv!"},


  /** Illegal DOMSource input          */
  //public static final int ER_ILLEGAL_DOMSOURCE_INPUT = 108;


  {
    ER_ILLEGAL_DOMSOURCE_INPUT,
      "Indatanoden till en DOMSource f\u00f6r newTemplates f\u00e5r inte vara null!"},


        /** Class not found for option         */
  //public static final int ER_CLASS_NOT_FOUND_FOR_OPTION = 109;


  {
    ER_CLASS_NOT_FOUND_FOR_OPTION,
                        "Klassfil f\u00f6r alternativ {0} saknas"},


        /** Required Element not found         */
  //public static final int ER_REQUIRED_ELEM_NOT_FOUND = 110;


  {
    ER_REQUIRED_ELEM_NOT_FOUND,
                        "N\u00f6dv\u00e4ndigt element saknas: {0}"},


  /** InputStream cannot be null         */
  //public static final int ER_INPUT_CANNOT_BE_NULL = 111;


  {
    ER_INPUT_CANNOT_BE_NULL,
                        "InputStream f\u00e5r inte vara null"},


  /** URI cannot be null         */
  //public static final int ER_URI_CANNOT_BE_NULL = 112;


  {
    ER_URI_CANNOT_BE_NULL,
                        "URI f\u00e5r inte vara null"},


  /** File cannot be null         */
  //public static final int ER_FILE_CANNOT_BE_NULL = 113;


  {
    ER_FILE_CANNOT_BE_NULL,
                        "Fil f\u00e5r inte vara null"},


   /** InputSource cannot be null         */
  //public static final int ER_SOURCE_CANNOT_BE_NULL = 114;


  {
    ER_SOURCE_CANNOT_BE_NULL,
                        "InputSource f\u00e5r inte vara null"},


  /** Could not initialize BSF Manager        */
  //public static final int ER_CANNOT_INIT_BSFMGR = 116;


  {
    ER_CANNOT_INIT_BSFMGR,
                        "Kan inte initialisera BSF Manager"},


  /** Could not compile extension       */
  //public static final int ER_CANNOT_CMPL_EXTENSN = 117;


  {
    ER_CANNOT_CMPL_EXTENSN,
                        "Kunde inte kompilera anslutning"},


  /** Could not create extension       */
  //public static final int ER_CANNOT_CREATE_EXTENSN = 118;


  {
    ER_CANNOT_CREATE_EXTENSN,
      "Kunde inte skapa anslutning: {0} p\u00e5 grund av: {1}"},


  /** Instance method call to method {0} requires an Object instance as first argument       */
  //public static final int ER_INSTANCE_MTHD_CALL_REQUIRES = 119;


  {
    ER_INSTANCE_MTHD_CALL_REQUIRES,
      "Instansmetodanrop till metod {0} kr\u00e4ver en Objektinstans som f\u00f6rsta argument"},


  /** Invalid element name specified       */
  //public static final int ER_INVALID_ELEMENT_NAME = 120;


  {
    ER_INVALID_ELEMENT_NAME,
      "Ogiltigt elementnamn angivet {0}"},


   /** Element name method must be static      */
  //public static final int ER_ELEMENT_NAME_METHOD_STATIC = 121;


  {
    ER_ELEMENT_NAME_METHOD_STATIC,
      "Elementnamnmetod m\u00e5ste vara static {0}"},


   /** Extension function {0} : {1} is unknown      */
  //public static final int ER_EXTENSION_FUNC_UNKNOWN = 122;


  {
    ER_EXTENSION_FUNC_UNKNOWN,
             "Anslutningsfunktion {0} : {1} \u00e4r ok\u00e4nd"},


   /** More than one best match for constructor for       */
  //public static final int ER_MORE_MATCH_CONSTRUCTOR = 123;


  {
    ER_MORE_MATCH_CONSTRUCTOR,
             "Fler \u00e4n en b\u00e4sta matchning f\u00f6r konstruktor f\u00f6r {0}"},


   /** More than one best match for method      */
  //public static final int ER_MORE_MATCH_METHOD = 124;


  {
    ER_MORE_MATCH_METHOD,
             "Fler \u00e4n en b\u00e4sta matchning f\u00f6r metod {0}"},


   /** More than one best match for element method      */
  //public static final int ER_MORE_MATCH_ELEMENT = 125;


  {
    ER_MORE_MATCH_ELEMENT,
             "Fler \u00e4n en b\u00e4sta matchning f\u00f6r elementmetod {0}"},


   /** Invalid context passed to evaluate       */
  //public static final int ER_INVALID_CONTEXT_PASSED = 126;


  {
    ER_INVALID_CONTEXT_PASSED,
             "Ogiltig kontext skickad f\u00f6r att utv\u00e4rdera {0}"},


   /** Pool already exists       */
  //public static final int ER_POOL_EXISTS = 127;


  {
    ER_POOL_EXISTS,
             "Pool finns redan"},


   /** No driver Name specified      */
  //public static final int ER_NO_DRIVER_NAME = 128;


  {
    ER_NO_DRIVER_NAME,
             "Inget driver-namn angivet"},


   /** No URL specified     */
  //public static final int ER_NO_URL = 129;


  {
    ER_NO_URL,
             "Ingen URL angiven"},


   /** Pool size is less than one    */
  //public static final int ER_POOL_SIZE_LESSTHAN_ONE = 130;


  {
    ER_POOL_SIZE_LESSTHAN_ONE,
             "Poolstorlek \u00e4r mindre \u00e4n ett!"},


   /** Invalid driver name specified    */
  //public static final int ER_INVALID_DRIVER = 131;


  {
    ER_INVALID_DRIVER,
             "Ogiltigt driver-namn angivet"},


   /** Did not find the stylesheet root    */
  //public static final int ER_NO_STYLESHEETROOT = 132;


  {
    ER_NO_STYLESHEETROOT,
             "Hittade inte stylesheet-roten!"},


   /** Illegal value for xml:space     */
  //public static final int ER_ILLEGAL_XMLSPACE_VALUE = 133;


  {
    ER_ILLEGAL_XMLSPACE_VALUE,
         "Ogiltigt v\u00e4rde f\u00f6r xml:space"},


   /** processFromNode failed     */
  //public static final int ER_PROCESSFROMNODE_FAILED = 134;


  {
    ER_PROCESSFROMNODE_FAILED,
         "processFromNode misslyckades"},


   /** The resource [] could not load:     */
  //public static final int ER_RESOURCE_COULD_NOT_LOAD = 135;


  {
    ER_RESOURCE_COULD_NOT_LOAD,
        "Resursen [ {0} ] kunde inte laddas: {1} \n {2} \t {3}"},



   /** Buffer size <=0     */
  //public static final int ER_BUFFER_SIZE_LESSTHAN_ZERO = 136;


  {
    ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "Bufferstorlek <=0"},


   /** Unknown error when calling extension    */
  //public static final int ER_UNKNOWN_ERROR_CALLING_EXTENSION = 137;


  {
    ER_UNKNOWN_ERROR_CALLING_EXTENSION,
        "Ok\u00e4nt fel vid anslutningsanrop"},


   /** Prefix {0} does not have a corresponding namespace declaration    */
  //public static final int ER_NO_NAMESPACE_DECL = 138;


  {
    ER_NO_NAMESPACE_DECL,
        "Prefix{0} har inte en motsvarande namnrymdsdeklaration"},


   /** Element content not allowed for lang=javaclass   */
  //public static final int ER_ELEM_CONTENT_NOT_ALLOWED = 139;


  {
    ER_ELEM_CONTENT_NOT_ALLOWED,
        "Elementinneh\u00e5ll \u00e4r inte till\u00e5tet f\u00f6r lang=javaclass {0}"},


   /** Stylesheet directed termination   */
  //public static final int ER_STYLESHEET_DIRECTED_TERMINATION = 140;


  {
    ER_STYLESHEET_DIRECTED_TERMINATION,
        "Stylesheet-ledd avslutning"},


   /** 1 or 2   */
  //public static final int ER_ONE_OR_TWO = 141;


  {
    ER_ONE_OR_TWO,
        "1 eller 2"},


   /** 2 or 3   */
  //public static final int ER_TWO_OR_THREE = 142;


  {
    ER_TWO_OR_THREE,
        "2 eller 3"},


   /** Could not load {0} (check CLASSPATH), now using just the defaults   */
  //public static final int ER_COULD_NOT_LOAD_RESOURCE = 143;


  {
    ER_COULD_NOT_LOAD_RESOURCE,
        "Kunde inte ladda {0} (kontrollera CLASSPATH), anv\u00e4nder nu enbart standard"},


   /** Cannot initialize default templates   */
  //public static final int ER_CANNOT_INIT_DEFAULT_TEMPLATES = 144;


  {
    ER_CANNOT_INIT_DEFAULT_TEMPLATES,
        "Kan inte initialisera standardmallar"},


   /** Result should not be null   */
  //public static final int ER_RESULT_NULL = 145;


  {
    ER_RESULT_NULL,
        "Result borde inte vara null"},


   /** Result could not be set   */
  //public static final int ER_RESULT_COULD_NOT_BE_SET = 146;


  {
    ER_RESULT_COULD_NOT_BE_SET,
        "Result kunde inte s\u00e4ttas"},


   /** No output specified   */
  //public static final int ER_NO_OUTPUT_SPECIFIED = 147;


  {
    ER_NO_OUTPUT_SPECIFIED,
        "Ingen utdata angiven"},


   /** Can't transform to a Result of type   */
  //public static final int ER_CANNOT_TRANSFORM_TO_RESULT_TYPE = 148;


  {
    ER_CANNOT_TRANSFORM_TO_RESULT_TYPE,
        "Kan inte omvandla till en Result av typ {0}"},


   /** Can't transform to a Source of type   */
  //public static final int ER_CANNOT_TRANSFORM_SOURCE_TYPE = 149;


  {
    ER_CANNOT_TRANSFORM_SOURCE_TYPE,
        "Kan inte omvandla en Source av typ {0}"},


   /** Null content handler  */
  //public static final int ER_NULL_CONTENT_HANDLER = 150;


  {
    ER_NULL_CONTENT_HANDLER,
        "Inneh\u00e5llshanterare med v\u00e4rde null"},


   /** Null error handler  */
  //public static final int ER_NULL_ERROR_HANDLER = 151;


  {
    ER_NULL_ERROR_HANDLER,
        "Felhanterare med v\u00e4rde null"},


   /** parse can not be called if the ContentHandler has not been set */
  //public static final int ER_CANNOT_CALL_PARSE = 152;


  {
    ER_CANNOT_CALL_PARSE,
        "parse kan inte anropas om ContentHandler inte har satts"},


   /**  No parent for filter */
  //public static final int ER_NO_PARENT_FOR_FILTER = 153;


  {
    ER_NO_PARENT_FOR_FILTER,
        "Ingen f\u00f6r\u00e4lder till filter"},



   /**  No stylesheet found in: {0}, media */
  //public static final int ER_NO_STYLESHEET_IN_MEDIA = 154;


  {
    ER_NO_STYLESHEET_IN_MEDIA,
         "Stylesheet saknas i: {0}, media= {1}"},


   /**  No xml-stylesheet PI found in */
  //public static final int ER_NO_STYLESHEET_PI = 155;


  {
    ER_NO_STYLESHEET_PI,
         "xml-stylesheet PI saknas i: {0}"},


   /**  Not supported  */
  //public static final int ER_NOT_SUPPORTED = 171;


  {
    ER_NOT_SUPPORTED,
       "Underst\u00f6ds inte: {0}"},


   /**  Value for property {0} should be a Boolean instance  */
  //public static final int ER_PROPERTY_VALUE_BOOLEAN = 177;


  {
    ER_PROPERTY_VALUE_BOOLEAN,
       "V\u00e4rde p\u00e5 egenskap {0} borde vara en Boolesk instans"},


   /* This key/message changed ,NEED ER_COULD_NOT_FIND_EXTERN_SCRIPT: Pending,Ramesh */

   /** src attribute not yet supported for  */
  //public static final int ER_SRC_ATTRIB_NOT_SUPPORTED = 195;


  {
    "ER_SRC_ATTRIB_NOT_SUPPORTED",
       "src-attributet underst\u00f6ds \u00e4nnu inte f\u00f6r {0}"},


  /** The resource [] could not be found     */
  //public static final int ER_RESOURCE_COULD_NOT_FIND = 196;


  {
    ER_RESOURCE_COULD_NOT_FIND,
        "Resursen [ {0} ] saknas. \n {1}"},


   /** output property not recognized:  */
  //public static final int ER_OUTPUT_PROPERTY_NOT_RECOGNIZED = 197;


  {
    ER_OUTPUT_PROPERTY_NOT_RECOGNIZED,
        "Utdata-egenskap k\u00e4nns inte igen: {0}"},


   /** Failed creating ElemLiteralResult instance   */
  //public static final int ER_FAILED_CREATING_ELEMLITRSLT = 203;


  {
    ER_FAILED_CREATING_ELEMLITRSLT,
        "Kunde inte skapa instans av ElemLiteralResult"},


   // Earlier (JDK 1.4 XALAN 2.2-D11) at key code '204' the key name was ER_PRIORITY_NOT_PARSABLE
   // In latest Xalan code base key name is  ER_VALUE_SHOULD_BE_NUMBER. This should also be taken care
   //in locale specific files like XSLTErrorResources_de.java, XSLTErrorResources_fr.java etc.
   //NOTE: Not only the key name but message has also been changed. - nb.

   /** Priority value does not contain a parsable number   */
  //public static final int ER_VALUE_SHOULD_BE_NUMBER = 204;


  {
     ER_VALUE_SHOULD_BE_NUMBER,
         "V\u00e4rdet f\u00f6r {0} b\u00f6r inneh\u00e5lla en siffra som inte kan tolkas"},


   /**  Value for {0} should equal 'yes' or 'no'   */
  //public static final int ER_VALUE_SHOULD_EQUAL = 205;


  {
    ER_VALUE_SHOULD_EQUAL,
        "V\u00e4rde p\u00e5 {0} borde motsvara ja eller nej"},


   /**  Failed calling {0} method   */
  //public static final int ER_FAILED_CALLING_METHOD = 206;


  {
    ER_FAILED_CALLING_METHOD,
        " Kunde inte anropa metoden {0}"},


   /** Failed creating ElemLiteralResult instance   */
  //public static final int ER_FAILED_CREATING_ELEMTMPL = 207;


  {
    ER_FAILED_CREATING_ELEMTMPL,
        "Kunde inte skapa instans av ElemTemplateElement"},


   /**  Characters are not allowed at this point in the document   */
  //public static final int ER_CHARS_NOT_ALLOWED = 208;


  {
    ER_CHARS_NOT_ALLOWED,
        "Tecken \u00e4r inte till\u00e5tna i dokumentet vid den h\u00e4r tidpunkten"},


  /**  attribute is not allowed on the element   */
  //public static final int ER_ATTR_NOT_ALLOWED = 209;


  {
    ER_ATTR_NOT_ALLOWED,
        "Attributet \"{0}\" \u00e4r inte till\u00e5ten i det {1} elementet!"},


  /**  Bad value    */
  //public static final int ER_BAD_VALUE = 211;


  {
    ER_BAD_VALUE,
     "{0} d\u00e5ligt v\u00e4rde {1} "},


  /**  attribute value not found   */
  //public static final int ER_ATTRIB_VALUE_NOT_FOUND = 212;


  {
    ER_ATTRIB_VALUE_NOT_FOUND,
     "Attributet {0} saknas "},


  /**  attribute value not recognized    */
  //public static final int ER_ATTRIB_VALUE_NOT_RECOGNIZED = 213;


  {
    ER_ATTRIB_VALUE_NOT_RECOGNIZED,
     "Attributv\u00e4rdet {0} k\u00e4nns inte igen "},


  /** Attempting to generate a namespace prefix with a null URI   */
  //public static final int ER_NULL_URI_NAMESPACE = 216;


  {
    ER_NULL_URI_NAMESPACE,
     "F\u00f6rs\u00f6ker generera ett namnomr\u00e5desprefix med en null-URI"},


  // Following are the new ERROR keys added in XALAN code base after Jdk 1.4 (Xalan 2.2-D11)

  /** Attempting to generate a namespace prefix with a null URI   */
  //public static final int ER_NUMBER_TOO_BIG = 217;


  {
    ER_NUMBER_TOO_BIG,
     "F\u00f6rs\u00f6ker formatera en siffra som \u00e4r st\u00f6rre \u00e4n det st\u00f6rsta l\u00e5nga heltalet"},


//ER_CANNOT_FIND_SAX1_DRIVER

  //public static final int  ER_CANNOT_FIND_SAX1_DRIVER = 218;


  {
    ER_CANNOT_FIND_SAX1_DRIVER,
     "Det g\u00e5r inte att hitta SAX1-drivrutinen klass {0}"},


//ER_SAX1_DRIVER_NOT_LOADED
  //public static final int  ER_SAX1_DRIVER_NOT_LOADED = 219;


  {
    ER_SAX1_DRIVER_NOT_LOADED,
     "SAX1-drivrutinen klass {0} hittades men kan inte laddas"},


//ER_SAX1_DRIVER_NOT_INSTANTIATED
  //public static final int  ER_SAX1_DRIVER_NOT_INSTANTIATED = 220 ;


  {
    ER_SAX1_DRIVER_NOT_INSTANTIATED,
     "SAX1-drivrutinen klass {0} hittades men kan inte instansieras"},



// ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER
  //public static final int ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER = 221;


  {
    ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER,
     "SAX1-drivrutinen klass {0} implementerar inte org.xml.sax.Parser"},


// ER_PARSER_PROPERTY_NOT_SPECIFIED
  //public static final int  ER_PARSER_PROPERTY_NOT_SPECIFIED = 222;


  {
    ER_PARSER_PROPERTY_NOT_SPECIFIED,
     "Systemegenskapen org.xml.sax.parser \u00e4r inte angiven"},


//ER_PARSER_ARG_CANNOT_BE_NULL
  //public static final int  ER_PARSER_ARG_CANNOT_BE_NULL = 223 ;


  {
    ER_PARSER_ARG_CANNOT_BE_NULL,
     "Tolkningsargumentet f\u00e5r inte vara null"},



// ER_FEATURE
  //public static final int  ER_FEATURE = 224;


  {
    ER_FEATURE,
     "Funktion:a {0}"},



// ER_PROPERTY
  //public static final int ER_PROPERTY = 225 ;


  {
    ER_PROPERTY,
     "Egenskap:a {0}"},


// ER_NULL_ENTITY_RESOLVER
  //public static final int ER_NULL_ENTITY_RESOLVER  = 226;


  {
    ER_NULL_ENTITY_RESOLVER,
     "Nullenhetsl\u00f6sare"},


// ER_NULL_DTD_HANDLER
  //public static final int  ER_NULL_DTD_HANDLER = 227 ;


  {
    ER_NULL_DTD_HANDLER,
     "Null-DTD-hanterare"},


// No Driver Name Specified!
  //public static final int ER_NO_DRIVER_NAME_SPECIFIED = 228;

  {
    ER_NO_DRIVER_NAME_SPECIFIED,
     "Inget drivrutinsnamn \u00e4r angett!"},



// No URL Specified!
  //public static final int ER_NO_URL_SPECIFIED = 229;

  {
    ER_NO_URL_SPECIFIED,
     "Ingen URL har angetts!"},



// Pool size is less than 1!
  //public static final int ER_POOLSIZE_LESS_THAN_ONE = 230;

  {
    ER_POOLSIZE_LESS_THAN_ONE,
     "Poolstorleken \u00e4r mindre \u00e4n 1!"},



// Invalid Driver Name Specified!
  //public static final int ER_INVALID_DRIVER_NAME = 231;

  {
    ER_INVALID_DRIVER_NAME,
     "Ett ogiltigt drivrutinsnamn har angetts!"},




// ErrorListener
  //public static final int ER_ERRORLISTENER = 232;

  {
    ER_ERRORLISTENER,
     "ErrorListener"},



// Programmer's error! expr has no ElemTemplateElement parent!
  //public static final int ER_ASSERT_NO_TEMPLATE_PARENT = 233;

  {
    ER_ASSERT_NO_TEMPLATE_PARENT,
     "Programmerarfel! expr har inget \u00f6verordnat ElemTemplateElement!"},



// Programmer's assertion in RundundentExprEliminator: {0}
  //public static final int ER_ASSERT_REDUNDENT_EXPR_ELIMINATOR = 234;

  {
    ER_ASSERT_REDUNDENT_EXPR_ELIMINATOR,
     "Programmerarkontroll i RundundentExprEliminator: {0}"},


  // {0}is not allowed in this position in the stylesheet!
  //public static final int ER_NOT_ALLOWED_IN_POSITION = 237;

  {
    ER_NOT_ALLOWED_IN_POSITION,
     "{0} \u00e4r inte till\u00e5ten i denna position i formatmallen!"},


  // Non-whitespace text is not allowed in this position in the stylesheet!
  //public static final int ER_NONWHITESPACE_NOT_ALLOWED_IN_POSITION = 238;

  {
    ER_NONWHITESPACE_NOT_ALLOWED_IN_POSITION,
     "Text utan blanksteg \u00e4r inte till\u00e5ten i denna position i formatmallen!"},


  // This code is shared with warning codes.
  // Illegal value: {1} used for CHAR attribute: {0}.  An attribute of type CHAR must be only 1 character!
  //public static final int INVALID_TCHAR = 239;
  // SystemId Unknown

  {
    INVALID_TCHAR,
     "Ogiltigt v\u00e4rde: {1} anv\u00e4nds f\u00f6r CHAR-attributet: {0}.  Ett attribut av CHAR-typ f\u00e5r bara ha 1 tecken!"},


    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "QNAME" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value and {0} is the attribute name.
    // INVALID_QNAME

  //The following codes are shared with the warning codes...
  // Illegal value: {1} used for QNAME attribute: {0}
  //public static final int INVALID_QNAME = 242;

  {
    INVALID_QNAME,
     "Ogiltigt v\u00e4rde:a {1} anv\u00e4nds f\u00f6r QNAME-attributet:a {0}"},


    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "ENUM" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value, {0} is the attribute name, and {2} is a list of valid
    // values.
    // INVALID_ENUM

  // Illegal value:a {1} used for ENUM attribute:a {0}.  Valid values are:a {2}.
  //public static final int INVALID_ENUM = 243;

  {
    INVALID_ENUM,
     "Ogiltigt v\u00e4rde:a  {1} anv\u00e4nds f\u00f6r ENUM-attributet:a {0}.  Giltiga v\u00e4rden \u00e4r:a {2}."},


// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NMTOKEN" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
// INVALID_NMTOKEN

  // Illegal value:a {1} used for NMTOKEN attribute:a {0}.
  //public static final int INVALID_NMTOKEN = 244;

  {
    INVALID_NMTOKEN,
     "Ogiltigt v\u00e4rde:a {1} anv\u00e4nds f\u00f6r NMTOKEN-attributet:a {0} "},


// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NCNAME" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
// INVALID_NCNAME

  // Illegal value:a {1} used for NCNAME attribute:a {0}.
  //public static final int INVALID_NCNAME = 245;

  {
    INVALID_NCNAME,
     "Ogiltigt v\u00e4rde:a {1} anv\u00e4nds f\u00f6r NCNAME-attributet:a {0} "},


// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "boolean" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
// INVALID_BOOLEAN

  // Illegal value:a {1} used for boolean attribute:a {0}.
  //public static final int INVALID_BOOLEAN = 246;


  {
    INVALID_BOOLEAN,
     "Ogiltigt v\u00e4rde:a {1} anv\u00e4nds som Booleskt attribut:a {0} "},


// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "number" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
// INVALID_NUMBER

  // Illegal value:a {1} used for number attribute:a {0}.
  //public static final int INVALID_NUMBER = 247;

  {
    INVALID_NUMBER,
     "Ogiltigt v\u00e4rde:a {1} anv\u00e4nds som sifferattribut:a {0} "},



  // End of shared codes...

// Note to translators:  A "match pattern" is a special form of XPath expression
// that is used for matching patterns.  The substitution text is the name of
// a function.  The message indicates that when this function is referenced in
// a match pattern, its argument must be a string literal (or constant.)
// ER_ARG_LITERAL - new error message for bugzilla //5202

  // Argument to {0} in match pattern must be a literal.
  //public static final int ER_ARG_LITERAL             = 248;

  {
    ER_ARG_LITERAL,
     "Argument f\u00f6r {0} i matchningsm\u00f6nstret m\u00e5ste vara literalt."},


// Note to translators:  The following message indicates that two definitions of
// a variable.  A "global variable" is a variable that is accessible everywher
// in the stylesheet.
// ER_DUPLICATE_GLOBAL_VAR - new error message for bugzilla #790

  // Duplicate global variable declaration.
  //public static final int ER_DUPLICATE_GLOBAL_VAR    = 249;

  {
    ER_DUPLICATE_GLOBAL_VAR,
     "Dubbel deklaration av global variabel."},



// Note to translators:  The following message indicates that two definitions of
// a variable were encountered.
// ER_DUPLICATE_VAR - new error message for bugzilla #790

  // Duplicate variable declaration.
  //public static final int ER_DUPLICATE_VAR           = 250;

  {
    ER_DUPLICATE_VAR,
     "Dubbel variabeldeklaration."},


    // Note to translators:  "xsl:template, "name" and "match" are XSLT keywords
    // which must not be translated.
    // ER_TEMPLATE_NAME_MATCH - new error message for bugzilla #789

  // xsl:template must have a name or match attribute (or both)
  //public static final int ER_TEMPLATE_NAME_MATCH     = 251;

  {
    ER_TEMPLATE_NAME_MATCH,
     "xsl: en mall m\u00e5ste ha ett namn och ett matchningsattribut (eller b\u00e5de och)"},


    // Note to translators:  "exclude-result-prefixes" is an XSLT keyword which
    // should not be translated.  The message indicates that a namespace prefix
    // encountered as part of the value of the exclude-result-prefixes attribute
    // was in error.
    // ER_INVALID_PREFIX - new error message for bugzilla #788

  // Prefix in exclude-result-prefixes is not valid:a {0}
  //public static final int ER_INVALID_PREFIX          = 252;

  {
    ER_INVALID_PREFIX,
     "Prefix i exclude-result-prefixes \u00e4r ogiltigt:a {0}"},


    // Note to translators:  An "attribute set" is a set of attributes that can be
    // added to an element in the output document as a group.  The message indicates
    // that there was a reference to an attribute set named {0} that was never
    // defined.
    // ER_NO_ATTRIB_SET - new error message for bugzilla #782

  // attribute-set named {0} does not exist
  //public static final int ER_NO_ATTRIB_SET           = 253;

  {
    ER_NO_ATTRIB_SET,
     "attributserien {0} finns inte"},


  // Warnings...

  /** WG_FOUND_CURLYBRACE          */
  //public static final int WG_FOUND_CURLYBRACE = 1;


  {
    WG_FOUND_CURLYBRACE,
      "Hittade '}' men ingen attributmall \u00e4r \u00f6ppen!"},


  /** WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR          */
  //public static final int WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR = 2;


  {
    WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR,
      "Varning: r\u00e4knarattribut matchar inte en f\u00f6rf\u00e4der in xsl:number! Target = {0}"},


  /** WG_EXPR_ATTRIB_CHANGED_TO_SELECT          */
  //public static final int WG_EXPR_ATTRIB_CHANGED_TO_SELECT = 3;


  {
    WG_EXPR_ATTRIB_CHANGED_TO_SELECT,
      "Gammal syntax: Namnet p\u00e5  'expr'-attributet har \u00e4ndrats till 'select'."},


  /** WG_NO_LOCALE_IN_FORMATNUMBER          */
  //public static final int WG_NO_LOCALE_IN_FORMATNUMBER = 4;


  {
    WG_NO_LOCALE_IN_FORMATNUMBER,
      "Xalan hanterar \u00e4nnu inte locale-namnet i funktionen format-number."},


  /** WG_LOCALE_NOT_FOUND          */
  //public static final int WG_LOCALE_NOT_FOUND = 5;


  {
    WG_LOCALE_NOT_FOUND,
      "Varning: Hittade inte locale f\u00f6r xml:lang{0}"},


  /** WG_CANNOT_MAKE_URL_FROM          */
  //public static final int WG_CANNOT_MAKE_URL_FROM = 6;


  {
    WG_CANNOT_MAKE_URL_FROM,
      "Kan inte skapa URL fr\u00e5n: {0}"},


  /** WG_CANNOT_LOAD_REQUESTED_DOC          */
  //public static final int WG_CANNOT_LOAD_REQUESTED_DOC = 7;


  {
    WG_CANNOT_LOAD_REQUESTED_DOC,
      "Kan inte ladda beg\u00e4rd doc: {0}"},


  /** WG_CANNOT_FIND_COLLATOR          */
  //public static final int WG_CANNOT_FIND_COLLATOR = 8;


  {
    WG_CANNOT_FIND_COLLATOR,
      "Hittade inte Collator f\u00f6r <sort xml:lang={0}"},


  /** WG_FUNCTIONS_SHOULD_USE_URL          */
  //public static final int WG_FUNCTIONS_SHOULD_USE_URL = 9;


  {
    WG_FUNCTIONS_SHOULD_USE_URL,
      "Gammal syntax: Funktionsinstruktionen borde anv\u00e4nda en url av {0}"},


  /** WG_ENCODING_NOT_SUPPORTED_USING_UTF8          */
  //public static final int WG_ENCODING_NOT_SUPPORTED_USING_UTF8 = 10;


  {
    WG_ENCODING_NOT_SUPPORTED_USING_UTF8,
      "kodning underst\u00f6ds inte: {0}, anv\u00e4nder UTF-8"},


  /** WG_ENCODING_NOT_SUPPORTED_USING_JAVA          */
  //public static final int WG_ENCODING_NOT_SUPPORTED_USING_JAVA = 11;


  {
    WG_ENCODING_NOT_SUPPORTED_USING_JAVA,
      "kodning underst\u00f6ds inte: {0}, anv\u00e4nder Java {1}"},


  /** WG_SPECIFICITY_CONFLICTS          */
  //public static final int WG_SPECIFICITY_CONFLICTS = 12;


  {
    WG_SPECIFICITY_CONFLICTS,
      "Hittade specificitetskonflikter: {0} Senast hittade i stylesheet kommer att anv\u00e4ndas."},


  /** WG_PARSING_AND_PREPARING          */
  //public static final int WG_PARSING_AND_PREPARING = 13;


  {
    WG_PARSING_AND_PREPARING,
      "========= Tolkar och f\u00f6rbereder {0} =========="},


  /** WG_ATTR_TEMPLATE          */
  //public static final int WG_ATTR_TEMPLATE = 14;


  {
    WG_ATTR_TEMPLATE, "Attributmall, {0}"},


  /** WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESPACE          */
  //public static final int WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESPACE = 15;


  {
    WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESPACE,
      "Matcha konflikter mellan xsl:strip-space och xsl:preserve-space"},


  /** WG_ATTRIB_NOT_HANDLED          */
  //public static final int WG_ATTRIB_NOT_HANDLED = 16;


  {
    WG_ATTRIB_NOT_HANDLED,
      "Xalan hanterar \u00e4nnu inte attributet {0}!"},


  /** WG_NO_DECIMALFORMAT_DECLARATION          */
  //public static final int WG_NO_DECIMALFORMAT_DECLARATION = 17;


  {
    WG_NO_DECIMALFORMAT_DECLARATION,
      "Deklaration saknas f\u00f6r decimalformat: {0}"},


  /** WG_OLD_XSLT_NS          */
  //public static final int WG_OLD_XSLT_NS = 18;


  {
    WG_OLD_XSLT_NS, "XSLT-Namnrymd saknas eller \u00e4r inkorrekt "},


  /** WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED          */
  //public static final int WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED = 19;


  {
    WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED,
      "Endast en standarddeklaration av xsl:decimal-format \u00e4r till\u00e5ten."},


  /** WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE          */
  //public static final int WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE = 20;


  {
    WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE,
      "xsl:decimal-formatnamn m\u00e5ste vara unika. Namnet \"{0}\" har blivit duplicerat."},


  /** WG_ILLEGAL_ATTRIBUTE          */
  //public static final int WG_ILLEGAL_ATTRIBUTE = 21;


  {
    WG_ILLEGAL_ATTRIBUTE,
      "{0} har ett otill\u00e5tet attribut: {1}"},


  /** WG_COULD_NOT_RESOLVE_PREFIX          */
  //public static final int WG_COULD_NOT_RESOLVE_PREFIX = 22;


  {
    WG_COULD_NOT_RESOLVE_PREFIX,
      "Kan inte l\u00f6sa namnrymdsprefix: {0}. Noden kommer att ignoreras."},


  /** WG_STYLESHEET_REQUIRES_VERSION_ATTRIB          */
  //public static final int WG_STYLESHEET_REQUIRES_VERSION_ATTRIB = 23;


  {
    WG_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet m\u00e5ste ha ett 'version'-attribut!"},


  /** WG_ILLEGAL_ATTRIBUTE_NAME          */
  //public static final int WG_ILLEGAL_ATTRIBUTE_NAME = 24;


  {
    WG_ILLEGAL_ATTRIBUTE_NAME,
      "Otill\u00e5tet attributnamn: {0}"},


  /** WG_ILLEGAL_ATTRIBUTE_VALUE          */
  //public static final int WG_ILLEGAL_ATTRIBUTE_VALUE = 25;


  {
    WG_ILLEGAL_ATTRIBUTE_VALUE,
      "Ogiltigt v\u00e4rde anv\u00e4nt f\u00f6r attribut {0}: {1}"},


  /** WG_EMPTY_SECOND_ARG          */
  //public static final int WG_EMPTY_SECOND_ARG = 26;


  {
    WG_EMPTY_SECOND_ARG,
      "Den resulterande nodm\u00e4ngden fr\u00e5n dokumentfunktions andra argument \u00e4r tomt. Det f\u00f6rsta argumentet kommer att anv\u00e4ndas."},


  // Following are the new WARNING keys added in XALAN code base after Jdk 1.4 (Xalan 2.2-D11)

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.
    // WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML


  /** WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML          */
  //public static final int WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML = 27;

  {
     WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "V\u00e4rdet p\u00e5 attributet 'name' i xsl:processing-instruction f\u00e5r inte vara 'xml'"},


    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.  "NCName" is an XML data-type and must not be
    // translated.
    // WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME

  /** WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME          */
  //public static final int WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME = 28;

  {
     WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "V\u00e4rdet p\u00e5 attributet  ''name'' i xsl:processing-instruction m\u00e5ste vara ett giltigt NCName:a {0}"},


    // Note to translators:  This message is reported if the stylesheet that is
    // being processed attempted to construct an XML document with an attribute in a
    // place other than on an element.  The substitution text specifies the name of
    // the attribute.
    // WG_ILLEGAL_ATTRIBUTE_POSITION

  /** WG_ILLEGAL_ATTRIBUTE_POSITION         */
  //public static final int WG_ILLEGAL_ATTRIBUTE_POSITION = 29;

  {
    WG_ILLEGAL_ATTRIBUTE_POSITION,
      "Det g\u00e5r inte att l\u00e4gga till attributet {0} efter undernoder eller innan ett element produceras. Attributet ignoreras."},


    // WHY THERE IS A GAP B/W NUMBERS in the XSLTErrorResources properties file?

  // Other miscellaneous text used inside the code...
  { "ui_language", "sv"},
  { "help_language", "sv"},
  { "language", "sv"},
    { "BAD_CODE",
      "Parameter till createMessage ligger utanf\u00f6r till\u00e5tet intervall"},
    { "FORMAT_FAILED",
      "Undantag utl\u00f6st vid messageFormat-anrop"},
    { "version", ">>>>>>> Xalan Version"},
    { "version2", "<<<<<<<"},
    { "yes", "ja"},
    { "line",  "Rad #"},
    { "column", "Kolumn #"},
    { "xsldone", "XSLProcessor: f\u00e4rdig"},
    { "xslProc_option", "Xalan-J kommando linje Process klass alternativ:"},
    { "optionIN", "    -IN inputXMLURL"},
    { "optionXSL", "   [-XSL XSLTransformationURL]"},
    { "optionOUT", "   [-OUT utdataFilnamn]"},
    { "optionLXCIN", "   [-LXCIN kompileratStylesheetFilnameIn]"},
    { "optionLXCOUT", "   [-LXCOUT kompileratStylesheetFilenameUt]"},
    { "optionPARSER",
      "   [-PARSER fullt kvalificerat klassnamn eller tolkf\u00f6rbindelse]"},
    { "optionE", "   [-E (Ut\u00f6ka inte enhetsreferenser)]"},
    { "optionV", "   [-E (Ut\u00f6ka inte enhetsreferenser)]"},
    { "optionQC",
      "   [-QC (Tysta M\u00f6nsterkonfliktvarningar)]"},
    { "optionQ", "   [-Q  (Tyst Tillst\u00e5nd)]"},
    { "optionLF",
      "   [-LF (Anv\u00e4nd radframmatning enbart p\u00e5 utdata {standard \u00e4r CR/LF})]"},
    { "optionCR",
      "   [-CR (Anv\u00e4nd vagnretur enbart p\u00e5 utdata {standard \u00e4r CR/LF})]"},
    { "optionESCAPE",
      "   [-ESCAPE (Vilka tecken \u00e4r skiftningstecken {standard \u00e4r <>&\"\'\\r\\n}]"},
    { "optionINDENT",
      "   [-INDENT (Best\u00e4m antal blanksteg f\u00f6r att tabulera {standard \u00e4r 0})]"},
    { "optionTT",
      "   [-TT (Sp\u00e5ra mallarna allt eftersom de blir anropade.)]"},
    { "optionTG",
      "   [-TG (Sp\u00e5ra varje generationsh\u00e4ndelse.)]"},
    { "optionTS", "   [-TS (Sp\u00e5ra varje valh\u00e4ndelse.)]"},
    { "optionTTC",
      "   [-TTC (Sp\u00e5ra mallbarnen allt eftersom de blir behandlade.)]"},
    { "optionTCLASS",
      "   [-TCLASS (TraceListener-klass f\u00f6r sp\u00e5rningsanslutningar.)]"},
    { "optionVALIDATE",
      "   [-VALIDATE (S\u00e4tt om validering ska ske.  Standard \u00e4r att validering \u00e4r avst\u00e4ngd)]"},
    { "optionEDUMP",
      "   [-EDUMP {valfritt filnamn) (G\u00f6r stackdump vid fel.)]"},
    { "optionXML",
      "   [-XML (Anv\u00e4nd XML-formaterare och l\u00e4gg till XML-huvud.)]"},
    { "optionTEXT",
      "   [-XML (Anv\u00e4nd enkel Text-formaterare.)]"},
    { "optionHTML", "   [-HTML (Anv\u00e4nd HTML-formaterare)]"},
    { "optionPARAM",
      "   [-PARAM namn uttryck (S\u00e4tt en stylesheet-parameter)]"},
    { "noParsermsg1", "XSL-Process misslyckades."},
    { "noParsermsg2", "** Hittade inte tolk **"},
    { "noParsermsg3", "V\u00e4nligen kontrollera din classpath"},
    { "noParsermsg4",
      "Om du inte har IBMs XML-Tolk f\u00f6r Java, kan du ladda ner den fr\u00e5n"},
    { "noParsermsg5",
      "IBM's AlphaWorks: http://www.alphaworks.ibm.com/formula/xml"},
    {  "optionURIRESOLVER",
    "   [-URIRESOLVER fullst\u00e4ndigt klassnamn (URIResolver som ska anv\u00e4ndas f\u00f6r att l\u00f6sa URI-er)]"},
    { "optionENTITYRESOLVER",
    "   [-ENTITYRESOLVER fullst\u00e4ndigt klassnamn (EntityResolver som ska anv\u00e4ndas f\u00f6r att l\u00f6sa enheter)]"},
    {  "optionCONTENTHANDLER",
    "   [-CONTENTRESOLVER fullst\u00e4ndigt klassnamn (ContentHandler som ska anv\u00e4ndas f\u00f6r att serialisera utdata)]"},
    { "optionLINENUMBERS", "   [-L anv\u00e4nd radnummer i k\u00e4lldokument]"},

    //Following are the new options added in XSLTErrorResources.properties files after Jdk 1.4 (Xalan 2.2-D11)


    { "optionMEDIA",
    " [-MEDIA mediaType (anv\u00e4nd medieattribut f\u00f6r att hitta en formatmall som \u00e4r associerad med ett dokument.)]"},
    { "optionFLAVOR",
    " [-FLAVOR flavorName (Anv\u00e4nd s2s=SAX eller d2d=DOM f\u00f6r transformationen.)] "}, // Added by sboag/scurcuru; experimental
    { "optionDIAG",
    " [-DIAG (Skriv ut totala transformationer, millisekunder.)]"},
    { "optionINCREMENTAL",
    " [-INCREMENTAL (beg\u00e4r inkrementell DTM-konstruktion genom att ange http://xml.apache.org/xalan/features/incremental true.)]"},
    { "optionNOOPTIMIMIZE",
    " [-NOOPTIMIMIZE (beg\u00e4r ingen formatmallsoptimering genom att ange http://xml.apache.org/xalan/features/optimize false.)]"},
    { "optionRL",
     " [-RL recursionlimit (kontrollera numerisk gr\u00e4ns p\u00e5 formatmallens rekursionsdjup.)]"},
    { "optionXO",
    " [-XO [transletName] (tilldela namnet till genererad translet)]"},
    { "optionXD",
    " [-XD destinationDirectory (ange m\u00e5lkatalog f\u00f6r translet)]"},
    { "optionXJ",
    " [-XJ jarfile (paketerar transletklasserna i en jar-fil med namnet <jarfile>)]"},
    { "optionXP",
    " [-XP-paket (anger ett paketnamnsprefix f\u00f6r alla genererade transletklasser)]"}


  };
  }


  /** String for use when a bad error code was encountered.    */
  public static final String BAD_CODE = "D\u00c5LIG_KOD";

  /** String for use when formatting of the error string failed.   */
  public static final String FORMAT_FAILED = "FORMATERING_MISSLYCKADES";

  /** General error string.   */
  public static final String ERROR_STRING = "#fel";

  /** String to prepend to error messages.  */
  public static final String ERROR_HEADER = "Fel: ";

  /** String to prepend to warning messages.    */
  public static final String WARNING_HEADER = "Varning: ";

  /** String to specify the XSLT module.  */
  public static final String XSL_HEADER = "XSLT ";

  /** String to specify the XML parser module.  */
  public static final String XML_HEADER = "XML ";

  /** I don't think this is used any more.
   * @deprecated  */
  public static final String QUERY_HEADER = "M\u00d6NSTER ";

}
