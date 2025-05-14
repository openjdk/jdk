/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the  "License");
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
package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * An instance of this class is a ListResourceBundle that
 * has the required getContents() method that returns
 * an array of message-key/message associations.
 * <p>
 * The message keys are defined in {@link MsgKey}. The
 * messages that those keys map to are defined here.
 * <p>
 * The messages in the English version are intended to be
 * translated.
 *
 * This class is not a public API, it is only public because it is
 * used in com.sun.org.apache.xml.internal.serializer.
 *
 * @xsl.usage internal
 */
public class SerializerMessages_ko extends ListResourceBundle {

    /*
     * This file contains error and warning messages related to
     * Serializer Error Handling.
     *
     *  General notes to translators:

     *  1) A stylesheet is a description of how to transform an input XML document
     *     into a resultant XML document (or HTML document or text).  The
     *     stylesheet itself is described in the form of an XML document.

     *
     *  2) An element is a mark-up tag in an XML document; an attribute is a
     *     modifier on the tag.  For example, in <elem attr='val' attr2='val2'>
     *     "elem" is an element name, "attr" and "attr2" are attribute names with
     *     the values "val" and "val2", respectively.
     *
     *  3) A namespace declaration is a special attribute that is used to associate
     *     a prefix with a URI (the namespace).  The meanings of element names and
     *     attribute names that use that prefix are defined with respect to that
     *     namespace.
     *
     *
     */

