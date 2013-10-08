/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2004 The Apache Software Foundation.
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
 * $Id: SerializerMessages_fr.java /st_wptg_1.8.0.0.0jdk/2 2013/09/16 07:05:15 gmolloy Exp $
 */
package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * An instance of this class is a ListResourceBundle that
 * has the required getContents() method that returns
 * an array of message-key/message associations.
 * <p>
 * The message keys are defined in {@link MsgKey}. The
 * messages that those keys map to are defined here.
 * <p>
 * The messages in the English version are intended to be
 * translated.
 *
 * This class is not a public API, it is only public because it is
 * used in com.sun.org.apache.xml.internal.serializer.
 *
 * @xsl.usage internal
 */
public class SerializerMessages_fr extends ListResourceBundle {

    /*
     * This file contains error and warning messages related to
     * Serializer Error Handling.
     *
     *  General notes to translators:

     *  1) A stylesheet is a description of how to transform an input XML document
     *     into a resultant XML document (or HTML document or text).  The
     *     stylesheet itself is described in the form of an XML document.

     *
     *  2) An element is a mark-up tag in an XML document; an attribute is a
     *     modifier on the tag.  For example, in <elem attr='val' attr2='val2'>
     *     "elem" is an element name, "attr" and "attr2" are attribute names with
     *     the values "val" and "val2", respectively.
     *
     *  3) A namespace declaration is a special attribute that is used to associate
     *     a prefix with a URI (the namespace).  The meanings of element names and
     *     attribute names that use that prefix are defined with respect to that
     *     namespace.
     *
     *
     */

