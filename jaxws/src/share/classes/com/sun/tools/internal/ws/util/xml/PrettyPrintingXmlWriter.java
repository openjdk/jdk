/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.ws.util.xml;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import com.sun.xml.internal.ws.util.xml.CDATA;

// ## Delay IOExceptions until flush or close
// ## Need DOM, SAX output

/**
 * A writer of XML output streams.
 *
 * <p> An XML writer knows hardly anything about XML document well-formedness,
 * to say nothing of validity.  It relies upon the invoker to ensure that the
 * generated document is well-formed and, if required, valid.
 *
 *
 * @author WS Development Team
 */

public class PrettyPrintingXmlWriter {

    private static final boolean shouldPrettyprint = true;

    private BufferedWriter out;

    private PrettyPrintingXmlWriter(OutputStreamWriter w, boolean declare)
        throws IOException {
        // XXX-NOTE - set the buffer size to 1024 here
        this.out = new BufferedWriter(w, 1024);
        String enc = w.getEncoding();

        /* Work around bogus canonical encoding names */
        if (enc.equals("UTF8"))
            enc = "UTF-8";
        else if (enc.equals("ASCII"))
            enc = "US-ASCII";

        if (declare) {
            out.write("<?xml version=\"1.0\" encoding=\"" + enc + "\"?>");
            out.newLine();
            needNewline = true;
        }
    }

    /**
     * Creates a new writer that will write to the given byte-output stream
     * using the given encoding.  An initial XML declaration will optionally be
     * written to the stream.  </p>
     *
     * @param  out
     *         The target byte-output stream
     *
     * @param  enc
     *         The character encoding to be used
     *
     * @param  declare
     *         If <tt>true</tt>, write the XML declaration to the output stream
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  UnsupportedEncodingException
     *          If the named encoding is not supported
     */
    public PrettyPrintingXmlWriter(
        OutputStream out,
        String enc,
        boolean declare)
        throws UnsupportedEncodingException, IOException {
        this(new OutputStreamWriter(out, enc), declare);
    }

    /**
     * Creates a new writer that will write to the given byte-output stream
     * using the given encoding.  An initial XML declaration will be written to
     * the stream.  </p>
     *
     * @param  out
     *         The target byte-output stream
     *
     * @param  enc
     *         The character encoding to be used
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  UnsupportedEncodingException
     *          If the named encoding is not supported
     */
    public PrettyPrintingXmlWriter(OutputStream out, String enc)
        throws UnsupportedEncodingException, IOException {
        this(new OutputStreamWriter(out, enc), true);
    }

    /**
     * Creates a new writer that will write to the given byte-output stream
     * using the UTF-8 encoding.  An initial XML declaration will be written to
     * the stream.  </p>
     *
     * @param  out
     *         The target byte-output stream
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public PrettyPrintingXmlWriter(OutputStream out) throws IOException {
        this(new OutputStreamWriter(out, "UTF-8"), true);
    }

    private char quoteChar = '"';

    /**
     * Sets the quote character to be used by this writer when writing
     * attribute values.  </p>
     *
     * @param  quote  The new quote character, either a
     *                <small>QUOTATION MARK</small> (<tt>'&#92;u0022'</tt>),
     *                or an <small>APOSTROPHE-QUOTE</small>
     *                (<tt>'&#92;u0027'</tt>)
     *
     * @throws  IllegalArgumentException
     *          If the argument is neither of the above characters
     */
    public void setQuote(char quote) {
        if (quote != '"' && quote != '\'')
            throw new IllegalArgumentException(
                "Illegal quote character: " + quote);
        quoteChar = quote;
    }

    // Quote a character
    private void quote(char c) throws IOException {
        switch (c) {
            case '&' :
                out.write("&amp;");
                break;
            case '<' :
                out.write("&lt;");
                break;
            case '>' :
                out.write("&gt;");
                break;
            default :
                out.write(c);
                break;
        }
    }

    // Quote a character in an attribute value
    private void aquote(char c) throws IOException {
        switch (c) {
            case '\'' :
                if (quoteChar == c)
                    out.write("&apos;");
                else
                    out.write(c);
                break;
            case '"' :
                if (quoteChar == c)
                    out.write("&quot;");
                else
                    out.write(c);
                break;
            default :
                quote(c);
                break;
        }
    }

    //
    private void nonQuote(char c) throws IOException {
        out.write(c);
    }

    // Quote a string containing character data
    private void quote(String s) throws IOException {
        for (int i = 0; i < s.length(); i++)
            quote(s.charAt(i));
    }

    /* Allowing support for CDATA */
    private void nonQuote(String s) throws IOException {
        for (int i = 0; i < s.length(); i++)
            nonQuote(s.charAt(i));
    }

    // Quote a string containing an attribute value
    private void aquote(String s) throws IOException {
        for (int i = 0; i < s.length(); i++)
            aquote(s.charAt(i));
    }

