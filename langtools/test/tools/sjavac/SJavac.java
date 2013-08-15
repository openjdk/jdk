/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests sjavac basic functionality
 */

import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.*;

import com.sun.tools.sjavac.Main;

public
class SJavac {

    public static void main(String... args) throws Exception {
        SJavac s = new SJavac();
        s.test();
    }

    FileSystem defaultfs = FileSystems.getDefault();

    // Where to put generated sources that will
    // test aspects of sjavac, ie JTWork/scratch/gensrc
    Path gensrc;
    // More gensrc dirs are used to test merging of serveral source roots.
    Path gensrc2;
    Path gensrc3;

    // Where to put compiled classes.
    Path bin;
    // Where to put c-header files.
    Path headers;

    // The sjavac compiler.
    Main main = new Main();

    // Remember the previous bin and headers state here.
    Map<String,Long> previous_bin_state;
    Map<String,Long> previous_headers_state;

    public void test() throws Exception {
        gensrc = defaultfs.getPath("gensrc");
        gensrc2 = defaultfs.getPath("gensrc2");
        gensrc3 = defaultfs.getPath("gensrc3");
        bin = defaultfs.getPath("bin");
        headers = defaultfs.getPath("headers");

        Files.createDirectory(gensrc);
        Files.createDirectory(gensrc2);
        Files.createDirectory(gensrc3);
        Files.createDirectory(bin);
        Files.createDirectory(headers);

        initialCompile();
        incrementalCompileNoChanges();
        incrementalCompileDroppingClasses();
        incrementalCompileWithChange();
        incrementalCompileDropAllNatives();
        incrementalCompileAddNative();
        incrementalCompileChangeNative();
        compileWithOverrideSource();
        compileWithInvisibleSources();
        compileCircularSources();
        compileExcludingDependency();

        delete(gensrc);
        delete(gensrc2);
        delete(gensrc3);
        delete(bin);
        delete(headers);
    }

    void initialCompile() throws Exception {
        System.out.println("\nInitial compile of gensrc.");
        System.out.println("----------------------------");
        populate(gensrc,
            "alfa/AINT.java",
            "package alfa; public interface AINT { void aint(); }",

            "alfa/A.java",
            "package alfa; public class A implements AINT { "+
                 "public final static int DEFINITION = 17; public void aint() { } }",

            "alfa/AA.java",
            "package alfa;"+
            "// A package private class, not contributing to the public api.\n"+
            "class AA {"+
            "   // A properly nested static inner class.\n"+
            "    static class AAA { }\n"+
            "    // A properly nested inner class.\n"+
            "    class AAAA { }\n"+
            "    Runnable foo() {\n"+
            "        // A proper anonymous class.\n"+
            "        return new Runnable() { public void run() { } };\n"+
            "    }\n"+
            "    AAA aaa;\n"+
            "    AAAA aaaa;\n"+
            "    AAAAA aaaaa;\n"+
            "}\n"+
            "class AAAAA {\n"+
            "    // A bad auxiliary class, but no one is referencing it\n"+
            "    // from outside of this source file, therefore it is ok.\n"+
            "}\n",

            "beta/BINT.java",
            "package beta;public interface BINT { void foo(); }",

            "beta/B.java",
            "package beta; import alfa.A; public class B {"+
            "private int b() { return A.DEFINITION; } native void foo(); }");

        compile("gensrc", "-d", "bin", "-h", "headers", "-j", "1",
                "--server:portfile=testserver,background=false", "--log=debug");
        previous_bin_state = collectState(bin);
        previous_headers_state = collectState(headers);
    }

    void incrementalCompileNoChanges() throws Exception {
        System.out.println("\nTesting that no change in sources implies no change in binaries.");
        System.out.println("------------------------------------------------------------------");
        compile("gensrc", "-d", "bin", "-h", "headers", "-j", "1",
                "--server:portfile=testserver,background=false", "--log=debug");
        Map<String,Long> new_bin_state = collectState(bin);
        verifyEqual(new_bin_state, previous_bin_state);
        Map<String,Long> new_headers_state = collectState(headers);
        verifyEqual(previous_headers_state, new_headers_state);
    }

