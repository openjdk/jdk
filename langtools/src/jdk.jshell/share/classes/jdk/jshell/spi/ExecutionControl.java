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

import java.io.Serializable;

/**
 * This interface specifies the functionality that must provided to implement a
 * pluggable JShell execution engine.
 * <p>
 * The audience for this Service Provider Interface is engineers wishing to
 * implement their own version of the execution engine in support of the JShell
 * API.
 * <p>
 * A Snippet is compiled into code wrapped in a 'wrapper class'. The execution
 * engine is used by the core JShell implementation to load and, for executable
 * Snippets, execute the Snippet.
 * <p>
 * Methods defined in this interface should only be called by the core JShell
 * implementation.
 * <p>
 * To install an {@code ExecutionControl}, its {@code Generator} is passed to
 * {@link jdk.jshell.JShell.Builder#executionEngine(ExecutionControl.Generator)  }.
 */
public interface ExecutionControl {

    /**
     * Defines a functional interface for creating {@link ExecutionControl}
     * instances.
     */
    public interface Generator {

        /**
         * Generates an execution engine, given an execution environment.
         *
         * @param env the context in which the {@link ExecutionControl} is to
         * be created
         * @return the created instance
         * @throws Throwable if problems occurred
         */
        ExecutionControl generate(ExecutionEnv env) throws Throwable;
    }

    /**
     * Attempts to load new classes.
     *
     * @param cbcs the class name and bytecodes to load
     * @throws ClassInstallException exception occurred loading the classes,
     * some or all were not loaded
     * @throws NotImplementedException if not implemented
     * @throws EngineTerminationException the execution engine has terminated
     */
    void load(ClassBytecodes[] cbcs)
            throws ClassInstallException, NotImplementedException, EngineTerminationException;

    /**
     * Attempts to redefine previously loaded classes.
     *
     * @param cbcs the class name and bytecodes to redefine
     * @throws ClassInstallException exception occurred redefining the classes,
     * some or all were not redefined
     * @throws NotImplementedException if not implemented
     * @throws EngineTerminationException the execution engine has terminated
     */
    void redefine(ClassBytecodes[] cbcs)
            throws ClassInstallException, NotImplementedException, EngineTerminationException;

    /**
     * Invokes an executable Snippet by calling a method on the specified
     * wrapper class. The method must have no arguments and return String.
     *
     * @param className the class whose method should be invoked
     * @param methodName the name of method to invoke
     * @return the result of the execution or null if no result
     * @throws UserException the invoke raised a user exception
     * @throws ResolutionException the invoke attempted to directly or
     * indirectly invoke an unresolved snippet
     * @throws StoppedException if the {@code invoke()} was canceled by
     * {@link ExecutionControl#stop}
     * @throws EngineTerminationException the execution engine has terminated
     * @throws InternalException an internal problem occurred
     */
    String invoke(String className, String methodName)
            throws RunException, EngineTerminationException, InternalException;

    /**
     * Returns the value of a variable.
     *
     * @param className the name of the wrapper class of the variable
     * @param varName the name of the variable
     * @return the value of the variable
     * @throws UserException formatting the value raised a user exception
     * @throws ResolutionException formatting the value attempted to directly or
     * indirectly invoke an unresolved snippet
     * @throws StoppedException if the formatting the value was canceled by
     * {@link ExecutionControl#stop}
     * @throws EngineTerminationException the execution engine has terminated
     * @throws InternalException an internal problem occurred
     */
    String varValue(String className, String varName)
            throws RunException, EngineTerminationException, InternalException;

    /**
     * Adds the path to the execution class path.
     *
     * @param path the path to add
     * @throws EngineTerminationException the execution engine has terminated
     * @throws InternalException an internal problem occurred
     */
    void addToClasspath(String path)
            throws EngineTerminationException, InternalException;

    /**
     * Sets the execution class path to the specified path.
     *
     * @param path the path to add
     * @throws EngineTerminationException the execution engine has terminated
     * @throws InternalException an internal problem occurred
     */
    void setClasspath(String path)
            throws EngineTerminationException, InternalException;

    /**
     * Interrupts a running invoke.
     *
     * @throws EngineTerminationException the execution engine has terminated
     * @throws InternalException an internal problem occurred
     */
    void stop()
            throws EngineTerminationException, InternalException;

    /**
     * Run a non-standard command (or a standard command from a newer version).
     *
     * @param command the non-standard command
     * @param arg the commands argument
     * @return the commands return value
     * @throws UserException the command raised a user exception
     * @throws ResolutionException the command attempted to directly or
     * indirectly invoke an unresolved snippet
     * @throws StoppedException if the command was canceled by
     * {@link ExecutionControl#stop}
     * @throws EngineTerminationException the execution engine has terminated
     * @throws NotImplementedException if not implemented
     * @throws InternalException an internal problem occurred
     */
    Object extensionCommand(String command, Object arg)
            throws RunException, EngineTerminationException, InternalException;

