/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.api.scripting;

import java.util.ArrayList;
import java.util.List;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.runtime.ECMAErrors;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * This is base exception for all Nashorn exceptions. These originate from
 * user's ECMAScript code. Example: script parse errors, exceptions thrown from
 * scripts. Note that ScriptEngine methods like "eval", "invokeMethod",
 * "invokeFunction" will wrap this as ScriptException and throw it. But, there
 * are cases where user may need to access this exception (or implementation
 * defined subtype of this). For example, if java interface is implemented by a
 * script object or Java access to script object properties via java.util.Map
 * interface. In these cases, user code will get an instance of this or
 * implementation defined subclass.
 */
@SuppressWarnings("serial")
public abstract class NashornException extends RuntimeException {
    // script file name
    private String fileName;
    // script line number
    private int line;
    // script column number
    private int column;
    // underlying ECMA error object - lazily initialized
    private Object ecmaError;

    /**
     * Constructor
     *
     * @param msg       exception message
     * @param fileName  file name
     * @param line      line number
     * @param column    column number
     */
    protected NashornException(final String msg, final String fileName, final int line, final int column) {
        this(msg, null, fileName, line, column);
    }

    /**
     * Constructor
     *
     * @param msg       exception message
     * @param cause     exception cause
     * @param fileName  file name
     * @param line      line number
     * @param column    column number
     */
    protected NashornException(final String msg, final Throwable cause, final String fileName, final int line, final int column) {
        super(msg, cause == null ? null : cause);
        this.fileName = fileName;
        this.line = line;
        this.column = column;
    }

    /**
     * Constructor
     *
     * @param msg       exception message
     * @param cause     exception cause
     */
    protected NashornException(final String msg, final Throwable cause) {
        super(msg, cause == null ? null : cause);
        // This is not so pretty - but it gets the job done. Note that the stack
        // trace has been already filled by "fillInStackTrace" call from
        // Throwable
        // constructor and so we don't pay additional cost for it.

        // Hard luck - no column number info
        this.column = -1;

        // Find the first JavaScript frame by walking and set file, line from it
        // Usually, we should be able to find it in just few frames depth.
        for (final StackTraceElement ste : getStackTrace()) {
            if (ECMAErrors.isScriptFrame(ste)) {
                // Whatever here is compiled from JavaScript code
                this.fileName = ste.getFileName();
                this.line = ste.getLineNumber();
                return;
            }
        }

        this.fileName = null;
        this.line = 0;
    }

    /**
     * Get the source file name for this {@code NashornException}
     *
     * @return the file name
     */
    public final String getFileName() {
        return fileName;
    }

    /**
     * Set the source file name for this {@code NashornException}
     *
     * @param fileName the file name
     */
    public final void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    /**
     * Get the line number for this {@code NashornException}
     *
     * @return the line number
     */
    public final int getLineNumber() {
        return line;
    }

    /**
     * Set the line number for this {@code NashornException}
     *
     * @param line the line number
     */
    public final void setLineNumber(final int line) {
        this.line = line;
    }

    /**
     * Get the column for this {@code NashornException}
     *
     * @return the column number
     */
    public final int getColumnNumber() {
        return column;
    }

    /**
     * Set the column for this {@code NashornException}
     *
     * @param column the column number
     */
    public final void setColumnNumber(final int column) {
        this.column = column;
    }

    /**
     * Returns array javascript stack frames from the given exception object.
     *
     * @param exception exception from which stack frames are retrieved and filtered
     * @return array of javascript stack frames
     */
    public static StackTraceElement[] getScriptFrames(final Throwable exception) {
        final StackTraceElement[] frames = exception.getStackTrace();
        final List<StackTraceElement> filtered = new ArrayList<>();
        for (final StackTraceElement st : frames) {
            if (ECMAErrors.isScriptFrame(st)) {
                final String className = "<" + st.getFileName() + ">";
                String methodName = st.getMethodName();
                if (methodName.equals(CompilerConstants.PROGRAM.symbolName())) {
                    methodName = "<program>";
                }

                if (methodName.contains(CompilerConstants.ANON_FUNCTION_PREFIX.symbolName())) {
                    methodName = "<anonymous>";
                }

                filtered.add(new StackTraceElement(className, methodName,
                        st.getFileName(), st.getLineNumber()));
            }
        }
        return filtered.toArray(new StackTraceElement[filtered.size()]);
    }

    /**
     * Return a formatted script stack trace string with frames information separated by '\n'
     *
     * @param exception exception for which script stack string is returned
     * @return formatted stack trace string
     */
    public static String getScriptStackString(final Throwable exception) {
        final StringBuilder buf = new StringBuilder();
        final StackTraceElement[] frames = getScriptFrames(exception);
        for (final StackTraceElement st : frames) {
            buf.append("\tat ");
            buf.append(st.getMethodName());
            buf.append(" (");
            buf.append(st.getFileName());
            buf.append(':');
            buf.append(st.getLineNumber());
            buf.append(")\n");
        }
        final int len = buf.length();
        // remove trailing '\n'
        if (len > 0) {
            assert buf.charAt(len - 1) == '\n';
            buf.deleteCharAt(len - 1);
        }
        return buf.toString();
    }

    /**
     * Get the thrown object. Subclass responsibility
     * @return thrown object
     */
    protected Object getThrown() {
        return null;
    }

    /**
     * Initialization function for ECMA errors. Stores the error
     * in the ecmaError field of this class. It is only initialized
     * once, and then reused
     *
     * @param global the global
     * @return initialized exception
     */
    protected NashornException initEcmaError(final ScriptObject global) {
        if (ecmaError != null) {
            return this; // initialized already!
        }

        final Object thrown = getThrown();
        if (thrown instanceof ScriptObject) {
            setEcmaError(ScriptObjectMirror.wrap(thrown, global));
        } else {
            setEcmaError(thrown);
        }

        return this;
    }

    /**
     * Return the underlying ECMA error object, if available.
     *
     * @return underlying ECMA Error object's mirror or whatever was thrown
     *         from script such as a String, Number or a Boolean.
     */
    public Object getEcmaError() {
        return ecmaError;
    }

    /**
     * Return the underlying ECMA error object, if available.
     *
     * @param ecmaError underlying ECMA Error object's mirror or whatever was thrown
     *         from script such as a String, Number or a Boolean.
     */
    public void setEcmaError(final Object ecmaError) {
        this.ecmaError = ecmaError;
    }
}
