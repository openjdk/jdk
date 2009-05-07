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

package com.sun.xml.internal.ws.streaming;

import org.xml.sax.helpers.XMLReaderFactory;

import java.util.Iterator;

import javax.xml.namespace.QName;

/**
 * <p> XMLReader provides a high-level streaming parser interface
 * for reading XML documents. </p>
 *
 * <p> The {@link #next} method is used to read events from the XML document. </p>
 *
 * <p> Each time it is called, {@link #next} returns the new state of the reader. </p>
 *
 * <p> Possible states are: BOF, the initial state, START, denoting the start
 * tag of an element, END, denoting the end tag of an element, CHARS, denoting
 * the character content of an element, PI, denoting a processing instruction,
 * EOF, denoting the end of the document. </p>
 *
 * <p> Depending on the state the reader is in, one or more of the following
 * query methods will be meaningful: {@link #getName}, {@link #getURI},
 * {@link #getLocalName}, {@link #getAttributes}, {@link #getValue}. </p>
 *
 * <p> Elements visited by a XMLReader are tagged with unique IDs. The ID of the
 * current element can be found by calling {@link #getElementId}. </p>
 *
 * <p> A XMLReader is always namespace-aware, and keeps track of the namespace
 * declarations which are in scope at any time during streaming. The
 * {@link #getURI(java.lang.String)} method can be used to find the URI
 * associated to a given prefix in the current scope. </p>
 *
 * <p> XMLReaders can be created using a {@link XMLReaderFactory}. </p>
 *
 * <p> Some utility methods, {@link #nextContent} and {@link #nextElementContent}
 * make it possible to ignore whitespace and processing instructions with
 * minimum impact on the client code. </p>
 *
 * <p> Similarly, the {@link #skipElement} and {@link #skipElement(int elementId)}
 * methods allow to skip to the end tag of an element ignoring all its content. </p>
 *
 * <p> Finally, the {@link #recordElement} method can be invoked when the XMLReader
 * is positioned on the start tag of an element to record the element's contents
 * so that they can be played back later. </p>
 *
 * @see XMLReaderFactory
 *
 * @author WS Development Team
 */
public interface XMLReader {
    /**
     * The initial state of a XMLReader.
     */
    public static final int BOF = 0;

    /**
     * The state denoting the start tag of an element.
     */
    public static final int START = 1;

    /**
     * The state denoting the end tag of an element.
     */
    public static final int END = 2;

    /**
     * The state denoting the character content of an element.
     */
    public static final int CHARS = 3;

    /**
     * The state denoting a processing instruction.
     */
    public static final int PI = 4;

    /**
     * The state denoting that the end of the document has been reached.
     */
    public static final int EOF = 5;

    /**
     * Return the next state of the XMLReader.
     *
     * The return value is one of: START, END, CHARS, PI, EOF.
     */
    public int next();

    /*
    * Return the next state of the XMLReader.
    *
    * <p> Whitespace character content and processing instructions are ignored. </p>
    *
    * <p> The return value is one of: START, END, CHARS, EOF. </p>
    */
    public int nextContent();

    /**
     * Return the next state of the XMLReader.
     *
     * <p> Whitespace character content, processing instructions are ignored.
     * Non-whitespace character content triggers an exception. </p>
     *
     * <p> The return value is one of: START, END, EOF. </p>
     */
    public int nextElementContent();

    /**
     * Return the current state of the XMLReader.
     *
     */
    public int getState();

    /**
     * Return the current qualified name.
     *
     * <p> Meaningful only when the state is one of: START, END. </p>
     */
    public QName getName();

    /**
     * Return the current URI.
     *
     * <p> Meaningful only when the state is one of: START, END. </p>
     */
    public String getURI();

    /**
     * Return the current local name.
     *
     * <p> Meaningful only when the state is one of: START, END, PI. </p>
     */
    public String getLocalName();

    /**
     * Return the current attribute list. In the jaxws implementation,
     * this list also includes namespace declarations.
     *
     * <p> Meaningful only when the state is one of: START. </p>
     *
     * <p> The returned {@link Attributes} object belong to the XMLReader and is
     * only guaranteed to be valid until the {@link #next} method is called,
     * directly or indirectly.</p>
     */
    public Attributes getAttributes();

    /**
     * Return the current value.
     *
     * <p> Meaningful only when the state is one of: CHARS, PI. </p>
     */
    public String getValue();

    /**
     * Return the current element ID.
     */
    public int getElementId();

    /**
     * Return the current line number.
     *
     * <p> Due to aggressive parsing, this value may be off by a few lines. </p>
     */
    public int getLineNumber();

    /**
     * Return the URI for the given prefix.
     *
     * <p> If there is no namespace declaration in scope for the given
     * prefix, return null. </p>
     */
    public String getURI(String prefix);

    /**
     * Return an iterator on all prefixes in scope, except for the default prefix.
     *
     */
    public Iterator getPrefixes();

    /**
     * Records the current element and leaves the reader positioned on its end tag.
     *
     * <p> The XMLReader must be positioned on the start tag of the element.
     * The returned reader will play back all events starting with the
     * start tag of the element and ending with its end tag. </p>
     */
    public XMLReader recordElement();

    /**
     * Skip all nodes up to the end tag of the element with the current element ID.
     */
    public void skipElement();

    /**
     * Skip all nodes up to the end tag of the element with the given element ID.
     */
    public void skipElement(int elementId);

    /**
     * Close the XMLReader.
     *
     * <p> All subsequent calls to {@link #next} will return EOF. </p>
     */
    public void close();
}