    void incrementalCompileDroppingClasses() throws Exception {
        System.out.println("\nTesting that deleting AA.java deletes all");
        System.out.println("generated inner class as well as AA.class");
        System.out.println("-----------------------------------------");
        removeFrom(gensrc, "alfa/AA.java");
        compile("gensrc", "-d", "bin", "-h", "headers", "-j", "1",
                "--server:portfile=testserver,background=false", "--log=debug");
        Map<String,Long> new_bin_state = collectState(bin);
        verifyThatFilesHaveBeenRemoved(previous_bin_state, new_bin_state,
                                       "bin/alfa/AA$1.class",
                                       "bin/alfa/AA$AAAA.class",
                                       "bin/alfa/AA$AAA.class",
                                       "bin/alfa/AAAAA.class",
                                       "bin/alfa/AA.class");

        previous_bin_state = new_bin_state;
        Map<String,Long> new_headers_state = collectState(headers);
        verifyEqual(previous_headers_state, new_headers_state);
    }

    void incrementalCompileWithChange() throws Exception {
        System.out.println("\nNow update the A.java file with a new timestamps and");
        System.out.println("new final static definition. This should trigger a recompile,");
        System.out.println("not only of alfa, but also beta.");
        System.out.println("But check that the generated native header was not updated!");
        System.out.println("Since we did not modify the native api of B.");
        System.out.println("-------------------------------------------------------------");

        populate(gensrc,"alfa/A.java",
                       "package alfa; public class A implements AINT { "+
                 "public final static int DEFINITION = 18; public void aint() { } private void foo() { } }");

        compile("gensrc", "-d", "bin", "-h", "headers", "-j", "1",
                "--server:portfile=testserver,background=false", "--log=debug");
        Map<String,Long> new_bin_state = collectState(bin);

        verifyNewerFiles(previous_bin_state, new_bin_state,
                         "bin/alfa/A.class",
                         "bin/alfa/AINT.class",
                         "bin/beta/B.class",
                         "bin/beta/BINT.class",
                         "bin/javac_state");
        previous_bin_state = new_bin_state;

        Map<String,Long> new_headers_state = collectState(headers);
        verifyEqual(new_headers_state, previous_headers_state);
    }

    void incrementalCompileDropAllNatives() throws Exception {
        System.out.println("\nNow update the B.java file with one less native method,");
        System.out.println("ie it has no longer any methods!");
        System.out.println("Verify that beta_B.h is removed!");
        System.out.println("---------------------------------------------------------");

        populate(gensrc,"beta/B.java",
                       "package beta; import alfa.A; public class B {"+
                       "private int b() { return A.DEFINITION; } }");

        compile("gensrc", "-d", "bin", "-h", "headers", "-j", "1",
                "--server:portfile=testserver,background=false", "--log=debug");
        Map<String,Long> new_bin_state = collectState(bin);
        verifyNewerFiles(previous_bin_state, new_bin_state,
                         "bin/beta/B.class",
                         "bin/beta/BINT.class",
                         "bin/javac_state");
        previous_bin_state = new_bin_state;

        Map<String,Long> new_headers_state = collectState(headers);
        verifyThatFilesHaveBeenRemoved(previous_headers_state, new_headers_state,
                                       "headers/beta_B.h");
        previous_headers_state = new_headers_state;
    }

    void incrementalCompileAddNative() throws Exception {
        System.out.println("\nNow update the B.java file with a final static annotated with @Native.");
        System.out.println("Verify that beta_B.h is added again!");
        System.out.println("------------------------------------------------------------------------");

        populate(gensrc,"beta/B.java",
                       "package beta; import alfa.A; public class B {"+
                       "private int b() { return A.DEFINITION; } "+
                 "@java.lang.annotation.Native final static int alfa = 42; }");

        compile("gensrc", "-d", "bin", "-h", "headers", "-j", "1",
                "--server:portfile=testserver,background=false", "--log=debug");
        Map<String,Long> new_bin_state = collectState(bin);
        verifyNewerFiles(previous_bin_state, new_bin_state,
                         "bin/beta/B.class",
                         "bin/beta/BINT.class",
                         "bin/javac_state");
        previous_bin_state = new_bin_state;

        Map<String,Long> new_headers_state = collectState(headers);
        verifyThatFilesHaveBeenAdded(previous_headers_state, new_headers_state,
                                     "headers/beta_B.h");
        previous_headers_state = new_headers_state;
    }

