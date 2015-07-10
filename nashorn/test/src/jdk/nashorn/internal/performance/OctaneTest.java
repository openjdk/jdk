/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.performance;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OctaneTest {

    @Test
    public void box2DTest() {
        genericTest("Box2D");
    }

    @Test
    public void codeLoadTest() {
        genericTest("Code-Load");
    }

    @Test
    public void cryptoTest() {
        genericTest("Crypto");
    }

    @Test
    public void deltaBlueTest() {
        genericTest("DeltaBlue");
    }

    @Test
    public void earleyBoyerTest() {
    genericTest("Earley-Boyer");
    }

    @Test
    public void gbEMUTest() {
        genericTest("GBEMU");
    }

/*    @Test
    public void mandreelTest() {
        genericTest("Mandreel");
    }*/

    @Test
    public void navierStokesTest() {
        genericTest("Navier-Stokes");
    }

    @Test
    public void pdfJSTest() {
        genericTest("PDFJS");
    }

    @Test
    public void raytraceTest() {
        genericTest("RayTrace");
    }

    @Test
    public void regexpTest() {
        genericTest("RegExp");
    }

    @Test
    public void richardsTest() {
        genericTest("Richards");
    }

    @Test
    public void splayTest() {
        genericTest("Splay");
    }

    @Test
/*    public void typeScriptTest() {
        genericTest("TypeScript");
    }

    @Test
    public void zlibTest() {
        genericTest("zlib");
    }/*/

    public void genericTest(final String benchmark) {
        try {
            final String mainScript      = "test/script/basic/run-octane.js";
            final String benchmarkScript = "test/script/external/octane/benchmarks/" + benchmark.toLowerCase() + ".js";
            final String[] args = {
                "--",
                benchmarkScript,
                "--verbose"
            };
            final Double score = genericNashornTest(benchmark, mainScript, args);

            Double rhinoScore = null;
            if (checkRhinoPresence()) {
                args[0]=mainScript; //rhino does not need this "--"
                rhinoScore = genericRhinoTest(benchmark,  args, System.getProperty("rhino.jar"));
            }

            Double v8Score = null;
            if (checkV8Presence()) {
                v8Score = genericV8Test(benchmark, "test/script/basic/run-octane.js -- test/script/external/octane/benchmarks/" + benchmark.toLowerCase() + ".js --verbose");
            }

            generateOutput(benchmark.toLowerCase(), score, rhinoScore, v8Score);

        } catch (final Throwable e) {
            e.printStackTrace();
        }
    }

    public Double genericNashornTest(final String benchmark, final String testPath, final String[] args) throws Throwable {
        try {
            final PerformanceWrapper wrapper = new PerformanceWrapper();

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final PrintStream ps = new PrintStream(baos);

            final java.io.File test=new java.io.File(testPath);
            final File absoluteFile=test.getAbsoluteFile();
            @SuppressWarnings("deprecation")
            final
            URL testURL=absoluteFile.toURL();

            wrapper.runExecuteOnlyTest(testPath, 0, 0, testURL.toString(), ps, System.err, args);

            final byte[] output = baos.toByteArray();
            final List<String> result = outputToStrings(output);

            final Double _result = filterBenchmark(result, benchmark);

            return _result;
        } catch (final Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    public Double genericRhinoTest(final String benchmark, final String[] testPath, final String jarPath) throws Throwable {

        final PrintStream systemOut = System.out;

        try {
            final ClassLoader loader = java.net.URLClassLoader.newInstance(new URL[] { new URL(jarPath) }, getClass().getClassLoader());
            final Class<?> clazz = Class.forName("org.mozilla.javascript.tools.shell.Main", true, loader);

            final Class<?>[] pars = { String[].class };
            final Method mthd = clazz.getMethod("main", pars);

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final PrintStream ps = new PrintStream(baos);

            System.setOut(ps);
            //final String[] realArgs = testPath.split(" ");//{ testPath };
            mthd.invoke(null, ((Object)testPath));
            System.setOut(systemOut);

            final byte[] output = baos.toByteArray();
            final List<String> result = outputToStrings(output);
            return filterBenchmark(result, benchmark);

        } catch (final Throwable e) {
            System.setOut(systemOut);
            e.printStackTrace();
            throw e;
        }
    }

    public Double genericV8Test(final String benchmark, final String testPath) throws Throwable {

        System.out.println("genericV8Test");
        if (!checkV8Presence()) {
            return null;
        }
        final String v8shell = System.getProperty("v8.shell.full.path");
        final PrintStream systemOut = System.out;

        try {

            final Process process = Runtime.getRuntime().exec(v8shell + " " + testPath);
            process.waitFor();
            final InputStream processOut = process.getInputStream();
            final BufferedInputStream bis = new BufferedInputStream(processOut);

            final byte[] output = new byte[bis.available()];
            bis.read(output, 0, bis.available());
            final List<String> result = outputToStrings(output);
            return filterBenchmark(result, benchmark);

        } catch (final Throwable e) {
            System.setOut(systemOut);
            e.printStackTrace();
            throw e;
        }
    }

    protected List<String> outputToStrings(final byte[] output) throws IOException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(output);
        final InputStreamReader reader = new InputStreamReader(bais);
        final BufferedReader bufReader = new BufferedReader(reader);
        final List<String> list = new java.util.LinkedList<>();
        while (bufReader.ready()) {
            list.add(bufReader.readLine());
        }
        return list;
    }

    protected Double filterBenchmark(final List<String> output, final String benchmarkName) throws Exception {
        Double currentScore = 0.;
        for (final String s : output) {
            //if (s.trim().startsWith(benchmarkName)) {
            if (s.trim().startsWith("Score")) {
                final String[] split = s.split(":");
                if (split.length != 2) {
                    for (final String outString : output) {
                        System.out.println("outString (score format)"+outString);
                    }
                    throw new IllegalArgumentException("Invalid benchmark output format");
                }

                final NumberFormat nf = NumberFormat.getInstance();
                final Number _newCurrentScore = nf.parse(split[1].trim());
                final Double newCurrentScore = _newCurrentScore.doubleValue();
                if (currentScore < newCurrentScore) {
                    currentScore = newCurrentScore;
                }
            }
        }
//        System.out.println("filterBenchmark current score:"+currentScore);
        return currentScore;
    }

    void generateOutput(final String benchmark, final Double nashorn, final Double rhino, final Double v8) throws Exception {
        Double nashornToRhino = null;
        Double nashornToV8 = null;
        if (rhino != null && rhino != 0) {
            nashornToRhino = nashorn / rhino;
        }
        if (v8 != null && rhino != 0) {
            nashornToV8 = nashorn / v8;
        }
        final String normalizedBenchmark=benchmark.replace("-", "");
        System.out.println("benchmark-" + normalizedBenchmark + "-nashorn=" + nashorn);
        AuroraWrapper.addResults(AuroraWrapper.createOrOpenDocument(), "benchmark-" + normalizedBenchmark + "-nashorn", nashorn.toString());

        if (rhino != null) {
            System.out.println("benchmark-" + normalizedBenchmark + "-rhino=" + rhino);
            AuroraWrapper.addResults(AuroraWrapper.createOrOpenDocument(), "benchmark-" + normalizedBenchmark + "-rhino", rhino.toString());
        }
        if (v8 != null) {
            AuroraWrapper.addResults(AuroraWrapper.createOrOpenDocument(), "benchmark-" + normalizedBenchmark + "-v8", v8.toString());
        }
        if (nashornToRhino != null) {
            System.out.println("benchmark-" + normalizedBenchmark + "-nashorn-to-rhino=" + ((float)((int)(nashornToRhino * 100))) / 100);
            AuroraWrapper.addResults(AuroraWrapper.createOrOpenDocument(), "benchmark-" + normalizedBenchmark + "-nashorn-to-rhino", "" + ((float)((int)(nashornToRhino * 100))) / 100);
        }
        if (nashornToV8 != null) {
            System.out.println("benchmark-" + normalizedBenchmark + "-nashorn-to-v8=" + ((float)((int)(nashornToV8 * 100))) / 100);
            AuroraWrapper.addResults(AuroraWrapper.createOrOpenDocument(), "benchmark-" + normalizedBenchmark + "-nashorn-to-v8", "" + ((float)((int)(nashornToV8 * 100))) / 100);
        }
    }

    boolean checkRhinoPresence() {
        final String rhinojar = System.getProperty("rhino.jar");
        return rhinojar != null;
    }

    boolean checkV8Presence() {
        final String v8shell = System.getProperty("v8.shell.full.path");
        return v8shell != null;
    }

}
