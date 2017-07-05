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
 * $Id: ErrorMessages_ko.java,v 1.2.4.1 2005/09/14 05:48:33 pvedula Exp $
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
        "''{0}''\uc758 \ub7f0\ud0c0\uc784 \ub0b4\ubd80 \uc624\ub958"},

        /*
         * Note to translators:  <xsl:copy> is a keyword that should not be
         * translated.
         */
        {BasisLibrary.RUN_TIME_COPY_ERR,
        "<xsl:copy> \uc2e4\ud589\uc2dc \ub7f0\ud0c0\uc784 \uc624\ub958\uac00 \ubc1c\uc0dd\ud588\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The substitution text refers to data types.
         * The message is displayed if a value in a particular context needs to
         * be converted to type {1}, but that's not possible for a value of type
         * {0}.
         */
        {BasisLibrary.DATA_CONVERSION_ERR,
        "''{0}''\uc5d0\uc11c ''{1}''\uc758 \ubcc0\ud658\uc774 \uc62c\ubc14\ub974\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  This message is displayed if the function named
         * by the substitution text is not a function that is supported.  XSLTC
         * is the acronym naming the product.
         */
        {BasisLibrary.EXTERNAL_FUNC_ERR,
        "XSLTC\uc5d0\uc11c ''{0}'' \uc678\ubd80 \ud568\uc218\ub97c \uc9c0\uc6d0\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  This message is displayed if two values are
         * compared for equality, but the data type of one of the values is
         * unknown.
         */
        {BasisLibrary.EQUALITY_EXPR_ERR,
        "\ub4f1\uc2dd\uc5d0 \uc54c \uc218 \uc5c6\ub294 \uc778\uc218 \uc720\ud615\uc774 \uc788\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The substitution text for {0} will be a data
         * type; the substitution text for {1} will be the name of a function.
         * This is displayed if an argument of the particular data type is not
         * permitted for a call to this function.
         */
        {BasisLibrary.INVALID_ARGUMENT_ERR,
        "''{1}''\uc5d0 \ub300\ud55c \ud638\ucd9c\uc5d0\uc11c \uc798\ubabb\ub41c \uc778\uc218 \uc720\ud615 ''{0}''"},

        /*
         * Note to translators:  There is way of specifying a format for a
         * number using a pattern; the processor was unable to format the
         * particular value using the specified pattern.
         */
        {BasisLibrary.FORMAT_NUMBER_ERR,
        "''{1}'' \ud328\ud134\uc744 \uc0ac\uc6a9\ud558\uc5ec ''{0}'' \uc22b\uc790 \ud3ec\ub9f7\uc744 \uc2dc\ub3c4 \uc911\uc785\ub2c8\ub2e4."},

        /*
         * Note to translators:  The following represents an internal error
         * situation in XSLTC.  The processor was unable to create a copy of an
         * iterator.  (See definition of iterator above.)
         */
        {BasisLibrary.ITERATOR_CLONE_ERR,
        "''{0}'' \ubc18\ubcf5\uae30\ub97c \ubcf5\uc81c\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The following represents an internal error
         * situation in XSLTC.  The processor attempted to create an iterator
         * for a particular axis (see definition above) that it does not
         * support.
         */
        {BasisLibrary.AXIS_SUPPORT_ERR,
        "''{0}'' \ucd95\uc5d0 \ub300\ud55c \ubc18\ubcf5\uae30\uac00 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The following represents an internal error
         * situation in XSLTC.  The processor attempted to create an iterator
         * for a particular axis (see definition above) that it does not
         * support.
         */
        {BasisLibrary.TYPED_AXIS_SUPPORT_ERR,
        "''{0}'' \uc720\ud615\ud654\ub41c \ucd95\uc5d0 \ub300\ud55c \ubc18\ubcf5\uae30\uac00 \uc9c0\uc6d0\ub418\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  This message is reported if the stylesheet
         * being processed attempted to construct an XML document with an
         * attribute in a place other than on an element.  The substitution text
         * specifies the name of the attribute.
         */
        {BasisLibrary.STRAY_ATTRIBUTE_ERR,
        "''{0}'' \uc18d\uc131\uc774 \uc694\uc18c\uc758 \uc678\ubd80\uc5d0 \uc788\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  As with the preceding message, a namespace
         * declaration has the form of an attribute and is only permitted to
         * appear on an element.  The substitution text {0} is the namespace
         * prefix and {1} is the URI that was being used in the erroneous
         * namespace declaration.
         */
        {BasisLibrary.STRAY_NAMESPACE_ERR,
        "''{0}''=''{1}'' \uc774\ub984 \uacf5\uac04 \uc120\uc5b8\uc774 \uc694\uc18c\uc758 \uc678\ubd80\uc5d0 \uc788\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The stylesheet contained a reference to a
         * namespace prefix that was undefined.  The value of the substitution
         * text is the name of the prefix.
         */
        {BasisLibrary.NAMESPACE_PREFIX_ERR,
        "''{0}'' \uc811\ub450\ubd80\uc5d0 \ub300\ud55c \uc774\ub984 \uacf5\uac04\uc774 \uc120\uc5b8\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The following represents an internal error.
         * DOMAdapter is a Java class in XSLTC.
         */
        {BasisLibrary.DOM_ADAPTER_INIT_ERR,
        "Source DOM\uc758 \uc798\ubabb\ub41c \uc720\ud615\uc744 \uc0ac\uc6a9\ud558\uc5ec DOMAdapter\uac00 \uc791\uc131\ub418\uc5c8\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The following message indicates that the XML
         * parser that is providing input to XSLTC cannot be used because it
         * does not describe to XSLTC the structure of the input XML document's
         * DTD.
         */
        {BasisLibrary.PARSER_DTD_SUPPORT_ERR,
        "\uc0ac\uc6a9 \uc911\uc778 SAX \uad6c\ubb38 \ubd84\uc11d\uae30\uac00 DTD \uc120\uc5b8 \uc774\ubca4\ud2b8\ub97c \ucc98\ub9ac\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The following message indicates that the XML
         * parser that is providing input to XSLTC cannot be used because it
         * does not distinguish between ordinary XML attributes and namespace
         * declarations.
         */
        {BasisLibrary.NAMESPACES_SUPPORT_ERR,
        "\uc0ac\uc6a9 \uc911\uc778 SAX \uad6c\ubb38 \ubd84\uc11d\uae30\uac00 XML \uc774\ub984 \uacf5\uac04\uc744 \uc9c0\uc6d0\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

        /*
         * Note to translators:  The substitution text is the URI that was in
         * error.
         */
        {BasisLibrary.CANT_RESOLVE_RELATIVE_URI_ERR,
        "''{0}'' URI \ucc38\uc870\ub97c \ubd84\uc11d\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."}
    };
    }

}
