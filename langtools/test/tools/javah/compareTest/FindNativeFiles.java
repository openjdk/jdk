/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Method;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class FindNativeFiles {
    public static void main(String[] args) throws IOException, ConstantPoolException {
        new FindNativeFiles().run(args);
    }

    public void run(String[] args) throws IOException, ConstantPoolException {
        JarFile jar = new JarFile(args[0]);
        Set<JarEntry> entries = getNativeClasses(jar);
        for (JarEntry e: entries) {
            String name = e.getName();
            String className = name.substring(0, name.length() - 6).replace("/", ".");
            System.out.println(className);
        }
    }

    Set<JarEntry> getNativeClasses(JarFile jar) throws IOException, ConstantPoolException {
        Set<JarEntry> results = new TreeSet<JarEntry>(new Comparator<JarEntry>() {
            public int compare(JarEntry o1, JarEntry o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        Enumeration<JarEntry> e = jar.entries();
        while (e.hasMoreElements()) {
            JarEntry je = e.nextElement();
            if (isNativeClass(jar, je))
                results.add(je);
        }
        return results;
    }

    boolean isNativeClass(JarFile jar, JarEntry entry) throws IOException, ConstantPoolException {
        String name = entry.getName();
        if (name.startsWith("META-INF") || !name.endsWith(".class"))
            return false;
        //String className = name.substring(0, name.length() - 6).replace("/", ".");
        //System.err.println("check " + className);
        InputStream in = jar.getInputStream(entry);
        ClassFile cf = ClassFile.read(in);
        in.close();
        for (int i = 0; i < cf.methods.length; i++) {
            Method m = cf.methods[i];
            if (m.access_flags.is(AccessFlags.ACC_NATIVE)) {
                // System.err.println(className);
                return true;
            }
        }
        return false;
    }
}
