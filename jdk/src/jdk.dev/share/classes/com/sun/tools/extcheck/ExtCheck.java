/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.extcheck;

import java.util.*;
import java.net.MalformedURLException;
import java.util.Vector;
import java.io.*;
import java.util.StringTokenizer;
import java.net.URL;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.net.URLConnection;
import java.security.Permission;
import java.util.jar.*;
import java.net.JarURLConnection;
import sun.net.www.ParseUtil;

/**
 * ExtCheck reports on clashes between a specified (target)
 * jar file and jar files already installed in the extensions
 * directory.
 *
 * @author Benedict Gomes
 * @since 1.2
 */

public class ExtCheck {

    private static final boolean DEBUG = false;

    // The following strings hold the values of the version variables
    // for the target jar file
    private String targetSpecTitle;
    private String targetSpecVersion;
    private String targetSpecVendor;
    private String targetImplTitle;
    private String targetImplVersion;
    private String targetImplVendor;
    private String targetsealed;

    /* Flag to indicate whether extra information should be dumped to stdout */
    private boolean verboseFlag;

    /*
     * Create a new instance of the jar reporting tool for a particular
     * targetFile.
     * @param targetFile is the file to compare against.
     * @param verbose indicates whether to dump filenames and manifest
     *                information (on conflict) to the standard output.
     */
    static ExtCheck create(File targetFile, boolean verbose) {
        return new ExtCheck(targetFile, verbose);
    }

    private ExtCheck(File targetFile, boolean verbose) {
        verboseFlag = verbose;
        investigateTarget(targetFile);
    }


    private void investigateTarget(File targetFile) {
        verboseMessage("Target file:" + targetFile);
        Manifest targetManifest = null;
        try {
            File canon = new File(targetFile.getCanonicalPath());
            URL url = ParseUtil.fileToEncodedURL(canon);
            if (url != null){
                JarLoader loader = new JarLoader(url);
                JarFile jarFile = loader.getJarFile();
                targetManifest = jarFile.getManifest();
            }
        } catch (MalformedURLException e){
            error("Malformed URL ");
        } catch (IOException e) {
            error("IO Exception ");
        }
        if (targetManifest == null)
            error("No manifest available in "+targetFile);
        Attributes attr = targetManifest.getMainAttributes();
        if (attr != null) {
            targetSpecTitle   = attr.getValue(Name.SPECIFICATION_TITLE);
            targetSpecVersion = attr.getValue(Name.SPECIFICATION_VERSION);
            targetSpecVendor  = attr.getValue(Name.SPECIFICATION_VENDOR);
            targetImplTitle   = attr.getValue(Name.IMPLEMENTATION_TITLE);
            targetImplVersion = attr.getValue(Name.IMPLEMENTATION_VERSION);
            targetImplVendor  = attr.getValue(Name.IMPLEMENTATION_VENDOR);
            targetsealed      = attr.getValue(Name.SEALED);
        } else {
            error("No attributes available in the manifest");
        }
        if (targetSpecTitle == null)
            error("The target file does not have a specification title");
        if (targetSpecVersion == null)
            error("The target file does not have a specification version");
        verboseMessage("Specification title:" + targetSpecTitle);
        verboseMessage("Specification version:" + targetSpecVersion);
        if (targetSpecVendor != null)
            verboseMessage("Specification vendor:" + targetSpecVendor);
        if (targetImplVersion != null)
            verboseMessage("Implementation version:" + targetImplVersion);
        if (targetImplVendor != null)
            verboseMessage("Implementation vendor:" + targetImplVendor);
        verboseMessage("");
    }

    /**
     * Verify that none of the jar files in the install directory
     * has the same specification-title and the same or a newer
     * specification-version.
     *
     * @return Return true if the target jar file is newer
     *        than any installed jar file with the same specification-title,
     *        otherwise return false
     */
    boolean checkInstalledAgainstTarget(){
        String s = System.getProperty("java.ext.dirs");
        File [] dirs;
        if (s != null) {
            StringTokenizer st =
                new StringTokenizer(s, File.pathSeparator);
            int count = st.countTokens();
            dirs = new File[count];
            for (int i = 0; i < count; i++) {
                dirs[i] = new File(st.nextToken());
            }
        } else {
            dirs = new File[0];
        }

        boolean result = true;
        for (int i = 0; i < dirs.length; i++) {
            String[] files = dirs[i].list();
            if (files != null) {
                for (int j = 0; j < files.length; j++) {
                    try {
                        File f = new File(dirs[i],files[j]);
                        File canon = new File(f.getCanonicalPath());
                        URL url = ParseUtil.fileToEncodedURL(canon);
                        if (url != null){
                            result = result && checkURLRecursively(1,url);
                        }
                    } catch (MalformedURLException e){
                        error("Malformed URL");
                    } catch (IOException e) {
                        error("IO Exception");
                    }
                }
            }
        }
        if (result) {
            generalMessage("No conflicting installed jar found.");
        } else {
            generalMessage("Conflicting installed jar found. "
                           + " Use -verbose for more information.");
        }
        return result;
    }

