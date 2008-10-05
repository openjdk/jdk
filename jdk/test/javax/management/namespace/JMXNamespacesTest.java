/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
/*
 * @test JMXNamespacesTest.java
 * @summary Test the static method that rewrite ObjectNames in JMXNamespacesTest
 * @author Daniel Fuchs
 * @bug 5072476
 * @run clean JMXNamespacesTest
 * @compile -XDignore.symbol.file=true JMXNamespacesTest.java
 * @run main JMXNamespacesTest
 */

import com.sun.jmx.namespace.ObjectNameRouter;
import java.io.Serializable;
import java.util.Arrays;
import java.util.logging.Logger;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.namespace.JMXNamespaces;

/**
 * Class JMXNamespacesTest
 * @author Sun Microsystems, 2005 - All rights reserved.
 */
public class JMXNamespacesTest {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(JMXNamespacesTest.class.getName());

    /** Creates a new instance of JMXNamespacesTest */
    public JMXNamespacesTest() {
    }

    public static class CustomObject implements Serializable {
        ObjectName toto;
        String titi;
        CustomObject(String toto, String titi) {
            try {
                this.toto = new ObjectName(toto);
            } catch (MalformedObjectNameException m) {
                throw new IllegalArgumentException(m);
            }
            this.titi = titi;
        }
        private Object[] data() {
            return new Object[] {toto, titi};
        }
        @Override
        public boolean equals(Object other) {
            if (! (other instanceof CustomObject)) return false;
            return Arrays.deepEquals(data(),((CustomObject)other).data());
        }
        @Override
        public int hashCode() {
            return Arrays.deepHashCode(data());
        }
    }

    public static CustomObject obj(String toto, String titi) {
        return new CustomObject(toto,titi);
    }

    private static String failure;

    public static void testDeepRewrite() throws Exception {
        failure = null;
        String s1 = "x//y//d:k=v";
        String s2 = "v//w//x//y//d:k=v";
        String p1 = "v//w";
        String p3 = "a//b";

        System.out.println("inserting "+p1);
        final CustomObject foo1 =
                JMXNamespaces.deepReplaceHeadNamespace(obj(s1,s1),"",p1);
        assertEquals(foo1.toto.toString(),p1+"//"+s1);
        assertEquals(foo1.titi,s1);

       System.out.println("removing "+p1);
       final CustomObject foo2 =
                JMXNamespaces.deepReplaceHeadNamespace(obj(s2,s2),p1,"");
        assertEquals(foo2.toto.toString(),s1);
        assertEquals(foo2.titi,s2);

        System.out.println("removing "+p1);
        final CustomObject foo3 =
                JMXNamespaces.deepReplaceHeadNamespace(obj(p1+"//"+s2,s2),p1,"");
        assertEquals(foo3.toto.toString(),s2);
        assertEquals(foo3.titi,s2);

        System.out.println("replacing "+p1+" with "+p3);
        final CustomObject foo4 =
                JMXNamespaces.deepReplaceHeadNamespace(obj(s2,s2),p1,p3);
        assertEquals(foo4.toto.toString(),p3+"//"+s1);
        assertEquals(foo4.titi,s2);

        System.out.println("replacing "+p1+" with "+p1);
        final CustomObject foo5 =
                JMXNamespaces.deepReplaceHeadNamespace(obj(s2,s2),p1,p1);
        assertEquals(foo5.toto.toString(),s2);
        assertEquals(foo5.titi,s2);

        System.out.println("removing x//y in "+s2);
        try {
            final CustomObject foo7 =
                JMXNamespaces.deepReplaceHeadNamespace(obj(s2,s2),"x//y","");
            failed("Remove x//y in "+s2+" should have failed!");
        } catch (IllegalArgumentException x) {
            System.out.println("Received expected exception: "+x);
        }

        System.out.println("replacing x//y with "+p3+" in "+s2);
        try {
            final CustomObject foo7 =
                JMXNamespaces.deepReplaceHeadNamespace(obj(s2,s2),"x//y",p3);
            failed("Replace x//y in "+s2+" should have failed!");
        } catch (IllegalArgumentException x) {
            System.out.println("Received expected exception: "+x);
        }

        if (failure != null) throw new Exception(failure);
    }

