/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */

package com.sun.xml.internal.fastinfoset;

import java.io.UnsupportedEncodingException;

public final class EncodingConstants {
    static {
        initiateXMLDeclarationValues();
    }

    public static final String XML_NAMESPACE_PREFIX = "xml";
    public static final int XML_NAMESPACE_PREFIX_LENGTH = XML_NAMESPACE_PREFIX.length();
    public static final String XML_NAMESPACE_NAME = "http://www.w3.org/XML/1998/namespace";
    public static final int XML_NAMESPACE_NAME_LENGTH = XML_NAMESPACE_NAME.length();

    public static final String XMLNS_NAMESPACE_PREFIX = "xmlns";
    public static final int XMLNS_NAMESPACE_PREFIX_LENGTH = XMLNS_NAMESPACE_PREFIX.length();
    public static final String XMLNS_NAMESPACE_NAME = "http://www.w3.org/2000/xmlns/";
    public static final int XMLNS_NAMESPACE_NAME_LENGTH = XMLNS_NAMESPACE_NAME.length();

    public static final QualifiedName DEFAULT_NAMESPACE_DECLARATION = new QualifiedName(
            "",
            EncodingConstants.XMLNS_NAMESPACE_NAME,
            EncodingConstants.XMLNS_NAMESPACE_PREFIX,
            EncodingConstants.XMLNS_NAMESPACE_PREFIX);

    public static final int DOCUMENT_ADDITIONAL_DATA_FLAG = 0x40; // 01000000
    public static final int DOCUMENT_INITIAL_VOCABULARY_FLAG = 0x20; // 00100000
    public static final int DOCUMENT_NOTATIONS_FLAG = 0x10; // 00010000
    public static final int DOCUMENT_UNPARSED_ENTITIES_FLAG = 0x08; // 00001000
    public static final int DOCUMENT_CHARACTER_ENCODING_SCHEME = 0x04; // 00000100
    public static final int DOCUMENT_STANDALONE_FLAG = 0x02; // 00000010
    public static final int DOCUMENT_VERSION_FLAG = 0x01; // 00000001

    public static final int INITIAL_VOCABULARY_EXTERNAL_VOCABULARY_FLAG = 0x10; // 00010000
    public static final int INITIAL_VOCABULARY_RESTRICTED_ALPHABETS_FLAG = 0x08; // 00001000
    public static final int INITIAL_VOCABULARY_ENCODING_ALGORITHMS_FLAG = 0x04; // 00000100
    public static final int INITIAL_VOCABULARY_PREFIXES_FLAG = 0x02; // 00000010
    public static final int INITIAL_VOCABULARY_NAMESPACE_NAMES_FLAG = 0x01; // 00000001
    public static final int INITIAL_VOCABULARY_LOCAL_NAMES_FLAG = 0x80; // 1000000
    public static final int INITIAL_VOCABULARY_OTHER_NCNAMES_FLAG = 0x40; // 01000000
    public static final int INITIAL_VOCABULARY_OTHER_URIS_FLAG = 0x20; // 00100000
    public static final int INITIAL_VOCABULARY_ATTRIBUTE_VALUES_FLAG = 0x10; // 00010000
    public static final int INITIAL_VOCABULARY_CONTENT_CHARACTER_CHUNKS_FLAG = 0x08; // 00001000
    public static final int INITIAL_VOCABULARY_OTHER_STRINGS_FLAG = 0x04; // 00000100
    public static final int INITIAL_VOCABULARY_ELEMENT_NAME_SURROGATES_FLAG = 0x02; // 0000010
    public static final int INITIAL_VOCABULARY_ATTRIBUTE_NAME_SURROGATES_FLAG = 0x01; // 00000001

    public static final int NAME_SURROGATE_PREFIX_FLAG = 0x02;
    public static final int NAME_SURROGATE_NAME_FLAG = 0x01;

    public static final int NOTATIONS = 0xC0; // 110000
    public static final int NOTATIONS_MASK = 0xFC; // 6 bits
    public static final int NOTATIONS_SYSTEM_IDENTIFIER_FLAG = 0x02;
    public static final int NOTATIONS_PUBLIC_IDENTIFIER_FLAG = 0x01;

    public static final int UNPARSED_ENTITIES = 0xD0; // 1101000
    public static final int UNPARSED_ENTITIES_MASK = 0xFE; // 7 bits
    public static final int UNPARSED_ENTITIES_PUBLIC_IDENTIFIER_FLAG = 0x01;

    public static final int PROCESSING_INSTRUCTION = 0xE1; // 11100001
    public static final int PROCESSING_INSTRUCTION_MASK = 0xFF; // 8 bits

