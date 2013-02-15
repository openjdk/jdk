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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.codegen.CompilerConstants.constructorNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.virtualField;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import javax.script.ScriptException;
import jdk.nashorn.api.scripting.NashornException;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.codegen.CompilerConstants.FieldAccess;
import jdk.nashorn.internal.scripts.JS$;

/**
 * Exception used to implement ECMAScript "throw" from scripts. The actual thrown
 * object from script need not be a Java exception and so it is wrapped as an
 * instance field called "thrown" here. This exception class is also used to
 * represent ECMA errors thrown from runtime code (for example, TypeError,
 * ReferenceError thrown from Nashorn engine runtime).
 */
@SuppressWarnings("serial")
public final class ECMAException extends NashornException {
    /**
     * Method handle pointing to the constructor {@link ECMAException#ECMAException(Object, String, int, int)},
     */
    public static final Call THROW_INIT = constructorNoLookup(ECMAException.class, Object.class, String.class, int.class, int.class);

    /** Field handle to the{@link ECMAException#thrown} field, so that it can be accessed from generated code */
    public static final FieldAccess THROWN = virtualField(ECMAException.class, "thrown", Object.class);

    private static final String EXCEPTION_PROPERTY = "nashornException";

    /** We assume that compiler generates script classes into the known package. */
    private static final String scriptPackage;
    static {
        String name = JS$.class.getName();
        scriptPackage = name.substring(0, name.lastIndexOf('.'));
    }

    /** Object thrown. */
    public final Object thrown;

    /**
     * Constructor. This is called from generated code to implement the {@code throw}
     * instruction from generated script code
     *
     * @param thrown    object to be thrown
     * @param fileName  script file name
     * @param line      line number of throw
     * @param column    column number of throw
     */
    public ECMAException(final Object thrown, final String fileName, final int line, final int column) {
        super(ScriptRuntime.safeToString(thrown), asThrowable(thrown));
        setFileName(fileName);
        setLineNumber(line);
        setColumnNumber(column);
        this.thrown = thrown;
        setExceptionToThrown();
    }

    /**
     * Constructor. This is called from runtime code in Nashorn to throw things like
     * type errors.
     *
     * @param thrown   object to be thrown
     * @param cause    Java exception that triggered this throw
     */
    public ECMAException(final Object thrown, final Throwable cause) {
        super(ScriptRuntime.safeToString(thrown), cause);
        this.thrown = thrown;
        setExceptionToThrown();
        // location is not directly available, get it from stack trace
        setLocationFromStack();
    }

    /**
     * Get the thrown object
     * @return thrown object
     */
    public Object getThrown() {
        return thrown;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        final String fileName = getFileName();
        final int line = getLineNumber();
        final int column = getColumnNumber();

        if (fileName != null) {
            sb.append(fileName);
            if (line >= 0) {
                sb.append(':');
                sb.append(line);
            }
            if (column >= 0) {
                sb.append(':');
                sb.append(column);
            }
            sb.append(' ');
        } else {
            sb.append("ECMAScript Exception: ");
        }

        sb.append(getMessage());
        return sb.toString();
    }

    /**
     * Get the {@link ECMAException}, i.e. the underlying Java object for the
     * JavaScript error object from a {@link ScriptObject} representing an error
     *
     * @param errObj the error object
     * @return a {@link ECMAException}
     */
    public static Object getException(final ScriptObject errObj) {
        return errObj.get(ECMAException.EXCEPTION_PROPERTY);
    }

    /**
     * Check if a stack trace element is in JavaScript
     *
     * @param frame frame
     *
     * @return true if frame is in the script
     */
    public static boolean isScriptFrame(final StackTraceElement frame) {
        final String className = frame.getClassName();

        // Look for script package in class name (into which compiler puts generated code)
        if (className.startsWith(scriptPackage)) {
            final String source = frame.getFileName();
            /*
             * Make sure that it is not some Java code that Nashorn has in that package!
             * also, we don't want to report JavaScript code that lives in script engine implementation
             * We want to report only user's own scripts and not any of our own scripts like "engine.js"
             */
            return source != null && !source.endsWith(".java") && !source.contains(ENGINE_SCRIPT_SOURCE_NAME);
        }
        return false;
    }

