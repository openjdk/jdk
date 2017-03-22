/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.module;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Module;
import java.net.URL;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Supports logging of access to members of API packages that are exported or
 * opened via backdoor mechanisms to code in unnamed modules.
 */

public final class IllegalAccessLogger {

    // true to print stack trace
    private static final boolean PRINT_STACK_TRACE;
    static {
        String s = System.getProperty("sun.reflect.debugModuleAccessChecks");
        PRINT_STACK_TRACE = "access".equals(s);
    }

    private static final StackWalker STACK_WALKER
        = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    // the maximum number of frames to capture
    private static final int MAX_STACK_FRAMES = 32;

    // lock to avoid interference when printing stack traces
    private static final Object OUTPUT_LOCK = new Object();

    // caller -> usages
    private final Map<Class<?>, Set<Usage>> callerToUsages = new WeakHashMap<>();

    // module -> (package name -> CLI option)
    private final Map<Module, Map<String, String>> exported;
    private final Map<Module, Map<String, String>> opened;

    private IllegalAccessLogger(Map<Module, Map<String, String>> exported,
                                Map<Module, Map<String, String>> opened) {
        this.exported = deepCopy(exported);
        this.opened = deepCopy(opened);
    }

    /**
     * Returns that a Builder that is seeded with the packages known to this logger.
     */
    public Builder toBuilder() {
        return new Builder(exported, opened);
    }

    /**
     * Logs access to the member of a target class by a caller class if the class
     * is in a package that is exported via a backdoor mechanism.
     *
     * The {@code whatSupplier} supplies the message that describes the member.
     */
    public void logIfExportedByBackdoor(Class<?> caller,
                                        Class<?> target,
                                        Supplier<String> whatSupplier) {
        Map<String, String> packages = exported.get(target.getModule());
        if (packages != null) {
            String how = packages.get(target.getPackageName());
            if (how != null) {
                log(caller, whatSupplier.get(), how);
            }
        }
    }

    /**
     * Logs access to the member of a target class by a caller class if the class
     * is in a package that is opened via a backdoor mechanism.
     *
     * The {@code what} parameter supplies the message that describes the member.
     */
    public void logIfOpenedByBackdoor(Class<?> caller,
                                      Class<?> target,
                                      Supplier<String> whatSupplier) {
        Map<String, String> packages = opened.get(target.getModule());
        if (packages != null) {
            String how = packages.get(target.getPackageName());
            if (how != null) {
                log(caller, whatSupplier.get(), how);
            }
        }
    }

    /**
     * Logs access by a caller class. The {@code what} parameter describes
     * the member is accessed, the {@code how} parameter is the means by which
     * access is allocated (CLI option for example).
     */
    private void log(Class<?> caller, String what, String how) {
        log(caller, what, () -> {
            PrivilegedAction<ProtectionDomain> pa = caller::getProtectionDomain;
            CodeSource cs = AccessController.doPrivileged(pa).getCodeSource();
            URL url = (cs != null) ? cs.getLocation() : null;
            String source = caller.getName();
            if (url != null)
                source += " (" + url + ")";
            return "WARNING: Illegal access by " + source + " to " + what
                    + " (permitted by " + how + ")";
        });
    }


    /**
     * Logs access to caller class if the class is in a package that is opened via
     * a backdoor mechanism.
     */
    public void logIfOpenedByBackdoor(MethodHandles.Lookup caller, Class<?> target) {
        Map<String, String> packages = opened.get(target.getModule());
        if (packages != null) {
            String how = packages.get(target.getPackageName());
            if (how != null) {
                log(caller.lookupClass(), target.getName(), () ->
                    "WARNING: Illegal access using Lookup on " + caller.lookupClass()
                    + " to " + target + " (permitted by " + how + ")");
            }
        }
    }