    private static String[][] wildcards = {
        { "", "*:*" },
        { "//", "//*:*" },
        { "foo", "foo//*:*" },
        { "//foo", "//foo//*:*" },
        { "////foo", "//foo//*:*" },
        { "foo//", "foo//*:*" },
        { "foo////", "foo//*:*" },
        { "//foo//", "//foo//*:*" },
        { "////foo//", "//foo//*:*" },
        { "////foo////", "//foo//*:*" },
        { "foo//bar", "foo//bar//*:*" },
        { "//foo//bar", "//foo//bar//*:*" },
        { "////foo//bar", "//foo//bar//*:*" },
        { "foo//bar//", "foo//bar//*:*" },
        { "foo//bar////", "foo//bar//*:*" },
        { "//foo//bar//", "//foo//bar//*:*" },
        { "////foo//bar//", "//foo//bar//*:*" },
        { "////foo//bar////", "//foo//bar//*:*" },
        { "foo////bar", "foo//bar//*:*" },
        { "//foo////bar", "//foo//bar//*:*" },
        { "////foo////bar", "//foo//bar//*:*" },
        { "foo////bar//", "foo//bar//*:*" },
        { "foo////bar////", "foo//bar//*:*" },
        { "//foo////bar//", "//foo//bar//*:*" },
        { "////foo////bar//", "//foo//bar//*:*" },
        { "////foo////bar////", "//foo//bar//*:*" },
        { "fo/o", "fo/o//*:*" },
        { "//f/oo", "//f/oo//*:*" },
        { "////f/o/o", "//f/o/o//*:*" },
        { "fo/o//", "fo/o//*:*" },
        { "f/oo////", "f/oo//*:*" },
        { "//fo/o//", "//fo/o//*:*" },
        { "////f/oo//", "//f/oo//*:*" },
        { "////f/o/o////", "//f/o/o//*:*" },
        { "foo//b/a/r", "foo//b/a/r//*:*" },
        { "//fo/o//bar", "//fo/o//bar//*:*" },
        { "////foo//b/ar", "//foo//b/ar//*:*" },
        { "foo//ba/r//", "foo//ba/r//*:*" },
        { "f/oo//bar////", "f/oo//bar//*:*" },
        { "//f/o/o//bar//", "//f/o/o//bar//*:*" },
        { "////foo//b/a/r//", "//foo//b/a/r//*:*" },
        { "////f/o/o//b/a/r////", "//f/o/o//b/a/r//*:*" },
        { "foo////ba/r", "foo//ba/r//*:*" },
        { "//foo////b/ar", "//foo//b/ar//*:*" },
        { "////f/oo////bar", "//f/oo//bar//*:*" },
        { "fo/o////bar//", "fo/o//bar//*:*" },
        { "foo////ba/r////", "foo//ba/r//*:*" },
        { "//fo/o////ba/r//", "//fo/o//ba/r//*:*" },
        { "////f/oo////b/ar//", "//f/oo//b/ar//*:*" },
        { "////f/o/o////b/a/r////", "//f/o/o//b/a/r//*:*" },
    };
    private final static String[] badguys = {
        null,
        "/",         "/*:*",
        "///",       "///*:*" ,
        "/foo",      "/foo//*:*",
        "//foo/",    "//foo///*:*" ,
        "/////foo",  "///foo//*:*",
        "/foo//",    "/foo//*:*",
        "foo/////",  "foo///*:*",
        "///foo//",  "///foo//*:*",
        "////foo///", "//foo///*:*" ,
        "/////foo/////", "///foo///*:*",
        "/foo//bar", "/foo//bar//*:*",
        "//foo///bar", "//foo///bar//*:*",
        "/////foo////bar/", "///foo//bar///*:*",
        "foo///bar//", "foo//bar///*:*",
        "foo//bar/////", "foo///bar//*:*",
        "///foo//bar//", "//foo///bar//*:*" ,
    };
    public static void testWildcard() throws Exception {
        int i = 0;
        for (String[] pair : wildcards) {
            i++;
            final String msg = "testWildcard[good,"+i+"] "+Arrays.asList(pair)+": ";
            assertEquals(msg, new ObjectName(pair[1]),
                    JMXNamespaces.getWildcardFor(pair[0]));
        }
        i=0;
        for (String bad : badguys) {
            i++;
            try {
                JMXNamespaces.getWildcardFor(bad);
                failed("testWildcard[bad,"+i+"] "+bad+" incorrectly accepted. " +
                        "IllegalArgumentException was expected");
            } catch (IllegalArgumentException x) {
                // OK
            }
        }
        if (failure != null) throw new Exception(failure);
    }

