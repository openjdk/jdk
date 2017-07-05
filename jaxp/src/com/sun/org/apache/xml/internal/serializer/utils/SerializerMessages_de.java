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
 * $Id: SerializerMessages_de.java /st_wptg_1.8.0.0.0jdk/2 2013/09/16 04:56:10 gmolloy Exp $
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
public class SerializerMessages_de extends ListResourceBundle {

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
                "Der Nachrichtenschl\u00FCssel \"{0}\" ist nicht in der Nachrichtenklasse \"{1}\" enthalten" },

            {   MsgKey.BAD_MSGFORMAT,
                "Das Format der Nachricht \"{0}\" in der Nachrichtenklasse \"{1}\" war nicht erfolgreich." },

            {   MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
                "Serializer-Klasse \"{0}\" implementiert org.xml.sax.ContentHandler nicht." },

            {   MsgKey.ER_RESOURCE_COULD_NOT_FIND,
                    "Ressource [ {0} ] konnte nicht gefunden werden.\n {1}" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
                    "Ressource [ {0} ] konnte nicht geladen werden: {1} \n {2} \t {3}" },

            {   MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
                    "Puffergr\u00F6\u00DFe <=0" },

            {   MsgKey.ER_INVALID_UTF16_SURROGATE,
                    "Ung\u00FCltige UTF-16-Ersetzung festgestellt: {0}?" },

            {   MsgKey.ER_OIERROR,
                "I/O-Fehler" },

            {   MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
                "Attribut {0} kann nicht nach untergeordneten Knoten oder vor dem Erstellen eines Elements hinzugef\u00FCgt werden. Attribut wird ignoriert." },

            /*
             * Note to translators:  The stylesheet contained a reference to a
             * namespace prefix that was undefined.  The value of the substitution
             * text is the name of the prefix.
             */
            {   MsgKey.ER_NAMESPACE_PREFIX,
                "Namespace f\u00FCr Pr\u00E4fix \"{0}\" wurde nicht deklariert." },

            /*
             * Note to translators:  This message is reported if the stylesheet
             * being processed attempted to construct an XML document with an
             * attribute in a place other than on an element.  The substitution text
             * specifies the name of the attribute.
             */
            {   MsgKey.ER_STRAY_ATTRIBUTE,
                "Attribut \"{0}\" au\u00DFerhalb des Elements." },

            /*
             * Note to translators:  As with the preceding message, a namespace
             * declaration has the form of an attribute and is only permitted to
             * appear on an element.  The substitution text {0} is the namespace
             * prefix and {1} is the URI that was being used in the erroneous
             * namespace declaration.
             */
            {   MsgKey.ER_STRAY_NAMESPACE,
                "Namespace-Deklaration {0}={1} au\u00DFerhalb des Elements." },

            {   MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
                "\"{0}\" konnte nicht geladen werden (CLASSPATH pr\u00FCfen). Die Standardwerte werden verwendet" },

            {   MsgKey.ER_ILLEGAL_CHARACTER,
                "Versuch, Zeichen mit Integralwert {0} auszugeben, das nicht in der speziellen Ausgabecodierung von {1} dargestellt wird." },

            {   MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
                "Property-Datei \"{0}\" konnte f\u00FCr Ausgabemethode \"{1}\" nicht geladen werden (CLASSPATH pr\u00FCfen)" },

            {   MsgKey.ER_INVALID_PORT,
                "Ung\u00FCltige Portnummer" },

            {   MsgKey.ER_PORT_WHEN_HOST_NULL,
                "Port kann nicht festgelegt werden, wenn der Host null ist" },

            {   MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
                "Host ist keine wohlgeformte Adresse" },

            {   MsgKey.ER_SCHEME_NOT_CONFORMANT,
                "Schema ist nicht konform." },

            {   MsgKey.ER_SCHEME_FROM_NULL_STRING,
                "Schema kann nicht von Nullzeichenfolge festgelegt werden" },

            {   MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
                "Pfad enth\u00E4lt eine ung\u00FCltige Escapesequenz" },

            {   MsgKey.ER_PATH_INVALID_CHAR,
                "Pfad enth\u00E4lt ung\u00FCltiges Zeichen: {0}" },

            {   MsgKey.ER_FRAG_INVALID_CHAR,
                "Fragment enth\u00E4lt ein ung\u00FCltiges Zeichen" },

            {   MsgKey.ER_FRAG_WHEN_PATH_NULL,
                "Fragment kann nicht festgelegt werden, wenn der Pfad null ist" },

            {   MsgKey.ER_FRAG_FOR_GENERIC_URI,
                "Fragment kann nur f\u00FCr einen generischen URI festgelegt werden" },

            {   MsgKey.ER_NO_SCHEME_IN_URI,
                "Kein Schema gefunden in URI" },

            {   MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
                "URI kann nicht mit leeren Parametern initialisiert werden" },

            {   MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
                "Fragment kann nicht im Pfad und im Fragment angegeben werden" },

            {   MsgKey.ER_NO_QUERY_STRING_IN_PATH,
                "Abfragezeichenfolge kann nicht im Pfad und in der Abfragezeichenfolge angegeben werden" },

            {   MsgKey.ER_NO_PORT_IF_NO_HOST,
                "Port kann nicht angegeben werden, wenn der Host nicht angegeben wurde" },

            {   MsgKey.ER_NO_USERINFO_IF_NO_HOST,
                "Benutzerinformationen k\u00F6nnen nicht angegeben werden, wenn der Host nicht angegeben wurde" },

            {   MsgKey.ER_XML_VERSION_NOT_SUPPORTED,
                "Warnung: Die Version des Ausgabedokuments soll \"{0}\" sein. Diese Version von XML wird nicht unterst\u00FCtzt. Die Version des Ausgabedokuments wird \"1.0\" sein." },

            {   MsgKey.ER_SCHEME_REQUIRED,
                "Schema ist erforderlich." },

            /*
             * Note to translators:  The words 'Properties' and
             * 'SerializerFactory' in this message are Java class names
             * and should not be translated.
             */
            {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
                "Das an die SerializerFactory \u00FCbergebene Properties-Objekt verf\u00FCgt \u00FCber keine Eigenschaft \"{0}\"." },

            {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
                "Warnung: Die Codierung \"{0}\" wird nicht von der Java-Laufzeit unterst\u00FCtzt." },


        };

        return contents;
    }
}
