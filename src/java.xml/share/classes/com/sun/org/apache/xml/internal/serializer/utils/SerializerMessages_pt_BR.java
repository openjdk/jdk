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
public class SerializerMessages_pt_BR extends ListResourceBundle {

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
                "A chave de mensagem ''{0}'' não está na classe de mensagem ''{1}''" },

            {   MsgKey.BAD_MSGFORMAT,
                "Houve falha no formato da mensagem ''{0}'' na classe de mensagem ''{1}''." },

            {   MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
                "A classe ''{0}'' do serializador não implementa org.xml.sax.ContentHandler." },

            {   MsgKey.ER_RESOURCE_COULD_NOT_FIND,
                    "Não foi possível encontrar o recurso [ {0} ].\n {1}" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
                    "O recurso [ {0} ] não foi carregado: {1} \n {2} \t {3}" },

            {   MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
                    "Tamanho do buffer <=0" },

            {   MsgKey.ER_INVALID_UTF16_SURROGATE,
                    "Foi detectado um substituto de UTF-16 inválido: {0} ?" },

            {   MsgKey.ER_OIERROR,
                "Erro de E/S" },

            {   MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
                "Não é possível adicionar o atributo {0} depois dos nós filhos ou antes que um elemento seja produzido. O atributo será ignorado." },

            /*
             * Note to translators:  The stylesheet contained a reference to a
             * namespace prefix that was undefined.  The value of the substitution
             * text is the name of the prefix.
             */
            {   MsgKey.ER_NAMESPACE_PREFIX,
                "O namespace do prefixo ''{0}'' não foi declarado." },

            /*
             * Note to translators:  This message is reported if the stylesheet
             * being processed attempted to construct an XML document with an
             * attribute in a place other than on an element.  The substitution text
             * specifies the name of the attribute.
             */
            {   MsgKey.ER_STRAY_ATTRIBUTE,
                "Atributo ''{0}'' fora do elemento." },

            /*
             * Note to translators:  As with the preceding message, a namespace
             * declaration has the form of an attribute and is only permitted to
             * appear on an element.  The substitution text {0} is the namespace
             * prefix and {1} is the URI that was being used in the erroneous
             * namespace declaration.
             */
            {   MsgKey.ER_STRAY_NAMESPACE,
                "Declaração de namespace ''{0}''=''{1}'' fora do elemento." },

            {   MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
                "Não foi possível carregar ''{0}'' (verificar CLASSPATH); usando agora apenas os padrões" },

            {   MsgKey.ER_ILLEGAL_CHARACTER,
                "Tentativa de exibir um caractere de valor integral {0} que não está representado na codificação de saída especificada de {1}." },

            {   MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
                "Não foi possível carregar o arquivo de propriedade ''{0}'' para o método de saída ''{1}'' (verificar CLASSPATH)" },

            {   MsgKey.ER_INVALID_PORT,
                "Número de porta inválido" },

            {   MsgKey.ER_PORT_WHEN_HOST_NULL,
                "A porta não pode ser definida quando o host é nulo" },

            {   MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
                "O host não é um endereço correto" },

            {   MsgKey.ER_SCHEME_NOT_CONFORMANT,
                "O esquema não é compatível." },

            {   MsgKey.ER_SCHEME_FROM_NULL_STRING,
                "Não é possível definir o esquema de uma string nula" },

            {   MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
                "O caminho contém uma sequência inválida de caracteres de escape" },

            {   MsgKey.ER_PATH_INVALID_CHAR,
                "O caminho contém um caractere inválido: {0}" },

            {   MsgKey.ER_FRAG_INVALID_CHAR,
                "O fragmento contém um caractere inválido" },

            {   MsgKey.ER_FRAG_WHEN_PATH_NULL,
                "O fragmento não pode ser definido quando o caminho é nulo" },

            {   MsgKey.ER_FRAG_FOR_GENERIC_URI,
                "O fragmento só pode ser definido para um URI genérico" },

            {   MsgKey.ER_NO_SCHEME_IN_URI,
                "Nenhum esquema encontrado no URI" },

            {   MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
                "Não é possível inicializar o URI com parâmetros vazios" },

            {   MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
                "O fragmento não pode ser especificado no caminho nem no fragmento" },

            {   MsgKey.ER_NO_QUERY_STRING_IN_PATH,
                "A string de consulta não pode ser especificada no caminho nem na string de consulta" },

            {   MsgKey.ER_NO_PORT_IF_NO_HOST,
                "A porta não pode ser especificada se o host não tiver sido especificado" },

            {   MsgKey.ER_NO_USERINFO_IF_NO_HOST,
                "As informações do usuário não podem ser especificadas se o host não tiver sido especificado" },

            {   MsgKey.ER_XML_VERSION_NOT_SUPPORTED,
                "Advertência: a versão do documento de saída deve ser obrigatoriamente ''{0}''. Esta versão do XML não é suportada. A versão do documento de saída será ''1.0''." },

            {   MsgKey.ER_SCHEME_REQUIRED,
                "O esquema é obrigatório!" },

            /*
             * Note to translators:  The words 'Properties' and
             * 'SerializerFactory' in this message are Java class names
             * and should not be translated.
             */
            {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
                "O objeto Properties especificado para a SerializerFactory não tem uma propriedade ''{0}''." },

            {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
                "Advertência: a codificação ''{0}'' não é suportada pelo Java runtime." },

             {MsgKey.ER_FEATURE_NOT_FOUND,
             "O parâmetro ''{0}'' não é reconhecido."},

             {MsgKey.ER_FEATURE_NOT_SUPPORTED,
             "O parâmetro ''{0}'' é reconhecido, mas o valor solicitado não pode ser definido."},

             {MsgKey.ER_STRING_TOO_LONG,
             "A string resultante é muito longa para se ajustar a uma DOMString: ''{0}''."},

             {MsgKey.ER_TYPE_MISMATCH_ERR,
             "O tipo de valor do nome deste parâmetro é incompatível com o tipo de valor esperado."},

             {MsgKey.ER_NO_OUTPUT_SPECIFIED,
             "O destino da saída dos dados a serem gravados era nulo."},

             {MsgKey.ER_UNSUPPORTED_ENCODING,
             "Uma codificação não suportada foi encontrada."},

             {MsgKey.ER_UNABLE_TO_SERIALIZE_NODE,
             "Não foi possível serializar o nó."},

             {MsgKey.ER_CDATA_SECTIONS_SPLIT,
             "A Seção CDATA contém um ou mais marcadores de término ']]>'."},

             {MsgKey.ER_WARNING_WF_NOT_CHECKED,
                 "Não foi possível criar uma instância do verificador de Formato Correto. O parâmetro formatado corretamente foi definido como verdadeiro, mas a verificação de formato correto não pode ser executada."
             },

             {MsgKey.ER_WF_INVALID_CHARACTER,
                 "O nó ''{0}'' contém caracteres XML inválidos."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_COMMENT,
                 "Um caractere XML inválido (Unicode: 0x{0}) foi encontrado no comentário."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_PI,
                 "Um caractere XML inválido (Unicode: 0x{0}) foi encontrado nos dados da instrução de processamento."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_CDATA,
                 "Um caractere XML inválido (Unicode: 0x {0}) foi encontrado no conteúdo da Seção CDATA."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_TEXT,
                 "Um caractere XML inválido (Unicode: 0x {0}) foi encontrado no conteúdo dos dados de caracteres do nó."
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_NODE_NAME,
                 "Um ou mais caracteres XML inválidos foram encontrados no nó {0} chamado ''{1}''."
             },

             { MsgKey.ER_WF_DASH_IN_COMMENT,
                 "A string \"--\" não é permitida nos comentários."
             },

             {MsgKey.ER_WF_LT_IN_ATTVAL,
                 "O valor do atributo \"{1}\" associado a um tipo de elemento \"{0}\" não deve conter o caractere ''<''."
             },

             {MsgKey.ER_WF_REF_TO_UNPARSED_ENT,
                 "A referência da entidade não submetida a parsing \"&{0};\" não é permitida."
             },

             {MsgKey.ER_WF_REF_TO_EXTERNAL_ENT,
                 "A referência da entidade externa \"&{0};\" não é permitida em um valor do atributo."
             },

             {MsgKey.ER_NS_PREFIX_CANNOT_BE_BOUND,
                 "O prefixo \"{0}\" não pode ser vinculado ao namespace \"{1}\"."
             },

             {MsgKey.ER_NULL_LOCAL_ELEMENT_NAME,
                 "O nome local do elemento \"{0}\" é nulo."
             },

             {MsgKey.ER_NULL_LOCAL_ATTR_NAME,
                 "O nome local do atributo \"{0}\" é nulo."
             },

             { MsgKey.ER_ELEM_UNBOUND_PREFIX_IN_ENTREF,
                 "O texto de substituição do nó \"{0}\" de entidade contém um nó \"{1}\" de elemento com um prefixo desvinculado \"{2}\"."
             },

             { MsgKey.ER_ATTR_UNBOUND_PREFIX_IN_ENTREF,
                 "O texto de substituição do nó \"{0}\" de entidade contém um nó \"{1}\" de atributo com um prefixo desvinculado \"{2}\"."
             },

             { MsgKey.ER_WRITING_INTERNAL_SUBSET,
                 "Ocorreu um erro ao gravar o subconjunto interno."
             },

        };

        return contents;
    }
}
