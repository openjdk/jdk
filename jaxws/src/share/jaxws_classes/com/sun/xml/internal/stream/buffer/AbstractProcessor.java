/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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
 */

package com.sun.xml.internal.stream.buffer;

/**
 * Base class for classes that processes {@link XMLStreamBuffer}
 * and produces infoset in API-specific form.
 */
public abstract class AbstractProcessor extends AbstractCreatorProcessor {
    protected  static final int STATE_ILLEGAL                       = 0;

    protected  static final int STATE_DOCUMENT                      = 1;
    protected  static final int STATE_DOCUMENT_FRAGMENT             = 2;
    protected  static final int STATE_ELEMENT_U_LN_QN               = 3;
    protected  static final int STATE_ELEMENT_P_U_LN                = 4;
    protected  static final int STATE_ELEMENT_U_LN                  = 5;
    protected  static final int STATE_ELEMENT_LN                    = 6;
    protected  static final int STATE_TEXT_AS_CHAR_ARRAY_SMALL      = 7;
    protected  static final int STATE_TEXT_AS_CHAR_ARRAY_MEDIUM     = 8;
    protected  static final int STATE_TEXT_AS_CHAR_ARRAY_COPY       = 9;
    protected  static final int STATE_TEXT_AS_STRING                = 10;
    protected  static final int STATE_TEXT_AS_OBJECT                = 11;
    protected  static final int STATE_COMMENT_AS_CHAR_ARRAY_SMALL   = 12;
    protected  static final int STATE_COMMENT_AS_CHAR_ARRAY_MEDIUM  = 13;
    protected  static final int STATE_COMMENT_AS_CHAR_ARRAY_COPY    = 14;
    protected  static final int STATE_COMMENT_AS_STRING             = 15;
    protected  static final int STATE_PROCESSING_INSTRUCTION        = 16;
    protected  static final int STATE_END                           = 17;
    private  static final int[] _eiiStateTable = new int[256];

    protected  static final int STATE_NAMESPACE_ATTRIBUTE           = 1;
    protected  static final int STATE_NAMESPACE_ATTRIBUTE_P         = 2;
    protected  static final int STATE_NAMESPACE_ATTRIBUTE_P_U       = 3;
    protected  static final int STATE_NAMESPACE_ATTRIBUTE_U         = 4;
    private  static final int[] _niiStateTable = new int[256];

    protected  static final int STATE_ATTRIBUTE_U_LN_QN             = 1;
    protected  static final int STATE_ATTRIBUTE_P_U_LN              = 2;
    protected  static final int STATE_ATTRIBUTE_U_LN                = 3;
    protected  static final int STATE_ATTRIBUTE_LN                  = 4;
    protected  static final int STATE_ATTRIBUTE_U_LN_QN_OBJECT      = 5;
    protected  static final int STATE_ATTRIBUTE_P_U_LN_OBJECT       = 6;
    protected  static final int STATE_ATTRIBUTE_U_LN_OBJECT         = 7;
    protected  static final int STATE_ATTRIBUTE_LN_OBJECT           = 8;
    private  static final int[] _aiiStateTable = new int[256];

