/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.fastinfoset.stax;

import com.sun.xml.internal.fastinfoset.Decoder;
import com.sun.xml.internal.fastinfoset.DecoderStateTables;
import com.sun.xml.internal.fastinfoset.EncodingConstants;
import com.sun.xml.internal.fastinfoset.OctetBufferListener;
import com.sun.xml.internal.fastinfoset.QualifiedName;
import com.sun.xml.internal.fastinfoset.algorithm.BuiltInEncodingAlgorithmFactory;
import com.sun.xml.internal.fastinfoset.sax.AttributesHolder;
import com.sun.xml.internal.fastinfoset.util.CharArray;
import com.sun.xml.internal.fastinfoset.util.CharArrayString;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithm;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmException;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmIndexes;
import com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetException;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;
import com.sun.xml.internal.fastinfoset.org.apache.xerces.util.XMLChar;
import com.sun.xml.internal.fastinfoset.util.DuplicateAttributeVerifier;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.xml.internal.org.jvnet.fastinfoset.stax.FastInfosetStreamReader;

/**
 * The Fast Infoset StAX parser.
 * <p>
 * Instantiate this parser to parse a fast infoset document in accordance
 * with the StAX API.
 *
 * <p>
 * More than one fast infoset document may be decoded from the
 * {@link java.io.InputStream}.
 */
