/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.org.apache.xpath.internal.res;

import java.util.ListResourceBundle;

/**
 * Set up error messages.
 * We build a two dimensional array of message keys and
 * message strings. In order to add a new message here,
 * you need to first add a Static string constant for the
 * Key and update the contents array with Key, Value pair
  * Also you need to  update the count of messages(MAX_CODE)or
 * the count of warnings(MAX_WARNING) [ Information purpose only]
 * @xsl.usage advanced
 * @LastModified: Nov 2024
 */
public class XPATHErrorResources_zh_TW extends ListResourceBundle
{

/*
 * General notes to translators:
 *
 * This file contains error and warning messages related to XPath Error
 * Handling.
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
 *  8) The context node is the node in the document with respect to which an
 *     XPath expression is being evaluated.
 *
 *  9) An iterator is an object that traverses nodes in the tree, one at a time.
 *
 *  10) NCName is an XML term used to describe a name that does not contain a
 *     colon (a "no-colon name").
 *
 *  11) QName is an XML term meaning "qualified name".
 */

  /*
   * static variables
   */
  public static final String ERROR0000 = "ERROR0000";
  public static final String ER_CURRENT_NOT_ALLOWED_IN_MATCH =
         "ER_CURRENT_NOT_ALLOWED_IN_MATCH";
  public static final String ER_CURRENT_TAKES_NO_ARGS =
         "ER_CURRENT_TAKES_NO_ARGS";
  public static final String ER_DOCUMENT_REPLACED = "ER_DOCUMENT_REPLACED";
  public static final String ER_CONTEXT_CAN_NOT_BE_NULL = "ER_CONTEXT_CAN_NOT_BE_NULL";
  public static final String ER_CONTEXT_HAS_NO_OWNERDOC =
         "ER_CONTEXT_HAS_NO_OWNERDOC";
  public static final String ER_LOCALNAME_HAS_TOO_MANY_ARGS =
         "ER_LOCALNAME_HAS_TOO_MANY_ARGS";
  public static final String ER_NAMESPACEURI_HAS_TOO_MANY_ARGS =
         "ER_NAMESPACEURI_HAS_TOO_MANY_ARGS";
  public static final String ER_NORMALIZESPACE_HAS_TOO_MANY_ARGS =
         "ER_NORMALIZESPACE_HAS_TOO_MANY_ARGS";
  public static final String ER_NUMBER_HAS_TOO_MANY_ARGS =
         "ER_NUMBER_HAS_TOO_MANY_ARGS";
  public static final String ER_NAME_HAS_TOO_MANY_ARGS =
         "ER_NAME_HAS_TOO_MANY_ARGS";
  public static final String ER_STRING_HAS_TOO_MANY_ARGS =
         "ER_STRING_HAS_TOO_MANY_ARGS";
  public static final String ER_STRINGLENGTH_HAS_TOO_MANY_ARGS =
         "ER_STRINGLENGTH_HAS_TOO_MANY_ARGS";
  public static final String ER_TRANSLATE_TAKES_3_ARGS =
         "ER_TRANSLATE_TAKES_3_ARGS";
  public static final String ER_UNPARSEDENTITYURI_TAKES_1_ARG =
         "ER_UNPARSEDENTITYURI_TAKES_1_ARG";
  public static final String ER_NAMESPACEAXIS_NOT_IMPLEMENTED =
         "ER_NAMESPACEAXIS_NOT_IMPLEMENTED";
  public static final String ER_UNKNOWN_AXIS = "ER_UNKNOWN_AXIS";
  public static final String ER_UNKNOWN_MATCH_OPERATION =
         "ER_UNKNOWN_MATCH_OPERATION";
  public static final String ER_INCORRECT_ARG_LENGTH ="ER_INCORRECT_ARG_LENGTH";
  public static final String ER_CANT_CONVERT_TO_NUMBER =
         "ER_CANT_CONVERT_TO_NUMBER";
  public static final String ER_CANT_CONVERT_XPATHRESULTTYPE_TO_NUMBER =
           "ER_CANT_CONVERT_XPATHRESULTTYPE_TO_NUMBER";
  public static final String ER_CANT_CONVERT_TO_NODELIST =
         "ER_CANT_CONVERT_TO_NODELIST";
  public static final String ER_CANT_CONVERT_TO_MUTABLENODELIST =
         "ER_CANT_CONVERT_TO_MUTABLENODELIST";
  public static final String ER_CANT_CONVERT_TO_TYPE ="ER_CANT_CONVERT_TO_TYPE";
  public static final String ER_EXPECTED_MATCH_PATTERN =
         "ER_EXPECTED_MATCH_PATTERN";
  public static final String ER_COULDNOT_GET_VAR_NAMED =
         "ER_COULDNOT_GET_VAR_NAMED";
  public static final String ER_UNKNOWN_OPCODE = "ER_UNKNOWN_OPCODE";
  public static final String ER_EXTRA_ILLEGAL_TOKENS ="ER_EXTRA_ILLEGAL_TOKENS";
  public static final String ER_EXPECTED_DOUBLE_QUOTE =
         "ER_EXPECTED_DOUBLE_QUOTE";
  public static final String ER_EXPECTED_SINGLE_QUOTE =
         "ER_EXPECTED_SINGLE_QUOTE";
  public static final String ER_EMPTY_EXPRESSION = "ER_EMPTY_EXPRESSION";
  public static final String ER_EXPECTED_BUT_FOUND = "ER_EXPECTED_BUT_FOUND";
  public static final String ER_INCORRECT_PROGRAMMER_ASSERTION =
         "ER_INCORRECT_PROGRAMMER_ASSERTION";
  public static final String ER_BOOLEAN_ARG_NO_LONGER_OPTIONAL =
         "ER_BOOLEAN_ARG_NO_LONGER_OPTIONAL";
  public static final String ER_FOUND_COMMA_BUT_NO_PRECEDING_ARG =
         "ER_FOUND_COMMA_BUT_NO_PRECEDING_ARG";
  public static final String ER_FOUND_COMMA_BUT_NO_FOLLOWING_ARG =
         "ER_FOUND_COMMA_BUT_NO_FOLLOWING_ARG";
  public static final String ER_PREDICATE_ILLEGAL_SYNTAX =
         "ER_PREDICATE_ILLEGAL_SYNTAX";
  public static final String ER_ILLEGAL_AXIS_NAME = "ER_ILLEGAL_AXIS_NAME";
  public static final String ER_UNKNOWN_NODETYPE = "ER_UNKNOWN_NODETYPE";
  public static final String ER_PATTERN_LITERAL_NEEDS_BE_QUOTED =
         "ER_PATTERN_LITERAL_NEEDS_BE_QUOTED";
  public static final String ER_COULDNOT_BE_FORMATTED_TO_NUMBER =
         "ER_COULDNOT_BE_FORMATTED_TO_NUMBER";
  public static final String ER_COULDNOT_CREATE_XMLPROCESSORLIAISON =
         "ER_COULDNOT_CREATE_XMLPROCESSORLIAISON";
  public static final String ER_DIDNOT_FIND_XPATH_SELECT_EXP =
         "ER_DIDNOT_FIND_XPATH_SELECT_EXP";
  public static final String ER_COULDNOT_FIND_ENDOP_AFTER_OPLOCATIONPATH =
         "ER_COULDNOT_FIND_ENDOP_AFTER_OPLOCATIONPATH";
  public static final String ER_ERROR_OCCURED = "ER_ERROR_OCCURED";
  public static final String ER_ILLEGAL_VARIABLE_REFERENCE =
         "ER_ILLEGAL_VARIABLE_REFERENCE";
  public static final String ER_AXES_NOT_ALLOWED = "ER_AXES_NOT_ALLOWED";
  public static final String ER_KEY_HAS_TOO_MANY_ARGS =
         "ER_KEY_HAS_TOO_MANY_ARGS";
  public static final String ER_COUNT_TAKES_1_ARG = "ER_COUNT_TAKES_1_ARG";
  public static final String ER_COULDNOT_FIND_FUNCTION =
         "ER_COULDNOT_FIND_FUNCTION";
  public static final String ER_UNSUPPORTED_ENCODING ="ER_UNSUPPORTED_ENCODING";
  public static final String ER_PROBLEM_IN_DTM_NEXTSIBLING =
         "ER_PROBLEM_IN_DTM_NEXTSIBLING";
  public static final String ER_CANNOT_WRITE_TO_EMPTYNODELISTIMPL =
         "ER_CANNOT_WRITE_TO_EMPTYNODELISTIMPL";
  public static final String ER_SETDOMFACTORY_NOT_SUPPORTED =
         "ER_SETDOMFACTORY_NOT_SUPPORTED";
  public static final String ER_PREFIX_MUST_RESOLVE = "ER_PREFIX_MUST_RESOLVE";
  public static final String ER_PARSE_NOT_SUPPORTED = "ER_PARSE_NOT_SUPPORTED";
  public static final String ER_SAX_API_NOT_HANDLED = "ER_SAX_API_NOT_HANDLED";
public static final String ER_IGNORABLE_WHITESPACE_NOT_HANDLED =
         "ER_IGNORABLE_WHITESPACE_NOT_HANDLED";
  public static final String ER_DTM_CANNOT_HANDLE_NODES =
         "ER_DTM_CANNOT_HANDLE_NODES";
  public static final String ER_XERCES_CANNOT_HANDLE_NODES =
         "ER_XERCES_CANNOT_HANDLE_NODES";
  public static final String ER_XERCES_PARSE_ERROR_DETAILS =
         "ER_XERCES_PARSE_ERROR_DETAILS";
  public static final String ER_XERCES_PARSE_ERROR = "ER_XERCES_PARSE_ERROR";
  public static final String ER_INVALID_UTF16_SURROGATE =
         "ER_INVALID_UTF16_SURROGATE";
  public static final String ER_OIERROR = "ER_OIERROR";
  public static final String ER_CANNOT_CREATE_URL = "ER_CANNOT_CREATE_URL";
  public static final String ER_XPATH_READOBJECT = "ER_XPATH_READOBJECT";
 public static final String ER_FUNCTION_TOKEN_NOT_FOUND =
         "ER_FUNCTION_TOKEN_NOT_FOUND";
  public static final String ER_CANNOT_DEAL_XPATH_TYPE =
         "ER_CANNOT_DEAL_XPATH_TYPE";
  public static final String ER_NODESET_NOT_MUTABLE = "ER_NODESET_NOT_MUTABLE";
  public static final String ER_NODESETDTM_NOT_MUTABLE =
         "ER_NODESETDTM_NOT_MUTABLE";
   /**  Variable not resolvable:   */
  public static final String ER_VAR_NOT_RESOLVABLE = "ER_VAR_NOT_RESOLVABLE";
   /** Null error handler  */
 public static final String ER_NULL_ERROR_HANDLER = "ER_NULL_ERROR_HANDLER";
   /**  Programmer's assertion: unknown opcode  */
  public static final String ER_PROG_ASSERT_UNKNOWN_OPCODE =
         "ER_PROG_ASSERT_UNKNOWN_OPCODE";
   /**  0 or 1   */
  public static final String ER_ZERO_OR_ONE = "ER_ZERO_OR_ONE";
   /**  rtf() not supported by XRTreeFragSelectWrapper   */
  public static final String ER_RTF_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER =
         "ER_RTF_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER";
   /**  asNodeIterator() not supported by XRTreeFragSelectWrapper   */
  public static final String ER_ASNODEITERATOR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER = "ER_ASNODEITERATOR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER";
   /**  fsb() not supported for XStringForChars   */
  public static final String ER_FSB_NOT_SUPPORTED_XSTRINGFORCHARS =
         "ER_FSB_NOT_SUPPORTED_XSTRINGFORCHARS";
   /**  Could not find variable with the name of   */
 public static final String ER_COULD_NOT_FIND_VAR = "ER_COULD_NOT_FIND_VAR";
   /**  XStringForChars can not take a string for an argument   */
 public static final String ER_XSTRINGFORCHARS_CANNOT_TAKE_STRING =
         "ER_XSTRINGFORCHARS_CANNOT_TAKE_STRING";
   /**  The FastStringBuffer argument can not be null   */
 public static final String ER_FASTSTRINGBUFFER_CANNOT_BE_NULL =
         "ER_FASTSTRINGBUFFER_CANNOT_BE_NULL";
   /**  2 or 3   */
  public static final String ER_TWO_OR_THREE = "ER_TWO_OR_THREE";
   /** Variable accessed before it is bound! */
  public static final String ER_VARIABLE_ACCESSED_BEFORE_BIND =
         "ER_VARIABLE_ACCESSED_BEFORE_BIND";
   /** XStringForFSB can not take a string for an argument! */
 public static final String ER_FSB_CANNOT_TAKE_STRING =
         "ER_FSB_CANNOT_TAKE_STRING";
   /** Error! Setting the root of a walker to null! */
  public static final String ER_SETTING_WALKER_ROOT_TO_NULL =
         "ER_SETTING_WALKER_ROOT_TO_NULL";
   /** This NodeSetDTM can not iterate to a previous node! */
  public static final String ER_NODESETDTM_CANNOT_ITERATE =
         "ER_NODESETDTM_CANNOT_ITERATE";
  /** This NodeSet can not iterate to a previous node! */
 public static final String ER_NODESET_CANNOT_ITERATE =
         "ER_NODESET_CANNOT_ITERATE";
  /** This NodeSetDTM can not do indexing or counting functions! */
  public static final String ER_NODESETDTM_CANNOT_INDEX =
         "ER_NODESETDTM_CANNOT_INDEX";
  /** This NodeSet can not do indexing or counting functions! */
  public static final String ER_NODESET_CANNOT_INDEX =
         "ER_NODESET_CANNOT_INDEX";
  /** Can not call setShouldCacheNodes after nextNode has been called! */
  public static final String ER_CANNOT_CALL_SETSHOULDCACHENODE =
         "ER_CANNOT_CALL_SETSHOULDCACHENODE";
  /** {0} only allows {1} arguments */
 public static final String ER_ONLY_ALLOWS = "ER_ONLY_ALLOWS";
  /** Programmer's assertion in getNextStepPos: unknown stepType: {0} */
  public static final String ER_UNKNOWN_STEP = "ER_UNKNOWN_STEP";
  /** Problem with RelativeLocationPath */
  public static final String ER_EXPECTED_REL_LOC_PATH =
         "ER_EXPECTED_REL_LOC_PATH";
  /** Problem with LocationPath */
  public static final String ER_EXPECTED_LOC_PATH = "ER_EXPECTED_LOC_PATH";
  public static final String ER_EXPECTED_LOC_PATH_AT_END_EXPR =
                                        "ER_EXPECTED_LOC_PATH_AT_END_EXPR";
  /** Problem with Step */
  public static final String ER_EXPECTED_LOC_STEP = "ER_EXPECTED_LOC_STEP";
  /** Problem with NodeTest */
  public static final String ER_EXPECTED_NODE_TEST = "ER_EXPECTED_NODE_TEST";
  /** Expected step pattern */
  public static final String ER_EXPECTED_STEP_PATTERN =
        "ER_EXPECTED_STEP_PATTERN";
  /** Expected relative path pattern */
  public static final String ER_EXPECTED_REL_PATH_PATTERN =
         "ER_EXPECTED_REL_PATH_PATTERN";
  /** ER_CANT_CONVERT_XPATHRESULTTYPE_TO_BOOLEAN          */
  public static final String ER_CANT_CONVERT_TO_BOOLEAN =
         "ER_CANT_CONVERT_TO_BOOLEAN";
  /** Field ER_CANT_CONVERT_TO_SINGLENODE       */
  public static final String ER_CANT_CONVERT_TO_SINGLENODE =
         "ER_CANT_CONVERT_TO_SINGLENODE";
  /** Field ER_CANT_GET_SNAPSHOT_LENGTH         */
  public static final String ER_CANT_GET_SNAPSHOT_LENGTH =
         "ER_CANT_GET_SNAPSHOT_LENGTH";
  /** Field ER_NON_ITERATOR_TYPE                */
  public static final String ER_NON_ITERATOR_TYPE = "ER_NON_ITERATOR_TYPE";
  /** Field ER_DOC_MUTATED                      */
  public static final String ER_DOC_MUTATED = "ER_DOC_MUTATED";
  public static final String ER_INVALID_XPATH_TYPE = "ER_INVALID_XPATH_TYPE";
  public static final String ER_EMPTY_XPATH_RESULT = "ER_EMPTY_XPATH_RESULT";
  public static final String ER_INCOMPATIBLE_TYPES = "ER_INCOMPATIBLE_TYPES";
  public static final String ER_NULL_RESOLVER = "ER_NULL_RESOLVER";
  public static final String ER_CANT_CONVERT_TO_STRING =
         "ER_CANT_CONVERT_TO_STRING";
  public static final String ER_NON_SNAPSHOT_TYPE = "ER_NON_SNAPSHOT_TYPE";
  public static final String ER_WRONG_DOCUMENT = "ER_WRONG_DOCUMENT";
  /* Note to translators:  The XPath expression cannot be evaluated with respect
   * to this type of node.
   */
  /** Field ER_WRONG_NODETYPE                    */
  public static final String ER_WRONG_NODETYPE = "ER_WRONG_NODETYPE";
  public static final String ER_XPATH_ERROR = "ER_XPATH_ERROR";

