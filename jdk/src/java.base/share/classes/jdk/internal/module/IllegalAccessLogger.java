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

import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import static java.util.Collections.*;

import jdk.internal.misc.JavaLangAccess;
import jdk.internal.misc.SharedSecrets;

/**
 * Supports logging of access to members of exported and concealed packages
 * that are opened to code in unnamed modules for illegal access.
 */

public final class IllegalAccessLogger {

    /**
     * Logger modes
     */
    public static enum Mode {
        /**
         * Prints a warning when an illegal access succeeds and then
         * discards the logger so that there is no further output.
         */
        ONESHOT,
        /**
         * Print warnings when illegal access succeeds
         */
        WARN,
        /**
         * Prints warnings and a stack trace when illegal access succeeds
         */
        DEBUG,
    }

    /**
     * A builder for IllegalAccessLogger objects.
     */
    public static class Builder {
        private final Mode mode;
        private final PrintStream warningStream;
        private final Map<Module, Set<String>> moduleToConcealedPackages;
        private final Map<Module, Set<String>> moduleToExportedPackages;
        private boolean complete;

        private void ensureNotComplete() {
            if (complete) throw new IllegalStateException();
        }

        /**
         * Creates a builder.
         */
        public Builder(Mode mode, PrintStream warningStream) {
            this.mode = mode;
            this.warningStream = warningStream;
            this.moduleToConcealedPackages = new HashMap<>();
            this.moduleToExportedPackages = new HashMap<>();
        }

        /**
         * Adding logging of reflective-access to any member of a type in
         * otherwise concealed packages.
         */
        public Builder logAccessToConcealedPackages(Module m, Set<String> packages) {
            ensureNotComplete();
            moduleToConcealedPackages.put(m, unmodifiableSet(packages));
            return this;
        }

        /**
         * Adding logging of reflective-access to non-public members/types in
         * otherwise exported (not open) packages.
         */
        public Builder logAccessToExportedPackages(Module m, Set<String> packages) {
            ensureNotComplete();
            moduleToExportedPackages.put(m, unmodifiableSet(packages));
            return this;
        }

        /**
         * Builds the IllegalAccessLogger and sets it as the system-wise logger.
         */
        public void complete() {
            Map<Module, Set<String>> map1 = unmodifiableMap(moduleToConcealedPackages);
            Map<Module, Set<String>> map2 = unmodifiableMap(moduleToExportedPackages);
            logger = new IllegalAccessLogger(mode, warningStream, map1, map2);
            complete = true;
        }
    }

    // need access to java.lang.Module
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    // system-wide IllegalAccessLogger
    private static volatile IllegalAccessLogger logger;

    // logger mode
    private final Mode mode;

    // the print stream to send the warnings
    private final PrintStream warningStream;

    // module -> packages open for illegal access
    private final Map<Module, Set<String>> moduleToConcealedPackages;
    private final Map<Module, Set<String>> moduleToExportedPackages;

    // caller -> usages
    private final Map<Class<?>, Usages> callerToUsages = new WeakHashMap<>();

    private IllegalAccessLogger(Mode mode,
                                PrintStream warningStream,
                                Map<Module, Set<String>> moduleToConcealedPackages,
                                Map<Module, Set<String>> moduleToExportedPackages)
    {
        this.mode = mode;
        this.warningStream = warningStream;
        this.moduleToConcealedPackages = moduleToConcealedPackages;
        this.moduleToExportedPackages = moduleToExportedPackages;
    }

    /**
     * Returns the system-wide IllegalAccessLogger or {@code null} if there is
     * no logger.
     */
    public static IllegalAccessLogger illegalAccessLogger() {
        return logger;
    }

    /**
     * Returns true if the module exports a concealed package for illegal
     * access.
     */
    public boolean isExportedForIllegalAccess(Module module, String pn) {
        Set<String> packages = moduleToConcealedPackages.get(module);
        if (packages != null && packages.contains(pn))
            return true;
        return false;
    }

    /**
     * Returns true if the module opens a concealed or exported package for
     * illegal access.
     */
    public boolean isOpenForIllegalAccess(Module module, String pn) {
        if (isExportedForIllegalAccess(module, pn))
            return true;
        Set<String> packages = moduleToExportedPackages.get(module);
        if (packages != null && packages.contains(pn))
            return true;
        return false;
    }

    /**
     * Logs access to the member of a target class by a caller class if the class
     * is in a package that is exported for illegal access.
     *
     * The {@code whatSupplier} supplies the message that describes the member.
     */
    public void logIfExportedForIllegalAccess(Class<?> caller,
                                              Class<?> target,
                                              Supplier<String> whatSupplier) {
        Module targetModule = target.getModule();
        String targetPackage = target.getPackageName();
        if (isExportedForIllegalAccess(targetModule, targetPackage)) {
            Module callerModule = caller.getModule();
            if (!JLA.isReflectivelyExported(targetModule, targetPackage, callerModule)) {
                log(caller, whatSupplier.get());
            }
        }
    }

