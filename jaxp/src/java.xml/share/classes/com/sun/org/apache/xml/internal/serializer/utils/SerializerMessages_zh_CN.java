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
 * $Id: SerializerMessages_zh_CN.java /st_wptg_1.8.0.0.0jdk/2 2013/09/16 04:44:25 gmolloy Exp $
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
public class SerializerMessages_zh_CN extends ListResourceBundle {

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
                "\u6D88\u606F\u5173\u952E\u5B57 ''{0}'' \u4E0D\u5728\u6D88\u606F\u7C7B ''{1}'' \u4E2D" },

            {   MsgKey.BAD_MSGFORMAT,
                "\u6D88\u606F\u7C7B ''{1}'' \u4E2D\u6D88\u606F ''{0}'' \u7684\u683C\u5F0F\u5316\u5931\u8D25\u3002" },

            {   MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
                "\u4E32\u884C\u5668\u7C7B ''{0}'' \u4E0D\u5B9E\u73B0 org.xml.sax.ContentHandler\u3002" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_FIND,
                    "\u627E\u4E0D\u5230\u8D44\u6E90 [ {0} ]\u3002\n {1}" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
                    "\u8D44\u6E90 [ {0} ] \u65E0\u6CD5\u52A0\u8F7D: {1} \n {2} \t {3}" },

            {   MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
                    "\u7F13\u51B2\u533A\u5927\u5C0F <=0" },

            {   MsgKey.ER_INVALID_UTF16_SURROGATE,
                    "\u68C0\u6D4B\u5230\u65E0\u6548\u7684 UTF-16 \u4EE3\u7406: {0}?" },

            {   MsgKey.ER_OIERROR,
                "IO \u9519\u8BEF" },

            {   MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
                "\u5728\u751F\u6210\u5B50\u8282\u70B9\u4E4B\u540E\u6216\u5728\u751F\u6210\u5143\u7D20\u4E4B\u524D\u65E0\u6CD5\u6DFB\u52A0\u5C5E\u6027 {0}\u3002\u5C06\u5FFD\u7565\u5C5E\u6027\u3002" },

            /*
             * Note to translators:  The stylesheet contained a reference to a
             * namespace prefix that was undefined.  The value of the substitution
             * text is the name of the prefix.
             */
            {   MsgKey.ER_NAMESPACE_PREFIX,
                "\u6CA1\u6709\u8BF4\u660E\u540D\u79F0\u7A7A\u95F4\u524D\u7F00 ''{0}''\u3002" },

            /*
             * Note to translators:  This message is reported if the stylesheet
             * being processed attempted to construct an XML document with an
             * attribute in a place other than on an element.  The substitution text
             * specifies the name of the attribute.
             */
            {   MsgKey.ER_STRAY_ATTRIBUTE,
                "\u5C5E\u6027 ''{0}'' \u5728\u5143\u7D20\u5916\u90E8\u3002" },

            /*
             * Note to translators:  As with the preceding message, a namespace
             * declaration has the form of an attribute and is only permitted to
             * appear on an element.  The substitution text {0} is the namespace
             * prefix and {1} is the URI that was being used in the erroneous
             * namespace declaration.
             */
            {   MsgKey.ER_STRAY_NAMESPACE,
                "\u540D\u79F0\u7A7A\u95F4\u58F0\u660E ''{0}''=''{1}'' \u5728\u5143\u7D20\u5916\u90E8\u3002" },

            {   MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
                "\u65E0\u6CD5\u52A0\u8F7D ''{0}'' (\u68C0\u67E5 CLASSPATH), \u73B0\u5728\u53EA\u4F7F\u7528\u9ED8\u8BA4\u503C" },

            {   MsgKey.ER_ILLEGAL_CHARACTER,
                "\u5C1D\u8BD5\u8F93\u51FA\u672A\u4EE5{1}\u7684\u6307\u5B9A\u8F93\u51FA\u7F16\u7801\u8868\u793A\u7684\u6574\u6570\u503C {0} \u7684\u5B57\u7B26\u3002" },

            {   MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
                "\u65E0\u6CD5\u4E3A\u8F93\u51FA\u65B9\u6CD5 ''{1}'' \u52A0\u8F7D\u5C5E\u6027\u6587\u4EF6 ''{0}'' (\u68C0\u67E5 CLASSPATH)" },

            {   MsgKey.ER_INVALID_PORT,
                "\u65E0\u6548\u7684\u7AEF\u53E3\u53F7" },

            {   MsgKey.ER_PORT_WHEN_HOST_NULL,
                "\u4E3B\u673A\u4E3A\u7A7A\u65F6, \u65E0\u6CD5\u8BBE\u7F6E\u7AEF\u53E3" },

            {   MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
                "\u4E3B\u673A\u4E0D\u662F\u683C\u5F0F\u826F\u597D\u7684\u5730\u5740" },

            {   MsgKey.ER_SCHEME_NOT_CONFORMANT,
                "\u65B9\u6848\u4E0D\u4E00\u81F4\u3002" },

            {   MsgKey.ER_SCHEME_FROM_NULL_STRING,
                "\u65E0\u6CD5\u4ECE\u7A7A\u5B57\u7B26\u4E32\u8BBE\u7F6E\u65B9\u6848" },

            {   MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
                "\u8DEF\u5F84\u5305\u542B\u65E0\u6548\u7684\u8F6C\u4E49\u5E8F\u5217" },

            {   MsgKey.ER_PATH_INVALID_CHAR,
                "\u8DEF\u5F84\u5305\u542B\u65E0\u6548\u7684\u5B57\u7B26: {0}" },

            {   MsgKey.ER_FRAG_INVALID_CHAR,
                "\u7247\u6BB5\u5305\u542B\u65E0\u6548\u7684\u5B57\u7B26" },

            {   MsgKey.ER_FRAG_WHEN_PATH_NULL,
                "\u8DEF\u5F84\u4E3A\u7A7A\u65F6, \u65E0\u6CD5\u8BBE\u7F6E\u7247\u6BB5" },

            {   MsgKey.ER_FRAG_FOR_GENERIC_URI,
                "\u53EA\u80FD\u4E3A\u4E00\u822C URI \u8BBE\u7F6E\u7247\u6BB5" },

            {   MsgKey.ER_NO_SCHEME_IN_URI,
                "\u5728 URI \u4E2D\u627E\u4E0D\u5230\u65B9\u6848" },

            {   MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
                "\u65E0\u6CD5\u4EE5\u7A7A\u53C2\u6570\u521D\u59CB\u5316 URI" },

            {   MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
                "\u8DEF\u5F84\u548C\u7247\u6BB5\u4E2D\u90FD\u65E0\u6CD5\u6307\u5B9A\u7247\u6BB5" },

            {   MsgKey.ER_NO_QUERY_STRING_IN_PATH,
                "\u8DEF\u5F84\u548C\u67E5\u8BE2\u5B57\u7B26\u4E32\u4E2D\u4E0D\u80FD\u6307\u5B9A\u67E5\u8BE2\u5B57\u7B26\u4E32" },

            {   MsgKey.ER_NO_PORT_IF_NO_HOST,
                "\u5982\u679C\u6CA1\u6709\u6307\u5B9A\u4E3B\u673A, \u5219\u4E0D\u53EF\u4EE5\u6307\u5B9A\u7AEF\u53E3" },

            {   MsgKey.ER_NO_USERINFO_IF_NO_HOST,
                "\u5982\u679C\u6CA1\u6709\u6307\u5B9A\u4E3B\u673A, \u5219\u4E0D\u53EF\u4EE5\u6307\u5B9A Userinfo" },

            {   MsgKey.ER_XML_VERSION_NOT_SUPPORTED,
                "\u8B66\u544A: \u8F93\u51FA\u6587\u6863\u7684\u7248\u672C\u5E94\u4E3A ''{0}''\u3002\u4E0D\u652F\u6301\u6B64\u7248\u672C\u7684 XML\u3002\u8F93\u51FA\u6587\u6863\u7684\u7248\u672C\u5C06\u4E3A ''1.0''\u3002" },

            {   MsgKey.ER_SCHEME_REQUIRED,
                "\u65B9\u6848\u662F\u5FC5\u9700\u7684!" },

            /*
             * Note to translators:  The words 'Properties' and
             * 'SerializerFactory' in this message are Java class names
             * and should not be translated.
             */
            {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
                "\u4F20\u9012\u5230 SerializerFactory \u7684 Properties \u5BF9\u8C61\u6CA1\u6709 ''{0}'' \u5C5E\u6027\u3002" },

            {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
                "\u8B66\u544A: Java \u8FD0\u884C\u65F6\u4E0D\u652F\u6301\u7F16\u7801 ''{0}''\u3002" },


        };

        return contents;
    }
}
