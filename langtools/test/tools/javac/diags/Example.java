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

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.regex.*;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

// The following two classes are both used, but cannot be imported directly
// import com.sun.tools.javac.Main
// import com.sun.tools.javac.main.Main

import com.sun.tools.javac.api.ClientCodeWrapper;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.JCDiagnostic;

/**
 * Class to handle example code designed to illustrate javac diagnostic messages.
 */
class Example implements Comparable<Example> {
    /* Create an Example from the files found at path.
     * The head of the file, up to the first Java code, is scanned
     * for information about the test, such as what resource keys it
     * generates when run, what options are required to run it, and so on.
     */
    Example(File file) {
        this.file = file;
        declaredKeys = new TreeSet<String>();
        srcFiles = new ArrayList<File>();
        procFiles = new ArrayList<File>();
        supportFiles = new ArrayList<File>();
        srcPathFiles = new ArrayList<File>();

        findFiles(file, srcFiles);
        for (File f: srcFiles) {
            parse(f);
        }

        if (infoFile == null)
            throw new Error("Example " + file + " has no info file");
    }

    private void findFiles(File f, List<File> files) {
        if (f.isDirectory()) {
            for (File c: f.listFiles()) {
                if (files == srcFiles && c.getName().equals("processors"))
                    findFiles(c, procFiles);
                else if (files == srcFiles && c.getName().equals("sourcepath")) {
                    srcPathDir = c;
                    findFiles(c, srcPathFiles);
                } else if (files == srcFiles && c.getName().equals("support"))
                    findFiles(c, supportFiles);
                else
                    findFiles(c, files);
            }
        } else if (f.isFile() && f.getName().endsWith(".java")) {
            files.add(f);
        }
    }

