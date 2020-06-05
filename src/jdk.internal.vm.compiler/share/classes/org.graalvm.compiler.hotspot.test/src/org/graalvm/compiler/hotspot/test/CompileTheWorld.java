/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.test;

import static java.util.Collections.singletonList;
import static org.graalvm.compiler.core.CompilationWrapper.ExceptionAction.Print;
import static org.graalvm.compiler.core.GraalCompilerOptions.CompilationBailoutAsFailure;
import static org.graalvm.compiler.core.GraalCompilerOptions.CompilationFailureAction;
import static org.graalvm.compiler.core.test.ReflectionOptionDescriptors.extractEntries;
import static org.graalvm.compiler.debug.MemUseTrackerKey.getCurrentThreadAllocatedBytes;
import static org.graalvm.compiler.hotspot.CompilationTask.CompilationTime;
import static org.graalvm.compiler.hotspot.CompilationTask.CompiledAndInstalledBytecodes;
import static org.graalvm.compiler.hotspot.test.CompileTheWorld.Options.DESCRIPTORS;
import static org.graalvm.compiler.hotspot.test.CompileTheWorld.Options.InvalidateInstalledCode;
import static sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.UnmodifiableMapCursor;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.test.ModuleSupport;
import org.graalvm.compiler.bytecode.Bytecodes;
import org.graalvm.compiler.core.phases.HighTier;
import org.graalvm.compiler.core.test.ReflectionOptionDescriptors;
import org.graalvm.compiler.debug.GlobalMetrics;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.MethodFilter;
import org.graalvm.compiler.debug.MetricKey;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.CompilationTask;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalCompiler;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.test.CompileTheWorld.LibGraalParams.StackTraceBuffer;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import jdk.internal.vm.compiler.libgraal.LibGraal;
import jdk.internal.vm.compiler.libgraal.LibGraalScope;
import org.graalvm.util.OptionsEncoder;

import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotInstalledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCICompiler;
import sun.misc.Unsafe;

/**
 * This class implements compile-the-world functionality with JVMCI.
 */
public final class CompileTheWorld {

    static {
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("jdk.internal.vm.compiler");
    }

    /**
     * Magic token to denote that JDK classes are to be compiled. For JDK 8, the classes in
     * {@code rt.jar} are compiled. Otherwise the classes in the Java runtime image are compiled.
     */
    public static final String SUN_BOOT_CLASS_PATH = "sun.boot.class.path";

    /**
     * Magic token to denote the classes in the Java runtime image (i.e. in the {@code jrt:/} file
     * system).
     */
    public static final String JRT_CLASS_PATH_ENTRY = "<jrt>";

    /**
     * @param options a space separated set of option value settings with each option setting in a
     *            {@code -Dgraal.<name>=<value>} format but without the leading {@code -Dgraal.}.
     *            Ignored if null.
     */
    public static EconomicMap<OptionKey<?>, Object> parseOptions(String options) {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        if (options != null) {
            EconomicMap<String, String> optionSettings = EconomicMap.create();
            for (String optionSetting : options.split("\\s+|#")) {
                OptionsParser.parseOptionSettingTo(optionSetting, optionSettings);
            }
            ServiceLoader<OptionDescriptors> loader = ServiceLoader.load(OptionDescriptors.class, OptionDescriptors.class.getClassLoader());
            OptionsParser.parseOptions(optionSettings, values, loader);
        }
        if (!values.containsKey(HighTier.Options.Inline)) {
            values.put(HighTier.Options.Inline, false);
        }
        return values;
    }

    private final HotSpotJVMCIRuntime jvmciRuntime;

    private final HotSpotGraalCompiler compiler;

    /**
     * Class path denoting classes to compile.
     *
     * @see Options#Classpath
     */
    private final String inputClassPath;

    /**
     * Class index to start compilation at.
     *
     * @see Options#StartAt
     */
    private final int startAt;

    /**
     * Class index to stop compilation at.
     *
     * @see Options#StopAt
     */
    private final int stopAt;

    /**
     * Max classes to compile.
     *
     * @see Options#MaxClasses
     */
    private final int maxClasses;

    /** Only compile methods matching this filter if the filter is non-null. */
    private final MethodFilter methodFilter;

    /**
     * Exclude methods matching this filter if the filter is non-null. This is used by mx to exclude
     * some methods, while users are expected to use positive or negative filters in
     * {@link #methodFilter} instead.
     */
    private final MethodFilter excludeMethodFilter;

    // Counters
    private int classFileCounter = 0;
    private AtomicLong compiledMethodsCounter = new AtomicLong();
    private AtomicLong compileTime = new AtomicLong();
    private AtomicLong memoryUsed = new AtomicLong();

    private boolean verbose;

    /**
     * Signal that the threads should start compiling in multithreaded mode.
     */
    private boolean running;

    private ThreadPoolExecutor threadPool;

    /**
     * Values for {@link CompileTheWorld.Options}.
     */
    private final OptionValues harnessOptions;

