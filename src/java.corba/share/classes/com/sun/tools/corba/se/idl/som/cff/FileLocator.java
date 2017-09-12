/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Licensed Materials - Property of IBM
 * 5639-D57 (C) COPYRIGHT International Business Machines Corp. 1997,1998
 * RMI-IIOP v1.0
 *
 */

package com.sun.tools.corba.se.idl.som.cff;

import java.lang.Exception;
import java.lang.String;
import java.lang.System;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.zip.*;

/**
 * FileLocator is an abstract class (one that cannot be instantiated) that
 * provides class methods for finding files in the directories or zip
 * archives that make up the CLASSPATH.
 *
 * @author      Larry K. Raper
 */
public abstract class FileLocator extends Object {

    /* Class variables */


    static final Properties pp = System.getProperties ();
    static final String classPath = pp.getProperty ("java.class.path", ".");
    static final String pathSeparator = pp.getProperty ("path.separator", ";");

    /* Instance variables */

    /* [None, no instances of this class are ever instantiated.] */

    /**
     * locateClassFile returns a DataInputStream with mark/reset
     * capability that can be used to read the requested class file.  The
     * CLASSPATH is used to locate the class.
     *
     * @param classFileName The name of the class to locate.  The class name
     * should be given in fully-qualified form, for example:
     * <pre>
     *     java.lang.Object
     *     java.io.DataInputStream
     * </pre>
     *
     * @exception java.io.FileNotFoundException The requested class file
     * could not be found.
     * @exception java.io.IOException The requested class file
     * could not be opened.
     */
    public static DataInputStream locateClassFile (String classFileName)
        throws FileNotFoundException, IOException {

        boolean notFound = true;
        StringTokenizer st;
        String path = "";
        String pathNameForm;
        File cf = null;
        NamedDataInputStream result;

        st = new StringTokenizer (classPath, pathSeparator, false);
        pathNameForm = classFileName.replace ('.', File.separatorChar) +
            ".class";

        while (st.hasMoreTokens () && notFound) {

            try {path = st.nextToken ();}
                catch (NoSuchElementException nse) {break;}
            int pLen = path.length ();
            String pathLast4 = pLen > 3 ? path.substring (pLen - 4) : "";
            if (pathLast4.equalsIgnoreCase (".zip") ||
                pathLast4.equalsIgnoreCase (".jar")) {

                try {

                    result = locateInZipFile (path, classFileName, true, true);
                    if (result == null)
                        continue;
                    return (DataInputStream) result;

                } catch (ZipException zfe) {
                    continue;
                } catch (IOException ioe) {
                    continue;
                }

            } else {
                try {cf = new File (path + File.separator + pathNameForm);
                } catch (NullPointerException npe) { continue; }
                if ((cf != null) && cf.exists ())
                    notFound = false;
            }
        }

        if (notFound) {

            /* Make one last attempt to find the file in the current
             * directory
             */

            int lastdot = classFileName.lastIndexOf ('.');
            String simpleName =
                (lastdot >= 0) ? classFileName.substring (lastdot+1) :
                classFileName;

            result = new NamedDataInputStream (new BufferedInputStream (
               new FileInputStream (simpleName + ".class")),
                   simpleName + ".class", false);
            return (DataInputStream) result;
        }

        result = new NamedDataInputStream (new BufferedInputStream (
            new FileInputStream (cf)), path + File.separator + pathNameForm,
                false);
        return (DataInputStream) result;

    }

    /**
     * locateLocaleSpecificFileInClassPath returns a DataInputStream that
     * can be used to read the requested file, but the name of the file is
     * determined using information from the current locale and the supplied
     * file name (which is treated as a "base" name, and is supplemented with
     * country and language related suffixes, obtained from the current
     * locale).  The CLASSPATH is used to locate the file.
     *
     * @param fileName The name of the file to locate.  The file name
     * may be qualified with a partial path name, using '/' as the separator
     * character or using separator characters appropriate for the host file
     * system, in which case each directory or zip file in the CLASSPATH will
     * be used as a base for finding the fully-qualified file.
     * Here is an example of how the supplied fileName is used as a base
     * for locating a locale-specific file:
     *
     * <pre>
     *     Supplied fileName: a/b/c/x.y,  current locale: US English
     *
     *                     Look first for: a/b/c/x_en_US.y
     *     (if that fails) Look next for:  a/b/c/x_en.y
     *     (if that fails) Look last for:  a/b/c/x.y
     *
     *     All elements of the class path are searched for each name,
     *     before the next possible name is tried.
     * </pre>
     *
     * @exception java.io.FileNotFoundException The requested class file
     * could not be found.
     * @exception java.io.IOException The requested class file
     * could not be opened.
     */
    public static DataInputStream locateLocaleSpecificFileInClassPath (
        String fileName) throws FileNotFoundException, IOException {

        String localeSuffix = "_" + Locale.getDefault ().toString ();
        int lastSlash = fileName.lastIndexOf ('/');
        int lastDot   = fileName.lastIndexOf ('.');
        String fnFront, fnEnd;
        DataInputStream result = null;
        boolean lastAttempt = false;

        if ((lastDot > 0) && (lastDot > lastSlash)) {
            fnFront = fileName.substring (0, lastDot);
            fnEnd   = fileName.substring (lastDot);
        } else {
            fnFront = fileName;
            fnEnd   = "";
        }

        while (true) {
            if (lastAttempt)
                result = locateFileInClassPath (fileName);
            else try {
                result = locateFileInClassPath (fnFront + localeSuffix + fnEnd);
            } catch (Exception e) { /* ignore */ }
            if ((result != null) || lastAttempt)
                break;
            int lastUnderbar = localeSuffix.lastIndexOf ('_');
            if (lastUnderbar > 0)
                localeSuffix = localeSuffix.substring (0, lastUnderbar);
            else
                lastAttempt = true;
        }
        return result;

    }

