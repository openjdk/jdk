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
 * $Id: XMLErrorResources_zh_CN.java,v 1.2.4.1 2005/09/15 07:45:47 suresh_emailid Exp $
 */
package com.sun.org.apache.xml.internal.res;


import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Set up error messages.
 * We build a two dimensional array of message keys and
 * message strings. In order to add a new message here,
 * you need to first add a String constant. And you need
 * to enter key, value pair as part of the contents
 * array. You also need to update MAX_CODE for error strings
 * and MAX_WARNING for warnings ( Needed for only information
 * purpose )
 */
public class XMLErrorResources_zh_CN extends ListResourceBundle
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
  public static final int MAX_CODE = 61;

  /** Maximum warnings, this is needed to keep track of the number of warnings.          */
  public static final int MAX_WARNING = 0;

  /** Maximum misc strings.   */
  public static final int MAX_OTHERS = 4;

  /** Maximum total warnings and error messages.          */
  public static final int MAX_MESSAGES = MAX_CODE + MAX_WARNING + 1;


  /*
   * Message keys
   */
  public static final String ER_FUNCTION_NOT_SUPPORTED = "ER_FUNCTION_NOT_SUPPORTED";
  public static final String ER_CANNOT_OVERWRITE_CAUSE = "ER_CANNOT_OVERWRITE_CAUSE";
  public static final String ER_NO_DEFAULT_IMPL = "ER_NO_DEFAULT_IMPL";
  public static final String ER_CHUNKEDINTARRAY_NOT_SUPPORTED = "ER_CHUNKEDINTARRAY_NOT_SUPPORTED";
  public static final String ER_OFFSET_BIGGER_THAN_SLOT = "ER_OFFSET_BIGGER_THAN_SLOT";
  public static final String ER_COROUTINE_NOT_AVAIL = "ER_COROUTINE_NOT_AVAIL";
  public static final String ER_COROUTINE_CO_EXIT = "ER_COROUTINE_CO_EXIT";
  public static final String ER_COJOINROUTINESET_FAILED = "ER_COJOINROUTINESET_FAILED";
  public static final String ER_COROUTINE_PARAM = "ER_COROUTINE_PARAM";
  public static final String ER_PARSER_DOTERMINATE_ANSWERS = "ER_PARSER_DOTERMINATE_ANSWERS";
  public static final String ER_NO_PARSE_CALL_WHILE_PARSING = "ER_NO_PARSE_CALL_WHILE_PARSING";
  public static final String ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED = "ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED";
  public static final String ER_ITERATOR_AXIS_NOT_IMPLEMENTED = "ER_ITERATOR_AXIS_NOT_IMPLEMENTED";
  public static final String ER_ITERATOR_CLONE_NOT_SUPPORTED = "ER_ITERATOR_CLONE_NOT_SUPPORTED";
  public static final String ER_UNKNOWN_AXIS_TYPE = "ER_UNKNOWN_AXIS_TYPE";
  public static final String ER_AXIS_NOT_SUPPORTED = "ER_AXIS_NOT_SUPPORTED";
  public static final String ER_NO_DTMIDS_AVAIL = "ER_NO_DTMIDS_AVAIL";
  public static final String ER_NOT_SUPPORTED = "ER_NOT_SUPPORTED";
  public static final String ER_NODE_NON_NULL = "ER_NODE_NON_NULL";
  public static final String ER_COULD_NOT_RESOLVE_NODE = "ER_COULD_NOT_RESOLVE_NODE";
  public static final String ER_STARTPARSE_WHILE_PARSING = "ER_STARTPARSE_WHILE_PARSING";
  public static final String ER_STARTPARSE_NEEDS_SAXPARSER = "ER_STARTPARSE_NEEDS_SAXPARSER";
  public static final String ER_COULD_NOT_INIT_PARSER = "ER_COULD_NOT_INIT_PARSER";
  public static final String ER_EXCEPTION_CREATING_POOL = "ER_EXCEPTION_CREATING_POOL";
  public static final String ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE = "ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE";
  public static final String ER_SCHEME_REQUIRED = "ER_SCHEME_REQUIRED";
  public static final String ER_NO_SCHEME_IN_URI = "ER_NO_SCHEME_IN_URI";
  public static final String ER_NO_SCHEME_INURI = "ER_NO_SCHEME_INURI";
  public static final String ER_PATH_INVALID_CHAR = "ER_PATH_INVALID_CHAR";
  public static final String ER_SCHEME_FROM_NULL_STRING = "ER_SCHEME_FROM_NULL_STRING";
  public static final String ER_SCHEME_NOT_CONFORMANT = "ER_SCHEME_NOT_CONFORMANT";
  public static final String ER_HOST_ADDRESS_NOT_WELLFORMED = "ER_HOST_ADDRESS_NOT_WELLFORMED";
  public static final String ER_PORT_WHEN_HOST_NULL = "ER_PORT_WHEN_HOST_NULL";
  public static final String ER_INVALID_PORT = "ER_INVALID_PORT";
  public static final String ER_FRAG_FOR_GENERIC_URI ="ER_FRAG_FOR_GENERIC_URI";
  public static final String ER_FRAG_WHEN_PATH_NULL = "ER_FRAG_WHEN_PATH_NULL";
  public static final String ER_FRAG_INVALID_CHAR = "ER_FRAG_INVALID_CHAR";
  public static final String ER_PARSER_IN_USE = "ER_PARSER_IN_USE";
  public static final String ER_CANNOT_CHANGE_WHILE_PARSING = "ER_CANNOT_CHANGE_WHILE_PARSING";
  public static final String ER_SELF_CAUSATION_NOT_PERMITTED = "ER_SELF_CAUSATION_NOT_PERMITTED";
  public static final String ER_NO_USERINFO_IF_NO_HOST = "ER_NO_USERINFO_IF_NO_HOST";
  public static final String ER_NO_PORT_IF_NO_HOST = "ER_NO_PORT_IF_NO_HOST";
  public static final String ER_NO_QUERY_STRING_IN_PATH = "ER_NO_QUERY_STRING_IN_PATH";
  public static final String ER_NO_FRAGMENT_STRING_IN_PATH = "ER_NO_FRAGMENT_STRING_IN_PATH";
  public static final String ER_CANNOT_INIT_URI_EMPTY_PARMS = "ER_CANNOT_INIT_URI_EMPTY_PARMS";
  public static final String ER_METHOD_NOT_SUPPORTED ="ER_METHOD_NOT_SUPPORTED";
  public static final String ER_INCRSAXSRCFILTER_NOT_RESTARTABLE = "ER_INCRSAXSRCFILTER_NOT_RESTARTABLE";
  public static final String ER_XMLRDR_NOT_BEFORE_STARTPARSE = "ER_XMLRDR_NOT_BEFORE_STARTPARSE";
  public static final String ER_AXIS_TRAVERSER_NOT_SUPPORTED = "ER_AXIS_TRAVERSER_NOT_SUPPORTED";
  public static final String ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER = "ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER";
  public static final String ER_SYSTEMID_UNKNOWN = "ER_SYSTEMID_UNKNOWN";
  public static final String ER_LOCATION_UNKNOWN = "ER_LOCATION_UNKNOWN";
  public static final String ER_PREFIX_MUST_RESOLVE = "ER_PREFIX_MUST_RESOLVE";
  public static final String ER_CREATEDOCUMENT_NOT_SUPPORTED = "ER_CREATEDOCUMENT_NOT_SUPPORTED";
  public static final String ER_CHILD_HAS_NO_OWNER_DOCUMENT = "ER_CHILD_HAS_NO_OWNER_DOCUMENT";
  public static final String ER_CHILD_HAS_NO_OWNER_DOCUMENT_ELEMENT = "ER_CHILD_HAS_NO_OWNER_DOCUMENT_ELEMENT";
  public static final String ER_CANT_OUTPUT_TEXT_BEFORE_DOC = "ER_CANT_OUTPUT_TEXT_BEFORE_DOC";
  public static final String ER_CANT_HAVE_MORE_THAN_ONE_ROOT = "ER_CANT_HAVE_MORE_THAN_ONE_ROOT";
  public static final String ER_ARG_LOCALNAME_NULL = "ER_ARG_LOCALNAME_NULL";
  public static final String ER_ARG_LOCALNAME_INVALID = "ER_ARG_LOCALNAME_INVALID";
  public static final String ER_ARG_PREFIX_INVALID = "ER_ARG_PREFIX_INVALID";

  // Message keys used by the serializer
  public static final String ER_RESOURCE_COULD_NOT_FIND = "ER_RESOURCE_COULD_NOT_FIND";
  public static final String ER_RESOURCE_COULD_NOT_LOAD = "ER_RESOURCE_COULD_NOT_LOAD";
  public static final String ER_BUFFER_SIZE_LESSTHAN_ZERO = "ER_BUFFER_SIZE_LESSTHAN_ZERO";
  public static final String ER_INVALID_UTF16_SURROGATE = "ER_INVALID_UTF16_SURROGATE";
  public static final String ER_OIERROR = "ER_OIERROR";
  public static final String ER_NAMESPACE_PREFIX = "ER_NAMESPACE_PREFIX";
  public static final String ER_STRAY_ATTRIBUTE = "ER_STRAY_ATTIRBUTE";
  public static final String ER_STRAY_NAMESPACE = "ER_STRAY_NAMESPACE";
  public static final String ER_COULD_NOT_LOAD_RESOURCE = "ER_COULD_NOT_LOAD_RESOURCE";
  public static final String ER_COULD_NOT_LOAD_METHOD_PROPERTY = "ER_COULD_NOT_LOAD_METHOD_PROPERTY";
  public static final String ER_SERIALIZER_NOT_CONTENTHANDLER = "ER_SERIALIZER_NOT_CONTENTHANDLER";
  public static final String ER_ILLEGAL_ATTRIBUTE_POSITION = "ER_ILLEGAL_ATTRIBUTE_POSITION";

  /*
   * Now fill in the message text.
   * Then fill in the message text for that message code in the
   * array. Use the new error code as the index into the array.
   */

  // Error messages...

  /**
   * Get the lookup table for error messages
   *
   * @return The association list.
   */
  public Object[][] getContents()
  {
    return new Object[][] {

  /** Error message ID that has a null message, but takes in a single object.    */
    {"ER0000" , "{0}" },

    { ER_FUNCTION_NOT_SUPPORTED,
      "\u4e0d\u652f\u6301\u51fd\u6570\uff01"},

    { ER_CANNOT_OVERWRITE_CAUSE,
      "\u65e0\u6cd5\u8986\u76d6\u539f\u56e0"},

    { ER_NO_DEFAULT_IMPL,
      "\u627e\u4e0d\u5230\u7f3a\u7701\u5b9e\u73b0"},

    { ER_CHUNKEDINTARRAY_NOT_SUPPORTED,
      "\u5f53\u524d\u4e0d\u652f\u6301 ChunkedIntArray({0})"},

    { ER_OFFSET_BIGGER_THAN_SLOT,
      "\u504f\u79fb\u5927\u4e8e\u69fd"},

    { ER_COROUTINE_NOT_AVAIL,
      "\u534f\u540c\u7a0b\u5e8f\u4e0d\u53ef\u7528\uff0cid={0}"},

    { ER_COROUTINE_CO_EXIT,
      "CoroutineManager \u63a5\u6536\u5230 co_exit() \u8bf7\u6c42"},

    { ER_COJOINROUTINESET_FAILED,
      "co_joinCoroutineSet() \u5931\u8d25"},

    { ER_COROUTINE_PARAM,
      "\u534f\u540c\u7a0b\u5e8f\u53c2\u6570\u9519\u8bef\uff08{0}\uff09"},

    { ER_PARSER_DOTERMINATE_ANSWERS,
      "\n\u610f\u5916\uff1a\u89e3\u6790\u5668 doTerminate \u5e94\u7b54 {0}"},

    { ER_NO_PARSE_CALL_WHILE_PARSING,
      "\u5206\u6790\u65f6\u53ef\u80fd\u6ca1\u6709\u8c03\u7528 parse"},

    { ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "\u9519\u8bef\uff1a\u6ca1\u6709\u5b9e\u73b0\u4e3a\u8f74 {0} \u8f93\u5165\u7684\u8fed\u4ee3\u5668"},

    { ER_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "\u9519\u8bef\uff1a\u6ca1\u6709\u5b9e\u73b0\u8f74 {0} \u7684\u8fed\u4ee3\u5668"},

    { ER_ITERATOR_CLONE_NOT_SUPPORTED,
      "\u4e0d\u652f\u6301\u8fed\u4ee3\u5668\u514b\u9686"},

    { ER_UNKNOWN_AXIS_TYPE,
      "\u672a\u77e5\u7684\u8f74\u904d\u5386\u7c7b\u578b\uff1a{0}"},

    { ER_AXIS_NOT_SUPPORTED,
      "\u4e0d\u652f\u6301\u8f74\u904d\u5386\u7a0b\u5e8f\uff1a{0}"},

    { ER_NO_DTMIDS_AVAIL,
      "\u65e0\u66f4\u591a\u7684 DTM \u6807\u8bc6\u53ef\u7528"},

    { ER_NOT_SUPPORTED,
      "\u4e0d\u652f\u6301\uff1a{0}"},

    { ER_NODE_NON_NULL,
      "\u8282\u70b9\u5bf9\u4e8e getDTMHandleFromNode \u5fc5\u987b\u662f\u975e\u7a7a\u7684"},

    { ER_COULD_NOT_RESOLVE_NODE,
      "\u65e0\u6cd5\u5c06\u8282\u70b9\u89e3\u6790\u5230\u53e5\u67c4"},

    { ER_STARTPARSE_WHILE_PARSING,
       "\u5206\u6790\u65f6\u53ef\u80fd\u6ca1\u6709\u8c03\u7528 startParse"},

    { ER_STARTPARSE_NEEDS_SAXPARSER,
       "startParse \u9700\u8981\u975e\u7a7a\u7684 SAXParser"},

    { ER_COULD_NOT_INIT_PARSER,
       "\u65e0\u6cd5\u7528\u4ee5\u4e0b\u5de5\u5177\u521d\u59cb\u5316\u89e3\u6790\u5668"},

    { ER_EXCEPTION_CREATING_POOL,
       "\u4e3a\u6c60\u521b\u5efa\u65b0\u5b9e\u4f8b\u65f6\u53d1\u751f\u5f02\u5e38"},

    { ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
       "\u8def\u5f84\u5305\u542b\u65e0\u6548\u7684\u8f6c\u4e49\u5e8f\u5217"},

    { ER_SCHEME_REQUIRED,
       "\u6a21\u5f0f\u662f\u5fc5\u9700\u7684\uff01"},

    { ER_NO_SCHEME_IN_URI,
       "\u5728 URI \u4e2d\u627e\u4e0d\u5230\u6a21\u5f0f\uff1a{0}"},

    { ER_NO_SCHEME_INURI,
       "URI \u4e2d\u672a\u627e\u5230\u6a21\u5f0f"},

    { ER_PATH_INVALID_CHAR,
       "\u8def\u5f84\u5305\u542b\u975e\u6cd5\u5b57\u7b26\uff1a{0}"},

    { ER_SCHEME_FROM_NULL_STRING,
       "\u65e0\u6cd5\u4ece\u7a7a\u5b57\u7b26\u4e32\u8bbe\u7f6e\u6a21\u5f0f"},

    { ER_SCHEME_NOT_CONFORMANT,
       "\u6a21\u5f0f\u4e0d\u4e00\u81f4\u3002"},

    { ER_HOST_ADDRESS_NOT_WELLFORMED,
       "\u4e3b\u673a\u4e0d\u662f\u683c\u5f0f\u826f\u597d\u7684\u5730\u5740"},

    { ER_PORT_WHEN_HOST_NULL,
       "\u4e3b\u673a\u4e3a\u7a7a\u65f6\uff0c\u65e0\u6cd5\u8bbe\u7f6e\u7aef\u53e3"},

    { ER_INVALID_PORT,
       "\u65e0\u6548\u7684\u7aef\u53e3\u53f7"},

    { ER_FRAG_FOR_GENERIC_URI,
       "\u53ea\u80fd\u4e3a\u4e00\u822c URI \u8bbe\u7f6e\u7247\u6bb5"},

    { ER_FRAG_WHEN_PATH_NULL,
       "\u8def\u5f84\u4e3a\u7a7a\u65f6\uff0c\u65e0\u6cd5\u8bbe\u7f6e\u7247\u6bb5"},

    { ER_FRAG_INVALID_CHAR,
       "\u7247\u6bb5\u5305\u542b\u65e0\u6548\u7684\u5b57\u7b26"},

    { ER_PARSER_IN_USE,
      "\u89e3\u6790\u5668\u5df2\u5728\u4f7f\u7528"},

    { ER_CANNOT_CHANGE_WHILE_PARSING,
      "\u5206\u6790\u65f6\u65e0\u6cd5\u66f4\u6539 {0} {1}"},

    { ER_SELF_CAUSATION_NOT_PERMITTED,
      "\u4e0d\u5141\u8bb8\u81ea\u6210\u56e0\u679c\u5173\u7cfb"},

    { ER_NO_USERINFO_IF_NO_HOST,
      "\u5982\u679c\u6ca1\u6709\u6307\u5b9a\u4e3b\u673a\uff0c\u5219\u4e0d\u53ef\u4ee5\u6307\u5b9a Userinfo"},

    { ER_NO_PORT_IF_NO_HOST,
      "\u5982\u679c\u6ca1\u6709\u6307\u5b9a\u4e3b\u673a\uff0c\u5219\u4e0d\u53ef\u4ee5\u6307\u5b9a\u7aef\u53e3"},

    { ER_NO_QUERY_STRING_IN_PATH,
      "\u8def\u5f84\u548c\u67e5\u8be2\u5b57\u7b26\u4e32\u4e2d\u4e0d\u80fd\u6307\u5b9a\u67e5\u8be2\u5b57\u7b26\u4e32"},

    { ER_NO_FRAGMENT_STRING_IN_PATH,
      "\u8def\u5f84\u548c\u7247\u6bb5\u4e2d\u90fd\u65e0\u6cd5\u6307\u5b9a\u7247\u6bb5"},

    { ER_CANNOT_INIT_URI_EMPTY_PARMS,
      "\u65e0\u6cd5\u4ee5\u7a7a\u53c2\u6570\u521d\u59cb\u5316 URI"},

    { ER_METHOD_NOT_SUPPORTED,
      "\u5c1a\u4e0d\u652f\u6301\u65b9\u6cd5"},

    { ER_INCRSAXSRCFILTER_NOT_RESTARTABLE,
      "\u5f53\u524d\u4e0d\u53ef\u91cd\u65b0\u542f\u52a8 IncrementalSAXSource_Filter"},

    { ER_XMLRDR_NOT_BEFORE_STARTPARSE,
      "XMLReader \u4e0d\u5728 startParse \u8bf7\u6c42\u4e4b\u524d"},

    { ER_AXIS_TRAVERSER_NOT_SUPPORTED,
      "\u4e0d\u652f\u6301\u8f74\u904d\u5386\u7a0b\u5e8f\uff1a{0}"},

    { ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER,
      "\u4ee5\u7a7a\u7684 PrintWriter \u521b\u5efa\u4e86 ListingErrorHandler\uff01"},

    { ER_SYSTEMID_UNKNOWN,
      "SystemId \u672a\u77e5"},

    { ER_LOCATION_UNKNOWN,
      "\u9519\u8bef\u4f4d\u7f6e\u672a\u77e5"},

    { ER_PREFIX_MUST_RESOLVE,
      "\u524d\u7f00\u5fc5\u987b\u89e3\u6790\u4e3a\u540d\u79f0\u7a7a\u95f4\uff1a{0}"},

    { ER_CREATEDOCUMENT_NOT_SUPPORTED,
      "XPathContext \u4e2d\u4e0d\u652f\u6301 createDocument ()\uff01"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT,
      "\u5b50\u5c5e\u6027\u6ca1\u6709\u6240\u6709\u8005\u6587\u6863\uff01"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT_ELEMENT,
      "\u5b50\u5c5e\u6027\u6ca1\u6709\u6240\u6709\u8005\u6587\u6863\u5143\u7d20\uff01"},

    { ER_CANT_OUTPUT_TEXT_BEFORE_DOC,
      "\u8b66\u544a\uff1a\u65e0\u6cd5\u8f93\u51fa document \u5143\u7d20\u524d\u7684\u6587\u672c\uff01\u5ffd\u7565..."},

    { ER_CANT_HAVE_MORE_THAN_ONE_ROOT,
      "DOM \u4e0a\u4e0d\u80fd\u6709\u591a\u4e2a\u6839\uff01"},

    { ER_ARG_LOCALNAME_NULL,
       "\u81ea\u53d8\u91cf\u201clocalName\u201d\u4e3a\u7a7a"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The localname is the portion after the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_LOCALNAME_INVALID,
       "QNAME \u4e2d\u7684\u672c\u5730\u540d\u5e94\u5f53\u662f\u6709\u6548\u7684 NCName"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The prefix is the portion before the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_PREFIX_INVALID,
       "QNAME \u4e2d\u7684\u524d\u7f00\u5e94\u5f53\u662f\u6709\u6548\u7684 NCName"},

    { "BAD_CODE", "createMessage \u7684\u53c2\u6570\u8d85\u51fa\u8303\u56f4"},
    { "FORMAT_FAILED", "\u5728 messageFormat \u8c03\u7528\u8fc7\u7a0b\u4e2d\u629b\u51fa\u7684\u5f02\u5e38"},
    { "line", "\u884c\u53f7"},
    { "column","\u5217\u53f7"},

    {ER_SERIALIZER_NOT_CONTENTHANDLER,
      "\u4e32\u884c\u5668\u7c7b\u201c{0}\u201d\u4e0d\u5b9e\u73b0 org.xml.sax.ContentHandler."},

    {ER_RESOURCE_COULD_NOT_FIND,
      "\u627e\u4e0d\u5230\u8d44\u6e90 [ {0} ]\u3002\n {1}" },

    {ER_RESOURCE_COULD_NOT_LOAD,
      "\u8d44\u6e90 [ {0} ] \u65e0\u6cd5\u88c5\u5165\uff1a{1} \n {2} \t {3}" },

    {ER_BUFFER_SIZE_LESSTHAN_ZERO,
      "\u7f13\u51b2\u533a\u5927\u5c0f <=0" },

    {ER_INVALID_UTF16_SURROGATE,
      "\u68c0\u6d4b\u5230\u65e0\u6548\u7684 UTF-16 \u66ff\u4ee3\u8005\uff1a{0}\uff1f" },

    {ER_OIERROR,
      "IO \u9519\u8bef" },

    {ER_ILLEGAL_ATTRIBUTE_POSITION,
      "\u5728\u751f\u6210\u5b50\u8282\u70b9\u4e4b\u540e\u6216\u5728\u751f\u6210\u5143\u7d20\u4e4b\u524d\u65e0\u6cd5\u6dfb\u52a0\u5c5e\u6027 {0}\u3002\u5c06\u5ffd\u7565\u5c5e\u6027\u3002"},

      /*
       * Note to translators:  The stylesheet contained a reference to a
       * namespace prefix that was undefined.  The value of the substitution
       * text is the name of the prefix.
       */
    {ER_NAMESPACE_PREFIX,
      "\u6ca1\u6709\u8bf4\u660e\u540d\u79f0\u7a7a\u95f4\u524d\u7f00\u201c{0}\u201d\u3002" },
      /*
       * Note to translators:  This message is reported if the stylesheet
       * being processed attempted to construct an XML document with an
       * attribute in a place other than on an element.  The substitution text
       * specifies the name of the attribute.
       */
    {ER_STRAY_ATTRIBUTE,
      "\u5c5e\u6027\u201c{0}\u201d\u5728\u5143\u7d20\u5916\u3002" },

      /*
       * Note to translators:  As with the preceding message, a namespace
       * declaration has the form of an attribute and is only permitted to
       * appear on an element.  The substitution text {0} is the namespace
       * prefix and {1} is the URI that was being used in the erroneous
       * namespace declaration.
       */
    {ER_STRAY_NAMESPACE,
      "\u540d\u79f0\u7a7a\u95f4\u8bf4\u660e\u201c{0}\u201d=\u201c{1}\u201d\u5728\u5143\u7d20\u5916\u3002" },

    {ER_COULD_NOT_LOAD_RESOURCE,
      "\u65e0\u6cd5\u88c5\u5165\u201c{0}\u201d\uff08\u68c0\u67e5 CLASSPATH\uff09\uff0c\u73b0\u5728\u53ea\u4f7f\u7528\u7f3a\u7701\u503c"},

    {ER_COULD_NOT_LOAD_METHOD_PROPERTY,
      "\u65e0\u6cd5\u4e3a\u8f93\u51fa\u65b9\u6cd5\u201c{1}\u201d\u88c5\u8f7d\u5c5e\u6027\u6587\u4ef6\u201c{0}\u201d\uff08\u68c0\u67e5 CLASSPATH\uff09" }


  };
  }

  /**
   *   Return a named ResourceBundle for a particular locale.  This method mimics the behavior
   *   of ResourceBundle.getBundle().
   *
   *   @param className the name of the class that implements the resource bundle.
   *   @return the ResourceBundle
   *   @throws MissingResourceException
   */
  public static final XMLErrorResources loadResourceBundle(String className)
          throws MissingResourceException
  {

    Locale locale = Locale.getDefault();
    String suffix = getResourceSuffix(locale);

    try
    {

      // first try with the given locale
      return (XMLErrorResources) ResourceBundle.getBundle(className
              + suffix, locale);
    }
    catch (MissingResourceException e)
    {
      try  // try to fall back to en_US if we can't load
      {

        // Since we can't find the localized property file,
        // fall back to en_US.
        return (XMLErrorResources) ResourceBundle.getBundle(className,
                new Locale("zh", "CN"));
      }
      catch (MissingResourceException e2)
      {

        // Now we are really in trouble.
        // very bad, definitely very bad...not going to get very far
        throw new MissingResourceException(
          "\u65e0\u6cd5\u88c5\u5165\u4efb\u4f55\u8d44\u6e90\u5305\u3002", className, "");
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