    /**
     * Option values used during compilation.
     */
    private final OptionValues compilerOptions;

    /**
     * Manages native memory buffers for passing arguments into libgraal and receiving return
     * values. The native memory buffers are freed when this object is {@linkplain #close() closed}.
     */
    static class LibGraalParams implements AutoCloseable {

        static {
            LibGraal.registerNativeMethods(CompileTheWorld.class);
        }

        /**
         * Native memory containing {@linkplain OptionsEncoder encoded} {@link OptionValues}.
         */
        static class OptionsBuffer {
            private long address;
            final int size;
            final int hash;

            OptionsBuffer(OptionValues options) {
                Map<String, Object> map = new HashMap<>();
                UnmodifiableMapCursor<OptionKey<?>, Object> cursor = options.getMap().getEntries();
                while (cursor.advance()) {
                    final OptionKey<?> key = cursor.getKey();
                    Object value = cursor.getValue();
                    map.put(key.getName(), value);
                }

                byte[] encoded = OptionsEncoder.encode(map);
                size = encoded.length;
                hash = Arrays.hashCode(encoded);
                address = UNSAFE.allocateMemory(encoded.length);
                UNSAFE.copyMemory(encoded, ARRAY_BYTE_BASE_OFFSET, null, address, size);
            }

            long getAddress() {
                if (address == 0) {
                    throw new IllegalStateException();
                }
                return address;
            }

            void free() {
                if (address != 0) {
                    UNSAFE.freeMemory(address);
                    address = 0;
                }
            }
        }

        /**
         * Manages native memory for receiving a {@linkplain Throwable#printStackTrace() stack
         * trace} from libgraal serialized via {@link ByteArrayOutputStream} to a byte array.
         */
        static class StackTraceBuffer {
            final int size;
            private long address;

            StackTraceBuffer(int size) {
                this.size = size;
                address = UNSAFE.allocateMemory(size);
            }

            void free() {
                if (address != 0L) {
                    UNSAFE.freeMemory(address);
                    address = 0L;
                }
            }

            long getAddress() {
                if (address == 0) {
                    throw new IllegalStateException();
                }
                return address;
            }
        }

        final OptionsBuffer options;

        private final List<StackTraceBuffer> stackTraceBuffers = new ArrayList<>();

        /**
         * Gets a stack trace buffer for the current thread.
         */
        StackTraceBuffer getStackTraceBuffer() {
            return stackTraceBuffer.get();
        }

        private final ThreadLocal<StackTraceBuffer> stackTraceBuffer = new ThreadLocal<StackTraceBuffer>() {
            @Override
            protected StackTraceBuffer initialValue() {
                StackTraceBuffer buffer = new StackTraceBuffer(10_000);
                synchronized (stackTraceBuffers) {
                    stackTraceBuffers.add(buffer);
                }
                return buffer;
            }
        };

        LibGraalParams(OptionValues options) {
            this.options = new OptionsBuffer(options);
        }

        @Override
        public void close() {
            options.free();
            synchronized (stackTraceBuffers) {
                for (StackTraceBuffer buffer : stackTraceBuffers) {
                    buffer.free();
                }
                stackTraceBuffers.clear();
            }
        }
    }

    /**
     * Creates a compile-the-world instance.
     *
     * @param files {@link File#pathSeparator} separated list of Zip/Jar files to compile
     * @param startAt index of the class file to start compilation at
     * @param stopAt index of the class file to stop compilation at
     * @param maxClasses maximum number of classes to process
     * @param methodFilters filters describing the methods to compile
     * @param excludeMethodFilters filters describing the methods not to compile
     * @param harnessOptions values for {@link CompileTheWorld.Options}
     * @param compilerOptions option values used by the compiler
     */
    public CompileTheWorld(HotSpotJVMCIRuntime jvmciRuntime,
                    HotSpotGraalCompiler compiler,
                    String files,
                    int startAt,
                    int stopAt,
                    int maxClasses,
                    String methodFilters,
                    String excludeMethodFilters,
                    boolean verbose,
                    OptionValues harnessOptions,
                    OptionValues compilerOptions) {
        this.jvmciRuntime = jvmciRuntime;
        this.compiler = compiler;
        this.inputClassPath = files;
        this.startAt = Math.max(startAt, 1);
        this.stopAt = Math.max(stopAt, 1);
        this.maxClasses = Math.max(maxClasses, 1);
        this.methodFilter = methodFilters == null ? null : MethodFilter.parse(methodFilters);
        this.excludeMethodFilter = excludeMethodFilters == null ? null : MethodFilter.parse(excludeMethodFilters);
        this.verbose = verbose;
        this.harnessOptions = harnessOptions;

        // Copy the initial options and add in any extra options
        EconomicMap<OptionKey<?>, Object> compilerOptionsMap = EconomicMap.create(compilerOptions.getMap());

        // We want to see stack traces when a method fails to compile
        CompilationBailoutAsFailure.putIfAbsent(compilerOptionsMap, true);
        CompilationFailureAction.putIfAbsent(compilerOptionsMap, Print);

        this.compilerOptions = new OptionValues(compilerOptionsMap);
    }

