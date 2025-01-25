/*
 * Copyright (c) 1995, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.image;

import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;

public class URLImageSource extends InputStreamImageSource {
    URL url;
    URLConnection conn;
    String actualHost;
    int actualPort;

    public URLImageSource(URL u) {
        this.url = u;
    }

    public URLImageSource(String href) throws MalformedURLException {
        this(newURL(null, href));
    }

    public URLImageSource(URL u, URLConnection uc) {
        this(u);
        conn = uc;
    }

    public URLImageSource(URLConnection uc) {
        this(uc.getURL(), uc);
    }

    private synchronized URLConnection getConnection() throws IOException {
        URLConnection c;
        if (conn != null) {
            c = conn;
            conn = null;
        } else {
            c = url.openConnection();
        }
        return c;
    }

    protected ImageDecoder getDecoder() {
        InputStream is = null;
        String type = null;
        URLConnection c = null;
        try {
            c = getConnection();
            is = c.getInputStream();
            type = c.getContentType();
            URL u = c.getURL();
            if (u != url && (!u.getHost().equals(url.getHost()) ||
                             u.getPort() != url.getPort()))
            {
                // The image is allowed to come from either the host/port
                // listed in the original URL, or it can come from one other
                // host/port that the URL is redirected to.  More than that
                // and we give up and just throw a SecurityException.
                // This is not directly related to SecurityManager so keep it.
                if (actualHost != null && (!actualHost.equals(u.getHost()) ||
                                           actualPort != u.getPort()))
                {
                    throw new SecurityException("image moved!");
                }
                actualHost = u.getHost();
                actualPort = u.getPort();
            }
        } catch (IOException e) {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e2) {
                }
            } else if (c instanceof HttpURLConnection) {
                ((HttpURLConnection)c).disconnect();
            }
            return null;
        }

        ImageDecoder id = decoderForType(is, type);
        if (id == null) {
            id = getDecoder(is);
        }

        if (id == null) {
            // probably, no appropriate decoder
            if  (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            } else if (c instanceof HttpURLConnection) {
                ((HttpURLConnection)c).disconnect();
            }
        }
        return id;
    }

    @SuppressWarnings("deprecation")
    private static URL newURL(URL context, String spec) throws MalformedURLException {
        return new URL(context, spec);
    }
}
