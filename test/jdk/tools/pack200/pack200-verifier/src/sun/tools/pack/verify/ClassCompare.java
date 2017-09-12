/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
package sun.tools.pack.verify;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import xmlkit.*;

public class ClassCompare {

    /*
     * @author ksrini
     */
    private static XMLKit.Element getXMLelement(InputStream is,
            boolean ignoreUnkAttrs,
            List<String> ignoreElements) throws IOException {

        ClassReader cr = new ClassReader();
        cr.keepOrder = false;
        XMLKit.Element e = cr.readFrom(is);

        if (ignoreElements != null) {
            XMLKit.Filter filter = XMLKit.elementFilter(ignoreElements);
            e.removeAllInTree(filter);
        }

        if (ignoreUnkAttrs == true) {
            // This removes any unknown attributes
            e.removeAllInTree(XMLKit.elementFilter("Attribute"));
        }
        return e;
    }

    private static String getXMLPrettyString(XMLKit.Element e) throws IOException {
        StringWriter out = new StringWriter();
        e.writePrettyTo(out);
        return out.toString();
    }

    private static boolean compareClass0(JarFile jf1, JarFile jf2,
            JarEntry je, boolean ignoreUnkAttrs,
            List<String> ignoreElements)
            throws IOException {

        InputStream is1 = jf1.getInputStream(je);
        InputStream is2 = jf2.getInputStream(je);

        // First we try to compare the bits if they are the same
        boolean bCompare = JarFileCompare.compareStreams(is1, is2);

        // If they are the same there is nothing more to do.
        if (bCompare) {
            Globals.println("+++" + je.getName() + "+++\t"
                    + "b/b:PASS");
            return bCompare;
        }
        is1.close();
        is2.close();

        is1 = jf1.getInputStream(je);
        is2 = jf2.getInputStream(je);


        XMLKit.Element e1 = getXMLelement(is1, ignoreUnkAttrs, ignoreElements);
        XMLKit.Element e2 = getXMLelement(is2, ignoreUnkAttrs, ignoreElements);

        Globals.print("+++" + je.getName() + "+++\t"
                + e1.size() + "/" + e1.size() + ":");

        boolean result = true;

        if (e1.equals(e2)) {
            Globals.println("PASS");
        } else {
            Globals.println("FAIL");
            Globals.log("Strings differs");
            Globals.log(getXMLPrettyString(e1));
            Globals.log("----------");
            Globals.log(getXMLPrettyString(e2));
            result = false;
        }
        return result;
    }

    /*
     * Given two Class Paths could be jars the first being a reference
     * will execute a series of comparisons on the classname  specified
     * The className could be null in which case it will iterate through
     * all the classes, otherwise it will compare one class and exit.
     */
    public static boolean compareClass(String jar1, String jar2,
            String className, boolean ignoreUnkAttrs,
            List<String> ignoreElements)
            throws IOException {

        Globals.println("Unknown attributes ignored:" + ignoreUnkAttrs);
        if (ignoreElements != null) {
            Globals.println(ignoreElements.toString());
        }

        JarFile jf1 = new JarFile(jar1);
        JarFile jf2 = new JarFile(jar2);

        boolean result = true;

        if (className == null) {
            for (JarEntry je1 : Collections.list((Enumeration<JarEntry>) jf1.entries())) {
                if (je1.getName().endsWith(".class")) {
                    JarEntry je2 = jf2.getJarEntry(je1.getName());
                    boolean pf = compareClass0(jf1, jf2, je1, ignoreUnkAttrs, ignoreElements);
                    if (result == true) {
                        result = pf;
                    }
                }
            }
        } else {
            JarEntry je1 = jf1.getJarEntry(className);
            result = compareClass0(jf1, jf2, je1, ignoreUnkAttrs, ignoreElements);
        }
        if (result == false) {
            throw new RuntimeException("Class structural comparison failure");
        }
        return result;
    }

    public static boolean compareClass(String jar1, String jar2,
            String className) throws IOException {

        Stack<String> s = new Stack();
        if (Globals.ignoreDebugAttributes()) {
            s = new Stack();
            s.push("LocalVariable");
            s.push("LocalVariableType");
            s.push("LineNumber");
            s.push("SourceFile");
        }
        return compareClass(jar1, jar2, className, Globals.ignoreUnknownAttributes(), s);
    }
}
