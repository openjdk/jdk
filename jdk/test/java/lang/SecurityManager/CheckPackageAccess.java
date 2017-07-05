/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 *  @test
 *  @bug 6741606 7146431 8000450
 *  @summary Make sure all restricted packages listed in the package.access
 *           property in the java.security file are blocked
 *  @run main/othervm CheckPackageAccess
 */

import java.security.Security;
import java.util.Collections;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/*
 * The main benefit of this test is to catch merge errors or other types
 * of issues where one or more of the packages are accidentally
 * removed. This is why the packages that are known to be restricted have to
 * be explicitly listed below.
 */
public class CheckPackageAccess {

    /*
     * This array should be updated whenever new packages are added to the
     * package.access property in the java.security file
     */
    private static final String[] packages = {
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
        "com.sun.org.apache.xml.internal.security.",
        "com.sun.org.apache.xml.internal.serializer.utils.",
        "com.sun.org.apache.xml.internal.utils.",
        "com.sun.org.glassfish.",
        "com.oracle.xmlns.internal.",
        "com.oracle.webservices.internal.",
        "oracle.jrockit.jfr.",
        "org.jcp.xml.dsig.internal.",
        "jdk.internal.",
        "jdk.nashorn.internal.",
        "jdk.nashorn.tools."
    };

    public static void main(String[] args) throws Exception {
        List<String> pkgs = new ArrayList<>(Arrays.asList(packages));
        String osName = System.getProperty("os.name");
        if (osName.contains("OS X")) {
            pkgs.add("apple.");  // add apple package for OS X
        } else if (osName.startsWith("Windows")) {
            pkgs.add("com.sun.java.accessibility.");
        }

        List<String> jspkgs =
            getPackages(Security.getProperty("package.access"));

        // Sort to ensure lists are comparable
        Collections.sort(pkgs);
        Collections.sort(jspkgs);

        if (!pkgs.equals(jspkgs)) {
            for (String p : pkgs)
                if (!jspkgs.contains(p))
                    System.out.println("In golden set, but not in j.s file: " + p);
            for (String p : jspkgs)
                if (!pkgs.contains(p))
                    System.out.println("In j.s file, but not in golden set: " + p);


            throw new RuntimeException("restricted packages are not " +
                                       "consistent with java.security file");
        }
        System.setSecurityManager(new SecurityManager());
        SecurityManager sm = System.getSecurityManager();
        for (String pkg : packages) {
            String subpkg = pkg + "foo";
            try {
                sm.checkPackageAccess(pkg);
                throw new RuntimeException("Able to access " + pkg +
                                           " package");
            } catch (SecurityException se) { }
            try {
                sm.checkPackageAccess(subpkg);
                throw new RuntimeException("Able to access " + subpkg +
                                           " package");
            } catch (SecurityException se) { }
            try {
                sm.checkPackageDefinition(pkg);
                throw new RuntimeException("Able to define class in " + pkg +
                                           " package");
            } catch (SecurityException se) { }
            try {
                sm.checkPackageDefinition(subpkg);
                throw new RuntimeException("Able to define class in " + subpkg +
                                           " package");
            } catch (SecurityException se) { }
        }
        System.out.println("Test passed");
    }

    private static List<String> getPackages(String p) {
        List<String> packages = new ArrayList<>();
        if (p != null && !p.equals("")) {
            StringTokenizer tok = new StringTokenizer(p, ",");
            while (tok.hasMoreElements()) {
                String s = tok.nextToken().trim();
                packages.add(s);
            }
        }
        return packages;
    }
}
