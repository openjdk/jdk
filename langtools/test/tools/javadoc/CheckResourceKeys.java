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
 * @bug 8000612
 * @summary need test program to validate javadoc resource bundles
 */

import java.io.*;
import java.util.*;
import javax.tools.*;
import com.sun.tools.classfile.*;

/**
 * Compare string constants in javadoc classes against keys in javadoc resource bundles.
 */
public class CheckResourceKeys {
    /**
     * Main program.
     * Options:
     * -finddeadkeys
     *      look for keys in resource bundles that are no longer required
     * -findmissingkeys
     *      look for keys in resource bundles that are missing
     *
     * @throws Exception if invoked by jtreg and errors occur
     */
    public static void main(String... args) throws Exception {
        CheckResourceKeys c = new CheckResourceKeys();
        if (c.run(args))
            return;

        if (is_jtreg())
            throw new Exception(c.errors + " errors occurred");
        else
            System.exit(1);
    }

    static boolean is_jtreg() {
        return (System.getProperty("test.src") != null);
    }

    /**
     * Main entry point.
     */
    boolean run(String... args) throws Exception {
        boolean findDeadKeys = false;
        boolean findMissingKeys = false;

        if (args.length == 0) {
            if (is_jtreg()) {
                findDeadKeys = true;
                findMissingKeys = true;
            } else {
                System.err.println("Usage: java CheckResourceKeys <options>");
                System.err.println("where options include");
                System.err.println("  -finddeadkeys      find keys in resource bundles which are no longer required");
                System.err.println("  -findmissingkeys   find keys in resource bundles that are required but missing");
                return true;
            }
        } else {
            for (String arg: args) {
                if (arg.equalsIgnoreCase("-finddeadkeys"))
                    findDeadKeys = true;
                else if (arg.equalsIgnoreCase("-findmissingkeys"))
                    findMissingKeys = true;
                else
                    error("bad option: " + arg);
            }
        }

        if (errors > 0)
            return false;

        Set<String> codeKeys = getCodeKeys();
        Set<String> resourceKeys = getResourceKeys();

        System.err.println("found " + codeKeys.size() + " keys in code");
        System.err.println("found " + resourceKeys.size() + " keys in resource bundles");

        if (findDeadKeys)
            findDeadKeys(codeKeys, resourceKeys);

        if (findMissingKeys)
            findMissingKeys(codeKeys, resourceKeys);

        return (errors == 0);
    }

    /**
     * Find keys in resource bundles which are probably no longer required.
     * A key is required if there is a string in the code that is a resource key,
     * or if the key is well-known according to various pragmatic rules.
     */
    void findDeadKeys(Set<String> codeKeys, Set<String> resourceKeys) {
        for (String rk: resourceKeys) {
            if (codeKeys.contains(rk))
                continue;

            error("Resource key not found in code: " + rk);
        }
    }

    /**
     * For all strings in the code that look like they might be
     * a resource key, verify that a key exists.
     */
    void findMissingKeys(Set<String> codeKeys, Set<String> resourceKeys) {
        for (String ck: codeKeys) {
            if (resourceKeys.contains(ck))
                continue;
            error("No resource for \"" + ck + "\"");
        }
    }

    /**
     * Get the set of strings from (most of) the javadoc classfiles.
     */
    Set<String> getCodeKeys() throws IOException {
        Set<String> results = new TreeSet<String>();
        JavaCompiler c = ToolProvider.getSystemJavaCompiler();
        JavaFileManager fm = c.getStandardFileManager(null, null, null);
        JavaFileManager.Location javadocLoc = findJavadocLocation(fm);
        String[] pkgs = {
            "com.sun.tools.doclets",
            "com.sun.tools.javadoc"
        };
        for (String pkg: pkgs) {
            for (JavaFileObject fo: fm.list(javadocLoc,
                    pkg, EnumSet.of(JavaFileObject.Kind.CLASS), true)) {
                String name = fo.getName();
                // ignore resource files
                if (name.matches(".*resources.[A-Za-z_0-9]+\\.class.*"))
                    continue;
                scan(fo, results);
            }
        }

        // special handling for code strings synthesized in
        // com.sun.tools.doclets.internal.toolkit.util.Util.getTypeName
        String[] extras = {
            "AnnotationType", "Class", "Enum", "Error", "Exception", "Interface"
        };
        for (String s: extras) {
            if (results.contains("doclet." + s))
                results.add("doclet." + s.toLowerCase());
        }

        // special handling for code strings synthesized in
        // com.sun.tools.javadoc.Messager
        results.add("javadoc.error.msg");
        results.add("javadoc.note.msg");
        results.add("javadoc.note.pos.msg");
        results.add("javadoc.warning.msg");

        return results;
    }

    // depending on how the test is run, javadoc may be on bootclasspath or classpath
    JavaFileManager.Location findJavadocLocation(JavaFileManager fm) {
        JavaFileManager.Location[] locns =
            { StandardLocation.PLATFORM_CLASS_PATH, StandardLocation.CLASS_PATH };
        try {
            for (JavaFileManager.Location l: locns) {
                JavaFileObject fo = fm.getJavaFileForInput(l,
                    "com.sun.tools.javadoc.Main", JavaFileObject.Kind.CLASS);
                if (fo != null) {
                    System.err.println("found javadoc in " + l);
                    return l;
                }
            }
        } catch (IOException e) {
            throw new Error(e);
        }
        throw new IllegalStateException("Cannot find javadoc");
    }

    /**
     * Get the set of strings from a class file.
     * Only strings that look like they might be a resource key are returned.
     */
    void scan(JavaFileObject fo, Set<String> results) throws IOException {
        //System.err.println("scan " + fo.getName());
        InputStream in = fo.openInputStream();
        try {
            ClassFile cf = ClassFile.read(in);
            for (ConstantPool.CPInfo cpinfo: cf.constant_pool.entries()) {
                if (cpinfo.getTag() == ConstantPool.CONSTANT_Utf8) {
                    String v = ((ConstantPool.CONSTANT_Utf8_info) cpinfo).value;
                    if (v.matches("(doclet|main|javadoc|tag)\\.[A-Za-z0-9-_.]+"))
                        results.add(v);
                }
            }
        } catch (ConstantPoolException ignore) {
        } finally {
            in.close();
        }
    }

    /**
     * Get the set of keys from the javadoc resource bundles.
     */
    Set<String> getResourceKeys() {
        String[] names = {
                "com.sun.tools.doclets.formats.html.resources.standard",
                "com.sun.tools.doclets.internal.toolkit.resources.doclets",
                "com.sun.tools.javadoc.resources.javadoc",
        };
        Set<String> results = new TreeSet<String>();
        for (String name : names) {
            ResourceBundle b = ResourceBundle.getBundle(name);
            results.addAll(b.keySet());
        }
        return results;
    }

    /**
     * Report an error.
     */
    void error(String msg) {
        System.err.println("Error: " + msg);
        errors++;
    }

    int errors;
}
