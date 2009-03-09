/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.xml.internal.bind.DatatypeConverterImpl;
import com.sun.xml.internal.bind.v2.runtime.Name;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.MarshallerImpl;

import org.xml.sax.SAXException;

/**
 * {@link XmlOutput} implementation specialized for UTF-8.
 *
 * @author Kohsuke Kawaguchi
 * @author Paul Sandoz
 */
public class UTF8XmlOutput extends XmlOutputAbstractImpl {
    protected final OutputStream out;

    /** prefixes encoded. */
    private Encoded[] prefixes = new Encoded[8];

    /**
     * Of the {@link #prefixes}, number of filled entries.
     * This is almost the same as {@link NamespaceContextImpl#count()},
     * except that it allows us to handle contextual in-scope namespace bindings correctly.
     */
    private int prefixCount;

    /** local names encoded in UTF-8. All entries are pre-filled. */
    private final Encoded[] localNames;

    /** Temporary buffer used to encode text. */
    /*
     * TODO
     * The textBuffer could write directly to the _octetBuffer
     * when encoding a string if Encoder is modified.
     * This will avoid an additional memory copy.
     */
    private final Encoded textBuffer = new Encoded();

    /** Buffer of octets for writing. */
    // TODO: Obtain buffer size from property on the JAXB context
    protected final byte[] octetBuffer = new byte[1024];

    /** Index in buffer to write to. */
    protected int octetBufferIndex;

    /**
     * Set to true to indicate that we need to write '>'
     * to close a start tag. Deferring the write of this char
     * allows us to write "/>" for empty elements.
     */
    protected boolean closeStartTagPending = false;

    /**
     * @see MarshallerImpl#header
     */
    private String header;

    /**
     *
     * @param localNames
     *      local names encoded in UTF-8.
     */
    public UTF8XmlOutput(OutputStream out, Encoded[] localNames) {
        this.out = out;
        this.localNames = localNames;
        for( int i=0; i<prefixes.length; i++ )
            prefixes[i] = new Encoded();
    }

    public void setHeader(String header) {
        this.header = header;
    }

    @Override
    public void startDocument(XMLSerializer serializer, boolean fragment, int[] nsUriIndex2prefixIndex, NamespaceContextImpl nsContext) throws IOException, SAXException, XMLStreamException {
        super.startDocument(serializer, fragment,nsUriIndex2prefixIndex,nsContext);

        octetBufferIndex = 0;
        if(!fragment) {
            write(XML_DECL);
        }
        if(header!=null) {
            textBuffer.set(header);
            textBuffer.write(this);
        }
    }

    public void endDocument(boolean fragment) throws IOException, SAXException, XMLStreamException {
        flushBuffer();
        super.endDocument(fragment);
    }

    /**
     * Writes '>' to close the start tag, if necessary.
     */
    protected final void closeStartTag() throws IOException {
        if(closeStartTagPending) {
            write('>');
            closeStartTagPending = false;
        }
    }

    public void beginStartTag(int prefix, String localName) throws IOException {
        closeStartTag();
        int base= pushNsDecls();
        write('<');
        writeName(prefix,localName);
        writeNsDecls(base);
    }

    public void beginStartTag(Name name) throws IOException {
        closeStartTag();
        int base = pushNsDecls();
        write('<');
        writeName(name);
        writeNsDecls(base);
    }

    private int pushNsDecls() {
        int total = nsContext.count();
        NamespaceContextImpl.Element ns = nsContext.getCurrent();

        if(total > prefixes.length) {
            // reallocate
            int m = Math.max(total,prefixes.length*2);
            Encoded[] buf = new Encoded[m];
            System.arraycopy(prefixes,0,buf,0,prefixes.length);
            for( int i=prefixes.length; i<buf.length; i++ )
                buf[i] = new Encoded();
            prefixes = buf;
        }

        int base = Math.min(prefixCount,ns.getBase());
        int size = nsContext.count();
        for( int i=base; i<size; i++ ) {
            String p = nsContext.getPrefix(i);

            Encoded e = prefixes[i];

            if(p.length()==0) {
                e.buf = EMPTY_BYTE_ARRAY;
                e.len = 0;
            } else {
                e.set(p);
                e.append(':');
            }
        }
        prefixCount = size;
        return base;
    }

    protected void writeNsDecls(int base) throws IOException {
        NamespaceContextImpl.Element ns = nsContext.getCurrent();
        int size = nsContext.count();

        for( int i=ns.getBase(); i<size; i++ )
            writeNsDecl(i);
    }

    /**
     * Writes a single namespace declaration for the specified prefix.
     */
    protected final void writeNsDecl(int prefixIndex) throws IOException {
        String p = nsContext.getPrefix(prefixIndex);

        if(p.length()==0) {
            if(nsContext.getCurrent().isRootElement()
            && nsContext.getNamespaceURI(prefixIndex).length()==0)
                return;     // no point in declaring xmlns="" on the root element
            write(XMLNS_EQUALS);
        } else {
            Encoded e = prefixes[prefixIndex];
            write(XMLNS_COLON);
            write(e.buf,0,e.len-1); // skip the trailing ':'
            write(EQUALS);
        }
        doText(nsContext.getNamespaceURI(prefixIndex),true);
        write('\"');
    }

    private void writePrefix(int prefix) throws IOException {
        prefixes[prefix].write(this);
    }

    private void writeName(Name name) throws IOException {
        writePrefix(nsUriIndex2prefixIndex[name.nsUriIndex]);
        localNames[name.localNameIndex].write(this);
    }

