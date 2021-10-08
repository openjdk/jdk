/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */
package jdk.internal.misc;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;

/**
 * This class makes use of the URLClassLoader to retrieve the bytes of a
 * class or jar file.
 * It delegates to AbstractJarReader to load the class using the class bytes.
 */
class SimpleJarReader extends AbstractJarReader {
    URLClassLoader loader;
    URL url;

    protected SimpleJarReader(URL url) throws MalformedURLException {
        loader = new URLClassLoader(new URL[]{url});
        this.url = url;
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException {
        CodeSource cs = new CodeSource(url, (CodeSigner[])null);
        ProtectionDomain pd = new ProtectionDomain(cs, null);
        return super.loadClass(name, pd);
    }

    @Override
    byte[] getEntry(String name) throws IOException {
        byte[] bytes = null;
        if (loader.findResource(name) != null) {
            InputStream is = loader.getResourceAsStream(name);
            bytes = new byte[is.available()];
            is.read(bytes);
            return bytes;
        } else {
            return null;
        }
    }
}
