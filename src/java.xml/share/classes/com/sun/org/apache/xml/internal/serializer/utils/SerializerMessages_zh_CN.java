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
                "消息关键字 ''{0}'' 不在消息类 ''{1}'' 中" },

            {   MsgKey.BAD_MSGFORMAT,
                "消息类 ''{1}'' 中消息 ''{0}'' 的格式化失败。" },

            {   MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
                "串行器类 ''{0}'' 不实现 org.xml.sax.ContentHandler。" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_FIND,
                    "找不到资源 [ {0} ]。\n {1}" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
                    "资源 [ {0} ] 无法加载: {1} \n {2} \t {3}" },

            {   MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
                    "缓冲区大小 <=0" },

            {   MsgKey.ER_INVALID_UTF16_SURROGATE,
                    "检测到无效的 UTF-16 代理: {0}?" },

            {   MsgKey.ER_OIERROR,
                "IO 错误" },

            {   MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
                "在生成子节点之后或在生成元素之前无法添加属性 {0}。将忽略属性。" },

            /*
             * Note to translators:  The stylesheet contained a reference to a
             * namespace prefix that was undefined.  The value of the substitution
             * text is the name of the prefix.
             */
            {   MsgKey.ER_NAMESPACE_PREFIX,
                "没有说明名称空间前缀 ''{0}''。" },

            /*
             * Note to translators:  This message is reported if the stylesheet
             * being processed attempted to construct an XML document with an
             * attribute in a place other than on an element.  The substitution text
             * specifies the name of the attribute.
             */
            {   MsgKey.ER_STRAY_ATTRIBUTE,
                "属性 ''{0}'' 在元素外部。" },

            /*
             * Note to translators:  As with the preceding message, a namespace
             * declaration has the form of an attribute and is only permitted to
             * appear on an element.  The substitution text {0} is the namespace
             * prefix and {1} is the URI that was being used in the erroneous
             * namespace declaration.
             */
            {   MsgKey.ER_STRAY_NAMESPACE,
                "名称空间声明 ''{0}''=''{1}'' 在元素外部。" },

            {   MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
                "无法加载 ''{0}'' (检查 CLASSPATH), 现在只使用默认值" },

            {   MsgKey.ER_ILLEGAL_CHARACTER,
                "尝试输出未以{1}的指定输出编码表示的整数值 {0} 的字符。" },

            {   MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
                "无法为输出方法 ''{1}'' 加载属性文件 ''{0}'' (检查 CLASSPATH)" },

            {   MsgKey.ER_INVALID_PORT,
                "无效的端口号" },

            {   MsgKey.ER_PORT_WHEN_HOST_NULL,
                "主机为空时, 无法设置端口" },

            {   MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
                "主机不是格式良好的地址" },

            {   MsgKey.ER_SCHEME_NOT_CONFORMANT,
                "方案不一致。" },

            {   MsgKey.ER_SCHEME_FROM_NULL_STRING,
                "无法从空字符串设置方案" },

            {   MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
                "路径包含无效的逃逸 序列" },

            {   MsgKey.ER_PATH_INVALID_CHAR,
                "路径包含无效的字符: {0}" },

            {   MsgKey.ER_FRAG_INVALID_CHAR,
                "片段包含无效的字符" },

            {   MsgKey.ER_FRAG_WHEN_PATH_NULL,
                "路径为空时, 无法设置片段" },

            {   MsgKey.ER_FRAG_FOR_GENERIC_URI,
                "只能为一般 URI 设置片段" },

            {   MsgKey.ER_NO_SCHEME_IN_URI,
                "在 URI 中找不到方案" },

            {   MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
                "无法以空参数初始化 URI" },

            {   MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
                "路径和片段中都无法指定片段" },

            {   MsgKey.ER_NO_QUERY_STRING_IN_PATH,
                "路径和查询字符串中不能指定查询字符串" },

            {   MsgKey.ER_NO_PORT_IF_NO_HOST,
                "如果没有指定主机, 则不可以指定端口" },

            {   MsgKey.ER_NO_USERINFO_IF_NO_HOST,
                "如果没有指定主机, 则不可以指定 Userinfo" },

            {   MsgKey.ER_XML_VERSION_NOT_SUPPORTED,
                "警告: 输出文档的版本应为 ''{0}''。不支持此版本的 XML。输出文档的版本将为 ''1.0''。" },

            {   MsgKey.ER_SCHEME_REQUIRED,
                "方案是必需的!" },

            /*
             * Note to translators:  The words 'Properties' and
             * 'SerializerFactory' in this message are Java class names
             * and should not be translated.
             */
            {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
                "传递到 SerializerFactory 的 Properties 对象没有 ''{0}'' 属性。" },

            {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
                "警告: Java 运行时不支持编码 ''{0}''。" },

             {MsgKey.ER_FEATURE_NOT_FOUND,
             "未识别参数 ''{0}''。"},

             {MsgKey.ER_FEATURE_NOT_SUPPORTED,
             "已识别参数 ''{0}'', 但无法设置请求的值。"},

             {MsgKey.ER_STRING_TOO_LONG,
             "生成的字符串太长, 不适合 DOMString: ''{0}''。"},

             {MsgKey.ER_TYPE_MISMATCH_ERR,
             "此参数名称的值类型与预期的值类型不兼容。"},

             {MsgKey.ER_NO_OUTPUT_SPECIFIED,
             "要将数据写入的输出目标为空值。"},

             {MsgKey.ER_UNSUPPORTED_ENCODING,
             "遇到不支持的编码。"},

             {MsgKey.ER_UNABLE_TO_SERIALIZE_NODE,
             "无法序列化节点。"},

             {MsgKey.ER_CDATA_SECTIONS_SPLIT,
             "CDATA 节包含一个或多个终止标记 ']]>'。"},

             {MsgKey.ER_WARNING_WF_NOT_CHECKED,
                 "无法创建格式合规性检查器的实例。格式合规性参数已设置为“真”, 但无法执行格式合规性检查。"
             },

             {MsgKey.ER_WF_INVALID_CHARACTER,
                 "节点 ''{0}'' 包含无效的 XML 字符。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_COMMENT,
                 "在注释中找到无效的 XML 字符 (Unicode: 0x{0})。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_PI,
                 "在处理指令数据中找到无效的 XML 字符 (Unicode: 0x{0})。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_CDATA,
                 "在 CDATA 节的内容中找到无效的 XML 字符 (Unicode: 0x{0})。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_TEXT,
                 "在节点的字符数据内容中找到无效的 XML 字符 (Unicode: 0x{0})。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_NODE_NAME,
                 "在名为 ''{1}'' 的{0}节点中找到无效的 XML 字符。"
             },

             { MsgKey.ER_WF_DASH_IN_COMMENT,
                 "注释中不允许出现字符串 \"--\"。"
             },

             {MsgKey.ER_WF_LT_IN_ATTVAL,
                 "与元素类型 \"{0}\" 相关联的 \"{1}\" 属性值不能包含 ''<'' 字符。"
             },

             {MsgKey.ER_WF_REF_TO_UNPARSED_ENT,
                 "不允许使用未解析的实体引用 \"&{0};\"。"
             },

             {MsgKey.ER_WF_REF_TO_EXTERNAL_ENT,
                 "属性值中不允许采用外部实体引用 \"&{0};\"。"
             },

             {MsgKey.ER_NS_PREFIX_CANNOT_BE_BOUND,
                 "前缀 \"{0}\" 无法绑定到名称空间 \"{1}\"。"
             },

             {MsgKey.ER_NULL_LOCAL_ELEMENT_NAME,
                 "元素 \"{0}\" 的本地名称为空值。"
             },

             {MsgKey.ER_NULL_LOCAL_ATTR_NAME,
                 "属性 \"{0}\" 的本地名称为空值。"
             },

             { MsgKey.ER_ELEM_UNBOUND_PREFIX_IN_ENTREF,
                 "实体节点 \"{0}\" 的替换文本包含带有未绑定前缀 \"{2}\" 的元素节点 \"{1}\"。"
             },

             { MsgKey.ER_ATTR_UNBOUND_PREFIX_IN_ENTREF,
                 "实体节点 \"{0}\" 的替换文本包含带有未绑定前缀 \"{2}\" 的属性节点 \"{1}\"。"
             },

             { MsgKey.ER_WRITING_INTERNAL_SUBSET,
                 "写入内部子集时出现错误。"
             },

        };

        return contents;
    }
}