    public static final int COMMENT = 0xE2; // 11100010
    public static final int COMMENT_MASK = 0xFF; // 8 bits

    public static final int DOCUMENT_TYPE_DECLARATION = 0xC4; // 110001
    public static final int DOCUMENT_TYPE_DECLARATION_MASK = 0xFC; // 6 bits
    public static final int DOCUMENT_TYPE_SYSTEM_IDENTIFIER_FLAG = 0x02;
    public static final int DOCUMENT_TYPE_PUBLIC_IDENTIFIER_FLAG = 0x01;

    public static final int ELEMENT = 0x00; // 0
    public static final int ELEMENT_ATTRIBUTE_FLAG = 0x40; // 01000000
    public static final int ELEMENT_NAMESPACES_FLAG = 0x38; // 00111000
    public static final int ELEMENT_LITERAL_QNAME_FLAG = 0x3C; // 00111100

    public static final int NAMESPACE_ATTRIBUTE = 0xCC; // 110011 00
    public static final int NAMESPACE_ATTRIBUTE_MASK = 0xFC; // 6 bits
    public static final int NAMESPACE_ATTRIBUTE_PREFIX_NAME_MASK = 0x03; // 2 bits
    public static final int NAMESPACE_ATTRIBUTE_PREFIX_FLAG = 0x02;
    public static final int NAMESPACE_ATTRIBUTE_NAME_FLAG = 0x01;

    public static final int ATTRIBUTE_LITERAL_QNAME_FLAG = 0x78; // 01111000

    public static final int LITERAL_QNAME_PREFIX_NAMESPACE_NAME_MASK = 0x03;
    public static final int LITERAL_QNAME_PREFIX_FLAG = 0x02;
    public static final int LITERAL_QNAME_NAMESPACE_NAME_FLAG = 0x01;

    public static final int CHARACTER_CHUNK = 0x80; // 10
    public static final int CHARACTER_CHUNK_ADD_TO_TABLE_FLAG = 0x10; // 00010000
    public static final int CHARACTER_CHUNK_UTF_8_FLAG = 0x00; // 00000000
    public static final int CHARACTER_CHUNK_UTF_16_FLAG = 0x04; // 00000100
    public static final int CHARACTER_CHUNK_RESTRICTED_ALPHABET_FLAG = 0x08; // 00001000
    public static final int CHARACTER_CHUNK_ENCODING_ALGORITHM_FLAG = 0x0C; // 00001100

    public static final int UNEXPANDED_ENTITY_REFERENCE = 0xC8; // 110010
    public static final int UNEXPANDED_ENTITY_REFERENCE_MASK = 0xFC; // 6 bits
    public static final int UNEXPANDED_ENTITY_SYSTEM_IDENTIFIER_FLAG = 0x02;
    public static final int UNEXPANDED_ENTITY_PUBLIC_IDENTIFIER_FLAG = 0x01;

    public static final int NISTRING_ADD_TO_TABLE_FLAG = 0x40; // 01000000
    public static final int NISTRING_UTF_8_FLAG = 0x00; // 00000000
    public static final int NISTRING_UTF_16_FLAG = 0x10; // 00010000
    public static final int NISTRING_RESTRICTED_ALPHABET_FLAG = 0x20; // 00100000
    public static final int NISTRING_ENCODING_ALGORITHM_FLAG = 0x30; // 00110000

    public static final int TERMINATOR = 0xF0;
    public static final int DOUBLE_TERMINATOR = 0xFF;


    public static final int ENCODING_ALGORITHM_BUILTIN_END = 9;
    public static final int ENCODING_ALGORITHM_APPLICATION_START = 32;
    public static final int ENCODING_ALGORITHM_APPLICATION_MAX = 255;

    public static final int RESTRICTED_ALPHABET_BUILTIN_END = 1;
    public static final int RESTRICTED_ALPHABET_APPLICATION_START = 32;
    public static final int RESTRICTED_ALPHABET_APPLICATION_MAX = 255;

    // Octet string length contants

    public static final int OCTET_STRING_LENGTH_SMALL_LIMIT = 0;
    public static final int OCTET_STRING_LENGTH_MEDIUM_LIMIT = 1;
    public static final int OCTET_STRING_LENGTH_MEDIUM_FLAG = 2;
    public static final int OCTET_STRING_LENGTH_LARGE_FLAG = 3;

    public static final long OCTET_STRING_MAXIMUM_LENGTH = 4294967296L;