    void incrementalCompileChangeNative() throws Exception {
        System.out.println("\nNow update the B.java file with a new value for the final static"+
                           " annotated with @Native.");
        System.out.println("Verify that beta_B.h is rewritten again!");
        System.out.println("-------------------------------------------------------------------");

        populate(gensrc,"beta/B.java",
                       "package beta; import alfa.A; public class B {"+
                       "private int b() { return A.DEFINITION; } "+
                 "@java.lang.annotation.Native final static int alfa = 43; }");

        compile("gensrc", "-d", "bin", "-h", "headers", "-j", "1",
                "--server:portfile=testserver,background=false", "--log=debug");
        Map<String,Long> new_bin_state = collectState(bin);
        verifyNewerFiles(previous_bin_state, new_bin_state,
                         "bin/beta/B.class",
                         "bin/beta/BINT.class",
                         "bin/javac_state");
        previous_bin_state = new_bin_state;

        Map<String,Long> new_headers_state = collectState(headers);
        verifyNewerFiles(previous_headers_state, new_headers_state,
                         "headers/beta_B.h");
        previous_headers_state = new_headers_state;
    }

    void compileWithOverrideSource() throws Exception {
        System.out.println("\nNow verify that we can override sources to be compiled.");
        System.out.println("Compile gensrc and gensrc2. However do not compile broken beta.B in gensrc,");
        System.out.println("only compile ok beta.B in gensrc2.");
        System.out.println("---------------------------------------------------------------------------");

        delete(gensrc);
        delete(gensrc2);
        delete(bin);
        previous_bin_state = collectState(bin);

        populate(gensrc,"alfa/A.java",
                 "package alfa; import beta.B; import gamma.C; public class A { B b; C c; }",
                 "beta/B.java",
                 "package beta; public class B { broken",
                 "gamma/C.java",
                 "package gamma; public class C { }");

        populate(gensrc2,
                 "beta/B.java",
                 "package beta; public class B { }");

        compile("-x", "beta", "gensrc", "gensrc2", "-d", "bin", "-h", "headers", "-j", "1",
                "--server:portfile=testserver,background=false");
        Map<String,Long> new_bin_state = collectState(bin);
        verifyThatFilesHaveBeenAdded(previous_bin_state, new_bin_state,
                                     "bin/alfa/A.class",
                                     "bin/beta/B.class",
                                     "bin/gamma/C.class",
                                     "bin/javac_state");

        System.out.println("----- Compile with exluded beta went well!");
        delete(bin);
        compileExpectFailure("gensrc", "gensrc2", "-d", "bin", "-h", "headers", "-j", "1",
                             "--server:portfile=testserver,background=false");

        System.out.println("----- Compile without exluded beta failed, as expected! Good!");
        delete(bin);
    }

