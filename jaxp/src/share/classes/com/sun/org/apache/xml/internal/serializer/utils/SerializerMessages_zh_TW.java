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
 * $Id: SerializerMessages_zh_TW.java,v 1.1.4.1 2005/09/08 11:03:18 suresh_emailid Exp $
 */

package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;

public class SerializerMessages_zh_TW extends ListResourceBundle {
  public Object[][] getContents() {
    Object[][] contents =  new Object[][] {
        // BAD_MSGKEY needs translation
        // BAD_MSGFORMAT needs translation
      { MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
        "serializer \u985e\u5225 ''{0}'' \u4e0d\u5be6\u4f5c org.xml.sax.ContentHandler\u3002"},

      { MsgKey.ER_RESOURCE_COULD_NOT_FIND,
        "\u627e\u4e0d\u5230\u8cc7\u6e90 [ {0} ]\u3002\n{1}"},

      { MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
        "\u7121\u6cd5\u8f09\u5165\u8cc7\u6e90 [ {0} ]\uff1a{1} \n {2} \n {3}"},

      { MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "\u7de9\u885d\u5340\u5927\u5c0f <=0"},

      { MsgKey.ER_INVALID_UTF16_SURROGATE,
        "\u5075\u6e2c\u5230\u7121\u6548\u7684 UTF-16 \u4ee3\u7406\uff1a{0}?"},

      { MsgKey.ER_OIERROR,
        "IO \u932f\u8aa4"},

      { MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
        "\u5728\u7522\u751f\u5b50\u9805\u7bc0\u9ede\u4e4b\u5f8c\uff0c\u6216\u5728\u7522\u751f\u5143\u7d20\u4e4b\u524d\uff0c\u4e0d\u53ef\u65b0\u589e\u5c6c\u6027 {0}\u3002\u5c6c\u6027\u6703\u88ab\u5ffd\u7565\u3002"},

      { MsgKey.ER_NAMESPACE_PREFIX,
        "\u5b57\u9996 ''{0}'' \u7684\u540d\u7a31\u7a7a\u9593\u5c1a\u672a\u5ba3\u544a\u3002"},

        // ER_STRAY_ATTRIBUTE needs translation
      { MsgKey.ER_STRAY_NAMESPACE,
        "\u540d\u7a31\u7a7a\u9593\u5ba3\u544a ''{0}''=''{1}'' \u8d85\u51fa\u5143\u7d20\u5916\u3002"},

      { MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
        "\u7121\u6cd5\u8f09\u5165 ''{0}''\uff08\u6aa2\u67e5 CLASSPATH\uff09\uff0c\u76ee\u524d\u53ea\u4f7f\u7528\u9810\u8a2d\u503c"},

        // ER_ILLEGAL_CHARACTER needs translation
      { MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
        "\u7121\u6cd5\u8f09\u5165\u8f38\u51fa\u65b9\u6cd5 ''{1}'' \u7684\u5167\u5bb9\u6a94 ''{0}''\uff08\u6aa2\u67e5 CLASSPATH\uff09"},

      { MsgKey.ER_INVALID_PORT,
        "\u7121\u6548\u7684\u57e0\u7de8\u865f"},

      { MsgKey.ER_PORT_WHEN_HOST_NULL,
        "\u4e3b\u6a5f\u70ba\u7a7a\u503c\u6642\uff0c\u7121\u6cd5\u8a2d\u5b9a\u57e0"},

      { MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
        "\u4e3b\u6a5f\u6c92\u6709\u5b8c\u6574\u7684\u4f4d\u5740"},

      { MsgKey.ER_SCHEME_NOT_CONFORMANT,
        "\u7db1\u8981\u4e0d\u662f conformant\u3002"},

      { MsgKey.ER_SCHEME_FROM_NULL_STRING,
        "\u7121\u6cd5\u5f9e\u7a7a\u5b57\u4e32\u8a2d\u5b9a\u7db1\u8981"},

      { MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
        "\u8def\u5f91\u5305\u542b\u7121\u6548\u7684\u8df3\u812b\u5b57\u5143"},

      { MsgKey.ER_PATH_INVALID_CHAR,
        "\u8def\u5f91\u5305\u542b\u7121\u6548\u7684\u5b57\u5143\uff1a{0}"},

      { MsgKey.ER_FRAG_INVALID_CHAR,
        "\u7247\u6bb5\u5305\u542b\u7121\u6548\u7684\u5b57\u5143"},

      { MsgKey.ER_FRAG_WHEN_PATH_NULL,
        "\u8def\u5f91\u70ba\u7a7a\u503c\u6642\uff0c\u7121\u6cd5\u8a2d\u5b9a\u7247\u6bb5"},

      { MsgKey.ER_FRAG_FOR_GENERIC_URI,
        "\u53ea\u80fd\u5c0d\u901a\u7528\u7684 URI \u8a2d\u5b9a\u7247\u6bb5"},

      { MsgKey.ER_NO_SCHEME_IN_URI,
        "\u5728 URI\uff1a{0} \u627e\u4e0d\u5230\u7db1\u8981"},

      { MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
        "\u7121\u6cd5\u4ee5\u7a7a\u767d\u53c3\u6578\u8d77\u59cb\u8a2d\u5b9a URI"},

      { MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
        "\u7247\u6bb5\u7121\u6cd5\u540c\u6642\u5728\u8def\u5f91\u548c\u7247\u6bb5\u4e2d\u6307\u5b9a"},

      { MsgKey.ER_NO_QUERY_STRING_IN_PATH,
        "\u5728\u8def\u5f91\u53ca\u67e5\u8a62\u5b57\u4e32\u4e2d\u4e0d\u53ef\u6307\u5b9a\u67e5\u8a62\u5b57\u4e32"},

      { MsgKey.ER_NO_PORT_IF_NO_HOST,
        "\u5982\u679c\u6c92\u6709\u6307\u5b9a\u4e3b\u6a5f\uff0c\u4e0d\u53ef\u6307\u5b9a\u57e0"},

      { MsgKey.ER_NO_USERINFO_IF_NO_HOST,
        "\u5982\u679c\u6c92\u6709\u6307\u5b9a\u4e3b\u6a5f\uff0c\u4e0d\u53ef\u6307\u5b9a Userinfo"},

      { MsgKey.ER_SCHEME_REQUIRED,
        "\u7db1\u8981\u662f\u5fc5\u9700\u7684\uff01"}

    };
    return contents;
  }
}
