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
 * $Id: SerializerMessages_sv.java,v 1.1.4.1 2005/09/08 11:03:17 suresh_emailid Exp $
 */

package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;

public class SerializerMessages_sv extends ListResourceBundle {
  public Object[][] getContents() {
    Object[][] contents =  new Object[][] {
        // BAD_MSGKEY needs translation
        // BAD_MSGFORMAT needs translation
        // ER_SERIALIZER_NOT_CONTENTHANDLER needs translation
        // ER_RESOURCE_COULD_NOT_FIND needs translation
        // ER_RESOURCE_COULD_NOT_LOAD needs translation
        // ER_BUFFER_SIZE_LESSTHAN_ZERO needs translation
        // ER_INVALID_UTF16_SURROGATE needs translation
        // ER_OIERROR needs translation
        // ER_ILLEGAL_ATTRIBUTE_POSITION needs translation
        // ER_NAMESPACE_PREFIX needs translation
        // ER_STRAY_ATTRIBUTE needs translation
        // ER_STRAY_NAMESPACE needs translation
        // ER_COULD_NOT_LOAD_RESOURCE needs translation
        // ER_ILLEGAL_CHARACTER needs translation
        // ER_COULD_NOT_LOAD_METHOD_PROPERTY needs translation
      { MsgKey.ER_INVALID_PORT,
        "Ogiltigt portnummer"},

      { MsgKey.ER_PORT_WHEN_HOST_NULL,
        "Port kan inte s\u00e4ttas n\u00e4r v\u00e4rd \u00e4r null"},

      { MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
        "V\u00e4rd \u00e4r inte en v\u00e4lformulerad adress"},

      { MsgKey.ER_SCHEME_NOT_CONFORMANT,
        "Schemat \u00e4r inte likformigt."},

      { MsgKey.ER_SCHEME_FROM_NULL_STRING,
        "Kan inte s\u00e4tta schema fr\u00e5n null-str\u00e4ng"},

      { MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
        "V\u00e4g inneh\u00e5ller ogiltig flyktsekvens"},

      { MsgKey.ER_PATH_INVALID_CHAR,
        "V\u00e4g inneh\u00e5ller ogiltigt tecken: {0}"},

      { MsgKey.ER_FRAG_INVALID_CHAR,
        "Fragment inneh\u00e5ller ogiltigt tecken"},

      { MsgKey.ER_FRAG_WHEN_PATH_NULL,
        "Fragment kan inte s\u00e4ttas n\u00e4r v\u00e4g \u00e4r null"},

      { MsgKey.ER_FRAG_FOR_GENERIC_URI,
        "Fragment kan bara s\u00e4ttas f\u00f6r en allm\u00e4n URI"},

      { MsgKey.ER_NO_SCHEME_IN_URI,
        "Schema saknas i URI: {0}"},

      { MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
        "Kan inte initialisera URI med tomma parametrar"},

      { MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
        "Fragment kan inte anges i b\u00e5de v\u00e4gen och fragmentet"},

      { MsgKey.ER_NO_QUERY_STRING_IN_PATH,
        "F\u00f6rfr\u00e5gan-str\u00e4ng kan inte anges i v\u00e4g och f\u00f6rfr\u00e5gan-str\u00e4ng"},

      { MsgKey.ER_NO_PORT_IF_NO_HOST,
        "Port f\u00e5r inte anges om v\u00e4rden inte \u00e4r angiven"},

      { MsgKey.ER_NO_USERINFO_IF_NO_HOST,
        "Userinfo f\u00e5r inte anges om v\u00e4rden inte \u00e4r angiven"},

      { MsgKey.ER_SCHEME_REQUIRED,
        "Schema kr\u00e4vs!"}

    };
    return contents;
  }
}
