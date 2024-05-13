/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.lang;

import java.io.PrintStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import static java.lang.StackWalker.Option.*;
import jdk.internal.access.JavaIOPrintStreamAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.InternalLock;
import jdk.internal.vm.Continuation;

/**
 * Helper class to print the virtual thread stack trace when pinned.
 *
 * The class maintains a ClassValue with the hashes of stack traces that are pinned by
 * code in that Class. This is used to avoid printing the same stack trace many times.
 */
class PinnedThreadPrinter {
    private static final JavaIOPrintStreamAccess JIOPSA = SharedSecrets.getJavaIOPrintStreamAccess();
    private static final StackWalker STACK_WALKER;
    static {
        var options = Set.of(SHOW_REFLECT_FRAMES, RETAIN_CLASS_REFERENCE);
        PrivilegedAction<StackWalker> pa = () ->
            LiveStackFrame.getStackWalker(options, VirtualThread.continuationScope());
        @SuppressWarnings("removal")
        var stackWalker = AccessController.doPrivileged(pa);
        STACK_WALKER = stackWalker;
    }

    private static final ClassValue<Hashes> HASHES = new ClassValue<>() {
        @Override
        protected Hashes computeValue(Class<?> type) {
            return new Hashes();
        }
    };

    @SuppressWarnings("serial")
    private static class Hashes extends LinkedHashMap<Integer, Boolean> {
        boolean add(int hash) {
            return (putIfAbsent(hash, Boolean.TRUE) == null);
        }
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> oldest) {
            // limit number of hashes
            return size() > 8;
        }
    }

    /**
     * Returns a hash of the given stack trace. The hash is based on the class,
     * method and bytecode index.
     */
    private static int hash(List<LiveStackFrame> stack) {
        int hash = 0;
        for (LiveStackFrame frame : stack) {
            hash = (31 * hash) + Objects.hash(frame.getDeclaringClass(),
                    frame.getMethodName(),
                    frame.getByteCodeIndex());
        }
        return hash;
    }

    /**
     * Returns true if the frame is native, a class initializer, or holds monitors.
     */
    private static boolean isInterestingFrame(LiveStackFrame f) {
        return f.isNativeMethod()
                || "<clinit>".equals(f.getMethodName())
                || (f.getMonitors().length > 0);
    }

    /**
     * Prints the current thread's stack trace.
     *
     * @param printAll true to print all stack frames, false to only print the
     *        frames that are native or holding a monitor
     */
    static void printStackTrace(PrintStream out, Continuation.Pinned reason, boolean printAll) {
        List<LiveStackFrame> stack = STACK_WALKER.walk(s ->
            s.map(f -> (LiveStackFrame) f)
                    .filter(f -> f.getDeclaringClass() != PinnedThreadPrinter.class)
                    .collect(Collectors.toList())
        );
        Object lockObj = JIOPSA.lock(out);
        if (lockObj instanceof InternalLock lock && lock.tryLock()) {
            try {
                // find the closest frame that is causing the thread to be pinned
                stack.stream()
                    .filter(f -> isInterestingFrame(f))
                    .map(LiveStackFrame::getDeclaringClass)
                    .findFirst()
                    .ifPresentOrElse(klass -> {
                        // print the stack trace if not already seen
                        int hash = hash(stack);
                        if (HASHES.get(klass).add(hash)) {
                            printStackTrace(out, reason, stack, printAll);
                        }
                    }, () -> printStackTrace(out, reason, stack, true));  // not found

            } finally {
                lock.unlock();
            }
        }
    }

    private static void printStackTrace(PrintStream out,
                                        Continuation.Pinned reason,
                                        List<LiveStackFrame> stack,
                                        boolean printAll) {
        out.format("%s reason:%s%n", Thread.currentThread(), reason);
        for (LiveStackFrame frame : stack) {
            var ste = frame.toStackTraceElement();
            int monitorCount = frame.getMonitors().length;
            if (monitorCount > 0) {
                out.format("    %s <== monitors:%d%n", ste, monitorCount);
            } else if (printAll || isInterestingFrame(frame)) {
                out.format("    %s%n", ste);
            }
        }
    }
}
