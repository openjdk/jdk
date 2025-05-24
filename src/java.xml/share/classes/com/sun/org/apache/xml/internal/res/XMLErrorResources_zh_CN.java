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
      "不支持该函数!"},

    { ER_CANNOT_OVERWRITE_CAUSE,
      "无法覆盖原因"},

    { ER_NO_DEFAULT_IMPL,
      "找不到默认实现 "},

    { ER_CHUNKEDINTARRAY_NOT_SUPPORTED,
      "当前不支持 ChunkedIntArray({0})"},

    { ER_OFFSET_BIGGER_THAN_SLOT,
      "偏移量大于插槽"},

    { ER_COROUTINE_NOT_AVAIL,
      "Coroutine 不可用, id={0}"},

    { ER_COROUTINE_CO_EXIT,
      "CoroutineManager 收到 co_exit() 请求"},

    { ER_COJOINROUTINESET_FAILED,
      "co_joinCoroutineSet() 失败"},

    { ER_COROUTINE_PARAM,
      "Coroutine 参数错误 ({0})"},

    { ER_PARSER_DOTERMINATE_ANSWERS,
      "\n意外: 解析器对答复{0}执行 doTerminate"},

    { ER_NO_PARSE_CALL_WHILE_PARSING,
      "无法在解析时调用 parse"},

    { ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "错误: 未实现轴{0}的类型化迭代器"},

    { ER_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "错误: 未实现轴{0}的迭代器 "},

    { ER_ITERATOR_CLONE_NOT_SUPPORTED,
      "不支持克隆迭代器"},

    { ER_UNKNOWN_AXIS_TYPE,
      "轴遍历类型未知: {0}"},

    { ER_AXIS_NOT_SUPPORTED,
      "不支持轴遍历程序: {0}"},

    { ER_NO_DTMIDS_AVAIL,
      "无法使用更多 DTM ID"},

    { ER_NOT_SUPPORTED,
      "不支持: {0}"},

    { ER_NODE_NON_NULL,
      "getDTMHandleFromNode 的节点必须为非空值"},

    { ER_COULD_NOT_RESOLVE_NODE,
      "无法将节点解析为句柄"},

    { ER_STARTPARSE_WHILE_PARSING,
       "无法在解析时调用 startParse"},

    { ER_STARTPARSE_NEEDS_SAXPARSER,
       "startParse 需要非空 SAXParser"},

    { ER_COULD_NOT_INIT_PARSER,
       "无法使用以下对象初始化解析器"},

    { ER_EXCEPTION_CREATING_POOL,
       "为池创建新实例时出现异常错误"},

    { ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
       "路径包含无效的逃逸 序列"},

    { ER_SCHEME_REQUIRED,
       "方案是必需的!"},

    { ER_NO_SCHEME_IN_URI,
       "在 URI 中找不到方案: {0}"},

    { ER_NO_SCHEME_INURI,
       "在 URI 中找不到方案"},

    { ER_PATH_INVALID_CHAR,
       "路径包含无效的字符: {0}"},

    { ER_SCHEME_FROM_NULL_STRING,
       "无法从空字符串设置方案"},

    { ER_SCHEME_NOT_CONFORMANT,
       "方案不一致。"},

    { ER_HOST_ADDRESS_NOT_WELLFORMED,
       "主机不是格式良好的地址"},

    { ER_PORT_WHEN_HOST_NULL,
       "主机为空时, 无法设置端口"},

    { ER_INVALID_PORT,
       "无效的端口号"},

    { ER_FRAG_FOR_GENERIC_URI,
       "只能为一般 URI 设置片段"},

    { ER_FRAG_WHEN_PATH_NULL,
       "路径为空时, 无法设置片段"},

    { ER_FRAG_INVALID_CHAR,
       "片段包含无效的字符"},

    { ER_PARSER_IN_USE,
      "解析器已在使用"},

    { ER_CANNOT_CHANGE_WHILE_PARSING,
      "无法在解析时更改{0} {1}"},

    { ER_SELF_CAUSATION_NOT_PERMITTED,
      "不允许使用自因"},

    { ER_NO_USERINFO_IF_NO_HOST,
      "如果没有指定主机, 则不可以指定 Userinfo"},

    { ER_NO_PORT_IF_NO_HOST,
      "如果没有指定主机, 则不可以指定端口"},

    { ER_NO_QUERY_STRING_IN_PATH,
      "路径和查询字符串中不能指定查询字符串"},

    { ER_NO_FRAGMENT_STRING_IN_PATH,
      "路径和片段中都无法指定片段"},

    { ER_CANNOT_INIT_URI_EMPTY_PARMS,
      "无法以空参数初始化 URI"},

    { ER_METHOD_NOT_SUPPORTED,
      "尚不支持该方法 "},

    { ER_INCRSAXSRCFILTER_NOT_RESTARTABLE,
      "当前无法重新启动 IncrementalSAXSource_Filter"},

    { ER_XMLRDR_NOT_BEFORE_STARTPARSE,
      "XMLReader 不在 startParse 请求之前"},

    { ER_AXIS_TRAVERSER_NOT_SUPPORTED,
      "不支持轴遍历程序: {0}"},

    { ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER,
      "使用空 PrintWriter 创建了 ListingErrorHandler!"},

    { ER_SYSTEMID_UNKNOWN,
      "SystemId 未知"},

    { ER_LOCATION_UNKNOWN,
      "错误所在的位置未知"},

    { ER_PREFIX_MUST_RESOLVE,
      "前缀必须解析为名称空间: {0}"},

    { ER_CREATEDOCUMENT_NOT_SUPPORTED,
      "XPathContext 中不支持 createDocument()!"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT,
      "属性子级没有所有者文档!"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT_ELEMENT,
      "属性子级没有所有者文档元素!"},

    { ER_CANT_OUTPUT_TEXT_BEFORE_DOC,
      "警告: 无法输出文档元素之前的文本! 将忽略..."},

    { ER_CANT_HAVE_MORE_THAN_ONE_ROOT,
      "DOM 上不能有多个根!"},

    { ER_ARG_LOCALNAME_NULL,
       "参数 'localName' 为空值"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The localname is the portion after the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_LOCALNAME_INVALID,
       "QNAME 中的本地名称应为有效 NCName"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The prefix is the portion before the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_PREFIX_INVALID,
       "QNAME 中的前缀应为有效 NCName"},

    { ER_NAME_CANT_START_WITH_COLON,
      "名称不能以冒号开头"},

    { "BAD_CODE", "createMessage 的参数超出范围"},
    { "FORMAT_FAILED", "调用 messageFormat 时抛出异常错误"},
    { "line", "行号"},
    { "column","列号"},

    {ER_SERIALIZER_NOT_CONTENTHANDLER,
      "串行器类 ''{0}'' 不实现 org.xml.sax.ContentHandler。"},

    {ER_RESOURCE_COULD_NOT_FIND,
      "找不到资源 [ {0} ]。\n {1}" },

    {ER_RESOURCE_COULD_NOT_LOAD,
      "资源 [ {0} ] 无法加载: {1} \n {2} \t {3}" },

    {ER_BUFFER_SIZE_LESSTHAN_ZERO,
      "缓冲区大小 <=0" },

    {ER_INVALID_UTF16_SURROGATE,
      "检测到无效的 UTF-16 代理: {0}?" },

    {ER_OIERROR,
      "IO 错误" },

    {ER_ILLEGAL_ATTRIBUTE_POSITION,
      "在生成子节点之后或在生成元素之前无法添加属性 {0}。将忽略属性。"},

      /*
       * Note to translators:  The stylesheet contained a reference to a
       * namespace prefix that was undefined.  The value of the substitution
       * text is the name of the prefix.
       */
    {ER_NAMESPACE_PREFIX,
      "没有说明名称空间前缀 ''{0}''。" },
      /*
       * Note to translators:  This message is reported if the stylesheet
       * being processed attempted to construct an XML document with an
       * attribute in a place other than on an element.  The substitution text
       * specifies the name of the attribute.
       */
    {ER_STRAY_ATTRIBUTE,
      "属性 ''{0}'' 在元素外部。" },

      /*
       * Note to translators:  As with the preceding message, a namespace
       * declaration has the form of an attribute and is only permitted to
       * appear on an element.  The substitution text {0} is the namespace
       * prefix and {1} is the URI that was being used in the erroneous
       * namespace declaration.
       */
    {ER_STRAY_NAMESPACE,
      "名称空间声明 ''{0}''=''{1}'' 在元素外部。" },

    {ER_COULD_NOT_LOAD_RESOURCE,
      "无法加载 ''{0}'' (检查 CLASSPATH), 现在只使用默认值"},

    { ER_ILLEGAL_CHARACTER,
       "尝试输出未以{1}的指定输出编码表示的整数值 {0} 的字符。"},

    {ER_COULD_NOT_LOAD_METHOD_PROPERTY,
      "无法为输出方法 ''{1}'' 加载属性文件 ''{0}'' (检查 CLASSPATH)" }


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
