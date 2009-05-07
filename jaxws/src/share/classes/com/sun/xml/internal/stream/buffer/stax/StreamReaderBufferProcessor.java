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
 */
package com.sun.xml.internal.stream.buffer.stax;

import com.sun.xml.internal.stream.buffer.AbstractProcessor;
import com.sun.xml.internal.stream.buffer.AttributesHolder;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferMark;
import com.sun.xml.internal.org.jvnet.staxex.NamespaceContextEx;
import com.sun.xml.internal.org.jvnet.staxex.XMLStreamReaderEx;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A processor of a {@link XMLStreamBuffer} that reads the XML infoset as
 * {@link XMLStreamReader}.
 *
 * <p>
 * Because of {@link XMLStreamReader} design, this processor always produce
 * a full document infoset, even if the buffer just contains a fragment.
 *
 * <p>
 * When {@link XMLStreamBuffer} contains a multiple tree (AKA "forest"),
 * {@link XMLStreamReader} will behave as if there are multiple root elements
 * (so you'll see {@link #START_ELEMENT} event where you'd normally expect
 * {@link #END_DOCUMENT}.)
 *
 * @author Paul.Sandoz@Sun.Com
 * @author K.Venugopal@sun.com
 */
public class StreamReaderBufferProcessor extends AbstractProcessor implements XMLStreamReaderEx {
    private static final int CACHE_SIZE = 16;

    // Stack to hold element and namespace declaration information
    protected ElementStackEntry[] _stack = new ElementStackEntry[CACHE_SIZE];
    /** The top-most active entry of the {@link #_stack}. */
    protected ElementStackEntry _stackTop;
    /** The element depth that we are in. Used to determine when we are done with a tree. */
    protected int _depth;

    // Arrays to hold all namespace declarations
    /**
     * Namespace prefixes. Can be empty but not null.
     */
    protected String[] _namespaceAIIsPrefix = new String[CACHE_SIZE];
    protected String[] _namespaceAIIsNamespaceName = new String[CACHE_SIZE];
    protected int _namespaceAIIsEnd;

    // Internal namespace context implementation
    protected InternalNamespaceContext _nsCtx = new InternalNamespaceContext();

    // The current event type
    protected int _eventType;

    /**
     * Holder of the attributes.
     *
     * Be careful that this follows the SAX convention of using "" instead of null.
     */
    protected AttributesHolder _attributeCache;

    // Characters as a CharSequence
    protected CharSequence _charSequence;

    // Characters as a char array with offset and length
    protected char[] _characters;
    protected int _textOffset;
    protected int _textLen;

    protected String _piTarget;
    protected String _piData;

    //
    // Represents the parser state wrt the end of parsing.
    //
    /**
     * The parser is in the middle of parsing a document,
     * with no end in sight.
     */
    private static final int PARSING = 1;
    /**
     * The parser has already reported the {@link #END_ELEMENT},
     * and we are parsing a fragment. We'll report {@link #END_DOCUMENT}
     * next and be done.
     */
    private static final int PENDING_END_DOCUMENT = 2;
    /**
     * The parser has reported the {@link #END_DOCUMENT} event,
     * so we are really done parsing.
     */
    private static final int COMPLETED = 3;

    /**
     * True if processing is complete.
     */
    private int _completionState;

    public StreamReaderBufferProcessor() {
        for (int i=0; i < _stack.length; i++){
            _stack[i] = new ElementStackEntry();
        }

        _attributeCache = new AttributesHolder();
    }

    public StreamReaderBufferProcessor(XMLStreamBuffer buffer) throws XMLStreamException {
        this();
        setXMLStreamBuffer(buffer);
    }

    public void setXMLStreamBuffer(XMLStreamBuffer buffer) throws XMLStreamException {
        setBuffer(buffer,buffer.isFragment());

        _completionState = PARSING;
        _namespaceAIIsEnd = 0;
        _characters = null;
        _charSequence = null;
        _eventType = START_DOCUMENT;
    }

    /**
     * Does {@link #nextTag()} and if the parser moved to a new start tag,
     * returns a {@link XMLStreamBufferMark} that captures the infoset starting
     * from the newly discovered element.
     *
     * <p>
     * (Ideally we should have a method that works against the current position,
     * but the way the data structure is read makes this somewhat difficult.)
     *
     * This creates a new {@link XMLStreamBufferMark} that shares the underlying
     * data storage, thus it's fairly efficient.
     */
    public XMLStreamBuffer nextTagAndMark() throws XMLStreamException {
        while (true) {
            int s = peekStructure();
            if((s &TYPE_MASK)==T_ELEMENT) {
                // next is start element.
                Map<String,String> inscope = new HashMap<String, String>(_namespaceAIIsEnd);

                for (int i=0 ; i<_namespaceAIIsEnd; i++)
                    inscope.put(_namespaceAIIsPrefix[i],_namespaceAIIsNamespaceName[i]);

                XMLStreamBufferMark mark = new XMLStreamBufferMark(inscope, this);
                next();
                return mark;
            }

            if(next()==END_ELEMENT)
                return null;
        }
    }

    public Object getProperty(String name) {
        return null;
    }

    public int next() throws XMLStreamException {
        switch(_completionState) {
            case COMPLETED:
                throw new XMLStreamException("Invalid State");
            case PENDING_END_DOCUMENT:
                _namespaceAIIsEnd = 0;
                _completionState = COMPLETED;
                return _eventType = END_DOCUMENT;
        }

        // Pop the stack of elements
        // This is a post-processing operation
        // The stack of the element should be poppoed after
        // the END_ELEMENT event is returned so that the correct element name
        // and namespace scope is returned
        switch(_eventType) {
            case END_ELEMENT:
                if (_depth > 1) {
                    _depth--;
                    // _depth index is always set to the next free stack entry
                    // to push
                    popElementStack(_depth);
                } else if (_depth == 1) {
                    _depth--;
                }
        }

        _characters = null;
        _charSequence = null;
        while(true) {// loop only if we read STATE_DOCUMENT
            switch(readEiiState()) {
                case STATE_DOCUMENT:
                    // we'll always produce a full document, and we've already report START_DOCUMENT event.
                    // so simply skil this
                    continue;
                case STATE_ELEMENT_U_LN_QN: {
                    final String uri = readStructureString();
                    final String localName = readStructureString();
                    final String prefix = getPrefixFromQName(readStructureString());

                    processElement(prefix, uri, localName);
                    return _eventType = START_ELEMENT;
                }
                case STATE_ELEMENT_P_U_LN:
                    processElement(readStructureString(), readStructureString(), readStructureString());
                    return _eventType = START_ELEMENT;
                case STATE_ELEMENT_U_LN:
                    processElement(null, readStructureString(), readStructureString());
                    return _eventType = START_ELEMENT;
                case STATE_ELEMENT_LN:
                    processElement(null, null, readStructureString());
                    return _eventType = START_ELEMENT;
                case STATE_TEXT_AS_CHAR_ARRAY_SMALL:
                    _textLen = readStructure();
                    _textOffset = readContentCharactersBuffer(_textLen);
                    _characters = _contentCharactersBuffer;

                    return _eventType = CHARACTERS;
                case STATE_TEXT_AS_CHAR_ARRAY_MEDIUM:
                    _textLen = readStructure16();
                    _textOffset = readContentCharactersBuffer(_textLen);
                    _characters = _contentCharactersBuffer;

                    return _eventType = CHARACTERS;
                case STATE_TEXT_AS_CHAR_ARRAY_COPY:
                    _characters = readContentCharactersCopy();
                    _textLen = _characters.length;
                    _textOffset = 0;

                    return _eventType = CHARACTERS;
                case STATE_TEXT_AS_STRING:
                    _eventType = CHARACTERS;
                    _charSequence = readContentString();

                    return _eventType = CHARACTERS;
                case STATE_TEXT_AS_OBJECT:
                    _eventType = CHARACTERS;
                    _charSequence = (CharSequence)readContentObject();

                    return _eventType = CHARACTERS;
                case STATE_COMMENT_AS_CHAR_ARRAY_SMALL:
                    _textLen = readStructure();
                    _textOffset = readContentCharactersBuffer(_textLen);
                    _characters = _contentCharactersBuffer;

                    return _eventType = COMMENT;
                case STATE_COMMENT_AS_CHAR_ARRAY_MEDIUM:
                    _textLen = readStructure16();
                    _textOffset = readContentCharactersBuffer(_textLen);
                    _characters = _contentCharactersBuffer;

                    return _eventType = COMMENT;
                case STATE_COMMENT_AS_CHAR_ARRAY_COPY:
                    _characters = readContentCharactersCopy();
                    _textLen = _characters.length;
                    _textOffset = 0;

                    return _eventType = COMMENT;
                case STATE_COMMENT_AS_STRING:
                    _charSequence = readContentString();

                    return _eventType = COMMENT;
                case STATE_PROCESSING_INSTRUCTION:
                    _piTarget = readStructureString();
                    _piData = readStructureString();

                    return _eventType = PROCESSING_INSTRUCTION;
                case STATE_END:
                    if (_depth > 1) {
                        // normal case
                        return _eventType = END_ELEMENT;
                    } else if (_depth == 1) {
                        // this is the last end element for the current tree.
                        if (_fragmentMode) {
                            if(--_treeCount==0) // is this the last tree in the forest?
                                _completionState = PENDING_END_DOCUMENT;
                        }
                        return _eventType = END_ELEMENT;
                    } else {
                        // this only happens when we are processing a full document
                        // and we hit the "end of document" marker
                        _namespaceAIIsEnd = 0;
                        _completionState = COMPLETED;
                        return _eventType = END_DOCUMENT;
                    }
                default:
                    throw new XMLStreamException("Invalid State");
            }
            // this should be unreachable
        }
    }

    public final void require(int type, String namespaceURI, String localName) throws XMLStreamException {
        if( type != _eventType) {
            throw new XMLStreamException("");
        }
        if( namespaceURI != null && !namespaceURI.equals(getNamespaceURI())) {
            throw new XMLStreamException("");
        }
        if(localName != null && !localName.equals(getLocalName())) {
            throw new XMLStreamException("");
        }
    }

    public final String getElementTextTrim() throws XMLStreamException {
        // TODO getElementText* methods more efficiently
        return getElementText().trim();
    }

    public final String getElementText() throws XMLStreamException {
        if(_eventType != START_ELEMENT) {
            throw new XMLStreamException("");
        }

        next();
        return getElementText(true);
    }

    public final String getElementText(boolean startElementRead) throws XMLStreamException {
        if (!startElementRead) {
            throw new XMLStreamException("");
        }

        int eventType = getEventType();
        StringBuffer content = new StringBuffer();
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
                throw new XMLStreamException("");
            } else if(eventType == START_ELEMENT) {
                throw new XMLStreamException("");
            } else {
                throw new XMLStreamException("");
            }
            eventType = next();
        }
        return content.toString();
    }

    public final int nextTag() throws XMLStreamException {
        next();
        return nextTag(true);
    }

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
            throw new XMLStreamException("");
        }
        return eventType;
    }

    public final boolean hasNext() {
        return (_eventType != END_DOCUMENT);
    }

    public void close() throws XMLStreamException {
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

    public final boolean isWhiteSpace() {
        if(isCharacters() || (_eventType == CDATA)){
            char [] ch = this.getTextCharacters();
            int start = this.getTextStart();
            int length = this.getTextLength();
            for (int i = start; i < length; i++){
                final char c = ch[i];
                if (!(c == 0x20 || c == 0x9 || c == 0xD || c == 0xA))
                    return false;
            }
            return true;
        }
        return false;
    }

    public final String getAttributeValue(String namespaceURI, String localName) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException("");
        }

        if (namespaceURI == null) {
            // Set to the empty string to be compatible with the
            // org.xml.sax.Attributes interface
            namespaceURI = "";
        }

        return _attributeCache.getValue(namespaceURI, localName);
    }

    public final int getAttributeCount() {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException("");
        }

        return _attributeCache.getLength();
    }

    public final javax.xml.namespace.QName getAttributeName(int index) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException("");
        }

        final String prefix = _attributeCache.getPrefix(index);
        final String localName = _attributeCache.getLocalName(index);
        final String uri = _attributeCache.getURI(index);
        return new QName(uri,localName,prefix);
    }


    public final String getAttributeNamespace(int index) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException("");
        }
        return fixEmptyString(_attributeCache.getURI(index));
    }

    public final String getAttributeLocalName(int index) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException("");
        }
        return _attributeCache.getLocalName(index);
    }

    public final String getAttributePrefix(int index) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException("");
        }
        return fixEmptyString(_attributeCache.getPrefix(index));
    }

    public final String getAttributeType(int index) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException("");
        }
        return _attributeCache.getType(index);
    }

    public final String getAttributeValue(int index) {
        if (_eventType != START_ELEMENT) {
            throw new IllegalStateException("");
        }

        return _attributeCache.getValue(index);
    }

    public final boolean isAttributeSpecified(int index) {
        return false;
    }

    public final int getNamespaceCount() {
        if (_eventType == START_ELEMENT || _eventType == END_ELEMENT) {
            return _stackTop.namespaceAIIsEnd - _stackTop.namespaceAIIsStart;
        }

        throw new IllegalStateException("");
    }

    public final String getNamespacePrefix(int index) {
        if (_eventType == START_ELEMENT || _eventType == END_ELEMENT) {
            return _namespaceAIIsPrefix[_stackTop.namespaceAIIsStart + index];
        }

        throw new IllegalStateException("");
    }

    public final String getNamespaceURI(int index) {
        if (_eventType == START_ELEMENT || _eventType == END_ELEMENT) {
            return _namespaceAIIsNamespaceName[_stackTop.namespaceAIIsStart + index];
        }

        throw new IllegalStateException("");
    }

    public final String getNamespaceURI(String prefix) {
        return _nsCtx.getNamespaceURI(prefix);
    }

    public final NamespaceContextEx getNamespaceContext() {
        return _nsCtx;
    }

    public final int getEventType() {
        return _eventType;
    }

    public final String getText() {
        if (_characters != null) {
            String s = new String(_characters, _textOffset, _textLen);
            _charSequence = s;
            return s;
        } else if (_charSequence != null) {
            return _charSequence.toString();
        } else {
            throw new IllegalStateException();
        }
    }

    public final char[] getTextCharacters() {
        if (_characters != null) {
            return _characters;
        } else if (_charSequence != null) {
            // TODO try to avoid creation of a temporary String for some
            // CharSequence implementations
            _characters = _charSequence.toString().toCharArray();
            _textLen = _characters.length;
            _textOffset = 0;
            return _characters;
        } else {
            throw new IllegalStateException();
        }
    }

    public final int getTextStart() {
        if (_characters != null) {
            return _textOffset;
        } else if (_charSequence != null) {
            return 0;
        } else {
            throw new IllegalStateException();
        }
    }

    public final int getTextLength() {
        if (_characters != null) {
            return _textLen;
        } else if (_charSequence != null) {
            return _charSequence.length();
        } else {
            throw new IllegalStateException();
        }
    }

    public final int getTextCharacters(int sourceStart, char[] target,
                                       int targetStart, int length) throws XMLStreamException {
        if (_characters != null) {
        } else if (_charSequence != null) {
            _characters = _charSequence.toString().toCharArray();
            _textLen = _characters.length;
            _textOffset = 0;
        } else {
            throw new IllegalStateException("");
        }

        try {
            System.arraycopy(_characters, sourceStart, target,
                    targetStart, length);
            return length;
        } catch (IndexOutOfBoundsException e) {
            throw new XMLStreamException(e);
        }
    }

    private class CharSequenceImpl implements CharSequence {
        private final int _offset;
        private final int _length;

        CharSequenceImpl(int offset, int length) {
            _offset = offset;
            _length = length;
        }

        public int length() {
            return _length;
        }

        public char charAt(int index) {
            if (index >= 0 && index < _textLen) {
                return _characters[_textOffset + index];
            } else {
                throw new IndexOutOfBoundsException();
            }
        }

        public CharSequence subSequence(int start, int end) {
            final int length = end - start;
            if (end < 0 || start < 0 || end > length || start > end) {
                throw new IndexOutOfBoundsException();
            }

            return new CharSequenceImpl(_offset + start, length);
        }

        public String toString() {
            return new String(_characters, _offset, _length);
        }
    }

    public final CharSequence getPCDATA() {
        if (_characters != null) {
            return new CharSequenceImpl(_textOffset, _textLen);
        } else if (_charSequence != null) {
            return _charSequence;
        } else {
            throw new IllegalStateException();
        }
    }

    public final String getEncoding() {
        return "UTF-8";
    }

    public final boolean hasText() {
        return (_characters != null || _charSequence != null);
    }

    public final Location getLocation() {
        return new DummyLocation();
    }

    public final boolean hasName() {
        return (_eventType == START_ELEMENT || _eventType == END_ELEMENT);
    }

    public final QName getName() {
        return _stackTop.getQName();
    }

    public final String getLocalName() {
        return _stackTop.localName;
    }

    public final String getNamespaceURI() {
        return _stackTop.uri;
    }

    public final String getPrefix() {
        return _stackTop.prefix;

    }

    public final String getVersion() {
        return "1.0";
    }

    public final boolean isStandalone() {
        return false;
    }

    public final boolean standaloneSet() {
        return false;
    }

    public final String getCharacterEncodingScheme() {
        return "UTF-8";
    }

    public final String getPITarget() {
        if (_eventType == PROCESSING_INSTRUCTION) {
            return _piTarget;
        }
        throw new IllegalStateException("");
    }

    public final String getPIData() {
        if (_eventType == PROCESSING_INSTRUCTION) {
            return _piData;
        }
        throw new IllegalStateException("");
    }

    protected void processElement(String prefix, String uri, String localName) {
        pushElementStack();
        _stackTop.set(prefix, uri, localName);

        _attributeCache.clear();

        int item = peekStructure();
        if ((item & TYPE_MASK) == T_NAMESPACE_ATTRIBUTE) {
            // Skip the namespace declarations on the element
            // they will have been added already
            item = processNamespaceAttributes(item);
        }
        if ((item & TYPE_MASK) == T_ATTRIBUTE) {
            processAttributes(item);
        }
    }

    private void resizeNamespaceAttributes() {
        final String[] namespaceAIIsPrefix = new String[_namespaceAIIsEnd * 2];
        System.arraycopy(_namespaceAIIsPrefix, 0, namespaceAIIsPrefix, 0, _namespaceAIIsEnd);
        _namespaceAIIsPrefix = namespaceAIIsPrefix;

        final String[] namespaceAIIsNamespaceName = new String[_namespaceAIIsEnd * 2];
        System.arraycopy(_namespaceAIIsNamespaceName, 0, namespaceAIIsNamespaceName, 0, _namespaceAIIsEnd);
        _namespaceAIIsNamespaceName = namespaceAIIsNamespaceName;
    }

    private int processNamespaceAttributes(int item){
        _stackTop.namespaceAIIsStart = _namespaceAIIsEnd;

        do {
            if (_namespaceAIIsEnd == _namespaceAIIsPrefix.length) {
                resizeNamespaceAttributes();
            }

            switch(_niiStateTable[item]){
                case STATE_NAMESPACE_ATTRIBUTE:
                    // Undeclaration of default namespace
                    _namespaceAIIsPrefix[_namespaceAIIsEnd] =
                    _namespaceAIIsNamespaceName[_namespaceAIIsEnd++] = "";
                    break;
                case STATE_NAMESPACE_ATTRIBUTE_P:
                    // Undeclaration of namespace
                    _namespaceAIIsPrefix[_namespaceAIIsEnd] = readStructureString();
                    _namespaceAIIsNamespaceName[_namespaceAIIsEnd++] = "";
                    break;
                case STATE_NAMESPACE_ATTRIBUTE_P_U:
                    // Declaration with prefix
                    _namespaceAIIsPrefix[_namespaceAIIsEnd] = readStructureString();
                    _namespaceAIIsNamespaceName[_namespaceAIIsEnd++] = readStructureString();
                    break;
                case STATE_NAMESPACE_ATTRIBUTE_U:
                    // Default declaration
                    _namespaceAIIsPrefix[_namespaceAIIsEnd] = "";
                    _namespaceAIIsNamespaceName[_namespaceAIIsEnd++] = readStructureString();
                    break;
            }
            readStructure();

            item = peekStructure();
        } while((item & TYPE_MASK) == T_NAMESPACE_ATTRIBUTE);

        _stackTop.namespaceAIIsEnd = _namespaceAIIsEnd;

        return item;
    }

    private void processAttributes(int item){
        do {
            switch(_aiiStateTable[item]){
                case STATE_ATTRIBUTE_U_LN_QN: {
                    final String uri = readStructureString();
                    final String localName = readStructureString();
                    final String prefix = getPrefixFromQName(readStructureString());
                    _attributeCache.addAttributeWithPrefix(prefix, uri, localName, readStructureString(), readContentString());
                    break;
                }
                case STATE_ATTRIBUTE_P_U_LN:
                    _attributeCache.addAttributeWithPrefix(readStructureString(), readStructureString(), readStructureString(), readStructureString(), readContentString());
                    break;
                case STATE_ATTRIBUTE_U_LN:
                    // _attributeCache follows SAX convention
                    _attributeCache.addAttributeWithPrefix("", readStructureString(), readStructureString(), readStructureString(), readContentString());
                    break;
                case STATE_ATTRIBUTE_LN: {
                    _attributeCache.addAttributeWithPrefix("", "", readStructureString(), readStructureString(), readContentString());
                    break;
                }
            }
            readStructure();

            item = peekStructure();
        } while((item & TYPE_MASK) == T_ATTRIBUTE);
    }

    private void pushElementStack() {
        if (_depth == _stack.length) {
            // resize stack
            ElementStackEntry [] tmp = _stack;
            _stack = new ElementStackEntry[_stack.length * 3 /2 + 1];
            System.arraycopy(tmp, 0, _stack, 0, tmp.length);
            for (int i = tmp.length; i < _stack.length; i++){
                _stack[i] = new ElementStackEntry();
            }
        }

        _stackTop = _stack[_depth++];
    }

    private void popElementStack(int depth) {
        // _depth is checked outside this method
        _stackTop = _stack[depth - 1];
        // Move back the position of the namespace index
        _namespaceAIIsEnd = _stack[depth].namespaceAIIsStart;
    }

    private final class ElementStackEntry {
        /**
         * Prefix.
         * Just like everywhere else in StAX, this can be null but can't be empty.
         */
        String prefix;
        /**
         * Namespace URI.
         * Just like everywhere else in StAX, this can be null but can't be empty.
         */
        String uri;
        String localName;
        QName qname;

        // Start and end of namespace declarations
        // in namespace declaration arrays
        int namespaceAIIsStart;
        int namespaceAIIsEnd;

        public void set(String prefix, String uri, String localName) {
            this.prefix = prefix;
            this.uri = uri;
            this.localName = localName;
            this.qname = null;

            this.namespaceAIIsStart = this.namespaceAIIsEnd = StreamReaderBufferProcessor.this._namespaceAIIsEnd;
        }

        public QName getQName() {
            if (qname == null) {
                qname = new QName(fixNull(uri), localName, fixNull(prefix));
            }
            return qname;
        }

        private String fixNull(String s) {
            return (s == null) ? "" : s;
        }
    }

    private final class InternalNamespaceContext implements NamespaceContextEx {
        @SuppressWarnings({"StringEquality"})
        public String getNamespaceURI(String prefix) {
            if (prefix == null) {
                throw new IllegalArgumentException("Prefix cannot be null");
            }

            /*
             * If the buffer was created using string interning
             * intern the prefix and check for reference equality
             * rather than using String.equals();
             */
            if (_stringInterningFeature) {
                prefix = prefix.intern();

                // Find the most recently declared prefix
                for (int i = _namespaceAIIsEnd - 1; i >=0; i--) {
                    if (prefix == _namespaceAIIsPrefix[i]) {
                        return _namespaceAIIsNamespaceName[i];
                    }
                }
            } else {
                // Find the most recently declared prefix
                for (int i = _namespaceAIIsEnd - 1; i >=0; i--) {
                    if (prefix.equals(_namespaceAIIsPrefix[i])) {
                        return _namespaceAIIsNamespaceName[i];
                    }
                }
            }

            // Check for XML-based prefixes
            if (prefix.equals(XMLConstants.XML_NS_PREFIX)) {
                return XMLConstants.XML_NS_URI;
            } else if (prefix.equals(XMLConstants.XMLNS_ATTRIBUTE)) {
                return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
            }

            return null;
        }

        public String getPrefix(String namespaceURI) {
            final Iterator i = getPrefixes(namespaceURI);
            if (i.hasNext()) {
                return (String)i.next();
            } else {
                return null;
            }
        }

        public Iterator getPrefixes(final String namespaceURI) {
            if (namespaceURI == null){
                throw new IllegalArgumentException("NamespaceURI cannot be null");
            }

            if (namespaceURI.equals(XMLConstants.XML_NS_URI)) {
                return Collections.singletonList(XMLConstants.XML_NS_PREFIX).iterator();
            } else if (namespaceURI.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
                return Collections.singletonList(XMLConstants.XMLNS_ATTRIBUTE).iterator();
            }

            return new Iterator() {
                private int i = _namespaceAIIsEnd - 1;
                private boolean requireFindNext = true;
                private String p;

                private String findNext() {
                    while(i >= 0) {
                        // Find the most recently declared namespace
                        if (namespaceURI.equals(_namespaceAIIsNamespaceName[i])) {
                            // Find the most recently declared prefix of the namespace
                            // and check if the prefix is in scope with that namespace
                            if (getNamespaceURI(_namespaceAIIsPrefix[i]).equals(
                                    _namespaceAIIsNamespaceName[i])) {
                                return p = _namespaceAIIsPrefix[i];
                            }
                        }
                        i--;
                    }
                    return p = null;
                }

                public boolean hasNext() {
                    if (requireFindNext) {
                        findNext();
                        requireFindNext = false;
                    }
                    return (p != null);
                }

                public Object next() {
                    if (requireFindNext) {
                        findNext();
                    }
                    requireFindNext = true;

                    if (p == null) {
                        throw new NoSuchElementException();
                    }

                    return p;
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        private class BindingImpl implements NamespaceContextEx.Binding {
            final String _prefix;
            final String _namespaceURI;

            BindingImpl(String prefix, String namespaceURI) {
                _prefix = prefix;
                _namespaceURI = namespaceURI;
            }

            public String getPrefix() {
                return _prefix;
            }

            public String getNamespaceURI() {
                return _namespaceURI;
            }
        }

        public Iterator<NamespaceContextEx.Binding> iterator() {
            return new Iterator<NamespaceContextEx.Binding>() {
                private final int end = _namespaceAIIsEnd - 1;
                private int current = end;
                private boolean requireFindNext = true;
                private NamespaceContextEx.Binding namespace;

                private NamespaceContextEx.Binding findNext() {
                    while(current >= 0) {
                        final String prefix = _namespaceAIIsPrefix[current];

                        // Find if the current prefix occurs more recently
                        // If so then it is not in scope
                        int i = end;
                        for (;i > current; i--) {
                            if (prefix.equals(_namespaceAIIsPrefix[i])) {
                                break;
                            }
                        }
                        if (i == current--) {
                            // The current prefix is in-scope
                            return namespace = new BindingImpl(prefix, _namespaceAIIsNamespaceName[current]);
                        }
                    }
                    return namespace = null;
                }

                public boolean hasNext() {
                    if (requireFindNext) {
                        findNext();
                        requireFindNext = false;
                    }
                    return (namespace != null);
                }

                public NamespaceContextEx.Binding next() {
                    if (requireFindNext) {
                        findNext();
                    }
                    requireFindNext = true;

                    if (namespace == null) {
                        throw new NoSuchElementException();
                    }

                    return namespace;
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    private class DummyLocation  implements Location {
        public int getLineNumber() {
            return -1;
        }

        public int getColumnNumber() {
            return -1;
        }

        public int getCharacterOffset() {
            return -1;
        }

        public String getPublicId() {
            return null;
        }

        public String getSystemId() {
            return _buffer.getSystemId();
        }
    }

    private static String fixEmptyString(String s) {
        // s must not be null, so no need to check for that. that would be bug.
        if(s.length()==0)   return null;
        else                return s;
    }

}