    /**
     * Logs access to the member of a target class by a caller class if the class
     * is in a package that is opened for illegal access.
     *
     * The {@code what} parameter supplies the message that describes the member.
     */
    public void logIfOpenedForIllegalAccess(Class<?> caller,
                                            Class<?> target,
                                            Supplier<String> whatSupplier) {
        Module targetModule = target.getModule();
        String targetPackage = target.getPackageName();
        if (isOpenForIllegalAccess(targetModule, targetPackage)) {
            Module callerModule = caller.getModule();
            if (!JLA.isReflectivelyOpened(targetModule, targetPackage, callerModule)) {
                log(caller, whatSupplier.get());
            }
        }
    }

    /**
     * Logs access by caller lookup if the target class is in a package that is
     * opened for illegal access.
     */
    public void logIfOpenedForIllegalAccess(MethodHandles.Lookup caller, Class<?> target) {
        Module targetModule = target.getModule();
        String targetPackage = target.getPackageName();
        if (isOpenForIllegalAccess(targetModule, targetPackage)) {
            Class<?> callerClass = caller.lookupClass();
            Module callerModule = callerClass.getModule();
            if (!JLA.isReflectivelyOpened(targetModule, targetPackage, callerModule)) {
                URL url = codeSource(callerClass);
                final String source;
                if (url == null) {
                    source = callerClass.getName();
                } else {
                    source = callerClass.getName() + " (" + url + ")";
                }
                log(callerClass, target.getName(), () ->
                    "WARNING: Illegal reflective access using Lookup on " + source
                    + " to " + target);
            }
        }
    }

    /**
     * Logs access by a caller class. The {@code what} parameter describes
     * the member being accessed.
     */
    private void log(Class<?> caller, String what) {
        log(caller, what, () -> {
            URL url = codeSource(caller);
            String source = caller.getName();
            if (url != null)
                source += " (" + url + ")";
            return "WARNING: Illegal reflective access by " + source + " to " + what;
        });
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
        if (mode == Mode.ONESHOT) {
            synchronized (IllegalAccessLogger.class) {
                // discard the system wide logger
                if (logger == null)
                    return;
                logger = null;
            }
            warningStream.println(loudWarning(caller, msgSupplier));
            return;
        }

        // stack trace without the top-most frames in java.base
        List<StackWalker.StackFrame> stack = StackWalkerHolder.INSTANCE.walk(s ->
            s.dropWhile(this::isJavaBase)
             .limit(32)
             .collect(Collectors.toList())
        );

        // record usage if this is the first (or not recently recorded)
        Usage u = new Usage(what, hash(stack));
        boolean added;
        synchronized (this) {
            added = callerToUsages.computeIfAbsent(caller, k -> new Usages()).add(u);
        }

        // print warning if this is the first (or not a recent) usage
        if (added) {
            String msg = msgSupplier.get();
            if (mode == Mode.DEBUG) {
                StringBuilder sb = new StringBuilder(msg);
                stack.forEach(f ->
                    sb.append(System.lineSeparator()).append("\tat " + f)
                );
                msg = sb.toString();
            }
            warningStream.println(msg);
        }
    }

    /**
     * Returns the code source for the given class or null if there is no code source
     */
    private URL codeSource(Class<?> clazz) {
        PrivilegedAction<ProtectionDomain> pa = clazz::getProtectionDomain;
        CodeSource cs = AccessController.doPrivileged(pa).getCodeSource();
        return (cs != null) ? cs.getLocation() : null;
    }

    private String loudWarning(Class<?> caller,  Supplier<String> msgSupplier) {
        StringJoiner sj = new StringJoiner(System.lineSeparator());
        sj.add("WARNING: An illegal reflective access operation has occurred");
        sj.add(msgSupplier.get());
        sj.add("WARNING: Please consider reporting this to the maintainers of "
                + caller.getName());
        sj.add("WARNING: Use --illegal-access=warn to enable warnings of further"
                + " illegal reflective access operations");
        sj.add("WARNING: All illegal access operations will be denied in a"
                + " future release");
        return sj.toString();
    }

    private static class StackWalkerHolder {
        static final StackWalker INSTANCE;
        static {
            PrivilegedAction<StackWalker> pa = () ->
                StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
            INSTANCE = AccessController.doPrivileged(pa);
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

    @SuppressWarnings("serial")
    private static class Usages extends LinkedHashMap<Usage, Boolean> {
        Usages() { }
        boolean add(Usage u) {
            return (putIfAbsent(u, Boolean.TRUE) == null);
        }
        @Override
        protected boolean removeEldestEntry(Map.Entry<Usage, Boolean> oldest) {
            // prevent map growing too big, say where a utility class
            // is used by generated code to do illegal access
            return size() > 16;
        }
    }
}
