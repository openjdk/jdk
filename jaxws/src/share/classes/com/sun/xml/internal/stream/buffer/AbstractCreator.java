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
package com.sun.xml.internal.stream.buffer;

/**
 * Base class for classes that creates {@link MutableXMLStreamBuffer}
 * and from infoset in API-specific form.
 */
public class AbstractCreator extends AbstractCreatorProcessor {

    protected MutableXMLStreamBuffer _buffer;

    public void setXMLStreamBuffer(MutableXMLStreamBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer cannot be null");
        }
        setBuffer(buffer);
    }

    public MutableXMLStreamBuffer getXMLStreamBuffer() {
        return _buffer;
    }


    protected final void createBuffer() {
        setBuffer(new MutableXMLStreamBuffer());
    }

    /**
     * Should be called whenever a new tree is stored on the buffer.
     */
    protected final void increaseTreeCount() {
        _buffer.treeCount++;
    }

    protected final void setBuffer(MutableXMLStreamBuffer buffer) {
        _buffer = buffer;

        _currentStructureFragment = _buffer.getStructure();
        _structure = _currentStructureFragment.getArray();
        _structurePtr = 0;

        _currentStructureStringFragment = _buffer.getStructureStrings();
        _structureStrings = _currentStructureStringFragment.getArray();
        _structureStringsPtr = 0;

        _currentContentCharactersBufferFragment = _buffer.getContentCharactersBuffer();
        _contentCharactersBuffer = _currentContentCharactersBufferFragment.getArray();
        _contentCharactersBufferPtr = 0;

        _currentContentObjectFragment = _buffer.getContentObjects();
        _contentObjects = _currentContentObjectFragment.getArray();
        _contentObjectsPtr = 0;
    }

    protected final void setHasInternedStrings(boolean hasInternedStrings) {
        _buffer.setHasInternedStrings(hasInternedStrings);
    }

    protected final void storeStructure(int b) {
        _structure[_structurePtr++] = (byte)b;
        if (_structurePtr == _structure.length) {
            resizeStructure();
        }
    }

    protected final void resizeStructure() {
        _structurePtr = 0;
        if (_currentStructureFragment.getNext() != null) {
            _currentStructureFragment = _currentStructureFragment.getNext();
            _structure = _currentStructureFragment.getArray();
        } else {
            _structure = new byte[_structure.length];
            _currentStructureFragment = new FragmentedArray(_structure, _currentStructureFragment);
        }
    }

    protected final void storeStructureString(String s) {
        _structureStrings[_structureStringsPtr++] = s;
        if (_structureStringsPtr == _structureStrings.length) {
            resizeStructureStrings();
        }
    }

    protected final void resizeStructureStrings() {
        _structureStringsPtr = 0;
        if (_currentStructureStringFragment.getNext() != null) {
            _currentStructureStringFragment = _currentStructureStringFragment.getNext();
            _structureStrings = _currentStructureStringFragment.getArray();
        } else {
            _structureStrings = new String[_structureStrings.length];
            _currentStructureStringFragment = new FragmentedArray(_structureStrings, _currentStructureStringFragment);
        }
    }

    protected final void storeContentString(String s) {
        storeContentObject(s);
    }

    protected final void storeContentCharacters(int type, char[] ch, int start, int length) {
        if (_contentCharactersBufferPtr + length >= _contentCharactersBuffer.length) {
            if (length >= 512) {
                storeStructure(type | CONTENT_TYPE_CHAR_ARRAY_COPY);
                storeContentCharactersCopy(ch, start, length);
                return;
            }
            resizeContentCharacters();
        }

        if (length < CHAR_ARRAY_LENGTH_SMALL_SIZE) {
            storeStructure(type | CHAR_ARRAY_LENGTH_SMALL);
            storeStructure(length);
            System.arraycopy(ch, start, _contentCharactersBuffer, _contentCharactersBufferPtr, length);
            _contentCharactersBufferPtr += length;
        } else if (length < CHAR_ARRAY_LENGTH_MEDIUM_SIZE) {
            storeStructure(type | CHAR_ARRAY_LENGTH_MEDIUM);
            storeStructure(length >> 8);
            storeStructure(length & 255);
            System.arraycopy(ch, start, _contentCharactersBuffer, _contentCharactersBufferPtr, length);
            _contentCharactersBufferPtr += length;
        } else {
            storeStructure(type | CONTENT_TYPE_CHAR_ARRAY_COPY);
            storeContentCharactersCopy(ch, start, length);
        }
    }

    protected final void resizeContentCharacters() {
        _contentCharactersBufferPtr = 0;
        if (_currentContentCharactersBufferFragment.getNext() != null) {
            _currentContentCharactersBufferFragment = _currentContentCharactersBufferFragment.getNext();
            _contentCharactersBuffer = _currentContentCharactersBufferFragment.getArray();
        } else {
            _contentCharactersBuffer = new char[_contentCharactersBuffer.length];
            _currentContentCharactersBufferFragment = new FragmentedArray(_contentCharactersBuffer,
                    _currentContentCharactersBufferFragment);
        }
    }

    protected final void storeContentCharactersCopy(char[] ch, int start, int length) {
        char[] copyOfCh = new char[length];
        System.arraycopy(ch, start, copyOfCh, 0, length);
        storeContentObject(copyOfCh);
    }

    protected final Object peekAtContentObject() {
        return _contentObjects[_contentObjectsPtr];
    }

    protected final void storeContentObject(Object s) {
        _contentObjects[_contentObjectsPtr++] = s;
        if (_contentObjectsPtr == _contentObjects.length) {
            resizeContentObjects();
        }
    }

    protected final void resizeContentObjects() {
        _contentObjectsPtr = 0;
        if (_currentContentObjectFragment.getNext() != null) {
            _currentContentObjectFragment = _currentContentObjectFragment.getNext();
            _contentObjects = _currentContentObjectFragment.getArray();
        } else {
            _contentObjects = new Object[_contentObjects.length];
            _currentContentObjectFragment = new FragmentedArray(_contentObjects, _currentContentObjectFragment);
        }
    }
}
