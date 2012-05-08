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
 * $Id: SerializerMessages_it.java,v 1.1.4.1 2005/09/08 11:03:15 suresh_emailid Exp $
 */

package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;

public class SerializerMessages_it extends ListResourceBundle {
  public Object[][] getContents() {
    Object[][] contents =  new Object[][] {
        // BAD_MSGKEY needs translation
        // BAD_MSGFORMAT needs translation
      { MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
        "La classe serializer ''{0}'' non implementa org.xml.sax.ContentHandler."},

      { MsgKey.ER_RESOURCE_COULD_NOT_FIND,
        "Risorsa [ {0} ] non trovata.\n {1}"},

      { MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
        "Impossibile caricare la risorsa [ {0} ]: {1} \n {2} \n {3}"},

      { MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "Dimensione buffer <=0"},

      { MsgKey.ER_INVALID_UTF16_SURROGATE,
        "Rilevato surrogato UTF-16 non valido: {0} ?"},

      { MsgKey.ER_OIERROR,
        "Errore IO"},

      { MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
        "Impossibile aggiungere l''attributo {0} dopo i nodi secondari o prima che sia prodotto un elemento. L''attributo verr\u00e0 ignorato. "},

      { MsgKey.ER_NAMESPACE_PREFIX,
        "Lo spazio nomi per il prefisso ''{0}'' non \u00e8 stato dichiarato. "},

        // ER_STRAY_ATTRIBUTE needs translation
      { MsgKey.ER_STRAY_NAMESPACE,
        "Dichiarazione dello spazio nome ''{0}''=''{1}'' al di fuori dell''elemento. "},

      { MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
        "Impossibile caricare ''{0}'' (verificare CLASSPATH); verranno utilizzati i valori predefiniti "},

        // ER_ILLEGAL_CHARACTER needs translation
      { MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
        "Impossibile caricare il file delle propriet\u00e0 ''{0}'' per il metodo di emissione ''{1}'' (verificare CLASSPATH)"},

      { MsgKey.ER_INVALID_PORT,
        "Numero di porta non valido"},

      { MsgKey.ER_PORT_WHEN_HOST_NULL,
        "La porta non pu\u00f2 essere impostata se l'host \u00e8 nullo"},

      { MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
        "Host non \u00e8 un'indirizzo corretto"},

      { MsgKey.ER_SCHEME_NOT_CONFORMANT,
        "Lo schema non \u00e8 conforme."},

      { MsgKey.ER_SCHEME_FROM_NULL_STRING,
        "Impossibile impostare lo schema da una stringa nulla"},

      { MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
        "Il percorso contiene sequenza di escape non valida"},

      { MsgKey.ER_PATH_INVALID_CHAR,
        "Il percorso contiene un carattere non valido: {0}"},

      { MsgKey.ER_FRAG_INVALID_CHAR,
        "Il frammento contiene un carattere non valido"},

      { MsgKey.ER_FRAG_WHEN_PATH_NULL,
        "Il frammento non pu\u00f2 essere impostato se il percorso \u00e8 nullo"},

      { MsgKey.ER_FRAG_FOR_GENERIC_URI,
        "Il frammento pu\u00f2 essere impostato solo per un URI generico"},

      { MsgKey.ER_NO_SCHEME_IN_URI,
        "Nessuno schema trovato nell''URI: {0}"},

      { MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
        "Impossibile inizializzare l'URI con i parametri vuoti"},

      { MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
        "Il frammento non pu\u00f2 essere specificato sia nel percorso che nel frammento"},

      { MsgKey.ER_NO_QUERY_STRING_IN_PATH,
        "La stringa di interrogazione non pu\u00f2 essere specificata nella stringa di interrogazione e percorso."},

      { MsgKey.ER_NO_PORT_IF_NO_HOST,
        "La porta non pu\u00f2 essere specificata se l'host non \u00e8 specificato"},

      { MsgKey.ER_NO_USERINFO_IF_NO_HOST,
        "Userinfo non pu\u00f2 essere specificato se l'host non \u00e8 specificato"},

      { MsgKey.ER_SCHEME_REQUIRED,
        "Lo schema \u00e8 obbligatorio."}

    };
    return contents;
  }
}
