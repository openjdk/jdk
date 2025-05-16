/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.org.apache.xalan.internal.xsltc.compiler.util;

import java.util.ListResourceBundle;

/**
 * @author Morten Jorgensen
 */
public class ErrorMessages_ko extends ListResourceBundle {

/*
 * XSLTC compile-time error messages.
 *
 * General notes to translators and definitions:
 *
 *   1) XSLTC is the name of the product.  It is an acronym for "XSLT Compiler".
 *      XSLT is an acronym for "XML Stylesheet Language: Transformations".
 *
 *   2) A stylesheet is a description of how to transform an input XML document
 *      into a resultant XML document (or HTML document or text).  The
 *      stylesheet itself is described in the form of an XML document.
 *
 *   3) A template is a component of a stylesheet that is used to match a
 *      particular portion of an input document and specifies the form of the
 *      corresponding portion of the output document.
 *
 *   4) An axis is a particular "dimension" in a tree representation of an XML
 *      document; the nodes in the tree are divided along different axes.
 *      Traversing the "child" axis, for instance, means that the program
 *      would visit each child of a particular node; traversing the "descendant"
 *      axis means that the program would visit the child nodes of a particular
 *      node, their children, and so on until the leaf nodes of the tree are
 *      reached.
 *
 *   5) An iterator is an object that traverses nodes in a tree along a
 *      particular axis, one at a time.
 *
 *   6) An element is a mark-up tag in an XML document; an attribute is a
 *      modifier on the tag.  For example, in <elem attr='val' attr2='val2'>
 *      "elem" is an element name, "attr" and "attr2" are attribute names with
 *      the values "val" and "val2", respectively.
 *
 *   7) A namespace declaration is a special attribute that is used to associate
 *      a prefix with a URI (the namespace).  The meanings of element names and
 *      attribute names that use that prefix are defined with respect to that
 *      namespace.
 *
 *   8) DOM is an acronym for Document Object Model.  It is a tree
 *      representation of an XML document.
 *
 *      SAX is an acronym for the Simple API for XML processing.  It is an API
 *      used inform an XML processor (in this case XSLTC) of the structure and
 *      content of an XML document.
 *
 *      Input to the stylesheet processor can come from an XML parser in the
 *      form of a DOM tree or through the SAX API.
 *
 *   9) DTD is a document type declaration.  It is a way of specifying the
 *      grammar for an XML file, the names and types of elements, attributes,
 *      etc.
 *
 *  10) XPath is a specification that describes a notation for identifying
 *      nodes in a tree-structured representation of an XML document.  An
 *      instance of that notation is referred to as an XPath expression.
 *
 *  11) Translet is an invented term that refers to the class file that contains
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
        {ErrorMsg.MULTIPLE_STYLESHEET_ERR,
        "동일한 파일에 스타일시트가 두 개 이상 정의되었습니다."},

        /*
         * Note to translators:  The substitution text is the name of a
         * template.  The same name was used on two different templates in the
         * same stylesheet.
         */
        {ErrorMsg.TEMPLATE_REDEF_ERR,
        "이 스타일시트에는 ''{0}'' 템플리트가 이미 정의되었습니다."},


        /*
         * Note to translators:  The substitution text is the name of a
         * template.  A reference to the template name was encountered, but the
         * template is undefined.
         */
        {ErrorMsg.TEMPLATE_UNDEF_ERR,
        "이 스타일시트에는 ''{0}'' 템플리트가 정의되지 않았습니다."},

        /*
         * Note to translators:  The substitution text is the name of a variable
         * that was defined more than once.
         */
        {ErrorMsg.VARIABLE_REDEF_ERR,
        "동일한 범위에서 ''{0}'' 변수가 여러 개 정의되었습니다."},

        /*
         * Note to translators:  The substitution text is the name of a variable
         * or parameter.  A reference to the variable or parameter was found,
         * but it was never defined.
         */
        {ErrorMsg.VARIABLE_UNDEF_ERR,
        "변수 또는 매개변수 ''{0}''이(가) 정의되지 않았습니다."},

        /*
         * Note to translators:  The word "class" here refers to a Java class.
         * Processing the stylesheet required a class to be loaded, but it could
         * not be found.  The substitution text is the name of the class.
         */
        {ErrorMsg.CLASS_NOT_FOUND_ERR,
        "''{0}'' 클래스를 찾을 수 없습니다."},

        /*
         * Note to translators:  The word "method" here refers to a Java method.
         * Processing the stylesheet required a reference to the method named by
         * the substitution text, but it could not be found.  "public" is the
         * Java keyword.
         */
        {ErrorMsg.METHOD_NOT_FOUND_ERR,
        "외부 메소드 ''{0}''을(를) 찾을 수 없습니다. 이 메소드는 public이어야 합니다."},

        /*
         * Note to translators:  The word "method" here refers to a Java method.
         * Processing the stylesheet required a reference to the method named by
         * the substitution text, but no method with the required types of
         * arguments or return type could be found.
         */
        {ErrorMsg.ARGUMENT_CONVERSION_ERR,
        "''{0}'' 메소드에 대한 호출에서 인수/반환 유형을 변환할 수 없습니다."},

