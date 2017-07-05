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
 * $Id: XSLTErrorResources_ko.java,v 1.2.4.1 2005/09/13 11:03:57 pvedula Exp $
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


//  public static final int ER_NO_CURLYBRACE = 1;
  /*
   * Now fill in the message text.
   * Then fill in the message text for that message code in the
   * array. Use the new error code as the index into the array.
   */

  // Error messages...

  /** Get the lookup table for error messages.
   *
   * @return The int to message lookup table.
   */
  public Object[][] getContents()
  {
    return new Object[][] {

  /** Error message ID that has a null message, but takes in a single object.    */
  {"ER0000" , "{0}" },


  /** ER_NO_CURLYBRACE          */

//  public static final int ER_NO_CURLYBRACE = 1;

    { ER_NO_CURLYBRACE,
      "\uc624\ub958: \ud45c\ud604\uc2dd\uc5d0 '{'\uac00 \uc62c \uc218 \uc5c6\uc74c"},

  /** ER_ILLEGAL_ATTRIBUTE          */
// public static final int ER_ILLEGAL_ATTRIBUTE = 2;

    { ER_ILLEGAL_ATTRIBUTE ,
     "{0}\uc5d0 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc740 \uc18d\uc131 {1}\uc774(\uac00) \uc788\uc74c"},

  /** ER_NULL_SOURCENODE_APPLYIMPORTS          */
//  public static final int ER_NULL_SOURCENODE_APPLYIMPORTS = 3;

  {ER_NULL_SOURCENODE_APPLYIMPORTS ,
      "xsl:apply-imports\uc5d0\uc11c sourceNode\uac00 \ub110(null)\uc785\ub2c8\ub2e4!"},

  /** ER_CANNOT_ADD          */
 // public static final int ER_CANNOT_ADD = 4;

  {ER_CANNOT_ADD,
      "{1}\uc5d0 {0}\uc744(\ub97c) \ucd94\uac00\ud560 \uc218 \uc5c6\uc74c"},


  /** ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES          */
//  public static final int ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES = 5;


    { ER_NULL_SOURCENODE_HANDLEAPPLYTEMPLATES,
      "handleApplyTemplatesInstruction\uc5d0\uc11c sourceNode\uac00 \ub110(null)\uc785\ub2c8\ub2e4!"},

  /** ER_NO_NAME_ATTRIB          */
//  public static final int ER_NO_NAME_ATTRIB = 6;


    { ER_NO_NAME_ATTRIB,
     "{0}\uc5d0 \uc774\ub984 \uc18d\uc131\uc774 \uc788\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

  /** ER_TEMPLATE_NOT_FOUND          */
  //public static final int ER_TEMPLATE_NOT_FOUND = 7;


    {ER_TEMPLATE_NOT_FOUND,
     "{0} \uc774\ub984\uc758 \ud15c\ud50c\ub9ac\ud2b8\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** ER_CANT_RESOLVE_NAME_AVT          */
  // public static final int ER_CANT_RESOLVE_NAME_AVT = 8;

    {ER_CANT_RESOLVE_NAME_AVT,
      "xsl:call-template\uc5d0 \uc788\ub294 \uc774\ub984 AVT\ub97c \ubd84\uc11d\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** ER_REQUIRES_ATTRIB          */
  //public static final int ER_REQUIRES_ATTRIB = 9;


    {ER_REQUIRES_ATTRIB,
     "{0}\uc740(\ub294) {1} \uc18d\uc131\uc744 \ud544\uc694\ub85c \ud568"},

  /** ER_MUST_HAVE_TEST_ATTRIB          */
 // public static final int ER_MUST_HAVE_TEST_ATTRIB = 10;


    { ER_MUST_HAVE_TEST_ATTRIB,
      "{0}\uc5d0 ''test'' \uc18d\uc131\uc774 \uc788\uc5b4\uc57c \ud569\ub2c8\ub2e4. "},

  /** ER_BAD_VAL_ON_LEVEL_ATTRIB          */
//  public static final int ER_BAD_VAL_ON_LEVEL_ATTRIB = 11;


    {ER_BAD_VAL_ON_LEVEL_ATTRIB,
      "{0} \ub808\ubca8 \uc18d\uc131\uc5d0 \uc798\ubabb\ub41c \uac12\uc774 \uc788\uc74c"},

  /** ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML          */
//  public static final int ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML = 12;


    {ER_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "\ucc98\ub9ac \uba85\ub839\uc5b4 \uc774\ub984\uc740 'xml'\uc774 \ub420 \uc218 \uc5c6\uc74c"},

  /** ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME          */
//  public static final int ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME = 13;


    { ER_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "\ucc98\ub9ac \uba85\ub839\uc5b4 \uc774\ub984\uc740 \uc62c\ubc14\ub978 NCName\uc774\uc5b4\uc57c \ud568: {0}"},

  /** ER_NEED_MATCH_ATTRIB          */
//  public static final int ER_NEED_MATCH_ATTRIB = 14;


    { ER_NEED_MATCH_ATTRIB,
      "{0}\uc5d0 \ubaa8\ub4dc\uac00 \uc788\uc73c\uba74 \uc77c\uce58 \uc18d\uc131\uc774 \uc788\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

  /** ER_NEED_NAME_OR_MATCH_ATTRIB          */
//  public static final int ER_NEED_NAME_OR_MATCH_ATTRIB = 15;


    { ER_NEED_NAME_OR_MATCH_ATTRIB,
      "{0}\uc5d0 \uc774\ub984 \ub610\ub294 \uc77c\uce58 \uc18d\uc131\uc774 \ud544\uc694\ud569\ub2c8\ub2e4."},

  /** ER_CANT_RESOLVE_NSPREFIX          */
//  public static final int ER_CANT_RESOLVE_NSPREFIX = 16;


    {ER_CANT_RESOLVE_NSPREFIX,
      "\uc774\ub984 \uacf5\uac04 \uc811\ub450\ubd80\ub97c \ubd84\uc11d\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4: {0}"},

  /** ER_ILLEGAL_VALUE          */
//  public static final int ER_ILLEGAL_VALUE = 17;


    { ER_ILLEGAL_VALUE,
     "xml:space\uc5d0 \uc798\ubabb\ub41c \uac12\uc774 \uc788\uc2b5\ub2c8\ub2e4: {0}"},

  /** ER_NO_OWNERDOC          */
//  public static final int ER_NO_OWNERDOC = 18;


    { ER_NO_OWNERDOC,
      "\ud558\uc704 \ub178\ub4dc\uc5d0 \uc18c\uc720\uc790 \ubb38\uc11c\uac00 \uc5c6\uc2b5\ub2c8\ub2e4!"},

  /** ER_ELEMTEMPLATEELEM_ERR          */
//  public static final int ER_ELEMTEMPLATEELEM_ERR = 19;


    { ER_ELEMTEMPLATEELEM_ERR,
     "ElemTemplateElement \uc624\ub958: {0}"},

  /** ER_NULL_CHILD          */
//  public static final int ER_NULL_CHILD = 20;


    { ER_NULL_CHILD,
     "\ub110(null) \ud558\uc704\ub97c \ucd94\uac00\ud558\ub824\uace0 \uc2dc\ub3c4 \uc911\uc785\ub2c8\ub2e4!"},

  /** ER_NEED_SELECT_ATTRIB          */
//  public static final int ER_NEED_SELECT_ATTRIB = 21;


    { ER_NEED_SELECT_ATTRIB,
     "{0}\uc5d0 \uc120\ud0dd\uc801 \uc18d\uc131\uc774 \ud544\uc694\ud569\ub2c8\ub2e4."},

  /** ER_NEED_TEST_ATTRIB          */
//  public static final int ER_NEED_TEST_ATTRIB = 22;


    { ER_NEED_TEST_ATTRIB ,
      "xsl:when\uc5d0 'test' \uc18d\uc131\uc774 \uc788\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

  /** ER_NEED_NAME_ATTRIB          */
//  public static final int ER_NEED_NAME_ATTRIB = 23;


    { ER_NEED_NAME_ATTRIB,
      "xsl:with-param\uc5d0 'name' \uc18d\uc131\uc774 \uc788\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

  /** ER_NO_CONTEXT_OWNERDOC          */
//  public static final int ER_NO_CONTEXT_OWNERDOC = 24;


    { ER_NO_CONTEXT_OWNERDOC,
      "\ubb38\ub9e5\uc5d0 \uc18c\uc720\uc790 \ubb38\uc11c\uac00 \uc5c6\uc2b5\ub2c8\ub2e4!"},

  /** ER_COULD_NOT_CREATE_XML_PROC_LIAISON          */
//  public static final int ER_COULD_NOT_CREATE_XML_PROC_LIAISON = 25;


    {ER_COULD_NOT_CREATE_XML_PROC_LIAISON,
      "XML TransformerFactory Liaison\uc744 \uc791\uc131\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4: {0}"},

  /** ER_PROCESS_NOT_SUCCESSFUL          */
//  public static final int ER_PROCESS_NOT_SUCCESSFUL = 26;


    {ER_PROCESS_NOT_SUCCESSFUL,
      "Xalan: \ud504\ub85c\uc138\uc2a4\uac00 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4."},

  /** ER_NOT_SUCCESSFUL          */
//  public static final int ER_NOT_SUCCESSFUL = 27;


    { ER_NOT_SUCCESSFUL,
     "Xalan:\uc774 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4."},

  /** ER_ENCODING_NOT_SUPPORTED          */
//  public static final int ER_ENCODING_NOT_SUPPORTED = 28;


    { ER_ENCODING_NOT_SUPPORTED,
     "\uc778\ucf54\ub529\uc774 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc74c: {0}"},

  /** ER_COULD_NOT_CREATE_TRACELISTENER          */
//  public static final int ER_COULD_NOT_CREATE_TRACELISTENER = 29;


    {ER_COULD_NOT_CREATE_TRACELISTENER,
      "TraceListener\ub97c \uc791\uc131\ud560 \uc218 \uc5c6\uc74c: {0}"},

  /** ER_KEY_REQUIRES_NAME_ATTRIB          */
//  public static final int ER_KEY_REQUIRES_NAME_ATTRIB = 30;


    {ER_KEY_REQUIRES_NAME_ATTRIB,
      "xsl:key\uc5d0 'name' \uc18d\uc131\uc774 \ud544\uc694\ud569\ub2c8\ub2e4!"},

  /** ER_KEY_REQUIRES_MATCH_ATTRIB          */
//  public static final int ER_KEY_REQUIRES_MATCH_ATTRIB = 31;


    { ER_KEY_REQUIRES_MATCH_ATTRIB,
      "xsl:key\uc5d0 'match' \uc18d\uc131\uc774 \ud544\uc694\ud569\ub2c8\ub2e4!"},

  /** ER_KEY_REQUIRES_USE_ATTRIB          */
//  public static final int ER_KEY_REQUIRES_USE_ATTRIB = 32;


    { ER_KEY_REQUIRES_USE_ATTRIB,
      "xsl:key\uc5d0 'use' \uc18d\uc131\uc774 \ud544\uc694\ud569\ub2c8\ub2e4!"},

  /** ER_REQUIRES_ELEMENTS_ATTRIB          */
//  public static final int ER_REQUIRES_ELEMENTS_ATTRIB = 33;


    { ER_REQUIRES_ELEMENTS_ATTRIB,
      "(StylesheetHandler) {0}\uc5d0 ''elements'' \uc18d\uc131\uc774 \ud544\uc694\ud569\ub2c8\ub2e4!"},

  /** ER_MISSING_PREFIX_ATTRIB          */
//  public static final int ER_MISSING_PREFIX_ATTRIB = 34;


    { ER_MISSING_PREFIX_ATTRIB,
      "(StylesheetHandler) {0} \uc18d\uc131 ''prefix''\uac00 \ub204\ub77d\ub418\uc5c8\uc74c"},

  /** ER_BAD_STYLESHEET_URL          */
//  public static final int ER_BAD_STYLESHEET_URL = 35;


    { ER_BAD_STYLESHEET_URL,
     "\uc2a4\ud0c0\uc77c \uc2dc\ud2b8 URL\uc774 \uc798\ubabb\ub418\uc5c8\uc74c: {0}"},

  /** ER_FILE_NOT_FOUND          */
//  public static final int ER_FILE_NOT_FOUND = 36;


    { ER_FILE_NOT_FOUND,
     "\uc2a4\ud0c0\uc77c \uc2dc\ud2b8 \ud30c\uc77c\uc744 \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4: {0}"},

  /** ER_IOEXCEPTION          */
//  public static final int ER_IOEXCEPTION = 37;


    { ER_IOEXCEPTION,
      "\uc2a4\ud0c0\uc77c \uc2dc\ud2b8 \ud30c\uc77c\uc5d0 \uc785\ucd9c\ub825 \uc608\uc678\uac00 \uc788\uc2b5\ub2c8\ub2e4: {0}"},

  /** ER_NO_HREF_ATTRIB          */
//  public static final int ER_NO_HREF_ATTRIB = 38;


    { ER_NO_HREF_ATTRIB,
      "(StylesheetHandler) {0}\uc758 href \uc18d\uc131\uc744 \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** ER_STYLESHEET_INCLUDES_ITSELF          */
//  public static final int ER_STYLESHEET_INCLUDES_ITSELF = 39;


    { ER_STYLESHEET_INCLUDES_ITSELF,
      "(StylesheetHandler) {0}\uc774(\uac00) \uc9c1\uc811 \ub610\ub294 \uac04\uc811\uc73c\ub85c \uc790\uc2e0\uc744 \ud3ec\ud568\ud569\ub2c8\ub2e4!"},

  /** ER_PROCESSINCLUDE_ERROR          */
//  public static final int ER_PROCESSINCLUDE_ERROR = 40;


    { ER_PROCESSINCLUDE_ERROR,
      "StylesheetHandler.processInclude \uc624\ub958, {0}"},

  /** ER_MISSING_LANG_ATTRIB          */
//  public static final int ER_MISSING_LANG_ATTRIB = 41;


    { ER_MISSING_LANG_ATTRIB,
      "(StylesheetHandler) {0} \uc18d\uc131 ''lang''\uc774 \ub204\ub77d\ub418\uc5c8\uc74c"},

  /** ER_MISSING_CONTAINER_ELEMENT_COMPONENT          */
//  public static final int ER_MISSING_CONTAINER_ELEMENT_COMPONENT = 42;

    { ER_MISSING_CONTAINER_ELEMENT_COMPONENT,
      "(StylesheetHandler) {0} \uc694\uc18c\uac00 \uc798\ubabb\ub41c \uc704\uce58\uc5d0 \uc788\uc74c?? \ub204\ub77d\ub41c \ucee8\ud14c\uc774\ub108 \uc694\uc18c ''component''"},

  /** ER_CAN_ONLY_OUTPUT_TO_ELEMENT          */
//  public static final int ER_CAN_ONLY_OUTPUT_TO_ELEMENT = 43;

    { ER_CAN_ONLY_OUTPUT_TO_ELEMENT,
      "Element, DocumentFragment, Document \ub610\ub294 PrintWriter\ub85c\ub9cc \ucd9c\ub825\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4."},

  /** ER_PROCESS_ERROR          */
//  public static final int ER_PROCESS_ERROR = 44;

    { ER_PROCESS_ERROR,
     "StylesheetRoot.process \uc624\ub958"},

  /** ER_UNIMPLNODE_ERROR          */
//  public static final int ER_UNIMPLNODE_ERROR = 45;

    { ER_UNIMPLNODE_ERROR,
     "UnImplNode \uc624\ub958: {0}"},

  /** ER_NO_SELECT_EXPRESSION          */
//  public static final int ER_NO_SELECT_EXPRESSION = 46;

    { ER_NO_SELECT_EXPRESSION,
      "\uc624\ub958! xpath \uc120\ud0dd \ud45c\ud604\uc2dd(-select)\uc744 \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** ER_CANNOT_SERIALIZE_XSLPROCESSOR          */
//  public static final int ER_CANNOT_SERIALIZE_XSLPROCESSOR = 47;

    { ER_CANNOT_SERIALIZE_XSLPROCESSOR,
      "XSLProcessor\ub97c \uc9c1\ub82c\ud654\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4!"},

  /** ER_NO_INPUT_STYLESHEET          */
//  public static final int ER_NO_INPUT_STYLESHEET = 48;

    { ER_NO_INPUT_STYLESHEET,
      "\uc2a4\ud0c0\uc77c \uc2dc\ud2b8 \uc785\ub825\uc744 \uc9c0\uc815\ud558\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4!"},

  /** ER_FAILED_PROCESS_STYLESHEET          */
//  public static final int ER_FAILED_PROCESS_STYLESHEET = 49;

    { ER_FAILED_PROCESS_STYLESHEET,
      "\uc2a4\ud0c0\uc77c \uc2dc\ud2b8\ub97c \ucc98\ub9ac\ud558\ub294 \ub370 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4!"},

  /** ER_COULDNT_PARSE_DOC          */
//  public static final int ER_COULDNT_PARSE_DOC = 50;

    { ER_COULDNT_PARSE_DOC,
     "{0} \ubb38\uc11c\ub97c \uad6c\ubb38 \ubd84\uc11d\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4!"},

  /** ER_COULDNT_FIND_FRAGMENT          */
//  public static final int ER_COULDNT_FIND_FRAGMENT = 51;

    { ER_COULDNT_FIND_FRAGMENT,
     "\ub2e8\ud3b8\uc744 \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4: {0}"},

  /** ER_NODE_NOT_ELEMENT          */
 // public static final int ER_NODE_NOT_ELEMENT = 52;

    { ER_NODE_NOT_ELEMENT,
      "\ub2e8\ud3b8 ID\uac00 \uac00\ub9ac\ud0a4\ub294 \ub178\ub4dc\uac00 \uc694\uc18c\uac00 \uc544\ub2d9\ub2c8\ub2e4: {0}"},

  /** ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB          */
//  public static final int ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB = 53;

    { ER_FOREACH_NEED_MATCH_OR_NAME_ATTRIB,
      "for-each\uc5d0\ub294 \uc77c\uce58 \ub610\ub294 \uc774\ub984 \uc18d\uc131\uc774 \uc788\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

  /** ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB          */
//  public static final int ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB = 54;

    { ER_TEMPLATES_NEED_MATCH_OR_NAME_ATTRIB,
      "\ud15c\ud50c\ub9ac\ud2b8\uc5d0\ub294 \uc77c\uce58 \ub610\ub294 \uc774\ub984 \uc18d\uc131\uc774 \uc788\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

  /** ER_NO_CLONE_OF_DOCUMENT_FRAG          */
//  public static final int ER_NO_CLONE_OF_DOCUMENT_FRAG = 55;

    { ER_NO_CLONE_OF_DOCUMENT_FRAG,
      "\ubb38\uc11c \ub2e8\ud3b8\uc758 \ubcf5\uc81c\ubcf8\uc774 \uc5c6\uc2b5\ub2c8\ub2e4!"},

  /** ER_CANT_CREATE_ITEM          */
//  public static final int ER_CANT_CREATE_ITEM = 56;

    { ER_CANT_CREATE_ITEM,
      "\uacb0\uacfc \ud2b8\ub9ac\uc5d0 \ud56d\ubaa9\uc744 \uc791\uc131\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.: {0}"},

  /** ER_XMLSPACE_ILLEGAL_VALUE          */
//  public static final int ER_XMLSPACE_ILLEGAL_VALUE = 57;

    { ER_XMLSPACE_ILLEGAL_VALUE,
      "\uc6d0\ubcf8 XML\uc758 xml:space\uc5d0 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc740 \uac12\uc774 \uc788\uc2b5\ub2c8\ub2e4.: {0}"},

  /** ER_NO_XSLKEY_DECLARATION          */
//  public static final int ER_NO_XSLKEY_DECLARATION = 58;

    { ER_NO_XSLKEY_DECLARATION,
      "{0}\uc5d0 \ub300\ud55c xsl:key \uc120\uc5b8\uc774 \uc5c6\uc2b5\ub2c8\ub2e4!"},

  /** ER_CANT_CREATE_URL          */
//  public static final int ER_CANT_CREATE_URL = 59;

    { ER_CANT_CREATE_URL,
     "\uc624\ub958! url\uc744 \uc791\uc131\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.: {0}"},

  /** ER_XSLFUNCTIONS_UNSUPPORTED          */
//  public static final int ER_XSLFUNCTIONS_UNSUPPORTED = 60;

    { ER_XSLFUNCTIONS_UNSUPPORTED,
     "xsl:functions\uac00 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

  /** ER_PROCESSOR_ERROR          */
//  public static final int ER_PROCESSOR_ERROR = 61;

    { ER_PROCESSOR_ERROR,
     "XSLT TransformerFactory \uc624\ub958"},

  /** ER_NOT_ALLOWED_INSIDE_STYLESHEET          */
//  public static final int ER_NOT_ALLOWED_INSIDE_STYLESHEET = 62;

    { ER_NOT_ALLOWED_INSIDE_STYLESHEET,
      "(StylesheetHandler) \uc2a4\ud0c0\uc77c \uc2dc\ud2b8 \ub0b4\uc5d0 {0}\uc774(\uac00) \ud5c8\uc6a9\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!"},

  /** ER_RESULTNS_NOT_SUPPORTED          */
//  public static final int ER_RESULTNS_NOT_SUPPORTED = 63;

    { ER_RESULTNS_NOT_SUPPORTED,
      "result-ns\uac00 \ub354 \uc774\uc0c1 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!  \ub300\uc2e0 xsl:output\uc744 \uc0ac\uc6a9\ud558\uc2ed\uc2dc\uc624."},

  /** ER_DEFAULTSPACE_NOT_SUPPORTED          */
//  public static final int ER_DEFAULTSPACE_NOT_SUPPORTED = 64;

    { ER_DEFAULTSPACE_NOT_SUPPORTED,
      "default-space\uac00 \ub354 \uc774\uc0c1 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!  \ub300\uc2e0 xsl:strip-space \ub610\ub294 xsl:preserve-space\ub97c \uc0ac\uc6a9\ud558\uc2ed\uc2dc\uc624."},

  /** ER_INDENTRESULT_NOT_SUPPORTED          */
//  public static final int ER_INDENTRESULT_NOT_SUPPORTED = 65;

    { ER_INDENTRESULT_NOT_SUPPORTED,
      "indent-result\uac00 \ub354 \uc774\uc0c1 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!  \ub300\uc2e0 xsl:output\uc744 \uc0ac\uc6a9\ud558\uc2ed\uc2dc\uc624."},

  /** ER_ILLEGAL_ATTRIB          */
//  public static final int ER_ILLEGAL_ATTRIB = 66;

    { ER_ILLEGAL_ATTRIB,
      "(StylesheetHandler) {0}\uc5d0 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc740 \uc18d\uc131\uc774 \uc788\uc74c: {1}"},

  /** ER_UNKNOWN_XSL_ELEM          */
//  public static final int ER_UNKNOWN_XSL_ELEM = 67;

    { ER_UNKNOWN_XSL_ELEM,
     "\uc54c \uc218 \uc5c6\ub294 XSL \uc694\uc18c: {0}"},

  /** ER_BAD_XSLSORT_USE          */
//  public static final int ER_BAD_XSLSORT_USE = 68;

    { ER_BAD_XSLSORT_USE,
      "(StylesheetHandler) xsl:sort\ub294 xsl:apply-templates \ub610\ub294 xsl:for-each\uc640 \ud568\uaed8\ub9cc \uc0ac\uc6a9\ub420 \uc218 \uc788\uc2b5\ub2c8\ub2e4."},

  /** ER_MISPLACED_XSLWHEN          */
//  public static final int ER_MISPLACED_XSLWHEN = 69;

    { ER_MISPLACED_XSLWHEN,
      "(StylesheetHandler) xsl:when\uc774 \uc798\ubabb\ub41c \uc704\uce58\uc5d0 \ub193\uc5ec \uc788\uc2b5\ub2c8\ub2e4!"},

  /** ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE          */
//  public static final int ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE = 70;

    { ER_XSLWHEN_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:when\uc774 xsl:choose\uc758 \uc0c1\uc704\uc5d0 \uc788\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!"},

  /** ER_MISPLACED_XSLOTHERWISE          */
//  public static final int ER_MISPLACED_XSLOTHERWISE = 71;

    { ER_MISPLACED_XSLOTHERWISE,
      "(StylesheetHandler) xsl:otherwise\uac00 \uc798\ubabb\ub41c \uc704\uce58\uc5d0 \ub193\uc5ec \uc788\uc2b5\ub2c8\ub2e4!"},

  /** ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE          */
//  public static final int ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE = 72;

    { ER_XSLOTHERWISE_NOT_PARENTED_BY_XSLCHOOSE,
      "(StylesheetHandler) xsl:otherwise\uac00 xsl:choose\uc758 \uc0c1\uc704\uc5d0 \uc788\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!"},

  /** ER_NOT_ALLOWED_INSIDE_TEMPLATE          */
//  public static final int ER_NOT_ALLOWED_INSIDE_TEMPLATE = 73;

    { ER_NOT_ALLOWED_INSIDE_TEMPLATE,
      "(StylesheetHandler) \ud15c\ud50c\ub9ac\ud2b8 \ub0b4\uc5d0 {0}\uc774(\uac00) \ud5c8\uc6a9\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!"},

  /** ER_UNKNOWN_EXT_NS_PREFIX          */
//  public static final int ER_UNKNOWN_EXT_NS_PREFIX = 74;

    { ER_UNKNOWN_EXT_NS_PREFIX,
      "(StylesheetHandler) {0} \ud655\uc7a5\uc790 \uc774\ub984 \uacf5\uac04 \uc811\ub450\ubd80 {1}\uc744(\ub97c) \uc54c \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** ER_IMPORTS_AS_FIRST_ELEM          */
//  public static final int ER_IMPORTS_AS_FIRST_ELEM = 75;

    { ER_IMPORTS_AS_FIRST_ELEM,
      "(StylesheetHandler) \uac00\uc838\uc624\uae30\ub294 \uc2a4\ud0c0\uc77c \uc2dc\ud2b8\uc5d0\uc11c \uccab \ubc88\uc9f8 \uc694\uc18c\ub85c\ub9cc \ub098\ud0c0\ub0a0 \uc218 \uc788\uc2b5\ub2c8\ub2e4!"},

  /** ER_IMPORTING_ITSELF          */
//  public static final int ER_IMPORTING_ITSELF = 76;

    { ER_IMPORTING_ITSELF,
      "(StylesheetHandler) {0}\uc774(\uac00) \uc9c1\uc811 \ub610\ub294 \uac04\uc811\uc73c\ub85c \uc790\uc2e0\uc744 \uac00\uc838\uc635\ub2c8\ub2e4!"},

  /** ER_XMLSPACE_ILLEGAL_VAL          */
//  public static final int ER_XMLSPACE_ILLEGAL_VAL = 77;

    { ER_XMLSPACE_ILLEGAL_VAL,
      "(StylesheetHandler) xml:\uacf5\uac04\uc5d0 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc740 \uac12\uc774 \uc788\uc74c: {0}"},

  /** ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL          */
//  public static final int ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL = 78;

    { ER_PROCESSSTYLESHEET_NOT_SUCCESSFUL,
      "processStylesheet\uc5d0 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4!"},

  /** ER_SAX_EXCEPTION          */
//  public static final int ER_SAX_EXCEPTION = 79;

    { ER_SAX_EXCEPTION,
     "SAX \uc608\uc678"},

  /** ER_FUNCTION_NOT_SUPPORTED          */
//  public static final int ER_FUNCTION_NOT_SUPPORTED = 80;

    //{ ER_FUNCTION_NOT_SUPPORTED,
    // "Function not supported!"},

  /** ER_XSLT_ERROR          */
//  public static final int ER_XSLT_ERROR = 81;

    { ER_XSLT_ERROR,
     "XSLT \uc624\ub958"},

  /** ER_CURRENCY_SIGN_ILLEGAL          */
//  public static final int ER_CURRENCY_SIGN_ILLEGAL = 82;

    { ER_CURRENCY_SIGN_ILLEGAL,
      "\ud3ec\ub9f7 \ud328\ud134 \ubb38\uc790\uc5f4\uc5d0 \ud1b5\ud654 \ubd80\ud638\uac00 \ud5c8\uc6a9\ub418\uc9c0 \uc54a\uc74c"},

  /** ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM          */
//  public static final int ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM = 83;

    { ER_DOCUMENT_FUNCTION_INVALID_IN_STYLESHEET_DOM,
      "\uc2a4\ud0c0\uc77c \uc2dc\ud2b8 DOM\uc5d0\uc11c Document \ud568\uc218\uac00 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!"},

  /** ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER          */
//  public static final int ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER = 84;

    { ER_CANT_RESOLVE_PREFIX_OF_NON_PREFIX_RESOLVER,
      "\ube44 \uc811\ub450\ubd80 \ubd84\uc11d\uc790\uc758 \uc811\ub450\ubd80\ub97c \ubd84\uc11d\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4!"},

  /** ER_REDIRECT_COULDNT_GET_FILENAME          */
//  public static final int ER_REDIRECT_COULDNT_GET_FILENAME = 85;

    { ER_REDIRECT_COULDNT_GET_FILENAME,
      "Redirect extension: \ud30c\uc77c \uc774\ub984\uc744 \uac00\uc838\uc62c \uc218 \uc5c6\uc2b5\ub2c8\ub2e4. \ud30c\uc77c \ub610\ub294 \uc120\ud0dd\uc801 \uc18d\uc131\uc740 \uc62c\ubc14\ub978 \ubb38\uc790\uc5f4\uc744 \ub9ac\ud134\ud574\uc57c \ud569\ub2c8\ub2e4. "},

  /** ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT          */
//  public static final int ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT = 86;

    { ER_CANNOT_BUILD_FORMATTERLISTENER_IN_REDIRECT,
      "\uacbd\ub85c \uc7ac\uc9c0\uc815 \ud655\uc7a5\uc5d0 FormatterListener\ub97c \ube4c\ub4dc\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4!"},

  /** ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX          */
//  public static final int ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX = 87;

    { ER_INVALID_PREFIX_IN_EXCLUDERESULTPREFIX,
      "exclude-result-prefixes\uc5d0 \uc788\ub294 \uc811\ub450\ubd80\uac00 \uc62c\ubc14\ub974\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4: {0}"},

  /** ER_MISSING_NS_URI          */
//  public static final int ER_MISSING_NS_URI = 88;

    { ER_MISSING_NS_URI,
      "\uc9c0\uc815\ub41c \uc811\ub450\ubd80\uc758 \uc774\ub984 \uacf5\uac04 URI\uac00 \ub204\ub77d\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},

  /** ER_MISSING_ARG_FOR_OPTION          */
//  public static final int ER_MISSING_ARG_FOR_OPTION = 89;

    { ER_MISSING_ARG_FOR_OPTION,
      "\uc635\uc158\uc758 \uc778\uc218\uac00 \ub204\ub77d\ub418\uc5c8\uc2b5\ub2c8\ub2e4: {0}"},

  /** ER_INVALID_OPTION          */
//  public static final int ER_INVALID_OPTION = 90;

    { ER_INVALID_OPTION,
     "\uc798\ubabb\ub41c \uc635\uc158: {0}"},

  /** ER_MALFORMED_FORMAT_STRING          */
//  public static final int ER_MALFORMED_FORMAT_STRING = 91;

    { ER_MALFORMED_FORMAT_STRING,
     "\uc798\ubabb \ud615\uc2dd\ud654\ub41c \ud3ec\ub9f7 \ubb38\uc790\uc5f4: {0}"},

  /** ER_STYLESHEET_REQUIRES_VERSION_ATTRIB          */
//  public static final int ER_STYLESHEET_REQUIRES_VERSION_ATTRIB = 92;

    { ER_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet\uc5d0 'version' \uc18d\uc131\uc774 \ud544\uc694\ud569\ub2c8\ub2e4!"},

  /** ER_ILLEGAL_ATTRIBUTE_VALUE          */
//  public static final int ER_ILLEGAL_ATTRIBUTE_VALUE = 93;

    { ER_ILLEGAL_ATTRIBUTE_VALUE,
      "\uc18d\uc131: {0}\uc5d0 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc740 \uac12\uc774 \uc788\uc74c: {1}"},

  /** ER_CHOOSE_REQUIRES_WHEN          */
//  public static final int ER_CHOOSE_REQUIRES_WHEN = 94;

    { ER_CHOOSE_REQUIRES_WHEN,
     "xsl:choose\uc5d0 xsl:when\uc774 \ud544\uc694\ud568"},

  /** ER_NO_APPLY_IMPORT_IN_FOR_EACH          */
//  public static final int ER_NO_APPLY_IMPORT_IN_FOR_EACH = 95;

    { ER_NO_APPLY_IMPORT_IN_FOR_EACH,
      "xsl:apply-imports\ub294 xsl:for-each\uc5d0 \ud5c8\uc6a9\ub418\uc9c0 \uc54a\uc74c"},

  /** ER_CANT_USE_DTM_FOR_OUTPUT          */
//  public static final int ER_CANT_USE_DTM_FOR_OUTPUT = 96;

    { ER_CANT_USE_DTM_FOR_OUTPUT,
      "\ucd9c\ub825 DOM \ub178\ub4dc\uc5d0 DTMLiaison\uc744 \uc0ac\uc6a9\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4. \ub300\uc2e0 com.sun.org.apache.xpath.internal.DOM2Helper\ub97c \uc804\ub2ec\ud558\uc2ed\uc2dc\uc624!"},

  /** ER_CANT_USE_DTM_FOR_INPUT          */
//  public static final int ER_CANT_USE_DTM_FOR_INPUT = 97;

    { ER_CANT_USE_DTM_FOR_INPUT,
      "\uc785\ub825 DOM \ub178\ub4dc\uc5d0 DTMLiaison\uc744 \uc0ac\uc6a9\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4. \ub300\uc2e0 com.sun.org.apache.xpath.internal.DOM2Helper\ub97c \uc804\ub2ec\ud558\uc2ed\uc2dc\uc624!"},

  /** ER_CALL_TO_EXT_FAILED          */
//  public static final int ER_CALL_TO_EXT_FAILED = 98;

    { ER_CALL_TO_EXT_FAILED,
      "\ud655\uc7a5\uc790 \uc694\uc18c \ud638\ucd9c\uc5d0 \uc2e4\ud328\ud568: {0}"},

  /** ER_PREFIX_MUST_RESOLVE          */
//  public static final int ER_PREFIX_MUST_RESOLVE = 99;

    { ER_PREFIX_MUST_RESOLVE,
      "\uc811\ub450\ubd80\ub294 \uc774\ub984 \uacf5\uac04\uc73c\ub85c \ubd84\uc11d\ub418\uc5b4\uc57c \ud569\ub2c8\ub2e4: {0}"},

  /** ER_INVALID_UTF16_SURROGATE          */
//  public static final int ER_INVALID_UTF16_SURROGATE = 100;

    { ER_INVALID_UTF16_SURROGATE,
      "\uc798\ubabb\ub41c UTF-16 \ub300\ub9ac\uc790\uac00 \ubc1c\uacac\ub418\uc5c8\uc2b5\ub2c8\ub2e4: {0} ?"},

  /** ER_XSLATTRSET_USED_ITSELF          */
 // public static final int ER_XSLATTRSET_USED_ITSELF = 101;

    { ER_XSLATTRSET_USED_ITSELF,
      "xsl:attribute-set {0}\uc774(\uac00) \uc790\uc2e0\uc744 \uc0ac\uc6a9\ud588\uc73c\ubbc0\ub85c \ubb34\ud55c \ub8e8\ud504\ub97c \ucd08\ub798\ud569\ub2c8\ub2e4."},

  /** ER_CANNOT_MIX_XERCESDOM          */
//  public static final int ER_CANNOT_MIX_XERCESDOM = 102;

    { ER_CANNOT_MIX_XERCESDOM,
      "\ube44 Xerces-DOM \uc785\ub825\uacfc Xerces-DOM \ucd9c\ub825\uc744 \ud63c\ud569\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4!"},

  /** ER_TOO_MANY_LISTENERS          */
//  public static final int ER_TOO_MANY_LISTENERS = 103;

    { ER_TOO_MANY_LISTENERS,
      "addTraceListenersToStylesheet - TooManyListenersException"},

  /** ER_IN_ELEMTEMPLATEELEM_READOBJECT          */
//  public static final int ER_IN_ELEMTEMPLATEELEM_READOBJECT = 104;

    { ER_IN_ELEMTEMPLATEELEM_READOBJECT,
      "ElemTemplateElement.readObject\uc5d0\uc11c: {0}"},

  /** ER_DUPLICATE_NAMED_TEMPLATE          */
//  public static final int ER_DUPLICATE_NAMED_TEMPLATE = 105;

    { ER_DUPLICATE_NAMED_TEMPLATE,
      "{0} \uc774\ub984\uc758 \ud15c\ud50c\ub9ac\ud2b8\uac00 \ub458 \uc774\uc0c1\uc785\ub2c8\ub2e4."},

  /** ER_INVALID_KEY_CALL          */
//  public static final int ER_INVALID_KEY_CALL = 106;

    { ER_INVALID_KEY_CALL,
      "\uc798\ubabb\ub41c \ud568\uc218 \ud638\ucd9c: recursive key() \ud638\ucd9c\uc774 \ud5c8\uc6a9\ub418\uc9c0 \uc54a\uc74c"},

  /** Variable is referencing itself          */
//  public static final int ER_REFERENCING_ITSELF = 107;

    { ER_REFERENCING_ITSELF,
      "{0} \ubcc0\uc218\uac00 \uc9c1\uc811 \ub610\ub294 \uac04\uc811\uc73c\ub85c \uc790\uc2e0\uc744 \ucc38\uc870\ud569\ub2c8\ub2e4!"},

  /** Illegal DOMSource input          */
//  public static final int ER_ILLEGAL_DOMSOURCE_INPUT = 108;

    { ER_ILLEGAL_DOMSOURCE_INPUT,
      "newTemplates\uc758 DOMSource\uc5d0 \ub300\ud55c \uc785\ub825 \ub178\ub4dc\ub294 \ub110(null)\uc774 \ub420 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4!"},

        /** Class not found for option         */
//  public static final int ER_CLASS_NOT_FOUND_FOR_OPTION = 109;

    { ER_CLASS_NOT_FOUND_FOR_OPTION,
        "{0} \uc635\uc158\uc5d0 \ub300\ud55c \ud074\ub798\uc2a4 \ud30c\uc77c\uc774 \uc5c6\uc74c"},

        /** Required Element not found         */
//  public static final int ER_REQUIRED_ELEM_NOT_FOUND = 110;

    { ER_REQUIRED_ELEM_NOT_FOUND,
        "\ud544\uc218 \uc694\uc18c\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4: {0}"},

  /** InputStream cannot be null         */
//  public static final int ER_INPUT_CANNOT_BE_NULL = 111;

    { ER_INPUT_CANNOT_BE_NULL,
        "InputStream\uc740 \ub110(null)\uc774 \ub420 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** URI cannot be null         */
//  public static final int ER_URI_CANNOT_BE_NULL = 112;

    { ER_URI_CANNOT_BE_NULL,
        "URI\uc740 \ub110(null)\uc774 \ub420 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** File cannot be null         */
//  public static final int ER_FILE_CANNOT_BE_NULL = 113;

    { ER_FILE_CANNOT_BE_NULL,
        "\ud30c\uc77c\uc740 \ub110(null)\uc774 \ub420 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

   /** InputSource cannot be null         */
//  public static final int ER_SOURCE_CANNOT_BE_NULL = 114;

    { ER_SOURCE_CANNOT_BE_NULL,
                "InputSource\ub294 \ub110(null)\uc774 \ub420 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** Can't overwrite cause         */
//  public static final int ER_CANNOT_OVERWRITE_CAUSE = 115;

    //{ ER_CANNOT_OVERWRITE_CAUSE,
        //      "Cannot overwrite cause"},

  /** Could not initialize BSF Manager        */
//  public static final int ER_CANNOT_INIT_BSFMGR = 116;

    { ER_CANNOT_INIT_BSFMGR,
                "BSF \uad00\ub9ac\uc790\ub97c \ucd08\uae30\ud654\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** Could not compile extension       */
//  public static final int ER_CANNOT_CMPL_EXTENSN = 117;

    { ER_CANNOT_CMPL_EXTENSN,
                "\ud655\uc7a5\uc790\ub97c \ucef4\ud30c\uc77c\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** Could not create extension       */
//  public static final int ER_CANNOT_CREATE_EXTENSN = 118;

    { ER_CANNOT_CREATE_EXTENSN,
      "\ud655\uc7a5\uc790\ub97c \uc791\uc131\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.: {0} \uc6d0\uc778: {1}"},

  /** Instance method call to method {0} requires an Object instance as first argument       */
//  public static final int ER_INSTANCE_MTHD_CALL_REQUIRES = 119;

    { ER_INSTANCE_MTHD_CALL_REQUIRES,
      "{0} \uba54\uc18c\ub4dc\uc5d0 \ub300\ud55c \uc778\uc2a4\ud134\uc2a4 \uba54\uc18c\ub4dc \ud638\ucd9c\uc740 \uccab \ubc88\uc9f8 \uc778\uc218\ub85c \uc624\ube0c\uc81d\ud2b8 \uc778\uc2a4\ud134\uc2a4\ub97c \ud544\uc694\ub85c \ud569\ub2c8\ub2e4."},

  /** Invalid element name specified       */
//  public static final int ER_INVALID_ELEMENT_NAME = 120;

    { ER_INVALID_ELEMENT_NAME,
      "\uc798\ubabb\ub41c \uc694\uc18c \uc774\ub984\uc774 \uc9c0\uc815\ub418\uc5c8\uc2b5\ub2c8\ub2e4: {0}"},

   /** Element name method must be static      */
//  public static final int ER_ELEMENT_NAME_METHOD_STATIC = 121;

    { ER_ELEMENT_NAME_METHOD_STATIC,
      "\uc694\uc18c \uc774\ub984 \uba54\uc18c\ub4dc\ub294 static\uc774\uc5b4\uc57c \ud569\ub2c8\ub2e4: {0}"},

   /** Extension function {0} : {1} is unknown      */
//  public static final int ER_EXTENSION_FUNC_UNKNOWN = 122;

    { ER_EXTENSION_FUNC_UNKNOWN,
             "\ud655\uc7a5\uc790 \ud568\uc218 {0} : {1}\uc744(\ub97c) \uc54c \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

   /** More than one best match for constructor for       */
//  public static final int ER_MORE_MATCH_CONSTRUCTOR = 123;

    { ER_MORE_MATCH_CONSTRUCTOR,
             "{0}\uc5d0 \ub300\ud55c \uc0dd\uc131\uc790\uc5d0 \uac00\uc7a5 \uc77c\uce58\ud558\ub294 \uac83\uc774 \uc5c6\uc2b5\ub2c8\ub2e4."},

   /** More than one best match for method      */
//  public static final int ER_MORE_MATCH_METHOD = 124;

    { ER_MORE_MATCH_METHOD,
             "{0} \uba54\uc18c\ub4dc\uc5d0 \uac00\uc7a5 \uc77c\uce58\ud558\ub294 \uac83\uc774 \uc5c6\uc2b5\ub2c8\ub2e4."},

   /** More than one best match for element method      */
//  public static final int ER_MORE_MATCH_ELEMENT = 125;

    { ER_MORE_MATCH_ELEMENT,
             "{0} \uc694\uc18c \uba54\uc18c\ub4dc\uc5d0 \uac00\uc7a5 \uc77c\uce58\ud558\ub294 \uac83\uc774 \uc5c6\uc2b5\ub2c8\ub2e4."},

   /** Invalid context passed to evaluate       */
//  public static final int ER_INVALID_CONTEXT_PASSED = 126;

    { ER_INVALID_CONTEXT_PASSED,
             "{0}\uc744(\ub97c) \ud3c9\uac00\ud558\ub294 \ub370 \uc798\ubabb\ub41c \ubb38\ub9e5\uc774 \uc804\ub2ec\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},

   /** Pool already exists       */
//  public static final int ER_POOL_EXISTS = 127;

    { ER_POOL_EXISTS,
             "\ud480\uc774 \uc774\ubbf8 \uc788\uc2b5\ub2c8\ub2e4."},

   /** No driver Name specified      */
//  public static final int ER_NO_DRIVER_NAME = 128;

    { ER_NO_DRIVER_NAME,
             "\ub4dc\ub77c\uc774\ubc84 \uc774\ub984\uc744 \uc9c0\uc815\ud558\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4."},

   /** No URL specified     */
//  public static final int ER_NO_URL = 129;

    { ER_NO_URL,
             "URL\uc744 \uc9c0\uc815\ud558\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4."},

   /** Pool size is less than one    */
//  public static final int ER_POOL_SIZE_LESSTHAN_ONE = 130;

    { ER_POOL_SIZE_LESSTHAN_ONE,
             "\ud480 \ud06c\uae30\uac00 1 \ubbf8\ub9cc\uc785\ub2c8\ub2e4!"},

   /** Invalid driver name specified    */
//  public static final int ER_INVALID_DRIVER = 131;

    { ER_INVALID_DRIVER,
             "\uc798\ubabb\ub41c \ub4dc\ub77c\uc774\ubc84 \uc774\ub984\uc744 \uc9c0\uc815\ud588\uc2b5\ub2c8\ub2e4!"},

   /** Did not find the stylesheet root    */
//  public static final int ER_NO_STYLESHEETROOT = 132;

    { ER_NO_STYLESHEETROOT,
             "\uc2a4\ud0c0\uc77c \uc2dc\ud2b8 \ub8e8\ud2b8\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4!"},

   /** Illegal value for xml:space     */
//  public static final int ER_ILLEGAL_XMLSPACE_VALUE = 133;

    { ER_ILLEGAL_XMLSPACE_VALUE,
         "xml:space\uc5d0 \ub300\ud574 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc740 \uac12"},

   /** processFromNode failed     */
//  public static final int ER_PROCESSFROMNODE_FAILED = 134;

    { ER_PROCESSFROMNODE_FAILED,
         "processFromNode\uac00 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4."},

   /** The resource [] could not load:     */
//  public static final int ER_RESOURCE_COULD_NOT_LOAD = 135;

    { ER_RESOURCE_COULD_NOT_LOAD,
        "[ {0} ] \uc790\uc6d0\uc774 {1} \n {2} \t {3}\uc744 \ub85c\ub4dc\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},


   /** Buffer size <=0     */
//  public static final int ER_BUFFER_SIZE_LESSTHAN_ZERO = 136;

    { ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "\ubc84\ud37c \ud06c\uae30 <=0"},

   /** Unknown error when calling extension    */
//  public static final int ER_UNKNOWN_ERROR_CALLING_EXTENSION = 137;

    { ER_UNKNOWN_ERROR_CALLING_EXTENSION,
        "\ud655\uc7a5\uc790 \ud638\ucd9c \uc2dc \uc54c \uc218 \uc5c6\ub294 \uc624\ub958"},

   /** Prefix {0} does not have a corresponding namespace declaration    */
//  public static final int ER_NO_NAMESPACE_DECL = 138;

    { ER_NO_NAMESPACE_DECL,
        "{0} \uc811\ub450\ubd80\uc5d0 \ud574\ub2f9\ud558\ub294 \uc774\ub984 \uacf5\uac04 \uc120\uc5b8\uc774 \uc5c6\uc2b5\ub2c8\ub2e4."},

   /** Element content not allowed for lang=javaclass   */
//  public static final int ER_ELEM_CONTENT_NOT_ALLOWED = 139;

    { ER_ELEM_CONTENT_NOT_ALLOWED,
        "lang=javaclass {0}\uc5d0 \ub300\ud574 \uc694\uc18c \ucee8\ud150\uce20\uac00 \ud5c8\uc6a9\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

   /** Stylesheet directed termination   */
//  public static final int ER_STYLESHEET_DIRECTED_TERMINATION = 140;

    { ER_STYLESHEET_DIRECTED_TERMINATION,
        "\uc2a4\ud0c0\uc77c \uc2dc\ud2b8\uac00 \uc885\ub8cc\ub97c \uc9c0\uc2dc\ud588\uc2b5\ub2c8\ub2e4."},

   /** 1 or 2   */
//  public static final int ER_ONE_OR_TWO = 141;

    { ER_ONE_OR_TWO,
        "1 \ub610\ub294 2"},

   /** 2 or 3   */
//  public static final int ER_TWO_OR_THREE = 142;

    { ER_TWO_OR_THREE,
        "2 \ub610\ub294 3"},

   /** Could not load {0} (check CLASSPATH), now using just the defaults   */
//  public static final int ER_COULD_NOT_LOAD_RESOURCE = 143;

    { ER_COULD_NOT_LOAD_RESOURCE,
        "{0}(check CLASSPATH)\uc744(\ub97c) \ub85c\ub4dc\ud560 \uc218 \uc5c6\uc73c\ubbc0\ub85c, \ud604\uc7ac \uae30\ubcf8\uac12\ub9cc\uc744 \uc0ac\uc6a9 \uc911\uc785\ub2c8\ub2e4."},

   /** Cannot initialize default templates   */
//  public static final int ER_CANNOT_INIT_DEFAULT_TEMPLATES = 144;

    { ER_CANNOT_INIT_DEFAULT_TEMPLATES,
        "\uae30\ubcf8 \ud15c\ud50c\ub9ac\ud2b8\ub97c \ucd08\uae30\ud654\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

   /** Result should not be null   */
//  public static final int ER_RESULT_NULL = 145;

    { ER_RESULT_NULL,
        "\uacb0\uacfc\ub294 \ub110(null)\uc774 \ub420 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

   /** Result could not be set   */
//  public static final int ER_RESULT_COULD_NOT_BE_SET = 146;

    { ER_RESULT_COULD_NOT_BE_SET,
        "\uacb0\uacfc\ub97c \uc124\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

   /** No output specified   */
//  public static final int ER_NO_OUTPUT_SPECIFIED = 147;

    { ER_NO_OUTPUT_SPECIFIED,
        "\ucd9c\ub825\uc744 \uc9c0\uc815\ud558\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4."},

   /** Can't transform to a Result of type   */
//  public static final int ER_CANNOT_TRANSFORM_TO_RESULT_TYPE = 148;

    { ER_CANNOT_TRANSFORM_TO_RESULT_TYPE,
        "{0} \uc720\ud615\uc758 \uacb0\uacfc\ub85c \ubcc0\ud658\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

   /** Can't transform to a Source of type   */
//  public static final int ER_CANNOT_TRANSFORM_SOURCE_TYPE = 149;

    { ER_CANNOT_TRANSFORM_SOURCE_TYPE,
        "{0} \uc720\ud615\uc758 \uc18c\uc2a4\ub85c \ubcc0\ud658\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

   /** Null content handler  */
//  public static final int ER_NULL_CONTENT_HANDLER = 150;

    { ER_NULL_CONTENT_HANDLER,
        "\ub110(null) \ucee8\ud150\uce20 \ud578\ub4e4\ub7ec"},

   /** Null error handler  */
//  public static final int ER_NULL_ERROR_HANDLER = 151;
    { ER_NULL_ERROR_HANDLER,
        "\ub110(null) \uc624\ub958 \ud578\ub4e4\ub7ec"},

   /** parse can not be called if the ContentHandler has not been set */
//  public static final int ER_CANNOT_CALL_PARSE = 152;

    { ER_CANNOT_CALL_PARSE,
        "ContentHandler\ub97c \uc124\uc815\ud558\uc9c0 \uc54a\uc740 \uacbd\uc6b0\uc5d0\ub294 parse\ub97c \ud638\ucd9c\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

   /**  No parent for filter */
//  public static final int ER_NO_PARENT_FOR_FILTER = 153;

    { ER_NO_PARENT_FOR_FILTER,
        "\uc0c1\uc704 \ud544\ud130\uac00 \uc5c6\uc2b5\ub2c8\ub2e4."},


   /**  No stylesheet found in: {0}, media */
//  public static final int ER_NO_STYLESHEET_IN_MEDIA = 154;

    { ER_NO_STYLESHEET_IN_MEDIA,
         "{0}\uc5d0 \uc2a4\ud0c0\uc77c \uc2dc\ud2b8\uac00 \uc5c6\uc2b5\ub2c8\ub2e4. \ub9e4\uccb4= {1}"},

   /**  No xml-stylesheet PI found in */
//  public static final int ER_NO_STYLESHEET_PI = 155;

    { ER_NO_STYLESHEET_PI,
         "{0}\uc5d0 xml-\uc2a4\ud0c0\uc77c \uc2dc\ud2b8 PI\uac00 \uc5c6\uc2b5\ub2c8\ub2e4."},

   /**  No default implementation found */
//  public static final int ER_NO_DEFAULT_IMPL = 156;

    //{ ER_NO_DEFAULT_IMPL,
     //    "No default implementation found "},

   /**  ChunkedIntArray({0}) not currently supported */
//  public static final int ER_CHUNKEDINTARRAY_NOT_SUPPORTED = 157;

    //{ ER_CHUNKEDINTARRAY_NOT_SUPPORTED,
     //  "ChunkedIntArray({0}) not currently supported"},

   /**  Offset bigger than slot */
//  public static final int ER_OFFSET_BIGGER_THAN_SLOT = 158;

    //{ ER_OFFSET_BIGGER_THAN_SLOT,
     //  "Offset bigger than slot"},

   /**  Coroutine not available, id= */
//  public static final int ER_COROUTINE_NOT_AVAIL = 159;

    //{ ER_COROUTINE_NOT_AVAIL,
    //   "Coroutine not available, id={0}"},

   /**  CoroutineManager recieved co_exit() request */
//  public static final int ER_COROUTINE_CO_EXIT = 160;

    //{ ER_COROUTINE_CO_EXIT,
     //  "CoroutineManager received co_exit() request"},

   /**  co_joinCoroutineSet() failed */
//  public static final int ER_COJOINROUTINESET_FAILED = 161;

    //{ ER_COJOINROUTINESET_FAILED,
    //   "co_joinCoroutineSet() failed"},

   /**  Coroutine parameter error () */
//  public static final int ER_COROUTINE_PARAM = 162;

    //{ ER_COROUTINE_PARAM,
    //   "Coroutine parameter error ({0})"},

   /**  UNEXPECTED: Parser doTerminate answers  */
//  public static final int ER_PARSER_DOTERMINATE_ANSWERS = 163;

    //{ ER_PARSER_DOTERMINATE_ANSWERS,
    //   "\nUNEXPECTED: Parser doTerminate answers {0}"},

   /**  parse may not be called while parsing */
//  public static final int ER_NO_PARSE_CALL_WHILE_PARSING = 164;

    //{ ER_NO_PARSE_CALL_WHILE_PARSING,
    //   "parse may not be called while parsing"},

   /**  Error: typed iterator for axis  {0} not implemented  */
//  public static final int ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED = 165;

    //{ ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED,
     //  "Error: typed iterator for axis  {0} not implemented"},

   /**  Error: iterator for axis {0} not implemented  */
//  public static final int ER_ITERATOR_AXIS_NOT_IMPLEMENTED = 166;

    //{ ER_ITERATOR_AXIS_NOT_IMPLEMENTED,
    //   "Error: iterator for axis {0} not implemented "},

   /**  Iterator clone not supported  */
//  public static final int ER_ITERATOR_CLONE_NOT_SUPPORTED = 167;

    //{ ER_ITERATOR_CLONE_NOT_SUPPORTED,
     //  "Iterator clone not supported"},

   /**  Unknown axis traversal type  */
//  public static final int ER_UNKNOWN_AXIS_TYPE = 168;

    //{ ER_UNKNOWN_AXIS_TYPE,
    //   "Unknown axis traversal type: {0}"},

   /**  Axis traverser not supported  */
//  public static final int ER_AXIS_NOT_SUPPORTED = 169;

    //{ ER_AXIS_NOT_SUPPORTED,
    //   "Axis traverser not supported: {0}"},

   /**  No more DTM IDs are available  */
//  public static final int ER_NO_DTMIDS_AVAIL = 170;

    //{ ER_NO_DTMIDS_AVAIL,
     //  "No more DTM IDs are available"},

   /**  Not supported  */
//  public static final int ER_NOT_SUPPORTED = 171;

    { ER_NOT_SUPPORTED,
       "\uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc74c: {0}"},

   /**  node must be non-null for getDTMHandleFromNode  */
//  public static final int ER_NODE_NON_NULL = 172;

    //{ ER_NODE_NON_NULL,
    //   "Node must be non-null for getDTMHandleFromNode"},

   /**  Could not resolve the node to a handle  */
//  public static final int ER_COULD_NOT_RESOLVE_NODE = 173;

    //{ ER_COULD_NOT_RESOLVE_NODE,
    //   "Could not resolve the node to a handle"},

   /**  startParse may not be called while parsing */
//  public static final int ER_STARTPARSE_WHILE_PARSING = 174;

    //{ ER_STARTPARSE_WHILE_PARSING,
     //  "startParse may not be called while parsing"},

   /**  startParse needs a non-null SAXParser  */
//  public static final int ER_STARTPARSE_NEEDS_SAXPARSER = 175;

    //{ ER_STARTPARSE_NEEDS_SAXPARSER,
    //   "startParse needs a non-null SAXParser"},

   /**  could not initialize parser with */
//  public static final int ER_COULD_NOT_INIT_PARSER = 176;
    //{ ER_COULD_NOT_INIT_PARSER,
     //  "could not initialize parser with"},

   /**  Value for property {0} should be a Boolean instance  */
//  public static final int ER_PROPERTY_VALUE_BOOLEAN = 177;

    { ER_PROPERTY_VALUE_BOOLEAN,
       "{0} \ud2b9\uc131\uac12\uc740 \ubd80\uc6b8 \uc778\uc2a4\ud134\uc2a4\uc774\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

   /**  exception creating new instance for pool  */
//  public static final int ER_EXCEPTION_CREATING_POOL = 178;

    //{ ER_EXCEPTION_CREATING_POOL,
    //   "exception creating new instance for pool"},

   /**  Path contains invalid escape sequence  */
//  public static final int ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE = 179;

    //{ ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
    //   "Path contains invalid escape sequence"},

   /**  Scheme is required!  */
//  public static final int ER_SCHEME_REQUIRED = 180;

    //{ ER_SCHEME_REQUIRED,
     //  "Scheme is required!"},

   /**  No scheme found in URI  */
//  public static final int ER_NO_SCHEME_IN_URI = 181;

    //{ ER_NO_SCHEME_IN_URI,
    //   "No scheme found in URI: {0}"},

   /**  No scheme found in URI  */
//  public static final int ER_NO_SCHEME_INURI = 182;

    //{ ER_NO_SCHEME_INURI,
    //   "No scheme found in URI"},

   /**  Path contains invalid character:   */
//  public static final int ER_PATH_INVALID_CHAR = 183;

    //{ ER_PATH_INVALID_CHAR,
    //   "Path contains invalid character: {0}"},

   /**  Cannot set scheme from null string  */
//  public static final int ER_SCHEME_FROM_NULL_STRING = 184;

    //{ ER_SCHEME_FROM_NULL_STRING,
    //   "Cannot set scheme from null string"},

   /**  The scheme is not conformant. */
//  public static final int ER_SCHEME_NOT_CONFORMANT = 185;

    //{ ER_SCHEME_NOT_CONFORMANT,
    //   "The scheme is not conformant."},

   /**  Host is not a well formed address  */
//  public static final int ER_HOST_ADDRESS_NOT_WELLFORMED = 186;

    //{ ER_HOST_ADDRESS_NOT_WELLFORMED,
    //   "Host is not a well formed address"},

   /**  Port cannot be set when host is null  */
//  public static final int ER_PORT_WHEN_HOST_NULL = 187;

    //{ ER_PORT_WHEN_HOST_NULL,
    //   "Port cannot be set when host is null"},

   /**  Invalid port number  */
//  public static final int ER_INVALID_PORT = 188;

    //{ ER_INVALID_PORT,
    //   "Invalid port number"},

   /**  Fragment can only be set for a generic URI  */
//  public static final int ER_FRAG_FOR_GENERIC_URI = 189;

    //{ ER_FRAG_FOR_GENERIC_URI,
    //   "Fragment can only be set for a generic URI"},

   /**  Fragment cannot be set when path is null  */
//  public static final int ER_FRAG_WHEN_PATH_NULL = 190;

    //{ ER_FRAG_WHEN_PATH_NULL,
    //   "Fragment cannot be set when path is null"},

   /**  Fragment contains invalid character  */
//  public static final int ER_FRAG_INVALID_CHAR = 191;

    //{ ER_FRAG_INVALID_CHAR,
    //   "Fragment contains invalid character"},



   /** Parser is already in use  */
//  public static final int ER_PARSER_IN_USE = 192;

    //{ ER_PARSER_IN_USE,
    //    "Parser is already in use"},

   /** Parser is already in use  */
//  public static final int ER_CANNOT_CHANGE_WHILE_PARSING = 193;

    //{ ER_CANNOT_CHANGE_WHILE_PARSING,
    //    "Cannot change {0} {1} while parsing"},

   /** Self-causation not permitted  */
//  public static final int ER_SELF_CAUSATION_NOT_PERMITTED = 194;

    //{ ER_SELF_CAUSATION_NOT_PERMITTED,
     //   "Self-causation not permitted"},

   /** src attribute not yet supported for  */
//  public static final int ER_COULD_NOT_FIND_EXTERN_SCRIPT = 195;

    { ER_COULD_NOT_FIND_EXTERN_SCRIPT,
         "{0}\uc5d0 \uc788\ub294 \uc678\ubd80 \uc2a4\ud06c\ub9bd\ud2b8\uc5d0 \ub3c4\ub2ec\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** The resource [] could not be found     */
//  public static final int ER_RESOURCE_COULD_NOT_FIND = 196;

    { ER_RESOURCE_COULD_NOT_FIND,
        "[ {0} ] \uc790\uc6d0\uc744 \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.\n {1}"},

   /** output property not recognized:  */
//  public static final int ER_OUTPUT_PROPERTY_NOT_RECOGNIZED = 197;

    { ER_OUTPUT_PROPERTY_NOT_RECOGNIZED,
        "\ucd9c\ub825 \ud2b9\uc131\uc774 \uc778\uc2dd\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4: {0}"},

   /** Userinfo may not be specified if host is not specified   */
//  public static final int ER_NO_USERINFO_IF_NO_HOST = 198;

    //{ ER_NO_USERINFO_IF_NO_HOST,
    //    "Userinfo may not be specified if host is not specified"},

   /** Port may not be specified if host is not specified   */
//  public static final int ER_NO_PORT_IF_NO_HOST = 199;

    //{ ER_NO_PORT_IF_NO_HOST,
    //    "Port may not be specified if host is not specified"},

   /** Query string cannot be specified in path and query string   */
//  public static final int ER_NO_QUERY_STRING_IN_PATH = 200;

    //{ ER_NO_QUERY_STRING_IN_PATH,
    //    "Query string cannot be specified in path and query string"},

   /** Fragment cannot be specified in both the path and fragment   */
//  public static final int ER_NO_FRAGMENT_STRING_IN_PATH = 201;

    //{ ER_NO_FRAGMENT_STRING_IN_PATH,
    //    "Fragment cannot be specified in both the path and fragment"},

   /** Cannot initialize URI with empty parameters   */
//  public static final int ER_CANNOT_INIT_URI_EMPTY_PARMS = 202;

    //{ ER_CANNOT_INIT_URI_EMPTY_PARMS,
    //    "Cannot initialize URI with empty parameters"},

   /** Failed creating ElemLiteralResult instance   */
//  public static final int ER_FAILED_CREATING_ELEMLITRSLT = 203;

    { ER_FAILED_CREATING_ELEMLITRSLT,
        "ElemLiteralResult \uc778\uc2a4\ud134\uc2a4 \uc791\uc131\uc5d0 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4."},

  //Earlier (JDK 1.4 XALAN 2.2-D11) at key code '204' the key name was ER_PRIORITY_NOT_PARSABLE
  // In latest Xalan code base key name is  ER_VALUE_SHOULD_BE_NUMBER. This should also be taken care
  //in locale specific files like XSLTErrorResources_de.java, XSLTErrorResources_fr.java etc.
  //NOTE: Not only the key name but message has also been changed.

   /** Priority value does not contain a parsable number   */
//  public static final int ER_VALUE_SHOULD_BE_NUMBER = 204;

    { ER_VALUE_SHOULD_BE_NUMBER,
        "{0}\uc5d0 \ub300\ud55c \uac12\uc5d0 \uad6c\ubb38 \ubd84\uc11d \uac00\ub2a5\ud55c \uc22b\uc790\uac00 \uc788\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

   /**  Value for {0} should equal 'yes' or 'no'   */
//  public static final int ER_VALUE_SHOULD_EQUAL = 205;

    { ER_VALUE_SHOULD_EQUAL,
        "{0}\uc758 \uac12\uc740 yes \ub610\ub294 no\uc5ec\uc57c \ud569\ub2c8\ub2e4."},

   /**  Failed calling {0} method   */
//  public static final int ER_FAILED_CALLING_METHOD = 206;

    { ER_FAILED_CALLING_METHOD,
        "{0} \uba54\uc18c\ub4dc \ud638\ucd9c\uc5d0 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4."},

   /** Failed creating ElemLiteralResult instance   */
//  public static final int ER_FAILED_CREATING_ELEMTMPL = 207;

    { ER_FAILED_CREATING_ELEMTMPL,
        "ElemTemplateElement \uc778\uc2a4\ud134\uc2a4 \uc791\uc131\uc5d0 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4."},

   /**  Characters are not allowed at this point in the document   */
//  public static final int ER_CHARS_NOT_ALLOWED = 208;

    { ER_CHARS_NOT_ALLOWED,
        "\ubb38\uc11c\uc758 \uc774 \uc9c0\uc810\uc5d0 \ubb38\uc790\uac00 \ud5c8\uc6a9\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

  /**  attribute is not allowed on the element   */
//  public static final int ER_ATTR_NOT_ALLOWED = 209;
    { ER_ATTR_NOT_ALLOWED,
        "{1} \uc694\uc18c\uc5d0 \"{0}\" \uc18d\uc131\uc774 \ud5c8\uc6a9\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!"},

  /**  Method not yet supported    */
//  public static final int ER_METHOD_NOT_SUPPORTED = 210;

    //{ ER_METHOD_NOT_SUPPORTED,
     //   "Method not yet supported "},

  /**  Bad value    */
//  public static final int ER_BAD_VALUE = 211;

    { ER_BAD_VALUE,
     "{0} \uc798\ubabb\ub41c \uac12 {1} "},

  /**  attribute value not found   */
//  public static final int ER_ATTRIB_VALUE_NOT_FOUND = 212;

    { ER_ATTRIB_VALUE_NOT_FOUND,
     "{0} \uc18d\uc131\uac12\uc774 \uc5c6\uc2b5\ub2c8\ub2e4. "},

  /**  attribute value not recognized    */
//  public static final int ER_ATTRIB_VALUE_NOT_RECOGNIZED = 213;

    { ER_ATTRIB_VALUE_NOT_RECOGNIZED,
     "{0} \uc18d\uc131\uac12\uc774 \uc778\uc2dd\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. "},

  /** IncrementalSAXSource_Filter not currently restartable   */
//  public static final int ER_INCRSAXSRCFILTER_NOT_RESTARTABLE = 214;

    //{ ER_INCRSAXSRCFILTER_NOT_RESTARTABLE,
   //  "IncrementalSAXSource_Filter not currently restartable"},

  /** IncrementalSAXSource_Filter not currently restartable   */
//  public static final int ER_XMLRDR_NOT_BEFORE_STARTPARSE = 215;

    //{ ER_XMLRDR_NOT_BEFORE_STARTPARSE,
    // "XMLReader not before startParse request"},

  /** Attempting to generate a namespace prefix with a null URI   */
//  public static final int ER_NULL_URI_NAMESPACE = 216;

    { ER_NULL_URI_NAMESPACE,
     "\ub110(null) URI\ub85c \uc774\ub984 \uacf5\uac04 \uc811\ub450\ubd80\ub97c \uc0dd\uc131\ud558\ub824\uace0 \uc2dc\ub3c4 \uc911\uc785\ub2c8\ub2e4."},

  //New ERROR keys added in XALAN code base after Jdk 1.4 (Xalan 2.2-D11)

  /** Attempting to generate a namespace prefix with a null URI   */
//  public static final int ER_NUMBER_TOO_BIG = 217;

    { ER_NUMBER_TOO_BIG,
     "\ucd5c\ub300\ub85c \uae34 \uc815\uc218\ubcf4\ub2e4 \ud070 \uc22b\uc790\ub97c \ud3ec\ub9f7\ud558\ub824\uace0 \uc2dc\ub3c4 \uc911\uc785\ub2c8\ub2e4."},

//ER_CANNOT_FIND_SAX1_DRIVER

//  public static final int  ER_CANNOT_FIND_SAX1_DRIVER = 218;

    { ER_CANNOT_FIND_SAX1_DRIVER,
     "SAX1 \ub4dc\ub77c\uc774\ubc84 \ud074\ub798\uc2a4 {0}\uc744(\ub97c) \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

//ER_SAX1_DRIVER_NOT_LOADED
//  public static final int  ER_SAX1_DRIVER_NOT_LOADED = 219;

    { ER_SAX1_DRIVER_NOT_LOADED,
     "SAX1 \ub4dc\ub77c\uc774\ubc84 \ud074\ub798\uc2a4 {0}\uc774(\uac00) \uc788\uc73c\ub098 \ub85c\ub4dc\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

//ER_SAX1_DRIVER_NOT_INSTANTIATED
//  public static final int  ER_SAX1_DRIVER_NOT_INSTANTIATED = 220 ;

    { ER_SAX1_DRIVER_NOT_INSTANTIATED,
     "SAX1 \ub4dc\ub77c\uc774\ubc84 \ud074\ub798\uc2a4 {0}\uc744(\ub97c) \ub85c\ub4dc\ud588\uc73c\ub098 \uc778\uc2a4\ud134\uc2a4\ud654\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},


// ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER
//  public static final int ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER = 221;

    { ER_SAX1_DRIVER_NOT_IMPLEMENT_PARSER,
     "SAX1 \ub4dc\ub77c\uc774\ubc84 \ud074\ub798\uc2a4 {0}\uc774(\uac00) org.xml.sax.Parser\ub97c \uad6c\ud604\ud558\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4."},

// ER_PARSER_PROPERTY_NOT_SPECIFIED
//  public static final int  ER_PARSER_PROPERTY_NOT_SPECIFIED = 222;

    { ER_PARSER_PROPERTY_NOT_SPECIFIED,
     "\uc2dc\uc2a4\ud15c \ud2b9\uc131 org.xml.sax.parser\ub97c \uc9c0\uc815\ud558\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4."},

//ER_PARSER_ARG_CANNOT_BE_NULL
//  public static final int  ER_PARSER_ARG_CANNOT_BE_NULL = 223 ;

    { ER_PARSER_ARG_CANNOT_BE_NULL,
     "\uad6c\ubb38 \ubd84\uc11d\uae30 \uc778\uc218\ub294 \ub110(null)\uc774 \ub420 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},


// ER_FEATURE
//  public static final int  ER_FEATURE = 224;

    { ER_FEATURE,
     "\ud2b9\uc131: {0}"},


// ER_PROPERTY
//  public static final int ER_PROPERTY = 225 ;

    { ER_PROPERTY,
     "\ud2b9\uc131: {0}"},

// ER_NULL_ENTITY_RESOLVER
//  public static final int ER_NULL_ENTITY_RESOLVER  = 226;

    { ER_NULL_ENTITY_RESOLVER,
     "\ub110(null) \uc5d4\ud2f0\ud2f0 \ubd84\uc11d\uae30"},

// ER_NULL_DTD_HANDLER
//  public static final int  ER_NULL_DTD_HANDLER = 227 ;

    { ER_NULL_DTD_HANDLER,
     "\ub110(null) DTD \ud578\ub4e4\ub7ec"},

// No Driver Name Specified!
//  public static final int ER_NO_DRIVER_NAME_SPECIFIED = 228;
    { ER_NO_DRIVER_NAME_SPECIFIED,
     "\ub4dc\ub77c\uc774\ubc84 \uc774\ub984\uc744 \uc9c0\uc815\ud558\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4!"},


// No URL Specified!
//  public static final int ER_NO_URL_SPECIFIED = 229;
    { ER_NO_URL_SPECIFIED,
     "URL\uc744 \uc9c0\uc815\ud558\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4!"},


// Pool size is less than 1!
//  public static final int ER_POOLSIZE_LESS_THAN_ONE = 230;
    { ER_POOLSIZE_LESS_THAN_ONE,
     "\ud480 \ud06c\uae30\uac00 1 \ubbf8\ub9cc\uc785\ub2c8\ub2e4!"},


// Invalid Driver Name Specified!
//  public static final int ER_INVALID_DRIVER_NAME = 231;
    { ER_INVALID_DRIVER_NAME,
     "\uc798\ubabb\ub41c \ub4dc\ub77c\uc774\ubc84 \uc774\ub984\uc744 \uc9c0\uc815\ud588\uc2b5\ub2c8\ub2e4!"},



// ErrorListener
//  public static final int ER_ERRORLISTENER = 232;
    { ER_ERRORLISTENER,
     "ErrorListener"},


// Programmer's error! expr has no ElemTemplateElement parent!
//  public static final int ER_ASSERT_NO_TEMPLATE_PARENT = 233;
    { ER_ASSERT_NO_TEMPLATE_PARENT,
     "\ud504\ub85c\uadf8\ub798\uba38 \uc624\ub958! expr\uc5d0 ElemTemplateElement \uc0c1\uc704\uac00 \uc5c6\uc2b5\ub2c8\ub2e4!"},


// Programmer''s assertion in RundundentExprEliminator: {0}
//  public static final int ER_ASSERT_REDUNDENT_EXPR_ELIMINATOR = 234;
    { ER_ASSERT_REDUNDENT_EXPR_ELIMINATOR,
     "RundundentExprEliminator\uc5d0 \ud504\ub85c\uadf8\ub798\uba38\uc758 \ub2e8\uc5b8\ubb38: {0}"},

// Axis traverser not supported: {0}
//  public static final int ER_AXIS_TRAVERSER_NOT_SUPPORTED = 235;
    //{ ER_AXIS_TRAVERSER_NOT_SUPPORTED,
    // "Axis traverser not supported: {0}"},

// ListingErrorHandler created with null PrintWriter!
//  public static final int ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER = 236;
    //{ ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER,
    // "ListingErrorHandler created with null PrintWriter!"},

  // {0}is not allowed in this position in the stylesheet!
//  public static final int ER_NOT_ALLOWED_IN_POSITION = 237;
    { ER_NOT_ALLOWED_IN_POSITION,
     "{0}\uc740(\ub294) \uc2a4\ud0c0\uc77c \uc2dc\ud2b8\uc758 \uc774 \uc704\uce58\uc5d0\uc11c \ud5c8\uc6a9\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!"},

  // Non-whitespace text is not allowed in this position in the stylesheet!
//  public static final int ER_NONWHITESPACE_NOT_ALLOWED_IN_POSITION = 238;
    { ER_NONWHITESPACE_NOT_ALLOWED_IN_POSITION,
     "Non-whitespace \ud14d\uc2a4\ud2b8\ub294 \uc2a4\ud0c0\uc77c \uc2dc\ud2b8\uc758 \uc774 \uc704\uce58\uc5d0\uc11c \ud5c8\uc6a9\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!"},

  // This code is shared with warning codes.
  // Illegal value: {1} used for CHAR attribute: {0}.  An attribute of type CHAR must be only 1 character!
//  public static final int INVALID_TCHAR = 239;
  // SystemId Unknown
    { INVALID_TCHAR,
     "{0} CHAR \uc18d\uc131\uc5d0 \ub300\ud574 \uc0ac\uc6a9\ub41c {1} \uac12\uc774 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. CHAR \uc720\ud615\uc758 \uc18d\uc131\uc740 1 \ubb38\uc790\uc774\uc5b4\uc57c \ud569\ub2c8\ub2e4!"},

//  public static final int ER_SYSTEMID_UNKNOWN = 240;
    //{ ER_SYSTEMID_UNKNOWN,
    // "SystemId Unknown"},

  // Location of error unknown
//  public static final int ER_LOCATION_UNKNOWN = 241;
    //{ ER_LOCATION_UNKNOWN,
    // "Location of error unknown"},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "QNAME" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value and {0} is the attribute name.
    // INVALID_QNAME

  //The following codes are shared with the warning codes...
  // Illegal value: {1} used for QNAME attribute: {0}
//  public static final int INVALID_QNAME = 242;
    { INVALID_QNAME,
     "{0} QNAME \uc18d\uc131\uc5d0 \ub300\ud574 \uc0ac\uc6a9\ub41c {1} \uac12\uc774 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

    // Note to translators:  The following message is used if the value of
    // an attribute in a stylesheet is invalid.  "ENUM" is the XML data-type of
    // the attribute, and should not be translated.  The substitution text {1} is
    // the attribute value, {0} is the attribute name, and {2} is a list of valid
    // values.
    // INVALID_ENUM

  // Illegal value: {1} used for ENUM attribute: {0}.  Valid values are: {2}.
//  public static final int INVALID_ENUM = 243;
    { INVALID_ENUM,
     "{0} ENUM \uc18d\uc131\uc5d0 \ub300\ud574 \uc0ac\uc6a9\ub41c {1} \uac12\uc774 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. \uc62c\ubc14\ub978 \uac12\uc740 {2}\uc785\ub2c8\ub2e4."},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NMTOKEN" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
// INVALID_NMTOKEN

  // Illegal value: {1} used for NMTOKEN attribute: {0}.
//  public static final int INVALID_NMTOKEN = 244;
    { INVALID_NMTOKEN,
     "{0} NMTOKEN \uc18d\uc131\uc5d0 \ub300\ud574 \uc0ac\uc6a9\ub41c {1} \uac12\uc774 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "NCNAME" is the XML data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
// INVALID_NCNAME

  // Illegal value: {1} used for NCNAME attribute: {0}.
//  public static final int INVALID_NCNAME = 245;
    { INVALID_NCNAME,
     "{0} NCNAME \uc18d\uc131\uc5d0 \ub300\ud574 \uc0ac\uc6a9\ub41c {1} \uac12\uc774 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "boolean" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
// INVALID_BOOLEAN

  // Illegal value: {1} used for boolean attribute: {0}.
//  public static final int INVALID_BOOLEAN = 246;

    { INVALID_BOOLEAN,
     "{0} \ubd80\uc6b8 \uc18d\uc131\uc5d0 \ub300\ud574 \uc0ac\uc6a9\ub41c {1} \uac12\uc774 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. "},

// Note to translators:  The following message is used if the value of
// an attribute in a stylesheet is invalid.  "number" is the XSLT data-type
// of the attribute, and should not be translated.  The substitution text {1} is
// the attribute value and {0} is the attribute name.
// INVALID_NUMBER

  // Illegal value: {1} used for number attribute: {0}.
//  public static final int INVALID_NUMBER = 247;
     { INVALID_NUMBER,
     "{0} \uc22b\uc790 \uc18d\uc131\uc5d0 \ub300\ud574 \uc0ac\uc6a9\ub41c {1} \uac12\uc774 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. "},


  // End of shared codes...

// Note to translators:  A "match pattern" is a special form of XPath expression
// that is used for matching patterns.  The substitution text is the name of
// a function.  The message indicates that when this function is referenced in
// a match pattern, its argument must be a string literal (or constant.)
// ER_ARG_LITERAL - new error message for bugzilla //5202

  // Argument to {0} in match pattern must be a literal.
//  public static final int ER_ARG_LITERAL             = 248;
    { ER_ARG_LITERAL,
     "\uc77c\uce58 \ud328\ud134\uc5d0\uc11c {0}\uc5d0 \ub300\ud55c \uc778\uc218\ub294 \ub9ac\ud130\ub7f4\uc774\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

// Note to translators:  The following message indicates that two definitions of
// a variable.  A "global variable" is a variable that is accessible everywher
// in the stylesheet.
// ER_DUPLICATE_GLOBAL_VAR - new error message for bugzilla #790

  // Duplicate global variable declaration.
//  public static final int ER_DUPLICATE_GLOBAL_VAR    = 249;
    { ER_DUPLICATE_GLOBAL_VAR,
     "\uc911\ubcf5 \uae00\ub85c\ubc8c \ubcc0\uc218 \uc120\uc5b8\uc785\ub2c8\ub2e4."},


// Note to translators:  The following message indicates that two definitions of
// a variable were encountered.
// ER_DUPLICATE_VAR - new error message for bugzilla #790

  // Duplicate variable declaration.
//  public static final int ER_DUPLICATE_VAR           = 250;
    { ER_DUPLICATE_VAR,
     "\uc911\ubcf5 \ubcc0\uc218 \uc120\uc5b8\uc785\ub2c8\ub2e4."},

    // Note to translators:  "xsl:template, "name" and "match" are XSLT keywords
    // which must not be translated.
    // ER_TEMPLATE_NAME_MATCH - new error message for bugzilla #789

  // xsl:template must have a name or match attribute (or both)
//  public static final int ER_TEMPLATE_NAME_MATCH     = 251;
    { ER_TEMPLATE_NAME_MATCH,
     "xsl:template\uc5d0 \uc774\ub984 \ub610\ub294 \uc77c\uce58 \uc18d\uc131(\ub610\ub294 \ub458 \ub2e4)\uc774 \uc788\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

    // Note to translators:  "exclude-result-prefixes" is an XSLT keyword which
    // should not be translated.  The message indicates that a namespace prefix
    // encountered as part of the value of the exclude-result-prefixes attribute
    // was in error.
    // ER_INVALID_PREFIX - new error message for bugzilla #788

  // Prefix in exclude-result-prefixes is not valid: {0}
//  public static final int ER_INVALID_PREFIX          = 252;
    { ER_INVALID_PREFIX,
     "exclude-result-prefixes\uc5d0 \uc788\ub294 \uc811\ub450\ubd80\uac00 \uc62c\ubc14\ub974\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4: {0}"},

    // Note to translators:  An "attribute set" is a set of attributes that can be
    // added to an element in the output document as a group.  The message indicates
    // that there was a reference to an attribute set named {0} that was never
    // defined.
    // ER_NO_ATTRIB_SET - new error message for bugzilla #782

  // attribute-set named {0} does not exist
//  public static final int ER_NO_ATTRIB_SET           = 253;
    { ER_NO_ATTRIB_SET,
     "\uc774\ub984\uc774 {0}\uc778 attribute-set\uac00 \uc5c6\uc2b5\ub2c8\ub2e4."},





  // Warnings...

  /** WG_FOUND_CURLYBRACE          */
//  public static final int WG_FOUND_CURLYBRACE = 1;
    { WG_FOUND_CURLYBRACE,
      "'}'\uac00 \ubc1c\uacac\ub418\uc5c8\uc73c\ub098 \uc5f4\ub9b0 \uc18d\uc131 \ud15c\ud50c\ub9ac\ud2b8\uac00 \uc5c6\uc2b5\ub2c8\ub2e4!"},

  /** WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR          */
//  public static final int WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR = 2;

    { WG_COUNT_ATTRIB_MATCHES_NO_ANCESTOR,
      "\uacbd\uace0: \uacc4\uc218 \uc18d\uc131\uc774 xsl:number\uc758 \uc0c1\uc704 \uc694\uc18c\uc640 \uc77c\uce58\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4! \ub300\uc0c1 = {0}"},

  /** WG_EXPR_ATTRIB_CHANGED_TO_SELECT          */
//  public static final int WG_EXPR_ATTRIB_CHANGED_TO_SELECT = 3;

    { WG_EXPR_ATTRIB_CHANGED_TO_SELECT,
      "\uc774\uc804 \uad6c\ubb38: 'expr' \uc18d\uc131\uc758 \uc774\ub984\uc774 'select'\ub85c \ubcc0\uacbd\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},

  /** WG_NO_LOCALE_IN_FORMATNUMBER          */
//  public static final int WG_NO_LOCALE_IN_FORMATNUMBER = 4;

    { WG_NO_LOCALE_IN_FORMATNUMBER,
      "Xalan\uc774 \uc544\uc9c1 format-number \ud568\uc218\uc5d0 \uc788\ub294 \ub85c\ucf00\uc77c \uc774\ub984\uc744 \ucc98\ub9ac\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

  /** WG_LOCALE_NOT_FOUND          */
//  public static final int WG_LOCALE_NOT_FOUND = 5;

    { WG_LOCALE_NOT_FOUND,
      "\uacbd\uace0: xml:lang={0}\uc5d0 \ub300\ud55c \ub85c\ucf00\uc77c\uc744 \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** WG_CANNOT_MAKE_URL_FROM          */
//  public static final int WG_CANNOT_MAKE_URL_FROM = 6;

    { WG_CANNOT_MAKE_URL_FROM,
      "{0}\uc5d0\uc11c URL\uc744 \uc791\uc131\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** WG_CANNOT_LOAD_REQUESTED_DOC          */
//  public static final int WG_CANNOT_LOAD_REQUESTED_DOC = 7;

    { WG_CANNOT_LOAD_REQUESTED_DOC,
      "\uc694\uccad\ub41c \ubb38\uc11c {0}\uc744(\ub97c) \ub85c\ub4dc\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** WG_CANNOT_FIND_COLLATOR          */
//  public static final int WG_CANNOT_FIND_COLLATOR = 8;
    { WG_CANNOT_FIND_COLLATOR,
      "<sort xml:lang={0}\uc5d0 \ub300\ud55c Collator\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

  /** WG_FUNCTIONS_SHOULD_USE_URL          */
//  public static final int WG_FUNCTIONS_SHOULD_USE_URL = 9;

    { WG_FUNCTIONS_SHOULD_USE_URL,
      "\uc774\uc804 \uad6c\ubb38: \ud568\uc218 \uba85\ub839\uc5b4\ub294 {0}\uc758 url\uc744 \uc0ac\uc6a9\ud574\uc57c \ud569\ub2c8\ub2e4."},

  /** WG_ENCODING_NOT_SUPPORTED_USING_UTF8          */
//  public static final int WG_ENCODING_NOT_SUPPORTED_USING_UTF8 = 10;

    { WG_ENCODING_NOT_SUPPORTED_USING_UTF8,
      "\uc778\ucf54\ub529\uc774 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4: {0}, UTF-8 \uc0ac\uc6a9"},

  /** WG_ENCODING_NOT_SUPPORTED_USING_JAVA          */
//  public static final int WG_ENCODING_NOT_SUPPORTED_USING_JAVA = 11;

    { WG_ENCODING_NOT_SUPPORTED_USING_JAVA,
      "\uc778\ucf54\ub529\uc774 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4: {0}, Java {1} \uc0ac\uc6a9"},

  /** WG_SPECIFICITY_CONFLICTS          */
//  public static final int WG_SPECIFICITY_CONFLICTS = 12;

    { WG_SPECIFICITY_CONFLICTS,
      "\ud2b9\uc131 \ucda9\ub3cc\uc774 \ubc1c\uacac\ub418\uc5c8\uc2b5\ub2c8\ub2e4: {0} \uc2a4\ud0c0\uc77c \uc2dc\ud2b8\uc5d0\uc11c \ub9c8\uc9c0\ub9c9\uc73c\ub85c \ubc1c\uacac\ub41c \uac83\uc774 \uc0ac\uc6a9\ub429\ub2c8\ub2e4."},

  /** WG_PARSING_AND_PREPARING          */
//  public static final int WG_PARSING_AND_PREPARING = 13;

    { WG_PARSING_AND_PREPARING,
      "========= \uad6c\ubb38 \ubd84\uc11d \ubc0f \uc900\ube44 {0} =========="},

  /** WG_ATTR_TEMPLATE          */
//  public static final int WG_ATTR_TEMPLATE = 14;

    { WG_ATTR_TEMPLATE,
     "Attr \ud15c\ud50c\ub9ac\ud2b8, {0}"},

  /** WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESPACE          */
//  public static final int WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESPACE = 15;

    { WG_CONFLICT_BETWEEN_XSLSTRIPSPACE_AND_XSLPRESERVESPACE,
      "xsl:strip-space \ubc0f xsl:preserve-space \uc0ac\uc774\uc758 \uc77c\uce58 \ucda9\ub3cc"},

  /** WG_ATTRIB_NOT_HANDLED          */
//  public static final int WG_ATTRIB_NOT_HANDLED = 16;

    { WG_ATTRIB_NOT_HANDLED,
      "Xalan\uc774 \uc544\uc9c1 {0} \uc18d\uc131\uc744 \ucc98\ub9ac\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!"},

  /** WG_NO_DECIMALFORMAT_DECLARATION          */
//  public static final int WG_NO_DECIMALFORMAT_DECLARATION = 17;

    { WG_NO_DECIMALFORMAT_DECLARATION,
      "10\uc9c4\uc218 \ud3ec\ub9f7\uc5d0 \ub300\ud55c \uc120\uc5b8\uc774 \uc5c6\uc2b5\ub2c8\ub2e4: {0}"},

  /** WG_OLD_XSLT_NS          */
//  public static final int WG_OLD_XSLT_NS = 18;

    { WG_OLD_XSLT_NS,
     "XSLT \uc774\ub984 \uacf5\uac04\uc774 \ub204\ub77d\ub418\uc5c8\uac70\ub098 \uc62c\ubc14\ub974\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. "},

  /** WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED          */
//  public static final int WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED = 19;

    { WG_ONE_DEFAULT_XSLDECIMALFORMAT_ALLOWED,
      "\ud558\ub098\uc758 \uae30\ubcf8 xsl:decimal-format \uc120\uc5b8\ub9cc \ud5c8\uc6a9\ub429\ub2c8\ub2e4."},

  /** WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE          */
//  public static final int WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE = 20;

    { WG_XSLDECIMALFORMAT_NAMES_MUST_BE_UNIQUE,
      "xsl:decimal-format \uc774\ub984\uc774 \uace0\uc720\ud574\uc57c \ud569\ub2c8\ub2e4. \"{0}\" \uc774\ub984\uc774 \uc911\ubcf5\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},

  /** WG_ILLEGAL_ATTRIBUTE          */
  //public static final int WG_ILLEGAL_ATTRIBUTE = 21;

    { WG_ILLEGAL_ATTRIBUTE,
      "{0}\uc5d0 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc740 \uc18d\uc131 {1}\uc774(\uac00) \uc788\uc2b5\ub2c8\ub2e4."},

  /** WG_COULD_NOT_RESOLVE_PREFIX          */
//  public static final int WG_COULD_NOT_RESOLVE_PREFIX = 22;

    { WG_COULD_NOT_RESOLVE_PREFIX,
      "\uc774\ub984 \uacf5\uac04 \uc811\ub450\ubd80\ub97c \ubd84\uc11d\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4: {0}. \ub178\ub4dc\uac00 \ubb34\uc2dc\ub429\ub2c8\ub2e4."},

  /** WG_STYLESHEET_REQUIRES_VERSION_ATTRIB          */
//  public static final int WG_STYLESHEET_REQUIRES_VERSION_ATTRIB = 23;
    { WG_STYLESHEET_REQUIRES_VERSION_ATTRIB,
      "xsl:stylesheet\uc5d0 'version' \uc18d\uc131\uc774 \ud544\uc694\ud569\ub2c8\ub2e4!"},

  /** WG_ILLEGAL_ATTRIBUTE_NAME          */
//  public static final int WG_ILLEGAL_ATTRIBUTE_NAME = 24;

    { WG_ILLEGAL_ATTRIBUTE_NAME,
      "\uc720\ud6a8\ud558\uc9c0 \uc54a\uc740 \uc18d\uc131 \uc774\ub984: {0}"},

  /** WG_ILLEGAL_ATTRIBUTE_VALUE          */
//  public static final int WG_ILLEGAL_ATTRIBUTE_VALUE = 25;
    { WG_ILLEGAL_ATTRIBUTE_VALUE,
      "{0} \uc18d\uc131\uc5d0 \ub300\ud574 \uc0ac\uc6a9\ub41c \uc720\ud6a8\ud558\uc9c0 \uc54a\uc740 \uac12: {1}"},

  /** WG_EMPTY_SECOND_ARG          */
//  public static final int WG_EMPTY_SECOND_ARG = 26;

    { WG_EMPTY_SECOND_ARG,
      "document \ud568\uc218 \ub450 \ubc88\uc9f8 \uc778\uc218\ub85c\ubd80\ud130\uc758 \uacb0\uacfc nodeset\uac00 \ube44\uc5b4 \uc788\uc2b5\ub2c8\ub2e4. \ube48 \ub178\ub4dc \uc138\ud2b8\ub97c \ub9ac\ud134\ud558\uc2ed\uc2dc\uc624. "},

  //Following are the new WARNING keys added in XALAN code base after Jdk 1.4 (Xalan 2.2-D11)

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.
    // WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML


  /** WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML          */
//  public static final int WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML = 27;
    { WG_PROCESSINGINSTRUCTION_NAME_CANT_BE_XML,
      "xsl:processing-instruction\uc758 'name' \uc18d\uc131\uac12\uc740 'xml'\uc77c \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    // Note to translators:  "name" and "xsl:processing-instruction" are keywords
    // and must not be translated.  "NCName" is an XML data-type and must not be
    // translated.
    // WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME

  /** WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME          */
//  public static final int WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME = 28;
    { WG_PROCESSINGINSTRUCTION_NOTVALID_NCNAME,
      "xsl:processing-instruction\uc758 'name' \uc18d\uc131\uac12\uc774 \uc62c\ubc14\ub978 NCName\uc774\uc5b4\uc57c \ud569\ub2c8\ub2e4: {0}"},

    // Note to translators:  This message is reported if the stylesheet that is
    // being processed attempted to construct an XML document with an attribute in a
    // place other than on an element.  The substitution text specifies the name of
    // the attribute.
    // WG_ILLEGAL_ATTRIBUTE_POSITION

  /** WG_ILLEGAL_ATTRIBUTE_POSITION         */
//  public static final int WG_ILLEGAL_ATTRIBUTE_POSITION = 29;
    { WG_ILLEGAL_ATTRIBUTE_POSITION,
      "\ud558\uc704 \ub178\ub4dc\uac00 \uc0dd\uc131\ub41c \uc774\ud6c4 \ub610\ub294 \uc694\uc18c\uac00 \uc791\uc131\ub418\uae30 \uc774\uc804\uc5d0 {0} \uc18d\uc131\uc744 \ucd94\uac00\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4. \uc18d\uc131\uc774 \ubb34\uc2dc\ub429\ub2c8\ub2e4."},

    //Check: WHY THERE IS A GAP B/W NUMBERS in the XSLTErrorResources properties file?

  // Other miscellaneous text used inside the code...
  { "ui_language", "ko"},
  {  "help_language",  "ko" },
  {  "language",  "ko" },
  { "BAD_CODE", "createMessage\uc5d0 \ub300\ud55c \ub9e4\uac1c\ubcc0\uc218\uac00 \ubc94\uc704\ub97c \ubc97\uc5b4\ub0a9\ub2c8\ub2e4."},
  {  "FORMAT_FAILED", "messageFormat \ud638\ucd9c \uc911 \uc608\uc678 \ubc1c\uc0dd"},
  {  "version", ">>>>>>> Xalan \ubc84\uc804 "},
  {  "version2",  "<<<<<<<"},
  {  "yes", "\uc608"},
  { "line", "\ud589 #"},
  { "column","\uc5f4 #"},
  { "xsldone", "XSLProcessor: \uc644\ub8cc"},


  // Note to translators:  The following messages provide usage information
  // for the Xalan Process command line.  "Process" is the name of a Java class,
  // and should not be translated.
  { "xslProc_option", "Xalan-J \uba85\ub839\ud589 \ud504\ub85c\uc138\uc2a4 \ud074\ub798\uc2a4 \uc635\uc158:"},
  { "xslProc_option", "Xalan-J \uba85\ub839\ud589 \ud504\ub85c\uc138\uc2a4 \ud074\ub798\uc2a4 \uc635\uc158\u003a"},
  { "xslProc_invalid_xsltc_option", "{0} \uc635\uc158\uc774 XSLTC \ubaa8\ub4dc\uc5d0\uc11c \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},
  { "xslProc_invalid_xalan_option", "{0} \uc635\uc158\uc740 -XSLTC\ub85c\ub9cc \uc0ac\uc6a9\ub420 \uc218 \uc788\uc2b5\ub2c8\ub2e4."},
  { "xslProc_no_input", "\uc624\ub958: \uc9c0\uc815\ub41c \uc2a4\ud0c0\uc77c \uc2dc\ud2b8 \ub610\ub294 \uc785\ub825 xml\uc774 \uc5c6\uc2b5\ub2c8\ub2e4. \uc0ac\uc6a9\ubc95 \uba85\ub839\uc5b4\uc5d0 \ub300\ud55c \uc635\uc158 \uc5c6\uc774 \uc774 \uba85\ub839\uc744 \uc2e4\ud589\ud558\uc2ed\uc2dc\uc624."},
  { "xslProc_common_options", "-\uc77c\ubc18 \uc635\uc158-"},
  { "xslProc_xalan_options", "-Xalan\uc5d0 \ub300\ud55c \uc635\uc158-"},
  { "xslProc_xsltc_options", "-XSLTC\uc5d0 \ub300\ud55c \uc635\uc158-"},
  { "xslProc_return_to_continue", "(\uacc4\uc18d\ud558\ub824\uba74 Enter \ud0a4\ub97c \ub204\ub974\uc2ed\uc2dc\uc624)"},

   // Note to translators: The option name and the parameter name do not need to
   // be translated. Only translate the messages in parentheses.  Note also that
   // leading whitespace in the messages is used to indent the usage information
   // for each option in the English messages.
   // Do not translate the keywords: XSLTC, SAX, DOM and DTM.
  { "optionXSLTC", "[-XSLTC (\ubcc0\ud658\uc5d0 \ub300\ud574 XSLTC \uc0ac\uc6a9)]"},
  { "optionIN", "[-IN inputXMLURL]"},
  { "optionXSL", "[-XSL XSLTransformationURL]"},
  { "optionOUT",  "[-OUT outputFileName]"},
  { "optionLXCIN", "[-LXCIN compiledStylesheetFileNameIn]"},
  { "optionLXCOUT", "[-LXCOUT compiledStylesheetFileNameOutOut]"},
  { "optionPARSER", "[-PARSER \uad6c\ubb38 \ubd84\uc11d\uae30 liaison\uc758 \uc644\uc804\ud55c \ud074\ub798\uc2a4 \uc774\ub984]"},
  {  "optionE", "[-V (\uc5d4\ud2f0\ud2f0 ref\ub97c \ud3bc\uce58\uc9c0 \uc54a\uc74c)]"},
  {  "optionV",  "[-V (\uc5d4\ud2f0\ud2f0 ref\ub97c \ud3bc\uce58\uc9c0 \uc54a\uc74c)]"},
  {  "optionQC", "[-QC (\uc790\ub3d9 \ud328\ud134 \ucda9\ub3cc \uacbd\uace0)]"},
  {  "optionQ", "[-Q  (\uc790\ub3d9 \ubaa8\ub4dc)]"},
  {  "optionLF", "[-LF (\ucd9c\ub825\uc5d0\uc11c\ub9cc \uc904\ubc14\uafb8\uae30 \uc0ac\uc6a9 {\uae30\ubcf8\uac12\uc740 CR/LF \uc785\ub2c8\ub2e4})]"},
  {  "optionCR", "[-CR (\ucd9c\ub825\uc5d0\uc11c\ub9cc \uce90\ub9ac\uc9c0 \ub9ac\ud134 \uc0ac\uc6a9 {\uae30\ubcf8\uac12\uc740 CR/LF \uc785\ub2c8\ub2e4})]"},
  { "optionESCAPE", "[-ESCAPE (\uc5d0\uc2a4\ucf00\uc774\ud504\ud560 \ubb38\uc790 {\uae30\ubcf8\uac12\uc740 <>&\"\'\\r\\n \uc785\ub2c8\ub2e4}]"},
  { "optionINDENT", "[-INDENT (\ub4e4\uc5ec\uc4f0\uae30\ud560 \uacf5\ubc31 \uc218 \uc81c\uc5b4 {\uae30\ubcf8\uac12\uc740 0 \uc785\ub2c8\ub2e4})]"},
  { "optionTT", "[-TT (\ud15c\ud50c\ub9ac\ud2b8 \ud638\ucd9c \uc2dc \ud15c\ud50c\ub9ac\ud2b8 \ucd94\uc801.)]"},
  { "optionTG", "[-TG (\uac01 \uc0dd\uc131 \uc774\ubca4\ud2b8 \ucd94\uc801.)]"},
  { "optionTS", "[-TS (\uac01 \uc120\ud0dd \uc774\ubca4\ud2b8 \ucd94\uc801.)]"},
  {  "optionTTC", "[-TTC (\ud558\uc704 \ud15c\ud50c\ub9ac\ud2b8 \ucc98\ub9ac \uc2dc \ud558\uc704 \ud15c\ud50c\ub9ac\ud2b8 \ucd94\uc801.)]"},
  { "optionTCLASS", "[-TCLASS (\ucd94\uc801 \ud655\uc7a5\uc790\uc5d0 \ub300\ud55c TraceListener \ud074\ub798\uc2a4.)]"},
  { "optionVALIDATE", "[-VALIDATE (\uc720\ud6a8\uc131 \uac80\uc99d \ubc1c\uc0dd \uc5ec\ubd80 \uc124\uc815.  \uae30\ubcf8\uc801\uc73c\ub85c\ub294 \uc720\ud6a8\uc131 \uac80\uc99d\uc774 off\ub85c \uc124\uc815\ub428.)]"},
  { "optionEDUMP", "[-EDUMP {optional filename} (\uc624\ub958 \uc2dc stackdump \uc218\ud589.)]"},
  {  "optionXML", "[-XML (XML \ud3ec\ub9f7\ud130\ub97c \uc0ac\uc6a9\ud558\uace0 XML \uba38\ub9ac\uae00 \ucd94\uac00.)]"},
  {  "optionTEXT", "[-TEXT (\ub2e8\uc21c \ud14d\uc2a4\ud2b8 \ud3ec\ub9f7\ud130 \uc0ac\uc6a9.)]"},
  {  "optionHTML", "[-HTML (HTML \ud3ec\ub9f7\ud130 \uc0ac\uc6a9.)]"},
  {  "optionPARAM", "[-PARAM name expression (\uc2a4\ud0c0\uc77c \uc2dc\ud2b8 \ub9e4\uac1c\ubcc0\uc218 \uc124\uc815)]"},
  {  "noParsermsg1", "XSL \ud504\ub85c\uc138\uc2a4\uac00 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4."},
  {  "noParsermsg2", "** \uad6c\ubb38 \ubd84\uc11d\uae30\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc74c **"},
  { "noParsermsg3",  "\ud074\ub798\uc2a4 \uacbd\ub85c\ub97c \uc810\uac80\ud558\uc2ed\uc2dc\uc624."},
  { "noParsermsg4", "Java\uc6a9 IBM XML \uad6c\ubb38 \ubd84\uc11d\uae30\uac00 \uc5c6\uc73c\uba74"},
  { "noParsermsg5", "IBM's AlphaWorks: http://www.alphaworks.ibm.com/formula/xml \uc5d0\uc11c \ub2e4\uc6b4\ub85c\ub4dc \ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4."},
  { "optionURIRESOLVER", "[-URIRESOLVER full class name (URIResolver\ub97c \uc0ac\uc6a9\ud558\uc5ec URI \ubd84\uc11d)]"},
  { "optionENTITYRESOLVER",  "[-ENTITYRESOLVER full class name (EntityResolver\ub97c \uc0ac\uc6a9\ud558\uc5ec \uc5d4\ud2f0\ud2f0 \ubd84\uc11d)]"},
  { "optionCONTENTHANDLER",  "[-CONTENTHANDLER full class name (ContentHandler\ub97c \uc0ac\uc6a9\ud558\uc5ec \ucd9c\ub825 \uc9c1\ub82c\ud654)]"},
  {  "optionLINENUMBERS",  "[-L \uc18c\uc2a4 \ubb38\uc11c\uc5d0 \ud589 \ubc88\ud638 \uc0ac\uc6a9]"},

    // Following are the new options added in XSLTErrorResources.properties files after Jdk 1.4 (Xalan 2.2-D11)


  {  "optionMEDIA",  "   [-MEDIA mediaType(\ub9e4\uccb4 \uc18d\uc131\uc744 \uc0ac\uc6a9\ud558\uc5ec \ubb38\uc11c\uc640 \uc5f0\uad00\ub41c \uc2a4\ud0c0\uc77c \uc2dc\ud2b8 \ucc3e\uae30.)]"},
  {  "optionFLAVOR",  "   [-FLAVOR flavorName(\uba85\uc2dc\uc801\uc73c\ub85c s2s=SAX \ub610\ub294 d2d=DOM\uc744 \uc0ac\uc6a9\ud558\uc5ec \ubcc0\ud658 \uc218\ud589.)] "}, // Added by sboag/scurcuru; experimental
  { "optionDIAG", "   [-DIAG(\ubcc0\ud658\uc5d0 \uc18c\uc694\ub41c \uc804\uccb4 \ubc00\ub9ac\ucd08 \uc778\uc1c4.)]"},
  { "optionINCREMENTAL",  "   [-INCREMENTAL(http://xml.apache.org/xalan/features/incremental\uc744 true\ub85c \uc124\uc815\ud558\uc5ec \uc99d\ubd84 DTM \uad6c\uc131 \uc694\uccad.)]"},
  {  "optionNOOPTIMIMIZE",  "   [-NOOPTIMIMIZE(http://xml.apache.org/xalan/features/optimize\ub97c false\ub85c \uc124\uc815\ud558\uc5ec \uc2a4\ud0c0\uc77c \uc2dc\ud2b8 \ucd5c\uc801\ud654 \ucc98\ub9ac\ub97c \uc694\uccad \uc548\ud568.)]"},
  { "optionRL",  "   [-RL recursionlimit(\uc2a4\ud0c0\uc77c \uc2dc\ud2b8 \ubc18\ubcf5 \uc815\ub3c4\uc5d0 \ub300\ud55c \uc22b\uc790 \ud55c\uacc4 \ub2e8\uc5b8.)]"},
  {   "optionXO",  "[-XO [transletName](\uc0dd\uc131\ub41c translet\uc5d0 \uc774\ub984 \uc9c0\uc815)]"},
  {  "optionXD", "[-XD destinationDirectory(translet\uc5d0 \ub300\ud574 \ub300\uc0c1 \ub514\ub809\ud1a0\ub9ac \uc9c0\uc815)]"},
  {  "optionXJ",  "[-XJ jarfile(\uc774\ub984\uc774 <jarfile>\uc778 jar \ud30c\uc77c\ub85c translet \ud074\ub798\uc2a4 \ud328\ud0a4\uc9c0)]"},
  {   "optionXP",  "[-XP package(\uc0dd\uc131\ub41c \ubaa8\ub4e0 translet \ud074\ub798\uc2a4\uc5d0 \ub300\ud574 \ud328\ud0a4\uc9c0 \uc774\ub984 \uc811\ub450\ubd80 \uc9c0\uc815)]"},

  //AddITIONAL  STRINGS that need L10n
  // Note to translators:  The following message describes usage of a particular
  // command-line option that is used to enable the "template inlining"
  // optimization.  The optimization involves making a copy of the code
  // generated for a template in another template that refers to it.
  { "optionXN",  "[-XN(\ud15c\ud50c\ub9ac\ud2b8 \uc778\ub77c\uc774\ub2dd \uc0ac\uc6a9 \uac00\ub2a5)]" },
  { "optionXX",  "[-XX(\ucd94\uac00 \ub514\ubc84\uae45 \uba54\uc2dc\uc9c0 \ucd9c\ub825 \ucf1c\uae30)]"},
  { "optionXT" , "[-XT(\uac00\ub2a5\ud55c \uacbd\uc6b0, translet\uc744 \uc0ac\uc6a9\ud558\uc5ec \ubcc0\ud658)]"},
  { "diagTiming","--------- {1}\uc744(\ub97c) \ud1b5\ud55c {0} \ubcc0\ud658\uc5d0 {2}ms\uac00 \uc18c\uc694\ub418\uc5c8\uc74c" },
  { "recursionTooDeep","\ud15c\ud50c\ub9ac\ud2b8 \uc911\ucca9\uc774 \ub108\ubb34 \ub9ce\uc2b5\ub2c8\ub2e4. \uc911\ucca9 = {0}, \ud15c\ud50c\ub9ac\ud2b8 {1} {2}" },
  { "nameIs", "\uc774\ub984\uc740" },
  { "matchPatternIs", "\uc77c\uce58 \ud328\ud134\uc740" }

  };
  }


  /** String for use when a bad error code was encountered.    */
  public static final String BAD_CODE = "BAD_CODE";

  /** String for use when formatting of the error string failed.   */
  public static final String FORMAT_FAILED = "FORMAT_FAILED";

  /** General error string.   */
  public static final String ERROR_STRING = "#error";

  /** String to prepend to error messages.  */
  public static final String ERROR_HEADER = "\uc624\ub958: ";

  /** String to prepend to warning messages.    */
  public static final String WARNING_HEADER = "\uacbd\uace0: ";

  /** String to specify the XSLT module.  */
  public static final String XSL_HEADER = "XSLT ";

  /** String to specify the XML parser module.  */
  public static final String XML_HEADER = "XML ";

  /** I don't think this is used any more.
   * @deprecated  */
  public static final String QUERY_HEADER = "PATTERN ";


  /**
   *   Return a named ResourceBundle for a particular locale.  This method mimics the behavior
   *   of ResourceBundle.getBundle().
   *
   *   @param className the name of the class that implements the resource bundle.
   *   @return the ResourceBundle
   *   @throws MissingResourceException
   */
  public static final XSLTErrorResources loadResourceBundle(String className)
          throws MissingResourceException
  {

    Locale locale = Locale.getDefault();
    String suffix = getResourceSuffix(locale);

    try
    {

      // first try with the given locale
      return (XSLTErrorResources) ResourceBundle.getBundle(className
              + suffix, locale);
    }
    catch (MissingResourceException e)
    {
      try  // try to fall back to en_US if we can't load
      {

        // Since we can't find the localized property file,
        // fall back to en_US.
        return (XSLTErrorResources) ResourceBundle.getBundle(className,
                new Locale("ko", "KR"));
      }
      catch (MissingResourceException e2)
      {

        // Now we are really in trouble.
        // very bad, definitely very bad...not going to get very far
        throw new MissingResourceException(
          "Could not load any resource bundles.", className, "");
      }
    }
  }

  /**
   * Return the resource file suffic for the indicated locale
   * For most locales, this will be based the language code.  However
   * for Chinese, we do distinguish between Taiwan and PRC
   *
   * @param locale the locale
   * @return an String suffix which canbe appended to a resource name
   */
  private static final String getResourceSuffix(Locale locale)
  {

    String suffix = "_" + locale.getLanguage();
    String country = locale.getCountry();

    if (country.equals("TW"))
      suffix += "_" + country;

    return suffix;
  }


}
