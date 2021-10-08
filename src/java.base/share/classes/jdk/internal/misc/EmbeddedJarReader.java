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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;

/**
 * This class is for retrieving the class bytes from the bytes of a jar file.
 * It delegates to AbstractJarReader to load the class using the class bytes.
 */
class EmbeddedJarReader extends AbstractJarReader {
    final static int BUFFER_SIZE = 4096;
    String jarPath;
    ZipInputStream zins;
    HashMap<String, byte[]> entryCache;

    protected EmbeddedJarReader(String jarPath, byte[] jarBytes) {
        this.jarPath = jarPath;
        this.zins = new ZipInputStream(new ByteArrayInputStream(jarBytes));
        this.entryCache =  new HashMap<String, byte[]>();
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException {
        ProtectionDomain pd = null;
        try {
            URL u = new URL("file:" + jarPath);
            CodeSource cs = new CodeSource(u, (CodeSigner[])null);
            pd = new ProtectionDomain(cs, null);
        } catch (MalformedURLException mue) {
            // ignore MalformedURLException. The class can be loaded with null pd.
            // The pd is for showing the "source:" in the class loading trace.
        }
        return super.loadClass(name, pd);
    }

    @Override
    byte[] getEntry(String name) throws IOException, ClassNotFoundException {
        boolean found = false;
        byte[] bytes = entryCache.get(name);
        if (bytes != null) {
            return bytes;
        } else {
            ZipEntry ze = null;
            while ((ze = zins.getNextEntry()) != null) {
                if (!ze.isDirectory()) {
                    bytes = readEntry(zins, ze);
                    String entryName = ze.getName();
                    entryCache.put(entryName, bytes);
                    if (entryName.equals(name)) {
                        found = true;
                        break;
                    }
                }
            }
        }

        if (found) {
            return bytes;
        } else {
            throw new ClassNotFoundException(name);
        }
    }

    private static byte[] readEntry(InputStream in, ZipEntry entry) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        long size = entry.getSize();
        int nRead;
        byte[] data = new byte[BUFFER_SIZE];
        while ((nRead = in.read(data, 0, data.length)) != -1) {
            baos.write(data, 0, nRead);
        }
        baos.close();
        return baos.toByteArray();
    }
}
