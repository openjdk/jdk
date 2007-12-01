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
 * $Id: SerializerMessages_ko.java,v 1.1.4.1 2005/09/08 11:03:16 suresh_emailid Exp $
 */

package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;

public class SerializerMessages_ko extends ListResourceBundle {
  public Object[][] getContents() {
    Object[][] contents =  new Object[][] {
        // BAD_MSGKEY needs translation
        // BAD_MSGFORMAT needs translation
      { MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
        "''{0}'' \uc9c1\ub82c\ud654 \ud504\ub85c\uadf8\ub7a8 \ud074\ub798\uc2a4\uac00 org.xml.sax.ContentHandler\ub97c \uad6c\ud604\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_RESOURCE_COULD_NOT_FIND,
        "[ {0} ] \uc790\uc6d0\uc744 \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.\n {1}"},

      { MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
        "[ {0} ] \uc790\uc6d0\uc774 {1} \n {2} \n {3}\uc744(\ub97c) \ub85c\ub4dc\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4. "},

      { MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "\ubc84\ud37c \ud06c\uae30 <=0"},

      { MsgKey.ER_INVALID_UTF16_SURROGATE,
        "\uc798\ubabb\ub41c UTF-16 \ub300\ub9ac\uc790(surrogate)\uac00 \ubc1c\uacac\ub418\uc5c8\uc2b5\ub2c8\ub2e4: {0} ?"},

      { MsgKey.ER_OIERROR,
        "IO \uc624\ub958"},

      { MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
        "\ud558\uc704 \ub178\ub4dc\uac00 \uc0dd\uc131\ub41c \uc774\ud6c4 \ub610\ub294 \uc694\uc18c\uac00 \uc791\uc131\ub418\uae30 \uc774\uc804\uc5d0 {0} \uc18d\uc131\uc744 \ucd94\uac00\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4. \uc18d\uc131\uc774 \ubb34\uc2dc\ub429\ub2c8\ub2e4."},

      { MsgKey.ER_NAMESPACE_PREFIX,
        "''{0}'' \uc811\ub450\ubd80\uc5d0 \ub300\ud55c \uc774\ub984 \uacf5\uac04\uc774 \uc120\uc5b8\ub418\uc9c0 \uc54a\uc558\uc2b5\ub2c8\ub2e4."},

        // ER_STRAY_ATTRIBUTE needs translation
      { MsgKey.ER_STRAY_NAMESPACE,
        "''{0}''=''{1}'' \uc774\ub984 \uacf5\uac04 \uc120\uc5b8\uc774 \uc694\uc18c\uc758 \uc678\ubd80\uc5d0 \uc788\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
        "''{0}''(CLASSPATH \ud655\uc778)\uc744(\ub97c) \ub85c\ub4dc\ud560 \uc218 \uc5c6\uc73c\ubbc0\ub85c, \ud604\uc7ac \uae30\ubcf8\uac12\ub9cc\uc744 \uc0ac\uc6a9 \uc911\uc785\ub2c8\ub2e4."},

        // ER_ILLEGAL_CHARACTER needs translation
      { MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
        "''{1}''\ucd9c\ub825 \uba54\uc18c\ub4dc(CLASSPATH \ud655\uc778)\uc5d0 \ub300\ud55c ''{0}'' \ud2b9\uc131 \ud30c\uc77c\uc744 \ub85c\ub4dc\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_INVALID_PORT,
        "\uc798\ubabb\ub41c \ud3ec\ud2b8 \ubc88\ud638"},

      { MsgKey.ER_PORT_WHEN_HOST_NULL,
        "\ud638\uc2a4\ud2b8\uac00 \ub110(null)\uc774\uba74 \ud3ec\ud2b8\ub97c \uc124\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
        "\ud638\uc2a4\ud2b8\uac00 \uc644\uc804\ud55c \uc8fc\uc18c\uac00 \uc544\ub2d9\ub2c8\ub2e4."},

      { MsgKey.ER_SCHEME_NOT_CONFORMANT,
        "\uc124\uacc4\uac00 \uc77c\uce58\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_SCHEME_FROM_NULL_STRING,
        "\ub110(null) \ubb38\uc790\uc5f4\uc5d0\uc11c \uc124\uacc4\ub97c \uc124\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
        "\uacbd\ub85c\uc5d0 \uc798\ubabb\ub41c \uc774\uc2a4\ucf00\uc774\ud504 \uc21c\uc11c\uac00 \uc788\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_PATH_INVALID_CHAR,
        "\uacbd\ub85c\uc5d0 \uc798\ubabb\ub41c \ubb38\uc790\uac00 \uc788\uc2b5\ub2c8\ub2e4: {0}"},

      { MsgKey.ER_FRAG_INVALID_CHAR,
        "\ub2e8\ud3b8\uc5d0 \uc798\ubabb\ub41c \ubb38\uc790\uac00 \uc788\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_FRAG_WHEN_PATH_NULL,
        "\uacbd\ub85c\uac00 \ub110(null)\uc774\uba74 \ub2e8\ud3b8\uc744 \uc124\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_FRAG_FOR_GENERIC_URI,
        "\uc77c\ubc18 URI\uc5d0 \ub300\ud574\uc11c\ub9cc \ub2e8\ud3b8\uc744 \uc124\uc815\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_NO_SCHEME_IN_URI,
        "URI\uc5d0 \uc124\uacc4\uac00 \uc5c6\uc2b5\ub2c8\ub2e4: {0}"},

      { MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
        "\ube48 \ub9e4\uac1c\ubcc0\uc218\ub85c URI\ub97c \ucd08\uae30\ud654\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
        "\uacbd\ub85c \ubc0f \ub2e8\ud3b8 \ub458 \ub2e4\uc5d0 \ub2e8\ud3b8\uc744 \uc9c0\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_NO_QUERY_STRING_IN_PATH,
        "\uacbd\ub85c \ubc0f \uc870\ud68c \ubb38\uc790\uc5f4\uc5d0 \uc870\ud68c \ubb38\uc790\uc5f4\uc744 \uc9c0\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_NO_PORT_IF_NO_HOST,
        "\ud638\uc2a4\ud2b8\ub97c \uc9c0\uc815\ud558\uc9c0 \uc54a\uc740 \uacbd\uc6b0\uc5d0\ub294 \ud3ec\ud2b8\ub97c \uc9c0\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_NO_USERINFO_IF_NO_HOST,
        "\ud638\uc2a4\ud2b8\ub97c \uc9c0\uc815\ud558\uc9c0 \uc54a\uc740 \uacbd\uc6b0\uc5d0\ub294 Userinfo\ub97c \uc9c0\uc815\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4."},

      { MsgKey.ER_SCHEME_REQUIRED,
        "\uc124\uacc4\uac00 \ud544\uc694\ud569\ub2c8\ub2e4!"}

    };
    return contents;
  }
}