        /*
         * Note to translators:  The file or URI named in the substitution text
         * is missing.
         */
        {ErrorMsg.FILE_NOT_FOUND_ERR,
        "파일 또는 URI ''{0}''을(를) 찾을 수 없습니다."},

        /*
         * Note to translators:  This message is displayed when the URI
         * mentioned in the substitution text is not well-formed syntactically.
         */
        {ErrorMsg.INVALID_URI_ERR,
        "URI ''{0}''이(가) 부적합합니다."},

        /*
         * Note to translators:  This message is displayed when the URI
         * mentioned in the substitution text is not well-formed syntactically.
         */
        {ErrorMsg.CATALOG_EXCEPTION,
        "JAXP08090001: CatalogResolver가 \"{0}\" 카탈로그에 사용으로 설정되었지만 CatalogException이 반환되었습니다."},

        /*
         * Note to translators:  The file or URI named in the substitution text
         * exists but could not be opened.
         */
        {ErrorMsg.FILE_ACCESS_ERR,
        "파일 또는 URI ''{0}''을(를) 열 수 없습니다."},

        /*
         * Note to translators: <xsl:stylesheet> and <xsl:transform> are
         * keywords that should not be translated.
         */
        {ErrorMsg.MISSING_ROOT_ERR,
        "<xsl:stylesheet> 또는 <xsl:transform> 요소가 필요합니다."},

        /*
         * Note to translators:  The stylesheet contained a reference to a
         * namespace prefix that was undefined.  The value of the substitution
         * text is the name of the prefix.
         */
        {ErrorMsg.NAMESPACE_UNDEF_ERR,
        "네임스페이스 접두어 ''{0}''이(가) 선언되지 않았습니다."},

        /*
         * Note to translators:  The Java function named in the stylesheet could
         * not be found.
         */
        {ErrorMsg.FUNCTION_RESOLVE_ERR,
        "''{0}'' 함수에 대한 호출을 분석할 수 없습니다."},

        /*
         * Note to translators:  The substitution text is the name of a
         * function.  A literal string here means a constant string value.
         */
        {ErrorMsg.NEED_LITERAL_ERR,
        "''{0}''에 대한 인수는 리터럴 문자열이어야 합니다."},

        /*
         * Note to translators:  This message indicates there was a syntactic
         * error in the form of an XPath expression.  The substitution text is
         * the expression.
         */
        {ErrorMsg.XPATH_PARSER_ERR,
        "XPath 표현식 ''{0}''의 구문을 분석하는 중 오류가 발생했습니다."},

        /*
         * Note to translators:  An element in the stylesheet requires a
         * particular attribute named by the substitution text, but that
         * attribute was not specified in the stylesheet.
         */
        {ErrorMsg.REQUIRED_ATTR_ERR,
        "필수 속성 ''{0}''이(가) 누락되었습니다."},

        /*
         * Note to translators:  This message indicates that a character not
         * permitted in an XPath expression was encountered.  The substitution
         * text is the offending character.
         */
        {ErrorMsg.ILLEGAL_CHAR_ERR,
        "XPath 표현식에 잘못된 문자 ''{0}''이(가) 있습니다."},

        /*
         * Note to translators:  A processing instruction is a mark-up item in
         * an XML document that request some behaviour of an XML processor.  The
         * form of the name of was invalid in this case, and the substitution
         * text is the name.
         */
        {ErrorMsg.ILLEGAL_PI_ERR,
        "''{0}''은(는) 명령 처리에 잘못된 이름입니다."},

        /*
         * Note to translators:  This message is reported if the stylesheet
         * being processed attempted to construct an XML document with an
         * attribute in a place other than on an element.  The substitution text
         * specifies the name of the attribute.
         */
        {ErrorMsg.STRAY_ATTRIBUTE_ERR,
        "''{0}'' 속성이 요소에 포함되어 있지 않습니다."},

        /*
         * Note to translators:  An attribute that wasn't recognized was
         * specified on an element in the stylesheet.  The attribute is named
         * by the substitution
         * text.
         */
        {ErrorMsg.ILLEGAL_ATTRIBUTE_ERR,
        "''{0}''은(는) 잘못된 속성입니다."},

        /*
         * Note to translators:  "import" and "include" are keywords that should
         * not be translated.  This messages indicates that the stylesheet
         * named in the substitution text imported or included itself either
         * directly or indirectly.
         */
        {ErrorMsg.CIRCULAR_INCLUDE_ERR,
        "순환 import/include입니다. ''{0}'' 스타일시트가 이미 로드되었습니다."},

        /*
         * Note to translators:  "xsl:import" and "xsl:include" are keywords that
         * should not be translated.
         */
        {ErrorMsg.IMPORT_PRECEDE_OTHERS_ERR,
        "xsl:import 요소 하위는 xsl:include 요소 하위를 포함해 xsl:stylesheet 요소의 모든 다른 요소 하위 앞에 와야 합니다."},

        /*
         * Note to translators:  A result-tree fragment is a portion of a
         * resulting XML document represented as a tree.  "<xsl:sort>" is a
         * keyword and should not be translated.
         */
        {ErrorMsg.RESULT_TREE_SORT_ERR,
        "Result-tree 부분을 정렬할 수 없습니다(<xsl:sort> 요소가 무시됨). 결과 트리를 생성할 때는 노드를 정렬해야 합니다."},

