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

import java.util.Map;

/**
 * A mark into a buffer.
 *
 * <p>
 * A mark can be processed in the same manner as a XMLStreamBuffer.
 *
 * <p>
 * A mark will share a sub set of information of the buffer that is
 * marked. If the buffer is directly or indirectly associated with a
 * (mutable) {@link XMLStreamBuffer} which is reset and/or re-created
 * then this will invalidate the mark and processing behvaiour of the mark
 * is undefined. It is the responsibility of the application to manage the
 * relationship between the marked XMLStreamBuffer and one or more marks.
 */
public class XMLStreamBufferMark extends XMLStreamBuffer {

    /**
     * Create a mark from the buffer that is being created.
     *
     * <p>
     * A mark will be created from the current position of creation of the
     * {@link XMLStreamBuffer} that is being created by a {@link AbstractCreator}.
     *
     * @param inscopeNamespaces
     * The in-scope namespaces on the fragment of XML infoset that is
     * to be marked.
     *
     * @param src
     * The {@link AbstractCreator} or {@link AbstractProcessor} from which the current
     * position of creation of the XMLStreamBuffer will be taken as the mark.
     */
    public XMLStreamBufferMark(Map<String,String> inscopeNamespaces, AbstractCreatorProcessor src) {
        _inscopeNamespaces = inscopeNamespaces;

        _structure = src._currentStructureFragment;
        _structurePtr = src._structurePtr;

        _structureStrings = src._currentStructureStringFragment;
        _structureStringsPtr = src._structureStringsPtr;

        _contentCharactersBuffer = src._currentContentCharactersBufferFragment;
        _contentCharactersBufferPtr = src._contentCharactersBufferPtr;

        _contentObjects = src._currentContentObjectFragment;
        _contentObjectsPtr = src._contentObjectsPtr;
        treeCount = 1; // TODO: define a way to create a mark over a forest
    }
}
