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
 * $Id: SerializerMessages_ca.java,v 1.1.4.1 2005/09/08 11:03:11 suresh_emailid Exp $
 */

package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;

public class SerializerMessages_ca extends ListResourceBundle {
  public Object[][] getContents() {
    Object[][] contents =  new Object[][] {
      { MsgKey.BAD_MSGKEY,
        "The message key ''{0}'' is not in the message class ''{1}''"},

      { MsgKey.BAD_MSGFORMAT,
        "The format of message ''{0}'' in message class ''{1}'' failed."},

      { MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
        "The serializer class ''{0}'' does not implement org.xml.sax.ContentHandler."},

      { MsgKey.ER_RESOURCE_COULD_NOT_FIND,
        "The resource [ {0} ] could not be found.\n {1}"},

      { MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
        "The resource [ {0} ] could not load: {1} \n {2} \n {3}"},

      { MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "Buffer size <=0"},

      { MsgKey.ER_INVALID_UTF16_SURROGATE,
        "Invalid UTF-16 surrogate detected: {0} ?"},

      { MsgKey.ER_OIERROR,
        "IO error"},

      { MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
        "Cannot add attribute {0} after child nodes or before an element is produced.  Attribute will be ignored."},

      { MsgKey.ER_NAMESPACE_PREFIX,
        "Namespace for prefix ''{0}'' has not been declared."},

      { MsgKey.ER_STRAY_ATTRIBUTE,
        "Attribute ''{0}'' outside of element."},

      { MsgKey.ER_STRAY_NAMESPACE,
        "Namespace declaration ''{0}''=''{1}'' outside of element."},

      { MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
        "Could not load ''{0}'' (check CLASSPATH), now using just the defaults"},

      { MsgKey.ER_ILLEGAL_CHARACTER,
        "Attempt to output character of integral value {0} that is not represented in specified output encoding of {1}."},

      { MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
        "Could not load the propery file ''{0}'' for output method ''{1}'' (check CLASSPATH)"},

      { MsgKey.ER_INVALID_PORT,
        "Invalid port number"},

      { MsgKey.ER_PORT_WHEN_HOST_NULL,
        "Port cannot be set when host is null"},

      { MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
        "Host is not a well formed address"},

      { MsgKey.ER_SCHEME_NOT_CONFORMANT,
        "The scheme is not conformant."},

      { MsgKey.ER_SCHEME_FROM_NULL_STRING,
        "Cannot set scheme from null string"},

      { MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
        "Path contains invalid escape sequence"},

      { MsgKey.ER_PATH_INVALID_CHAR,
        "Path contains invalid character: {0}"},

      { MsgKey.ER_FRAG_INVALID_CHAR,
        "Fragment contains invalid character"},

      { MsgKey.ER_FRAG_WHEN_PATH_NULL,
        "Fragment cannot be set when path is null"},

      { MsgKey.ER_FRAG_FOR_GENERIC_URI,
        "Fragment can only be set for a generic URI"},

      { MsgKey.ER_NO_SCHEME_IN_URI,
        "No scheme found in URI"},

      { MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
        "Cannot initialize URI with empty parameters"},

      { MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
        "Fragment cannot be specified in both the path and fragment"},

      { MsgKey.ER_NO_QUERY_STRING_IN_PATH,
        "Query string cannot be specified in path and query string"},

      { MsgKey.ER_NO_PORT_IF_NO_HOST,
        "Port may not be specified if host is not specified"},

      { MsgKey.ER_NO_USERINFO_IF_NO_HOST,
        "Userinfo may not be specified if host is not specified"},

      { MsgKey.ER_SCHEME_REQUIRED,
        "Scheme is required!"},

      /*
       * Note to translators:  The words 'Properties' and
       * 'SerializerFactory' in this message are Java class names
       * and should not be translated.
       */
      {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
          "L''objecte de propietats passat a SerializerFactory no té cap propietat ''{0}''." },

      {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
          "Avís: el temps d''execució de Java no dóna suport a la codificació ''{0}''." },

       {MsgKey.ER_FEATURE_NOT_FOUND,
       "El paràmetre ''{0}'' no es reconeix."},

       {MsgKey.ER_FEATURE_NOT_SUPPORTED,
       "El paràmetre ''{0}'' es reconeix però el valor sol·licitat no es pot establir."},

       {MsgKey.ER_STRING_TOO_LONG,
       "La cadena resultant és massa llarga per cabre en una DOMString: ''{0}''."},

       {MsgKey.ER_TYPE_MISMATCH_ERR,
       "El tipus de valor per a aquest nom de paràmetre és incompatible amb el tipus de valor esperat."},

       {MsgKey.ER_NO_OUTPUT_SPECIFIED,
       "La destinació de sortida per a les dades que s'ha d'escriure era nul·la."},

       {MsgKey.ER_UNSUPPORTED_ENCODING,
       "S'ha trobat una codificació no suportada."},

       {MsgKey.ER_UNABLE_TO_SERIALIZE_NODE,
       "El node no s'ha pogut serialitzat."},

       {MsgKey.ER_CDATA_SECTIONS_SPLIT,
       "La secció CDATA conté un o més marcadors d'acabament ']]>'."},

       {MsgKey.ER_WARNING_WF_NOT_CHECKED,
           "No s'ha pogut crear cap instància per comprovar si té un format correcte o no. El paràmetre del tipus ben format es va establir en cert, però la comprovació de format no s'ha pogut realitzar."
       },

       {MsgKey.ER_WF_INVALID_CHARACTER,
           "El node ''{0}'' conté caràcters XML no vàlids."
       },

       { MsgKey.ER_WF_INVALID_CHARACTER_IN_COMMENT,
           "S''ha trobat un caràcter XML no vàlid (Unicode: 0x{0}) en el comentari."
       },

       { MsgKey.ER_WF_INVALID_CHARACTER_IN_PI,
           "S''ha trobat un caràcter XML no vàlid (Unicode: 0x{0}) en les dades d''instrucció de procés."
       },

       { MsgKey.ER_WF_INVALID_CHARACTER_IN_CDATA,
           "S''ha trobat un caràcter XML no vàlid (Unicode: 0x''{0})'' en els continguts de la CDATASection."
       },

       { MsgKey.ER_WF_INVALID_CHARACTER_IN_TEXT,
           "S''ha trobat un caràcter XML no vàlid (Unicode: 0x''{0})'' en el contingut de dades de caràcter del node."
       },

       { MsgKey.ER_WF_INVALID_CHARACTER_IN_NODE_NAME,
           "S''han trobat caràcters XML no vàlids al node {0} anomenat ''{1}''."
       },

       { MsgKey.ER_WF_DASH_IN_COMMENT,
           "La cadena \"--\" no està permesa dins dels comentaris."
       },

       {MsgKey.ER_WF_LT_IN_ATTVAL,
           "El valor d''atribut \"{1}\" associat amb un tipus d''element \"{0}\" no pot contenir el caràcter ''<''."
       },

       {MsgKey.ER_WF_REF_TO_UNPARSED_ENT,
           "La referència de l''entitat no analitzada \"&{0};\" no està permesa."
       },

       {MsgKey.ER_WF_REF_TO_EXTERNAL_ENT,
           "La referència externa de l''entitat \"&{0};\" no està permesa en un valor d''atribut."
       },

       {MsgKey.ER_NS_PREFIX_CANNOT_BE_BOUND,
           "El prefix \"{0}\" no es pot vincular a l''espai de noms \"{1}\"."
       },

       {MsgKey.ER_NULL_LOCAL_ELEMENT_NAME,
           "El nom local de l''element \"{0}\" és nul."
       },

       {MsgKey.ER_NULL_LOCAL_ATTR_NAME,
           "El nom local d''atr \"{0}\" és nul."
       },

       { MsgKey.ER_ELEM_UNBOUND_PREFIX_IN_ENTREF,
           "El text de recanvi del node de l''entitat \"{0}\" conté un node d''element \"{1}\" amb un prefix de no enllaçat \"{2}\"."
       },

       { MsgKey.ER_ATTR_UNBOUND_PREFIX_IN_ENTREF,
           "El text de recanvi del node de l''entitat \"{0}\" conté un node d''atribut \"{1}\" amb un prefix de no enllaçat \"{2}\"."
       },

    };
    return contents;
  }
}
