/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.util;

import java.io.*;

import javax.xml.transform.stream.StreamSource;


/**
 *
 * @author Anil Vijendran
 */
public class JAXMStreamSource extends StreamSource {
    InputStream in;
    Reader reader;
    private static final boolean lazyContentLength;
    static {
        lazyContentLength = SAAJUtil.getSystemBoolean("saaj.lazy.contentlength");
    }
    public JAXMStreamSource(InputStream is) throws IOException {
        if (lazyContentLength) {
            in = is;
        } else if (is instanceof ByteInputStream) {
            this.in = (ByteInputStream) is;
        } else {
            ByteOutputStream bout = null;
            try {
                bout = new ByteOutputStream();
                bout.write(is);
                this.in = bout.newInputStream();
            } finally {
                if (bout != null)
                    bout.close();
            }
        }
    }

    public JAXMStreamSource(Reader rdr) throws IOException {

        if (lazyContentLength) {
            this.reader = rdr;
            return;
        }
        CharArrayWriter cout = new CharArrayWriter();
        char[] temp = new char[1024];
        int len;

        while (-1 != (len = rdr.read(temp)))
            cout.write(temp, 0, len);

        this.reader = new CharArrayReader(cout.toCharArray(), 0, cout.size());
    }

    @Override
    public InputStream getInputStream() {
        return in;
    }

    @Override
    public Reader getReader() {
        return reader;
    }

    public void reset() throws IOException {
            if (in != null)
                in.reset();
            if (reader != null)
                reader.reset();
    }
}