        /*
         * Note to translators:  A name can be given to a particular style to be
         * used to format decimal values.  The substitution text gives the name
         * of such a style for which more than one declaration was encountered.
         */
        {ErrorMsg.SYMBOLS_REDEF_ERR,
        "십진수 형식 ''{0}''이(가) 이미 정의되었습니다."},

        /*
         * Note to translators:  The stylesheet version named in the
         * substitution text is not supported.
         */
        {ErrorMsg.XSL_VERSION_ERR,
        "XSLTC는 XSL 버전 ''{0}''을(를) 지원하지 않습니다."},

        /*
         * Note to translators:  The definitions of one or more variables or
         * parameters depend on one another.
         */
        {ErrorMsg.CIRCULAR_VARIABLE_ERR,
        "''{0}''에 순환 변수/매개변수 참조가 있습니다."},

        /*
         * Note to translators:  The operator in an expresion with two operands was
         * not recognized.
         */
        {ErrorMsg.ILLEGAL_BINARY_OP_ERR,
        "바이너리 표현식에 대해 알 수 없는 연산자입니다."},

        /*
         * Note to translators:  This message is produced if a reference to a
         * function has too many or too few arguments.
         */
        {ErrorMsg.ILLEGAL_ARG_ERR,
        "함수 호출에 대한 인수가 잘못되었습니다."},

        /*
         * Note to translators:  "document()" is the name of function and must
         * not be translated.  A node-set is a set of the nodes in the tree
         * representation of an XML document.
         */
        {ErrorMsg.DOCUMENT_ARG_ERR,
        "document() 함수에 대한 두번째 인수는 node-set여야 합니다."},

        /*
         * Note to translators:  "<xsl:when>" and "<xsl:choose>" are keywords
         * and should not be translated.  This message describes a syntax error
         * in the stylesheet.
         */
        {ErrorMsg.MISSING_WHEN_ERR,
        "<xsl:choose>에는 <xsl:when> 요소가 하나 이상 필요합니다."},

        /*
         * Note to translators:  "<xsl:otherwise>" and "<xsl:choose>" are
         * keywords and should not be translated.  This message describes a
         * syntax error in the stylesheet.
         */
        {ErrorMsg.MULTIPLE_OTHERWISE_ERR,
        "<xsl:choose>에서는 <xsl:otherwise> 요소가 하나만 허용됩니다."},

        /*
         * Note to translators:  "<xsl:otherwise>" and "<xsl:choose>" are
         * keywords and should not be translated.  This message describes a
         * syntax error in the stylesheet.
         */
        {ErrorMsg.STRAY_OTHERWISE_ERR,
        "<xsl:otherwise>는 <xsl:choose>에서만 사용할 수 있습니다."},

        /*
         * Note to translators:  "<xsl:when>" and "<xsl:choose>" are keywords
         * and should not be translated.  This message describes a syntax error
         * in the stylesheet.
         */
        {ErrorMsg.STRAY_WHEN_ERR,
        "<xsl:when>은 <xsl:choose>에서만 사용할 수 있습니다."},

        /*
         * Note to translators:  "<xsl:when>", "<xsl:otherwise>" and
         * "<xsl:choose>" are keywords and should not be translated.  This
         * message describes a syntax error in the stylesheet.
         */
        {ErrorMsg.WHEN_ELEMENT_ERR,
        "<xsl:choose>에서는 <xsl:when> 및 <xsl:otherwise> 요소만 허용됩니다."},

        /*
         * Note to translators:  "<xsl:attribute-set>" and "name" are keywords
         * that should not be translated.
         */
        {ErrorMsg.UNNAMED_ATTRIBSET_ERR,
        "<xsl:attribute-set>에 'name' 속성이 누락되었습니다."},

        /*
         * Note to translators:  An element in the stylesheet contained an
         * element of a type that it was not permitted to contain.
         */
        {ErrorMsg.ILLEGAL_CHILD_ERR,
        "하위 요소가 잘못되었습니다."},

        /*
         * Note to translators:  The stylesheet tried to create an element with
         * a name that was not a valid XML name.  The substitution text contains
         * the name.
         */
        {ErrorMsg.ILLEGAL_ELEM_NAME_ERR,
        "''{0}'' 요소를 호출할 수 없습니다."},

        /*
         * Note to translators:  The stylesheet tried to create an attribute
         * with a name that was not a valid XML name.  The substitution text
         * contains the name.
         */
        {ErrorMsg.ILLEGAL_ATTR_NAME_ERR,
        "''{0}'' 속성을 호출할 수 없습니다."},

        /*
         * Note to translators:  The children of the outermost element of a
         * stylesheet are referred to as top-level elements.  No text should
         * occur within that outermost element unless it is within a top-level
         * element.  This message indicates that that constraint was violated.
         * "<xsl:stylesheet>" is a keyword that should not be translated.
         */
        {ErrorMsg.ILLEGAL_TEXT_NODE_ERR,
        "텍스트 데이터가 최상위 레벨 <xsl:stylesheet> 요소에 포함되어 있지 않습니다."},

