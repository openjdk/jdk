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

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import jdk.jshell.EvalException;
import jdk.jshell.JShell;
import jdk.jshell.UnresolvedReferenceException;

/**
 * Functionality made available to a pluggable JShell execution engine.  It is
 * provided to the execution engine by the core JShell implementation calling
 * {@link ExecutionControl#start(jdk.jshell.spi.ExecutionEnv) }.
 * <p>
 * This interface is designed to provide the access to core JShell functionality
 * needed to implement ExecutionControl.
 *
 * @see ExecutionControl
 */
public interface ExecutionEnv {

    /**
     * Returns the user's input stream.
     *
     * @return the user's input stream
     */
    InputStream userIn();

    /**
     * Returns the user's output stream.
     *
     * @return the user's output stream
     */
    PrintStream userOut();

    /**
     * Returns the user's error stream.
     *
     * @return the user's error stream
     */
    PrintStream userErr();

    /**
     * @return the JShell instance
     */
    JShell state();

    /**
     * Returns the additional VM options to be used when launching the remote
     * JVM. This is advice to the execution engine.
     * <p>
     * Note: an execution engine need not launch a remote JVM.
     *
     * @return the additional options with which to launch the remote JVM
     */
    List<String> extraRemoteVMOptions();

    /**
     * Retrieves the class file bytes for the specified wrapper class.
     *
     * @param className the name of the wrapper class
     * @return the current class file bytes as a byte array
     */
    byte[] getClassBytes(String className);

    /**
     * Creates an {@code EvalException} corresponding to a user exception. An
     * user exception thrown during
     * {@link ExecutionControl#invoke(java.lang.String, java.lang.String) }
     * should be converted to an {@code EvalException} using this method.
     *
     * @param message the exception message to use (from the user exception)
     * @param exceptionClass the class name of the user exception
     * @param stackElements the stack trace elements to install
     * @return a user API EvalException for the user exception
     */
    EvalException createEvalException(String message, String exceptionClass,
            StackTraceElement[] stackElements);

    /**
     * Creates an {@code UnresolvedReferenceException} for the Snippet identifed
     * by the specified identifier. An {@link SPIResolutionException} thrown
     * during {@link ExecutionControl#invoke(java.lang.String, java.lang.String) }
     * should be converted to an {@code UnresolvedReferenceException} using
     * this method.
     * <p>
     * The identifier is an internal id, different from the id in the API. This
     * internal id is returned by {@link SPIResolutionException#id()}.
     *
     * @param id the internal integer identifier
     * @param stackElements the stack trace elements to install
     * @return an {@code UnresolvedReferenceException} for the unresolved
     * reference
     */
    UnresolvedReferenceException createUnresolvedReferenceException(int id,
            StackTraceElement[] stackElements);

    /**
     * Reports that the execution engine has shutdown.
     */
    void closeDown();
}