  //BEGIN: Keys needed for exception messages of  JAXP 1.3 XPath API implementation
  public static final String ER_EXTENSION_FUNCTION_CANNOT_BE_INVOKED = "ER_EXTENSION_FUNCTION_CANNOT_BE_INVOKED";
  public static final String ER_RESOLVE_VARIABLE_RETURNS_NULL = "ER_RESOLVE_VARIABLE_RETURNS_NULL";
  public static final String ER_UNSUPPORTED_RETURN_TYPE = "ER_UNSUPPORTED_RETURN_TYPE";
  public static final String ER_SOURCE_RETURN_TYPE_CANNOT_BE_NULL = "ER_SOURCE_RETURN_TYPE_CANNOT_BE_NULL";
  public static final String ER_ARG_CANNOT_BE_NULL = "ER_ARG_CANNOT_BE_NULL";

  public static final String ER_OBJECT_MODEL_NULL = "ER_OBJECT_MODEL_NULL";
  public static final String ER_OBJECT_MODEL_EMPTY = "ER_OBJECT_MODEL_EMPTY";
  public static final String ER_FEATURE_NAME_NULL = "ER_FEATURE_NAME_NULL";
  public static final String ER_FEATURE_UNKNOWN = "ER_FEATURE_UNKNOWN";
  public static final String ER_GETTING_NULL_FEATURE = "ER_GETTING_NULL_FEATURE";
  public static final String ER_GETTING_UNKNOWN_FEATURE = "ER_GETTING_UNKNOWN_FEATURE";
  public static final String ER_SECUREPROCESSING_FEATURE = "ER_SECUREPROCESSING_FEATURE";
  public static final String ER_NULL_XPATH_FUNCTION_RESOLVER = "ER_NULL_XPATH_FUNCTION_RESOLVER";
  public static final String ER_NULL_XPATH_VARIABLE_RESOLVER = "ER_NULL_XPATH_VARIABLE_RESOLVER";
  //END: Keys needed for exception messages of  JAXP 1.3 XPath API implementation

