/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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
package sun.rmi.rmic.iiop;

import java.io.*;
import sun.tools.java.ClassPath ;
import sun.tools.java.ClassFile ;

/**
 * A ClassLoader that will ultimately use a given sun.tools.java.ClassPath to
 * find the desired file.  This works for any JAR files specified in the given
 * ClassPath as well -- reusing all of that wonderful sun.tools.java code.
 *
 *@author Everett Anderson
 */
public class ClassPathLoader extends ClassLoader
{
    private ClassPath classPath;

    public ClassPathLoader(ClassPath classPath) {
        this.classPath = classPath;
    }

    // Called by the super class
    protected Class findClass(String name) throws ClassNotFoundException
    {
        byte[] b = loadClassData(name);
        return defineClass(name, b, 0, b.length);
    }

    /**
     * Load the class with the given fully qualified name from the ClassPath.
     */
    private byte[] loadClassData(String className)
        throws ClassNotFoundException
    {
        // Build the file name and subdirectory from the
        // class name
        String filename = className.replace('.', File.separatorChar)
                          + ".class";

        // Have ClassPath find the file for us, and wrap it in a
        // ClassFile.  Note:  This is where it looks inside jar files that
        // are specified in the path.
        ClassFile classFile = classPath.getFile(filename);

        if (classFile != null) {

            // Provide the most specific reason for failure in addition
            // to ClassNotFound
            Exception reportedError = null;
            byte data[] = null;

            try {
                // ClassFile is beautiful because it shields us from
                // knowing if it's a separate file or an entry in a
                // jar file.
                DataInputStream input
                    = new DataInputStream(classFile.getInputStream());

                // Can't rely on input available() since it will be
                // something unusual if it's a jar file!  May need
                // to worry about a possible problem if someone
                // makes a jar file entry with a size greater than
                // max int.
                data = new byte[(int)classFile.length()];

                try {
                    input.readFully(data);
                } catch (IOException ex) {
                    // Something actually went wrong reading the file.  This
                    // is a real error so save it to report it.
                    data = null;
                    reportedError = ex;
                } finally {
                    // Just don't care if there's an exception on close!
                    // I hate that close can throw an IOException!
                    try { input.close(); } catch (IOException ex) {}
                }
            } catch (IOException ex) {
                // Couldn't get the input stream for the file.  This is
                // probably also a real error.
                reportedError = ex;
            }

            if (data == null)
                throw new ClassNotFoundException(className, reportedError);

            return data;
        }

        // Couldn't find the file in the class path.
        throw new ClassNotFoundException(className);
    }
}
