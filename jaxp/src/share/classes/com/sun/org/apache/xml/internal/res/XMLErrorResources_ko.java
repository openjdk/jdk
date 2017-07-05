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
 * $Id: XMLErrorResources_ko.java,v 1.2.4.1 2005/09/15 07:45:42 suresh_emailid Exp $
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
public class XMLErrorResources_ko extends ListResourceBundle
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
      "\ud568\uc218\uac00 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!"},

    { ER_CANNOT_OVERWRITE_CAUSE,
      "\uc6d0\uc778\uc744 \uacb9\uccd0\uc4f8 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_NO_DEFAULT_IMPL,
      "\uae30\ubcf8 \uad6c\ud604\uc774 \uc5c6\uc2b5\ub2c8\ub2e4. "},

    { ER_CHUNKEDINTARRAY_NOT_SUPPORTED,
      "ChunkedIntArray({0})\uac00 \ud604\uc7ac \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

    { ER_OFFSET_BIGGER_THAN_SLOT,
      "\uc624\ud504\uc14b\uc774 \uc2ac\ub86f\ubcf4\ub2e4 \ud07d\ub2c8\ub2e4."},

    { ER_COROUTINE_NOT_AVAIL,
      "Coroutine\uc744 \uc0ac\uc6a9\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4, id={0}"},

    { ER_COROUTINE_CO_EXIT,
      "CoroutineManager\uac00 co_exit() \uc694\uccad\uc744 \ubc1b\uc558\uc2b5\ub2c8\ub2e4."},

    { ER_COJOINROUTINESET_FAILED,
      "co_joinCoroutineSet()\uac00 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4."},

    { ER_COROUTINE_PARAM,
      "Coroutine \ub9e4\uac1c\ubcc0\uc218 \uc624\ub958({0})"},

    { ER_PARSER_DOTERMINATE_ANSWERS,
      "\nUNEXPECTED: \uad6c\ubd84 \ubd84\uc11d\uae30 doTerminate\uac00 {0}\uc5d0 \uc751\ub2f5\ud569\ub2c8\ub2e4."},

    { ER_NO_PARSE_CALL_WHILE_PARSING,
      "\uad6c\ubb38 \ubd84\uc11d \uc911\uc5d0\ub294 parse\ub97c \ud638\ucd9c\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "\uc624\ub958: {0} \ucd95\uc5d0 \ub300\ud574 \uc720\ud615\ud654\ub41c \ubc18\ubcf5\uae30\ub97c \uad6c\ud604\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "\uc624\ub958: {0} \ucd95\uc5d0 \ub300\ud55c \ubc18\ubcf5\uae30\ub97c \uad6c\ud604\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4. "},

    { ER_ITERATOR_CLONE_NOT_SUPPORTED,
      "\ubc18\ubcf5\uae30 \ubcf5\uc81c\uac00 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

    { ER_UNKNOWN_AXIS_TYPE,
      "\uc54c \uc218 \uc5c6\ub294 axis traversal \uc720\ud615: {0}"},

    { ER_AXIS_NOT_SUPPORTED,
      "Axis traverser\uac00 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4: {0}"},

    { ER_NO_DTMIDS_AVAIL,
      "\uc0ac\uc6a9 \uac00\ub2a5\ud55c \ucd94\uac00 DTM ID\uac00 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_NOT_SUPPORTED,
      "\uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4: {0}"},

    { ER_NODE_NON_NULL,
      "getDTMHandleFromNode\uc758 \ub178\ub4dc\ub294 \ub110(null) \uc774\uc678\uc758 \uac12\uc774\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

    { ER_COULD_NOT_RESOLVE_NODE,
      "\ub178\ub4dc\ub97c \ud578\ub4e4\ub85c \ubd84\uc11d\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_STARTPARSE_WHILE_PARSING,
       "\uad6c\ubb38 \ubd84\uc11d \uc911\uc5d0\ub294 startParse\ub97c \ud638\ucd9c\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_STARTPARSE_NEEDS_SAXPARSER,
       "startParse\ub294 \ub110(null)\uc774 \uc544\ub2cc SAXParser\ub97c \ud544\uc694\ub85c \ud569\ub2c8\ub2e4."},

    { ER_COULD_NOT_INIT_PARSER,
       "\uad6c\ubb38 \ubd84\uc11d\uae30\ub97c \ucd08\uae30\ud654\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_EXCEPTION_CREATING_POOL,
       "\ud480\uc758 \uc0c8 \uc778\uc2a4\ud134\uc2a4 \uc791\uc131 \uc911 \uc608\uc678"},

    { ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
       "\uacbd\ub85c\uc5d0 \uc798\ubabb\ub41c \uc774\uc2a4\ucf00\uc774\ud504 \uc21c\uc11c\uac00 \uc788\uc2b5\ub2c8\ub2e4."},

    { ER_SCHEME_REQUIRED,
       "\uc124\uacc4\uac00 \ud544\uc694\ud569\ub2c8\ub2e4!"},

    { ER_NO_SCHEME_IN_URI,
       "URI\uc5d0 \uc124\uacc4\uac00 \uc5c6\uc2b5\ub2c8\ub2e4: {0}"},

    { ER_NO_SCHEME_INURI,
       "URI\uc5d0 \uc124\uacc4\uac00 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_PATH_INVALID_CHAR,
       "\uacbd\ub85c\uc5d0 \uc798\ubabb\ub41c \ubb38\uc790\uac00 \uc788\uc2b5\ub2c8\ub2e4: {0}"},

    { ER_SCHEME_FROM_NULL_STRING,
       "\ub110(null) \ubb38\uc790\uc5f4\uc5d0\uc11c \uc124\uacc4\ub97c \uc124\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_SCHEME_NOT_CONFORMANT,
       "\uc124\uacc4\uac00 \uc77c\uce58\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

    { ER_HOST_ADDRESS_NOT_WELLFORMED,
       "\ud638\uc2a4\ud2b8\uac00 \uc644\uc804\ud55c \uc8fc\uc18c\uac00 \uc544\ub2d9\ub2c8\ub2e4."},

    { ER_PORT_WHEN_HOST_NULL,
       "\ud638\uc2a4\ud2b8\uac00 \ub110(null)\uc774\uba74 \ud3ec\ud2b8\ub97c \uc124\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_INVALID_PORT,
       "\uc798\ubabb\ub41c \ud3ec\ud2b8 \ubc88\ud638"},

    { ER_FRAG_FOR_GENERIC_URI,
       "\uc77c\ubc18 URI\uc5d0 \ub300\ud574\uc11c\ub9cc \ub2e8\ud3b8\uc744 \uc124\uc815\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4."},

    { ER_FRAG_WHEN_PATH_NULL,
       "\uacbd\ub85c\uac00 \ub110(null)\uc774\uba74 \ub2e8\ud3b8\uc744 \uc124\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_FRAG_INVALID_CHAR,
       "\ub2e8\ud3b8\uc5d0 \uc798\ubabb\ub41c \ubb38\uc790\uac00 \uc788\uc2b5\ub2c8\ub2e4."},

    { ER_PARSER_IN_USE,
      "\uad6c\ubb38 \ubd84\uc11d\uae30\uac00 \uc774\ubbf8 \uc0ac\uc6a9 \uc911\uc785\ub2c8\ub2e4."},

    { ER_CANNOT_CHANGE_WHILE_PARSING,
      "\uad6c\ubb38 \ubd84\uc11d \uc911\uc5d0\ub294 {0} {1}\uc744(\ub97c) \ubcc0\uacbd\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_SELF_CAUSATION_NOT_PERMITTED,
      "Self-causation\uc774 \ud5c8\uc6a9\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

    { ER_NO_USERINFO_IF_NO_HOST,
      "\ud638\uc2a4\ud2b8\ub97c \uc9c0\uc815\ud558\uc9c0 \uc54a\uc740 \uacbd\uc6b0\uc5d0\ub294 Userinfo\ub97c \uc9c0\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_NO_PORT_IF_NO_HOST,
      "\ud638\uc2a4\ud2b8\ub97c \uc9c0\uc815\ud558\uc9c0 \uc54a\uc740 \uacbd\uc6b0\uc5d0\ub294 \ud3ec\ud2b8\ub97c \uc9c0\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_NO_QUERY_STRING_IN_PATH,
      "\uacbd\ub85c \ubc0f \uc870\ud68c \ubb38\uc790\uc5f4\uc5d0 \uc870\ud68c \ubb38\uc790\uc5f4\uc744 \uc9c0\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_NO_FRAGMENT_STRING_IN_PATH,
      "\uacbd\ub85c \ubc0f \ub2e8\ud3b8 \ub458 \ub2e4\uc5d0 \ub2e8\ud3b8\uc744 \uc9c0\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_CANNOT_INIT_URI_EMPTY_PARMS,
      "\ube48 \ub9e4\uac1c\ubcc0\uc218\ub85c URI\ub97c \ucd08\uae30\ud654\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_METHOD_NOT_SUPPORTED,
      "\uc544\uc9c1 \uba54\uc18c\ub4dc\uac00 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4. "},

    { ER_INCRSAXSRCFILTER_NOT_RESTARTABLE,
      "\ud604\uc7ac IncrementalSAXSource_Filter\ub97c \ub2e4\uc2dc \uc2dc\uc791\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_XMLRDR_NOT_BEFORE_STARTPARSE,
      "startParse \uc694\uccad \uc804\uc5d0 XMLReader\ub97c \uc2dc\uc791\ud588\uc2b5\ub2c8\ub2e4."},

    { ER_AXIS_TRAVERSER_NOT_SUPPORTED,
      "Axis traverser\uac00 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4: {0}"},

    { ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER,
      "\ub110(null) PrintWriter\ub85c ListingErrorHandler\ub97c \uc791\uc131\ud588\uc2b5\ub2c8\ub2e4!"},

    { ER_SYSTEMID_UNKNOWN,
      "SystemId\ub97c \uc54c \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_LOCATION_UNKNOWN,
      "\uc624\ub958\uc758 \uc704\uce58\ub97c \uc54c \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

    { ER_PREFIX_MUST_RESOLVE,
      "\uc811\ub450\ubd80\ub294 \uc774\ub984 \uacf5\uac04\uc73c\ub85c \ubd84\uc11d\ub418\uc5b4\uc57c \ud569\ub2c8\ub2e4: {0}"},

    { ER_CREATEDOCUMENT_NOT_SUPPORTED,
      "XPathContext\uc5d0\uc11c createDocument()\uac00 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4!"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT,
      "\ud558\uc704 \uc18d\uc131\uc5d0 \uc18c\uc720\uc790 \ubb38\uc11c\uac00 \uc5c6\uc2b5\ub2c8\ub2e4!"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT_ELEMENT,
      "\ud558\uc704 \uc18d\uc131\uc5d0 \uc18c\uc720\uc790 \ubb38\uc11c \uc694\uc18c\uac00 \uc5c6\uc2b5\ub2c8\ub2e4!"},

    { ER_CANT_OUTPUT_TEXT_BEFORE_DOC,
      "\uacbd\uace0: \ubb38\uc11c \uc694\uc18c \uc55e\uc5d0 \ud14d\uc2a4\ud2b8\ub97c \ucd9c\ub825\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4! \ubb34\uc2dc \uc911..."},

    { ER_CANT_HAVE_MORE_THAN_ONE_ROOT,
      "DOM\uc5d0 \ub458 \uc774\uc0c1\uc758 \ub8e8\ud2b8\uac00 \uc788\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4!"},

    { ER_ARG_LOCALNAME_NULL,
       "'localName' \uc778\uc218\uac00 \ub110(null)\uc785\ub2c8\ub2e4."},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The localname is the portion after the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_LOCALNAME_INVALID,
       "QNAME\uc758 \ub85c\uceec \uc774\ub984\uc740 \uc62c\ubc14\ub978 NCName\uc774\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The prefix is the portion before the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_PREFIX_INVALID,
       "QNAME\uc758 \uc811\ub450\ubd80\ub294 \uc62c\ubc14\ub978 NCName\uc774\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

    { "BAD_CODE", "createMessage\uc5d0 \ub300\ud55c \ub9e4\uac1c\ubcc0\uc218\uac00 \ubc94\uc704\ub97c \ubc97\uc5b4\ub0a9\ub2c8\ub2e4."},
    { "FORMAT_FAILED", "messageFormat \ud638\ucd9c \uc911 \uc608\uc678 \ubc1c\uc0dd"},
    { "line", "\ud589 #"},
    { "column","\uc5f4 #"},

    {ER_SERIALIZER_NOT_CONTENTHANDLER,
      "''{0}'' \uc9c1\ub82c\ud654 \ud504\ub85c\uadf8\ub7a8 \ud074\ub798\uc2a4\uac00 org.xml.sax.ContentHandler\ub97c \uad6c\ud604\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

    {ER_RESOURCE_COULD_NOT_FIND,
      "[ {0} ] \uc790\uc6d0\uc744 \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.\n {1}" },

    {ER_RESOURCE_COULD_NOT_LOAD,
      "[ {0} ] \uc790\uc6d0\uc774 {1} \n {2} \t {3}\uc744(\ub97c) \ub85c\ub4dc\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4. " },

    {ER_BUFFER_SIZE_LESSTHAN_ZERO,
      "\ubc84\ud37c \ud06c\uae30 <=0" },

    {ER_INVALID_UTF16_SURROGATE,
      "\uc798\ubabb\ub41c UTF-16 \ub300\ub9ac\uc790(surrogate)\uac00 \ubc1c\uacac\ub418\uc5c8\uc2b5\ub2c8\ub2e4: {0} ?" },

    {ER_OIERROR,
      "IO \uc624\ub958" },

    {ER_ILLEGAL_ATTRIBUTE_POSITION,
      "\ud558\uc704 \ub178\ub4dc\uac00 \uc0dd\uc131\ub41c \uc774\ud6c4 \ub610\ub294 \uc694\uc18c\uac00 \uc791\uc131\ub418\uae30 \uc774\uc804\uc5d0 {0} \uc18d\uc131\uc744 \ucd94\uac00\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4. \uc18d\uc131\uc774 \ubb34\uc2dc\ub429\ub2c8\ub2e4."},

      /*
       * Note to translators:  The stylesheet contained a reference to a
       * namespace prefix that was undefined.  The value of the substitution
       * text is the name of the prefix.
       */
    {ER_NAMESPACE_PREFIX,
      "''{0}'' \uc811\ub450\ubd80\uc5d0 \ub300\ud55c \uc774\ub984 \uacf5\uac04\uc774 \uc120\uc5b8\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4." },
      /*
       * Note to translators:  This message is reported if the stylesheet
       * being processed attempted to construct an XML document with an
       * attribute in a place other than on an element.  The substitution text
       * specifies the name of the attribute.
       */
    {ER_STRAY_ATTRIBUTE,
      "''{0}'' \uc18d\uc131\uc774 \uc694\uc18c\uc758 \uc678\ubd80\uc5d0 \uc788\uc2b5\ub2c8\ub2e4." },

      /*
       * Note to translators:  As with the preceding message, a namespace
       * declaration has the form of an attribute and is only permitted to
       * appear on an element.  The substitution text {0} is the namespace
       * prefix and {1} is the URI that was being used in the erroneous
       * namespace declaration.
       */
    {ER_STRAY_NAMESPACE,
      "''{0}''=''{1}'' \uc774\ub984 \uacf5\uac04 \uc120\uc5b8\uc774 \uc694\uc18c\uc758 \uc678\ubd80\uc5d0 \uc788\uc2b5\ub2c8\ub2e4." },

    {ER_COULD_NOT_LOAD_RESOURCE,
      "''{0}''(CLASSPATH \ud655\uc778)\uc744(\ub97c) \ub85c\ub4dc\ud560 \uc218 \uc5c6\uc73c\ubbc0\ub85c, \ud604\uc7ac \uae30\ubcf8\uac12\ub9cc\uc744 \uc0ac\uc6a9 \uc911\uc785\ub2c8\ub2e4."},

    {ER_COULD_NOT_LOAD_METHOD_PROPERTY,
      "''{1}''\ucd9c\ub825 \uba54\uc18c\ub4dc(CLASSPATH \ud655\uc778)\uc5d0 \ub300\ud55c ''{0}'' \ud2b9\uc131 \ud30c\uc77c\uc744 \ub85c\ub4dc\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4." }


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
                new Locale("ko", "US"));
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