    /** The lookup table for error messages.   */
    public Object[][] getContents() {
        Object[][] contents = new Object[][] {
            {   MsgKey.BAD_MSGKEY,
                "메시지 키 ''{0}''이(가) 메시지 클래스 ''{1}''에 없습니다." },

            {   MsgKey.BAD_MSGFORMAT,
                "메시지 클래스 ''{1}''에서 ''{0}'' 메시지의 형식이 잘못되었습니다." },

            {   MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
                "Serializer 클래스 ''{0}''이(가) org.xml.sax.ContentHandler를 구현하지 않았습니다." },

            {   MsgKey.ER_RESOURCE_COULD_NOT_FIND,
                    "[{0}] 리소스를 찾을 수 없습니다.\n {1}" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
                    "[{0}] 리소스가 다음을 로드할 수 없음: {1} \n {2} \t {3}" },

            {   MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
                    "버퍼 크기 <=0" },

            {   MsgKey.ER_INVALID_UTF16_SURROGATE,
                    "부적합한 UTF-16 대리 요소가 감지됨: {0}" },

            {   MsgKey.ER_OIERROR,
                "IO 오류" },

            {   MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
                "하위 노드가 생성된 후 또는 요소가 생성되기 전에 {0} 속성을 추가할 수 없습니다. 속성이 무시됩니다." },

            /*
             * Note to translators:  The stylesheet contained a reference to a
             * namespace prefix that was undefined.  The value of the substitution
             * text is the name of the prefix.
             */
            {   MsgKey.ER_NAMESPACE_PREFIX,
                "''{0}'' 접두어에 대한 네임스페이스가 선언되지 않았습니다." },

            /*
             * Note to translators:  This message is reported if the stylesheet
             * being processed attempted to construct an XML document with an
             * attribute in a place other than on an element.  The substitution text
             * specifies the name of the attribute.
             */
            {   MsgKey.ER_STRAY_ATTRIBUTE,
                "''{0}'' 속성이 요소에 포함되어 있지 않습니다." },

            /*
             * Note to translators:  As with the preceding message, a namespace
             * declaration has the form of an attribute and is only permitted to
             * appear on an element.  The substitution text {0} is the namespace
             * prefix and {1} is the URI that was being used in the erroneous
             * namespace declaration.
             */
            {   MsgKey.ER_STRAY_NAMESPACE,
                "네임스페이스 선언 ''{0}''=''{1}''이(가) 요소에 포함되어 있지 않습니다." },

            {   MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
                "{0}을(를) 로드할 수 없습니다. CLASSPATH를 확인하십시오. 현재 기본값만 사용하는 중입니다." },

            {   MsgKey.ER_ILLEGAL_CHARACTER,
                "{1}의 지정된 출력 인코딩에서 표시되지 않는 정수 값 {0}의 문자를 출력하려고 시도했습니다." },

            {   MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
                "출력 메소드 ''{1}''에 대한 속성 파일 ''{0}''을(를) 로드할 수 없습니다. CLASSPATH를 확인하십시오." },

            {   MsgKey.ER_INVALID_PORT,
                "포트 번호가 부적합합니다." },

            {   MsgKey.ER_PORT_WHEN_HOST_NULL,
                "호스트가 널일 경우 포트를 설정할 수 없습니다." },

            {   MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
                "호스트가 완전한 주소가 아닙니다." },

            {   MsgKey.ER_SCHEME_NOT_CONFORMANT,
                "체계가 일치하지 않습니다." },

            {   MsgKey.ER_SCHEME_FROM_NULL_STRING,
                "널 문자열에서 체계를 설정할 수 없습니다." },

            {   MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
                "경로에 부적합한 이스케이프 시퀀스가 포함되어 있습니다." },

            {   MsgKey.ER_PATH_INVALID_CHAR,
                "경로에 부적합한 문자가 포함됨: {0}" },

            {   MsgKey.ER_FRAG_INVALID_CHAR,
                "부분에 부적합한 문자가 포함되어 있습니다." },

            {   MsgKey.ER_FRAG_WHEN_PATH_NULL,
                "경로가 널일 경우 부분을 설정할 수 없습니다." },

            {   MsgKey.ER_FRAG_FOR_GENERIC_URI,
                "일반 URI에 대해서만 부분을 설정할 수 있습니다." },

            {   MsgKey.ER_NO_SCHEME_IN_URI,
                "URI에서 체계를 찾을 수 없습니다." },

            {   MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
                "빈 매개변수로 URI를 초기화할 수 없습니다." },

            {   MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
                "경로와 부분에 모두 부분을 지정할 수는 없습니다." },

            {   MsgKey.ER_NO_QUERY_STRING_IN_PATH,
                "경로 및 질의 문자열에 질의 문자열을 지정할 수 없습니다." },

            {   MsgKey.ER_NO_PORT_IF_NO_HOST,
                "호스트를 지정하지 않은 경우에는 포트를 지정할 수 없습니다." },

            {   MsgKey.ER_NO_USERINFO_IF_NO_HOST,
                "호스트를 지정하지 않은 경우에는 Userinfo를 지정할 수 없습니다." },

            {   MsgKey.ER_XML_VERSION_NOT_SUPPORTED,
                "경고: 출력 문서의 버전이 ''{0}''이(가) 되도록 요청했습니다. 이 버전의 XML은 지원되지 않습니다. 출력 문서의 버전은 ''1.0''이 됩니다." },

            {   MsgKey.ER_SCHEME_REQUIRED,
                "체계가 필요합니다!" },

            /*
             * Note to translators:  The words 'Properties' and
             * 'SerializerFactory' in this message are Java class names
             * and should not be translated.
             */
            {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
                "SerializerFactory에 전달된 Properties 객체에 ''{0}'' 속성이 없습니다." },

            {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
                "경고: 인코딩 ''{0}''은(는) Java 런타임에 지원되지 않습니다." },

             {MsgKey.ER_FEATURE_NOT_FOUND,
             "''{0}'' 매개변수를 인식할 수 없습니다."},

             {MsgKey.ER_FEATURE_NOT_SUPPORTED,
             "''{0}'' 매개변수가 인식되었지만 요청된 값을 설정할 수 없습니다."},

             {MsgKey.ER_STRING_TOO_LONG,
             "결과 문자열이 너무 커서 DOMString에 맞지 않음: ''{0}''."},

             {MsgKey.ER_TYPE_MISMATCH_ERR,
             "이 매개변수 이름에 대한 값 유형이 필요한 값 유형과 호환되지 않습니다."},

             {MsgKey.ER_NO_OUTPUT_SPECIFIED,
             "데이터를 쓸 출력 대상이 널입니다."},

             {MsgKey.ER_UNSUPPORTED_ENCODING,
             "지원되지 않는 인코딩이 발견되었습니다."},

             {MsgKey.ER_UNABLE_TO_SERIALIZE_NODE,
             "노드를 직렬화할 수 없습니다."},

             {MsgKey.ER_CDATA_SECTIONS_SPLIT,
             "CDATA 섹션에 하나 이상의 종료 표시자 ']]>'가 있습니다."},

             {MsgKey.ER_WARNING_WF_NOT_CHECKED,
                 "Well-Formedness 검사기의 인스턴스를 생성할 수 없습니다. well-formed 매개변수가 true로 설정되었지만 well-formedness 검사를 수행할 수 없습니다."
             },

             {MsgKey.ER_WF_INVALID_CHARACTER,
                 "''{0}'' 노드에 부적합한 XML 문자가 포함되어 있습니다."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_COMMENT,
                 "주석에서 부적합한 XML 문자(유니코드: 0x{0})가 발견되었습니다."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_PI,
                 "부적합한 XML 문자(유니코드: 0x{0})가 instructiondata 처리에서 발견되었습니다."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_CDATA,
                 "부적합한 XML 문자(유니코드: 0x{0})가 CDATASection의 콘텐츠에서 발견되었습니다."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_TEXT,
                 "부적합한 XML 문자(유니코드: 0x{0})가 노드의 문자 데이터 콘텐츠에서 발견되었습니다."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_NODE_NAME,
                 "부적합한 XML 문자가 이름이 ''{1}''인 {0} 노드에서 발견되었습니다."
             },

             { MsgKey.ER_WF_DASH_IN_COMMENT,
                 "주석에서는 \"--\" 문자열이 허용되지 않습니다."
             },

             {MsgKey.ER_WF_LT_IN_ATTVAL,
                 "요소 유형 \"{0}\"과(와) 연관된 \"{1}\" 속성의 값에는 ''<'' 문자가 포함되지 않아야 합니다."
             },

             {MsgKey.ER_WF_REF_TO_UNPARSED_ENT,
                 "구문이 분석되지 않은 엔티티 참조 \"&{0};\"은(는) 허용되지 않습니다."
             },

             {MsgKey.ER_WF_REF_TO_EXTERNAL_ENT,
                 "속성값에서는 외부 엔티티 참조 \"&{0};\"이 허용되지 않습니다."
             },

             {MsgKey.ER_NS_PREFIX_CANNOT_BE_BOUND,
                 "\"{0}\" 접두어를 \"{1}\" 네임스페이스에 바인드할 수 없습니다."
             },

             {MsgKey.ER_NULL_LOCAL_ELEMENT_NAME,
                 "\"{0}\" 요소의 로컬 이름이 널입니다."
             },

             {MsgKey.ER_NULL_LOCAL_ATTR_NAME,
                 "\"{0}\" 속성의 로컬 이름이 널입니다."
             },

             { MsgKey.ER_ELEM_UNBOUND_PREFIX_IN_ENTREF,
                 "엔티티 노드 \"{0}\"의 대체 텍스트에 바인드되지 않은 접두어 \"{2}\"을(를) 사용하는 요소 노드 \"{1}\"이(가) 포함되어 있습니다."
             },

             { MsgKey.ER_ATTR_UNBOUND_PREFIX_IN_ENTREF,
                 "엔티티 노드 \"{0}\"의 대체 텍스트에 바인드되지 않은 접두어 \"{2}\"을(를) 사용하는 속성 노드 \"{1}\"이(가) 포함되어 있습니다."
             },

             { MsgKey.ER_WRITING_INTERNAL_SUBSET,
                 "내부 부분 집합을 쓰는 중 오류가 발생했습니다."
             },

        };

        return contents;
    }
}
