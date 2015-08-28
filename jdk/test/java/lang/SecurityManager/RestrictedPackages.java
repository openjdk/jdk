/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

/**
 * A collection of utility methods and constants for testing the package
 * access and package definition security checks.
 */
final class RestrictedPackages {

    /*
     * The expected list of restricted packages.
     *
     * This array should be updated whenever new packages are added to the
     * package.access property in the java.security file
     * NOTE: it should be in the same order as the java.security file
     */
    static final String[] EXPECTED = {
        "sun.",
        "com.sun.xml.internal.",
        "com.sun.imageio.",
        "com.sun.istack.internal.",
        "com.sun.jmx.",
        "com.sun.media.sound.",
        "com.sun.naming.internal.",
        "com.sun.proxy.",
        "com.sun.corba.se.",
        "com.sun.org.apache.bcel.internal.",
        "com.sun.org.apache.regexp.internal.",
        "com.sun.org.apache.xerces.internal.",
        "com.sun.org.apache.xpath.internal.",
        "com.sun.org.apache.xalan.internal.extensions.",
        "com.sun.org.apache.xalan.internal.lib.",
        "com.sun.org.apache.xalan.internal.res.",
        "com.sun.org.apache.xalan.internal.templates.",
        "com.sun.org.apache.xalan.internal.utils.",
        "com.sun.org.apache.xalan.internal.xslt.",
        "com.sun.org.apache.xalan.internal.xsltc.cmdline.",
        "com.sun.org.apache.xalan.internal.xsltc.compiler.",
        "com.sun.org.apache.xalan.internal.xsltc.trax.",
        "com.sun.org.apache.xalan.internal.xsltc.util.",
        "com.sun.org.apache.xml.internal.res.",
        "com.sun.org.apache.xml.internal.security.",
        "com.sun.org.apache.xml.internal.serializer.utils.",
        "com.sun.org.apache.xml.internal.utils.",
        "com.sun.org.glassfish.",
        "com.sun.tools.script.",
        "com.oracle.xmlns.internal.",
        "com.oracle.webservices.internal.",
        "org.jcp.xml.dsig.internal.",
        "jdk.internal.",
        "jdk.nashorn.internal.",
        "jdk.nashorn.tools.",
        "jdk.tools.jimage.",
        "com.sun.activation.registries.",
        "com.sun.java.accessibility.util.internal."
    };

    /*
     * A non-exhaustive list of restricted packages.
     *
     * Contrary to what is in the EXPECTED list, this list does not need
     * to be exhaustive.
     */
    static final String[] EXPECTED_NONEXHAUSTIVE = {
        "sun.",
        "com.sun.xml.internal.",
        "com.sun.imageio.",
        "com.sun.istack.internal.",
        "com.sun.jmx.",
        "com.sun.proxy.",
        "com.sun.org.apache.bcel.internal.",
        "com.sun.org.apache.regexp.internal.",
        "com.sun.org.apache.xerces.internal.",
        "com.sun.org.apache.xpath.internal.",
        "com.sun.org.apache.xalan.internal.extensions.",
        "com.sun.org.apache.xalan.internal.lib.",
        "com.sun.org.apache.xalan.internal.res.",
        "com.sun.org.apache.xalan.internal.templates.",
        "com.sun.org.apache.xalan.internal.utils.",
        "com.sun.org.apache.xalan.internal.xslt.",
        "com.sun.org.apache.xalan.internal.xsltc.cmdline.",
        "com.sun.org.apache.xalan.internal.xsltc.compiler.",
        "com.sun.org.apache.xalan.internal.xsltc.trax.",
        "com.sun.org.apache.xalan.internal.xsltc.util.",
        "com.sun.org.apache.xml.internal.res.",
        "com.sun.org.apache.xml.internal.serializer.utils.",
        "com.sun.org.apache.xml.internal.utils.",
        "com.sun.org.apache.xml.internal.security.",
        "com.sun.org.glassfish.",
        "org.jcp.xml.dsig.internal."
    };

    private static final String OS_NAME = System.getProperty("os.name");

    /**
     * Returns a list of expected restricted packages, including any
     * OS specific packages. The returned list is mutable.
     */
    static List<String> expected() {
        List<String> pkgs = new ArrayList<>(Arrays.asList(EXPECTED));
        if (OS_NAME.contains("OS X")) {
            pkgs.add("apple.");  // add apple package for OS X
        }
        if (OS_NAME.contains("Win")) {
            pkgs.add("com.sun.java.accessibility.internal.");  // add Win only package
        }
        return pkgs;
    }

    /**
     * Returns a list of actual restricted packages. The returned list
     * is mutable.
     */
    static List<String> actual() {
        String prop = Security.getProperty("package.access");
        List<String> packages = new ArrayList<>();
        if (prop != null && !prop.equals("")) {
            StringTokenizer tok = new StringTokenizer(prop, ",");
            while (tok.hasMoreElements()) {
                String s = tok.nextToken().trim();
                packages.add(s);
            }
        }
        return packages;
    }

    private RestrictedPackages() { }
}
