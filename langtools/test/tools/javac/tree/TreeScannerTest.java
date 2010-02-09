/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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


/**
 * Utility and test program to check javac's internal TreeScanner class.
 * The program can be run standalone, or as a jtreg test.  For info on
 * command line args, run program with no args.
 *
 * <p>
 * jtreg: Note that by using the -r switch in the test description below, this test
 * will process all java files in the langtools/test directory, thus implicitly
 * covering any new language features that may be tested in this test suite.
 */

/*
 * @test
 * @bug 6923080
 * @summary TreeScanner.visitNewClass should scan tree.typeargs
 * @run main TreeScannerTest -q -r .
 */

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import javax.tools.*;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.List;

public class TreeScannerTest {
    /**
     * Main entry point.
     * If test.src is set, program runs in jtreg mode, and will throw an Error
     * if any errors arise, otherwise System.exit will be used. In jtreg mode,
     * the default base directory for file args is the value of ${test.src}.
     * In jtreg mode, the -r option can be given to change the default base
     * directory to the root test directory.
     */
    public static void main(String... args) {
        String testSrc = System.getProperty("test.src");
        File baseDir = (testSrc == null) ? null : new File(testSrc);
        boolean ok = new TreeScannerTest().run(baseDir, args);
        if (!ok) {
            if (testSrc != null)  // jtreg mode
                throw new Error("failed");
            else
                System.exit(1);
        }
    }

    /**
     * Run the program. A base directory can be provided for file arguments.
     * In jtreg mode, the -r option can be given to change the default base
     * directory to the test root directory. For other options, see usage().
     * @param baseDir base directory for any file arguments.
     * @param args command line args
     * @return true if successful or in gui mode
     */
    boolean run(File baseDir, String... args) {
        if (args.length == 0) {
            usage(System.out);
            return true;
        }

        ArrayList<File> files = new ArrayList<File>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-q"))
                quiet = true;
            else if (arg.equals("-v"))
                verbose = true;
            else if (arg.equals("-r")) {
                File d = baseDir;
                while (!new File(d, "TEST.ROOT").exists()) {
                    d = d.getParentFile();
                    if (d == null)
                        throw new Error("cannot find TEST.ROOT");
                }
                baseDir = d;
            }
            else if (arg.startsWith("-"))
                throw new Error("unknown option: " + arg);
            else {
                while (i < args.length)
                    files.add(new File(baseDir, args[i++]));
            }
        }

        for (File file: files) {
            if (file.exists())
                test(file);
            else
                error("File not found: " + file);
        }

        if (fileCount != 1)
            System.err.println(fileCount + " files read");
        if (errors > 0)
            System.err.println(errors + " errors");

