/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ReflectPermission;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.PropertyPermission;
import java.util.concurrent.Callable;

import jdk.internal.misc.Unsafe;
import jdk.internal.module.Modules;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.FlightRecorderListener;
import jdk.jfr.FlightRecorderPermission;
import jdk.jfr.Recording;

/**
 * Contains JFR code that does
 * {@link AccessController#doPrivileged(PrivilegedAction)}
 */
public final class SecuritySupport {
    private final static Unsafe unsafe = Unsafe.getUnsafe();
    private final static MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private final static Module JFR_MODULE = Event.class.getModule();
    public  final static SafePath JFC_DIRECTORY = getPathInProperty("java.home", "lib/jfr");

    static final SafePath USER_HOME = getPathInProperty("user.home", null);
    static final SafePath JAVA_IO_TMPDIR = getPathInProperty("java.io.tmpdir", null);

    final static class SecureRecorderListener implements FlightRecorderListener {

        private final AccessControlContext context;
        private final FlightRecorderListener changeListener;

        SecureRecorderListener(AccessControlContext context, FlightRecorderListener changeListener) {
            this.context = Objects.requireNonNull(context);
            this.changeListener = Objects.requireNonNull(changeListener);
        }

        @Override
        public void recordingStateChanged(Recording recording) {
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                try {
                    changeListener.recordingStateChanged(recording);
                } catch (Throwable t) {
                    // Prevent malicious user to propagate exception callback in the wrong context
                    Logger.log(LogTag.JFR, LogLevel.WARN, "Unexpected exception in listener " + changeListener.getClass()+ " at recording state change");
                }
                return null;
            }, context);
        }

        @Override
        public void recorderInitialized(FlightRecorder recorder) {
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                try  {
                    changeListener.recorderInitialized(recorder);
                } catch (Throwable t) {
                    // Prevent malicious user to propagate exception callback in the wrong context
                    Logger.log(LogTag.JFR, LogLevel.WARN, "Unexpected exception in listener " + changeListener.getClass()+ " when initializing FlightRecorder");
                }
                return null;
            }, context);
        }

        public FlightRecorderListener getChangeListener() {
            return changeListener;
        }
    }

    private static final class DirectoryCleaner extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
            Files.delete(path);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc != null) {
                throw exc;
            }
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * Path created by the default file provider,and not
     * a malicious provider.
     *
     */
    public static final class SafePath {
        private final Path path;
        private final String text;

        public SafePath(Path p) {
            // sanitize
            text = p.toString();
            path = Paths.get(text);
        }

        public SafePath(String path) {
            this(Paths.get(path));
        }

        public Path toPath() {
            return path;
        }

        public String toString() {
            return text;
        }
    }

    private interface RunnableWithCheckedException {
        public void run() throws Exception;
    }

    private interface CallableWithoutCheckException<T> {
        public T call();
    }

    private static <U> U doPrivilegedIOWithReturn(Callable<U> function) throws IOException {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<U>() {
                @Override
                public U run() throws Exception {
                    return function.call();
                }
            }, null);
        } catch (PrivilegedActionException e) {
            Throwable t = e.getCause();
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException("Unexpected error during I/O operation. " + t.getMessage(), t);
        }
    }

    private static void doPriviligedIO(RunnableWithCheckedException function) throws IOException {
        doPrivilegedIOWithReturn(() -> {
            function.run();
            return null;
        });
    }

    private static void doPrivileged(Runnable function, Permission... perms) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                function.run();
                return null;
            }
        }, null, perms);
    }

    private static void doPrivileged(Runnable function) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                function.run();
                return null;
            }
        });
    }

    private static <T> T doPrivilegedWithReturn(CallableWithoutCheckException<T> function, Permission... perms) {
        return AccessController.doPrivileged(new PrivilegedAction<T>() {
            @Override
            public T run() {
                return function.call();
            }
        }, null, perms);
    }

    public static List<SafePath> getPredefinedJFCFiles() {
        List<SafePath> list = new ArrayList<>();
        try {
            Iterator<Path> pathIterator = doPrivilegedIOWithReturn(() -> {
                return Files.newDirectoryStream(JFC_DIRECTORY.toPath(), "*").iterator();
            });
            while (pathIterator.hasNext()) {
                Path path = pathIterator.next();
                if (path.toString().endsWith(".jfc")) {
                    list.add(new SafePath(path));
                }
            }
        } catch (IOException ioe) {
            Logger.log(LogTag.JFR, LogLevel.WARN, "Could not access .jfc-files in " + JFC_DIRECTORY + ", " + ioe.getMessage());
        }
        return list;
    }

    static void makeVisibleToJFR(Class<?> clazz) {
        Module classModule = clazz.getModule();
        Modules.addReads(JFR_MODULE, classModule);
        if (clazz.getPackage() != null) {
            String packageName = clazz.getPackage().getName();
            Modules.addExports(classModule, packageName, JFR_MODULE);
            Modules.addOpens(classModule, packageName, JFR_MODULE);
        }
    }

    /**
     * Adds a qualified export of the internal.jdk.jfr.internal.handlers package
     * (for EventHandler)
     */
    static void addHandlerExport(Class<?> clazz) {
        Modules.addExports(JFR_MODULE, Utils.HANDLERS_PACKAGE_NAME, clazz.getModule());
    }

    public static void registerEvent(Class<? extends Event> eventClass) {
        doPrivileged(() -> FlightRecorder.register(eventClass), new FlightRecorderPermission(Utils.REGISTER_EVENT));
    }

    static boolean getBooleanProperty(String propertyName) {
        return doPrivilegedWithReturn(() -> Boolean.getBoolean(propertyName), new PropertyPermission(propertyName, "read"));
    }

    private static SafePath getPathInProperty(String prop, String subPath) {
        return doPrivilegedWithReturn(() -> {
            String path = System.getProperty(prop);
            if (path == null) {
                return null;
            }
            File file = subPath == null ? new File(path) : new File(path, subPath);
            return new SafePath(file.getAbsolutePath());
        }, new PropertyPermission("*", "read"));
    }

    // Called by JVM during initialization of JFR
    static Thread createRecorderThread(ThreadGroup systemThreadGroup, ClassLoader contextClassLoader) {
        // The thread should have permission = new Permission[0], and not "modifyThreadGroup" and "modifyThread" on the stack,
        // but it's hard circumvent if we are going to pass in system thread group in the constructor
        Thread thread = doPrivilegedWithReturn(() -> new Thread(systemThreadGroup, "JFR Recorder Thread"), new RuntimePermission("modifyThreadGroup"), new RuntimePermission("modifyThread"));
        doPrivileged(() -> thread.setContextClassLoader(contextClassLoader), new RuntimePermission("setContextClassLoader"), new RuntimePermission("modifyThread"));
        return thread;
    }

    static void registerShutdownHook(Thread shutdownHook) {
        doPrivileged(() -> Runtime.getRuntime().addShutdownHook(shutdownHook), new RuntimePermission("shutdownHooks"));
    }

    static void setUncaughtExceptionHandler(Thread thread, Thread.UncaughtExceptionHandler eh) {
        doPrivileged(() -> thread.setUncaughtExceptionHandler(eh), new RuntimePermission("modifyThread"));
    }

    static void moveReplace(SafePath from, SafePath to) throws IOException {
        doPrivilegedIOWithReturn(() -> Files.move(from.toPath(), to.toPath()));
    }

    static void clearDirectory(SafePath safePath) throws IOException {
        doPriviligedIO(() -> Files.walkFileTree(safePath.toPath(), new DirectoryCleaner()));
    }

    static SafePath toRealPath(SafePath safePath) throws Exception {
        return new SafePath(doPrivilegedIOWithReturn(() -> safePath.toPath().toRealPath()));
    }

    static boolean existDirectory(SafePath directory) throws IOException {
        return doPrivilegedIOWithReturn(() -> Files.exists(directory.toPath()));
    }

    static RandomAccessFile createRandomAccessFile(SafePath path) throws Exception {
        return doPrivilegedIOWithReturn(() -> new RandomAccessFile(path.toPath().toFile(), "rw"));
    }

    public static InputStream newFileInputStream(SafePath safePath) throws IOException {
        return doPrivilegedIOWithReturn(() -> Files.newInputStream(safePath.toPath()));
    }

    public static long getFileSize(SafePath safePath) throws IOException {
        return doPrivilegedIOWithReturn(() -> Files.size(safePath.toPath()));
    }

    static SafePath createDirectories(SafePath safePath) throws IOException {
        Path p = doPrivilegedIOWithReturn(() -> Files.createDirectories(safePath.toPath()));
        return new SafePath(p);
    }

    public static boolean exists(SafePath safePath) throws IOException {
        return doPrivilegedIOWithReturn(() -> Files.exists(safePath.toPath()));
    }

    public static boolean isDirectory(SafePath safePath) throws IOException {
        return doPrivilegedIOWithReturn(() -> Files.isDirectory(safePath.toPath()));
    }

    static void delete(SafePath localPath) throws IOException {
        doPriviligedIO(() -> Files.delete(localPath.toPath()));
    }

    static boolean isWritable(SafePath safePath) throws IOException {
        return doPrivilegedIOWithReturn(() -> Files.isWritable(safePath.toPath()));
    }

    static void deleteOnExit(SafePath safePath) {
        doPrivileged(() -> safePath.toPath().toFile().deleteOnExit());
    }

    static ReadableByteChannel newFileChannelToRead(SafePath safePath) throws IOException {
        return doPrivilegedIOWithReturn(() -> FileChannel.open(safePath.toPath(), StandardOpenOption.READ));
    }

    public static InputStream getResourceAsStream(String name) throws IOException {
        return doPrivilegedIOWithReturn(() -> SecuritySupport.class.getResourceAsStream(name));
    }

    public static Reader newFileReader(SafePath safePath) throws FileNotFoundException, IOException {
        return doPrivilegedIOWithReturn(() -> Files.newBufferedReader(safePath.toPath()));
    }

    static void touch(SafePath path) throws IOException {
        doPriviligedIO(() -> new RandomAccessFile(path.toPath().toFile(), "rw").close());
    }

    static void setAccessible(Method method) {
        doPrivileged(() -> method.setAccessible(true), new ReflectPermission("suppressAccessChecks"));
    }

    static void setAccessible(Field field) {
        doPrivileged(() -> field.setAccessible(true), new ReflectPermission("suppressAccessChecks"));
    }

    static void setAccessible(Constructor<?> constructor) {
        doPrivileged(() -> constructor.setAccessible(true), new ReflectPermission("suppressAccessChecks"));
    }

    static void ensureClassIsInitialized(Class<?> clazz) {
        unsafe.ensureClassInitialized(clazz);
    }

    static Class<?> defineClass(Class<?> lookupClass, byte[] bytes) {
        return AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public Class<?> run() {
                try {
                    return MethodHandles.privateLookupIn(lookupClass, LOOKUP).defineClass(bytes);
                } catch (IllegalAccessException e) {
                    throw new InternalError(e);
                }
            }
        });
    }

    static Thread createThreadWitNoPermissions(String threadName, Runnable runnable) {
        return doPrivilegedWithReturn(() -> new Thread(runnable, threadName), new Permission[0]);
    }

    static void setDaemonThread(Thread t, boolean daeomn) {
      doPrivileged(()-> t.setDaemon(daeomn), new RuntimePermission("modifyThread"));
    }

    public static SafePath getAbsolutePath(SafePath path) throws IOException {
        return new SafePath(doPrivilegedIOWithReturn((()-> path.toPath().toAbsolutePath())));
    }
}
