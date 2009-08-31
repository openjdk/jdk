/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */
package com.sun.xml.internal.fastinfoset;

import com.sun.xml.internal.fastinfoset.algorithm.BuiltInEncodingAlgorithmFactory;
import com.sun.xml.internal.fastinfoset.org.apache.xerces.util.XMLChar;
import com.sun.xml.internal.fastinfoset.util.CharArrayIntMap;
import com.sun.xml.internal.fastinfoset.util.KeyIntMap;
import com.sun.xml.internal.fastinfoset.util.LocalNameQualifiedNamesMap;
import com.sun.xml.internal.fastinfoset.util.StringIntMap;
import com.sun.xml.internal.fastinfoset.vocab.SerializerVocabulary;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithm;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmException;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmIndexes;
import com.sun.xml.internal.org.jvnet.fastinfoset.ExternalVocabulary;
import com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetException;
import com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetSerializer;
import com.sun.xml.internal.org.jvnet.fastinfoset.RestrictedAlphabet;
import com.sun.xml.internal.org.jvnet.fastinfoset.VocabularyApplicationData;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Abstract encoder for developing concrete encoders.
 *
 * Concrete implementations extending Encoder will utilize methods on Encoder
 * to encode XML infoset according to the Fast Infoset standard. It is the
 * responsibility of the concrete implementation to ensure that methods are
 * invoked in the correct order to produce a valid fast infoset document.
 *
 * <p>
 * This class extends org.sax.xml.DefaultHandler so that concrete SAX
 * implementations can be used with javax.xml.parsers.SAXParser and the parse
 * methods that take org.sax.xml.DefaultHandler as a parameter.
 *
 * <p>
 * Buffering of octets that are written to an {@link java.io.OutputStream} is
 * supported in a similar manner to a {@link java.io.BufferedOutputStream}.
 * Combining buffering with encoding enables better performance.
 *
 * <p>
 * More than one fast infoset document may be encoded to the
 * {@link java.io.OutputStream}.
 *
 */
public abstract class Encoder extends DefaultHandler implements FastInfosetSerializer {

    /**
     * Character encoding scheme system property for the encoding
     * of content and attribute values.
     */
    public static final String CHARACTER_ENCODING_SCHEME_SYSTEM_PROPERTY =
        "com.sun.xml.internal.fastinfoset.serializer.character-encoding-scheme";

    /**
     * Default character encoding scheme system property for the encoding
     * of content and attribute values.
     */
    protected static final String _characterEncodingSchemeSystemDefault = getDefaultEncodingScheme();

    private static String getDefaultEncodingScheme() {
        String p = System.getProperty(CHARACTER_ENCODING_SCHEME_SYSTEM_PROPERTY,
            UTF_8);
        if (p.equals(UTF_16BE)) {
            return UTF_16BE;
        } else {
            return UTF_8;
        }
    }

    private static int[] NUMERIC_CHARACTERS_TABLE;

    private static int[] DATE_TIME_CHARACTERS_TABLE;

    static {
        NUMERIC_CHARACTERS_TABLE = new int[maxCharacter(RestrictedAlphabet.NUMERIC_CHARACTERS) + 1];
        DATE_TIME_CHARACTERS_TABLE = new int[maxCharacter(RestrictedAlphabet.DATE_TIME_CHARACTERS) + 1];

        for (int i = 0; i < NUMERIC_CHARACTERS_TABLE.length ; i++) {
            NUMERIC_CHARACTERS_TABLE[i] = -1;
        }
        for (int i = 0; i < DATE_TIME_CHARACTERS_TABLE.length ; i++) {
            DATE_TIME_CHARACTERS_TABLE[i] = -1;
        }

        for (int i = 0; i < RestrictedAlphabet.NUMERIC_CHARACTERS.length() ; i++) {
            NUMERIC_CHARACTERS_TABLE[RestrictedAlphabet.NUMERIC_CHARACTERS.charAt(i)] = i;
        }
        for (int i = 0; i < RestrictedAlphabet.DATE_TIME_CHARACTERS.length() ; i++) {
            DATE_TIME_CHARACTERS_TABLE[RestrictedAlphabet.DATE_TIME_CHARACTERS.charAt(i)] = i;
        }
    }

    private static int maxCharacter(String alphabet) {
        int c = 0;
        for (int i = 0; i < alphabet.length() ; i++) {
            if (c < alphabet.charAt(i)) {
                c = alphabet.charAt(i);
            }
        }

        return c;
    }

    /**
     * True if DTD and internal subset shall be ignored.
     */
    private boolean _ignoreDTD;

    /**
     * True if comments shall be ignored.
     */
    private boolean _ignoreComments;

    /**
     * True if procesing instructions shall be ignored.
     */
    private boolean _ignoreProcessingInstructions;

    /**
     * True if white space characters for text content shall be ignored.
     */
    private boolean _ignoreWhiteSpaceTextContent;

    /**
     * True, if the local name string is used as the key to find the
     * associated set of qualified names.
     * <p>
     * False,  if the <prefix>:<local name> string is used as the key
     * to find the associated set of qualified names.
     */
    private boolean _useLocalNameAsKeyForQualifiedNameLookup;

    /**
     * True if strings for text content and attribute values will be
     * UTF-8 encoded otherwise they will be UTF-16 encoded.
     */
    private boolean _encodingStringsAsUtf8 = true;

    /**
     * Encoding constant generated from the string encoding.
     */
    private int _nonIdentifyingStringOnThirdBitCES;

    /**
     * Encoding constant generated from the string encoding.
     */
    private int _nonIdentifyingStringOnFirstBitCES;

    /**
     * The map of URIs to algorithms.
     */
    private Map _registeredEncodingAlgorithms = new HashMap();

    /**
     * The vocabulary that is used by the encoder
     */
    protected SerializerVocabulary _v;

    /**
     * The vocabulary application data that is used by the encoder
     */
    protected VocabularyApplicationData _vData;

    /**
     * True if the vocubulary is internal to the encoder
     */
    private boolean _vIsInternal;

    /**
     * True if terminatation of an information item is required
     */
    protected boolean _terminate = false;

    /**
     * The current octet that is to be written.
     */
    protected int _b;

    /**
     * The {@link java.io.OutputStream} that the encoded XML infoset (the
     * fast infoset document) is written to.
     */
    protected OutputStream _s;

    /**
     * The internal buffer of characters used for the UTF-8 or UTF-16 encoding
     * of characters.
     */
    protected char[] _charBuffer = new char[512];

    /**
     * The internal buffer of bytes.
     */
    protected byte[] _octetBuffer = new byte[1024];

    /**
     * The current position in the internal buffer.
     */
    protected int _octetBufferIndex;

    /**
     * The current mark in the internal buffer.
     *
     * <p>
     * If the value of the mark is < 0 then the mark is not set.
     */
    protected int _markIndex = -1;

    /**
     * The minimum size of [normalized value] of Attribute Information
     * Items that will be indexed.
     */
    protected int minAttributeValueSize = FastInfosetSerializer.MIN_ATTRIBUTE_VALUE_SIZE;

    /**
     * The maximum size of [normalized value] of Attribute Information
     * Items that will be indexed.
     */
    protected int maxAttributeValueSize = FastInfosetSerializer.MAX_ATTRIBUTE_VALUE_SIZE;

    /**
     * The limit on the size of indexed Map for attribute values
     * Limit is measured in characters number
     */
    protected int attributeValueMapTotalCharactersConstraint = FastInfosetSerializer.ATTRIBUTE_VALUE_MAP_MEMORY_CONSTRAINT / 2;

    /**
     * The minimum size of character content chunks
     * of Character Information Items or Comment Information Items that
     * will be indexed.
     */
    protected int minCharacterContentChunkSize = FastInfosetSerializer.MIN_CHARACTER_CONTENT_CHUNK_SIZE;

    /**
     * The maximum size of character content chunks
     * of Character Information Items or Comment Information Items that
     * will be indexed.
     */
    protected int maxCharacterContentChunkSize = FastInfosetSerializer.MAX_CHARACTER_CONTENT_CHUNK_SIZE;

    /**
     * The limit on the size of indexed Map for character content chunks
     * Limit is measured in characters number
     */
    protected int characterContentChunkMapTotalCharactersConstraint = FastInfosetSerializer.CHARACTER_CONTENT_CHUNK_MAP_MEMORY_CONSTRAINT / 2;

    /**
     * Default constructor for the Encoder.
     */
    protected Encoder() {
        setCharacterEncodingScheme(_characterEncodingSchemeSystemDefault);
    }

    protected Encoder(boolean useLocalNameAsKeyForQualifiedNameLookup) {
        setCharacterEncodingScheme(_characterEncodingSchemeSystemDefault);
        _useLocalNameAsKeyForQualifiedNameLookup = useLocalNameAsKeyForQualifiedNameLookup;
    }


    // FastInfosetSerializer interface

    /**
     * {@inheritDoc}
     */
    public final void setIgnoreDTD(boolean ignoreDTD) {
        _ignoreDTD = ignoreDTD;
    }

    /**
     * {@inheritDoc}
     */
    public final boolean getIgnoreDTD() {
        return _ignoreDTD;
    }

    /**
     * {@inheritDoc}
     */
    public final void setIgnoreComments(boolean ignoreComments) {
        _ignoreComments = ignoreComments;
    }

    /**
     * {@inheritDoc}
     */
    public final boolean getIgnoreComments() {
        return _ignoreComments;
    }

    /**
     * {@inheritDoc}
     */
    public final void setIgnoreProcesingInstructions(boolean
            ignoreProcesingInstructions) {
        _ignoreProcessingInstructions = ignoreProcesingInstructions;
    }

    /**
     * {@inheritDoc}
     */
    public final boolean getIgnoreProcesingInstructions() {
        return _ignoreProcessingInstructions;
    }

    /**
     * {@inheritDoc}
     */
    public final void setIgnoreWhiteSpaceTextContent(boolean ignoreWhiteSpaceTextContent) {
        _ignoreWhiteSpaceTextContent = ignoreWhiteSpaceTextContent;
    }

    /**
     * {@inheritDoc}
     */
    public final boolean getIgnoreWhiteSpaceTextContent() {
        return _ignoreWhiteSpaceTextContent;
    }

