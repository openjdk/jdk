/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.core.GraalCompilerOptions.CompilationBailoutAction;
import static org.graalvm.compiler.core.GraalCompilerOptions.CompilationFailureAction;
import static org.graalvm.compiler.core.test.ReflectionOptionDescriptors.extractEntries;
import static org.graalvm.compiler.debug.MemUseTrackerKey.getCurrentThreadAllocatedBytes;
import static org.graalvm.compiler.hotspot.test.CompileTheWorld.Options.DESCRIPTORS;
import static org.graalvm.compiler.serviceprovider.GraalServices.Java8OrEarlier;

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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.bytecode.Bytecodes;
import org.graalvm.compiler.core.CompilerThreadFactory;
import org.graalvm.compiler.core.test.ReflectionOptionDescriptors;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.MethodFilter;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.CompilationTask;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalCompiler;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.serviceprovider.GraalServices;

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

/**
 * This class implements compile-the-world functionality with JVMCI.
 */
public final class CompileTheWorld {

    /**
     * Magic token to denote that JDK classes are to be compiled. If
     * {@link GraalServices#Java8OrEarlier}, then the classes in {@code rt.jar} are compiled.
     * Otherwise the classes in the Java runtime image are compiled.
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
        if (options != null) {
            EconomicMap<String, String> optionSettings = EconomicMap.create();
            for (String optionSetting : options.split("\\s+|#")) {
                OptionsParser.parseOptionSettingTo(optionSetting, optionSettings);
            }
            EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
            ServiceLoader<OptionDescriptors> loader = ServiceLoader.load(OptionDescriptors.class, OptionDescriptors.class.getClassLoader());
            OptionsParser.parseOptions(optionSettings, values, loader);
            return values;
        }
        return EconomicMap.create();
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

    /** Only compile methods matching one of the filters in this array if the array is non-null. */
    private final MethodFilter[] methodFilters;

    /** Exclude methods matching one of the filters in this array if the array is non-null. */
    private final MethodFilter[] excludeMethodFilters;

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

    private OptionValues currentOptions;
    private final UnmodifiableEconomicMap<OptionKey<?>, Object> compilationOptions;

    /**
     * Creates a compile-the-world instance.
     *
     * @param files {@link File#pathSeparator} separated list of Zip/Jar files to compile
     * @param startAt index of the class file to start compilation at
     * @param stopAt index of the class file to stop compilation at
     * @param methodFilters
     * @param excludeMethodFilters
     */
    public CompileTheWorld(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalCompiler compiler, String files, int startAt, int stopAt, String methodFilters, String excludeMethodFilters,
                    boolean verbose, OptionValues initialOptions, EconomicMap<OptionKey<?>, Object> compilationOptions) {
        this.jvmciRuntime = jvmciRuntime;
        this.compiler = compiler;
        this.inputClassPath = files;
        this.startAt = startAt;
        this.stopAt = stopAt;
        this.methodFilters = methodFilters == null || methodFilters.isEmpty() ? null : MethodFilter.parse(methodFilters);
        this.excludeMethodFilters = excludeMethodFilters == null || excludeMethodFilters.isEmpty() ? null : MethodFilter.parse(excludeMethodFilters);
        this.verbose = verbose;
        this.currentOptions = initialOptions;

        // Copy the initial options and add in any extra options
        EconomicMap<OptionKey<?>, Object> compilationOptionsCopy = EconomicMap.create(initialOptions.getMap());
        compilationOptionsCopy.putAll(compilationOptions);

        // We want to see stack traces when a method fails to compile
        CompilationBailoutAction.putIfAbsent(compilationOptionsCopy, Print);
        CompilationFailureAction.putIfAbsent(compilationOptionsCopy, Print);

        // By default only report statistics for the CTW threads themselves
        DebugOptions.MetricsThreadFilter.putIfAbsent(compilationOptionsCopy, "^CompileTheWorld");
        this.compilationOptions = compilationOptionsCopy;
    }

