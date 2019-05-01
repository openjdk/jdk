/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import java.util.Arrays;
import java.util.Formatter;
import java.util.Objects;

/**
 * Support for translating exceptions between different runtime heaps.
 */
@SuppressWarnings("serial")
final class TranslatedException extends Exception {

    private TranslatedException(String message) {
        super(message);
    }

    private TranslatedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * No need to record an initial stack trace since it will be manually overwritten.
     */
    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public String toString() {
        return getMessage();
    }

    private static TranslatedException create(String className, String message) {
        if (className.equals(TranslatedException.class.getName())) {
            // Chop the class name when boxing another TranslatedException
            return new TranslatedException(message);
        }
        if (message == null) {
            return new TranslatedException(className);
        }
        return new TranslatedException(className + ": " + message);
    }

    private static String encodedString(String value) {
        return Objects.toString(value, "").replace('|', '_');
    }

    /**
     * Encodes {@code throwable} including its stack and causes as a string. The encoding format of
     * a single exception with its cause is:
     *
     * <pre>
     * <exception class name> '|' <exception message> '|' <stack size> '|' [<class> '|' <method> '|' <file> '|' <line> '|' ]*
     * </pre>
     *
     * Each cause is appended after the exception is it the cause of.
     */
    @VMEntryPoint
    static String encodeThrowable(Throwable throwable) throws Throwable {
        try {
            Formatter enc = new Formatter();
            Throwable current = throwable;
            do {
                enc.format("%s|%s|", current.getClass().getName(), encodedString(current.getMessage()));
                StackTraceElement[] stackTrace = current.getStackTrace();
                if (stackTrace == null) {
                    stackTrace = new StackTraceElement[0];
                }
                enc.format("%d|", stackTrace.length);
                for (int i = 0; i < stackTrace.length; i++) {
                    StackTraceElement frame = stackTrace[i];
                    if (frame != null) {
                        enc.format("%s|%s|%s|%d|", frame.getClassName(), frame.getMethodName(),
                                        encodedString(frame.getFileName()), frame.getLineNumber());
                    }
                }
                current = current.getCause();
            } while (current != null);
            return enc.toString();
        } catch (Throwable e) {
            try {
                return e.getClass().getName() + "|" + encodedString(e.getMessage()) + "|0|";
            } catch (Throwable e2) {
                return "java.lang.Throwable|too many errors during encoding|0|";
            }
        }
    }

    /**
     * Gets the stack of the current thread without the frames between this call and the one just
     * below the frame of the first method in {@link CompilerToVM}. The chopped frames are specific
     * to the implementation of {@link HotSpotJVMCIRuntime#decodeThrowable(String)}.
     */
    private static StackTraceElement[] getStackTraceSuffix() {
        StackTraceElement[] stack = new Exception().getStackTrace();
        for (int i = 0; i < stack.length; i++) {
            StackTraceElement e = stack[i];
            if (e.getClassName().equals(CompilerToVM.class.getName())) {
                return Arrays.copyOfRange(stack, i, stack.length);
            }
        }
        // This should never happen but since we're in exception handling
        // code, just return a safe value instead raising a nested exception.
        return new StackTraceElement[0];
    }

    /**
     * Decodes {@code encodedThrowable} into a {@link TranslatedException}.
     *
     * @param encodedThrowable an encoded exception in the format specified by
     *            {@link #encodeThrowable}
     */
    @VMEntryPoint
    static Throwable decodeThrowable(String encodedThrowable) {
        try {
            int i = 0;
            String[] parts = encodedThrowable.split("\\|");
            Throwable parent = null;
            Throwable result = null;
            while (i != parts.length) {
                String exceptionClassName = parts[i++];
                String exceptionMessage = parts[i++];
                Throwable throwable = create(exceptionClassName, exceptionMessage);
                int stackTraceDepth = Integer.parseInt(parts[i++]);

                StackTraceElement[] suffix = parent == null ? new StackTraceElement[0] : getStackTraceSuffix();
                StackTraceElement[] stackTrace = new StackTraceElement[stackTraceDepth + suffix.length];
                for (int j = 0; j < stackTraceDepth; j++) {
                    String className = parts[i++];
                    String methodName = parts[i++];
                    String fileName = parts[i++];
                    int lineNumber = Integer.parseInt(parts[i++]);
                    if (fileName.isEmpty()) {
                        fileName = null;
                    }
                    stackTrace[j] = new StackTraceElement(className, methodName, fileName, lineNumber);
                }
                System.arraycopy(suffix, 0, stackTrace, stackTraceDepth, suffix.length);
                throwable.setStackTrace(stackTrace);
                if (parent != null) {
                    parent.initCause(throwable);
                } else {
                    result = throwable;
                }
                parent = throwable;
            }
            return result;
        } catch (Throwable t) {
            return new TranslatedException("Error decoding exception: " + encodedThrowable, t);
        }
    }
}
