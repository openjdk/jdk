/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
public class XPATHErrorResources_ja extends ListResourceBundle
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
  public static final String ER_UNION_MUST_BE_NODESET = "ER_UNION_MUST_BE_NODESET";
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
  public static final String ER_PREDICATE_TOO_MANY_OPEN =
         "ER_PREDICATE_TOO_MANY_OPEN";
  public static final String ER_COMPILATION_TOO_MANY_OPERATION =
         "ER_COMPILATION_TOO_MANY_OPERATION";
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
  public static final String ER_NO_XPATH_VARIABLE_RESOLVER = "ER_NO_XPATH_VARIABLE_RESOLVER";
  public static final String ER_NO_XPATH_FUNCTION_PROVIDER = "ER_NO_XPATH_FUNCTION_PROVIDER";
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
  public static final String ER_PROPERTY_NAME_NULL = "ER_PROPERTY_NAME_NULL";
  public static final String ER_PROPERTY_UNKNOWN = "ER_PROPERTY_UNKNOWN";
  public static final String ER_GETTING_NULL_PROPERTY = "ER_GETTING_NULL_PROPERTY";
  public static final String ER_GETTING_UNKNOWN_PROPERTY = "ER_GETTING_UNKNOWN_PROPERTY";
  public static final String ER_XPATH_GROUP_LIMIT = "ER_XPATH_GROUP_LIMIT";
  public static final String ER_XPATH_OPERATOR_LIMIT = "ER_XPATH_OPERATOR_LIMIT";

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

  { ER_CURRENT_NOT_ALLOWED_IN_MATCH, "current()関数は一致パターンでは許可されません。" },

  { ER_CURRENT_TAKES_NO_ARGS, "current()関数は引数を受け入れません。" },

  { ER_DOCUMENT_REPLACED,
      "document()関数の実装はcom.sun.org.apache.xalan.internal.xslt.FuncDocumentによって置換されました。"},

  { ER_CONTEXT_CAN_NOT_BE_NULL,
      "操作がコンテキストに依存している場合、コンテキストをnullにすることはできません。"},

  { ER_CONTEXT_HAS_NO_OWNERDOC,
      "コンテキストに所有者ドキュメントがありません。"},

  { ER_LOCALNAME_HAS_TOO_MANY_ARGS,
      "local-name()の引数が多すぎます。"},

  { ER_NAMESPACEURI_HAS_TOO_MANY_ARGS,
      "namespace-uri()の引数が多すぎます。"},

  { ER_NORMALIZESPACE_HAS_TOO_MANY_ARGS,
      "normalize-space()の引数が多すぎます。"},

  { ER_NUMBER_HAS_TOO_MANY_ARGS,
      "number()の引数が多すぎます。"},

  { ER_NAME_HAS_TOO_MANY_ARGS,
     "name()の引数が多すぎます。"},

  { ER_STRING_HAS_TOO_MANY_ARGS,
      "string()の引数が多すぎます。"},

  { ER_STRINGLENGTH_HAS_TOO_MANY_ARGS,
      "string-length()の引数が多すぎます。"},

  { ER_TRANSLATE_TAKES_3_ARGS,
      "translate()関数は3つの引数を取ります。"},

  { ER_UNPARSEDENTITYURI_TAKES_1_ARG,
      "unparsed-entity-uri関数は引数を1つ取る必要があります。"},

  { ER_NAMESPACEAXIS_NOT_IMPLEMENTED,
      "namespace軸はまだ実装されていません。"},

  { ER_UNKNOWN_AXIS,
     "不明な軸です: {0}"},

  { ER_UNKNOWN_MATCH_OPERATION,
     "不明な一致操作です。"},

  { ER_INCORRECT_ARG_LENGTH,
      "processing-instruction()ノード・テストの引数の長さが不正です。"},

  { ER_CANT_CONVERT_TO_NUMBER,
      "{0}を数値に変換できません"},

  { ER_CANT_CONVERT_TO_NODELIST,
      "{0}をNodeListに変換できません。"},

  { ER_CANT_CONVERT_TO_MUTABLENODELIST,
      "{0}をNodeSetDTMに変換できません。"},

  { ER_CANT_CONVERT_TO_TYPE,
      "{0}をtype#{1}に変換できません"},

  { ER_EXPECTED_MATCH_PATTERN,
      "getMatchScoreに一致パターンが必要です。"},

  { ER_COULDNOT_GET_VAR_NAMED,
      "名前{0}の変数を取得できませんでした"},

  { ER_UNKNOWN_OPCODE,
     "エラー。不明な操作コード: {0}"},

  { ER_EXTRA_ILLEGAL_TOKENS,
     "余分の不正なトークン: {0}"},

  { ER_EXPECTED_DOUBLE_QUOTE,
      "リテラルの引用符が不正です... 二重引用符が必要です。"},

  { ER_EXPECTED_SINGLE_QUOTE,
      "リテラルの引用符が不正です... 一重引用符が必要です。"},

  { ER_EMPTY_EXPRESSION,
     "式が空です。"},

  { ER_EXPECTED_BUT_FOUND,
     "{0}ではなく{1}が検出されました"},

  { ER_UNION_MUST_BE_NODESET,
     "共用体のオペランドは、ノードセットである必要があります。"},

  { ER_INCORRECT_PROGRAMMER_ASSERTION,
      "プログラマ・アサーションが不正です。- {0}"},

  { ER_BOOLEAN_ARG_NO_LONGER_OPTIONAL,
      "boolean(...)引数は、19990709 XPathドラフトによってオプションでなくなりました。"},

  { ER_FOUND_COMMA_BUT_NO_PRECEDING_ARG,
      "','が見つかりましたが前に引数がありません。"},

  { ER_FOUND_COMMA_BUT_NO_FOLLOWING_ARG,
      "','が見つかりましたが後ろに引数がありません。"},

  { ER_PREDICATE_ILLEGAL_SYNTAX,
      "'..[predicate]'または'.[predicate]'は不正な構文です。かわりに'self::node()[predicate]'を使用してください。"},

  { ER_PREDICATE_TOO_MANY_OPEN,
      "{1}で{0}を解析中にスタック・オーバーフローが発生しました。オープン述語が多すぎます({2})。"},

  { ER_COMPILATION_TOO_MANY_OPERATION,
      "式のコンパイル中にスタック・オーバーフローが発生しました。操作が多すぎます({0})。"},

  { ER_ILLEGAL_AXIS_NAME,
     "不正な軸名: {0}"},

  { ER_UNKNOWN_NODETYPE,
     "不明なnodetype: {0}"},

  { ER_PATTERN_LITERAL_NEEDS_BE_QUOTED,
      "パターン・リテラル({0})に引用符を付ける必要があります。"},

  { ER_COULDNOT_BE_FORMATTED_TO_NUMBER,
      "{0}を数値にフォーマットできませんでした。"},

  { ER_COULDNOT_CREATE_XMLPROCESSORLIAISON,
      "XML TransformerFactory Liaisonを作成できませんでした: {0}"},

  { ER_DIDNOT_FIND_XPATH_SELECT_EXP,
      "エラー。xpath選択式(-select)が見つかりませんでした。"},

  { ER_COULDNOT_FIND_ENDOP_AFTER_OPLOCATIONPATH,
      "エラー。OP_LOCATIONPATHの後にENDOPが見つかりませんでした"},

  { ER_ERROR_OCCURED,
     "エラーが発生しました。"},

  { ER_ILLEGAL_VARIABLE_REFERENCE,
      "変数に指定したVariableReferenceがコンテキスト範囲外か定義がありません。名前= {0}"},

  { ER_AXES_NOT_ALLOWED,
      "一致パターンでは、child::軸とattribute::軸のみが許可されます。問題となる軸= {0}"},

  { ER_KEY_HAS_TOO_MANY_ARGS,
      "key()が持つ引数の数が不正です。"},

  { ER_COUNT_TAKES_1_ARG,
      "カウント関数は引数を1つ取る必要があります。"},

  { ER_COULDNOT_FIND_FUNCTION,
     "関数{0}が見つかりませんでした"},

  { ER_UNSUPPORTED_ENCODING,
     "サポートされていないエンコーディングです: {0}"},

  { ER_PROBLEM_IN_DTM_NEXTSIBLING,
      "getNextSiblingのDTMで問題が発生しました...復元の試行中です"},

  { ER_CANNOT_WRITE_TO_EMPTYNODELISTIMPL,
      "プログラマ・エラー: EmptyNodeListに書き込めません。"},

  { ER_SETDOMFACTORY_NOT_SUPPORTED,
      "setDOMFactoryはXPathContextでサポートされていません。"},

  { ER_PREFIX_MUST_RESOLVE,
      "接頭辞はネームスペースに解決される必要があります: {0}"},

  { ER_PARSE_NOT_SUPPORTED,
      "解析(InputSourceソース)はXPathContextでサポートされていません。{0}を開けません"},

  { ER_SAX_API_NOT_HANDLED,
      "SAX API characters(char ch[]...はDTMによって処理されません。"},

  { ER_IGNORABLE_WHITESPACE_NOT_HANDLED,
      "ignorableWhitespace(char ch[]...はDTMによって処理されません。"},

  { ER_DTM_CANNOT_HANDLE_NODES,
      "DTMLiaisonはタイプ{0}のノードを処理できません"},

  { ER_XERCES_CANNOT_HANDLE_NODES,
      "DOM2Helperは{0}タイプのノードを処理できません"},

  { ER_XERCES_PARSE_ERROR_DETAILS,
      "DOM2Helper.parseエラー: SystemID - {0} 行 - {1}"},

  { ER_XERCES_PARSE_ERROR,
     "DOM2Helper.parseエラー"},

  { ER_INVALID_UTF16_SURROGATE,
      "無効なUTF-16サロゲートが検出されました: {0}。"},

  { ER_OIERROR,
     "IOエラー"},

  { ER_CANNOT_CREATE_URL,
     "{0}のURLを作成できません"},

  { ER_XPATH_READOBJECT,
     "XPath.readObject内: {0}"},

  { ER_FUNCTION_TOKEN_NOT_FOUND,
      "関数トークンが見つかりません。"},

  { ER_CANNOT_DEAL_XPATH_TYPE,
       "XPathタイプを処理できません: {0}"},

  { ER_VAR_NOT_RESOLVABLE,
        "変数を解決できません: {0}"},

  { ER_NULL_ERROR_HANDLER,
        "Nullのエラー・ハンドラ"},

  { ER_PROG_ASSERT_UNKNOWN_OPCODE,
       "プログラマのアサーション: 不明なopcode: {0}"},

  { ER_ZERO_OR_ONE,
       "0または1"},

  { ER_RTF_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
       "rtf()はXRTreeFragSelectWrapperによってサポートされていません"},

  { ER_ASNODEITERATOR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
       "asNodeIterator()はXRTreeFragSelectWrapperによってサポートされていません"},

        /**  detach() not supported by XRTreeFragSelectWrapper   */
   { ER_DETACH_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
                "detach()はXRTreeFragSelectWrapperによってサポートされていません"},

        /**  num() not supported by XRTreeFragSelectWrapper   */
   { ER_NUM_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
                "num()はXRTreeFragSelectWrapperによってサポートされていません"},

        /**  xstr() not supported by XRTreeFragSelectWrapper   */
   { ER_XSTR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
                "xstr()はXRTreeFragSelectWrapperによってサポートされていません"},

        /**  str() not supported by XRTreeFragSelectWrapper   */
   { ER_STR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
                "str()はXRTreeFragSelectWrapperによってサポートされていません"},

  { ER_FSB_NOT_SUPPORTED_XSTRINGFORCHARS,
       "fsb()はXStringForChars用にサポートされていません"},

  { ER_COULD_NOT_FIND_VAR,
      "名前{0}の変数が見つかりませんでした"},

  { ER_XSTRINGFORCHARS_CANNOT_TAKE_STRING,
      "XStringForCharsは引数について文字列を取ることができません"},

  { ER_FASTSTRINGBUFFER_CANNOT_BE_NULL,
      "FastStringBuffer引数はnullにできません"},

  { ER_TWO_OR_THREE,
       "2または3"},

  { ER_VARIABLE_ACCESSED_BEFORE_BIND,
       "変数がバインドされる前にアクセスされました。"},

  { ER_FSB_CANNOT_TAKE_STRING,
       "XStringForFSBは引数について文字列を取ることができません。"},

  { ER_SETTING_WALKER_ROOT_TO_NULL,
       "\n エラー。ウォーカのルートをnullに設定しています。"},

  { ER_NODESETDTM_CANNOT_ITERATE,
       "このNodeSetDTMは前のノードを反復できません。"},

  { ER_NODESET_CANNOT_ITERATE,
       "このNodeSetは前のノードを反復できません。"},

  { ER_NODESETDTM_CANNOT_INDEX,
       "このNodeSetDTMは索引付けまたはカウント機能を実行できません。"},

  { ER_NODESET_CANNOT_INDEX,
       "このNodeSetは索引付けまたはカウント機能を実行できません。"},

  { ER_CANNOT_CALL_SETSHOULDCACHENODE,
       "nextNodeを呼び出した後にsetShouldCacheNodesを呼び出せません。"},

  { ER_ONLY_ALLOWS,
       "{0}は{1}個の引数のみ許可します"},

  { ER_UNKNOWN_STEP,
       "getNextStepPosでのプログラマのアサーション: 不明なstepType: {0}"},

  //Note to translators:  A relative location path is a form of XPath expression.
  // The message indicates that such an expression was expected following the
  // characters '/' or '//', but was not found.
  { ER_EXPECTED_REL_LOC_PATH,
      "'/'または'//'トークンの後に相対ロケーション・パスが必要です。"},

  // Note to translators:  A location path is a form of XPath expression.
  // The message indicates that syntactically such an expression was expected,but
  // the characters specified by the substitution text were encountered instead.
  { ER_EXPECTED_LOC_PATH,
       "ロケーション・パスが必要ですが、次のトークンが検出されました:  {0}"},

  // Note to translators:  A location path is a form of XPath expression.
  // The message indicates that syntactically such a subexpression was expected,
  // but no more characters were found in the expression.
  { ER_EXPECTED_LOC_PATH_AT_END_EXPR,
       "ロケーション・パスが必要ですが、かわりにXPath式の終わりが検出されました。"},

  // Note to translators:  A location step is part of an XPath expression.
  // The message indicates that syntactically such an expression was expected
  // following the specified characters.
  { ER_EXPECTED_LOC_STEP,
       "'/'または'//'トークンの後にロケーション・ステップが必要です。"},

  // Note to translators:  A node test is part of an XPath expression that is
  // used to test for particular kinds of nodes.  In this case, a node test that
  // consists of an NCName followed by a colon and an asterisk or that consists
  // of a QName was expected, but was not found.
  { ER_EXPECTED_NODE_TEST,
       "NCName:*またはQNameに一致するノード・テストがありません。"},

  // Note to translators:  A step pattern is part of an XPath expression.
  // The message indicates that syntactically such an expression was expected,
  // but the specified character was found in the expression instead.
  { ER_EXPECTED_STEP_PATTERN,
       "ステップ・パターンが必要ですが、'/'が検出されました。"},

  // Note to translators: A relative path pattern is part of an XPath expression.
  // The message indicates that syntactically such an expression was expected,
  // but was not found.
  { ER_EXPECTED_REL_PATH_PATTERN,
       "相対パス・パターンがありません。"},

  // Note to translators:  The substitution text is the name of a data type.  The
  // message indicates that a value of a particular type could not be converted
  // to a value of type boolean.
  { ER_CANT_CONVERT_TO_BOOLEAN,
       "XPath式''{0}''のXPathResultは、booleanに変換できない{1}のXPathResultTypeです。"},

  // Note to translators: Do not translate ANY_UNORDERED_NODE_TYPE and
  // FIRST_ORDERED_NODE_TYPE.
  { ER_CANT_CONVERT_TO_SINGLENODE,
       "XPath式''{0}''のXPathResultは、単一ノードに変換できない{1}のXPathResultTypeです。メソッドgetSingleNodeValueは、ANY_UNORDERED_NODE_TYPEタイプおよびFIRST_ORDERED_NODE_TYPEタイプにのみ適用されます。"},

  // Note to translators: Do not translate UNORDERED_NODE_SNAPSHOT_TYPE and
  // ORDERED_NODE_SNAPSHOT_TYPE.
  { ER_CANT_GET_SNAPSHOT_LENGTH,
       "XPathResultTypeが{1}のため、メソッドgetSnapshotLengthはXPath式''{0}''のXPathResultで呼び出すことができません。このメソッドは、UNORDERED_NODE_SNAPSHOT_TYPEタイプおよびORDERED_NODE_SNAPSHOT_TYPEタイプにのみ適用されます。"},

  { ER_NON_ITERATOR_TYPE,
       "XPathResultTypeが{1}のため、メソッドiterateNextはXPath式''{0}''のXPathResultで呼び出すことができません。このメソッドは、UNORDERED_NODE_ITERATOR_TYPEタイプおよびORDERED_NODE_ITERATOR_TYPEタイプにのみ適用されます。"},

  // Note to translators: This message indicates that the document being operated
  // upon changed, so the iterator object that was being used to traverse the
  // document has now become invalid.
  { ER_DOC_MUTATED,
       "結果が返された後にドキュメントが変更されました。イテレータが無効です。"},

  { ER_INVALID_XPATH_TYPE,
       "XPathタイプの引数{0}が無効です"},

  { ER_EMPTY_XPATH_RESULT,
       "XPath結果オブジェクトが空です"},

  { ER_INCOMPATIBLE_TYPES,
       "XPath式''{0}''のXPathResultは、{2}の指定されたXPathResultTypeに強制変換できない{1}のXPathResultTypeを持ちます。"},

  { ER_NULL_RESOLVER,
       "null接頭辞リゾルバで接頭辞を解決できません。"},

  // Note to translators:  The substitution text is the name of a data type.  The
  // message indicates that a value of a particular type could not be converted
  // to a value of type string.
  { ER_CANT_CONVERT_TO_STRING,
       "XPath式''{0}''のXPathResultは、文字列に変換できない{1}のXPathResultTypeを持ちます。"},

  // Note to translators: Do not translate snapshotItem,
  // UNORDERED_NODE_SNAPSHOT_TYPE and ORDERED_NODE_SNAPSHOT_TYPE.
  { ER_NON_SNAPSHOT_TYPE,
       "XPathResultTypeが{1}のため、メソッドsnapshotItemはXPath式''{0}''のXPathResultで呼び出すことができません。このメソッドは、UNORDERED_NODE_SNAPSHOT_TYPEタイプおよびORDERED_NODE_SNAPSHOT_TYPEタイプにのみ適用されます。"},

  // Note to translators:  XPathEvaluator is a Java interface name.  An
  // XPathEvaluator is created with respect to a particular XML document, and in
  // this case the expression represented by this object was being evaluated with
  // respect to a context node from a different document.
  { ER_WRONG_DOCUMENT,
       "コンテキスト・ノードは、このXPathEvaluatorにバインドされたドキュメントに属しません。"},

  // Note to translators:  The XPath expression cannot be evaluated with respect
  // to this type of node.
  { ER_WRONG_NODETYPE,
       "コンテキスト・ノード・タイプはサポートされていません。"},

  { ER_XPATH_ERROR,
       "XPathに不明なエラーが発生しました。"},

  { ER_CANT_CONVERT_XPATHRESULTTYPE_TO_NUMBER,
        "XPath式''{0}''のXPathResultは、数値に変換できない{1}のXPathResultTypeを持ちます"},

  //BEGIN:  Definitions of error keys used  in exception messages of  JAXP 1.3 XPath API implementation

  /** Field ER_EXTENSION_FUNCTION_CANNOT_BE_INVOKED                       */

  { ER_EXTENSION_FUNCTION_CANNOT_BE_INVOKED,
       "拡張関数: XMLConstants.FEATURE_SECURE_PROCESSING機能がtrueに設定されると''{0}''を起動できません。"},

  /** Field ER_RESOLVE_VARIABLE_RETURNS_NULL                       */

  { ER_RESOLVE_VARIABLE_RETURNS_NULL,
       "変数{0}のresolveVariableがnullを返しています"},

  { ER_NO_XPATH_VARIABLE_RESOLVER,
       "変数{0}を解決しようとしていますが、変数リゾルバが設定されていません。"},

  { ER_NO_XPATH_FUNCTION_PROVIDER,
       "拡張関数{0}を呼び出そうとしていますが、拡張プロバイダが設定されていません。"},

  /** Field ER_UNSUPPORTED_RETURN_TYPE                       */

  { ER_UNSUPPORTED_RETURN_TYPE,
       "サポートされていない戻り型: {0}"},

  /** Field ER_SOURCE_RETURN_TYPE_CANNOT_BE_NULL                       */

  { ER_SOURCE_RETURN_TYPE_CANNOT_BE_NULL,
       "ソース・タイプまたは戻り型はnullにできません"},

  /** Field ER_ARG_CANNOT_BE_NULL                       */

  { ER_ARG_CANNOT_BE_NULL,
       "{0}引数はnullにできません"},

  /** Field ER_OBJECT_MODEL_NULL                       */

  { ER_OBJECT_MODEL_NULL,
       "{0}#isObjectModelSupported( String objectModel )はobjectModel == nullで呼び出せません"},

  /** Field ER_OBJECT_MODEL_EMPTY                       */

  { ER_OBJECT_MODEL_EMPTY,
       "{0}#isObjectModelSupported( String objectModel )はobjectModel == \"\"で呼び出せません"},

  /** Field ER_OBJECT_MODEL_EMPTY                       */

  { ER_FEATURE_NAME_NULL,
       "機能にnullの名前を設定しようとしました: {0}#setFeature( null, {1})"},

  /** Field ER_FEATURE_UNKNOWN                       */

  { ER_FEATURE_UNKNOWN,
       "不明な機能\"{0}\"を設定しようとしました: {1}#setFeature({0},{2})"},

  /** Field ER_GETTING_NULL_FEATURE                       */

  { ER_GETTING_NULL_FEATURE,
       "null名の機能を取得しようとしました: {0}#getFeature(null)"},

  /** Field ER_GETTING_NULL_FEATURE                       */

  { ER_GETTING_UNKNOWN_FEATURE,
       "不明な機能\"{0}\"を取得しようとしました: {1}#getFeature({0})"},

  {ER_SECUREPROCESSING_FEATURE,
        "FEATURE_SECURE_PROCESSING: セキュリティ・マネージャが存在するとき、機能をfalseに設定できません: {1}#setFeature({0},{2})"},

  /** Field ER_NULL_XPATH_FUNCTION_RESOLVER                       */

  { ER_NULL_XPATH_FUNCTION_RESOLVER,
       "nullのXPathFunctionResolverを設定しようとしました: {0}#setXPathFunctionResolver(null)"},

  /** Field ER_NULL_XPATH_VARIABLE_RESOLVER                       */

  { ER_NULL_XPATH_VARIABLE_RESOLVER,
       "nullのXPathVariableResolverを設定しようとしました: {0}#setXPathVariableResolver(null)"},

  /** Field ER_PROPERTY_NAME_NULL                       */

  { ER_PROPERTY_NAME_NULL,
       "プロパティにnullの名前を設定しようとしました: {0}#setProperty( null, {1})"},

  /** Field ER_PROPERTY_UNKNOWN                       */

  { ER_PROPERTY_UNKNOWN,
       "不明なプロパティ\"{0}\"を設定しようとしました:{1}#setProperty({0},{2})"},

  /** Field ER_GETTING_NULL_PROPERTY                       */

  { ER_GETTING_NULL_PROPERTY,
       "null名のプロパティを取得しようとしました: {0}#getProperty(null)"},

  /** Field ER_GETTING_NULL_PROPERTY                       */

  { ER_GETTING_UNKNOWN_PROPERTY,
       "不明なプロパティ\"{0}\"を取得しようとしました:{1}#getProperty({0})"},

  { ER_XPATH_GROUP_LIMIT,
      "JAXP0801001: コンパイラは、''{2}''で設定された''{1}''制限を超える''{0}''グループを含むXPath式を検出しました。"},

  { ER_XPATH_OPERATOR_LIMIT,
      "JAXP0801002: コンパイラは、''{2}''で設定された''{1}''制限を超える''{0}''演算子を含むXPath式を検出しました。"},

  //END:  Definitions of error keys used  in exception messages of  JAXP 1.3 XPath API implementation

  // Warnings...

  { WG_LOCALE_NAME_NOT_HANDLED,
      "format-number関数のロケール名が未処理です。"},

  { WG_PROPERTY_NOT_SUPPORTED,
      "XSLプロパティはサポートされていません: {0}"},

  { WG_DONT_DO_ANYTHING_WITH_NS,
      "プロパティ{1}内のネームスペース{0}では現在何も実行しないでください"},

  { WG_QUO_NO_LONGER_DEFINED,
      "古い構文: quo(...)はXPathでは現在定義されていません。"},

  { WG_NEED_DERIVED_OBJECT_TO_IMPLEMENT_NODETEST,
      "XPathにはnodeTestを実装するための導出オブジェクトが必要です。"},

  { WG_FUNCTION_TOKEN_NOT_FOUND,
      "関数トークンが見つかりません。"},

  { WG_COULDNOT_FIND_FUNCTION,
      "関数{0}が見つかりませんでした"},

  { WG_CANNOT_MAKE_URL_FROM,
      "{0}からURLを作成できません"},

  { WG_EXPAND_ENTITIES_NOT_SUPPORTED,
      "-EオプションはDTMパーサーではサポートされていません"},

  { WG_ILLEGAL_VARIABLE_REFERENCE,
      "変数に指定したVariableReferenceがコンテキスト範囲外か定義がありません。名前= {0}"},

  { WG_UNSUPPORTED_ENCODING,
     "サポートされていないエンコーディングです: {0}"},



  // Other miscellaneous text used inside the code...
  { "ui_language", "ja"},
  { "help_language", "ja"},
  { "language", "ja"},
  { "BAD_CODE", "createMessageのパラメータが範囲外です"},
  { "FORMAT_FAILED", "messageFormatの呼出し中に例外がスローされました"},
  { "version", ">>>>>>> Xalanバージョン "},
  { "version2", "<<<<<<<"},
  { "yes", "yes"},
  { "line", "行番号"},
  { "column", "列番号"},
  { "xsldone", "XSLProcessor: 完了しました"},
  { "xpath_option", "xpathオプション: "},
  { "optionIN", "   [-in inputXMLURL]"},
  { "optionSelect", "   [-select xpath expression]"},
  { "optionMatch", "   [-match match pattern (一致診断用)]"},
  { "optionAnyExpr", "または、xpath式が診断ダンプを実行します"},
  { "noParsermsg1", "XSLプロセスは成功しませんでした。"},
  { "noParsermsg2", "** パーサーが見つかりませんでした **"},
  { "noParsermsg3", "クラスパスを確認してください。"},
  { "noParsermsg4", "IBMのJava用XMLパーサーがない場合、次のサイトからダウンロードできます"},
  { "noParsermsg5", "IBMのAlphaWorks: http://www.alphaworks.ibm.com/formula/xml"},
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
