/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/**
 *  @test
 *  @bug 8001098 8004961
 *  @summary Provide a simple light-weight "plug-in" mechanism for javac
 */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

public class Test {
    public static void main(String... args) throws Exception {
        new Test().run();
    }

    final File testSrc;
    final File pluginSrc;
    final File pluginClasses ;
    final File pluginJar;
    final List<String> ref1;
    final List<String> ref2;
    final JavaCompiler compiler;
    final StandardJavaFileManager fm;

    Test() throws Exception {
        testSrc = new File(System.getProperty("test.src"));
        pluginSrc = new File(testSrc, "ShowTypePlugin.java");
        pluginClasses = new File("plugin");
        pluginJar = new File("plugin.jar");
        ref1 = readFile(testSrc, "Identifiers.out");
        ref2 = readFile(testSrc, "Identifiers_PI.out");
        compiler = ToolProvider.getSystemJavaCompiler();
        fm = compiler.getStandardFileManager(null, null, null);
    }

    void run() throws Exception {
        // compile the plugin explicitly, to a non-standard directory
        // so that we don't find it on the wrong path by accident
        pluginClasses.mkdirs();
        compile("-d", pluginClasses.getPath(), pluginSrc.getPath());
        writeFile(new File(pluginClasses, "META-INF/services/com.sun.source.util.Plugin"),
                "ShowTypePlugin\n");
        jar("cf", pluginJar.getPath(), "-C", pluginClasses.getPath(), ".");

        testCommandLine("-Xplugin:showtype", ref1);
        testCommandLine("-Xplugin:showtype PI", ref2);
        testAPI("-Xplugin:showtype", ref1);
        testAPI("-Xplugin:showtype PI", ref2);

        if (errors > 0)
            throw new Exception(errors + " errors occurred");
    }

    void testAPI(String opt, List<String> ref) throws Exception {
        File identifiers = new File(testSrc, "Identifiers.java");
        fm.setLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH, Arrays.asList(pluginJar));
        fm.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(new File(".")));
        List<String> options = Arrays.asList(opt);
        Iterable<? extends JavaFileObject> files = fm.getJavaFileObjects(identifiers);

        System.err.println("test api: " + options + " " + files);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        boolean ok = compiler.getTask(pw, fm, null, options, null, files).call();
        String out = sw.toString();
        System.err.println(out);
        if (!ok)
            error("testCommandLine: compilation failed");
        checkOutput(out, ref);
    }

    void testCommandLine(String opt, List<String> ref) {
        File identifiers = new File(testSrc, "Identifiers.java");
        String[] args = {
            "-d", ".",
            "-processorpath", pluginJar.getPath(),
            opt,
            identifiers.getPath() };

        System.err.println("test command line: " + Arrays.asList(args));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javac.Main.compile(args, pw);
        String out = sw.toString();
        System.err.println(out);
        if (rc != 0)
            error("testCommandLine: compilation failed");
        checkOutput(out, ref);
    }

    private void checkOutput(String out, List<String> ref) {
        List<String> lines = Arrays.asList(out
                .replaceAll(".*?([A-Za-z.]+:[0-9]+: .*)", "$1") // remove file directory
                .split("[\r\n]+"));                             // allow for newline formats
        if (!lines.equals(ref)) {
            error("unexpected output");
        }
    }

    private void compile(String... args) throws Exception {
        System.err.println("compile: " + Arrays.asList(args));
        int rc = com.sun.tools.javac.Main.compile(args);
        if (rc != 0)
            throw new Exception("compiled failed, rc=" + rc);
    }

    private void jar(String... args) throws Exception {
        System.err.println("jar: " + Arrays.asList(args));
        boolean ok = new sun.tools.jar.Main(System.out, System.err, "jar").run(args);
        if (!ok)
            throw new Exception("jar failed");
    }

    private List<String> readFile(File dir, String name) throws IOException {
        return Files.readAllLines(new File(dir, name).toPath(), Charset.defaultCharset());
    }

    private void writeFile(File f, String body) throws IOException {
        f.getParentFile().mkdirs();
        try (FileWriter out = new FileWriter(f)) {
            out.write(body);
        }
    }

    private void error(String msg) {
        System.err.println(msg);
        errors++;
    }

    int errors;
}
