/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
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

package com.sun.org.apache.xml.internal.res;


import java.util.ListResourceBundle;

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
public class XMLErrorResources_ja extends ListResourceBundle
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
  public static final String ER_NAME_CANT_START_WITH_COLON = "ER_NAME_CANT_START_WITH_COLON";

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
  public static final String ER_ILLEGAL_CHARACTER = "ER_ILLEGAL_CHARACTER";

  /*
   * Now fill in the message text.
   * Then fill in the message text for that message code in the
   * array. Use the new error code as the index into the array.
   */

  // Error messages...

  /** The lookup table for error messages.   */
  private static final Object[][] contents = {

  /** Error message ID that has a null message, but takes in a single object.    */
    {"ER0000" , "{0}" },

    { ER_FUNCTION_NOT_SUPPORTED,
      "関数がサポートされていません。"},

    { ER_CANNOT_OVERWRITE_CAUSE,
      "原因を上書きできません"},

    { ER_NO_DEFAULT_IMPL,
      "デフォルト実装が見つかりません "},

    { ER_CHUNKEDINTARRAY_NOT_SUPPORTED,
      "ChunkedIntArray({0})は現在サポートされていません"},

    { ER_OFFSET_BIGGER_THAN_SLOT,
      "オフセットがスロットよりも大きいです"},

    { ER_COROUTINE_NOT_AVAIL,
      "コルーチンを使用できません。id={0}"},

    { ER_COROUTINE_CO_EXIT,
      "CoroutineManagerがco_exit()リクエストを受け取りました"},

    { ER_COJOINROUTINESET_FAILED,
      "co_joinCoroutineSet()が失敗しました"},

    { ER_COROUTINE_PARAM,
      "コルーチン・パラメータのエラー({0})"},

    { ER_PARSER_DOTERMINATE_ANSWERS,
      "\n不明: パーサーdoTerminateの応答は{0}です"},

    { ER_NO_PARSE_CALL_WHILE_PARSING,
      "解析は構文解析中に呼び出すことができません"},

    { ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "エラー: 軸{0}の型指定されたイテレータが実装されていません"},

    { ER_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "エラー: 軸{0}のイテレータが実装されていません "},

    { ER_ITERATOR_CLONE_NOT_SUPPORTED,
      "イテレータのクローンはサポートされていません"},

    { ER_UNKNOWN_AXIS_TYPE,
      "不明な軸トラバース・タイプです: {0}"},

    { ER_AXIS_NOT_SUPPORTED,
      "軸トラバーサ機能はサポートされていません: {0}"},

    { ER_NO_DTMIDS_AVAIL,
      "DTM IDはこれ以上使用できません"},

    { ER_NOT_SUPPORTED,
      "サポートされていません: {0}"},

    { ER_NODE_NON_NULL,
      "ノードはgetDTMHandleFromNodeについて非nullである必要があります"},

    { ER_COULD_NOT_RESOLVE_NODE,
      "ノードをハンドルに解決できませんでした"},

    { ER_STARTPARSE_WHILE_PARSING,
       "startParseは構文解析中に呼び出すことはできません"},

    { ER_STARTPARSE_NEEDS_SAXPARSER,
       "startParseには非nullのSAXParserが必要です"},

    { ER_COULD_NOT_INIT_PARSER,
       "次の理由でパーサーを初期化できませんでした: "},

    { ER_EXCEPTION_CREATING_POOL,
       "プール用の新規インスタンスの作成中に発生した例外"},

    { ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
       "パスに無効なエスケープ・シーケンスが含まれています"},

    { ER_SCHEME_REQUIRED,
       "スキームが必要です。"},

    { ER_NO_SCHEME_IN_URI,
       "スキームがURIに見つかりません: {0}"},

    { ER_NO_SCHEME_INURI,
       "スキームがURIに見つかりません"},

    { ER_PATH_INVALID_CHAR,
       "パスに無効な文字が含まれています: {0}"},

    { ER_SCHEME_FROM_NULL_STRING,
       "null文字列からはスキームを設定できません"},

    { ER_SCHEME_NOT_CONFORMANT,
       "スキームが整合していません。"},

    { ER_HOST_ADDRESS_NOT_WELLFORMED,
       "ホストは整形式のアドレスではありません"},

    { ER_PORT_WHEN_HOST_NULL,
       "ホストがnullの場合はポートを設定できません"},

    { ER_INVALID_PORT,
       "無効なポート番号"},

    { ER_FRAG_FOR_GENERIC_URI,
       "汎用URIのフラグメントのみ設定できます"},

    { ER_FRAG_WHEN_PATH_NULL,
       "パスがnullの場合はフラグメントを設定できません"},

    { ER_FRAG_INVALID_CHAR,
       "フラグメントに無効文字が含まれています"},

    { ER_PARSER_IN_USE,
      "パーサーはすでに使用中です"},

    { ER_CANNOT_CHANGE_WHILE_PARSING,
      "解析中に{0} {1}を変更できません"},

    { ER_SELF_CAUSATION_NOT_PERMITTED,
      "自己原因は許可されません"},

    { ER_NO_USERINFO_IF_NO_HOST,
      "ホストが指定されていない場合はUserinfoを指定できません"},

    { ER_NO_PORT_IF_NO_HOST,
      "ホストが指定されていない場合はポートを指定できません"},

    { ER_NO_QUERY_STRING_IN_PATH,
      "問合せ文字列はパスおよび問合せ文字列内に指定できません"},

    { ER_NO_FRAGMENT_STRING_IN_PATH,
      "フラグメントはパスとフラグメントの両方に指定できません"},

    { ER_CANNOT_INIT_URI_EMPTY_PARMS,
      "URIは空のパラメータを使用して初期化できません"},

    { ER_METHOD_NOT_SUPPORTED,
      "メソッドはまだサポートされていません "},

    { ER_INCRSAXSRCFILTER_NOT_RESTARTABLE,
      "IncrementalSAXSource_Filterは現在は再起動可能ではありません"},

    { ER_XMLRDR_NOT_BEFORE_STARTPARSE,
      "XMLReaderはstartParseリクエストより前にできません"},

    { ER_AXIS_TRAVERSER_NOT_SUPPORTED,
      "軸トラバーサ機能はサポートされていません: {0}"},

    { ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER,
      "null PrintWriterによってListingErrorHandlerが作成されました。"},

    { ER_SYSTEMID_UNKNOWN,
      "不明なSystemId"},

    { ER_LOCATION_UNKNOWN,
      "エラーの場所が不明です"},

    { ER_PREFIX_MUST_RESOLVE,
      "接頭辞はネームスペースに解決される必要があります: {0}"},

    { ER_CREATEDOCUMENT_NOT_SUPPORTED,
      "createDocument()はXPathContextでサポートされていません。"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT,
      "属性の子に所有者ドキュメントがありません。"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT_ELEMENT,
      "属性の子に所有者ドキュメント要素がありません。"},

    { ER_CANT_OUTPUT_TEXT_BEFORE_DOC,
      "警告: ドキュメント要素の前にテキストを出力できません。  無視します..."},

    { ER_CANT_HAVE_MORE_THAN_ONE_ROOT,
      "DOMに複数のルートを持つことはできません。"},

    { ER_ARG_LOCALNAME_NULL,
       "引数'localName'はnullです"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The localname is the portion after the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_LOCALNAME_INVALID,
       "QNAMEのLocalnameは有効なNCNameである必要があります"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The prefix is the portion before the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_PREFIX_INVALID,
       "QNAMEの接頭辞は有効なNCNameである必要があります"},

    { ER_NAME_CANT_START_WITH_COLON,
      "名前の先頭をコロンにすることはできません"},

    { "BAD_CODE", "createMessageのパラメータが範囲外です"},
    { "FORMAT_FAILED", "messageFormatの呼出し中に例外がスローされました"},
    { "line", "行番号"},
    { "column","列番号"},

    {ER_SERIALIZER_NOT_CONTENTHANDLER,
      "シリアライザ・クラス''{0}''はorg.xml.sax.ContentHandlerを実装しません。"},

    {ER_RESOURCE_COULD_NOT_FIND,
      "リソース[ {0} ]は見つかりませんでした。\n {1}" },

    {ER_RESOURCE_COULD_NOT_LOAD,
      "リソース[ {0} ]をロードできませんでした: {1} \n {2} \t {3}" },

    {ER_BUFFER_SIZE_LESSTHAN_ZERO,
      "バッファ・サイズ<=0" },

    {ER_INVALID_UTF16_SURROGATE,
      "無効なUTF-16サロゲートが検出されました: {0}。" },

    {ER_OIERROR,
      "IOエラー" },

    {ER_ILLEGAL_ATTRIBUTE_POSITION,
      "子ノードの後または要素が生成される前に属性{0}を追加できません。属性は無視されます。"},

      /*
       * Note to translators:  The stylesheet contained a reference to a
       * namespace prefix that was undefined.  The value of the substitution
       * text is the name of the prefix.
       */
    {ER_NAMESPACE_PREFIX,
      "接頭辞''{0}''のネームスペースが宣言されていません。" },
      /*
       * Note to translators:  This message is reported if the stylesheet
       * being processed attempted to construct an XML document with an
       * attribute in a place other than on an element.  The substitution text
       * specifies the name of the attribute.
       */
    {ER_STRAY_ATTRIBUTE,
      "属性''{0}''が要素の外側にあります。" },

      /*
       * Note to translators:  As with the preceding message, a namespace
       * declaration has the form of an attribute and is only permitted to
       * appear on an element.  The substitution text {0} is the namespace
       * prefix and {1} is the URI that was being used in the erroneous
       * namespace declaration.
       */
    {ER_STRAY_NAMESPACE,
      "ネームスペース宣言''{0}''=''{1}''が要素の外側にあります。" },

    {ER_COULD_NOT_LOAD_RESOURCE,
      "''{0}''をロードできませんでした(CLASSPATHを確認してください)。現在は単にデフォルトを使用しています"},

    { ER_ILLEGAL_CHARACTER,
       "{1}の指定された出力エンコーディングで示されない整数値{0}の文字を出力しようとしました。"},

    {ER_COULD_NOT_LOAD_METHOD_PROPERTY,
      "出力メソッド''{1}''のプロパティ・ファイル''{0}''をロードできませんでした(CLASSPATHを確認してください)" }


  };

  /**
   * Get the association list.
   *
   * @return The association list.
   */

    protected Object[][] getContents() {
        return contents;
    }

}