    /**
     * {@inheritDoc}
     */
    public void setCharacterEncodingScheme(String characterEncodingScheme) {
        if (characterEncodingScheme.equals(UTF_16BE)) {
            _encodingStringsAsUtf8 = false;
            _nonIdentifyingStringOnThirdBitCES = EncodingConstants.CHARACTER_CHUNK | EncodingConstants.CHARACTER_CHUNK_UTF_16_FLAG;
            _nonIdentifyingStringOnFirstBitCES = EncodingConstants.NISTRING_UTF_16_FLAG;
        } else {
            _encodingStringsAsUtf8 = true;
            _nonIdentifyingStringOnThirdBitCES = EncodingConstants.CHARACTER_CHUNK;
            _nonIdentifyingStringOnFirstBitCES = 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getCharacterEncodingScheme() {
        return (_encodingStringsAsUtf8) ? UTF_8 : UTF_16BE;
    }

    /**
     * {@inheritDoc}
     */
    public void setRegisteredEncodingAlgorithms(Map algorithms) {
        _registeredEncodingAlgorithms = algorithms;
        if (_registeredEncodingAlgorithms == null) {
            _registeredEncodingAlgorithms = new HashMap();
        }
    }

    /**
     * {@inheritDoc}
     */
    public Map getRegisteredEncodingAlgorithms() {
        return _registeredEncodingAlgorithms;
    }

    /**
     * {@inheritDoc}
     */
    public int getMinCharacterContentChunkSize() {
        return minCharacterContentChunkSize;
    }

    /**
     * {@inheritDoc}
     */
    public void setMinCharacterContentChunkSize(int size) {
        if (size < 0 ) {
            size = 0;
        }

        minCharacterContentChunkSize = size;
    }

    /**
     * {@inheritDoc}
     */
    public int getMaxCharacterContentChunkSize() {
        return maxCharacterContentChunkSize;
    }

    /**
     * {@inheritDoc}
     */
    public void setMaxCharacterContentChunkSize(int size) {
        if (size < 0 ) {
            size = 0;
        }

        maxCharacterContentChunkSize = size;
    }

    /**
     * {@inheritDoc}
     */
    public int getCharacterContentChunkMapMemoryLimit() {
        return characterContentChunkMapTotalCharactersConstraint * 2;
    }

    /**
     * {@inheritDoc}
     */
    public void setCharacterContentChunkMapMemoryLimit(int size) {
        if (size < 0 ) {
            size = 0;
        }

        characterContentChunkMapTotalCharactersConstraint = size / 2;
    }

    /**
     * Checks whether character content chunk (its length) matches length limit
     *
     * @param length the length of character content chunk is checking to be added to Map.
     * @return whether character content chunk length matches limit
     */
    public boolean isCharacterContentChunkLengthMatchesLimit(int length) {
        return length >= minCharacterContentChunkSize &&
                length <= maxCharacterContentChunkSize;
    }

    /**
     * Checks whether character content table has enough memory to
     * store character content chunk with the given length
     *
     * @param length the length of character content chunk is checking to be added to Map.
     * @param map the custom CharArrayIntMap, which memory limits will be checked.
     * @return whether character content map has enough memory
     */
    public boolean canAddCharacterContentToTable(int length, CharArrayIntMap map) {
        return map.getTotalCharacterCount() + length <
                        characterContentChunkMapTotalCharactersConstraint;
    }

    /**
     * {@inheritDoc}
     */
    public int getMinAttributeValueSize() {
        return minAttributeValueSize;
    }

    /**
     * {@inheritDoc}
     */
    public void setMinAttributeValueSize(int size) {
        if (size < 0 ) {
            size = 0;
        }

        minAttributeValueSize = size;
    }

    /**
     * {@inheritDoc}
     */
    public int getMaxAttributeValueSize() {
        return maxAttributeValueSize;
    }

    /**
     * {@inheritDoc}
     */
    public void setMaxAttributeValueSize(int size) {
        if (size < 0 ) {
            size = 0;
        }

        maxAttributeValueSize = size;
    }

    /**
     * {@inheritDoc}
     */
    public void setAttributeValueMapMemoryLimit(int size) {
        if (size < 0 ) {
            size = 0;
        }

        attributeValueMapTotalCharactersConstraint = size / 2;

    }

    /**
     * {@inheritDoc}
     */
    public int getAttributeValueMapMemoryLimit() {
        return attributeValueMapTotalCharactersConstraint * 2;
    }

    /**
     * Checks whether attribute value (its length) matches length limit
     *
     * @param length the length of attribute
     * @return whether attribute value matches limit
     */
    public boolean isAttributeValueLengthMatchesLimit(int length) {
        return length >= minAttributeValueSize &&
                length <= maxAttributeValueSize;
    }

    /**
     * Checks whether attribute table has enough memory to
     * store attribute value with the given length
     *
     * @param length the length of attribute value is checking to be added to Map.
     * @return whether attribute map has enough memory
     */
    public boolean canAddAttributeToTable(int length) {
        return _v.attributeValue.getTotalCharacterCount() + length <
                        attributeValueMapTotalCharactersConstraint;
    }

    /**
     * {@inheritDoc}
     */
    public void setExternalVocabulary(ExternalVocabulary v) {
        // Create internal serializer vocabulary
        _v = new SerializerVocabulary();
        // Set the external vocabulary
        SerializerVocabulary ev = new SerializerVocabulary(v.vocabulary,
                _useLocalNameAsKeyForQualifiedNameLookup);
        _v.setExternalVocabulary(v.URI,
                ev, false);

        _vIsInternal = true;
    }

    /**
     * {@inheritDoc}
     */
    public void setVocabularyApplicationData(VocabularyApplicationData data) {
        _vData = data;
    }

    /**
     * {@inheritDoc}
     */
    public VocabularyApplicationData getVocabularyApplicationData() {
        return _vData;
    }

    // End of FastInfosetSerializer interface

    /**
     * Reset the encoder for reuse encoding another XML infoset.
     */
    public void reset() {
        _terminate = false;
    }

    /**
     * Set the OutputStream to encode the XML infoset to a
     * fast infoset document.
     *
     * @param s the OutputStream where the fast infoset document is written to.
     */
    public void setOutputStream(OutputStream s) {
        _octetBufferIndex = 0;
        _markIndex = -1;
        _s = s;
    }

    /**
     * Set the SerializerVocabulary to be used for encoding.
     *
     * @param vocabulary the vocabulary to be used for encoding.
     */
    public void setVocabulary(SerializerVocabulary vocabulary) {
        _v = vocabulary;
        _vIsInternal = false;
    }

    /**
     * Encode the header of a fast infoset document.
     *
     * @param encodeXmlDecl true if the XML declaration should be encoded.
     */
    protected final void encodeHeader(boolean encodeXmlDecl) throws IOException {
        if (encodeXmlDecl) {
            _s.write(EncodingConstants.XML_DECLARATION_VALUES[0]);
        }
        _s.write(EncodingConstants.BINARY_HEADER);
    }

    /**
     * Encode the initial vocabulary of a fast infoset document.
     *
     */
    protected final void encodeInitialVocabulary() throws IOException {
        if (_v == null) {
            _v = new SerializerVocabulary();
            _vIsInternal = true;
        } else if (_vIsInternal) {
            _v.clear();
            if (_vData != null)
                _vData.clear();
        }

        if (!_v.hasInitialVocabulary() && !_v.hasExternalVocabulary()) {
            write(0);
        } else if (_v.hasInitialVocabulary()) {
            _b = EncodingConstants.DOCUMENT_INITIAL_VOCABULARY_FLAG;
            write(_b);

            SerializerVocabulary initialVocabulary = _v.getReadOnlyVocabulary();

            // TODO check for contents of vocabulary to assign bits
            if (initialVocabulary.hasExternalVocabulary()) {
                _b = EncodingConstants.INITIAL_VOCABULARY_EXTERNAL_VOCABULARY_FLAG;
                write(_b);
                write(0);
            }

            if (initialVocabulary.hasExternalVocabulary()) {
                encodeNonEmptyOctetStringOnSecondBit(_v.getExternalVocabularyURI());
            }

            // TODO check for contents of vocabulary to encode values
        } else if (_v.hasExternalVocabulary()) {
            _b = EncodingConstants.DOCUMENT_INITIAL_VOCABULARY_FLAG;
            write(_b);

            _b = EncodingConstants.INITIAL_VOCABULARY_EXTERNAL_VOCABULARY_FLAG;
            write(_b);
            write(0);

            encodeNonEmptyOctetStringOnSecondBit(_v.getExternalVocabularyURI());
        }
    }

    /**
     * Encode the termination of the Document Information Item.
     *
     */
    protected final void encodeDocumentTermination() throws IOException {
        encodeElementTermination();
        encodeTermination();
        _flush();
        _s.flush();
    }

    /**
     * Encode the termination of an Element Information Item.
     *
     */
    protected final void encodeElementTermination() throws IOException {
        _terminate = true;
        switch (_b) {
            case EncodingConstants.TERMINATOR:
                _b = EncodingConstants.DOUBLE_TERMINATOR;
                break;
            case EncodingConstants.DOUBLE_TERMINATOR:
                write(EncodingConstants.DOUBLE_TERMINATOR);
            default:
                _b = EncodingConstants.TERMINATOR;
        }
    }

    /**
     * Encode a termination if required.
     *
     */
    protected final void encodeTermination() throws IOException {
        if (_terminate) {
            write(_b);
            _b = 0;
            _terminate = false;
        }
    }

    /**
     * Encode a Attribute Information Item that is a namespace declaration.
     *
     * @param prefix the prefix of the namespace declaration,
     * if "" then there is no prefix for the namespace declaration.
     * @param uri the URI of the namespace declaration,
     * if "" then there is no URI for the namespace declaration.
     */
    protected final void encodeNamespaceAttribute(String prefix, String uri) throws IOException {
        _b = EncodingConstants.NAMESPACE_ATTRIBUTE;
        if (prefix.length() > 0) {
            _b |= EncodingConstants.NAMESPACE_ATTRIBUTE_PREFIX_FLAG;
        }
        if (uri.length() > 0) {
            _b |= EncodingConstants.NAMESPACE_ATTRIBUTE_NAME_FLAG;
        }

        // NOTE a prefix with out a namespace name is an undeclaration
        // of the namespace bound to the prefix
        // TODO needs to investigate how the startPrefixMapping works in
        // relation to undeclaration

        write(_b);

        if (prefix.length() > 0) {
            encodeIdentifyingNonEmptyStringOnFirstBit(prefix, _v.prefix);
        }
        if (uri.length() > 0) {
            encodeIdentifyingNonEmptyStringOnFirstBit(uri, _v.namespaceName);
        }
    }

    /**
     * Encode a chunk of Character Information Items.
     *
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     * @throws ArrayIndexOutOfBoundsException.
     */
    protected final void encodeCharacters(char[] ch, int offset, int length) throws IOException {
        final boolean addToTable = isCharacterContentChunkLengthMatchesLimit(length);
        encodeNonIdentifyingStringOnThirdBit(ch, offset, length, _v.characterContentChunk, addToTable, true);
    }

    /**
     * Encode a chunk of Character Information Items.
     *
     * If the array of characters is to be indexed (as determined by
     * {@link Encoder#characterContentChunkSizeContraint}) then the array is not cloned
     * when adding the array to the vocabulary.
     *
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     * @throws ArrayIndexOutOfBoundsException.
     */
    protected final void encodeCharactersNoClone(char[] ch, int offset, int length) throws IOException {
        final boolean addToTable = isCharacterContentChunkLengthMatchesLimit(length);
        encodeNonIdentifyingStringOnThirdBit(ch, offset, length, _v.characterContentChunk, addToTable, false);
    }

    /**
     * Encode a chunk of Character Information Items using a numeric
     * alphabet that results in the encoding of a character in 4 bits
     * (or two characters per octet).
     *
     * @param id the restricted alphabet identifier.
     * @param table the table mapping characters to 4 bit values.
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     * @param addToTable if characters should be added to table.
     * @throws ArrayIndexOutOfBoundsException.
     */
    protected final void encodeNumericFourBitCharacters(char[] ch, int offset, int length,
            boolean addToTable) throws FastInfosetException, IOException {
        encodeFourBitCharacters(RestrictedAlphabet.NUMERIC_CHARACTERS_INDEX,
                NUMERIC_CHARACTERS_TABLE, ch, offset, length, addToTable);
    }

    /**
     * Encode a chunk of Character Information Items using a date-time
     * alphabet that results in the encoding of a character in 4 bits
     * (or two characters per octet).
     *
     * @param id the restricted alphabet identifier.
     * @param table the table mapping characters to 4 bit values.
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     * @param addToTable if characters should be added to table.
     * @throws ArrayIndexOutOfBoundsException.
     */
    protected final void encodeDateTimeFourBitCharacters(char[] ch, int offset, int length,
            boolean addToTable) throws FastInfosetException, IOException {
        encodeFourBitCharacters(RestrictedAlphabet.DATE_TIME_CHARACTERS_INDEX,
                DATE_TIME_CHARACTERS_TABLE, ch, offset, length, addToTable);
    }

    /**
     * Encode a chunk of Character Information Items using a restricted
     * alphabet that results in the encoding of a character in 4 bits
     * (or two characters per octet).
     *
     * @param id the restricted alphabet identifier.
     * @param table the table mapping characters to 4 bit values.
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     * @param addToTable if characters should be added to table.
     * @throws ArrayIndexOutOfBoundsException.
     */
    protected final void encodeFourBitCharacters(int id, int[] table, char[] ch, int offset, int length,
            boolean addToTable) throws FastInfosetException, IOException {
        if (addToTable) {
            // if char array could be added to table
            boolean canAddCharacterContentToTable =
                    canAddCharacterContentToTable(length, _v.characterContentChunk);

            // obtain/get index
            int index = canAddCharacterContentToTable ?
                _v.characterContentChunk.obtainIndex(ch, offset, length, true) :
                _v.characterContentChunk.get(ch, offset, length);

            if (index != KeyIntMap.NOT_PRESENT) {
                // if char array is in table
                _b = EncodingConstants.CHARACTER_CHUNK | 0x20;
                encodeNonZeroIntegerOnFourthBit(index);
                return;
            } else if (canAddCharacterContentToTable) {
                // if char array is not in table, but could be added
                _b = EncodingConstants.CHARACTER_CHUNK | EncodingConstants.CHARACTER_CHUNK_RESTRICTED_ALPHABET_FLAG | EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG;
            } else {
                // if char array is not in table and could not be added
                _b = EncodingConstants.CHARACTER_CHUNK | EncodingConstants.CHARACTER_CHUNK_RESTRICTED_ALPHABET_FLAG;
            }
        } else {
            _b = EncodingConstants.CHARACTER_CHUNK | EncodingConstants.CHARACTER_CHUNK_RESTRICTED_ALPHABET_FLAG;
        }

        write (_b);

        // Encode bottom 6 bits of enoding algorithm id
        _b = id << 2;

        encodeNonEmptyFourBitCharacterStringOnSeventhBit(table, ch, offset, length);
    }

    /**
     * Encode a chunk of Character Information Items using a restricted
     * alphabet table.
     *
     * @param alphabet the alphabet defining the mapping between characters and
     *        integer values.
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     * @param addToTable if characters should be added to table
     * @throws ArrayIndexOutOfBoundsException.
     * @throws FastInfosetException if the alphabet is not present in the
     *         vocabulary.
     */
    protected final void encodeAlphabetCharacters(String alphabet, char[] ch, int offset, int length,
            boolean addToTable) throws FastInfosetException, IOException {
        if (addToTable) {
            // if char array could be added to table
            boolean canAddCharacterContentToTable =
                    canAddCharacterContentToTable(length, _v.characterContentChunk);

            // obtain/get index
            int index = canAddCharacterContentToTable ?
                _v.characterContentChunk.obtainIndex(ch, offset, length, true) :
                _v.characterContentChunk.get(ch, offset, length);

            if (index != KeyIntMap.NOT_PRESENT) {
                // if char array is in table
                _b = EncodingConstants.CHARACTER_CHUNK | 0x20;
                encodeNonZeroIntegerOnFourthBit(index);
                return;
            } else if (canAddCharacterContentToTable) {
                // if char array is not in table, but could be added
                _b = EncodingConstants.CHARACTER_CHUNK | EncodingConstants.CHARACTER_CHUNK_RESTRICTED_ALPHABET_FLAG | EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG;
            } else {
                // if char array is not in table and could not be added
                _b = EncodingConstants.CHARACTER_CHUNK | EncodingConstants.CHARACTER_CHUNK_RESTRICTED_ALPHABET_FLAG;
            }
        } else {
            _b = EncodingConstants.CHARACTER_CHUNK | EncodingConstants.CHARACTER_CHUNK_RESTRICTED_ALPHABET_FLAG;
        }

        int id = _v.restrictedAlphabet.get(alphabet);
        if (id == KeyIntMap.NOT_PRESENT) {
            throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.restrictedAlphabetNotPresent"));
        }
        id += EncodingConstants.RESTRICTED_ALPHABET_APPLICATION_START;

        _b |= (id & 0xC0) >> 6;
        write(_b);

        // Encode bottom 6 bits of enoding algorithm id
        _b = (id & 0x3F) << 2;

        encodeNonEmptyNBitCharacterStringOnSeventhBit(alphabet, ch, offset, length);
    }

    /**
     * Encode a Processing Instruction Information Item.
     *
     * @param target the target of the processing instruction.
     * @param data the data of the processing instruction.
     */
    protected final void encodeProcessingInstruction(String target, String data) throws IOException {
        write(EncodingConstants.PROCESSING_INSTRUCTION);

        // Target
        encodeIdentifyingNonEmptyStringOnFirstBit(target, _v.otherNCName);

        // Data
        boolean addToTable = isCharacterContentChunkLengthMatchesLimit(data.length());
        encodeNonIdentifyingStringOnFirstBit(data, _v.otherString, addToTable);
    }

    /**
     * Encode a Document Type Declaration.
     *
     * @param systemId the system identifier of the external subset.
     * @param publicId the public identifier of the external subset.
     */
    protected final void encodeDocumentTypeDeclaration(String systemId, String publicId) throws IOException {
        _b = EncodingConstants.DOCUMENT_TYPE_DECLARATION;
        if (systemId != null && systemId.length() > 0) {
            _b |= EncodingConstants.DOCUMENT_TYPE_SYSTEM_IDENTIFIER_FLAG;
        }
        if (publicId != null && publicId.length() > 0) {
            _b |= EncodingConstants.DOCUMENT_TYPE_PUBLIC_IDENTIFIER_FLAG;
        }
        write(_b);

        if (systemId != null && systemId.length() > 0) {
            encodeIdentifyingNonEmptyStringOnFirstBit(systemId, _v.otherURI);
        }
        if (publicId != null && publicId.length() > 0) {
            encodeIdentifyingNonEmptyStringOnFirstBit(publicId, _v.otherURI);
        }
    }

    /**
     * Encode a Comment Information Item.
     *
     * @param ch the array of characters that is as comment.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     * @throws ArrayIndexOutOfBoundsException.
     */
    protected final void encodeComment(char[] ch, int offset, int length) throws IOException {
        write(EncodingConstants.COMMENT);

        boolean addToTable = isCharacterContentChunkLengthMatchesLimit(length);
        encodeNonIdentifyingStringOnFirstBit(ch, offset, length, _v.otherString, addToTable, true);
    }

    /**
     * Encode a Comment Information Item.
     *
     * If the array of characters that is a comment is to be indexed (as
     * determined by {@link Encoder#characterContentChunkSizeContraint}) then
     * the array is not cloned when adding the array to the vocabulary.
     *
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     * @throws ArrayIndexOutOfBoundsException.
     */
    protected final void encodeCommentNoClone(char[] ch, int offset, int length) throws IOException {
        write(EncodingConstants.COMMENT);

        boolean addToTable = isCharacterContentChunkLengthMatchesLimit(length);
        encodeNonIdentifyingStringOnFirstBit(ch, offset, length, _v.otherString, addToTable, false);
    }

    /**
     * Encode a qualified name of an Element Informaiton Item on the third bit
     * of an octet.
     * Implementation of clause C.18 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * <p>
     * The index of the qualified name will be encoded if the name is present
     * in the vocabulary otherwise the qualified name will be encoded literally
     * (see {@link #encodeLiteralElementQualifiedNameOnThirdBit}).
     *
     * @param namespaceURI the namespace URI of the qualified name.
     * @param prefix the prefix of the qualified name.
     * @param localName the local name of the qualified name.
     */
    protected final void encodeElementQualifiedNameOnThirdBit(String namespaceURI, String prefix, String localName) throws IOException {
        LocalNameQualifiedNamesMap.Entry entry = _v.elementName.obtainEntry(localName);
        if (entry._valueIndex > 0) {
            QualifiedName[] names = entry._value;
            for (int i = 0; i < entry._valueIndex; i++) {
                if ((prefix == names[i].prefix || prefix.equals(names[i].prefix))
                        && (namespaceURI == names[i].namespaceName || namespaceURI.equals(names[i].namespaceName))) {
                    encodeNonZeroIntegerOnThirdBit(names[i].index);
                    return;
                }
            }
        }

        encodeLiteralElementQualifiedNameOnThirdBit(namespaceURI, prefix,
                localName, entry);
    }

    /**
     * Encode a literal qualified name of an Element Informaiton Item on the
     * third bit of an octet.
     * Implementation of clause C.18 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param namespaceURI the namespace URI of the qualified name.
     * @param prefix the prefix of the qualified name.
     * @param localName the local name of the qualified name.
     */
    protected final void encodeLiteralElementQualifiedNameOnThirdBit(String namespaceURI, String prefix, String localName,
            LocalNameQualifiedNamesMap.Entry entry) throws IOException {
        QualifiedName name = new QualifiedName(prefix, namespaceURI, localName, "", _v.elementName.getNextIndex());
        entry.addQualifiedName(name);

        int namespaceURIIndex = KeyIntMap.NOT_PRESENT;
        int prefixIndex = KeyIntMap.NOT_PRESENT;
        if (namespaceURI.length() > 0) {
            namespaceURIIndex = _v.namespaceName.get(namespaceURI);
            if (namespaceURIIndex == KeyIntMap.NOT_PRESENT) {
                throw new IOException(CommonResourceBundle.getInstance().getString("message.namespaceURINotIndexed", new Object[]{namespaceURI}));
            }

            if (prefix.length() > 0) {
                prefixIndex = _v.prefix.get(prefix);
                if (prefixIndex == KeyIntMap.NOT_PRESENT) {
                    throw new IOException(CommonResourceBundle.getInstance().getString("message.prefixNotIndexed", new Object[]{prefix}));
                }
            }
        }

        int localNameIndex = _v.localName.obtainIndex(localName);

        _b |= EncodingConstants.ELEMENT_LITERAL_QNAME_FLAG;
        if (namespaceURIIndex >= 0) {
            _b |= EncodingConstants.LITERAL_QNAME_NAMESPACE_NAME_FLAG;
            if (prefixIndex >= 0) {
                _b |= EncodingConstants.LITERAL_QNAME_PREFIX_FLAG;
            }
        }
        write(_b);

        if (namespaceURIIndex >= 0) {
            if (prefixIndex >= 0) {
                encodeNonZeroIntegerOnSecondBitFirstBitOne(prefixIndex);
            }
            encodeNonZeroIntegerOnSecondBitFirstBitOne(namespaceURIIndex);
        }

        if (localNameIndex >= 0) {
            encodeNonZeroIntegerOnSecondBitFirstBitOne(localNameIndex);
        } else {
            encodeNonEmptyOctetStringOnSecondBit(localName);
        }
    }

    /**
     * Encode a qualified name of an Attribute Informaiton Item on the third bit
     * of an octet.
     * Implementation of clause C.17 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * <p>
     * The index of the qualified name will be encoded if the name is present
     * in the vocabulary otherwise the qualified name will be encoded literally
     * (see {@link #encodeLiteralAttributeQualifiedNameOnSecondBit}).
     *
     * @param namespaceURI the namespace URI of the qualified name.
     * @param prefix the prefix of the qualified name.
     * @param localName the local name of the qualified name.
     */
    protected final void encodeAttributeQualifiedNameOnSecondBit(String namespaceURI, String prefix, String localName) throws IOException {
        LocalNameQualifiedNamesMap.Entry entry = _v.attributeName.obtainEntry(localName);
        if (entry._valueIndex > 0) {
            QualifiedName[] names = entry._value;
            for (int i = 0; i < entry._valueIndex; i++) {
                if ((prefix == names[i].prefix || prefix.equals(names[i].prefix))
                        && (namespaceURI == names[i].namespaceName || namespaceURI.equals(names[i].namespaceName))) {
                    encodeNonZeroIntegerOnSecondBitFirstBitZero(names[i].index);
                    return;
                }
            }
        }

        encodeLiteralAttributeQualifiedNameOnSecondBit(namespaceURI, prefix,
                localName, entry);
    }

    /**
     * Encode a literal qualified name of an Attribute Informaiton Item on the
     * third bit of an octet.
     * Implementation of clause C.17 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param namespaceURI the namespace URI of the qualified name.
     * @param prefix the prefix of the qualified name.
     * @param localName the local name of the qualified name.
     */
    protected final boolean encodeLiteralAttributeQualifiedNameOnSecondBit(String namespaceURI, String prefix, String localName,
                LocalNameQualifiedNamesMap.Entry entry) throws IOException {
        int namespaceURIIndex = KeyIntMap.NOT_PRESENT;
        int prefixIndex = KeyIntMap.NOT_PRESENT;
        if (namespaceURI.length() > 0) {
            namespaceURIIndex = _v.namespaceName.get(namespaceURI);
            if (namespaceURIIndex == KeyIntMap.NOT_PRESENT) {
                if (namespaceURI == EncodingConstants.XMLNS_NAMESPACE_NAME ||
                        namespaceURI.equals(EncodingConstants.XMLNS_NAMESPACE_NAME)) {
                    return false;
                } else {
                    throw new IOException(CommonResourceBundle.getInstance().getString("message.namespaceURINotIndexed", new Object[]{namespaceURI}));
                }
            }

            if (prefix.length() > 0) {
                prefixIndex = _v.prefix.get(prefix);
                if (prefixIndex == KeyIntMap.NOT_PRESENT) {
                    throw new IOException(CommonResourceBundle.getInstance().getString("message.prefixNotIndexed", new Object[]{prefix}));
                }
            }
        }

        int localNameIndex = _v.localName.obtainIndex(localName);

        QualifiedName name = new QualifiedName(prefix, namespaceURI, localName, "", _v.attributeName.getNextIndex());
        entry.addQualifiedName(name);

        _b = EncodingConstants.ATTRIBUTE_LITERAL_QNAME_FLAG;
        if (namespaceURI.length() > 0) {
            _b |= EncodingConstants.LITERAL_QNAME_NAMESPACE_NAME_FLAG;
            if (prefix.length() > 0) {
                _b |= EncodingConstants.LITERAL_QNAME_PREFIX_FLAG;
            }
        }

        write(_b);

        if (namespaceURIIndex >= 0) {
            if (prefixIndex >= 0) {
                encodeNonZeroIntegerOnSecondBitFirstBitOne(prefixIndex);
            }
            encodeNonZeroIntegerOnSecondBitFirstBitOne(namespaceURIIndex);
        } else if (namespaceURI != "") {
            // XML prefix and namespace name
            encodeNonEmptyOctetStringOnSecondBit("xml");
            encodeNonEmptyOctetStringOnSecondBit("http://www.w3.org/XML/1998/namespace");
        }

        if (localNameIndex >= 0) {
            encodeNonZeroIntegerOnSecondBitFirstBitOne(localNameIndex);
        } else {
            encodeNonEmptyOctetStringOnSecondBit(localName);
        }

        return true;
    }

    /**
     * Encode a non identifying string on the first bit of an octet.
     * Implementation of clause C.14 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param s the string to encode
     * @param map the vocabulary table of strings to indexes.
     * @param addToTable true if the string could be added to the vocabulary
     *                   table (if table has enough memory)
     * @param mustBeAddedToTable true if the string must be added to the vocabulary
     *                   table (if not already present in the table).
     */
    protected final void encodeNonIdentifyingStringOnFirstBit(String s, StringIntMap map,
            boolean addToTable, boolean mustBeAddedToTable) throws IOException {
        if (s == null || s.length() == 0) {
            // C.26 an index (first bit '1') with seven '1' bits for an empty string
            write(0xFF);
        } else {
            if (addToTable || mustBeAddedToTable) {
                // if attribute value could be added to table
                boolean canAddAttributeToTable = mustBeAddedToTable ||
                        canAddAttributeToTable(s.length());

                // obtain/get index
                int index = canAddAttributeToTable ?
                    map.obtainIndex(s) :
                    map.get(s);

                if (index != KeyIntMap.NOT_PRESENT) {
                    // if attribute value is in table
                    encodeNonZeroIntegerOnSecondBitFirstBitOne(index);
                } else if (canAddAttributeToTable) {
                    // if attribute value is not in table, but could be added
                    _b = EncodingConstants.NISTRING_ADD_TO_TABLE_FLAG |
                            _nonIdentifyingStringOnFirstBitCES;
                    encodeNonEmptyCharacterStringOnFifthBit(s);
                } else {
                    // if attribute value is not in table and could not be added
                    _b = _nonIdentifyingStringOnFirstBitCES;
                    encodeNonEmptyCharacterStringOnFifthBit(s);
                }
            } else {
                _b = _nonIdentifyingStringOnFirstBitCES;
                encodeNonEmptyCharacterStringOnFifthBit(s);
            }
        }
    }

    /**
     * Encode a non identifying string on the first bit of an octet.
     * Implementation of clause C.14 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param s the string to encode
     * @param map the vocabulary table of character arrays to indexes.
     * @param addToTable true if the string should be added to the vocabulary
     *                   table (if not already present in the table).
     */
    protected final void encodeNonIdentifyingStringOnFirstBit(String s, CharArrayIntMap map, boolean addToTable) throws IOException {
        if (s == null || s.length() == 0) {
            // C.26 an index (first bit '1') with seven '1' bits for an empty string
            write(0xFF);
        } else {
            if (addToTable) {
                final char[] ch = s.toCharArray();
                final int length = s.length();

                // if char array could be added to table
                boolean canAddCharacterContentToTable =
                        canAddCharacterContentToTable(length, map);

                // obtain/get index
                int index = canAddCharacterContentToTable ?
                    map.obtainIndex(ch, 0, length, false) :
                    map.get(ch, 0, length);

                if (index != KeyIntMap.NOT_PRESENT) {
                    // if char array is in table
                    encodeNonZeroIntegerOnSecondBitFirstBitOne(index);
                } else if (canAddCharacterContentToTable) {
                    // if char array is not in table, but could be added
                    _b = EncodingConstants.NISTRING_ADD_TO_TABLE_FLAG |
                            _nonIdentifyingStringOnFirstBitCES;
                    encodeNonEmptyCharacterStringOnFifthBit(ch, 0, length);
                } else {
                    // if char array is not in table and could not be added
                    _b = _nonIdentifyingStringOnFirstBitCES;
                    encodeNonEmptyCharacterStringOnFifthBit(s);
                }
            } else {
                _b = _nonIdentifyingStringOnFirstBitCES;
                encodeNonEmptyCharacterStringOnFifthBit(s);
            }
        }
    }

    /**
     * Encode a non identifying string on the first bit of an octet.
     * Implementation of clause C.14 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     * @param map the vocabulary table of character arrays to indexes.
     * @param addToTable true if the string should be added to the vocabulary
     *                   table (if not already present in the table).
     * @param clone true if the array of characters should be cloned if added
     *              to the vocabulary table.
     */
    protected final void encodeNonIdentifyingStringOnFirstBit(char[] ch, int offset, int length, CharArrayIntMap map,
            boolean addToTable, boolean clone) throws IOException {
        if (length == 0) {
            // C.26 an index (first bit '1') with seven '1' bits for an empty string
            write(0xFF);
        } else {
            if (addToTable) {
                // if char array could be added to table
                boolean canAddCharacterContentToTable =
                        canAddCharacterContentToTable(length, map);

                // obtain/get index
                int index = canAddCharacterContentToTable ?
                    map.obtainIndex(ch, offset, length, clone) :
                    map.get(ch, offset, length);

                if (index != KeyIntMap.NOT_PRESENT) {
                    // if char array is in table
                    encodeNonZeroIntegerOnSecondBitFirstBitOne(index);
                } else if (canAddCharacterContentToTable) {
                    // if char array is not in table, but could be added
                    _b = EncodingConstants.NISTRING_ADD_TO_TABLE_FLAG |
                            _nonIdentifyingStringOnFirstBitCES;
                    encodeNonEmptyCharacterStringOnFifthBit(ch, offset, length);
                } else {
                    // if char array is not in table and could not be added
                    _b = _nonIdentifyingStringOnFirstBitCES;
                    encodeNonEmptyCharacterStringOnFifthBit(ch, offset, length);
                }
            } else {
                _b = _nonIdentifyingStringOnFirstBitCES;
                encodeNonEmptyCharacterStringOnFifthBit(ch, offset, length);
            }
        }
    }

    protected final void encodeNumericNonIdentifyingStringOnFirstBit(
            String s, boolean addToTable, boolean mustBeAddedToTable)
            throws IOException, FastInfosetException {
        encodeNonIdentifyingStringOnFirstBit(
                                    RestrictedAlphabet.NUMERIC_CHARACTERS_INDEX,
                                    NUMERIC_CHARACTERS_TABLE, s, addToTable,
                                    mustBeAddedToTable);
    }

    protected final void encodeDateTimeNonIdentifyingStringOnFirstBit(
            String s, boolean addToTable, boolean mustBeAddedToTable)
            throws IOException, FastInfosetException {
        encodeNonIdentifyingStringOnFirstBit(
                                    RestrictedAlphabet.DATE_TIME_CHARACTERS_INDEX,
                                    DATE_TIME_CHARACTERS_TABLE, s, addToTable,
                                    mustBeAddedToTable);
    }

    protected final void encodeNonIdentifyingStringOnFirstBit(int id, int[] table,
            String s, boolean addToTable, boolean mustBeAddedToTable)
            throws IOException, FastInfosetException {
        if (s == null || s.length() == 0) {
            // C.26 an index (first bit '1') with seven '1' bits for an empty string
            write(0xFF);
            return;
        }

        if (addToTable || mustBeAddedToTable) {
            // if attribute value could be added to table
            boolean canAddAttributeToTable = mustBeAddedToTable ||
                    canAddAttributeToTable(s.length());

            // obtain/get index
            int index = canAddAttributeToTable ?
                _v.attributeValue.obtainIndex(s) :
                _v.attributeValue.get(s);

            if (index != KeyIntMap.NOT_PRESENT) {
                // if attribute value is in table
                encodeNonZeroIntegerOnSecondBitFirstBitOne(index);
                return;
            } else if (canAddAttributeToTable) {
                // if attribute value is not in table, but could be added
                _b = EncodingConstants.NISTRING_RESTRICTED_ALPHABET_FLAG |
                        EncodingConstants.NISTRING_ADD_TO_TABLE_FLAG;
            } else {
                // if attribute value is not in table and could not be added
                _b = EncodingConstants.NISTRING_RESTRICTED_ALPHABET_FLAG;
            }
        } else {
            _b = EncodingConstants.NISTRING_RESTRICTED_ALPHABET_FLAG;
        }

        // Encode identification and top four bits of alphabet id
        write (_b | ((id & 0xF0) >> 4));
        // Encode bottom 4 bits of alphabet id
        _b = (id & 0x0F) << 4;

        final int length = s.length();
        final int octetPairLength = length / 2;
        final int octetSingleLength = length % 2;
        encodeNonZeroOctetStringLengthOnFifthBit(octetPairLength + octetSingleLength);
        encodeNonEmptyFourBitCharacterString(table, s.toCharArray(), 0, octetPairLength, octetSingleLength);
    }

    /**
     * Encode a non identifying string on the first bit of an octet as binary
     * data using an encoding algorithm.
     * Implementation of clause C.14 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param URI the encoding algorithm URI. If the URI == null then the
     *            encoding algorithm identifier takes precendence.
     * @param id the encoding algorithm identifier.
     * @param data the data to be encoded using an encoding algorithm.
     * @throws EncodingAlgorithmException if the encoding algorithm URI is not
     *         present in the vocabulary, or the encoding algorithm identifier
     *         is not with the required range.
     */
    protected final void encodeNonIdentifyingStringOnFirstBit(String URI, int id, Object data) throws FastInfosetException, IOException {
        if (URI != null) {
            id = _v.encodingAlgorithm.get(URI);
            if (id == KeyIntMap.NOT_PRESENT) {
                throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.EncodingAlgorithmURI", new Object[]{URI}));
            }
            id += EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START;

            EncodingAlgorithm ea = (EncodingAlgorithm)_registeredEncodingAlgorithms.get(URI);
            if (ea != null) {
                encodeAIIObjectAlgorithmData(id, data, ea);
            } else {
                if (data instanceof byte[]) {
                    byte[] d = (byte[])data;
                    encodeAIIOctetAlgorithmData(id, d, 0, d.length);
                } else {
                    throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.nullEncodingAlgorithmURI"));
                }
            }
        } else if (id <= EncodingConstants.ENCODING_ALGORITHM_BUILTIN_END) {
            int length = 0;
            switch(id) {
                case EncodingAlgorithmIndexes.HEXADECIMAL:
                case EncodingAlgorithmIndexes.BASE64:
                    length = ((byte[])data).length;
                    break;
                case EncodingAlgorithmIndexes.SHORT:
                    length = ((short[])data).length;
                    break;
                case EncodingAlgorithmIndexes.INT:
                    length = ((int[])data).length;
                    break;
                case EncodingAlgorithmIndexes.LONG:
                case EncodingAlgorithmIndexes.UUID:
                    length = ((long[])data).length;
                    break;
                case EncodingAlgorithmIndexes.BOOLEAN:
                    length = ((boolean[])data).length;
                    break;
                case EncodingAlgorithmIndexes.FLOAT:
                    length = ((float[])data).length;
                    break;
                case EncodingAlgorithmIndexes.DOUBLE:
                    length = ((double[])data).length;
                    break;
                case EncodingAlgorithmIndexes.CDATA:
                    throw new UnsupportedOperationException(CommonResourceBundle.getInstance().getString("message.CDATA"));
                default:
                    throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.UnsupportedBuiltInAlgorithm", new Object[]{Integer.valueOf(id)}));
            }
            encodeAIIBuiltInAlgorithmData(id, data, 0, length);
        } else if (id >= EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START) {
            if (data instanceof byte[]) {
                byte[] d = (byte[])data;
                encodeAIIOctetAlgorithmData(id, d, 0, d.length);
            } else {
                throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.nullEncodingAlgorithmURI"));
            }
        } else {
            throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.identifiers10to31Reserved"));
        }
    }

    /**
     * Encode the [normalized value] of an Attribute Information Item using
     * using an encoding algorithm.
     * Implementation of clause C.14 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param id the encoding algorithm identifier.
     * @param d the data, as an array of bytes, to be encoded.
     * @param offset the offset into the array of bytes.
     * @param length the length of bytes.
     */
    protected final void encodeAIIOctetAlgorithmData(int id, byte[] d, int offset, int length) throws IOException {
        // Encode identification and top four bits of encoding algorithm id
        write (EncodingConstants.NISTRING_ENCODING_ALGORITHM_FLAG |
                ((id & 0xF0) >> 4));

        // Encode bottom 4 bits of enoding algorithm id
        _b = (id & 0x0F) << 4;

        // Encode the length
        encodeNonZeroOctetStringLengthOnFifthBit(length);

        write(d, offset, length);
    }

    /**
     * Encode the [normalized value] of an Attribute Information Item using
     * using an encoding algorithm.
     * Implementation of clause C.14 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param id the encoding algorithm identifier.
     * @param data the data to be encoded using an encoding algorithm.
     * @param ea the encoding algorithm to use to encode the data into an
     *           array of bytes.
     */
    protected final void encodeAIIObjectAlgorithmData(int id, Object data, EncodingAlgorithm ea) throws FastInfosetException, IOException {
        // Encode identification and top four bits of encoding algorithm id
        write (EncodingConstants.NISTRING_ENCODING_ALGORITHM_FLAG |
                ((id & 0xF0) >> 4));

        // Encode bottom 4 bits of enoding algorithm id
        _b = (id & 0x0F) << 4;

        _encodingBufferOutputStream.reset();
        ea.encodeToOutputStream(data, _encodingBufferOutputStream);
        encodeNonZeroOctetStringLengthOnFifthBit(_encodingBufferIndex);
        write(_encodingBuffer, _encodingBufferIndex);
    }

    /**
     * Encode the [normalized value] of an Attribute Information Item using
     * using a built in encoding algorithm.
     * Implementation of clause C.14 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param id the built in encoding algorithm identifier.
     * @param data the data to be encoded using an encoding algorithm. The data
     *        represents an array of items specified by the encoding algorithm
     *        identifier
     * @param offset the offset into the array of bytes.
     * @param length the length of bytes.
     */
    protected final void encodeAIIBuiltInAlgorithmData(int id, Object data, int offset, int length) throws IOException {
        // Encode identification and top four bits of encoding algorithm id
        write (EncodingConstants.NISTRING_ENCODING_ALGORITHM_FLAG |
                ((id & 0xF0) >> 4));

        // Encode bottom 4 bits of enoding algorithm id
        _b = (id & 0x0F) << 4;

        final int octetLength = BuiltInEncodingAlgorithmFactory.getAlgorithm(id).
                    getOctetLengthFromPrimitiveLength(length);

        encodeNonZeroOctetStringLengthOnFifthBit(octetLength);

        ensureSize(octetLength);
        BuiltInEncodingAlgorithmFactory.getAlgorithm(id).
                encodeToBytes(data, offset, length, _octetBuffer, _octetBufferIndex);
        _octetBufferIndex += octetLength;
    }

    /**
     * Encode a non identifying string on the third bit of an octet.
     * Implementation of clause C.15 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     * @param map the vocabulary table of character arrays to indexes.
     * @param addToTable true if the array of characters should be added to the vocabulary
     *                   table (if not already present in the table).
     * @param clone true if the array of characters should be cloned if added
     *              to the vocabulary table.
     */
    protected final void encodeNonIdentifyingStringOnThirdBit(char[] ch, int offset, int length,
            CharArrayIntMap map, boolean addToTable, boolean clone) throws IOException {
        // length cannot be zero since sequence of CIIs has to be > 0

        if (addToTable) {
            // if char array could be added to table
            boolean canAddCharacterContentToTable =
                    canAddCharacterContentToTable(length, map);

            // obtain/get index
            int index = canAddCharacterContentToTable ?
                map.obtainIndex(ch, offset, length, clone) :
                map.get(ch, offset, length);

            if (index != KeyIntMap.NOT_PRESENT) {
                // if char array is in table
                _b = EncodingConstants.CHARACTER_CHUNK | 0x20;
                encodeNonZeroIntegerOnFourthBit(index);
            } else if (canAddCharacterContentToTable) {
                // if char array is not in table, but could be added
                _b = EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG |
                        _nonIdentifyingStringOnThirdBitCES;
                encodeNonEmptyCharacterStringOnSeventhBit(ch, offset, length);
            } else {
                // if char array is not in table and could not be added
                    _b = _nonIdentifyingStringOnThirdBitCES;
                    encodeNonEmptyCharacterStringOnSeventhBit(ch, offset, length);
            }
        } else {
            // char array will not be added to map
            _b = _nonIdentifyingStringOnThirdBitCES;
            encodeNonEmptyCharacterStringOnSeventhBit(ch, offset, length);
        }
    }

    /**
     * Encode a non identifying string on the third bit of an octet as binary
     * data using an encoding algorithm.
     * Implementation of clause C.15 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param URI the encoding algorithm URI. If the URI == null then the
     *            encoding algorithm identifier takes precendence.
     * @param id the encoding algorithm identifier.
     * @param data the data to be encoded using an encoding algorithm.
     * @throws EncodingAlgorithmException if the encoding algorithm URI is not
     *         present in the vocabulary, or the encoding algorithm identifier
     *         is not with the required range.
     */
    protected final void encodeNonIdentifyingStringOnThirdBit(String URI, int id, Object data) throws FastInfosetException, IOException {
        if (URI != null) {
            id = _v.encodingAlgorithm.get(URI);
            if (id == KeyIntMap.NOT_PRESENT) {
                throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.EncodingAlgorithmURI", new Object[]{URI}));
            }
            id += EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START;

            EncodingAlgorithm ea = (EncodingAlgorithm)_registeredEncodingAlgorithms.get(URI);
            if (ea != null) {
                encodeCIIObjectAlgorithmData(id, data, ea);
            } else {
                if (data instanceof byte[]) {
                    byte[] d = (byte[])data;
                    encodeCIIOctetAlgorithmData(id, d, 0, d.length);
                } else {
                    throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.nullEncodingAlgorithmURI"));
                }
            }
        } else if (id <= EncodingConstants.ENCODING_ALGORITHM_BUILTIN_END) {
            int length = 0;
            switch(id) {
                case EncodingAlgorithmIndexes.HEXADECIMAL:
                case EncodingAlgorithmIndexes.BASE64:
                    length = ((byte[])data).length;
                    break;
                case EncodingAlgorithmIndexes.SHORT:
                    length = ((short[])data).length;
                    break;
                case EncodingAlgorithmIndexes.INT:
                    length = ((int[])data).length;
                    break;
                case EncodingAlgorithmIndexes.LONG:
                case EncodingAlgorithmIndexes.UUID:
                    length = ((long[])data).length;
                    break;
                case EncodingAlgorithmIndexes.BOOLEAN:
                    length = ((boolean[])data).length;
                    break;
                case EncodingAlgorithmIndexes.FLOAT:
                    length = ((float[])data).length;
                    break;
                case EncodingAlgorithmIndexes.DOUBLE:
                    length = ((double[])data).length;
                    break;
                case EncodingAlgorithmIndexes.CDATA:
                    throw new UnsupportedOperationException(CommonResourceBundle.getInstance().getString("message.CDATA"));
                default:
                    throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.UnsupportedBuiltInAlgorithm", new Object[]{Integer.valueOf(id)}));
            }
            encodeCIIBuiltInAlgorithmData(id, data, 0, length);
        } else if (id >= EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START) {
            if (data instanceof byte[]) {
                byte[] d = (byte[])data;
                encodeCIIOctetAlgorithmData(id, d, 0, d.length);
            } else {
                throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.nullEncodingAlgorithmURI"));
            }
        } else {
            throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.identifiers10to31Reserved"));
        }
    }

    /**
     * Encode a non identifying string on the third bit of an octet as binary
     * data using an encoding algorithm.
     * Implementation of clause C.15 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param URI the encoding algorithm URI. If the URI == null then the
     *            encoding algorithm identifier takes precendence.
     * @param id the encoding algorithm identifier.
     * @param d the data, as an array of bytes, to be encoded.
     * @param offset the offset into the array of bytes.
     * @param length the length of bytes.
     * @throws EncodingAlgorithmException if the encoding algorithm URI is not
     *         present in the vocabulary.
     */
    protected final void encodeNonIdentifyingStringOnThirdBit(String URI, int id, byte[] d, int offset, int length) throws FastInfosetException, IOException {
        if (URI != null) {
            id = _v.encodingAlgorithm.get(URI);
            if (id == KeyIntMap.NOT_PRESENT) {
                throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.EncodingAlgorithmURI", new Object[]{URI}));
            }
            id += EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START;
        }

        encodeCIIOctetAlgorithmData(id, d, offset, length);
    }

    /**
     * Encode a chunk of Character Information Items using
     * using an encoding algorithm.
     * Implementation of clause C.15 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param id the encoding algorithm identifier.
     * @param d the data, as an array of bytes, to be encoded.
     * @param offset the offset into the array of bytes.
     * @param length the length of bytes.
     */
    protected final void encodeCIIOctetAlgorithmData(int id, byte[] d, int offset, int length) throws IOException {
        // Encode identification and top two bits of encoding algorithm id
        write (EncodingConstants.CHARACTER_CHUNK | EncodingConstants.CHARACTER_CHUNK_ENCODING_ALGORITHM_FLAG |
                ((id & 0xC0) >> 6));

        // Encode bottom 6 bits of enoding algorithm id
        _b = (id & 0x3F) << 2;

        // Encode the length
        encodeNonZeroOctetStringLengthOnSenventhBit(length);

        write(d, offset, length);
    }

    /**
     * Encode a chunk of Character Information Items using
     * using an encoding algorithm.
     * Implementation of clause C.15 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param id the encoding algorithm identifier.
     * @param data the data to be encoded using an encoding algorithm.
     * @param ea the encoding algorithm to use to encode the data into an
     *           array of bytes.
     */
    protected final void encodeCIIObjectAlgorithmData(int id, Object data, EncodingAlgorithm ea) throws FastInfosetException, IOException {
        // Encode identification and top two bits of encoding algorithm id
        write (EncodingConstants.CHARACTER_CHUNK | EncodingConstants.CHARACTER_CHUNK_ENCODING_ALGORITHM_FLAG |
                ((id & 0xC0) >> 6));

        // Encode bottom 6 bits of enoding algorithm id
        _b = (id & 0x3F) << 2;

        _encodingBufferOutputStream.reset();
        ea.encodeToOutputStream(data, _encodingBufferOutputStream);
        encodeNonZeroOctetStringLengthOnSenventhBit(_encodingBufferIndex);
        write(_encodingBuffer, _encodingBufferIndex);
    }

    /**
     * Encode a chunk of Character Information Items using
     * using an encoding algorithm.
     * Implementation of clause C.15 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param id the built in encoding algorithm identifier.
     * @param data the data to be encoded using an encoding algorithm. The data
     *        represents an array of items specified by the encoding algorithm
     *        identifier
     * @param offset the offset into the array of bytes.
     * @param length the length of bytes.
     */
    protected final void encodeCIIBuiltInAlgorithmData(int id, Object data, int offset, int length) throws FastInfosetException, IOException {
        // Encode identification and top two bits of encoding algorithm id
        write (EncodingConstants.CHARACTER_CHUNK | EncodingConstants.CHARACTER_CHUNK_ENCODING_ALGORITHM_FLAG |
                ((id & 0xC0) >> 6));

        // Encode bottom 6 bits of enoding algorithm id
        _b = (id & 0x3F) << 2;

        final int octetLength = BuiltInEncodingAlgorithmFactory.getAlgorithm(id).
                    getOctetLengthFromPrimitiveLength(length);

        encodeNonZeroOctetStringLengthOnSenventhBit(octetLength);

        ensureSize(octetLength);
        BuiltInEncodingAlgorithmFactory.getAlgorithm(id).
                encodeToBytes(data, offset, length, _octetBuffer, _octetBufferIndex);
        _octetBufferIndex += octetLength;
    }

    /**
     * Encode a chunk of Character Information Items using
     * using the CDATA built in encoding algorithm.
     * Implementation of clause C.15 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     */
    protected final void encodeCIIBuiltInAlgorithmDataAsCDATA(char[] ch, int offset, int length) throws FastInfosetException, IOException {
        // Encode identification and top two bits of encoding algorithm id
        write (EncodingConstants.CHARACTER_CHUNK | EncodingConstants.CHARACTER_CHUNK_ENCODING_ALGORITHM_FLAG);

        // Encode bottom 6 bits of enoding algorithm id
        _b = EncodingAlgorithmIndexes.CDATA << 2;


        length = encodeUTF8String(ch, offset, length);
        encodeNonZeroOctetStringLengthOnSenventhBit(length);
        write(_encodingBuffer, length);
    }

    /**
     * Encode a non empty identifying string on the first bit of an octet.
     * Implementation of clause C.13 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param s the identifying string.
     * @param map the vocabulary table to use to determin the index of the
     *        identifying string
     */
    protected final void encodeIdentifyingNonEmptyStringOnFirstBit(String s, StringIntMap map) throws IOException {
        int index = map.obtainIndex(s);
        if (index == KeyIntMap.NOT_PRESENT) {
            // _b = 0;
            encodeNonEmptyOctetStringOnSecondBit(s);
        } else {
            // _b = 0x80;
            encodeNonZeroIntegerOnSecondBitFirstBitOne(index);
        }
    }

    /**
     * Encode a non empty string on the second bit of an octet using the UTF-8
     * encoding.
     * Implementation of clause C.22 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param s the string.
     */
    protected final void encodeNonEmptyOctetStringOnSecondBit(String s) throws IOException {
        final int length = encodeUTF8String(s);
        encodeNonZeroOctetStringLengthOnSecondBit(length);
        write(_encodingBuffer, length);
    }

    /**
     * Encode the length of a UTF-8 encoded string on the second bit of an octet.
     * Implementation of clause C.22 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param length the length to encode.
     */
    protected final void encodeNonZeroOctetStringLengthOnSecondBit(int length) throws IOException {
        if (length < EncodingConstants.OCTET_STRING_LENGTH_2ND_BIT_SMALL_LIMIT) {
            // [1, 64]
            write(length - 1);
        } else if (length < EncodingConstants.OCTET_STRING_LENGTH_2ND_BIT_MEDIUM_LIMIT) {
            // [65, 320]
            write(EncodingConstants.OCTET_STRING_LENGTH_2ND_BIT_MEDIUM_FLAG); // 010 00000
            write(length - EncodingConstants.OCTET_STRING_LENGTH_2ND_BIT_SMALL_LIMIT);
        } else {
            // [321, 4294967296]
            write(EncodingConstants.OCTET_STRING_LENGTH_2ND_BIT_LARGE_FLAG); // 0110 0000
            length -= EncodingConstants.OCTET_STRING_LENGTH_2ND_BIT_MEDIUM_LIMIT;
            write(length >>> 24);
            write((length >> 16) & 0xFF);
            write((length >> 8) & 0xFF);
            write(length & 0xFF);
        }
    }

    /**
     * Encode a non empty string on the fifth bit of an octet using the UTF-8
     * or UTF-16 encoding.
     * Implementation of clause C.23 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param s the string.
     */
    protected final void encodeNonEmptyCharacterStringOnFifthBit(String s) throws IOException {
        final int length = (_encodingStringsAsUtf8) ? encodeUTF8String(s) : encodeUtf16String(s);
        encodeNonZeroOctetStringLengthOnFifthBit(length);
        write(_encodingBuffer, length);
    }

    /**
     * Encode a non empty string on the fifth bit of an octet using the UTF-8
     * or UTF-16 encoding.
     * Implementation of clause C.23 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     */
    protected final void encodeNonEmptyCharacterStringOnFifthBit(char[] ch, int offset, int length) throws IOException {
        length = (_encodingStringsAsUtf8) ? encodeUTF8String(ch, offset, length) : encodeUtf16String(ch, offset, length);
        encodeNonZeroOctetStringLengthOnFifthBit(length);
        write(_encodingBuffer, length);
    }

    /**
     * Encode the length of a UTF-8 or UTF-16 encoded string on the fifth bit
     * of an octet.
     * Implementation of clause C.23 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param length the length to encode.
     */
    protected final void encodeNonZeroOctetStringLengthOnFifthBit(int length) throws IOException {
        if (length < EncodingConstants.OCTET_STRING_LENGTH_5TH_BIT_SMALL_LIMIT) {
            // [1, 8]
            write(_b | (length - 1));
        } else if (length < EncodingConstants.OCTET_STRING_LENGTH_5TH_BIT_MEDIUM_LIMIT) {
            // [9, 264]
            write(_b | EncodingConstants.OCTET_STRING_LENGTH_5TH_BIT_MEDIUM_FLAG); // 000010 00
            write(length - EncodingConstants.OCTET_STRING_LENGTH_5TH_BIT_SMALL_LIMIT);
        } else {
            // [265, 4294967296]
            write(_b | EncodingConstants.OCTET_STRING_LENGTH_5TH_BIT_LARGE_FLAG); // 000011 00
            length -= EncodingConstants.OCTET_STRING_LENGTH_5TH_BIT_MEDIUM_LIMIT;
            write(length >>> 24);
            write((length >> 16) & 0xFF);
            write((length >> 8) & 0xFF);
            write(length & 0xFF);
        }
    }

    /**
     * Encode a non empty string on the seventh bit of an octet using the UTF-8
     * or UTF-16 encoding.
     * Implementation of clause C.24 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     */
    protected final void encodeNonEmptyCharacterStringOnSeventhBit(char[] ch, int offset, int length) throws IOException {
        length = (_encodingStringsAsUtf8) ? encodeUTF8String(ch, offset, length) : encodeUtf16String(ch, offset, length);
        encodeNonZeroOctetStringLengthOnSenventhBit(length);
        write(_encodingBuffer, length);
    }

    /**
     * Encode a non empty string on the seventh bit of an octet using a restricted
     * alphabet that results in the encoding of a character in 4 bits
     * (or two characters per octet).
     * Implementation of clause C.24 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param table the table mapping characters to 4 bit values.
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     */
    protected final void encodeNonEmptyFourBitCharacterStringOnSeventhBit(int[] table, char[] ch, int offset, int length) throws FastInfosetException, IOException {
        final int octetPairLength = length / 2;
        final int octetSingleLength = length % 2;

        // Encode the length
        encodeNonZeroOctetStringLengthOnSenventhBit(octetPairLength + octetSingleLength);
        encodeNonEmptyFourBitCharacterString(table, ch, offset, octetPairLength, octetSingleLength);
    }

    protected final void encodeNonEmptyFourBitCharacterString(int[] table, char[] ch, int offset,
            int octetPairLength, int octetSingleLength) throws FastInfosetException, IOException {
        ensureSize(octetPairLength + octetSingleLength);
        // Encode all pairs
        int v = 0;
        for (int i = 0; i < octetPairLength; i++) {
            v = (table[ch[offset++]] << 4) | table[ch[offset++]];
            if (v < 0) {
                throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.characterOutofAlphabetRange"));
            }
            _octetBuffer[_octetBufferIndex++] = (byte)v;
        }
        // Encode single character at end with termination bits
        if (octetSingleLength == 1) {
            v = (table[ch[offset]] << 4) | 0x0F;
            if (v < 0) {
                throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.characterOutofAlphabetRange"));
            }
            _octetBuffer[_octetBufferIndex++] = (byte)v;
        }
    }

    /**
     * Encode a non empty string on the seventh bit of an octet using a restricted
     * alphabet table.
     * Implementation of clause C.24 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param alphabet the alphabet defining the mapping between characters and
     *        integer values.
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     */
    protected final void encodeNonEmptyNBitCharacterStringOnSeventhBit(String alphabet, char[] ch, int offset, int length) throws FastInfosetException, IOException {
        int bitsPerCharacter = 1;
        while ((1 << bitsPerCharacter) <= alphabet.length()) {
            bitsPerCharacter++;
        }

        final int bits = length * bitsPerCharacter;
        final int octets = bits / 8;
        final int bitsOfLastOctet = bits % 8;
        final int totalOctets = octets + ((bitsOfLastOctet > 0) ? 1 : 0);

        // Encode the length
        encodeNonZeroOctetStringLengthOnSenventhBit(totalOctets);

        resetBits();
        ensureSize(totalOctets);
        int v = 0;
        for (int i = 0; i < length; i++) {
            final char c = ch[offset + i];
            // This is grotesquely slow, need to use hash table of character to int value
            for (v = 0; v < alphabet.length(); v++) {
                if (c == alphabet.charAt(v)) {
                    break;
                }
            }
            if (v == alphabet.length()) {
                throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.characterOutofAlphabetRange"));
            }
            writeBits(bitsPerCharacter, v);
        }

        if (bitsOfLastOctet > 0) {
            _b |= (1 << (8 - bitsOfLastOctet)) - 1;
            write(_b);
        }
    }

    private int _bitsLeftInOctet;

    private final void resetBits() {
        _bitsLeftInOctet = 8;
        _b = 0;
    }

    private final void writeBits(int bits, int v) throws IOException {
        while (bits > 0) {
            final int bit = (v & (1 << --bits)) > 0 ? 1 : 0;
            _b |= bit << (--_bitsLeftInOctet);
            if (_bitsLeftInOctet == 0) {
                write(_b);
                _bitsLeftInOctet = 8;
                _b = 0;
            }
        }
    }

    /**
     * Encode the length of a encoded string on the seventh bit
     * of an octet.
     * Implementation of clause C.24 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param length the length to encode.
     */
    protected final void encodeNonZeroOctetStringLengthOnSenventhBit(int length) throws IOException {
        if (length < EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_LIMIT) {
            // [1, 2]
            write(_b | (length - 1));
        } else if (length < EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_MEDIUM_LIMIT) {
            // [3, 258]
            write(_b | EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_MEDIUM_FLAG); // 00000010
            write(length - EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_LIMIT);
        } else {
            // [259, 4294967296]
            write(_b | EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_LARGE_FLAG); // 00000011
            length -= EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_MEDIUM_LIMIT;
            write(length >>> 24);
            write((length >> 16) & 0xFF);
            write((length >> 8) & 0xFF);
            write(length & 0xFF);
        }
    }

    /**
     * Encode a non zero integer on the second bit of an octet, setting
     * the first bit to 1.
     * Implementation of clause C.24 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * <p>
     * The first bit of the first octet is set, as specified in clause C.13 of
     * ITU-T Rec. X.891 | ISO/IEC 24824-1
     *
     * @param i The integer to encode, which is a member of the interval
     *          [0, 1048575]. In the specification the interval is [1, 1048576]
     *
     */
    protected final void encodeNonZeroIntegerOnSecondBitFirstBitOne(int i) throws IOException {
        if (i < EncodingConstants.INTEGER_2ND_BIT_SMALL_LIMIT) {
            // [1, 64] ( [0, 63] ) 6 bits
            write(0x80 | i);
        } else if (i < EncodingConstants.INTEGER_2ND_BIT_MEDIUM_LIMIT) {
            // [65, 8256] ( [64, 8255] ) 13 bits
            i -= EncodingConstants.INTEGER_2ND_BIT_SMALL_LIMIT;
            _b = (0x80 | EncodingConstants.INTEGER_2ND_BIT_MEDIUM_FLAG) | (i >> 8); // 010 00000
            // _b = 0xC0 | (i >> 8); // 010 00000
            write(_b);
            write(i & 0xFF);
        } else if (i < EncodingConstants.INTEGER_2ND_BIT_LARGE_LIMIT) {
            // [8257, 1048576] ( [8256, 1048575] ) 20 bits
            i -= EncodingConstants.INTEGER_2ND_BIT_MEDIUM_LIMIT;
            _b = (0x80 | EncodingConstants.INTEGER_2ND_BIT_LARGE_FLAG) | (i >> 16); // 0110 0000
            // _b = 0xE0 | (i >> 16); // 0110 0000
            write(_b);
            write((i >> 8) & 0xFF);
            write(i & 0xFF);
        } else {
            throw new IOException(
                    CommonResourceBundle.getInstance().getString("message.integerMaxSize",
                    new Object[]{Integer.valueOf(EncodingConstants.INTEGER_2ND_BIT_LARGE_LIMIT)}));
        }
    }

    /**
     * Encode a non zero integer on the second bit of an octet, setting
     * the first bit to 0.
     * Implementation of clause C.25 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * <p>
     * The first bit of the first octet is set, as specified in clause C.13 of
     * ITU-T Rec. X.891 | ISO/IEC 24824-1
     *
     * @param i The integer to encode, which is a member of the interval
     *          [0, 1048575]. In the specification the interval is [1, 1048576]
     *
     */
    protected final void encodeNonZeroIntegerOnSecondBitFirstBitZero(int i) throws IOException {
        if (i < EncodingConstants.INTEGER_2ND_BIT_SMALL_LIMIT) {
            // [1, 64] ( [0, 63] ) 6 bits
            write(i);
        } else if (i < EncodingConstants.INTEGER_2ND_BIT_MEDIUM_LIMIT) {
            // [65, 8256] ( [64, 8255] ) 13 bits
            i -= EncodingConstants.INTEGER_2ND_BIT_SMALL_LIMIT;
            _b = EncodingConstants.INTEGER_2ND_BIT_MEDIUM_FLAG | (i >> 8); // 010 00000
            write(_b);
            write(i & 0xFF);
        } else {
            // [8257, 1048576] ( [8256, 1048575] ) 20 bits
            i -= EncodingConstants.INTEGER_2ND_BIT_MEDIUM_LIMIT;
            _b = EncodingConstants.INTEGER_2ND_BIT_LARGE_FLAG | (i >> 16); // 0110 0000
            write(_b);
            write((i >> 8) & 0xFF);
            write(i & 0xFF);
        }
    }

    /**
     * Encode a non zero integer on the third bit of an octet.
     * Implementation of clause C.27 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param i The integer to encode, which is a member of the interval
     *          [0, 1048575]. In the specification the interval is [1, 1048576]
     *
     */
    protected final void encodeNonZeroIntegerOnThirdBit(int i) throws IOException {
        if (i < EncodingConstants.INTEGER_3RD_BIT_SMALL_LIMIT) {
            // [1, 32] ( [0, 31] ) 5 bits
            write(_b | i);
        } else if (i < EncodingConstants.INTEGER_3RD_BIT_MEDIUM_LIMIT) {
            // [33, 2080] ( [32, 2079] ) 11 bits
            i -= EncodingConstants.INTEGER_3RD_BIT_SMALL_LIMIT;
            _b |= EncodingConstants.INTEGER_3RD_BIT_MEDIUM_FLAG | (i >> 8); // 00100 000
            write(_b);
            write(i & 0xFF);
        } else if (i < EncodingConstants.INTEGER_3RD_BIT_LARGE_LIMIT) {
            // [2081, 526368] ( [2080, 526367] ) 19 bits
            i -= EncodingConstants.INTEGER_3RD_BIT_MEDIUM_LIMIT;
            _b |= EncodingConstants.INTEGER_3RD_BIT_LARGE_FLAG | (i >> 16); // 00101 000
            write(_b);
            write((i >> 8) & 0xFF);
            write(i & 0xFF);
        } else {
            // [526369, 1048576] ( [526368, 1048575] ) 20 bits
            i -= EncodingConstants.INTEGER_3RD_BIT_LARGE_LIMIT;
            _b |= EncodingConstants.INTEGER_3RD_BIT_LARGE_LARGE_FLAG; // 00110 000
            write(_b);
            write(i >> 16);
            write((i >> 8) & 0xFF);
            write(i & 0xFF);
        }
    }

    /**
     * Encode a non zero integer on the fourth bit of an octet.
     * Implementation of clause C.28 of ITU-T Rec. X.891 | ISO/IEC 24824-1.
     *
     * @param i The integer to encode, which is a member of the interval
     *          [0, 1048575]. In the specification the interval is [1, 1048576]
     *
     */
    protected final void encodeNonZeroIntegerOnFourthBit(int i) throws IOException {
        if (i < EncodingConstants.INTEGER_4TH_BIT_SMALL_LIMIT) {
            // [1, 16] ( [0, 15] ) 4 bits
            write(_b | i);
        } else if (i < EncodingConstants.INTEGER_4TH_BIT_MEDIUM_LIMIT) {
            // [17, 1040] ( [16, 1039] ) 10 bits
            i -= EncodingConstants.INTEGER_4TH_BIT_SMALL_LIMIT;
            _b |= EncodingConstants.INTEGER_4TH_BIT_MEDIUM_FLAG | (i >> 8); // 000 100 00
            write(_b);
            write(i & 0xFF);
        } else if (i < EncodingConstants.INTEGER_4TH_BIT_LARGE_LIMIT) {
            // [1041, 263184] ( [1040, 263183] ) 18 bits
            i -= EncodingConstants.INTEGER_4TH_BIT_MEDIUM_LIMIT;
            _b |= EncodingConstants.INTEGER_4TH_BIT_LARGE_FLAG | (i >> 16); // 000 101 00
            write(_b);
            write((i >> 8) & 0xFF);
            write(i & 0xFF);
        } else {
            // [263185, 1048576] ( [263184, 1048575] ) 20 bits
            i -= EncodingConstants.INTEGER_4TH_BIT_LARGE_LIMIT;
            _b |= EncodingConstants.INTEGER_4TH_BIT_LARGE_LARGE_FLAG; // 000 110 00
            write(_b);
            write(i >> 16);
            write((i >> 8) & 0xFF);
            write(i & 0xFF);
        }
    }

    /**
     * Encode a non empty string using the UTF-8 encoding.
     *
     * @param b the current octet that is being written.
     * @param s the string to be UTF-8 encoded.
     * @param constants the array of constants to use when encoding to determin
     *        how the length of the UTF-8 encoded string is encoded.
     */
    protected final void encodeNonEmptyUTF8StringAsOctetString(int b, String s, int[] constants) throws IOException {
        final char[] ch = s.toCharArray();
        encodeNonEmptyUTF8StringAsOctetString(b, ch, 0, ch.length, constants);
    }

    /**
     * Encode a non empty string using the UTF-8 encoding.
     *
     * @param b the current octet that is being written.
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     *        how the length of the UTF-8 encoded string is encoded.
     * @param constants the array of constants to use when encoding to determin
     *        how the length of the UTF-8 encoded string is encoded.
     */
    protected final void encodeNonEmptyUTF8StringAsOctetString(int b, char ch[], int offset, int length, int[] constants) throws IOException {
        length = encodeUTF8String(ch, offset, length);
        encodeNonZeroOctetStringLength(b, length, constants);
        write(_encodingBuffer, length);
    }

    /**
     * Encode the length of non empty UTF-8 encoded string.
     *
     * @param b the current octet that is being written.
     * @param length the length of the UTF-8 encoded string.
     *        how the length of the UTF-8 encoded string is encoded.
     * @param constants the array of constants to use when encoding to determin
     *        how the length of the UTF-8 encoded string is encoded.
     */
    protected final void encodeNonZeroOctetStringLength(int b, int length, int[] constants) throws IOException {
        if (length < constants[EncodingConstants.OCTET_STRING_LENGTH_SMALL_LIMIT]) {
            write(b | (length - 1));
        } else if (length < constants[EncodingConstants.OCTET_STRING_LENGTH_MEDIUM_LIMIT]) {
            write(b | constants[EncodingConstants.OCTET_STRING_LENGTH_MEDIUM_FLAG]);
            write(length - constants[EncodingConstants.OCTET_STRING_LENGTH_SMALL_LIMIT]);
        } else {
            write(b | constants[EncodingConstants.OCTET_STRING_LENGTH_LARGE_FLAG]);
            length -= constants[EncodingConstants.OCTET_STRING_LENGTH_MEDIUM_LIMIT];
            write(length >>> 24);
            write((length >> 16) & 0xFF);
            write((length >> 8) & 0xFF);
            write(length & 0xFF);
        }
    }

    /**
     * Encode a non zero integer.
     *
     * @param b the current octet that is being written.
     * @param i the non zero integer.
     * @param constants the array of constants to use when encoding to determin
     *        how the non zero integer is encoded.
     */
    protected final void encodeNonZeroInteger(int b, int i, int[] constants) throws IOException {
        if (i < constants[EncodingConstants.INTEGER_SMALL_LIMIT]) {
            write(b | i);
        } else if (i < constants[EncodingConstants.INTEGER_MEDIUM_LIMIT]) {
            i -= constants[EncodingConstants.INTEGER_SMALL_LIMIT];
            write(b | constants[EncodingConstants.INTEGER_MEDIUM_FLAG] | (i >> 8));
            write(i & 0xFF);
        } else if (i < constants[EncodingConstants.INTEGER_LARGE_LIMIT]) {
            i -= constants[EncodingConstants.INTEGER_MEDIUM_LIMIT];
            write(b | constants[EncodingConstants.INTEGER_LARGE_FLAG] | (i >> 16));
            write((i >> 8) & 0xFF);
            write(i & 0xFF);
        } else if (i < EncodingConstants.INTEGER_MAXIMUM_SIZE) {
            i -= constants[EncodingConstants.INTEGER_LARGE_LIMIT];
            write(b | constants[EncodingConstants.INTEGER_LARGE_LARGE_FLAG]);
            write(i >> 16);
            write((i >> 8) & 0xFF);
            write(i & 0xFF);
        } else {
            throw new IOException(CommonResourceBundle.getInstance().getString("message.integerMaxSize", new Object[]{Integer.valueOf(EncodingConstants.INTEGER_MAXIMUM_SIZE)}));
        }
    }

    /**
     * Mark the current position in the buffered stream.
     */
    protected final void mark() {
        _markIndex = _octetBufferIndex;
    }

    /**
     * Reset the marked position in the buffered stream.
     */
    protected final void resetMark() {
        _markIndex = -1;
    }

    /**
     * @return true if the mark has been set, otherwise false if the mark
     *         has not been set.
     */
    protected final boolean hasMark() {
        return _markIndex != -1;
    }

    /**
     * Write a byte to the buffered stream.
     */
    protected final void write(int i) throws IOException {
        if (_octetBufferIndex < _octetBuffer.length) {
            _octetBuffer[_octetBufferIndex++] = (byte)i;
        } else {
            if (_markIndex == -1) {
                _s.write(_octetBuffer);
                _octetBufferIndex = 1;
                _octetBuffer[0] = (byte)i;
            } else {
                resize(_octetBuffer.length * 3 / 2);
                _octetBuffer[_octetBufferIndex++] = (byte)i;
            }
        }
    }

    /**
     * Write an array of bytes to the buffered stream.
     *
     * @param b the array of bytes.
     * @param length the length of bytes.
     */
    protected final void write(byte[] b, int length) throws IOException {
        write(b, 0,  length);
    }

    /**
     * Write an array of bytes to the buffered stream.
     *
     * @param b the array of bytes.
     * @param offset the offset into the array of bytes.
     * @param length the length of bytes.
     */
    protected final void write(byte[] b, int offset, int length) throws IOException {
        if ((_octetBufferIndex + length) < _octetBuffer.length) {
            System.arraycopy(b, offset, _octetBuffer, _octetBufferIndex, length);
            _octetBufferIndex += length;
        } else {
            if (_markIndex == -1) {
                _s.write(_octetBuffer, 0, _octetBufferIndex);
                _s.write(b, offset, length);
                _octetBufferIndex = 0;
            } else {
                resize((_octetBuffer.length + length) * 3 / 2 + 1);
                System.arraycopy(b, offset, _octetBuffer, _octetBufferIndex, length);
                _octetBufferIndex += length;
            }
        }
    }

    private void ensureSize(int length) {
        if ((_octetBufferIndex + length) > _octetBuffer.length) {
            resize((_octetBufferIndex + length) * 3 / 2 + 1);
        }
    }

    private void resize(int length) {
        byte[] b = new byte[length];
        System.arraycopy(_octetBuffer, 0, b, 0, _octetBufferIndex);
        _octetBuffer = b;
    }

    private void _flush() throws IOException {
        if (_octetBufferIndex > 0) {
            _s.write(_octetBuffer, 0, _octetBufferIndex);
            _octetBufferIndex = 0;
        }
    }


    private EncodingBufferOutputStream _encodingBufferOutputStream = new EncodingBufferOutputStream();

    private byte[] _encodingBuffer = new byte[512];

    private int _encodingBufferIndex;

    private class EncodingBufferOutputStream extends OutputStream {

        public void write(int b) throws IOException {
            if (_encodingBufferIndex < _encodingBuffer.length) {
                _encodingBuffer[_encodingBufferIndex++] = (byte)b;
            } else {
                byte newbuf[] = new byte[Math.max(_encodingBuffer.length << 1, _encodingBufferIndex)];
                System.arraycopy(_encodingBuffer, 0, newbuf, 0, _encodingBufferIndex);
                _encodingBuffer = newbuf;

                _encodingBuffer[_encodingBufferIndex++] = (byte)b;
            }
        }

        public void write(byte b[], int off, int len) throws IOException {
            if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) > b.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return;
            }
            final int newoffset = _encodingBufferIndex + len;
            if (newoffset > _encodingBuffer.length) {
                byte newbuf[] = new byte[Math.max(_encodingBuffer.length << 1, newoffset)];
                System.arraycopy(_encodingBuffer, 0, newbuf, 0, _encodingBufferIndex);
                _encodingBuffer = newbuf;
            }
            System.arraycopy(b, off, _encodingBuffer, _encodingBufferIndex, len);
            _encodingBufferIndex = newoffset;
        }

        public int getLength() {
            return _encodingBufferIndex;
        }

        public void reset() {
            _encodingBufferIndex = 0;
        }
    }