    private static String[][] goodinsert = {
        {"","d:k=v","d:k=v"},
        {"","//d:k=v","//d:k=v"},
        {"//","d:k=v","//d:k=v"},
        {"//","//d:k=v","//d:k=v"},
        {"//","a//d:k=v","//a//d:k=v"},
        {"//","//a//d:k=v","//a//d:k=v"},
        {"//","////a////d:k=v","//a//d:k=v"},
        {"//b","////a////d:k=v","//b//a//d:k=v"},
        {"b","////a////d:k=v","b//a//d:k=v"},
        {"b","d:k=v","b//d:k=v"},
        {"b//","d:k=v","b//d:k=v"},
        {"//b//","d:k=v","//b//d:k=v"},
        {"//b","////a////d:k=v","//b//a//d:k=v"},
        {"b//c","////a////d:k=v","b//c//a//d:k=v"},
        {"b//c","d:k=v","b//c//d:k=v"},
        {"b//c//","d:k=v","b//c//d:k=v"},
        {"//b//c//","d:k=v","//b//c//d:k=v"},
        {"","/d:k=v","/d:k=v"},
        {"","///d:k=v","///d:k=v"},
        {"//","/d:k=v","///d:k=v"},
        {"//","///d:k=v","///d:k=v"},
        {"//","a///d:k=v","//a///d:k=v"},
        {"//","//a///d:k=v","//a///d:k=v"},
        {"//","////a////d/:k=v","//a//d/:k=v"},
        {"//b","////a/////d:k=v","//b//a///d:k=v"},
        {"b","////a////d/:k=v","b//a//d/:k=v"},
        {"b","/d:k=v","b///d:k=v"},
        {"b//","/d:k=v","b///d:k=v"},
        {"//b//","/d:k=v","//b///d:k=v"},
        {"//b","////a/////d:k=v","//b//a///d:k=v"},
        {"b//c","////a/////d:k=v","b//c//a///d:k=v"},
        {"b//c","/d:k=v","b//c///d:k=v"},
        {"b//c//","/d:k=v","b//c///d:k=v"},
        {"//b//c//","d/:k=v","//b//c//d/:k=v"},
    };

    private static String[][] badinsert = {
        {"/","d:k=v"},
        {"/","//d:k=v"},
        {"///","d:k=v"},
        {"///","//d:k=v"},
        {"///","/a//d:k=v"},
        {"///","///a//d:k=v"},
        {"///","/////a////d:k=v"},
        {"//b","/////a////d:k=v"},
        {"b/","////a////d:k=v"},
        {"b/","d:k=v"},
        {"b///","d:k=v"},
        {"//b///","d:k=v"},
        {"//b/","////a////d:k=v"},
        {"b///c","////a////d:k=v"},
        {"b//c/","d:k=v"},
        {"b///c//","d:k=v"},
        {"//b///c//","d:k=v"},

    };

    public static void testInsertPath() throws Exception {
        int i = 0;
        for (String[] pair : goodinsert) {
            i++;
            final String msg = "testInsertPath[good,"+i+"] "+Arrays.asList(pair)+": ";
            assertEquals(msg,new ObjectName(pair[2]),
                    JMXNamespaces.insertPath(pair[0],
                    new ObjectName(pair[1])));
        }
        i=0;
        for (String[] bad : badinsert) {
            i++;
            try {
                JMXNamespaces.insertPath(bad[0],
                    new ObjectName(bad[1]));
                failed("testInsertPath[bad,"+i+"] "+
                        Arrays.asList(bad)+" incorrectly accepted. " +
                        "IllegalArgumentException was expected");
            } catch (IllegalArgumentException x) {
                // OK
            }
        }
        if (failure != null) throw new Exception(failure);
    }

