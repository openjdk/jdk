/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
            ClassLoader cl = beanClass.getClassLoader();
            if(cl==null)    return null;    // how do I inject classes to this "null" class loader? for now, back off.

            Class c = Injector.find(cl,newClassName);
            if(c==null) {
                byte[] image = tailor(templateClassName,newClassName,replacements);
//                try {
//                    new FileOutputStream("debug.class").write(image);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                if(image==null)
                    return null;
                c = Injector.inject(cl,newClassName,image);
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
     *      The resouce that contains the template class file.
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

    private static final ClassLoader CLASS_LOADER = AccessorInjector.class.getClassLoader();
}
