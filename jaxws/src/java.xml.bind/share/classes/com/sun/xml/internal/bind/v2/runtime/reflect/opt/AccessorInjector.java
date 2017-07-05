/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime.reflect.opt;

import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.xml.internal.bind.Util;
import com.sun.xml.internal.bind.v2.bytecode.ClassTailor;

/**
 * @author Kohsuke Kawaguchi
 */
class AccessorInjector {

    private static final Logger logger = Util.getClassLogger();

    protected static final boolean noOptimize =
        Util.getSystemProperty(ClassTailor.class.getName()+".noOptimize")!=null;

    static {
        if(noOptimize)
            logger.info("The optimized code generation is disabled");
    }

    /**
     * Loads the optimized class and returns it.
     *
     * @return null
     *      if it fails for some reason.
     */
    public static Class<?> prepare(
        Class beanClass, String templateClassName, String newClassName, String... replacements ) {

        if(noOptimize)
            return null;

        try {
            ClassLoader cl = SecureLoader.getClassClassLoader(beanClass);
            if(cl==null)    return null;    // how do I inject classes to this "null" class loader? for now, back off.

            Class c = Injector.find(cl,newClassName);
            if (c==null) {
                byte[] image = tailor(templateClassName,newClassName,replacements);
                if (image==null) {
                    return null;
                }
                c = Injector.inject(cl,newClassName,image);
                if (c == null) {
                    Injector.find(cl, newClassName);
                }
            }
            return c;
        } catch(SecurityException e) {
            // we don't have enough permission to do this
            logger.log(Level.INFO,"Unable to create an optimized TransducedAccessor ",e);
            return null;
        }
    }


    /**
     * Customizes a class file by replacing constant pools.
     *
     * @param templateClassName
     *      The resource that contains the template class file.
     * @param replacements
     *      A list of pair of strings that specify the substitution
     *      {@code String[]{search_0, replace_0, search_1, replace_1, ..., search_n, replace_n }
     *
     *      The search strings found in the constant pool will be replaced by the corresponding
     *      replacement string.
     */
    private static byte[] tailor( String templateClassName, String newClassName, String... replacements ) {
        InputStream resource;
        if(CLASS_LOADER!=null)
            resource = CLASS_LOADER.getResourceAsStream(templateClassName+".class");
        else
            resource = ClassLoader.getSystemResourceAsStream(templateClassName+".class");
        if(resource==null)
            return null;

        return ClassTailor.tailor(resource,templateClassName,newClassName,replacements);
    }

    private static final ClassLoader CLASS_LOADER = SecureLoader.getClassClassLoader(AccessorInjector.class);

}
