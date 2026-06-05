/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.langtools.javac;

import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Context.Factory;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Pair;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Queue;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.CONFIG;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class JavacBenchmark {

    static final Logger LOG = Logger.getLogger(JavacBenchmark.class.getName());

    public enum Stage {
        Init, Parse, InitModules, Enter, Attribute, Flow, Desugar, Generate;

        public synchronized void waitFor() throws InterruptedException {
            wait();
        }
        public synchronized void notifyDone() {
            notifyAll();
            LOG.log(FINE, "{0} finished.", this.name());
        }
        public boolean isAfter(Stage other) {
            return ordinal() > other.ordinal();
        }
    }

    private Path root;
    private Path srcList;

    @Setup(Level.Trial)
    public void setup(Blackhole bh) throws IOException, InterruptedException {
        LOG.log(CONFIG, "Release info of the sources to be compiled by the benchmark:\n{0}", new String(JavacBenchmark.class.getResourceAsStream("/release").readAllBytes(), StandardCharsets.UTF_8));
        root = Files.createTempDirectory("JavacBenchmarkRoot");
        srcList = root.resolve("sources.list");
        int i = 0;
        try (PrintStream srcListOut = new PrintStream(srcList.toFile())) {
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(JavacBenchmark.class.getResourceAsStream("/src.zip")))) {
                for (ZipEntry entry; (entry = zis.getNextEntry()) != null;) {
                    final String ename = entry.getName();
                    if (!ename.startsWith("java.desktop") && !ename.startsWith("jdk.internal.vm.compiler") && !ename.startsWith("jdk.aot") && !ename.startsWith("jdk.accessibility") && !ename.startsWith("jdk.jsobject")) {
                        if (!entry.isDirectory() && ename.endsWith(".java")) {
                            Path dst = root.resolve(ename);
                            Files.createDirectories(dst.getParent());
                            Files.copy(zis, dst);
                            Files.readAllBytes(dst); //reads all the file back to exclude antivirus scanning time from following measurements
                            srcListOut.println(dst.toString());
                            i++;
                        }
                    }
                }
            }
        }
        Files.walk(root).map(Path::toFile).forEach(File::deleteOnExit); //mark all files and folders for deletion on JVM exit for cases when tearDown is not executed
        Thread.sleep(10000); //give some more time for the system to catch a breath for more precise measurement
        LOG.log(FINE, "Extracted {0} sources.", i);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        Files.walk(root).sorted(Comparator.reverseOrder()).map(Path::toFile).forEachOrdered(File::delete);
        LOG.fine("Sources deleted.");
    }

    protected void compile(Blackhole bh, final Stage stopAt) throws IOException {
        final OutputStream bhos = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                bh.consume(b);
            }
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                bh.consume(b);
            }
        };
        final Context ctx = new Context();
        //inject JavaCompiler wrapping all measured methods so they directly report to the benchmark
        ctx.put(JavaCompiler.compilerKey, (Factory<JavaCompiler>)(c) -> {
            return new JavaCompiler(c) {
                @Override
                public List<JCTree.JCCompilationUnit> parseFiles(Iterable<JavaFileObject> fileObjects) {
                    Stage.Init.notifyDone();
                    return stopAt.isAfter(Stage.Init) ? super.parseFiles(fileObjects) : List.nil();
                }

                @Override
                public List<JCTree.JCCompilationUnit> initModules(List<JCTree.JCCompilationUnit> roots) {
                    Stage.Parse.notifyDone();
                    return stopAt.isAfter(Stage.Parse) ? super.initModules(roots) : List.nil();
                }

                @Override
                public List<JCTree.JCCompilationUnit> enterTrees(List<JCTree.JCCompilationUnit> roots) {
                    Stage.InitModules.notifyDone();
                    return stopAt.isAfter(Stage.InitModules) ? super.enterTrees(roots) : List.nil();
                }

                @Override
                public Queue<Env<AttrContext>> attribute(Queue<Env<AttrContext>> envs) {
                    Stage.Enter.notifyDone();
                    return stopAt.isAfter(Stage.Enter) ? super.attribute(envs) : new ListBuffer<>();
                }

                @Override
                public Queue<Env<AttrContext>> flow(Queue<Env<AttrContext>> envs) {
                    Stage.Attribute.notifyDone();
                    return stopAt.isAfter(Stage.Attribute) ? super.flow(envs) : new ListBuffer<>();
                }

                @Override
                public Queue<Pair<Env<AttrContext>, JCTree.JCClassDecl>> desugar(Queue<Env<AttrContext>> envs) {
                    Stage.Flow.notifyDone();
                    return stopAt.isAfter(Stage.Flow) ? super.desugar(envs) : new ListBuffer<>();
                }

                @Override
                public void generate(Queue<Pair<Env<AttrContext>, JCTree.JCClassDecl>> queue) {
                    Stage.Desugar.notifyDone();
                    if (stopAt.isAfter(Stage.Desugar)) super.generate(queue);
                }
            };
        });
        //JavaFileManager directing all writes to a Blackhole to avoid measurement fluctuations due to delayed filesystem writes
        try (JavacFileManager mngr = new JavacFileManager(ctx, true, null) {
            @Override
            public JavaFileObject getJavaFileForOutput(JavaFileManager.Location arg0, String arg1, JavaFileObject.Kind arg2, FileObject arg3) throws IOException {
                return new ForwardingJavaFileObject<JavaFileObject>(super.getJavaFileForOutput(arg0, arg1, arg2, arg3)) {
                    @Override
                    public OutputStream openOutputStream() throws IOException {
                        return bhos;
                    }
                };
            }
        }) {
            String[] cmdLine = new String[] {"-source", "25", "-XDcompilePolicy=simple", "-implicit:none", "-nowarn", "--module-source-path", root.toString(), "-d", root.toString(), "-XDignore.symbol.file=true", "@" + srcList.toString()};
            if (new Main("javac").compile(cmdLine, ctx).exitCode != 0) {
                throw new IOException("compilation failed");
            }
        }
        LOG.fine("Compilation finished.");
    }
}
