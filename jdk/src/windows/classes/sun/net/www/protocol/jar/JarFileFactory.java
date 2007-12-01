/*
 * Copyright 1999-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.net.www.protocol.jar;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipFile;
import java.security.Permission;

/* A factory for cached JAR file. This class is used to both retrieve
 * and cache Jar files.
 *
 * @author Benjamin Renaud
 * @since JDK1.2
 */
class JarFileFactory implements URLJarFile.URLJarFileCloseController {

    /* the url to file cache */
    private static HashMap fileCache = new HashMap();

    /* the file to url cache */
    private static HashMap urlCache = new HashMap();

    URLConnection getConnection(JarFile jarFile) throws IOException {
        URL u = (URL)urlCache.get(jarFile);
        if (u != null)
            return u.openConnection();

        return null;
    }

    public JarFile get(URL url) throws IOException {
        return get(url, true);
    }

    JarFile get(URL url, boolean useCaches) throws IOException {
        if (url.getProtocol().equalsIgnoreCase("file")) {
            // Deal with UNC pathnames specially. See 4180841

            String host = url.getHost();
            if (host != null && !host.equals("") &&
                !host.equalsIgnoreCase("localhost")) {

                url = new URL("file", "", "//" + host + url.getPath());
            }
        }

        JarFile result = null;
        JarFile local_result = null;

        if (useCaches) {
            synchronized (this) {
                result = getCachedJarFile(url);
            }
            if (result == null) {
                local_result = URLJarFile.getJarFile(url, this);
                synchronized (this) {
                    result = getCachedJarFile(url);
                    if (result == null) {
                        fileCache.put(url, local_result);
                        urlCache.put(local_result, url);
                        result = local_result;
                    } else {
                        if (local_result != null) {
                            local_result.close();
                        }
                    }
                }
            }
        } else {
            result = URLJarFile.getJarFile(url, this);
        }
        if (result == null)
            throw new FileNotFoundException(url.toString());

        return result;
    }

    /**
     * Callback method of the URLJarFileCloseController to
     * indicate that the JarFile is close. This way we can
     * remove the JarFile from the cache
     */
    public void close(JarFile jarFile) {
        URL urlRemoved = (URL) urlCache.remove(jarFile);
        if( urlRemoved != null) {
                fileCache.remove(urlRemoved);
        }
    }

    private JarFile getCachedJarFile(URL url) {
        JarFile result = (JarFile)fileCache.get(url);

        /* if the JAR file is cached, the permission will always be there */
        if (result != null) {
            Permission perm = getPermission(result);
            if (perm != null) {
                SecurityManager sm = System.getSecurityManager();
                if (sm != null) {
                    try {
                        sm.checkPermission(perm);
                    } catch (SecurityException se) {
                        // fallback to checkRead/checkConnect for pre 1.2
                        // security managers
                        if ((perm instanceof java.io.FilePermission) &&
                            perm.getActions().indexOf("read") != -1) {
                            sm.checkRead(perm.getName());
                        } else if ((perm instanceof
                            java.net.SocketPermission) &&
                            perm.getActions().indexOf("connect") != -1) {
                            sm.checkConnect(url.getHost(), url.getPort());
                        } else {
                            throw se;
                        }
                    }
                }
            }
        }
        return result;
    }

    private Permission getPermission(JarFile jarFile) {
        try {
            URLConnection uc = (URLConnection)getConnection(jarFile);
            if (uc != null)
                return uc.getPermission();
        } catch (IOException ioe) {
            // gulp
        }

        return null;
    }
}