    public CompileTheWorld(HotSpotJVMCIRuntime jvmciRuntime,
                    HotSpotGraalCompiler compiler,
                    OptionValues harnessOptions,
                    OptionValues compilerOptions) {
        this(jvmciRuntime, compiler, Options.Classpath.getValue(harnessOptions),
                        Options.StartAt.getValue(harnessOptions),
                        Options.StopAt.getValue(harnessOptions),
                        Options.MaxClasses.getValue(harnessOptions),
                        Options.MethodFilter.getValue(harnessOptions),
                        Options.ExcludeMethodFilter.getValue(harnessOptions),
                        Options.Verbose.hasBeenSet(harnessOptions) ? Options.Verbose.getValue(harnessOptions) : !Options.MultiThreaded.getValue(harnessOptions),
                        harnessOptions,
                        new OptionValues(compilerOptions, parseOptions(Options.Config.getValue(harnessOptions))));
    }

    /**
     * Compiles all methods in all classes in {@link #inputClassPath}. If {@link #inputClassPath}
     * equals {@link #SUN_BOOT_CLASS_PATH} the boot classes are used.
     */
    @SuppressWarnings("try")
    public void compile() throws Throwable {
        try (LibGraalParams libgraal = LibGraal.isAvailable() ? new LibGraalParams(compilerOptions) : null) {
            if (SUN_BOOT_CLASS_PATH.equals(inputClassPath)) {
                String bcpEntry = null;
                if (JavaVersionUtil.JAVA_SPEC <= 8) {
                    final String[] entries = System.getProperty(SUN_BOOT_CLASS_PATH).split(File.pathSeparator);
                    for (int i = 0; i < entries.length && bcpEntry == null; i++) {
                        String entry = entries[i];
                        File entryFile = new File(entry);
                        if (entryFile.getName().endsWith("rt.jar") && entryFile.isFile()) {
                            bcpEntry = entry;
                        }
                    }
                    if (bcpEntry == null) {
                        throw new GraalError("Could not find rt.jar on boot class path %s", System.getProperty(SUN_BOOT_CLASS_PATH));
                    }
                } else {
                    bcpEntry = JRT_CLASS_PATH_ENTRY;
                }
                compile(bcpEntry, libgraal);
            } else {
                compile(inputClassPath, libgraal);
            }
        }
    }

    public void println() {
        println("");
    }

    public void println(String format, Object... args) {
        println(String.format(format, args));
    }

    public void println(String s) {
        println(verbose, s);
    }

    public static void println(boolean cond, String s) {
        if (cond) {
            TTY.println(s);
        }
    }

    public void printStackTrace(Throwable t) {
        if (verbose) {
            t.printStackTrace(TTY.out);
        }
    }

    @SuppressWarnings("unused")
    private static void dummy() {
    }

    /**
     * Abstraction over different types of class path entries.
     */
    abstract static class ClassPathEntry implements Closeable {
        final String name;

        ClassPathEntry(String name) {
            this.name = name;
        }

        /**
         * Creates a {@link ClassLoader} for loading classes from this entry.
         */
        public abstract ClassLoader createClassLoader() throws IOException;

        /**
         * Gets the list of classes available under this entry.
         */
        public abstract List<String> getClassNames() throws IOException;

        @Override
        public String toString() {
            return name;
        }

        @Override
        public void close() throws IOException {
        }
    }

    /**
     * A class path entry that is a normal file system directory.
     */
    static class DirClassPathEntry extends ClassPathEntry {

        private final File dir;

        DirClassPathEntry(String name) {
            super(name);
            dir = new File(name);
            assert dir.isDirectory();
        }

        @Override
        public ClassLoader createClassLoader() throws IOException {
            URL url = dir.toURI().toURL();
            return new URLClassLoader(new URL[]{url});
        }

