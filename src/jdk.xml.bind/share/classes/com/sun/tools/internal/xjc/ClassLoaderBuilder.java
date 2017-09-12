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

package com.sun.tools.internal.xjc;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.JAXBContext;

import com.sun.istack.internal.tools.MaskingClassLoader;
import com.sun.istack.internal.tools.ParallelWorldClassLoader;

/**
 * Creates a class loader configured to run XJC 1.0/2.0 safely without
 * interference with JAXB 2.0 API in Mustang.
 *
 * @author Kohsuke Kawaguchi
 */
class ClassLoaderBuilder {

    /**
     * Creates a new class loader that eventually delegates to the given {@link ClassLoader}
     * such that XJC can be loaded by using this classloader.
     *
     * @param v
     *      Either "1.0" or "2.0", indicating the version of the -source value.
     */
    protected static ClassLoader createProtectiveClassLoader(ClassLoader cl, String v) throws ClassNotFoundException, MalformedURLException {
        if(noHack)  return cl;  // provide an escape hatch

        boolean mustang = false;

        if (SecureLoader.getClassClassLoader(JAXBContext.class) == null) {
            // JAXB API is loaded from the bootstrap. We need to override one with ours
            mustang = true;

            List<String> mask = new ArrayList<String>(Arrays.asList(maskedPackages));
            mask.add("javax.xml.bind.");

            cl = new MaskingClassLoader(cl,mask);

            URL apiUrl = cl.getResource("javax/xml/bind/JAXBPermission.class");
            if(apiUrl==null)
                throw new ClassNotFoundException("There's no JAXB 2.2 API in the classpath");

            cl = new URLClassLoader(new URL[]{ParallelWorldClassLoader.toJarUrl(apiUrl)},cl);
        }

        //Leave XJC2 in the publicly visible place
        // and then isolate XJC1 in a child class loader,
        // then use a MaskingClassLoader
        // so that the XJC2 classes in the parent class loader
        //  won't interfere with loading XJC1 classes in a child class loader

        if ("1.0".equals(v)) {
            if(!mustang)
                // if we haven't used Masking ClassLoader, do so now.
                cl = new MaskingClassLoader(cl,toolPackages);
            cl = new ParallelWorldClassLoader(cl,"1.0/");
        } else {
            if(mustang)
                // the whole RI needs to be loaded in a separate class loader
                cl = new ParallelWorldClassLoader(cl,"");
        }

        return cl;
    }


    /**
     * The list of package prefixes we want the
     * {@link MaskingClassLoader} to prevent the parent
     * classLoader from loading
     */
    private static String[] maskedPackages = new String[]{
        // toolPackages + alpha
        "com.sun.tools.",
        "com.sun.codemodel.internal.",
        "com.sun.relaxng.",
        "com.sun.xml.internal.xsom.",
        "com.sun.xml.internal.bind.",
    };

    private static String[] toolPackages = new String[]{
        "com.sun.tools.",
        "com.sun.codemodel.internal.",
        "com.sun.relaxng.",
        "com.sun.xml.internal.xsom."
    };

    /**
     * Escape hatch in case this class loader hack breaks.
     */
    public static final boolean noHack = Boolean.getBoolean(XJCFacade.class.getName()+".nohack");
}