    /**
     * Print the stack trace for a {@code ScriptObject} representing an error
     *
     * @param errObj the error object
     * @return undefined
     */
    public static Object printStackTrace(final ScriptObject errObj) {
        final Object exception = getException(errObj);
        if (exception instanceof Throwable) {
            ((Throwable)exception).printStackTrace(Context.getCurrentErr());
        } else {
            Context.err("<stack trace not available>");
        }
        return UNDEFINED;
    }

    /**
     * Get the line number for a {@code ScriptObject} representing an error
     *
     * @param errObj the error object
     * @return the line number, or undefined if wrapped exception is not a ParserException
     */
    public static Object getLineNumber(final ScriptObject errObj) {
        final Object e = getException(errObj);
        if (e instanceof NashornException) {
            return ((NashornException)e).getLineNumber();
        } else if (e instanceof ScriptException) {
            return ((ScriptException)e).getLineNumber();
        }

        return UNDEFINED;
    }

    /**
     * Get the column number for a {@code ScriptObject} representing an error
     *
     * @param errObj the error object
     * @return the column number, or undefined if wrapped exception is not a ParserException
     */
    public static Object getColumnNumber(final ScriptObject errObj) {
        final Object e = getException(errObj);
        if (e instanceof NashornException) {
            return ((NashornException)e).getColumnNumber();
        } else if (e instanceof ScriptException) {
            return ((ScriptException)e).getColumnNumber();
        }

        return UNDEFINED;
    }

    /**
     * Get the file name for a {@code ScriptObject} representing an error
     *
     * @param errObj the error object
     * @return the file name, or undefined if wrapped exception is not a ParserException
     */
    public static Object getFileName(final ScriptObject errObj) {
        final Object e = getException(errObj);
        if (e instanceof NashornException) {
            return ((NashornException)e).getFileName();
        } else if (e instanceof ScriptException) {
            return ((ScriptException)e).getFileName();
        }

        return UNDEFINED;
    }

    /**
     * Stateless string conversion for an error object
     *
     * @param errObj the error object
     * @return string representation of {@code errObj}
     */
    public static String safeToString(final ScriptObject errObj) {
        Object name = UNDEFINED;
        try {
            name = errObj.get("name");
        } catch (final Exception e) {
            //ignored
        }

        if (name == UNDEFINED) {
            name = "Error";
        } else {
            name = ScriptRuntime.safeToString(name);
        }

        Object msg = UNDEFINED;
        try {
            msg = errObj.get("message");
        } catch (final Exception e) {
            //ignored
        }

        if (msg == UNDEFINED) {
            msg = "";
        } else {
            msg = ScriptRuntime.safeToString(msg);
        }

        if (((String)name).isEmpty()) {
            return (String)msg;
        }

        if (((String)msg).isEmpty()) {
            return (String)name;
        }

        return (String)name + ": " + (String)msg;
    }

    private static Throwable asThrowable(final Object obj) {
        return (obj instanceof Throwable)? (Throwable)obj : null;
    }

    private void setExceptionToThrown() {
        /*
         * Nashorn extension: errorObject.nashornException
         * Expose this exception via "nashornException" property of on the
         * thrown object. This exception object can be used to print stack
         * trace and fileName, line number etc. from script code.
         */

        if (thrown instanceof ScriptObject) {
            final ScriptObject sobj = (ScriptObject)thrown;
            if (!sobj.has(EXCEPTION_PROPERTY)) {
                sobj.addOwnProperty(EXCEPTION_PROPERTY, Property.NOT_ENUMERABLE, this);
            }
        }
    }

    private void setLocationFromStack() {
        // This is not so pretty - but it gets the job done. Note that the stack
        // trace has been already filled by "fillInStackTrace" call from Throwable
        // constructor and so we don't pay additional cost for it.

        // Find the first JavaScript frame by walking and set file, line from it
        // Usually, we should be able to find it in just few frames depth.
        for (final StackTraceElement ste : getStackTrace()) {
            if (isScriptFrame(ste)) {
                // Whatever here is compiled from JavaScript code
                setFileName(ste.getFileName());
                setLineNumber(ste.getLineNumber());
                // Hard luck - no column number info
                setColumnNumber(-1);
                break;
            }
        }
    }
}
