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
public class XPATHErrorResources_ko extends ListResourceBundle
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

  { ER_CURRENT_NOT_ALLOWED_IN_MATCH, "일치 패턴에서는 current() 함수가 허용되지 않습니다!" },

  { ER_CURRENT_TAKES_NO_ARGS, "current() 함수에는 인수를 사용할 수 없습니다!" },

  { ER_DOCUMENT_REPLACED,
      "document() 함수 구현이 com.sun.org.apache.xalan.internal.xslt.FuncDocument로 대체되었습니다!"},

  { ER_CONTEXT_CAN_NOT_BE_NULL,
      "작업이 컨텍스트에 종속적일 때 컨텍스트는 널일 수 없습니다."},

  { ER_CONTEXT_HAS_NO_OWNERDOC,
      "컨텍스트에 소유자 문서가 없습니다!"},

  { ER_LOCALNAME_HAS_TOO_MANY_ARGS,
      "local-name()에 인수가 너무 많습니다."},

  { ER_NAMESPACEURI_HAS_TOO_MANY_ARGS,
      "namespace-uri()에 인수가 너무 많습니다."},

  { ER_NORMALIZESPACE_HAS_TOO_MANY_ARGS,
      "normalize-space()에 인수가 너무 많습니다."},

  { ER_NUMBER_HAS_TOO_MANY_ARGS,
      "number()에 인수가 너무 많습니다."},

  { ER_NAME_HAS_TOO_MANY_ARGS,
     "name()에 인수가 너무 많습니다."},

  { ER_STRING_HAS_TOO_MANY_ARGS,
      "string()에 인수가 너무 많습니다."},

  { ER_STRINGLENGTH_HAS_TOO_MANY_ARGS,
      "string-length()에 인수가 너무 많습니다."},

  { ER_TRANSLATE_TAKES_3_ARGS,
      "translate() 함수에 세 개의 인수가 사용됩니다!"},

  { ER_UNPARSEDENTITYURI_TAKES_1_ARG,
      "unparsed-entity-uri 함수에는 한 개의 인수가 사용되어야 합니다!"},

  { ER_NAMESPACEAXIS_NOT_IMPLEMENTED,
      "네임스페이스 축이 아직 구현되지 않았습니다!"},

  { ER_UNKNOWN_AXIS,
     "알 수 없는 축: {0}"},

  { ER_UNKNOWN_MATCH_OPERATION,
     "알 수 없는 일치 작업입니다!"},

  { ER_INCORRECT_ARG_LENGTH,
      "processing-instruction() 노드 테스트의 인수 길이가 올바르지 않습니다!"},

  { ER_CANT_CONVERT_TO_NUMBER,
      "{0}을(를) 숫자로 변환할 수 없습니다."},

  { ER_CANT_CONVERT_TO_NODELIST,
      "{0}을(를) NodeList로 변환할 수 없습니다!"},

  { ER_CANT_CONVERT_TO_MUTABLENODELIST,
      "{0}을(를) NodeSetDTM으로 변환할 수 없습니다!"},

  { ER_CANT_CONVERT_TO_TYPE,
      "{0}을(를) type#{1}(으)로 변환할 수 없습니다."},

  { ER_EXPECTED_MATCH_PATTERN,
      "getMatchScore에 일치 패턴이 필요합니다!"},

  { ER_COULDNOT_GET_VAR_NAMED,
      "이름이 {0}인 변수를 가져올 수 없습니다."},

  { ER_UNKNOWN_OPCODE,
     "오류! 알 수 없는 작업 코드: {0}"},

  { ER_EXTRA_ILLEGAL_TOKENS,
     "잘못된 추가 토큰: {0}"},

  { ER_EXPECTED_DOUBLE_QUOTE,
      "리터럴의 따옴표가 잘못 지정되었습니다. 큰 따옴표가 필요합니다!"},

  { ER_EXPECTED_SINGLE_QUOTE,
      "리터럴의 따옴표가 잘못 지정되었습니다. 작은 따옴표가 필요합니다!"},

  { ER_EMPTY_EXPRESSION,
     "표현식이 비어 있습니다!"},

  { ER_EXPECTED_BUT_FOUND,
     "{0}이(가) 필요하지만 {1}이(가) 발견되었습니다."},

  { ER_INCORRECT_PROGRAMMER_ASSERTION,
      "프로그래머 검증이 올바르지 않음 - {0}"},

  { ER_BOOLEAN_ARG_NO_LONGER_OPTIONAL,
      "19990709 XPath 초안에서는 boolean(...) 인수가 더 이상 선택적 인수가 아닙니다."},

  { ER_FOUND_COMMA_BUT_NO_PRECEDING_ARG,
      "','를 찾았지만 선행 인수가 없습니다!"},

  { ER_FOUND_COMMA_BUT_NO_FOLLOWING_ARG,
      "','를 찾았지만 후행 인수가 없습니다!"},

  { ER_PREDICATE_ILLEGAL_SYNTAX,
      "'..[predicate]' 또는 '.[predicate]'는 잘못된 구문입니다. 대신 'self::node()[predicate]'를 사용하십시오."},

  { ER_ILLEGAL_AXIS_NAME,
     "잘못된 축 이름: {0}"},

  { ER_UNKNOWN_NODETYPE,
     "알 수 없는 nodetype: {0}"},

  { ER_PATTERN_LITERAL_NEEDS_BE_QUOTED,
      "패턴 리터럴({0})에 따옴표를 지정해야 합니다!"},

  { ER_COULDNOT_BE_FORMATTED_TO_NUMBER,
      "{0}의 형식을 숫자로 지정할 수 없습니다!"},

  { ER_COULDNOT_CREATE_XMLPROCESSORLIAISON,
      "XML TransformerFactory 연결을 생성할 수 없음: {0}"},

  { ER_DIDNOT_FIND_XPATH_SELECT_EXP,
      "오류: xpath select 표현식(-select)을 찾을 수 없습니다."},

  { ER_COULDNOT_FIND_ENDOP_AFTER_OPLOCATIONPATH,
      "오류! OP_LOCATIONPATH 뒤에서 ENDOP를 찾을 수 없습니다."},

  { ER_ERROR_OCCURED,
     "오류가 발생했습니다!"},

  { ER_ILLEGAL_VARIABLE_REFERENCE,
      "변수에 대해 제공된 VariableReference가 컨텍스트에서 벗어나거나 정의를 포함하지 없습니다! 이름 = {0}"},

  { ER_AXES_NOT_ALLOWED,
      "일치 패턴에서는 child:: 및 attribute:: 축만 허용됩니다! 잘못된 축 = {0}"},

  { ER_KEY_HAS_TOO_MANY_ARGS,
      "key()에 올바르지 않은 수의 인수가 있습니다."},

  { ER_COUNT_TAKES_1_ARG,
      "count 함수에는 한 개의 인수가 사용되어야 합니다!"},

  { ER_COULDNOT_FIND_FUNCTION,
     "함수를 찾을 수 없음: {0}"},

  { ER_UNSUPPORTED_ENCODING,
     "지원되지 않는 인코딩: {0}"},

  { ER_PROBLEM_IN_DTM_NEXTSIBLING,
      "DTM에서 getNextSibling에 문제가 발생했습니다. 복구하려고 시도하는 중"},

  { ER_CANNOT_WRITE_TO_EMPTYNODELISTIMPL,
      "프로그래머 오류: EmptyNodeList에 쓸 수 없습니다."},

  { ER_SETDOMFACTORY_NOT_SUPPORTED,
      "XPathContext에서는 setDOMFactory가 지원되지 않습니다!"},

  { ER_PREFIX_MUST_RESOLVE,
      "접두어는 네임스페이스로 분석되어야 함: {0}"},

  { ER_PARSE_NOT_SUPPORTED,
      "XPathContext에서는 parse(InputSource 소스)가 지원되지 않습니다! {0}을(를) 열 수 없습니다."},

  { ER_SAX_API_NOT_HANDLED,
      "DTM이 SAX API 문자(char ch[]...를 처리하지 않았습니다!"},

  { ER_IGNORABLE_WHITESPACE_NOT_HANDLED,
      "DTM이 ignorableWhitespace(char ch[]...를 처리하지 않았습니다!"},

  { ER_DTM_CANNOT_HANDLE_NODES,
      "DTMLiaison은 {0} 유형의 노드를 처리할 수 없습니다."},

  { ER_XERCES_CANNOT_HANDLE_NODES,
      "DOM2Helper는 {0} 유형의 노드를 처리할 수 없습니다."},

  { ER_XERCES_PARSE_ERROR_DETAILS,
      "DOM2Helper.parse 오류: SystemID - {0} 행 - {1}"},

  { ER_XERCES_PARSE_ERROR,
     "DOM2Helper.parse 오류"},

  { ER_INVALID_UTF16_SURROGATE,
      "부적합한 UTF-16 대리 요소가 감지됨: {0}"},

  { ER_OIERROR,
     "IO 오류"},

  { ER_CANNOT_CREATE_URL,
     "{0}에 대한 URL을 생성할 수 없습니다."},

  { ER_XPATH_READOBJECT,
     "XPath.readObject에 오류 발생: {0}"},

  { ER_FUNCTION_TOKEN_NOT_FOUND,
      "함수 토큰을 찾을 수 없습니다."},

  { ER_CANNOT_DEAL_XPATH_TYPE,
       "XPath 유형을 처리할 수 없음: {0}"},

  { ER_NODESET_NOT_MUTABLE,
       "이 NodeSet는 변경할 수 없습니다."},

  { ER_NODESETDTM_NOT_MUTABLE,
       "이 NodeSetDTM은 변경할 수 없습니다."},

  { ER_VAR_NOT_RESOLVABLE,
        "변수를 분석할 수 없음: {0}"},

  { ER_NULL_ERROR_HANDLER,
        "널 오류 처리기"},

  { ER_PROG_ASSERT_UNKNOWN_OPCODE,
       "프로그래머 검증: 알 수 없는 opcode: {0}"},

  { ER_ZERO_OR_ONE,
       "0 또는 1"},

  { ER_RTF_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
       "XRTreeFragSelectWrapper는 rtf()를 지원하지 않습니다."},

  { ER_ASNODEITERATOR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
       "XRTreeFragSelectWrapper는 asNodeIterator()를 지원하지 않습니다."},

        /**  detach() not supported by XRTreeFragSelectWrapper   */
   { ER_DETACH_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
                "XRTreeFragSelectWrapper는 detach()를 지원하지 않습니다."},

        /**  num() not supported by XRTreeFragSelectWrapper   */
   { ER_NUM_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
                "XRTreeFragSelectWrapper는 num()을 지원하지 않습니다."},

        /**  xstr() not supported by XRTreeFragSelectWrapper   */
   { ER_XSTR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
                "XRTreeFragSelectWrapper는 xstr()을 지원하지 않습니다."},

        /**  str() not supported by XRTreeFragSelectWrapper   */
   { ER_STR_NOT_SUPPORTED_XRTREEFRAGSELECTWRAPPER,
                "XRTreeFragSelectWrapper는 str()을 지원하지 않습니다."},

  { ER_FSB_NOT_SUPPORTED_XSTRINGFORCHARS,
       "fsb()는 XStringForChars에 대해 지원되지 않습니다."},

  { ER_COULD_NOT_FIND_VAR,
      "이름이 {0}인 변수를 찾을 수 없습니다."},

  { ER_XSTRINGFORCHARS_CANNOT_TAKE_STRING,
      "XStringForChars는 인수에 문자열을 사용할 수 없습니다."},

  { ER_FASTSTRINGBUFFER_CANNOT_BE_NULL,
      "FastStringBuffer 인수는 널일 수 없습니다."},

  { ER_TWO_OR_THREE,
       "2 또는 3"},

  { ER_VARIABLE_ACCESSED_BEFORE_BIND,
       "변수가 바인드되기 전에 변수에 액세스되었습니다!"},

  { ER_FSB_CANNOT_TAKE_STRING,
       "XStringForFSB는 인수에 문자열을 사용할 수 없습니다!"},

  { ER_SETTING_WALKER_ROOT_TO_NULL,
       "\n !!!! 오류! 워커의 루트를 null로 설정하는 중입니다!"},

  { ER_NODESETDTM_CANNOT_ITERATE,
       "이 NodeSetDTM은 이전 노드를 반복할 수 없습니다!"},

  { ER_NODESET_CANNOT_ITERATE,
       "이 NodeSet는 이전 노드를 반복할 수 없습니다!"},

  { ER_NODESETDTM_CANNOT_INDEX,
       "이 NodeSetDTM은 함수를 인덱스화하거나 집계할 수 없습니다!"},

  { ER_NODESET_CANNOT_INDEX,
       "이 NodeSet는 함수를 인덱스화하거나 집계할 수 없습니다!"},

  { ER_CANNOT_CALL_SETSHOULDCACHENODE,
       "nextNode가 호출된 후에는 setShouldCacheNodes를 호출할 수 없습니다!"},

  { ER_ONLY_ALLOWS,
       "{0}은(는) {1}개의 인수만 허용합니다."},

  { ER_UNKNOWN_STEP,
       "getNextStepPos에 프로그래머 검증이 있음: 알 수 없는 stepType: {0}"},

  //Note to translators:  A relative location path is a form of XPath expression.
  // The message indicates that such an expression was expected following the
  // characters '/' or '//', but was not found.
  { ER_EXPECTED_REL_LOC_PATH,
      "'/' 또는 '//' 토큰 뒤에 상대 위치 경로가 필요합니다."},

  // Note to translators:  A location path is a form of XPath expression.
  // The message indicates that syntactically such an expression was expected,but
  // the characters specified by the substitution text were encountered instead.
  { ER_EXPECTED_LOC_PATH,
       "위치 경로가 필요하지만 {0} 토큰이 발견되었습니다."},

  // Note to translators:  A location path is a form of XPath expression.
  // The message indicates that syntactically such a subexpression was expected,
  // but no more characters were found in the expression.
  { ER_EXPECTED_LOC_PATH_AT_END_EXPR,
       "위치 경로가 필요하지만 XPath 표현식 끝이 발견되었습니다."},

  // Note to translators:  A location step is part of an XPath expression.
  // The message indicates that syntactically such an expression was expected
  // following the specified characters.
  { ER_EXPECTED_LOC_STEP,
       "'/' 또는 '//' 토큰 뒤에 위치 단계가 필요합니다."},

  // Note to translators:  A node test is part of an XPath expression that is
  // used to test for particular kinds of nodes.  In this case, a node test that
  // consists of an NCName followed by a colon and an asterisk or that consists
  // of a QName was expected, but was not found.
  { ER_EXPECTED_NODE_TEST,
       "NCName:* 또는 QName과 일치하는 노드 테스트가 필요합니다."},

  // Note to translators:  A step pattern is part of an XPath expression.
  // The message indicates that syntactically such an expression was expected,
  // but the specified character was found in the expression instead.
  { ER_EXPECTED_STEP_PATTERN,
       "단계 패턴이 필요하지만 '/'가 발견되었습니다."},

  // Note to translators: A relative path pattern is part of an XPath expression.
  // The message indicates that syntactically such an expression was expected,
  // but was not found.
  { ER_EXPECTED_REL_PATH_PATTERN,
       "상대 경로 패턴이 필요합니다."},

  // Note to translators:  The substitution text is the name of a data type.  The
  // message indicates that a value of a particular type could not be converted
  // to a value of type boolean.
  { ER_CANT_CONVERT_TO_BOOLEAN,
       "XPath 표현식 ''{0}''에 대한 XPathResult의 XPathResultType이 부울로 변환될 수 없는 {1}입니다."},

  // Note to translators: Do not translate ANY_UNORDERED_NODE_TYPE and
  // FIRST_ORDERED_NODE_TYPE.
  { ER_CANT_CONVERT_TO_SINGLENODE,
       "XPath 표현식 ''{0}''에 대한 XPathResult의 XPathResultType이 단일 노드로 변환될 수 없는 {1}입니다. getSingleNodeValue 메소드는 ANY_UNORDERED_NODE_TYPE 및 FIRST_ORDERED_NODE_TYPE 유형에만 적용됩니다."},

  // Note to translators: Do not translate UNORDERED_NODE_SNAPSHOT_TYPE and
  // ORDERED_NODE_SNAPSHOT_TYPE.
  { ER_CANT_GET_SNAPSHOT_LENGTH,
       "XPathResultType이 {1}이므로 getSnapshotLength 메소드는 XPath 표현식 ''{0}''에 대한 XPathResult에서 호출될 수 없습니다. 이 메소드는 UNORDERED_NODE_SNAPSHOT_TYPE 및 ORDERED_NODE_SNAPSHOT_TYPE 유형에만 적용됩니다."},

  { ER_NON_ITERATOR_TYPE,
       "XPathResultType이 {1}이므로 iterateNext 메소드는 XPath 표현식 ''{0}''에 대한 XPathResult에서 호출될 수 없습니다. 이 메소드는 UNORDERED_NODE_ITERATOR_TYPE 및 ORDERED_NODE_ITERATOR_TYPE 유형에만 적용됩니다."},

  // Note to translators: This message indicates that the document being operated
  // upon changed, so the iterator object that was being used to traverse the
  // document has now become invalid.
  { ER_DOC_MUTATED,
       "결과가 반환된 후 문서가 변경되었습니다. 이터레이터가 부적합합니다."},

  { ER_INVALID_XPATH_TYPE,
       "부적합한 XPath 유형 인수: {0}"},

  { ER_EMPTY_XPATH_RESULT,
       "XPath 결과 객체가 비어 있습니다."},

  { ER_INCOMPATIBLE_TYPES,
       "XPath 표현식 ''{0}''에 대한 XPathResult의 XPathResultType이 지정된 XPathResultType {2}(으)로 강제 변환될 수 없는 {1}입니다."},

  { ER_NULL_RESOLVER,
       "널 접두어 분석기로 접두어를 분석할 수 없습니다."},

  // Note to translators:  The substitution text is the name of a data type.  The
  // message indicates that a value of a particular type could not be converted
  // to a value of type string.
  { ER_CANT_CONVERT_TO_STRING,
       "XPath 표현식 ''{0}''에 대한 XPathResult의 XPathResultType이 문자열로 변환될 수 없는 {1}입니다."},

  // Note to translators: Do not translate snapshotItem,
  // UNORDERED_NODE_SNAPSHOT_TYPE and ORDERED_NODE_SNAPSHOT_TYPE.
  { ER_NON_SNAPSHOT_TYPE,
       "XPathResultType이 {1}이므로 snapshotItem 메소드는 XPath 표현식 ''{0}''에 대한 XPathResult에서 호출될 수 없습니다. 이 메소드는 UNORDERED_NODE_SNAPSHOT_TYPE 및 ORDERED_NODE_SNAPSHOT_TYPE 유형에만 적용됩니다."},

  // Note to translators:  XPathEvaluator is a Java interface name.  An
  // XPathEvaluator is created with respect to a particular XML document, and in
  // this case the expression represented by this object was being evaluated with
  // respect to a context node from a different document.
  { ER_WRONG_DOCUMENT,
       "컨텍스트 노드가 이 XPathEvaluator에 바인드된 문서에 속하지 않습니다."},

  // Note to translators:  The XPath expression cannot be evaluated with respect
  // to this type of node.
  { ER_WRONG_NODETYPE,
       "컨텍스트 노드 유형은 지원되지 않습니다."},

  { ER_XPATH_ERROR,
       "XPath에 알 수 없는 오류가 발생했습니다."},

  { ER_CANT_CONVERT_XPATHRESULTTYPE_TO_NUMBER,
        "XPath 표현식 ''{0}''에 대한 XPathResult의 XPathResultType이 숫자로 변환될 수 없는 {1}입니다."},

  //BEGIN:  Definitions of error keys used  in exception messages of  JAXP 1.3 XPath API implementation

  /** Field ER_EXTENSION_FUNCTION_CANNOT_BE_INVOKED                       */

  { ER_EXTENSION_FUNCTION_CANNOT_BE_INVOKED,
       "XMLConstants.FEATURE_SECURE_PROCESSING 기능이 true로 설정된 경우 확장 함수 ''{0}''을(를) 호출할 수 없습니다."},

  /** Field ER_RESOLVE_VARIABLE_RETURNS_NULL                       */

  { ER_RESOLVE_VARIABLE_RETURNS_NULL,
       "{0} 변수에 대한 resolveVariable이 널을 반환합니다."},

  /** Field ER_UNSUPPORTED_RETURN_TYPE                       */

  { ER_UNSUPPORTED_RETURN_TYPE,
       "지원되지 않는 반환 유형: {0}"},

  /** Field ER_SOURCE_RETURN_TYPE_CANNOT_BE_NULL                       */

  { ER_SOURCE_RETURN_TYPE_CANNOT_BE_NULL,
       "소스 및/또는 반환 유형은 널일 수 없습니다."},

  /** Field ER_SOURCE_RETURN_TYPE_CANNOT_BE_NULL                       */

  { ER_SOURCE_RETURN_TYPE_CANNOT_BE_NULL,
       "소스 및/또는 반환 유형은 널일 수 없습니다."},

  /** Field ER_ARG_CANNOT_BE_NULL                       */

  { ER_ARG_CANNOT_BE_NULL,
       "{0} 인수는 널일 수 없습니다."},

  /** Field ER_OBJECT_MODEL_NULL                       */

  { ER_OBJECT_MODEL_NULL,
       "objectModel == null로 {0}#isObjectModelSupported(String objectModel)를 호출할 수 없습니다."},

  /** Field ER_OBJECT_MODEL_EMPTY                       */

  { ER_OBJECT_MODEL_EMPTY,
       "objectModel == \"\"로 {0}#isObjectModelSupported(String objectModel)를 호출할 수 없습니다."},

  /** Field ER_OBJECT_MODEL_EMPTY                       */

  { ER_FEATURE_NAME_NULL,
       "널 이름으로 기능을 설정하려고 시도하는 중: {0}#setFeature(null, {1})"},

  /** Field ER_FEATURE_UNKNOWN                       */

  { ER_FEATURE_UNKNOWN,
       "알 수 없는 기능 \"{0}\"을(를) 설정하려고 시도하는 중: {1}#setFeature({0},{2})"},

  /** Field ER_GETTING_NULL_FEATURE                       */

  { ER_GETTING_NULL_FEATURE,
       "널 이름으로 기능을 가져오려고 시도하는 중: {0}#getFeature(null)"},

  /** Field ER_GETTING_NULL_FEATURE                       */

  { ER_GETTING_UNKNOWN_FEATURE,
       "알 수 없는 기능 \"{0}\"을(를) 가져오려고 시도하는 중: {1}#getFeature({0})"},

  {ER_SECUREPROCESSING_FEATURE,
        "FEATURE_SECURE_PROCESSING: 보안 관리자가 있을 경우 기능을 false로 설정할 수 없음: {1}#setFeature({0},{2})"},

  /** Field ER_NULL_XPATH_FUNCTION_RESOLVER                       */

  { ER_NULL_XPATH_FUNCTION_RESOLVER,
       "널 XPathFunctionResolver를 설정하려고 시도하는 중: {0}#setXPathFunctionResolver(null)"},

  /** Field ER_NULL_XPATH_VARIABLE_RESOLVER                       */

  { ER_NULL_XPATH_VARIABLE_RESOLVER,
       "널 XPathVariableResolver를 설정하려고 시도하는 중: {0}#setXPathVariableResolver(null)"},

  //END:  Definitions of error keys used  in exception messages of  JAXP 1.3 XPath API implementation

  // Warnings...

  { WG_LOCALE_NAME_NOT_HANDLED,
      "format-number 함수의 로케일 이름이 아직 처리되지 않았습니다!"},

  { WG_PROPERTY_NOT_SUPPORTED,
      "XSL 속성이 지원되지 않음: {0}"},

  { WG_DONT_DO_ANYTHING_WITH_NS,
      "속성의 {0} 네임스페이스에 대해 현재 어떤 작업도 수행하지 않아야 함: {1}"},

  { WG_QUO_NO_LONGER_DEFINED,
      "이전 구문: quo(...)가 XPath에 더 이상 정의되어 있지 않습니다."},

  { WG_NEED_DERIVED_OBJECT_TO_IMPLEMENT_NODETEST,
      "nodeTest를 구현하려면 XPath에 파생 객체가 필요합니다!"},

  { WG_FUNCTION_TOKEN_NOT_FOUND,
      "함수 토큰을 찾을 수 없습니다."},

  { WG_COULDNOT_FIND_FUNCTION,
      "함수를 찾을 수 없음: {0}"},

  { WG_CANNOT_MAKE_URL_FROM,
      "{0}에서 URL을 생성할 수 없습니다."},

  { WG_EXPAND_ENTITIES_NOT_SUPPORTED,
      "DTM 구문 분석기에 대해서는 -E 옵션이 지원되지 않습니다."},

  { WG_ILLEGAL_VARIABLE_REFERENCE,
      "변수에 대해 제공된 VariableReference가 컨텍스트에서 벗어나거나 정의를 포함하지 없습니다! 이름 = {0}"},

  { WG_UNSUPPORTED_ENCODING,
     "지원되지 않는 인코딩: {0}"},



  // Other miscellaneous text used inside the code...
  { "ui_language", "ko"},
  { "help_language", "ko"},
  { "language", "ko"},
  { "BAD_CODE", "createMessage에 대한 매개변수가 범위를 벗어났습니다."},
  { "FORMAT_FAILED", "messageFormat 호출 중 예외사항이 발생했습니다."},
  { "version", ">>>>>>> Xalan 버전 "},
  { "version2", "<<<<<<<"},
  { "yes", "예"},
  { "line", "행 번호"},
  { "column", "열 번호"},
  { "xsldone", "XSLProcessor: 완료"},
  { "xpath_option", "XPath 옵션: "},
  { "optionIN", "   [-in inputXMLURL]"},
  { "optionSelect", "   [-select XPath 표현식]"},
  { "optionMatch", "   [-match 일치 패턴(일치 진단의 경우)]"},
  { "optionAnyExpr", "또는 XPath 표현식이 진단 덤프를 수행합니다."},
  { "noParsermsg1", "XSL 프로세스를 실패했습니다."},
  { "noParsermsg2", "** 구문 분석기를 찾을 수 없음 **"},
  { "noParsermsg3", "클래스 경로를 확인하십시오."},
  { "noParsermsg4", "IBM의 Java용 XML 구문 분석기가 없을 경우 다음 위치에서 다운로드할 수 있습니다."},
  { "noParsermsg5", "IBM AlphaWorks: http://www.alphaworks.ibm.com/formula/xml"},
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