        return (errors == 0);
    }

    /**
     * Print command line help.
     * @param out output stream
     */
    void usage(PrintStream out) {
        out.println("Usage:");
        out.println("  java TreeScannerTest options... files...");
        out.println("");
        out.println("where options include:");
        out.println("-q        Quiet: don't report on inapplicable files");
        out.println("-v        Verbose: report on files as they are being read");
        out.println("");
        out.println("files may be directories or files");
        out.println("directories will be scanned recursively");
        out.println("non java files, or java files which cannot be parsed, will be ignored");
        out.println("");
    }

    /**
     * Test a file. If the file is a directory, it will be recursively scanned
     * for java files.
     * @param file the file or directory to test
     */
    void test(File file) {
        if (file.isDirectory()) {
            for (File f: file.listFiles()) {
                test(f);
            }
            return;
        }

        if (file.isFile() && file.getName().endsWith(".java")) {
            try {
                if (verbose)
                    System.err.println(file);
                fileCount++;
                ScanTester t = new ScanTester();
                t.test(read(file));
            } catch (ParseException e) {
                if (!quiet) {
                    error("Error parsing " + file + "\n" + e.getMessage());
                }
            } catch (IOException e) {
                error("Error reading " + file + ": " + e);
            }
            return;
        }

        if (!quiet)
            error("File " + file + " ignored");
    }

    /**
     * Read a file.
     * @param file the file to be read
     * @return the tree for the content of the file
     * @throws IOException if any IO errors occur
     * @throws TreePosTest.ParseException if any errors occur while parsing the file
     */
    JCCompilationUnit read(File file) throws IOException, ParseException {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Reporter r = new Reporter(pw);
        JavacTool tool = JavacTool.create();
        StandardJavaFileManager fm = tool.getStandardFileManager(r, null, null);
        Iterable<? extends JavaFileObject> files = fm.getJavaFileObjects(file);
        JavacTask task = tool.getTask(pw, fm, r, Collections.<String>emptyList(), null, files);
        Iterable<? extends CompilationUnitTree> trees = task.parse();
        pw.flush();
        if (r.errors > 0)
            throw new ParseException(sw.toString());
        Iterator<? extends CompilationUnitTree> iter = trees.iterator();
        if (!iter.hasNext())
            throw new Error("no trees found");
        JCCompilationUnit t = (JCCompilationUnit) iter.next();
        if (iter.hasNext())
            throw new Error("too many trees found");
        return t;
    }

    /**
     * Report an error. When the program is complete, the program will either
     * exit or throw an Error if any errors have been reported.
     * @param msg the error message
     */
    void error(String msg) {
        System.err.println(msg);
        errors++;
    }

    /**
     *  Report an error for a specific tree node.
     *  @param file the source file for the tree
     *  @param t    the tree node
     *  @param label an indication of the error
     */
    void error(JavaFileObject file, JCTree t, String msg) {
        error(file.getName() + ":" + getLine(file, t) + ": " + msg + " " + trim(t, 64));
    }

    /**
     * Get a trimmed string for a tree node, with normalized white space and limited length.
     */
    String trim(JCTree t, int len) {
        String s = t.toString().replaceAll("[\r\n]+", " ").replaceAll(" +", " ");
        return (s.length() < len) ? s : s.substring(0, len);
    }

    /** Number of files that have been analyzed. */
    int fileCount;
    /** Number of errors reported. */
    int errors;
    /** Flag: don't report irrelevant files. */
    boolean quiet;
    /** Flag: report files as they are processed. */
    boolean verbose;

    /**
     * Main class for testing operation of tree scanner.
     * The set of nodes found by the scanner are compared
     * against the set of nodes found by reflection.
     */
    private class ScanTester extends TreeScanner {
        /** Main entry method for the class. */
        void test(JCCompilationUnit tree) {
            sourcefile = tree.sourcefile;
            found = new HashSet<JCTree>();
            scan(tree);
            expect = new HashSet<JCTree>();
            reflectiveScan(tree);
            if (found.equals(expect))
                return;

            error("Differences found for " + tree.sourcefile.getName());

            if (found.size() != expect.size())
                error("Size mismatch; found: " + found.size() + ", expected: " + expect.size());

            Set<JCTree> missing = new HashSet<JCTree>();
            missing.addAll(expect);
            missing.removeAll(found);
            for (JCTree t: missing)
                error(tree.sourcefile, t, "missing");

            Set<JCTree> excess = new HashSet<JCTree>();
            excess.addAll(found);
            excess.removeAll(expect);
            for (JCTree t: excess)
                error(tree.sourcefile, t, "unexpected");
        }

        /** Record all tree nodes found by scanner. */
        @Override
        public void scan(JCTree tree) {
            if (tree == null)
                return;
            System.err.println("FOUND: " + tree.getTag() + " " + trim(tree, 64));
            found.add(tree);
            super.scan(tree);
        }

        /** record all tree nodes found by reflection. */
        public void reflectiveScan(Object o) {
            if (o == null)
                return;
            if (o instanceof JCTree) {
                JCTree tree = (JCTree) o;
                System.err.println("EXPECT: " + tree.getTag() + " " + trim(tree, 64));
                expect.add(tree);
                for (Field f: getFields(tree)) {
                    try {
                        //System.err.println("FIELD: " + f.getName());
                        reflectiveScan(f.get(tree));
                    } catch (IllegalAccessException e) {
                        error(e.toString());
                    }
                }
            } else if (o instanceof List) {
                List<?> list = (List<?>) o;
                for (Object item: list)
                    reflectiveScan(item);
            } else
                error("unexpected item: " + o);
        }

        JavaFileObject sourcefile;
        Set<JCTree> found;
        Set<JCTree> expect;
    }

    /**
     * Thrown when errors are found parsing a java file.
     */
    private static class ParseException extends Exception {
        ParseException(String msg) {
            super(msg);
        }
    }

    /**
     * DiagnosticListener to report diagnostics and count any errors that occur.
     */
    private static class Reporter implements DiagnosticListener<JavaFileObject> {
        Reporter(PrintWriter out) {
            this.out = out;
        }

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            out.println(diagnostic);
            switch (diagnostic.getKind()) {
                case ERROR:
                    errors++;
            }
        }
        int errors;
        PrintWriter out;
    }

    /**
     * Get the set of fields for a tree node that may contain child tree nodes.
     * These are the fields that are subtypes of JCTree or List.
     * The results are cached, based on the tree's tag.
     */
    Set<Field> getFields(JCTree tree) {
        Set<Field> fields = map.get(tree.getTag());
        if (fields == null) {
            fields = new HashSet<Field>();
            for (Field f: tree.getClass().getFields()) {
                Class<?> fc = f.getType();
                if (JCTree.class.isAssignableFrom(fc) || List.class.isAssignableFrom(fc))
                    fields.add(f);
            }
            map.put(tree.getTag(), fields);
        }
        return fields;
    }
    // where
    Map<Integer, Set<Field>> map = new HashMap<Integer,Set<Field>>();

    /** Get the line number for the primary position for a tree.
     * The code is intended to be simple, although not necessarily efficient.
     * However, note that a file manager such as JavacFileManager is likely
     * to cache the results of file.getCharContent, avoiding the need to read
     * the bits from disk each time this method is called.
     */
    int getLine(JavaFileObject file, JCTree tree) {
        try {
            CharSequence cs = file.getCharContent(true);
            int line = 1;
            for (int i = 0; i < tree.pos; i++) {
                if (cs.charAt(i) == '\n') // jtreg tests always use Unix line endings
                    line++;
            }
            return line;
        } catch (IOException e) {
            return -1;
        }
    }
}
