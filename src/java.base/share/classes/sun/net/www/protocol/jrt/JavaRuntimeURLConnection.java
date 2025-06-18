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
import java.net.MalformedURLException;
import java.net.URL;

import jdk.internal.jimage.ImageReader;
import jdk.internal.jimage.ImageReader.Node;
import jdk.internal.jimage.ImageReaderFactory;

import sun.net.www.ParseUtil;
import sun.net.www.URLConnection;

/**
 * URLConnection implementation that can be used to connect to resources
 * contained in the runtime image.
 */
public class JavaRuntimeURLConnection extends URLConnection {

    // ImageReader to access resources in jimage (never null).
    private static final ImageReader READER = ImageReaderFactory.getImageReader();

    // The module and resource name in the URL ("jrt:/<module-name>/<resource-name>").
    //
    // It is important to note that all of this information comes from the given
    // URL's path part, and there's no requirement for there to be distinct rules
    // about percent encoding, and it is likely that any differences between how
    // module names and resource names are treated is unintentional. The rules
    // about percent encoding may well be tightened up in the future.
    //
    // The module name is not percent-decoded, and can be empty.
    private final String module;
    // The resource name permits UTF-8 percent encoding of non-ASCII characters.
    private final String name;

    // The resource node (when connected).
    private volatile Node resource;

    JavaRuntimeURLConnection(URL url) throws IOException {
        super(url);
        // TODO: Allow percent encoding in module names.
        // TODO: Consider rejecting URLs with fragments, queries or authority.
        String urlPath = url.getPath();
        if (urlPath.isEmpty() || urlPath.charAt(0) != '/') {
            throw new MalformedURLException(url + " missing path or /");
        }
        int pathSep = urlPath.indexOf('/', 1);
        if (pathSep == -1) {
            // No trailing resource path. This can never "connect" or return a
            // resource, but might be useful as a representation to pass around.
            // The module name *can* be empty here (e.g. "jrt:/") but not null.
            this.module = urlPath.substring(1);
            this.name = null;
        } else {
            this.module = urlPath.substring(1, pathSep);
            this.name = percentDecode(urlPath.substring(pathSep + 1));
        }
    }

    /**
     * Finds and caches the resource node associated with this URL and marks the
     * connection as "connected".
     */
    private synchronized Node getResourceNode() throws IOException {
        if (resource == null) {
            if (name == null) {
                throw new IOException("cannot connect to jrt:/" + module);
            }
            Node node = READER.findNode("/modules/" + module + "/" + name);
            if (node == null || !node.isResource()) {
                throw new IOException(module + "/" + name + " not found");
            }
            this.resource = node;
            super.connected = true;
        }
        return resource;
    }

    @Override
    public void connect() throws IOException {
        getResourceNode();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(READER.getResource(getResourceNode()));
    }

    @Override
    public long getContentLengthLong() {
        try {
            return getResourceNode().size();
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
        if (path.indexOf('%') == -1) {
            // Nothing to decode (overwhelmingly common case).
            return path;
        }
        // TODO: Maybe reject over-encoded paths here to reduce obfuscation
        //  (especially %2F (/) and %24 ($), but probably just all ASCII).
        try {
            return ParseUtil.decode(path);
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException(e.getMessage());
        }
    }
}