        @Override
        public List<String> getClassNames() throws IOException {
            List<String> classNames = new ArrayList<>();
            String root = dir.getPath();
            SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isRegularFile()) {
                        File path = file.toFile();
                        if (path.getName().endsWith(".class")) {
                            String pathString = path.getPath();
                            assert pathString.startsWith(root);
                            String classFile = pathString.substring(root.length() + 1);
                            String className = classFile.replace(File.separatorChar, '.');
                            classNames.add(className.replace('/', '.').substring(0, className.length() - ".class".length()));
                        }
                    }
                    return super.visitFile(file, attrs);
                }
            };
            Files.walkFileTree(dir.toPath(), visitor);
            return classNames;
        }
    }

    /**
     * A class path entry that is a jar or zip file.
     */
    static class JarClassPathEntry extends ClassPathEntry {

        private final JarFile jarFile;

        JarClassPathEntry(String name) throws IOException {
            super(name);
            jarFile = new JarFile(name);
        }

        @Override
        public ClassLoader createClassLoader() throws IOException {
            URL url = new URL("jar", "", "file:" + name + "!/");
            return new URLClassLoader(new URL[]{url});
        }

        /**
         * @see "https://docs.oracle.com/javase/9/docs/specs/jar/jar.html#Multi-release"
         */
        static Pattern MultiReleaseJarVersionedClassRE = Pattern.compile("META-INF/versions/[1-9][0-9]*/(.+)");

        @Override
        public List<String> getClassNames() throws IOException {
            Enumeration<JarEntry> e = jarFile.entries();
            List<String> classNames = new ArrayList<>(jarFile.size());
            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (je.isDirectory() || !je.getName().endsWith(".class")) {
                    continue;
                }
                String className = je.getName().substring(0, je.getName().length() - ".class".length());
                if (className.equals("module-info")) {
                    continue;
                }
                if (className.startsWith("META-INF/versions/")) {
                    Matcher m = MultiReleaseJarVersionedClassRE.matcher(className);
                    if (m.matches()) {
                        className = m.group(1);
                    } else {
                        continue;
                    }
                }
                classNames.add(className.replace('/', '.'));
            }
            return classNames;
        }

        @Override
        public void close() throws IOException {
            jarFile.close();
        }
    }

    /**
     * A class path entry representing the {@code jrt:/} file system.
     */
    static class JRTClassPathEntry extends ClassPathEntry {

        private final String limitModules;

        JRTClassPathEntry(String name, String limitModules) {
            super(name);
            this.limitModules = limitModules;
        }

        @Override
        public ClassLoader createClassLoader() throws IOException {
            URL url = URI.create("jrt:/").toURL();
            return new URLClassLoader(new URL[]{url});
        }

        @Override
        public List<String> getClassNames() throws IOException {
            Set<String> negative = new HashSet<>();
            Set<String> positive = new HashSet<>();
            if (limitModules != null && !limitModules.isEmpty()) {
                for (String s : limitModules.split(",")) {
                    if (s.startsWith("~")) {
                        negative.add(s.substring(1));
                    } else {
                        positive.add(s);
                    }
                }
            }
            List<String> classNames = new ArrayList<>();
            FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"), Collections.emptyMap());
            Path top = fs.getPath("/modules/");
            Files.find(top, Integer.MAX_VALUE,
                            (path, attrs) -> attrs.isRegularFile()).forEach(p -> {
                                int nameCount = p.getNameCount();
                                if (nameCount > 2) {
                                    String base = p.getName(nameCount - 1).toString();
                                    if (base.endsWith(".class") && !base.equals("module-info.class")) {
                                        String module = p.getName(1).toString();
                                        if (positive.isEmpty() || positive.contains(module)) {
                                            if (negative.isEmpty() || !negative.contains(module)) {
                                                // Strip module prefix and convert to dotted form
                                                String className = p.subpath(2, nameCount).toString().replace('/', '.');
                                                // Strip ".class" suffix
                                                className = className.replace('/', '.').substring(0, className.length() - ".class".length());
                                                classNames.add(className);
                                            }
                                        }
                                    }
                                }
                            });
            return classNames;
        }
    }

    private boolean isClassIncluded(String className) {
        if (methodFilter != null && !methodFilter.matchesClassName(className)) {
            return false;
        }
        if (excludeMethodFilter != null && excludeMethodFilter.matchesClassName(className)) {
            return false;
        }
        return true;
    }

    private ClassPathEntry openClassPathEntry(String entry) throws IOException {
        if (entry.endsWith(".zip") || entry.endsWith(".jar")) {
            return new JarClassPathEntry(entry);
        } else if (entry.equals(JRT_CLASS_PATH_ENTRY)) {
            return new JRTClassPathEntry(entry, Options.LimitModules.getValue(harnessOptions));
        } else {
            if (!new File(entry).isDirectory()) {
                return null;
            }
            return new DirClassPathEntry(entry);
        }
    }

    static final class CTWThread extends Thread {
        private final LibGraalParams libgraal;

        CTWThread(Runnable r, LibGraalParams libgraal) {
            super(r);
            this.libgraal = libgraal;
            setName("CTWThread-" + getId());
            setPriority(Thread.MAX_PRIORITY);
            setDaemon(true);
        }

        @SuppressWarnings("try")
        @Override
        public void run() {
            setContextClassLoader(getClass().getClassLoader());
            try (LibGraalScope scope = libgraal == null ? null : new LibGraalScope()) {
                super.run();
            }
        }
    }

    static final class CTWThreadFactory implements ThreadFactory {
        private final LibGraalParams libgraal;

        CTWThreadFactory(LibGraalParams libgraal) {
            this.libgraal = libgraal;
        }

        @Override
        public Thread newThread(Runnable r) {
            return new CTWThread(r, libgraal);
        }
    }

    /**
     * Compiles all methods in all classes in a given class path.
     *
     * @param classPath class path denoting classes to compile
     * @throws IOException
     */
    @SuppressWarnings("try")
    private void compile(String classPath, LibGraalParams libgraal) throws IOException {
        final String[] entries = classPath.split(File.pathSeparator);
        long start = System.nanoTime();
        Map<Thread, StackTraceElement[]> initialThreads = Thread.getAllStackTraces();

        if (libgraal == null) {
            try {
                // compile dummy method to get compiler initialized outside of the
                // config debug override.
                HotSpotResolvedJavaMethod dummyMethod = (HotSpotResolvedJavaMethod) JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess().lookupJavaMethod(
                                CompileTheWorld.class.getDeclaredMethod("dummy"));
                int entryBCI = JVMCICompiler.INVOCATION_ENTRY_BCI;
                boolean useProfilingInfo = false;
                boolean installAsDefault = false;
                CompilationTask task = new CompilationTask(jvmciRuntime, compiler, new HotSpotCompilationRequest(dummyMethod, entryBCI, 0L), useProfilingInfo, installAsDefault);
                task.runCompilation(compilerOptions);
            } catch (NoSuchMethodException | SecurityException e1) {
                printStackTrace(e1);
            }
        }

        /*
         * Always use a thread pool, even for single threaded mode since it simplifies the use of
         * DebugValueThreadFilter to filter on the thread names.
         */
        int threadCount = 1;
        if (Options.MultiThreaded.getValue(harnessOptions)) {
            threadCount = Options.Threads.getValue(harnessOptions);
            if (threadCount == 0) {
                threadCount = Runtime.getRuntime().availableProcessors();
            }
        } else {
            running = true;
        }

        threadPool = new ThreadPoolExecutor(threadCount, threadCount, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new CTWThreadFactory(libgraal));

        int compileStartAt = startAt;
        int compileStopAt = stopAt;
        int compileStep = 1;
        if (maxClasses != Integer.MAX_VALUE) {
            int totalClassFileCount = 0;
            for (String entry : entries) {
                try (ClassPathEntry cpe = openClassPathEntry(entry)) {
                    if (cpe != null) {
                        totalClassFileCount += cpe.getClassNames().size();
                    }
                }
            }

            int lastClassFile = totalClassFileCount - 1;
            compileStartAt = Math.min(startAt, lastClassFile);
            compileStopAt = Math.min(stopAt, lastClassFile);
            int range = compileStopAt - compileStartAt + 1;
            if (maxClasses < range) {
                compileStep = range / maxClasses;
            }
        }

        for (int i = 0; i < entries.length; i++) {
            final String entry = entries[i];
            try (ClassPathEntry cpe = openClassPathEntry(entry)) {
                if (cpe == null) {
                    println("CompileTheWorld : Skipped classes in " + entry);
                    println();
                    continue;
                }

                if (methodFilter == null || methodFilter.matchesNothing()) {
                    println("CompileTheWorld : Compiling all classes in " + entry);
                } else {
                    String include = methodFilter.toString();
                    println("CompileTheWorld : Compiling all methods in " + entry + " matching one of the following filters: " + include);
                }
                if (excludeMethodFilter != null && !excludeMethodFilter.matchesNothing()) {
                    String exclude = excludeMethodFilter.toString();
                    println("CompileTheWorld : Excluding all methods matching one of the following filters: " + exclude);
                }
                println();

                ClassLoader loader = cpe.createClassLoader();

                for (String className : cpe.getClassNames()) {

                    // Are we done?
                    if (classFileCounter >= compileStopAt) {
                        break;
                    }

                    classFileCounter++;

                    if (compileStep > 1 && ((classFileCounter - compileStartAt) % compileStep) != 0) {
                        continue;
                    }

                    if (className.startsWith("jdk.management.") ||
                                    className.startsWith("jdk.internal.cmm.*") ||
                                    // GR-5881: The class initializer for
                                    // sun.tools.jconsole.OutputViewer
                                    // spawns non-daemon threads for redirecting sysout and syserr.
                                    // These threads tend to cause deadlock at VM exit
                                    className.startsWith("sun.tools.jconsole.")) {
                        continue;
                    }

                    if (!isClassIncluded(className)) {
                        continue;
                    }

                    try {
                        // Load and initialize class
                        Class<?> javaClass = Class.forName(className, true, loader);
                        MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();

                        // Pre-load all classes in the constant pool.
                        try {
                            HotSpotResolvedObjectType objectType = (HotSpotResolvedObjectType) metaAccess.lookupJavaType(javaClass);
                            ConstantPool constantPool = objectType.getConstantPool();
                            for (int cpi = 1; cpi < constantPool.length(); cpi++) {
                                constantPool.loadReferencedType(cpi, Bytecodes.LDC);
                            }
                        } catch (Throwable t) {
                            // If something went wrong during pre-loading we just ignore it.
                            if (isClassIncluded(className)) {
                                println("Preloading failed for (%d) %s: %s", classFileCounter, className, t);
                            }
                            continue;
                        }

                        // Are we compiling this class?
                        if (classFileCounter >= compileStartAt) {

                            long start0 = System.nanoTime();
                            // Compile each constructor/method in the class.
                            for (Constructor<?> constructor : javaClass.getDeclaredConstructors()) {
                                HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaMethod(constructor);
                                if (canBeCompiled(javaMethod, constructor.getModifiers())) {
                                    compileMethod(javaMethod, libgraal);
                                }
                            }
                            for (Method method : javaClass.getDeclaredMethods()) {
                                HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaMethod(method);
                                if (canBeCompiled(javaMethod, method.getModifiers())) {
                                    compileMethod(javaMethod, libgraal);
                                }
                            }

                            // Also compile the class initializer if it exists
                            HotSpotResolvedJavaMethod clinit = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaType(javaClass).getClassInitializer();
                            if (clinit != null && canBeCompiled(clinit, clinit.getModifiers())) {
                                compileMethod(clinit, libgraal);
                            }
                            println("CompileTheWorld (%d) : %s (%d us)", classFileCounter, className, (System.nanoTime() - start0) / 1000);
                        }
                    } catch (Throwable t) {
                        if (isClassIncluded(className)) {
                            println("CompileTheWorld (%d) : Skipping %s %s", classFileCounter, className, t.toString());
                            printStackTrace(t);
                        }
                    }
                }
            }
        }

        if (!running) {
            startThreads();
        }
        int wakeups = 0;
        long lastCompletedTaskCount = 0;
        for (long completedTaskCount = threadPool.getCompletedTaskCount(); completedTaskCount != threadPool.getTaskCount(); completedTaskCount = threadPool.getCompletedTaskCount()) {
            if (wakeups % 15 == 0) {
                TTY.printf("CompileTheWorld : Waiting for %d compiles, just completed %d compiles%n", threadPool.getTaskCount() - completedTaskCount, completedTaskCount - lastCompletedTaskCount);
                lastCompletedTaskCount = completedTaskCount;
            }
            try {
                threadPool.awaitTermination(1, TimeUnit.SECONDS);
                wakeups++;
            } catch (InterruptedException e) {
            }
        }
        threadPool.shutdown();
        threadPool = null;

        long elapsedTime = System.nanoTime() - start;

        println();
        int compiledClasses = classFileCounter > compileStartAt ? classFileCounter - compileStartAt : 0;
        if (Options.MultiThreaded.getValue(harnessOptions)) {
            TTY.println("CompileTheWorld : Done (%d classes, %d methods, %d ms elapsed, %d ms compile time, %d bytes of memory used)", compiledClasses, compiledMethodsCounter.get(), elapsedTime,
                            compileTime.get() / 1000000, memoryUsed.get());
        } else {
            TTY.println("CompileTheWorld : Done (%d classes, %d methods, %d ms, %d bytes of memory used)", compiledClasses, compiledMethodsCounter.get(), compileTime.get(), memoryUsed.get());
        }

        GlobalMetrics metricValues = ((HotSpotGraalRuntime) compiler.getGraalRuntime()).getMetricValues();
        EconomicMap<MetricKey, Long> map = metricValues.asKeyValueMap();
        Long compiledAndInstalledBytecodes = map.get(CompiledAndInstalledBytecodes);
        Long compilationTime = map.get(CompilationTime);
        if (compiledAndInstalledBytecodes != null && compilationTime != null) {
            TTY.println("CompileTheWorld : Aggregate compile speed %d bytecodes per second (%d / %d)", (int) (compiledAndInstalledBytecodes / (compilationTime / 1000000000.0)),
                            compiledAndInstalledBytecodes, compilationTime);
        }

        metricValues.print(compilerOptions);
        metricValues.clear();

        // Apart from the main thread, there should be only be daemon threads
        // alive now. If not, then a class initializer has probably started
        // a thread that could cause a deadlock while trying to exit the VM.
        // One known example of this is sun.tools.jconsole.OutputViewer which
        // spawns threads to redirect sysout and syserr. To help debug such
        // scenarios, the stacks of potentially problematic threads are dumped.
        Map<Thread, StackTraceElement[]> suspiciousThreads = new HashMap<>();
        for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
            Thread thread = e.getKey();
            if (thread != Thread.currentThread() && !initialThreads.containsKey(thread) && !thread.isDaemon() && thread.isAlive()) {
                suspiciousThreads.put(thread, e.getValue());
            }
        }
        if (!suspiciousThreads.isEmpty()) {
            TTY.println("--- Non-daemon threads started during CTW ---");
            for (Map.Entry<Thread, StackTraceElement[]> e : suspiciousThreads.entrySet()) {
                Thread thread = e.getKey();
                if (thread.isAlive()) {
                    TTY.println(thread.toString() + " " + thread.getState());
                    for (StackTraceElement ste : e.getValue()) {
                        TTY.println("\tat " + ste);
                    }
                }
            }
            TTY.println("---------------------------------------------");
        }
    }

    private synchronized void startThreads() {
        running = true;
        // Wake up any waiting threads
        notifyAll();
    }

    private synchronized void waitToRun() {
        while (!running) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
    }

    @SuppressWarnings("try")
    private void compileMethod(HotSpotResolvedJavaMethod method, LibGraalParams libgraal) throws InterruptedException, ExecutionException {
        if (methodFilter != null && !methodFilter.matches(method)) {
            return;
        }
        if (excludeMethodFilter != null && excludeMethodFilter.matches(method)) {
            return;
        }
        Future<?> task = threadPool.submit(new Runnable() {
            @Override
            public void run() {
                waitToRun();
                compileMethod(method, classFileCounter, libgraal);
            }
        });
        if (threadPool.getCorePoolSize() == 1) {
            task.get();
        }
    }

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    /**
     * Implemented by
     * {@code com.oracle.svm.graal.hotspot.libgraal.LibGraalEntryPoints.compileMethod}.
     */
    static native long compileMethodInLibgraal(long isolateThread,
                    long methodHandle,
                    boolean useProfilingInfo,
                    boolean installAsDefault,
                    long optionsAddress,
                    int optionsSize,
                    int optionsHash,
                    long encodedThrowableBufferAddress,
                    int encodedThrowableBufferSize);

    /**
     * Compiles a method and gathers some statistics.
     */
    @SuppressWarnings("try")
    private void compileMethod(HotSpotResolvedJavaMethod method, int counter, LibGraalParams libgraal) {
        try {
            long start = System.nanoTime();
            long allocatedAtStart = getCurrentThreadAllocatedBytes();
            // For more stable CTW execution, disable use of profiling information
            boolean useProfilingInfo = false;
            boolean installAsDefault = false;
            HotSpotInstalledCode installedCode;
            if (libgraal != null) {
                long methodHandle = LibGraal.translate(method);
                long isolateThread = LibGraalScope.getIsolateThread();

                StackTraceBuffer stackTraceBuffer = libgraal.getStackTraceBuffer();

                long stackTraceBufferAddress = stackTraceBuffer.getAddress();
                long installedCodeHandle = compileMethodInLibgraal(isolateThread,
                                methodHandle,
                                useProfilingInfo,
                                installAsDefault,
                                libgraal.options.getAddress(),
                                libgraal.options.size,
                                libgraal.options.hash,
                                stackTraceBufferAddress,
                                stackTraceBuffer.size);

                installedCode = LibGraal.unhand(HotSpotInstalledCode.class, installedCodeHandle);
                if (installedCode == null) {
                    int length = UNSAFE.getInt(stackTraceBufferAddress);
                    byte[] data = new byte[length];
                    UNSAFE.copyMemory(null, stackTraceBufferAddress + Integer.BYTES, data, ARRAY_BYTE_BASE_OFFSET, length);
                    String stackTrace = new String(data).trim();
                    println(true, String.format("CompileTheWorld (%d) : Error compiling method: %s", counter, method.format("%H.%n(%p):%r")));
                    println(true, stackTrace);
                }
            } else {
                int entryBCI = JVMCICompiler.INVOCATION_ENTRY_BCI;
                HotSpotCompilationRequest request = new HotSpotCompilationRequest(method, entryBCI, 0L);
                CompilationTask task = new CompilationTask(jvmciRuntime, compiler, request, useProfilingInfo, installAsDefault);
                task.runCompilation(compilerOptions);
                installedCode = task.getInstalledCode();
            }

            // Invalidate the generated code so the code cache doesn't fill up
            if (installedCode != null && InvalidateInstalledCode.getValue(compilerOptions)) {
                installedCode.invalidate();
            }

            memoryUsed.getAndAdd(getCurrentThreadAllocatedBytes() - allocatedAtStart);
            compileTime.getAndAdd(System.nanoTime() - start);
            compiledMethodsCounter.incrementAndGet();
        } catch (Throwable t) {
            // Catch everything and print a message
            println("CompileTheWorld (%d) : Error compiling method: %s", counter, method.format("%H.%n(%p):%r"));
            printStackTrace(t);
        }
    }

    /**
     * Determines if a method should be compiled (Cf. CompilationPolicy::can_be_compiled).
     *
     * @return true if it can be compiled, false otherwise
     */
    private boolean canBeCompiled(HotSpotResolvedJavaMethod javaMethod, int modifiers) {
        if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
            return false;
        }
        GraalHotSpotVMConfig c = compiler.getGraalRuntime().getVMConfig();
        if (c.dontCompileHugeMethods && javaMethod.getCodeSize() > c.hugeMethodLimit) {
            println(verbose || methodFilter != null,
                            String.format("CompileTheWorld (%d) : Skipping huge method %s (use -XX:-DontCompileHugeMethods or -XX:HugeMethodLimit=%d to include it)", classFileCounter,
                                            javaMethod.format("%H.%n(%p):%r"),
                                            javaMethod.getCodeSize()));
            return false;
        }
        // Allow use of -XX:CompileCommand=dontinline to exclude problematic methods
        if (!javaMethod.canBeInlined()) {
            return false;
        }
        // Skip @Snippets for now
        for (Annotation annotation : javaMethod.getAnnotations()) {
            if (annotation.annotationType().equals(Snippet.class)) {
                return false;
            }
        }
        return true;
    }

    static class Options {
        public static final OptionKey<Boolean> Help = new OptionKey<>(false);
        public static final OptionKey<String> Classpath = new OptionKey<>(CompileTheWorld.SUN_BOOT_CLASS_PATH);
        public static final OptionKey<Boolean> Verbose = new OptionKey<>(true);
        /**
         * Ignore Graal classes by default to avoid problems associated with compiling snippets and
         * method substitutions.
         */
        public static final OptionKey<String> LimitModules = new OptionKey<>("~jdk.internal.vm.compiler");
        public static final OptionKey<Integer> Iterations = new OptionKey<>(1);
        public static final OptionKey<String> MethodFilter = new OptionKey<>(null);
        public static final OptionKey<String> ExcludeMethodFilter = new OptionKey<>(null);
        public static final OptionKey<Integer> StartAt = new OptionKey<>(1);
        public static final OptionKey<Integer> StopAt = new OptionKey<>(Integer.MAX_VALUE);
        public static final OptionKey<Integer> MaxClasses = new OptionKey<>(Integer.MAX_VALUE);
        public static final OptionKey<String> Config = new OptionKey<>(null);
        public static final OptionKey<Boolean> MultiThreaded = new OptionKey<>(false);
        public static final OptionKey<Integer> Threads = new OptionKey<>(0);
        public static final OptionKey<Boolean> InvalidateInstalledCode = new OptionKey<>(false);

        // @formatter:off
        static final ReflectionOptionDescriptors DESCRIPTORS = new ReflectionOptionDescriptors(Options.class,
                           "Help", "List options and their help messages and then exit.",
                      "Classpath", "Class path denoting methods to compile. Default is to compile boot classes.",
                        "Verbose", "Verbose operation. Default is !MultiThreaded.",
                   "LimitModules", "Comma separated list of module names to which compilation should be limited. " +
                                   "Module names can be prefixed with \"~\" to exclude the named module.",
                     "Iterations", "The number of iterations to perform.",
                   "MethodFilter", "Only compile methods matching this filter.",
            "ExcludeMethodFilter", "Exclude methods matching this filter from compilation.",
                        "StartAt", "First class to consider for compilation (default = 1).",
                         "StopAt", "Last class to consider for compilation (default = <number of classes>).",
                     "MaxClasses", "Maximum number of classes to process (default = <number of classes>). " +
                                   "Ignored if less than (StopAt - StartAt + 1).",
                         "Config", "Option values to use during compile the world compilations. For example, " +
                                   "to disable partial escape analysis and print compilations specify " +
                                   "'PartialEscapeAnalysis=false PrintCompilation=true'. " +
                                   "Unless explicitly enabled with 'Inline=true' here, inlining is disabled.",
                  "MultiThreaded", "Run using multiple threads for compilation.",
                        "Threads", "Number of threads to use for multithreaded execution. Defaults to Runtime.getRuntime().availableProcessors().",
        "InvalidateInstalledCode", "Invalidate the generated code so the code cache doesn't fill up.");
        // @formatter:on
    }

    public static OptionValues loadHarnessOptions() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        List<OptionDescriptors> loader = singletonList(DESCRIPTORS);
        OptionsParser.parseOptions(extractEntries(System.getProperties(), "CompileTheWorld.", true), values, loader);
        OptionValues options = new OptionValues(values);
        if (Options.Help.getValue(options)) {
            options.printHelp(loader, System.out, "CompileTheWorld.");
            System.exit(0);
        }
        return options;
    }

    public static void main(String[] args) throws Throwable {
        HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) jvmciRuntime.getCompiler();
        HotSpotGraalRuntimeProvider graalRuntime = compiler.getGraalRuntime();
        HotSpotCodeCacheProvider codeCache = graalRuntime.getHostProviders().getCodeCache();
        OptionValues harnessOptions = loadHarnessOptions();

        int iterations = Options.Iterations.getValue(harnessOptions);
        for (int i = 0; i < iterations; i++) {
            codeCache.resetCompilationStatistics();
            TTY.println("CompileTheWorld : iteration " + i);

            CompileTheWorld ctw = new CompileTheWorld(jvmciRuntime, compiler, harnessOptions, graalRuntime.getOptions());
            ctw.compile();
            if (iterations > 1) {
                // Force a GC to encourage reclamation of nmethods when their InstalledCode
                // reference has been dropped.
                System.gc();
            }
        }
        // This is required as non-daemon threads can be started by class initializers
        System.exit(0);
    }
}
