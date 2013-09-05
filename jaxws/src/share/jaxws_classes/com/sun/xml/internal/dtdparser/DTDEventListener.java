/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.dtdparser;

import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.util.EventListener;

/**
 * All DTD parsing events are signaled through this interface.
 */
public interface DTDEventListener extends EventListener {

    public void setDocumentLocator(Locator loc);

    /**
     * Receive notification of a Processing Instruction.
     * Processing instructions contain information meaningful
     * to the application.
     *
     * @param target The target of the proceessing instruction
     *               which should have meaning to the application.
     * @param data   The instruction itself which should contain
     *               valid XML characters.
     * @throws SAXException
     */
    public void processingInstruction(String target, String data)
            throws SAXException;

    /**
     * Receive notification of a Notation Declaration.
     * Notation declarations are used by elements and entities
     * for identifying embedded non-XML data.
     *
     * @param name     The notation name, referred to by entities and
     *                 elements.
     * @param publicId The public identifier
     * @param systemId The system identifier
     */
    public void notationDecl(String name, String publicId, String systemId)
            throws SAXException;

    /**
     * Receive notification of an unparsed entity declaration.
     * Unparsed entities are non-XML data.
     *
     * @param name         The name of the unparsed entity.
     * @param publicId     The public identifier
     * @param systemId     The system identifier
     * @param notationName The associated notation
     */
    public void unparsedEntityDecl(String name, String publicId,
                                   String systemId, String notationName)
            throws SAXException;

    /**
     * Receive notification of a internal general entity declaration event.
     *
     * @param name  The internal general entity name.
     * @param value The value of the entity, which may include unexpanded
     *              entity references.  Character references will have been
     *              expanded.
     * @throws SAXException
     * @see #externalGeneralEntityDecl(String, String, String)
     */
    public void internalGeneralEntityDecl(String name, String value)
            throws SAXException;

    /**
     * Receive notification of an external parsed general entity
     * declaration event.
     * <p/>
     * <p>If a system identifier is present, and it is a relative URL, the
     * parser will have resolved it fully before passing it through this
     * method to a listener.</p>
     *
     * @param name     The entity name.
     * @param publicId The entity's public identifier, or null if
     *                 none was given.
     * @param systemId The entity's system identifier.
     * @throws SAXException
     * @see #unparsedEntityDecl(String, String, String, String)
     */
    public void externalGeneralEntityDecl(String name, String publicId,
                                          String systemId)
            throws SAXException;

    /**
     * Receive notification of a internal parameter entity declaration
     * event.
     *
     * @param name  The internal parameter entity name.
     * @param value The value of the entity, which may include unexpanded
     *              entity references.  Character references will have been
     *              expanded.
     * @throws SAXException
     * @see #externalParameterEntityDecl(String, String, String)
     */
    public void internalParameterEntityDecl(String name, String value)
            throws SAXException;

    /**
     * Receive notification of an external parameter entity declaration
     * event.
     * <p/>
     * <p>If a system identifier is present, and it is a relative URL, the
     * parser will have resolved it fully before passing it through this
     * method to a listener.</p>
     *
     * @param name     The parameter entity name.
     * @param publicId The entity's public identifier, or null if
     *                 none was given.
     * @param systemId The entity's system identifier.
     * @throws SAXException
     * @see #unparsedEntityDecl(String, String, String, String)
     */
    public void externalParameterEntityDecl(String name, String publicId,
                                            String systemId)
            throws SAXException;

    /**
     * Receive notification of the beginning of the DTD.
     *
     * @param in Current input entity.
     * @see #endDTD()
     */
    public void startDTD(InputEntity in)
            throws SAXException;

    /**
     * Receive notification of the end of a DTD.  The parser will invoke
     * this method only once.
     *
     * @throws SAXException
     * @see #startDTD(InputEntity)
     */
    public void endDTD()
            throws SAXException;

    /**
     * Receive notification that a comment has been read.
     * <p/>
     * <P> Note that processing instructions are the mechanism designed
     * to hold information for consumption by applications, not comments.
     * XML systems may rely on applications being able to access information
     * found in processing instructions; this is not true of comments, which
     * are typically discarded.
     *
     * @param text the text within the comment delimiters.
     * @throws SAXException
     */
    public void comment(String text)
            throws SAXException;

    /**
     * Receive notification of character data.
     * <p/>
     * <p>The Parser will call this method to report each chunk of
     * character data.  SAX parsers may return all contiguous character
     * data in a single chunk, or they may split it into several
     * chunks; however, all of the characters in any single event
     * must come from the same external entity, so that the Locator
     * provides useful information.</p>
     * <p/>
     * <p>The application must not attempt to read from the array
     * outside of the specified range.</p>
     * <p/>
     * <p>Note that some parsers will report whitespace using the
     * ignorableWhitespace() method rather than this one (validating
     * parsers must do so).</p>
     *
     * @param ch     The characters from the DTD.
     * @param start  The start position in the array.
     * @param length The number of characters to read from the array.
     * @throws SAXException
     * @see #ignorableWhitespace(char[], int, int)
     */
    public void characters(char ch[], int start, int length)
            throws SAXException;


