/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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
 * @bug 6957241
 * @summary Verify that multiple file are loaded with JarIndex
 * @modules jdk.jartool/sun.tools.jar
 *          jdk.httpserver
 *          jdk.compiler
 *          jdk.zipfs
 * @run main/othervm MultiSameFileTest
 */

import java.io.IOException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Verifies the fix for 6957241: ClassLoader.getResources() returns only 1 
 * instance when using jar indexing 
 *
 * 1) Compile the test sources:
 *   jarE:
 *     Main.java
 *     resource.txt
 *   jarF:
 *     resource.txt
 *
 * 2) Build three jar files e.jar, f.jar
 *
 * 3) Create an index in a.jar (jar -i e.jar f.jar)
 *
 * 4) Start a process to execute java -cp e.jar:f.jar Main
 *
 * The test then tries to locate services/resources within the jars using
 * URLClassLoader.
 *
 */

public class MultiSameFileTest {
    static final String slash = File.separator;
    static final String[] testSources =  {
         "jarE" + slash + "e" + slash + "Main.java",
         "jarE" + slash + "resource.txt",
         "jarF" + slash + "resource.txt"};

    static final String testSrc = System.getProperty("test.src");
    static final String testSrcDir = testSrc != null ? testSrc : ".";
    static final String testClasses = System.getProperty("test.classes");
    static final String testClassesDir = testClasses != null ? testClasses : ".";


    public static void main(String[] args) throws Exception {

        buildTest();

        doTest(testClassesDir);
    }

    static void buildTest() {
        /* compile the source that will be used to generate the jars */
        testSources[0] = testSrcDir + slash + testSources[0];

        compile("-d" , testClassesDir,
                "-sourcepath", testSrcDir,
                testSources[0]);

        /* build the 2 jar files */
        jar("-cf", testClassesDir + slash + "e.jar",
            "-C", testClassesDir, "e",
            "-C", testSrcDir + slash + "jarE", "resource.txt");
        jar("-cf", testClassesDir + slash + "f.jar",
            "-C", testSrcDir + slash + "jarF", "resource.txt");

        /* Create an index in a.jar for b.jar and c.jar */
        createIndex(testClassesDir);
    }

    /* run jar <args> */
    static void jar(String... args) {
        debug("Running: jar " + Arrays.toString(args));
        sun.tools.jar.Main jar = new sun.tools.jar.Main(System.out, System.err, "jar");
        if (!jar.run(args)) {
            throw new RuntimeException("jar failed: args=" + Arrays.toString(args));
        }
    }

    /* run javac <args> */
    static void compile(String... args) {
        debug("Running: javac " + Arrays.toString(args));
        if (com.sun.tools.javac.Main.compile(args) != 0) {
             throw new RuntimeException("javac failed: args=" + Arrays.toString(args));
        }
    }

    static String jar;
    static String java;
    static {
        jar = System.getProperty("java.home") + slash+  "bin" + slash + "jar";
        java = System.getProperty("java.home") + slash+  "bin" + slash + "java";
    }

    /* create the index */
    static void createIndex(String workingDir) {
        // ProcessBuilder is used so that the current directory can be set
        // to the directory that directly contains the jars.
        debug("Running jar to create the index");
        ProcessBuilder pb = new ProcessBuilder(
           jar, "-J-Dsun.misc.JarIndex.metaInfFilenames=true", "-i", "e.jar", "f.jar");
        pb.directory(new File(workingDir));
        //pd.inheritIO();
        try {
            Process p = pb.start();
            if(p.waitFor() != 0)
                throw new RuntimeException("jar indexing failed");

            if(debug && p != null) {
                String line = null;
                BufferedReader reader =
                         new BufferedReader(new InputStreamReader(p.getInputStream()));
                while((line = reader.readLine()) != null)
                    debug(line);
                reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                while((line = reader.readLine()) != null)
                    debug(line);
            }
        } catch(InterruptedException ie) { throw new RuntimeException(ie);
        } catch(IOException e) { throw new RuntimeException(e); }
    }

    static final boolean debug = true;

    static void debug(Object message) { if (debug) System.out.println(message); }

    /* create the index */
    static void doTest(String workingDir) {
        // ProcessBuilder is used so that the current directory can be set
        // to the directory that directly contains the jars.
        debug("Running java -cp e.jar:f.jar e.Main");
        ProcessBuilder pb = new ProcessBuilder(
           java, "-cp", "e.jar:f.jar", "e.Main");
        pb.directory(new File(workingDir));
        //pd.inheritIO();
        try {
            Process p = pb.start();
            if(p.waitFor() != 0)
                throw new RuntimeException("run java failed");

            if(p != null) {
                String line = null;
                BufferedReader reader =
                         new BufferedReader(new InputStreamReader(p.getInputStream()));
                String output = "";
                while((line = reader.readLine()) != null)
                    output += line;

		debug(output);

		if (output.contains("There are 2 resouce files") == false) {
                    throw new RuntimeException("can't get the 2 instances.");
                }

                reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                while((line = reader.readLine()) != null)
                    debug(line);
            }
        } catch(InterruptedException ie) { throw new RuntimeException(ie);
        } catch(IOException e) { throw new RuntimeException(e); }
    }
}