    /*
     * C.22
     */
    public static final int OCTET_STRING_LENGTH_2ND_BIT_SMALL_LIMIT = 65;
    public static final int OCTET_STRING_LENGTH_2ND_BIT_MEDIUM_LIMIT = 321;
    public static final int OCTET_STRING_LENGTH_2ND_BIT_MEDIUM_FLAG = 0x40;
    public static final int OCTET_STRING_LENGTH_2ND_BIT_LARGE_FLAG = 0x60;
    public static final int OCTET_STRING_LENGTH_2ND_BIT_SMALL_MASK = 0x1F;

    /* package */ static final int[] OCTET_STRING_LENGTH_2ND_BIT_VALUES = {
        OCTET_STRING_LENGTH_2ND_BIT_SMALL_LIMIT,
        OCTET_STRING_LENGTH_2ND_BIT_MEDIUM_LIMIT,
        OCTET_STRING_LENGTH_2ND_BIT_MEDIUM_FLAG,
        OCTET_STRING_LENGTH_2ND_BIT_LARGE_FLAG
    };

    /*
     * C.23
     */
    public static final int OCTET_STRING_LENGTH_5TH_BIT_SMALL_LIMIT = 9;
    public static final int OCTET_STRING_LENGTH_5TH_BIT_MEDIUM_LIMIT = 265;
    public static final int OCTET_STRING_LENGTH_5TH_BIT_MEDIUM_FLAG = 0x08;
    public static final int OCTET_STRING_LENGTH_5TH_BIT_LARGE_FLAG = 0x0C;
    public static final int OCTET_STRING_LENGTH_5TH_BIT_SMALL_MASK = 0x07;

    /* package */ static final int[] OCTET_STRING_LENGTH_5TH_BIT_VALUES = {
        OCTET_STRING_LENGTH_5TH_BIT_SMALL_LIMIT,
        OCTET_STRING_LENGTH_5TH_BIT_MEDIUM_LIMIT,
        OCTET_STRING_LENGTH_5TH_BIT_MEDIUM_FLAG,
        OCTET_STRING_LENGTH_5TH_BIT_LARGE_FLAG
    };

    /*
     * C.24
     */
    public static final int OCTET_STRING_LENGTH_7TH_BIT_SMALL_LIMIT = 3;
    public static final int OCTET_STRING_LENGTH_7TH_BIT_MEDIUM_LIMIT = 259;
    public static final int OCTET_STRING_LENGTH_7TH_BIT_MEDIUM_FLAG = 0x02;
    public static final int OCTET_STRING_LENGTH_7TH_BIT_LARGE_FLAG = 0x03;
    public static final int OCTET_STRING_LENGTH_7TH_BIT_SMALL_MASK = 0x01;

    /* package */ static final int[] OCTET_STRING_LENGTH_7TH_BIT_VALUES = {
        OCTET_STRING_LENGTH_7TH_BIT_SMALL_LIMIT,
        OCTET_STRING_LENGTH_7TH_BIT_MEDIUM_LIMIT,
        OCTET_STRING_LENGTH_7TH_BIT_MEDIUM_FLAG,
        OCTET_STRING_LENGTH_7TH_BIT_LARGE_FLAG
    };


    // Integer

    public static final int INTEGER_SMALL_LIMIT = 0;
    public static final int INTEGER_MEDIUM_LIMIT = 1;
    public static final int INTEGER_LARGE_LIMIT = 2;
    public static final int INTEGER_MEDIUM_FLAG = 3;
    public static final int INTEGER_LARGE_FLAG = 4;
    public static final int INTEGER_LARGE_LARGE_FLAG = 5;

    public static final int INTEGER_MAXIMUM_SIZE = 1048576;

    /*
     * C.25
     */
    public static final int INTEGER_2ND_BIT_SMALL_LIMIT = 64;
    public static final int INTEGER_2ND_BIT_MEDIUM_LIMIT = 8256;
    public static final int INTEGER_2ND_BIT_LARGE_LIMIT = INTEGER_MAXIMUM_SIZE;
    public static final int INTEGER_2ND_BIT_MEDIUM_FLAG = 0x40;
    public static final int INTEGER_2ND_BIT_LARGE_FLAG = 0x60;
    public static final int INTEGER_2ND_BIT_SMALL_MASK = 0x3F;
    public static final int INTEGER_2ND_BIT_MEDIUM_MASK = 0x1F;
    public static final int INTEGER_2ND_BIT_LARGE_MASK = 0x0F;

