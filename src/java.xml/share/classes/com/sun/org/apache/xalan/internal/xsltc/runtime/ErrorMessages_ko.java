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

package com.sun.org.apache.xalan.internal.xsltc.runtime;

import java.util.ListResourceBundle;

/**
 * @author Morten Jorgensen
 */
public class ErrorMessages_ko extends ListResourceBundle {

/*
 * XSLTC run-time error messages.
 *
 * General notes to translators and definitions:
 *
 *   1) XSLTC is the name of the product.  It is an acronym for XML Stylesheet:
 *      Transformations Compiler
 *
 *   2) A stylesheet is a description of how to transform an input XML document
 *      into a resultant output XML document (or HTML document or text)
 *
 *   3) An axis is a particular "dimension" in a tree representation of an XML
 *      document; the nodes in the tree are divided along different axes.
 *      Traversing the "child" axis, for instance, means that the program
 *      would visit each child of a particular node; traversing the "descendant"
 *      axis means that the program would visit the child nodes of a particular
 *      node, their children, and so on until the leaf nodes of the tree are
 *      reached.
 *
 *   4) An iterator is an object that traverses nodes in a tree along a
 *      particular axis, one at a time.
 *
 *   5) An element is a mark-up tag in an XML document; an attribute is a
 *      modifier on the tag.  For example, in <elem attr='val' attr2='val2'>
 *      "elem" is an element name, "attr" and "attr2" are attribute names with
 *      the values "val" and "val2", respectively.
 *
 *   6) A namespace declaration is a special attribute that is used to associate
 *      a prefix with a URI (the namespace).  The meanings of element names and
 *      attribute names that use that prefix are defined with respect to that
 *      namespace.
 *
 *   7) DOM is an acronym for Document Object Model.  It is a tree
 *      representation of an XML document.
 *
 *      SAX is an acronym for the Simple API for XML processing.  It is an API
 *      used inform an XML processor (in this case XSLTC) of the structure and
 *      content of an XML document.
 *
 *      Input to the stylesheet processor can come from an XML parser in the
 *      form of a DOM tree or through the SAX API.
 *
 *   8) DTD is a document type declaration.  It is a way of specifying the
 *      grammar for an XML file, the names and types of elements, attributes,
 *      etc.
 *
 *   9) Translet is an invented term that refers to the class file that contains
 *      the compiled form of a stylesheet.
 */