    /**
     * Log access by a caller. The {@code what} parameter describes the class or
     * member that is being accessed. The {@code msgSupplier} supplies the log
     * message.
     *
     * To reduce output, this method only logs the access if it hasn't been seen
     * previously. "Seen previously" is implemented as a map of caller class -> Usage,
     * where a Usage is the "what" and a hash of the stack trace. The map has weak
     * keys so it can be expunged when the caller is GC'ed/unloaded.
     */
    private void log(Class<?> caller, String what, Supplier<String> msgSupplier) {
        // stack trace without the top-most frames in java.base
        List<StackWalker.StackFrame> stack = STACK_WALKER.walk(s ->
            s.dropWhile(this::isJavaBase)
             .limit(MAX_STACK_FRAMES)
             .collect(Collectors.toList())
        );

        // check if the access has already been recorded
        Usage u = new Usage(what, hash(stack));
        boolean firstUsage;
        synchronized (this) {
            firstUsage = callerToUsages.computeIfAbsent(caller, k -> new HashSet<>()).add(u);
        }

        // log message if first usage
        if (firstUsage) {
            String msg = msgSupplier.get();
            if (PRINT_STACK_TRACE) {
                synchronized (OUTPUT_LOCK) {
                    System.err.println(msg);
                    stack.forEach(f -> System.err.println("\tat " + f));
                }
            } else {
                System.err.println(msg);
            }
        }
    }

    private static class Usage {
        private final String what;
        private final int stack;
        Usage(String what, int stack) {
            this.what = what;
            this.stack = stack;
        }
        @Override
        public int hashCode() {
            return what.hashCode() ^ stack;
        }
        @Override
        public boolean equals(Object ob) {
            if (ob instanceof Usage) {
                Usage that = (Usage)ob;
                return what.equals(that.what) && stack == (that.stack);
            } else {
                return false;
            }
        }
    }

    /**
     * Returns true if the stack frame is for a class in java.base.
     */
    private boolean isJavaBase(StackWalker.StackFrame frame) {
        Module caller = frame.getDeclaringClass().getModule();
        return "java.base".equals(caller.getName());
    }

    /**
     * Computes a hash code for the give stack frames. The hash code is based
     * on the class, method name, and BCI.
     */
    private int hash(List<StackWalker.StackFrame> stack) {
        int hash = 0;
        for (StackWalker.StackFrame frame : stack) {
            hash = (31 * hash) + Objects.hash(frame.getDeclaringClass(),
                                              frame.getMethodName(),
                                              frame.getByteCodeIndex());
        }
        return hash;
    }

    // system-wide IllegalAccessLogger
    private static volatile IllegalAccessLogger logger;

    /**
     * Sets the system-wide IllegalAccessLogger
     */
    public static void setIllegalAccessLogger(IllegalAccessLogger l) {
        if (l.exported.isEmpty() && l.opened.isEmpty()) {
            logger = null;
        } else {
            logger = l;
        }
    }

    /**
     * Returns the system-wide IllegalAccessLogger or {@code null} if there is
     * no logger.
     */
    public static IllegalAccessLogger illegalAccessLogger() {
        return logger;
    }

    /**
     * A builder for IllegalAccessLogger objects.
     */
    public static class Builder {
        private Map<Module, Map<String, String>> exported;
        private Map<Module, Map<String, String>> opened;

        public Builder() { }

        public Builder(Map<Module, Map<String, String>> exported,
                       Map<Module, Map<String, String>> opened) {
            this.exported = deepCopy(exported);
            this.opened = deepCopy(opened);
        }

        public void logAccessToExportedPackage(Module m, String pn, String how) {
            if (!m.isExported(pn)) {
                if (exported == null)
                    exported = new HashMap<>();
                exported.computeIfAbsent(m, k -> new HashMap<>()).putIfAbsent(pn, how);
            }
        }

        public void logAccessToOpenPackage(Module m, String pn, String how) {
            // opens implies exported at run-time.
            logAccessToExportedPackage(m, pn, how);

            if (!m.isOpen(pn)) {
                if (opened == null)
                    opened = new HashMap<>();
                opened.computeIfAbsent(m, k -> new HashMap<>()).putIfAbsent(pn, how);
            }
        }

        /**
         * Builds the logger.
         */
        public IllegalAccessLogger build() {
            return new IllegalAccessLogger(exported, opened);
        }
    }


    static Map<Module, Map<String, String>> deepCopy(Map<Module, Map<String, String>> map) {
        if (map == null || map.isEmpty()) {
            return new HashMap<>();
        } else {
            Map<Module, Map<String, String>> newMap = new HashMap<>();
            for (Map.Entry<Module, Map<String, String>> e : map.entrySet()) {
                newMap.put(e.getKey(), new HashMap<>(e.getValue()));
            }
            return newMap;
        }
    }
}
