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
 * $Id: ErrorMessages_zh_CN.java,v 1.2.4.1 2005/09/15 10:15:21 pvedula Exp $
 */

package com.sun.org.apache.xalan.internal.xsltc.compiler.util;

import java.util.ListResourceBundle;

/**
 * @author Morten Jorgensen
 */
public class ErrorMessages_zh_CN extends ListResourceBundle {

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
        "\u540c\u4e00\u6587\u4ef6\u4e2d\u5b9a\u4e49\u4e86\u591a\u4e2a\u6837\u5f0f\u8868\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a
         * template.  The same name was used on two different templates in the
         * same stylesheet.
         */
        {ErrorMsg.TEMPLATE_REDEF_ERR,
        "\u6b64\u6837\u5f0f\u8868\u4e2d\u5df2\u7ecf\u5b9a\u4e49\u4e86\u6a21\u677f\u201c{0}\u201d\u3002"},


        /*
         * Note to translators:  The substitution text is the name of a
         * template.  A reference to the template name was encountered, but the
         * template is undefined.
         */
        {ErrorMsg.TEMPLATE_UNDEF_ERR,
        "\u6b64\u6837\u5f0f\u8868\u4e2d\u672a\u5b9a\u4e49\u6a21\u677f\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a variable
         * that was defined more than once.
         */
        {ErrorMsg.VARIABLE_REDEF_ERR,
        "\u540c\u4e00\u4f5c\u7528\u57df\u4e2d\u591a\u6b21\u5b9a\u4e49\u4e86\u53d8\u91cf\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a variable
         * or parameter.  A reference to the variable or parameter was found,
         * but it was never defined.
         */
        {ErrorMsg.VARIABLE_UNDEF_ERR,
        "\u672a\u5b9a\u4e49\u53d8\u91cf\u6216\u53c2\u6570\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  The word "class" here refers to a Java class.
         * Processing the stylesheet required a class to be loaded, but it could
         * not be found.  The substitution text is the name of the class.
         */
        {ErrorMsg.CLASS_NOT_FOUND_ERR,
        "\u627e\u4e0d\u5230\u7c7b\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  The word "method" here refers to a Java method.
         * Processing the stylesheet required a reference to the method named by
         * the substitution text, but it could not be found.  "public" is the
         * Java keyword.
         */
        {ErrorMsg.METHOD_NOT_FOUND_ERR,
        "\u627e\u4e0d\u5230\u5916\u90e8\u65b9\u6cd5\u201c{0}\u201d\uff08\u5fc5\u987b\u662f public\uff09\u3002"},

        /*
         * Note to translators:  The word "method" here refers to a Java method.
         * Processing the stylesheet required a reference to the method named by
         * the substitution text, but no method with the required types of
         * arguments or return type could be found.
         */
        {ErrorMsg.ARGUMENT_CONVERSION_ERR,
        "\u65e0\u6cd5\u5c06\u8c03\u7528\u4e2d\u7684\u81ea\u53d8\u91cf\uff0f\u8fd4\u56de\u7c7b\u578b\u8f6c\u6362\u4e3a\u65b9\u6cd5\u201c{0}\u201d"},

        /*
         * Note to translators:  The file or URI named in the substitution text
         * is missing.
         */
        {ErrorMsg.FILE_NOT_FOUND_ERR,
        "\u627e\u4e0d\u5230\u6587\u4ef6\u6216 URI\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  This message is displayed when the URI
         * mentioned in the substitution text is not well-formed syntactically.
         */
        {ErrorMsg.INVALID_URI_ERR,
        "URI\u201c{0}\u201d\u65e0\u6548\u3002"},

        /*
         * Note to translators:  The file or URI named in the substitution text
         * exists but could not be opened.
         */
        {ErrorMsg.FILE_ACCESS_ERR,
        "\u65e0\u6cd5\u6253\u5f00\u6587\u4ef6\u6216 URI\u201c{0}\u201d\u3002"},

        /*
         * Note to translators: <xsl:stylesheet> and <xsl:transform> are
         * keywords that should not be translated.
         */
        {ErrorMsg.MISSING_ROOT_ERR,
        "\u671f\u671b\u51fa\u73b0 <xsl:stylesheet> \u6216 <xsl:transform> \u5143\u7d20\u3002"},

        /*
         * Note to translators:  The stylesheet contained a reference to a
         * namespace prefix that was undefined.  The value of the substitution
         * text is the name of the prefix.
         */
        {ErrorMsg.NAMESPACE_UNDEF_ERR,
        "\u672a\u8bf4\u660e\u540d\u79f0\u7a7a\u95f4\u524d\u7f00\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  The Java function named in the stylesheet could
         * not be found.
         */
        {ErrorMsg.FUNCTION_RESOLVE_ERR,
        "\u65e0\u6cd5\u89e3\u6790\u5bf9\u51fd\u6570\u201c{0}\u201d\u7684\u8c03\u7528\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a
         * function.  A literal string here means a constant string value.
         */
        {ErrorMsg.NEED_LITERAL_ERR,
        "\u201c{0}\u201d\u7684\u81ea\u53d8\u91cf\u5fc5\u987b\u662f\u6587\u5b57\u5b57\u7b26\u4e32\u3002"},

        /*
         * Note to translators:  This message indicates there was a syntactic
         * error in the form of an XPath expression.  The substitution text is
         * the expression.
         */
        {ErrorMsg.XPATH_PARSER_ERR,
        "\u5206\u6790 XPath \u8868\u8fbe\u5f0f\u201c{0}\u201d\u65f6\u51fa\u9519\u3002"},

        /*
         * Note to translators:  An element in the stylesheet requires a
         * particular attribute named by the substitution text, but that
         * attribute was not specified in the stylesheet.
         */
        {ErrorMsg.REQUIRED_ATTR_ERR,
        "\u7f3a\u5c11\u5fc5\u9700\u7684\u5c5e\u6027\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  This message indicates that a character not
         * permitted in an XPath expression was encountered.  The substitution
         * text is the offending character.
         */
        {ErrorMsg.ILLEGAL_CHAR_ERR,
        "XPath \u8868\u8fbe\u5f0f\u4e2d\u7684\u5b57\u7b26\u201c{0}\u201d\u975e\u6cd5\u3002"},

        /*
         * Note to translators:  A processing instruction is a mark-up item in
         * an XML document that request some behaviour of an XML processor.  The
         * form of the name of was invalid in this case, and the substitution
         * text is the name.
         */
        {ErrorMsg.ILLEGAL_PI_ERR,
        "\u5904\u7406\u6307\u4ee4\u7684\u540d\u79f0\u201c{0}\u201d\u975e\u6cd5\u3002"},

        /*
         * Note to translators:  This message is reported if the stylesheet
         * being processed attempted to construct an XML document with an
         * attribute in a place other than on an element.  The substitution text
         * specifies the name of the attribute.
         */
        {ErrorMsg.STRAY_ATTRIBUTE_ERR,
        "\u5c5e\u6027\u201c{0}\u201d\u5728\u5143\u7d20\u5916\u3002"},

        /*
         * Note to translators:  An attribute that wasn't recognized was
         * specified on an element in the stylesheet.  The attribute is named
         * by the substitution
         * text.
         */
        {ErrorMsg.ILLEGAL_ATTRIBUTE_ERR,
        "\u5c5e\u6027\u201c{0}\u201d\u975e\u6cd5\u3002"},

        /*
         * Note to translators:  "import" and "include" are keywords that should
         * not be translated.  This messages indicates that the stylesheet
         * named in the substitution text imported or included itself either
         * directly or indirectly.
         */
        {ErrorMsg.CIRCULAR_INCLUDE_ERR,
        "\u5faa\u73af import\uff0finclude\u3002\u5df2\u88c5\u5165\u6837\u5f0f\u8868\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  A result-tree fragment is a portion of a
         * resulting XML document represented as a tree.  "<xsl:sort>" is a
         * keyword and should not be translated.
         */
        {ErrorMsg.RESULT_TREE_SORT_ERR,
        "\u65e0\u6cd5\u6392\u5e8f\u7ed3\u679c\u6811\u7247\u6bb5\uff08<xsl:sort> \u5143\u7d20\u88ab\u5ffd\u7565\uff09\u3002\u5fc5\u987b\u5728\u521b\u5efa\u7ed3\u679c\u6811\u65f6\u5bf9\u8282\u70b9\u8fdb\u884c\u6392\u5e8f\u3002"},

        /*
         * Note to translators:  A name can be given to a particular style to be
         * used to format decimal values.  The substitution text gives the name
         * of such a style for which more than one declaration was encountered.
         */
        {ErrorMsg.SYMBOLS_REDEF_ERR,
        "\u5df2\u7ecf\u5b9a\u4e49\u4e86\u5341\u8fdb\u5236\u683c\u5f0f\u7684\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  The stylesheet version named in the
         * substitution text is not supported.
         */
        {ErrorMsg.XSL_VERSION_ERR,
        "XSLTC \u4e0d\u652f\u6301 XSL \u7248\u672c\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  The definitions of one or more variables or
         * parameters depend on one another.
         */
        {ErrorMsg.CIRCULAR_VARIABLE_ERR,
        "\u201c{0}\u201d\u4e2d\u5b58\u5728\u5faa\u73af\u53d8\u91cf\uff0f\u53c2\u6570\u5f15\u7528\u3002"},

        /*
         * Note to translators:  The operator in an expresion with two operands was
         * not recognized.
         */
        {ErrorMsg.ILLEGAL_BINARY_OP_ERR,
        "\u4e8c\u8fdb\u5236\u8868\u8fbe\u5f0f\u7684\u8fd0\u7b97\u7b26\u672a\u77e5\u3002"},

        /*
         * Note to translators:  This message is produced if a reference to a
         * function has too many or too few arguments.
         */
        {ErrorMsg.ILLEGAL_ARG_ERR,
        "\u51fd\u6570\u8c03\u7528\u7684\u81ea\u53d8\u91cf\u975e\u6cd5\u3002"},

        /*
         * Note to translators:  "document()" is the name of function and must
         * not be translated.  A node-set is a set of the nodes in the tree
         * representation of an XML document.
         */
        {ErrorMsg.DOCUMENT_ARG_ERR,
        "\u51fd\u6570 document() \u7684\u7b2c\u4e8c\u4e2a\u81ea\u53d8\u91cf\u5fc5\u987b\u662f\u8282\u70b9\u96c6\u3002"},

        /*
         * Note to translators:  "<xsl:when>" and "<xsl:choose>" are keywords
         * and should not be translated.  This message describes a syntax error
         * in the stylesheet.
         */
        {ErrorMsg.MISSING_WHEN_ERR,
        "<xsl:choose> \u4e2d\u81f3\u5c11\u8981\u6709\u4e00\u4e2a <xsl:when> \u5143\u7d20\u3002"},

        /*
         * Note to translators:  "<xsl:otherwise>" and "<xsl:choose>" are
         * keywords and should not be translated.  This message describes a
         * syntax error in the stylesheet.
         */
        {ErrorMsg.MULTIPLE_OTHERWISE_ERR,
        "<xsl:choose> \u4e2d\u53ea\u5141\u8bb8\u6709\u4e00\u4e2a <xsl:otherwise> \u5143\u7d20\u3002"},

        /*
         * Note to translators:  "<xsl:otherwise>" and "<xsl:choose>" are
         * keywords and should not be translated.  This message describes a
         * syntax error in the stylesheet.
         */
        {ErrorMsg.STRAY_OTHERWISE_ERR,
        "<xsl:otherwise> \u53ea\u80fd\u5728 <xsl:choose> \u4e2d\u4f7f\u7528\u3002"},

        /*
         * Note to translators:  "<xsl:when>" and "<xsl:choose>" are keywords
         * and should not be translated.  This message describes a syntax error
         * in the stylesheet.
         */
        {ErrorMsg.STRAY_WHEN_ERR,
        "<xsl:when> \u53ea\u80fd\u5728 <xsl:choose> \u4e2d\u4f7f\u7528\u3002"},

        /*
         * Note to translators:  "<xsl:when>", "<xsl:otherwise>" and
         * "<xsl:choose>" are keywords and should not be translated.  This
         * message describes a syntax error in the stylesheet.
         */
        {ErrorMsg.WHEN_ELEMENT_ERR,
        "<xsl:choose> \u4e2d\u53ea\u5141\u8bb8\u4f7f\u7528 <xsl:when> \u548c <xsl:otherwise>\u3002"},

        /*
         * Note to translators:  "<xsl:attribute-set>" and "name" are keywords
         * that should not be translated.
         */
        {ErrorMsg.UNNAMED_ATTRIBSET_ERR,
        "<xsl:attribute-set> \u7f3a\u5c11\u201cname\u201d\u5c5e\u6027\u3002"},

        /*
         * Note to translators:  An element in the stylesheet contained an
         * element of a type that it was not permitted to contain.
         */
        {ErrorMsg.ILLEGAL_CHILD_ERR,
        "\u5b50\u5143\u7d20\u975e\u6cd5\u3002"},

        /*
         * Note to translators:  The stylesheet tried to create an element with
         * a name that was not a valid XML name.  The substitution text contains
         * the name.
         */
        {ErrorMsg.ILLEGAL_ELEM_NAME_ERR,
        "\u4e0d\u80fd\u8c03\u7528\u5143\u7d20\u201c{0}\u201d"},

        /*
         * Note to translators:  The stylesheet tried to create an attribute
         * with a name that was not a valid XML name.  The substitution text
         * contains the name.
         */
        {ErrorMsg.ILLEGAL_ATTR_NAME_ERR,
        "\u4e0d\u80fd\u8c03\u7528\u5c5e\u6027\u201c{0}\u201d"},

        /*
         * Note to translators:  The children of the outermost element of a
         * stylesheet are referred to as top-level elements.  No text should
         * occur within that outermost element unless it is within a top-level
         * element.  This message indicates that that constraint was violated.
         * "<xsl:stylesheet>" is a keyword that should not be translated.
         */
        {ErrorMsg.ILLEGAL_TEXT_NODE_ERR,
        "\u6587\u672c\u6570\u636e\u5728\u9876\u7ea7 <xsl:stylesheet> \u5143\u7d20\u5916\u3002"},

        /*
         * Note to translators:  JAXP is an acronym for the Java API for XML
         * Processing.  This message indicates that the XML parser provided to
         * XSLTC to process the XML input document had a configuration problem.
         */
        {ErrorMsg.SAX_PARSER_CONFIG_ERR,
        "JAXP \u89e3\u6790\u5668\u6ca1\u6709\u6b63\u786e\u914d\u7f6e"},

        /*
         * Note to translators:  The substitution text names the internal error
         * encountered.
         */
        {ErrorMsg.INTERNAL_ERR,
        "\u4e0d\u53ef\u6062\u590d\u7684 XSLTC \u5185\u90e8\u9519\u8bef\uff1a\u201c{0}\u201d"},

        /*
         * Note to translators:  The stylesheet contained an element that was
         * not recognized as part of the XSL syntax.  The substitution text
         * gives the element name.
         */
        {ErrorMsg.UNSUPPORTED_XSL_ERR,
        "\u4e0d\u53d7\u652f\u6301\u7684 XSL \u5143\u7d20\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  The stylesheet referred to an extension to the
         * XSL syntax and indicated that it was defined by XSLTC, but XSTLC does
         * not recognized the particular extension named.  The substitution text
         * gives the extension name.
         */
        {ErrorMsg.UNSUPPORTED_EXT_ERR,
        "\u672a\u88ab\u8bc6\u522b\u7684 XSLTC \u6269\u5c55\u540d\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  The XML document given to XSLTC as a stylesheet
         * was not, in fact, a stylesheet.  XSLTC is able to detect that in this
         * case because the outermost element in the stylesheet has to be
         * declared with respect to the XSL namespace URI, but no declaration
         * for that namespace was seen.
         */
        {ErrorMsg.MISSING_XSLT_URI_ERR,
        "\u8f93\u5165\u6587\u6863\u4e0d\u662f\u6837\u5f0f\u8868\uff08XSL \u540d\u79f0\u7a7a\u95f4\u6ca1\u6709\u5728\u6839\u5143\u7d20\u4e2d\u8bf4\u660e\uff09\u3002"},

        /*
         * Note to translators:  XSLTC could not find the stylesheet document
         * with the name specified by the substitution text.
         */
        {ErrorMsg.MISSING_XSLT_TARGET_ERR,
        "\u627e\u4e0d\u5230\u6837\u5f0f\u8868\u76ee\u6807\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  This message represents an internal error in
         * condition in XSLTC.  The substitution text is the class name in XSLTC
         * that is missing some functionality.
         */
        {ErrorMsg.NOT_IMPLEMENTED_ERR,
        "\u6ca1\u6709\u5b9e\u73b0\uff1a\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  The XML document given to XSLTC as a stylesheet
         * was not, in fact, a stylesheet.
         */
        {ErrorMsg.NOT_STYLESHEET_ERR,
        "\u8f93\u5165\u6587\u6863\u4e0d\u5305\u542b XSL \u6837\u5f0f\u8868\u3002"},

        /*
         * Note to translators:  The element named in the substitution text was
         * encountered in the stylesheet but is not recognized.
         */
        {ErrorMsg.ELEMENT_PARSE_ERR,
        "\u65e0\u6cd5\u5206\u6790\u5143\u7d20\u201c{0}\u201d"},

        /*
         * Note to translators:  "use", "<key>", "node", "node-set", "string"
         * and "number" are keywords in this context and should not be
         * translated.  This message indicates that the value of the "use"
         * attribute was not one of the permitted values.
         */
        {ErrorMsg.KEY_USE_ATTR_ERR,
        "<key> \u7684 use \u5c5e\u6027\u5fc5\u987b\u662f node\u3001node-set\u3001string \u6216 number\u3002"},

        /*
         * Note to translators:  An XML document can specify the version of the
         * XML specification to which it adheres.  This message indicates that
         * the version specified for the output document was not valid.
         */
        {ErrorMsg.OUTPUT_VERSION_ERR,
        "\u8f93\u51fa XML \u6587\u6863\u7684\u7248\u672c\u5e94\u5f53\u662f 1.0"},

        /*
         * Note to translators:  The operator in a comparison operation was
         * not recognized.
         */
        {ErrorMsg.ILLEGAL_RELAT_OP_ERR,
        "\u5173\u7cfb\u8868\u8fbe\u5f0f\u7684\u8fd0\u7b97\u7b26\u672a\u77e5"},

        /*
         * Note to translators:  An attribute set defines as a set of XML
         * attributes that can be added to an element in the output XML document
         * as a group.  This message is reported if the name specified was not
         * used to declare an attribute set.  The substitution text is the name
         * that is in error.
         */
        {ErrorMsg.ATTRIBSET_UNDEF_ERR,
        "\u8bd5\u56fe\u4f7f\u7528\u4e0d\u5b58\u5728\u7684\u5c5e\u6027\u96c6\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  The term "attribute value template" is a term
         * defined by XSLT which describes the value of an attribute that is
         * determined by an XPath expression.  The message indicates that the
         * expression was syntactically incorrect; the substitution text
         * contains the expression that was in error.
         */
        {ErrorMsg.ATTR_VAL_TEMPLATE_ERR,
        "\u65e0\u6cd5\u5206\u6790\u5c5e\u6027\u503c\u6a21\u677f\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  ???
         */
        {ErrorMsg.UNKNOWN_SIG_TYPE_ERR,
        "\u7c7b\u201c{0}\u201d\u7684\u7b7e\u540d\u4e2d\u7684\u6570\u636e\u7c7b\u578b\u672a\u77e5\u3002"},

        /*
         * Note to translators:  The substitution text refers to data types.
         * The message is displayed if a value in a particular context needs to
         * be converted to type {1}, but that's not possible for a value of
         * type {0}.
         */
        {ErrorMsg.DATA_CONVERSION_ERR,
        "\u65e0\u6cd5\u5c06\u6570\u636e\u7c7b\u578b\u201c{0}\u201d\u8f6c\u6362\u6210\u201c{1}\u201d\u3002"},

        /*
         * Note to translators:  "Templates" is a Java class name that should
         * not be translated.
         */
        {ErrorMsg.NO_TRANSLET_CLASS_ERR,
        "\u6b64 Templates \u4e0d\u5305\u542b\u6709\u6548\u7684 translet \u7c7b\u5b9a\u4e49\u3002"},

        /*
         * Note to translators:  "Templates" is a Java class name that should
         * not be translated.
         */
        {ErrorMsg.NO_MAIN_TRANSLET_ERR,
        "\u6b64 Templates \u4e0d\u5305\u542b\u540d\u4e3a\u201c{0}\u201d\u7684\u7c7b\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a class.
         */
        {ErrorMsg.TRANSLET_CLASS_ERR,
        "\u65e0\u6cd5\u88c5\u5165 translet \u7c7b\u201c{0}\u201d\u3002"},

        {ErrorMsg.TRANSLET_OBJECT_ERR,
        "Translet \u7c7b\u5df2\u88c5\u5165\uff0c\u4f46\u65e0\u6cd5\u521b\u5efa translet \u5b9e\u4f8b\u3002"},

        /*
         * Note to translators:  "ErrorListener" is a Java interface name that
         * should not be translated.  The message indicates that the user tried
         * to set an ErrorListener object on object of the class named in the
         * substitution text with "null" Java value.
         */
        {ErrorMsg.ERROR_LISTENER_NULL_ERR,
        "\u8bd5\u56fe\u5c06\u201c{0}\u201d\u7684 ErrorListener \u8bbe\u7f6e\u4e3a null"},

        /*
         * Note to translators:  StreamSource, SAXSource and DOMSource are Java
         * interface names that should not be translated.
         */
        {ErrorMsg.JAXP_UNKNOWN_SOURCE_ERR,
        "XSLTC \u53ea\u652f\u6301 StreamSource\u3001SAXSource \u548c DOMSource"},

        /*
         * Note to translators:  "Source" is a Java class name that should not
         * be translated.  The substitution text is the name of Java method.
         */
        {ErrorMsg.JAXP_NO_SOURCE_ERR,
        "\u4f20\u9012\u7ed9\u201c{0}\u201d\u7684 Source \u5bf9\u8c61\u6ca1\u6709\u5185\u5bb9\u3002"},

        /*
         * Note to translators:  The message indicates that XSLTC failed to
         * compile the stylesheet into a translet (class file).
         */
        {ErrorMsg.JAXP_COMPILE_ERR,
        "\u65e0\u6cd5\u7f16\u8bd1\u6837\u5f0f\u8868"},

        /*
         * Note to translators:  "TransformerFactory" is a class name.  In this
         * context, an attribute is a property or setting of the
         * TransformerFactory object.  The substitution text is the name of the
         * unrecognised attribute.  The method used to retrieve the attribute is
         * "getAttribute", so it's not clear whether it would be best to
         * translate the term "attribute".
         */
        {ErrorMsg.JAXP_INVALID_ATTR_ERR,
        "TransformerFactory \u65e0\u6cd5\u8bc6\u522b\u5c5e\u6027\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  "setResult()" and "startDocument()" are Java
         * method names that should not be translated.
         */
        {ErrorMsg.JAXP_SET_RESULT_ERR,
        "setResult() \u5fc5\u987b\u5728 startDocument() \u4e4b\u524d\u8c03\u7528\u3002"},

        /*
         * Note to translators:  "Transformer" is a Java interface name that
         * should not be translated.  A Transformer object should contained a
         * reference to a translet object in order to be used for
         * transformations; this message is produced if that requirement is not
         * met.
         */
        {ErrorMsg.JAXP_NO_TRANSLET_ERR,
        "Transformer \u6ca1\u6709\u5c01\u88c5\u7684 translet \u5bf9\u8c61\u3002"},

        /*
         * Note to translators:  The XML document that results from a
         * transformation needs to be sent to an output handler object; this
         * message is produced if that requirement is not met.
         */
        {ErrorMsg.JAXP_NO_HANDLER_ERR,
        "\u6ca1\u6709\u4e3a\u8f6c\u6362\u7ed3\u679c\u5b9a\u4e49\u8f93\u51fa\u5904\u7406\u7a0b\u5e8f\u3002"},

        /*
         * Note to translators:  "Result" is a Java interface name in this
         * context.  The substitution text is a method name.
         */
        {ErrorMsg.JAXP_NO_RESULT_ERR,
        "\u4f20\u9012\u7ed9\u201c{0}\u201d\u7684 Result \u5bf9\u8c61\u65e0\u6548\u3002"},

        /*
         * Note to translators:  "Transformer" is a Java interface name.  The
         * user's program attempted to access an unrecognized property with the
         * name specified in the substitution text.  The method used to retrieve
         * the property is "getOutputProperty", so it's not clear whether it
         * would be best to translate the term "property".
         */
        {ErrorMsg.JAXP_UNKNOWN_PROP_ERR,
        "\u8bd5\u56fe\u8bbf\u95ee\u65e0\u6548\u7684 Transformer \u5c5e\u6027\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  SAX2DOM is the name of a Java class that should
         * not be translated.  This is an adapter in the sense that it takes a
         * DOM object and converts it to something that uses the SAX API.
         */
        {ErrorMsg.SAX2DOM_ADAPTER_ERR,
        "\u65e0\u6cd5\u521b\u5efa SAX2DOM \u9002\u914d\u5668\uff1a\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  "XSLTCSource.build()" is a Java method name.
         * "systemId" is an XML term that is short for "system identification".
         */
        {ErrorMsg.XSLTC_SOURCE_ERR,
        "\u6ca1\u6709\u8bbe\u7f6e systemId \u5c31\u8c03\u7528 XSLTCSource.build()\u3002"},


        {ErrorMsg.COMPILE_STDIN_ERR,
        "-i \u9009\u9879\u5fc5\u987b\u4e0e -o \u9009\u9879\u4e00\u8d77\u4f7f\u7528\u3002"},


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
        "SYNOPSIS\n java com.sun.org.apache.xalan.internal.xsltc.cmdline.Compile [-o <output>]\n [-d <directory>] [-j <jarfile>] [-p <package>]\n [-n] [-x] [-s] [-u] [-v] [-h] { <stylesheet> | -i }\n\n OPTIONS\n -o <output>    \u5c06\u540d\u79f0 <output> \u6307\u5b9a\u7ed9\u751f\u6210\u7684 translet\u3002\n\u7f3a\u7701\u60c5\u51b5\u4e0b\uff0ctranslet \u540d\u79f0\n \u6765\u81ea <stylesheet> \u7684\u540d\u79f0\u3002\n \u5982\u679c\u7f16\u8bd1\u591a\u4e2a\u6837\u5f0f\u8868\uff0c\u5219\u5ffd\u7565\u6b64\u9009\u9879\u3002\n-d <directory> \u6307\u5b9a translet \u7684\u76ee\u6807\u76ee\u5f55\n -j <jarfile>   \u5c06 translet \u7c7b\u5c01\u88c5\u6210\u547d\u540d\u4e3a <jarfile>\n \u7684 jar \u6587\u4ef6\n -p <package>   \u4e3a\u6240\u6709\u751f\u6210\u7684 translet \u7c7b\n\u6307\u5b9a\u8f6f\u4ef6\u5305\u540d\u79f0\u524d\u7f00\u3002\n-n             \u542f\u7528\u6a21\u677f\u5185\u5d4c\uff08\u5e73\u5747\u7f3a\u7701\n\u884c\u4e3a\u66f4\u4f73\uff09\u3002\n-x             \u6253\u5f00\u989d\u5916\u7684\u8c03\u8bd5\u6d88\u606f\u8f93\u51fa\n -s             \u7981\u6b62\u8c03\u7528 System.exit\n -u             \u5c06 <stylesheet> \u81ea\u53d8\u91cf\u89e3\u91ca\u4e3a URL\n -i             \u5f3a\u5236\u7f16\u8bd1\u5668\u4ece stdin \u8bfb\u5165\u6837\u5f0f\u8868\n -v             \u6253\u5370\u7f16\u8bd1\u5668\u7684\u7248\u672c\n -h             \u6253\u5370\u6b64\u7528\u6cd5\u8bed\u53e5\n"},

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
        "SYNOPSIS \n java com.sun.org.apache.xalan.internal.xsltc.cmdline.Transform [-j <jarfile>]\n [-x] [-s] [-n <iterations>] {-u <document_url> | <document>}\n <class> [<param1>=<value1> ...]\n\n \u4f7f\u7528 translet <class> \u6765\u8f6c\u6362\u6307\u5b9a\u4e3a <document> \u7684\nXML \u6587\u6863\u3002translet <class> \u8981\u4e48\u5728\n \u7528\u6237\u7684 CLASSPATH \u4e2d\uff0c\u8981\u4e48\u5728\u4efb\u610f\u6307\u5b9a\u7684 <jarfile> \u4e2d\u3002\n\u9009\u9879\n -j <jarfile>    \u6307\u5b9a\u88c5\u5165 translet \u7684 jarfile\n -x              \u6253\u5f00\u9644\u52a0\u7684\u8c03\u8bd5\u6d88\u606f\u8f93\u51fa\n -s              \u7981\u6b62\u8c03\u7528 System.exit\n -n <iterations> \u8fd0\u884c\u8f6c\u6362\u8fc7\u7a0b <iterations> \u6b21\u5e76\n \u663e\u793a\u6982\u8981\u5206\u6790\u4fe1\u606f\n -u <document_url> \u5c06 XML \u8f93\u5165\u6587\u6863\u6307\u5b9a\u4e3a URL\n"},



        /*
         * Note to translators:  "<xsl:sort>", "<xsl:for-each>" and
         * "<xsl:apply-templates>" are keywords that should not be translated.
         * The message indicates that an xsl:sort element must be a child of
         * one of the other kinds of elements mentioned.
         */
        {ErrorMsg.STRAY_SORT_ERR,
        "<xsl:sort> \u53ea\u80fd\u5728 <xsl:for-each> \u6216 <xsl:apply-templates> \u4e2d\u4f7f\u7528\u3002"},

        /*
         * Note to translators:  The message indicates that the encoding
         * requested for the output document was on that requires support that
         * is not available from the Java Virtual Machine being used to execute
         * the program.
         */
        {ErrorMsg.UNSUPPORTED_ENCODING,
        "\u6b64 JVM \u4e0d\u652f\u6301\u8f93\u51fa\u7f16\u7801\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  The message indicates that the XPath expression
         * named in the substitution text was not well formed syntactically.
         */
        {ErrorMsg.SYNTAX_ERR,
        "\u201c{0}\u201d\u4e2d\u7684\u8bed\u6cd5\u9519\u8bef\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a Java
         * class.  The term "constructor" here is the Java term.  The message is
         * displayed if XSLTC could not find a constructor for the specified
         * class.
         */
        {ErrorMsg.CONSTRUCTOR_NOT_FOUND,
        "\u627e\u4e0d\u5230\u5916\u90e8\u6784\u9020\u51fd\u6570\u201c{0}\u201d\u3002"},

        /*
         * Note to translators:  "static" is the Java keyword.  The substitution
         * text is the name of a function.  The first argument of that function
         * is not of the required type.
         */
        {ErrorMsg.NO_JAVA_FUNCT_THIS_REF,
        "\u975e static Java \u51fd\u6570\u201c{0}\u201d\u7684\u7b2c\u4e00\u4e2a\u81ea\u53d8\u91cf\u4e0d\u662f\u6709\u6548\u7684\u5bf9\u8c61\u53c2\u8003\u3002"},

        /*
         * Note to translators:  An XPath expression was not of the type
         * required in a particular context.  The substitution text is the
         * expression that was in error.
         */
        {ErrorMsg.TYPE_CHECK_ERR,
        "\u68c0\u67e5\u8868\u8fbe\u5f0f\u201c{0}\u201d\u7684\u7c7b\u578b\u65f6\u51fa\u9519\u3002"},

        /*
         * Note to translators:  An XPath expression was not of the type
         * required in a particular context.  However, the location of the
         * problematic expression is unknown.
         */
        {ErrorMsg.TYPE_CHECK_UNK_LOC_ERR,
        "\u68c0\u67e5\u672a\u77e5\u4f4d\u7f6e\u7684\u8868\u8fbe\u5f0f\u7c7b\u578b\u65f6\u51fa\u9519\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a command-
         * line option that was not recognized.
         */
        {ErrorMsg.ILLEGAL_CMDLINE_OPTION_ERR,
        "\u547d\u4ee4\u884c\u9009\u9879\u201c{0}\u201d\u65e0\u6548\u3002"},

        /*
         * Note to translators:  The substitution text is the name of a command-
         * line option.
         */
        {ErrorMsg.CMDLINE_OPT_MISSING_ARG_ERR,
        "\u547d\u4ee4\u884c\u9009\u9879\u201c{0}\u201d\u7f3a\u5c11\u5fc5\u9700\u7684\u81ea\u53d8\u91cf\u3002"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text contains two error
         * messages.  The spacing before the second substitution text indents
         * it the same amount as the first in English.
         */
        {ErrorMsg.WARNING_PLUS_WRAPPED_MSG,
        "\u8b66\u544a\uff1a\u201c{0}\u201d\n       \uff1a{1}"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text is an error message.
         */
        {ErrorMsg.WARNING_MSG,
        "\u8b66\u544a\uff1a\u201c{0}\u201d"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text contains two error
         * messages.  The spacing before the second substitution text indents
         * it the same amount as the first in English.
         */
        {ErrorMsg.FATAL_ERR_PLUS_WRAPPED_MSG,
        "\u81f4\u547d\u9519\u8bef\uff1a\u201c{0}\u201d\n           \uff1a{1}"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text is an error message.
         */
        {ErrorMsg.FATAL_ERR_MSG,
        "\u81f4\u547d\u9519\u8bef\uff1a\u201c{0}\u201d"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text contains two error
         * messages.  The spacing before the second substitution text indents
         * it the same amount as the first in English.
         */
        {ErrorMsg.ERROR_PLUS_WRAPPED_MSG,
        "\u9519\u8bef\uff1a\u201c{0}\u201d\n     \uff1a{1}"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text is an error message.
         */
        {ErrorMsg.ERROR_MSG,
        "\u9519\u8bef\uff1a\u201c{0}\u201d"},

        /*
         * Note to translators:  The substitution text is the name of a class.
         */
        {ErrorMsg.TRANSFORM_WITH_TRANSLET_STR,
        "\u4f7f\u7528 translet\u201c{0}\u201d\u8f6c\u6362"},

        /*
         * Note to translators:  The first substitution is the name of a class,
         * while the second substitution is the name of a jar file.
         */
        {ErrorMsg.TRANSFORM_WITH_JAR_STR,
        "\u4f7f\u7528 translet\u201c{0}\u201d\u4ece jar \u6587\u4ef6\u201c{1}\u201d\u8f6c\u6362"},

        /*
         * Note to translators:  "TransformerFactory" is the name of a Java
         * interface and must not be translated.  The substitution text is
         * the name of the class that could not be instantiated.
         */
        {ErrorMsg.COULD_NOT_CREATE_TRANS_FACT,
        "\u65e0\u6cd5\u521b\u5efa TransformerFactory \u7c7b\u201c{0}\u201d\u7684\u5b9e\u4f8b\u3002"},

        /*
         * Note to translators:  The following message is used as a header.
         * All the error messages are collected together and displayed beneath
         * this message.
         */
        {ErrorMsg.COMPILER_ERROR_KEY,
        "\u7f16\u8bd1\u5668\u9519\u8bef\uff1a"},

        /*
         * Note to translators:  The following message is used as a header.
         * All the warning messages are collected together and displayed
         * beneath this message.
         */
        {ErrorMsg.COMPILER_WARNING_KEY,
        "\u7f16\u8bd1\u5668\u8b66\u544a\uff1a"},

        /*
         * Note to translators:  The following message is used as a header.
         * All the error messages that are produced when the stylesheet is
         * applied to an input document are collected together and displayed
         * beneath this message.  A 'translet' is the compiled form of a
         * stylesheet (see above).
         */
        {ErrorMsg.RUNTIME_ERROR_KEY,
        "Translet \u9519\u8bef\uff1a"}
    };
    }
}
