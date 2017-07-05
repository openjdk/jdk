/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.util.List;

import jdk.internal.jimage.ImageLocation;
import jdk.internal.jimage.ImageReader;
import jdk.internal.jimage.ImageReaderFactory;

import sun.misc.URLClassPath;
import sun.misc.Resource;
import sun.net.www.ParseUtil;
import sun.net.www.URLConnection;

/**
 * URLConnection implementation that can be used to connect to resources
 * contained in the runtime image.
 */
public class JavaRuntimeURLConnection extends URLConnection {

    // ImageReader to access resources in jimage
    private static final ImageReader reader = ImageReaderFactory.getImageReader();

    // the module and resource name in the URL
    private final String module;
    private final String name;

    // the Resource when connected
    private volatile Resource resource;

    // the permission to access resources in the runtime image, created lazily
    private static volatile Permission permission;

    JavaRuntimeURLConnection(URL url) throws IOException {
        super(url);
        String path = url.getPath();
        if (path.length() == 0 || path.charAt(0) != '/')
            throw new MalformedURLException(url + " missing path or /");
        if (path.length() == 1) {
            this.module = null;
            this.name = null;
        } else {
            int pos = path.indexOf('/', 1);
            if (pos == -1) {
                this.module = path.substring(1);
                this.name = null;
            } else {
                this.module = path.substring(1, pos);
                this.name = ParseUtil.decode(path.substring(pos+1));
            }
        }
    }

    /**
     * Finds a resource in a module, returning {@code null} if the resource
     * is not found.
     */
    private static Resource findResource(String module, String name) {
        if (reader != null) {
            URL url = toJrtURL(module, name);
            ImageLocation location = reader.findLocation(module, name);
            if (location != null && URLClassPath.checkURL(url) != null) {
                return new Resource() {
                    @Override
                    public String getName() {
                        return name;
                    }
                    @Override
                    public URL getURL() {
                        return url;
                    }
                    @Override
                    public URL getCodeSourceURL() {
                        return toJrtURL(module);
                    }
                    @Override
                    public InputStream getInputStream() throws IOException {
                        byte[] resource = reader.getResource(location);
                        return new ByteArrayInputStream(resource);
                    }
                    @Override
                    public int getContentLength() {
                        long size = location.getUncompressedSize();
                        return (size > Integer.MAX_VALUE) ? -1 : (int) size;
                    }
                };
            }
        }
        return null;
    }

    @Override
    public synchronized void connect() throws IOException {
        if (!connected) {
            if (name == null) {
                String s = (module == null) ? "" : module;
                throw new IOException("cannot connect to jrt:/" + s);
            }
            resource = findResource(module, name);
            if (resource == null)
                throw new IOException(module + "/" + name + " not found");
            connected = true;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        connect();
        return resource.getInputStream();
    }

    @Override
    public long getContentLengthLong() {
        try {
            connect();
            return resource.getContentLength();
        } catch (IOException ioe) {
            return -1L;
        }
    }

    @Override
    public int getContentLength() {
        long len = getContentLengthLong();
        return len > Integer.MAX_VALUE ? -1 : (int)len;
    }

    @Override
    public Permission getPermission() throws IOException {
        Permission p = permission;
        if (p == null) {
            // using lambda expression here leads to recursive initialization
            PrivilegedAction<String> pa = new PrivilegedAction<String>() {
                public String run() { return System.getProperty("java.home"); }
            };
            String home = AccessController.doPrivileged(pa);
            p = new FilePermission(home + File.separator + "-", "read");
            permission = p;
        }
        return p;
    }

    /**
     * Returns a jrt URL for the given module and resource name.
     */
    private static URL toJrtURL(String module, String name) {
        try {
            return new URL("jrt:/" + module + "/" + name);
        } catch (MalformedURLException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Returns a jrt URL for the given module.
     */
    private static URL toJrtURL(String module) {
        try {
            return new URL("jrt:/" + module);
        } catch (MalformedURLException e) {
            throw new InternalError(e);
        }
    }
}