    /**
     * Encode a string using the UTF-8 encoding.
     *
     * @param s the string to encode.
     */
    protected final int encodeUTF8String(String s) throws IOException {
        final int length = s.length();
        if (length < _charBuffer.length) {
            s.getChars(0, length, _charBuffer, 0);
            return encodeUTF8String(_charBuffer, 0, length);
        } else {
            char[] ch = s.toCharArray();
            return encodeUTF8String(ch, 0, length);
        }
    }

    private void ensureEncodingBufferSizeForUtf8String(int length) {
        final int newLength = 4 * length;
        if (_encodingBuffer.length < newLength) {
            _encodingBuffer = new byte[newLength];
        }
    }

    /**
     * Encode a string using the UTF-8 encoding.
     *
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     */
    protected final int encodeUTF8String(char[] ch, int offset, int length) throws IOException {
        int bpos = 0;

        // Make sure buffer is large enough
        ensureEncodingBufferSizeForUtf8String(length);

        final int end = offset + length;
        int c;
        while (end != offset) {
            c = ch[offset++];
            if (c < 0x80) {
                // 1 byte, 7 bits
                _encodingBuffer[bpos++] = (byte) c;
            } else if (c < 0x800) {
                // 2 bytes, 11 bits
                _encodingBuffer[bpos++] =
                    (byte) (0xC0 | (c >> 6));    // first 5
                _encodingBuffer[bpos++] =
                    (byte) (0x80 | (c & 0x3F));  // second 6
            } else if (c <= '\uFFFF') {
                if (!XMLChar.isHighSurrogate(c) && !XMLChar.isLowSurrogate(c)) {
                    // 3 bytes, 16 bits
                    _encodingBuffer[bpos++] =
                        (byte) (0xE0 | (c >> 12));   // first 4
                    _encodingBuffer[bpos++] =
                        (byte) (0x80 | ((c >> 6) & 0x3F));  // second 6
                    _encodingBuffer[bpos++] =
                        (byte) (0x80 | (c & 0x3F));  // third 6
                } else {
                    // 4 bytes, high and low surrogate
                    encodeCharacterAsUtf8FourByte(c, ch, offset, end, bpos);
                    bpos += 4;
                    offset++;
                }
            }
        }

        return bpos;
    }

