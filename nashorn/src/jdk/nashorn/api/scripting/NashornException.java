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

/**
 * This is base exception for all Nashorn exceptions. These originate from user's
 * ECMAScript code. Example: script parse errors, exceptions thrown from scripts.
 * Note that ScriptEngine methods like "eval", "invokeMethod", "invokeFunction"
 * will wrap this as ScriptException and throw it. But, there are cases where user
 * may need to access this exception (or implementation defined subtype of this).
 * For example, if java interface is implemented by a script object or Java access
 * to script object properties via java.util.Map interface. In these cases, user
 * code will get an instance of this or implementation defined subclass.
 */
@SuppressWarnings("serial")
public class NashornException extends RuntimeException {
    // script file name
    private String      fileName;
    // script line number
    private int         line;
    // script column number
    private int         column;

    /** script source name used for "engine.js" */
    protected static final String ENGINE_SCRIPT_SOURCE_NAME = "nashorn:engine/resources/engine.js";

    /**
     * Constructor
     *
     * @param msg exception message
     */
    protected NashornException(final String msg) {
        super(msg);
    }

    /**
     * Constructor
     * @param msg   exception message
     * @param cause exception cause
     */
    protected NashornException(final String msg, final Throwable cause) {
        super(msg, cause);
    }

    /**
     * Constructor
     *
     * @param cause exception cause
     */
    protected NashornException(final Throwable cause) {
        super(cause);
    }

     /**
      * Get the source file name for this {@code NashornException}
      * @return the file name
      */
     public final String getFileName() {
         return fileName;
     }

    /**
     * Set the source file name for this {@code NashornException}
     * @param fileName file name
     */
    protected final void setFileName(final String fileName) {
         this.fileName = fileName;
     }

    /**
     * Get the line number for this {@code NashornException}
     * @return the line number
     */
     public final int getLineNumber() {
         return line;
     }

    /**
     * Set the line number for this {@code NashornException}
     * @param line line number
     */
    protected final void setLineNumber(final int line) {
         this.line = line;
     }

    /**
     * Get the column for this {@code NashornException}
     * @return the column
     */
    public final int getColumnNumber() {
        return column;
    }

   /**
    * Set the column number for this {@code NashornException}
    * @param column the column
    */
    public final void setColumnNumber(final int column) {
        this.column = column;
    }
}
