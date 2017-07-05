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
 * $Id: SerializerMessages_fr.java,v 1.1.4.1 2005/09/08 11:03:13 suresh_emailid Exp $
 */

package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;

public class SerializerMessages_fr extends ListResourceBundle {
  public Object[][] getContents() {
    Object[][] contents =  new Object[][] {
        // BAD_MSGKEY needs translation
        // BAD_MSGFORMAT needs translation
      { MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
        "La classe de la m\u00e9thode de s\u00e9rialisation ''{0}'' n''impl\u00e9mente pas org.xml.sax.ContentHandler."},

      { MsgKey.ER_RESOURCE_COULD_NOT_FIND,
        "La ressource [ {0} ] est introuvable.\n {1}"},

      { MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
        "La ressource [ {0} ] n''a pas pu charger : {1} \n {2} \n {3}"},

      { MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
        "Taille du tampon <=0"},

      { MsgKey.ER_INVALID_UTF16_SURROGATE,
        "Substitut UTF-16 non valide d\u00e9tect\u00e9 : {0} ?"},

      { MsgKey.ER_OIERROR,
        "Erreur d''E-S"},

      { MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
        "Ajout impossible de l''attribut {0} apr\u00e8s des noeuds enfants ou avant la production d''un \u00e9l\u00e9ment.  L''attribut est ignor\u00e9."},

      { MsgKey.ER_NAMESPACE_PREFIX,
        "L''espace de noms du pr\u00e9fixe ''{0}'' n''a pas \u00e9t\u00e9 d\u00e9clar\u00e9."},

        // ER_STRAY_ATTRIBUTE needs translation
      { MsgKey.ER_STRAY_NAMESPACE,
        "La d\u00e9claration d''espace de noms ''{0}''=''{1}'' est \u00e0 l''ext\u00e9rieur de l''\u00e9l\u00e9ment."},

      { MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
        "Impossible de charger ''{0}'' (v\u00e9rifier CLASSPATH), les valeurs par d\u00e9faut sont donc employ\u00e9es "},

        // ER_ILLEGAL_CHARACTER needs translation
      { MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
        "Impossible de charger le fichier de propri\u00e9t\u00e9s ''{0}'' pour la m\u00e9thode de sortie ''{1}'' (v\u00e9rifier CLASSPATH)"},

      { MsgKey.ER_INVALID_PORT,
        "Num\u00e9ro de port non valide"},

      { MsgKey.ER_PORT_WHEN_HOST_NULL,
        "Le port ne peut \u00eatre d\u00e9fini quand l'h\u00f4te est vide"},

      { MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
        "L'h\u00f4te n'est pas une adresse bien form\u00e9e"},

      { MsgKey.ER_SCHEME_NOT_CONFORMANT,
        "Le processus n'est pas conforme."},

      { MsgKey.ER_SCHEME_FROM_NULL_STRING,
        "Impossible de d\u00e9finir le processus \u00e0 partir de la cha\u00eene vide"},

      { MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
        "Le chemin d'acc\u00e8s contient une s\u00e9quence d'\u00e9chappement non valide"},

      { MsgKey.ER_PATH_INVALID_CHAR,
        "Le chemin contient un caract\u00e8re non valide : {0}"},

      { MsgKey.ER_FRAG_INVALID_CHAR,
        "Le fragment contient un caract\u00e8re non valide"},

      { MsgKey.ER_FRAG_WHEN_PATH_NULL,
        "Le fragment ne peut \u00eatre d\u00e9fini quand le chemin d'acc\u00e8s est vide"},

      { MsgKey.ER_FRAG_FOR_GENERIC_URI,
        "Le fragment ne peut \u00eatre d\u00e9fini que pour un URI g\u00e9n\u00e9rique"},

      { MsgKey.ER_NO_SCHEME_IN_URI,
        "Processus introuvable dans l''URI : {0}"},

      { MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
        "Impossible d'initialiser l'URI avec des param\u00e8tres vides"},

      { MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
        "Le fragment ne doit pas \u00eatre indiqu\u00e9 \u00e0 la fois dans le chemin et dans le fragment"},

      { MsgKey.ER_NO_QUERY_STRING_IN_PATH,
        "La cha\u00eene de requ\u00eate ne doit pas figurer dans un chemin et une cha\u00eene de requ\u00eate"},

      { MsgKey.ER_NO_PORT_IF_NO_HOST,
        "Le port peut ne pas \u00eatre sp\u00e9cifi\u00e9 si l'h\u00f4te n'est pas sp\u00e9cifi\u00e9"},

      { MsgKey.ER_NO_USERINFO_IF_NO_HOST,
        "Userinfo ne peut \u00eatre sp\u00e9cifi\u00e9 si l'h\u00f4te ne l'est pas"},

      { MsgKey.ER_SCHEME_REQUIRED,
        "Processus requis !"}

    };
    return contents;
  }
}
