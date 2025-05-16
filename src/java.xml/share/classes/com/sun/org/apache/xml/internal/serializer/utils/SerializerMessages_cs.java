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
 * $Id: SerializerMessages_cs.java,v 1.1.4.1 2005/09/08 11:03:12 suresh_emailid Exp $
 */

package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;

public class SerializerMessages_cs extends ListResourceBundle {
  public Object[][] getContents() {
    Object[][] contents =  new Object[][] {
        // BAD_MSGKEY needs translation
        // BAD_MSGFORMAT needs translation
      { MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
        "Třída serializace ''{0}'' neimplementuje org.xml.sax.ContentHandler."},

      { MsgKey.ER_RESOURCE_COULD_NOT_FIND,
        "Nelze najít zdroj [ {0} ].\n {1}"},

      { MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
        "Nelze zavést zdroj [ {0} ]: {1} \n {2} \n {3}"},

      { MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "Velikost vyrovnávací paměti <=0"},

      { MsgKey.ER_INVALID_UTF16_SURROGATE,
        "Byla zjištěna neplatná náhrada UTF-16: {0} ?"},

      { MsgKey.ER_OIERROR,
        "Chyba vstupu/výstupu"},

      { MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
        "Nelze přidat atribut {0} po uzlech potomků ani před tím, než je vytvořen prvek. Atribut bude ignorován."},

      { MsgKey.ER_NAMESPACE_PREFIX,
        "Obor názvů pro předponu ''{0}'' nebyl deklarován."},

        // ER_STRAY_ATTRIBUTE needs translation
      { MsgKey.ER_STRAY_NAMESPACE,
        "Deklarace oboru názvů ''{0}''=''{1}'' je vně prvku."},

      { MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
        "Nelze zavést ''{0}'' (zkontrolujte proměnnou CLASSPATH), proto se používají pouze výchozí hodnoty"},

        // ER_ILLEGAL_CHARACTER needs translation
      { MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
        "Nelze načíst soubor vlastností ''{0}'' pro výstupní metodu ''{1}'' (zkontrolujte proměnnou CLASSPATH)."},

      { MsgKey.ER_INVALID_PORT,
        "Neplatné číslo portu."},

      { MsgKey.ER_PORT_WHEN_HOST_NULL,
        "Má-li hostitel hodnotu null, nelze nastavit port."},

      { MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
        "Adresa hostitele má nesprávný formát."},

      { MsgKey.ER_SCHEME_NOT_CONFORMANT,
        "Schéma nevyhovuje."},

      { MsgKey.ER_SCHEME_FROM_NULL_STRING,
        "Nelze nastavit schéma řetězce s hodnotou null."},

      { MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
        "Cesta obsahuje neplatnou escape sekvenci"},

      { MsgKey.ER_PATH_INVALID_CHAR,
        "Cesta obsahuje neplatný znak: {0}"},

      { MsgKey.ER_FRAG_INVALID_CHAR,
        "Fragment obsahuje neplatný znak."},

      { MsgKey.ER_FRAG_WHEN_PATH_NULL,
        "Má-li cesta hodnotu null, nelze nastavit fragment."},

      { MsgKey.ER_FRAG_FOR_GENERIC_URI,
        "Fragment lze nastavit jen u generického URI."},

      { MsgKey.ER_NO_SCHEME_IN_URI,
        "V URI nebylo nalezeno žádné schéma: {0}"},

      { MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
        "URI nelze inicializovat s prázdnými parametry."},

      { MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
        "Fragment nelze určit zároveň v cestě i ve fragmentu."},

      { MsgKey.ER_NO_QUERY_STRING_IN_PATH,
        "V řetězci cesty a dotazu nelze zadat řetězec dotazu."},

      { MsgKey.ER_NO_PORT_IF_NO_HOST,
        "Není-li určen hostitel, nelze zadat port."},

      { MsgKey.ER_NO_USERINFO_IF_NO_HOST,
        "Není-li určen hostitel, nelze zadat údaje o uživateli."},

      { MsgKey.ER_SCHEME_REQUIRED,
        "Je vyžadováno schéma!"},

      /*
       * Note to translators:  The words 'Properties' and
       * 'SerializerFactory' in this message are Java class names
       * and should not be translated.
       */
      {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
          "Objekt vlastností předaný faktorii SerializerFactory neobsahuje vlastnost ''{0}''. " },

      {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
          "Varování: Kódování ''{0}'' není v běhovém prostředí Java podporováno." },

       {MsgKey.ER_FEATURE_NOT_FOUND,
       "Parametr ''{0}'' nebyl rozpoznán."},

       {MsgKey.ER_FEATURE_NOT_SUPPORTED,
       "Parametr ''{0}'' byl rozpoznán, ale nelze nastavit požadovanou hodnotu."},

       {MsgKey.ER_STRING_TOO_LONG,
       "Výsledný řetězec je příliš dlouhý pro řetězec DOMString: ''{0}''."},

       {MsgKey.ER_TYPE_MISMATCH_ERR,
       "Typ hodnoty pro tento název parametru není kompatibilní s očekávaným typem hodnoty."},

       {MsgKey.ER_NO_OUTPUT_SPECIFIED,
       "Cílové umístění výstupu pro data určená k zápisu je rovno hodnotě Null. "},

       {MsgKey.ER_UNSUPPORTED_ENCODING,
       "Bylo nalezeno nepodporované kódování."},

       {MsgKey.ER_UNABLE_TO_SERIALIZE_NODE,
       "Nelze provést serializaci uzlu. "},

       {MsgKey.ER_CDATA_SECTIONS_SPLIT,
       "Sekce CDATA obsahuje jednu nebo více ukončovacích značek ']]>'."},

       {MsgKey.ER_WARNING_WF_NOT_CHECKED,
           "Nelze vytvořit instanci modulu pro kontrolu správného utvoření. Parametr správného utvoření byl nastaven na hodnotu true, nepodařilo se však zkontrolovat správnost utvoření. "
       },

       {MsgKey.ER_WF_INVALID_CHARACTER,
           "Uzel ''{0}'' obsahuje neplatné znaky XML. "
       },

       { MsgKey.ER_WF_INVALID_CHARACTER_IN_COMMENT,
           "V poznámce byl zjištěn neplatný znak XML (Unicode: 0x{0})."
       },

       { MsgKey.ER_WF_INVALID_CHARACTER_IN_PI,
           "V datech instrukce zpracování byl nalezen neplatný znak XML (Unicode: 0x{0})."
       },

       { MsgKey.ER_WF_INVALID_CHARACTER_IN_CDATA,
           "V oddílu CDATASection byl nalezen neplatný znak XML (Unicode: 0x{0})."
       },

       { MsgKey.ER_WF_INVALID_CHARACTER_IN_TEXT,
           "V obsahu znakových dat uzlu byl nalezen neplatný znak XML (Unicode: 0x{0})."
       },

       { MsgKey.ER_WF_INVALID_CHARACTER_IN_NODE_NAME,
           "V objektu {0} s názvem ''{1}'' byl nalezen neplatný znak XML. "
       },

       { MsgKey.ER_WF_DASH_IN_COMMENT,
           "V poznámkách není povolen řetězec \"--\"."
       },

       {MsgKey.ER_WF_LT_IN_ATTVAL,
           "Hodnota atributu \"{1}\" souvisejícího s typem prvku \"{0}\" nesmí obsahovat znak ''<''."
       },

       {MsgKey.ER_WF_REF_TO_UNPARSED_ENT,
           "Odkaz na neanalyzovanou entitu \"&{0};\" není povolen."
       },

       {MsgKey.ER_WF_REF_TO_EXTERNAL_ENT,
           "Externí odkaz na entitu \"&{0};\" není v hodnotě atributu povolen."
       },

       {MsgKey.ER_NS_PREFIX_CANNOT_BE_BOUND,
           "Předpona \"{0}\" nesmí být vázaná k oboru názvů \"{1}\"."
       },

       {MsgKey.ER_NULL_LOCAL_ELEMENT_NAME,
           "Lokální název prvku \"{0}\" má hodnotu Null. "
       },

       {MsgKey.ER_NULL_LOCAL_ATTR_NAME,
           "Lokální název atributu \"{0}\" má hodnotu Null. "
       },

       { MsgKey.ER_ELEM_UNBOUND_PREFIX_IN_ENTREF,
           "Nový text uzlu entity \"{0}\" obsahuje uzel prvku \"{1}\" s nesvázanou předponou \"{2}\"."
       },

       { MsgKey.ER_ATTR_UNBOUND_PREFIX_IN_ENTREF,
           "Nový text uzlu entity \"{0}\" obsahuje uzel atributu \"{1}\" s nesvázanou předponou \"{2}\". "
       },

    };
    return contents;
  }
}
