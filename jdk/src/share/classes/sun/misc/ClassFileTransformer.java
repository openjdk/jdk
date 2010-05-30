/*
 * Copyright (c) 2001, 2008, Oracle and/or its affiliates. All rights reserved.
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
    private static ArrayList<ClassFileTransformer> transformerList
        = new ArrayList<ClassFileTransformer>();
    private static ClassFileTransformer[] transformers
        = new ClassFileTransformer[0];

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
            transformers = transformerList.toArray(new ClassFileTransformer[0]);
        }
    }

    /**
     * Get the array of ClassFileTransformer object.
     *
     * @return ClassFileTransformer object array
     */
    public static ClassFileTransformer[] getTransformers()
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
