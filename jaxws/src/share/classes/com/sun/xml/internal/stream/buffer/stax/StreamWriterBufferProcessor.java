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
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.org.jvnet.staxex.XMLStreamWriterEx;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;


/**
 * A processor of a {@link XMLStreamBuffer} that writes the XML infoset to a
 * {@link XMLStreamWriter}.
 *
 * @author Paul.Sandoz@Sun.Com
 * @author K.Venugopal@sun.com
 */
public class StreamWriterBufferProcessor extends AbstractProcessor {


    public StreamWriterBufferProcessor() {
    }

    /**
     * @deprecated
     *      Use {@link #StreamWriterBufferProcessor(XMLStreamBuffer, boolean)}
     */
    public StreamWriterBufferProcessor(XMLStreamBuffer buffer) {
        setXMLStreamBuffer(buffer,buffer.isFragment());
    }

    /**
     * @param produceFragmentEvent
     *      True to generate fragment SAX events without start/endDocument.
     *      False to generate a full document SAX events.
     */
    public StreamWriterBufferProcessor(XMLStreamBuffer buffer,boolean produceFragmentEvent) {
        setXMLStreamBuffer(buffer,produceFragmentEvent);
    }

    public final void process(XMLStreamBuffer buffer, XMLStreamWriter writer) throws XMLStreamException {
        setXMLStreamBuffer(buffer,buffer.isFragment());
        process(writer);
    }

    public void process(XMLStreamWriter writer) throws XMLStreamException {
        if(_fragmentMode){
            writeFragment(writer);
        }else{
            write(writer);
        }
    }

    /**
     * @deprecated
     *      Use {@link #setXMLStreamBuffer(XMLStreamBuffer, boolean)}
     */
    public void setXMLStreamBuffer(XMLStreamBuffer buffer) {
        setBuffer(buffer);
    }

    /**
     * @param produceFragmentEvent
     *      True to generate fragment SAX events without start/endDocument.
     *      False to generate a full document SAX events.
     */
    public void setXMLStreamBuffer(XMLStreamBuffer buffer, boolean produceFragmentEvent) {
        setBuffer(buffer,produceFragmentEvent);
    }

    /**
     * Writes a full XML infoset event to the given writer,
     * including start/end document.
     */
    public void write(XMLStreamWriter writer) throws XMLStreamException{

        if(!_fragmentMode) {
            if(_treeCount>1)
                throw new IllegalStateException("forest cannot be written as a full infoset");
            writer.writeStartDocument();
        }

        // TODO: if we are writing a fragment XMLStreamBuffer as a full document,
        // we need to put in-scope namespaces as top-level ns decls.

        while(true) {
            int item = _eiiStateTable[peekStructure()];
            writer.flush();

            switch(item) {
                case STATE_DOCUMENT:
                    readStructure(); //skip
                    break;
                case STATE_ELEMENT_U_LN_QN:
                case STATE_ELEMENT_P_U_LN:
                case STATE_ELEMENT_U_LN:
                case STATE_ELEMENT_LN:
                    writeFragment(writer);
                    break;
                case STATE_COMMENT_AS_CHAR_ARRAY_SMALL: {
                    readStructure();
                    final int length = readStructure();
                    final int start = readContentCharactersBuffer(length);
                    final String comment = new String(_contentCharactersBuffer, start, length);
                    writer.writeComment(comment);
                    break;
                }
                case STATE_COMMENT_AS_CHAR_ARRAY_MEDIUM: {
                    readStructure();
                    final int length = readStructure16();
                    final int start = readContentCharactersBuffer(length);
                    final String comment = new String(_contentCharactersBuffer, start, length);
                    writer.writeComment(comment);
                    break;
                }
                case STATE_COMMENT_AS_CHAR_ARRAY_COPY: {
                    readStructure();
                    final char[] ch = readContentCharactersCopy();
                    writer.writeComment(new String(ch));
                    break;
                }
                case STATE_PROCESSING_INSTRUCTION:
                    readStructure();
                    writer.writeProcessingInstruction(readStructureString(), readStructureString());
                    break;
                case STATE_END: // done
                    readStructure();
                    writer.writeEndDocument();
                    return;
                default:
                    throw new XMLStreamException("Invalid State "+item);
            }
        }

    }

    /**
     * Writes the buffer as a fragment, meaning
     * the writer will not receive start/endDocument events.
     *
     * <p>
     * If {@link XMLStreamBuffer} has a forest, this method will write all the forests.
     */
    public void writeFragment(XMLStreamWriter writer) throws XMLStreamException {
        if (writer instanceof XMLStreamWriterEx) {
            writeFragmentEx((XMLStreamWriterEx)writer);
        } else {
            writeFragmentNoEx(writer);
        }
    }

