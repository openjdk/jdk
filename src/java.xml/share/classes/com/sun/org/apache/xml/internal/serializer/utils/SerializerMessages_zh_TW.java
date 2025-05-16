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
                "訊息索引鍵 ''{0}'' 的訊息類別不是 ''{1}''" },

            {   MsgKey.BAD_MSGFORMAT,
                "訊息類別 ''{1}'' 中的訊息 ''{0}'' 格式不正確。" },

            {   MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
                "serializer 類別 ''{0}'' 不實行 org.xml.sax.ContentHandler。" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_FIND,
                    "找不到資源 [ {0} ]。\n{1}" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
                    "無法載入資源 [ {0} ]: {1} \n {2} \t {3}" },

            {   MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
                    "緩衝區大小 <=0" },

            {   MsgKey.ER_INVALID_UTF16_SURROGATE,
                    "偵測到無效的 UTF-16 代理: {0}？" },

            {   MsgKey.ER_OIERROR,
                "IO 錯誤" },

            {   MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
                "在產生子項節點之後，或在產生元素之前，不可新增屬性 {0}。屬性會被忽略。" },

            /*
             * Note to translators:  The stylesheet contained a reference to a
             * namespace prefix that was undefined.  The value of the substitution
             * text is the name of the prefix.
             */
            {   MsgKey.ER_NAMESPACE_PREFIX,
                "字首 ''{0}'' 的命名空間尚未宣告。" },

            /*
             * Note to translators:  This message is reported if the stylesheet
             * being processed attempted to construct an XML document with an
             * attribute in a place other than on an element.  The substitution text
             * specifies the name of the attribute.
             */
            {   MsgKey.ER_STRAY_ATTRIBUTE,
                "屬性 ''{0}'' 在元素之外。" },

            /*
             * Note to translators:  As with the preceding message, a namespace
             * declaration has the form of an attribute and is only permitted to
             * appear on an element.  The substitution text {0} is the namespace
             * prefix and {1} is the URI that was being used in the erroneous
             * namespace declaration.
             */
            {   MsgKey.ER_STRAY_NAMESPACE,
                "命名空間宣告 ''{0}''=''{1}'' 超出元素外。" },

            {   MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
                "無法載入 ''{0}'' (檢查 CLASSPATH)，目前只使用預設值" },

            {   MsgKey.ER_ILLEGAL_CHARACTER,
                "嘗試輸出整數值 {0} 的字元，但是它不是以指定的 {1} 輸出編碼呈現。" },

            {   MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
                "無法載入輸出方法 ''{1}'' 的屬性檔 ''{0}'' (檢查 CLASSPATH)" },

            {   MsgKey.ER_INVALID_PORT,
                "無效的連接埠號碼" },

            {   MsgKey.ER_PORT_WHEN_HOST_NULL,
                "主機為空值時，無法設定連接埠" },

            {   MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
                "主機沒有完整的位址" },

            {   MsgKey.ER_SCHEME_NOT_CONFORMANT,
                "配置不一致。" },

            {   MsgKey.ER_SCHEME_FROM_NULL_STRING,
                "無法從空值字串設定配置" },

            {   MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
                "路徑包含無效的遁離序列" },

            {   MsgKey.ER_PATH_INVALID_CHAR,
                "路徑包含無效的字元: {0}" },

            {   MsgKey.ER_FRAG_INVALID_CHAR,
                "片段包含無效的字元" },

            {   MsgKey.ER_FRAG_WHEN_PATH_NULL,
                "路徑為空值時，無法設定片段" },

            {   MsgKey.ER_FRAG_FOR_GENERIC_URI,
                "只能對一般 URI 設定片段" },

            {   MsgKey.ER_NO_SCHEME_IN_URI,
                "在 URI 找不到配置" },

            {   MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
                "無法以空白參數起始設定 URI" },

            {   MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
                "路徑和片段不能同時指定片段" },

            {   MsgKey.ER_NO_QUERY_STRING_IN_PATH,
                "在路徑及查詢字串中不可指定查詢字串" },

            {   MsgKey.ER_NO_PORT_IF_NO_HOST,
                "如果沒有指定主機，不可指定連接埠" },

            {   MsgKey.ER_NO_USERINFO_IF_NO_HOST,
                "如果沒有指定主機，不可指定 Userinfo" },

            {   MsgKey.ER_XML_VERSION_NOT_SUPPORTED,
                "警告:  要求的輸出文件版本為 ''{0}''。不支援此版本的 XML。輸出文件的版本將會是 ''1.0''。" },

            {   MsgKey.ER_SCHEME_REQUIRED,
                "配置是必要項目！" },

            /*
             * Note to translators:  The words 'Properties' and
             * 'SerializerFactory' in this message are Java class names
             * and should not be translated.
             */
            {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
                "傳遞給 SerializerFactory 的 Properties 物件沒有 ''{0}'' 屬性。" },

            {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
                "警告:  Java Runtime 不支援編碼 ''{0}''。" },

             {MsgKey.ER_FEATURE_NOT_FOUND,
             "無法辨識參數 ''{0}''。"},

             {MsgKey.ER_FEATURE_NOT_SUPPORTED,
             "可辨識參數 ''{0}''，但無法設定要求的值。"},

             {MsgKey.ER_STRING_TOO_LONG,
             "結果字串太長，無法納入 DOMString: ''{0}''。"},

             {MsgKey.ER_TYPE_MISMATCH_ERR,
             "此參數名稱的值類型與預期的值類型不相容。"},

             {MsgKey.ER_NO_OUTPUT_SPECIFIED,
             "供寫入資料的輸出目的地為空值。"},

             {MsgKey.ER_UNSUPPORTED_ENCODING,
             "發現不支援的編碼。"},

             {MsgKey.ER_UNABLE_TO_SERIALIZE_NODE,
             "無法序列化此節點。"},

             {MsgKey.ER_CDATA_SECTIONS_SPLIT,
             "CDATA 段落包含一或多個終止標記 ']]>'。"},

             {MsgKey.ER_WARNING_WF_NOT_CHECKED,
                 "無法建立 Well-Formedness 檢查程式執行處理。well-formed 參數設為 true，但無法執行 well-formedness 檢查。"
             },

             {MsgKey.ER_WF_INVALID_CHARACTER,
                 "節點 ''{0}'' 包含無效的 XML 字元。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_COMMENT,
                 "在註解中找到無效的 XML 字元 (Unicode: 0x{0})。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_PI,
                 "在處理的指示資料中發現無效的 XML 字元 (Unicode: 0x{0})。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_CDATA,
                 "在 CDATASection 的內容中發現無效的 XML 字元 (Unicode: 0x{0})。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_TEXT,
                 "在節點的字元資料內容中發現無效的 XML 字元 (Unicode: 0x{0})。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_NODE_NAME,
                 "在名稱為 ''{1}'' 的 {0} 節點中發現無效的 XML 字元。"
             },

             { MsgKey.ER_WF_DASH_IN_COMMENT,
                 "註解不允許字串 \"--\"。"
             },

             {MsgKey.ER_WF_LT_IN_ATTVAL,
                 "關聯元素類型 \"{0}\" 之屬性 \"{1}\" 的值不可包含 ''<'' 字元。"
             },

             {MsgKey.ER_WF_REF_TO_UNPARSED_ENT,
                 "不允許未剖析的實體參照 \"&{0};\"。"
             },

             {MsgKey.ER_WF_REF_TO_EXTERNAL_ENT,
                 "屬性值不允許參照外部實體 \"&{0};\"。"
             },

             {MsgKey.ER_NS_PREFIX_CANNOT_BE_BOUND,
                 "無法將前置碼 \"{0}\" 連結至命名空間 \"{1}\"。"
             },

             {MsgKey.ER_NULL_LOCAL_ELEMENT_NAME,
                 "元素 \"{0}\" 的區域名稱為空值。"
             },

             {MsgKey.ER_NULL_LOCAL_ATTR_NAME,
                 "屬性 \"{0}\" 的區域名稱為空值。"
             },

             { MsgKey.ER_ELEM_UNBOUND_PREFIX_IN_ENTREF,
                 "實體節點 \"{0}\" 的取代文字包含具有未連結前置碼 \"{2}\" 的元素節點 \"{1}\"。"
             },

             { MsgKey.ER_ATTR_UNBOUND_PREFIX_IN_ENTREF,
                 "實體節點 \"{0}\" 的取代文字包含具有未連結前置碼 \"{2}\" 的屬性節點 \"{1}\"。"
             },

             { MsgKey.ER_WRITING_INTERNAL_SUBSET,
                 "寫入內部子集時發生錯誤。"
             },

        };

        return contents;
    }
}