    private void indent(int depth) throws IOException {
        for (int i = 0; i < depth; i++)
            out.write("  ");
    }

    // Formatting state
    private int depth = 0;
    private boolean inStart = false;
    private boolean needNewline = false;
    private boolean writtenChars = false;
    private boolean inAttribute = false;
    private boolean inAttributeValue = false;

    /**
     * Writes a DOCTYPE declaration.  </p>
     *
     * @param  root  The name of the root element
     *
     * @param  dtd   The URI of the document-type definition
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void doctype(String root, String dtd) throws IOException {
        if (shouldPrettyprint && needNewline)
            out.newLine();
        needNewline = true;
        out.write("<!DOCTYPE " + root + " SYSTEM " + quoteChar);
        quote(dtd);
        out.write(quoteChar + ">");
        if (shouldPrettyprint)
            out.newLine();
    }

    private void start0(String name) throws IOException {
        finishStart();
        if (shouldPrettyprint && !writtenChars) {
            needNewline = true;
            indent(depth);
        }
        out.write('<');
        out.write(name);
        inStart = true;
        writtenChars = false;
        depth++;
    }

    private void start1(String name) throws IOException {
        finishStart();
        if (shouldPrettyprint && !writtenChars) {
            if (needNewline)
                out.newLine();
            needNewline = true;
            indent(depth);
        }
        out.write('<');
        out.write(name);
        inStart = true;
        writtenChars = false;
        depth++;
    }

    private void finishStart() throws IOException {
        if (inStart) {
            if (inAttribute)
                out.write(quoteChar);
            out.write('>');
            inStart = false;
            inAttribute = false;
            inAttributeValue = false;
        }
    }

    /**
     * Writes a start tag for the named element.  </p>
     *
     * @param  name  The name to be used in the start tag
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void start(String name) throws IOException {
        start1(name);
    }

    /**
     * Writes an attribute for the current element.  </p>
     *
     * @param  name  The attribute's name
     *
     * @param  value  The attribute's value
     *
     * @throws  IllegalStateException
     *          If the previous method invoked upon this object was neither
     *          {@link #start start} nor {@link #attribute attribute}
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void attribute(String name, String value) throws IOException {
        attributeName(name);
        attributeValue(value);
    }

    /**
     * Writes an attribute (unquoted) for the current element.  </p>
     *
     * @param  name  The attribute's name
     *
     * @param  value  The attribute's value
     *
     * @throws  IllegalStateException
     *          If the previous method invoked upon this object was neither
     *          {@link #start start} nor {@link #attribute attribute}
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void attributeUnquoted(String name, String value)
        throws IOException {
        attributeName(name);
        attributeValueUnquoted(value);
    }

    /**
     * Writes an attribute for the current element.  </p>
     *
     * @param  prefix  The attribute's prefix
     *
     * @param  name  The attribute's name
     *
     * @param  value  The attribute's value
     *
     * @throws  IllegalStateException
     *          If the previous method invoked upon this object was neither
     *          {@link #start start} nor {@link #attribute attribute}
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void attribute(String prefix, String name, String value)
        throws IOException {
        attributeName(prefix, name);
        attributeValue(value);
    }

    /**
     * Writes an attribute (unquoted) for the current element.  </p>
     *
     * @param  prefix  The attribute's prefix
     *
     * @param  name  The attribute's name
     *
     * @param  value  The attribute's value
     *
     * @throws  IllegalStateException
     *          If the previous method invoked upon this object was neither
     *          {@link #start start} nor {@link #attribute attribute}
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void attributeUnquoted(String prefix, String name, String value)
        throws IOException {
        attributeName(prefix, name);
        attributeValueUnquoted(value);
    }

    /**
     * Writes an attribute name for the current element.  After invoking this
     * method, invoke the {@link #attributeValue attributeValue} method to
     * write the attribute value, or invoke the {@link #attributeValueToken
     * attributeValueToken} method to write one or more space-separated value
     * tokens.  </p>
     *
     * @param   name  The attribute's name
     *
     * @throws  IllegalStateException
     *          If the previous method invoked upon this object was neither
     *          {@link #start start} nor {@link #attribute attribute}
     */
    public void attributeName(String name) throws IOException {
        if (!inStart)
            throw new IllegalStateException();
        if (inAttribute) {
            out.write(quoteChar);
            inAttribute = false;
            inAttributeValue = false;
        }
        out.write(' ');
        out.write(name);
        out.write('=');
        out.write(quoteChar);
        inAttribute = true;
    }

