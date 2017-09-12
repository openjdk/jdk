/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.mimepull;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.List;

/**
 * InternetHeaders is a utility class that manages RFC822 style
 * headers. Given an RFC822 format message stream, it reads lines
 * until the blank line that indicates end of header. The input stream
 * is positioned at the start of the body. The lines are stored
 * within the object and can be extracted as either Strings or
 * {@link Header} objects. <p>
 * <p/>
 * This class is mostly intended for service providers. MimeMessage
 * and MimeBody use this class for holding their headers. <p>
 * <p/>
 * <hr> <strong>A note on RFC822 and MIME headers</strong><p>
 * <p/>
 * RFC822 and MIME header fields <strong>must</strong> contain only
 * US-ASCII characters. If a header contains non US-ASCII characters,
 * it must be encoded as per the rules in RFC 2047. The MimeUtility
 * class provided in this package can be used to to achieve this.
 * Callers of the <code>setHeader</code>, <code>addHeader</code>, and
 * <code>addHeaderLine</code> methods are responsible for enforcing
 * the MIME requirements for the specified headers.  In addition, these
 * header fields must be folded (wrapped) before being sent if they
 * exceed the line length limitation for the transport (1000 bytes for
 * SMTP).  Received headers may have been folded.  The application is
 * responsible for folding and unfolding headers as appropriate. <p>
 *
 * @author John Mani
 * @author Bill Shannon
 */
final class InternetHeaders {

    private final FinalArrayList<Hdr> headers = new FinalArrayList<Hdr>();

    /**
     * Read and parse the given RFC822 message stream till the
     * blank line separating the header from the body. Store the
     * header lines inside this InternetHeaders object. <p>
     * <p/>
     * Note that the header lines are added into this InternetHeaders
     * object, so any existing headers in this object will not be
     * affected.
     *
     * @param   lis RFC822 input stream
     */
    InternetHeaders(MIMEParser.LineInputStream lis) {
        // Read header lines until a blank line. It is valid
        // to have BodyParts with no header lines.
        String line;
        String prevline = null; // the previous header line, as a string
        // a buffer to accumulate the header in, when we know it's needed
        StringBuilder lineBuffer = new StringBuilder();

        try {
            //while ((line = lis.readLine()) != null) {
            do {
                line = lis.readLine();
                if (line != null &&
                        (line.startsWith(" ") || line.startsWith("\t"))) {
                    // continuation of header
                    if (prevline != null) {
                        lineBuffer.append(prevline);
                        prevline = null;
                    }
                    lineBuffer.append("\r\n");
                    lineBuffer.append(line);
                } else {
                    // new header
                    if (prevline != null) {
                        addHeaderLine(prevline);
                    } else if (lineBuffer.length() > 0) {
                        // store previous header first
                        addHeaderLine(lineBuffer.toString());
                        lineBuffer.setLength(0);
                    }
                    prevline = line;
                }
            } while (line != null && line.length() > 0);
        } catch (IOException ioex) {
            throw new MIMEParsingException("Error in input stream", ioex);
        }
    }

    /**
     * Return all the values for the specified header. The
     * values are String objects.  Returns <code>null</code>
     * if no headers with the specified name exist.
     *
     * @param   name header name
     * @return          array of header values, or null if none
     */
    List<String> getHeader(String name) {
        // XXX - should we just step through in index order?
        FinalArrayList<String> v = new FinalArrayList<String>(); // accumulate return values

        int len = headers.size();
        for( int i=0; i<len; i++ ) {
            Hdr h = (Hdr) headers.get(i);
            if (name.equalsIgnoreCase(h.name)) {
                v.add(h.getValue());
            }
        }
        return (v.size() == 0) ? null : v;
    }

    /**
     * Return all the headers as an Enumeration of
     * {@link Header} objects.
     *
     * @return  Header objects
     */
    FinalArrayList<? extends Header> getAllHeaders() {
        return headers; // conceptually it should be read-only, but for performance reason I'm not wrapping it here
    }

    /**
     * Add an RFC822 header line to the header store.
     * If the line starts with a space or tab (a continuation line),
     * add it to the last header line in the list. <p>
     * <p/>
     * Note that RFC822 headers can only contain US-ASCII characters
     *
     * @param   line    raw RFC822 header line
     */
    void addHeaderLine(String line) {
        try {
            char c = line.charAt(0);
            if (c == ' ' || c == '\t') {
                Hdr h = (Hdr) headers.get(headers.size() - 1);
                h.line += "\r\n" + line;
            } else {
                headers.add(new Hdr(line));
            }
        } catch (StringIndexOutOfBoundsException e) {
            // line is empty, ignore it
        } catch (NoSuchElementException e) {
            // XXX - vector is empty?
        }
    }

}

/*
 * A private utility class to represent an individual header.
 */

class Hdr implements Header {

    String name;    // the canonicalized (trimmed) name of this header
    // XXX - should name be stored in lower case?
    String line;    // the entire RFC822 header "line"

    /*
     * Constructor that takes a line and splits out
     * the header name.
     */
    Hdr(String l) {
        int i = l.indexOf(':');
        if (i < 0) {
            // should never happen
            name = l.trim();
        } else {
            name = l.substring(0, i).trim();
        }
        line = l;
    }

    /*
     * Constructor that takes a header name and value.
     */
    Hdr(String n, String v) {
        name = n;
        line = n + ": " + v;
    }

    /*
     * Return the "name" part of the header line.
     */
    @Override
    public String getName() {
        return name;
    }

    /*
     * Return the "value" part of the header line.
     */
    @Override
    public String getValue() {
        int i = line.indexOf(':');
        if (i < 0) {
            return line;
        }

        int j;
        if (name.equalsIgnoreCase("Content-Description")) {
            // Content-Description should retain the folded whitespace after header unfolding -
            // rf. RFC2822 section 2.2.3, rf. RFC2822 section 3.2.3
            for (j = i + 1; j < line.length(); j++) {
                char c = line.charAt(j);
                if (!(/*c == ' ' ||*/c == '\t' || c == '\r' || c == '\n')) {
                    break;
                }
            }
        } else {
            // skip whitespace after ':'
            for (j = i + 1; j < line.length(); j++) {
                char c = line.charAt(j);
                if (!(c == ' ' || c == '\t' || c == '\r' || c == '\n')) {
                    break;
                }
            }
        }
        return line.substring(j);
    }
}