    private void encodeCharacterAsUtf8FourByte(int c, char[] ch, int chpos, int chend, int bpos) throws IOException {
        if (chpos == chend) {
            throw new IOException("");
        }

        final char d = ch[chpos];
        if (!XMLChar.isLowSurrogate(d)) {
            throw new IOException("");
        }

        final int uc = (((c & 0x3ff) << 10) | (d & 0x3ff)) + 0x10000;
        if (uc < 0 || uc >= 0x200000) {
            throw new IOException("");
        }

        _encodingBuffer[bpos++] = (byte)(0xF0 | ((uc >> 18)));
        _encodingBuffer[bpos++] = (byte)(0x80 | ((uc >> 12) & 0x3F));
        _encodingBuffer[bpos++] = (byte)(0x80 | ((uc >> 6) & 0x3F));
        _encodingBuffer[bpos++] = (byte)(0x80 | (uc & 0x3F));
    }

    /**
     * Encode a string using the UTF-16 encoding.
     *
     * @param s the string to encode.
     */
    protected final int encodeUtf16String(String s) throws IOException {
        final int length = s.length();
        if (length < _charBuffer.length) {
            s.getChars(0, length, _charBuffer, 0);
            return encodeUtf16String(_charBuffer, 0, length);
        } else {
            char[] ch = s.toCharArray();
            return encodeUtf16String(ch, 0, length);
        }
    }

