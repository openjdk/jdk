/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www.protocol.jrt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;

import jdk.internal.jimage.ImageReader;
import jdk.internal.jimage.ImageReader.Node;
import jdk.internal.jimage.ImageReaderFactory;

import sun.net.www.ParseUtil;
import sun.net.www.URLConnection;

/**
 * URLConnection implementation that can be used to connect to resources
 * contained in the runtime image. See section "New URI scheme for naming stored
 * modules, classes, and resources" in <a href="https://openjdk.org/jeps/220">
 * JEP 220</a>.
 */
public class JavaRuntimeURLConnection extends URLConnection {

    // ImageReader to access resources in jimage.
    private static final ImageReader READER = ImageReaderFactory.getImageReader();

    // The module and resource name in the URL (i.e. "jrt:/[$MODULE[/$PATH]]").
    //
    // The module name is not percent-decoded, and can be empty.
    private final String module;
    // The resource name permits UTF-8 percent encoding of non-ASCII characters.
    private final String path;

    // The resource node (non-null when connected).
    private Node resourceNode;

    JavaRuntimeURLConnection(URL url) throws IOException {
        super(url);
        String urlPath = url.getPath();
        if (urlPath.isEmpty() || urlPath.charAt(0) != '/') {
            throw new MalformedURLException(url + " missing path or /");
        }
        int pathSep = urlPath.indexOf('/', 1);
        if (pathSep == -1) {
            // No trailing resource path. This can never "connect" or return a
            // resource (see JEP 220 for details).
            this.module = urlPath.substring(1);
            this.path = null;
        } else {
            this.module = urlPath.substring(1, pathSep);
            this.path = percentDecode(urlPath.substring(pathSep + 1));
        }
    }

    /**
     * Finds and caches the resource node associated with this URL and marks the
     * connection as "connected".
     */
    private synchronized Node connectResourceNode() throws IOException {
        if (resourceNode == null) {
            if (module.isEmpty() || path == null) {
                throw new IOException("cannot connect to jrt:/" + module);
            }
            Node node = READER.findNode("/modules/" + module + "/" + path);
            if (node == null || !node.isResource()) {
                throw new IOException(module + "/" + path + " not found");
            }
            this.resourceNode = node;
            super.connected = true;
        }
        return resourceNode;
    }

    @Override
    public void connect() throws IOException {
        connectResourceNode();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(READER.getResource(connectResourceNode()));
    }

    @Override
    public long getContentLengthLong() {
        try {
            return connectResourceNode().size();
        } catch (IOException ioe) {
            return -1L;
        }
    }

    @Override
    public int getContentLength() {
        long len = getContentLengthLong();
        return len > Integer.MAX_VALUE ? -1 : (int)len;
    }

    // Perform percent decoding of the resource name/path from the URL.
    private static String percentDecode(String path) throws MalformedURLException {
        // Any additional special case decoding logic should go here.
        try {
            return ParseUtil.decode(path);
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException(e.getMessage());
        }
    }
}
