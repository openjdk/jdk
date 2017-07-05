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
import java.util.concurrent.CopyOnWriteArrayList;

import sun.misc.Resource;
import sun.net.www.ParseUtil;
import sun.net.www.URLConnection;

/**
 * URLConnection implementation that can be used to connect to resources
 * contained in the runtime image.
 */
public class JavaRuntimeURLConnection extends URLConnection {

    /**
     * Finds resource {@code name} in module {@code module}.
     */
    public interface ResourceFinder {
        Resource find(String module, String name) throws IOException;
    }

    /**
     * The list of resource finders for jimages in the runtime image.
     */
    private static final List<ResourceFinder> finders = new CopyOnWriteArrayList<>();

    /**
     * Called on behalf of the boot, extension and system class loaders to
     * register a resource finder.
     */
    public static void register(ResourceFinder finder) {
        finders.add(finder);
    }

    private static Resource find(String module, String name) throws IOException {
        for (ResourceFinder finder: finders) {
            Resource r = finder.find(module, name);
            if (r != null) return r;
        }
        return null;
    }

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

    @Override
    public synchronized void connect() throws IOException {
        if (!connected) {
            if (name == null) {
                String s = (module == null) ? "" : module;
                throw new IOException("cannot connect to jrt:/" + s);
            }
            resource = find(module, name);
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
}
