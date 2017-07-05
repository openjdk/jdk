/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @(#)InternetHeaders.java   1.16 02/08/08
 */



package com.sun.xml.internal.messaging.saaj.packaging.mime.internet;

import com.sun.xml.internal.messaging.saaj.packaging.mime.Header;
import com.sun.xml.internal.messaging.saaj.packaging.mime.MessagingException;
import com.sun.xml.internal.messaging.saaj.packaging.mime.util.LineInputStream;
import com.sun.xml.internal.messaging.saaj.util.FinalArrayList;

import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractList;
import java.util.List;
import java.util.NoSuchElementException;

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
 * @see MimeUtility
 */
public final class InternetHeaders {

    private final FinalArrayList<hdr> headers = new FinalArrayList<hdr>();

    /**
     * Lazily cerated view of header lines (Strings).
     */
    private List<String> headerValueView;

    /**
     * Create an empty InternetHeaders object.
     */
    public InternetHeaders() {
    }

    /**
     * Read and parse the given RFC822 message stream till the
     * blank line separating the header from the body. The input
     * stream is left positioned at the start of the body. The
     * header lines are stored internally. <p>
     * <p/>
     * For efficiency, wrap a BufferedInputStream around the actual
     * input stream and pass it as the parameter.
     *
     * @param   is RFC822 input stream
     */
    public InternetHeaders(InputStream is) throws MessagingException {
        load(is);
    }

    /**
     * Read and parse the given RFC822 message stream till the
     * blank line separating the header from the body. Store the
     * header lines inside this InternetHeaders object. <p>
     * <p/>
     * Note that the header lines are added into this InternetHeaders
     * object, so any existing headers in this object will not be
     * affected.
     *
     * @param   is RFC822 input stream
     */
    public void load(InputStream is) throws MessagingException {
        // Read header lines until a blank line. It is valid
        // to have BodyParts with no header lines.
        String line;
        LineInputStream lis = new LineInputStream(is);
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
                    if (prevline != null)
                        addHeaderLine(prevline);
                    else if (lineBuffer.length() > 0) {
                        // store previous header first
                        addHeaderLine(lineBuffer.toString());
                        lineBuffer.setLength(0);
                    }
                    prevline = line;
                }
            } while (line != null && line.length() > 0);
        } catch (IOException ioex) {
            throw new MessagingException("Error in input stream", ioex);
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
    public String[] getHeader(String name) {
        // XXX - should we just step through in index order?
        FinalArrayList<String> v = new FinalArrayList<String>(); // accumulate return values

        int len = headers.size();
        for( int i=0; i<len; i++ ) {
            hdr h = headers.get(i);
            if (name.equalsIgnoreCase(h.name)) {
                v.add(h.getValue());
            }
        }
        if (v.size() == 0)
            return (null);
        // convert Vector to an array for return
        return v.toArray(new String[v.size()]);
    }

    /**
     * Get all the headers for this header name, returned as a single
     * String, with headers separated by the delimiter. If the
     * delimiter is <code>null</code>, only the first header is
     * returned.  Returns <code>null</code>
     * if no headers with the specified name exist.
     *
     * @param delimiter delimiter
     * @return the value fields for all headers with
     *         this name, or null if none
     * @param   name header name
     */
    public String getHeader(String name, String delimiter) {
        String[] s = getHeader(name);

        if (s == null)
            return null;

        if ((s.length == 1) || delimiter == null)
            return s[0];

        StringBuilder r = new StringBuilder(s[0]);
        for (int i = 1; i < s.length; i++) {
            r.append(delimiter);
            r.append(s[i]);
        }
        return r.toString();
    }

    /**
     * Change the first header line that matches name
     * to have value, adding a new header if no existing header
     * matches. Remove all matching headers but the first. <p>
     * <p/>
     * Note that RFC822 headers can only contain US-ASCII characters
     *
     * @param   name    header name
     * @param   value   header value
     */
    public void setHeader(String name, String value) {
        boolean found = false;

        for (int i = 0; i < headers.size(); i++) {
            hdr h = headers.get(i);
            if (name.equalsIgnoreCase(h.name)) {
                if (!found) {
                    int j;
                    if (h.line != null && (j = h.line.indexOf(':')) >= 0) {
                        h.line = h.line.substring(0, j + 1) + " " + value;
                    } else {
                        h.line = name + ": " + value;
                    }
                    found = true;
                } else {
                    headers.remove(i);
                    i--;    // have to look at i again
                }
            }
        }

        if (!found) {
            addHeader(name, value);
        }
    }

    /**
     * Add a header with the specified name and value to the header list. <p>
     * <p/>
     * Note that RFC822 headers can only contain US-ASCII characters.
     *
     * @param   name    header name
     * @param   value   header value
     */
    public void addHeader(String name, String value) {
        int pos = headers.size();
        for (int i = headers.size() - 1; i >= 0; i--) {
            hdr h = headers.get(i);
            if (name.equalsIgnoreCase(h.name)) {
                headers.add(i + 1, new hdr(name, value));
                return;
            }
            // marker for default place to add new headers
            if (h.name.equals(":"))
                pos = i;
        }
        headers.add(pos, new hdr(name, value));
    }

    /**
     * Remove all header entries that match the given name
     *
     * @param   name header name
     */
    public void removeHeader(String name) {
        for (int i = 0; i < headers.size(); i++) {
            hdr h = headers.get(i);
            if (name.equalsIgnoreCase(h.name)) {
                headers.remove(i);
                i--;    // have to look at i again
            }
        }
    }

    /**
     * Return all the headers as an Enumeration of
     * {@link Header} objects.
     *
     * @return  Header objects
     */
    public List<? extends Header> getAllHeaders() {
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
    public void addHeaderLine(String line) {
        try {
            char c = line.charAt(0);
            if (c == ' ' || c == '\t') {
                hdr h = headers.get(headers.size() - 1);
                h.line += "\r\n" + line;
            } else
                headers.add(new hdr(line));
        } catch (StringIndexOutOfBoundsException e) {
            // line is empty, ignore it
            return;
        } catch (NoSuchElementException e) {
            // XXX - vector is empty?
        }
    }

    /**
     * Return all the header lines as a collection
     */
    public List<String> getAllHeaderLines() {
        if(headerValueView==null)
            headerValueView = new AbstractList<String>() {
                public String get(int index) {
                    return headers.get(index).line;
                }

                public int size() {
                    return headers.size();
                }
            };
        return headerValueView;
    }
}

/*
 * A private utility class to represent an individual header.
 */

class hdr implements Header {
    // XXX - should these be private?
    String name;    // the canonicalized (trimmed) name of this header
    // XXX - should name be stored in lower case?
    String line;    // the entire RFC822 header "line"

    /*
     * Constructor that takes a line and splits out
     * the header name.
     */
    hdr(String l) {
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
    hdr(String n, String v) {
        name = n;
        line = n + ": " + v;
    }

    /*
     * Return the "name" part of the header line.
     */
    public String getName() {
        return name;
    }

    /*
     * Return the "value" part of the header line.
     */
    public String getValue() {
        int i = line.indexOf(':');
        if (i < 0)
            return line;

        int j;
        if (name.equalsIgnoreCase("Content-Description")) {
            // Content-Description should retain the folded whitespace after header unfolding -
            // rf. RFC2822 section 2.2.3, rf. RFC2822 section 3.2.3
            for (j = i + 1; j < line.length(); j++) {
                char c = line.charAt(j);
                if (!(/*c == ' ' ||*/c == '\t' || c == '\r' || c == '\n'))
                    break;
            }
        } else {
            // skip whitespace after ':'
            for (j = i + 1; j < line.length(); j++) {
                char c = line.charAt(j);
                if (!(c == ' ' || c == '\t' || c == '\r' || c == '\n'))
                    break;
            }
        }
        return line.substring(j);
    }
}
