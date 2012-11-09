/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 4777124 6920545 6911753
 * @summary Verify that all Charset subclasses are available through the API
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import sun.misc.Launcher;


public class NIOCharsetAvailabilityTest {

    public static void main(String[] args) throws Exception {
        // build the set of all Charset subclasses in the
        // two known charset implementation packages
        Set charsets = new HashSet();
        addCharsets(charsets, "sun.nio.cs");
        addCharsets(charsets, "sun.nio.cs.ext");

        // remove the charsets that the API says are available
        Collection availableCharsets = Charset.availableCharsets().values();
        Iterator iter = availableCharsets.iterator();
        while (iter.hasNext()) {
            charsets.remove(((Charset) iter.next()).getClass());
        }

        // remove the known pseudo-charsets that serve only to implement
        // other charsets, but shouldn't be known to the public
        charsets.remove(Class.forName("sun.nio.cs.Unicode"));
        charsets.remove(Class.forName("sun.nio.cs.ext.ISO2022"));
        charsets.remove(Class.forName("sun.nio.cs.ext.ISO2022_CN_GB"));
        charsets.remove(Class.forName("sun.nio.cs.ext.ISO2022_CN_CNS"));
        charsets.remove(Class.forName("sun.nio.cs.ext.JIS_X_0208_Solaris"));
        charsets.remove(Class.forName("sun.nio.cs.ext.JIS_X_0208_MS932"));
        charsets.remove(Class.forName("sun.nio.cs.ext.JIS_X_0212_MS5022X"));
        charsets.remove(Class.forName("sun.nio.cs.ext.JIS_X_0212_Solaris"));
        charsets.remove(Class.forName("sun.nio.cs.ext.JIS_X_0208_MS5022X"));

        // report the charsets that are implemented but not available
        iter = charsets.iterator();
        while (iter.hasNext()) {
            System.out.println("Unused Charset subclass: " + ((Class) iter.next()).getName());
        }
        if (charsets.size() > 0) {
            throw new RuntimeException();
        }
    }

    private static Vector classPathSegments = new Vector();

    private static void addCharsets(Set charsets, final String packageName)
            throws Exception {

        String classPath =
            (String) java.security.AccessController.doPrivileged(
             new sun.security.action.GetPropertyAction("sun.boot.class.path"));
        String s =
            (String) java.security.AccessController.doPrivileged(
             new sun.security.action.GetPropertyAction("java.class.path"));

        // Search combined system and application class path
        if (s != null && s.length() != 0) {
            classPath += File.pathSeparator + s;
        }
        while (classPath != null && classPath.length() != 0) {
            int i = classPath.lastIndexOf(java.io.File.pathSeparatorChar);
            String dir = classPath.substring(i + 1);
            if (i == -1) {
                classPath = null;
            } else {
                classPath = classPath.substring(0, i);
            }
            classPathSegments.insertElementAt(dir, 0);
        }

        // add extensions from the extension class loader
        ClassLoader appLoader = Launcher.getLauncher().getClassLoader();
        URLClassLoader extLoader = (URLClassLoader) appLoader.getParent();
        URL[] urls = extLoader.getURLs();
        for (int i = 0; i < urls.length; i++) {
            try {
                URI uri = new URI(urls[i].toString());
                classPathSegments.insertElementAt(uri.getPath(), 0);
            } catch (URISyntaxException e) {
            }
        }

        String[] classList = (String[])
            java.security.AccessController.doPrivileged(
                                    new java.security.PrivilegedAction() {
                public Object run() {
                    return getClassList(packageName, "");
                }
            });

        for (int i = 0; i < classList.length; i++) {
            try {
                Class clazz = Class.forName(packageName + "." + classList[i]);
                Class superclazz = clazz.getSuperclass();
                while (superclazz != null && !superclazz.equals(Object.class)) {
                    if (superclazz.equals(Charset.class)) {
                        charsets.add(clazz);
                        break;
                    } else {
                        superclazz = superclazz.getSuperclass();
                    }
                }
            } catch (ClassNotFoundException e) {
            }
        }
    }

    private static final char ZIPSEPARATOR = '/';

    /**
     * Walk through CLASSPATH and find class list from a package.
     * The class names start with prefix string
     * @param package name, class name prefix
     * @return class list in an array of String
     */
    private static String[] getClassList(String pkgName, String prefix) {
        Vector listBuffer = new Vector();
        String packagePath = pkgName.replace('.', File.separatorChar)
            + File.separatorChar;
        String zipPackagePath = pkgName.replace('.', ZIPSEPARATOR)
            + ZIPSEPARATOR;
        for (int i = 0; i < classPathSegments.size(); i++){
            String onePath = (String) classPathSegments.elementAt(i);
            File f = new File(onePath);
            if (!f.exists())
                continue;
            if (f.isFile())
                scanFile(f, zipPackagePath, listBuffer, prefix);
            else if (f.isDirectory()) {
                String fullPath;
                if (onePath.endsWith(File.separator))
                    fullPath = onePath + packagePath;
                else
                    fullPath = onePath + File.separatorChar + packagePath;
                File dir = new File(fullPath);
                if (dir.exists() && dir.isDirectory())
                    scanDir(dir, listBuffer, prefix);
            }
        }
        String[] classNames = new String[listBuffer.size()];
        listBuffer.copyInto(classNames);
        return classNames;
    }

    private static void addClass (String className, Vector listBuffer, String prefix) {
        if (className != null && className.startsWith(prefix)
                    && !listBuffer.contains(className))
            listBuffer.addElement(className);
    }

    private static String midString(String str, String pre, String suf) {
        String midStr;
        if (str.startsWith(pre) && str.endsWith(suf))
            midStr = str.substring(pre.length(), str.length() - suf.length());
        else
            midStr = null;
        return midStr;
    }

    private static void scanDir(File dir, Vector listBuffer, String prefix) {
        String[] fileList = dir.list();
        for (int i = 0; i < fileList.length; i++) {
            addClass(midString(fileList[i], "", ".class"), listBuffer, prefix);
        }
    }

    private static void scanFile(File f, String packagePath, Vector listBuffer,
                String prefix) {
        try {
            ZipInputStream zipFile = new ZipInputStream(new FileInputStream(f));
            ZipEntry entry;
            while ((entry = zipFile.getNextEntry()) != null) {
                String eName = entry.getName();
                if (eName.startsWith(packagePath)) {
                    if (eName.endsWith(".class")) {
                        addClass(midString(eName, packagePath, ".class"),
                                listBuffer, prefix);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("file not found:" + e);
        } catch (IOException e) {
            System.out.println("file IO Exception:" + e);
        } catch (Exception e) {
            System.out.println("Exception:" + e);
        }
    }
}
