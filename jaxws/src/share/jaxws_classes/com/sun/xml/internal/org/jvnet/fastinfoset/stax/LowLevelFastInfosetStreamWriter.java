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

package com.sun.xml.internal.org.jvnet.fastinfoset.stax;

import java.io.IOException;
import javax.xml.stream.XMLStreamException;

/**
 * Low level Fast Infoset stream writer.
 * <p>
 * This interface provides additional stream-based serialization methods for the
 * case where an application is in specific control of the serialization
 * process and has the knowledge to call the LowLevel methods in the required
 * order.
 * <p>
 * For example, the application may be able to perform efficient information
 * to indexing mapping and to provide certain information in UTF-8 encoded form.
 * <p>
 * These methods may be used in conjuction with {@link javax.xml.stream.XMLStreamWriter}
 * as long as an element fragment written using the efficient streaming methods
 * are self-contained and no sub-fragment is written using methods from
 * {@link javax.xml.stream.XMLStreamWriter}.
 * <p>
 * The required call sequence is as follows:
 * <pre>
 * CALLSEQUENCE    := {@link #startDocument startDocument}
 *                    initiateLowLevelWriting ELEMENT
 *                    {@link #endDocument endDocument}
 *                 |  initiateLowLevelWriting ELEMENT   // for fragment
 *
 * ELEMENT         := writeLowLevelTerminationAndMark
 *                    NAMESPACES?
 *                    ELEMENT_NAME
 *                    ATTRIBUTES?
 *                    writeLowLevelEndStartElement
 *                    CONTENTS
 *                    writeLowLevelEndElement
 *
 * NAMESPACES      := writeLowLevelStartNamespaces
 *                    writeLowLevelNamespace*
 *                    writeLowLevelEndNamespaces
 *
 * ELEMENT_NAME    := writeLowLevelStartElementIndexed
 *                 |  writeLowLevelStartNameLiteral
 *                 |  writeLowLevelStartElement
 *
 * ATTRUBUTES      := writeLowLevelStartAttributes
 *                   (ATTRIBUTE_NAME writeLowLevelAttributeValue)*
 *
 * ATTRIBUTE_NAME  := writeLowLevelAttributeIndexed
 *                 |  writeLowLevelStartNameLiteral
 *                 |  writeLowLevelAttribute
 *
 *
 * CONTENTS      := (ELEMENT | writeLowLevelText writeLowLevelOctets)*
 * </pre>
 * <p>
 * Some methods defer to the application for the mapping of information
 * to indexes.
 */
public interface LowLevelFastInfosetStreamWriter {
    /**
     * Initiate low level writing of an element fragment.
     * <p>
     * This method must be invoked before other low level method.
     */
    public void initiateLowLevelWriting()
    throws XMLStreamException;

    /**
     * Get the next index to apply to an Element Information Item.
     * <p>
     * This will increment the next obtained index such that:
     * <pre>
     * i = w.getNextElementIndex();
     * j = w.getNextElementIndex();
     * i == j + 1;
     * </pre>
     * @return the index.
     */
    public int getNextElementIndex();

    /**
     * Get the next index to apply to an Attribute Information Item.
     * This will increment the next obtained index such that:
     * <pre>
     * i = w.getNextAttributeIndex();
     * j = w.getNextAttributeIndex();
     * i == j + 1;
     * </pre>
     * @return the index.
     */
    public int getNextAttributeIndex();

    /**
     * Get the current index that was applied to an [local name] of an
     * Element or Attribute Information Item.
     * </pre>
     * @return the index.
     */
    public int getLocalNameIndex();

    /**
     * Get the next index to apply to an [local name] of an Element or Attribute
     * Information Item.
     * This will increment the next obtained index such that:
     * <pre>
     * i = w.getNextLocalNameIndex();
     * j = w.getNextLocalNameIndex();
     * i == j + 1;
     * </pre>
     * @return the index.
     */
    public int getNextLocalNameIndex();

    public void writeLowLevelTerminationAndMark()
    throws IOException;

    public void writeLowLevelStartElementIndexed(int type, int index)
    throws IOException;

    /**
     * Write the start of an element.
     *
     * @return true if element is indexed, otherwise false.
     */
    public boolean writeLowLevelStartElement(int type,
            String prefix, String localName, String namespaceURI)
            throws IOException;

    public void writeLowLevelStartNamespaces()
    throws IOException;

    public void writeLowLevelNamespace(String prefix, String namespaceName)
        throws IOException;

    public void writeLowLevelEndNamespaces()
    throws IOException;

    public void writeLowLevelStartAttributes()
    throws IOException;

    public void writeLowLevelAttributeIndexed(int index)
    throws IOException;

    /**
     * Write an attribute.
     *
     * @return true if attribute is indexed, otherwise false.
     */
    public boolean writeLowLevelAttribute(
            String prefix, String namespaceURI, String localName)
            throws IOException;

    public void writeLowLevelAttributeValue(String value)
    throws IOException;

    public void writeLowLevelStartNameLiteral(int type,
            String prefix, byte[] utf8LocalName, String namespaceURI)
            throws IOException;

    public void writeLowLevelStartNameLiteral(int type,
            String prefix, int localNameIndex, String namespaceURI)
            throws IOException;

    public void writeLowLevelEndStartElement()
    throws IOException;

    public void writeLowLevelEndElement()
    throws IOException;

    public void writeLowLevelText(char[] text, int length)
    throws IOException;

    public void writeLowLevelText(String text)
    throws IOException;

    public void writeLowLevelOctets(byte[] octets, int length)
    throws IOException;
}