        /*
         * Note to translators:  JAXP is an acronym for the Java API for XML
         * Processing.  This message indicates that the XML parser provided to
         * XSLTC to process the XML input document had a configuration problem.
         */
        {ErrorMsg.SAX_PARSER_CONFIG_ERR,
        "JAXP 구문 분석기가 제대로 구성되지 않았습니다."},

        /*
         * Note to translators:  The substitution text names the internal error
         * encountered.
         */
        {ErrorMsg.INTERNAL_ERR,
        "복구할 수 없는 XSLTC 내부 오류: ''{0}''"},

        /*
         * Note to translators:  The stylesheet contained an element that was
         * not recognized as part of the XSL syntax.  The substitution text
         * gives the element name.
         */
        {ErrorMsg.UNSUPPORTED_XSL_ERR,
        "''{0}''은(는) 지원되지 않는 XSL 요소입니다."},

        /*
         * Note to translators:  The stylesheet referred to an extension to the
         * XSL syntax and indicated that it was defined by XSLTC, but XSTLC does
         * not recognized the particular extension named.  The substitution text
         * gives the extension name.
         */
        {ErrorMsg.UNSUPPORTED_EXT_ERR,
        "''{0}''은(는) 알 수 없는 XSLTC 확장입니다."},

        /*
         * Note to translators:  The XML document given to XSLTC as a stylesheet
         * was not, in fact, a stylesheet.  XSLTC is able to detect that in this
         * case because the outermost element in the stylesheet has to be
         * declared with respect to the XSL namespace URI, but no declaration
         * for that namespace was seen.
         */
        {ErrorMsg.MISSING_XSLT_URI_ERR,
        "입력 문서는 스타일시트가 아닙니다. XSL 네임스페이스가 루트 요소에 선언되지 않았습니다."},

        /*
         * Note to translators:  XSLTC could not find the stylesheet document
         * with the name specified by the substitution text.
         */
        {ErrorMsg.MISSING_XSLT_TARGET_ERR,
        "스타일시트 대상 ''{0}''을(를) 찾을 수 없습니다."},

        /*
         * Note to translators:  access to the stylesheet target is denied
         */
        {ErrorMsg.ACCESSING_XSLT_TARGET_ERR,
        "accessExternalStylesheet 속성으로 설정된 제한으로 인해 ''{1}'' 액세스가 허용되지 않으므로 스타일시트 대상 ''{0}''을(를) 읽을 수 없습니다."},

        /*
         * Note to translators:  This message represents an internal error in
         * condition in XSLTC.  The substitution text is the class name in XSLTC
         * that is missing some functionality.
         */
        {ErrorMsg.NOT_IMPLEMENTED_ERR,
        "구현되지 않음: ''{0}''."},

        /*
         * Note to translators:  The XML document given to XSLTC as a stylesheet
         * was not, in fact, a stylesheet.
         */
        {ErrorMsg.NOT_STYLESHEET_ERR,
        "입력 문서에 XSL 스타일시트가 포함되어 있지 않습니다."},

        /*
         * Note to translators:  The element named in the substitution text was
         * encountered in the stylesheet but is not recognized.
         */
        {ErrorMsg.ELEMENT_PARSE_ERR,
        "''{0}'' 요소의 구문을 분석할 수 없습니다."},

        /*
         * Note to translators:  "use", "<key>", "node", "node-set", "string"
         * and "number" are keywords in this context and should not be
         * translated.  This message indicates that the value of the "use"
         * attribute was not one of the permitted values.
         */
        {ErrorMsg.KEY_USE_ATTR_ERR,
        "<key>의 use 속성은 node, node-set, string 또는 number여야 합니다."},

        /*
         * Note to translators:  An XML document can specify the version of the
         * XML specification to which it adheres.  This message indicates that
         * the version specified for the output document was not valid.
         */
        {ErrorMsg.OUTPUT_VERSION_ERR,
        "출력 XML 문서 버전은 1.0이어야 합니다."},

        /*
         * Note to translators:  The operator in a comparison operation was
         * not recognized.
         */
        {ErrorMsg.ILLEGAL_RELAT_OP_ERR,
        "관계 표현식에 대해 알 수 없는 연산자입니다."},

        /*
         * Note to translators:  An attribute set defines as a set of XML
         * attributes that can be added to an element in the output XML document
         * as a group.  This message is reported if the name specified was not
         * used to declare an attribute set.  The substitution text is the name
         * that is in error.
         */
        {ErrorMsg.ATTRIBSET_UNDEF_ERR,
        "존재하지 않는 속성 집합 ''{0}''을(를) 사용하려고 시도하는 중입니다."},

        /*
         * Note to translators:  The term "attribute value template" is a term
         * defined by XSLT which describes the value of an attribute that is
         * determined by an XPath expression.  The message indicates that the
         * expression was syntactically incorrect; the substitution text
         * contains the expression that was in error.
         */
        {ErrorMsg.ATTR_VAL_TEMPLATE_ERR,
        "속성값 템플리트 ''{0}''의 구문을 분석할 수 없습니다."},

