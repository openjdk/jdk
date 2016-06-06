/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jshell.spi;

import java.util.Collection;
import jdk.jshell.JShellException;

/**
 * This interface specifies the functionality that must provided to implement
 * a pluggable JShell execution engine.
 * <p>
 * The audience for this Service Provider Interface is engineers
 * wishing to implement their own version of the execution engine in support
 * of the JShell API.  This is NOT a part of the JShell API.
 * <p>
 * A Snippet is compiled into code wrapped in a 'wrapper class'.  The execution
 * engine is used by the core JShell implementation to load and, for
 * executable Snippets, execute the Snippet.
 * <p>
 * Methods defined in this interface should only be called by the core JShell
 * implementation.
 * <p>
 * To install an instance of ExecutionControl, it is passed to
 * {@link jdk.jshell.JShell.Builder#executionEngine(jdk.jshell.spi.ExecutionControl) }.
 */
public interface ExecutionControl {

    /**
     * Represents the current status of a class in the execution engine.
     */
    public enum ClassStatus {
        /**
         * Class is not known to the execution engine (not loaded).
         */
        UNKNOWN,

        /**
         * Class is loaded, but the loaded/redefined bytes do not match those
         * returned by {@link ExecutionEnv#getClassBytes(java.lang.String) }.
         */
        NOT_CURRENT,

        /**
         * Class is loaded and loaded/redefined bytes match those
         * returned by {@link ExecutionEnv#getClassBytes(java.lang.String) }.
         */
        CURRENT
    };

    /**
     * Initializes the instance. No methods in this interface can be called
     * before this.
     *
     * @param env the execution environment information provided by JShell
     * @throws Exception if the instance is unable to initialize
     */
    void start(ExecutionEnv env) throws Exception;

    /**
     * Shuts down this execution engine. Implementation should free all
     * resources held by this execution engine.
     * <p>
     * No calls to methods on this interface should be made after close.
     */
    void close();

    /**
     * Adds the path to the execution class path.
     *
     * @param path the path to add
     * @return true if successful
     */
    boolean addToClasspath(String path);

    /**
     * Invokes an executable Snippet by calling a method on the specified
     * wrapper class. The method must have no arguments and return String.
     *
     * @param classname the class whose method should be invoked
     * @param methodname the name of method to invoke
     * @return the result of the execution or null if no result
     * @throws JShellException if a user exception if thrown,
     * {@link jdk.jshell.EvalException EvalException} will be thrown; if an
     * unresolved reference is encountered,
     * {@link jdk.jshell.UnresolvedReferenceException UnresolvedReferenceException}
     * will be thrown
     */
    String invoke(String classname, String methodname) throws JShellException;

    /**
     * Attempts to load new classes. Class bytes are retrieved from
     * {@link ExecutionEnv#getClassBytes(java.lang.String) }
     *
     * @param classes list of class names to load
     * @return true if load succeeded
     */
    boolean load(Collection<String> classes);

    /**
     * Attempts to redefine previously loaded classes. Class bytes are retrieved
     * from {@link ExecutionEnv#getClassBytes(java.lang.String) }
     *
     * @param classes list of class names to redefine
     * @return true if redefine succeeded
     */
    boolean redefine(Collection<String> classes);

    /**
     * Queries if the class is loaded and the class bytes are current.
     *
     * @param classname name of the wrapper class to query
     * @return {@code UNKNOWN} if the class is not loaded; {@code CURRENT} if
     * the loaded/redefined bytes are equal to the most recent bytes for this
     * wrapper class; otherwise {@code NOT_CURRENT}
     */
    ClassStatus getClassStatus(String classname);

    /**
     * Interrupt a running invoke.
     */
    void stop();

    /**
     * Returns the value of a variable.
     *
     * @param classname the name of the wrapper class of the variable
     * @param varname the name of the variable
     * @return the value of the variable
     */
    String varValue(String classname, String varname);
}
