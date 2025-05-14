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
      "함수가 지원되지 않습니다!"},

    { ER_CANNOT_OVERWRITE_CAUSE,
      "원인을 겹쳐 쓸 수 없습니다."},

    { ER_NO_DEFAULT_IMPL,
      "기본 구현을 찾을 수 없습니다. "},

    { ER_CHUNKEDINTARRAY_NOT_SUPPORTED,
      "ChunkedIntArray({0})는 현재 지원되지 않습니다."},

    { ER_OFFSET_BIGGER_THAN_SLOT,
      "오프셋이 슬롯보다 큽니다."},

    { ER_COROUTINE_NOT_AVAIL,
      "Coroutine을 사용할 수 없습니다. ID={0}"},

    { ER_COROUTINE_CO_EXIT,
      "CoroutineManager가 co_exit() 요청을 수신했습니다."},

    { ER_COJOINROUTINESET_FAILED,
      "co_joinCoroutineSet()를 실패했습니다."},

    { ER_COROUTINE_PARAM,
      "Coroutine 매개변수 오류({0})"},

    { ER_PARSER_DOTERMINATE_ANSWERS,
      "\n예상치 않은 오류: 구문 분석기 doTerminate가 {0}에 응답합니다."},

    { ER_NO_PARSE_CALL_WHILE_PARSING,
      "구문 분석 중 parse를 호출할 수 없습니다."},

    { ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "오류: {0} 축에 대해 입력된 이터레이터가 구현되지 않았습니다."},

    { ER_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "오류: {0} 축에 대한 이터레이터가 구현되지 않았습니다. "},

    { ER_ITERATOR_CLONE_NOT_SUPPORTED,
      "이터레이터 복제는 지원되지 않습니다."},

    { ER_UNKNOWN_AXIS_TYPE,
      "알 수 없는 축 순회 유형: {0}"},

    { ER_AXIS_NOT_SUPPORTED,
      "축 순환기가 지원되지 않음: {0}"},

    { ER_NO_DTMIDS_AVAIL,
      "더 이상 사용 가능한 DTM ID가 없습니다."},

    { ER_NOT_SUPPORTED,
      "지원되지 않음: {0}"},

    { ER_NODE_NON_NULL,
      "노드는 getDTMHandleFromNode에 대해 널이 아니어야 합니다."},

    { ER_COULD_NOT_RESOLVE_NODE,
      "노드를 핸들로 분석할 수 없습니다."},

    { ER_STARTPARSE_WHILE_PARSING,
       "구문 분석 중 startParse를 호출할 수 없습니다."},

    { ER_STARTPARSE_NEEDS_SAXPARSER,
       "startParse에는 널이 아닌 SAXParser가 필요합니다."},

    { ER_COULD_NOT_INIT_PARSER,
       "구문 분석기를 초기화할 수 없습니다."},

    { ER_EXCEPTION_CREATING_POOL,
       "풀에 대한 새 인스턴스를 생성하는 중 예외사항이 발생했습니다."},

    { ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
       "경로에 부적합한 이스케이프 시퀀스가 포함되어 있습니다."},

    { ER_SCHEME_REQUIRED,
       "체계가 필요합니다!"},

    { ER_NO_SCHEME_IN_URI,
       "URI에서 체계를 찾을 수 없음: {0}"},

    { ER_NO_SCHEME_INURI,
       "URI에서 체계를 찾을 수 없습니다."},

    { ER_PATH_INVALID_CHAR,
       "경로에 부적합한 문자가 포함됨: {0}"},

    { ER_SCHEME_FROM_NULL_STRING,
       "널 문자열에서 체계를 설정할 수 없습니다."},

    { ER_SCHEME_NOT_CONFORMANT,
       "체계가 일치하지 않습니다."},

    { ER_HOST_ADDRESS_NOT_WELLFORMED,
       "호스트가 완전한 주소가 아닙니다."},

    { ER_PORT_WHEN_HOST_NULL,
       "호스트가 널일 경우 포트를 설정할 수 없습니다."},

    { ER_INVALID_PORT,
       "포트 번호가 부적합합니다."},

    { ER_FRAG_FOR_GENERIC_URI,
       "일반 URI에 대해서만 부분을 설정할 수 있습니다."},

    { ER_FRAG_WHEN_PATH_NULL,
       "경로가 널일 경우 부분을 설정할 수 없습니다."},

    { ER_FRAG_INVALID_CHAR,
       "부분에 부적합한 문자가 포함되어 있습니다."},

    { ER_PARSER_IN_USE,
      "구문 분석기가 이미 사용되고 있습니다."},

    { ER_CANNOT_CHANGE_WHILE_PARSING,
      "구문 분석 중 {0} {1}을(를) 변경할 수 없습니다."},

    { ER_SELF_CAUSATION_NOT_PERMITTED,
      "자체 인과 관계는 허용되지 않습니다."},

    { ER_NO_USERINFO_IF_NO_HOST,
      "호스트를 지정하지 않은 경우에는 Userinfo를 지정할 수 없습니다."},

    { ER_NO_PORT_IF_NO_HOST,
      "호스트를 지정하지 않은 경우에는 포트를 지정할 수 없습니다."},

    { ER_NO_QUERY_STRING_IN_PATH,
      "경로 및 질의 문자열에 질의 문자열을 지정할 수 없습니다."},

    { ER_NO_FRAGMENT_STRING_IN_PATH,
      "경로와 부분에 모두 부분을 지정할 수는 없습니다."},

    { ER_CANNOT_INIT_URI_EMPTY_PARMS,
      "빈 매개변수로 URI를 초기화할 수 없습니다."},

    { ER_METHOD_NOT_SUPPORTED,
      "메소드가 아직 지원되지 않습니다. "},

    { ER_INCRSAXSRCFILTER_NOT_RESTARTABLE,
      "현재 IncrementalSAXSource_Filter를 재시작할 수 없습니다."},

    { ER_XMLRDR_NOT_BEFORE_STARTPARSE,
      "startParse 요청 전에 XMLReader가 실행되지 않았습니다."},

    { ER_AXIS_TRAVERSER_NOT_SUPPORTED,
      "축 순환기가 지원되지 않음: {0}"},

    { ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER,
      "널 PrintWriter로 ListingErrorHandler가 생성되었습니다!"},

    { ER_SYSTEMID_UNKNOWN,
      "SystemId를 알 수 없습니다."},

    { ER_LOCATION_UNKNOWN,
      "오류 위치를 알 수 없습니다."},

    { ER_PREFIX_MUST_RESOLVE,
      "접두어는 네임스페이스로 분석되어야 함: {0}"},

    { ER_CREATEDOCUMENT_NOT_SUPPORTED,
      "XPathContext에서는 createDocument()가 지원되지 않습니다!"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT,
      "속성 하위에 소유자 문서가 없습니다!"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT_ELEMENT,
      "속성 하위에 소유자 문서 요소가 없습니다!"},

    { ER_CANT_OUTPUT_TEXT_BEFORE_DOC,
      "경고: 문서 요소 앞에 텍스트를 출력할 수 없습니다! 무시하는 중..."},

    { ER_CANT_HAVE_MORE_THAN_ONE_ROOT,
      "DOM에서 루트를 두 개 이상 사용할 수 없습니다!"},

    { ER_ARG_LOCALNAME_NULL,
       "'localName' 인수가 널입니다."},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The localname is the portion after the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_LOCALNAME_INVALID,
       "QNAME의 Localname은 적합한 NCName이어야 합니다."},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The prefix is the portion before the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_PREFIX_INVALID,
       "QNAME의 접두어는 적합한 NCName이어야 합니다."},

    { ER_NAME_CANT_START_WITH_COLON,
      "이름은 콜론으로 시작할 수 없습니다."},

    { "BAD_CODE", "createMessage에 대한 매개변수가 범위를 벗어났습니다."},
    { "FORMAT_FAILED", "messageFormat 호출 중 예외사항이 발생했습니다."},
    { "line", "행 번호"},
    { "column","열 번호"},

    {ER_SERIALIZER_NOT_CONTENTHANDLER,
      "Serializer 클래스 ''{0}''이(가) org.xml.sax.ContentHandler를 구현하지 않았습니다."},

    {ER_RESOURCE_COULD_NOT_FIND,
      "[{0}] 리소스를 찾을 수 없습니다.\n {1}" },

    {ER_RESOURCE_COULD_NOT_LOAD,
      "[{0}] 리소스가 다음을 로드할 수 없음: {1} \n {2} \t {3}" },

    {ER_BUFFER_SIZE_LESSTHAN_ZERO,
      "버퍼 크기 <=0" },

    {ER_INVALID_UTF16_SURROGATE,
      "부적합한 UTF-16 대리 요소가 감지됨: {0}" },

    {ER_OIERROR,
      "IO 오류" },

    {ER_ILLEGAL_ATTRIBUTE_POSITION,
      "하위 노드가 생성된 후 또는 요소가 생성되기 전에 {0} 속성을 추가할 수 없습니다. 속성이 무시됩니다."},

      /*
       * Note to translators:  The stylesheet contained a reference to a
       * namespace prefix that was undefined.  The value of the substitution
       * text is the name of the prefix.
       */
    {ER_NAMESPACE_PREFIX,
      "''{0}'' 접두어에 대한 네임스페이스가 선언되지 않았습니다." },
      /*
       * Note to translators:  This message is reported if the stylesheet
       * being processed attempted to construct an XML document with an
       * attribute in a place other than on an element.  The substitution text
       * specifies the name of the attribute.
       */
    {ER_STRAY_ATTRIBUTE,
      "''{0}'' 속성이 요소에 포함되어 있지 않습니다." },

      /*
       * Note to translators:  As with the preceding message, a namespace
       * declaration has the form of an attribute and is only permitted to
       * appear on an element.  The substitution text {0} is the namespace
       * prefix and {1} is the URI that was being used in the erroneous
       * namespace declaration.
       */
    {ER_STRAY_NAMESPACE,
      "네임스페이스 선언 ''{0}''=''{1}''이(가) 요소에 포함되어 있지 않습니다." },

    {ER_COULD_NOT_LOAD_RESOURCE,
      "{0}을(를) 로드할 수 없습니다. CLASSPATH를 확인하십시오. 현재 기본값만 사용하는 중입니다."},

    { ER_ILLEGAL_CHARACTER,
       "{1}의 지정된 출력 인코딩에서 표시되지 않는 정수 값 {0}의 문자를 출력하려고 시도했습니다."},

    {ER_COULD_NOT_LOAD_METHOD_PROPERTY,
      "출력 메소드 ''{1}''에 대한 속성 파일 ''{0}''을(를) 로드할 수 없습니다. CLASSPATH를 확인하십시오." }


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