        /*
         * Note to translators:  ???
         */
        {ErrorMsg.UNKNOWN_SIG_TYPE_ERR,
        "''{0}'' 클래스에 대한 서명에 알 수 없는 데이터 유형이 있습니다."},

        /*
         * Note to translators:  The substitution text refers to data types.
         * The message is displayed if a value in a particular context needs to
         * be converted to type {1}, but that's not possible for a value of
         * type {0}.
         */
        {ErrorMsg.DATA_CONVERSION_ERR,
        "데이터 유형 ''{0}''을(를) ''{1}''(으)로 변환할 수 없습니다."},

        /*
         * Note to translators:  "Templates" is a Java class name that should
         * not be translated.
         */
        {ErrorMsg.NO_TRANSLET_CLASS_ERR,
        "이 Templates에는 적합한 translet 클래스 정의가 포함되어 있지 않습니다."},

        /*
         * Note to translators:  "Templates" is a Java class name that should
         * not be translated.
         */
        {ErrorMsg.NO_MAIN_TRANSLET_ERR,
        "이 Templates에는 이름이 ''{0}''인 클래스가 포함되어 있지 않습니다."},

        /*
         * Note to translators:  The substitution text is the name of a class.
         */
        {ErrorMsg.TRANSLET_CLASS_ERR,
        "Translet 클래스 ''{0}''을(를) 로드할 수 없습니다."},

        {ErrorMsg.TRANSLET_OBJECT_ERR,
        "Translet 클래스가 로드되었지만 translet 인스턴스를 생성할 수 없습니다."},

        /*
         * Note to translators:  "ErrorListener" is a Java interface name that
         * should not be translated.  The message indicates that the user tried
         * to set an ErrorListener object on object of the class named in the
         * substitution text with "null" Java value.
         */
        {ErrorMsg.ERROR_LISTENER_NULL_ERR,
        "''{0}''에 대한 ErrorListener를 null로 설정하려고 시도하는 중"},

        /*
         * Note to translators:  StreamSource, SAXSource and DOMSource are Java
         * interface names that should not be translated.
         */
        {ErrorMsg.JAXP_UNKNOWN_SOURCE_ERR,
        "XSLTC는 StreamSource, SAXSource 및 DOMSource만 지원합니다."},

        /*
         * Note to translators:  "Source" is a Java class name that should not
         * be translated.  The substitution text is the name of Java method.
         */
        {ErrorMsg.JAXP_NO_SOURCE_ERR,
        "''{0}''(으)로 전달된 Source 객체에 콘텐츠가 없습니다."},

        /*
         * Note to translators:  The message indicates that XSLTC failed to
         * compile the stylesheet into a translet (class file).
         */
        {ErrorMsg.JAXP_COMPILE_ERR,
        "스타일시트를 컴파일할 수 없습니다."},

        /*
         * Note to translators:  "TransformerFactory" is a class name.  In this
         * context, an attribute is a property or setting of the
         * TransformerFactory object.  The substitution text is the name of the
         * unrecognised attribute.  The method used to retrieve the attribute is
         * "getAttribute", so it's not clear whether it would be best to
         * translate the term "attribute".
         */
        {ErrorMsg.JAXP_INVALID_ATTR_ERR,
        "TransformerFactory에서 ''{0}'' 속성을 인식하지 못했습니다."},

        {ErrorMsg.JAXP_INVALID_ATTR_VALUE_ERR,
        "''{0}'' 속성에 대해 올바르지 않은 값이 지정되었습니다."},

        /*
         * Note to translators:  "setResult()" and "startDocument()" are Java
         * method names that should not be translated.
         */
        {ErrorMsg.JAXP_SET_RESULT_ERR,
        "setResult()는 startDocument() 앞에 호출되어야 합니다."},

        /*
         * Note to translators:  "Transformer" is a Java interface name that
         * should not be translated.  A Transformer object should contained a
         * reference to a translet object in order to be used for
         * transformations; this message is produced if that requirement is not
         * met.
         */
        {ErrorMsg.JAXP_NO_TRANSLET_ERR,
        "Transformer에 캡슐화된 translet 객체가 없습니다."},

        /*
         * Note to translators:  The XML document that results from a
         * transformation needs to be sent to an output handler object; this
         * message is produced if that requirement is not met.
         */
        {ErrorMsg.JAXP_NO_HANDLER_ERR,
        "변환 결과에 대해 정의된 출력 처리기가 없습니다."},

        /*
         * Note to translators:  "Result" is a Java interface name in this
         * context.  The substitution text is a method name.
         */
        {ErrorMsg.JAXP_NO_RESULT_ERR,
        "''{0}''(으)로 전달된 Result 객체가 부적합합니다."},

        /*
         * Note to translators:  "Transformer" is a Java interface name.  The
         * user's program attempted to access an unrecognized property with the
         * name specified in the substitution text.  The method used to retrieve
         * the property is "getOutputProperty", so it's not clear whether it
         * would be best to translate the term "property".
         */
        {ErrorMsg.JAXP_UNKNOWN_PROP_ERR,
        "부적합한 Transformer 속성 ''{0}''에 액세스하려고 시도하는 중입니다."},

