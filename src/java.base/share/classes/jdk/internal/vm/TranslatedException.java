/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.vm;

import jdk.internal.misc.VM;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Support for translating exceptions between the HotSpot heap and libjvmci heap.
 *
 * Successfully translated exceptions are wrapped in a TranslatedException instance.
 * This allows callers to distiguish between a translated exception and an error
 * that arose during translation.
 */
@SuppressWarnings("serial")
public final class TranslatedException extends Exception {

    /**
     * The value returned by {@link #encodeThrowable(Throwable)} when encoding
     * fails due to an {@link OutOfMemoryError}.
     */
    private static final byte[] FALLBACK_ENCODED_OUTOFMEMORYERROR_BYTES;

    /**
     * The value returned by {@link #encodeThrowable(Throwable)} when encoding
     * fails for any reason other than {@link OutOfMemoryError}.
     */
    private static final byte[] FALLBACK_ENCODED_THROWABLE_BYTES;
    static {
        maybeFailClinit();
        try {
            FALLBACK_ENCODED_THROWABLE_BYTES =
                encodeThrowable(translationFailure("error during encoding"), false);
            FALLBACK_ENCODED_OUTOFMEMORYERROR_BYTES =
                encodeThrowable(translationFailure("OutOfMemoryError during encoding"), false);
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    private static InternalError translationFailure(String messageFormat, Object... messageArgs) {
        return new InternalError(messageFormat.formatted(messageArgs));
    }

    /**
     * Helper to test exception translation.
     */
    private static void maybeFailClinit() {
        String className = VM.getSavedProperty("test.jvmci.TranslatedException.clinit.throw");
        if (className != null) {
            try {
                throw (Throwable) Class.forName(className).getDeclaredConstructor().newInstance();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }
    }

    TranslatedException(Throwable translated) {
        super(translated);
    }

    /**
     * No need to record an initial stack trace since
     * it will be manually overwritten.
     */
    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    /**
     * Prints a stack trace for {@code throwable} if the system property
     * {@code "jdk.internal.vm.TranslatedException.debug"} is true.
     */
    private static void debugPrintStackTrace(Throwable throwable, boolean debug) {
        if (debug) {
            System.err.print("DEBUG: ");
            throwable.printStackTrace();
        }
    }

    private static Throwable initCause(Throwable throwable, Throwable cause, boolean debug) {
        if (cause != null) {
            try {
                throwable.initCause(cause);
            } catch (IllegalStateException e) {
                // Cause could not be set or overwritten.
                debugPrintStackTrace(e, debug);
            }
        }
        return throwable;
    }

    private static Throwable create(String className, String message, Throwable cause, boolean debug) {
        // Try create with reflection first.
        try {
            Class<?> cls = Class.forName(className);
            if (cause != null) {
                // Handle known exception types whose cause must
                // be set in the constructor
                if (cls == InvocationTargetException.class) {
                    return new InvocationTargetException(cause, message);
                }
                if (cls == ExceptionInInitializerError.class) {
                    return new ExceptionInInitializerError(cause);
                }
            }
            if (message == null) {
                Constructor<?> cons = cls.getConstructor();
                return initCause((Throwable) cons.newInstance(), cause, debug);
            }
            Constructor<?> cons = cls.getDeclaredConstructor(String.class);
            return initCause((Throwable) cons.newInstance(message), cause, debug);
        } catch (Throwable translationFailure) {
            debugPrintStackTrace(translationFailure, debug);
            return initCause(translationFailure("%s [%s]", message, className), cause, debug);
        }
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String emptyAsNull(String value) {
        return value.isEmpty() ? null : value;
    }

    /**
     * Encodes {@code throwable} including its stack and causes as a {@linkplain GZIPOutputStream
     * compressed} byte array that can be decoded by {@link #decodeThrowable}.
     */
    static byte[] encodeThrowable(Throwable throwable) {
        try {
            return encodeThrowable(throwable, true);
        } catch (OutOfMemoryError e) {
            return FALLBACK_ENCODED_OUTOFMEMORYERROR_BYTES;
        } catch (Throwable e) {
            return FALLBACK_ENCODED_THROWABLE_BYTES;
        }
    }

    private static byte[] encodeThrowable(Throwable throwable,
                                          boolean withCauseAndStack) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(baos))) {
            List<Throwable> throwables = new ArrayList<>();
            for (Throwable current = throwable; current != null; current = current.getCause()) {
                throwables.add(current);
                if (!withCauseAndStack) {
                    break;
                }
            }

            // Encode from inner most cause outwards
            Collections.reverse(throwables);

            for (Throwable current : throwables) {
                dos.writeUTF(current.getClass().getName());
                dos.writeUTF(emptyIfNull(current.getMessage()));
                StackTraceElement[] stackTrace = withCauseAndStack ? current.getStackTrace() : null;
                if (stackTrace == null) {
                    stackTrace = new StackTraceElement[0];
                }
                dos.writeInt(stackTrace.length);
                for (int i = 0; i < stackTrace.length; i++) {
                    StackTraceElement frame = stackTrace[i];
                    if (frame != null) {
                        dos.writeUTF(emptyIfNull(frame.getClassLoaderName()));
                        dos.writeUTF(emptyIfNull(frame.getModuleName()));
                        dos.writeUTF(emptyIfNull(frame.getModuleVersion()));
                        dos.writeUTF(emptyIfNull(frame.getClassName()));
                        dos.writeUTF(emptyIfNull(frame.getMethodName()));
                        dos.writeUTF(emptyIfNull(frame.getFileName()));
                        dos.writeInt(frame.getLineNumber());
                    }
                }
            }
        }
        return baos.toByteArray();
    }

    /**
     * Gets the stack of the current thread as of the first native method. The chopped
     * frames are for the VM call to {@link VMSupport#decodeAndThrowThrowable}.
     */
    private static StackTraceElement[] getMyStackTrace() {
        Exception ex = new Exception();
        StackTraceElement[] stack = ex.getStackTrace();
        for (int i = 0; i < stack.length; i++) {
            StackTraceElement e = stack[i];
            if (e.isNativeMethod()) {
                return Arrays.copyOfRange(stack, i, stack.length);
            }
        }
        // This should never happen but since this is exception handling
        // code, be defensive instead raising a nested exception.
        return new StackTraceElement[0];
    }

    /**
     * Decodes {@code encodedThrowable} into a {@link TranslatedException}.
     *
     * @param encodedThrowable an encoded exception in the format specified by
     *            {@link #encodeThrowable}
     */
    static Throwable decodeThrowable(byte[] encodedThrowable, boolean debug) {
        ByteArrayInputStream bais = new ByteArrayInputStream(encodedThrowable);
        try (DataInputStream dis = new DataInputStream(new GZIPInputStream(bais))) {
            Throwable cause = null;
            Throwable throwable = null;
            StackTraceElement[] myStack = getMyStackTrace();
            while (dis.available() != 0) {
                String exceptionClassName = dis.readUTF();
                String exceptionMessage = emptyAsNull(dis.readUTF());
                throwable = create(exceptionClassName, exceptionMessage, cause, debug);
                int stackTraceDepth = dis.readInt();
                StackTraceElement[] stackTrace = new StackTraceElement[stackTraceDepth + myStack.length];
                int stackTraceIndex = 0;
                int myStackIndex = 0;
                for (int j = 0; j < stackTraceDepth; j++) {
                    String classLoaderName = emptyAsNull(dis.readUTF());
                    String moduleName = emptyAsNull(dis.readUTF());
                    String moduleVersion = emptyAsNull(dis.readUTF());
                    String className = emptyAsNull(dis.readUTF());
                    String methodName = emptyAsNull(dis.readUTF());
                    String fileName = emptyAsNull(dis.readUTF());
                    int lineNumber = dis.readInt();
                    StackTraceElement ste = new StackTraceElement(classLoaderName,
                                                                  moduleName,
                                                                  moduleVersion,
                                                                  className,
                                                                  methodName,
                                                                  fileName,
                                                                  lineNumber);

                    if (ste.isNativeMethod()) {
                        // Best effort attempt to weave stack traces from two heaps into
                        // a single stack trace using native method frames as stitching points.
                        // This is not 100% reliable as there's no guarantee that native method
                        // frames only exist for calls between HotSpot and libjvmci.
                        while (myStackIndex < myStack.length) {
                            StackTraceElement suffixSTE = myStack[myStackIndex++];
                            if (suffixSTE.isNativeMethod()) {
                                break;
                            }
                            stackTrace[stackTraceIndex++] = suffixSTE;
                        }
                    }
                    stackTrace[stackTraceIndex++] = ste;
                }
                while (myStackIndex < myStack.length) {
                    stackTrace[stackTraceIndex++] = myStack[myStackIndex++];
                }
                if (stackTraceIndex != stackTrace.length) {
                    // Remove null entries at end of stackTrace
                    stackTrace = Arrays.copyOf(stackTrace, stackTraceIndex);
                }
                throwable.setStackTrace(stackTrace);
                cause = throwable;
            }
            return new TranslatedException(throwable);
        } catch (Throwable translationFailure) {
            debugPrintStackTrace(translationFailure, debug);
            return translationFailure("error decoding exception: %s", encodedThrowable);
        }
    }
}
