/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2004 The Apache Software Foundation.
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
 * $Id: SerializerMessages_zh_CN.java,v 1.1.4.1 2005/09/08 11:03:18 suresh_emailid Exp $
 */

package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;

public class SerializerMessages_zh_CN extends ListResourceBundle {
  public Object[][] getContents() {
    Object[][] contents =  new Object[][] {
        // BAD_MSGKEY needs translation
        // BAD_MSGFORMAT needs translation
      { MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
        "\u4e32\u884c\u5668\u7c7b\u201c{0}\u201d\u4e0d\u5b9e\u73b0 org.xml.sax.ContentHandler."},

      { MsgKey.ER_RESOURCE_COULD_NOT_FIND,
        "\u627e\u4e0d\u5230\u8d44\u6e90 [ {0} ]\u3002\n {1}"},

      { MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
        "\u8d44\u6e90 [ {0} ] \u65e0\u6cd5\u88c5\u5165\uff1a{1} \n {2} \n {3}"},

      { MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "\u7f13\u51b2\u533a\u5927\u5c0f <=0"},

      { MsgKey.ER_INVALID_UTF16_SURROGATE,
        "\u68c0\u6d4b\u5230\u65e0\u6548\u7684 UTF-16 \u66ff\u4ee3\u8005\uff1a{0}\uff1f"},

      { MsgKey.ER_OIERROR,
        "IO \u9519\u8bef"},

      { MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
        "\u5728\u751f\u6210\u5b50\u8282\u70b9\u4e4b\u540e\u6216\u5728\u751f\u6210\u5143\u7d20\u4e4b\u524d\u65e0\u6cd5\u6dfb\u52a0\u5c5e\u6027 {0}\u3002\u5c06\u5ffd\u7565\u5c5e\u6027\u3002"},

      { MsgKey.ER_NAMESPACE_PREFIX,
        "\u6ca1\u6709\u8bf4\u660e\u540d\u79f0\u7a7a\u95f4\u524d\u7f00\u201c{0}\u201d\u3002"},

        // ER_STRAY_ATTRIBUTE needs translation
      { MsgKey.ER_STRAY_NAMESPACE,
        "\u540d\u79f0\u7a7a\u95f4\u8bf4\u660e\u201c{0}\u201d=\u201c{1}\u201d\u5728\u5143\u7d20\u5916\u3002"},

      { MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
        "\u65e0\u6cd5\u88c5\u5165\u201c{0}\u201d\uff08\u68c0\u67e5 CLASSPATH\uff09\uff0c\u73b0\u5728\u53ea\u4f7f\u7528\u7f3a\u7701\u503c"},

        // ER_ILLEGAL_CHARACTER needs translation
      { MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
        "\u65e0\u6cd5\u4e3a\u8f93\u51fa\u65b9\u6cd5\u201c{1}\u201d\u88c5\u8f7d\u5c5e\u6027\u6587\u4ef6\u201c{0}\u201d\uff08\u68c0\u67e5 CLASSPATH\uff09"},

      { MsgKey.ER_INVALID_PORT,
        "\u65e0\u6548\u7684\u7aef\u53e3\u53f7"},

      { MsgKey.ER_PORT_WHEN_HOST_NULL,
        "\u4e3b\u673a\u4e3a\u7a7a\u65f6\uff0c\u65e0\u6cd5\u8bbe\u7f6e\u7aef\u53e3"},

      { MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
        "\u4e3b\u673a\u4e0d\u662f\u683c\u5f0f\u826f\u597d\u7684\u5730\u5740"},

      { MsgKey.ER_SCHEME_NOT_CONFORMANT,
        "\u6a21\u5f0f\u4e0d\u4e00\u81f4\u3002"},

      { MsgKey.ER_SCHEME_FROM_NULL_STRING,
        "\u65e0\u6cd5\u4ece\u7a7a\u5b57\u7b26\u4e32\u8bbe\u7f6e\u6a21\u5f0f"},

      { MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
        "\u8def\u5f84\u5305\u542b\u65e0\u6548\u7684\u8f6c\u4e49\u5e8f\u5217"},

      { MsgKey.ER_PATH_INVALID_CHAR,
        "\u8def\u5f84\u5305\u542b\u975e\u6cd5\u5b57\u7b26\uff1a{0}"},

      { MsgKey.ER_FRAG_INVALID_CHAR,
        "\u7247\u6bb5\u5305\u542b\u65e0\u6548\u7684\u5b57\u7b26"},

      { MsgKey.ER_FRAG_WHEN_PATH_NULL,
        "\u8def\u5f84\u4e3a\u7a7a\u65f6\uff0c\u65e0\u6cd5\u8bbe\u7f6e\u7247\u6bb5"},

      { MsgKey.ER_FRAG_FOR_GENERIC_URI,
        "\u53ea\u80fd\u4e3a\u4e00\u822c URI \u8bbe\u7f6e\u7247\u6bb5"},

      { MsgKey.ER_NO_SCHEME_IN_URI,
        "\u5728 URI \u4e2d\u627e\u4e0d\u5230\u6a21\u5f0f\uff1a{0}"},

      { MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
        "\u65e0\u6cd5\u4ee5\u7a7a\u53c2\u6570\u521d\u59cb\u5316 URI"},

      { MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
        "\u8def\u5f84\u548c\u7247\u6bb5\u4e2d\u90fd\u65e0\u6cd5\u6307\u5b9a\u7247\u6bb5"},

      { MsgKey.ER_NO_QUERY_STRING_IN_PATH,
        "\u8def\u5f84\u548c\u67e5\u8be2\u5b57\u7b26\u4e32\u4e2d\u4e0d\u80fd\u6307\u5b9a\u67e5\u8be2\u5b57\u7b26\u4e32"},

      { MsgKey.ER_NO_PORT_IF_NO_HOST,
        "\u5982\u679c\u6ca1\u6709\u6307\u5b9a\u4e3b\u673a\uff0c\u5219\u4e0d\u53ef\u4ee5\u6307\u5b9a\u7aef\u53e3"},

      { MsgKey.ER_NO_USERINFO_IF_NO_HOST,
        "\u5982\u679c\u6ca1\u6709\u6307\u5b9a\u4e3b\u673a\uff0c\u5219\u4e0d\u53ef\u4ee5\u6307\u5b9a Userinfo"},

      { MsgKey.ER_SCHEME_REQUIRED,
        "\u6a21\u5f0f\u662f\u5fc5\u9700\u7684\uff01"}

    };
    return contents;
  }
}