    static {
        /*
         * Create a state table from information items and options.
         * The swtich statement using such states will often generate a more
         * efficient byte code representation that can be hotspotted using
         * jump tables.
         */
        _eiiStateTable[T_DOCUMENT] = STATE_DOCUMENT;
        _eiiStateTable[T_DOCUMENT_FRAGMENT] = STATE_DOCUMENT_FRAGMENT;
        _eiiStateTable[T_ELEMENT_U_LN_QN] = STATE_ELEMENT_U_LN_QN;
        _eiiStateTable[T_ELEMENT_P_U_LN] = STATE_ELEMENT_P_U_LN;
        _eiiStateTable[T_ELEMENT_U_LN] = STATE_ELEMENT_U_LN;
        _eiiStateTable[T_ELEMENT_LN] = STATE_ELEMENT_LN;
        _eiiStateTable[T_TEXT_AS_CHAR_ARRAY_SMALL] = STATE_TEXT_AS_CHAR_ARRAY_SMALL;
        _eiiStateTable[T_TEXT_AS_CHAR_ARRAY_MEDIUM] = STATE_TEXT_AS_CHAR_ARRAY_MEDIUM;
        _eiiStateTable[T_TEXT_AS_CHAR_ARRAY_COPY] = STATE_TEXT_AS_CHAR_ARRAY_COPY;
        _eiiStateTable[T_TEXT_AS_STRING] = STATE_TEXT_AS_STRING;
        _eiiStateTable[T_TEXT_AS_OBJECT] = STATE_TEXT_AS_OBJECT;
        _eiiStateTable[T_COMMENT_AS_CHAR_ARRAY_SMALL] = STATE_COMMENT_AS_CHAR_ARRAY_SMALL;
        _eiiStateTable[T_COMMENT_AS_CHAR_ARRAY_MEDIUM] = STATE_COMMENT_AS_CHAR_ARRAY_MEDIUM;
        _eiiStateTable[T_COMMENT_AS_CHAR_ARRAY_COPY] = STATE_COMMENT_AS_CHAR_ARRAY_COPY;
        _eiiStateTable[T_COMMENT_AS_STRING] = STATE_COMMENT_AS_STRING;
        _eiiStateTable[T_PROCESSING_INSTRUCTION] = STATE_PROCESSING_INSTRUCTION;
        _eiiStateTable[T_END] = STATE_END;

        _niiStateTable[T_NAMESPACE_ATTRIBUTE] = STATE_NAMESPACE_ATTRIBUTE;
        _niiStateTable[T_NAMESPACE_ATTRIBUTE_P] = STATE_NAMESPACE_ATTRIBUTE_P;
        _niiStateTable[T_NAMESPACE_ATTRIBUTE_P_U] = STATE_NAMESPACE_ATTRIBUTE_P_U;
        _niiStateTable[T_NAMESPACE_ATTRIBUTE_U] = STATE_NAMESPACE_ATTRIBUTE_U;

        _aiiStateTable[T_ATTRIBUTE_U_LN_QN] = STATE_ATTRIBUTE_U_LN_QN;
        _aiiStateTable[T_ATTRIBUTE_P_U_LN] = STATE_ATTRIBUTE_P_U_LN;
        _aiiStateTable[T_ATTRIBUTE_U_LN] = STATE_ATTRIBUTE_U_LN;
        _aiiStateTable[T_ATTRIBUTE_LN] = STATE_ATTRIBUTE_LN;
        _aiiStateTable[T_ATTRIBUTE_U_LN_QN_OBJECT] = STATE_ATTRIBUTE_U_LN_QN_OBJECT;
        _aiiStateTable[T_ATTRIBUTE_P_U_LN_OBJECT] = STATE_ATTRIBUTE_P_U_LN_OBJECT;
        _aiiStateTable[T_ATTRIBUTE_U_LN_OBJECT] = STATE_ATTRIBUTE_U_LN_OBJECT;
        _aiiStateTable[T_ATTRIBUTE_LN_OBJECT] = STATE_ATTRIBUTE_LN_OBJECT;
    }

    protected XMLStreamBuffer _buffer;

    /**
     * True if this processor should create a fragment of XML, without the start/end document markers.
     */
    protected boolean _fragmentMode;

    protected boolean _stringInterningFeature = false;

    /**
     * Number of remaining XML element trees that should be visible
     * through this {@link AbstractProcessor}.
     */
    protected int _treeCount;