    public void writeFragmentEx(XMLStreamWriterEx writer) throws XMLStreamException {
        int depth = 0;  // used to determine when we are done with a tree.

        int item = _eiiStateTable[peekStructure()];
        if(item==STATE_DOCUMENT)
            readStructure();    // skip STATE_DOCUMENT

        do {

            item = readEiiState();

            switch(item) {
                case STATE_DOCUMENT:
                    throw new AssertionError();
                case STATE_ELEMENT_U_LN_QN: {
                    depth ++;
                    final String uri = readStructureString();
                    final String localName = readStructureString();
                    final String prefix = getPrefixFromQName(readStructureString());
                    writer.writeStartElement(prefix,localName,uri);
                    writeAttributes(writer);
                    break;
                }
                case STATE_ELEMENT_P_U_LN: {
                    depth ++;
                    final String prefix = readStructureString();
                    final String uri = readStructureString();
                    final String localName = readStructureString();
                    writer.writeStartElement(prefix,localName,uri);
                    writeAttributes(writer);
                    break;
                }
                case STATE_ELEMENT_U_LN: {
                    depth ++;
                    final String uri = readStructureString();
                    final String localName = readStructureString();
                    writer.writeStartElement("",localName,uri);
                    writeAttributes(writer);
                    break;
                }
                case STATE_ELEMENT_LN: {
                    depth ++;
                    final String localName = readStructureString();
                    writer.writeStartElement(localName);
                    writeAttributes(writer);
                    break;
                }
                case STATE_TEXT_AS_CHAR_ARRAY_SMALL: {
                    final int length = readStructure();
                    final int start = readContentCharactersBuffer(length);
                    writer.writeCharacters(_contentCharactersBuffer,start,length);
                    break;
                }
                case STATE_TEXT_AS_CHAR_ARRAY_MEDIUM: {
                    final int length = readStructure16();
                    final int start = readContentCharactersBuffer(length);
                    writer.writeCharacters(_contentCharactersBuffer,start,length);
                    break;
                }
                case STATE_TEXT_AS_CHAR_ARRAY_COPY: {
                    char[] c = readContentCharactersCopy();
                    writer.writeCharacters(c,0,c.length);
                    break;
                }
                case STATE_TEXT_AS_STRING: {
                    final String s = readContentString();
                    writer.writeCharacters(s);
                    break;
                }
                case STATE_TEXT_AS_OBJECT: {
                    final CharSequence c = (CharSequence)readContentObject();
                    writer.writePCDATA(c);
                    break;
                }
                case STATE_COMMENT_AS_CHAR_ARRAY_SMALL: {
                    final int length = readStructure();
                    final int start = readContentCharactersBuffer(length);
                    final String comment = new String(_contentCharactersBuffer, start, length);
                    writer.writeComment(comment);
                    break;
                }
                case STATE_COMMENT_AS_CHAR_ARRAY_MEDIUM: {
                    final int length = readStructure16();
                    final int start = readContentCharactersBuffer(length);
                    final String comment = new String(_contentCharactersBuffer, start, length);
                    writer.writeComment(comment);
                    break;
                }
                case STATE_COMMENT_AS_CHAR_ARRAY_COPY: {
                    final char[] ch = readContentCharactersCopy();
                    writer.writeComment(new String(ch));
                    break;
                }
                case STATE_PROCESSING_INSTRUCTION:
                    writer.writeProcessingInstruction(readStructureString(), readStructureString());
                    break;
                case STATE_END:
                    writer.writeEndElement();
                    depth --;
                    if(depth==0)
                        _treeCount--;
                    break;
                default:
                    throw new XMLStreamException("Invalid State "+item);
            }
        } while(depth>0 || _treeCount>0);

    }

