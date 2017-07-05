/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.api.scripting;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import jdk.nashorn.internal.runtime.Source;

/**
 * A Reader that reads from a URL. Used to make sure that the reader
 * reads content from given URL and can be trusted to do so.
 */
public final class URLReader extends Reader {
    // underlying URL
    private final URL url;
    // Charset used to convert
    private final Charset cs;

    // lazily initialized underlying reader for URL
    private Reader reader;

    /**
     * Constructor
     *
     * @param url URL for this URLReader
     * @throws NullPointerException if url is null
     */
    public URLReader(final URL url) {
        this(url, (Charset)null);
    }

    /**
     * Constructor
     *
     * @param url URL for this URLReader
     * @param charsetName  Name of the Charset used to convert bytes to chars
     * @throws NullPointerException if url is null
     */
    public URLReader(final URL url, final String charsetName) {
        this(url, Charset.forName(charsetName));
    }

    /**
     * Constructor
     *
     * @param url URL for this URLReader
     * @param cs  Charset used to convert bytes to chars
     * @throws NullPointerException if url is null
     */
    public URLReader(final URL url, final Charset cs) {
        // null check
        url.getClass();
        this.url = url;
        this.cs  = cs;
    }

    @Override
    public int read(final char cbuf[], final int off, final int len) throws IOException {
        return getReader().read(cbuf, off, len);
    }

    @Override
    public void close() throws IOException {
        getReader().close();
    }

    /**
     * URL of this reader
     * @return the URL from which this reader reads.
     */
    public URL getURL() {
        return url;
    }

    /**
     * Charset used by this reader
     *
     * @return the Chartset used to convert bytes to chars
     */
    public Charset getCharset() {
        return cs;
    }

    // lazily initialize char array reader using URL content
    private Reader getReader() throws IOException {
        synchronized (lock) {
            if (reader == null) {
                reader = new CharArrayReader(Source.readFully(url, cs));
            }
        }

        return reader;
    }
}
