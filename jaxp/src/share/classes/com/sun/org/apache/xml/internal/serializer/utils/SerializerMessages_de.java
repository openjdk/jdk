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
 * $Id: SerializerMessages_de.java,v 1.1.4.1 2005/09/08 11:03:12 suresh_emailid Exp $
 */

package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;

public class SerializerMessages_de extends ListResourceBundle {
  public Object[][] getContents() {
    Object[][] contents =  new Object[][] {
        // BAD_MSGKEY needs translation
        // BAD_MSGFORMAT needs translation
      { MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
        "Die Parallel-Seriell-Umsetzerklasse ''{0}'' implementiert org.xml.sax.ContentHandler nicht."},

      { MsgKey.ER_RESOURCE_COULD_NOT_FIND,
        "Die Ressource [ {0} ] konnte nicht gefunden werden.\n {1}"},

      { MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
        "Die Ressource [ {0} ] konnte nicht geladen werden: {1} \n {2} \n {3}"},

      { MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "Puffergr\u00f6\u00dfe <=0"},

      { MsgKey.ER_INVALID_UTF16_SURROGATE,
        "Ung\u00fcltige UTF-16-Ersetzung festgestellt: {0} ?"},

      { MsgKey.ER_OIERROR,
        "E/A-Fehler"},

      { MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
        "Attribut {0} kann nicht nach Kindknoten oder vor dem Erstellen eines Elements hinzugef\u00fcgt werden.  Das Attribut wird ignoriert."},

      { MsgKey.ER_NAMESPACE_PREFIX,
        "Der Namensbereich f\u00fcr Pr\u00e4fix ''{0}'' wurde nicht deklariert."},

        // ER_STRAY_ATTRIBUTE needs translation
      { MsgKey.ER_STRAY_NAMESPACE,
        "Namensbereichsdeklaration ''{0}''=''{1}'' befindet sich nicht in einem Element."},

      { MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
        "''{0}'' konnte nicht geladen werden (CLASSPATH pr\u00fcfen); es werden die Standardwerte verwendet"},

        // ER_ILLEGAL_CHARACTER needs translation
      { MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
        "Merkmaldatei ''{0}'' konnte f\u00fcr Ausgabemethode ''{1}'' nicht geladen werden (CLASSPATH pr\u00fcfen)"},

      { MsgKey.ER_INVALID_PORT,
        "Ung\u00fcltige Portnummer"},

      { MsgKey.ER_PORT_WHEN_HOST_NULL,
        "Der Port kann nicht festgelegt werden, wenn der Host gleich Null ist."},

      { MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
        "Der Host ist keine syntaktisch korrekte Adresse."},

      { MsgKey.ER_SCHEME_NOT_CONFORMANT,
        "Das Schema ist nicht angepasst."},

      { MsgKey.ER_SCHEME_FROM_NULL_STRING,
        "Schema kann nicht von Nullzeichenfolge festgelegt werden."},

      { MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
        "Der Pfad enth\u00e4lt eine ung\u00fcltige Escapezeichenfolge."},

      { MsgKey.ER_PATH_INVALID_CHAR,
        "Pfad enth\u00e4lt ung\u00fcltiges Zeichen: {0}."},

      { MsgKey.ER_FRAG_INVALID_CHAR,
        "Fragment enth\u00e4lt ein ung\u00fcltiges Zeichen."},

      { MsgKey.ER_FRAG_WHEN_PATH_NULL,
        "Fragment kann nicht festgelegt werden, wenn der Pfad gleich Null ist."},

      { MsgKey.ER_FRAG_FOR_GENERIC_URI,
        "Fragment kann nur f\u00fcr eine generische URI (Uniform Resource Identifier) festgelegt werden."},

      { MsgKey.ER_NO_SCHEME_IN_URI,
        "Kein Schema gefunden in URI: {0}."},

      { MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
        "URI (Uniform Resource Identifier) kann nicht mit leeren Parametern initialisiert werden."},

      { MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
        "Fragment kann nicht im Pfad und im Fragment angegeben werden."},

      { MsgKey.ER_NO_QUERY_STRING_IN_PATH,
        "Abfragezeichenfolge kann nicht im Pfad und in der Abfragezeichenfolge angegeben werden."},

      { MsgKey.ER_NO_PORT_IF_NO_HOST,
        "Der Port kann nicht angegeben werden, wenn der Host nicht angegeben wurde."},

      { MsgKey.ER_NO_USERINFO_IF_NO_HOST,
        "Benutzerinformationen k\u00f6nnen nicht angegeben werden, wenn der Host nicht angegeben wurde."},

      { MsgKey.ER_SCHEME_REQUIRED,
        "Schema ist erforderlich!"}

    };
    return contents;
  }
}