    /**
     * Receive notification of ignorable whitespace in element content.
     * <p/>
     * <p>Validating Parsers must use this method to report each chunk
     * of ignorable whitespace (see the W3C XML 1.0 recommendation,
     * section 2.10): non-validating parsers may also use this method
     * if they are capable of parsing and using content models.</p>
     * <p/>
     * <p>SAX parsers may return all contiguous whitespace in a single
     * chunk, or they may split it into several chunks; however, all of
     * the characters in any single event must come from the same
     * external entity, so that the Locator provides useful
     * information.</p>
     * <p/>
     * <p>The application must not attempt to read from the array
     * outside of the specified range.</p>
     *
     * @param ch     The characters from the DTD.
     * @param start  The start position in the array.
     * @param length The number of characters to read from the array.
     * @throws SAXException
     * @see #characters(char[], int, int)
     */
    public void ignorableWhitespace(char ch[], int start, int length)
            throws SAXException;

    /**
     * Receive notification that a CDATA section is beginning.  Data in a
     * CDATA section is is reported through the appropriate event, either
     * <em>characters()</em> or <em>ignorableWhitespace</em>.
     *
     * @throws SAXException
     * @see #endCDATA()
     */
    public void startCDATA() throws SAXException;


    /**
     * Receive notification that the CDATA section finished.
     *
     * @throws SAXException
     * @see #startCDATA()
     */
    public void endCDATA() throws SAXException;


    public void fatalError(SAXParseException e)
            throws SAXException;

    public void error(SAXParseException e) throws SAXException;

    public void warning(SAXParseException err) throws SAXException;

    public final short CONTENT_MODEL_EMPTY = 0;
    public final short CONTENT_MODEL_ANY = 1;
    public final short CONTENT_MODEL_MIXED = 2;
    public final short CONTENT_MODEL_CHILDREN = 3;

    /**
     * receives notification that parsing of content model is beginning.
     *
     * @param elementName      name of the element whose content model is going to be defined.
     * @param contentModelType {@link #CONTENT_MODEL_EMPTY}
     *                         this element has EMPTY content model. This notification
     *                         will be immediately followed by the corresponding endContentModel.
     *                         {@link #CONTENT_MODEL_ANY}
     *                         this element has ANY content model. This notification
     *                         will be immediately followed by the corresponding endContentModel.
     *                         {@link #CONTENT_MODEL_MIXED}
     *                         this element has mixed content model. #PCDATA will not be reported.
     *                         each child element will be reported by mixedElement method.
     *                         {@link #CONTENT_MODEL_CHILDREN}
     *                         this elemen has child content model. The actual content model will
     *                         be reported by childElement, startModelGroup, endModelGroup, and
     *                         connector methods. Possible call sequences are:
     *                         <p/>
     *                         START := MODEL_GROUP
     *                         MODEL_GROUP := startModelGroup TOKEN (connector TOKEN)* endModelGroup
     *                         TOKEN := childElement
     *                         | MODEL_GROUP
     */
    public void startContentModel(String elementName, short contentModelType) throws SAXException;

    /**
     * receives notification that parsing of content model is finished.
     */
    public void endContentModel(String elementName, short contentModelType) throws SAXException;

    public final short USE_NORMAL = 0;
    public final short USE_IMPLIED = 1;
    public final short USE_FIXED = 2;
    public final short USE_REQUIRED = 3;

    /**
     * For each entry in an ATTLIST declaration,
     * this event will be fired.
     * <p/>
     * <p/>
     * DTD allows the same attributes to be declared more than
     * once, and in that case the first one wins. I think
     * this method will be only fired for the first one,
     * but I need to check.
     */
    public void attributeDecl(String elementName, String attributeName, String attributeType,
                              String[] enumeration, short attributeUse, String defaultValue) throws SAXException;

    public void childElement(String elementName, short occurence) throws SAXException;

    /**
     * receives notification of child element of mixed content model.
     * this method is called for each child element.
     *
     * @see #startContentModel(String, short)
     */
    public void mixedElement(String elementName) throws SAXException;

    public void startModelGroup() throws SAXException;

    public void endModelGroup(short occurence) throws SAXException;

    public final short CHOICE = 0;
    public final short SEQUENCE = 1;

    /**
     * Connectors in one model group is guaranteed to be the same.
     * <p/>
     * <p/>
     * IOW, you'll never see an event sequence like (a|b,c)
     *
     * @return {@link #CHOICE} or {@link #SEQUENCE}.
     */
    public void connector(short connectorType) throws SAXException;

    public final short OCCURENCE_ZERO_OR_MORE = 0;
    public final short OCCURENCE_ONE_OR_MORE = 1;
    public final short OCCURENCE_ZERO_OR_ONE = 2;
    public final short OCCURENCE_ONCE = 3;
}
