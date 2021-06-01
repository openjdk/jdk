/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.sun.tools.classfile.AccessFlags.ACC_PRIVATE;
import static com.sun.tools.classfile.ConstantPool.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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

/*
 * @test
 * @summary CallerSensitive methods should be static or final instance
 *          methods except the known list of non-final instance methods
 * @modules jdk.jdeps/com.sun.tools.classfile
 *          jdk.jdeps/com.sun.tools.jdeps
 * @build CheckCSMs
 * @run main/othervm/timeout=900 CheckCSMs
 */
public class CheckCSMs {
    private static int numThreads = 3;
    private static boolean listCSMs = false;
    private final ExecutorService pool;

    // The goal is to remove this list of Non-final instance @CS methods
    // over time.  Do not add any new one to this list.
    private static Set<String> KNOWN_NON_FINAL_CSMS =
        Set.of("java/io/ObjectStreamField#getType ()Ljava/lang/Class;",
               "java/io/ObjectStreamClass#forClass ()Ljava/lang/Class;",
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
            Set<String> diff = new HashSet<>(result);
            diff.removeAll(KNOWN_NON_FINAL_CSMS);
            throw new RuntimeException("Unexpected non-final instance method: " +
                result.stream().sorted()
                      .collect(Collectors.joining("\n", "\n", "")));
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

    private final ReferenceFinder finder;
    public CheckCSMs() {
        this.finder = new ReferenceFinder(getFilter(), getVisitor());
        pool = Executors.newFixedThreadPool(numThreads);

    }

    public Set<String> run(Stream<Path> classes)
        throws IOException, InterruptedException, ExecutionException,
               ConstantPoolException
    {
        classes.forEach(p -> pool.submit(getTask(p)));
        waitForCompletion();
        return nonFinalCSMs;
    }


    private ReferenceFinder.Filter getFilter() {
        final String classname = "jdk/internal/reflect/Reflection";
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
                    // ignore jdk.unsupported/sun.reflect.Reflection.getCallerClass
                    // which is a "special" delegate to the internal getCallerClass
                    if (cf.getName().equals("sun/reflect/Reflection") &&
                        m.getName(cf.constant_pool).equals("getCallerClass"))
                        return;

                    String name = methodSignature(cf, m);
                    if (!CheckCSMs.isStaticOrFinal(cf, m, cf.constant_pool)) {
                        System.err.println("Unsupported @CallerSensitive: " + name);
                        nonFinalCSMs.add(name);
                    } else {
                        if (listCSMs) {
                            System.out.format("@CS  %s%n", name);
                        }
                    }

                    // find the adapter implementation for CSM with the caller parameter
                    if (!csmWithCallerParameter.containsKey(cf.getName())) {
                        Set<String> methods = Arrays.stream(cf.methods)
                                                    .filter(m0 -> csmWithCallerParameter(cf, m, m0))
                                                    .map(m0 -> methodSignature(cf, m0))
                                                    .collect(Collectors.toSet());
                        csmWithCallerParameter.put(cf.getName(), methods);
                    }
                } catch (ConstantPoolException ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
    }

    private static String methodSignature(ClassFile cf, Method m) {
        try {
            return String.format("%s#%s %s", cf.getName(),
                                 m.getName(cf.constant_pool),
                                 m.descriptor.getValue(cf.constant_pool));
        } catch (ConstantPoolException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static boolean csmWithCallerParameter(ClassFile cf, Method csm, Method m) {
        ConstantPool cp = cf.constant_pool;
        try {
            int csmParamCount = csm.descriptor.getParameterCount(cp);
            int paramCount = m.descriptor.getParameterCount(cp);
            // an adapter method must have the same name and return type and a trailing Class parameter
            if (!(csm.getName(cp).equals(m.getName(cp)) &&
                    paramCount == (csmParamCount+1) &&
                    m.descriptor.getReturnType(cp).equals(csm.descriptor.getReturnType(cp)))) {
                return false;
            }
            // the descriptor of the adapter method must have the parameters
            // of the caller-sensitive method and an additional Class parameter
            String csmDesc = csm.descriptor.getParameterTypes(cp);
            String desc = m.descriptor.getParameterTypes(cp);
            int index = desc.indexOf(", java.lang.Class)");
            if (index == -1) {
                index = desc.indexOf("java.lang.Class)");
                if (index == -1) return false;
            }
            String s = desc.substring(0, index) + ")";
            if (s.equals(csmDesc)) {
                if (!m.access_flags.is(ACC_PRIVATE)) {
                    throw new RuntimeException(methodSignature(cf, m) + " adapter method for " +
                            methodSignature(cf, csm) + " must be private");
                }
                if (!isCallerSensitiveAdapter(m, cp)) {
                    throw new RuntimeException(methodSignature(cf, m) + " adapter method for " +
                            methodSignature(cf, csm) + " must be annotated with @CallerSensitiveAdapter");
                }
                return true;
            }
        } catch (ConstantPoolException|Descriptor.InvalidDescriptor e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private static final String CALLER_SENSITIVE_ANNOTATION
        = "Ljdk/internal/reflect/CallerSensitive;";
    private static final String CALLER_SENSITIVE_ADAPTER_ANNOTATION
        = "Ljdk/internal/reflect/CallerSensitiveAdapter;";

    private static boolean isCallerSensitive(Method m, ConstantPool cp)
        throws ConstantPoolException
    {
        RuntimeAnnotations_attribute attr =
            (RuntimeAnnotations_attribute)m.attributes.get(Attribute.RuntimeVisibleAnnotations);
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

    private static boolean isCallerSensitiveAdapter(Method m, ConstantPool cp)
            throws ConstantPoolException
    {
        RuntimeAnnotations_attribute attr =
                (RuntimeAnnotations_attribute)m.attributes.get(Attribute.RuntimeInvisibleAnnotations);
        if (attr != null) {
            for (int i = 0; i < attr.annotations.length; i++) {
                Annotation ann = attr.annotations[i];
                String annType = cp.getUTF8Value(ann.type_index);
                if (CALLER_SENSITIVE_ADAPTER_ANNOTATION.equals(annType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isStaticOrFinal(ClassFile cf, Method m, ConstantPool cp)
        throws ConstantPoolException
    {
        if (!isCallerSensitive(m, cp))
            return false;

        // either a static method or a final instance method
        return m.access_flags.is(AccessFlags.ACC_STATIC) ||
               m.access_flags.is(AccessFlags.ACC_FINAL) ||
               cf.access_flags.is(AccessFlags.ACC_FINAL);
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
                try (InputStream is = Files.newInputStream(p)) {
                    finder.parse(ClassFile.read(is));
                } catch (IOException x) {
                    throw new UncheckedIOException(x);
                } catch (ConstantPoolException x) {
                    throw new RuntimeException(x);
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
