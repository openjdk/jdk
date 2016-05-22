/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionEnv;
import jdk.jshell.spi.SPIResolutionException;
import jdk.jshell.EvalException;
import jdk.jshell.UnresolvedReferenceException;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An implementation of ExecutionControl which executes in the same JVM as the
 * JShell core.
 *
 * @author Grigory Ptashko
 */
class LocalExecutionControl implements ExecutionControl {
    private class REPLClassLoader extends URLClassLoader {
        REPLClassLoader() {
            super(new URL[0]);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            debug("findClass %s\n", name);
            byte[] b = execEnv.getClassBytes(name);
            if (b == null) {
                return super.findClass(name);
            }
            return super.defineClass(name, b, 0, b.length, (CodeSource)null);
        }

        @Override
        public void addURL(URL url) {
            super.addURL(url);
        }
    }

    private ExecutionEnv execEnv;
    private final Object STOP_LOCK = new Object();
    private boolean userCodeRunning = false;
    private REPLClassLoader loader = new REPLClassLoader();
    private final Map<String, Class<?>> klasses = new TreeMap<>();
    private final Map<String, byte[]> classBytes = new HashMap<>();
    private ThreadGroup execThreadGroup;

    @Override
    public void start(ExecutionEnv execEnv) throws Exception {
        this.execEnv = execEnv;

        debug("Process-local code snippets execution control started");
    }

    @Override
    public void close() {
    }

    @Override
    public boolean load(Collection<String> classes) {
        try {
            loadLocal(classes);

            return true;
        } catch (ClassNotFoundException | ClassCastException ex) {
            debug(ex, "Exception on load operation");
        }

        return false;
    }

    @Override
    public String invoke(String classname, String methodname) throws EvalException, UnresolvedReferenceException {
        try {
            synchronized (STOP_LOCK) {
                userCodeRunning = true;
            }

            // Invoke executable entry point in loaded code
            Class<?> klass = klasses.get(classname);
            if (klass == null) {
                debug("Invoke failure: no such class loaded %s\n", classname);

                return "";
            }

            Method doitMethod;
            try {
                this.getClass().getModule().addReads(klass.getModule());
                this.getClass().getModule().addExports(SPIResolutionException.class.getPackage()
                        .getName(), klass.getModule());
                doitMethod = klass.getDeclaredMethod(methodname, new Class<?>[0]);
                doitMethod.setAccessible(true);

                execThreadGroup = new ThreadGroup("JShell process local execution");

                AtomicReference<InvocationTargetException> iteEx = new AtomicReference<>();
                AtomicReference<IllegalAccessException> iaeEx = new AtomicReference<>();
                AtomicReference<NoSuchMethodException> nmeEx = new AtomicReference<>();
                AtomicReference<Boolean> stopped = new AtomicReference<>(false);

                Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                    if (e instanceof InvocationTargetException) {
                        if (e.getCause() instanceof ThreadDeath) {
                            stopped.set(true);
                        } else {
                            iteEx.set((InvocationTargetException)e);
                        }
                    } else if (e instanceof IllegalAccessException) {
                        iaeEx.set((IllegalAccessException)e);
                    } else if (e instanceof NoSuchMethodException) {
                        nmeEx.set((NoSuchMethodException)e);
                    } else if (e instanceof ThreadDeath) {
                        stopped.set(true);
                    }
                });

                final Object[] res = new Object[1];
                Thread snippetThread = new Thread(execThreadGroup, () -> {
                    try {
                        res[0] = doitMethod.invoke(null, new Object[0]);
                    } catch (InvocationTargetException e) {
                        if (e.getCause() instanceof ThreadDeath) {
                            stopped.set(true);
                        } else {
                            iteEx.set(e);
                        }
                    } catch (IllegalAccessException e) {
                        iaeEx.set(e);
                    } catch (ThreadDeath e) {
                        stopped.set(true);
                    }
                });

                snippetThread.start();
                Thread[] threadList = new Thread[execThreadGroup.activeCount()];
                execThreadGroup.enumerate(threadList);
                for (Thread thread : threadList) {
                    thread.join();
                }

                if (stopped.get()) {
                    debug("Killed.");

                    return "";
                }

                if (iteEx.get() != null) {
                    throw iteEx.get();
                } else if (nmeEx.get() != null) {
                    throw nmeEx.get();
                } else if (iaeEx.get() != null) {
                    throw iaeEx.get();
                }

                return valueString(res[0]);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                StackTraceElement[] elems = cause.getStackTrace();
                if (cause instanceof SPIResolutionException) {
                    int id = ((SPIResolutionException)cause).id();

                    throw execEnv.createUnresolvedReferenceException(id, elems);
                } else {
                    throw execEnv.createEvalException(cause.getMessage() == null ?
                            "<none>" : cause.getMessage(), cause.getClass().getName(), elems);
                }
            } catch (NoSuchMethodException | IllegalAccessException | InterruptedException ex) {
                debug(ex, "Invoke failure");
            }
        } finally {
            synchronized (STOP_LOCK) {
                userCodeRunning = false;
            }
        }

        return "";
    }

    @Override
    @SuppressWarnings("deprecation")
    public void stop() {
        synchronized (STOP_LOCK) {
            if (!userCodeRunning)
                return;

            if (execThreadGroup == null) {
                debug("Process-local code snippets thread group is null. Aborting stop.");

                return;
            }

            execThreadGroup.stop();
        }
    }

    @Override
    public String varValue(String classname, String varname) {
        Class<?> klass = klasses.get(classname);
        if (klass == null) {
            debug("Var value failure: no such class loaded %s\n", classname);

            return "";
        }
        try {
            this.getClass().getModule().addReads(klass.getModule());
            Field var = klass.getDeclaredField(varname);
            var.setAccessible(true);
            Object res = var.get(null);

            return valueString(res);
        } catch (Exception ex) {
            debug("Var value failure: no such field %s.%s\n", classname, varname);
        }

        return "";
    }

    @Override
    public boolean addToClasspath(String cp) {
        // Append to the claspath
        for (String path : cp.split(File.pathSeparator)) {
            try {
                loader.addURL(new File(path).toURI().toURL());
            } catch (MalformedURLException e) {
                throw new InternalError("Classpath addition failed: " + cp, e);
            }
        }

        return true;
    }

    @Override
    public boolean redefine(Collection<String> classes) {
        return false;
    }

    @Override
    public ClassStatus getClassStatus(String classname) {
        if (!classBytes.containsKey(classname)) {
            return ClassStatus.UNKNOWN;
        } else if (!Arrays.equals(classBytes.get(classname), execEnv.getClassBytes(classname))) {
            return ClassStatus.NOT_CURRENT;
        } else {
            return ClassStatus.CURRENT;
        }
    }

    private void loadLocal(Collection<String> classes) throws ClassNotFoundException {
        for (String className : classes) {
            Class<?> klass = loader.loadClass(className);
            klasses.put(className, klass);
            classBytes.put(className, execEnv.getClassBytes(className));
            klass.getDeclaredMethods();
        }
    }

    private void debug(String format, Object... args) {
        //debug(execEnv.state(), execEnv.userErr(), flags, format, args);
    }

    private void debug(Exception ex, String where) {
        //debug(execEnv.state(), execEnv.userErr(), ex, where);
    }

    private static String valueString(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + (String)value + "\"";
        } else if (value instanceof Character) {
            return "'" + value + "'";
        } else {
            return value.toString();
        }
    }
}
