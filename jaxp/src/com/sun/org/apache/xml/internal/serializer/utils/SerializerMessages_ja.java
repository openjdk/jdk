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
 * $Id: SerializerMessages_ja.java,v 1.1.4.1 2005/09/08 11:03:16 suresh_emailid Exp $
 */

package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;

public class SerializerMessages_ja extends ListResourceBundle {
  public Object[][] getContents() {
    Object[][] contents =  new Object[][] {
        // BAD_MSGKEY needs translation
        // BAD_MSGFORMAT needs translation
      { MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
        "\u30b7\u30ea\u30a2\u30e9\u30a4\u30b6\u30fc\u30fb\u30af\u30e9\u30b9 ''{0}'' \u306f org.xml.sax.ContentHandler \u3092\u30a4\u30f3\u30d7\u30ea\u30e1\u30f3\u30c8\u3057\u307e\u305b\u3093\u3002"},

      { MsgKey.ER_RESOURCE_COULD_NOT_FIND,
        "\u30ea\u30bd\u30fc\u30b9 [ {0} ] \u306f\u898b\u3064\u304b\u308a\u307e\u305b\u3093\u3067\u3057\u305f\u3002\n {1}"},

      { MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
        "\u30ea\u30bd\u30fc\u30b9 [ {0} ] \u3092\u30ed\u30fc\u30c9\u3067\u304d\u307e\u305b\u3093\u3067\u3057\u305f: {1} \n {2} \n {3}"},

      { MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "\u30d0\u30c3\u30d5\u30a1\u30fc\u30fb\u30b5\u30a4\u30ba <=0"},

      { MsgKey.ER_INVALID_UTF16_SURROGATE,
        "\u7121\u52b9\u306a UTF-16 \u30b5\u30ed\u30b2\u30fc\u30c8\u304c\u691c\u51fa\u3055\u308c\u307e\u3057\u305f: {0} ?"},

      { MsgKey.ER_OIERROR,
        "\u5165\u51fa\u529b\u30a8\u30e9\u30fc"},

      { MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
        "\u4e0b\u4f4d\u30ce\u30fc\u30c9\u306e\u5f8c\u307e\u305f\u306f\u30a8\u30ec\u30e1\u30f3\u30c8\u304c\u751f\u6210\u3055\u308c\u308b\u524d\u306b\u5c5e\u6027 {0} \u3092\u8ffd\u52a0\u3067\u304d\u307e\u305b\u3093\u3002\u5c5e\u6027\u306f\u7121\u8996\u3055\u308c\u307e\u3059\u3002"},

      { MsgKey.ER_NAMESPACE_PREFIX,
        "\u63a5\u982d\u90e8 ''{0}'' \u306e\u30cd\u30fc\u30e0\u30fb\u30b9\u30da\u30fc\u30b9\u304c\u5ba3\u8a00\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002"},

        // ER_STRAY_ATTRIBUTE needs translation
      { MsgKey.ER_STRAY_NAMESPACE,
        "\u30cd\u30fc\u30e0\u30fb\u30b9\u30da\u30fc\u30b9\u5ba3\u8a00 ''{0}''=''{1}'' \u304c\u30a8\u30ec\u30e1\u30f3\u30c8\u306e\u5916\u5074\u3067\u3059\u3002"},

      { MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
        "''{0}'' \u3092\u30ed\u30fc\u30c9\u3067\u304d\u307e\u305b\u3093\u3067\u3057\u305f (CLASSPATH \u3092\u8abf\u3079\u3066\u304f\u3060\u3055\u3044)\u3002\u73fe\u5728\u306f\u5358\u306b\u30c7\u30d5\u30a9\u30eb\u30c8\u3092\u4f7f\u7528\u3057\u3066\u3044\u307e\u3059\u3002"},

        // ER_ILLEGAL_CHARACTER needs translation
      { MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
        "\u51fa\u529b\u30e1\u30bd\u30c3\u30c9 ''{1}'' \u306e\u30d7\u30ed\u30d1\u30c6\u30a3\u30fc\u30fb\u30d5\u30a1\u30a4\u30eb ''{0}'' \u3092\u30ed\u30fc\u30c9\u3067\u304d\u307e\u305b\u3093\u3067\u3057\u305f (CLASSPATH \u3092\u78ba\u8a8d)"},

      { MsgKey.ER_INVALID_PORT,
        "\u7121\u52b9\u306a\u30dd\u30fc\u30c8\u756a\u53f7"},

      { MsgKey.ER_PORT_WHEN_HOST_NULL,
        "\u30db\u30b9\u30c8\u304c\u30cc\u30eb\u3067\u3042\u308b\u3068\u30dd\u30fc\u30c8\u3092\u8a2d\u5b9a\u3067\u304d\u307e\u305b\u3093"},

      { MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
        "\u30db\u30b9\u30c8\u306f\u3046\u307e\u304f\u69cb\u6210\u3055\u308c\u305f\u30a2\u30c9\u30ec\u30b9\u3067\u3042\u308a\u307e\u305b\u3093"},

      { MsgKey.ER_SCHEME_NOT_CONFORMANT,
        "\u30b9\u30ad\u30fc\u30e0\u306f\u4e00\u81f4\u3057\u3066\u3044\u307e\u305b\u3093\u3002"},

      { MsgKey.ER_SCHEME_FROM_NULL_STRING,
        "\u30cc\u30eb\u30fb\u30b9\u30c8\u30ea\u30f3\u30b0\u304b\u3089\u306f\u30b9\u30ad\u30fc\u30e0\u3092\u8a2d\u5b9a\u3067\u304d\u307e\u305b\u3093"},

      { MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
        "\u30d1\u30b9\u306b\u7121\u52b9\u306a\u30a8\u30b9\u30b1\u30fc\u30d7\u30fb\u30b7\u30fc\u30b1\u30f3\u30b9\u304c\u542b\u307e\u308c\u3066\u3044\u307e\u3059"},

      { MsgKey.ER_PATH_INVALID_CHAR,
        "\u30d1\u30b9\u306b\u7121\u52b9\u6587\u5b57: {0} \u304c\u542b\u307e\u308c\u3066\u3044\u307e\u3059"},

      { MsgKey.ER_FRAG_INVALID_CHAR,
        "\u30d5\u30e9\u30b0\u30e1\u30f3\u30c8\u306b\u7121\u52b9\u6587\u5b57\u304c\u542b\u307e\u308c\u3066\u3044\u307e\u3059"},

      { MsgKey.ER_FRAG_WHEN_PATH_NULL,
        "\u30d1\u30b9\u304c\u30cc\u30eb\u3067\u3042\u308b\u3068\u30d5\u30e9\u30b0\u30e1\u30f3\u30c8\u3092\u8a2d\u5b9a\u3067\u304d\u307e\u305b\u3093"},

      { MsgKey.ER_FRAG_FOR_GENERIC_URI,
        "\u7dcf\u79f0 URI \u306e\u30d5\u30e9\u30b0\u30e1\u30f3\u30c8\u3057\u304b\u8a2d\u5b9a\u3067\u304d\u307e\u305b\u3093"},

      { MsgKey.ER_NO_SCHEME_IN_URI,
        "\u30b9\u30ad\u30fc\u30e0\u306f URI {0} \u3067\u898b\u3064\u304b\u308a\u307e\u305b\u3093"},

      { MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
        "URI \u306f\u7a7a\u306e\u30d1\u30e9\u30e1\u30fc\u30bf\u30fc\u3092\u4f7f\u7528\u3057\u3066\u521d\u671f\u5316\u3067\u304d\u307e\u305b\u3093"},

      { MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
        "\u30d5\u30e9\u30b0\u30e1\u30f3\u30c8\u306f\u30d1\u30b9\u3068\u30d5\u30e9\u30b0\u30e1\u30f3\u30c8\u306e\u4e21\u65b9\u306b\u6307\u5b9a\u3067\u304d\u307e\u305b\u3093"},

      { MsgKey.ER_NO_QUERY_STRING_IN_PATH,
        "\u7167\u4f1a\u30b9\u30c8\u30ea\u30f3\u30b0\u306f\u30d1\u30b9\u304a\u3088\u3073\u7167\u4f1a\u30b9\u30c8\u30ea\u30f3\u30b0\u5185\u306b\u6307\u5b9a\u3067\u304d\u307e\u305b\u3093"},

      { MsgKey.ER_NO_PORT_IF_NO_HOST,
        "\u30db\u30b9\u30c8\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u306a\u3044\u5834\u5408\u306f\u30dd\u30fc\u30c8\u3092\u6307\u5b9a\u3057\u3066\u306f\u3044\u3051\u307e\u305b\u3093"},

      { MsgKey.ER_NO_USERINFO_IF_NO_HOST,
        "\u30db\u30b9\u30c8\u304c\u6307\u5b9a\u3055\u308c\u3066\u3044\u306a\u3044\u5834\u5408\u306f Userinfo \u3092\u6307\u5b9a\u3057\u3066\u306f\u3044\u3051\u307e\u305b\u3093"},

      { MsgKey.ER_SCHEME_REQUIRED,
        "\u30b9\u30ad\u30fc\u30e0\u304c\u5fc5\u8981\u3067\u3059!"}

    };
    return contents;
  }
}
