/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
/*
 * $Id: ErrorMessages_ja.java,v 1.2.4.1 2005/09/15 10:08:16 pvedula Exp $
 */

package com.sun.org.apache.xalan.internal.xsltc.compiler.util;

import java.util.ListResourceBundle;

/**
 * @author Morten Jorgensen
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
        "\u8907\u6570\u306e\u30b9\u30bf\u30a4\u30eb\u30b7\u30fc\u30c8\u304c\u540c\u3058\u30d5\u30a1\u30a4\u30eb\u5185\u306b\u5b9a\u7fa9\u3055\u308c\u3066\u3044\u307e\u3059\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a
         * template.  The same name was used on two different templates in the
         * same stylesheet.
         */
        {ErrorMsg.TEMPLATE_REDEF_ERR,
        "\u30c6\u30f3\u30d7\u30ec\u30fc\u30c8 ''{0}'' \u306f\u3053\u306e\u30b9\u30bf\u30a4\u30eb\u30b7\u30fc\u30c8\u5185\u306b\u3059\u3067\u306b\u5b9a\u7fa9\u3055\u308c\u3066\u3044\u307e\u3059\u3002"},


        /*
         * Note to translators:  The substitution text is the name of a
         * template.  A reference to the template name was encountered, but the
         * template is undefined.
         */
        {ErrorMsg.TEMPLATE_UNDEF_ERR,
        "\u30c6\u30f3\u30d7\u30ec\u30fc\u30c8 ''{0}'' \u306f\u3053\u306e\u30b9\u30bf\u30a4\u30eb\u30b7\u30fc\u30c8\u5185\u306b\u5b9a\u7fa9\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a variable
         * that was defined more than once.
         */
        {ErrorMsg.VARIABLE_REDEF_ERR,
        "\u5909\u6570 ''{0}'' \u306f\u540c\u3058\u6709\u52b9\u7bc4\u56f2\u5185\u306b\u8907\u6570\u56de\u5b9a\u7fa9\u3055\u308c\u3066\u3044\u307e\u3059\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a variable
         * or parameter.  A reference to the variable or parameter was found,
         * but it was never defined.
         */
        {ErrorMsg.VARIABLE_UNDEF_ERR,
        "\u5909\u6570\u307e\u305f\u306f\u30d1\u30e9\u30e1\u30fc\u30bf\u30fc ''{0}'' \u304c\u672a\u5b9a\u7fa9\u3067\u3059\u3002"},

        /*
         * Note to translators:  The word "class" here refers to a Java class.
         * Processing the stylesheet required a class to be loaded, but it could
         * not be found.  The substitution text is the name of the class.
         */
        {ErrorMsg.CLASS_NOT_FOUND_ERR,
        "\u30af\u30e9\u30b9 ''{0}'' \u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The word "method" here refers to a Java method.
         * Processing the stylesheet required a reference to the method named by
         * the substitution text, but it could not be found.  "public" is the
         * Java keyword.
         */
        {ErrorMsg.METHOD_NOT_FOUND_ERR,
        "\u5916\u90e8\u30e1\u30bd\u30c3\u30c9 ''{0}'' \u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093 (public \u3067\u306a\u3051\u308c\u3070\u306a\u308a\u307e\u305b\u3093)\u3002"},

        /*
         * Note to translators:  The word "method" here refers to a Java method.
         * Processing the stylesheet required a reference to the method named by
         * the substitution text, but no method with the required types of
         * arguments or return type could be found.
         */
        {ErrorMsg.ARGUMENT_CONVERSION_ERR,
        "\u5f15\u304d\u6570/\u623b\u308a\u30bf\u30a4\u30d7\u3092\u30e1\u30bd\u30c3\u30c9 ''{0}'' \u3078\u306e\u547c\u3073\u51fa\u3057\u3067\u5909\u63db\u3067\u304d\u307e\u305b\u3093"},

        /*
         * Note to translators:  The file or URI named in the substitution text
         * is missing.
         */
        {ErrorMsg.FILE_NOT_FOUND_ERR,
        "\u30d5\u30a1\u30a4\u30eb\u307e\u305f\u306f URI ''{0}'' \u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  This message is displayed when the URI
         * mentioned in the substitution text is not well-formed syntactically.
         */
        {ErrorMsg.INVALID_URI_ERR,
        "\u7121\u52b9\u306a URI ''{0}'' \u3067\u3059\u3002"},

        /*
         * Note to translators:  The file or URI named in the substitution text
         * exists but could not be opened.
         */
        {ErrorMsg.FILE_ACCESS_ERR,
        "\u30d5\u30a1\u30a4\u30eb\u307e\u305f\u306f URI ''{0}'' \u3092\u30aa\u30fc\u30d7\u30f3\u3067\u304d\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators: <xsl:stylesheet> and <xsl:transform> are
         * keywords that should not be translated.
         */
        {ErrorMsg.MISSING_ROOT_ERR,
        "<xsl:stylesheet> \u307e\u305f\u306f <xsl:transform> \u30a8\u30ec\u30e1\u30f3\u30c8\u304c\u5fc5\u8981\u3067\u3057\u305f\u3002"},

        /*
         * Note to translators:  The stylesheet contained a reference to a
         * namespace prefix that was undefined.  The value of the substitution
         * text is the name of the prefix.
         */
        {ErrorMsg.NAMESPACE_UNDEF_ERR,
        "\u30cd\u30fc\u30e0\u30fb\u30b9\u30da\u30fc\u30b9\u63a5\u982d\u90e8 ''{0}'' \u304c\u5ba3\u8a00\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The Java function named in the stylesheet could
         * not be found.
         */
        {ErrorMsg.FUNCTION_RESOLVE_ERR,
        "\u95a2\u6570 ''{0}'' \u3078\u306e\u547c\u3073\u51fa\u3057\u3092\u89e3\u6c7a\u3067\u304d\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a
         * function.  A literal string here means a constant string value.
         */
        {ErrorMsg.NEED_LITERAL_ERR,
        "''{0}'' \u3078\u306e\u5f15\u304d\u6570\u306f\u30ea\u30c6\u30e9\u30eb\u30fb\u30b9\u30c8\u30ea\u30f3\u30b0\u3067\u306a\u3051\u308c\u3070\u306a\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  This message indicates there was a syntactic
         * error in the form of an XPath expression.  The substitution text is
         * the expression.
         */
        {ErrorMsg.XPATH_PARSER_ERR,
        "XPath \u5f0f ''{0}'' \u3092\u69cb\u6587\u89e3\u6790\u4e2d\u306b\u30a8\u30e9\u30fc\u304c\u767a\u751f\u3057\u307e\u3057\u305f\u3002"},

        /*
         * Note to translators:  An element in the stylesheet requires a
         * particular attribute named by the substitution text, but that
         * attribute was not specified in the stylesheet.
         */
        {ErrorMsg.REQUIRED_ATTR_ERR,
        "\u5fc5\u9808\u5c5e\u6027 ''{0}'' \u304c\u3042\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  This message indicates that a character not
         * permitted in an XPath expression was encountered.  The substitution
         * text is the offending character.
         */
        {ErrorMsg.ILLEGAL_CHAR_ERR,
        "XPath \u5f0f\u5185\u306e\u6587\u5b57 ''{0}'' \u304c\u6b63\u3057\u304f\u3042\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  A processing instruction is a mark-up item in
         * an XML document that request some behaviour of an XML processor.  The
         * form of the name of was invalid in this case, and the substitution
         * text is the name.
         */
        {ErrorMsg.ILLEGAL_PI_ERR,
        "\u547d\u4ee4\u306e\u51e6\u7406\u306e\u305f\u3081\u306e\u540d\u524d ''{0}'' \u304c\u6b63\u3057\u304f\u3042\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  This message is reported if the stylesheet
         * being processed attempted to construct an XML document with an
         * attribute in a place other than on an element.  The substitution text
         * specifies the name of the attribute.
         */
        {ErrorMsg.STRAY_ATTRIBUTE_ERR,
        "\u5c5e\u6027 ''{0}'' \u304c\u30a8\u30ec\u30e1\u30f3\u30c8\u306e\u5916\u5074\u3067\u3059\u3002"},

        /*
         * Note to translators:  An attribute that wasn't recognized was
         * specified on an element in the stylesheet.  The attribute is named
         * by the substitution
         * text.
         */
        {ErrorMsg.ILLEGAL_ATTRIBUTE_ERR,
        "\u5c5e\u6027 ''{0}'' \u304c\u6b63\u3057\u304f\u3042\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  "import" and "include" are keywords that should
         * not be translated.  This messages indicates that the stylesheet
         * named in the substitution text imported or included itself either
         * directly or indirectly.
         */
        {ErrorMsg.CIRCULAR_INCLUDE_ERR,
        "import/include \u304c\u76f8\u4e92\u4f9d\u5b58\u3057\u3066\u3044\u307e\u3059\u3002 \u30b9\u30bf\u30a4\u30eb\u30fb\u30b7\u30fc\u30c8 ''{0}'' \u306f\u3059\u3067\u306b\u30ed\u30fc\u30c9\u3055\u308c\u3066\u3044\u307e\u3059\u3002"},

        /*
         * Note to translators:  A result-tree fragment is a portion of a
         * resulting XML document represented as a tree.  "<xsl:sort>" is a
         * keyword and should not be translated.
         */
        {ErrorMsg.RESULT_TREE_SORT_ERR,
        "\u7d50\u679c\u30c4\u30ea\u30fc\u30fb\u30d5\u30e9\u30b0\u30e1\u30f3\u30c8\u3092\u30bd\u30fc\u30c8\u3067\u304d\u307e\u305b\u3093 (<xsl:sort> \u30a8\u30ec\u30e1\u30f3\u30c8\u306f\u7121\u8996\u3055\u308c\u307e\u3059)\u3002 \u3053\u306e\u30ce\u30fc\u30c9\u306f\u7d50\u679c\u30c4\u30ea\u30fc\u306e\u4f5c\u6210\u6642\u306b\u30bd\u30fc\u30c8\u3057\u306a\u3051\u308c\u3070\u306a\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  A name can be given to a particular style to be
         * used to format decimal values.  The substitution text gives the name
         * of such a style for which more than one declaration was encountered.
         */
        {ErrorMsg.SYMBOLS_REDEF_ERR,
        "10 \u9032\u6570\u30d5\u30a9\u30fc\u30de\u30c3\u30c8\u8a2d\u5b9a ''{0}'' \u306f\u3059\u3067\u306b\u5b9a\u7fa9\u3055\u308c\u3066\u3044\u307e\u3059\u3002"},

        /*
         * Note to translators:  The stylesheet version named in the
         * substitution text is not supported.
         */
        {ErrorMsg.XSL_VERSION_ERR,
        "XSL \u30d0\u30fc\u30b8\u30e7\u30f3 ''{0}'' \u306f XSLTC \u306b\u3088\u308a\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The definitions of one or more variables or
         * parameters depend on one another.
         */
        {ErrorMsg.CIRCULAR_VARIABLE_ERR,
        "''{0}'' \u5185\u306e\u5909\u6570/\u30d1\u30e9\u30e1\u30fc\u30bf\u30fc\u306e\u53c2\u7167\u304c\u76f8\u4e92\u4f9d\u5b58\u3057\u3066\u3044\u307e\u3059\u3002"},

        /*
         * Note to translators:  The operator in an expresion with two operands was
         * not recognized.
         */
        {ErrorMsg.ILLEGAL_BINARY_OP_ERR,
        "2 \u9032\u5f0f\u306e\u6f14\u7b97\u5b50\u304c\u4e0d\u660e\u3067\u3059\u3002"},

        /*
         * Note to translators:  This message is produced if a reference to a
         * function has too many or too few arguments.
         */
        {ErrorMsg.ILLEGAL_ARG_ERR,
        "\u95a2\u6570\u547c\u3073\u51fa\u3057\u306e\u5f15\u304d\u6570 (1 \u3064\u4ee5\u4e0a) \u304c\u6b63\u3057\u304f\u3042\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  "document()" is the name of function and must
         * not be translated.  A node-set is a set of the nodes in the tree
         * representation of an XML document.
         */
        {ErrorMsg.DOCUMENT_ARG_ERR,
        "document() \u95a2\u6570\u3078\u306e 2 \u3064\u76ee\u306e\u5f15\u304d\u6570\u306f\u30ce\u30fc\u30c9\u30fb\u30bb\u30c3\u30c8\u3067\u306a\u3051\u308c\u3070\u306a\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  "<xsl:when>" and "<xsl:choose>" are keywords
         * and should not be translated.  This message describes a syntax error
         * in the stylesheet.
         */
        {ErrorMsg.MISSING_WHEN_ERR,
        "\u5c11\u306a\u304f\u3068\u3082 1 \u3064\u306e <xsl:when> \u30a8\u30ec\u30e1\u30f3\u30c8\u304c <xsl:choose> \u5185\u306b\u5fc5\u8981\u3067\u3059\u3002"},

        /*
         * Note to translators:  "<xsl:otherwise>" and "<xsl:choose>" are
         * keywords and should not be translated.  This message describes a
         * syntax error in the stylesheet.
         */
        {ErrorMsg.MULTIPLE_OTHERWISE_ERR,
        "<xsl:choose> \u3067\u8a31\u53ef\u3055\u308c\u3066\u3044\u308b <xsl:otherwise> \u30a8\u30ec\u30e1\u30f3\u30c8\u306f 1 \u3064\u3060\u3051\u3067\u3059\u3002"},

        /*
         * Note to translators:  "<xsl:otherwise>" and "<xsl:choose>" are
         * keywords and should not be translated.  This message describes a
         * syntax error in the stylesheet.
         */
        {ErrorMsg.STRAY_OTHERWISE_ERR,
        "<xsl:otherwise> \u3092\u4f7f\u7528\u3067\u304d\u308b\u306e\u306f <xsl:choose> \u5185\u3060\u3051\u3067\u3059\u3002"},

        /*
         * Note to translators:  "<xsl:when>" and "<xsl:choose>" are keywords
         * and should not be translated.  This message describes a syntax error
         * in the stylesheet.
         */
        {ErrorMsg.STRAY_WHEN_ERR,
        "<xsl:when> \u3092\u4f7f\u7528\u3067\u304d\u308b\u306e\u306f <xsl:choose> \u5185\u3060\u3051\u3067\u3059\u3002"},

        /*
         * Note to translators:  "<xsl:when>", "<xsl:otherwise>" and
         * "<xsl:choose>" are keywords and should not be translated.  This
         * message describes a syntax error in the stylesheet.
         */
        {ErrorMsg.WHEN_ELEMENT_ERR,
        "<xsl:choose> \u3067\u8a31\u53ef\u3055\u308c\u3066\u3044\u308b\u306e\u306f <xsl:when> \u304a\u3088\u3073 <xsl:otherwise> \u30a8\u30ec\u30e1\u30f3\u30c8\u3060\u3051\u3067\u3059\u3002"},

        /*
         * Note to translators:  "<xsl:attribute-set>" and "name" are keywords
         * that should not be translated.
         */
        {ErrorMsg.UNNAMED_ATTRIBSET_ERR,
        "<xsl:attribute-set> \u306b 'name' \u5c5e\u6027\u304c\u3042\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  An element in the stylesheet contained an
         * element of a type that it was not permitted to contain.
         */
        {ErrorMsg.ILLEGAL_CHILD_ERR,
        "\u5b50\u30a8\u30ec\u30e1\u30f3\u30c8\u304c\u6b63\u3057\u304f\u3042\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The stylesheet tried to create an element with
         * a name that was not a valid XML name.  The substitution text contains
         * the name.
         */
        {ErrorMsg.ILLEGAL_ELEM_NAME_ERR,
        "\u30a8\u30ec\u30e1\u30f3\u30c8 ''{0}'' \u306f\u547c\u3073\u51fa\u305b\u307e\u305b\u3093"},

        /*
         * Note to translators:  The stylesheet tried to create an attribute
         * with a name that was not a valid XML name.  The substitution text
         * contains the name.
         */
        {ErrorMsg.ILLEGAL_ATTR_NAME_ERR,
        "\u5c5e\u6027 ''{0}'' \u306f\u547c\u3073\u51fa\u305b\u307e\u305b\u3093"},

        /*
         * Note to translators:  The children of the outermost element of a
         * stylesheet are referred to as top-level elements.  No text should
         * occur within that outermost element unless it is within a top-level
         * element.  This message indicates that that constraint was violated.
         * "<xsl:stylesheet>" is a keyword that should not be translated.
         */
        {ErrorMsg.ILLEGAL_TEXT_NODE_ERR,
        "\u30c6\u30ad\u30b9\u30c8\u30fb\u30c7\u30fc\u30bf\u304c\u6700\u4e0a\u4f4d\u306e <xsl:stylesheet> \u30a8\u30ec\u30e1\u30f3\u30c8\u306e\u5916\u5074\u306b\u3042\u308a\u307e\u3059\u3002"},

        /*
         * Note to translators:  JAXP is an acronym for the Java API for XML
         * Processing.  This message indicates that the XML parser provided to
         * XSLTC to process the XML input document had a configuration problem.
         */
        {ErrorMsg.SAX_PARSER_CONFIG_ERR,
        "JAXP \u30d1\u30fc\u30b5\u30fc\u306f\u6b63\u3057\u304f\u69cb\u6210\u3055\u308c\u3066\u3044\u307e\u305b\u3093"},

        /*
         * Note to translators:  The substitution text names the internal error
         * encountered.
         */
        {ErrorMsg.INTERNAL_ERR,
        "\u30ea\u30ab\u30d0\u30ea\u30fc\u4e0d\u80fd XSLTC \u5185\u90e8\u30a8\u30e9\u30fc: ''{0}''"},

        /*
         * Note to translators:  The stylesheet contained an element that was
         * not recognized as part of the XSL syntax.  The substitution text
         * gives the element name.
         */
        {ErrorMsg.UNSUPPORTED_XSL_ERR,
        "XSL \u30a8\u30ec\u30e1\u30f3\u30c8 ''{0}'' \u306f\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The stylesheet referred to an extension to the
         * XSL syntax and indicated that it was defined by XSLTC, but XSTLC does
         * not recognized the particular extension named.  The substitution text
         * gives the extension name.
         */
        {ErrorMsg.UNSUPPORTED_EXT_ERR,
        "XSLTC \u62e1\u5f35 ''{0}'' \u306f\u8a8d\u8b58\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The XML document given to XSLTC as a stylesheet
         * was not, in fact, a stylesheet.  XSLTC is able to detect that in this
         * case because the outermost element in the stylesheet has to be
         * declared with respect to the XSL namespace URI, but no declaration
         * for that namespace was seen.
         */
        {ErrorMsg.MISSING_XSLT_URI_ERR,
        "\u5165\u529b\u6587\u66f8\u306f\u30b9\u30bf\u30a4\u30eb\u30b7\u30fc\u30c8\u3067\u306f\u3042\u308a\u307e\u305b\u3093 (XSL \u30cd\u30fc\u30e0\u30fb\u30b9\u30da\u30fc\u30b9\u306f\u30eb\u30fc\u30c8\u30fb\u30a8\u30ec\u30e1\u30f3\u30c8\u5185\u3067\u5ba3\u8a00\u3055\u308c\u3066\u3044\u307e\u305b\u3093)\u3002"},

        /*
         * Note to translators:  XSLTC could not find the stylesheet document
         * with the name specified by the substitution text.
         */
        {ErrorMsg.MISSING_XSLT_TARGET_ERR,
        "\u30b9\u30bf\u30a4\u30eb\u30b7\u30fc\u30c8\u30fb\u30bf\u30fc\u30b2\u30c3\u30c8 ''{0}'' \u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093\u3067\u3057\u305f\u3002"},

        /*
         * Note to translators:  This message represents an internal error in
         * condition in XSLTC.  The substitution text is the class name in XSLTC
         * that is missing some functionality.
         */
        {ErrorMsg.NOT_IMPLEMENTED_ERR,
        "''{0}'' \u304c\u30a4\u30f3\u30d7\u30ea\u30e1\u30f3\u30c8\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The XML document given to XSLTC as a stylesheet
         * was not, in fact, a stylesheet.
         */
        {ErrorMsg.NOT_STYLESHEET_ERR,
        "\u5165\u529b\u6587\u66f8\u306b\u306f XSL \u30b9\u30bf\u30a4\u30eb\u30b7\u30fc\u30c8\u304c\u5165\u3063\u3066\u3044\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The element named in the substitution text was
         * encountered in the stylesheet but is not recognized.
         */
        {ErrorMsg.ELEMENT_PARSE_ERR,
        "\u30a8\u30ec\u30e1\u30f3\u30c8 ''{0}'' \u3092\u69cb\u6587\u89e3\u6790\u3067\u304d\u307e\u305b\u3093\u3067\u3057\u305f"},

        /*
         * Note to translators:  "use", "<key>", "node", "node-set", "string"
         * and "number" are keywords in this context and should not be
         * translated.  This message indicates that the value of the "use"
         * attribute was not one of the permitted values.
         */
        {ErrorMsg.KEY_USE_ATTR_ERR,
        "<key> \u306e use \u5c5e\u6027\u306f node\u3001node-set\u3001string\u3001\u307e\u305f\u306f number \u3067\u306a\u3051\u308c\u3070\u306a\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  An XML document can specify the version of the
         * XML specification to which it adheres.  This message indicates that
         * the version specified for the output document was not valid.
         */
        {ErrorMsg.OUTPUT_VERSION_ERR,
        "\u51fa\u529b XML \u6587\u66f8\u306e\u30d0\u30fc\u30b8\u30e7\u30f3\u306f 1.0 \u306b\u306a\u3063\u3066\u3044\u308b\u306f\u305a\u3067\u3059"},

        /*
         * Note to translators:  The operator in a comparison operation was
         * not recognized.
         */
        {ErrorMsg.ILLEGAL_RELAT_OP_ERR,
        "\u95a2\u4fc2\u5f0f\u306e\u6f14\u7b97\u5b50\u304c\u4e0d\u660e\u3067\u3059"},

        /*
         * Note to translators:  An attribute set defines as a set of XML
         * attributes that can be added to an element in the output XML document
         * as a group.  This message is reported if the name specified was not
         * used to declare an attribute set.  The substitution text is the name
         * that is in error.
         */
        {ErrorMsg.ATTRIBSET_UNDEF_ERR,
        "\u5b58\u5728\u3057\u3066\u3044\u306a\u3044\u5c5e\u6027\u30bb\u30c3\u30c8 ''{0}'' \u3092\u4f7f\u7528\u3057\u3088\u3046\u3068\u3057\u3066\u3044\u307e\u3059\u3002"},

        /*
         * Note to translators:  The term "attribute value template" is a term
         * defined by XSLT which describes the value of an attribute that is
         * determined by an XPath expression.  The message indicates that the
         * expression was syntactically incorrect; the substitution text
         * contains the expression that was in error.
         */
        {ErrorMsg.ATTR_VAL_TEMPLATE_ERR,
        "\u5c5e\u6027\u5024\u30c6\u30f3\u30d7\u30ec\u30fc\u30c8 ''{0}'' \u3092\u69cb\u6587\u89e3\u6790\u3067\u304d\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  ???
         */
        {ErrorMsg.UNKNOWN_SIG_TYPE_ERR,
        "\u30af\u30e9\u30b9 ''{0}'' \u306e\u30b7\u30b0\u30cb\u30c1\u30e3\u30fc\u5185\u306e\u30c7\u30fc\u30bf\u30fb\u30bf\u30a4\u30d7\u304c\u4e0d\u660e\u3067\u3059\u3002"},

        /*
         * Note to translators:  The substitution text refers to data types.
         * The message is displayed if a value in a particular context needs to
         * be converted to type {1}, but that's not possible for a value of
         * type {0}.
         */
        {ErrorMsg.DATA_CONVERSION_ERR,
        "\u30c7\u30fc\u30bf\u30fb\u30bf\u30a4\u30d7 ''{0}'' \u3092 ''{1}'' \u306b\u5909\u63db\u3067\u304d\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  "Templates" is a Java class name that should
         * not be translated.
         */
        {ErrorMsg.NO_TRANSLET_CLASS_ERR,
        "\u3053\u306e\u30c6\u30f3\u30d7\u30ec\u30fc\u30c8\u306b\u306f\u6709\u52b9\u306a translet \u30af\u30e9\u30b9\u5b9a\u7fa9\u304c\u5165\u3063\u3066\u3044\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  "Templates" is a Java class name that should
         * not be translated.
         */
        {ErrorMsg.NO_MAIN_TRANSLET_ERR,
        "\u3053\u306e\u30c6\u30f3\u30d7\u30ec\u30fc\u30c8\u306b\u306f\u540d\u524d\u304c ''{0}'' \u306e\u30af\u30e9\u30b9\u306f\u5165\u3063\u3066\u3044\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a class.
         */
        {ErrorMsg.TRANSLET_CLASS_ERR,
        "translet \u30af\u30e9\u30b9 ''{0}'' \u3092\u30ed\u30fc\u30c9\u3067\u304d\u307e\u305b\u3093\u3067\u3057\u305f\u3002"},

        {ErrorMsg.TRANSLET_OBJECT_ERR,
        "translet \u30af\u30e9\u30b9\u304c\u30ed\u30fc\u30c9\u3055\u308c\u307e\u3057\u305f\u304c\u3001translet \u30a4\u30f3\u30b9\u30bf\u30f3\u30b9\u3092\u4f5c\u6210\u3067\u304d\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  "ErrorListener" is a Java interface name that
         * should not be translated.  The message indicates that the user tried
         * to set an ErrorListener object on object of the class named in the
         * substitution text with "null" Java value.
         */
        {ErrorMsg.ERROR_LISTENER_NULL_ERR,
        "''{0}'' \u306e ErrorListener \u3092\u30cc\u30eb\u306b\u8a2d\u5b9a\u3057\u3088\u3046\u3068\u3057\u3066\u3044\u307e\u3059"},

        /*
         * Note to translators:  StreamSource, SAXSource and DOMSource are Java
         * interface names that should not be translated.
         */
        {ErrorMsg.JAXP_UNKNOWN_SOURCE_ERR,
        "XSLTC \u304c\u30b5\u30dd\u30fc\u30c8\u3057\u3066\u3044\u308b\u306e\u306f StreamSource\u3001SAXSource\u3001\u304a\u3088\u3073 DOMSource \u3060\u3051\u3067\u3059"},

        /*
         * Note to translators:  "Source" is a Java class name that should not
         * be translated.  The substitution text is the name of Java method.
         */
        {ErrorMsg.JAXP_NO_SOURCE_ERR,
        "''{0}'' \u306b\u6e21\u3055\u308c\u305f\u30bd\u30fc\u30b9\u30fb\u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u306b\u306f\u30b3\u30f3\u30c6\u30f3\u30c4\u304c\u3042\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The message indicates that XSLTC failed to
         * compile the stylesheet into a translet (class file).
         */
        {ErrorMsg.JAXP_COMPILE_ERR,
        "\u30b9\u30bf\u30a4\u30eb\u30b7\u30fc\u30c8\u3092\u30b3\u30f3\u30d1\u30a4\u30eb\u3067\u304d\u307e\u305b\u3093\u3067\u3057\u305f"},

        /*
         * Note to translators:  "TransformerFactory" is a class name.  In this
         * context, an attribute is a property or setting of the
         * TransformerFactory object.  The substitution text is the name of the
         * unrecognised attribute.  The method used to retrieve the attribute is
         * "getAttribute", so it's not clear whether it would be best to
         * translate the term "attribute".
         */
        {ErrorMsg.JAXP_INVALID_ATTR_ERR,
        "TransformerFactory \u306f\u5c5e\u6027 ''{0}'' \u3092\u8a8d\u8b58\u3057\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  "setResult()" and "startDocument()" are Java
         * method names that should not be translated.
         */
        {ErrorMsg.JAXP_SET_RESULT_ERR,
        "setResult() \u306f startDocument() \u306e\u524d\u306b\u547c\u3073\u51fa\u3055\u308c\u3066\u3044\u306a\u3051\u308c\u3070\u306a\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  "Transformer" is a Java interface name that
         * should not be translated.  A Transformer object should contained a
         * reference to a translet object in order to be used for
         * transformations; this message is produced if that requirement is not
         * met.
         */
        {ErrorMsg.JAXP_NO_TRANSLET_ERR,
        "\u5909\u63db\u30d7\u30ed\u30b0\u30e9\u30e0\u306b\u306f\u30ab\u30d7\u30bb\u30eb\u5316\u3055\u308c\u305f translet \u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u304c\u3042\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The XML document that results from a
         * transformation needs to be sent to an output handler object; this
         * message is produced if that requirement is not met.
         */
        {ErrorMsg.JAXP_NO_HANDLER_ERR,
        "\u5909\u63db\u7d50\u679c\u306e\u51fa\u529b\u30cf\u30f3\u30c9\u30e9\u30fc\u304c\u5b9a\u7fa9\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  "Result" is a Java interface name in this
         * context.  The substitution text is a method name.
         */
        {ErrorMsg.JAXP_NO_RESULT_ERR,
        "''{0}'' \u306b\u6e21\u3055\u308c\u305f\u7d50\u679c\u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u304c\u7121\u52b9\u3067\u3059\u3002"},

        /*
         * Note to translators:  "Transformer" is a Java interface name.  The
         * user's program attempted to access an unrecognized property with the
         * name specified in the substitution text.  The method used to retrieve
         * the property is "getOutputProperty", so it's not clear whether it
         * would be best to translate the term "property".
         */
        {ErrorMsg.JAXP_UNKNOWN_PROP_ERR,
        "\u7121\u52b9\u306a Transformer \u30d7\u30ed\u30d1\u30c6\u30a3\u30fc ''{0}'' \u306b\u30a2\u30af\u30bb\u30b9\u3057\u3088\u3046\u3068\u3057\u3066\u3044\u307e\u3059\u3002"},

        /*
         * Note to translators:  SAX2DOM is the name of a Java class that should
         * not be translated.  This is an adapter in the sense that it takes a
         * DOM object and converts it to something that uses the SAX API.
         */
        {ErrorMsg.SAX2DOM_ADAPTER_ERR,
        "SAX2DOM \u30a2\u30c0\u30d7\u30bf\u30fc: ''{0}'' \u3092\u4f5c\u6210\u3067\u304d\u307e\u305b\u3093\u3067\u3057\u305f\u3002"},

        /*
         * Note to translators:  "XSLTCSource.build()" is a Java method name.
         * "systemId" is an XML term that is short for "system identification".
         */
        {ErrorMsg.XSLTC_SOURCE_ERR,
        "XSLTCSource.build() \u304c systemId \u3092\u8a2d\u5b9a\u3057\u306a\u3044\u3067\u547c\u3073\u51fa\u3055\u308c\u3066\u3044\u307e\u3059\u3002"},


        {ErrorMsg.COMPILE_STDIN_ERR,
        "-i \u30aa\u30d7\u30b7\u30e7\u30f3\u306f -o \u30aa\u30d7\u30b7\u30e7\u30f3\u3068\u4e00\u7dd2\u306b\u4f7f\u7528\u3057\u306a\u3051\u308c\u3070\u306a\u308a\u307e\u305b\u3093\u3002"},


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
        "SYNOPSIS\n   java com.sun.org.apache.xalan.internal.xsltc.cmdline.Compile [-o <output>]\n      [-d <directory>] [-j <jarfile>] [-p <package>]\n      [-n] [-x] [-s] [-u] [-v] [-h] { <stylesheet> | -i }\n\n\u30aa\u30d7\u30b7\u30e7\u30f3\n -o <output>    \u540d\u524d <output> \u3092\u751f\u6210\u5f8c\u306e translet \u306b\u5272\u308a\u5f53\u3066\n                \u307e\u3059\u3002 \u30c7\u30d5\u30a9\u30eb\u30c8\u3067\u306f\u3001translet \u540d\u306f <stylesheet>\n                \u540d\u304b\u3089\u3068\u3089\u308c\u307e\u3059\u3002 \u3053\u306e\u30aa\u30d7\u30b7\u30e7\u30f3\u306f\u8907\u6570\u306e\u30b9\u30bf\u30a4\u30eb\u30b7\u30fc\u30c8\u3092\n                \u30b3\u30f3\u30d1\u30a4\u30eb\u3059\u308b\u5834\u5408\u306f\u7121\u8996\u3055\u308c\u307e\u3059\u3002\n   -d <directory> translet \u306e\u5b9b\u5148\u30c7\u30a3\u30ec\u30af\u30c8\u30ea\u30fc\u3092\u6307\u5b9a\u3057\u307e\u3059\n -j <jarfile>   translet \u30af\u30e9\u30b9\u3092 <jarfile> \u3068\u3057\u3066\u6307\u5b9a\u3055\u308c\u305f\n                \u540d\u524d\u306e jar \u30d5\u30a1\u30a4\u30eb\u306b\u30d1\u30c3\u30b1\u30fc\u30b8\u3057\u307e\u3059\n -p <package>   \u751f\u6210\u5f8c\u306e\u3059\u3079\u3066\u306e translet \u30af\u30e9\u30b9\u306b\u30d1\u30c3\u30b1\u30fc\u30b8\u540d\n                \u63a5\u982d\u90e8\u3092\u6307\u5b9a\u3057\u307e\u3059\u3002\n   -n             \u30c6\u30f3\u30d7\u30ec\u30fc\u30c8\u306e\u30a4\u30f3\u30e9\u30a4\u30f3\u5316\u3092\u4f7f\u7528\u53ef\u80fd\u306b\u3057\u307e\u3059 (\u30c6\u30f3\u30d7\u30ec\u30fc\u30c8\u306e\u30a4\u30f3\n                \u30e9\u30a4\u30f3\u5316\u3067\u5e73\u5747\u3068\u3057\u3066\u826f\u597d\u306a\u30d1\u30d5\u30a9\u30fc\u30de\u30f3\u30b9\u3092\u5f97\u308b\u3053\u3068\u304c\u3067\u304d\u307e\u3059)\u3002\n   -x             \u8ffd\u52a0\u306e\u30c7\u30d0\u30c3\u30b0\u30fb\u30e1\u30c3\u30bb\u30fc\u30b8\u51fa\u529b\u3092\u30aa\u30f3\u306b\u3057\u307e\u3059\n -s             System.exit \u306e\u547c\u3073\u51fa\u3057\u3092\u4f7f\u7528\u4e0d\u53ef\u306b\u3057\u307e\u3059\n -u             <stylesheet> \u5f15\u304d\u6570\u3092 URL \u3068\u3057\u3066\u89e3\u91c8\u3057\u307e\u3059\n -i             \u30b3\u30f3\u30d1\u30a4\u30e9\u30fc\u304c\u30b9\u30bf\u30a4\u30eb\u30b7\u30fc\u30c8\u3092\u6a19\u6e96\u5165\u529b\u304b\u3089\u8aad\u307f\u53d6\u308b\u3053\u3068\u3092\u5f37\u5236\u3057\u307e\u3059\n -v             \u30b3\u30f3\u30d1\u30a4\u30e9\u30fc\u306e\u30d0\u30fc\u30b8\u30e7\u30f3\u3092\u5370\u5237\u3057\u307e\u3059\n -h             \u3053\u306e\u4f7f\u7528\u6cd5\u30b9\u30c6\u30fc\u30c8\u30e1\u30f3\u30c8\u3092\u5370\u5237\u3057\u307e\u3059\n"},

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
        "SYNOPSIS \n   java com.sun.org.apache.xalan.internal.xsltc.cmdline.Transform [-j <jarfile>]\n [-x] [-s] [-n <iterations>] {-u <document_url> | <document>}\n <class> [<param1>=<value1> ...]\n \n translet <class> \u3092\u4f7f\u7528\u3057\u3066 <document> \u3068\u3057\u3066\u6307\u5b9a\u3055\u308c\u305f\n XML \u6587\u66f8\u3092\u5909\u63db\u3057\u307e\u3059\u3002 translet <class> \u306f\u30e6\u30fc\u30b6\u30fc\u306e CLASSPATH\n \u307e\u305f\u306f\u30aa\u30d7\u30b7\u30e7\u30f3\u3067\u6307\u5b9a\u3055\u308c\u308b <jarfile> \u306b\u5165\u3063\u3066\u3044\u307e\u3059\u3002\n\u30aa\u30d7\u30b7\u30e7\u30f3\n -j <jarfile>      translet \u306e\u30ed\u30fc\u30c9\u5143\u306e jarfile \u3092\u6307\u5b9a\u3057\u307e\u3059\n -x                \u8ffd\u52a0\u306e\u30c7\u30d0\u30c3\u30b0\u30fb\u30e1\u30c3\u30bb\u30fc\u30b8\u51fa\u529b\u3092\u30aa\u30f3\u306b\u3057\u307e\u3059\n -s                System.exit \u306e\u547c\u3073\u51fa\u3057\u3092\u4f7f\u7528\u4e0d\u53ef\u306b\u3057\u307e\u3059\n -n <iterations>   \u5909\u63db\u3092 <iterations> \u56de\u5b9f\u884c\u3057\n                   \u30d7\u30ed\u30d5\u30a1\u30a4\u30eb\u60c5\u5831\u3092\u8868\u793a\u3057\u307e\u3059\n -u <document_url> XML \u5165\u529b\u6587\u66f8\u3092 URL \u3068\u3057\u3066\u6307\u5b9a\u3057\u307e\u3059\n"},



        /*
         * Note to translators:  "<xsl:sort>", "<xsl:for-each>" and
         * "<xsl:apply-templates>" are keywords that should not be translated.
         * The message indicates that an xsl:sort element must be a child of
         * one of the other kinds of elements mentioned.
         */
        {ErrorMsg.STRAY_SORT_ERR,
        "<xsl:sort> \u3092\u4f7f\u7528\u3067\u304d\u308b\u306e\u306f <xsl:for-each> \u307e\u305f\u306f <xsl:apply-templates> \u5185\u3060\u3051\u3067\u3059\u3002"},

        /*
         * Note to translators:  The message indicates that the encoding
         * requested for the output document was on that requires support that
         * is not available from the Java Virtual Machine being used to execute
         * the program.
         */
        {ErrorMsg.UNSUPPORTED_ENCODING,
        "\u51fa\u529b\u30a8\u30f3\u30b3\u30fc\u30c9 ''{0}'' \u306f\u3053\u306e JVM \u3067\u306f\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  The message indicates that the XPath expression
         * named in the substitution text was not well formed syntactically.
         */
        {ErrorMsg.SYNTAX_ERR,
        "''{0}'' \u5185\u306b\u69cb\u6587\u30a8\u30e9\u30fc\u304c\u3042\u308a\u307e\u3059\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a Java
         * class.  The term "constructor" here is the Java term.  The message is
         * displayed if XSLTC could not find a constructor for the specified
         * class.
         */
        {ErrorMsg.CONSTRUCTOR_NOT_FOUND,
        "\u5916\u90e8\u30b3\u30f3\u30b9\u30c8\u30e9\u30af\u30bf\u30fc ''{0}'' \u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  "static" is the Java keyword.  The substitution
         * text is the name of a function.  The first argument of that function
         * is not of the required type.
         */
        {ErrorMsg.NO_JAVA_FUNCT_THIS_REF,
        "\u975e static Java \u95a2\u6570 ''{0}'' \u3078\u306e\u5148\u982d\u5f15\u304d\u6570\u306f\u6709\u52b9\u306a\u30aa\u30d6\u30b8\u30a7\u30af\u30c8\u53c2\u7167\u5b50\u3067\u3042\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  An XPath expression was not of the type
         * required in a particular context.  The substitution text is the
         * expression that was in error.
         */
        {ErrorMsg.TYPE_CHECK_ERR,
        "\u5f0f ''{0}'' \u306e\u30bf\u30a4\u30d7\u3092\u691c\u67fb\u4e2d\u306b\u30a8\u30e9\u30fc\u304c\u767a\u751f\u3057\u307e\u3057\u305f\u3002"},

        /*
         * Note to translators:  An XPath expression was not of the type
         * required in a particular context.  However, the location of the
         * problematic expression is unknown.
         */
        {ErrorMsg.TYPE_CHECK_UNK_LOC_ERR,
        "\u4e0d\u660e\u306a\u30ed\u30b1\u30fc\u30b7\u30e7\u30f3\u3067\u5f0f\u306e\u30bf\u30a4\u30d7\u3092\u691c\u67fb\u4e2d\u306b\u30a8\u30e9\u30fc\u304c\u767a\u751f\u3057\u307e\u3057\u305f\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a command-
         * line option that was not recognized.
         */
        {ErrorMsg.ILLEGAL_CMDLINE_OPTION_ERR,
        "\u30b3\u30de\u30f3\u30c9\u884c\u30aa\u30d7\u30b7\u30e7\u30f3 ''{0}'' \u304c\u7121\u52b9\u3067\u3059\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a command-
         * line option.
         */
        {ErrorMsg.CMDLINE_OPT_MISSING_ARG_ERR,
        "\u30b3\u30de\u30f3\u30c9\u884c\u30aa\u30d7\u30b7\u30e7\u30f3 ''{0}'' \u306b\u5fc5\u9808\u5c5e\u6027\u304c\u3042\u308a\u307e\u305b\u3093\u3002"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text contains two error
         * messages.  The spacing before the second substitution text indents
         * it the same amount as the first in English.
         */
        {ErrorMsg.WARNING_PLUS_WRAPPED_MSG,
        "\u8b66\u544a:  ''{0}''\n       :{1}"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text is an error message.
         */
        {ErrorMsg.WARNING_MSG,
        "\u8b66\u544a:  ''{0}''"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text contains two error
         * messages.  The spacing before the second substitution text indents
         * it the same amount as the first in English.
         */
        {ErrorMsg.FATAL_ERR_PLUS_WRAPPED_MSG,
        "\u81f4\u547d\u7684\u30a8\u30e9\u30fc:  ''{0}''\n           :{1}"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text is an error message.
         */
        {ErrorMsg.FATAL_ERR_MSG,
        "\u81f4\u547d\u7684\u30a8\u30e9\u30fc:  ''{0}''"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text contains two error
         * messages.  The spacing before the second substitution text indents
         * it the same amount as the first in English.
         */
        {ErrorMsg.ERROR_PLUS_WRAPPED_MSG,
        "\u30a8\u30e9\u30fc:  ''{0}''\n     :{1}"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text is an error message.
         */
        {ErrorMsg.ERROR_MSG,
        "\u30a8\u30e9\u30fc:  ''{0}''"},

        /*
         * Note to translators:  The substitution text is the name of a class.
         */
        {ErrorMsg.TRANSFORM_WITH_TRANSLET_STR,
        "translet ''{0}'' \u3092\u4f7f\u7528\u3059\u308b\u5909\u63db "},

        /*
         * Note to translators:  The first substitution is the name of a class,
         * while the second substitution is the name of a jar file.
         */
        {ErrorMsg.TRANSFORM_WITH_JAR_STR,
        "jar \u30d5\u30a1\u30a4\u30eb ''{1}'' \u304b\u3089\u306e translet ''{0}'' \u3092\u4f7f\u7528\u3059\u308b\u5909\u63db"},

        /*
         * Note to translators:  "TransformerFactory" is the name of a Java
         * interface and must not be translated.  The substitution text is
         * the name of the class that could not be instantiated.
         */
        {ErrorMsg.COULD_NOT_CREATE_TRANS_FACT,
        "TransformerFactory \u30af\u30e9\u30b9 ''{0}'' \u306e\u30a4\u30f3\u30b9\u30bf\u30f3\u30b9\u3092\u4f5c\u6210\u3067\u304d\u307e\u305b\u3093\u3067\u3057\u305f\u3002"},

        /*
         * Note to translators:  The following message is used as a header.
         * All the error messages are collected together and displayed beneath
         * this message.
         */
        {ErrorMsg.COMPILER_ERROR_KEY,
        "\u30b3\u30f3\u30d1\u30a4\u30e9\u30fc\u30fb\u30a8\u30e9\u30fc:"},

        /*
         * Note to translators:  The following message is used as a header.
         * All the warning messages are collected together and displayed
         * beneath this message.
         */
        {ErrorMsg.COMPILER_WARNING_KEY,
        "\u30b3\u30f3\u30d1\u30a4\u30e9\u30fc\u8b66\u544a:"},

        /*
         * Note to translators:  The following message is used as a header.
         * All the error messages that are produced when the stylesheet is
         * applied to an input document are collected together and displayed
         * beneath this message.  A 'translet' is the compiled form of a
         * stylesheet (see above).
         */
        {ErrorMsg.RUNTIME_ERROR_KEY,
        "Translet \u30a8\u30e9\u30fc:"}
    };
    }
}
