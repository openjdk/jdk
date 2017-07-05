/*
 * Copyright (c) 1996, 1998, Oracle and/or its affiliates. All rights reserved.
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


package sun.tools.jar;

import sun.awt.image.URLImageSource;
import sun.awt.image.ImageDecoder;
import java.net.URL;
import java.net.JarURLConnection;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.io.InputStream;
import java.io.IOException;


public class JarImageSource extends URLImageSource {
    String mimeType;
    String entryName = null;
    URL url;

    /**
     * Create an image source from a Jar entry URL with the specified
     * mime type.
     */
    public JarImageSource(URL u, String type) {
        super(u);
        url = u;
        mimeType = type;
    }

    /**
     * Create an image source from a Jar file/entry URL
     * with the specified entry name and mime type.
     */
    public JarImageSource(URL u, String name, String type) {
        this(u, type);
        this.entryName = name;
    }

    protected ImageDecoder getDecoder() {
        InputStream is = null;
        try {
            JarURLConnection c = (JarURLConnection)url.openConnection();
            JarFile f = c.getJarFile();
            JarEntry e = c.getJarEntry();

            if (entryName != null && e == null) {
                e = f.getJarEntry(entryName);
            }
            if (e == null || (e != null && entryName != null
                              && (!(entryName.equals(e.getName()))))) {
                return null;
            }
            is = f.getInputStream(e);
        } catch (IOException e) {
            return null;
        }

        ImageDecoder id = decoderForType(is, mimeType);
        if (id == null) {
            id = getDecoder(is);
        }
        return id;
    }
}
