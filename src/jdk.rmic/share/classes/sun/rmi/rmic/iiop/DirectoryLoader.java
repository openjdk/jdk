/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package sun.rmi.rmic.iiop;

import java.util.Hashtable;
import java.io.File;
import java.io.FileInputStream;

/**
 * DirectoryLoader is a simple ClassLoader which loads from a specified
 * file system directory.
 * @author Bryan Atsatt
 */

public class DirectoryLoader extends ClassLoader {

    private Hashtable cache;
    private File root;

    /**
     * Constructor.
     */
    public DirectoryLoader (File rootDir) {
        cache = new Hashtable();
        if (rootDir == null || !rootDir.isDirectory()) {
            throw new IllegalArgumentException();
        }
        root = rootDir;
    }

    private DirectoryLoader () {}

    /**
     * Convenience version of loadClass which sets 'resolve' == true.
     */
    public Class loadClass(String className) throws ClassNotFoundException {
        return loadClass(className, true);
    }

    /**
     * This is the required version of loadClass which is called
     * both from loadClass above and from the internal function
     * FindClassFromClass.
     */
    public synchronized Class loadClass(String className, boolean resolve)
        throws ClassNotFoundException {
        Class result;
        byte  classData[];

        // Do we already have it in the cache?

        result = (Class) cache.get(className);

        if (result == null) {

            // Nope, can we get if from the system class loader?

            try {

                result = super.findSystemClass(className);

            } catch (ClassNotFoundException e) {

                // No, so try loading it...

                classData = getClassFileData(className);

                if (classData == null) {
                    throw new ClassNotFoundException();
                }

                // Parse the class file data...

                result = defineClass(classData, 0, classData.length);

                if (result == null) {
                    throw new ClassFormatError();
                }

                // Resolve it...

                if (resolve) resolveClass(result);

                // Add to cache...

                cache.put(className, result);
            }
        }

        return result;
    }

    /**
     * Reurn a byte array containing the contents of the class file.  Returns null
     * if an exception occurs.
     */
    private byte[] getClassFileData (String className) {

        byte result[] = null;
        FileInputStream stream = null;

        // Get the file...

        File classFile = new File(root,className.replace('.',File.separatorChar) + ".class");

        // Now get the bits...

        try {
            stream = new FileInputStream(classFile);
            result = new byte[stream.available()];
            stream.read(result);
        } catch(ThreadDeath death) {
            throw death;
        } catch (Throwable e) {
        }

        finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch(ThreadDeath death) {
                    throw death;
                } catch (Throwable e) {
                }
            }
        }

        return result;
    }
}
