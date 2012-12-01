/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.util;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

import javax.tools.DocumentationTool;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;

/**
 * Process and manage "-link" and "-linkoffline" to external packages. The
 * options "-link" and "-linkoffline" both depend on the fact that Javadoc now
 * generates "package-list"(lists all the packages which are getting
 * documented) file in the current or the destination directory, while
 * generating the documentation.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Robert Field
 */
public class Extern {

    /**
     * Map package names onto Extern Item objects.
     * Lazily initialized.
     */
    private Map<String,Item> packageToItemMap;

    /**
     * The global configuration information for this run.
     */
    private final Configuration configuration;

    /**
     * True if we are using -linkoffline and false if -link is used instead.
     */
    private boolean linkoffline = false;

    /**
     * Stores the info for one external doc set
     */
    private class Item {

        /**
         * Package name, found in the "package-list" file in the {@link path}.
         */
        final String packageName;

        /**
         * The URL or the directory path at which the package documentation will be
         * avaliable.
         */
        final String path;

        /**
         * If given path is directory path then true else if it is a URL then false.
         */
        final boolean relative;

        /**
         * Constructor to build a Extern Item object and map it with the package name.
         * If the same package name is found in the map, then the first mapped
         * Item object or offline location will be retained.
         *
         * @param packageName Package name found in the "package-list" file.
         * @param path        URL or Directory path from where the "package-list"
         * file is picked.
         * @param relative    True if path is URL, false if directory path.
         */
        Item(String packageName, String path, boolean relative) {
            this.packageName = packageName;
            this.path = path;
            this.relative = relative;
            if (packageToItemMap == null) {
                packageToItemMap = new HashMap<String,Item>();
            }
            if (!packageToItemMap.containsKey(packageName)) { // save the previous
                packageToItemMap.put(packageName, this);        // mapped location
            }
        }

        /**
         * String representation of "this" with packagename and the path.
         */
        public String toString() {
            return packageName + (relative? " -> " : " => ") + path;
        }
    }

    public Extern(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Determine if a doc item is externally documented.
     *
     * @param doc A ProgramElementDoc.
     */
    public boolean isExternal(ProgramElementDoc doc) {
        if (packageToItemMap == null) {
            return false;
        }
        return packageToItemMap.get(doc.containingPackage().name()) != null;
    }

    /**
     * Convert a link to be an external link if appropriate.
     *
     * @param pkgName The package name.
     * @param relativepath    The relative path.
     * @param filename    The link to convert.
     * @return if external return converted link else return null
     */
    public DocLink getExternalLink(String pkgName,
                                  DocPath relativepath, String filename) {
        return getExternalLink(pkgName, relativepath, filename, null);
    }

    public DocLink getExternalLink(String pkgName,
                                  DocPath relativepath, String filename, String memberName) {
        Item fnd = findPackageItem(pkgName);
        if (fnd == null)
            return null;

        DocPath p = fnd.relative ?
                relativepath.resolve(fnd.path).resolve(filename) :
                DocPath.create(fnd.path).resolve(filename);

        return new DocLink(p, "is-external=true", memberName);
    }

    /**
     * Build the extern package list from given URL or the directory path.
     * Flag error if the "-link" or "-linkoffline" option is already used.
     *
     * @param url        URL or Directory path.
     * @param pkglisturl This can be another URL for "package-list" or ordinary
     *                   file.
     * @param reporter   The <code>DocErrorReporter</code> used to report errors.
     * @param linkoffline True if -linkoffline is used and false if -link is used.
     */
    public boolean link(String url, String pkglisturl,
                              DocErrorReporter reporter, boolean linkoffline) {
        this.linkoffline = linkoffline;
        try {
            url = adjustEndFileSeparator(url);
            if (isUrl(pkglisturl)) {
                readPackageListFromURL(url, toURL(pkglisturl));
            } else {
                readPackageListFromFile(url, DocFile.createFileForInput(configuration, pkglisturl));
            }
            return true;
        } catch (Fault f) {
            reporter.printWarning(f.getMessage());
            return false;
        }
    }

    private URL toURL(String url) throws Fault {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new Fault(configuration.getText("doclet.MalformedURL", url), e);
        }
    }

