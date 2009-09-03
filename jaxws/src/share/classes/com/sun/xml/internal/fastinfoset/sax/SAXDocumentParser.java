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

package com.sun.xml.internal.fastinfoset.sax;

import com.sun.xml.internal.fastinfoset.Decoder;
import com.sun.xml.internal.fastinfoset.DecoderStateTables;
import com.sun.xml.internal.fastinfoset.EncodingConstants;
import com.sun.xml.internal.fastinfoset.QualifiedName;
import com.sun.xml.internal.fastinfoset.algorithm.BuiltInEncodingAlgorithmFactory;
import com.sun.xml.internal.fastinfoset.algorithm.BuiltInEncodingAlgorithmState;
import com.sun.xml.internal.org.jvnet.fastinfoset.sax.EncodingAlgorithmContentHandler;
import com.sun.xml.internal.org.jvnet.fastinfoset.sax.FastInfosetReader;
import com.sun.xml.internal.org.jvnet.fastinfoset.sax.PrimitiveTypeContentHandler;
import com.sun.xml.internal.fastinfoset.util.CharArray;
import com.sun.xml.internal.fastinfoset.util.CharArrayString;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithm;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmException;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmIndexes;
import com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetException;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;
import org.xml.sax.ext.DeclHandler;

/**
 * The Fast Infoset SAX parser.
 * <p>
 * Instantiate this parser to parse a fast infoset document in accordance
 * with the SAX API.
 *
 * <p>
 * More than one fast infoset document may be decoded from the
 * {@link java.io.InputStream}.
 */
public class SAXDocumentParser extends Decoder implements FastInfosetReader {

    /*
     * Empty lexical handler used by default to report
     * lexical-based events
     */
    private static final class LexicalHandlerImpl implements LexicalHandler {
        public void comment(char[] ch, int start, int end) { }

        public void startDTD(String name, String publicId, String systemId) { }
        public void endDTD() { }

        public void startEntity(String name) { }
        public void endEntity(String name) { }

        public void startCDATA() { }
        public void endCDATA() { }
    };

    /*
     * Empty DTD declaration handler used by default to report
     * DTD declaration-based events
     */
    private static final class DeclHandlerImpl implements DeclHandler {
        public void elementDecl(String name, String model) throws SAXException {
        }

        public void attributeDecl(String eName, String aName,
                String type, String mode, String value) throws SAXException {
        }

        public void internalEntityDecl(String name,
                String value) throws SAXException {
        }

        public void externalEntityDecl(String name,
                String publicId, String systemId) throws SAXException {
        }
    }

    /**
     * SAX Namespace attributes features
     */
    protected boolean _namespacePrefixesFeature = false;

    /**
     * Reference to entity resolver.
     */
    protected EntityResolver _entityResolver;

    /**
     * Reference to dtd handler.
     */
    protected DTDHandler _dtdHandler;

    /**
     * Reference to content handler.
     */
    protected ContentHandler _contentHandler;

    /**
     * Reference to error handler.
     */
    protected ErrorHandler _errorHandler;

    /**
     * Reference to lexical handler.
     */
    protected LexicalHandler _lexicalHandler;

    /**
     * Reference to DTD declaration handler.
     */
    protected DeclHandler _declHandler;

    protected EncodingAlgorithmContentHandler _algorithmHandler;

    protected PrimitiveTypeContentHandler _primitiveHandler;

    protected BuiltInEncodingAlgorithmState builtInAlgorithmState =
            new BuiltInEncodingAlgorithmState();

    protected AttributesHolder _attributes;

    protected int[] _namespacePrefixes = new int[16];

    protected int _namespacePrefixesIndex;

    protected boolean _clearAttributes = false;

    /** Creates a new instance of DocumetParser2 */
    public SAXDocumentParser() {
        DefaultHandler handler = new DefaultHandler();
        _attributes = new AttributesHolder(_registeredEncodingAlgorithms);

        _entityResolver = handler;
        _dtdHandler = handler;
        _contentHandler = handler;
        _errorHandler = handler;
        _lexicalHandler = new LexicalHandlerImpl();
        _declHandler = new DeclHandlerImpl();
    }

    protected void resetOnError() {
        _clearAttributes = false;
        _attributes.clear();
        _namespacePrefixesIndex = 0;

        if (_v != null) {
            _v.prefix.clearCompletely();
        }
        _duplicateAttributeVerifier.clear();
    }

    // XMLReader interface