    private static String[][] testpath  = {
        {"/a/a/:k=v",""},
        {"/:k=v",""},
        {"bli:k=v",""},
        {"///a/a/:k=v",""},
        {"///:k=v",""},
        {"//bli:k=v",""},
        {"/////a/a/:k=v",""},
        {"/////:k=v",""},
        {"////bli:k=v",""},
        {"y///a/a/:k=v","y"},
        {"y///:k=v","y"},
        {"y//bli:k=v","y"},
        {"y/////a/a/:k=v","y"},
        {"y/////:k=v","y"},
        {"y////bli:k=v","y"},
        {"//y///a/a/:k=v","y"},
        {"//y///:k=v","y"},
        {"//y//bli:k=v","y"},
        {"//y/////a/a/:k=v","y"},
        {"//y/////:k=v","y"},
        {"//y////bli:k=v","y"},
        {"////y///a/a/:k=v","y"},
        {"////y///:k=v","y"},
        {"////y//bli:k=v","y"},
        {"////y/////a/a/:k=v","y"},
        {"////y/////:k=v","y"},
        {"////y////bli:k=v","y"},

        {"z//y///a/a/:k=v","z//y"},
        {"z//y///:k=v","z//y"},
        {"z//y//bli:k=v","z//y"},
        {"z//y/////a/a/:k=v","z//y"},
        {"z//y/////:k=v","z//y"},
        {"z//y////bli:k=v","z//y"},
        {"//z//y///a/a/:k=v","z//y"},
        {"//z//y///:k=v","z//y"},
        {"//z//y//bli:k=v","z//y"},
        {"//z//y/////a/a/:k=v","z//y"},
        {"//z//y/////:k=v","z//y"},
        {"//z//y////bli:k=v","z//y"},
        {"z////y///a/a/:k=v","z//y"},
        {"z////y///:k=v","z//y"},
        {"z////y//bli:k=v","z//y"},
        {"z////y/////a/a/:k=v","z//y"},
        {"z////y/////:k=v","z//y"},
        {"z////y////bli:k=v","z//y"},
        {"//z////y///a/a/:k=v","z//y"},
        {"//z////y///:k=v","z//y"},
        {"//z////y//bli:k=v","z//y"},
        {"//z////y/////a/a/:k=v","z//y"},
        {"//z////y/////:k=v","z//y"},
        {"//z////y////bli:k=v","z//y"},
        {"////z////y///a/a/:k=v","z//y"},
        {"////z////y///:k=v","z//y"},
        {"////z////y//bli:k=v","z//y"},
        {"////z////y/////a/a/:k=v","z//y"},
        {"////z////y/////:k=v","z//y"},
        {"////z////y////bli:k=v","z//y"},

    };

    public static void testGetNormalizedPath() throws Exception {
        int i = 0;
        for (String[] pair : testpath) {
            i++;
            final String msg = "testGetNormalizedPath["+i+"] "+Arrays.asList(pair)+": ";
            assertEquals(msg,pair[1],
                    JMXNamespaces.getContainingNamespace(new ObjectName(pair[0])));
        }
        if (failure != null) throw new Exception(failure);
    }