    /**
     * Writes an attribute name for the current element.  After invoking this
     * method, invoke the {@link #attributeValue attributeValue} method to
     * write the attribute value, or invoke the {@link #attributeValueToken
     * attributeValueToken} method to write one or more space-separated value
     * tokens.  </p>
     *
     * @param   prefix The attribute's prefix
     * @param   name  The attribute's name
     *
     * @throws  IllegalStateException
     *          If the previous method invoked upon this object was neither
     *          {@link #start start} nor {@link #attribute attribute}
     */
    public void attributeName(String prefix, String name) throws IOException {
        if (!inStart)
            throw new IllegalStateException();
        if (inAttribute) {
            out.write(quoteChar);
            inAttribute = false;
            inAttributeValue = false;
        }
        out.write(' ');
        out.write(prefix);
        out.write(':');
        out.write(name);
        out.write('=');
        out.write(quoteChar);
        inAttribute = true;
    }

    /**
     * Writes a value for the current attribute.  </p>
     *
     * @param   value  The attribute's value
     *
     * @throws  IllegalStateException
     *          If the previous method invoked upon this object was not
     *          {@link #attributeName attributeName}
     */
    public void attributeValue(String value) throws IOException {
        if (!inAttribute || inAttributeValue)
            throw new IllegalStateException();
        aquote(value);
        out.write(quoteChar);
        inAttribute = false;
    }

    /**
     * Writes a value (unquoted) for the current attribute.  </p>
     *
     * @param   value  The attribute's value
     *
     * @throws  IllegalStateException
     *          If the previous method invoked upon this object was not
     *          {@link #attributeName attributeName}
     */
    public void attributeValueUnquoted(String value) throws IOException {
        if (!inAttribute || inAttributeValue)
            throw new IllegalStateException();
        out.write(value, 0, value.length());
        out.write(quoteChar);
        inAttribute = false;
    }

    /**
     * Writes one token of the current attribute's value.  Adjacent tokens will
     * be separated by single space characters.  </p>
     *
     * @param   token  The token to be written
     *
     * @throws  IllegalStateException
     *          If the previous method invoked upon this object was neither
     *          {@link #attributeName attributeName} nor
     *          {@link #attributeValueToken attributeValueToken}
     */
    public void attributeValueToken(String token) throws IOException {
        if (!inAttribute)
            throw new IllegalStateException();
        if (inAttributeValue)
            out.write(' ');
        aquote(token);
        inAttributeValue = true;
    }

    /**
     * Writes an end tag for the named element.  </p>
     *
     * @param  name  The name to be used in the end tag
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void end(String name) throws IOException {
        if (inStart) {
            if (inAttribute)
                out.write(quoteChar);
            out.write("/>");
            inStart = false;
            inAttribute = false;
            inAttributeValue = false;
        } else {
            out.write("</");
            out.write(name);
            out.write('>');
        }
        depth--;
        writtenChars = false;
    }

    /**
     * Writes some character data.  </p>
     *
     * @param  chars  The character data to be written
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void chars(String chars) throws IOException {
        finishStart();
        quote(chars);
        writtenChars = true;
    }

    public void chars(CDATA chars) throws IOException {
        finishStart();
        nonQuote(chars.getText());
        writtenChars = true;
    }

    /**
     * Writes some character data, skipping quoting.  </p>
     *
     * @param  chars  The character data to be written
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void charsUnquoted(String chars) throws IOException {
        finishStart();
        out.write(chars, 0, chars.length());
        writtenChars = true;
    }

    /**
     * Writes some character data, skipping quoting.  </p>
     *
     * @param  buf   Buffer containing the character data to be written
     * @param  off    The offset of the data to be written
     * @param  len    The length of the data to be written
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void charsUnquoted(char[] buf, int off, int len)
        throws IOException {
        finishStart();
        out.write(buf, off, len);
        writtenChars = true;
    }

    /**
     * Writes a leaf element with the given character content.  </p>
     *
     * @param name  ame to be used in the start and end tags
     *
     * @param chars  character data to be written
     *
     * <p> This method writes a start tag with the given name, followed by the
     * given character data, followed by an end tag.  If the <tt>chars</tt>
     * parameter is <tt>null</tt> or the empty string then an empty tag is
     * written.  </p>
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void leaf(String name, String chars) throws IOException {
        start1(name);
        if ((chars != null) && (chars.length() != 0))
            chars(chars);
        end(name);
    }

    public void inlineLeaf(String name, String chars) throws IOException {
        start0(name);
        if ((chars != null) && (chars.length() != 0))
            chars(chars);
        end(name);
    }

    /**
     * Writes an empty leaf element.  </p>
     *
     * @param  name name to be used in the empty-element tag
     */
    public void leaf(String name) throws IOException {
        leaf(name, null);
    }

    public void inlineLeaf(String name) throws IOException {
        inlineLeaf(name, null);
    }

    /**
     * Flushes the writer.  </p>
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void flush() throws IOException {
        if (depth != 0)
            throw new IllegalStateException("Nonzero depth");
        // if (shouldPrettyprint)
        out.newLine();
        out.flush();
    }

    /**
     * Flushes the writer and closes the underlying byte-output stream.  </p>
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public void close() throws IOException {
        flush();
        out.close();
    }
}
