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

package com.sun.xml.internal.txw2.output;

import com.sun.xml.internal.txw2.TypedXmlWriter;


/**
 * Low-level typeless XML writer driven from {@link TypedXmlWriter}.
 *
 * <p>
 * Applications can use one of the predefined implementations to
 * send TXW output to the desired location/format, or they can
 * choose to implement this interface for custom output.
 *
 * <p>
 * One {@link XmlSerializer} instance is responsible for writing
 * one XML document.
 *
 * <h2>Call Sequence</h2>
 * TXW calls methods on this interface in the following order:
 *
 * <pre>
 * WHOLE_SEQUENCE := startDocument ELEMENT endDocument
 * ELEMENT := beginStartTag writeXmlns* writeAttribute* endStartTag CONTENT endTag
 * CONTENT := (text|ELEMENT)*
 * </pre>
 *
 * <p>
 * TXW maintains all the in-scope namespace bindings and prefix allocation.
 * The {@link XmlSerializer} implementation should just use the prefix
 * specified.
 * </p>
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public interface XmlSerializer {
    /**
     * The first method to be called.
     */
    void startDocument();

    /**
     * Begins writing a start tag.
     *
     * @param uri
     *      the namespace URI of the element. Can be empty but never be null.
     * @param prefix
     *      the prefix that should be used for this element. Can be empty,
     *      but never null.
     */
    void beginStartTag(String uri,String localName,String prefix);

    /**
     * Writes an attribute.
     *
     * @param value
     *      The value of the attribute. It's the callee's responsibility to
     *      escape special characters (such as &lt;, &gt;, and &amp;) in this buffer.
     *
     * @param uri
     *      the namespace URI of the attribute. Can be empty but never be null.
     * @param prefix
     *      the prefix that should be used for this attribute. Can be empty,
     *      but never null.
     */
    void writeAttribute(String uri,String localName,String prefix,StringBuilder value);

    /**
     * Writes a namespace declaration.
     *
     * @param uri
     *      the namespace URI to be declared. Can be empty but never be null.
     * @param prefix
     *      the prefix that is allocated. Can be empty but never be null.
     */
    void writeXmlns(String prefix,String uri);

    /**
     * Completes the start tag.
     *
     * @param uri
     *      the namespace URI of the element. Can be empty but never be null.
     * @param prefix
     *      the prefix that should be used for this element. Can be empty,
     *      but never null.
     */
    void endStartTag(String uri,String localName,String prefix);

    /**
     * Writes an end tag.
     */
    void endTag();

    /**
     * Writes PCDATA.
     *
     * @param text
     *      The character data to be written. It's the callee's responsibility to
     *      escape special characters (such as &lt;, &gt;, and &amp;) in this buffer.
     */
    void text(StringBuilder text);

    /**
     * Writes CDATA.
     */
    void cdata(StringBuilder text);

    /**
     * Writes a comment.
     *
     * @throws UnsupportedOperationException
     *      if the writer doesn't support writing a comment, it can throw this exception.
     */
    void comment(StringBuilder comment);

    /**
     * The last method to be called.
     */
    void endDocument();

    /**
     * Flush the buffer.
     *
     * This method is called when applications invoke {@link TypedXmlWriter#commit(boolean)}
     * method. If the implementation performs any buffering, it should flush the buffer.
     */
    void flush();
}
