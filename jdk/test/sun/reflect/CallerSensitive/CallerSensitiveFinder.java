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

import com.sun.tools.classfile.*;
import com.sun.tools.jdeps.ClassFileReader;
import static com.sun.tools.classfile.ConstantPool.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/*
 * @test
 * @bug 8010117
 * @summary Verify if CallerSensitive methods are annotated with
 *          sun.reflect.CallerSensitive annotation
 * @build CallerSensitiveFinder
 * @run main/othervm/timeout=900 -mx600m CallerSensitiveFinder
 */
public class CallerSensitiveFinder {
    private static int numThreads = 3;
    private static boolean verbose = false;
    public static void main(String[] args) throws Exception {
        List<Path> classes = new ArrayList<>();
        String testclasses = System.getProperty("test.classes", ".");
        int i = 0;
        while (i < args.length) {
            String arg = args[i++];
            if (arg.equals("-v")) {
                verbose = true;
            } else {
                Path p = Paths.get(testclasses, arg);
                if (!p.toFile().exists()) {
                    throw new IllegalArgumentException(arg + " does not exist");
                }
                classes.add(p);
            }
        }
        if (classes.isEmpty()) {
            classes.addAll(PlatformClassPath.getJREClasses());
        }
        CallerSensitiveFinder csfinder = new CallerSensitiveFinder();

        List<String> errors = csfinder.run(classes);
        if (!errors.isEmpty()) {
            throw new RuntimeException(errors.size() +
                    " caller-sensitive methods are missing @CallerSensitive annotation");
        }
    }

    private final List<String> csMethodsMissingAnnotation = new ArrayList<>();
    private final ReferenceFinder finder;
    public CallerSensitiveFinder() {
        this.finder = new ReferenceFinder(getFilter(), getVisitor());
    }

    private ReferenceFinder.Filter getFilter() {
        final String classname = "sun/reflect/Reflection";
        final String method = "getCallerClass";
        return new ReferenceFinder.Filter() {
            public boolean accept(ConstantPool cpool, CPRefInfo cpref) {
                try {
                    CONSTANT_NameAndType_info nat = cpref.getNameAndTypeInfo();
                    return cpref.getClassName().equals(classname) && nat.getName().equals(method);
                } catch (ConstantPoolException ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
    }

    private ReferenceFinder.Visitor getVisitor() {
        return new ReferenceFinder.Visitor() {
            public void visit(ClassFile cf, Method m,  List<CPRefInfo> refs) {
                try {
                    String name = String.format("%s#%s %s", cf.getName(),
                                                m.getName(cf.constant_pool),
                                                m.descriptor.getValue(cf.constant_pool));
                    if (!CallerSensitiveFinder.isCallerSensitive(m, cf.constant_pool)) {
                        csMethodsMissingAnnotation.add(name);
                        System.err.println("Missing @CallerSensitive: " + name);
                    } else {
                        if (verbose) {
                            System.out.format("@CS  %s%n", name);
                        }
                    }
                } catch (ConstantPoolException ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
    }

    public List<String> run(List<Path> classes) throws IOException, InterruptedException,
            ExecutionException, ConstantPoolException
    {
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        for (Path path : classes) {
            ClassFileReader reader = ClassFileReader.newInstance(path);
            for (ClassFile cf : reader.getClassFiles()) {
                String classFileName = cf.getName();
                // for each ClassFile
                //    parse constant pool to find matching method refs
                //      parse each method (caller)
                //      - visit and find method references matching the given method name
                pool.submit(getTask(cf));
            }
        }
        waitForCompletion();
        pool.shutdown();
        return csMethodsMissingAnnotation;
    }

    private static final String CALLER_SENSITIVE_ANNOTATION = "Lsun/reflect/CallerSensitive;";
    private static boolean isCallerSensitive(Method m, ConstantPool cp)
            throws ConstantPoolException
    {
        RuntimeAnnotations_attribute attr =
            (RuntimeAnnotations_attribute)m.attributes.get(Attribute.RuntimeVisibleAnnotations);
        int index = 0;
        if (attr != null) {
            for (int i = 0; i < attr.annotations.length; i++) {
                Annotation ann = attr.annotations[i];
                String annType = cp.getUTF8Value(ann.type_index);
                if (CALLER_SENSITIVE_ANNOTATION.equals(annType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private final List<FutureTask<Void>> tasks = new ArrayList<FutureTask<Void>>();
    private FutureTask<Void> getTask(final ClassFile cf) {
        FutureTask<Void> task = new FutureTask<Void>(new Callable<Void>() {
            public Void call() throws Exception {
                finder.parse(cf);
                return null;
            }
        });
        tasks.add(task);
        return task;
    }

    private void waitForCompletion() throws InterruptedException, ExecutionException {
        for (FutureTask<Void> t : tasks) {
            t.get();
        }
        System.out.println("Parsed " + tasks.size() + " classfiles");
    }

    static class PlatformClassPath {
        static List<Path> getJREClasses() throws IOException {
            List<Path> result = new ArrayList<Path>();
            Path home = Paths.get(System.getProperty("java.home"));

            if (home.endsWith("jre")) {
                // jar files in <javahome>/jre/lib
                // skip <javahome>/lib
                result.addAll(addJarFiles(home.resolve("lib")));
            } else if (home.resolve("lib").toFile().exists()) {
                // either a JRE or a jdk build image
                File classes = home.resolve("classes").toFile();
                if (classes.exists() && classes.isDirectory()) {
                    // jdk build outputdir
                    result.add(classes.toPath());
                }
                // add other JAR files
                result.addAll(addJarFiles(home.resolve("lib")));
            } else {
                throw new RuntimeException("\"" + home + "\" not a JDK home");
            }
            return result;
        }

        static List<Path> addJarFiles(final Path root) throws IOException {
            final List<Path> result = new ArrayList<Path>();
            final Path ext = root.resolve("ext");
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    if (dir.equals(root) || dir.equals(ext)) {
                        return FileVisitResult.CONTINUE;
                    } else {
                        // skip other cobundled JAR files
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    File f = file.toFile();
                    String fn = f.getName();
                    // parse alt-rt.jar as well
                    if (fn.endsWith(".jar") && !fn.equals("jfxrt.jar")) {
                        result.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return result;
        }
    }
}