        /*
         * Note to translators:  SAX2DOM is the name of a Java class that should
         * not be translated.  This is an adapter in the sense that it takes a
         * DOM object and converts it to something that uses the SAX API.
         */
        {ErrorMsg.SAX2DOM_ADAPTER_ERR,
        "SAX2DOM 어댑터를 생성할 수 없음: ''{0}''."},

        /*
         * Note to translators:  "XSLTCSource.build()" is a Java method name.
         * "systemId" is an XML term that is short for "system identification".
         */
        {ErrorMsg.XSLTC_SOURCE_ERR,
        "systemId를 설정하지 않은 상태로 XSLTCSource.build()가 호출되었습니다."},

        { ErrorMsg.ER_RESULT_NULL,
            "결과는 널이 아니어야 합니다."},

        /*
         * Note to translators:  This message indicates that the value argument
         * of setParameter must be a valid Java Object.
         */
        {ErrorMsg.JAXP_INVALID_SET_PARAM_VALUE,
        "{0} 매개변수의 값은 적합한 Java 객체여야 합니다."},


        {ErrorMsg.COMPILE_STDIN_ERR,
        "-i 옵션은 -o 옵션과 함께 사용해야 합니다."},


        /*
         * Note to translators:  This message contains usage information for a
         * means of invoking XSLTC from the command-line.  The message is
         * formatted for presentation in English.  The strings <output>,
         * <directory>, etc. indicate user-specified argument values, and can
         * be translated - the argument <package> refers to a Java package, so
         * it should be handled in the same way the term is handled for JDK
         * documentation.
         */
        {ErrorMsg.COMPILE_USAGE_STR,
        "사용법\n   java com.sun.org.apache.xalan.internal.xsltc.cmdline.Compile [-o <output>]\n      [-d <directory>] [-j <jarfile>] [-p <package>]\n      [-n] [-x] [-u] [-v] [-h] { <stylesheet> | -i }\n\n옵션\n   -o <output>    생성된 translet에 <output> 이름을\n                  지정합니다. 기본적으로 translet 이름은\n                  <stylesheet> 이름에서 파생됩니다. 여러 스타일시트를\n                  컴파일하는 경우 이 옵션은 무시됩니다.\n   -d <directory> translet에 대한 대상 디렉토리를 지정합니다.\n   -j <jarfile>   translet 클래스를 <jarfile>이라는 이름이 지정된 jar 파일에\n                  패키지화합니다.\n   -p <package>   생성된 모든 translet 클래스에 대해 패키지 이름 접두어를\n                  지정합니다.\n   -n             템플리트 인라인을 사용으로 설정합니다. 일반적으로 기본 동작을\n                  사용하는 것이 좋습니다.\n   -x             추가 디버깅 메시지 출력을 설정합니다.\n   -u             <stylesheet> 인수를 URL로 해석합니다.\n   -i             컴파일러가 stdin에서 스타일시트를 강제로 읽도록 합니다.\n   -v             컴파일러의 버전을 인쇄합니다.\n   -h             이 사용법 지침을 인쇄합니다.\n"},

        /*
         * Note to translators:  This message contains usage information for a
         * means of invoking XSLTC from the command-line.  The message is
         * formatted for presentation in English.  The strings <jarfile>,
         * <document>, etc. indicate user-specified argument values, and can
         * be translated - the argument <class> refers to a Java class, so it
         * should be handled in the same way the term is handled for JDK
         * documentation.
         */
        {ErrorMsg.TRANSFORM_USAGE_STR,
        "사용법 \n   java com.sun.org.apache.xalan.internal.xsltc.cmdline.Transform [-j <jarfile>]\n      [-x] [-n <iterations>] {-u <document_url> | <document>}\n      <class> [<param1>=<value1> ...]\n\n   translet <class>를 사용하여 <document>로 지정된 XML 문서를 \n   변환합니다. translet <class>는 \n   사용자의 CLASSPATH 또는 선택적으로 지정된 <jarfile>에 있습니다.\n옵션\n   -j <jarfile>    translet을 로드해 올 jarfile을 지정합니다.\n   -x              추가 디버깅 메시지 출력을 설정합니다.\n   -n <iterations> 변환을 <iterations>회 실행하고\n                   프로파일 작성 정보를 표시합니다.\n   -u <document_url> XML 입력 문서를 URL로 지정합니다.\n"},



        /*
         * Note to translators:  "<xsl:sort>", "<xsl:for-each>" and
         * "<xsl:apply-templates>" are keywords that should not be translated.
         * The message indicates that an xsl:sort element must be a child of
         * one of the other kinds of elements mentioned.
         */
        {ErrorMsg.STRAY_SORT_ERR,
        "<xsl:sort>는 <xsl:for-each> 또는 <xsl:apply-templates>에서만 사용할 수 있습니다."},

        /*
         * Note to translators:  The message indicates that the encoding
         * requested for the output document was on that requires support that
         * is not available from the Java Virtual Machine being used to execute
         * the program.
         */
        {ErrorMsg.UNSUPPORTED_ENCODING,
        "이 JVM에서는 출력 인코딩 ''{0}''이(가) 지원되지 않습니다."},