public class StAXDocumentParser extends Decoder
        implements XMLStreamReader, FastInfosetStreamReader, OctetBufferListener {
    private static final Logger logger = Logger.getLogger(StAXDocumentParser.class.getName());

    protected static final int INTERNAL_STATE_START_DOCUMENT = 0;
    protected static final int INTERNAL_STATE_START_ELEMENT_TERMINATE = 1;
    protected static final int INTERNAL_STATE_SINGLE_TERMINATE_ELEMENT_WITH_NAMESPACES = 2;
    protected static final int INTERNAL_STATE_DOUBLE_TERMINATE_ELEMENT = 3;
    protected static final int INTERNAL_STATE_END_DOCUMENT = 4;
    protected static final int INTERNAL_STATE_VOID = -1;

    protected int _internalState;

    /**
     * Current event
     */
    protected int _eventType;

    /**
     * Stack of qualified names and namespaces
     */
    protected QualifiedName[] _qNameStack = new QualifiedName[32];
    protected int[] _namespaceAIIsStartStack = new int[32];
    protected int[] _namespaceAIIsEndStack = new int[32];
    protected int _stackCount = -1;

    protected String[] _namespaceAIIsPrefix = new String[32];
    protected String[] _namespaceAIIsNamespaceName = new String[32];
    protected int[] _namespaceAIIsPrefixIndex = new int[32];
    protected int _namespaceAIIsIndex;

    /**
     * Namespaces associated with START_ELEMENT or END_ELEMENT
     */
    protected int _currentNamespaceAIIsStart;
    protected int _currentNamespaceAIIsEnd;

    /**
     * Qualified name associated with START_ELEMENT or END_ELEMENT.
     */
    protected QualifiedName _qualifiedName;

    /**
     * List of attributes
     */
    protected AttributesHolder _attributes = new AttributesHolder();

    protected boolean _clearAttributes = false;

    /**
     * Characters associated with event.
     */
    protected char[] _characters;
    protected int _charactersOffset;

    protected String _algorithmURI;
    protected int _algorithmId;
    protected boolean _isAlgorithmDataCloned;
    protected byte[] _algorithmData;
    protected int _algorithmDataOffset;
    protected int _algorithmDataLength;

    /**
     * State for processing instruction
     */
    protected String _piTarget;
    protected String _piData;

    protected NamespaceContextImpl _nsContext = new NamespaceContextImpl();

    protected String _characterEncodingScheme;

    protected StAXManager _manager;

    public StAXDocumentParser() {
        reset();
        _manager = new StAXManager(StAXManager.CONTEXT_READER);
    }

    public StAXDocumentParser(InputStream s) {
        this();
        setInputStream(s);
        _manager = new StAXManager(StAXManager.CONTEXT_READER);
    }

    public StAXDocumentParser(InputStream s, StAXManager manager) {
        this(s);
        _manager = manager;
    }

    @Override
    public void setInputStream(InputStream s) {
        super.setInputStream(s);
        reset();
    }

    @Override
    public void reset() {
        super.reset();
        if (_internalState != INTERNAL_STATE_START_DOCUMENT &&
                _internalState != INTERNAL_STATE_END_DOCUMENT) {

            for (int i = _namespaceAIIsIndex - 1; i >= 0; i--) {
                _prefixTable.popScopeWithPrefixEntry(_namespaceAIIsPrefixIndex[i]);
            }

            _stackCount = -1;

            _namespaceAIIsIndex = 0;
            _characters = null;
            _algorithmData = null;
        }

        _characterEncodingScheme = "UTF-8";
        _eventType = START_DOCUMENT;
        _internalState = INTERNAL_STATE_START_DOCUMENT;
    }

    protected void resetOnError() {
        super.reset();

        if (_v != null) {
            _prefixTable.clearCompletely();
        }
        _duplicateAttributeVerifier.clear();

        _stackCount = -1;

        _namespaceAIIsIndex = 0;
        _characters = null;
        _algorithmData = null;

        _eventType = START_DOCUMENT;
        _internalState = INTERNAL_STATE_START_DOCUMENT;
    }

    // -- XMLStreamReader Interface -------------------------------------------

    public Object getProperty(java.lang.String name)
    throws java.lang.IllegalArgumentException {
        if (_manager != null) {
            return _manager.getProperty(name);
        }
        return null;
    }

    public int next() throws XMLStreamException {
        try {
            if (_internalState != INTERNAL_STATE_VOID) {
                switch (_internalState) {
                    case INTERNAL_STATE_START_DOCUMENT:
                        decodeHeader();
                        processDII();

                        _internalState = INTERNAL_STATE_VOID;
                        break;
                    case INTERNAL_STATE_START_ELEMENT_TERMINATE:
                        if (_currentNamespaceAIIsEnd > 0) {
                            for (int i = _currentNamespaceAIIsEnd - 1; i >= _currentNamespaceAIIsStart; i--) {
                                _prefixTable.popScopeWithPrefixEntry(_namespaceAIIsPrefixIndex[i]);
                            }
                            _namespaceAIIsIndex = _currentNamespaceAIIsStart;
                        }

                        // Pop information off the stack
                        popStack();

                        _internalState = INTERNAL_STATE_VOID;
                        return _eventType = END_ELEMENT;
                    case INTERNAL_STATE_SINGLE_TERMINATE_ELEMENT_WITH_NAMESPACES:
                        // Undeclare namespaces
                        for (int i = _currentNamespaceAIIsEnd - 1; i >= _currentNamespaceAIIsStart; i--) {
                            _prefixTable.popScopeWithPrefixEntry(_namespaceAIIsPrefixIndex[i]);
                        }
                        _namespaceAIIsIndex = _currentNamespaceAIIsStart;
                        _internalState = INTERNAL_STATE_VOID;
                        break;
                    case INTERNAL_STATE_DOUBLE_TERMINATE_ELEMENT:
                        // Undeclare namespaces
                        if (_currentNamespaceAIIsEnd > 0) {
                            for (int i = _currentNamespaceAIIsEnd - 1; i >= _currentNamespaceAIIsStart; i--) {
                                _prefixTable.popScopeWithPrefixEntry(_namespaceAIIsPrefixIndex[i]);
                            }
                            _namespaceAIIsIndex = _currentNamespaceAIIsStart;
                        }

                        if (_stackCount == -1) {
                            _internalState = INTERNAL_STATE_END_DOCUMENT;
                            return _eventType = END_DOCUMENT;
                        }

                        // Pop information off the stack
                        popStack();

                        _internalState = (_currentNamespaceAIIsEnd > 0) ?
                            INTERNAL_STATE_SINGLE_TERMINATE_ELEMENT_WITH_NAMESPACES :
                            INTERNAL_STATE_VOID;
                        return _eventType = END_ELEMENT;
                    case INTERNAL_STATE_END_DOCUMENT:
                        throw new NoSuchElementException(CommonResourceBundle.getInstance().getString("message.noMoreEvents"));
                }
            }

            // Reset internal state
            _characters = null;
            _algorithmData = null;
            _currentNamespaceAIIsEnd = 0;

            // Process information item
            final int b = read();
            switch(DecoderStateTables.EII(b)) {
                case DecoderStateTables.EII_NO_AIIS_INDEX_SMALL:
                    processEII(_elementNameTable._array[b], false);
                    return _eventType;
                case DecoderStateTables.EII_AIIS_INDEX_SMALL:
                    processEII(_elementNameTable._array[b & EncodingConstants.INTEGER_3RD_BIT_SMALL_MASK], true);
                    return _eventType;
                case DecoderStateTables.EII_INDEX_MEDIUM:
                    processEII(processEIIIndexMedium(b), (b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0);
                    return _eventType;
                case DecoderStateTables.EII_INDEX_LARGE:
                    processEII(processEIIIndexLarge(b), (b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0);
                    return _eventType;
                case DecoderStateTables.EII_LITERAL:
                {
                    final QualifiedName qn = processLiteralQualifiedName(
                            b & EncodingConstants.LITERAL_QNAME_PREFIX_NAMESPACE_NAME_MASK,
                            _elementNameTable.getNext());
                    _elementNameTable.add(qn);
                    processEII(qn, (b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0);
                    return _eventType;
                }
                case DecoderStateTables.EII_NAMESPACES:
                    processEIIWithNamespaces((b & EncodingConstants.ELEMENT_ATTRIBUTE_FLAG) > 0);
                    return _eventType;
                case DecoderStateTables.CII_UTF8_SMALL_LENGTH:
                    _octetBufferLength = (b & EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_MASK)
                    + 1;
                    processUtf8CharacterString(b);
                    return _eventType = CHARACTERS;
                case DecoderStateTables.CII_UTF8_MEDIUM_LENGTH:
                    _octetBufferLength = read() + EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_LIMIT;
                    processUtf8CharacterString(b);
                    return _eventType = CHARACTERS;
                case DecoderStateTables.CII_UTF8_LARGE_LENGTH:
                    _octetBufferLength = ((read() << 24) |
                            (read() << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_MEDIUM_LIMIT;
                    processUtf8CharacterString(b);
                    return _eventType = CHARACTERS;
                case DecoderStateTables.CII_UTF16_SMALL_LENGTH:
                    _octetBufferLength = (b & EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_MASK)
                    + 1;
                    processUtf16CharacterString(b);
                    return _eventType = CHARACTERS;
                case DecoderStateTables.CII_UTF16_MEDIUM_LENGTH:
                    _octetBufferLength = read() + EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_SMALL_LIMIT;
                    processUtf16CharacterString(b);
                    return _eventType = CHARACTERS;
                case DecoderStateTables.CII_UTF16_LARGE_LENGTH:
                    _octetBufferLength = ((read() << 24) |
                            (read() << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.OCTET_STRING_LENGTH_7TH_BIT_MEDIUM_LIMIT;
                    processUtf16CharacterString(b);
                    return _eventType = CHARACTERS;
                case DecoderStateTables.CII_RA:
                {
                    final boolean addToTable = (b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0;

                    _identifier = (b & 0x02) << 6;
                    final int b2 = read();
                    _identifier |= (b2 & 0xFC) >> 2;

                    decodeOctetsOnSeventhBitOfNonIdentifyingStringOnThirdBit(b2);

                    decodeRestrictedAlphabetAsCharBuffer();

                    if (addToTable) {
                        _charactersOffset = _characterContentChunkTable.add(_charBuffer, _charBufferLength);
                        _characters = _characterContentChunkTable._array;
                    } else {
                        _characters = _charBuffer;
                        _charactersOffset = 0;
                    }
                    return _eventType = CHARACTERS;
                }
                case DecoderStateTables.CII_EA:
                {
                    final boolean addToTable = (b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0;
                    // Decode encoding algorithm integer
                    _algorithmId = (b & 0x02) << 6;
                    final int b2 = read();
                    _algorithmId |= (b2 & 0xFC) >> 2;

                    decodeOctetsOnSeventhBitOfNonIdentifyingStringOnThirdBit(b2);
                    processCIIEncodingAlgorithm(addToTable);

                    if (_algorithmId == EncodingAlgorithmIndexes.CDATA) {
                        return _eventType = CDATA;
                    }

                    return _eventType = CHARACTERS;
                }
                case DecoderStateTables.CII_INDEX_SMALL:
                {
                    final int index = b & EncodingConstants.INTEGER_4TH_BIT_SMALL_MASK;
                    _characterContentChunkTable._cachedIndex = index;

                    _characters = _characterContentChunkTable._array;
                    _charactersOffset = _characterContentChunkTable._offset[index];
                    _charBufferLength = _characterContentChunkTable._length[index];
                    return _eventType = CHARACTERS;
                }
                case DecoderStateTables.CII_INDEX_MEDIUM:
                {
                    final int index = (((b & EncodingConstants.INTEGER_4TH_BIT_MEDIUM_MASK) << 8) | read())
                    + EncodingConstants.INTEGER_4TH_BIT_SMALL_LIMIT;
                    _characterContentChunkTable._cachedIndex = index;

                    _characters = _characterContentChunkTable._array;
                    _charactersOffset = _characterContentChunkTable._offset[index];
                    _charBufferLength = _characterContentChunkTable._length[index];
                    return _eventType = CHARACTERS;
                }
                case DecoderStateTables.CII_INDEX_LARGE:
                {
                    final int index = (((b & EncodingConstants.INTEGER_4TH_BIT_LARGE_MASK) << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.INTEGER_4TH_BIT_MEDIUM_LIMIT;
                    _characterContentChunkTable._cachedIndex = index;

                    _characters = _characterContentChunkTable._array;
                    _charactersOffset = _characterContentChunkTable._offset[index];
                    _charBufferLength = _characterContentChunkTable._length[index];
                    return _eventType = CHARACTERS;
                }
                case DecoderStateTables.CII_INDEX_LARGE_LARGE:
                {
                    final int index = ((read() << 16) |
                            (read() << 8) |
                            read())
                            + EncodingConstants.INTEGER_4TH_BIT_LARGE_LIMIT;
                    _characterContentChunkTable._cachedIndex = index;

                    _characters = _characterContentChunkTable._array;
                    _charactersOffset = _characterContentChunkTable._offset[index];
                    _charBufferLength = _characterContentChunkTable._length[index];
                    return _eventType = CHARACTERS;
                }
                case DecoderStateTables.COMMENT_II:
                    processCommentII();
                    return _eventType;
                case DecoderStateTables.PROCESSING_INSTRUCTION_II:
                    processProcessingII();
                    return _eventType;
                case DecoderStateTables.UNEXPANDED_ENTITY_REFERENCE_II:
                {
                    processUnexpandedEntityReference(b);
                    // Skip the reference
                    return next();
                }
                case DecoderStateTables.TERMINATOR_DOUBLE:
                    if (_stackCount != -1) {
                        // Pop information off the stack
                        popStack();

                        _internalState = INTERNAL_STATE_DOUBLE_TERMINATE_ELEMENT;
                        return _eventType = END_ELEMENT;
                    }

                    _internalState = INTERNAL_STATE_END_DOCUMENT;
                    return _eventType = END_DOCUMENT;
                case DecoderStateTables.TERMINATOR_SINGLE:
                    if (_stackCount != -1) {
                        // Pop information off the stack
                        popStack();

                        if (_currentNamespaceAIIsEnd > 0) {
                            _internalState = INTERNAL_STATE_SINGLE_TERMINATE_ELEMENT_WITH_NAMESPACES;
                        }
                        return _eventType = END_ELEMENT;
                    }

                    _internalState = INTERNAL_STATE_END_DOCUMENT;
                    return _eventType = END_DOCUMENT;
                default:
                    throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.IllegalStateDecodingEII"));
            }
        } catch (IOException e) {
            resetOnError();
            logger.log(Level.FINE, "next() exception", e);
            throw new XMLStreamException(e);
        } catch (FastInfosetException e) {
            resetOnError();
            logger.log(Level.FINE, "next() exception", e);
            throw new XMLStreamException(e);
        } catch (RuntimeException e) {
            resetOnError();
            logger.log(Level.FINE, "next() exception", e);
            throw e;
        }
    }

    private final void processUtf8CharacterString(final int b) throws IOException {
        if ((b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0) {
            _characterContentChunkTable.ensureSize(_octetBufferLength);
            _characters = _characterContentChunkTable._array;
            _charactersOffset = _characterContentChunkTable._arrayIndex;
            decodeUtf8StringAsCharBuffer(_characterContentChunkTable._array, _charactersOffset);
            _characterContentChunkTable.add(_charBufferLength);
        } else {
            decodeUtf8StringAsCharBuffer();
            _characters = _charBuffer;
            _charactersOffset = 0;
        }
    }

    private final void processUtf16CharacterString(final int b) throws IOException {
        decodeUtf16StringAsCharBuffer();
        if ((b & EncodingConstants.CHARACTER_CHUNK_ADD_TO_TABLE_FLAG) > 0) {
            _charactersOffset = _characterContentChunkTable.add(_charBuffer, _charBufferLength);
            _characters = _characterContentChunkTable._array;
        } else {
            _characters = _charBuffer;
            _charactersOffset = 0;
        }
    }

    private void popStack() {
        // Pop information off the stack
        _qualifiedName = _qNameStack[_stackCount];
        _currentNamespaceAIIsStart = _namespaceAIIsStartStack[_stackCount];
        _currentNamespaceAIIsEnd = _namespaceAIIsEndStack[_stackCount];
        _qNameStack[_stackCount--] = null;
    }

    /** Test if the current event is of the given type and if the namespace and name match the current namespace and name of the current event.
     * If the namespaceURI is null it is not checked for equality, if the localName is null it is not checked for equality.
     * @param type the event type
     * @param namespaceURI the uri of the event, may be null
     * @param localName the localName of the event, may be null
     * @throws XMLStreamException if the required values are not matched.
     */
    public final void require(int type, String namespaceURI, String localName)
    throws XMLStreamException {
        if( type != _eventType)
            throw new XMLStreamException(CommonResourceBundle.getInstance().getString("message.eventTypeNotMatch", new Object[]{getEventTypeString(type)}));
        if( namespaceURI != null && !namespaceURI.equals(getNamespaceURI()) )
            throw new XMLStreamException(CommonResourceBundle.getInstance().getString("message.namespaceURINotMatch", new Object[]{namespaceURI}));
        if(localName != null && !localName.equals(getLocalName()))
            throw new XMLStreamException(CommonResourceBundle.getInstance().getString("message.localNameNotMatch", new Object[]{localName}));
    }

    /** Reads the content of a text-only element. Precondition:
     * the current event is START_ELEMENT. Postcondition:
     * The current event is the corresponding END_ELEMENT.
     * @throws XMLStreamException if the current event is not a START_ELEMENT or if
     * a non text element is encountered
     */
    public final String getElementText() throws XMLStreamException {

        if(getEventType() != START_ELEMENT) {
            throw new XMLStreamException(
                    CommonResourceBundle.getInstance().getString("message.mustBeOnSTARTELEMENT"), getLocation());
        }
        //current is StartElement, move to the next
        next();
        return getElementText(true);
    }
    /**
     * @param startElementRead flag if start element has already been read
     */
    public final String getElementText(boolean startElementRead) throws XMLStreamException {
        if (!startElementRead) {
            throw new XMLStreamException(
                    CommonResourceBundle.getInstance().getString("message.mustBeOnSTARTELEMENT"), getLocation());
        }
        int eventType = getEventType();
        StringBuilder content = new StringBuilder();
        while(eventType != END_ELEMENT ) {
            if(eventType == CHARACTERS
                    || eventType == CDATA
                    || eventType == SPACE
                    || eventType == ENTITY_REFERENCE) {
                content.append(getText());
            } else if(eventType == PROCESSING_INSTRUCTION
                    || eventType == COMMENT) {
                // skipping
            } else if(eventType == END_DOCUMENT) {
                throw new XMLStreamException(CommonResourceBundle.getInstance().getString("message.unexpectedEOF"));
            } else if(eventType == START_ELEMENT) {
                throw new XMLStreamException(
                        CommonResourceBundle.getInstance().getString("message.getElementTextExpectTextOnly"), getLocation());
            } else {
                throw new XMLStreamException(
                        CommonResourceBundle.getInstance().getString("message.unexpectedEventType")+ getEventTypeString(eventType), getLocation());
            }
            eventType = next();
        }
        return content.toString();
    }

    /** Skips any white space (isWhiteSpace() returns true), COMMENT,
     * or PROCESSING_INSTRUCTION,
     * until a START_ELEMENT or END_ELEMENT is reached.
     * If other than white space characters, COMMENT, PROCESSING_INSTRUCTION, START_ELEMENT, END_ELEMENT
     * are encountered, an exception is thrown. This method should
     * be used when processing element-only content seperated by white space.
     * This method should
     * be used when processing element-only content because
     * the parser is not able to recognize ignorable whitespace if
     * then DTD is missing or not interpreted.
     * @return the event type of the element read
     * @throws XMLStreamException if the current event is not white space
     */
    public final int nextTag() throws XMLStreamException {
        next();
        return nextTag(true);
    }
    /** if the current tag has already read, such as in the case EventReader's
     * peek() has been called, the current cursor should not move before the loop
     */
    public final int nextTag(boolean currentTagRead) throws XMLStreamException {
        int eventType = getEventType();
        if (!currentTagRead) {
            eventType = next();
        }
        while((eventType == CHARACTERS && isWhiteSpace()) // skip whitespace
        || (eventType == CDATA && isWhiteSpace())
        || eventType == SPACE
                || eventType == PROCESSING_INSTRUCTION
                || eventType == COMMENT) {
            eventType = next();
        }
        if (eventType != START_ELEMENT && eventType != END_ELEMENT) {
            throw new XMLStreamException(CommonResourceBundle.getInstance().getString("message.expectedStartOrEnd"), getLocation());
        }
        return eventType;
    }

    public final boolean hasNext() throws XMLStreamException {
        return (_eventType != END_DOCUMENT);
    }

    public void close() throws XMLStreamException {
        try {
            super.closeIfRequired();
        } catch (IOException ex) {
        }
    }

    public final String getNamespaceURI(String prefix) {
        String namespace = getNamespaceDecl(prefix);
        if (namespace == null) {
            if (prefix == null) {
                throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.nullPrefix"));
            }
            return null;  // unbound
        }
        return namespace;
    }

    public final boolean isStartElement() {
        return (_eventType == START_ELEMENT);
    }

    public final boolean isEndElement() {
        return (_eventType == END_ELEMENT);
    }

    public final boolean isCharacters() {
        return (_eventType == CHARACTERS);
    }

    /**
     *  Returns true if the cursor points to a character data event that consists of all whitespace
     *  Application calling this method needs to cache the value and avoid calling this method again
     *  for the same event.
     * @return true if the cursor points to all whitespace, false otherwise
     */
    public final boolean isWhiteSpace() {
        if(isCharacters() || (_eventType == CDATA)){
            char [] ch = this.getTextCharacters();
            int start = this.getTextStart();
            int length = this.getTextLength();
            for (int i = start; i < start + length; i++){
                if(!XMLChar.isSpace(ch[i])){
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public final String getAttributeValue(String namespaceURI, String localName) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetAttributeValue"));
        }

        if (localName == null)
            throw new IllegalArgumentException();

        // Search for the attributes in _attributes
        if (namespaceURI != null) {
            for (int i = 0; i < _attributes.getLength(); i++) {
                if (_attributes.getLocalName(i).equals(localName) &&
                        _attributes.getURI(i).equals(namespaceURI)) {
                    return _attributes.getValue(i);
                }
            }
        } else {
            for (int i = 0; i < _attributes.getLength(); i++) {
                if (_attributes.getLocalName(i).equals(localName)) {
                    return _attributes.getValue(i);
                }
            }
        }

        return null;
    }

    public final int getAttributeCount() {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetAttributeValue"));
        }

        return _attributes.getLength();
    }

    public final javax.xml.namespace.QName getAttributeName(int index) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetAttributeValue"));
        }
        return _attributes.getQualifiedName(index).getQName();
    }

    public final String getAttributeNamespace(int index) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetAttributeValue"));
        }

        return _attributes.getURI(index);
    }

    public final String getAttributeLocalName(int index) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetAttributeValue"));
        }
        return _attributes.getLocalName(index);
    }

    public final String getAttributePrefix(int index) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetAttributeValue"));
        }
        return _attributes.getPrefix(index);
    }

    public final String getAttributeType(int index) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetAttributeValue"));
        }
        return _attributes.getType(index);
    }

    public final String getAttributeValue(int index) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetAttributeValue"));
        }
        return _attributes.getValue(index);
    }

    public final boolean isAttributeSpecified(int index) {
        return false;   // non-validating parser
    }

    public final int getNamespaceCount() {
        if (_eventType == START_ELEMENT || _eventType == END_ELEMENT) {
            return (_currentNamespaceAIIsEnd > 0) ? (_currentNamespaceAIIsEnd - _currentNamespaceAIIsStart) : 0;
        } else {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetNamespaceCount"));
        }
    }

    public final String getNamespacePrefix(int index) {
        if (_eventType == START_ELEMENT || _eventType == END_ELEMENT) {
            return _namespaceAIIsPrefix[_currentNamespaceAIIsStart + index];
        } else {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetNamespacePrefix"));
        }
    }

    public final String getNamespaceURI(int index) {
        if (_eventType == START_ELEMENT || _eventType == END_ELEMENT) {
            return _namespaceAIIsNamespaceName[_currentNamespaceAIIsStart + index];
        } else {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetNamespacePrefix"));
        }
    }

    public final NamespaceContext getNamespaceContext() {
        return _nsContext;
    }

    public final int getEventType() {
        return _eventType;
    }

    public final String getText() {
        if (_characters == null) {
            checkTextState();
        }

        if (_characters == _characterContentChunkTable._array) {
            return _characterContentChunkTable.getString(_characterContentChunkTable._cachedIndex);
        } else {
            return new String(_characters, _charactersOffset, _charBufferLength);
        }
    }

    public final char[] getTextCharacters() {
        if (_characters == null) {
            checkTextState();
        }

        return _characters;
    }

    public final int getTextStart() {
        if (_characters == null) {
            checkTextState();
        }

        return _charactersOffset;
    }

    public final int getTextLength() {
        if (_characters == null) {
            checkTextState();
        }

        return _charBufferLength;
    }

    public final int getTextCharacters(int sourceStart, char[] target,
            int targetStart, int length) throws XMLStreamException {
        if (_characters == null) {
            checkTextState();
        }

        try {
            int bytesToCopy = Math.min(_charBufferLength, length);
            System.arraycopy(_characters, _charactersOffset + sourceStart,
                    target, targetStart, bytesToCopy);
            return bytesToCopy;
        } catch (IndexOutOfBoundsException e) {
            throw new XMLStreamException(e);
        }
    }

    protected final void checkTextState() {
        if (_algorithmData == null) {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.InvalidStateForText"));
        }

        try {
            convertEncodingAlgorithmDataToCharacters();
        } catch (Exception e) {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.InvalidStateForText"));
        }
    }

    public final String getEncoding() {
        return _characterEncodingScheme;
    }

    public final boolean hasText() {
        return (_characters != null);
    }

    public final Location getLocation() {
        //location should be created in next()
        //returns a nil location for now
        return EventLocation.getNilLocation();
    }

    public final QName getName() {
        if (_eventType == START_ELEMENT || _eventType == END_ELEMENT) {
            return _qualifiedName.getQName();
        } else {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetName"));
        }
    }

    public final String getLocalName() {
        if (_eventType == START_ELEMENT || _eventType == END_ELEMENT) {
            return _qualifiedName.localName;
        } else {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetLocalName"));
        }
    }

    public final boolean hasName() {
        return (_eventType == START_ELEMENT || _eventType == END_ELEMENT);
    }

    public final String getNamespaceURI() {
        if (_eventType == START_ELEMENT || _eventType == END_ELEMENT) {
            return _qualifiedName.namespaceName;
        } else {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetNamespaceURI"));
        }
    }

    public final String getPrefix() {
        if (_eventType == START_ELEMENT || _eventType == END_ELEMENT) {
            return _qualifiedName.prefix;
        } else {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetPrefix"));
        }
    }

    public final String getVersion() {
        return null;
    }

    public final boolean isStandalone() {
        return false;
    }

    public final boolean standaloneSet() {
        return false;
    }

    public final String getCharacterEncodingScheme() {
        return null;
    }

    public final String getPITarget() {
        if (_eventType != PROCESSING_INSTRUCTION) {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetPITarget"));
        }

        return _piTarget;
    }

    public final String getPIData() {
        if (_eventType != PROCESSING_INSTRUCTION) {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetPIData"));
        }

        return _piData;
    }




    public final String getNameString() {
        if (_eventType == START_ELEMENT || _eventType == END_ELEMENT) {
            return _qualifiedName.getQNameString();
        } else {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetName"));
        }
    }

    public final String getAttributeNameString(int index) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException(CommonResourceBundle.getInstance().getString("message.invalidCallingGetAttributeValue"));
        }
        return _attributes.getQualifiedName(index).getQNameString();
    }


    public final String getTextAlgorithmURI() {
        return _algorithmURI;
    }

    public final int getTextAlgorithmIndex() {
        return _algorithmId;
    }

    public final boolean hasTextAlgorithmBytes() {
        return _algorithmData != null;
    }


    /**
     * Returns the byte[], which represents text algorithms.
     * @deprecated was deprecated due to security reasons. Now the method return cloned byte[].
     *
     * @return
     */
    public final byte[] getTextAlgorithmBytes() {
        // Do not return the actual _algorithmData due to security reasons
//        return _algorithmData;
        if (_algorithmData == null) {
            return null;
        }

        final byte[] algorithmData = new byte[_algorithmData.length];
        System.arraycopy(_algorithmData, 0, algorithmData, 0, _algorithmData.length);
        return algorithmData;
    }

    public final byte[] getTextAlgorithmBytesClone() {
        if (_algorithmData == null) {
            return null;
        }

        byte[] algorithmData = new byte[_algorithmDataLength];
        System.arraycopy(_algorithmData, _algorithmDataOffset, algorithmData, 0, _algorithmDataLength);
        return algorithmData;
    }

    public final int getTextAlgorithmStart() {
        return _algorithmDataOffset;
    }

    public final int getTextAlgorithmLength() {
        return _algorithmDataLength;
    }

    public final int getTextAlgorithmBytes(int sourceStart, byte[] target,
            int targetStart, int length) throws XMLStreamException {
        try {
            System.arraycopy(_algorithmData, sourceStart, target,
                    targetStart, length);
            return length;
        } catch (IndexOutOfBoundsException e) {
            throw new XMLStreamException(e);
        }
    }

    // FastInfosetStreamReader impl

    public final int peekNext() throws XMLStreamException {
        try {
            switch(DecoderStateTables.EII(peek(this))) {
                case DecoderStateTables.EII_NO_AIIS_INDEX_SMALL:
                case DecoderStateTables.EII_AIIS_INDEX_SMALL:
                case DecoderStateTables.EII_INDEX_MEDIUM:
                case DecoderStateTables.EII_INDEX_LARGE:
                case DecoderStateTables.EII_LITERAL:
                case DecoderStateTables.EII_NAMESPACES:
                    return START_ELEMENT;
                case DecoderStateTables.CII_UTF8_SMALL_LENGTH:
                case DecoderStateTables.CII_UTF8_MEDIUM_LENGTH:
                case DecoderStateTables.CII_UTF8_LARGE_LENGTH:
                case DecoderStateTables.CII_UTF16_SMALL_LENGTH:
                case DecoderStateTables.CII_UTF16_MEDIUM_LENGTH:
                case DecoderStateTables.CII_UTF16_LARGE_LENGTH:
                case DecoderStateTables.CII_RA:
                case DecoderStateTables.CII_EA:
                case DecoderStateTables.CII_INDEX_SMALL:
                case DecoderStateTables.CII_INDEX_MEDIUM:
                case DecoderStateTables.CII_INDEX_LARGE:
                case DecoderStateTables.CII_INDEX_LARGE_LARGE:
                    return CHARACTERS;
                case DecoderStateTables.COMMENT_II:
                    return COMMENT;
                case DecoderStateTables.PROCESSING_INSTRUCTION_II:
                    return PROCESSING_INSTRUCTION;
                case DecoderStateTables.UNEXPANDED_ENTITY_REFERENCE_II:
                    return ENTITY_REFERENCE;
                case DecoderStateTables.TERMINATOR_DOUBLE:
                case DecoderStateTables.TERMINATOR_SINGLE:
                    return (_stackCount != -1) ? END_ELEMENT : END_DOCUMENT;
                default:
                    throw new FastInfosetException(
                            CommonResourceBundle.getInstance().getString("message.IllegalStateDecodingEII"));
            }
        } catch (IOException e) {
            throw new XMLStreamException(e);
        } catch (FastInfosetException e) {
            throw new XMLStreamException(e);
        }
    }

    public void onBeforeOctetBufferOverwrite() {
        if (_algorithmData != null) {
            _algorithmData = getTextAlgorithmBytesClone();
            _algorithmDataOffset = 0;
            _isAlgorithmDataCloned = true;
        }
    }

    // Faster access methods without checks

    public final int accessNamespaceCount() {
        return (_currentNamespaceAIIsEnd > 0) ? (_currentNamespaceAIIsEnd - _currentNamespaceAIIsStart) : 0;
    }

    public final String accessLocalName() {
        return _qualifiedName.localName;
    }

    public final String accessNamespaceURI() {
        return _qualifiedName.namespaceName;
    }

    public final String accessPrefix() {
        return _qualifiedName.prefix;
    }

    public final char[] accessTextCharacters() {
        if (_characters == null) return null;

        // we return a cloned version of _characters
        final char[] clonedCharacters = new char[_characters.length];
        System.arraycopy(_characters, 0, clonedCharacters, 0, _characters.length);
        return clonedCharacters;
    }

    public final int accessTextStart() {
        return _charactersOffset;
    }

    public final int accessTextLength() {
        return _charBufferLength;
    }

    //

    protected final void processDII() throws FastInfosetException, IOException {
        final int b = read();
        if (b > 0) {
            processDIIOptionalProperties(b);
        }
    }

    protected final void processDIIOptionalProperties(int b) throws FastInfosetException, IOException {
        // Optimize for the most common case
        if (b == EncodingConstants.DOCUMENT_INITIAL_VOCABULARY_FLAG) {
            decodeInitialVocabulary();
            return;
        }

        if ((b & EncodingConstants.DOCUMENT_ADDITIONAL_DATA_FLAG) > 0) {
            decodeAdditionalData();
            /*
             * TODO
             * how to report the additional data?
             */
        }

        if ((b & EncodingConstants.DOCUMENT_INITIAL_VOCABULARY_FLAG) > 0) {
            decodeInitialVocabulary();
        }

        if ((b & EncodingConstants.DOCUMENT_NOTATIONS_FLAG) > 0) {
            decodeNotations();
            /*
                try {
                    _dtdHandler.notationDecl(name, public_identifier, system_identifier);
                } catch (SAXException e) {
                    throw new IOException("NotationsDeclarationII");
                }
             */
        }

        if ((b & EncodingConstants.DOCUMENT_UNPARSED_ENTITIES_FLAG) > 0) {
            decodeUnparsedEntities();
            /*
                try {
                    _dtdHandler.unparsedEntityDecl(name, public_identifier, system_identifier, notation_name);
                } catch (SAXException e) {
                    throw new IOException("UnparsedEntitiesII");
                }
             */
        }

        if ((b & EncodingConstants.DOCUMENT_CHARACTER_ENCODING_SCHEME) > 0) {
            _characterEncodingScheme = decodeCharacterEncodingScheme();
        }

        if ((b & EncodingConstants.DOCUMENT_STANDALONE_FLAG) > 0) {
            boolean standalone = (read() > 0) ? true : false ;
            /*
             * TODO
             * how to report the standalone flag?
             */
        }

        if ((b & EncodingConstants.DOCUMENT_VERSION_FLAG) > 0) {
            decodeVersion();
            /*
             * TODO
             * how to report the standalone flag?
             */
        }
    }


    protected final void resizeNamespaceAIIs() {
        final String[] namespaceAIIsPrefix = new String[_namespaceAIIsIndex * 2];
        System.arraycopy(_namespaceAIIsPrefix, 0, namespaceAIIsPrefix, 0, _namespaceAIIsIndex);
        _namespaceAIIsPrefix = namespaceAIIsPrefix;

        final String[] namespaceAIIsNamespaceName = new String[_namespaceAIIsIndex * 2];
        System.arraycopy(_namespaceAIIsNamespaceName, 0, namespaceAIIsNamespaceName, 0, _namespaceAIIsIndex);
        _namespaceAIIsNamespaceName = namespaceAIIsNamespaceName;

        final int[] namespaceAIIsPrefixIndex = new int[_namespaceAIIsIndex * 2];
        System.arraycopy(_namespaceAIIsPrefixIndex, 0, namespaceAIIsPrefixIndex, 0, _namespaceAIIsIndex);
        _namespaceAIIsPrefixIndex = namespaceAIIsPrefixIndex;
    }

    protected final void processEIIWithNamespaces(boolean hasAttributes) throws FastInfosetException, IOException {
        if (++_prefixTable._declarationId == Integer.MAX_VALUE) {
            _prefixTable.clearDeclarationIds();
        }

        _currentNamespaceAIIsStart = _namespaceAIIsIndex;
        String prefix = "", namespaceName = "";
        int b = read();
        while ((b & EncodingConstants.NAMESPACE_ATTRIBUTE_MASK) == EncodingConstants.NAMESPACE_ATTRIBUTE) {
            if (_namespaceAIIsIndex == _namespaceAIIsPrefix.length) {
                resizeNamespaceAIIs();
            }

            switch (b & EncodingConstants.NAMESPACE_ATTRIBUTE_PREFIX_NAME_MASK) {
                // no prefix, no namespace
                // Undeclaration of default namespace
                case 0:
                    prefix = namespaceName =
                            _namespaceAIIsPrefix[_namespaceAIIsIndex] =
                            _namespaceAIIsNamespaceName[_namespaceAIIsIndex] = "";

                    _namespaceNameIndex = _prefixIndex = _namespaceAIIsPrefixIndex[_namespaceAIIsIndex++] = -1;
                    break;
                    // no prefix, namespace
                    // Declaration of default namespace
                case 1:
                    prefix = _namespaceAIIsPrefix[_namespaceAIIsIndex] = "";
                    namespaceName = _namespaceAIIsNamespaceName[_namespaceAIIsIndex] =
                            decodeIdentifyingNonEmptyStringOnFirstBitAsNamespaceName(false);

                    _prefixIndex = _namespaceAIIsPrefixIndex[_namespaceAIIsIndex++] = -1;
                    break;
                    // prefix, no namespace
                    // Undeclaration of namespace
                case 2:
                    prefix = _namespaceAIIsPrefix[_namespaceAIIsIndex] =
                            decodeIdentifyingNonEmptyStringOnFirstBitAsPrefix(false);
                    namespaceName = _namespaceAIIsNamespaceName[_namespaceAIIsIndex] = "";

                    _namespaceNameIndex = -1;
                    _namespaceAIIsPrefixIndex[_namespaceAIIsIndex++] = _prefixIndex;
                    break;
                    // prefix, namespace
                    // Declaration of prefixed namespace
                case 3:
                    prefix = _namespaceAIIsPrefix[_namespaceAIIsIndex] =
                            decodeIdentifyingNonEmptyStringOnFirstBitAsPrefix(true);
                    namespaceName = _namespaceAIIsNamespaceName[_namespaceAIIsIndex] =
                            decodeIdentifyingNonEmptyStringOnFirstBitAsNamespaceName(true);

                    _namespaceAIIsPrefixIndex[_namespaceAIIsIndex++] = _prefixIndex;
                    break;
            }

            // Push namespace declarations onto the stack
            _prefixTable.pushScopeWithPrefixEntry(prefix, namespaceName, _prefixIndex, _namespaceNameIndex);

            b = read();
        }
        if (b != EncodingConstants.TERMINATOR) {
            throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.EIInamespaceNameNotTerminatedCorrectly"));
        }
        _currentNamespaceAIIsEnd = _namespaceAIIsIndex;

        b = read();
        switch(DecoderStateTables.EII(b)) {
            case DecoderStateTables.EII_NO_AIIS_INDEX_SMALL:
                processEII(_elementNameTable._array[b], hasAttributes);
                break;
            case DecoderStateTables.EII_INDEX_MEDIUM:
                processEII(processEIIIndexMedium(b), hasAttributes);
                break;
            case DecoderStateTables.EII_INDEX_LARGE:
                processEII(processEIIIndexLarge(b), hasAttributes);
                break;
            case DecoderStateTables.EII_LITERAL:
            {
                final QualifiedName qn = processLiteralQualifiedName(
                        b & EncodingConstants.LITERAL_QNAME_PREFIX_NAMESPACE_NAME_MASK,
                        _elementNameTable.getNext());
                _elementNameTable.add(qn);
                processEII(qn, hasAttributes);
                break;
            }
            default:
                throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.IllegalStateDecodingEIIAfterAIIs"));
        }
    }

    protected final void processEII(QualifiedName name, boolean hasAttributes) throws FastInfosetException, IOException {
        if (_prefixTable._currentInScope[name.prefixIndex] != name.namespaceNameIndex) {
            throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.qnameOfEIINotInScope"));
        }

        _eventType = START_ELEMENT;
        _qualifiedName = name;

        if (_clearAttributes) {
            _attributes.clear();
            _clearAttributes = false;
        }

        if (hasAttributes) {
            processAIIs();
        }

        // Push element holder onto the stack
        _stackCount++;
        if (_stackCount == _qNameStack.length) {
            QualifiedName[] qNameStack = new QualifiedName[_qNameStack.length * 2];
            System.arraycopy(_qNameStack, 0, qNameStack, 0, _qNameStack.length);
            _qNameStack = qNameStack;

            int[] namespaceAIIsStartStack = new int[_namespaceAIIsStartStack.length * 2];
            System.arraycopy(_namespaceAIIsStartStack, 0, namespaceAIIsStartStack, 0, _namespaceAIIsStartStack.length);
            _namespaceAIIsStartStack = namespaceAIIsStartStack;

            int[] namespaceAIIsEndStack = new int[_namespaceAIIsEndStack.length * 2];
            System.arraycopy(_namespaceAIIsEndStack, 0, namespaceAIIsEndStack, 0, _namespaceAIIsEndStack.length);
            _namespaceAIIsEndStack = namespaceAIIsEndStack;
        }
        _qNameStack[_stackCount] = _qualifiedName;
        _namespaceAIIsStartStack[_stackCount] = _currentNamespaceAIIsStart;
        _namespaceAIIsEndStack[_stackCount] = _currentNamespaceAIIsEnd;
    }

    protected final void processAIIs() throws FastInfosetException, IOException {
        QualifiedName name;
        int b;
        String value;

        if (++_duplicateAttributeVerifier._currentIteration == Integer.MAX_VALUE) {
            _duplicateAttributeVerifier.clear();
        }

        _clearAttributes = true;
        boolean terminate = false;
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
                    name = processLiteralQualifiedName(
                            b & EncodingConstants.LITERAL_QNAME_PREFIX_NAMESPACE_NAME_MASK,
                            _attributeNameTable.getNext());
                    name.createAttributeValues(DuplicateAttributeVerifier.MAP_SIZE);
                    _attributeNameTable.add(name);
                    break;
                case DecoderStateTables.AII_TERMINATOR_DOUBLE:
                    _internalState = INTERNAL_STATE_START_ELEMENT_TERMINATE;
                case DecoderStateTables.AII_TERMINATOR_SINGLE:
                    terminate = true;
                    // AIIs have finished break out of loop
                    continue;
                default:
                    throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.decodingAIIs"));
            }

            // [normalized value] of AII

            if (name.prefixIndex > 0 && _prefixTable._currentInScope[name.prefixIndex] != name.namespaceNameIndex) {
                throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.AIIqNameNotInScope"));
            }

            _duplicateAttributeVerifier.checkForDuplicateAttribute(name.attributeHash, name.attributeId);

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
                    // Decode encoding algorithm integer
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
                    throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.decodingAIIValue"));
            }

        } while (!terminate);

        // Reset duplication attribute verfifier
        _duplicateAttributeVerifier._poolCurrent = _duplicateAttributeVerifier._poolHead;
    }

    protected final QualifiedName processEIIIndexMedium(int b) throws FastInfosetException, IOException {
        final int i = (((b & EncodingConstants.INTEGER_3RD_BIT_MEDIUM_MASK) << 8) | read())
        + EncodingConstants.INTEGER_3RD_BIT_SMALL_LIMIT;
        return _elementNameTable._array[i];
    }

    protected final QualifiedName processEIIIndexLarge(int b) throws FastInfosetException, IOException {
        int i;
        if ((b & EncodingConstants.INTEGER_3RD_BIT_LARGE_LARGE_FLAG) == 0x20) {
            // EII large index
            i = (((b & EncodingConstants.INTEGER_3RD_BIT_LARGE_MASK) << 16) | (read() << 8) | read())
            + EncodingConstants.INTEGER_3RD_BIT_MEDIUM_LIMIT;
        } else {
            // EII large large index
            i = (((read() & EncodingConstants.INTEGER_3RD_BIT_LARGE_LARGE_MASK) << 16) | (read() << 8) | read())
            + EncodingConstants.INTEGER_3RD_BIT_LARGE_LIMIT;
        }
        return _elementNameTable._array[i];
    }

    protected final QualifiedName processLiteralQualifiedName(int state, QualifiedName q)
    throws FastInfosetException, IOException {
        if (q == null) q = new QualifiedName();

        switch (state) {
            // no prefix, no namespace
            case 0:
                return q.set(
                        "",
                        "",
                        decodeIdentifyingNonEmptyStringOnFirstBit(_v.localName),
                        "",
                        0,
                        -1,
                        -1,
                        _identifier);
                // no prefix, namespace
            case 1:
                return q.set(
                        "",
                        decodeIdentifyingNonEmptyStringIndexOnFirstBitAsNamespaceName(false),
                        decodeIdentifyingNonEmptyStringOnFirstBit(_v.localName),
                        "",
                        0,
                        -1,
                        _namespaceNameIndex,
                        _identifier);
                // prefix, no namespace
            case 2:
                throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.qNameMissingNamespaceName"));
                // prefix, namespace
            case 3:
                return q.set(
                        decodeIdentifyingNonEmptyStringIndexOnFirstBitAsPrefix(true),
                        decodeIdentifyingNonEmptyStringIndexOnFirstBitAsNamespaceName(true),
                        decodeIdentifyingNonEmptyStringOnFirstBit(_v.localName),
                        "",
                        0,
                        _prefixIndex,
                        _namespaceNameIndex,
                        _identifier);
            default:
                throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.decodingEII"));
        }
    }

    protected final void processCommentII() throws FastInfosetException, IOException {
        _eventType = COMMENT;

        switch(decodeNonIdentifyingStringOnFirstBit()) {
            case NISTRING_STRING:
                if (_addToTable) {
                    _v.otherString.add(new CharArray(_charBuffer, 0, _charBufferLength, true));
                }

                _characters = _charBuffer;
                _charactersOffset = 0;
                break;
            case NISTRING_ENCODING_ALGORITHM:
                throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.commentIIAlgorithmNotSupported"));
            case NISTRING_INDEX:
                final CharArray ca = _v.otherString.get(_integer);

                _characters = ca.ch;
                _charactersOffset = ca.start;
                _charBufferLength = ca.length;
                break;
            case NISTRING_EMPTY_STRING:
                _characters = _charBuffer;
                _charactersOffset = 0;
                _charBufferLength = 0;
                break;
        }
    }

    protected final void processProcessingII() throws FastInfosetException, IOException {
        _eventType = PROCESSING_INSTRUCTION;

        _piTarget = decodeIdentifyingNonEmptyStringOnFirstBit(_v.otherNCName);

        switch(decodeNonIdentifyingStringOnFirstBit()) {
            case NISTRING_STRING:
                _piData = new String(_charBuffer, 0, _charBufferLength);
                if (_addToTable) {
                    _v.otherString.add(new CharArrayString(_piData));
                }
                break;
            case NISTRING_ENCODING_ALGORITHM:
                throw new FastInfosetException(CommonResourceBundle.getInstance().getString("message.processingIIWithEncodingAlgorithm"));
            case NISTRING_INDEX:
                _piData = _v.otherString.get(_integer).toString();
                break;
            case NISTRING_EMPTY_STRING:
                _piData = "";
                break;
        }
    }

    protected final void processUnexpandedEntityReference(final int b) throws FastInfosetException, IOException {
        _eventType = ENTITY_REFERENCE;

        /*
         * TODO
         * How does StAX report such events?
         */
        String entity_reference_name = decodeIdentifyingNonEmptyStringOnFirstBit(_v.otherNCName);

        String system_identifier = ((b & EncodingConstants.UNEXPANDED_ENTITY_SYSTEM_IDENTIFIER_FLAG) > 0)
        ? decodeIdentifyingNonEmptyStringOnFirstBit(_v.otherURI) : "";
        String public_identifier = ((b & EncodingConstants.UNEXPANDED_ENTITY_PUBLIC_IDENTIFIER_FLAG) > 0)
        ? decodeIdentifyingNonEmptyStringOnFirstBit(_v.otherURI) : "";

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "processUnexpandedEntityReference: entity_reference_name={0} system_identifier={1}public_identifier={2}",
                    new Object[]{entity_reference_name, system_identifier, public_identifier});
        }
    }

    protected final void processCIIEncodingAlgorithm(boolean addToTable) throws FastInfosetException, IOException {
        _algorithmData = _octetBuffer;
        _algorithmDataOffset = _octetBufferStart;
        _algorithmDataLength = _octetBufferLength;
        _isAlgorithmDataCloned = false;

        if (_algorithmId >= EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START) {
            _algorithmURI = _v.encodingAlgorithm.get(_algorithmId - EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START);
            if (_algorithmURI == null) {
                throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.URINotPresent", new Object[]{Integer.valueOf(_identifier)}));
            }
        } else if (_algorithmId > EncodingConstants.ENCODING_ALGORITHM_BUILTIN_END) {
            // Reserved built-in algorithms for future use
            // TODO should use sax property to decide if event will be
            // reported, allows for support through handler if required.
            throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.identifiers10to31Reserved"));
        }

        if (addToTable) {
            convertEncodingAlgorithmDataToCharacters();
            _characterContentChunkTable.add(_characters, _characters.length);
        }
    }

    protected final void processAIIEncodingAlgorithm(QualifiedName name, boolean addToTable) throws FastInfosetException, IOException {
        EncodingAlgorithm ea = null;
        String URI = null;
        if (_identifier >= EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START) {
            URI = _v.encodingAlgorithm.get(_identifier - EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START);
            if (URI == null) {
                throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.URINotPresent", new Object[]{Integer.valueOf(_identifier)}));
            } else if (_registeredEncodingAlgorithms != null) {
                ea = (EncodingAlgorithm)_registeredEncodingAlgorithms.get(URI);
            }
        } else if (_identifier >= EncodingConstants.ENCODING_ALGORITHM_BUILTIN_END) {
            if (_identifier == EncodingAlgorithmIndexes.CDATA) {
                throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.CDATAAlgorithmNotSupported"));
            }

            // Reserved built-in algorithms for future use
            // TODO should use sax property to decide if event will be
            // reported, allows for support through handler if required.
            throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().getString("message.identifiers10to31Reserved"));
        } else {
            ea = BuiltInEncodingAlgorithmFactory.getAlgorithm(_identifier);
        }

        Object algorithmData;

        if (ea != null) {
            algorithmData = ea.decodeFromBytes(_octetBuffer, _octetBufferStart,
                    _octetBufferLength);
        } else {
            final byte[] data = new byte[_octetBufferLength];
            System.arraycopy(_octetBuffer, _octetBufferStart, data, 0,
                    _octetBufferLength);
            algorithmData = data;
        }

        _attributes.addAttributeWithAlgorithmData(name, URI, _identifier,
                algorithmData);
        if (addToTable) {
            _attributeValueTable.add(_attributes.getValue(_attributes.getIndex(name.qName)));
        }
    }

    protected final void convertEncodingAlgorithmDataToCharacters() throws FastInfosetException, IOException {
        StringBuffer buffer = new StringBuffer();
        if (_algorithmId == EncodingAlgorithmIndexes.BASE64) {
            convertBase64AlorithmDataToCharacters(buffer);
        } else if (_algorithmId < EncodingConstants.ENCODING_ALGORITHM_BUILTIN_END) {
            Object array = BuiltInEncodingAlgorithmFactory.getAlgorithm(_algorithmId).
                    decodeFromBytes(_algorithmData, _algorithmDataOffset, _algorithmDataLength);
            BuiltInEncodingAlgorithmFactory.getAlgorithm(_algorithmId).convertToCharacters(array,  buffer);
        } else if (_algorithmId == EncodingAlgorithmIndexes.CDATA) {
            _octetBufferOffset -= _octetBufferLength;
            decodeUtf8StringIntoCharBuffer();

            _characters = _charBuffer;
            _charactersOffset = 0;
            return;
        } else if (_algorithmId >= EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START) {
            final EncodingAlgorithm ea = (EncodingAlgorithm)_registeredEncodingAlgorithms.get(_algorithmURI);
            if (ea != null) {
                final Object data = ea.decodeFromBytes(_octetBuffer, _octetBufferStart, _octetBufferLength);
                ea.convertToCharacters(data, buffer);
            } else {
                throw new EncodingAlgorithmException(
                        CommonResourceBundle.getInstance().getString("message.algorithmDataCannotBeReported"));
            }
        }

        _characters = new char[buffer.length()];
        buffer.getChars(0, buffer.length(), _characters, 0);
        _charactersOffset = 0;
        _charBufferLength = _characters.length;
    }

    /* If base64 data comes is chunks, bytes, which were cut to align 3,
     * from prev. base64 chunk are stored in this buffer */
    private byte[] base64TaleBytes = new byte[3];
    private int base64TaleLength;
    /*
     * Method converts _algorithmData to base64 encoded String
     * Counts with base64 data coming in chunks, aligning input chunks by 3,
     * avoiding double cloning, happening after possible peek, peek2 cloning by Base64 algorithm
     */
    protected void convertBase64AlorithmDataToCharacters(StringBuffer buffer) throws EncodingAlgorithmException, IOException {
        // How much new came data was serialized with prev. tale
        int afterTaleOffset = 0;

        if (base64TaleLength > 0) {
            // Serialize tale left from prev. chunk
            int bytesToCopy = Math.min(3 - base64TaleLength, _algorithmDataLength);
            System.arraycopy(_algorithmData, _algorithmDataOffset, base64TaleBytes, base64TaleLength, bytesToCopy);
            if (base64TaleLength + bytesToCopy == 3) {
                base64DecodeWithCloning(buffer, base64TaleBytes, 0, 3);
            } else if (!isBase64Follows()) {
                // End of text was read to temp array
                base64DecodeWithCloning(buffer, base64TaleBytes, 0, base64TaleLength + bytesToCopy);
                return;
            } else {
                // If the end of chunk fit to tmp array, but next chunk is expected
                base64TaleLength += bytesToCopy;
                return;
            }

            afterTaleOffset = bytesToCopy;
            base64TaleLength = 0;
        }

        int taleBytesRemaining = isBase64Follows() ? (_algorithmDataLength - afterTaleOffset) % 3 : 0;

        if (_isAlgorithmDataCloned) {
            base64DecodeWithoutCloning(buffer, _algorithmData, _algorithmDataOffset + afterTaleOffset,
                    _algorithmDataLength - afterTaleOffset - taleBytesRemaining);
        } else {
            base64DecodeWithCloning(buffer, _algorithmData, _algorithmDataOffset + afterTaleOffset,
                    _algorithmDataLength - afterTaleOffset - taleBytesRemaining);
        }

        if (taleBytesRemaining > 0) {
            System.arraycopy(_algorithmData, _algorithmDataOffset + _algorithmDataLength - taleBytesRemaining,
                    base64TaleBytes, 0, taleBytesRemaining);
            base64TaleLength = taleBytesRemaining;
        }
    }

    /*
     * Encodes incoming data to Base64 string.
     * Method performs additional input data cloning
     */
    private void base64DecodeWithCloning(StringBuffer dstBuffer, byte[] data, int offset, int length) throws EncodingAlgorithmException {
        Object array = BuiltInEncodingAlgorithmFactory.base64EncodingAlgorithm.
                decodeFromBytes(data, offset, length);
        BuiltInEncodingAlgorithmFactory.base64EncodingAlgorithm.convertToCharacters(array, dstBuffer);
    }

    /*
     * Encodes incoming data to Base64 string.
     * Avoids input data cloning
     */
    private void base64DecodeWithoutCloning(StringBuffer dstBuffer, byte[] data, int offset, int length) throws EncodingAlgorithmException {
        BuiltInEncodingAlgorithmFactory.base64EncodingAlgorithm.convertToCharacters(data, offset, length, dstBuffer);
    }


    /*
     * Looks ahead in InputStream, whether next data is Base64 chunk
     */
    public boolean isBase64Follows() throws IOException {
        // Process information item
        int b = peek(this);
        switch (DecoderStateTables.EII(b)) {
            case DecoderStateTables.CII_EA:
                int algorithmId = (b & 0x02) << 6;
                int b2 = peek2(this);
                algorithmId |= (b2 & 0xFC) >> 2;

                return algorithmId == EncodingAlgorithmIndexes.BASE64;
            default:
                return false;
        }
    }

    protected class NamespaceContextImpl implements NamespaceContext {
        public final String getNamespaceURI(String prefix) {
            return _prefixTable.getNamespaceFromPrefix(prefix);
        }

        public final String getPrefix(String namespaceURI) {
            return _prefixTable.getPrefixFromNamespace(namespaceURI);
        }

        public final Iterator getPrefixes(String namespaceURI) {
            return _prefixTable.getPrefixesFromNamespace(namespaceURI);
        }
    }

    public final String getNamespaceDecl(String prefix) {
        return _prefixTable.getNamespaceFromPrefix(prefix);
    }

    public final String getURI(String prefix) {
        return getNamespaceDecl(prefix);
    }

    public final Iterator getPrefixes() {
        return _prefixTable.getPrefixes();
    }

    public final AttributesHolder getAttributesHolder() {
        return _attributes;
    }

    public final void setManager(StAXManager manager) {
        _manager = manager;
    }

    final static String getEventTypeString(int eventType) {
        switch (eventType){
            case START_ELEMENT:
                return "START_ELEMENT";
            case END_ELEMENT:
                return "END_ELEMENT";
            case PROCESSING_INSTRUCTION:
                return "PROCESSING_INSTRUCTION";
            case CHARACTERS:
                return "CHARACTERS";
            case COMMENT:
                return "COMMENT";
            case START_DOCUMENT:
                return "START_DOCUMENT";
            case END_DOCUMENT:
                return "END_DOCUMENT";
            case ENTITY_REFERENCE:
                return "ENTITY_REFERENCE";
            case ATTRIBUTE:
                return "ATTRIBUTE";
            case DTD:
                return "DTD";
            case CDATA:
                return "CDATA";
        }
        return "UNKNOWN_EVENT_TYPE";
    }
}
