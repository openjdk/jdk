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
 * $Id: XMLErrorResources_zh_TW.java,v 1.2.4.1 2005/09/15 07:45:48 suresh_emailid Exp $
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
public class XMLErrorResources_zh_TW extends ListResourceBundle
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
      "\u51fd\u6578\u4e0d\u53d7\u652f\u63f4\uff01"},

    { ER_CANNOT_OVERWRITE_CAUSE,
      "\u7121\u6cd5\u6539\u5beb\u539f\u56e0"},

    { ER_NO_DEFAULT_IMPL,
      "\u627e\u4e0d\u5230\u9810\u8a2d\u5be6\u4f5c"},

    { ER_CHUNKEDINTARRAY_NOT_SUPPORTED,
      "ChunkedIntArray({0}) \u76ee\u524d\u4e0d\u53d7\u652f\u63f4"},

    { ER_OFFSET_BIGGER_THAN_SLOT,
      "\u504f\u79fb\u6bd4\u69fd\u5927"},

    { ER_COROUTINE_NOT_AVAIL,
      "\u6c92\u6709 Coroutine \u53ef\u7528\uff0cid={0}"},

    { ER_COROUTINE_CO_EXIT,
      "CoroutineManager \u6536\u5230 co_exit() \u8981\u6c42"},

    { ER_COJOINROUTINESET_FAILED,
      "co_joinCoroutineSet() \u5931\u6548"},

    { ER_COROUTINE_PARAM,
      "Coroutine \u53c3\u6578\u932f\u8aa4 ({0})"},

    { ER_PARSER_DOTERMINATE_ANSWERS,
      "\n\u975e\u9810\u671f\u7684\u7d50\u679c\uff1a\u5256\u6790\u5668 doTerminate \u56de\u7b54 {0}"},

    { ER_NO_PARSE_CALL_WHILE_PARSING,
      "\u5728\u5256\u6790\u6642\u672a\u547c\u53eb parse"},

    { ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "\u932f\u8aa4\uff1a\u91dd\u5c0d\u8ef8 {0} \u8f38\u5165\u7684\u91cd\u8907\u9805\u76ee\u6c92\u6709\u5be6\u4f5c"},

    { ER_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "\u932f\u8aa4\uff1a\u8ef8 {0} \u7684\u91cd\u8907\u9805\u76ee\u6c92\u6709\u5be6\u4f5c"},

    { ER_ITERATOR_CLONE_NOT_SUPPORTED,
      "\u91cd\u8907\u9805\u76ee\u8907\u88fd\u4e0d\u53d7\u652f\u63f4"},

    { ER_UNKNOWN_AXIS_TYPE,
      "\u4e0d\u660e\u7684\u8ef8\u904d\u6b77\u985e\u578b\uff1a{0}"},

    { ER_AXIS_NOT_SUPPORTED,
      "\u4e0d\u652f\u63f4\u8ef8\u904d\u6b77\uff1a{0}"},

    { ER_NO_DTMIDS_AVAIL,
      "\u6c92\u6709\u53ef\u7528\u7684 DTM ID"},

    { ER_NOT_SUPPORTED,
      "\u4e0d\u652f\u63f4\uff1a{0}"},

    { ER_NODE_NON_NULL,
      "\u5c0d getDTMHandleFromNode \u800c\u8a00\uff0c\u7bc0\u9ede\u5fc5\u9808\u70ba\u975e\u7a7a\u503c"},

    { ER_COULD_NOT_RESOLVE_NODE,
      "\u7121\u6cd5\u89e3\u6790\u7bc0\u9ede\u70ba\u63a7\u9ede"},

    { ER_STARTPARSE_WHILE_PARSING,
       "\u5728\u5256\u6790\u6642\u672a\u547c\u53eb startParse"},

    { ER_STARTPARSE_NEEDS_SAXPARSER,
       "startParse \u9700\u8981\u975e\u7a7a\u503c\u7684 SAXParser"},

    { ER_COULD_NOT_INIT_PARSER,
       "\u7121\u6cd5\u4f7f\u7528\u4ee5\u4e0b\u9805\u76ee\u8d77\u59cb\u8a2d\u5b9a\u5256\u6790\u5668"},

    { ER_EXCEPTION_CREATING_POOL,
       "\u5efa\u7acb\u5132\u5b58\u6c60\u7684\u65b0\u5be6\u4f8b\u6642\u767c\u751f\u7570\u5e38"},

    { ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
       "\u8def\u5f91\u5305\u542b\u7121\u6548\u7684\u8df3\u812b\u5b57\u5143"},

    { ER_SCHEME_REQUIRED,
       "\u7db1\u8981\u662f\u5fc5\u9700\u7684\uff01"},

    { ER_NO_SCHEME_IN_URI,
       "\u5728 URI\uff1a{0} \u627e\u4e0d\u5230\u7db1\u8981"},

    { ER_NO_SCHEME_INURI,
       "\u5728 URI \u627e\u4e0d\u5230\u7db1\u8981"},

    { ER_PATH_INVALID_CHAR,
       "\u8def\u5f91\u5305\u542b\u7121\u6548\u7684\u5b57\u5143\uff1a{0}"},

    { ER_SCHEME_FROM_NULL_STRING,
       "\u7121\u6cd5\u5f9e\u7a7a\u5b57\u4e32\u8a2d\u5b9a\u7db1\u8981"},

    { ER_SCHEME_NOT_CONFORMANT,
       "\u7db1\u8981\u4e0d\u662f conformant\u3002"},

    { ER_HOST_ADDRESS_NOT_WELLFORMED,
       "\u4e3b\u6a5f\u6c92\u6709\u5b8c\u6574\u7684\u4f4d\u5740"},

    { ER_PORT_WHEN_HOST_NULL,
       "\u4e3b\u6a5f\u70ba\u7a7a\u503c\u6642\uff0c\u7121\u6cd5\u8a2d\u5b9a\u57e0"},

    { ER_INVALID_PORT,
       "\u7121\u6548\u7684\u57e0\u7de8\u865f"},

    { ER_FRAG_FOR_GENERIC_URI,
       "\u53ea\u80fd\u5c0d\u901a\u7528\u7684 URI \u8a2d\u5b9a\u7247\u6bb5"},

    { ER_FRAG_WHEN_PATH_NULL,
       "\u8def\u5f91\u70ba\u7a7a\u503c\u6642\uff0c\u7121\u6cd5\u8a2d\u5b9a\u7247\u6bb5"},

    { ER_FRAG_INVALID_CHAR,
       "\u7247\u6bb5\u5305\u542b\u7121\u6548\u7684\u5b57\u5143"},

    { ER_PARSER_IN_USE,
      "\u5256\u6790\u5668\u5df2\u5728\u4f7f\u7528\u4e2d"},

    { ER_CANNOT_CHANGE_WHILE_PARSING,
      "\u5256\u6790\u6642\u7121\u6cd5\u8b8a\u66f4 {0} {1}"},

    { ER_SELF_CAUSATION_NOT_PERMITTED,
      "\u4e0d\u5141\u8a31\u672c\u8eab\u7684\u56e0\u679c\u95dc\u4fc2"},

    { ER_NO_USERINFO_IF_NO_HOST,
      "\u5982\u679c\u6c92\u6709\u6307\u5b9a\u4e3b\u6a5f\uff0c\u4e0d\u53ef\u6307\u5b9a Userinfo"},

    { ER_NO_PORT_IF_NO_HOST,
      "\u5982\u679c\u6c92\u6709\u6307\u5b9a\u4e3b\u6a5f\uff0c\u4e0d\u53ef\u6307\u5b9a\u57e0"},

    { ER_NO_QUERY_STRING_IN_PATH,
      "\u5728\u8def\u5f91\u53ca\u67e5\u8a62\u5b57\u4e32\u4e2d\u4e0d\u53ef\u6307\u5b9a\u67e5\u8a62\u5b57\u4e32"},

    { ER_NO_FRAGMENT_STRING_IN_PATH,
      "\u7247\u6bb5\u7121\u6cd5\u540c\u6642\u5728\u8def\u5f91\u548c\u7247\u6bb5\u4e2d\u6307\u5b9a"},

    { ER_CANNOT_INIT_URI_EMPTY_PARMS,
      "\u7121\u6cd5\u4ee5\u7a7a\u767d\u53c3\u6578\u8d77\u59cb\u8a2d\u5b9a URI"},

    { ER_METHOD_NOT_SUPPORTED,
      "\u65b9\u6cd5\u4e0d\u53d7\u652f\u63f4"},

    { ER_INCRSAXSRCFILTER_NOT_RESTARTABLE,
      "IncrementalSAXSource_Filter \u76ee\u524d\u7121\u6cd5\u91cd\u65b0\u555f\u52d5"},

    { ER_XMLRDR_NOT_BEFORE_STARTPARSE,
      "XMLReader \u6c92\u6709\u5728 startParse \u8981\u6c42\u4e4b\u524d"},

    { ER_AXIS_TRAVERSER_NOT_SUPPORTED,
      "\u4e0d\u652f\u63f4\u8ef8\u904d\u6b77\uff1a{0}"},

    { ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER,
      "\u4ee5\u7a7a\u503c PrintWriter \u5efa\u7acb\u7684 ListingErrorHandler\uff01"},

    { ER_SYSTEMID_UNKNOWN,
      "\u4e0d\u660e\u7684 SystemId"},

    { ER_LOCATION_UNKNOWN,
      "\u932f\u8aa4\u4f4d\u7f6e\u4e0d\u660e"},

    { ER_PREFIX_MUST_RESOLVE,
      "\u5b57\u9996\u5fc5\u9808\u89e3\u6790\u70ba\u540d\u7a31\u7a7a\u9593\uff1a{0}"},

    { ER_CREATEDOCUMENT_NOT_SUPPORTED,
      "\u5728 XPathContext \u4e2d\u4e0d\u652f\u63f4 createDocument()"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT,
      "\u5c6c\u6027\u5b50\u9805\u5143\u4ef6\u6c92\u6709\u64c1\u6709\u8005\u6587\u4ef6\uff01"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT_ELEMENT,
      "\u5c6c\u6027\u5b50\u9805\u5143\u4ef6\u6c92\u6709\u64c1\u6709\u8005\u6587\u4ef6\u5143\u7d20\uff01"},

    { ER_CANT_OUTPUT_TEXT_BEFORE_DOC,
      "\u8b66\u544a\uff1a\u4e0d\u80fd\u8f38\u51fa\u6587\u4ef6\u5143\u7d20\u4e4b\u524d\u7684\u6587\u5b57\uff01\u5ffd\u7565..."},

    { ER_CANT_HAVE_MORE_THAN_ONE_ROOT,
      "\u4e00\u500b DOM \u53ea\u80fd\u6709\u4e00\u500b\u6839\u76ee\u9304\uff01"},

    { ER_ARG_LOCALNAME_NULL,
       "\u5f15\u6578 'localName' \u70ba\u7a7a\u503c"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The localname is the portion after the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_LOCALNAME_INVALID,
       "QNAME \u4e2d\u7684\u672c\u7aef\u540d\u7a31\u61c9\u8a72\u662f\u6709\u6548\u7684 NCName"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The prefix is the portion before the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_PREFIX_INVALID,
       "QNAME \u4e2d\u7684\u5b57\u9996\u61c9\u8a72\u662f\u6709\u6548\u7684 NCName"},

    { "BAD_CODE", "createMessage \u7684\u53c3\u6578\u8d85\u51fa\u754c\u9650"},
    { "FORMAT_FAILED", "\u5728 messageFormat \u547c\u53eb\u671f\u9593\u64f2\u51fa\u7570\u5e38"},
    { "line", "\u884c\u865f"},
    { "column","\u6b04\u865f"},

    {ER_SERIALIZER_NOT_CONTENTHANDLER,
      "serializer \u985e\u5225 ''{0}'' \u4e0d\u5be6\u4f5c org.xml.sax.ContentHandler\u3002"},

    {ER_RESOURCE_COULD_NOT_FIND,
      "\u627e\u4e0d\u5230\u8cc7\u6e90 [ {0} ]\u3002\n{1}" },

    {ER_RESOURCE_COULD_NOT_LOAD,
      "\u7121\u6cd5\u8f09\u5165\u8cc7\u6e90 [ {0} ]\uff1a{1} \n {2} \t {3}" },

    {ER_BUFFER_SIZE_LESSTHAN_ZERO,
      "\u7de9\u885d\u5340\u5927\u5c0f <=0" },

    {ER_INVALID_UTF16_SURROGATE,
      "\u5075\u6e2c\u5230\u7121\u6548\u7684 UTF-16 \u4ee3\u7406\uff1a{0}?" },

    {ER_OIERROR,
      "IO \u932f\u8aa4" },

    {ER_ILLEGAL_ATTRIBUTE_POSITION,
      "\u5728\u7522\u751f\u5b50\u9805\u7bc0\u9ede\u4e4b\u5f8c\uff0c\u6216\u5728\u7522\u751f\u5143\u7d20\u4e4b\u524d\uff0c\u4e0d\u53ef\u65b0\u589e\u5c6c\u6027 {0}\u3002\u5c6c\u6027\u6703\u88ab\u5ffd\u7565\u3002"},

      /*
       * Note to translators:  The stylesheet contained a reference to a
       * namespace prefix that was undefined.  The value of the substitution
       * text is the name of the prefix.
       */
    {ER_NAMESPACE_PREFIX,
      "\u5b57\u9996 ''{0}'' \u7684\u540d\u7a31\u7a7a\u9593\u5c1a\u672a\u5ba3\u544a\u3002" },
      /*
       * Note to translators:  This message is reported if the stylesheet
       * being processed attempted to construct an XML document with an
       * attribute in a place other than on an element.  The substitution text
       * specifies the name of the attribute.
       */
    {ER_STRAY_ATTRIBUTE,
      "\u5c6c\u6027 ''{0}'' \u8d85\u51fa\u5143\u7d20\u5916\u3002" },

      /*
       * Note to translators:  As with the preceding message, a namespace
       * declaration has the form of an attribute and is only permitted to
       * appear on an element.  The substitution text {0} is the namespace
       * prefix and {1} is the URI that was being used in the erroneous
       * namespace declaration.
       */
    {ER_STRAY_NAMESPACE,
      "\u540d\u7a31\u7a7a\u9593\u5ba3\u544a ''{0}''=''{1}'' \u8d85\u51fa\u5143\u7d20\u5916\u3002" },

    {ER_COULD_NOT_LOAD_RESOURCE,
      "\u7121\u6cd5\u8f09\u5165 ''{0}''\uff08\u6aa2\u67e5 CLASSPATH\uff09\uff0c\u76ee\u524d\u53ea\u4f7f\u7528\u9810\u8a2d\u503c"},

    {ER_COULD_NOT_LOAD_METHOD_PROPERTY,
      "\u7121\u6cd5\u8f09\u5165\u8f38\u51fa\u65b9\u6cd5 ''{1}'' \u7684\u5167\u5bb9\u6a94 ''{0}''\uff08\u6aa2\u67e5 CLASSPATH\uff09" }


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
                new Locale("zh", "TW"));
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