    /**
     * @deprecated
     *      Use {@link #setBuffer(XMLStreamBuffer, boolean)}
     */
    protected final void setBuffer(XMLStreamBuffer buffer) {
        setBuffer(buffer,buffer.isFragment());
    }
    protected final void setBuffer(XMLStreamBuffer buffer, boolean fragmentMode) {
        _buffer = buffer;
        _fragmentMode = fragmentMode;

        _currentStructureFragment = _buffer.getStructure();
        _structure = _currentStructureFragment.getArray();
        _structurePtr = _buffer.getStructurePtr();

        _currentStructureStringFragment = _buffer.getStructureStrings();
        _structureStrings = _currentStructureStringFragment.getArray();
        _structureStringsPtr = _buffer.getStructureStringsPtr();

        _currentContentCharactersBufferFragment = _buffer.getContentCharactersBuffer();
        _contentCharactersBuffer = _currentContentCharactersBufferFragment.getArray();
        _contentCharactersBufferPtr = _buffer.getContentCharactersBufferPtr();

        _currentContentObjectFragment = _buffer.getContentObjects();
        _contentObjects = _currentContentObjectFragment.getArray();
        _contentObjectsPtr = _buffer.getContentObjectsPtr();

        _stringInterningFeature = _buffer.hasInternedStrings();
        _treeCount = _buffer.treeCount;
    }

    protected final int peekStructure() {
        if (_structurePtr < _structure.length) {
            return _structure[_structurePtr] & 255;
        }

        return readFromNextStructure(0);
    }

    protected final int readStructure() {
        if (_structurePtr < _structure.length) {
            return _structure[_structurePtr++] & 255;
        }

        return readFromNextStructure(1);
    }

    protected final int readEiiState() {
        return _eiiStateTable[readStructure()];
    }

    protected static int getEIIState(int item) {
        return _eiiStateTable[item];
    }

    protected static int getNIIState(int item) {
        return _niiStateTable[item];
    }

    protected static int getAIIState(int item) {
        return _aiiStateTable[item];
    }

    protected final int readStructure16() {
        return (readStructure() << 8) | readStructure();
    }

    private int readFromNextStructure(int v) {
        _structurePtr = v;
        _currentStructureFragment = _currentStructureFragment.getNext();
        _structure = _currentStructureFragment.getArray();
        return _structure[0] & 255;
    }

    protected final String readStructureString() {
        if (_structureStringsPtr < _structureStrings.length) {
            return _structureStrings[_structureStringsPtr++];
        }

        _structureStringsPtr = 1;
        _currentStructureStringFragment = _currentStructureStringFragment.getNext();
        _structureStrings = _currentStructureStringFragment.getArray();
        return _structureStrings[0];
    }

    protected final String readContentString() {
        return (String)readContentObject();
    }

    protected final char[] readContentCharactersCopy() {
        return (char[])readContentObject();
    }

    protected final int readContentCharactersBuffer(int length) {
        if (_contentCharactersBufferPtr + length < _contentCharactersBuffer.length) {
            final int start = _contentCharactersBufferPtr;
            _contentCharactersBufferPtr += length;
            return start;
        }

        _contentCharactersBufferPtr = length;
        _currentContentCharactersBufferFragment = _currentContentCharactersBufferFragment.getNext();
        _contentCharactersBuffer = _currentContentCharactersBufferFragment.getArray();
        return 0;
    }

    protected final Object readContentObject() {
        if (_contentObjectsPtr < _contentObjects.length) {
            return _contentObjects[_contentObjectsPtr++];
        }

        _contentObjectsPtr = 1;
        _currentContentObjectFragment = _currentContentObjectFragment.getNext();
        _contentObjects = _currentContentObjectFragment.getArray();
        return _contentObjects[0];
    }

    protected final StringBuilder _qNameBuffer = new StringBuilder();

    protected final String getQName(String prefix, String localName) {
        _qNameBuffer.append(prefix).append(':').append(localName);
        final String qName = _qNameBuffer.toString();
        _qNameBuffer.setLength(0);
        return (_stringInterningFeature) ? qName.intern() : qName;
    }

    protected final String getPrefixFromQName(String qName) {
        int pIndex = qName.indexOf(':');
        if (_stringInterningFeature) {
            return (pIndex != -1) ? qName.substring(0,pIndex).intern() : "";
        } else {
            return (pIndex != -1) ? qName.substring(0,pIndex) : "";
        }
    }
}