  public static final String WG_LOCALE_NAME_NOT_HANDLED =
         "WG_LOCALE_NAME_NOT_HANDLED";
  public static final String WG_PROPERTY_NOT_SUPPORTED =
         "WG_PROPERTY_NOT_SUPPORTED";
  public static final String WG_DONT_DO_ANYTHING_WITH_NS =
         "WG_DONT_DO_ANYTHING_WITH_NS";
  public static final String WG_QUO_NO_LONGER_DEFINED =
         "WG_QUO_NO_LONGER_DEFINED";
  public static final String WG_NEED_DERIVED_OBJECT_TO_IMPLEMENT_NODETEST =
         "WG_NEED_DERIVED_OBJECT_TO_IMPLEMENT_NODETEST";
  public static final String WG_FUNCTION_TOKEN_NOT_FOUND =
         "WG_FUNCTION_TOKEN_NOT_FOUND";
  public static final String WG_COULDNOT_FIND_FUNCTION =
         "WG_COULDNOT_FIND_FUNCTION";
  public static final String WG_CANNOT_MAKE_URL_FROM ="WG_CANNOT_MAKE_URL_FROM";
  public static final String WG_EXPAND_ENTITIES_NOT_SUPPORTED =
         "WG_EXPAND_ENTITIES_NOT_SUPPORTED";
  public static final String WG_ILLEGAL_VARIABLE_REFERENCE =
         "WG_ILLEGAL_VARIABLE_REFERENCE";
  public static final String WG_UNSUPPORTED_ENCODING ="WG_UNSUPPORTED_ENCODING";

