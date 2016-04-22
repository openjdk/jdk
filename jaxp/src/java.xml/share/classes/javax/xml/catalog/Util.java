/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package javax.xml.catalog;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.xml.internal.SecuritySupport;

/**
 *
 * @since 9
 */
class Util {

    /**
     * Resolves the specified file path to an absolute systemId. If it is
     * relative, it shall be resolved using the base or user.dir property if
     * base is not specified.
     *
     * @param file The specified file path
     * @param baseURI the base URI
     * @return The URI
     * @throws CatalogException if the specified file path can not be converted
     * to a system id
     */
    static URI verifyAndGetURI(String file, URL baseURI)
            throws MalformedURLException, URISyntaxException, IllegalArgumentException {
        URL filepath;
        URI temp;
        if (file != null && file.length() > 0) {
            File f = new File(file);

            if (baseURI != null && !f.isAbsolute()) {
                filepath = new URL(baseURI, fixSlashes(file));
                temp = filepath.toURI();
            } else {
                temp = resolveURI(file);
            }
            //Paths.get may throw IllegalArgumentException
            Path path = Paths.get(temp);
            if (path.toFile().isFile()) {
                return temp;
            }
        }
        return null;
    }

    /**
     * Resolves the specified uri. If the uri is relative, makes it absolute by
     * the user.dir directory.
     *
     * @param uri The specified URI.
     * @return The resolved URI
     */
    static URI resolveURI(String uri) throws MalformedURLException {
        if (uri == null) {
            uri = "";
        }

        URI temp = null;
        try {
            URL url = new URL(uri);
            temp = url.toURI();
        } catch (MalformedURLException | URISyntaxException mue) {
            File file = new File(uri);
            temp = file.toURI();
        }

        return temp;
    }

    /**
     * Replace backslashes with forward slashes. (URLs always use forward
     * slashes.)
     *
     * @param sysid The input system identifier.
     * @return The same system identifier with backslashes turned into forward
     * slashes.
     */
    static String fixSlashes(String sysid) {
        return sysid.replace('\\', '/');
    }

    /**
     * Find catalog file paths by reading the system property, and then
     * jaxp.properties if the system property is not specified.
     *
     * @param sysPropertyName the name of system property
     * @return the catalog file paths, or null if not found.
     */
    static String[] getCatalogFiles(String sysPropertyName) {
        String value = SecuritySupport.getJAXPSystemProperty(sysPropertyName);
        if (value != null && !value.equals("")) {
            return value.split(";");
        }
        return null;
    }

    /**
     * Checks whether the specified string is null or empty, returns the original
     * string with leading and trailing spaces removed if not.
     * @param test the string to be tested
     * @return the original string with leading and trailing spaces removed,
     * or null if it is null or empty
     *
     */
    static String getNotNullOrEmpty(String test) {
        if (test == null) {
            return test;
        } else {
            String temp = test.trim();
            if (temp.length() == 0) {
                return null;
            } else {
                return temp;
            }
        }
    }
}
