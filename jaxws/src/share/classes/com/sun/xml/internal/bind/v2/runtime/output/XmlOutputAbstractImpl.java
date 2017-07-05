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

package com.sun.xml.internal.bind.v2.runtime.output;

import java.io.IOException;
import java.io.OutputStream;

import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.v2.runtime.Name;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;

import org.xml.sax.SAXException;

/**
 * Abstract implementation of {@link XmlOutput}
 *
 * Implements the optimal methods, where defer to
 * the non-optimal methods.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class XmlOutputAbstractImpl implements XmlOutput {
//
//
// Contracts
//
//
    /**
     * Called at the very beginning.
     *
     * @param serializer
     *      the {@link XMLSerializer} that coordinates this whole marshalling episode.
     * @param fragment
     *      true if we are marshalling a fragment.
     */
    public void startDocument(XMLSerializer serializer, boolean fragment, int[] nsUriIndex2prefixIndex, NamespaceContextImpl nsContext) throws IOException, SAXException, XMLStreamException {
        this.nsUriIndex2prefixIndex = nsUriIndex2prefixIndex;
        this.nsContext = nsContext;
        this.serializer = serializer;
    }

    /**
     * Called at the very end.
     *
     * @param fragment
     *      false if we are writing the whole document.
     */
    public void endDocument(boolean fragment) throws IOException, SAXException, XMLStreamException {
        serializer = null;
    }

    /**
     * Writes a start tag.
     *
     * <p>
     * At this point {@link #nsContext} holds namespace declarations needed for this
     * new element.
     *
     * <p>
     * This method is used for writing tags that are indexed.
     */
    public void beginStartTag(Name name) throws IOException, XMLStreamException {
        beginStartTag( nsUriIndex2prefixIndex[name.nsUriIndex], name.localName );
    }

    public abstract void beginStartTag(int prefix, String localName) throws IOException, XMLStreamException;

    public void attribute( Name name, String value ) throws IOException, XMLStreamException {
        short idx = name.nsUriIndex;
        if(idx==-1)
            attribute(-1,name.localName, value);
        else
            attribute( nsUriIndex2prefixIndex[idx], name.localName, value );
    }
    /**
     * @param prefix
     *      -1 if this attribute does not have a prefix
     *      (this handling differs from that of elements.)
     */
    public abstract void attribute( int prefix, String localName, String value ) throws IOException, XMLStreamException;

    public abstract void endStartTag() throws IOException, SAXException;

    public void endTag(Name name) throws IOException, SAXException, XMLStreamException {
        endTag( nsUriIndex2prefixIndex[name.nsUriIndex], name.localName);
    }
    public abstract void endTag(int prefix, String localName) throws IOException, SAXException, XMLStreamException;




//
//
// Utilities for implementations
//
//
    /**
     * The conversion table from the namespace URI index to prefix index.
     *
     * This array is shared with {@link XMLSerializer} and
     * is updated by it automatically.
     *
     * This allows {@link Name#nsUriIndex} to be converted to prefix index
     * (for {@link NamespaceContextImpl}) quickly.
     */
    protected int[] nsUriIndex2prefixIndex;

    /**
     * Set by the marshaller before the start tag is written for the root element.
     */
    protected NamespaceContextImpl nsContext;

    protected XMLSerializer serializer;
}