    void compileWithInvisibleSources() throws Exception {
        System.out.println("\nNow verify that we can make sources invisible to linking (sourcepath).");
        System.out.println("Compile gensrc and link against gensrc2 and gensrc3, however");
        System.out.println("gensrc2 contains broken code in beta.B, thus we must exclude that package");
        System.out.println("fortunately gensrc3 contains a proper beta.B.");
        System.out.println("------------------------------------------------------------------------");

        // Start with a fresh gensrcs and bin.
        delete(gensrc);
        delete(gensrc2);
        delete(gensrc3);
        delete(bin);
        previous_bin_state = collectState(bin);

        populate(gensrc,"alfa/A.java",
                 "package alfa; import beta.B; import gamma.C; public class A { B b; C c; }");
        populate(gensrc2,"beta/B.java",
                 "package beta; public class B { broken",
                 "gamma/C.java",
                 "package gamma; public class C { }");
        populate(gensrc3, "beta/B.java",
                 "package beta; public class B { }");

        compile("gensrc", "-x", "beta", "-sourcepath", "gensrc2",
                "-sourcepath", "gensrc3", "-d", "bin", "-h", "headers", "-j", "1",
                "--server:portfile=testserver,background=false");

        System.out.println("The first compile went well!");
        Map<String,Long> new_bin_state = collectState(bin);
        verifyThatFilesHaveBeenAdded(previous_bin_state, new_bin_state,
                                     "bin/alfa/A.class",
                                     "bin/javac_state");

        System.out.println("----- Compile with exluded beta went well!");
        delete(bin);
        compileExpectFailure("gensrc", "-sourcepath", "gensrc2", "-sourcepath", "gensrc3",
                             "-d", "bin", "-h", "headers", "-j", "1",
                             "--server:portfile=testserver,background=false");

        System.out.println("----- Compile without exluded beta failed, as expected! Good!");
        delete(bin);
    }

    void compileCircularSources() throws Exception {
        System.out.println("\nNow verify that circular sources split on multiple cores can be compiled.");
        System.out.println("---------------------------------------------------------------------------");

        // Start with a fresh gensrcs and bin.
        delete(gensrc);
        delete(gensrc2);
        delete(gensrc3);
        delete(bin);
        previous_bin_state = collectState(bin);

        populate(gensrc,"alfa/A.java",
                 "package alfa; public class A { beta.B b; }",
                 "beta/B.java",
                 "package beta; public class B { gamma.C c; }",
                 "gamma/C.java",
                 "package gamma; public class C { alfa.A a; }");

        compile("gensrc", "-d", "bin", "-h", "headers", "-j", "3",
                "--server:portfile=testserver,background=false","--log=debug");
        Map<String,Long> new_bin_state = collectState(bin);
        verifyThatFilesHaveBeenAdded(previous_bin_state, new_bin_state,
                                     "bin/alfa/A.class",
                                     "bin/beta/B.class",
                                     "bin/gamma/C.class",
                                     "bin/javac_state");
        delete(bin);
    }

    /**
     * Tests compiling class A that depends on class B without compiling class B
     * @throws Exception If test fails
     */
    void compileExcludingDependency() throws Exception {
        System.out.println("\nVerify that excluding classes from compilation but not from linking works.");
        System.out.println("---------------------------------------------------------------------------");

        delete(gensrc);
        delete(bin);
        previous_bin_state = collectState(bin);

        populate(gensrc,
                 "alfa/A.java",
                 "package alfa; public class A { beta.B b; }",
                 "beta/B.java",
                 "package beta; public class B { }");

        compile("-x", "beta", "-src", "gensrc", "-x", "alfa", "-sourcepath", "gensrc",
                "-d", "bin", "--server:portfile=testserver,background=false");

        Map<String,Long> new_bin_state = collectState(bin);
        verifyThatFilesHaveBeenAdded(previous_bin_state, new_bin_state,
                                     "bin/alfa/A.class",
                                     "bin/javac_state");
    }

    void removeFrom(Path dir, String... args) throws IOException {
        for (String filename : args) {
            Path p = dir.resolve(filename);
            Files.delete(p);
        }
    }

    void populate(Path src, String... args) throws IOException {
        if (!Files.exists(src)) {
            Files.createDirectory(src);
        }
        String[] a = args;
        for (int i = 0; i<a.length; i+=2) {
            String filename = a[i];
            String content = a[i+1];
            Path p = src.resolve(filename);
            Files.createDirectories(p.getParent());
            PrintWriter out = new PrintWriter(Files.newBufferedWriter(p,
                                                                      Charset.defaultCharset()));
            out.println(content);
            out.close();
        }
    }

