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
      "不支援函數！"},

    { ER_CANNOT_OVERWRITE_CAUSE,
      "無法覆寫原因"},

    { ER_NO_DEFAULT_IMPL,
      "找不到預設的實行"},

    { ER_CHUNKEDINTARRAY_NOT_SUPPORTED,
      "目前不支援 ChunkedIntArray({0})"},

    { ER_OFFSET_BIGGER_THAN_SLOT,
      "位移大於位置"},

    { ER_COROUTINE_NOT_AVAIL,
      "沒有可用的共同常式，id={0}"},

    { ER_COROUTINE_CO_EXIT,
      "CoroutineManager 收到 co_exit() 要求"},

    { ER_COJOINROUTINESET_FAILED,
      "co_joinCoroutineSet() 失敗"},

    { ER_COROUTINE_PARAM,
      "共同常式參數錯誤 ({0})"},

    { ER_PARSER_DOTERMINATE_ANSWERS,
      "\n未預期: 剖析器 doTerminate 答覆 {0}"},

    { ER_NO_PARSE_CALL_WHILE_PARSING,
      "剖析時可能未呼叫 parse"},

    { ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "錯誤: 未實行軸 {0} 的類型重複程式"},

    { ER_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "錯誤: 未實行軸 {0} 的重複程式"},

    { ER_ITERATOR_CLONE_NOT_SUPPORTED,
      "不支援重複程式複製"},

    { ER_UNKNOWN_AXIS_TYPE,
      "不明的軸周遊類型: {0}"},

    { ER_AXIS_NOT_SUPPORTED,
      "不支援軸周遊程式: {0}"},

    { ER_NO_DTMIDS_AVAIL,
      "不再有可用的 DTM ID"},

    { ER_NOT_SUPPORTED,
      "不支援: {0}"},

    { ER_NODE_NON_NULL,
      "節點必須是非空值的 getDTMHandleFromNode"},

    { ER_COULD_NOT_RESOLVE_NODE,
      "無法解析節點為控制代碼"},

    { ER_STARTPARSE_WHILE_PARSING,
       "剖析時可能未呼叫 startParse"},

    { ER_STARTPARSE_NEEDS_SAXPARSER,
       "startParse 需要非空值 SAXParser"},

    { ER_COULD_NOT_INIT_PARSER,
       "無法起始剖析器"},

    { ER_EXCEPTION_CREATING_POOL,
       "建立集區的新執行處理時發生異常狀況"},

    { ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
       "路徑包含無效的遁離序列"},

    { ER_SCHEME_REQUIRED,
       "配置是必要項目！"},

    { ER_NO_SCHEME_IN_URI,
       "在 URI 中找不到配置: {0}"},

    { ER_NO_SCHEME_INURI,
       "在 URI 找不到配置"},

    { ER_PATH_INVALID_CHAR,
       "路徑包含無效的字元: {0}"},

    { ER_SCHEME_FROM_NULL_STRING,
       "無法從空值字串設定配置"},

    { ER_SCHEME_NOT_CONFORMANT,
       "配置不一致。"},

    { ER_HOST_ADDRESS_NOT_WELLFORMED,
       "主機沒有完整的位址"},

    { ER_PORT_WHEN_HOST_NULL,
       "主機為空值時，無法設定連接埠"},

    { ER_INVALID_PORT,
       "無效的連接埠號碼"},

    { ER_FRAG_FOR_GENERIC_URI,
       "只能對一般 URI 設定片段"},

    { ER_FRAG_WHEN_PATH_NULL,
       "路徑為空值時，無法設定片段"},

    { ER_FRAG_INVALID_CHAR,
       "片段包含無效的字元"},

    { ER_PARSER_IN_USE,
      "剖析器使用中"},

    { ER_CANNOT_CHANGE_WHILE_PARSING,
      "剖析時無法變更 {0} {1}"},

    { ER_SELF_CAUSATION_NOT_PERMITTED,
      "不允許自行引發"},

    { ER_NO_USERINFO_IF_NO_HOST,
      "如果沒有指定主機，不可指定 Userinfo"},

    { ER_NO_PORT_IF_NO_HOST,
      "如果沒有指定主機，不可指定連接埠"},

    { ER_NO_QUERY_STRING_IN_PATH,
      "在路徑及查詢字串中不可指定查詢字串"},

    { ER_NO_FRAGMENT_STRING_IN_PATH,
      "路徑和片段不能同時指定片段"},

    { ER_CANNOT_INIT_URI_EMPTY_PARMS,
      "無法以空白參數起始設定 URI"},

    { ER_METHOD_NOT_SUPPORTED,
      "尚不支援方法"},

    { ER_INCRSAXSRCFILTER_NOT_RESTARTABLE,
      "IncrementalSAXSource_Filter 目前無法重新啟動"},

    { ER_XMLRDR_NOT_BEFORE_STARTPARSE,
      "XMLReader 不能在 startParse 要求之前"},

    { ER_AXIS_TRAVERSER_NOT_SUPPORTED,
      "不支援軸周遊程式: {0}"},

    { ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER,
      "使用空值 PrintWriter 建立 ListingErrorHandler！"},

    { ER_SYSTEMID_UNKNOWN,
      "不明的 SystemId"},

    { ER_LOCATION_UNKNOWN,
      "不明的錯誤位置"},

    { ER_PREFIX_MUST_RESOLVE,
      "前置碼必須解析為命名空間: {0}"},

    { ER_CREATEDOCUMENT_NOT_SUPPORTED,
      "XPathContext 中不支援 createDocument()！"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT,
      "屬性子項不具有擁有者文件！"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT_ELEMENT,
      "屬性子項不具有擁有者文件元素！"},

    { ER_CANT_OUTPUT_TEXT_BEFORE_DOC,
      "警告: 無法在文件元素之前輸出文字！正在忽略..."},

    { ER_CANT_HAVE_MORE_THAN_ONE_ROOT,
      "DOM 的根不能超過一個！"},

    { ER_ARG_LOCALNAME_NULL,
       "引數 'localName' 為空值"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The localname is the portion after the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_LOCALNAME_INVALID,
       "QNAME 中的 Localname 應為有效的 NCName"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The prefix is the portion before the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_PREFIX_INVALID,
       "QNAME 中的前置碼應為有效的 NCName"},

    { ER_NAME_CANT_START_WITH_COLON,
      "名稱不能以冒號為開頭"},

    { "BAD_CODE", "createMessage 的參數超出範圍"},
    { "FORMAT_FAILED", "messageFormat 呼叫期間發生異常狀況"},
    { "line", "行號"},
    { "column","資料欄編號"},

    {ER_SERIALIZER_NOT_CONTENTHANDLER,
      "serializer 類別 ''{0}'' 不實行 org.xml.sax.ContentHandler。"},

    {ER_RESOURCE_COULD_NOT_FIND,
      "找不到資源 [ {0} ]。\n{1}" },

    {ER_RESOURCE_COULD_NOT_LOAD,
      "無法載入資源 [ {0} ]: {1} \n {2} \t {3}" },

    {ER_BUFFER_SIZE_LESSTHAN_ZERO,
      "緩衝區大小 <=0" },

    {ER_INVALID_UTF16_SURROGATE,
      "偵測到無效的 UTF-16 代理: {0}？" },

    {ER_OIERROR,
      "IO 錯誤" },

    {ER_ILLEGAL_ATTRIBUTE_POSITION,
      "在產生子項節點之後，或在產生元素之前，不可新增屬性 {0}。屬性會被忽略。"},

      /*
       * Note to translators:  The stylesheet contained a reference to a
       * namespace prefix that was undefined.  The value of the substitution
       * text is the name of the prefix.
       */
    {ER_NAMESPACE_PREFIX,
      "字首 ''{0}'' 的命名空間尚未宣告。" },
      /*
       * Note to translators:  This message is reported if the stylesheet
       * being processed attempted to construct an XML document with an
       * attribute in a place other than on an element.  The substitution text
       * specifies the name of the attribute.
       */
    {ER_STRAY_ATTRIBUTE,
      "屬性 ''{0}'' 在元素之外。" },

      /*
       * Note to translators:  As with the preceding message, a namespace
       * declaration has the form of an attribute and is only permitted to
       * appear on an element.  The substitution text {0} is the namespace
       * prefix and {1} is the URI that was being used in the erroneous
       * namespace declaration.
       */
    {ER_STRAY_NAMESPACE,
      "命名空間宣告 ''{0}''=''{1}'' 超出元素外。" },

    {ER_COULD_NOT_LOAD_RESOURCE,
      "無法載入 ''{0}'' (檢查 CLASSPATH)，目前只使用預設值"},

    { ER_ILLEGAL_CHARACTER,
       "嘗試輸出整數值 {0} 的字元，但是它不是以指定的 {1} 輸出編碼呈現。"},

    {ER_COULD_NOT_LOAD_METHOD_PROPERTY,
      "無法載入輸出方法 ''{1}'' 的屬性檔 ''{0}'' (檢查 CLASSPATH)" }


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