    /**
     * Shuts down this execution engine. Implementation should free all
     * resources held by this execution engine.
     * <p>
     * No calls to methods on this interface should be made after close.
     */
    void close();

    /**
     * Bundles class name with class bytecodes.
     */
    public static final class ClassBytecodes implements Serializable {

        private static final long serialVersionUID = 0xC1A55B47EC0DE5L;
        private final String name;
        private final byte[] bytecodes;

        /**
         * Creates a name/bytecode pair.
         * @param name the class name
         * @param bytecodes the class bytecodes
         */
        public ClassBytecodes(String name, byte[] bytecodes) {
            this.name = name;
            this.bytecodes = bytecodes;
        }

        /**
         * The bytecodes for the class.
         *
         * @return the bytecodes
         */
        public byte[] bytecodes() {
            return bytecodes;
        }

        /**
         * The class name.
         *
         * @return the class name
         */
        public String name() {
            return name;
        }
    }

    /**
     * The abstract base of all {@code ExecutionControl} exceptions.
     */
    public static abstract class ExecutionControlException extends Exception {

        private static final long serialVersionUID = 1L;

        public ExecutionControlException(String message) {
            super(message);
        }
    }

    /**
     * Unbidden execution engine termination has occurred.
     */
    public static class EngineTerminationException extends ExecutionControlException {

        private static final long serialVersionUID = 1L;

        public EngineTerminationException(String message) {
            super(message);
        }
    }

    /**
     * The command is not implemented.
     */
    public static class NotImplementedException extends InternalException {

        private static final long serialVersionUID = 1L;

        public NotImplementedException(String message) {
            super(message);
        }
    }

    /**
     * An internal problem has occurred.
     */
    public static class InternalException extends ExecutionControlException {

        private static final long serialVersionUID = 1L;

        public InternalException(String message) {
            super(message);
        }
    }

    /**
     * A class install (load or redefine) encountered a problem.
     */
    public static class ClassInstallException extends ExecutionControlException {

        private static final long serialVersionUID = 1L;

        private final boolean[] installed;

        public ClassInstallException(String message, boolean[] installed) {
            super(message);
            this.installed = installed;
        }

        /**
         * Indicates which of the passed classes were successfully
         * loaded/redefined.
         * @return a one-to-one array with the {@link ClassBytecodes}{@code[]}
         * array -- {@code true} if installed
         */
        public boolean[] installed() {
            return installed;
        }
    }

    /**
     * The abstract base of of exceptions specific to running user code.
     */
    public static abstract class RunException extends ExecutionControlException {

        private static final long serialVersionUID = 1L;

        private RunException(String message) {
            super(message);
        }
    }

    /**
     * A 'normal' user exception occurred.
     */
    public static class UserException extends RunException {

        private static final long serialVersionUID = 1L;

        private final String causeExceptionClass;

        public UserException(String message, String causeExceptionClass, StackTraceElement[] stackElements) {
            super(message);
            this.causeExceptionClass = causeExceptionClass;
            this.setStackTrace(stackElements);
        }

        /**
         * Returns the class of the user exception.
         * @return the name of the user exception class
         */
        public String causeExceptionClass() {
            return causeExceptionClass;
        }
    }

    /**
     * An exception indicating that a {@code DeclarationSnippet} with unresolved
     * references has been encountered.
     * <p>
     * Contrast this with the initiating {@link SPIResolutionException}
     * (a {@code RuntimeException}) which is embedded in generated corralled
     * code.  Also, contrast this with
     * {@link jdk.jshell.UnresolvedReferenceException} the high-level
     * exception (with {@code DeclarationSnippet} reference) provided in the
     * main API.
     */
    public static class ResolutionException extends RunException {

        private static final long serialVersionUID = 1L;

        private final int id;

        /**
         * Constructs an exception indicating that a {@code DeclarationSnippet}
         * with unresolved references has been encountered.
         *
         * @param id An internal identifier of the specific method
         * @param stackElements the stack trace
         */
        public ResolutionException(int id, StackTraceElement[] stackElements) {
            super("resolution exception: " + id);
            this.id = id;
            this.setStackTrace(stackElements);
        }

        /**
         * Retrieves the internal identifier of the unresolved identifier.
         *
         * @return the internal identifier
         */
        public int id() {
            return id;
        }
    }

    /**
     * An exception indicating that an
     * {@link ExecutionControl#invoke(java.lang.String, java.lang.String) }
     * (or theoretically a
     * {@link ExecutionControl#varValue(java.lang.String, java.lang.String) })
     * has been interrupted by a {@link ExecutionControl#stop() }.
     */
    public static class StoppedException extends RunException {

        private static final long serialVersionUID = 1L;

        public StoppedException() {
            super("stopped by stop()");
        }
    }

}