    private class Fault extends Exception {
        private static final long serialVersionUID = 0;

        Fault(String msg, Exception cause) {
            super(msg, cause);
        }
    }

    /**
     * Get the Extern Item object associated with this package name.
     *
     * @param pkgName Package name.
     */
    private Item findPackageItem(String pkgName) {
        if (packageToItemMap == null) {
            return null;
        }
        return packageToItemMap.get(pkgName);
    }

    /**
     * If the URL or Directory path is missing end file separator, add that.
     */
    private String adjustEndFileSeparator(String url) {
        return url.endsWith("/") ? url : url + '/';
    }

    /**
     * Fetch the URL and read the "package-list" file.
     *
     * @param urlpath        Path to the packages.
     * @param pkglisturlpath URL or the path to the "package-list" file.
     */
    private void readPackageListFromURL(String urlpath, URL pkglisturlpath)
            throws Fault {
        try {
            URL link = pkglisturlpath.toURI().resolve(DocPaths.PACKAGE_LIST.getPath()).toURL();
            readPackageList(link.openStream(), urlpath, false);
        } catch (URISyntaxException exc) {
            throw new Fault(configuration.getText("doclet.MalformedURL", pkglisturlpath.toString()), exc);
        } catch (MalformedURLException exc) {
            throw new Fault(configuration.getText("doclet.MalformedURL", pkglisturlpath.toString()), exc);
        } catch (IOException exc) {
            throw new Fault(configuration.getText("doclet.URL_error", pkglisturlpath.toString()), exc);
        }
    }

    /**
     * Read the "package-list" file which is available locally.
     *
     * @param path URL or directory path to the packages.
     * @param pkgListPath Path to the local "package-list" file.
     */
    private void readPackageListFromFile(String path, DocFile pkgListPath)
            throws Fault {
        DocFile file = pkgListPath.resolve(DocPaths.PACKAGE_LIST);
        if (! (file.isAbsolute() || linkoffline)){
            file = file.resolveAgainst(DocumentationTool.Location.DOCUMENTATION_OUTPUT);
        }
        try {
            if (file.exists() && file.canRead()) {
                boolean pathIsRelative =
                        !DocFile.createFileForInput(configuration, path).isAbsolute()
                        && !isUrl(path);
                readPackageList(file.openInputStream(), path, pathIsRelative);
            } else {
                throw new Fault(configuration.getText("doclet.File_error", file.getPath()), null);
            }
        } catch (IOException exc) {
           throw new Fault(configuration.getText("doclet.File_error", file.getPath()), exc);
        }
    }

    /**
     * Read the file "package-list" and for each package name found, create
     * Extern object and associate it with the package name in the map.
     *
     * @param input    InputStream from the "package-list" file.
     * @param path     URL or the directory path to the packages.
     * @param relative Is path relative?
     */
    private void readPackageList(InputStream input, String path,
                                boolean relative)
                         throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        StringBuilder strbuf = new StringBuilder();
        try {
            int c;
            while ((c = in.read()) >= 0) {
                char ch = (char)c;
                if (ch == '\n' || ch == '\r') {
                    if (strbuf.length() > 0) {
                        String packname = strbuf.toString();
                        String packpath = path +
                                      packname.replace('.', '/') + '/';
                        new Item(packname, packpath, relative);
                        strbuf.setLength(0);
                    }
                } else {
                    strbuf.append(ch);
                }
            }
        } finally {
            input.close();
        }
    }

    public boolean isUrl (String urlCandidate) {
        try {
            new URL(urlCandidate);
            //No exception was thrown, so this must really be a URL.
            return true;
        } catch (MalformedURLException e) {
            //Since exception is thrown, this must be a directory path.
            return false;
        }
    }
}
