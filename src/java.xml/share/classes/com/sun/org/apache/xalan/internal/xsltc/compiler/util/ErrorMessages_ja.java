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
 * @LastModified: Dec 2024
 */
public class ErrorMessages_ja extends ListResourceBundle {

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
        "同じファイルに複数のスタイルシートが定義されています。"},

        /*
         * Note to translators:  The substitution text is the name of a
         * template.  The same name was used on two different templates in the
         * same stylesheet.
         */
        {ErrorMsg.TEMPLATE_REDEF_ERR,
        "テンプレート''{0}''はこのスタイルシート内ですでに定義されています。"},


        /*
         * Note to translators:  The substitution text is the name of a
         * template.  A reference to the template name was encountered, but the
         * template is undefined.
         */
        {ErrorMsg.TEMPLATE_UNDEF_ERR,
        "テンプレート''{0}''はこのスタイルシート内で定義されていません。"},

        /*
         * Note to translators:  The substitution text is the name of a variable
         * that was defined more than once.
         */
        {ErrorMsg.VARIABLE_REDEF_ERR,
        "変数''{0}''は同じスコープ内で複数定義されています。"},

        /*
         * Note to translators:  The substitution text is the name of a variable
         * or parameter.  A reference to the variable or parameter was found,
         * but it was never defined.
         */
        {ErrorMsg.VARIABLE_UNDEF_ERR,
        "変数またはパラメータ''{0}''が未定義です。"},

        /*
         * Note to translators:  The word "class" here refers to a Java class.
         * Processing the stylesheet required a class to be loaded, but it could
         * not be found.  The substitution text is the name of the class.
         */
        {ErrorMsg.CLASS_NOT_FOUND_ERR,
        "クラス''{0}''が見つかりません。"},

        /*
         * Note to translators:  The word "method" here refers to a Java method.
         * Processing the stylesheet required a reference to the method named by
         * the substitution text, but it could not be found.  "public" is the
         * Java keyword.
         */
        {ErrorMsg.METHOD_NOT_FOUND_ERR,
        "外部メソッド''{0}''が見つかりません(publicである必要があります)。"},

        /*
         * Note to translators:  The word "method" here refers to a Java method.
         * Processing the stylesheet required a reference to the method named by
         * the substitution text, but no method with the required types of
         * arguments or return type could be found.
         */
        {ErrorMsg.ARGUMENT_CONVERSION_ERR,
        "メソッド''{0}''の呼出しの引数タイプまたは戻り型を変換できません"},

        /*
         * Note to translators:  The file or URI named in the substitution text
         * is missing.
         */
        {ErrorMsg.FILE_NOT_FOUND_ERR,
        "ファイルまたはURI ''{0}''が見つかりません。"},

        /*
         * Note to translators:  This message is displayed when the URI
         * mentioned in the substitution text is not well-formed syntactically.
         */
        {ErrorMsg.INVALID_URI_ERR,
        "URI ''{0}''が無効です。"},

        /*
         * Note to translators:  This message is displayed when the URI
         * mentioned in the substitution text is not well-formed syntactically.
         */
        {ErrorMsg.CATALOG_EXCEPTION,
        "JAXP08090001: CatalogResolverはカタログ\"{0}\"で有効ですが、CatalogExceptionが返されます。"},

        /*
         * Note to translators:  The file or URI named in the substitution text
         * exists but could not be opened.
         */
        {ErrorMsg.FILE_ACCESS_ERR,
        "ファイルまたはURI ''{0}''を開くことができません。"},

        /*
         * Note to translators: <xsl:stylesheet> and <xsl:transform> are
         * keywords that should not be translated.
         */
        {ErrorMsg.MISSING_ROOT_ERR,
        "<xsl:stylesheet>または<xsl:transform>の要素がありません。"},

        /*
         * Note to translators:  The stylesheet contained a reference to a
         * namespace prefix that was undefined.  The value of the substitution
         * text is the name of the prefix.
         */
        {ErrorMsg.NAMESPACE_UNDEF_ERR,
        "ネームスペースの接頭辞''{0}''は宣言されていません。"},

        /*
         * Note to translators:  The Java function named in the stylesheet could
         * not be found.
         */
        {ErrorMsg.FUNCTION_RESOLVE_ERR,
        "関数''{0}''の呼出しを解決できません。"},

        /*
         * Note to translators:  The substitution text is the name of a
         * function.  A literal string here means a constant string value.
         */
        {ErrorMsg.NEED_LITERAL_ERR,
        "''{0}''への引数はリテラル文字列である必要があります。"},

        /*
         * Note to translators:  This message indicates there was a syntactic
         * error in the form of an XPath expression.  The substitution text is
         * the expression.
         */
        {ErrorMsg.XPATH_PARSER_ERR,
        "XPath式''{0}''の解析中にエラーが発生しました。"},

        /*
         * Note to translators:  An element in the stylesheet requires a
         * particular attribute named by the substitution text, but that
         * attribute was not specified in the stylesheet.
         */
        {ErrorMsg.REQUIRED_ATTR_ERR,
        "必須属性''{0}''がありません。"},

        /*
         * Note to translators:  This message indicates that a character not
         * permitted in an XPath expression was encountered.  The substitution
         * text is the offending character.
         */
        {ErrorMsg.ILLEGAL_CHAR_ERR,
        "XPath式の文字''{0}''は無効です。"},

        /*
         * Note to translators:  A processing instruction is a mark-up item in
         * an XML document that request some behaviour of an XML processor.  The
         * form of the name of was invalid in this case, and the substitution
         * text is the name.
         */
        {ErrorMsg.ILLEGAL_PI_ERR,
        "処理命令の名前''{0}''は無効です。"},

        /*
         * Note to translators:  This message is reported if the stylesheet
         * being processed attempted to construct an XML document with an
         * attribute in a place other than on an element.  The substitution text
         * specifies the name of the attribute.
         */
        {ErrorMsg.STRAY_ATTRIBUTE_ERR,
        "属性''{0}''が要素の外側にあります。"},

        /*
         * Note to translators:  An attribute that wasn't recognized was
         * specified on an element in the stylesheet.  The attribute is named
         * by the substitution
         * text.
         */
        {ErrorMsg.ILLEGAL_ATTRIBUTE_ERR,
        "不正な属性''{0}''です。"},

        /*
         * Note to translators:  "import" and "include" are keywords that should
         * not be translated.  This messages indicates that the stylesheet
         * named in the substitution text imported or included itself either
         * directly or indirectly.
         */
        {ErrorMsg.CIRCULAR_INCLUDE_ERR,
        "インポートまたはインクルードが循環しています。スタイルシート''{0}''はすでにロードされています。"},

        /*
         * Note to translators:  "xsl:import" and "xsl:include" are keywords that
         * should not be translated.
         */
        {ErrorMsg.IMPORT_PRECEDE_OTHERS_ERR,
        "xsl:import要素の子は、xsl:stylesheet要素の他のすべての要素の子(すべてのxsl:include要素の子を含む)より前に置く必要があります。"},

        /*
         * Note to translators:  A result-tree fragment is a portion of a
         * resulting XML document represented as a tree.  "<xsl:sort>" is a
         * keyword and should not be translated.
         */
        {ErrorMsg.RESULT_TREE_SORT_ERR,
        "結果ツリー・フラグメントはソートできません(<xsl:sort>要素は無視されます)。結果ツリーを作成するときにノードをソートする必要があります。"},

        /*
         * Note to translators:  A name can be given to a particular style to be
         * used to format decimal values.  The substitution text gives the name
         * of such a style for which more than one declaration was encountered.
         */
        {ErrorMsg.SYMBOLS_REDEF_ERR,
        "10進数フォーマット''{0}''はすでに定義されています。"},

        /*
         * Note to translators:  The stylesheet version named in the
         * substitution text is not supported.
         */
        {ErrorMsg.XSL_VERSION_ERR,
        "XSLバージョン''{0}''はXSLTCによってサポートされていません。"},

        /*
         * Note to translators:  The definitions of one or more variables or
         * parameters depend on one another.
         */
        {ErrorMsg.CIRCULAR_VARIABLE_ERR,
        "''{0}''内の変数参照またはパラメータ参照が循環しています。"},

        /*
         * Note to translators:  The operator in an expresion with two operands was
         * not recognized.
         */
        {ErrorMsg.ILLEGAL_BINARY_OP_ERR,
        "2進数の式に対する不明な演算子です。"},

        /*
         * Note to translators:  This message is produced if a reference to a
         * function has too many or too few arguments.
         */
        {ErrorMsg.ILLEGAL_ARG_ERR,
        "関数呼出しの引数が不正です。"},

        /*
         * Note to translators:  "document()" is the name of function and must
         * not be translated.  A node-set is a set of the nodes in the tree
         * representation of an XML document.
         */
        {ErrorMsg.DOCUMENT_ARG_ERR,
        "document()関数の2番目の引数はノードセットである必要があります。"},

        /*
         * Note to translators:  "<xsl:when>" and "<xsl:choose>" are keywords
         * and should not be translated.  This message describes a syntax error
         * in the stylesheet.
         */
        {ErrorMsg.MISSING_WHEN_ERR,
        "<xsl:choose>内には少なくとも1つの<xsl:when>要素が必要です。"},

        /*
         * Note to translators:  "<xsl:otherwise>" and "<xsl:choose>" are
         * keywords and should not be translated.  This message describes a
         * syntax error in the stylesheet.
         */
        {ErrorMsg.MULTIPLE_OTHERWISE_ERR,
        "<xsl:choose>内では1つの<xsl:otherwise>要素のみが許可されています。"},

        /*
         * Note to translators:  "<xsl:otherwise>" and "<xsl:choose>" are
         * keywords and should not be translated.  This message describes a
         * syntax error in the stylesheet.
         */
        {ErrorMsg.STRAY_OTHERWISE_ERR,
        "<xsl:otherwise>は<xsl:choose>内でのみ使用できます。"},

        /*
         * Note to translators:  "<xsl:when>" and "<xsl:choose>" are keywords
         * and should not be translated.  This message describes a syntax error
         * in the stylesheet.
         */
        {ErrorMsg.STRAY_WHEN_ERR,
        "<xsl:when>は<xsl:choose>内でのみ使用できます。"},

        /*
         * Note to translators:  "<xsl:when>", "<xsl:otherwise>" and
         * "<xsl:choose>" are keywords and should not be translated.  This
         * message describes a syntax error in the stylesheet.
         */
        {ErrorMsg.WHEN_ELEMENT_ERR,
        "<xsl:choose>内では<xsl:when>と<xsl:otherwise>の要素のみが許可されます。"},

        /*
         * Note to translators:  "<xsl:attribute-set>" and "name" are keywords
         * that should not be translated.
         */
        {ErrorMsg.UNNAMED_ATTRIBSET_ERR,
        "<xsl:attribute-set>に'name'属性がありません。"},

        /*
         * Note to translators:  An element in the stylesheet contained an
         * element of a type that it was not permitted to contain.
         */
        {ErrorMsg.ILLEGAL_CHILD_ERR,
        "子要素が不正です。"},

        /*
         * Note to translators:  The stylesheet tried to create an element with
         * a name that was not a valid XML name.  The substitution text contains
         * the name.
         */
        {ErrorMsg.ILLEGAL_ELEM_NAME_ERR,
        "要素''{0}''を呼び出すことはできません"},

        /*
         * Note to translators:  The stylesheet tried to create an attribute
         * with a name that was not a valid XML name.  The substitution text
         * contains the name.
         */
        {ErrorMsg.ILLEGAL_ATTR_NAME_ERR,
        "属性''{0}''を呼び出すことはできません"},

        /*
         * Note to translators:  The children of the outermost element of a
         * stylesheet are referred to as top-level elements.  No text should
         * occur within that outermost element unless it is within a top-level
         * element.  This message indicates that that constraint was violated.
         * "<xsl:stylesheet>" is a keyword that should not be translated.
         */
        {ErrorMsg.ILLEGAL_TEXT_NODE_ERR,
        "テキスト・データはトップレベルの<xsl:stylesheet>要素の外側にあります。"},

        /*
         * Note to translators:  JAXP is an acronym for the Java API for XML
         * Processing.  This message indicates that the XML parser provided to
         * XSLTC to process the XML input document had a configuration problem.
         */
        {ErrorMsg.SAX_PARSER_CONFIG_ERR,
        "JAXPパーサーが正しく構成されていません"},

        /*
         * Note to translators:  The substitution text names the internal error
         * encountered.
         */
        {ErrorMsg.INTERNAL_ERR,
        "リカバリ不能なXSLTC内部エラー: ''{0}''"},

        /*
         * Note to translators:  The stylesheet contained an element that was
         * not recognized as part of the XSL syntax.  The substitution text
         * gives the element name.
         */
        {ErrorMsg.UNSUPPORTED_XSL_ERR,
        "XSL要素''{0}''はサポートされていません。"},

        /*
         * Note to translators:  The stylesheet referred to an extension to the
         * XSL syntax and indicated that it was defined by XSLTC, but XSTLC does
         * not recognized the particular extension named.  The substitution text
         * gives the extension name.
         */
        {ErrorMsg.UNSUPPORTED_EXT_ERR,
        "XSLTC拡張''{0}''は認識されません。"},

        /*
         * Note to translators:  The XML document given to XSLTC as a stylesheet
         * was not, in fact, a stylesheet.  XSLTC is able to detect that in this
         * case because the outermost element in the stylesheet has to be
         * declared with respect to the XSL namespace URI, but no declaration
         * for that namespace was seen.
         */
        {ErrorMsg.MISSING_XSLT_URI_ERR,
        "入力ドキュメントはスタイルシートではありません(XSLのネームスペースはルート要素内で宣言されていません)。"},

        /*
         * Note to translators:  XSLTC could not find the stylesheet document
         * with the name specified by the substitution text.
         */
        {ErrorMsg.MISSING_XSLT_TARGET_ERR,
        "スタイルシート・ターゲット''{0}''が見つかりませんでした。"},

        /*
         * Note to translators:  access to the stylesheet target is denied
         */
        {ErrorMsg.ACCESSING_XSLT_TARGET_ERR,
        "accessExternalStylesheetプロパティで設定された制限により''{1}''アクセスが許可されていないため、スタイルシート・ターゲット''{0}''を読み取れませんでした。"},

        /*
         * Note to translators:  This message represents an internal error in
         * condition in XSLTC.  The substitution text is the class name in XSLTC
         * that is missing some functionality.
         */
        {ErrorMsg.NOT_IMPLEMENTED_ERR,
        "''{0}''が実装されていません。"},

        /*
         * Note to translators:  The XML document given to XSLTC as a stylesheet
         * was not, in fact, a stylesheet.
         */
        {ErrorMsg.NOT_STYLESHEET_ERR,
        "入力ドキュメントにXSLスタイルシートが含まれていません。"},

        /*
         * Note to translators:  The element named in the substitution text was
         * encountered in the stylesheet but is not recognized.
         */
        {ErrorMsg.ELEMENT_PARSE_ERR,
        "要素''{0}''を解析できませんでした"},

        /*
         * Note to translators:  "use", "<key>", "node", "node-set", "string"
         * and "number" are keywords in this context and should not be
         * translated.  This message indicates that the value of the "use"
         * attribute was not one of the permitted values.
         */
        {ErrorMsg.KEY_USE_ATTR_ERR,
        "<key>のuse属性は、ノード、ノードセット、文字列または数値である必要があります。"},

        /*
         * Note to translators:  An XML document can specify the version of the
         * XML specification to which it adheres.  This message indicates that
         * the version specified for the output document was not valid.
         */
        {ErrorMsg.OUTPUT_VERSION_ERR,
        "出力XMLドキュメントのバージョンは1.0である必要があります"},

        /*
         * Note to translators:  The operator in a comparison operation was
         * not recognized.
         */
        {ErrorMsg.ILLEGAL_RELAT_OP_ERR,
        "関係式の不明な演算子です"},

        /*
         * Note to translators:  An attribute set defines as a set of XML
         * attributes that can be added to an element in the output XML document
         * as a group.  This message is reported if the name specified was not
         * used to declare an attribute set.  The substitution text is the name
         * that is in error.
         */
        {ErrorMsg.ATTRIBSET_UNDEF_ERR,
        "存在しない属性セット''{0}''を使用しようとしました。"},

        /*
         * Note to translators:  The term "attribute value template" is a term
         * defined by XSLT which describes the value of an attribute that is
         * determined by an XPath expression.  The message indicates that the
         * expression was syntactically incorrect; the substitution text
         * contains the expression that was in error.
         */
        {ErrorMsg.ATTR_VAL_TEMPLATE_ERR,
        "属性値テンプレート''{0}''を解析できません。"},

        /*
         * Note to translators:  ???
         */
        {ErrorMsg.UNKNOWN_SIG_TYPE_ERR,
        "クラス''{0}''の署名に不明なデータ型があります。"},

        /*
         * Note to translators:  The substitution text refers to data types.
         * The message is displayed if a value in a particular context needs to
         * be converted to type {1}, but that's not possible for a value of
         * type {0}.
         */
        {ErrorMsg.DATA_CONVERSION_ERR,
        "データ型''{0}''を''{1}''に変換できません。"},

        /*
         * Note to translators:  property name "jdk.xml.enableExtensionFunctions"
         * and value "true" should not be translated.
         */
        {ErrorMsg.UNSUPPORTED_EXT_FUNC_ERR,
        "セキュア処理機能またはプロパティ''jdk.xml.enableExtensionFunctions''によって拡張関数が無効になっているとき、拡張関数''{0}''の使用は許可されません。拡張関数を有効にするには、''jdk.xml.enableExtensionFunctions''を''true''に設定します。"},
        /*
         * Note to translators:  "Templates" is a Java class name that should
         * not be translated.
         */
        {ErrorMsg.NO_TRANSLET_CLASS_ERR,
        "このテンプレートには有効なtransletクラス定義が含まれていません。"},

        /*
         * Note to translators:  "Templates" is a Java class name that should
         * not be translated.
         */
        {ErrorMsg.NO_MAIN_TRANSLET_ERR,
        "このテンプレートには名前''{0}''を持つクラスが含まれていません。"},

        /*
         * Note to translators:  The substitution text is the name of a class.
         */
        {ErrorMsg.TRANSLET_CLASS_ERR,
        "transletクラス''{0}''をロードできませんでした。"},

        {ErrorMsg.TRANSLET_OBJECT_ERR,
        "Transletクラスがロードされましたが、transletインスタンスを作成できません。"},

        /*
         * Note to translators:  "ErrorListener" is a Java interface name that
         * should not be translated.  The message indicates that the user tried
         * to set an ErrorListener object on object of the class named in the
         * substitution text with "null" Java value.
         */
        {ErrorMsg.ERROR_LISTENER_NULL_ERR,
        "''{0}''のErrorListenerをnullに設定しようとしました"},

        /*
         * Note to translators:  StreamSource, SAXSource and DOMSource are Java
         * interface names that should not be translated.
         */
        {ErrorMsg.JAXP_UNKNOWN_SOURCE_ERR,
        "StreamSource、SAXSourceおよびDOMSourceのみがXSLTCによってサポートされています"},

        /*
         * Note to translators:  "Source" is a Java class name that should not
         * be translated.  The substitution text is the name of Java method.
         */
        {ErrorMsg.JAXP_NO_SOURCE_ERR,
        "''{0}''に渡されたソース・オブジェクトにコンテンツがありません。"},

        /*
         * Note to translators:  The message indicates that XSLTC failed to
         * compile the stylesheet into a translet (class file).
         */
        {ErrorMsg.JAXP_COMPILE_ERR,
        "スタイルシートをコンパイルできませんでした"},

        /*
         * Note to translators:  "TransformerFactory" is a class name.  In this
         * context, an attribute is a property or setting of the
         * TransformerFactory object.  The substitution text is the name of the
         * unrecognised attribute.  The method used to retrieve the attribute is
         * "getAttribute", so it's not clear whether it would be best to
         * translate the term "attribute".
         */
        {ErrorMsg.JAXP_INVALID_ATTR_ERR,
        "TransformerFactoryは属性''{0}''を認識しません。"},

        {ErrorMsg.JAXP_INVALID_ATTR_VALUE_ERR,
        "''{0}''属性に指定された値が正しくありません。"},

        /*
         * Note to translators:  "setResult()" and "startDocument()" are Java
         * method names that should not be translated.
         */
        {ErrorMsg.JAXP_SET_RESULT_ERR,
        "setResult()はstartDocument()よりも前に呼び出す必要があります。"},

        /*
         * Note to translators:  "Transformer" is a Java interface name that
         * should not be translated.  A Transformer object should contained a
         * reference to a translet object in order to be used for
         * transformations; this message is produced if that requirement is not
         * met.
         */
        {ErrorMsg.JAXP_NO_TRANSLET_ERR,
        "トランスフォーマにはカプセル化されたtransletオブジェクトがありません。"},

        /*
         * Note to translators:  The XML document that results from a
         * transformation needs to be sent to an output handler object; this
         * message is produced if that requirement is not met.
         */
        {ErrorMsg.JAXP_NO_HANDLER_ERR,
        "変換結果に対して定義済の出力ハンドラがありません。"},

        /*
         * Note to translators:  "Result" is a Java interface name in this
         * context.  The substitution text is a method name.
         */
        {ErrorMsg.JAXP_NO_RESULT_ERR,
        "''{0}''に渡された結果オブジェクトは無効です。"},

        /*
         * Note to translators:  "Transformer" is a Java interface name.  The
         * user's program attempted to access an unrecognized property with the
         * name specified in the substitution text.  The method used to retrieve
         * the property is "getOutputProperty", so it's not clear whether it
         * would be best to translate the term "property".
         */
        {ErrorMsg.JAXP_UNKNOWN_PROP_ERR,
        "無効なトランスフォーマ・プロパティ''{0}''にアクセスしようとしました。"},

        /*
         * Note to translators:  SAX2DOM is the name of a Java class that should
         * not be translated.  This is an adapter in the sense that it takes a
         * DOM object and converts it to something that uses the SAX API.
         */
        {ErrorMsg.SAX2DOM_ADAPTER_ERR,
        "SAX2DOMアダプタ''{0}''を作成できませんでした。"},

        /*
         * Note to translators:  "XSLTCSource.build()" is a Java method name.
         * "systemId" is an XML term that is short for "system identification".
         */
        {ErrorMsg.XSLTC_SOURCE_ERR,
        "systemIdを設定せずにXSLTCSource.build()が呼び出されました。"},

        { ErrorMsg.ER_RESULT_NULL,
            "結果はnullにできません"},

        /*
         * Note to translators:  This message indicates that the value argument
         * of setParameter must be a valid Java Object.
         */
        {ErrorMsg.JAXP_INVALID_SET_PARAM_VALUE,
        "パラメータ{0}は有効なJavaオブジェクトである必要があります"},


        {ErrorMsg.COMPILE_STDIN_ERR,
        "-iオプションは-oオプションとともに使用する必要があります。"},


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
        "形式\n   java com.sun.org.apache.xalan.internal.xsltc.cmdline.Compile [-o <output>]\n      [-d <directory>] [-j <jarfile>] [-p <package>]\n      [-n] [-x] [-u] [-v] [-h] { <stylesheet> | -i }\n\nOPTIONS\n   -o <output>    名前<output>を生成済transletに\n                  割り当てる。デフォルトでは、translet名は\n                  <stylesheet>名に由来します。このオプションは\n                  複数のスタイルシートをコンパイルする場合は無視されます。\n   -d <directory> transletの宛先ディレクトリを指定する\n   -j <jarfile>   <jarfile>で指定される名前のjarファイルにtransletクラスを\n                  パッケージする\n   -p <package>   生成されるすべてのtransletクラスのパッケージ名\n                  接頭辞を指定する。\n   -n             テンプレートのインライン化を有効にする(平均してデフォルト動作の方が\n                  優れています)。\n   -x             追加のデバッグ・メッセージ出力をオンにする\n   -u             <stylesheet>引数をURLとして解釈する\n   -i             スタイルシートをstdinから読み込むことをコンパイラに強制する\n   -v             コンパイラのバージョンを出力する\n   -h             この使用方法の文を出力する\n"},

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
        "形式 \n   java com.sun.org.apache.xalan.internal.xsltc.cmdline.Transform [-j <jarfile>]\n      [-x] [-n <iterations>] {-u <document_url> | <document>}\n      <class> [<param1>=<value1> ...]\n\n   translet <class>を使用して、<document>で指定される\n   XMLドキュメントを変換する。translet <class>は\n   ユーザーのCLASSPATH内か、オプションで指定された<jarfile>内にあります。\nOPTIONS\n   -j <jarfile>    transletをロードするjarfileを指定する\n   -x              追加のデバッグ・メッセージ出力をオンにする\n   -n <iterations> 変換を<iterations>回実行し、\n                   プロファイリング情報を表示する\n   -u <document_url> XML入力ドキュメントをURLとして指定する\n"},



        /*
         * Note to translators:  "<xsl:sort>", "<xsl:for-each>" and
         * "<xsl:apply-templates>" are keywords that should not be translated.
         * The message indicates that an xsl:sort element must be a child of
         * one of the other kinds of elements mentioned.
         */
        {ErrorMsg.STRAY_SORT_ERR,
        "<xsl:sort>は<xsl:for-each>または<xsl:apply-templates>の内部でのみ使用できます。"},

        /*
         * Note to translators:  The message indicates that the encoding
         * requested for the output document was on that requires support that
         * is not available from the Java Virtual Machine being used to execute
         * the program.
         */
        {ErrorMsg.UNSUPPORTED_ENCODING,
        "出力エンコーディング''{0}''はこのJVMではサポートされていません。"},

        /*
         * Note to translators:  The message indicates that the XPath expression
         * named in the substitution text was not well formed syntactically.
         */
        {ErrorMsg.SYNTAX_ERR,
        "''{0}''に構文エラーがあります。"},

        /*
         * Note to translators:  The substitution text is the name of a Java
         * class.  The term "constructor" here is the Java term.  The message is
         * displayed if XSLTC could not find a constructor for the specified
         * class.
         */
        {ErrorMsg.CONSTRUCTOR_NOT_FOUND,
        "外部コンストラクタ''{0}''が見つかりません。"},

        /*
         * Note to translators:  "static" is the Java keyword.  The substitution
         * text is the name of a function.  The first argument of that function
         * is not of the required type.
         */
        {ErrorMsg.NO_JAVA_FUNCT_THIS_REF,
        "staticでないJava関数''{0}''の最初の引数は無効なオブジェクト参照です。"},

        /*
         * Note to translators:  An XPath expression was not of the type
         * required in a particular context.  The substitution text is the
         * expression that was in error.
         */
        {ErrorMsg.TYPE_CHECK_ERR,
        "式''{0}''のタイプの確認中にエラーが発生しました。"},

        /*
         * Note to translators:  An XPath expression was not of the type
         * required in a particular context.  However, the location of the
         * problematic expression is unknown.
         */
        {ErrorMsg.TYPE_CHECK_UNK_LOC_ERR,
        "不明な場所での式のタイプの確認中にエラーが発生しました。"},

        /*
         * Note to translators:  The substitution text is the name of a command-
         * line option that was not recognized.
         */
        {ErrorMsg.ILLEGAL_CMDLINE_OPTION_ERR,
        "コマンド行オプション''{0}''は無効です。"},

        /*
         * Note to translators:  The substitution text is the name of a command-
         * line option.
         */
        {ErrorMsg.CMDLINE_OPT_MISSING_ARG_ERR,
        "コマンド行オプション''{0}''に必須の引数がありません。"},

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
        "translet ''{0}''を使用して変換します "},

        /*
         * Note to translators:  The first substitution is the name of a class,
         * while the second substitution is the name of a jar file.
         */
        {ErrorMsg.TRANSFORM_WITH_JAR_STR,
        "translet ''{0}''を使用してjarファイル''{1}''から変換します"},

        /*
         * Note to translators:  "TransformerFactory" is the name of a Java
         * interface and must not be translated.  The substitution text is
         * the name of the class that could not be instantiated.
         */
        {ErrorMsg.COULD_NOT_CREATE_TRANS_FACT,
        "TransformerFactoryクラス''{0}''のインスタンスを作成できませんでした。"},

        /*
         * Note to translators:  This message is produced when the user
         * specified a name for the translet class that contains characters
         * that are not permitted in a Java class name.  The substitution
         * text "{0}" specifies the name the user requested, while "{1}"
         * specifies the name the processor used instead.
         */
        {ErrorMsg.TRANSLET_NAME_JAVA_CONFLICT,
         "名前''{0}''にはJavaクラスの名前に許可されていない文字が含まれているため、transletクラスの名前として使用できませんでした。名前''{1}''がかわりに使用されます。"},

        /*
         * Note to translators:  The following message is used as a header.
         * All the error messages are collected together and displayed beneath
         * this message.
         */
        {ErrorMsg.COMPILER_ERROR_KEY,
        "コンパイラ・エラー:"},

        /*
         * Note to translators:  The following message is used as a header.
         * All the warning messages are collected together and displayed
         * beneath this message.
         */
        {ErrorMsg.COMPILER_WARNING_KEY,
        "コンパイラの警告:"},

        /*
         * Note to translators:  The following message is used as a header.
         * All the error messages that are produced when the stylesheet is
         * applied to an input document are collected together and displayed
         * beneath this message.  A 'translet' is the compiled form of a
         * stylesheet (see above).
         */
        {ErrorMsg.RUNTIME_ERROR_KEY,
        "Transletエラー:"},

        /*
         * Note to translators:  An attribute whose value is constrained to
         * be a "QName" or a list of "QNames" had a value that was incorrect.
         * 'QName' is an XML syntactic term that must not be translated.  The
         * substitution text contains the actual value of the attribute.
         */
        {ErrorMsg.INVALID_QNAME_ERR,
        "値が1つのQNameまたはQNameの空白文字区切りリストであることが必要な属性の値が''{0}''でした"},

        /*
         * Note to translators:  An attribute whose value is required to
         * be an "NCName".
         * 'NCName' is an XML syntactic term that must not be translated.  The
         * substitution text contains the actual value of the attribute.
         */
        {ErrorMsg.INVALID_NCNAME_ERR,
        "値がNCNameであることが必要な属性の値が''{0}''でした"},

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
        "<xsl:output>要素のメソッド属性の値が''{0}''でした。値は''xml''、''html''、''text''またはqname-but-not-ncnameのいずれかである必要があります"},

        {ErrorMsg.JAXP_GET_FEATURE_NULL_NAME,
        "機能名はTransformerFactory.getFeature(String name)内でnullにできません。"},

        {ErrorMsg.JAXP_SET_FEATURE_NULL_NAME,
        "機能名はTransformerFactory.setFeature(String name, boolean value)内でnullにできません。"},

        {ErrorMsg.JAXP_UNSUPPORTED_FEATURE,
        "機能''{0}''をこのTransformerFactoryに設定できません。"},

        {ErrorMsg.JAXP_SECUREPROCESSING_FEATURE,
        "FEATURE_SECURE_PROCESSING: セキュリティ・マネージャが存在するとき、機能をfalseに設定できません。"},

        /*
         * Note to translators:  This message describes an internal error in the
         * processor.  The term "byte code" is a Java technical term for the
         * executable code in a Java method, and "try-catch-finally block"
         * refers to the Java keywords with those names.  "Outlined" is a
         * technical term internal to XSLTC and should not be translated.
         */
        {ErrorMsg.OUTLINE_ERR_TRY_CATCH,
         "内部XSLTCエラー: 生成されたバイト・コードは、try-catch-finallyブロックを含んでいるため、アウトライン化できません。"},

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
         "内部XSLTCエラー: OutlineableChunkStartマーカーとOutlineableChunkEndマーカーは、対になっており、かつ正しくネストされている必要があります。"},

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
         "内部XSLTCエラー: アウトライン化されたバイト・コードのブロックの一部であった命令は、元のメソッドの中でまだ参照されています。"
        },


        /*
         * Note to translators:  This message describes an internal error in the
         * processor.  The "method" that is being referred to is a Java method
         * in a translet that XSLTC is generating.
         *
         */
        {ErrorMsg.OUTLINE_ERR_METHOD_TOO_BIG,
         "内部XSLTCエラー: トランスレット内のメソッドが、Java仮想マシンの制限(1メソッドの長さは最大64キロバイト)を超えています。一般的に、スタイルシート内のテンプレートのサイズが大き過ぎることが原因として考えられます。小さいサイズのテンプレートを使用して、スタイルシートを再構成してください。"
        },

        {ErrorMsg.XPATH_GROUP_LIMIT,
            "JAXP0801001: コンパイラは、''{2}''で設定された''{1}''制限を超える''{0}''グループを含むXPath式を検出しました。"},

        {ErrorMsg.XPATH_OPERATOR_LIMIT,
            "JAXP0801002: コンパイラは、''{2}''で設定された''{1}''制限を超える''{0}''演算子を含むXPath式を検出しました。"},
        {ErrorMsg.XPATH_TOTAL_OPERATOR_LIMIT,
            "JAXP0801003: コンパイラは、''{2}''で設定された''{1}''制限を超える累積''{0}''演算子を含むXPath式を検出しました。"},
      };

    }
}