    private void writeName(int prefix, String localName) throws IOException {
        writePrefix(prefix);
        textBuffer.set(localName);
        textBuffer.write(this);
    }

    @Override
    public void attribute(Name name, String value) throws IOException {
        write(' ');
        if(name.nsUriIndex==-1) {
            localNames[name.localNameIndex].write(this);
        } else
            writeName(name);
        write(EQUALS);
        doText(value,true);
        write('\"');
    }

    public void attribute(int prefix, String localName, String value) throws IOException {
        write(' ');
        if(prefix==-1) {
            textBuffer.set(localName);
            textBuffer.write(this);
        } else
            writeName(prefix,localName);
        write(EQUALS);
        doText(value,true);
        write('\"');
    }

    public void endStartTag() throws IOException {
        closeStartTagPending = true;
    }

    @Override
    public void endTag(Name name) throws IOException {
        if(closeStartTagPending) {
            write(EMPTY_TAG);
            closeStartTagPending = false;
        } else {
            write(CLOSE_TAG);
            writeName(name);
            write('>');
        }
    }

    public void endTag(int prefix, String localName) throws IOException {
        if(closeStartTagPending) {
            write(EMPTY_TAG);
            closeStartTagPending = false;
        } else {
            write(CLOSE_TAG);
            writeName(prefix,localName);
            write('>');
        }
    }

    public void text(String value, boolean needSP) throws IOException {
        closeStartTag();
        if(needSP)
            write(' ');
        doText(value,false);
    }

    public void text(Pcdata value, boolean needSP) throws IOException {
        closeStartTag();
        if(needSP)
            write(' ');
        value.writeTo(this);
    }

    private void doText(String value,boolean isAttribute) throws IOException {
        textBuffer.setEscape(value,isAttribute);
        textBuffer.write(this);
    }

    public final void text(int value) throws IOException {
        closeStartTag();
        /*
         * TODO
         * Change to use the octet buffer directly
         */

        // max is -2147483648 and 11 digits
        boolean minus = (value<0);
        textBuffer.ensureSize(11);
        byte[] buf = textBuffer.buf;
        int idx = 11;

        do {
            int r = value%10;
            if(r<0) r = -r;
            buf[--idx] = (byte)('0'|r);    // really measn 0x30+r but 0<=r<10, so bit-OR would do.
            value /= 10;
        } while(value!=0);

        if(minus)   buf[--idx] = (byte)'-';

        write(buf,idx,11-idx);
    }

    /**
     * Writes the given byte[] as base64 encoded binary to the output.
     *
     * <p>
     * Being defined on this class allows this method to access the buffer directly,
     * which translates to a better performance.
     */
    public void text(byte[] data, int dataLen) throws IOException {
        closeStartTag();

        int start = 0;

        while(dataLen>0) {
            // how many bytes (in data) can we write without overflowing the buffer?
            int batchSize = Math.min(((octetBuffer.length-octetBufferIndex)/4)*3,dataLen);

            // write the batch
            octetBufferIndex = DatatypeConverterImpl._printBase64Binary(data,start,batchSize,octetBuffer,octetBufferIndex);

            if(batchSize<dataLen)
                flushBuffer();

            start += batchSize;
            dataLen -= batchSize;

        }
    }

//
//
// series of the write method that places bytes to the output
// (by doing some buffering internal to this class)
//

    /**
     * Writes one byte directly into the buffer.
     *
     * <p>
     * This method can be used somewhat like the {@code text} method,
     * but it doesn't perform character escaping.
     */
    public final void write(int i) throws IOException {
        if (octetBufferIndex < octetBuffer.length) {
            octetBuffer[octetBufferIndex++] = (byte)i;
        } else {
            out.write(octetBuffer);
            octetBufferIndex = 1;
            octetBuffer[0] = (byte)i;
        }
    }

    protected final void write(byte[] b) throws IOException {
        write(b, 0,  b.length);
    }

    protected final void write(byte[] b, int start, int length) throws IOException {
        if ((octetBufferIndex + length) < octetBuffer.length) {
            System.arraycopy(b, start, octetBuffer, octetBufferIndex, length);
            octetBufferIndex += length;
        } else {
            out.write(octetBuffer, 0, octetBufferIndex);
            out.write(b, start, length);
            octetBufferIndex = 0;
        }
    }

    protected final void flushBuffer() throws IOException {
        out.write(octetBuffer, 0, octetBufferIndex);
        octetBufferIndex = 0;
    }

    static byte[] toBytes(String s) {
        byte[] buf = new byte[s.length()];
        for( int i=s.length()-1; i>=0; i-- )
            buf[i] = (byte)s.charAt(i);
        return buf;
    }

    // per instance copy to prevent an attack where malicious OutputStream
    // rewrites the byte array.
    private final byte[] XMLNS_EQUALS = _XMLNS_EQUALS.clone();
    private final byte[] XMLNS_COLON = _XMLNS_COLON.clone();
    private final byte[] EQUALS = _EQUALS.clone();
    private final byte[] CLOSE_TAG = _CLOSE_TAG.clone();
    private final byte[] EMPTY_TAG = _EMPTY_TAG.clone();
    private final byte[] XML_DECL = _XML_DECL.clone();

    // masters
    private static final byte[] _XMLNS_EQUALS = toBytes(" xmlns=\"");
    private static final byte[] _XMLNS_COLON = toBytes(" xmlns:");
    private static final byte[] _EQUALS = toBytes("=\"");
    private static final byte[] _CLOSE_TAG = toBytes("</");
    private static final byte[] _EMPTY_TAG = toBytes("/>");
    private static final byte[] _XML_DECL = toBytes("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");

    // no need to copy
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
}
