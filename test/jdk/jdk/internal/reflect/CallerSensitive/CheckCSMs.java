/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.AccessFlag;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.constant.ConstantDescs.CD_Class;

/*
 * @test
 * @summary CallerSensitive methods should be static or final instance
 *          methods except the known list of non-final instance methods
 * @enablePreview
 * @build CheckCSMs
 * @run main/othervm/timeout=900 CheckCSMs
 */
public class CheckCSMs {
    private static int numThreads = 3;
    private static boolean listCSMs = false;
    private final ExecutorService pool;

    // The goal is to remove this list of Non-final instance @CS methods
    // over time.  Do not add any new one to this list.
    private static final Set<String> KNOWN_NON_FINAL_CSMS =
        Set.of("java/io/ObjectStreamField#getType ()Ljava/lang/Class;",
               "java/lang/Runtime#load (Ljava/lang/String;)V",
               "java/lang/Runtime#loadLibrary (Ljava/lang/String;)V",
               "java/lang/Thread#getContextClassLoader ()Ljava/lang/ClassLoader;",
               "javax/sql/rowset/serial/SerialJavaObject#getFields ()[Ljava/lang/reflect/Field;"
        );

    // These non-static non-final methods must not have @CallerSensitiveAdapter
    // methods that takes an additional caller class parameter.
    private static Set<String> UNSUPPORTED_VIRTUAL_METHODS =
        Set.of("java/io/ObjectStreamField#getType (Ljava/lang/Class;)Ljava/lang/Class;",
               "java/lang/Thread#getContextClassLoader (Ljava/lang/Class;)Ljava/lang/ClassLoader;",
               "javax/sql/rowset/serial/SerialJavaObject#getFields (Ljava/lang/Class;)[Ljava/lang/reflect/Field;"
        );

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--list")) {
            listCSMs = true;
        }

        CheckCSMs checkCSMs = new CheckCSMs();
        Set<String> result = checkCSMs.run(getPlatformClasses());
        if (!KNOWN_NON_FINAL_CSMS.equals(result)) {
            Set<String> extras = new HashSet<>(result);
            extras.removeAll(KNOWN_NON_FINAL_CSMS);
            Set<String> missing = new HashSet<>(KNOWN_NON_FINAL_CSMS);
            missing.removeAll(result);
            throw new RuntimeException("Mismatch in non-final instance methods.\n" +
                "Extra methods:\n" + String.join("\n", extras) + "\n" +
                "Missing methods:\n" + String.join("\n", missing) + "\n");
        }

        // check if all csm methods with a trailing Class parameter are supported
        checkCSMs.csmWithCallerParameter.values().stream()
                 .flatMap(Set::stream)
                 .forEach(m -> {
                     if (UNSUPPORTED_VIRTUAL_METHODS.contains(m))
                         throw new RuntimeException("Unsupported alternate csm adapter: " + m);
                 });
    }

    private final Set<String> nonFinalCSMs = new ConcurrentSkipListSet<>();
    private final Map<String, Set<String>> csmWithCallerParameter = new ConcurrentHashMap<>();

    public CheckCSMs() {
        pool = Executors.newFixedThreadPool(numThreads);
    }

    public Set<String> run(Stream<Path> classes)
        throws IOException, InterruptedException, ExecutionException,
               IllegalArgumentException
    {
        classes.forEach(p -> pool.submit(getTask(p)));
        waitForCompletion();
        return nonFinalCSMs;
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
        if (cf.thisClass().name().equalsString("sun/reflect/Reflection")
                && m.methodName().equalsString("getCallerClass"))
            return;

        String name = methodSignature(cf, m);
        if (!CheckCSMs.isStaticOrFinal(cf, m)) {
            System.err.println("Unsupported @CallerSensitive: " + name);
            nonFinalCSMs.add(name);
        } else {
            if (listCSMs) {
                System.out.format("@CS  %s%n", name);
            }
        }

        // find the adapter implementation for CSM with the caller parameter
        if (!csmWithCallerParameter.containsKey(cf.thisClass().asInternalName())) {
            Set<String> methods = cf.methods().stream()
                    .filter(m0 -> csmWithCallerParameter(cf, m, m0))
                    .map(m0 -> methodSignature(cf, m0))
                    .collect(Collectors.toSet());
            csmWithCallerParameter.put(cf.thisClass().asInternalName(), methods);
        }
    }

    private static String methodSignature(ClassModel cf, MethodModel m) {
        return cf.thisClass().asInternalName() + '#'
                + m.methodName().stringValue() + ' '
                + m.methodType().stringValue();
    }

    private static boolean csmWithCallerParameter(ClassModel cf, MethodModel csm, MethodModel m) {
        var csmType = csm.methodTypeSymbol();
        var mType = m.methodTypeSymbol();
        // an adapter method must have the same name and return type and a trailing Class parameter
        if (!(csm.methodName().equals(m.methodName()) &&
                mType.parameterCount() == (csmType.parameterCount() + 1) &&
                mType.returnType().equals(csmType.returnType()))) {
            return false;
        }
        // the descriptor of the adapter method must have the parameters
        // of the caller-sensitive method and an additional Class parameter
        for (int i = 0; i < csmType.parameterCount(); i++) {
            if (mType.parameterType(i) != csmType.parameterType(i)) {
                return false;
            }
        }

        if (!mType.parameterType(mType.parameterCount() - 1).equals(CD_Class)) {
            return false;
        }

        if (!m.flags().has(AccessFlag.PRIVATE)) {
            throw new RuntimeException(methodSignature(cf, m) + " adapter method for " +
                    methodSignature(cf, csm) + " must be private");
        }
        if (!isCallerSensitiveAdapter(m)) {
            throw new RuntimeException(methodSignature(cf, m) + " adapter method for " +
                    methodSignature(cf, csm) + " must be annotated with @CallerSensitiveAdapter");
        }
        return true;
    }

    private static final String CALLER_SENSITIVE_ANNOTATION
        = "Ljdk/internal/reflect/CallerSensitive;";
    private static final String CALLER_SENSITIVE_ADAPTER_ANNOTATION
        = "Ljdk/internal/reflect/CallerSensitiveAdapter;";

    private static boolean isCallerSensitive(MethodModel m)
        throws IllegalArgumentException
    {
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

    private static boolean isCallerSensitiveAdapter(MethodModel m) {
        var attr = m.findAttribute(Attributes.runtimeInvisibleAnnotations()).orElse(null);

        if (attr != null) {
            for (var ann : attr.annotations()) {
                if (ann.className().equalsString(CALLER_SENSITIVE_ADAPTER_ANNOTATION)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isStaticOrFinal(ClassModel cf, MethodModel m) {
        if (!isCallerSensitive(m))
            return false;

        // either a static method or a final instance method
        return m.flags().has(AccessFlag.STATIC) ||
               m.flags().has(AccessFlag.FINAL) ||
               cf.flags().has(AccessFlag.FINAL);
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
