/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.instruction.InvokeInstruction;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.stream.Stream;

/*
 * @test
 * @bug 8010117
 * @summary Verify if CallerSensitive methods are annotated with
 *          CallerSensitive annotation
 * @enablePreview
 * @build CallerSensitiveFinder
 * @run main/othervm/timeout=900 CallerSensitiveFinder
 */
public class CallerSensitiveFinder {
    private static int numThreads = 3;
    private static boolean verbose = false;
    private final ExecutorService pool;

    public static void main(String[] args) throws Exception {
        Stream<Path> classes = null;
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
                classes = Stream.of(p);
            }
        }

        if (classes == null) {
            classes = getPlatformClasses();
        }

        CallerSensitiveFinder csfinder = new CallerSensitiveFinder();
        List<String> errors = csfinder.run(classes);

        if (!errors.isEmpty()) {
            throw new RuntimeException(errors.size() +
                    " caller-sensitive methods are missing @CallerSensitive annotation");
        }
    }

    private final List<String> csMethodsMissingAnnotation = new CopyOnWriteArrayList<>();
    public CallerSensitiveFinder() {
        pool = Executors.newFixedThreadPool(numThreads);

    }

    private void check(ClassModel clazz) {
        final String className = "jdk/internal/reflect/Reflection";
        final String methodName = "getCallerClass";
        boolean checkMethods = false;
        for (var pe : clazz.constantPool()) {
            if (pe instanceof MethodRefEntry ref
                    && ref.owner().name().equalsString(className)
                    && ref.name().equalsString(methodName)) {
                checkMethods = true;
            }
        }

        if (!checkMethods)
            return;

        for (var method : clazz.methods()) {
            var code = method.code().orElse(null);
            if (code == null)
                continue;

            boolean needsCsm = false;
            for (var element : code) {
                if (element instanceof InvokeInstruction invoke
                        && invoke.opcode() == Opcode.INVOKESTATIC
                        && invoke.method() instanceof MethodRefEntry ref
                        && ref.owner().name().equalsString(className)
                        && ref.name().equalsString(methodName)) {
                    needsCsm = true;
                    break;
                }
            }

            if (needsCsm) {
                process(clazz, method);
            }
        }
    }

    private void process(ClassModel cf, MethodModel m) {
        // ignore jdk.unsupported/sun.reflect.Reflection.getCallerClass
        // which is a "special" delegate to the internal getCallerClass
        if (cf.thisClass().name().equalsString("sun/reflect/Reflection") &&
                m.methodName().equalsString("getCallerClass"))
            return;

        String name = cf.thisClass().asInternalName() + '#'
                + m.methodName().stringValue() + ' '
                + m.methodType().stringValue();
        if (!CallerSensitiveFinder.isCallerSensitive(m)) {
            csMethodsMissingAnnotation.add(name);
            System.err.println("Missing @CallerSensitive: " + name);
        } else {
            if (verbose) {
                System.out.format("@CS  %s%n", name);
            }
        }
    }

    public List<String> run(Stream<Path> classes)throws IOException, InterruptedException,
            ExecutionException, IllegalArgumentException
    {
        classes.forEach(p -> pool.submit(getTask(p)));
        waitForCompletion();
        return csMethodsMissingAnnotation;
    }

    private static final String CALLER_SENSITIVE_ANNOTATION = "Ljdk/internal/reflect/CallerSensitive;";
    private static boolean isCallerSensitive(MethodModel m) {
        var attr = m.findAttribute(Attributes.runtimeVisibleAnnotations()).orElse(null);
        if (attr != null) {
            for (var ann : attr.annotations()) {
                if (ann.className().equalsString(CALLER_SENSITIVE_ANNOTATION)) {
                    return true;
                }
            }
        }
        return false;
    }

    private final List<FutureTask<Void>> tasks = new ArrayList<>();

    /*
     * Each task parses the class file of the given path.
     * - parse constant pool to find matching method refs
     * - parse each method (caller)
     * - visit and find method references matching the given method name
     */
    private FutureTask<Void> getTask(Path p) {
        FutureTask<Void> task = new FutureTask<>(new Callable<>() {
            public Void call() throws Exception {
                try {
                    var clz = ClassFile.of().parse(p); // propagate IllegalArgumentException
                    check(clz);
                } catch (IOException x) {
                    throw new UncheckedIOException(x);
                }
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
        if (tasks.isEmpty()) {
            throw new RuntimeException("No classes found, or specified.");
        }
        pool.shutdown();
        System.out.println("Parsed " + tasks.size() + " classfiles");
    }

    static Stream<Path> getPlatformClasses() throws IOException {
        Path home = Paths.get(System.getProperty("java.home"));

        // Either an exploded build or an image.
        File classes = home.resolve("modules").toFile();
        Path root = classes.isDirectory()
                        ? classes.toPath()
                        : FileSystems.getFileSystem(URI.create("jrt:/"))
                                     .getPath("/");

        try {
            return Files.walk(root)
                .filter(p -> p.getNameCount() > 1)
                .filter(p -> p.toString().endsWith(".class") &&
                             !p.toString().equals("module-info.class"));
        } catch (IOException x) {
            throw new UncheckedIOException(x);
        }
    }
}
