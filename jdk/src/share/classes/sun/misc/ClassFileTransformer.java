/*
 * Copyright 2001 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.misc;

import java.util.ArrayList;

/**
 * This is an abstract base class which is called by java.lang.ClassLoader
 * when ClassFormatError is thrown inside defineClass().
 *
 * The purpose of this class is to allow applications (e.g. Java Plug-in)
 * to have a chance to transform the byte code from one form to another
 * if necessary.
 *
 * One application of this class is used by Java Plug-in to transform
 * malformed JDK 1.1 class file into a well-formed Java 2 class file
 * on-the-fly, so JDK 1.1 applets with malformed class file in the
 * Internet may run in Java 2 after transformation.
 *
 * @author      Stanley Man-Kit Ho
 */

public abstract class ClassFileTransformer
{
    // Singleton of ClassFileTransformer
    //
    private static ArrayList transformerList = new ArrayList();
    private static Object[] transformers = new Object[0];

    /**
     * Add the class file transformer object.
     *
     * @param t Class file transformer instance
     */
    public static void add(ClassFileTransformer t)
    {
        synchronized(transformerList)
        {
            transformerList.add(t);
            transformers = transformerList.toArray();
        }
    }

    /**
     * Get the array of ClassFileTransformer object.
     *
     * @return ClassFileTransformer object array
     */
    public static Object[] getTransformers()
    {
        // transformers is not intended to be changed frequently,
        // so it is okay to not put synchronized block here
        // to speed up performance.
        //
        return transformers;
    }


    /**
     * Transform a byte array from one to the other.
     *
     * @param b Byte array
     * @param off Offset
     * @param len Length of byte array
     * @return Transformed byte array
     */
    public abstract byte[] transform(byte[] b, int off, int len)
                           throws ClassFormatError;
}