    void delete(final Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                 @Override
                 public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
                 {
                     Files.delete(file);
                     return FileVisitResult.CONTINUE;
                 }

                 @Override
                 public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException
                 {
                     if (e == null) {
                         if (!dir.equals(root)) Files.delete(dir);
                         return FileVisitResult.CONTINUE;
                     } else {
                         // directory iteration failed
                         throw e;
                     }
                 }
            });
    }

    void compile(String... args) throws Exception {
        int rc = main.go(args, System.out, System.err);
        if (rc != 0) throw new Exception("Error during compile!");

        // Wait a second, to get around the (temporary) problem with
        // second resolution in the Java file api. But do not do this
        // on windows where the timestamps work.
        long in_a_sec = System.currentTimeMillis()+1000;
        while (in_a_sec > System.currentTimeMillis()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
    }

    void compileExpectFailure(String... args) throws Exception {
        int rc = main.go(args, System.out, System.err);
        if (rc == 0) throw new Exception("Expected error during compile! Did not fail!");
    }

    Map<String,Long> collectState(Path dir) throws IOException
    {
        final Map<String,Long> files = new HashMap<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                 @Override
                 public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                   throws IOException
                 {
                     files.put(file.toString(),new Long(Files.getLastModifiedTime(file).toMillis()));
                     return FileVisitResult.CONTINUE;
                 }
            });
        return files;
    }

    void verifyThatFilesHaveBeenRemoved(Map<String,Long> from,
                                        Map<String,Long> to,
                                        String... args) throws Exception {

        Set<String> froms = from.keySet();
        Set<String> tos = to.keySet();

        if (froms.equals(tos)) {
            throw new Exception("Expected new state to have fewer files than previous state!");
        }

        for (String t : tos) {
            if (!froms.contains(t)) {
                throw new Exception("Expected "+t+" to exist in previous state!");
            }
        }

        for (String f : args) {
            f = f.replace("/", File.separator);
            if (!froms.contains(f)) {
                throw new Exception("Expected "+f+" to exist in previous state!");
            }
            if (tos.contains(f)) {
                throw new Exception("Expected "+f+" to have been removed from the new state!");
            }
        }

        if (froms.size() - args.length != tos.size()) {
            throw new Exception("There are more removed files than the expected list!");
        }
    }

    void verifyThatFilesHaveBeenAdded(Map<String,Long> from,
                                      Map<String,Long> to,
                                      String... args) throws Exception {

        Set<String> froms = from.keySet();
        Set<String> tos = to.keySet();

        if (froms.equals(tos)) {
            throw new Exception("Expected new state to have more files than previous state!");
        }

        for (String t : froms) {
            if (!tos.contains(t)) {
                throw new Exception("Expected "+t+" to exist in new state!");
            }
        }

        for (String f : args) {
            f = f.replace("/", File.separator);
            if (!tos.contains(f)) {
                throw new Exception("Expected "+f+" to have been added to new state!");
            }
            if (froms.contains(f)) {
                throw new Exception("Expected "+f+" to not exist in previous state!");
            }
        }

        if (froms.size() + args.length != tos.size()) {
            throw new Exception("There are more added files than the expected list!");
        }
    }

    void verifyNewerFiles(Map<String,Long> from,
                          Map<String,Long> to,
                          String... args) throws Exception {
        if (!from.keySet().equals(to.keySet())) {
            throw new Exception("Expected the set of files to be identical!");
        }
        Set<String> files = new HashSet<String>();
        for (String s : args) {
            files.add(s.replace("/", File.separator));
        }
        for (String fn : from.keySet()) {
            long f = from.get(fn);
            long t = to.get(fn);
            if (files.contains(fn)) {
                if (t <= f) {
                    throw new Exception("Expected "+fn+" to have a more recent timestamp!");
                }
            } else {
                if (t != f) {
                    throw new Exception("Expected "+fn+" to have the same timestamp!");
                }
            }
        }
    }

    String print(Map<String,Long> m) {
        StringBuilder b = new StringBuilder();
        Set<String> keys = m.keySet();
        for (String k : keys) {
            b.append(k+" "+m.get(k)+"\n");
        }
        return b.toString();
    }

    void verifyEqual(Map<String,Long> from, Map<String,Long> to) throws Exception {
        if (!from.equals(to)) {
            System.out.println("FROM---"+print(from));
            System.out.println("TO-----"+print(to));
            throw new Exception("The dir should not differ! But it does!");
        }
    }
}
