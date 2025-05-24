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
                "La clé de message ''{0}'' ne figure pas dans la classe de messages ''{1}''" },

            {   MsgKey.BAD_MSGFORMAT,
                "Echec du format de message ''{0}'' dans la classe de messages ''{1}''." },

            {   MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
                "La classe de serializer ''{0}'' n''implémente pas org.xml.sax.ContentHandler." },

            {   MsgKey.ER_RESOURCE_COULD_NOT_FIND,
                    "La ressource [ {0} ] est introuvable.\n {1}" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
                    "La ressource [ {0} ] n''a pas pu charger : {1} \n {2} \t {3}" },

            {   MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
                    "Taille du tampon <=0" },

            {   MsgKey.ER_INVALID_UTF16_SURROGATE,
                    "Substitut UTF-16 non valide détecté : {0} ?" },

            {   MsgKey.ER_OIERROR,
                "Erreur d'E/S" },

            {   MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
                "Impossible d''ajouter l''attribut {0} après des noeuds enfant ou avant la production d''un élément. L''attribut est ignoré." },

            /*
             * Note to translators:  The stylesheet contained a reference to a
             * namespace prefix that was undefined.  The value of the substitution
             * text is the name of the prefix.
             */
            {   MsgKey.ER_NAMESPACE_PREFIX,
                "L''espace de noms du préfixe ''{0}'' n''a pas été déclaré." },

            /*
             * Note to translators:  This message is reported if the stylesheet
             * being processed attempted to construct an XML document with an
             * attribute in a place other than on an element.  The substitution text
             * specifies the name of the attribute.
             */
            {   MsgKey.ER_STRAY_ATTRIBUTE,
                "Attribut ''{0}'' en dehors de l''élément." },

            /*
             * Note to translators:  As with the preceding message, a namespace
             * declaration has the form of an attribute and is only permitted to
             * appear on an element.  The substitution text {0} is the namespace
             * prefix and {1} is the URI that was being used in the erroneous
             * namespace declaration.
             */
            {   MsgKey.ER_STRAY_NAMESPACE,
                "La déclaration d''espace de noms ''{0}''=''{1}'' est à l''extérieur de l''élément." },

            {   MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
                "Impossible de charger ''{0}'' (vérifier CLASSPATH), les valeurs par défaut sont donc employées" },

            {   MsgKey.ER_ILLEGAL_CHARACTER,
                "Tentative de sortie d''un caractère avec une valeur entière {0}, non représenté dans l''encodage de sortie spécifié pour {1}." },

            {   MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
                "Impossible de charger le fichier de propriétés ''{0}'' pour la méthode de sortie ''{1}'' (vérifier CLASSPATH)" },

            {   MsgKey.ER_INVALID_PORT,
                "Numéro de port non valide" },

            {   MsgKey.ER_PORT_WHEN_HOST_NULL,
                "Impossible de définir le port quand l'hôte est NULL" },

            {   MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
                "Le format de l'adresse de l'hôte n'est pas correct" },

            {   MsgKey.ER_SCHEME_NOT_CONFORMANT,
                "Le modèle n'est pas conforme." },

            {   MsgKey.ER_SCHEME_FROM_NULL_STRING,
                "Impossible de définir le modèle à partir de la chaîne NULL" },

            {   MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
                "Le chemin d'accès contient une séquence d'échappement non valide" },

            {   MsgKey.ER_PATH_INVALID_CHAR,
                "Le chemin contient un caractère non valide : {0}" },

            {   MsgKey.ER_FRAG_INVALID_CHAR,
                "Le fragment contient un caractère non valide" },

            {   MsgKey.ER_FRAG_WHEN_PATH_NULL,
                "Impossible de définir le fragment quand le chemin d'accès est NULL" },

            {   MsgKey.ER_FRAG_FOR_GENERIC_URI,
                "Le fragment ne peut être défini que pour un URI générique" },

            {   MsgKey.ER_NO_SCHEME_IN_URI,
                "Modèle introuvable dans l'URI" },

            {   MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
                "Impossible d'initialiser l'URI avec des paramètres vides" },

            {   MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
                "Le fragment ne doit pas être indiqué à la fois dans le chemin et dans le fragment" },

            {   MsgKey.ER_NO_QUERY_STRING_IN_PATH,
                "La chaîne de requête ne doit pas figurer dans un chemin et une chaîne de requête" },

            {   MsgKey.ER_NO_PORT_IF_NO_HOST,
                "Le port peut ne pas être spécifié si l'hôte ne l'est pas" },

            {   MsgKey.ER_NO_USERINFO_IF_NO_HOST,
                "Userinfo peut ne pas être spécifié si l'hôte ne l'est pas" },

            {   MsgKey.ER_XML_VERSION_NOT_SUPPORTED,
                "Avertissement : la version du document de sortie doit être ''{0}''. Cette version XML n''est pas prise en charge. La version du document de sortie sera ''1.0''." },

            {   MsgKey.ER_SCHEME_REQUIRED,
                "Modèle obligatoire." },

            /*
             * Note to translators:  The words 'Properties' and
             * 'SerializerFactory' in this message are Java class names
             * and should not be translated.
             */
            {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
                "L''objet de propriétés transmis à SerializerFactory ne comporte aucune propriété ''{0}''." },

            {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
                "Avertissement : l''encodage ''{0}'' n''est pas pris en charge par l''exécution Java." },

             {MsgKey.ER_FEATURE_NOT_FOUND,
             "Le paramètre ''{0}'' n''est pas reconnu."},

             {MsgKey.ER_FEATURE_NOT_SUPPORTED,
             "Le paramètre ''{0}'' est reconnu mais la valeur demandée ne peut pas être définie."},

             {MsgKey.ER_STRING_TOO_LONG,
             "La chaîne obtenue est trop longue pour tenir dans un élément DOMString : ''{0}''."},

             {MsgKey.ER_TYPE_MISMATCH_ERR,
             "Le type de valeur pour ce nom de paramètre n'est pas compatible avec le type de valeur attendu. "},

             {MsgKey.ER_NO_OUTPUT_SPECIFIED,
             "La destination de sortie dans laquelle écrire les données est NULL."},

             {MsgKey.ER_UNSUPPORTED_ENCODING,
             "Un encodage non pris en charge a été détecté."},

             {MsgKey.ER_UNABLE_TO_SERIALIZE_NODE,
             "Le noeud n'a pas pu être sérialisé."},

             {MsgKey.ER_CDATA_SECTIONS_SPLIT,
             "La section CDATA contient des marqueurs de fin ']]>'."},

             {MsgKey.ER_WARNING_WF_NOT_CHECKED,
                 "Une instance du vérificateur de format correct n'a pas pu être créée. Le paramètre de format correct a été défini sur True mais la vérification de format correct n'a pas pu être réalisée."
             },

             {MsgKey.ER_WF_INVALID_CHARACTER,
                 "Le noeud ''{0}'' contient des caractères XML non valides."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_COMMENT,
                 "Un caractère XML non valide (Unicode : 0x{0}) a été détecté dans le commentaire."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_PI,
                 "Un caractère XML non valide (Unicode : 0x{0}) a été détecté dans les données d''instruction de traitement."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_CDATA,
                 "Un caractère XML non valide (Unicode : 0x{0}) a été détecté dans le contenu de la section CDATA."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_TEXT,
                 "Un caractère XML non valide (Unicode : 0x{0}) a été détecté dans le contenu des données alphanumériques du noeud."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_NODE_NAME,
                 "Un caractère XML non valide a été détecté dans le noeud {0} nommé ''{1}''."
             },

             { MsgKey.ER_WF_DASH_IN_COMMENT,
                 "La chaîne \"--\" n'est pas autorisée dans les commentaires."
             },

             {MsgKey.ER_WF_LT_IN_ATTVAL,
                 "La valeur de l''attribut \"{1}\" associé à un type d''élément \"{0}\" ne doit pas contenir le caractère ''<''."
             },

             {MsgKey.ER_WF_REF_TO_UNPARSED_ENT,
                 "La référence d''entité non analysée \"&{0};\" n''est pas autorisée."
             },

             {MsgKey.ER_WF_REF_TO_EXTERNAL_ENT,
                 "La référence d''entité externe \"&{0};\" n''est pas autorisée dans une valeur d''attribut."
             },

             {MsgKey.ER_NS_PREFIX_CANNOT_BE_BOUND,
                 "Le préfixe \"{0}\" ne peut pas être lié à l''espace de noms \"{1}\"."
             },

             {MsgKey.ER_NULL_LOCAL_ELEMENT_NAME,
                 "Le nom local de l''élément \"{0}\" est NULL."
             },

             {MsgKey.ER_NULL_LOCAL_ATTR_NAME,
                 "Le nom local de l''attribut \"{0}\" est NULL."
             },

             { MsgKey.ER_ELEM_UNBOUND_PREFIX_IN_ENTREF,
                 "Le texte de remplacement du noeud d''entité \"{0}\" contient un noeud d''élément \"{1}\" avec un préfixe non lié \"{2}\"."
             },

             { MsgKey.ER_ATTR_UNBOUND_PREFIX_IN_ENTREF,
                 "Le texte de remplacement du noeud d''entité \"{0}\" contient un noeud d''attribut \"{1}\" avec un préfixe non lié \"{2}\"."
             },

             { MsgKey.ER_WRITING_INTERNAL_SUBSET,
                 "Une erreur s'est produite lors de l'écriture du sous-ensemble interne."
             },

        };

        return contents;
    }
}
