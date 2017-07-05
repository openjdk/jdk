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
 * $Id: SerializerMessages_sv.java /st_wptg_1.8.0.0.0jdk/2 2013/09/16 06:50:58 gmolloy Exp $
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
public class SerializerMessages_sv extends ListResourceBundle {

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
                "Meddelandenyckeln ''{0}'' \u00E4r inte i meddelandeklassen ''{1}''" },

            {   MsgKey.BAD_MSGFORMAT,
                "Formatet p\u00E5 meddelandet ''{0}'' i meddelandeklassen ''{1}'' underk\u00E4ndes." },

            {   MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
                "Serializerklassen ''{0}'' implementerar inte org.xml.sax.ContentHandler." },

            {   MsgKey.ER_RESOURCE_COULD_NOT_FIND,
                    "Resursen [ {0} ] kunde inte h\u00E4mtas.\n {1}" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
                    "Resursen [ {0} ] kunde inte laddas: {1} \n {2} \t {3}" },

            {   MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
                    "Buffertstorlek <=0" },

            {   MsgKey.ER_INVALID_UTF16_SURROGATE,
                    "Ogiltigt UTF-16-surrogat uppt\u00E4ckt: {0} ?" },

            {   MsgKey.ER_OIERROR,
                "IO-fel" },

            {   MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
                "Kan inte l\u00E4gga till attributet {0} efter underordnade noder eller innan ett element har skapats. Attributet ignoreras." },

            /*
             * Note to translators:  The stylesheet contained a reference to a
             * namespace prefix that was undefined.  The value of the substitution
             * text is the name of the prefix.
             */
            {   MsgKey.ER_NAMESPACE_PREFIX,
                "Namnrymd f\u00F6r prefix ''{0}'' har inte deklarerats." },

            /*
             * Note to translators:  This message is reported if the stylesheet
             * being processed attempted to construct an XML document with an
             * attribute in a place other than on an element.  The substitution text
             * specifies the name of the attribute.
             */
            {   MsgKey.ER_STRAY_ATTRIBUTE,
                "Attributet ''{0}'' finns utanf\u00F6r elementet." },

            /*
             * Note to translators:  As with the preceding message, a namespace
             * declaration has the form of an attribute and is only permitted to
             * appear on an element.  The substitution text {0} is the namespace
             * prefix and {1} is the URI that was being used in the erroneous
             * namespace declaration.
             */
            {   MsgKey.ER_STRAY_NAMESPACE,
                "Namnrymdsdeklarationen ''{0}''=''{1}'' finns utanf\u00F6r element." },

            {   MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
                "Kunde inte ladda ''{0}'' (kontrollera CLASSPATH), anv\u00E4nder nu enbart standardv\u00E4rden" },

            {   MsgKey.ER_ILLEGAL_CHARACTER,
                "F\u00F6rs\u00F6k att skriva utdatatecken med integralv\u00E4rdet {0} som inte \u00E4r representerat i angiven utdatakodning av {1}." },

            {   MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
                "Kunde inte ladda egenskapsfilen ''{0}'' f\u00F6r utdatametoden ''{1}'' (kontrollera CLASSPATH)" },

            {   MsgKey.ER_INVALID_PORT,
                "Ogiltigt portnummer" },

            {   MsgKey.ER_PORT_WHEN_HOST_NULL,
                "Port kan inte st\u00E4llas in n\u00E4r v\u00E4rd \u00E4r null" },

            {   MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
                "V\u00E4rd \u00E4r inte en v\u00E4lformulerad adress" },

            {   MsgKey.ER_SCHEME_NOT_CONFORMANT,
                "Schemat \u00E4r inte likformigt." },

            {   MsgKey.ER_SCHEME_FROM_NULL_STRING,
                "Kan inte st\u00E4lla in schema fr\u00E5n null-str\u00E4ng" },

            {   MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
                "S\u00F6kv\u00E4gen inneh\u00E5ller en ogiltig escape-sekvens" },

            {   MsgKey.ER_PATH_INVALID_CHAR,
                "S\u00F6kv\u00E4gen inneh\u00E5ller ett ogiltigt tecken: {0}" },

            {   MsgKey.ER_FRAG_INVALID_CHAR,
                "Fragment inneh\u00E5ller ett ogiltigt tecken" },

            {   MsgKey.ER_FRAG_WHEN_PATH_NULL,
                "Fragment kan inte st\u00E4llas in n\u00E4r s\u00F6kv\u00E4g \u00E4r null" },

            {   MsgKey.ER_FRAG_FOR_GENERIC_URI,
                "Fragment kan bara st\u00E4llas in f\u00F6r en allm\u00E4n URI" },

            {   MsgKey.ER_NO_SCHEME_IN_URI,
                "Schema saknas i URI" },

            {   MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
                "Kan inte initiera URI med tomma parametrar" },

            {   MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
                "Fragment kan inte anges i b\u00E5de s\u00F6kv\u00E4gen och fragmentet" },

            {   MsgKey.ER_NO_QUERY_STRING_IN_PATH,
                "Fr\u00E5gestr\u00E4ng kan inte anges i b\u00E5de s\u00F6kv\u00E4gen och fr\u00E5gestr\u00E4ngen" },

            {   MsgKey.ER_NO_PORT_IF_NO_HOST,
                "Port f\u00E5r inte anges om v\u00E4rden inte \u00E4r angiven" },

            {   MsgKey.ER_NO_USERINFO_IF_NO_HOST,
                "Anv\u00E4ndarinfo f\u00E5r inte anges om v\u00E4rden inte \u00E4r angiven" },

            {   MsgKey.ER_XML_VERSION_NOT_SUPPORTED,
                "Varning:  Versionen av utdatadokumentet som beg\u00E4rts \u00E4r ''{0}''.  Den h\u00E4r versionen av XML st\u00F6ds inte.  Versionen av utdatadokumentet kommer att vara ''1.0''." },

            {   MsgKey.ER_SCHEME_REQUIRED,
                "Schema kr\u00E4vs!" },

            /*
             * Note to translators:  The words 'Properties' and
             * 'SerializerFactory' in this message are Java class names
             * and should not be translated.
             */
            {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
                "Egenskapsobjektet som \u00F6verf\u00F6rts till SerializerFactory har ingen ''{0}''-egenskap." },

            {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
                "Varning: Kodningen ''{0}'' st\u00F6ds inte av Java runtime." },


        };

        return contents;
    }
}