    /**
     * locateFileInClassPath returns a DataInputStream that can be used
     * to read the requested file.  The resource is located in the java.corba
     * module or if not found, then the CLASSPATH is searched.
     *
     * @param fileName The name of the file to locate.  The file name
     * may be qualified with a partial path name, using '/' as the separator
     * character or using separator characters appropriate for the host file
     * system, in which case each directory or zip file in the CLASSPATH will
     * be used as a base for finding the fully-qualified file.
     *
     * @exception java.io.FileNotFoundException The requested class file
     * could not be found.
     * @exception java.io.IOException The requested class file
     * could not be opened.
     */
    public static DataInputStream locateFileInClassPath (String fileName)
        throws FileNotFoundException, IOException {

        // The resource should be in the java.corba module
        InputStream in = FileLocator.class.getResourceAsStream("/" + fileName);
        if (in != null) {
            return new DataInputStream(in);
        }

        boolean notFound = true;
        StringTokenizer st;
        String path = "";
        File cf = null;
        NamedDataInputStream result;

        String zipEntryName = File.separatorChar == '/' ? fileName :
            fileName.replace (File.separatorChar, '/');

        String localFileName = File.separatorChar == '/' ? fileName :
            fileName.replace ('/', File.separatorChar);

        st = new StringTokenizer (classPath, pathSeparator, false);

        while (st.hasMoreTokens () && notFound) {

            try {path = st.nextToken ();}
                catch (NoSuchElementException nse) {break;}
            int pLen = path.length ();
            String pathLast4 = pLen > 3 ? path.substring (pLen - 4) : "";
            if (pathLast4.equalsIgnoreCase (".zip") ||
                pathLast4.equalsIgnoreCase (".jar")) {

                try {

                    result = locateInZipFile (path, zipEntryName, false, false);
                    if (result == null)
                        continue;
                    return (DataInputStream) result;

                } catch (ZipException zfe) {
                    continue;
                } catch (IOException ioe) {
                    continue;
                }

            } else {
                try {cf = new File (path + File.separator + localFileName);
                } catch (NullPointerException npe) { continue; }
                if ((cf != null) && cf.exists ())
                    notFound = false;
            }
        }

        if (notFound) {

            /* Make one last attempt to find the file in the current
             * directory
             */

            int lastpart = localFileName.lastIndexOf (File.separator);
            String simpleName =
                (lastpart >= 0) ? localFileName.substring (lastpart+1) :
                localFileName;

            result = new NamedDataInputStream (new BufferedInputStream (
               new FileInputStream (simpleName)), simpleName, false);
            return (DataInputStream) result;
        }

        result = new NamedDataInputStream (new BufferedInputStream (
            new FileInputStream (cf)), path + File.separator + localFileName,
                false);
        return (DataInputStream) result;

    }

    /**
     * Returns the fully qualified file name associated with the passed
     * DataInputStream <i>if the DataInputStream was created using one
     * of the static locate methods supplied with this class</i>, otherwise
     * returns a zero length string.
     */
    public static String getFileNameFromStream (DataInputStream ds) {

        if (ds instanceof NamedDataInputStream)
            return ((NamedDataInputStream) ds).fullyQualifiedFileName;
        return "";

    }

    /**
     * Returns an indication of whether the passed DataInputStream is
     * associated with a member of a zip file <i>if the DataInputStream was
     * created using one of the static locate methods supplied with this
     * class</i>, otherwise returns false.
     */
    public static boolean isZipFileAssociatedWithStream (DataInputStream ds) {

        if (ds instanceof NamedDataInputStream)
            return ((NamedDataInputStream) ds).inZipFile;
        return false;

    }

    private static NamedDataInputStream locateInZipFile (String zipFileName,
        String fileName, boolean wantClass, boolean buffered)
        throws ZipException, IOException {

        ZipFile zf;
        ZipEntry ze;
        zf = new ZipFile (zipFileName);

        if (zf == null)
            return null;
        String zeName = wantClass ?
            fileName.replace ('.', '/') + ".class" :
            fileName;

        //  This code works with JDK 1.0 level SUN zip classes
        //

        //  ze = zf.get (zeName);
        //  if (ze == null)
        //      return null;
        //  return new NamedDataInputStream (
        //      new BufferedInputStream (new ZipInputStream (ze)),
        //          zipFileName + '(' +zeName + ')', true);

        //  This code works with JDK 1.0.2 and JDK 1.1 level SUN zip classes
        //

            ze = zf.getEntry (zeName);
            if (ze == null) {
                zf.close(); // D55355, D56419
                zf = null;
                return null;
            }
            InputStream istream = zf.getInputStream(ze);
            if (buffered)
                istream = new BufferedInputStream(istream);
            return new NamedDataInputStream (istream,
                    zipFileName + '(' + zeName + ')', true);

    }

}

/**
 * This class is used to associate a filename with a DataInputStream
 * The host platform's file naming conventions are assumed for the filename.
 *
 * @author      Larry K. Raper
 *
 */
/* default access */ class NamedDataInputStream extends DataInputStream {

    /* Instance variables */

    /**
     * The name of the file associated with the DataInputStream.
     */
    public String fullyQualifiedFileName;

    /**
     * Indicates whether or not the file is contained in a .zip file.
     */
    public boolean inZipFile;

    /* Constructors */

    protected NamedDataInputStream (InputStream in, String fullyQualifiedName,
        boolean inZipFile) {

        super (in);
        this.fullyQualifiedFileName = fullyQualifiedName;
        this.inZipFile = inZipFile;

    }

}
