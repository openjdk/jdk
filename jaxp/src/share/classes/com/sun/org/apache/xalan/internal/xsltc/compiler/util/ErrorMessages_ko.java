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
 * $Id: ErrorMessages_ko.java,v 1.2.4.1 2005/09/15 10:10:07 pvedula Exp $
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
        "\ud558\ub098 \uc774\uc0c1\uc758 \uc2a4\ud0c0\uc77c \uc2dc\ud2b8\uac00 \ub3d9\uc77c\ud55c \ud30c\uc77c\uc5d0\uc11c \uc815\uc758\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The substitution text is the name of a
         * template.  The same name was used on two different templates in the
         * same stylesheet.
         */
        {ErrorMsg.TEMPLATE_REDEF_ERR,
        "''{0}'' \ud15c\ud50c\ub9ac\ud2b8\uac00 \uc774\ubbf8 \uc774 \uc2a4\ud0c0\uc77c \uc2dc\ud2b8\uc5d0\uc11c \uc815\uc758\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},


        /*
         * Note to translators:  The substitution text is the name of a
         * template.  A reference to the template name was encountered, but the
         * template is undefined.
         */
        {ErrorMsg.TEMPLATE_UNDEF_ERR,
        "''{0}'' \ud15c\ud50c\ub9ac\ud2b8\uac00 \uc774 \uc2a4\ud0c0\uc77c \uc2dc\ud2b8\uc5d0\uc11c \uc815\uc758\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The substitution text is the name of a variable
         * that was defined more than once.
         */
        {ErrorMsg.VARIABLE_REDEF_ERR,
        "''{0}'' \ubcc0\uc218\uac00 \ub3d9\uc77c\ud55c \ubc94\uc704 \uc548\uc5d0\uc11c \uc5ec\ub7ec \ubc88 \uc815\uc758\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The substitution text is the name of a variable
         * or parameter.  A reference to the variable or parameter was found,
         * but it was never defined.
         */
        {ErrorMsg.VARIABLE_UNDEF_ERR,
        "''{0}'' \ub9e4\uac1c\ubcc0\uc218 \ub610\ub294 \ubcc0\uc218\uac00 \uc815\uc758\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The word "class" here refers to a Java class.
         * Processing the stylesheet required a class to be loaded, but it could
         * not be found.  The substitution text is the name of the class.
         */
        {ErrorMsg.CLASS_NOT_FOUND_ERR,
        "''{0}'' \ud074\ub798\uc2a4\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The word "method" here refers to a Java method.
         * Processing the stylesheet required a reference to the method named by
         * the substitution text, but it could not be found.  "public" is the
         * Java keyword.
         */
        {ErrorMsg.METHOD_NOT_FOUND_ERR,
        "''{0}'' \uc678\ubd80 \uba54\uc18c\ub4dc\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4(public\uc774\uc5b4\uc57c \ud569\ub2c8\ub2e4)."},

        /*
         * Note to translators:  The word "method" here refers to a Java method.
         * Processing the stylesheet required a reference to the method named by
         * the substitution text, but no method with the required types of
         * arguments or return type could be found.
         */
        {ErrorMsg.ARGUMENT_CONVERSION_ERR,
        "''{0}'' \uba54\uc18c\ub4dc\ub85c\uc758 \ud638\ucd9c\uc5d0\uc11c \uc778\uc218/\ub9ac\ud134 \uc720\ud615\uc744 \ubcc0\ud658\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The file or URI named in the substitution text
         * is missing.
         */
        {ErrorMsg.FILE_NOT_FOUND_ERR,
        "''{0}'' URI \ub610\ub294 \ud30c\uc77c\uc744 \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  This message is displayed when the URI
         * mentioned in the substitution text is not well-formed syntactically.
         */
        {ErrorMsg.INVALID_URI_ERR,
        "''{0}'' URI\uac00 \uc62c\ubc14\ub974\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The file or URI named in the substitution text
         * exists but could not be opened.
         */
        {ErrorMsg.FILE_ACCESS_ERR,
        "''{0}'' URI \ub610\ub294 \ud30c\uc77c\uc744 \uc5f4 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators: <xsl:stylesheet> and <xsl:transform> are
         * keywords that should not be translated.
         */
        {ErrorMsg.MISSING_ROOT_ERR,
        "<xsl:stylesheet> \ub610\ub294 <xsl:transform> \uc694\uc18c\uac00 \uc608\uc0c1\ub429\ub2c8\ub2e4."},

        /*
         * Note to translators:  The stylesheet contained a reference to a
         * namespace prefix that was undefined.  The value of the substitution
         * text is the name of the prefix.
         */
        {ErrorMsg.NAMESPACE_UNDEF_ERR,
        "''{0}'' \uc774\ub984 \uacf5\uac04 \uc811\ub450\ubd80\uac00 \uc120\uc5b8\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The Java function named in the stylesheet could
         * not be found.
         */
        {ErrorMsg.FUNCTION_RESOLVE_ERR,
        "''{0}'' \ud568\uc218\uc5d0 \ub300\ud55c \ud638\ucd9c\uc744 \ubd84\uc11d\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The substitution text is the name of a
         * function.  A literal string here means a constant string value.
         */
        {ErrorMsg.NEED_LITERAL_ERR,
        "''{0}''\uc5d0 \ub300\ud55c \uc778\uc218\ub294 \ub9ac\ud130\ub7f4 \ubb38\uc790\uc5f4\uc774\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

        /*
         * Note to translators:  This message indicates there was a syntactic
         * error in the form of an XPath expression.  The substitution text is
         * the expression.
         */
        {ErrorMsg.XPATH_PARSER_ERR,
        "''{0}'' XPath \ud45c\ud604\uc2dd \uad6c\ubb38 \ubd84\uc11d \uc911 \uc624\ub958\uac00 \ubc1c\uc0dd\ud588\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  An element in the stylesheet requires a
         * particular attribute named by the substitution text, but that
         * attribute was not specified in the stylesheet.
         */
        {ErrorMsg.REQUIRED_ATTR_ERR,
        "''{0}'' \ud544\uc218 \uc18d\uc131\uc774 \ub204\ub77d\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  This message indicates that a character not
         * permitted in an XPath expression was encountered.  The substitution
         * text is the offending character.
         */
        {ErrorMsg.ILLEGAL_CHAR_ERR,
        "XPath \ud45c\ud604\uc2dd\uc758 ''{0}'' \ubb38\uc790\uac00 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  A processing instruction is a mark-up item in
         * an XML document that request some behaviour of an XML processor.  The
         * form of the name of was invalid in this case, and the substitution
         * text is the name.
         */
        {ErrorMsg.ILLEGAL_PI_ERR,
        "\ucc98\ub9ac \uba85\ub839\uc5b4\uc5d0 \ub300\ud55c ''{0}'' \uc774\ub984\uc774 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  This message is reported if the stylesheet
         * being processed attempted to construct an XML document with an
         * attribute in a place other than on an element.  The substitution text
         * specifies the name of the attribute.
         */
        {ErrorMsg.STRAY_ATTRIBUTE_ERR,
        "''{0}'' \uc18d\uc131\uc774 \uc694\uc18c\uc758 \uc678\ubd80\uc5d0 \uc788\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  An attribute that wasn't recognized was
         * specified on an element in the stylesheet.  The attribute is named
         * by the substitution
         * text.
         */
        {ErrorMsg.ILLEGAL_ATTRIBUTE_ERR,
        "''{0}'' \uc18d\uc131\uc774 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "import" and "include" are keywords that should
         * not be translated.  This messages indicates that the stylesheet
         * named in the substitution text imported or included itself either
         * directly or indirectly.
         */
        {ErrorMsg.CIRCULAR_INCLUDE_ERR,
        "import/include \uac00 \uc21c\ud658\ub429\ub2c8\ub2e4. ''{0}'' \uc2a4\ud0c0\uc77c \uc2dc\ud2b8\uac00 \uc774\ubbf8 \ub85c\ub4dc\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  A result-tree fragment is a portion of a
         * resulting XML document represented as a tree.  "<xsl:sort>" is a
         * keyword and should not be translated.
         */
        {ErrorMsg.RESULT_TREE_SORT_ERR,
        "\uacb0\uacfc \ud2b8\ub9ac \ub2e8\ud3b8\uc744 \uc815\ub82c\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4(<xsl:sort> \uc694\uc18c\uac00 \ubb34\uc2dc\ub429\ub2c8\ub2e4). \uacb0\uacfc \ud2b8\ub9ac\ub97c \uc791\uc131\ud560 \ub54c \ub178\ub4dc\ub97c \uc815\ub82c\ud574\uc57c \ud569\ub2c8\ub2e4."},

        /*
         * Note to translators:  A name can be given to a particular style to be
         * used to format decimal values.  The substitution text gives the name
         * of such a style for which more than one declaration was encountered.
         */
        {ErrorMsg.SYMBOLS_REDEF_ERR,
        "''{0}'' 10\uc9c4\uc218 \ud3ec\ub9f7\ud305\uc774 \uc774\ubbf8 \uc815\uc758\ub418\uc5b4 \uc788\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The stylesheet version named in the
         * substitution text is not supported.
         */
        {ErrorMsg.XSL_VERSION_ERR,
        "XSLTC\uc5d0\uc11c ''{0}'' XSL \ubc84\uc804\uc744 \uc9c0\uc6d0\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The definitions of one or more variables or
         * parameters depend on one another.
         */
        {ErrorMsg.CIRCULAR_VARIABLE_ERR,
        "''{0}''\uc5d0\uc11c \ubcc0\uc218/\ub9e4\uac1c\ubcc0\uc218 \ucc38\uc870\uac00 \uc21c\ud658\ub429\ub2c8\ub2e4."},

        /*
         * Note to translators:  The operator in an expresion with two operands was
         * not recognized.
         */
        {ErrorMsg.ILLEGAL_BINARY_OP_ERR,
        "2\uc9c4 \ud45c\ud604\uc2dd\uc5d0 \ub300\ud55c \uc5f0\uc0b0\uc790\ub97c \uc54c \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  This message is produced if a reference to a
         * function has too many or too few arguments.
         */
        {ErrorMsg.ILLEGAL_ARG_ERR,
        "\ud568\uc218 \ud638\ucd9c\uc5d0 \ub300\ud55c \uc778\uc218\uac00 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "document()" is the name of function and must
         * not be translated.  A node-set is a set of the nodes in the tree
         * representation of an XML document.
         */
        {ErrorMsg.DOCUMENT_ARG_ERR,
        "document() \ud568\uc218\uc5d0 \ub300\ud55c \ub450 \ubc88\uc9f8 \uc778\uc218\ub294 node-set\uc5ec\uc57c \ud569\ub2c8\ub2e4."},

        /*
         * Note to translators:  "<xsl:when>" and "<xsl:choose>" are keywords
         * and should not be translated.  This message describes a syntax error
         * in the stylesheet.
         */
        {ErrorMsg.MISSING_WHEN_ERR,
        "<xsl:choose>\uc5d0 \ucd5c\uc18c \ud558\ub098\uc758 <xsl:when> \uc694\uc18c\uac00 \ud544\uc694\ud569\ub2c8\ub2e4."},

        /*
         * Note to translators:  "<xsl:otherwise>" and "<xsl:choose>" are
         * keywords and should not be translated.  This message describes a
         * syntax error in the stylesheet.
         */
        {ErrorMsg.MULTIPLE_OTHERWISE_ERR,
        "<xsl:choose>\uc5d0 \ud558\ub098\uc758 <xsl:otherwise> \uc694\uc18c\ub9cc\uc774 \ud5c8\uc6a9\ub429\ub2c8\ub2e4."},

        /*
         * Note to translators:  "<xsl:otherwise>" and "<xsl:choose>" are
         * keywords and should not be translated.  This message describes a
         * syntax error in the stylesheet.
         */
        {ErrorMsg.STRAY_OTHERWISE_ERR,
        "<xsl:otherwise>\ub294 <xsl:choose>\uc5d0\uc11c\ub9cc \uc0ac\uc6a9\ub420 \uc218 \uc788\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "<xsl:when>" and "<xsl:choose>" are keywords
         * and should not be translated.  This message describes a syntax error
         * in the stylesheet.
         */
        {ErrorMsg.STRAY_WHEN_ERR,
        "<xsl:when>\uc740 <xsl:choose>\uc5d0\uc11c\ub9cc \uc0ac\uc6a9\ub420 \uc218 \uc788\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "<xsl:when>", "<xsl:otherwise>" and
         * "<xsl:choose>" are keywords and should not be translated.  This
         * message describes a syntax error in the stylesheet.
         */
        {ErrorMsg.WHEN_ELEMENT_ERR,
        "<xsl:when> \ubc0f <xsl:otherwise> \uc694\uc18c\ub9cc\uc774 <xsl:choose>\uc5d0\uc11c \ud5c8\uc6a9\ub429\ub2c8\ub2e4."},

        /*
         * Note to translators:  "<xsl:attribute-set>" and "name" are keywords
         * that should not be translated.
         */
        {ErrorMsg.UNNAMED_ATTRIBSET_ERR,
        "<xsl:attribute-set>\uc774 'name' \uc18d\uc131\uc5d0\uc11c \ub204\ub77d\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  An element in the stylesheet contained an
         * element of a type that it was not permitted to contain.
         */
        {ErrorMsg.ILLEGAL_CHILD_ERR,
        "\ud558\uc704 \uc694\uc18c\uac00 \uc720\ud6a8\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The stylesheet tried to create an element with
         * a name that was not a valid XML name.  The substitution text contains
         * the name.
         */
        {ErrorMsg.ILLEGAL_ELEM_NAME_ERR,
        "''{0}'' \uc694\uc18c\ub97c \ud638\ucd9c\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The stylesheet tried to create an attribute
         * with a name that was not a valid XML name.  The substitution text
         * contains the name.
         */
        {ErrorMsg.ILLEGAL_ATTR_NAME_ERR,
        "''{0}'' \uc18d\uc131\uc744 \ud638\ucd9c\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The children of the outermost element of a
         * stylesheet are referred to as top-level elements.  No text should
         * occur within that outermost element unless it is within a top-level
         * element.  This message indicates that that constraint was violated.
         * "<xsl:stylesheet>" is a keyword that should not be translated.
         */
        {ErrorMsg.ILLEGAL_TEXT_NODE_ERR,
        "\ud14d\uc2a4\ud2b8 \ub370\uc774\ud130\uac00 \ucd5c\uc0c1\uc704 \ub808\ubca8 <xsl:stylesheet> \uc694\uc18c\uc758 \uc678\ubd80\uc5d0 \uc788\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  JAXP is an acronym for the Java API for XML
         * Processing.  This message indicates that the XML parser provided to
         * XSLTC to process the XML input document had a configuration problem.
         */
        {ErrorMsg.SAX_PARSER_CONFIG_ERR,
        "JAXP \uad6c\ubb38 \ubd84\uc11d\uae30\uac00 \uc81c\ub300\ub85c \uad6c\uc131\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The substitution text names the internal error
         * encountered.
         */
        {ErrorMsg.INTERNAL_ERR,
        "\ubcf5\uad6c\ud560 \uc218 \uc5c6\ub294 XSLTC-\ub0b4\ubd80 \uc624\ub958: ''{0}''"},

        /*
         * Note to translators:  The stylesheet contained an element that was
         * not recognized as part of the XSL syntax.  The substitution text
         * gives the element name.
         */
        {ErrorMsg.UNSUPPORTED_XSL_ERR,
        "''{0}'' XSL \uc694\uc18c\uac00 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The stylesheet referred to an extension to the
         * XSL syntax and indicated that it was defined by XSLTC, but XSTLC does
         * not recognized the particular extension named.  The substitution text
         * gives the extension name.
         */
        {ErrorMsg.UNSUPPORTED_EXT_ERR,
        "''{0}'' XSLTC \ud655\uc7a5\uc790\ub97c \uc778\uc2dd\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The XML document given to XSLTC as a stylesheet
         * was not, in fact, a stylesheet.  XSLTC is able to detect that in this
         * case because the outermost element in the stylesheet has to be
         * declared with respect to the XSL namespace URI, but no declaration
         * for that namespace was seen.
         */
        {ErrorMsg.MISSING_XSLT_URI_ERR,
        "\uc785\ub825 \ubb38\uc11c\ub294 \uc2a4\ud0c0\uc77c \uc2dc\ud2b8\uac00 \uc544\ub2d9\ub2c8\ub2e4(XSL \uc774\ub984 \uacf5\uac04\uc774 \ub8e8\ud2b8 \uc694\uc18c\uc5d0\uc11c \uc120\uc5b8\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4)."},

        /*
         * Note to translators:  XSLTC could not find the stylesheet document
         * with the name specified by the substitution text.
         */
        {ErrorMsg.MISSING_XSLT_TARGET_ERR,
        "''{0}'' \uc2a4\ud0c0\uc77c \uc2dc\ud2b8 \ub300\uc0c1\uc744 \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  This message represents an internal error in
         * condition in XSLTC.  The substitution text is the class name in XSLTC
         * that is missing some functionality.
         */
        {ErrorMsg.NOT_IMPLEMENTED_ERR,
        "\uad6c\ud604\ub418\uc9c0 \uc54a\uc558\uc74c: ''{0}''"},

        /*
         * Note to translators:  The XML document given to XSLTC as a stylesheet
         * was not, in fact, a stylesheet.
         */
        {ErrorMsg.NOT_STYLESHEET_ERR,
        "\uc785\ub825 \ubb38\uc11c\uc5d0 XSL \uc2a4\ud0c0\uc77c \uc2dc\ud2b8\uac00 \ud3ec\ud568\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The element named in the substitution text was
         * encountered in the stylesheet but is not recognized.
         */
        {ErrorMsg.ELEMENT_PARSE_ERR,
        "''{0}'' \uc694\uc18c\ub97c \uad6c\ubb38 \ubd84\uc11d\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "use", "<key>", "node", "node-set", "string"
         * and "number" are keywords in this context and should not be
         * translated.  This message indicates that the value of the "use"
         * attribute was not one of the permitted values.
         */
        {ErrorMsg.KEY_USE_ATTR_ERR,
        "<key>\uc758 use \uc18d\uc131\uc740 node, node-set, string \ub610\ub294 number\uc5ec\uc57c \ud569\ub2c8\ub2e4."},

        /*
         * Note to translators:  An XML document can specify the version of the
         * XML specification to which it adheres.  This message indicates that
         * the version specified for the output document was not valid.
         */
        {ErrorMsg.OUTPUT_VERSION_ERR,
        "\ucd9c\ub825 XML \ubb38\uc11c \ubc84\uc804\uc740 1.0\uc774\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

        /*
         * Note to translators:  The operator in a comparison operation was
         * not recognized.
         */
        {ErrorMsg.ILLEGAL_RELAT_OP_ERR,
        "\uad00\uacc4\uc2dd\uc5d0 \ub300\ud55c \uc5f0\uc0b0\uc790\ub97c \uc54c \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  An attribute set defines as a set of XML
         * attributes that can be added to an element in the output XML document
         * as a group.  This message is reported if the name specified was not
         * used to declare an attribute set.  The substitution text is the name
         * that is in error.
         */
        {ErrorMsg.ATTRIBSET_UNDEF_ERR,
        "\uc874\uc7ac\ud558\uc9c0 \uc54a\ub294 \uc18d\uc131 \uc138\ud2b8 ''{0}'' \uc0ac\uc6a9\uc744 \uc2dc\ub3c4 \uc911\uc785\ub2c8\ub2e4."},

        /*
         * Note to translators:  The term "attribute value template" is a term
         * defined by XSLT which describes the value of an attribute that is
         * determined by an XPath expression.  The message indicates that the
         * expression was syntactically incorrect; the substitution text
         * contains the expression that was in error.
         */
        {ErrorMsg.ATTR_VAL_TEMPLATE_ERR,
        "''{0}'' \uc18d\uc131\uac12 \ud15c\ud50c\ub9ac\ud2b8\ub97c \uad6c\ubb38 \ubd84\uc11d\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  ???
         */
        {ErrorMsg.UNKNOWN_SIG_TYPE_ERR,
        "''{0}'' \ud074\ub798\uc2a4\uc5d0 \ub300\ud55c \uc11c\uba85\uc5d0 \uc54c \uc218 \uc5c6\ub294 \ub370\uc774\ud130 \uc720\ud615\uc774 \uc788\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The substitution text refers to data types.
         * The message is displayed if a value in a particular context needs to
         * be converted to type {1}, but that's not possible for a value of
         * type {0}.
         */
        {ErrorMsg.DATA_CONVERSION_ERR,
        "\ub370\uc774\ud130 \uc720\ud615\uc744 ''{0}''\uc5d0\uc11c ''{1}''(\uc73c)\ub85c \ubcc0\ud658\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "Templates" is a Java class name that should
         * not be translated.
         */
        {ErrorMsg.NO_TRANSLET_CLASS_ERR,
        "\uc774 Templates\uc5d0\ub294 \uc62c\ubc14\ub978 translet \ud074\ub798\uc2a4 \uc815\uc758\uac00 \ud3ec\ud568\ub418\uc5b4 \uc788\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "Templates" is a Java class name that should
         * not be translated.
         */
        {ErrorMsg.NO_MAIN_TRANSLET_ERR,
        "\uc774 Templates\uc5d0\ub294 ''{0}'' \uc774\ub984\uc778 \ud074\ub798\uc2a4\uac00 \ud3ec\ud568\ub418\uc5b4 \uc788\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The substitution text is the name of a class.
         */
        {ErrorMsg.TRANSLET_CLASS_ERR,
        "''{0}'' translet \ud074\ub798\uc2a4\ub97c \ub85c\ub4dc\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        {ErrorMsg.TRANSLET_OBJECT_ERR,
        "translet \ud074\ub798\uc2a4\ub294 \ub85c\ub4dc\ub418\uc5c8\uc9c0\ub9cc translet \uc778\uc2a4\ud134\uc2a4\ub97c \uc791\uc131\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "ErrorListener" is a Java interface name that
         * should not be translated.  The message indicates that the user tried
         * to set an ErrorListener object on object of the class named in the
         * substitution text with "null" Java value.
         */
        {ErrorMsg.ERROR_LISTENER_NULL_ERR,
        "''{0}''\uc5d0 \ub300\ud55c ErrorListener\ub97c \ub110(null)\ub85c \uc124\uc815\ud558\ub824\uace0 \uc2dc\ub3c4 \uc911\uc785\ub2c8\ub2e4."},

        /*
         * Note to translators:  StreamSource, SAXSource and DOMSource are Java
         * interface names that should not be translated.
         */
        {ErrorMsg.JAXP_UNKNOWN_SOURCE_ERR,
        "XSLTC\uc5d0\uc11c StreamSource, SAXSource \ubc0f DOMSource\ub9cc\uc744 \uc9c0\uc6d0\ud569\ub2c8\ub2e4."},

        /*
         * Note to translators:  "Source" is a Java class name that should not
         * be translated.  The substitution text is the name of Java method.
         */
        {ErrorMsg.JAXP_NO_SOURCE_ERR,
        "''{0}''(\uc73c)\ub85c \ud328\uc2a4\ub41c Source \uc624\ube0c\uc81d\ud2b8\uc5d0 \ucee8\ud150\uce20\uac00 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The message indicates that XSLTC failed to
         * compile the stylesheet into a translet (class file).
         */
        {ErrorMsg.JAXP_COMPILE_ERR,
        "\uc2a4\ud0c0\uc77c \uc2dc\ud2b8\ub97c \ucef4\ud30c\uc77c\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "TransformerFactory" is a class name.  In this
         * context, an attribute is a property or setting of the
         * TransformerFactory object.  The substitution text is the name of the
         * unrecognised attribute.  The method used to retrieve the attribute is
         * "getAttribute", so it's not clear whether it would be best to
         * translate the term "attribute".
         */
        {ErrorMsg.JAXP_INVALID_ATTR_ERR,
        "TransformerFactory ''{0}'' \uc18d\uc131\uc744 \uc778\uc2dd\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "setResult()" and "startDocument()" are Java
         * method names that should not be translated.
         */
        {ErrorMsg.JAXP_SET_RESULT_ERR,
        "setResult()\ub294 startDocument()\uc5d0 \uc55e\uc11c \ud638\ucd9c\ub418\uc5b4\uc57c \ud569\ub2c8\ub2e4."},

        /*
         * Note to translators:  "Transformer" is a Java interface name that
         * should not be translated.  A Transformer object should contained a
         * reference to a translet object in order to be used for
         * transformations; this message is produced if that requirement is not
         * met.
         */
        {ErrorMsg.JAXP_NO_TRANSLET_ERR,
        "Transformer\uc5d0 \uc694\uc57d\ub41c translet \uc624\ube0c\uc81d\ud2b8\uac00 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The XML document that results from a
         * transformation needs to be sent to an output handler object; this
         * message is produced if that requirement is not met.
         */
        {ErrorMsg.JAXP_NO_HANDLER_ERR,
        "\ubcc0\ud658 \uacb0\uacfc\uc5d0 \ub300\ud55c \ucd9c\ub825 \ud578\ub4e4\ub7ec\uac00 \uc815\uc758\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "Result" is a Java interface name in this
         * context.  The substitution text is a method name.
         */
        {ErrorMsg.JAXP_NO_RESULT_ERR,
        "''{0}''(\uc73c)\ub85c \ud328\uc2a4\ub41c Result \uc624\ube0c\uc81d\ud2b8\uac00 \uc62c\ubc14\ub974\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "Transformer" is a Java interface name.  The
         * user's program attempted to access an unrecognized property with the
         * name specified in the substitution text.  The method used to retrieve
         * the property is "getOutputProperty", so it's not clear whether it
         * would be best to translate the term "property".
         */
        {ErrorMsg.JAXP_UNKNOWN_PROP_ERR,
        "''{0}'' \uc798\ubabb\ub41c Transformer \ud2b9\uc131\uc5d0 \uc561\uc138\uc2a4\ub97c \uc2dc\ub3c4 \uc911\uc785\ub2c8\ub2e4."},

        /*
         * Note to translators:  SAX2DOM is the name of a Java class that should
         * not be translated.  This is an adapter in the sense that it takes a
         * DOM object and converts it to something that uses the SAX API.
         */
        {ErrorMsg.SAX2DOM_ADAPTER_ERR,
        "SAX2DOM ''{0}'' \uc5b4\ub311\ud130\ub97c \uc791\uc131\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "XSLTCSource.build()" is a Java method name.
         * "systemId" is an XML term that is short for "system identification".
         */
        {ErrorMsg.XSLTC_SOURCE_ERR,
        "XSLTCSource.build()\uac00 \uc124\uc815\ub41c \uc2dc\uc2a4\ud15c ID \uc5c6\uc774 \ud638\ucd9c\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},


        {ErrorMsg.COMPILE_STDIN_ERR,
        "-i \uc635\uc158\uc740 -o \uc635\uc158\uacfc \ud568\uaed8 \uc0ac\uc6a9\ub418\uc5b4\uc57c \ud569\ub2c8\ub2e4."},


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
        "SYNOPSIS\n   java com.sun.org.apache.xalan.internal.xsltc.cmdline.Compile [-o <output>]\n      [-d <directory>] [-j <jarfile>] [-p <package>]\n      [-n] [-x] [-s] [-u] [-v] [-h] { <stylesheet> | -i }\n\n\uc635\uc158\n   -o <output>\uc740   \uc0dd\uc131\ub41c translet\uc5d0 \uc774\ub984 <output>\uc744 \uc9c0\uc815\ud569\ub2c8\ub2e4.\n                   translet \uc774\ub984\uc740 \uae30\ubcf8\uac12\uc73c\ub85c\n                   <stylesheet> \uc774\ub984\uc5d0\uc11c \uac00\uc838\uc635\ub2c8\ub2e4. \uc774 \uc635\uc158\uc740\n                   \ub2e4\uc911 \uc2a4\ud0c0\uc77c \uc2dc\ud2b8 \ucef4\ud30c\uc77c \uc911\uc778 \uacbd\uc6b0 \ubb34\uc2dc\ub429\ub2c8\ub2e4.\n   -d <directory>\ub294 translet\uc758 \ub300\uc0c1 \ub514\ub809\ud1a0\ub9ac\ub97c \uc9c0\uc815\ud569\ub2c8\ub2e4. \n   -j <jarfile>\ub294  <jarfile>\ub85c \uc9c0\uc815\ub41c \n                   jar \ud30c\uc77c\uc758 \uc774\ub984\uc73c\ub85c translet \ud074\ub798\uc2a4\ub97c \ud328\ud0a4\uc9c0\ud569\ub2c8\ub2e4. \n   -p <package>\ub294  \uc0dd\uc131\ub41c \ubaa8\ub4e0 \n                   translet \ud074\ub798\uc2a4\uc758 \ud328\ud0a4\uc9c0 \uc774\ub984\uc758 \uc811\ub450\ubd80\ub97c \uc9c0\uc815\ud569\ub2c8\ub2e4.\n   -n\uc740            \ud15c\ud50c\ub9ac\ud2b8 \uc778\ub77c\uc774\ub2dd(\ud3c9\uade0\ubcf4\ub2e4 \uc88b\uc740 \n                   \uc131\ub2a5\uc744 \uc0dd\uc131)\uc744 \uc0ac\uc6a9 \uac00\ub2a5\ud558\uac8c \ud569\ub2c8\ub2e4.\n   -x\ub294            \ucd94\uac00 \ub514\ubc84\uae45 \uba54\uc2dc\uc9c0 \ucd9c\ub825\uc744 \uc2dc\uc791\ud569\ub2c8\ub2e4.\n   -s\ub294            System.exit \ud638\ucd9c\uc744 \uc0ac\uc6a9 \ubd88\uac00\ub2a5\ud558\uac8c \ud569\ub2c8\ub2e4.\n   -u\ub294            <stylesheet> \uc778\uc218\ub97c URL\ub85c \ud574\uc11d\ud569\ub2c8\ub2e4.\n   -i\ub294            stdin\uc73c\ub85c\ubd80\ud130 \uc2a4\ud0c0\uc77c \uc2dc\ud2b8\ub97c \uc77d\uc744 \uc218 \uc788\ub3c4\ub85d\n                   \ucef4\ud30c\uc77c\ub7ec\ub97c \uac15\uc81c \uc2e4\ud589\ud569\ub2c8\ub2e4.\n   -v\ub294            \ucef4\ud30c\uc77c\ub7ec\uc758 \ubc84\uc804\uc744 \uc778\uc1c4\ud569\ub2c8\ub2e4.\n   -h\ub294            \uc0ac\uc6a9\ubc95 \uba85\ub839\ubb38\uc744 \uc778\uc1c4\ud569\ub2c8\ub2e4.\n"},

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
        "SYNOPSIS \n   java com.sun.org.apache.xalan.internal.xsltc.cmdline.Transform [-j <jarfile>]\n      [-x] [-s] [-n <iterations>] {-u <document_url> | <document>}\n      <class> [<param1>=<value1> ...]\n\n   translet <class>\ub97c \uc0ac\uc6a9\ud558\uc5ec <document>\ub85c \uc9c0\uc815\ub41c XML \ubb38\uc11c\ub97c \n   \ubcc0\ud658\ud569\ub2c8\ub2e4. translet <class> \ub294 \n   \uc0ac\uc6a9\uc790\uc758 CLASSPATH \ub098 \uc120\ud0dd\uc801\uc73c\ub85c \uc9c0\uc815\ub41c  <jarfile> \ub0b4\uc5d0 \uc788\uc2b5\ub2c8\ub2e4.\n\uc635\uc158\n   -j <jarfile>\ub294     \ub85c\ub4dc\ud560 translet\ub85c\ubd80\ud130 jarfile\uc744 \uc9c0\uc815\ud569\ub2c8\ub2e4.\n   -x\ub294               \ucd94\uac00 \ub514\ubc84\uae45 \uba54\uc2dc\uc9c0 \ucd9c\ub825\uc744 \uc2dc\uc791\ud569\ub2c8\ub2e4.\n   -s\ub294               System.exit \ud638\ucd9c\uc744 \uc0ac\uc6a9 \ubd88\uac00\ub2a5\ud558\uac8c \ud569\ub2c8\ub2e4.\n   -n <iterations>\uc740  <iterations> \ud69f\uc218\ub85c \ubcc0\ud658\uc744 \uc2e4\ud589\ud558\uba70\n                      \ud504\ub85c\ud30c\uc77c\ub9c1 \uc815\ubcf4\ub97c \ud45c\uc2dc\ud569\ub2c8\ub2e4.\n   -u <document_url>\ub294 XML \uc785\ub825 \ubb38\uc11c\ub97c URL\ub85c \uc9c0\uc815\ud569\ub2c8\ub2e4.\n"},



        /*
         * Note to translators:  "<xsl:sort>", "<xsl:for-each>" and
         * "<xsl:apply-templates>" are keywords that should not be translated.
         * The message indicates that an xsl:sort element must be a child of
         * one of the other kinds of elements mentioned.
         */
        {ErrorMsg.STRAY_SORT_ERR,
        "<xsl:sort>\ub294 <xsl:for-each> \ub610\ub294 <xsl:apply-templates>\uc5d0\uc11c\ub9cc \uc0ac\uc6a9\ub420 \uc218 \uc788\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The message indicates that the encoding
         * requested for the output document was on that requires support that
         * is not available from the Java Virtual Machine being used to execute
         * the program.
         */
        {ErrorMsg.UNSUPPORTED_ENCODING,
        "\uc774 JVM\uc5d0\uc11c ''{0}'' \ucd9c\ub825 \uc778\ucf54\ub529\uc744 \uc9c0\uc6d0\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The message indicates that the XPath expression
         * named in the substitution text was not well formed syntactically.
         */
        {ErrorMsg.SYNTAX_ERR,
        "''{0}''\uc5d0 \uad6c\ubb38 \uc624\ub958\uac00 \uc788\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The substitution text is the name of a Java
         * class.  The term "constructor" here is the Java term.  The message is
         * displayed if XSLTC could not find a constructor for the specified
         * class.
         */
        {ErrorMsg.CONSTRUCTOR_NOT_FOUND,
        "''{0}'' \uc678\ubd80 \uad6c\uc131\uc790\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  "static" is the Java keyword.  The substitution
         * text is the name of a function.  The first argument of that function
         * is not of the required type.
         */
        {ErrorMsg.NO_JAVA_FUNCT_THIS_REF,
        "non-static Java \ud568\uc218 ''{0}''\uc758 \uccab \ubc88\uc9f8 \uc778\uc218\uac00 \uc62c\ubc14\ub978 \uc624\ube0c\uc81d\ud2b8 \ucc38\uc870\uac00 \uc544\ub2d9\ub2c8\ub2e4."},

        /*
         * Note to translators:  An XPath expression was not of the type
         * required in a particular context.  The substitution text is the
         * expression that was in error.
         */
        {ErrorMsg.TYPE_CHECK_ERR,
        "''{0}'' \ud45c\ud604\uc2dd\uc758 \uc720\ud615\uc744 \uac80\uc0ac\ud558\ub294 \uc911 \uc624\ub958\uac00 \ubc1c\uc0dd\ud588\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  An XPath expression was not of the type
         * required in a particular context.  However, the location of the
         * problematic expression is unknown.
         */
        {ErrorMsg.TYPE_CHECK_UNK_LOC_ERR,
        "\uc54c \uc218 \uc5c6\ub294 \uc704\uce58\uc5d0\uc11c \ud45c\ud604\uc2dd\uc758 \uc720\ud615\uc744 \uac80\uc0ac\ud558\ub294 \uc911 \uc624\ub958\uac00 \ubc1c\uc0dd\ud588\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The substitution text is the name of a command-
         * line option that was not recognized.
         */
        {ErrorMsg.ILLEGAL_CMDLINE_OPTION_ERR,
        "''{0}'' \uba85\ub839\ud589 \uc635\uc158\uc774 \uc62c\ubc14\ub974\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The substitution text is the name of a command-
         * line option.
         */
        {ErrorMsg.CMDLINE_OPT_MISSING_ARG_ERR,
        "''{0}'' \uba85\ub839\ud589 \uc635\uc158\uc5d0 \ud544\uc218 \uc778\uc218\uac00 \ub204\ub77d\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text contains two error
         * messages.  The spacing before the second substitution text indents
         * it the same amount as the first in English.
         */
        {ErrorMsg.WARNING_PLUS_WRAPPED_MSG,
        "\uacbd\uace0:  ''{0}''\n       :{1}"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text is an error message.
         */
        {ErrorMsg.WARNING_MSG,
        "\uacbd\uace0:  ''{0}''"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text contains two error
         * messages.  The spacing before the second substitution text indents
         * it the same amount as the first in English.
         */
        {ErrorMsg.FATAL_ERR_PLUS_WRAPPED_MSG,
        "\uc2ec\uac01\ud55c \uc624\ub958:  ''{0}''\n           :{1}"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text is an error message.
         */
        {ErrorMsg.FATAL_ERR_MSG,
        "\uc2ec\uac01\ud55c \uc624\ub958:  ''{0}''"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text contains two error
         * messages.  The spacing before the second substitution text indents
         * it the same amount as the first in English.
         */
        {ErrorMsg.ERROR_PLUS_WRAPPED_MSG,
        "\uc624\ub958:  ''{0}''\n     :{1}"},

        /*
         * Note to translators:  This message is used to indicate the severity
         * of another message.  The substitution text is an error message.
         */
        {ErrorMsg.ERROR_MSG,
        "\uc624\ub958:  ''{0}''"},

        /*
         * Note to translators:  The substitution text is the name of a class.
         */
        {ErrorMsg.TRANSFORM_WITH_TRANSLET_STR,
        "''{0}'' translet\uc744 \uc0ac\uc6a9\ud558\uc5ec \ubcc0\ud658 "},

        /*
         * Note to translators:  The first substitution is the name of a class,
         * while the second substitution is the name of a jar file.
         */
        {ErrorMsg.TRANSFORM_WITH_JAR_STR,
        "''{1}'' jar \ud30c\uc77c\uc5d0\uc11c ''{0}'' translet\uc744 \uc0ac\uc6a9\ud558\uc5ec \ubcc0\ud658"},

        /*
         * Note to translators:  "TransformerFactory" is the name of a Java
         * interface and must not be translated.  The substitution text is
         * the name of the class that could not be instantiated.
         */
        {ErrorMsg.COULD_NOT_CREATE_TRANS_FACT,
        "TransformerFactory \ud074\ub798\uc2a4 ''{0}''\uc758 \uc778\uc2a4\ud134\uc2a4\ub97c \uc791\uc131\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The following message is used as a header.
         * All the error messages are collected together and displayed beneath
         * this message.
         */
        {ErrorMsg.COMPILER_ERROR_KEY,
        "\ucef4\ud30c\uc77c\ub7ec \uc624\ub958:"},

        /*
         * Note to translators:  The following message is used as a header.
         * All the warning messages are collected together and displayed
         * beneath this message.
         */
        {ErrorMsg.COMPILER_WARNING_KEY,
        "\ucef4\ud30c\uc77c\ub7ec \uacbd\uace0:"},

        /*
         * Note to translators:  The following message is used as a header.
         * All the error messages that are produced when the stylesheet is
         * applied to an input document are collected together and displayed
         * beneath this message.  A 'translet' is the compiled form of a
         * stylesheet (see above).
         */
        {ErrorMsg.RUNTIME_ERROR_KEY,
        "Translet \uc624\ub958:"}
    };
    }
}