    // These message should be read from a locale-specific resource bundle
    /** Get the lookup table for error messages.
     *
     * @return The message lookup table.
     */
    public Object[][] getContents()
    {
        return new Object[][] {

        /*
         * Note to translators:  the substitution text in the following message
         * is a class name.  Used for internal errors in the processor.
         */
        {BasisLibrary.RUN_TIME_INTERNAL_ERR,
        "''{0}''에 런타임 내부 오류가 있습니다."},

        /*
         * Note to translators:  <xsl:copy> is a keyword that should not be
         * translated.
         */
        {BasisLibrary.RUN_TIME_COPY_ERR,
        "<xsl:copy>를 실행하는 중 런타임 오류가 발생했습니다."},

        /*
         * Note to translators:  The substitution text refers to data types.
         * The message is displayed if a value in a particular context needs to
         * be converted to type {1}, but that's not possible for a value of type
         * {0}.
         */
        {BasisLibrary.DATA_CONVERSION_ERR,
        "''{0}''에서 ''{1}''(으)로의 변환이 부적합합니다."},

        /*
         * Note to translators:  This message is displayed if the function named
         * by the substitution text is not a function that is supported.  XSLTC
         * is the acronym naming the product.
         */
        {BasisLibrary.EXTERNAL_FUNC_ERR,
        "XSLTC는 외부 함수 ''{0}''을(를) 지원하지 않습니다."},

        /*
         * Note to translators:  This message is displayed if two values are
         * compared for equality, but the data type of one of the values is
         * unknown.
         */
        {BasisLibrary.EQUALITY_EXPR_ERR,
        "동등성 표현식에 알 수 없는 인수 유형이 있습니다."},

        /*
         * Note to translators:  The substitution text for {0} will be a data
         * type; the substitution text for {1} will be the name of a function.
         * This is displayed if an argument of the particular data type is not
         * permitted for a call to this function.
         */
        {BasisLibrary.INVALID_ARGUMENT_ERR,
        "''{1}''에 대한 호출에 부적합한 인수 유형 ''{0}''이(가) 있습니다."},

        /*
         * Note to translators:  There is way of specifying a format for a
         * number using a pattern; the processor was unable to format the
         * particular value using the specified pattern.
         */
        {BasisLibrary.FORMAT_NUMBER_ERR,
        "''{1}'' 패턴을 사용하여 ''{0}'' 숫자의 형식을 지정하려고 시도하는 중입니다."},

        /*
         * Note to translators:  The following represents an internal error
         * situation in XSLTC.  The processor was unable to create a copy of an
         * iterator.  (See definition of iterator above.)
         */
        {BasisLibrary.ITERATOR_CLONE_ERR,
        "''{0}'' 이터레이터를 복제할 수 없습니다."},

        /*
         * Note to translators:  The following represents an internal error
         * situation in XSLTC.  The processor attempted to create an iterator
         * for a particular axis (see definition above) that it does not
         * support.
         */
        {BasisLibrary.AXIS_SUPPORT_ERR,
        "''{0}'' 축에 대한 이터레이터는 지원되지 않습니다."},

        /*
         * Note to translators:  The following represents an internal error
         * situation in XSLTC.  The processor attempted to create an iterator
         * for a particular axis (see definition above) that it does not
         * support.
         */
        {BasisLibrary.TYPED_AXIS_SUPPORT_ERR,
        "입력된 축 ''{0}''에 대한 이터레이터는 지원되지 않습니다."},

        /*
         * Note to translators:  This message is reported if the stylesheet
         * being processed attempted to construct an XML document with an
         * attribute in a place other than on an element.  The substitution text
         * specifies the name of the attribute.
         */
        {BasisLibrary.STRAY_ATTRIBUTE_ERR,
        "''{0}'' 속성이 요소에 포함되어 있지 않습니다."},

        /*
         * Note to translators:  As with the preceding message, a namespace
         * declaration has the form of an attribute and is only permitted to
         * appear on an element.  The substitution text {0} is the namespace
         * prefix and {1} is the URI that was being used in the erroneous
         * namespace declaration.
         */
        {BasisLibrary.STRAY_NAMESPACE_ERR,
        "네임스페이스 선언 ''{0}''=''{1}''이(가) 요소에 포함되어 있지 않습니다."},

        /*
         * Note to translators:  The stylesheet contained a reference to a
         * namespace prefix that was undefined.  The value of the substitution
         * text is the name of the prefix.
         */
        {BasisLibrary.NAMESPACE_PREFIX_ERR,
        "''{0}'' 접두어에 대한 네임스페이스가 선언되지 않았습니다."},

        /*
         * Note to translators:  The following represents an internal error.
         * DOMAdapter is a Java class in XSLTC.
         */
        {BasisLibrary.DOM_ADAPTER_INIT_ERR,
        "소스 DOM의 잘못된 유형을 사용하여 DOMAdapter가 생성되었습니다."},

        /*
         * Note to translators:  The following message indicates that the XML
         * parser that is providing input to XSLTC cannot be used because it
         * does not describe to XSLTC the structure of the input XML document's
         * DTD.
         */
        {BasisLibrary.PARSER_DTD_SUPPORT_ERR,
        "사용 중인 SAX 구문 분석기가 DTD 선언 이벤트를 처리하지 않습니다."},

        /*
         * Note to translators:  The following message indicates that the XML
         * parser that is providing input to XSLTC cannot be used because it
         * does not distinguish between ordinary XML attributes and namespace
         * declarations.
         */
        {BasisLibrary.NAMESPACES_SUPPORT_ERR,
        "사용 중인 SAX 구문 분석기가 XML 네임스페이스를 지원하지 않습니다."},

        /*
         * Note to translators:  The substitution text is the URI that was in
         * error.
         */
        {BasisLibrary.CANT_RESOLVE_RELATIVE_URI_ERR,
        "URI 참조 ''{0}''을(를) 분석할 수 없습니다."},

         /*
         * Note to translators:  The stylesheet contained an element that was
         * not recognized as part of the XSL syntax.  The substitution text
         * gives the element name.
         */
        {BasisLibrary.UNSUPPORTED_XSL_ERR,
        "''{0}''은(는) 지원되지 않는 XSL 요소입니다."},

        /*
         * Note to translators:  The stylesheet referred to an extension to the
         * XSL syntax and indicated that it was defined by XSLTC, but XSLTC does
         * not recognize the particular extension named.  The substitution text
         * gives the extension name.
         */
        {BasisLibrary.UNSUPPORTED_EXT_ERR,
        "''{0}''은(는) 알 수 없는 XSLTC 확장입니다."},


        /*
         * Note to translators:  This error message is produced if the translet
         * class was compiled using a newer version of XSLTC and deployed for
         * execution with an older version of XSLTC.  The substitution text is
         * the name of the translet class.
         */
        {BasisLibrary.UNKNOWN_TRANSLET_VERSION_ERR,
        "지정된 translet ''{0}''이(가) 사용 중인 XSLTC 런타임 버전보다 최신의 XSLTC 버전을 사용하여 생성되었습니다. 이 translet을 실행하려면 스타일시트를 재컴파일하거나 최신 XSLTC 버전을 사용해야 합니다."},

        /*
         * Note to translators:  An attribute whose effective value is required
         * to be a "QName" had a value that was incorrect.
         * 'QName' is an XML syntactic term that must not be translated.  The
         * substitution text contains the actual value of the attribute.
         */
        {BasisLibrary.INVALID_QNAME_ERR,
        "값이 QName이어야 하는 속성의 값이 ''{0}''입니다."},


        /*
         * Note to translators:  An attribute whose effective value is required
         * to be a "NCName" had a value that was incorrect.
         * 'NCName' is an XML syntactic term that must not be translated.  The
         * substitution text contains the actual value of the attribute.
         */
        {BasisLibrary.INVALID_NCNAME_ERR,
        "값이 NCName이어야 하는 속성의 값이 ''{0}''입니다."},

        {BasisLibrary.UNALLOWED_EXTENSION_FUNCTION_ERR,
        "보안 처리 기능이 true로 설정된 경우 확장 함수 ''{0}''을(를) 사용할 수 없습니다."},

        {BasisLibrary.UNALLOWED_EXTENSION_ELEMENT_ERR,
        "보안 처리 기능이 true로 설정된 경우 확장 요소 ''{0}''을(를) 사용할 수 없습니다."},
    };
    }

}
