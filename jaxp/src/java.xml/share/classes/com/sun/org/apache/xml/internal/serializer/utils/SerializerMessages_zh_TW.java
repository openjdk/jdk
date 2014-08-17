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
/*
 * $Id: SerializerMessages_zh_TW.java /st_wptg_1.8.0.0.0jdk/2 2013/09/14 02:16:34 gmolloy Exp $
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
public class SerializerMessages_zh_TW extends ListResourceBundle {

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
                "\u8A0A\u606F\u7D22\u5F15\u9375 ''{0}'' \u7684\u8A0A\u606F\u985E\u5225\u4E0D\u662F ''{1}''" },

            {   MsgKey.BAD_MSGFORMAT,
                "\u8A0A\u606F\u985E\u5225 ''{1}'' \u4E2D\u7684\u8A0A\u606F ''{0}'' \u683C\u5F0F\u4E0D\u6B63\u78BA\u3002" },

            {   MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
                "serializer \u985E\u5225 ''{0}'' \u4E0D\u5BE6\u884C org.xml.sax.ContentHandler\u3002" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_FIND,
                    "\u627E\u4E0D\u5230\u8CC7\u6E90 [ {0} ]\u3002\n{1}" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
                    "\u7121\u6CD5\u8F09\u5165\u8CC7\u6E90 [ {0} ]: {1} \n {2} \t {3}" },

            {   MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
                    "\u7DE9\u885D\u5340\u5927\u5C0F <=0" },

            {   MsgKey.ER_INVALID_UTF16_SURROGATE,
                    "\u5075\u6E2C\u5230\u7121\u6548\u7684 UTF-16 \u4EE3\u7406: {0}\uFF1F" },

            {   MsgKey.ER_OIERROR,
                "IO \u932F\u8AA4" },

            {   MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
                "\u5728\u7522\u751F\u5B50\u9805\u7BC0\u9EDE\u4E4B\u5F8C\uFF0C\u6216\u5728\u7522\u751F\u5143\u7D20\u4E4B\u524D\uFF0C\u4E0D\u53EF\u65B0\u589E\u5C6C\u6027 {0}\u3002\u5C6C\u6027\u6703\u88AB\u5FFD\u7565\u3002" },

            /*
             * Note to translators:  The stylesheet contained a reference to a
             * namespace prefix that was undefined.  The value of the substitution
             * text is the name of the prefix.
             */
            {   MsgKey.ER_NAMESPACE_PREFIX,
                "\u5B57\u9996 ''{0}'' \u7684\u547D\u540D\u7A7A\u9593\u5C1A\u672A\u5BA3\u544A\u3002" },

            /*
             * Note to translators:  This message is reported if the stylesheet
             * being processed attempted to construct an XML document with an
             * attribute in a place other than on an element.  The substitution text
             * specifies the name of the attribute.
             */
            {   MsgKey.ER_STRAY_ATTRIBUTE,
                "\u5C6C\u6027 ''{0}'' \u5728\u5143\u7D20\u4E4B\u5916\u3002" },

            /*
             * Note to translators:  As with the preceding message, a namespace
             * declaration has the form of an attribute and is only permitted to
             * appear on an element.  The substitution text {0} is the namespace
             * prefix and {1} is the URI that was being used in the erroneous
             * namespace declaration.
             */
            {   MsgKey.ER_STRAY_NAMESPACE,
                "\u547D\u540D\u7A7A\u9593\u5BA3\u544A ''{0}''=''{1}'' \u8D85\u51FA\u5143\u7D20\u5916\u3002" },

            {   MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
                "\u7121\u6CD5\u8F09\u5165 ''{0}'' (\u6AA2\u67E5 CLASSPATH)\uFF0C\u76EE\u524D\u53EA\u4F7F\u7528\u9810\u8A2D\u503C" },

            {   MsgKey.ER_ILLEGAL_CHARACTER,
                "\u5617\u8A66\u8F38\u51FA\u6574\u6578\u503C {0} \u7684\u5B57\u5143\uFF0C\u4F46\u662F\u5B83\u4E0D\u662F\u4EE5\u6307\u5B9A\u7684 {1} \u8F38\u51FA\u7DE8\u78BC\u5448\u73FE\u3002" },

            {   MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
                "\u7121\u6CD5\u8F09\u5165\u8F38\u51FA\u65B9\u6CD5 ''{1}'' \u7684\u5C6C\u6027\u6A94 ''{0}'' (\u6AA2\u67E5 CLASSPATH)" },

            {   MsgKey.ER_INVALID_PORT,
                "\u7121\u6548\u7684\u9023\u63A5\u57E0\u865F\u78BC" },

            {   MsgKey.ER_PORT_WHEN_HOST_NULL,
                "\u4E3B\u6A5F\u70BA\u7A7A\u503C\u6642\uFF0C\u7121\u6CD5\u8A2D\u5B9A\u9023\u63A5\u57E0" },

            {   MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
                "\u4E3B\u6A5F\u6C92\u6709\u5B8C\u6574\u7684\u4F4D\u5740" },

            {   MsgKey.ER_SCHEME_NOT_CONFORMANT,
                "\u914D\u7F6E\u4E0D\u4E00\u81F4\u3002" },

            {   MsgKey.ER_SCHEME_FROM_NULL_STRING,
                "\u7121\u6CD5\u5F9E\u7A7A\u503C\u5B57\u4E32\u8A2D\u5B9A\u914D\u7F6E" },

            {   MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
                "\u8DEF\u5F91\u5305\u542B\u7121\u6548\u7684\u9041\u96E2\u5E8F\u5217" },

            {   MsgKey.ER_PATH_INVALID_CHAR,
                "\u8DEF\u5F91\u5305\u542B\u7121\u6548\u7684\u5B57\u5143: {0}" },

            {   MsgKey.ER_FRAG_INVALID_CHAR,
                "\u7247\u6BB5\u5305\u542B\u7121\u6548\u7684\u5B57\u5143" },

            {   MsgKey.ER_FRAG_WHEN_PATH_NULL,
                "\u8DEF\u5F91\u70BA\u7A7A\u503C\u6642\uFF0C\u7121\u6CD5\u8A2D\u5B9A\u7247\u6BB5" },

            {   MsgKey.ER_FRAG_FOR_GENERIC_URI,
                "\u53EA\u80FD\u5C0D\u4E00\u822C URI \u8A2D\u5B9A\u7247\u6BB5" },

            {   MsgKey.ER_NO_SCHEME_IN_URI,
                "\u5728 URI \u627E\u4E0D\u5230\u914D\u7F6E" },

            {   MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
                "\u7121\u6CD5\u4EE5\u7A7A\u767D\u53C3\u6578\u8D77\u59CB\u8A2D\u5B9A URI" },

            {   MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
                "\u8DEF\u5F91\u548C\u7247\u6BB5\u4E0D\u80FD\u540C\u6642\u6307\u5B9A\u7247\u6BB5" },

            {   MsgKey.ER_NO_QUERY_STRING_IN_PATH,
                "\u5728\u8DEF\u5F91\u53CA\u67E5\u8A62\u5B57\u4E32\u4E2D\u4E0D\u53EF\u6307\u5B9A\u67E5\u8A62\u5B57\u4E32" },

            {   MsgKey.ER_NO_PORT_IF_NO_HOST,
                "\u5982\u679C\u6C92\u6709\u6307\u5B9A\u4E3B\u6A5F\uFF0C\u4E0D\u53EF\u6307\u5B9A\u9023\u63A5\u57E0" },

            {   MsgKey.ER_NO_USERINFO_IF_NO_HOST,
                "\u5982\u679C\u6C92\u6709\u6307\u5B9A\u4E3B\u6A5F\uFF0C\u4E0D\u53EF\u6307\u5B9A Userinfo" },

            {   MsgKey.ER_XML_VERSION_NOT_SUPPORTED,
                "\u8B66\u544A:  \u8981\u6C42\u7684\u8F38\u51FA\u6587\u4EF6\u7248\u672C\u70BA ''{0}''\u3002\u4E0D\u652F\u63F4\u6B64\u7248\u672C\u7684 XML\u3002\u8F38\u51FA\u6587\u4EF6\u7684\u7248\u672C\u5C07\u6703\u662F ''1.0''\u3002" },

            {   MsgKey.ER_SCHEME_REQUIRED,
                "\u5FC5\u9808\u6709\u914D\u7F6E\uFF01" },

            /*
             * Note to translators:  The words 'Properties' and
             * 'SerializerFactory' in this message are Java class names
             * and should not be translated.
             */
            {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
                "\u50B3\u905E\u7D66 SerializerFactory \u7684 Properties \u7269\u4EF6\u6C92\u6709 ''{0}'' \u5C6C\u6027\u3002" },

            {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
                "\u8B66\u544A:  Java Runtime \u4E0D\u652F\u63F4\u7DE8\u78BC ''{0}''\u3002" },

             {MsgKey.ER_FEATURE_NOT_FOUND,
             "\u7121\u6cd5\u8fa8\u8b58\u53c3\u6578 ''{0}''\u3002"},

             {MsgKey.ER_FEATURE_NOT_SUPPORTED,
             "\u53ef\u8fa8\u8b58 ''{0}'' \u53c3\u6578\uff0c\u4f46\u6240\u8981\u6c42\u7684\u503c\u7121\u6cd5\u8a2d\u5b9a\u3002"},

             {MsgKey.ER_STRING_TOO_LONG,
             "\u7d50\u679c\u5b57\u4e32\u904e\u9577\uff0c\u7121\u6cd5\u7f6e\u5165 DOMString: ''{0}'' \u4e2d\u3002"},

             {MsgKey.ER_TYPE_MISMATCH_ERR,
             "\u9019\u500b\u53c3\u6578\u540d\u7a31\u7684\u503c\u985e\u578b\u8207\u671f\u671b\u503c\u985e\u578b\u4e0d\u76f8\u5bb9\u3002"},

             {MsgKey.ER_NO_OUTPUT_SPECIFIED,
             "\u8cc7\u6599\u8981\u5beb\u5165\u7684\u8f38\u51fa\u76ee\u7684\u5730\u70ba\u7a7a\u503c\u3002"},

             {MsgKey.ER_UNSUPPORTED_ENCODING,
             "\u767c\u73fe\u4e0d\u652f\u63f4\u7684\u7de8\u78bc\u3002"},

             {MsgKey.ER_UNABLE_TO_SERIALIZE_NODE,
             "\u7bc0\u9ede\u7121\u6cd5\u5e8f\u5217\u5316\u3002"},

             {MsgKey.ER_CDATA_SECTIONS_SPLIT,
             "CDATA \u5340\u6bb5\u5305\u542b\u4e00\u6216\u591a\u500b\u7d42\u6b62\u6a19\u8a18 ']]>'\u3002"},

             {MsgKey.ER_WARNING_WF_NOT_CHECKED,
                 "\u7121\u6cd5\u5efa\u7acb\u300c\u5f62\u5f0f\u5b8c\u6574\u300d\u6aa2\u67e5\u7a0b\u5f0f\u7684\u5be6\u4f8b\u3002Well-formed \u53c3\u6578\u96d6\u8a2d\u70ba true\uff0c\u4f46\u7121\u6cd5\u57f7\u884c\u5f62\u5f0f\u5b8c\u6574\u6aa2\u67e5\u3002"
             },

             {MsgKey.ER_WF_INVALID_CHARACTER,
                 "\u7bc0\u9ede ''{0}'' \u5305\u542b\u7121\u6548\u7684 XML \u5b57\u5143\u3002"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_COMMENT,
                 "\u5728\u8a3b\u89e3\u4e2d\u767c\u73fe\u7121\u6548\u7684 XML \u5b57\u5143 (Unicode: 0x{0})\u3002"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_PI,
                 "\u5728\u8655\u7406\u7a0b\u5e8f instructiondata \u4e2d\u767c\u73fe\u7121\u6548\u7684 XML \u5b57\u5143 (Unicode: 0x{0})\u3002"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_CDATA,
                 "\u5728 CDATASection \u7684\u5167\u5bb9\u4e2d\u767c\u73fe\u7121\u6548\u7684 XML \u5b57\u5143 (Unicode: 0x{0})\u3002"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_TEXT,
                 "\u5728\u7bc0\u9ede\u7684\u5b57\u5143\u8cc7\u6599\u5167\u5bb9\u4e2d\u767c\u73fe\u7121\u6548\u7684 XML \u5b57\u5143 (Unicode: 0x{0})\u3002"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_NODE_NAME,
                 "\u5728\u540d\u70ba ''{1}'' \u7684 ''{0}'' \u4e2d\u767c\u73fe\u7121\u6548\u7684 XML \u5b57\u5143\u3002"
             },

             { MsgKey.ER_WF_DASH_IN_COMMENT,
                 "\u8a3b\u89e3\u4e2d\u4e0d\u5141\u8a31\u4f7f\u7528\u5b57\u4e32 \"--\"\u3002"
             },

             {MsgKey.ER_WF_LT_IN_ATTVAL,
                 "\u8207\u5143\u7d20\u985e\u578b \"{0}\" \u76f8\u95dc\u806f\u7684\u5c6c\u6027 \"{1}\" \u503c\u4e0d\u53ef\u5305\u542b ''<'' \u5b57\u5143\u3002"
             },

             {MsgKey.ER_WF_REF_TO_UNPARSED_ENT,
                 "\u4e0d\u5141\u8a31\u4f7f\u7528\u672a\u5256\u6790\u7684\u5be6\u9ad4\u53c3\u7167 \"&{0};\"\u3002"
             },

             {MsgKey.ER_WF_REF_TO_EXTERNAL_ENT,
                 "\u5c6c\u6027\u503c\u4e2d\u4e0d\u5141\u8a31\u4f7f\u7528\u5916\u90e8\u5be6\u9ad4\u53c3\u7167 \"&{0};\"\u3002"
             },

             {MsgKey.ER_NS_PREFIX_CANNOT_BE_BOUND,
                 "\u5b57\u9996 \"{0}\" \u7121\u6cd5\u9023\u7d50\u5230\u540d\u7a31\u7a7a\u9593 \"{1}\"\u3002"
             },

             {MsgKey.ER_NULL_LOCAL_ELEMENT_NAME,
                 "\u5143\u7d20 \"{0}\" \u7684\u672c\u7aef\u540d\u7a31\u662f\u7a7a\u503c\u3002"
             },

             {MsgKey.ER_NULL_LOCAL_ATTR_NAME,
                 "\u5c6c\u6027 \"{0}\" \u7684\u672c\u7aef\u540d\u7a31\u662f\u7a7a\u503c\u3002"
             },

             { MsgKey.ER_ELEM_UNBOUND_PREFIX_IN_ENTREF,
                 "\u5be6\u9ad4\u7bc0\u9ede \"{0}\" \u7684\u53d6\u4ee3\u6587\u5b57\u5305\u542b\u9644\u6709\u5df2\u5207\u65b7\u9023\u7d50\u5b57\u9996 \"{2}\" \u7684\u5143\u7d20\u7bc0\u9ede \"{1}\"\u3002"
             },

             { MsgKey.ER_ATTR_UNBOUND_PREFIX_IN_ENTREF,
                 "\u5be6\u9ad4\u7bc0\u9ede \"{0}\" \u7684\u53d6\u4ee3\u6587\u5b57\u5305\u542b\u9644\u6709\u5df2\u5207\u65b7\u9023\u7d50\u5b57\u9996 \"{2}\" \u7684\u5c6c\u6027\u7bc0\u9ede \"{1}\"\u3002"
             },

        };

        return contents;
    }
}