    /* package */ static final int[] INTEGER_2ND_BIT_VALUES = {
        INTEGER_2ND_BIT_SMALL_LIMIT,
        INTEGER_2ND_BIT_MEDIUM_LIMIT,
        INTEGER_2ND_BIT_LARGE_LIMIT,
        INTEGER_2ND_BIT_MEDIUM_FLAG,
        INTEGER_2ND_BIT_LARGE_FLAG,
        -1
    };

    /*
     * C.27
     */
    public static final int INTEGER_3RD_BIT_SMALL_LIMIT = 32;
    public static final int INTEGER_3RD_BIT_MEDIUM_LIMIT = 2080;
    public static final int INTEGER_3RD_BIT_LARGE_LIMIT = 526368;
    public static final int INTEGER_3RD_BIT_MEDIUM_FLAG = 0x20;
    public static final int INTEGER_3RD_BIT_LARGE_FLAG = 0x28;
    public static final int INTEGER_3RD_BIT_LARGE_LARGE_FLAG = 0x30;
    public static final int INTEGER_3RD_BIT_SMALL_MASK = 0x1F;
    public static final int INTEGER_3RD_BIT_MEDIUM_MASK = 0x07;
    public static final int INTEGER_3RD_BIT_LARGE_MASK = 0x07;
    public static final int INTEGER_3RD_BIT_LARGE_LARGE_MASK = 0x0F;

    /* package */ static final int[] INTEGER_3RD_BIT_VALUES = {
        INTEGER_3RD_BIT_SMALL_LIMIT,
        INTEGER_3RD_BIT_MEDIUM_LIMIT,
        INTEGER_3RD_BIT_LARGE_LIMIT,
        INTEGER_3RD_BIT_MEDIUM_FLAG,
        INTEGER_3RD_BIT_LARGE_FLAG,
        INTEGER_3RD_BIT_LARGE_LARGE_FLAG
    };

    /*
     * C.28
     */
    public static final int INTEGER_4TH_BIT_SMALL_LIMIT = 16;
    public static final int INTEGER_4TH_BIT_MEDIUM_LIMIT = 1040;
    public static final int INTEGER_4TH_BIT_LARGE_LIMIT = 263184;
    public static final int INTEGER_4TH_BIT_MEDIUM_FLAG = 0x10;
    public static final int INTEGER_4TH_BIT_LARGE_FLAG = 0x14;
    public static final int INTEGER_4TH_BIT_LARGE_LARGE_FLAG = 0x18;
    public static final int INTEGER_4TH_BIT_SMALL_MASK = 0x0F;
    public static final int INTEGER_4TH_BIT_MEDIUM_MASK = 0x03;
    public static final int INTEGER_4TH_BIT_LARGE_MASK = 0x03;

    /* package */ static final int[] INTEGER_4TH_BIT_VALUES = {
        INTEGER_4TH_BIT_SMALL_LIMIT,
        INTEGER_4TH_BIT_MEDIUM_LIMIT,
        INTEGER_4TH_BIT_LARGE_LIMIT,
        INTEGER_4TH_BIT_MEDIUM_FLAG,
        INTEGER_4TH_BIT_LARGE_FLAG,
        INTEGER_4TH_BIT_LARGE_LARGE_FLAG
    };

    /* package */ static final byte[] BINARY_HEADER = {(byte)0xE0, 0, 0, 1};

    /* package */ static byte[][] XML_DECLARATION_VALUES;

    private static void initiateXMLDeclarationValues() {

        XML_DECLARATION_VALUES = new byte[9][];

        try {
            XML_DECLARATION_VALUES[0] = "<?xml encoding='finf'?>".getBytes("UTF-8");
            XML_DECLARATION_VALUES[1] = "<?xml version='1.0' encoding='finf'?>".getBytes("UTF-8");
            XML_DECLARATION_VALUES[2] = "<?xml version='1.1' encoding='finf'?>".getBytes("UTF-8");
            XML_DECLARATION_VALUES[3] = "<?xml encoding='finf' standalone='no'?>".getBytes("UTF-8");
            XML_DECLARATION_VALUES[4] = "<?xml encoding='finf' standalone='yes'?>".getBytes("UTF-8");
            XML_DECLARATION_VALUES[5] = "<?xml version='1.0' encoding='finf' standalone='no'?>".getBytes("UTF-8");
            XML_DECLARATION_VALUES[6] = "<?xml version='1.1' encoding='finf' standalone='no'?>".getBytes("UTF-8");
            XML_DECLARATION_VALUES[7] = "<?xml version='1.0' encoding='finf' standalone='yes'?>".getBytes("UTF-8");
            XML_DECLARATION_VALUES[8] = "<?xml version='1.1' encoding='finf' standalone='yes'?>".getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
    }
}
