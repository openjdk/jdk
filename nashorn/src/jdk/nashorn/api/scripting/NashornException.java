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
    private final String fileName;
    // script line number
    private final int line;
    // script column number
    private final int column;

    /** script source name used for "engine.js" */
    public static final String ENGINE_SCRIPT_SOURCE_NAME = "nashorn:engine/resources/engine.js";

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
     * Get the line number for this {@code NashornException}
     *
     * @return the line number
     */
    public final int getLineNumber() {
        return line;
    }

    /**
     * Get the column for this {@code NashornException}
     *
     * @return the column
     */
    public final int getColumnNumber() {
        return column;
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
                if (methodName.equals(CompilerConstants.RUN_SCRIPT.symbolName())) {
                    methodName = "<program>";
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
}
