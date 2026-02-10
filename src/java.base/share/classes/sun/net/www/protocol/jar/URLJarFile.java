/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www.protocol.jar;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import sun.net.www.ParseUtil;

/* URL jar file is a common JarFile subtype used for JarURLConnection */
public class URLJarFile extends JarFile {

    /* Controller of the Jar File's closing */
    private URLJarFileCloseController closeController = null;

    private Manifest superMan;
    private Attributes superAttr;
    private Map<String, Attributes> superEntries;

    static JarFile getJarFile(URL url, URLJarFileCloseController closeController) throws IOException {
        if (ParseUtil.isLocalFileURL(url)) {
            Runtime.Version version = "runtime".equals(url.getRef())
                    ? JarFile.runtimeVersion()
                    : JarFile.baseVersion();
            return new URLJarFile(url, closeController, version);
        } else {
            return retrieve(url, closeController);
        }
    }

    private URLJarFile(File file, URLJarFileCloseController closeController, Runtime.Version version)
            throws IOException {
        super(file, true, ZipFile.OPEN_READ | ZipFile.OPEN_DELETE, version);
        this.closeController = closeController;
    }

    private URLJarFile(URL url, URLJarFileCloseController closeController, Runtime.Version version)
            throws IOException {
        super(new File(ParseUtil.decode(url.getFile())), true, ZipFile.OPEN_READ, version);
        this.closeController = closeController;
    }

    /**
     * Returns the <code>ZipEntry</code> for the given entry name or
     * <code>null</code> if not found.
     *
     * @param name the JAR file entry name
     * @return the <code>ZipEntry</code> for the given entry name or
     *         <code>null</code> if not found
     * @see java.util.zip.ZipEntry
     */
    public ZipEntry getEntry(String name) {
        ZipEntry ze = super.getEntry(name);
        if (ze != null) {
            if (ze instanceof JarEntry)
                return new URLJarFileEntry((JarEntry)ze);
            else
                throw new InternalError(super.getClass() +
                                        " returned unexpected entry type " +
                                        ze.getClass());
        }
        return null;
    }

    public Manifest getManifest() throws IOException {

        if (!isSuperMan()) {
            return null;
        }

        Manifest man = new Manifest();
        Attributes attr = man.getMainAttributes();
        attr.putAll((Map)superAttr.clone());

        // now deep copy the manifest entries
        if (superEntries != null) {
            Map<String, Attributes> entries = man.getEntries();
            for (String key : superEntries.keySet()) {
                Attributes at = superEntries.get(key);
                entries.put(key, (Attributes) at.clone());
            }
        }

        return man;
    }

    /* If close controller is set the notify the controller about the pending close */
    public void close() throws IOException {
        if (closeController != null) {
            closeController.close(this);
        }
        super.close();
    }

    // optimal side-effects
    private synchronized boolean isSuperMan() throws IOException {

        if (superMan == null) {
            superMan = super.getManifest();
        }

        if (superMan != null) {
            superAttr = superMan.getMainAttributes();
            superEntries = superMan.getEntries();
            return true;
        } else
            return false;
    }

    /**
     * Given a URL, retrieves a JAR file, caches it to disk, and creates a
     * cached JAR file object.
     */
    private static JarFile retrieve(final URL url, final URLJarFileCloseController closeController) throws IOException {
        Runtime.Version version = "runtime".equals(url.getRef())
                ? JarFile.runtimeVersion()
                : JarFile.baseVersion();
        try (final InputStream in = url.openConnection().getInputStream()) {
            Path tmpFile = Files.createTempFile("jar_cache", null);
            try {
                Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
                JarFile jarFile = new URLJarFile(tmpFile.toFile(), closeController, version);
                tmpFile.toFile().deleteOnExit();
                return jarFile;
            } catch (Throwable thr) {
                try {
                    Files.delete(tmpFile);
                } catch (IOException ioe) {
                    thr.addSuppressed(ioe);
                }
                throw thr;
            }
        }
    }

    private class URLJarFileEntry extends JarEntry {

        URLJarFileEntry(JarEntry je) {
            super(je);
        }

        @Override
        public Attributes getAttributes() throws IOException {
            if (URLJarFile.this.isSuperMan()) {
                Map<String, Attributes> e = URLJarFile.this.superEntries;
                if (e != null) {
                    Attributes a = e.get(getName());
                    if (a != null)
                        return  (Attributes)a.clone();
                }
            }
            return null;
        }
    }

    public interface URLJarFileCloseController {
        public void close(JarFile jarFile);
    }
}