    /**
     * Recursively verify that a jar file, and any urls mentioned
     * in its class path, do not conflict with the target jar file.
     *
     * @param indent is the current nesting level
     * @param url is the path to the jar file being checked.
     * @return true if there is no newer URL, otherwise false
     */
    private boolean checkURLRecursively(int indent, URL url)
        throws IOException
    {
        verboseMessage("Comparing with " + url);
        JarLoader jarloader = new JarLoader(url);
        JarFile j = jarloader.getJarFile();
        Manifest man = j.getManifest();
        if (man != null) {
            Attributes attr = man.getMainAttributes();
            if (attr != null){
                String title   = attr.getValue(Name.SPECIFICATION_TITLE);
                String version = attr.getValue(Name.SPECIFICATION_VERSION);
                String vendor  = attr.getValue(Name.SPECIFICATION_VENDOR);
                String implTitle   = attr.getValue(Name.IMPLEMENTATION_TITLE);
                String implVersion
                    = attr.getValue(Name.IMPLEMENTATION_VERSION);
                String implVendor  = attr.getValue(Name.IMPLEMENTATION_VENDOR);
                String sealed      = attr.getValue(Name.SEALED);
                if (title != null){
                    if (title.equals(targetSpecTitle)){
                        if (version != null){
                            if (version.equals(targetSpecVersion) ||
                                isNotOlderThan(version,targetSpecVersion)){
                                verboseMessage("");
                                verboseMessage("CONFLICT DETECTED ");
                                verboseMessage("Conflicting file:"+ url);
                                verboseMessage("Installed Version:" +
                                               version);
                                if (implTitle != null)
                                    verboseMessage("Implementation Title:"+
                                                   implTitle);
                                if (implVersion != null)
                                    verboseMessage("Implementation Version:"+
                                                   implVersion);
                                if (implVendor != null)
                                    verboseMessage("Implementation Vendor:"+
                                                   implVendor);
                                return false;
                            }
                        }
                    }
                }
            }
        }
        boolean result = true;
        URL[] loaderList = jarloader.getClassPath();
        if (loaderList != null) {
            for(int i=0; i < loaderList.length; i++){
                if (url != null){
                    boolean res =  checkURLRecursively(indent+1,loaderList[i]);
                    result = res && result;
                }
            }
        }
        return result;
    }

    /**
     *  See comment in method java.lang.Package.isCompatibleWith.
     *  Return true if already is not older than target. i.e. the
     *  target file may be superseded by a file already installed
     */
    private boolean isNotOlderThan(String already,String target)
        throws NumberFormatException
    {
            if (already == null || already.length() < 1) {
            throw new NumberFormatException("Empty version string");
        }

            // Until it matches scan and compare numbers
            StringTokenizer dtok = new StringTokenizer(target, ".", true);
            StringTokenizer stok = new StringTokenizer(already, ".", true);
        while (dtok.hasMoreTokens() || stok.hasMoreTokens()) {
            int dver;
            int sver;
            if (dtok.hasMoreTokens()) {
                dver = Integer.parseInt(dtok.nextToken());
            } else
                dver = 0;

            if (stok.hasMoreTokens()) {
                sver = Integer.parseInt(stok.nextToken());
            } else
                sver = 0;

                if (sver < dver)
                        return false;                // Known to be incompatible
                if (sver > dver)
                        return true;                // Known to be compatible

                // Check for and absorb separators
                if (dtok.hasMoreTokens())
                        dtok.nextToken();
                if (stok.hasMoreTokens())
                        stok.nextToken();
                // Compare next component
            }
            // All components numerically equal
        return true;
    }


    /**
     * Prints out message if the verboseFlag is set
     */
    void verboseMessage(String message){
        if (verboseFlag) {
            System.err.println(message);
        }
    }

    void generalMessage(String message){
        System.err.println(message);
    }

    /**
     * Throws a RuntimeException with a message describing the error.
     */
    static void error(String message) throws RuntimeException {
        throw new RuntimeException(message);
    }


    /**
     * Inner class used to represent a loader of resources and classes
     * from a base URL. Somewhat modified version of code in
     * sun.misc.URLClassPath.JarLoader
     */
    private static class JarLoader {
        private final URL base;
        private JarFile jar;
        private URL csu;

        /*
         * Creates a new Loader for the specified URL.
         */
        JarLoader(URL url) {
            String urlName = url + "!/";
            URL tmpBaseURL = null;
            try {
                tmpBaseURL = new URL("jar","",urlName);
                jar = findJarFile(url);
                csu = url;
            } catch (MalformedURLException e) {
                ExtCheck.error("Malformed url "+urlName);
            } catch (IOException e) {
                ExtCheck.error("IO Exception occurred");
            }
            base = tmpBaseURL;

        }

        /*
         * Returns the base URL for this Loader.
         */
        URL getBaseURL() {
            return base;
        }

        JarFile getJarFile() {
            return jar;
        }

        private JarFile findJarFile(URL url) throws IOException {
             // Optimize case where url refers to a local jar file
             if ("file".equals(url.getProtocol())) {
                 String path = url.getFile().replace('/', File.separatorChar);
                 File file = new File(path);
                 if (!file.exists()) {
                     throw new FileNotFoundException(path);
                 }
                 return new JarFile(path);
             }
             URLConnection uc = getBaseURL().openConnection();
             //uc.setRequestProperty(USER_AGENT_JAVA_VERSION, JAVA_VERSION);
             return ((JarURLConnection)uc).getJarFile();
         }


        /*
         * Returns the JAR file local class path, or null if none.
         */
        URL[] getClassPath() throws IOException {
            Manifest man = jar.getManifest();
            if (man != null) {
                Attributes attr = man.getMainAttributes();
                if (attr != null) {
                    String value = attr.getValue(Name.CLASS_PATH);
                    if (value != null) {
                        return parseClassPath(csu, value);
                    }
                }
            }
            return null;
        }

        /*
         * Parses value of the Class-Path manifest attribute and returns
         * an array of URLs relative to the specified base URL.
         */
        private URL[] parseClassPath(URL base, String value)
            throws MalformedURLException
        {
            StringTokenizer st = new StringTokenizer(value);
            URL[] urls = new URL[st.countTokens()];
            int i = 0;
            while (st.hasMoreTokens()) {
                String path = st.nextToken();
                urls[i] = new URL(base, path);
                i++;
            }
            return urls;
        }
    }


}