    /** The lookup table for error messages.   */
    public Object[][] getContents() {
        Object[][] contents = new Object[][] {
            {   MsgKey.BAD_MSGKEY,
                "La cl\u00E9 de message ''{0}'' ne figure pas dans la classe de messages ''{1}''" },

            {   MsgKey.BAD_MSGFORMAT,
                "Echec du format de message ''{0}'' dans la classe de messages ''{1}''." },

            {   MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
                "La classe de serializer ''{0}'' n''impl\u00E9mente pas org.xml.sax.ContentHandler." },

            {   MsgKey.ER_RESOURCE_COULD_NOT_FIND,
                    "La ressource [ {0} ] est introuvable.\n {1}" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
                    "La ressource [ {0} ] n''a pas pu charger : {1} \n {2} \t {3}" },

            {   MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
                    "Taille du tampon <=0" },

            {   MsgKey.ER_INVALID_UTF16_SURROGATE,
                    "Substitut UTF-16 non valide d\u00E9tect\u00E9 : {0} ?" },

            {   MsgKey.ER_OIERROR,
                "Erreur d'E/S" },

            {   MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
                "Impossible d''ajouter l''attribut {0} apr\u00E8s des noeuds enfant ou avant la production d''un \u00E9l\u00E9ment. L''attribut est ignor\u00E9." },

            /*
             * Note to translators:  The stylesheet contained a reference to a
             * namespace prefix that was undefined.  The value of the substitution
             * text is the name of the prefix.
             */
            {   MsgKey.ER_NAMESPACE_PREFIX,
                "L''espace de noms du pr\u00E9fixe ''{0}'' n''a pas \u00E9t\u00E9 d\u00E9clar\u00E9." },

            /*
             * Note to translators:  This message is reported if the stylesheet
             * being processed attempted to construct an XML document with an
             * attribute in a place other than on an element.  The substitution text
             * specifies the name of the attribute.
             */
            {   MsgKey.ER_STRAY_ATTRIBUTE,
                "Attribut ''{0}'' en dehors de l''\u00E9l\u00E9ment." },

            /*
             * Note to translators:  As with the preceding message, a namespace
             * declaration has the form of an attribute and is only permitted to
             * appear on an element.  The substitution text {0} is the namespace
             * prefix and {1} is the URI that was being used in the erroneous
             * namespace declaration.
             */
            {   MsgKey.ER_STRAY_NAMESPACE,
                "La d\u00E9claration d''espace de noms ''{0}''=''{1}'' est \u00E0 l''ext\u00E9rieur de l''\u00E9l\u00E9ment." },

            {   MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
                "Impossible de charger ''{0}'' (v\u00E9rifier CLASSPATH), les valeurs par d\u00E9faut sont donc employ\u00E9es" },

            {   MsgKey.ER_ILLEGAL_CHARACTER,
                "Tentative de sortie d''un caract\u00E8re avec une valeur enti\u00E8re {0}, non repr\u00E9sent\u00E9 dans l''encodage de sortie sp\u00E9cifi\u00E9 pour {1}." },

            {   MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
                "Impossible de charger le fichier de propri\u00E9t\u00E9s ''{0}'' pour la m\u00E9thode de sortie ''{1}'' (v\u00E9rifier CLASSPATH)" },

            {   MsgKey.ER_INVALID_PORT,
                "Num\u00E9ro de port non valide" },

            {   MsgKey.ER_PORT_WHEN_HOST_NULL,
                "Impossible de d\u00E9finir le port quand l'h\u00F4te est NULL" },

            {   MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
                "Le format de l'adresse de l'h\u00F4te n'est pas correct" },

            {   MsgKey.ER_SCHEME_NOT_CONFORMANT,
                "Le mod\u00E8le n'est pas conforme." },

            {   MsgKey.ER_SCHEME_FROM_NULL_STRING,
                "Impossible de d\u00E9finir le mod\u00E8le \u00E0 partir de la cha\u00EEne NULL" },

            {   MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
                "Le chemin d'acc\u00E8s contient une s\u00E9quence d'\u00E9chappement non valide" },

            {   MsgKey.ER_PATH_INVALID_CHAR,
                "Le chemin contient un caract\u00E8re non valide : {0}" },

            {   MsgKey.ER_FRAG_INVALID_CHAR,
                "Le fragment contient un caract\u00E8re non valide" },

            {   MsgKey.ER_FRAG_WHEN_PATH_NULL,
                "Impossible de d\u00E9finir le fragment quand le chemin d'acc\u00E8s est NULL" },

            {   MsgKey.ER_FRAG_FOR_GENERIC_URI,
                "Le fragment ne peut \u00EAtre d\u00E9fini que pour un URI g\u00E9n\u00E9rique" },

            {   MsgKey.ER_NO_SCHEME_IN_URI,
                "Mod\u00E8le introuvable dans l'URI" },

            {   MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
                "Impossible d'initialiser l'URI avec des param\u00E8tres vides" },

            {   MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
                "Le fragment ne doit pas \u00EAtre indiqu\u00E9 \u00E0 la fois dans le chemin et dans le fragment" },

            {   MsgKey.ER_NO_QUERY_STRING_IN_PATH,
                "La cha\u00EEne de requ\u00EAte ne doit pas figurer dans un chemin et une cha\u00EEne de requ\u00EAte" },

            {   MsgKey.ER_NO_PORT_IF_NO_HOST,
                "Le port peut ne pas \u00EAtre sp\u00E9cifi\u00E9 si l'h\u00F4te ne l'est pas" },

            {   MsgKey.ER_NO_USERINFO_IF_NO_HOST,
                "Userinfo peut ne pas \u00EAtre sp\u00E9cifi\u00E9 si l'h\u00F4te ne l'est pas" },

            {   MsgKey.ER_XML_VERSION_NOT_SUPPORTED,
                "Avertissement : la version du document de sortie doit \u00EAtre ''{0}''. Cette version XML n''est pas prise en charge. La version du document de sortie sera ''1.0''." },

            {   MsgKey.ER_SCHEME_REQUIRED,
                "Mod\u00E8le obligatoire." },

            /*
             * Note to translators:  The words 'Properties' and
             * 'SerializerFactory' in this message are Java class names
             * and should not be translated.
             */
            {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
                "L''objet de propri\u00E9t\u00E9s transmis \u00E0 SerializerFactory ne comporte aucune propri\u00E9t\u00E9 ''{0}''." },

            {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
                "Avertissement : l''encodage ''{0}'' n''est pas pris en charge par l''ex\u00E9cution Java." },


        };

        return contents;
    }
}