    public void writeFragmentNoEx(XMLStreamWriter writer) throws XMLStreamException {
        int depth = 0;

        int item = _eiiStateTable[peekStructure()];
        if(item==STATE_DOCUMENT)
            readStructure();    // skip STATE_DOCUMENT

        do {
            item = readEiiState();

            switch(item) {
                case STATE_DOCUMENT:
                    throw new AssertionError();
                case STATE_ELEMENT_U_LN_QN: {
                    depth ++;
                    final String uri = readStructureString();
                    final String localName = readStructureString();
                    final String prefix = getPrefixFromQName(readStructureString());
                    writer.writeStartElement(prefix,localName,uri);
                    writeAttributes(writer);
                    break;
                }
                case STATE_ELEMENT_P_U_LN: {
                    depth ++;
                    final String prefix = readStructureString();
                    final String uri = readStructureString();
                    final String localName = readStructureString();
                    writer.writeStartElement(prefix,localName,uri);
                    writeAttributes(writer);
                    break;
                }
                case STATE_ELEMENT_U_LN: {
                    depth ++;
                    final String uri = readStructureString();
                    final String localName = readStructureString();
                    writer.writeStartElement("",localName,uri);
                    writeAttributes(writer);
                    break;
                }
                case STATE_ELEMENT_LN: {
                    depth ++;
                    final String localName = readStructureString();
                    writer.writeStartElement(localName);
                    writeAttributes(writer);
                    break;
                }
                case STATE_TEXT_AS_CHAR_ARRAY_SMALL: {
                    final int length = readStructure();
                    final int start = readContentCharactersBuffer(length);
                    writer.writeCharacters(_contentCharactersBuffer,start,length);
                    break;
                }
                case STATE_TEXT_AS_CHAR_ARRAY_MEDIUM: {
                    final int length = readStructure16();
                    final int start = readContentCharactersBuffer(length);
                    writer.writeCharacters(_contentCharactersBuffer,start,length);
                    break;
                }
                case STATE_TEXT_AS_CHAR_ARRAY_COPY: {
                    char[] c = readContentCharactersCopy();
                    writer.writeCharacters(c,0,c.length);
                    break;
                }
                case STATE_TEXT_AS_STRING: {
                    final String s = readContentString();
                    writer.writeCharacters(s);
                    break;
                }
                case STATE_TEXT_AS_OBJECT: {
                    final CharSequence c = (CharSequence)readContentObject();
                    writer.writeCharacters(c.toString());
                    break;
                }
                case STATE_COMMENT_AS_CHAR_ARRAY_SMALL: {
                    final int length = readStructure();
                    final int start = readContentCharactersBuffer(length);
                    final String comment = new String(_contentCharactersBuffer, start, length);
                    writer.writeComment(comment);
                    break;
                }
                case STATE_COMMENT_AS_CHAR_ARRAY_MEDIUM: {
                    final int length = readStructure16();
                    final int start = readContentCharactersBuffer(length);
                    final String comment = new String(_contentCharactersBuffer, start, length);
                    writer.writeComment(comment);
                    break;
                }
                case STATE_COMMENT_AS_CHAR_ARRAY_COPY: {
                    final char[] ch = readContentCharactersCopy();
                    writer.writeComment(new String(ch));
                    break;
                }
                case STATE_PROCESSING_INSTRUCTION:
                    writer.writeProcessingInstruction(readStructureString(), readStructureString());
                    break;
                case STATE_END:
                    writer.writeEndElement();
                    depth --;
                    if(depth==0)
                        _treeCount--;
                    break;
                default:
                    throw new XMLStreamException("Invalid State "+item);
            }
        } while(depth > 0 && _treeCount>0);

    }

    private void writeAttributes(XMLStreamWriter writer) throws XMLStreamException {
        int item = peekStructure();
        if ((item & TYPE_MASK) == T_NAMESPACE_ATTRIBUTE) {
            // Skip the namespace declarations on the element
            // they will have been added already
            item = writeNamespaceAttributes(item, writer);
        }
        if ((item & TYPE_MASK) == T_ATTRIBUTE) {
            writeAttributes(item, writer);
        }
    }

    private int writeNamespaceAttributes(int item, XMLStreamWriter writer) throws XMLStreamException {
        do {
            switch(_niiStateTable[item]){
                case STATE_NAMESPACE_ATTRIBUTE:
                    // Undeclaration of default namespace
                    writer.writeDefaultNamespace("");
                    break;
                case STATE_NAMESPACE_ATTRIBUTE_P:
                    // Undeclaration of namespace
                    // Declaration with prefix
                    writer.writeNamespace(readStructureString(), "");
                    break;
                case STATE_NAMESPACE_ATTRIBUTE_P_U:
                    // Declaration with prefix
                    writer.writeNamespace(readStructureString(), readStructureString());
                    break;
                case STATE_NAMESPACE_ATTRIBUTE_U:
                    // Default declaration
                    writer.writeDefaultNamespace(readStructureString());
                    break;
            }
            readStructure();

            item = peekStructure();
        } while((item & TYPE_MASK) == T_NAMESPACE_ATTRIBUTE);

        return item;
    }

    private void writeAttributes(int item, XMLStreamWriter writer) throws XMLStreamException {
        do {
            switch(_aiiStateTable[item]) {
                case STATE_ATTRIBUTE_U_LN_QN: {
                    final String uri = readStructureString();
                    final String localName = readStructureString();
                    final String prefix = getPrefixFromQName(readStructureString());
                    writer.writeAttribute(prefix,uri,localName,readContentString());
                    break;
                }
                case STATE_ATTRIBUTE_P_U_LN:
                    writer.writeAttribute(readStructureString(), readStructureString(),
                            readStructureString(), readContentString());
                    break;
                case STATE_ATTRIBUTE_U_LN:
                    writer.writeAttribute(readStructureString(), readStructureString(), readContentString());
                    break;
                case STATE_ATTRIBUTE_LN:
                    writer.writeAttribute(readStructureString(), readContentString());
                    break;
            }
            // Ignore the attribute type
            readStructureString();

            readStructure();

            item = peekStructure();
        } while((item & TYPE_MASK) == T_ATTRIBUTE);
    }
}