    public boolean getFeature(String name)
    throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name.equals(Features.NAMESPACES_FEATURE)) {
            return true;
        } else if (name.equals(Features.NAMESPACE_PREFIXES_FEATURE)) {
            return _namespacePrefixesFeature;
        } else if (name.equals(Features.STRING_INTERNING_FEATURE) ||
                name.equals(FastInfosetReader.STRING_INTERNING_PROPERTY)) {
            return getStringInterning();
        } else {
            throw new SAXNotRecognizedException(
                    CommonResourceBundle.getInstance().getString("message.featureNotSupported") + name);
        }
    }

    public void setFeature(String name, boolean value)
    throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name.equals(Features.NAMESPACES_FEATURE)) {
            if (value == false) {
                throw new SAXNotSupportedException(name + ":" + value);
            }
        } else if (name.equals(Features.NAMESPACE_PREFIXES_FEATURE)) {
            _namespacePrefixesFeature = value;
        } else if (name.equals(Features.STRING_INTERNING_FEATURE) ||
                name.equals(FastInfosetReader.STRING_INTERNING_PROPERTY)) {
            setStringInterning(value);
        } else {
            throw new SAXNotRecognizedException(
                    CommonResourceBundle.getInstance().getString("message.featureNotSupported") + name);
        }
    }

    public Object getProperty(String name)
    throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name.equals(Properties.LEXICAL_HANDLER_PROPERTY)) {
            return getLexicalHandler();
        } else if (name.equals(Properties.DTD_DECLARATION_HANDLER_PROPERTY)) {
            return getDeclHandler();
        } else if (name.equals(FastInfosetReader.EXTERNAL_VOCABULARIES_PROPERTY)) {
            return getExternalVocabularies();
        } else if (name.equals(FastInfosetReader.REGISTERED_ENCODING_ALGORITHMS_PROPERTY)) {
            return getRegisteredEncodingAlgorithms();
        } else if (name.equals(FastInfosetReader.ENCODING_ALGORITHM_CONTENT_HANDLER_PROPERTY)) {
            return getEncodingAlgorithmContentHandler();
        } else if (name.equals(FastInfosetReader.PRIMITIVE_TYPE_CONTENT_HANDLER_PROPERTY)) {
            return getPrimitiveTypeContentHandler();
        } else {
            throw new SAXNotRecognizedException(CommonResourceBundle.getInstance().
                    getString("message.propertyNotRecognized", new Object[]{name}));
        }
    }

    public void setProperty(String name, Object value)
    throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name.equals(Properties.LEXICAL_HANDLER_PROPERTY)) {
            if (value instanceof LexicalHandler) {
                setLexicalHandler((LexicalHandler)value);
            } else {
                throw new SAXNotSupportedException(Properties.LEXICAL_HANDLER_PROPERTY);
            }
        } else if (name.equals(Properties.DTD_DECLARATION_HANDLER_PROPERTY)) {
            if (value instanceof DeclHandler) {
                setDeclHandler((DeclHandler)value);
            } else {
                throw new SAXNotSupportedException(Properties.LEXICAL_HANDLER_PROPERTY);
            }
        } else if (name.equals(FastInfosetReader.EXTERNAL_VOCABULARIES_PROPERTY)) {
            if (value instanceof Map) {
                setExternalVocabularies((Map)value);
            } else {
                throw new SAXNotSupportedException(FastInfosetReader.EXTERNAL_VOCABULARIES_PROPERTY);
            }
        } else if (name.equals(FastInfosetReader.REGISTERED_ENCODING_ALGORITHMS_PROPERTY)) {
            if (value instanceof Map) {
                setRegisteredEncodingAlgorithms((Map)value);
            } else {
                throw new SAXNotSupportedException(FastInfosetReader.REGISTERED_ENCODING_ALGORITHMS_PROPERTY);
            }
        } else if (name.equals(FastInfosetReader.ENCODING_ALGORITHM_CONTENT_HANDLER_PROPERTY)) {
            if (value instanceof EncodingAlgorithmContentHandler) {
                setEncodingAlgorithmContentHandler((EncodingAlgorithmContentHandler)value);
            } else {
                throw new SAXNotSupportedException(FastInfosetReader.ENCODING_ALGORITHM_CONTENT_HANDLER_PROPERTY);
            }
        } else if (name.equals(FastInfosetReader.PRIMITIVE_TYPE_CONTENT_HANDLER_PROPERTY)) {
            if (value instanceof PrimitiveTypeContentHandler) {
                setPrimitiveTypeContentHandler((PrimitiveTypeContentHandler)value);
            } else {
                throw new SAXNotSupportedException(FastInfosetReader.PRIMITIVE_TYPE_CONTENT_HANDLER_PROPERTY);
            }
        } else if (name.equals(FastInfosetReader.BUFFER_SIZE_PROPERTY)) {
            if (value instanceof Integer) {
                setBufferSize(((Integer)value).intValue());
            } else {
                throw new SAXNotSupportedException(FastInfosetReader.BUFFER_SIZE_PROPERTY);
            }
        } else {
            throw new SAXNotRecognizedException(CommonResourceBundle.getInstance().
                    getString("message.propertyNotRecognized", new Object[]{name}));
        }
    }

    public void setEntityResolver(EntityResolver resolver) {
        _entityResolver = resolver;
    }

    public EntityResolver getEntityResolver() {
        return _entityResolver;
    }

    public void setDTDHandler(DTDHandler handler) {
        _dtdHandler = handler;
    }

    public DTDHandler getDTDHandler() {
        return _dtdHandler;
    }
    public void setContentHandler(ContentHandler handler) {
        _contentHandler = handler;
    }

    public ContentHandler getContentHandler() {
        return _contentHandler;
    }

    public void setErrorHandler(ErrorHandler handler) {
        _errorHandler = handler;
    }

    public ErrorHandler getErrorHandler() {
        return _errorHandler;
    }

    public void parse(InputSource input) throws IOException, SAXException {
        try {
            InputStream s = input.getByteStream();
            if (s == null) {
                String systemId = input.getSystemId();
                if (systemId == null) {
                    throw new SAXException(CommonResourceBundle.getInstance().getString("message.inputSource"));
                }
                parse(systemId);
            } else {
                parse(s);
            }
        } catch (FastInfosetException e) {
            e.printStackTrace();
            throw new SAXException(e);
        }
    }

    public void parse(String systemId) throws IOException, SAXException {
        try {
            systemId = SystemIdResolver.getAbsoluteURI(systemId);
            parse(new URL(systemId).openStream());
        } catch (FastInfosetException e) {
            e.printStackTrace();
            throw new SAXException(e);
        }
    }




    // FastInfosetReader

    public final void parse(InputStream s) throws IOException, FastInfosetException, SAXException {
        setInputStream(s);
        parse();
    }

    public void setLexicalHandler(LexicalHandler handler) {
        _lexicalHandler = handler;
    }

    public LexicalHandler getLexicalHandler() {
        return _lexicalHandler;
    }

    public void setDeclHandler(DeclHandler handler) {
        _declHandler = handler;
    }

    public DeclHandler getDeclHandler() {
        return _declHandler;
    }

    public void setEncodingAlgorithmContentHandler(EncodingAlgorithmContentHandler handler) {
        _algorithmHandler = handler;
    }

    public EncodingAlgorithmContentHandler getEncodingAlgorithmContentHandler() {
        return _algorithmHandler;
    }

    public void setPrimitiveTypeContentHandler(PrimitiveTypeContentHandler handler) {
        _primitiveHandler = handler;
    }

    public PrimitiveTypeContentHandler getPrimitiveTypeContentHandler() {
        return _primitiveHandler;
    }




    public final void parse() throws FastInfosetException, IOException {
        if (_octetBuffer.length < _bufferSize) {
            _octetBuffer = new byte[_bufferSize];
        }

        try {
            reset();
            decodeHeader();
            if (_parseFragments)
                processDIIFragment();
            else
                processDII();
        } catch (RuntimeException e) {
            try {
                _errorHandler.fatalError(new SAXParseException(e.getClass().getName(), null, e));
            } catch (Exception ee) {
            }
            resetOnError();
            // Wrap runtime exception
            throw new FastInfosetException(e);
        } catch (FastInfosetException e) {
            try {
                _errorHandler.fatalError(new SAXParseException(e.getClass().getName(), null, e));
            } catch (Exception ee) {
            }
            resetOnError();
            throw e;
        } catch (IOException e) {
            try {
                _errorHandler.fatalError(new SAXParseException(e.getClass().getName(), null, e));
            } catch (Exception ee) {
            }
            resetOnError();
            throw e;
        }
    }

    protected final void processDII() throws FastInfosetException, IOException {
        try {
            _contentHandler.startDocument();
        } catch (SAXException e) {
            throw new FastInfosetException("processDII", e);
        }

        _b = read();
        if (_b > 0) {
            processDIIOptionalProperties();
        }

        // Decode one Document Type II, Comment IIs, PI IIs and one EII
        boolean firstElementHasOccured = false;
        boolean documentTypeDeclarationOccured = false;
        while(!_terminate || !firstElementHasOccured) {
            _b = read();
            switch(DecoderStateTables.DII(_b)) {
                case DecoderStateTables.EII_NO_AIIS_INDEX_SMALL:
                    processEII(_elementNameTable._array[_b], false);
                    firstElementHasOccured = true;
                    break;
                case DecoderStateTables.EII_AIIS_INDEX_SMALL:
                    processEII(_elementNameTable._array[_b & EncodingConstants.INTEGER_3RD_BIT_SMALL_MASK], true);
                    firstElementHasOccured = true;
                    break;
                case DecoderStateTables.EII_INDEX_MEDIUM:
                    processEII(decodeEIIIndexMedium(), (_b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0);
                    firstElementHasOccured = true;
                    break;
                case DecoderStateTables.EII_INDEX_LARGE:
                    processEII(decodeEIIIndexLarge(), (_b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0);
                    firstElementHasOccured = true;
                    break;
                case DecoderStateTables.EII_LITERAL:
                {
                    final QualifiedName qn = decodeLiteralQualifiedName(
                            _b & EncodingConstants.LITERAL_QNAME_PREFIX_NAMESPACE_NAME_MASK,
                            _elementNameTable.getNext());
                    _elementNameTable.add(qn);
                    processEII(qn, (_b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0);
                    firstElementHasOccured = true;
                    break;
                }
                case DecoderStateTables.EII_NAMESPACES:
                    processEIIWithNamespaces();
                    firstElementHasOccured = true;
                    break;
                case DecoderStateTables.DOCUMENT_TYPE_DECLARATION_II:
                {
                    if (documentTypeDeclarationOccured) {
                        throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.secondOccurenceOfDTDII"));
                    }
                    documentTypeDeclarationOccured = true;

                    String system_identifier = ((_b & EncodingConstants.DOCUMENT_TYPE_SYSTEM_IDENTIFIER_FLAG) > 0)
                    ? decodeIdentifyingNonEmptyStringOnFirstBit(_v.otherURI) : "";
                    String public_identifier = ((_b & EncodingConstants.DOCUMENT_TYPE_PUBLIC_IDENTIFIER_FLAG) > 0)
                    ? decodeIdentifyingNonEmptyStringOnFirstBit(_v.otherURI) : "";

                    _b = read();
                    while (_b == EncodingConstants.PROCESSING_INSTRUCTION) {
                        switch(decodeNonIdentifyingStringOnFirstBit()) {
                            case NISTRING_STRING:
                                if (_addToTable) {
                                    _v.otherString.add(new CharArray(_charBuffer, 0, _charBufferLength, true));
                                }
                                break;
                            case NISTRING_ENCODING_ALGORITHM:
                                throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.processingIIWithEncodingAlgorithm"));
                            case NISTRING_INDEX:
                                break;
                            case NISTRING_EMPTY_STRING:
                                break;
                        }
                        _b = read();
                    }
                    if ((_b & EncodingConstants.TERMINATOR) != EncodingConstants.TERMINATOR) {
                        throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.processingInstructionIIsNotTerminatedCorrectly"));
                    }
                    if (_b == EncodingConstants.DOUBLE_TERMINATOR) {
                        _terminate = true;
                    }

                    if (_notations != null) _notations.clear();
                    if (_unparsedEntities != null) _unparsedEntities.clear();
                    /*
                     * TODO
                     * Report All events associated with DTD, PIs, notations etc
                     */
                    break;
                }
                case DecoderStateTables.COMMENT_II:
                    processCommentII();
                    break;
                case DecoderStateTables.PROCESSING_INSTRUCTION_II:
                    processProcessingII();
                    break;
                case DecoderStateTables.TERMINATOR_DOUBLE:
                    _doubleTerminate = true;
                case DecoderStateTables.TERMINATOR_SINGLE:
                    _terminate = true;
                    break;
                default:
                    throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.IllegalStateDecodingDII"));
            }
        }

        // Decode any remaining Comment IIs, PI IIs
        while(!_terminate) {
            _b = read();
            switch(DecoderStateTables.DII(_b)) {
                case DecoderStateTables.COMMENT_II:
                    processCommentII();
                    break;
                case DecoderStateTables.PROCESSING_INSTRUCTION_II:
                    processProcessingII();
                    break;
                case DecoderStateTables.TERMINATOR_DOUBLE:
                    _doubleTerminate = true;
                case DecoderStateTables.TERMINATOR_SINGLE:
                    _terminate = true;
                    break;
                default:
                    throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.IllegalStateDecodingDII"));
            }
        }

        try {
            _contentHandler.endDocument();
        } catch (SAXException e) {
            throw new FastInfosetException("processDII", e);
        }
    }

    protected final void processDIIFragment() throws FastInfosetException, IOException {
        try {
            _contentHandler.startDocument();
        } catch (SAXException e) {
            throw new FastInfosetException("processDII", e);
        }

        _b = read();
        if (_b > 0) {
            processDIIOptionalProperties();
        }

        while(!_terminate) {
            _b = read();
            switch(DecoderStateTables.EII(_b)) {
                case DecoderStateTables.EII_NO_AIIS_INDEX_SMALL:
                    processEII(_elementNameTable._array[_b], false);
                    break;
                case DecoderStateTables.EII_AIIS_INDEX_SMALL:
                    processEII(_elementNameTable._array[_b & EncodingConstants.INTEGER_3RD_BIT_SMALL_MASK], true);
                    break;
                case DecoderStateTables.EII_INDEX_MEDIUM:
                    processEII(decodeEIIIndexMedium(), (_b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0);
                    break;
                case DecoderStateTables.EII_INDEX_LARGE:
                    processEII(decodeEIIIndexLarge(), (_b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0);
                    break;
                case DecoderStateTables.EII_LITERAL:
                {
                    final QualifiedName qn = decodeLiteralQualifiedName(
                            _b & EncodingConstants.LITERAL_QNAME_PREFIX_NAMESPACE_NAME_MASK,
                            _elementNameTable.getNext());
                    _elementNameTable.add(qn);
                    processEII(qn, (_b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0);
                    break;
                }
                case DecoderStateTables.EII_NAMESPACES:
                    processEIIWithNamespaces();
                    break;
                case DecoderStateTables.CII_UTF8_SMALL_LENGTH:
                    _octetBufferLength = (_b & EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_MASK)
                    + 1;
                    processUtf8CharacterString();
                    break;
                case DecoderStateTables.CII_UTF8_MEDIUM_LENGTH:
                    _octetBufferLength = read() + EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_LIMIT;
                    processUtf8CharacterString();
                    break;
                case DecoderStateTables.CII_UTF8_LARGE_LENGTH:
                    _octetBufferLength = ((read() << 24) |
                            (read() << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_MEDIUM_LIMIT;
                    processUtf8CharacterString();
                    break;
                case DecoderStateTables.CII_UTF16_SMALL_LENGTH:
                    _octetBufferLength = (_b & EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_MASK)
                    + 1;
                    decodeUtf16StringAsCharBuffer();
                    if ((_b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0) {
                        _characterContentChunkTable.add(_charBuffer, _charBufferLength);
                    }

                    try {
                        _contentHandler.characters(_charBuffer, 0, _charBufferLength);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                case DecoderStateTables.CII_UTF16_MEDIUM_LENGTH:
                    _octetBufferLength = read() + EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_LIMIT;
                    decodeUtf16StringAsCharBuffer();
                    if ((_b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0) {
                        _characterContentChunkTable.add(_charBuffer, _charBufferLength);
                    }

                    try {
                        _contentHandler.characters(_charBuffer, 0, _charBufferLength);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                case DecoderStateTables.CII_UTF16_LARGE_LENGTH:
                    _octetBufferLength = ((read() << 24) |
                            (read() << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_MEDIUM_LIMIT;
                    decodeUtf16StringAsCharBuffer();
                    if ((_b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0) {
                        _characterContentChunkTable.add(_charBuffer, _charBufferLength);
                    }

                    try {
                        _contentHandler.characters(_charBuffer, 0, _charBufferLength);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                case DecoderStateTables.CII_RA:
                {
                    final boolean addToTable = (_b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0;

                    // Decode resitricted alphabet integer
                    _identifier = (_b & 0x02) << 6;
                    _b = read();
                    _identifier |= (_b & 0xFC) >> 2;

                    decodeOctetsOnSeventhBitOfNonIdentifyingStringOnThirdBit(_b);

                    decodeRestrictedAlphabetAsCharBuffer();

                    if (addToTable) {
                        _characterContentChunkTable.add(_charBuffer, _charBufferLength);
                    }

                    try {
                        _contentHandler.characters(_charBuffer, 0, _charBufferLength);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                }
                case DecoderStateTables.CII_EA:
                {
                    final boolean addToTable = (_b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0;

                    // Decode encoding algorithm integer
                    _identifier = (_b & 0x02) << 6;
                    _b = read();
                    _identifier |= (_b & 0xFC) >> 2;

                    decodeOctetsOnSeventhBitOfNonIdentifyingStringOnThirdBit(_b);

                    processCIIEncodingAlgorithm(addToTable);
                    break;
                }
                case DecoderStateTables.CII_INDEX_SMALL:
                {
                    final int index = _b & EncodingConstants.INTEGER_4TH_BIT_SMALL_MASK;
                    try {
                        _contentHandler.characters(_characterContentChunkTable._array,
                                _characterContentChunkTable._offset[index],
                                _characterContentChunkTable._length[index]);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                }
                case DecoderStateTables.CII_INDEX_MEDIUM:
                {
                    final int index = (((_b & EncodingConstants.INTEGER_4TH_BIT_MEDIUM_MASK) << 8) | read())
                    + EncodingConstants.INTEGER_4TH_BIT_SMALL_LIMIT;
                    try {
                        _contentHandler.characters(_characterContentChunkTable._array,
                                _characterContentChunkTable._offset[index],
                                _characterContentChunkTable._length[index]);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                }
                case DecoderStateTables.CII_INDEX_LARGE:
                {
                    final int index = (((_b & EncodingConstants.INTEGER_4TH_BIT_LARGE_MASK) << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.INTEGER_4TH_BIT_MEDIUM_LIMIT;

                    try {
                        _contentHandler.characters(_characterContentChunkTable._array,
                                _characterContentChunkTable._offset[index],
                                _characterContentChunkTable._length[index]);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                }
                case DecoderStateTables.CII_INDEX_LARGE_LARGE:
                {
                    final int index = ((read() << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.INTEGER_4TH_BIT_LARGE_LIMIT;

                    try {
                        _contentHandler.characters(_characterContentChunkTable._array,
                                _characterContentChunkTable._offset[index],
                                _characterContentChunkTable._length[index]);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                }
                case DecoderStateTables.COMMENT_II:
                    processCommentII();
                    break;
                case DecoderStateTables.PROCESSING_INSTRUCTION_II:
                    processProcessingII();
                    break;
                case DecoderStateTables.UNEXPANDED_ENTITY_REFERENCE_II:
                {
                    String entity_reference_name = decodeIdentifyingNonEmptyStringOnFirstBit(_v.otherNCName);

                    String system_identifier = ((_b & EncodingConstants.UNEXPANDED_ENTITY_SYSTEM_IDENTIFIER_FLAG) > 0)
                    ? decodeIdentifyingNonEmptyStringOnFirstBit(_v.otherURI) : "";
                    String public_identifier = ((_b & EncodingConstants.UNEXPANDED_ENTITY_PUBLIC_IDENTIFIER_FLAG) > 0)
                    ? decodeIdentifyingNonEmptyStringOnFirstBit(_v.otherURI) : "";

                    try {
                        /*
                         * TODO
                         * Need to verify if the skippedEntity method:
                         * http://java.sun.com/j2se/1.4.2/docs/api/org/xml/sax/ContentHandler.html#skippedEntity(java.lang.String)
                         * is the correct method to call. It appears so but a more extensive
                         * check is necessary.
                         */
                        _contentHandler.skippedEntity(entity_reference_name);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processUnexpandedEntityReferenceII", e);
                    }
                    break;
                }
                case DecoderStateTables.TERMINATOR_DOUBLE:
                    _doubleTerminate = true;
                case DecoderStateTables.TERMINATOR_SINGLE:
                    _terminate = true;
                    break;
                default:
                    throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.IllegalStateDecodingEII"));
            }
        }

        try {
            _contentHandler.endDocument();
        } catch (SAXException e) {
            throw new FastInfosetException("processDII", e);
        }
    }

    protected final void processDIIOptionalProperties() throws FastInfosetException, IOException {
        // Optimize for the most common case
        if (_b == EncodingConstants.DOCUMENT_INITIAL_VOCABULARY_FLAG) {
            decodeInitialVocabulary();
            return;
        }

        if ((_b & EncodingConstants.DOCUMENT_ADDITIONAL_DATA_FLAG) > 0) {
            decodeAdditionalData();
            /*
             * TODO
             * how to report the additional data?
             */
        }

        if ((_b & EncodingConstants.DOCUMENT_INITIAL_VOCABULARY_FLAG) > 0) {
            decodeInitialVocabulary();
        }

        if ((_b & EncodingConstants.DOCUMENT_NOTATIONS_FLAG) > 0) {
            decodeNotations();
            /*
                try {
                    _dtdHandler.notationDecl(name, public_identifier, system_identifier);
                } catch (SAXException e) {
                    throw new IOException("NotationsDeclarationII");
                }
             */
        }

        if ((_b & EncodingConstants.DOCUMENT_UNPARSED_ENTITIES_FLAG) > 0) {
            decodeUnparsedEntities();
            /*
                try {
                    _dtdHandler.unparsedEntityDecl(name, public_identifier, system_identifier, notation_name);
                } catch (SAXException e) {
                    throw new IOException("UnparsedEntitiesII");
                }
             */
        }

        if ((_b & EncodingConstants.DOCUMENT_CHARACTER_ENCODING_SCHEME) > 0) {
            String characterEncodingScheme = decodeCharacterEncodingScheme();
            /*
             * TODO
             * how to report the character encoding scheme?
             */
        }

        if ((_b & EncodingConstants.DOCUMENT_STANDALONE_FLAG) > 0) {
            boolean standalone = (read() > 0) ? true : false ;
            /*
             * TODO
             * how to report the standalone flag?
             */
        }

        if ((_b & EncodingConstants.DOCUMENT_VERSION_FLAG) > 0) {
            decodeVersion();
            /*
             * TODO
             * how to report the standalone flag?
             */
        }
    }

    protected final void processEII(QualifiedName name, boolean hasAttributes) throws FastInfosetException, IOException {
        if (_prefixTable._currentInScope[name.prefixIndex] != name.namespaceNameIndex) {
            throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.qNameOfEIINotInScope"));
        }

        if (hasAttributes) {
            processAIIs();
        }

        try {
            _contentHandler.startElement(name.namespaceName, name.localName, name.qName, _attributes);
        } catch (SAXException e) {
            e.printStackTrace();
            throw new FastInfosetException("processEII", e);
        }

        if (_clearAttributes) {
            _attributes.clear();
            _clearAttributes = false;
        }

        while(!_terminate) {
            _b = read();
            switch(DecoderStateTables.EII(_b)) {
                case DecoderStateTables.EII_NO_AIIS_INDEX_SMALL:
                    processEII(_elementNameTable._array[_b], false);
                    break;
                case DecoderStateTables.EII_AIIS_INDEX_SMALL:
                    processEII(_elementNameTable._array[_b & EncodingConstants.INTEGER_3RD_BIT_SMALL_MASK], true);
                    break;
                case DecoderStateTables.EII_INDEX_MEDIUM:
                    processEII(decodeEIIIndexMedium(), (_b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0);
                    break;
                case DecoderStateTables.EII_INDEX_LARGE:
                    processEII(decodeEIIIndexLarge(), (_b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0);
                    break;
                case DecoderStateTables.EII_LITERAL:
                {
                    final QualifiedName qn = decodeLiteralQualifiedName(
                            _b & EncodingConstants.LITERAL_QNAME_PREFIX_NAMESPACE_NAME_MASK,
                            _elementNameTable.getNext());
                    _elementNameTable.add(qn);
                    processEII(qn, (_b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0);
                    break;
                }
                case DecoderStateTables.EII_NAMESPACES:
                    processEIIWithNamespaces();
                    break;
                case DecoderStateTables.CII_UTF8_SMALL_LENGTH:
                    _octetBufferLength = (_b & EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_MASK)
                    + 1;
                    processUtf8CharacterString();
                    break;
                case DecoderStateTables.CII_UTF8_MEDIUM_LENGTH:
                    _octetBufferLength = read() + EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_LIMIT;
                    processUtf8CharacterString();
                    break;
                case DecoderStateTables.CII_UTF8_LARGE_LENGTH:
                    _octetBufferLength = ((read() << 24) |
                            (read() << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_MEDIUM_LIMIT;
                    processUtf8CharacterString();
                    break;
                case DecoderStateTables.CII_UTF16_SMALL_LENGTH:
                    _octetBufferLength = (_b & EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_MASK)
                    + 1;
                    decodeUtf16StringAsCharBuffer();
                    if ((_b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0) {
                        _characterContentChunkTable.add(_charBuffer, _charBufferLength);
                    }

                    try {
                        _contentHandler.characters(_charBuffer, 0, _charBufferLength);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                case DecoderStateTables.CII_UTF16_MEDIUM_LENGTH:
                    _octetBufferLength = read() + EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_LIMIT;
                    decodeUtf16StringAsCharBuffer();
                    if ((_b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0) {
                        _characterContentChunkTable.add(_charBuffer, _charBufferLength);
                    }

                    try {
                        _contentHandler.characters(_charBuffer, 0, _charBufferLength);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                case DecoderStateTables.CII_UTF16_LARGE_LENGTH:
                    _octetBufferLength = ((read() << 24) |
                            (read() << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_MEDIUM_LIMIT;
                    decodeUtf16StringAsCharBuffer();
                    if ((_b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0) {
                        _characterContentChunkTable.add(_charBuffer, _charBufferLength);
                    }

                    try {
                        _contentHandler.characters(_charBuffer, 0, _charBufferLength);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                case DecoderStateTables.CII_RA:
                {
                    final boolean addToTable = (_b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0;

                    // Decode resitricted alphabet integer
                    _identifier = (_b & 0x02) << 6;
                    _b = read();
                    _identifier |= (_b & 0xFC) >> 2;

                    decodeOctetsOnSeventhBitOfNonIdentifyingStringOnThirdBit(_b);

                    decodeRestrictedAlphabetAsCharBuffer();

                    if (addToTable) {
                        _characterContentChunkTable.add(_charBuffer, _charBufferLength);
                    }

                    try {
                        _contentHandler.characters(_charBuffer, 0, _charBufferLength);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                }
                case DecoderStateTables.CII_EA:
                {
                    final boolean addToTable = (_b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0;
                    // Decode encoding algorithm integer
                    _identifier = (_b & 0x02) << 6;
                    _b = read();
                    _identifier |= (_b & 0xFC) >> 2;

                    decodeOctetsOnSeventhBitOfNonIdentifyingStringOnThirdBit(_b);

                    processCIIEncodingAlgorithm(addToTable);
                    break;
                }
                case DecoderStateTables.CII_INDEX_SMALL:
                {
                    final int index = _b & EncodingConstants.INTEGER_4TH_BIT_SMALL_MASK;
                    try {
                        _contentHandler.characters(_characterContentChunkTable._array,
                                _characterContentChunkTable._offset[index],
                                _characterContentChunkTable._length[index]);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                }
                case DecoderStateTables.CII_INDEX_MEDIUM:
                {
                    final int index = (((_b & EncodingConstants.INTEGER_4TH_BIT_MEDIUM_MASK) << 8) | read())
                    + EncodingConstants.INTEGER_4TH_BIT_SMALL_LIMIT;
                    try {
                        _contentHandler.characters(_characterContentChunkTable._array,
                                _characterContentChunkTable._offset[index],
                                _characterContentChunkTable._length[index]);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                }
                case DecoderStateTables.CII_INDEX_LARGE:
                {
                    final int index = (((_b & EncodingConstants.INTEGER_4TH_BIT_LARGE_MASK) << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.INTEGER_4TH_BIT_MEDIUM_LIMIT;

                    try {
                        _contentHandler.characters(_characterContentChunkTable._array,
                                _characterContentChunkTable._offset[index],
                                _characterContentChunkTable._length[index]);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                }
                case DecoderStateTables.CII_INDEX_LARGE_LARGE:
                {
                    final int index = ((read() << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.INTEGER_4TH_BIT_LARGE_LIMIT;

                    try {
                        _contentHandler.characters(_characterContentChunkTable._array,
                                _characterContentChunkTable._offset[index],
                                _characterContentChunkTable._length[index]);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processCII", e);
                    }
                    break;
                }
                case DecoderStateTables.COMMENT_II:
                    processCommentII();
                    break;
                case DecoderStateTables.PROCESSING_INSTRUCTION_II:
                    processProcessingII();
                    break;
                case DecoderStateTables.UNEXPANDED_ENTITY_REFERENCE_II:
                {
                    String entity_reference_name = decodeIdentifyingNonEmptyStringOnFirstBit(_v.otherNCName);

                    String system_identifier = ((_b & EncodingConstants.UNEXPANDED_ENTITY_SYSTEM_IDENTIFIER_FLAG) > 0)
                    ? decodeIdentifyingNonEmptyStringOnFirstBit(_v.otherURI) : "";
                    String public_identifier = ((_b & EncodingConstants.UNEXPANDED_ENTITY_PUBLIC_IDENTIFIER_FLAG) > 0)
                    ? decodeIdentifyingNonEmptyStringOnFirstBit(_v.otherURI) : "";

                    try {
                        /*
                         * TODO
                         * Need to verify if the skippedEntity method:
                         * http://java.sun.com/j2se/1.4.2/docs/api/org/xml/sax/ContentHandler.html#skippedEntity(java.lang.String)
                         * is the correct method to call. It appears so but a more extensive
                         * check is necessary.
                         */
                        _contentHandler.skippedEntity(entity_reference_name);
                    } catch (SAXException e) {
                        throw new FastInfosetException("processUnexpandedEntityReferenceII", e);
                    }
                    break;
                }
                case DecoderStateTables.TERMINATOR_DOUBLE:
                    _doubleTerminate = true;
                case DecoderStateTables.TERMINATOR_SINGLE:
                    _terminate = true;
                    break;
                default:
                    throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.IllegalStateDecodingEII"));
            }
        }

        _terminate = _doubleTerminate;
        _doubleTerminate = false;

        try {
            _contentHandler.endElement(name.namespaceName, name.localName, name.qName);
        } catch (SAXException e) {
            throw new FastInfosetException("processEII", e);
        }
    }

    private final void processUtf8CharacterString() throws FastInfosetException, IOException {
        if ((_b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0) {
            _characterContentChunkTable.ensureSize(_octetBufferLength);
            final int charactersOffset = _characterContentChunkTable._arrayIndex;
            decodeUtf8StringAsCharBuffer(_characterContentChunkTable._array, charactersOffset);
            _characterContentChunkTable.add(_charBufferLength);
            try {
                _contentHandler.characters(_characterContentChunkTable._array, charactersOffset, _charBufferLength);
            } catch (SAXException e) {
                throw new FastInfosetException("processCII", e);
            }
        } else {
            decodeUtf8StringAsCharBuffer();
            try {
                _contentHandler.characters(_charBuffer, 0, _charBufferLength);
            } catch (SAXException e) {
                throw new FastInfosetException("processCII", e);
            }
        }
    }

    protected final void processEIIWithNamespaces() throws FastInfosetException, IOException {
        final boolean hasAttributes = (_b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0;

        _clearAttributes = (_namespacePrefixesFeature) ? true : false;

        if (++_prefixTable._declarationId == Integer.MAX_VALUE) {
            _prefixTable.clearDeclarationIds();
        }

        String prefix = "", namespaceName = "";
        final int start = _namespacePrefixesIndex;
        int b = read();
        while ((b & EncodingConstants.NAMESPACE_ATTRIBUTE_MASK) == EncodingConstants.NAMESPACE_ATTRIBUTE) {
            if (_namespacePrefixesIndex == _namespacePrefixes.length) {
                final int[] namespaceAIIs = new int[_namespacePrefixesIndex * 3 / 2 + 1];
                System.arraycopy(_namespacePrefixes, 0, namespaceAIIs, 0, _namespacePrefixesIndex);
                _namespacePrefixes = namespaceAIIs;
            }

            switch (b & EncodingConstants.NAMESPACE_ATTRIBUTE_PREFIX_NAME_MASK) {
                // no prefix, no namespace
                // Undeclaration of default namespace
                case 0:
                    prefix = namespaceName = "";
                    _namespaceNameIndex = _prefixIndex = _namespacePrefixes[_namespacePrefixesIndex++] = -1;
                    break;
                    // no prefix, namespace
                    // Declaration of default namespace
                case 1:
                    prefix = "";
                    namespaceName = decodeIdentifyingNonEmptyStringOnFirstBitAsNamespaceName(false);

                    _prefixIndex = _namespacePrefixes[_namespacePrefixesIndex++] = -1;
                    break;
                    // prefix, no namespace
                    // Undeclaration of namespace
                case 2:
                    prefix = decodeIdentifyingNonEmptyStringOnFirstBitAsPrefix(false);
                    namespaceName = "";

                    _namespaceNameIndex = -1;
                    _namespacePrefixes[_namespacePrefixesIndex++] = _prefixIndex;
                    break;
                    // prefix, namespace
                    // Declaration of prefixed namespace
                case 3:
                    prefix = decodeIdentifyingNonEmptyStringOnFirstBitAsPrefix(true);
                    namespaceName = decodeIdentifyingNonEmptyStringOnFirstBitAsNamespaceName(true);

                    _namespacePrefixes[_namespacePrefixesIndex++] = _prefixIndex;
                    break;
            }

            _prefixTable.pushScope(_prefixIndex, _namespaceNameIndex);

            if (_namespacePrefixesFeature) {
                // Add the namespace delcaration as an attribute
                if (prefix != "") {
                    _attributes.addAttribute(new QualifiedName(
                            EncodingConstants.XMLNS_NAMESPACE_PREFIX,
                            EncodingConstants.XMLNS_NAMESPACE_NAME,
                            prefix),
                            namespaceName);
                } else {
                    _attributes.addAttribute(EncodingConstants.DEFAULT_NAMESPACE_DECLARATION,
                            namespaceName);
                }
            }

            try {
                _contentHandler.startPrefixMapping(prefix, namespaceName);
            } catch (SAXException e) {
                throw new IOException("processStartNamespaceAII");
            }

            b = read();
        }
        if (b != EncodingConstants.TERMINATOR) {
            throw new IOException(CommonResourceBundle.getInstance().getString("message.EIInamespaceNameNotTerminatedCorrectly"));
        }
        final int end = _namespacePrefixesIndex;

        _b = read();
        switch(DecoderStateTables.EII(_b)) {
            case DecoderStateTables.EII_NO_AIIS_INDEX_SMALL:
                processEII(_elementNameTable._array[_b], hasAttributes);
                break;
            case DecoderStateTables.EII_INDEX_MEDIUM:
                processEII(decodeEIIIndexMedium(), hasAttributes);
                break;
            case DecoderStateTables.EII_INDEX_LARGE:
                processEII(decodeEIIIndexLarge(), hasAttributes);
                break;
            case DecoderStateTables.EII_LITERAL:
            {
                final QualifiedName qn = decodeLiteralQualifiedName(
                        _b & EncodingConstants.LITERAL_QNAME_PREFIX_NAMESPACE_NAME_MASK,
                        _elementNameTable.getNext());
                _elementNameTable.add(qn);
                processEII(qn, hasAttributes);
                break;
            }
            default:
                throw new IOException(CommonResourceBundle.getInstance().getString("message.IllegalStateDecodingEIIAfterAIIs"));
        }

        try {
            for (int i = end - 1; i >= start; i--) {
                final int prefixIndex = _namespacePrefixes[i];
                _prefixTable.popScope(prefixIndex);
                prefix = (prefixIndex > 0) ? _prefixTable.get(prefixIndex - 1) :
                    (prefixIndex == -1) ? "" : EncodingConstants.XML_NAMESPACE_PREFIX;
                _contentHandler.endPrefixMapping(prefix);
            }
            _namespacePrefixesIndex = start;
        } catch (SAXException e) {
            throw new IOException("processStartNamespaceAII");
        }
    }

    protected final void processAIIs() throws FastInfosetException, IOException {
        QualifiedName name;
        int b;
        String value;

        _clearAttributes = true;

        if (++_duplicateAttributeVerifier._currentIteration == Integer.MAX_VALUE) {
            _duplicateAttributeVerifier.clear();
        }

        do {
            // AII qualified name
            b = read();
            switch (DecoderStateTables.AII(b)) {
                case DecoderStateTables.AII_INDEX_SMALL:
                    name = _attributeNameTable._array[b];
                    break;
                case DecoderStateTables.AII_INDEX_MEDIUM:
                {
                    final int i = (((b & EncodingConstants.INTEGER_2ND_BIT_MEDIUM_MASK) << 8) | read())
                    + EncodingConstants.INTEGER_2ND_BIT_SMALL_LIMIT;
                    name = _attributeNameTable._array[i];
                    break;
                }
                case DecoderStateTables.AII_INDEX_LARGE:
                {
                    final int i = (((b & EncodingConstants.INTEGER_2ND_BIT_LARGE_MASK) << 16) | (read() << 8) | read())
                    + EncodingConstants.INTEGER_2ND_BIT_MEDIUM_LIMIT;
                    name = _attributeNameTable._array[i];
                    break;
                }
                case DecoderStateTables.AII_LITERAL:
                    name = decodeLiteralQualifiedName(
                            b & EncodingConstants.LITERAL_QNAME_PREFIX_NAMESPACE_NAME_MASK,
                            _attributeNameTable.getNext());
                    name.createAttributeValues(_duplicateAttributeVerifier.MAP_SIZE);
                    _attributeNameTable.add(name);
                    break;
                case DecoderStateTables.AII_TERMINATOR_DOUBLE:
                    _doubleTerminate = true;
                case DecoderStateTables.AII_TERMINATOR_SINGLE:
                    _terminate = true;
                    // AIIs have finished break out of loop
                    continue;
                default:
                    throw new IOException(CommonResourceBundle.getInstance().getString("message.decodingAIIs"));
            }

            if (name.prefixIndex > 0 && _prefixTable._currentInScope[name.prefixIndex] != name.namespaceNameIndex) {
                throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.AIIqNameNotInScope"));
            }

            _duplicateAttributeVerifier.checkForDuplicateAttribute(name.attributeHash, name.attributeId);

            // [normalized value] of AII

            b = read();
            switch(DecoderStateTables.NISTRING(b)) {
                case DecoderStateTables.NISTRING_UTF8_SMALL_LENGTH:
                    _octetBufferLength = (b & EncodingConstants.OCTET_STRING_LENGTH_5TH_BIT_SMALL_MASK) + 1;
                    value = decodeUtf8StringAsString();
                    if ((b & EncodingConstants.NISTRING_ADD_TO_TABLE_FLAG) > 0) {
                        _attributeValueTable.add(value);
                    }

                    _attributes.addAttribute(name, value);
                    break;
                case DecoderStateTables.NISTRING_UTF8_MEDIUM_LENGTH:
                    _octetBufferLength = read() + EncodingConstants.OCTET_STRING_LENGTH_5TH_BIT_SMALL_LIMIT;
                    value = decodeUtf8StringAsString();
                    if ((b & EncodingConstants.NISTRING_ADD_TO_TABLE_FLAG) > 0) {
                        _attributeValueTable.add(value);
                    }

                    _attributes.addAttribute(name, value);
                    break;
                case DecoderStateTables.NISTRING_UTF8_LARGE_LENGTH:
                    _octetBufferLength = ((read() << 24) |
                            (read() << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.OCTET_STRING_LENGTH_5TH_BIT_MEDIUM_LIMIT;
                    value = decodeUtf8StringAsString();
                    if ((b & EncodingConstants.NISTRING_ADD_TO_TABLE_FLAG) > 0) {
                        _attributeValueTable.add(value);
                    }

                    _attributes.addAttribute(name, value);
                    break;
                case DecoderStateTables.NISTRING_UTF16_SMALL_LENGTH:
                    _octetBufferLength = (b & EncodingConstants.OCTET_STRING_LENGTH_5TH_BIT_SMALL_MASK) + 1;
                    value = decodeUtf16StringAsString();
                    if ((b & EncodingConstants.NISTRING_ADD_TO_TABLE_FLAG) > 0) {
                        _attributeValueTable.add(value);
                    }

                    _attributes.addAttribute(name, value);
                    break;
                case DecoderStateTables.NISTRING_UTF16_MEDIUM_LENGTH:
                    _octetBufferLength = read() + EncodingConstants.OCTET_STRING_LENGTH_5TH_BIT_SMALL_LIMIT;
                    value = decodeUtf16StringAsString();
                    if ((b & EncodingConstants.NISTRING_ADD_TO_TABLE_FLAG) > 0) {
                        _attributeValueTable.add(value);
                    }

                    _attributes.addAttribute(name, value);
                    break;
                case DecoderStateTables.NISTRING_UTF16_LARGE_LENGTH:
                    _octetBufferLength = ((read() << 24) |
                            (read() << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.OCTET_STRING_LENGTH_5TH_BIT_MEDIUM_LIMIT;
                    value = decodeUtf16StringAsString();
                    if ((b & EncodingConstants.NISTRING_ADD_TO_TABLE_FLAG) > 0) {
                        _attributeValueTable.add(value);
                    }

                    _attributes.addAttribute(name, value);
                    break;
                case DecoderStateTables.NISTRING_RA:
                {
                    final boolean addToTable = (b & EncodingConstants.NISTRING_ADD_TO_TABLE_FLAG) > 0;
                    // Decode resitricted alphabet integer
                    _identifier = (b & 0x0F) << 4;
                    b = read();
                    _identifier |= (b & 0xF0) >> 4;

                    decodeOctetsOnFifthBitOfNonIdentifyingStringOnFirstBit(b);

                    value = decodeRestrictedAlphabetAsString();
                    if (addToTable) {
                        _attributeValueTable.add(value);
                    }

                    _attributes.addAttribute(name, value);
                    break;
                }
                case DecoderStateTables.NISTRING_EA:
                {
                    final boolean addToTable = (b & EncodingConstants.NISTRING_ADD_TO_TABLE_FLAG) > 0;

                    _identifier = (b & 0x0F) << 4;
                    b = read();
                    _identifier |= (b & 0xF0) >> 4;

                    decodeOctetsOnFifthBitOfNonIdentifyingStringOnFirstBit(b);

                    processAIIEncodingAlgorithm(name, addToTable);
                    break;
                }
                case DecoderStateTables.NISTRING_INDEX_SMALL:
                    _attributes.addAttribute(name,
                            _attributeValueTable._array[b & EncodingConstants.INTEGER_2ND_BIT_SMALL_MASK]);
                    break;
                case DecoderStateTables.NISTRING_INDEX_MEDIUM:
                {
                    final int index = (((b & EncodingConstants.INTEGER_2ND_BIT_MEDIUM_MASK) << 8) | read())
                    + EncodingConstants.INTEGER_2ND_BIT_SMALL_LIMIT;

                    _attributes.addAttribute(name,
                            _attributeValueTable._array[index]);
                    break;
                }
                case DecoderStateTables.NISTRING_INDEX_LARGE:
                {
                    final int index = (((b & EncodingConstants.INTEGER_2ND_BIT_LARGE_MASK) << 16) | (read() << 8) | read())
                    + EncodingConstants.INTEGER_2ND_BIT_MEDIUM_LIMIT;

                    _attributes.addAttribute(name,
                            _attributeValueTable._array[index]);
                    break;
                }
                case DecoderStateTables.NISTRING_EMPTY:
                    _attributes.addAttribute(name, "");
                    break;
                default:
                    throw new IOException(CommonResourceBundle.getInstance().getString("message.decodingAIIValue"));
            }

        } while (!_terminate);

        // Reset duplication attribute verfifier
        _duplicateAttributeVerifier._poolCurrent = _duplicateAttributeVerifier._poolHead;

        _terminate = _doubleTerminate;
        _doubleTerminate = false;
    }

    protected final void processCommentII() throws FastInfosetException, IOException {
        switch(decodeNonIdentifyingStringOnFirstBit()) {
            case NISTRING_STRING:
                if (_addToTable) {
                    _v.otherString.add(new CharArray(_charBuffer, 0, _charBufferLength, true));
                }

                try {
                    _lexicalHandler.comment(_charBuffer, 0, _charBufferLength);
                } catch (SAXException e) {
                    throw new FastInfosetException("processCommentII", e);
                }
                break;
            case NISTRING_ENCODING_ALGORITHM:
                throw new IOException(CommonResourceBundle.getInstance().getString("message.commentIIAlgorithmNotSupported"));
            case NISTRING_INDEX:
                final CharArray ca = _v.otherString.get(_integer);

                try {
                    _lexicalHandler.comment(ca.ch, ca.start, ca.length);
                } catch (SAXException e) {
                    throw new FastInfosetException("processCommentII", e);
                }
                break;
            case NISTRING_EMPTY_STRING:
                try {
                    _lexicalHandler.comment(_charBuffer, 0, 0);
                } catch (SAXException e) {
                    throw new FastInfosetException("processCommentII", e);
                }
                break;
        }
    }

    protected final void processProcessingII() throws FastInfosetException, IOException {
        final String target = decodeIdentifyingNonEmptyStringOnFirstBit(_v.otherNCName);

        switch(decodeNonIdentifyingStringOnFirstBit()) {
            case NISTRING_STRING:
                final String data = new String(_charBuffer, 0, _charBufferLength);
                if (_addToTable) {
                    _v.otherString.add(new CharArrayString(data));
                }
                try {
                    _contentHandler.processingInstruction(target, data);
                } catch (SAXException e) {
                    throw new FastInfosetException("processProcessingII", e);
                }
                break;
            case NISTRING_ENCODING_ALGORITHM:
                throw new IOException(CommonResourceBundle.getInstance().getString("message.processingIIWithEncodingAlgorithm"));
            case NISTRING_INDEX:
                try {
                    _contentHandler.processingInstruction(target, _v.otherString.get(_integer).toString());
                } catch (SAXException e) {
                    throw new FastInfosetException("processProcessingII", e);
                }
                break;
            case NISTRING_EMPTY_STRING:
                try {
                    _contentHandler.processingInstruction(target, "");
                } catch (SAXException e) {
                    throw new FastInfosetException("processProcessingII", e);
                }
                break;
        }
    }

    protected final void processCIIEncodingAlgorithm(boolean addToTable) throws FastInfosetException, IOException {
        if (_identifier < EncodingConstants.ENCODING_ALGORITHM_BUILTIN_END) {
            if (_primitiveHandler != null) {
                processCIIBuiltInEncodingAlgorithmAsPrimitive();
            } else if (_algorithmHandler != null) {
                Object array = processBuiltInEncodingAlgorithmAsObject();

                try {
                    _algorithmHandler.object(null, _identifier, array);
                } catch (SAXException e) {
                    throw new FastInfosetException(e);
                }
            } else {
                StringBuffer buffer = new StringBuffer();
                processBuiltInEncodingAlgorithmAsCharacters(buffer);

                try {
                    _contentHandler.characters(buffer.toString().toCharArray(), 0, buffer.length());
                } catch (SAXException e) {
                    throw new FastInfosetException(e);
                }
            }

            if (addToTable) {
                StringBuffer buffer = new StringBuffer();
                processBuiltInEncodingAlgorithmAsCharacters(buffer);
                _characterContentChunkTable.add(buffer.toString().toCharArray(), buffer.length());
            }
        } else if (_identifier == EncodingAlgorithmIndexes.CDATA) {
            // Set back buffer position to start of encoded string
            _octetBufferOffset -= _octetBufferLength;
            decodeUtf8StringIntoCharBuffer();

            try {
                _lexicalHandler.startCDATA();
                _contentHandler.characters(_charBuffer, 0, _charBufferLength);
                _lexicalHandler.endCDATA();
            } catch (SAXException e) {
                throw new FastInfosetException(e);
            }

            if (addToTable) {
                _characterContentChunkTable.add(_charBuffer, _charBufferLength);
            }
        } else if (_identifier >= EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START && _algorithmHandler != null) {
            final String URI = _v.encodingAlgorithm.get(_identifier - EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START);
            if (URI == null) {
                throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().
                        getString("message.URINotPresent", new Object[]{Integer.valueOf(_identifier)}));
            }

            final EncodingAlgorithm ea = (EncodingAlgorithm)_registeredEncodingAlgorithms.get(URI);
            if (ea != null) {
                final Object data = ea.decodeFromBytes(_octetBuffer, _octetBufferStart, _octetBufferLength);
                try {
                    _algorithmHandler.object(URI, _identifier, data);
                } catch (SAXException e) {
                    throw new FastInfosetException(e);
                }
            } else {
                try {
                    _algorithmHandler.octets(URI, _identifier, _octetBuffer, _octetBufferStart, _octetBufferLength);
                } catch (SAXException e) {
                    throw new FastInfosetException(e);
                }
            }
            if (addToTable) {
                throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.addToTableNotSupported"));
            }
        } else if (_identifier >= EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START) {
            // TODO should have property to ignore
            throw new EncodingAlgorithmException(
                    CommonResourceBundle.getInstance().getString("message.algorithmDataCannotBeReported"));
        } else {
            // Reserved built-in algorithms for future use
            // TODO should use sax property to decide if event will be
            // reported, allows for support through handler if required.
            throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.identifiers10to31Reserved"));
        }
    }

    protected final void processCIIBuiltInEncodingAlgorithmAsPrimitive() throws FastInfosetException, IOException {
        try {
            int length;
            switch(_identifier) {
                case EncodingAlgorithmIndexes.HEXADECIMAL:
                case EncodingAlgorithmIndexes.BASE64:
                    _primitiveHandler.bytes(_octetBuffer, _octetBufferStart, _octetBufferLength);
                    break;
                case EncodingAlgorithmIndexes.SHORT:
                    length = BuiltInEncodingAlgorithmFactory.shortEncodingAlgorithm.
                            getPrimtiveLengthFromOctetLength(_octetBufferLength);
                    if (length > builtInAlgorithmState.shortArray.length) {
                        final short[] array = new short[length * 3 / 2 + 1];
                        System.arraycopy(builtInAlgorithmState.shortArray, 0,
                                array, 0, builtInAlgorithmState.shortArray.length);
                        builtInAlgorithmState.shortArray = array;
                    }

                    BuiltInEncodingAlgorithmFactory.shortEncodingAlgorithm.
                            decodeFromBytesToShortArray(builtInAlgorithmState.shortArray, 0,
                            _octetBuffer, _octetBufferStart, _octetBufferLength);
                    _primitiveHandler.shorts(builtInAlgorithmState.shortArray, 0, length);
                    break;
                case EncodingAlgorithmIndexes.INT:
                    length = BuiltInEncodingAlgorithmFactory.intEncodingAlgorithm.
                            getPrimtiveLengthFromOctetLength(_octetBufferLength);
                    if (length > builtInAlgorithmState.intArray.length) {
                        final int[] array = new int[length * 3 / 2 + 1];
                        System.arraycopy(builtInAlgorithmState.intArray, 0,
                                array, 0, builtInAlgorithmState.intArray.length);
                        builtInAlgorithmState.intArray = array;
                    }

                    BuiltInEncodingAlgorithmFactory.intEncodingAlgorithm.
                            decodeFromBytesToIntArray(builtInAlgorithmState.intArray, 0,
                            _octetBuffer, _octetBufferStart, _octetBufferLength);
                    _primitiveHandler.ints(builtInAlgorithmState.intArray, 0, length);
                    break;
                case EncodingAlgorithmIndexes.LONG:
                    length = BuiltInEncodingAlgorithmFactory.longEncodingAlgorithm.
                            getPrimtiveLengthFromOctetLength(_octetBufferLength);
                    if (length > builtInAlgorithmState.longArray.length) {
                        final long[] array = new long[length * 3 / 2 + 1];
                        System.arraycopy(builtInAlgorithmState.longArray, 0,
                                array, 0, builtInAlgorithmState.longArray.length);
                        builtInAlgorithmState.longArray = array;
                    }

                    BuiltInEncodingAlgorithmFactory.longEncodingAlgorithm.
                            decodeFromBytesToLongArray(builtInAlgorithmState.longArray, 0,
                            _octetBuffer, _octetBufferStart, _octetBufferLength);
                    _primitiveHandler.longs(builtInAlgorithmState.longArray, 0, length);
                    break;
                case EncodingAlgorithmIndexes.BOOLEAN:
                    length = BuiltInEncodingAlgorithmFactory.booleanEncodingAlgorithm.
                            getPrimtiveLengthFromOctetLength(_octetBufferLength, _octetBuffer[_octetBufferStart] & 0xFF);
                    if (length > builtInAlgorithmState.booleanArray.length) {
                        final boolean[] array = new boolean[length * 3 / 2 + 1];
                        System.arraycopy(builtInAlgorithmState.booleanArray, 0,
                                array, 0, builtInAlgorithmState.booleanArray.length);
                        builtInAlgorithmState.booleanArray = array;
                    }

                    BuiltInEncodingAlgorithmFactory.booleanEncodingAlgorithm.
                            decodeFromBytesToBooleanArray(
                            builtInAlgorithmState.booleanArray, 0, length,
                            _octetBuffer, _octetBufferStart, _octetBufferLength);
                    _primitiveHandler.booleans(builtInAlgorithmState.booleanArray, 0, length);
                    break;
                case EncodingAlgorithmIndexes.FLOAT:
                    length = BuiltInEncodingAlgorithmFactory.floatEncodingAlgorithm.
                            getPrimtiveLengthFromOctetLength(_octetBufferLength);
                    if (length > builtInAlgorithmState.floatArray.length) {
                        final float[] array = new float[length * 3 / 2 + 1];
                        System.arraycopy(builtInAlgorithmState.floatArray, 0,
                                array, 0, builtInAlgorithmState.floatArray.length);
                        builtInAlgorithmState.floatArray = array;
                    }

                    BuiltInEncodingAlgorithmFactory.floatEncodingAlgorithm.
                            decodeFromBytesToFloatArray(builtInAlgorithmState.floatArray, 0,
                            _octetBuffer, _octetBufferStart, _octetBufferLength);
                    _primitiveHandler.floats(builtInAlgorithmState.floatArray, 0, length);
                    break;
                case EncodingAlgorithmIndexes.DOUBLE:
                    length = BuiltInEncodingAlgorithmFactory.doubleEncodingAlgorithm.
                            getPrimtiveLengthFromOctetLength(_octetBufferLength);
                    if (length > builtInAlgorithmState.doubleArray.length) {
                        final double[] array = new double[length * 3 / 2 + 1];
                        System.arraycopy(builtInAlgorithmState.doubleArray, 0,
                                array, 0, builtInAlgorithmState.doubleArray.length);
                        builtInAlgorithmState.doubleArray = array;
                    }

                    BuiltInEncodingAlgorithmFactory.doubleEncodingAlgorithm.
                            decodeFromBytesToDoubleArray(builtInAlgorithmState.doubleArray, 0,
                            _octetBuffer, _octetBufferStart, _octetBufferLength);
                    _primitiveHandler.doubles(builtInAlgorithmState.doubleArray, 0, length);
                    break;
                case EncodingAlgorithmIndexes.UUID:
                    length = BuiltInEncodingAlgorithmFactory.uuidEncodingAlgorithm.
                            getPrimtiveLengthFromOctetLength(_octetBufferLength);
                    if (length > builtInAlgorithmState.longArray.length) {
                        final long[] array = new long[length * 3 / 2 + 1];
                        System.arraycopy(builtInAlgorithmState.longArray, 0,
                                array, 0, builtInAlgorithmState.longArray.length);
                        builtInAlgorithmState.longArray = array;
                    }

                    BuiltInEncodingAlgorithmFactory.uuidEncodingAlgorithm.
                            decodeFromBytesToLongArray(builtInAlgorithmState.longArray, 0,
                            _octetBuffer, _octetBufferStart, _octetBufferLength);
                    _primitiveHandler.uuids(builtInAlgorithmState.longArray, 0, length);
                    break;
                case EncodingAlgorithmIndexes.CDATA:
                    throw new UnsupportedOperationException("CDATA");
                default:
                    throw new FastInfosetException(CommonResourceBundle.getInstance().
                            getString("message.unsupportedAlgorithm", new Object[]{Integer.valueOf(_identifier)}));
            }
        } catch (SAXException e) {
            throw new FastInfosetException(e);
        }
    }


    protected final void processAIIEncodingAlgorithm(QualifiedName name, boolean addToTable) throws FastInfosetException, IOException {
        if (_identifier < EncodingConstants.ENCODING_ALGORITHM_BUILTIN_END) {
            if (_primitiveHandler != null || _algorithmHandler != null) {
                Object data = processBuiltInEncodingAlgorithmAsObject();
                _attributes.addAttributeWithAlgorithmData(name, null, _identifier, data);
            } else {
                StringBuffer buffer = new StringBuffer();
                processBuiltInEncodingAlgorithmAsCharacters(buffer);
                _attributes.addAttribute(name, buffer.toString());
            }
        } else if (_identifier >= EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START && _algorithmHandler != null) {
            final String URI = _v.encodingAlgorithm.get(_identifier - EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START);
            if (URI == null) {
                throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().
                        getString("message.URINotPresent", new Object[]{Integer.valueOf(_identifier)}));
            }

            final EncodingAlgorithm ea = (EncodingAlgorithm)_registeredEncodingAlgorithms.get(URI);
            if (ea != null) {
                final Object data = ea.decodeFromBytes(_octetBuffer, _octetBufferStart, _octetBufferLength);
                _attributes.addAttributeWithAlgorithmData(name, URI, _identifier, data);
            } else {
                final byte[] data = new byte[_octetBufferLength];
                System.arraycopy(_octetBuffer, _octetBufferStart, data, 0, _octetBufferLength);
                _attributes.addAttributeWithAlgorithmData(name, URI, _identifier, data);
            }
        } else if (_identifier >= EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START) {
            // TODO should have property to ignore
            throw new EncodingAlgorithmException(
                    CommonResourceBundle.getInstance().getString("message.algorithmDataCannotBeReported"));
        } else if (_identifier == EncodingAlgorithmIndexes.CDATA) {
            throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.CDATAAlgorithmNotSupported"));
        } else {
            // Reserved built-in algorithms for future use
            // TODO should use sax property to decide if event will be
            // reported, allows for support through handler if required.
            throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.identifiers10to31Reserved"));
        }

        if (addToTable) {
            _attributeValueTable.add(_attributes.getValue(_attributes.getIndex(name.qName)));
        }
    }

    protected final void processBuiltInEncodingAlgorithmAsCharacters(StringBuffer buffer) throws FastInfosetException, IOException {
        // TODO not very efficient, need to reuse buffers
        Object array = BuiltInEncodingAlgorithmFactory.getAlgorithm(_identifier).
                decodeFromBytes(_octetBuffer, _octetBufferStart, _octetBufferLength);

        BuiltInEncodingAlgorithmFactory.getAlgorithm(_identifier).convertToCharacters(array,  buffer);
    }

    protected final Object processBuiltInEncodingAlgorithmAsObject() throws FastInfosetException, IOException {
        return BuiltInEncodingAlgorithmFactory.getAlgorithm(_identifier).
                decodeFromBytes(_octetBuffer, _octetBufferStart, _octetBufferLength);
    }
}
