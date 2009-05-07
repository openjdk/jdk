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

import com.sun.xml.internal.org.jvnet.staxex.XMLStreamReaderEx;

import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import javax.activation.DataHandler;
import java.io.OutputStream;

/**
 * {@link XMLStreamWriter} extended to support XOP.
 *
 * <p>
 * Some infoset serializer (such as XOP encoder, FastInfoset) uses a format
 * that can represent binary data more efficiently than base64 encoding.
 * Such infoset serializer may choose to implement this interface, to allow
 * the caller to pass in binary data more efficiently without first converting
 * it to binary data.
 *
 * <p>
 * Callers capable of using this interface can see if the serializer supports
 * it by simply downcasting {@link XMLStreamWriter} to {@link XMLStreamWriterEx}.
 *
 * <h2>TODO</h2>
 * <ol>
 * <li>
 *   Add methods to write other primitive types, such as hex and integers
 *   (and arrays of).
 *   A textual implementation would write characters in accordance
 *   to the canonical lexical definitions specified in W3C XML Schema: datatypes.
 *   A MTOM implementation would write characters except for the case where octets
 *   that would otherwise be base64 encoded when using the textual implementation.
 *   A Fast Infoset implementation would encoded binary data the primitive types in
 *   binary form.
 * <li>
 *   Consider renaming writeBinary to writeBytesAsBase64 to be consistent with
 *   infoset abstraction.
 * <li>
 *   Add the ability to writeStart and writeEnd on attributes so that the same
 *   methods for writing primitive types (and characters, which will require new methods)
 *   can be used for writing attribute values as well as element content.
 * </ol>
 *
 * @see XMLStreamReaderEx
 * @author Kohsuke Kawaguchi
 * @author Paul Sandoz
 */
public interface XMLStreamWriterEx extends XMLStreamWriter {

    /**
     * Write the binary data.
     *
     * <p>
     * Conceptually (infoset-wise), this produces the base64-encoded binary data on the
     * output. But this allows implementations like FastInfoset or XOP to do the smart
     * thing.
     *
     * <p>
     * The use of this method has some restriction to support XOP. Namely, this method
     * must be invoked as a sole content of an element.
     *
     * <p>
     * (data,start,len) triplet identifies the binary data to be written.
     *
     * @param contentType
     *      this mandatory parameter identifies the MIME type of the binary data.
     *      If the MIME type isn't known by the caller, "application/octet-stream" can
     *      be always used to indicate "I don't know." Never null.
     */
    void writeBinary(byte[] data, int start, int len, String contentType) throws XMLStreamException;

    /**
     * Writes the binary data.
     *
     * <p>
     * This method works like the {@link #writeBinary(byte[], int, int, String)} method,
     * except that it takes the binary data in the form of {@link DataHandler}, which
     * contains a MIME type ({@link DataHandler#getContentType()} as well as the payload
     * {@link DataHandler#getInputStream()}.
     *
     * @param data
     *      always non-null.
     */
    void writeBinary(DataHandler data) throws XMLStreamException;

    /**
     * Writes the binary data.
     *
     * <p>
     * This version of the writeBinary method allows the caller to produce
     * the binary data by writing it to {@link OutputStream}.
     *
     * <p>
     * It is the caller's responsibility to write and close
     * a stream before it invokes any other methods on {@link XMLStreamWriter}.
     *
     * TODO: experimental. appreciate feedback
     * @param contentType
     *      See the content-type parameter of
     *      {@link #writeBinary(byte[], int, int, String)}. Must not be null.
     *
     * @return
     *      always return a non-null {@link OutputStream}.
     */
    OutputStream writeBinary(String contentType) throws XMLStreamException;

    /**
     * Writes like {@link #writeCharacters(String)} but hides
     * actual data format.
     *
     * @return
     *      The {@link CharSequence} that represents the
     *      character infoset items to be written.
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
     */
    void writePCDATA(CharSequence data) throws XMLStreamException;

    /**
     * {@inheritDoc}
     */
    NamespaceContextEx getNamespaceContext();
}