    private void ensureEncodingBufferSizeForUtf16String(int length) {
        final int newLength = 2 * length;
        if (_encodingBuffer.length < newLength) {
            _encodingBuffer = new byte[newLength];
        }
    }

    /**
     * Encode a string using the UTF-16 encoding.
     *
     * @param ch the array of characters.
     * @param offset the offset into the array of characters.
     * @param length the length of characters.
     */
    protected final int encodeUtf16String(char[] ch, int offset, int length) throws IOException {
        int byteLength = 0;

        // Make sure buffer is large enough
        ensureEncodingBufferSizeForUtf16String(length);

        final int n = offset + length;
        for (int i = offset; i < n; i++) {
            final int c = (int) ch[i];
            _encodingBuffer[byteLength++] = (byte)(c >> 8);
            _encodingBuffer[byteLength++] = (byte)(c & 0xFF);
        }

        return byteLength;
    }

    /**
     * Obtain the prefix from a qualified name.
     *
     * @param qName the qualified name
     * @return the prefix, or "" if there is no prefix.
     */
    public static String getPrefixFromQualifiedName(String qName) {
        int i = qName.indexOf(':');
        String prefix = "";
        if (i != -1) {
            prefix = qName.substring(0, i);
        }
        return prefix;
    }

    /**
     * Check if character array contains characters that are all white space.
     *
     * @param ch the character array
     * @param start the starting character index into the array to check from
     * @param length the number of characters to check
     * @return true if all characters are white space, false otherwise
     */
    public static boolean isWhiteSpace(final char[] ch, int start, final int length) {
        if (!XMLChar.isSpace(ch[start])) return false;

        final int end = start + length;
        while(++start < end && XMLChar.isSpace(ch[start]));

        return start == end;
    }

    /**
     * Check if a String contains characters that are all white space.
     *
     * @param s the string
     * @return true if all characters are white space, false otherwise
     */
    public static boolean isWhiteSpace(String s) {
        if (!XMLChar.isSpace(s.charAt(0))) return false;

        final int end = s.length();
        int start = 1;
        while(start < end && XMLChar.isSpace(s.charAt(start++)));
        return start == end;
    }
}
