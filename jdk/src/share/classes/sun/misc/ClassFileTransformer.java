/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

/**
 * This is an abstract base class originally intended to be called by
 * {@code java.lang.ClassLoader} when {@code ClassFormatError} is
 * thrown inside {@code defineClass()}. It is no longer hooked into
 * {@code ClassLoader} and will be removed in a future release.
 *
 * @author      Stanley Man-Kit Ho
 */

@Deprecated
public abstract class ClassFileTransformer {

    private static final List<ClassFileTransformer> transformers
        = new ArrayList<ClassFileTransformer>();

    /**
     * Add the class file transformer object.
     *
     * @param t Class file transformer instance
     */
    public static void add(ClassFileTransformer t) {
        synchronized (transformers) {
            transformers.add(t);
        }
    }

    /**
     * Get the array of ClassFileTransformer object.
     *
     * @return ClassFileTransformer object array
     */
    public static ClassFileTransformer[] getTransformers() {
        synchronized (transformers) {
            ClassFileTransformer[] result = new ClassFileTransformer[transformers.size()];
            return transformers.toArray(result);
        }
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
