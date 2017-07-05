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
package com.sun.xml.internal.org.jvnet.staxex;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamException;

/**
 * {@link XMLStreamReader} extended for reading binary data.
 *
 * <p>
 * Some producer of infoset (in particular, such as FastInfoset,
 * XOP deecoder), usees a native format that enables efficient
 * treatment of binary data. For ordinary infoset consumer
 * (that just uses {@link XMLStreamReader}, those binary data
 * will just look like base64-encoded string, but this interface
 * allows consumers of such infoset to access this raw binary data.
 * Such infoset producer may choose to implement this additoinal
 * interface, to expose this functionality.
 *
 * <p>
 * Consumers that are capable of using this interface can query
 * {@link XMLStreamReader} if it supports this by simply downcasting
 * it to this interface like this:
 *
 * <pre>
 * XMLStreamReader reader = ...;
 * if( reader instanceof XMLStreamReaderEx ) {
 *   // this reader supports binary data exchange
 *   ...
 * } else {
 *   // noop
 *   ...
 * }
 * </pre>
 *
 * <p>
 * Also note that it is also allowed for the infoset producer
 * to implement this interface in such a way that {@link #getPCDATA()}
 * always delegate to {@link #getText()}, although it's not desirable.
 *
 * <p>
 * This interface is a private contract between such producers
 * and consumers to allow them to exchange binary data without
 * converting it to base64.
 *
 * @see XMLStreamWriterEx
 * @author Kohsuke Kawaguchi
 * @author Paul Sandoz
 */
public interface XMLStreamReaderEx extends XMLStreamReader {
    ///**
    // * Works like {@link XMLStreamReader#getText()}
    // * but returns text as {@link DataSource}.
    // *
    // * <p>
    // * This method can be invoked whenever {@link XMLStreamReader#getText()}
    // * can be invoked. Invoking this method means the caller is assuming
    // * that the text is (conceptually) base64-encoded binary data.
    // *
    // * <p>
    // * This abstraction is necessary to treat XOP as infoset encoding.
    // * That is, you can either access the XOP-attached binary through
    // * {@link XMLStreamReader#getText()} (in which case you'll see the
    // * base64 encoded string), or you can access it as a binary data
    // * directly by using this method.
    // *
    // * <p>
    // * Note that even if you are reading from non XOP-aware {@link XMLStreamReader},
    // * this method must be still supported; if the reader is pointing
    // * to a text, this method is responsible for decoding base64 and
    // * producing a {@link DataHandler} with "application/octet-stream"
    // * as the content type.
    // *
    // * @return
    // *      always non-null valid object.
    // *      Invocations of this method may return the same object as long
    // *      as the {@link XMLStreamReader#next()} method is not used,
    // *      but otherwise {@link DataSource} object returned from this method
    // *      is considered to be owned by the client, and therefore it shouldn't
    // *      be reused by the implementation of this method.
    // *
    // *      <p>
    // *      The returned {@link DataSource} is read-only, and the caller
    // *      must not invoke {@link DataSource#getOutputStream()}.
    // *
    // * @throws IllegalStateException
    // *      if the parser is not pointing at characters infoset item.
    // * @throws XMLStreamException
    // *      if the parser points to text but text is not base64-encoded text,
    // *      or if some other parsing error occurs (such as if the &lt;xop:Include>
    // *      points to a non-existing attachment.)
    // *
    // *      <p>
    // *      It is also OK for this method to return successfully, only to fail
    // *      during an {@link InputStream} is read from {@link DataSource}.
    // */
    //DataSource getTextAsDataHandler() throws XMLStreamException;

    ///**
    // * Works like {@link XMLStreamReader#getText()}
    // * but returns text as {@link byte[]}.
    // *
    // * <p>
    // * The contract of this method is mostly the same as
    // * {@link #getTextAsDataHandler()}, except that this
    // * method returns the binary datas as an exact-size byte[].
    // *
    // * <p>
    // * This method is also not capable of reporting the content type
    // * of this binary data, even if it is available to the parser.
    // *
    // * @see #getTextAsDataHandler()
    // */
    //byte[] getTextAsByteArray() throws XMLStreamException;

    /**
     * Works like {@link #getText()}
     * but hides the actual data representation.
     *
     * @return
     *      The {@link CharSequence} that represents the
     *      character infoset items at the current position.
     *
     *      <p>
     *      The {@link CharSequence} is normally a {@link String},
     *      but can be any other {@link CharSequence} implementation.
     *      For binary data, however, use of {@link Base64Data} is
     *      recommended (so that the consumer interested in seeing it
     *      as binary data may take advantage of mor efficient
     *      data representation.)
     *
     *      <p>
     *      The object returned from this method belongs to the parser,
     *      and its content is guaranteed to be the same only until
     *      the {@link #next()} method is invoked.
     *
     * @throws IllegalStateException
     *      if the parser is not pointing at characters infoset item.
     *
     * TODO:
     *      fix the dependency to JAXB internal class.
     */
    CharSequence getPCDATA() throws XMLStreamException;

    /**
     * {@inheritDoc}
     */
    NamespaceContextEx getNamespaceContext();

    /**
     * Works like {@link #getElementText()} but trims the leading
     * and trailing whitespace.
     *
     * <p>
     * The parser can often do this more efficiently than
     * {@code getElementText().trim()}.
     *
     * @see #getElementText()
     */
    String getElementTextTrim() throws XMLStreamException;
}
