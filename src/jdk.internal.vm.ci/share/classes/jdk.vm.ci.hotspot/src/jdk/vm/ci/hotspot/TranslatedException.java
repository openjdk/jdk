/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import jdk.vm.ci.common.JVMCIError;

/**
 * Support for translating exceptions between different runtime heaps.
 */
@SuppressWarnings("serial")
final class TranslatedException extends Exception {

    /**
     * The value returned by {@link #encodeThrowable(Throwable)} when encoding fails due to an
     * {@link OutOfMemoryError}.
     */
    private static final byte[] FALLBACK_ENCODED_OUTOFMEMORYERROR_BYTES;

    /**
     * The value returned by {@link #encodeThrowable(Throwable)} when encoding fails for any reason
     * other than {@link OutOfMemoryError}.
     */
    private static final byte[] FALLBACK_ENCODED_THROWABLE_BYTES;
    static {
        try {
            FALLBACK_ENCODED_THROWABLE_BYTES = encodeThrowable(new TranslatedException("error during encoding", "<unknown>"), false);
            FALLBACK_ENCODED_OUTOFMEMORYERROR_BYTES = encodeThrowable(new OutOfMemoryError(), false);
        } catch (IOException e) {
            throw new JVMCIError(e);
        }
    }

    /**
     * Class name of exception that could not be instantiated.
     */
    private String originalExceptionClassName;

    private TranslatedException(String message, String originalExceptionClassName) {
        super(message);
        this.originalExceptionClassName = originalExceptionClassName;
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
        String s;
        if (originalExceptionClassName.equals(TranslatedException.class.getName())) {
            s = getClass().getName();
        } else {
            s = getClass().getName() + "[" + originalExceptionClassName + "]";
        }
        String message = getMessage();
        return (message != null) ? (s + ": " + message) : s;
    }

    /**
     * Prints a stack trace for {@code throwable} and returns {@code true}. Used to print stack
     * traces only when assertions are enabled.
     */
    private static boolean printStackTrace(Throwable throwable) {
        throwable.printStackTrace();
        return true;
    }

    private static Throwable initCause(Throwable throwable, Throwable cause) {
        if (cause != null) {
            try {
                throwable.initCause(cause);
            } catch (IllegalStateException e) {
                // Cause could not be set or overwritten.
                assert printStackTrace(e);
            }
        }
        return throwable;
    }

    private static Throwable create(String className, String message, Throwable cause) {
        // Try create with reflection first.
        try {
            Class<?> cls = Class.forName(className);
            if (cause != null) {
                // Handle known exception types whose cause must be set in the constructor
                if (cls == InvocationTargetException.class) {
                    return new InvocationTargetException(cause, message);
                }
                if (cls == ExceptionInInitializerError.class) {
                    return new ExceptionInInitializerError(cause);
                }
            }
            if (message == null) {
                return initCause((Throwable) cls.getConstructor().newInstance(), cause);
            }
            return initCause((Throwable) cls.getDeclaredConstructor(String.class).newInstance(message), cause);
        } catch (Throwable translationFailure) {
            return initCause(new TranslatedException(message, className), cause);
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
    @VMEntryPoint
    static byte[] encodeThrowable(Throwable throwable) throws Throwable {
        try {
            return encodeThrowable(throwable, true);
        } catch (OutOfMemoryError e) {
            return FALLBACK_ENCODED_OUTOFMEMORYERROR_BYTES;
        } catch (Throwable e) {
            return FALLBACK_ENCODED_THROWABLE_BYTES;
        }
    }

    private static byte[] encodeThrowable(Throwable throwable, boolean withCauseAndStack) throws IOException {
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
     * Gets the stack of the current thread without the frames between this call and the one just
     * below the frame of the first method in {@link CompilerToVM}. The chopped frames are for the
     * VM call to {@link HotSpotJVMCIRuntime#decodeAndThrowThrowable}.
     */
    private static StackTraceElement[] getMyStackTrace() {
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
    static Throwable decodeThrowable(byte[] encodedThrowable) {
        try (DataInputStream dis = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(encodedThrowable)))) {
            Throwable cause = null;
            Throwable throwable = null;
            StackTraceElement[] myStack = getMyStackTrace();
            while (dis.available() != 0) {
                String exceptionClassName = dis.readUTF();
                String exceptionMessage = emptyAsNull(dis.readUTF());
                throwable = create(exceptionClassName, exceptionMessage, cause);
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
                    StackTraceElement ste = new StackTraceElement(classLoaderName, moduleName, moduleVersion, className, methodName, fileName, lineNumber);

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
                throwable.setStackTrace(stackTrace);
                cause = throwable;
            }
            return throwable;
        } catch (Throwable translationFailure) {
            assert printStackTrace(translationFailure);
            return new TranslatedException("Error decoding exception: " + encodedThrowable, translationFailure.getClass().getName());
        }
    }
}