    private static String[][] testdomain  = {
        {"/a/a/","/a/a/"},
        {"/","/"},
        {"bli","bli"},
        {"///a/a/","///a/a/"},
        {"///","///"},
        {"//bli","//bli"},
        {"/////a/a/","///a/a/"},
        {"/////","///"},
        {"////bli","//bli"},
        {"y///a/a/","y///a/a/"},
        {"y///","y///"},
        {"y//bli","y//bli"},
        {"y/////a/a/","y///a/a/"},
        {"y/////","y///"},
        {"y////bli","y//bli"},
        {"//y///a/a/","//y///a/a/"},
        {"//y///","//y///"},
        {"//y//bli","//y//bli"},
        {"//y/////a/a/","//y///a/a/"},
        {"//y/////","//y///"},
        {"//y////bli","//y//bli"},
        {"////y///a/a/","//y///a/a/"},
        {"////y///","//y///"},
        {"////y//bli","//y//bli"},
        {"////y/////a/a/","//y///a/a/"},
        {"////y/////","//y///"},
        {"////y////bli","//y//bli"},

        {"z//y///a/a/","z//y///a/a/"},
        {"z//y///","z//y///"},
        {"z//y//bli","z//y//bli"},
        {"z//y/////a/a/","z//y///a/a/"},
        {"z//y/////","z//y///"},
        {"z//y////bli","z//y//bli"},
        {"//z//y///a/a/","//z//y///a/a/"},
        {"//z//y///","//z//y///"},
        {"//z//y//bli","//z//y//bli"},
        {"//z//y/////a/a/","//z//y///a/a/"},
        {"//z//y/////","//z//y///"},
        {"//z//y////bli","//z//y//bli"},
        {"z////y///a/a/","z//y///a/a/"},
        {"z////y///","z//y///"},
        {"z////y//bli","z//y//bli"},
        {"z////y/////a/a/","z//y///a/a/"},
        {"z////y/////","z//y///"},
        {"z////y////bli","z//y//bli"},
        {"//z////y///a/a/","//z//y///a/a/"},
        {"//z////y///","//z//y///"},
        {"//z////y//bli","//z//y//bli"},
        {"//z////y/////a/a/","//z//y///a/a/"},
        {"//z////y/////","//z//y///"},
        {"//z////y////bli","//z//y//bli"},
        {"////z////y///a/a/","//z//y///a/a/"},
        {"////z////y///","//z//y///"},
        {"////z////y//bli","//z//y//bli"},
        {"////z////y/////a/a/","//z//y///a/a/"},
        {"////z////y/////","//z//y///"},
        {"////z////y////bli","//z//y//bli"},

        {"bli//","bli//"},
        {"//bli//","//bli//"},
        {"////bli//","//bli//"},
        {"y////","y//"},
        {"y//bli//","y//bli//"},
        {"y////","y//"},
        {"y////bli//","y//bli//"},
        {"//y////","//y//"},
        {"//y//bli//","//y//bli//"},
        {"//y//////","//y//"},
        {"//y////bli//","//y//bli//"},
        {"////y////","//y//"},
        {"////y//bli////","//y//bli//"},
        {"////y//////","//y//"},
        {"////y////bli////","//y//bli//"},
        {"z//y////","z//y//"},
        {"z//y//bli//","z//y//bli//"},
        {"z//y//////","z//y//"},
        {"z//y////bli//","z//y//bli//"},
        {"//z//y////","//z//y//"},
        {"//z//y//bli//","//z//y//bli//"},
        {"//z//y//////","//z//y//"},
        {"//z//y////bli//","//z//y//bli//"},
        {"z////y////","z//y//"},
        {"z////y//bli//","z//y//bli//"},
        {"z////y//////","z//y//"},
        {"z////y////bli//","z//y//bli//"},
        {"//z////y////","//z//y//"},
        {"//z////y//bli//","//z//y//bli//"},
        {"//z////y//////","//z//y//"},
        {"//z////y////bli//","//z//y//bli//"},
        {"////z////y////","//z//y//"},
        {"////z////y//bli//","//z//y//bli//"},
        {"////z////y//////","//z//y//"},
        {"////z////y////bli//","//z//y//bli//"},

    };
    private static String[][] testnolead  = {
        {"/a/a/","/a/a/"},
        {"/","/"},
        {"bli","bli"},
        {"///a/a/","/a/a/"},
        {"///","/"},
        {"//bli","bli"},
        {"/////a/a/","/a/a/"},
        {"/////","/"},
        {"////bli","bli"},
        {"y///a/a/","y///a/a/"},
        {"y///","y///"},
        {"y//bli","y//bli"},
        {"y/////a/a/","y///a/a/"},
        {"y/////","y///"},
        {"y////bli","y//bli"},
        {"//y///a/a/","y///a/a/"},
        {"//y///","y///"},
        {"//y//bli","y//bli"},
        {"//y/////a/a/","y///a/a/"},
        {"//y/////","y///"},
        {"//y////bli","y//bli"},
        {"////y///a/a/","y///a/a/"},
        {"////y///","y///"},
        {"////y//bli","y//bli"},
        {"////y/////a/a/","y///a/a/"},
        {"////y/////","y///"},
        {"////y////bli","y//bli"},

        {"z//y///a/a/","z//y///a/a/"},
        {"z//y///","z//y///"},
        {"z//y//bli","z//y//bli"},
        {"z//y/////a/a/","z//y///a/a/"},
        {"z//y/////","z//y///"},
        {"z//y////bli","z//y//bli"},
        {"//z//y///a/a/","z//y///a/a/"},
        {"//z//y///","z//y///"},
        {"//z//y//bli","z//y//bli"},
        {"//z//y/////a/a/","z//y///a/a/"},
        {"//z//y/////","z//y///"},
        {"//z//y////bli","z//y//bli"},
        {"z////y///a/a/","z//y///a/a/"},
        {"z////y///","z//y///"},
        {"z////y//bli","z//y//bli"},
        {"z////y/////a/a/","z//y///a/a/"},
        {"z////y/////","z//y///"},
        {"z////y////bli","z//y//bli"},
        {"//z////y///a/a/","z//y///a/a/"},
        {"//z////y///","z//y///"},
        {"//z////y//bli","z//y//bli"},
        {"//z////y/////a/a/","z//y///a/a/"},
        {"//z////y/////","z//y///"},
        {"//z////y////bli","z//y//bli"},
        {"////z////y///a/a/","z//y///a/a/"},
        {"////z////y///","z//y///"},
        {"////z////y//bli","z//y//bli"},
        {"////z////y/////a/a/","z//y///a/a/"},
        {"////z////y/////","z//y///"},
        {"////z////y////bli","z//y//bli"},

        {"bli//","bli//"},
        {"//bli//","bli//"},
        {"////bli//","bli//"},
        {"y////","y//"},
        {"y//bli//","y//bli//"},
        {"y////","y//"},
        {"y////bli//","y//bli//"},
        {"//y////","y//"},
        {"//y//bli//","y//bli//"},
        {"//y//////","y//"},
        {"//y////bli//","y//bli//"},
        {"////y////","y//"},
        {"////y//bli////","y//bli//"},
        {"////y//////","y//"},
        {"////y////bli////","y//bli//"},
        {"z//y////","z//y//"},
        {"z//y//bli//","z//y//bli//"},
        {"z//y//////","z//y//"},
        {"z//y////bli//","z//y//bli//"},
        {"//z//y////","z//y//"},
        {"//z//y//bli//","z//y//bli//"},
        {"//z//y//////","z//y//"},
        {"//z//y////bli//","z//y//bli//"},
        {"z////y////","z//y//"},
        {"z////y//bli//","z//y//bli//"},
        {"z////y//////","z//y//"},
        {"z////y////bli//","z//y//bli//"},
        {"//z////y////","z//y//"},
        {"//z////y//bli//","z//y//bli//"},
        {"//z////y//////","z//y//"},
        {"//z////y////bli//","z//y//bli//"},
        {"////z////y////","z//y//"},
        {"////z////y//bli//","z//y//bli//"},
        {"////z////y//////","z//y//"},
        {"////z////y////bli//","z//y//bli//"},

    };