    private void parse(File f) {
        Pattern keyPat = Pattern.compile(" *// *key: *([^ ]+) *");
        Pattern optPat = Pattern.compile(" *// *options: *(.*)");
        Pattern runPat = Pattern.compile(" *// *run: *(.*)");
        Pattern javaPat = Pattern.compile(" *@?[A-Za-z].*");
        try {
            String[] lines = read(f).split("[\r\n]+");
            for (String line: lines) {
                Matcher keyMatch = keyPat.matcher(line);
                if (keyMatch.matches()) {
                    foundInfo(f);
                    declaredKeys.add(keyMatch.group(1));
                    continue;
                }
                Matcher optMatch = optPat.matcher(line);
                if (optMatch.matches()) {
                    foundInfo(f);
                    options = Arrays.asList(optMatch.group(1).trim().split(" +"));
                    continue;
                }
                Matcher runMatch = runPat.matcher(line);
                if (runMatch.matches()) {
                    foundInfo(f);
                    runOpts = Arrays.asList(runMatch.group(1).trim().split(" +"));
                }
                if (javaPat.matcher(line).matches())
                    break;
            }
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private void foundInfo(File file) {
        if (infoFile != null && !infoFile.equals(file))
            throw new Error("multiple info files found: " + infoFile + ", " + file);
        infoFile = file;
    }

    String getName() {
        return file.getName();
    }

    /**
     * Get the set of resource keys that this test declares it will generate
     * when it is run.
     */
    Set<String> getDeclaredKeys() {
        return declaredKeys;
    }

    /**
     * Get the set of resource keys that this test generates when it is run.
     * The test will be run if it has not already been run.
     */
    Set<String> getActualKeys() {
        if (actualKeys == null)
            actualKeys = run(false);
        return actualKeys;
    }

    /**
     * Run the test.  Information in the test header is used to determine
     * how to run the test.
     */
    void run(PrintWriter out, boolean raw, boolean verbose) {
        if (out == null)
            throw new NullPointerException();
        try {
            run(out, null, raw, verbose);
        } catch (IOException e) {
            e.printStackTrace(out);
        }
    }

    Set<String> run(boolean verbose) {
        Set<String> keys = new TreeSet<String>();
        try {
            run(null, keys, true, verbose);
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
        return keys;
    }

    /**
     * Run the test.  Information in the test header is used to determine
     * how to run the test.
     */
    private void run(PrintWriter out, Set<String> keys, boolean raw, boolean verbose)
            throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        if (supportFiles.size() > 0) {
            File supportDir = new File(tempDir, "support");
            supportDir.mkdirs();
            clean(supportDir);
            List<String> sOpts = Arrays.asList("-d", supportDir.getPath());
            new Jsr199Compiler(verbose).run(null, null, false, sOpts, procFiles);
            URLClassLoader ucl =
                    new URLClassLoader(new URL[] { supportDir.toURI().toURL() }, loader);
            loader = ucl;
        }

        File classesDir = new File(tempDir, "classes");
        classesDir.mkdirs();
        clean(classesDir);

        List<String> opts = new ArrayList<String>();
        opts.add("-d");
        opts.add(classesDir.getPath());
        if (options != null)
            opts.addAll(options);

        if (procFiles.size() > 0) {
            List<String> pOpts = Arrays.asList("-d", classesDir.getPath());
            new Jsr199Compiler(verbose).run(null, null, false, pOpts, procFiles);
            opts.add("-classpath"); // avoid using -processorpath for now
            opts.add(classesDir.getPath());
            createAnnotationServicesFile(classesDir, procFiles);
        } else if (options != null) {
            int i = options.indexOf("-processor");
            // check for built-in anno-processor(s)
            if (i != -1 && options.get(i + 1).equals("DocCommentProcessor")) {
                opts.add("-classpath");
                opts.add(System.getProperty("test.classes"));
            }
        }

        if (srcPathDir != null) {
            opts.add("-sourcepath");
            opts.add(srcPathDir.getPath());
        }

        try {
            Compiler c = Compiler.getCompiler(runOpts, verbose);
            c.run(out, keys, raw, opts, srcFiles);
        } catch (IllegalArgumentException e) {
            if (out != null) {
                out.println("Invalid value for run tag: " + runOpts);
            }
        }
    }

    void createAnnotationServicesFile(File dir, List<File> procFiles) throws IOException {
        File servicesDir = new File(new File(dir, "META-INF"), "services");
        servicesDir.mkdirs();
        File annoServices = new File(servicesDir, Processor.class.getName());
        Writer out = new FileWriter(annoServices);
        try {
            for (File f: procFiles) {
                out.write(f.getName().toString().replace(".java", ""));
            }
        } finally {
            out.close();
        }
    }

    @Override
    public int compareTo(Example e) {
        return file.compareTo(e.file);
    }

    @Override
    public String toString() {
        return file.getPath();
    }

    /**
     * Read the contents of a file.
     */
    private String read(File f) throws IOException {
        byte[] bytes = new byte[(int) f.length()];
        DataInputStream in = new DataInputStream(new FileInputStream(f));
        try {
            in.readFully(bytes);
        } finally {
            in.close();
        }
        return new String(bytes);
    }

    /**
     * Clean the contents of a directory.
     */
    boolean clean(File dir) {
        boolean ok = true;
        for (File f: dir.listFiles()) {
            if (f.isDirectory())
                ok &= clean(f);
            ok &= f.delete();
        }
        return ok;
    }

    File file;
    List<File> srcFiles;
    List<File> procFiles;
    File srcPathDir;
    List<File> srcPathFiles;
    List<File> supportFiles;
    File infoFile;
    private List<String> runOpts;
    private List<String> options;
    private Set<String> actualKeys;
    private Set<String> declaredKeys;

    static File tempDir = new File(System.getProperty("java.io.tmpdir"));
    static void setTempDir(File tempDir) {
        Example.tempDir = tempDir;
    }

    abstract static class Compiler {
        interface Factory {
            Compiler getCompiler(List<String> opts, boolean verbose);
        }

        static class DefaultFactory implements Factory {
            public Compiler getCompiler(List<String> opts, boolean verbose) {
            String first;
            String[] rest;
                if (opts == null || opts.isEmpty()) {
                first = null;
                rest = new String[0];
            } else {
                first = opts.get(0);
                rest = opts.subList(1, opts.size()).toArray(new String[opts.size() - 1]);
            }
            if (first == null || first.equals("jsr199"))
                return new Jsr199Compiler(verbose, rest);
            else if (first.equals("simple"))
                return new SimpleCompiler(verbose);
            else if (first.equals("backdoor"))
                return new BackdoorCompiler(verbose);
            else
                throw new IllegalArgumentException(first);
                }
        }

        static Factory factory;

        static Compiler getCompiler(List<String> opts, boolean verbose) {
            if (factory == null)
                factory = new DefaultFactory();

            return factory.getCompiler(opts, verbose);
        }

        protected Compiler(boolean verbose) {
            this.verbose = verbose;
        }

        abstract boolean run(PrintWriter out, Set<String> keys, boolean raw,
                List<String> opts,  List<File> files);

        void setSupportClassLoader(ClassLoader cl) {
            loader = cl;
        }

        protected ClassLoader loader;
        protected boolean verbose;
    }

    /**
     * Compile using the JSR 199 API.  The diagnostics generated are
     * scanned for resource keys.   Not all diagnostic keys are generated
     * via the JSR 199 API -- for example, rich diagnostics are not directly
     * accessible, and some diagnostics generated by the file manager may
     * not be generated (for example, the JSR 199 file manager does not see
     * -Xlint:path).
     */
    static class Jsr199Compiler extends Compiler {
        List<String> fmOpts;

        Jsr199Compiler(boolean verbose, String... args) {
            super(verbose);
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("-filemanager") && (i + 1 < args.length)) {
                    fmOpts = Arrays.asList(args[++i].split(","));
                } else
                    throw new IllegalArgumentException(arg);
            }
        }

        @Override
        boolean run(PrintWriter out, Set<String> keys, boolean raw, List<String> opts, List<File> files) {
            if (out != null && keys != null)
                throw new IllegalArgumentException();

            if (verbose)
                System.err.println("run_jsr199: " + opts + " " + files);

            DiagnosticCollector<JavaFileObject> dc = null;
            if (keys != null)
                dc = new DiagnosticCollector<JavaFileObject>();

            if (raw) {
                List<String> newOpts = new ArrayList<String>();
                newOpts.add("-XDrawDiagnostics");
                newOpts.addAll(opts);
                opts = newOpts;
            }

            JavaCompiler c = ToolProvider.getSystemJavaCompiler();

            StandardJavaFileManager fm = c.getStandardFileManager(dc, null, null);
            if (fmOpts != null)
                fm = new FileManager(fm, fmOpts);

            Iterable<? extends JavaFileObject> fos = fm.getJavaFileObjectsFromFiles(files);

            CompilationTask t = c.getTask(out, fm, dc, opts, null, fos);
            Boolean ok = t.call();

            if (keys != null) {
                for (Diagnostic<? extends JavaFileObject> d: dc.getDiagnostics()) {
                    scanForKeys(unwrap(d), keys);
                }
            }

            return ok;
        }

        /**
         * Scan a diagnostic for resource keys.  This will not detect additional
         * sub diagnostics that might be generated by a rich diagnostic formatter.
         */
        private static void scanForKeys(JCDiagnostic d, Set<String> keys) {
            keys.add(d.getCode());
            for (Object o: d.getArgs()) {
                if (o instanceof JCDiagnostic) {
                    scanForKeys((JCDiagnostic) o, keys);
                }
            }
            for (JCDiagnostic sd: d.getSubdiagnostics())
                scanForKeys(sd, keys);
        }

        private JCDiagnostic unwrap(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic instanceof JCDiagnostic)
                return (JCDiagnostic) diagnostic;
            if (diagnostic instanceof ClientCodeWrapper.DiagnosticSourceUnwrapper)
                return ((ClientCodeWrapper.DiagnosticSourceUnwrapper)diagnostic).d;
            throw new IllegalArgumentException();
        }
    }

    /**
     * Run the test using the standard simple entry point.
     */
    static class SimpleCompiler extends Compiler {
        SimpleCompiler(boolean verbose) {
            super(verbose);
        }

        @Override
        boolean run(PrintWriter out, Set<String> keys, boolean raw, List<String> opts, List<File> files) {
            if (out != null && keys != null)
                throw new IllegalArgumentException();

            if (verbose)
                System.err.println("run_simple: " + opts + " " + files);

            List<String> args = new ArrayList<String>();

            if (keys != null || raw)
                args.add("-XDrawDiagnostics");

            args.addAll(opts);
            for (File f: files)
                args.add(f.getPath());

            StringWriter sw = null;
            PrintWriter pw;
            if (keys != null) {
                sw = new StringWriter();
                pw = new PrintWriter(sw);
            } else
                pw = out;

            int rc = com.sun.tools.javac.Main.compile(args.toArray(new String[args.size()]), pw);

            if (keys != null) {
                pw.close();
                scanForKeys(sw.toString(), keys);
            }

            return (rc == 0);
        }

        private static void scanForKeys(String text, Set<String> keys) {
            StringTokenizer st = new StringTokenizer(text, " ,\r\n():");
            while (st.hasMoreElements()) {
                String t = st.nextToken();
                if (t.startsWith("compiler."))
                    keys.add(t);
            }
        }
    }

    static class BackdoorCompiler extends Compiler {
        BackdoorCompiler(boolean verbose) {
            super(verbose);
        }

        @Override
        boolean run(PrintWriter out, Set<String> keys, boolean raw, List<String> opts, List<File> files) {
            if (out != null && keys != null)
                throw new IllegalArgumentException();

            if (verbose)
                System.err.println("run_simple: " + opts + " " + files);

            List<String> args = new ArrayList<String>();

            if (out != null && raw)
                args.add("-XDrawDiagnostics");

            args.addAll(opts);
            for (File f: files)
                args.add(f.getPath());

            StringWriter sw = null;
            PrintWriter pw;
            if (keys != null) {
                sw = new StringWriter();
                pw = new PrintWriter(sw);
            } else
                pw = out;

            Context c = new Context();
            JavacFileManager.preRegister(c); // can't create it until Log has been set up
            MessageTracker.preRegister(c, keys);
            Main m = new Main("javac", pw);
            Main.Result rc = m.compile(args.toArray(new String[args.size()]), c);

            if (keys != null) {
                pw.close();
            }

            return rc.isOK();
        }

        static class MessageTracker extends JavacMessages {

            MessageTracker(Context context) {
                super(context);
            }

            static void preRegister(Context c, final Set<String> keys) {
                if (keys != null) {
                    c.put(JavacMessages.messagesKey, new Context.Factory<JavacMessages>() {
                        public JavacMessages make(Context c) {
                            return new MessageTracker(c) {
                                @Override
                                public String getLocalizedString(Locale l, String key, Object... args) {
                                    keys.add(key);
                                    return super.getLocalizedString(l, key, args);
                                }
                            };
                        }
                    });
                }
            }
        }

    }
}
