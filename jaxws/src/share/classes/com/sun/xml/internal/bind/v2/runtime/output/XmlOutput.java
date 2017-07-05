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

import javax.xml.bind.JAXBContext;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.v2.runtime.Name;
import com.sun.xml.internal.bind.v2.runtime.NameList;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;

import org.xml.sax.SAXException;

/**
 * Well-formed XML writer.
 *
 * <p>
 * Implementations of this interface is used to connect {@link XMLSerializer}
 * to the actual target. This allows {@link XMLSerializer} to be API agnostic.
 *
 *
 * <h2>Notes</h2>
 * <p>
 * {@link JAXBContext} assigns indices to URIs and local names
 * that are statically known by using {@link NameList}.
 * {@link XmlOutput} implementation can use these indices to improve
 * the performance. For example, those namespace URI indices can be
 * turned into prefixes quickly.
 *
 * <p>
 * {@link XmlOutput} still allows arbitrary namepsace URIs / local names
 * to be written.
 *
 * <p>
 * The {@link NamespaceContextImpl} object, which is shared between {@link XmlOutput} and
 * {@link XMLSerializer}, keeps track of the in-scope namespace bindings. By the time
 * the {@link #beginStartTag} method is called, all the namespace bindings for the new
 * element is already declared. Similarly, after the {@link #endTag} method is called,
 * in-scope bindings will be removed. This book keeping is all done outside {@link XmlOutput}.
 *
 * <p>
 * {@link XmlOutput} and {@link XMLSerializer} uses indices to
 * reference prefixes/URIs to be written. {@link NamespaceContextImpl} can
 * convert prefix indices to URIs and the string representations of prefixes.
 * Binding from indices to URIs and prefixes do not change while indices
 * are "in scope", so {@link XmlOutput} is again expected to take advantage of
 * this to improve the perofmrnace.
 *
 * <p>
 * prefix index 0 is reserved for "xml", and this binding is assumed to be always there.
 * {@link NamespaceContextImpl} can handle this index correctly, but this binding will never
 * be reported to {@link XmlOutput} through {@link #beginStartTag}.
 *
 * <p>
 * One pecurilar behavior of a {@link NamespaceContextImpl} object is that it tries
 * to define redundant xmlns="" on the root element. Implementations of {@link XmlOutput}
 * is encouraged to check for this and avoid generating redundant namespace declarations.
 *
 *
 *
 * <h2>Call Sequence</h2>
 * <p>
 * {@link XMLSerializer} calls the writer methods in the following order:
 *
 * <pre>
 * CALLSEQUENCE  :=  {@link #startDocument startDocument} ELEMENT {@link #endDocument endDocument}
 *               |   ELEMENT   // for fragment
 *
 * ELEMENT       :=  {@link #beginStartTag beginStartTag} {@link #attribute attribute}* {@link #endStartTag endStartTag} CONTENTS {@link #endTag endTag}
 *
 * CONTENTS      :=  (ELEMENT | {@link #text text})*
 * </pre>
 *
 * TODO: for FI, consider making attribute values from Strings to CharSequences.
 *
 * @author Kohsuke Kawaguchi
 */
public interface XmlOutput {
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
    public void startDocument(XMLSerializer serializer, boolean fragment, int[] nsUriIndex2prefixIndex, NamespaceContextImpl nsContext) throws IOException, SAXException, XMLStreamException;

    /**
     * Called at the very end. This is the last method to be invoked.
     *
     * @param fragment
     *      false if we are writing the whole document.
     */
    public void endDocument(boolean fragment) throws IOException, SAXException, XMLStreamException;

    /**
     * Writes a start tag.
     *
     * <p>
     * At this point {@link NamespaceContextImpl} holds namespace declarations needed for this
     * new element.
     *
     * <p>
     * This method is used for writing tags that are indexed.
     */
    public void beginStartTag(Name name) throws IOException, XMLStreamException;

    public void beginStartTag(int prefix, String localName) throws IOException, XMLStreamException;

    public void attribute( Name name, String value ) throws IOException, XMLStreamException;

    /**
     * @param prefix
     *      -1 if this attribute does not have a prefix
     *      (this handling differs from that of elements.)
     */
    public void attribute( int prefix, String localName, String value ) throws IOException, XMLStreamException;

    public void endStartTag() throws IOException, SAXException;

    public void endTag(Name name) throws IOException, SAXException, XMLStreamException;

    public void endTag(int prefix, String localName) throws IOException, SAXException, XMLStreamException;

    /**
     * Writes XML text with character escaping, if necessary.
     *
     * @param value
     *      this string can contain characters that might need escaping
     *      (such as '&amp;' or '>')
     * @param needsSeparatingWhitespace
     */
    public void text( String value, boolean needsSeparatingWhitespace ) throws IOException, SAXException, XMLStreamException;

    /**
     * Writes XML text with character escaping, if necessary.
     *
     * @param value
     *      this string can contain characters that might need escaping
     *      (such as '&amp;' or '>')
     * @param needsSeparatingWhitespace
     */
    public void text( Pcdata value, boolean needsSeparatingWhitespace ) throws IOException, SAXException, XMLStreamException;
}