        /*
         * Note to translators:  The message indicates that the XPath expression
         * named in the substitution text was not well formed syntactically.
         */
        {ErrorMsg.SYNTAX_ERR,
        "''{0}''에 구문 오류가 있습니다."},

        /*
         * Note to translators:  The substitution text is the name of a Java
         * class.  The term "constructor" here is the Java term.  The message is
         * displayed if XSLTC could not find a constructor for the specified
         * class.
         */
        {ErrorMsg.CONSTRUCTOR_NOT_FOUND,
        "외부 constructor ''{0}''을(를) 찾을 수 없습니다."},

        /*
         * Note to translators:  "static" is the Java keyword.  The substitution
         * text is the name of a function.  The first argument of that function
         * is not of the required type.
         */
        {ErrorMsg.NO_JAVA_FUNCT_THIS_REF,
        "비static Java 함수 ''{0}''에 대한 첫번째 인수는 적합한 객체 참조가 아닙니다."},

        /*
         * Note to translators:  An XPath expression was not of the type
         * required in a particular context.  The substitution text is the
         * expression that was in error.
         */
        {ErrorMsg.TYPE_CHECK_ERR,
        "''{0}'' 표현식의 유형을 확인하는 중 오류가 발생했습니다."},

        /*
         * Note to translators:  An XPath expression was not of the type
         * required in a particular context.  However, the location of the
         * problematic expression is unknown.
         */
        {ErrorMsg.TYPE_CHECK_UNK_LOC_ERR,
        "알 수 없는 위치에서 표현식의 유형을 확인하는 중 오류가 발생했습니다."},

        /*
         * Note to translators:  The substitution text is the name of a command-
         * line option that was not recognized.
         */
        {ErrorMsg.ILLEGAL_CMDLINE_OPTION_ERR,
        "명령행 옵션 ''{0}''이(가) 부적합합니다."},