  /**  detach() not supported by XRTreeFragSelectWrapper   */
  public static final String ER_DETACH_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER =
         "ER_DETACH_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER";
  /**  num() not supported by XRTreeFragSelectWrapper   */
  public static final String ER_NUM_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER =
         "ER_NUM_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER";
  /**  xstr() not supported by XRTreeFragSelectWrapper   */
  public static final String ER_XSTR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER =
         "ER_XSTR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER";
  /**  str() not supported by XRTreeFragSelectWrapper   */
  public static final String ER_STR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER =
         "ER_STR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER";

  // Error messages...

  private static final Object[][] _contents = new Object[][]{

  { "ERROR0000" , "{0}" },

  { ER_CURRENT_NOT_ALLOWED_IN_MATCH, "配對樣式中不允許 current() 函數！" },

  { ER_CURRENT_TAKES_NO_ARGS, "current() 函數不接受引數！" },

  { ER_DOCUMENT_REPLACED,
      "document() 函數實行已由 com.sun.org.apache.xalan.internal.xslt.FuncDocument 取代。"},

  { ER_CONTEXT_CAN_NOT_BE_NULL,
      "如果作業與相關資訊環境相依，則相關資訊環境不可以是空值。"},

  { ER_CONTEXT_HAS_NO_OWNERDOC,
      "相關資訊環境不具有擁有者文件！"},

  { ER_LOCALNAME_HAS_TOO_MANY_ARGS,
      "local-name() 具有過多的引數。"},

  { ER_NAMESPACEURI_HAS_TOO_MANY_ARGS,
      "namespace-uri() 具有過多的引數。"},

  { ER_NORMALIZESPACE_HAS_TOO_MANY_ARGS,
      "normalize-space() 具有過多的引數。"},

  { ER_NUMBER_HAS_TOO_MANY_ARGS,
      "number() 具有過多的引數。"},

  { ER_NAME_HAS_TOO_MANY_ARGS,
     "name() 具有過多的引數。"},

  { ER_STRING_HAS_TOO_MANY_ARGS,
      "string() 具有過多的引數。"},

  { ER_STRINGLENGTH_HAS_TOO_MANY_ARGS,
      "string-length() 具有過多的引數。"},

  { ER_TRANSLATE_TAKES_3_ARGS,
      "translate() 函數接受三個引數！"},

  { ER_UNPARSEDENTITYURI_TAKES_1_ARG,
      "unparsed-entity-uri 函數應接受一個引數！"},

  { ER_NAMESPACEAXIS_NOT_IMPLEMENTED,
      "尚未實行命名空間軸！"},

  { ER_UNKNOWN_AXIS,
     "不明的軸: {0}"},

  { ER_UNKNOWN_MATCH_OPERATION,
     "不明的配對作業！"},

  { ER_INCORRECT_ARG_LENGTH,
      "processing-instruction() 節點的引數長度不正確！"},

  { ER_CANT_CONVERT_TO_NUMBER,
      "無法轉換 {0} 為數字"},

  { ER_CANT_CONVERT_TO_NODELIST,
      "無法轉換 {0} 為 NodeList！"},

  { ER_CANT_CONVERT_TO_MUTABLENODELIST,
      "無法轉換 {0} 為 NodeSetDTM！"},

  { ER_CANT_CONVERT_TO_TYPE,
      "無法轉換 {0} 為 type#{1}"},

  { ER_EXPECTED_MATCH_PATTERN,
      "在 getMatchScore 中預期配對樣式"},

  { ER_COULDNOT_GET_VAR_NAMED,
      "無法取得名稱為 {0} 的變數"},

  { ER_UNKNOWN_OPCODE,
     "錯誤！不明的作業代碼: {0}"},

  { ER_EXTRA_ILLEGAL_TOKENS,
     "額外的無效記號: {0}"},

  { ER_EXPECTED_DOUBLE_QUOTE,
      "引號錯誤的文字... 預期雙引號！"},

  { ER_EXPECTED_SINGLE_QUOTE,
      "引號錯誤的文字... 預期單引號！"},

  { ER_EMPTY_EXPRESSION,
     "空白表示式！"},

  { ER_EXPECTED_BUT_FOUND,
     "預期 {0}，但找到: {1}"},

  { ER_INCORRECT_PROGRAMMER_ASSERTION,
      "程式設計人員宣告不正確！- {0}"},

  { ER_BOOLEAN_ARG_NO_LONGER_OPTIONAL,
      "根據 19990709 XPath 草案，boolean(...) 不再是選擇性引數。"},

  { ER_FOUND_COMMA_BUT_NO_PRECEDING_ARG,
      "找到 ',' 但沒有先前的引數！"},

  { ER_FOUND_COMMA_BUT_NO_FOLLOWING_ARG,
      "找到 ',' 但沒有後續的引數！"},

  { ER_PREDICATE_ILLEGAL_SYNTAX,
      "'..[predicate]' 或 '.[predicate]' 是無效的語法。請改用 'self::node()[predicate]'。"},

  { ER_ILLEGAL_AXIS_NAME,
     "無效的軸名稱: {0}"},

  { ER_UNKNOWN_NODETYPE,
     "不明的 nodetype: {0}"},

  { ER_PATTERN_LITERAL_NEEDS_BE_QUOTED,
      "樣式文字 ({0}) 需要加上引號！"},

  { ER_COULDNOT_BE_FORMATTED_TO_NUMBER,
      "{0} 無法格式化為數字！"},

  { ER_COULDNOT_CREATE_XMLPROCESSORLIAISON,
      "無法建立 XML TransformerFactory Liaison: {0}"},

  { ER_DIDNOT_FIND_XPATH_SELECT_EXP,
      "錯誤！找不到 xpath 選取表示式 (-select)。"},

  { ER_COULDNOT_FIND_ENDOP_AFTER_OPLOCATIONPATH,
      "錯誤！在 OP_LOCATIONPATH 之後找不到 ENDOP"},

  { ER_ERROR_OCCURED,
     "發生錯誤！"},

  { ER_ILLEGAL_VARIABLE_REFERENCE,
      "為變數指定的 VariableReference 超出相關資訊環境或沒有定義！名稱 = {0}"},

  { ER_AXES_NOT_ALLOWED,
      "配對樣式中僅允許 child:: 與 attribute:: 軸！違反的軸 = {0}"},

  { ER_KEY_HAS_TOO_MANY_ARGS,
      "key() 具有不正確的引數數目。"},

  { ER_COUNT_TAKES_1_ARG,
      "count 函數應接受一個引數！"},

  { ER_COULDNOT_FIND_FUNCTION,
     "找不到函數: {0}"},

  { ER_UNSUPPORTED_ENCODING,
     "不支援的編碼: {0}"},

  { ER_PROBLEM_IN_DTM_NEXTSIBLING,
      "在 getNextSibling 的 DTM 中發生問題... 正在嘗試復原"},

  { ER_CANNOT_WRITE_TO_EMPTYNODELISTIMPL,
      "程式設計人員錯誤: 無法寫入 EmptyNodeList。"},

  { ER_SETDOMFACTORY_NOT_SUPPORTED,
      "XPathContext 不支援 setDOMFactory！"},

  { ER_PREFIX_MUST_RESOLVE,
      "前置碼必須解析為命名空間: {0}"},

  { ER_PARSE_NOT_SUPPORTED,
      "XPathContext 中不支援 parse (InputSource 來源)。無法開啟 {0}"},

  { ER_SAX_API_NOT_HANDLED,
      "SAX API characters(char ch[]... 並非由 DTM 處理！"},

  { ER_IGNORABLE_WHITESPACE_NOT_HANDLED,
      "ignorableWhitespace(char ch[]... 並非由 DTM 處理！"},

  { ER_DTM_CANNOT_HANDLE_NODES,
      "DTMLiaison 無法處理類型 {0} 的控制代碼節點"},

  { ER_XERCES_CANNOT_HANDLE_NODES,
      "DOM2Helper 無法處理類型 {0} 的控制代碼節點"},

  { ER_XERCES_PARSE_ERROR_DETAILS,
      "DOM2Helper.parse 錯誤: SystemID - {0} 行 - {1}"},

  { ER_XERCES_PARSE_ERROR,
     "DOM2Helper.parse 錯誤"},

  { ER_INVALID_UTF16_SURROGATE,
      "偵測到無效的 UTF-16 代理: {0}？"},

  { ER_OIERROR,
     "IO 錯誤"},

  { ER_CANNOT_CREATE_URL,
     "無法為 {0} 建立 url"},

  { ER_XPATH_READOBJECT,
     "在 XPath.readObject 中: {0}"},

  { ER_FUNCTION_TOKEN_NOT_FOUND,
      "找不到函數記號。"},

  { ER_CANNOT_DEAL_XPATH_TYPE,
       "無法處理 XPath 類型: {0}"},

  { ER_NODESET_NOT_MUTABLE,
       "此 NodeSet 不可變更"},

  { ER_NODESETDTM_NOT_MUTABLE,
       "此 NodeSetDTM 不可變更"},

  { ER_VAR_NOT_RESOLVABLE,
        "變數無法解析: {0}"},

  { ER_NULL_ERROR_HANDLER,
        "空值錯誤處理程式"},

  { ER_PROG_ASSERT_UNKNOWN_OPCODE,
       "程式設計人員宣告: 不明的 opcode: {0}"},

  { ER_ZERO_OR_ONE,
       "0 或 1"},

  { ER_RTF_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
       "XRTreeFragSelectWrapper 不支援 rtf()"},

  { ER_ASNODEITERATOR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
       "XRTreeFragSelectWrapper 不支援 asNodeIterator()"},

        /**  detach() not supported by XRTreeFragSelectWrapper   */
   { ER_DETACH_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
                "XRTreeFragSelectWrapper 不支援 detach()"},

        /**  num() not supported by XRTreeFragSelectWrapper   */
   { ER_NUM_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
                "XRTreeFragSelectWrapper 不支援 num()"},

        /**  xstr() not supported by XRTreeFragSelectWrapper   */
   { ER_XSTR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
                "XRTreeFragSelectWrapper 不支援 xstr()"},

        /**  str() not supported by XRTreeFragSelectWrapper   */
   { ER_STR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
                "XRTreeFragSelectWrapper 不支援 str()"},

  { ER_FSB_NOT_SUPPORTED_XSTRINGFORCHARS,
       "XStringForChars 不支援 fsb()"},

  { ER_COULD_NOT_FIND_VAR,
      "找不到名稱為 {0} 的變數"},

  { ER_XSTRINGFORCHARS_CANNOT_TAKE_STRING,
      "XStringForChars 無法接受字串作為引數"},

  { ER_FASTSTRINGBUFFER_CANNOT_BE_NULL,
      "FastStringBuffer 引數不可為空值"},

  { ER_TWO_OR_THREE,
       "2 或 3"},

  { ER_VARIABLE_ACCESSED_BEFORE_BIND,
       "變數連結之前便進行存取！"},

  { ER_FSB_CANNOT_TAKE_STRING,
       "XStringForFSB 無法接受字串作為引數！"},

  { ER_SETTING_WALKER_ROOT_TO_NULL,
       "\n 錯誤！將蒐集程式的根設定為空值！"},

  { ER_NODESETDTM_CANNOT_ITERATE,
       "此 NodeSetDTM 無法重複先前的節點！"},

  { ER_NODESET_CANNOT_ITERATE,
       "此 NodeSet 無法重複先前的節點！"},

  { ER_NODESETDTM_CANNOT_INDEX,
       "此 NodeSetDTM 無法執行製作索引或計數功能！"},

  { ER_NODESET_CANNOT_INDEX,
       "此 NodeSet 無法執行製作索引或計數功能！"},

  { ER_CANNOT_CALL_SETSHOULDCACHENODE,
       "呼叫 nextNode 之後，無法呼叫 setShouldCacheNodes！"},

  { ER_ONLY_ALLOWS,
       "{0} 僅允許 {1} 個引數"},

  { ER_UNKNOWN_STEP,
       "在 getNextStepPos 中程式設計人員的宣告: 不明的 stepType: {0}"},

  //Note to translators:  A relative location path is a form of XPath expression.
  // The message indicates that such an expression was expected following the
  // characters '/' or '//', but was not found.
  { ER_EXPECTED_REL_LOC_PATH,
      "'/' 或 '//' 記號之後，預期相對位置路徑。"},

  // Note to translators:  A location path is a form of XPath expression.
  // The message indicates that syntactically such an expression was expected,but
  // the characters specified by the substitution text were encountered instead.
  { ER_EXPECTED_LOC_PATH,
       "預期位置路徑，但出現下列記號:  {0}"},

  // Note to translators:  A location path is a form of XPath expression.
  // The message indicates that syntactically such a subexpression was expected,
  // but no more characters were found in the expression.
  { ER_EXPECTED_LOC_PATH_AT_END_EXPR,
       "預期位置路徑，但出現 XPath 表示式的結尾。"},

  // Note to translators:  A location step is part of an XPath expression.
  // The message indicates that syntactically such an expression was expected
  // following the specified characters.
  { ER_EXPECTED_LOC_STEP,
       "'/' 或 '//' 記號之後，預期位置步驟。"},

  // Note to translators:  A node test is part of an XPath expression that is
  // used to test for particular kinds of nodes.  In this case, a node test that
  // consists of an NCName followed by a colon and an asterisk or that consists
  // of a QName was expected, but was not found.
  { ER_EXPECTED_NODE_TEST,
       "預期符合 NCName:* 或 QName 的節點測試。"},

  // Note to translators:  A step pattern is part of an XPath expression.
  // The message indicates that syntactically such an expression was expected,
  // but the specified character was found in the expression instead.
  { ER_EXPECTED_STEP_PATTERN,
       "預期步驟樣式，但出現 '/'。"},

  // Note to translators: A relative path pattern is part of an XPath expression.
  // The message indicates that syntactically such an expression was expected,
  // but was not found.
  { ER_EXPECTED_REL_PATH_PATTERN,
       "預期相對路徑樣式。"},

  // Note to translators:  The substitution text is the name of a data type.  The
  // message indicates that a value of a particular type could not be converted
  // to a value of type boolean.
  { ER_CANT_CONVERT_TO_BOOLEAN,
       "XPath 表示式 ''{0}'' 的 XPathResult 具有 XPathResultType 的 {1}，它無法轉換為布林值。"},

  // Note to translators: Do not translate ANY_UNORDERED_NODE_TYPE and
  // FIRST_ORDERED_NODE_TYPE.
  { ER_CANT_CONVERT_TO_SINGLENODE,
       "XPath 表示式 ''{0}'' 的 XPathResult 具有 XPathResultType 的 {1}，它無法轉換為單一節點。方法 getSingleNodeValue 僅適用於類型 ANY_UNORDERED_NODE_TYPE 與 FIRST_ORDERED_NODE_TYPE。"},

  // Note to translators: Do not translate UNORDERED_NODE_SNAPSHOT_TYPE and
  // ORDERED_NODE_SNAPSHOT_TYPE.
  { ER_CANT_GET_SNAPSHOT_LENGTH,
       "無法在 XPath 表示式 ''{0}'' 的 XPathResult 上呼叫方法 getSnapshotLength，因為它的 XPathResultType 是 {1}。此方法僅適用於類型 UNORDERED_NODE_SNAPSHOT_TYPE 與 ORDERED_NODE_SNAPSHOT_TYPE。"},

  { ER_NON_ITERATOR_TYPE,
       "無法在 XPath 表示式 ''{0}'' 的 XPathResult 上呼叫方法 iterateNext，因為它的 XPathResultType 是 {1}。此方法僅適用於類型 UNORDERED_NODE_ITERATOR_TYPE 與 ORDERED_NODE_ITERATOR_TYPE。"},

  // Note to translators: This message indicates that the document being operated
  // upon changed, so the iterator object that was being used to traverse the
  // document has now become invalid.
  { ER_DOC_MUTATED,
       "結果傳回後文件已變更。重複程式無效。"},

  { ER_INVALID_XPATH_TYPE,
       "無效的 XPath 類型引數: {0}"},

  { ER_EMPTY_XPATH_RESULT,
       "空白的 XPath 結果物件"},

  { ER_INCOMPATIBLE_TYPES,
       "XPath 表示式 ''{0}'' 的 XPathResult 具有 XPathResultType 的 {1}，它無法強制轉成 {2} 指定的 XPathResultType。"},

  { ER_NULL_RESOLVER,
       "無法以空值前置碼解析器來解析前置碼。"},

  // Note to translators:  The substitution text is the name of a data type.  The
  // message indicates that a value of a particular type could not be converted
  // to a value of type string.
  { ER_CANT_CONVERT_TO_STRING,
       "XPath 表示式 ''{0}'' 的 XPathResult 具有 XPathResultType 的 {1}，它無法轉換為字串。"},

  // Note to translators: Do not translate snapshotItem,
  // UNORDERED_NODE_SNAPSHOT_TYPE and ORDERED_NODE_SNAPSHOT_TYPE.
  { ER_NON_SNAPSHOT_TYPE,
       "無法在 XPath 表示式 ''{0}'' 的 XPathResult 上呼叫方法 snapshotItem，因為它的 XPathResultType 是 {1}。此方法僅適用於類型 UNORDERED_NODE_SNAPSHOT_TYPE 與 ORDERED_NODE_SNAPSHOT_TYPE。"},

  // Note to translators:  XPathEvaluator is a Java interface name.  An
  // XPathEvaluator is created with respect to a particular XML document, and in
  // this case the expression represented by this object was being evaluated with
  // respect to a context node from a different document.
  { ER_WRONG_DOCUMENT,
       "相關資訊環境節點不屬於連結至此 XPathEvaluator 的文件。"},

  // Note to translators:  The XPath expression cannot be evaluated with respect
  // to this type of node.
  { ER_WRONG_NODETYPE,
       "不支援相關資訊環境節點類型。"},

  { ER_XPATH_ERROR,
       "XPath 發生不明的錯誤。"},

  { ER_CANT_CONVERT_XPATHRESULTTYPE_TO_NUMBER,
        "XPath 表示式 ''{0}'' 的 XPathResult 具有 XPathResultType 的 {1}，它無法轉換為數字。"},

  //BEGIN:  Definitions of error keys used  in exception messages of  JAXP 1.3 XPath API implementation

  /** Field ER_EXTENSION_FUNCTION_CANNOT_BE_INVOKED                       */

  { ER_EXTENSION_FUNCTION_CANNOT_BE_INVOKED,
       "當 XMLConstants.FEATURE_SECURE_PROCESSING 功能設為真時，無法呼叫擴充函數: ''{0}''。"},

  /** Field ER_RESOLVE_VARIABLE_RETURNS_NULL                       */

  { ER_RESOLVE_VARIABLE_RETURNS_NULL,
       "變數 {0} 的 resolveVariable 傳回空值"},

  /** Field ER_UNSUPPORTED_RETURN_TYPE                       */

  { ER_UNSUPPORTED_RETURN_TYPE,
       "不支援的傳回類型: {0}"},

  /** Field ER_SOURCE_RETURN_TYPE_CANNOT_BE_NULL                       */

  { ER_SOURCE_RETURN_TYPE_CANNOT_BE_NULL,
       "來源和 (或) 傳回類型不可為空值"},

  /** Field ER_SOURCE_RETURN_TYPE_CANNOT_BE_NULL                       */

  { ER_SOURCE_RETURN_TYPE_CANNOT_BE_NULL,
       "來源和 (或) 傳回類型不可為空值"},

  /** Field ER_ARG_CANNOT_BE_NULL                       */

  { ER_ARG_CANNOT_BE_NULL,
       "{0} 引數不可為空值"},

  /** Field ER_OBJECT_MODEL_NULL                       */

  { ER_OBJECT_MODEL_NULL,
       "{0}#isObjectModelSupported( String objectModel ) 無法使用 objectModel == null 來呼叫"},

  /** Field ER_OBJECT_MODEL_EMPTY                       */

  { ER_OBJECT_MODEL_EMPTY,
       "{0}#isObjectModelSupported( String objectModel ) 無法使用 objectModel == \"\" 來呼叫"},

  /** Field ER_OBJECT_MODEL_EMPTY                       */

  { ER_FEATURE_NAME_NULL,
       "嘗試以空值名稱設定功能: {0}#setFeature( null, {1})"},

  /** Field ER_FEATURE_UNKNOWN                       */

  { ER_FEATURE_UNKNOWN,
       "嘗試設定不明的功能 \"{0}\":{1}#setFeature({0},{2})"},

  /** Field ER_GETTING_NULL_FEATURE                       */

  { ER_GETTING_NULL_FEATURE,
       "嘗試以空值名稱取得功能: {0}#getFeature(null)"},

  /** Field ER_GETTING_NULL_FEATURE                       */

  { ER_GETTING_UNKNOWN_FEATURE,
       "嘗試取得不明的功能 \"{0}\":{1}#getFeature({0})"},

  {ER_SECUREPROCESSING_FEATURE,
        "FEATURE_SECURE_PROCESSING: 安全管理程式存在時，無法將功能設為偽: {1}#setFeature({0},{2})"},

  /** Field ER_NULL_XPATH_FUNCTION_RESOLVER                       */

  { ER_NULL_XPATH_FUNCTION_RESOLVER,
       "嘗試設定空值 XPathFunctionResolver:{0}#setXPathFunctionResolver(null)"},

  /** Field ER_NULL_XPATH_VARIABLE_RESOLVER                       */

  { ER_NULL_XPATH_VARIABLE_RESOLVER,
       "嘗試設定空值 XPathVariableResolver:{0}#setXPathVariableResolver(null)"},

  //END:  Definitions of error keys used  in exception messages of  JAXP 1.3 XPath API implementation

  // Warnings...

  { WG_LOCALE_NAME_NOT_HANDLED,
      "尚未處理 format-number 函數中的地區設定名稱！"},

  { WG_PROPERTY_NOT_SUPPORTED,
      "不支援 XSL 屬性: {0}"},

  { WG_DONT_DO_ANYTHING_WITH_NS,
      "目前不會處理屬性中的命名空間 {0}: {1}"},

  { WG_QUO_NO_LONGER_DEFINED,
      "舊語法: XPath 中不再定義 quo(...)。"},

  { WG_NEED_DERIVED_OBJECT_TO_IMPLEMENT_NODETEST,
      "XPath 需要衍生的物件來實行 nodeTest！"},

  { WG_FUNCTION_TOKEN_NOT_FOUND,
      "找不到函數記號。"},

  { WG_COULDNOT_FIND_FUNCTION,
      "找不到函數: {0}"},

  { WG_CANNOT_MAKE_URL_FROM,
      "無法從 {0} 建立 URL"},

  { WG_EXPAND_ENTITIES_NOT_SUPPORTED,
      "DTM 剖析器不支援 -E 選項"},

  { WG_ILLEGAL_VARIABLE_REFERENCE,
      "為變數指定的 VariableReference 超出相關資訊環境或沒有定義！名稱 = {0}"},

  { WG_UNSUPPORTED_ENCODING,
     "不支援的編碼: {0}"},



  // Other miscellaneous text used inside the code...
  { "ui_language", "tw"},
  { "help_language", "tw"},
  { "language", "tw"},
  { "BAD_CODE", "createMessage 的參數超出範圍"},
  { "FORMAT_FAILED", "messageFormat 呼叫期間發生異常狀況"},
  { "version", ">>>>>>> Xalan 版本 "},
  { "version2", "<<<<<<<"},
  { "yes", "是"},
  { "line", "行號"},
  { "column", "資料欄編號"},
  { "xsldone", "XSLProcessor: 完成"},
  { "xpath_option", "xpath 選項: "},
  { "optionIN", "   [-in inputXMLURL]"},
  { "optionSelect", "   [-select xpath 表示式]"},
  { "optionMatch", "   [-match 配對樣式 (針對配對診斷)]"},
  { "optionAnyExpr", "或者，只有 xpath 表示式時將進行診斷傾印"},
  { "noParsermsg1", "XSL 處理作業失敗。"},
  { "noParsermsg2", "** 找不到剖析器 **"},
  { "noParsermsg3", "請檢查類別路徑。"},
  { "noParsermsg4", "若無 IBM 的 XML Parser for Java，可下載自"},
  { "noParsermsg5", "IBM 的 AlphaWorks: http://www.alphaworks.ibm.com/formula/xml"},
  { "gtone", ">1" },
  { "zero", "0" },
  { "one", "1" },
  { "two" , "2" },
  { "three", "3" }

  };

  /**
   * Get the association list.
   *
   * @return The association list.
   */
  public Object[][] getContents()
  {
      return _contents;
  }


  // ================= INFRASTRUCTURE ======================

  /** Field BAD_CODE          */
  public static final String BAD_CODE = "BAD_CODE";

  /** Field FORMAT_FAILED          */
  public static final String FORMAT_FAILED = "FORMAT_FAILED";

  /** Field ERROR_RESOURCES          */
  public static final String ERROR_RESOURCES =
    "com.sun.org.apache.xpath.internal.res.XPATHErrorResources";

  /** Field ERROR_STRING          */
  public static final String ERROR_STRING = "#error";

  /** Field ERROR_HEADER          */
  public static final String ERROR_HEADER = "Error: ";

  /** Field WARNING_HEADER          */
  public static final String WARNING_HEADER = "Warning: ";

  /** Field XSL_HEADER          */
  public static final String XSL_HEADER = "XSL ";

  /** Field XML_HEADER          */
  public static final String XML_HEADER = "XML ";

  /** Field QUERY_HEADER          */
  public static final String QUERY_HEADER = "PATTERN ";

}