    public CompileTheWorld(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalCompiler compiler, OptionValues options) {
        this(jvmciRuntime, compiler, Options.Classpath.getValue(options),
                        Options.StartAt.getValue(options),
                        Options.StopAt.getValue(options),
                        Options.MethodFilter.getValue(options),
                        Options.ExcludeMethodFilter.getValue(options),
                        Options.Verbose.getValue(options),
                        options,
                        parseOptions(Options.Config.getValue(options)));
    }

    /**
     * Compiles all methods in all classes in {@link #inputClassPath}. If {@link #inputClassPath}
     * equals {@link #SUN_BOOT_CLASS_PATH} the boot classes are used.
     */
    public void compile() throws Throwable {
        if (SUN_BOOT_CLASS_PATH.equals(inputClassPath)) {
            String bcpEntry = null;
            if (Java8OrEarlier) {
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
            compile(bcpEntry);
        } else {
            compile(inputClassPath);
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
        if (methodFilters != null && !MethodFilter.matchesClassName(methodFilters, className)) {
            return false;
        }
        if (excludeMethodFilters != null && MethodFilter.matchesClassName(excludeMethodFilters, className)) {
            return false;
        }
        return true;
    }

    /**
     * Compiles all methods in all classes in a given class path.
     *
     * @param classPath class path denoting classes to compile
     * @throws IOException
     */
    @SuppressWarnings("try")
    private void compile(String classPath) throws IOException {
        final String[] entries = classPath.split(File.pathSeparator);
        long start = System.currentTimeMillis();
        Map<Thread, StackTraceElement[]> initialThreads = Thread.getAllStackTraces();

        try {
            // compile dummy method to get compiler initialized outside of the
            // config debug override.
            HotSpotResolvedJavaMethod dummyMethod = (HotSpotResolvedJavaMethod) JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess().lookupJavaMethod(
                            CompileTheWorld.class.getDeclaredMethod("dummy"));
            int entryBCI = JVMCICompiler.INVOCATION_ENTRY_BCI;
            boolean useProfilingInfo = false;
            boolean installAsDefault = false;
            CompilationTask task = new CompilationTask(jvmciRuntime, compiler, new HotSpotCompilationRequest(dummyMethod, entryBCI, 0L), useProfilingInfo, installAsDefault, currentOptions);
            task.runCompilation();
        } catch (NoSuchMethodException | SecurityException e1) {
            printStackTrace(e1);
        }

        /*
         * Always use a thread pool, even for single threaded mode since it simplifies the use of
         * DebugValueThreadFilter to filter on the thread names.
         */
        int threadCount = 1;
        if (Options.MultiThreaded.getValue(currentOptions)) {
            threadCount = Options.Threads.getValue(currentOptions);
            if (threadCount == 0) {
                threadCount = Runtime.getRuntime().availableProcessors();
            }
        } else {
            running = true;
        }

        OptionValues savedOptions = currentOptions;
        currentOptions = new OptionValues(compilationOptions);
        threadPool = new ThreadPoolExecutor(threadCount, threadCount, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new CompilerThreadFactory("CompileTheWorld"));

        try {
            for (int i = 0; i < entries.length; i++) {
                final String entry = entries[i];

                ClassPathEntry cpe;
                if (entry.endsWith(".zip") || entry.endsWith(".jar")) {
                    cpe = new JarClassPathEntry(entry);
                } else if (entry.equals(JRT_CLASS_PATH_ENTRY)) {
                    cpe = new JRTClassPathEntry(entry, Options.LimitModules.getValue(currentOptions));
                } else {
                    if (!new File(entry).isDirectory()) {
                        println("CompileTheWorld : Skipped classes in " + entry);
                        println();
                        continue;
                    }
                    cpe = new DirClassPathEntry(entry);
                }

                if (methodFilters == null || methodFilters.length == 0) {
                    println("CompileTheWorld : Compiling all classes in " + entry);
                } else {
                    String include = Arrays.asList(methodFilters).stream().map(MethodFilter::toString).collect(Collectors.joining(", "));
                    println("CompileTheWorld : Compiling all methods in " + entry + " matching one of the following filters: " + include);
                }
                if (excludeMethodFilters != null && excludeMethodFilters.length > 0) {
                    String exclude = Arrays.asList(excludeMethodFilters).stream().map(MethodFilter::toString).collect(Collectors.joining(", "));
                    println("CompileTheWorld : Excluding all methods matching one of the following filters: " + exclude);
                }
                println();

                ClassLoader loader = cpe.createClassLoader();

                for (String className : cpe.getClassNames()) {

                    // Are we done?
                    if (classFileCounter >= stopAt) {
                        break;
                    }

                    classFileCounter++;

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
                        if (classFileCounter >= startAt) {
                            println("CompileTheWorld (%d) : %s", classFileCounter, className);

                            // Compile each constructor/method in the class.
                            for (Constructor<?> constructor : javaClass.getDeclaredConstructors()) {
                                HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaMethod(constructor);
                                if (canBeCompiled(javaMethod, constructor.getModifiers())) {
                                    compileMethod(javaMethod);
                                }
                            }
                            for (Method method : javaClass.getDeclaredMethods()) {
                                HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaMethod(method);
                                if (canBeCompiled(javaMethod, method.getModifiers())) {
                                    compileMethod(javaMethod);
                                }
                            }

                            // Also compile the class initializer if it exists
                            HotSpotResolvedJavaMethod clinit = (HotSpotResolvedJavaMethod) metaAccess.lookupJavaType(javaClass).getClassInitializer();
                            if (clinit != null && canBeCompiled(clinit, clinit.getModifiers())) {
                                compileMethod(clinit);
                            }
                        }
                    } catch (Throwable t) {
                        if (isClassIncluded(className)) {
                            println("CompileTheWorld (%d) : Skipping %s %s", classFileCounter, className, t.toString());
                            printStackTrace(t);
                        }
                    }
                }
                cpe.close();
            }
        } finally {
            currentOptions = savedOptions;
        }

        if (!running) {
            startThreads();
        }
        int wakeups = 0;
        while (threadPool.getCompletedTaskCount() != threadPool.getTaskCount()) {
            if (wakeups % 15 == 0) {
                TTY.println("CompileTheWorld : Waiting for " + (threadPool.getTaskCount() - threadPool.getCompletedTaskCount()) + " compiles");
            }
            try {
                threadPool.awaitTermination(1, TimeUnit.SECONDS);
                wakeups++;
            } catch (InterruptedException e) {
            }
        }
        threadPool = null;

        long elapsedTime = System.currentTimeMillis() - start;

        println();
        if (Options.MultiThreaded.getValue(currentOptions)) {
            TTY.println("CompileTheWorld : Done (%d classes, %d methods, %d ms elapsed, %d ms compile time, %d bytes of memory used)", classFileCounter, compiledMethodsCounter.get(), elapsedTime,
                            compileTime.get(), memoryUsed.get());
        } else {
            TTY.println("CompileTheWorld : Done (%d classes, %d methods, %d ms, %d bytes of memory used)", classFileCounter, compiledMethodsCounter.get(), compileTime.get(), memoryUsed.get());
        }

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
    private void compileMethod(HotSpotResolvedJavaMethod method) throws InterruptedException, ExecutionException {
        if (methodFilters != null && !MethodFilter.matches(methodFilters, method)) {
            return;
        }
        if (excludeMethodFilters != null && MethodFilter.matches(excludeMethodFilters, method)) {
            return;
        }
        Future<?> task = threadPool.submit(new Runnable() {
            @Override
            public void run() {
                waitToRun();
                OptionValues savedOptions = currentOptions;
                currentOptions = new OptionValues(compilationOptions);
                try {
                    compileMethod(method, classFileCounter);
                } finally {
                    currentOptions = savedOptions;
                }
            }
        });
        if (threadPool.getCorePoolSize() == 1) {
            task.get();
        }
    }

    /**
     * Compiles a method and gathers some statistics.
     */
    private void compileMethod(HotSpotResolvedJavaMethod method, int counter) {
        try {
            long start = System.currentTimeMillis();
            long allocatedAtStart = getCurrentThreadAllocatedBytes();
            int entryBCI = JVMCICompiler.INVOCATION_ENTRY_BCI;
            HotSpotCompilationRequest request = new HotSpotCompilationRequest(method, entryBCI, 0L);
            // For more stable CTW execution, disable use of profiling information
            boolean useProfilingInfo = false;
            boolean installAsDefault = false;
            CompilationTask task = new CompilationTask(jvmciRuntime, compiler, request, useProfilingInfo, installAsDefault, currentOptions);
            task.runCompilation();

            // Invalidate the generated code so the code cache doesn't fill up
            HotSpotInstalledCode installedCode = task.getInstalledCode();
            if (installedCode != null) {
                installedCode.invalidate();
            }

            memoryUsed.getAndAdd(getCurrentThreadAllocatedBytes() - allocatedAtStart);
            compileTime.getAndAdd(System.currentTimeMillis() - start);
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
            println(verbose || methodFilters != null,
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
        // @formatter:off
        public static final OptionKey<Boolean> Help = new OptionKey<>(false);
        public static final OptionKey<String> Classpath = new OptionKey<>(CompileTheWorld.SUN_BOOT_CLASS_PATH);
        public static final OptionKey<Boolean> Verbose = new OptionKey<>(true);
        /**
         * Ignore Graal classes by default to avoid problems associated with compiling
         * snippets and method substitutions.
         */
        public static final OptionKey<String> LimitModules = new OptionKey<>("~jdk.internal.vm.compiler");
        public static final OptionKey<Integer> Iterations = new OptionKey<>(1);
        public static final OptionKey<String> MethodFilter = new OptionKey<>(null);
        public static final OptionKey<String> ExcludeMethodFilter = new OptionKey<>(null);
        public static final OptionKey<Integer> StartAt = new OptionKey<>(1);
        public static final OptionKey<Integer> StopAt = new OptionKey<>(Integer.MAX_VALUE);
        public static final OptionKey<String> Config = new OptionKey<>(null);
        public static final OptionKey<Boolean> MultiThreaded = new OptionKey<>(false);
        public static final OptionKey<Integer> Threads = new OptionKey<>(0);

        static final ReflectionOptionDescriptors DESCRIPTORS = new ReflectionOptionDescriptors(Options.class,
                           "Help", "List options and their help messages and then exit.",
                      "Classpath", "Class path denoting methods to compile. Default is to compile boot classes.",
                        "Verbose", "Verbose operation.",
                   "LimitModules", "Comma separated list of module names to which compilation should be limited. " +
                                   "Module names can be prefixed with \"~\" to exclude the named module.",
                     "Iterations", "The number of iterations to perform.",
                   "MethodFilter", "Only compile methods matching this filter.",
            "ExcludeMethodFilter", "Exclude methods matching this filter from compilation.",
                        "StartAt", "First class to consider for compilation.",
                         "StopAt", "Last class to consider for compilation.",
                         "Config", "Option value overrides to use during compile the world. For example, " +
                                   "to disable inlining and partial escape analysis specify 'PartialEscapeAnalysis=false Inline=false'. " +
                                   "The format for each option is the same as on the command line just without the '-Dgraal.' prefix.",
                  "MultiThreaded", "Run using multiple threads for compilation.",
                        "Threads", "Number of threads to use for multithreaded execution. Defaults to Runtime.getRuntime().availableProcessors().");
        // @formatter:on
    }

    public static OptionValues loadOptions(OptionValues initialValues) {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        List<OptionDescriptors> loader = singletonList(DESCRIPTORS);
        OptionsParser.parseOptions(extractEntries(System.getProperties(), "CompileTheWorld.", true), values, loader);
        OptionValues options = new OptionValues(initialValues, values);
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
        OptionValues options = loadOptions(graalRuntime.getOptions());

        int iterations = Options.Iterations.getValue(options);
        for (int i = 0; i < iterations; i++) {
            codeCache.resetCompilationStatistics();
            TTY.println("CompileTheWorld : iteration " + i);

            CompileTheWorld ctw = new CompileTheWorld(jvmciRuntime, compiler, options);
            ctw.compile();
        }
        // This is required as non-daemon threads can be started by class initializers
        System.exit(0);
    }
}