        /*
         * Note to translators:  The substitution text is the name of a command-
         * line option.
         */
        {ErrorMsg.CMDLINE_OPT_MISSING_ARG_ERR,
        "명령행 옵션 ''{0}''에 필수 인수가 누락되었습니다."},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text contains two error
         * messages.  The spacing before the second substitution text indents
         * it the same amount as the first in English.
         */
        {ErrorMsg.WARNING_PLUS_WRAPPED_MSG,
        "WARNING:  ''{0}''\n       :{1}"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text is an error message.
         */
        {ErrorMsg.WARNING_MSG,
        "WARNING:  ''{0}''"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text contains two error
         * messages.  The spacing before the second substitution text indents
         * it the same amount as the first in English.
         */
        {ErrorMsg.FATAL_ERR_PLUS_WRAPPED_MSG,
        "FATAL ERROR:  ''{0}''\n           :{1}"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text is an error message.
         */
        {ErrorMsg.FATAL_ERR_MSG,
        "FATAL ERROR:  ''{0}''"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text contains two error
         * messages.  The spacing before the second substitution text indents
         * it the same amount as the first in English.
         */
        {ErrorMsg.ERROR_PLUS_WRAPPED_MSG,
        "ERROR:  ''{0}''\n     :{1}"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text is an error message.
         */
        {ErrorMsg.ERROR_MSG,
        "ERROR:  ''{0}''"},

        /*
         * Note to translators:  The substitution text is the name of a class.
         */
        {ErrorMsg.TRANSFORM_WITH_TRANSLET_STR,
        "translet ''{0}''을(를) 사용하여 변환하십시오. "},

        /*
         * Note to translators:  The first substitution is the name of a class,
         * while the second substitution is the name of a jar file.
         */
        {ErrorMsg.TRANSFORM_WITH_JAR_STR,
        "jar 파일 ''{1}''의 translet ''{0}''을(를) 사용하여 변환하십시오."},

        /*
         * Note to translators:  "TransformerFactory" is the name of a Java
         * interface and must not be translated.  The substitution text is
         * the name of the class that could not be instantiated.
         */
        {ErrorMsg.COULD_NOT_CREATE_TRANS_FACT,
        "TransformerFactory 클래스 ''{0}''의 인스턴스를 생성할 수 없습니다."},

        /*
         * Note to translators:  This message is produced when the user
         * specified a name for the translet class that contains characters
         * that are not permitted in a Java class name.  The substitution
         * text "{0}" specifies the name the user requested, while "{1}"
         * specifies the name the processor used instead.
         */
        {ErrorMsg.TRANSLET_NAME_JAVA_CONFLICT,
         "''{0}'' 이름에는 Java 클래스 이름에 허용되지 않는 문자가 포함되어 있어 이 이름을 translet 클래스의 이름으로 사용할 수 없습니다. 대신 ''{1}'' 이름이 사용되었습니다."},

        /*
         * Note to translators:  The following message is used as a header.
         * All the error messages are collected together and displayed beneath
         * this message.
         */
        {ErrorMsg.COMPILER_ERROR_KEY,
        "컴파일러 오류:"},

        /*
         * Note to translators:  The following message is used as a header.
         * All the warning messages are collected together and displayed
         * beneath this message.
         */
        {ErrorMsg.COMPILER_WARNING_KEY,
        "컴파일러 경고:"},

        /*
         * Note to translators:  The following message is used as a header.
         * All the error messages that are produced when the stylesheet is
         * applied to an input document are collected together and displayed
         * beneath this message.  A 'translet' is the compiled form of a
         * stylesheet (see above).
         */
        {ErrorMsg.RUNTIME_ERROR_KEY,
        "Translet 오류:"},

        /*
         * Note to translators:  An attribute whose value is constrained to
         * be a "QName" or a list of "QNames" had a value that was incorrect.
         * 'QName' is an XML syntactic term that must not be translated.  The
         * substitution text contains the actual value of the attribute.
         */
        {ErrorMsg.INVALID_QNAME_ERR,
        "값이 QName 또는 공백으로 구분된 QName 목록이어야 하는 속성의 값이 ''{0}''입니다."},

        /*
         * Note to translators:  An attribute whose value is required to
         * be an "NCName".
         * 'NCName' is an XML syntactic term that must not be translated.  The
         * substitution text contains the actual value of the attribute.
         */
        {ErrorMsg.INVALID_NCNAME_ERR,
        "값이 NCName이어야 하는 속성의 값이 ''{0}''입니다."},

        /*
         * Note to translators:  An attribute with an incorrect value was
         * encountered.  The permitted value is one of the literal values
         * "xml", "html" or "text"; it is also permitted to have the form of
         * a QName that is not also an NCName.  The terms "method",
         * "xsl:output", "xml", "html" and "text" are keywords that must not
         * be translated.  The term "qname-but-not-ncname" is an XML syntactic
         * term.  The substitution text contains the actual value of the
         * attribute.
         */
        {ErrorMsg.INVALID_METHOD_IN_OUTPUT,
        "<xsl:output> 요소에 대한 method 속성의 값이 ''{0}''입니다. 값은 ''xml'', ''html'', ''text'' 또는 qname-but-not-ncname 중 하나여야 합니다."},

        {ErrorMsg.JAXP_GET_FEATURE_NULL_NAME,
        "기능 이름은 TransformerFactory.getFeature(문자열 이름)에서 널일 수 없습니다."},

        {ErrorMsg.JAXP_SET_FEATURE_NULL_NAME,
        "기능 이름은 TransformerFactory.setFeature(문자열 이름, 부울 값)에서 널일 수 없습니다."},

        {ErrorMsg.JAXP_UNSUPPORTED_FEATURE,
        "이 TransformerFactory에서 ''{0}'' 기능을 설정할 수 없습니다."},

        {ErrorMsg.JAXP_SECUREPROCESSING_FEATURE,
        "FEATURE_SECURE_PROCESSING: 보안 관리자가 있을 경우 기능을 false로 설정할 수 없습니다."},

        /*
         * Note to translators:  This message describes an internal error in the
         * processor.  The term "byte code" is a Java technical term for the
         * executable code in a Java method, and "try-catch-finally block"
         * refers to the Java keywords with those names.  "Outlined" is a
         * technical term internal to XSLTC and should not be translated.
         */
        {ErrorMsg.OUTLINE_ERR_TRY_CATCH,
         "내부 XSLTC 오류: 생성된 바이트 코드가 try-catch-finally 블록을 포함하므로 outlined 처리할 수 없습니다."},

        /*
         * Note to translators:  This message describes an internal error in the
         * processor.  The terms "OutlineableChunkStart" and
         * "OutlineableChunkEnd" are the names of classes internal to XSLTC and
         * should not be translated.  The message indicates that for every
         * "start" there must be a corresponding "end", and vice versa, and
         * that if one of a pair of "start" and "end" appears between another
         * pair of corresponding "start" and "end", then the other half of the
         * pair must also be between that same enclosing pair.
         */
        {ErrorMsg.OUTLINE_ERR_UNBALANCED_MARKERS,
         "내부 XSLTC 오류: OutlineableChunkStart 및 OutlineableChunkEnd 표시자의 짝이 맞아야 하고 올바르게 중첩되어야 합니다."},

        /*
         * Note to translators:  This message describes an internal error in the
         * processor.  The term "byte code" is a Java technical term for the
         * executable code in a Java method.  The "method" that is being
         * referred to is a Java method in a translet that XSLTC is generating
         * in processing a stylesheet.  The "instruction" that is being
         * referred to is one of the instrutions in the Java byte code in that
         * method.  "Outlined" is a technical term internal to XSLTC and
         * should not be translated.
         */
        {ErrorMsg.OUTLINE_ERR_DELETED_TARGET,
         "내부 XSLTC 오류: outlined 처리된 바이트 코드 블록에 속한 명령이 여전히 원래 메소드에서 참조됩니다."
        },


        /*
         * Note to translators:  This message describes an internal error in the
         * processor.  The "method" that is being referred to is a Java method
         * in a translet that XSLTC is generating.
         *
         */
        {ErrorMsg.OUTLINE_ERR_METHOD_TOO_BIG,
         "내부 XSLTC 오류: translet의 메소드가 Java Virtual Machine의 메소드 길이 제한인 64KB를 초과합니다. 대개 스타일시트의 템플리트가 매우 크기 때문에 발생합니다. 더 작은 템플리트를 사용하도록 스타일시트를 재구성해 보십시오."
        },

    };

    }
}