    public static void testNormalizeDomain() throws Exception {
        int i = 0;
        for (String[] pair : testdomain) {
            i++;
            final String msg = "testNormalizeDomain["+i+", false] "+Arrays.asList(pair)+": ";
            assertEquals(msg,pair[1],
                    ObjectNameRouter.normalizeDomain(pair[0],false));
        }
        if (failure != null) throw new Exception(failure);
        i = 0;
        for (String[] pair : testnolead) {
            i++;
            final String msg = "testNormalizeDomain["+i+", true] "+Arrays.asList(pair)+": ";
            assertEquals(msg,pair[1],
                    ObjectNameRouter.normalizeDomain(pair[0],true));
        }
        if (failure != null) throw new Exception(failure);
    }

    public static void main(String[] args) throws Exception {
        testDeepRewrite();
        testNormalizeDomain();
        testInsertPath();
        testWildcard();
        testGetNormalizedPath();
    }

    private static void assertEquals(Object x, Object y) {
        assertEquals("",x,y);
    }

    private static void assertEquals(String msg, Object x, Object y) {
        if (msg == null) msg="";
        if (!equal(x, y))
            failed(msg+"expected " + string(x) + "; got " + string(y));
    }

    private static boolean equal(Object x, Object y) {
        if (x == y)
            return true;
        if (x == null || y == null)
            return false;
        if (x.getClass().isArray())
            return Arrays.deepEquals(new Object[] {x}, new Object[] {y});
        return x.equals(y);
    }

    private static String string(Object x) {
        String s = Arrays.deepToString(new Object[] {x});
        return s.substring(1, s.length() - 1);
    }


    private static void failed(String why) {
        failure = why;
        new Throwable("FAILED: " + why).printStackTrace(System.out);
    }

}
