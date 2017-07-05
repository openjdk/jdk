/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2004 The Apache Software Foundation.
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
 * $Id: SerializerMessages_ja.java /st_wptg_1.8.0.0.0jdk/2 2013/09/12 17:39:58 gmolloy Exp $
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
public class SerializerMessages_ja extends ListResourceBundle {

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
                "\u30E1\u30C3\u30BB\u30FC\u30B8\u30FB\u30AD\u30FC''{0}''\u306F\u3001\u30E1\u30C3\u30BB\u30FC\u30B8\u30FB\u30AF\u30E9\u30B9''{1}''\u3067\u306F\u3042\u308A\u307E\u305B\u3093" },

            {   MsgKey.BAD_MSGFORMAT,
                "\u30E1\u30C3\u30BB\u30FC\u30B8\u30FB\u30AF\u30E9\u30B9''{1}''\u306E\u30E1\u30C3\u30BB\u30FC\u30B8''{0}''\u306E\u30D5\u30A9\u30FC\u30DE\u30C3\u30C8\u306B\u5931\u6557\u3057\u307E\u3057\u305F\u3002" },

            {   MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
                "\u30B7\u30EA\u30A2\u30E9\u30A4\u30B6\u30FB\u30AF\u30E9\u30B9''{0}''\u306Forg.xml.sax.ContentHandler\u3092\u5B9F\u88C5\u3057\u307E\u305B\u3093\u3002" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_FIND,
                    "\u30EA\u30BD\u30FC\u30B9[ {0} ]\u306F\u898B\u3064\u304B\u308A\u307E\u305B\u3093\u3067\u3057\u305F\u3002\n {1}" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
                    "\u30EA\u30BD\u30FC\u30B9[ {0} ]\u3092\u30ED\u30FC\u30C9\u3067\u304D\u307E\u305B\u3093\u3067\u3057\u305F: {1} \n {2} \t {3}" },

            {   MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
                    "\u30D0\u30C3\u30D5\u30A1\u30FB\u30B5\u30A4\u30BA<=0" },

            {   MsgKey.ER_INVALID_UTF16_SURROGATE,
                    "\u7121\u52B9\u306AUTF-16\u30B5\u30ED\u30B2\u30FC\u30C8\u304C\u691C\u51FA\u3055\u308C\u307E\u3057\u305F: {0}\u3002" },

            {   MsgKey.ER_OIERROR,
                "IO\u30A8\u30E9\u30FC" },

            {   MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
                "\u5B50\u30CE\u30FC\u30C9\u306E\u5F8C\u307E\u305F\u306F\u8981\u7D20\u304C\u751F\u6210\u3055\u308C\u308B\u524D\u306B\u5C5E\u6027{0}\u3092\u8FFD\u52A0\u3067\u304D\u307E\u305B\u3093\u3002\u5C5E\u6027\u306F\u7121\u8996\u3055\u308C\u307E\u3059\u3002" },

            /*
             * Note to translators:  The stylesheet contained a reference to a
             * namespace prefix that was undefined.  The value of the substitution
             * text is the name of the prefix.
             */
            {   MsgKey.ER_NAMESPACE_PREFIX,
                "\u63A5\u982D\u8F9E''{0}''\u306E\u30CD\u30FC\u30E0\u30B9\u30DA\u30FC\u30B9\u304C\u5BA3\u8A00\u3055\u308C\u3066\u3044\u307E\u305B\u3093\u3002" },

            /*
             * Note to translators:  This message is reported if the stylesheet
             * being processed attempted to construct an XML document with an
             * attribute in a place other than on an element.  The substitution text
             * specifies the name of the attribute.
             */
            {   MsgKey.ER_STRAY_ATTRIBUTE,
                "\u5C5E\u6027''{0}''\u304C\u8981\u7D20\u306E\u5916\u5074\u306B\u3042\u308A\u307E\u3059\u3002" },

            /*
             * Note to translators:  As with the preceding message, a namespace
             * declaration has the form of an attribute and is only permitted to
             * appear on an element.  The substitution text {0} is the namespace
             * prefix and {1} is the URI that was being used in the erroneous
             * namespace declaration.
             */
            {   MsgKey.ER_STRAY_NAMESPACE,
                "\u30CD\u30FC\u30E0\u30B9\u30DA\u30FC\u30B9\u5BA3\u8A00''{0}''=''{1}''\u304C\u8981\u7D20\u306E\u5916\u5074\u306B\u3042\u308A\u307E\u3059\u3002" },

            {   MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
                "''{0}''\u3092\u30ED\u30FC\u30C9\u3067\u304D\u307E\u305B\u3093\u3067\u3057\u305F(CLASSPATH\u3092\u78BA\u8A8D\u3057\u3066\u304F\u3060\u3055\u3044)\u3002\u73FE\u5728\u306F\u5358\u306B\u30C7\u30D5\u30A9\u30EB\u30C8\u3092\u4F7F\u7528\u3057\u3066\u3044\u307E\u3059" },

            {   MsgKey.ER_ILLEGAL_CHARACTER,
                "{1}\u306E\u6307\u5B9A\u3055\u308C\u305F\u51FA\u529B\u30A8\u30F3\u30B3\u30FC\u30C7\u30A3\u30F3\u30B0\u3067\u793A\u3055\u308C\u306A\u3044\u6574\u6570\u5024{0}\u306E\u6587\u5B57\u3092\u51FA\u529B\u3057\u3088\u3046\u3068\u3057\u307E\u3057\u305F\u3002" },

            {   MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
                "\u51FA\u529B\u30E1\u30BD\u30C3\u30C9''{1}''\u306E\u30D7\u30ED\u30D1\u30C6\u30A3\u30FB\u30D5\u30A1\u30A4\u30EB''{0}''\u3092\u30ED\u30FC\u30C9\u3067\u304D\u307E\u305B\u3093\u3067\u3057\u305F(CLASSPATH\u3092\u78BA\u8A8D\u3057\u3066\u304F\u3060\u3055\u3044)" },

            {   MsgKey.ER_INVALID_PORT,
                "\u7121\u52B9\u306A\u30DD\u30FC\u30C8\u756A\u53F7" },

            {   MsgKey.ER_PORT_WHEN_HOST_NULL,
                "\u30DB\u30B9\u30C8\u304Cnull\u306E\u5834\u5408\u306F\u30DD\u30FC\u30C8\u3092\u8A2D\u5B9A\u3067\u304D\u307E\u305B\u3093" },

            {   MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
                "\u30DB\u30B9\u30C8\u306F\u6574\u5F62\u5F0F\u306E\u30A2\u30C9\u30EC\u30B9\u3067\u306F\u3042\u308A\u307E\u305B\u3093" },

            {   MsgKey.ER_SCHEME_NOT_CONFORMANT,
                "\u30B9\u30AD\u30FC\u30E0\u304C\u6574\u5408\u3057\u3066\u3044\u307E\u305B\u3093\u3002" },

            {   MsgKey.ER_SCHEME_FROM_NULL_STRING,
                "null\u6587\u5B57\u5217\u304B\u3089\u306F\u30B9\u30AD\u30FC\u30E0\u3092\u8A2D\u5B9A\u3067\u304D\u307E\u305B\u3093" },

            {   MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
                "\u30D1\u30B9\u306B\u7121\u52B9\u306A\u30A8\u30B9\u30B1\u30FC\u30D7\u30FB\u30B7\u30FC\u30B1\u30F3\u30B9\u304C\u542B\u307E\u308C\u3066\u3044\u307E\u3059" },

            {   MsgKey.ER_PATH_INVALID_CHAR,
                "\u30D1\u30B9\u306B\u7121\u52B9\u306A\u6587\u5B57\u304C\u542B\u307E\u308C\u3066\u3044\u307E\u3059: {0}" },

            {   MsgKey.ER_FRAG_INVALID_CHAR,
                "\u30D5\u30E9\u30B0\u30E1\u30F3\u30C8\u306B\u7121\u52B9\u6587\u5B57\u304C\u542B\u307E\u308C\u3066\u3044\u307E\u3059" },

            {   MsgKey.ER_FRAG_WHEN_PATH_NULL,
                "\u30D1\u30B9\u304Cnull\u306E\u5834\u5408\u306F\u30D5\u30E9\u30B0\u30E1\u30F3\u30C8\u3092\u8A2D\u5B9A\u3067\u304D\u307E\u305B\u3093" },

            {   MsgKey.ER_FRAG_FOR_GENERIC_URI,
                "\u6C4E\u7528URI\u306E\u30D5\u30E9\u30B0\u30E1\u30F3\u30C8\u306E\u307F\u8A2D\u5B9A\u3067\u304D\u307E\u3059" },

            {   MsgKey.ER_NO_SCHEME_IN_URI,
                "\u30B9\u30AD\u30FC\u30E0\u304CURI\u306B\u898B\u3064\u304B\u308A\u307E\u305B\u3093" },

            {   MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
                "URI\u306F\u7A7A\u306E\u30D1\u30E9\u30E1\u30FC\u30BF\u3092\u4F7F\u7528\u3057\u3066\u521D\u671F\u5316\u3067\u304D\u307E\u305B\u3093" },

            {   MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
                "\u30D5\u30E9\u30B0\u30E1\u30F3\u30C8\u306F\u30D1\u30B9\u3068\u30D5\u30E9\u30B0\u30E1\u30F3\u30C8\u306E\u4E21\u65B9\u306B\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093" },

            {   MsgKey.ER_NO_QUERY_STRING_IN_PATH,
                "\u554F\u5408\u305B\u6587\u5B57\u5217\u306F\u30D1\u30B9\u304A\u3088\u3073\u554F\u5408\u305B\u6587\u5B57\u5217\u5185\u306B\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093" },

            {   MsgKey.ER_NO_PORT_IF_NO_HOST,
                "\u30DB\u30B9\u30C8\u304C\u6307\u5B9A\u3055\u308C\u3066\u3044\u306A\u3044\u5834\u5408\u306F\u30DD\u30FC\u30C8\u3092\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093" },

            {   MsgKey.ER_NO_USERINFO_IF_NO_HOST,
                "\u30DB\u30B9\u30C8\u304C\u6307\u5B9A\u3055\u308C\u3066\u3044\u306A\u3044\u5834\u5408\u306FUserinfo\u3092\u6307\u5B9A\u3067\u304D\u307E\u305B\u3093" },

            {   MsgKey.ER_XML_VERSION_NOT_SUPPORTED,
                "\u8B66\u544A: \u51FA\u529B\u30C9\u30AD\u30E5\u30E1\u30F3\u30C8\u306E\u30D0\u30FC\u30B8\u30E7\u30F3\u306F\u3001''{0}''\u3067\u3042\u308B\u3053\u3068\u304C\u30EA\u30AF\u30A8\u30B9\u30C8\u3055\u308C\u3066\u3044\u307E\u3059\u3002XML\u306E\u3053\u306E\u30D0\u30FC\u30B8\u30E7\u30F3\u306F\u30B5\u30DD\u30FC\u30C8\u3055\u308C\u3066\u3044\u307E\u305B\u3093\u3002\u51FA\u529B\u30C9\u30AD\u30E5\u30E1\u30F3\u30C8\u306E\u30D0\u30FC\u30B8\u30E7\u30F3\u306F\u3001''1.0''\u306B\u306A\u308A\u307E\u3059\u3002" },

            {   MsgKey.ER_SCHEME_REQUIRED,
                "\u30B9\u30AD\u30FC\u30E0\u304C\u5FC5\u8981\u3067\u3059\u3002" },

            /*
             * Note to translators:  The words 'Properties' and
             * 'SerializerFactory' in this message are Java class names
             * and should not be translated.
             */
            {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
                "SerializerFactory\u306B\u6E21\u3055\u308C\u308B\u30D7\u30ED\u30D1\u30C6\u30A3\u30FB\u30AA\u30D6\u30B8\u30A7\u30AF\u30C8\u306B\u3001''{0}''\u30D7\u30ED\u30D1\u30C6\u30A3\u304C\u3042\u308A\u307E\u305B\u3093\u3002" },

            {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
                "\u8B66\u544A:  \u30A8\u30F3\u30B3\u30FC\u30C7\u30A3\u30F3\u30B0''{0}''\u306F\u3001Java\u30E9\u30F3\u30BF\u30A4\u30E0\u3067\u30B5\u30DD\u30FC\u30C8\u3055\u308C\u3066\u3044\u307E\u305B\u3093\u3002" },


        };

        return contents;
    }
}
