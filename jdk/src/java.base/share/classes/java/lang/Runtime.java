/*
 * Copyright (c) 1995, 2014, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.io.*;
import java.util.StringTokenizer;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

/**
 * Every Java application has a single instance of class
 * {@code Runtime} that allows the application to interface with
 * the environment in which the application is running. The current
 * runtime can be obtained from the {@code getRuntime} method.
 * <p>
 * An application cannot create its own instance of this class.
 *
 * @author  unascribed
 * @see     java.lang.Runtime#getRuntime()
 * @since   1.0
 */

public class Runtime {
    private static final Runtime currentRuntime = new Runtime();

    /**
     * Returns the runtime object associated with the current Java application.
     * Most of the methods of class {@code Runtime} are instance
     * methods and must be invoked with respect to the current runtime object.
     *
     * @return  the {@code Runtime} object associated with the current
     *          Java application.
     */
    public static Runtime getRuntime() {
        return currentRuntime;
    }

    /** Don't let anyone else instantiate this class */
    private Runtime() {}

    /**
     * Terminates the currently running Java virtual machine by initiating its
     * shutdown sequence.  This method never returns normally.  The argument
     * serves as a status code; by convention, a nonzero status code indicates
     * abnormal termination.
     *
     * <p> The virtual machine's shutdown sequence consists of two phases.  In
     * the first phase all registered {@link #addShutdownHook shutdown hooks},
     * if any, are started in some unspecified order and allowed to run
     * concurrently until they finish.  In the second phase all uninvoked
     * finalizers are run if {@link #runFinalizersOnExit finalization-on-exit}
     * has been enabled.  Once this is done the virtual machine {@link #halt halts}.
     *
     * <p> If this method is invoked after the virtual machine has begun its
     * shutdown sequence then if shutdown hooks are being run this method will
     * block indefinitely.  If shutdown hooks have already been run and on-exit
     * finalization has been enabled then this method halts the virtual machine
     * with the given status code if the status is nonzero; otherwise, it
     * blocks indefinitely.
     *
     * <p> The {@link System#exit(int) System.exit} method is the
     * conventional and convenient means of invoking this method.
     *
     * @param  status
     *         Termination status.  By convention, a nonzero status code
     *         indicates abnormal termination.
     *
     * @throws SecurityException
     *         If a security manager is present and its
     *         {@link SecurityManager#checkExit checkExit} method does not permit
     *         exiting with the specified status
     *
     * @see java.lang.SecurityException
     * @see java.lang.SecurityManager#checkExit(int)
     * @see #addShutdownHook
     * @see #removeShutdownHook
     * @see #runFinalizersOnExit
     * @see #halt(int)
     */
    public void exit(int status) {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkExit(status);
        }
        Shutdown.exit(status);
    }

    /**
     * Registers a new virtual-machine shutdown hook.
     *
     * <p> The Java virtual machine <i>shuts down</i> in response to two kinds
     * of events:
     *
     *   <ul>
     *
     *   <li> The program <i>exits</i> normally, when the last non-daemon
     *   thread exits or when the {@link #exit exit} (equivalently,
     *   {@link System#exit(int) System.exit}) method is invoked, or
     *
     *   <li> The virtual machine is <i>terminated</i> in response to a
     *   user interrupt, such as typing {@code ^C}, or a system-wide event,
     *   such as user logoff or system shutdown.
     *
     *   </ul>
     *
     * <p> A <i>shutdown hook</i> is simply an initialized but unstarted
     * thread.  When the virtual machine begins its shutdown sequence it will
     * start all registered shutdown hooks in some unspecified order and let
     * them run concurrently.  When all the hooks have finished it will then
     * run all uninvoked finalizers if finalization-on-exit has been enabled.
     * Finally, the virtual machine will halt.  Note that daemon threads will
     * continue to run during the shutdown sequence, as will non-daemon threads
     * if shutdown was initiated by invoking the {@link #exit exit} method.
     *
     * <p> Once the shutdown sequence has begun it can be stopped only by
     * invoking the {@link #halt halt} method, which forcibly
     * terminates the virtual machine.
     *
     * <p> Once the shutdown sequence has begun it is impossible to register a
     * new shutdown hook or de-register a previously-registered hook.
     * Attempting either of these operations will cause an
     * {@link IllegalStateException} to be thrown.
     *
     * <p> Shutdown hooks run at a delicate time in the life cycle of a virtual
     * machine and should therefore be coded defensively.  They should, in
     * particular, be written to be thread-safe and to avoid deadlocks insofar
     * as possible.  They should also not rely blindly upon services that may
     * have registered their own shutdown hooks and therefore may themselves in
     * the process of shutting down.  Attempts to use other thread-based
     * services such as the AWT event-dispatch thread, for example, may lead to
     * deadlocks.
     *
     * <p> Shutdown hooks should also finish their work quickly.  When a
     * program invokes {@link #exit exit} the expectation is
     * that the virtual machine will promptly shut down and exit.  When the
     * virtual machine is terminated due to user logoff or system shutdown the
     * underlying operating system may only allow a fixed amount of time in
     * which to shut down and exit.  It is therefore inadvisable to attempt any
     * user interaction or to perform a long-running computation in a shutdown
     * hook.
     *
     * <p> Uncaught exceptions are handled in shutdown hooks just as in any
     * other thread, by invoking the
     * {@link ThreadGroup#uncaughtException uncaughtException} method of the
     * thread's {@link ThreadGroup} object. The default implementation of this
     * method prints the exception's stack trace to {@link System#err} and
     * terminates the thread; it does not cause the virtual machine to exit or
     * halt.
     *
     * <p> In rare circumstances the virtual machine may <i>abort</i>, that is,
     * stop running without shutting down cleanly.  This occurs when the
     * virtual machine is terminated externally, for example with the
     * {@code SIGKILL} signal on Unix or the {@code TerminateProcess} call on
     * Microsoft Windows.  The virtual machine may also abort if a native
     * method goes awry by, for example, corrupting internal data structures or
     * attempting to access nonexistent memory.  If the virtual machine aborts
     * then no guarantee can be made about whether or not any shutdown hooks
     * will be run.
     *
     * @param   hook
     *          An initialized but unstarted {@link Thread} object
     *
     * @throws  IllegalArgumentException
     *          If the specified hook has already been registered,
     *          or if it can be determined that the hook is already running or
     *          has already been run
     *
     * @throws  IllegalStateException
     *          If the virtual machine is already in the process
     *          of shutting down
     *
     * @throws  SecurityException
     *          If a security manager is present and it denies
     *          {@link RuntimePermission}("shutdownHooks")
     *
     * @see #removeShutdownHook
     * @see #halt(int)
     * @see #exit(int)
     * @since 1.3
     */
    public void addShutdownHook(Thread hook) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("shutdownHooks"));
        }
        ApplicationShutdownHooks.add(hook);
    }

    /**
     * De-registers a previously-registered virtual-machine shutdown hook.
     *
     * @param hook the hook to remove
     * @return {@code true} if the specified hook had previously been
     * registered and was successfully de-registered, {@code false}
     * otherwise.
     *
     * @throws  IllegalStateException
     *          If the virtual machine is already in the process of shutting
     *          down
     *
     * @throws  SecurityException
     *          If a security manager is present and it denies
     *          {@link RuntimePermission}("shutdownHooks")
     *
     * @see #addShutdownHook
     * @see #exit(int)
     * @since 1.3
     */
    public boolean removeShutdownHook(Thread hook) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("shutdownHooks"));
        }
        return ApplicationShutdownHooks.remove(hook);
    }

    /**
     * Forcibly terminates the currently running Java virtual machine.  This
     * method never returns normally.
     *
     * <p> This method should be used with extreme caution.  Unlike the
     * {@link #exit exit} method, this method does not cause shutdown
     * hooks to be started and does not run uninvoked finalizers if
     * finalization-on-exit has been enabled.  If the shutdown sequence has
     * already been initiated then this method does not wait for any running
     * shutdown hooks or finalizers to finish their work.
     *
     * @param  status
     *         Termination status. By convention, a nonzero status code
     *         indicates abnormal termination. If the {@link Runtime#exit exit}
     *         (equivalently, {@link System#exit(int) System.exit}) method
     *         has already been invoked then this status code
     *         will override the status code passed to that method.
     *
     * @throws SecurityException
     *         If a security manager is present and its
     *         {@link SecurityManager#checkExit checkExit} method
     *         does not permit an exit with the specified status
     *
     * @see #exit
     * @see #addShutdownHook
     * @see #removeShutdownHook
     * @since 1.3
     */
    public void halt(int status) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkExit(status);
        }
        Shutdown.halt(status);
    }

    /**
     * Enable or disable finalization on exit; doing so specifies that the
     * finalizers of all objects that have finalizers that have not yet been
     * automatically invoked are to be run before the Java runtime exits.
     * By default, finalization on exit is disabled.
     *
     * <p>If there is a security manager,
     * its {@code checkExit} method is first called
     * with 0 as its argument to ensure the exit is allowed.
     * This could result in a SecurityException.
     *
     * @param value true to enable finalization on exit, false to disable
     * @deprecated  This method is inherently unsafe.  It may result in
     *      finalizers being called on live objects while other threads are
     *      concurrently manipulating those objects, resulting in erratic
     *      behavior or deadlock.
     *      This method is subject to removal in a future version of Java SE.
     *
     * @throws  SecurityException
     *        if a security manager exists and its {@code checkExit}
     *        method doesn't allow the exit.
     *
     * @see     java.lang.Runtime#exit(int)
     * @see     java.lang.Runtime#gc()
     * @see     java.lang.SecurityManager#checkExit(int)
     * @since   1.1
     */
    @Deprecated(since="1.2", forRemoval=true)
    public static void runFinalizersOnExit(boolean value) {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            try {
                security.checkExit(0);
            } catch (SecurityException e) {
                throw new SecurityException("runFinalizersOnExit");
            }
        }
        Shutdown.setRunFinalizersOnExit(value);
    }

    /**
     * Executes the specified string command in a separate process.
     *
     * <p>This is a convenience method.  An invocation of the form
     * {@code exec(command)}
     * behaves in exactly the same way as the invocation
     * {@link #exec(String, String[], File) exec}{@code (command, null, null)}.
     *
     * @param   command   a specified system command.
     *
     * @return  A new {@link Process} object for managing the subprocess
     *
     * @throws  SecurityException
     *          If a security manager exists and its
     *          {@link SecurityManager#checkExec checkExec}
     *          method doesn't allow creation of the subprocess
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If {@code command} is {@code null}
     *
     * @throws  IllegalArgumentException
     *          If {@code command} is empty
     *
     * @see     #exec(String[], String[], File)
     * @see     ProcessBuilder
     */
    public Process exec(String command) throws IOException {
        return exec(command, null, null);
    }

    /**
     * Executes the specified string command in a separate process with the
     * specified environment.
     *
     * <p>This is a convenience method.  An invocation of the form
     * {@code exec(command, envp)}
     * behaves in exactly the same way as the invocation
     * {@link #exec(String, String[], File) exec}{@code (command, envp, null)}.
     *
     * @param   command   a specified system command.
     *
     * @param   envp      array of strings, each element of which
     *                    has environment variable settings in the format
     *                    <i>name</i>=<i>value</i>, or
     *                    {@code null} if the subprocess should inherit
     *                    the environment of the current process.
     *
     * @return  A new {@link Process} object for managing the subprocess
     *
     * @throws  SecurityException
     *          If a security manager exists and its
     *          {@link SecurityManager#checkExec checkExec}
     *          method doesn't allow creation of the subprocess
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If {@code command} is {@code null},
     *          or one of the elements of {@code envp} is {@code null}
     *
     * @throws  IllegalArgumentException
     *          If {@code command} is empty
     *
     * @see     #exec(String[], String[], File)
     * @see     ProcessBuilder
     */
    public Process exec(String command, String[] envp) throws IOException {
        return exec(command, envp, null);
    }

    /**
     * Executes the specified string command in a separate process with the
     * specified environment and working directory.
     *
     * <p>This is a convenience method.  An invocation of the form
     * {@code exec(command, envp, dir)}
     * behaves in exactly the same way as the invocation
     * {@link #exec(String[], String[], File) exec}{@code (cmdarray, envp, dir)},
     * where {@code cmdarray} is an array of all the tokens in
     * {@code command}.
     *
     * <p>More precisely, the {@code command} string is broken
     * into tokens using a {@link StringTokenizer} created by the call
     * {@code new {@link StringTokenizer}(command)} with no
     * further modification of the character categories.  The tokens
     * produced by the tokenizer are then placed in the new string
     * array {@code cmdarray}, in the same order.
     *
     * @param   command   a specified system command.
     *
     * @param   envp      array of strings, each element of which
     *                    has environment variable settings in the format
     *                    <i>name</i>=<i>value</i>, or
     *                    {@code null} if the subprocess should inherit
     *                    the environment of the current process.
     *
     * @param   dir       the working directory of the subprocess, or
     *                    {@code null} if the subprocess should inherit
     *                    the working directory of the current process.
     *
     * @return  A new {@link Process} object for managing the subprocess
     *
     * @throws  SecurityException
     *          If a security manager exists and its
     *          {@link SecurityManager#checkExec checkExec}
     *          method doesn't allow creation of the subprocess
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If {@code command} is {@code null},
     *          or one of the elements of {@code envp} is {@code null}
     *
     * @throws  IllegalArgumentException
     *          If {@code command} is empty
     *
     * @see     ProcessBuilder
     * @since 1.3
     */
    public Process exec(String command, String[] envp, File dir)
        throws IOException {
        if (command.length() == 0)
            throw new IllegalArgumentException("Empty command");

        StringTokenizer st = new StringTokenizer(command);
        String[] cmdarray = new String[st.countTokens()];
        for (int i = 0; st.hasMoreTokens(); i++)
            cmdarray[i] = st.nextToken();
        return exec(cmdarray, envp, dir);
    }

    /**
     * Executes the specified command and arguments in a separate process.
     *
     * <p>This is a convenience method.  An invocation of the form
     * {@code exec(cmdarray)}
     * behaves in exactly the same way as the invocation
     * {@link #exec(String[], String[], File) exec}{@code (cmdarray, null, null)}.
     *
     * @param   cmdarray  array containing the command to call and
     *                    its arguments.
     *
     * @return  A new {@link Process} object for managing the subprocess
     *
     * @throws  SecurityException
     *          If a security manager exists and its
     *          {@link SecurityManager#checkExec checkExec}
     *          method doesn't allow creation of the subprocess
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If {@code cmdarray} is {@code null},
     *          or one of the elements of {@code cmdarray} is {@code null}
     *
     * @throws  IndexOutOfBoundsException
     *          If {@code cmdarray} is an empty array
     *          (has length {@code 0})
     *
     * @see     ProcessBuilder
     */
    public Process exec(String cmdarray[]) throws IOException {
        return exec(cmdarray, null, null);
    }

    /**
     * Executes the specified command and arguments in a separate process
     * with the specified environment.
     *
     * <p>This is a convenience method.  An invocation of the form
     * {@code exec(cmdarray, envp)}
     * behaves in exactly the same way as the invocation
     * {@link #exec(String[], String[], File) exec}{@code (cmdarray, envp, null)}.
     *
     * @param   cmdarray  array containing the command to call and
     *                    its arguments.
     *
     * @param   envp      array of strings, each element of which
     *                    has environment variable settings in the format
     *                    <i>name</i>=<i>value</i>, or
     *                    {@code null} if the subprocess should inherit
     *                    the environment of the current process.
     *
     * @return  A new {@link Process} object for managing the subprocess
     *
     * @throws  SecurityException
     *          If a security manager exists and its
     *          {@link SecurityManager#checkExec checkExec}
     *          method doesn't allow creation of the subprocess
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If {@code cmdarray} is {@code null},
     *          or one of the elements of {@code cmdarray} is {@code null},
     *          or one of the elements of {@code envp} is {@code null}
     *
     * @throws  IndexOutOfBoundsException
     *          If {@code cmdarray} is an empty array
     *          (has length {@code 0})
     *
     * @see     ProcessBuilder
     */
    public Process exec(String[] cmdarray, String[] envp) throws IOException {
        return exec(cmdarray, envp, null);
    }


    /**
     * Executes the specified command and arguments in a separate process with
     * the specified environment and working directory.
     *
     * <p>Given an array of strings {@code cmdarray}, representing the
     * tokens of a command line, and an array of strings {@code envp},
     * representing "environment" variable settings, this method creates
     * a new process in which to execute the specified command.
     *
     * <p>This method checks that {@code cmdarray} is a valid operating
     * system command.  Which commands are valid is system-dependent,
     * but at the very least the command must be a non-empty list of
     * non-null strings.
     *
     * <p>If {@code envp} is {@code null}, the subprocess inherits the
     * environment settings of the current process.
     *
     * <p>A minimal set of system dependent environment variables may
     * be required to start a process on some operating systems.
     * As a result, the subprocess may inherit additional environment variable
     * settings beyond those in the specified environment.
     *
     * <p>{@link ProcessBuilder#start()} is now the preferred way to
     * start a process with a modified environment.
     *
     * <p>The working directory of the new subprocess is specified by {@code dir}.
     * If {@code dir} is {@code null}, the subprocess inherits the
     * current working directory of the current process.
     *
     * <p>If a security manager exists, its
     * {@link SecurityManager#checkExec checkExec}
     * method is invoked with the first component of the array
     * {@code cmdarray} as its argument. This may result in a
     * {@link SecurityException} being thrown.
     *
     * <p>Starting an operating system process is highly system-dependent.
     * Among the many things that can go wrong are:
     * <ul>
     * <li>The operating system program file was not found.
     * <li>Access to the program file was denied.
     * <li>The working directory does not exist.
     * </ul>
     *
     * <p>In such cases an exception will be thrown.  The exact nature
     * of the exception is system-dependent, but it will always be a
     * subclass of {@link IOException}.
     *
     * <p>If the operating system does not support the creation of
     * processes, an {@link UnsupportedOperationException} will be thrown.
     *
     *
     * @param   cmdarray  array containing the command to call and
     *                    its arguments.
     *
     * @param   envp      array of strings, each element of which
     *                    has environment variable settings in the format
     *                    <i>name</i>=<i>value</i>, or
     *                    {@code null} if the subprocess should inherit
     *                    the environment of the current process.
     *
     * @param   dir       the working directory of the subprocess, or
     *                    {@code null} if the subprocess should inherit
     *                    the working directory of the current process.
     *
     * @return  A new {@link Process} object for managing the subprocess
     *
     * @throws  SecurityException
     *          If a security manager exists and its
     *          {@link SecurityManager#checkExec checkExec}
     *          method doesn't allow creation of the subprocess
     *
     * @throws  UnsupportedOperationException
     *          If the operating system does not support the creation of processes.
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  NullPointerException
     *          If {@code cmdarray} is {@code null},
     *          or one of the elements of {@code cmdarray} is {@code null},
     *          or one of the elements of {@code envp} is {@code null}
     *
     * @throws  IndexOutOfBoundsException
     *          If {@code cmdarray} is an empty array
     *          (has length {@code 0})
     *
     * @see     ProcessBuilder
     * @since 1.3
     */
    public Process exec(String[] cmdarray, String[] envp, File dir)
        throws IOException {
        return new ProcessBuilder(cmdarray)
            .environment(envp)
            .directory(dir)
            .start();
    }

    /**
     * Returns the number of processors available to the Java virtual machine.
     *
     * <p> This value may change during a particular invocation of the virtual
     * machine.  Applications that are sensitive to the number of available
     * processors should therefore occasionally poll this property and adjust
     * their resource usage appropriately. </p>
     *
     * @return  the maximum number of processors available to the virtual
     *          machine; never smaller than one
     * @since 1.4
     */
    public native int availableProcessors();

    /**
     * Returns the amount of free memory in the Java Virtual Machine.
     * Calling the
     * {@code gc} method may result in increasing the value returned
     * by {@code freeMemory.}
     *
     * @return  an approximation to the total amount of memory currently
     *          available for future allocated objects, measured in bytes.
     */
    public native long freeMemory();

    /**
     * Returns the total amount of memory in the Java virtual machine.
     * The value returned by this method may vary over time, depending on
     * the host environment.
     * <p>
     * Note that the amount of memory required to hold an object of any
     * given type may be implementation-dependent.
     *
     * @return  the total amount of memory currently available for current
     *          and future objects, measured in bytes.
     */
    public native long totalMemory();

    /**
     * Returns the maximum amount of memory that the Java virtual machine
     * will attempt to use.  If there is no inherent limit then the value
     * {@link java.lang.Long#MAX_VALUE} will be returned.
     *
     * @return  the maximum amount of memory that the virtual machine will
     *          attempt to use, measured in bytes
     * @since 1.4
     */
    public native long maxMemory();

    /**
     * Runs the garbage collector.
     * Calling this method suggests that the Java virtual machine expend
     * effort toward recycling unused objects in order to make the memory
     * they currently occupy available for quick reuse. When control
     * returns from the method call, the virtual machine has made
     * its best effort to recycle all discarded objects.
     * <p>
     * The name {@code gc} stands for "garbage
     * collector". The virtual machine performs this recycling
     * process automatically as needed, in a separate thread, even if the
     * {@code gc} method is not invoked explicitly.
     * <p>
     * The method {@link System#gc()} is the conventional and convenient
     * means of invoking this method.
     */
    public native void gc();

    /* Wormhole for calling java.lang.ref.Finalizer.runFinalization */
    private static native void runFinalization0();

    /**
     * Runs the finalization methods of any objects pending finalization.
     * Calling this method suggests that the Java virtual machine expend
     * effort toward running the {@code finalize} methods of objects
     * that have been found to be discarded but whose {@code finalize}
     * methods have not yet been run. When control returns from the
     * method call, the virtual machine has made a best effort to
     * complete all outstanding finalizations.
     * <p>
     * The virtual machine performs the finalization process
     * automatically as needed, in a separate thread, if the
     * {@code runFinalization} method is not invoked explicitly.
     * <p>
     * The method {@link System#runFinalization()} is the conventional
     * and convenient means of invoking this method.
     *
     * @see     java.lang.Object#finalize()
     */
    public void runFinalization() {
        runFinalization0();
    }

    /**
     * Not implemented, does nothing.
     *
     * @deprecated
     * This method was intended to control instruction tracing.
     * It has been superseded by JVM-specific tracing mechanisms.
     *
     * @param on ignored
     */
    @Deprecated(since="9", forRemoval=true)
    public void traceInstructions(boolean on) { }

    /**
     * Not implemented, does nothing.
     *
     * @deprecated
     * This method was intended to control method call tracing.
     * It has been superseded by JVM-specific tracing mechanisms.
     *
     * @param on ignored
     */
    @Deprecated(since="9", forRemoval=true)
    public void traceMethodCalls(boolean on) { }

    /**
     * Loads the native library specified by the filename argument.  The filename
     * argument must be an absolute path name.
     * (for example
     * {@code Runtime.getRuntime().load("/home/avh/lib/libX11.so");}).
     *
     * If the filename argument, when stripped of any platform-specific library
     * prefix, path, and file extension, indicates a library whose name is,
     * for example, L, and a native library called L is statically linked
     * with the VM, then the JNI_OnLoad_L function exported by the library
     * is invoked rather than attempting to load a dynamic library.
     * A filename matching the argument does not have to exist in the file
     * system. See the JNI Specification for more details.
     *
     * Otherwise, the filename argument is mapped to a native library image in
     * an implementation-dependent manner.
     * <p>
     * First, if there is a security manager, its {@code checkLink}
     * method is called with the {@code filename} as its argument.
     * This may result in a security exception.
     * <p>
     * This is similar to the method {@link #loadLibrary(String)}, but it
     * accepts a general file name as an argument rather than just a library
     * name, allowing any file of native code to be loaded.
     * <p>
     * The method {@link System#load(String)} is the conventional and
     * convenient means of invoking this method.
     *
     * @param      filename   the file to load.
     * @exception  SecurityException  if a security manager exists and its
     *             {@code checkLink} method doesn't allow
     *             loading of the specified dynamic library
     * @exception  UnsatisfiedLinkError  if either the filename is not an
     *             absolute path name, the native library is not statically
     *             linked with the VM, or the library cannot be mapped to
     *             a native library image by the host system.
     * @exception  NullPointerException if {@code filename} is
     *             {@code null}
     * @see        java.lang.Runtime#getRuntime()
     * @see        java.lang.SecurityException
     * @see        java.lang.SecurityManager#checkLink(java.lang.String)
     */
    @CallerSensitive
    public void load(String filename) {
        load0(Reflection.getCallerClass(), filename);
    }

    synchronized void load0(Class<?> fromClass, String filename) {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkLink(filename);
        }
        if (!(new File(filename).isAbsolute())) {
            throw new UnsatisfiedLinkError(
                "Expecting an absolute path of the library: " + filename);
        }
        ClassLoader.loadLibrary(fromClass, filename, true);
    }

    /**
     * Loads the native library specified by the {@code libname}
     * argument.  The {@code libname} argument must not contain any platform
     * specific prefix, file extension or path. If a native library
     * called {@code libname} is statically linked with the VM, then the
     * JNI_OnLoad_{@code libname} function exported by the library is invoked.
     * See the JNI Specification for more details.
     *
     * Otherwise, the libname argument is loaded from a system library
     * location and mapped to a native library image in an implementation-
     * dependent manner.
     * <p>
     * First, if there is a security manager, its {@code checkLink}
     * method is called with the {@code libname} as its argument.
     * This may result in a security exception.
     * <p>
     * The method {@link System#loadLibrary(String)} is the conventional
     * and convenient means of invoking this method. If native
     * methods are to be used in the implementation of a class, a standard
     * strategy is to put the native code in a library file (call it
     * {@code LibFile}) and then to put a static initializer:
     * <blockquote><pre>
     * static { System.loadLibrary("LibFile"); }
     * </pre></blockquote>
     * within the class declaration. When the class is loaded and
     * initialized, the necessary native code implementation for the native
     * methods will then be loaded as well.
     * <p>
     * If this method is called more than once with the same library
     * name, the second and subsequent calls are ignored.
     *
     * @param      libname   the name of the library.
     * @exception  SecurityException  if a security manager exists and its
     *             {@code checkLink} method doesn't allow
     *             loading of the specified dynamic library
     * @exception  UnsatisfiedLinkError if either the libname argument
     *             contains a file path, the native library is not statically
     *             linked with the VM,  or the library cannot be mapped to a
     *             native library image by the host system.
     * @exception  NullPointerException if {@code libname} is
     *             {@code null}
     * @see        java.lang.SecurityException
     * @see        java.lang.SecurityManager#checkLink(java.lang.String)
     */
    @CallerSensitive
    public void loadLibrary(String libname) {
        loadLibrary0(Reflection.getCallerClass(), libname);
    }

    synchronized void loadLibrary0(Class<?> fromClass, String libname) {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkLink(libname);
        }
        if (libname.indexOf((int)File.separatorChar) != -1) {
            throw new UnsatisfiedLinkError(
    "Directory separator should not appear in library name: " + libname);
        }
        ClassLoader.loadLibrary(fromClass, libname, false);
    }

    /**
     * Creates a localized version of an input stream. This method takes
     * an {@code InputStream} and returns an {@code InputStream}
     * equivalent to the argument in all respects except that it is
     * localized: as characters in the local character set are read from
     * the stream, they are automatically converted from the local
     * character set to Unicode.
     * <p>
     * If the argument is already a localized stream, it may be returned
     * as the result.
     *
     * @param      in InputStream to localize
     * @return     a localized input stream
     * @see        java.io.InputStream
     * @see        java.io.BufferedReader#BufferedReader(java.io.Reader)
     * @see        java.io.InputStreamReader#InputStreamReader(java.io.InputStream)
     * @deprecated As of JDK&nbsp;1.1, the preferred way to translate a byte
     * stream in the local encoding into a character stream in Unicode is via
     * the {@code InputStreamReader} and {@code BufferedReader}
     * classes.
     * This method is subject to removal in a future version of Java SE.
     */
    @Deprecated(since="1.1", forRemoval=true)
    public InputStream getLocalizedInputStream(InputStream in) {
        return in;
    }

    /**
     * Creates a localized version of an output stream. This method
     * takes an {@code OutputStream} and returns an
     * {@code OutputStream} equivalent to the argument in all respects
     * except that it is localized: as Unicode characters are written to
     * the stream, they are automatically converted to the local
     * character set.
     * <p>
     * If the argument is already a localized stream, it may be returned
     * as the result.
     *
     * @deprecated As of JDK&nbsp;1.1, the preferred way to translate a
     * Unicode character stream into a byte stream in the local encoding is via
     * the {@code OutputStreamWriter}, {@code BufferedWriter}, and
     * {@code PrintWriter} classes.
     * This method is subject to removal in a future version of Java SE.
     *
     * @param      out OutputStream to localize
     * @return     a localized output stream
     * @see        java.io.OutputStream
     * @see        java.io.BufferedWriter#BufferedWriter(java.io.Writer)
     * @see        java.io.OutputStreamWriter#OutputStreamWriter(java.io.OutputStream)
     * @see        java.io.PrintWriter#PrintWriter(java.io.OutputStream)
     */
    @Deprecated(since="1.1", forRemoval=true)
    public OutputStream getLocalizedOutputStream(OutputStream out) {
        return out;
    }

}
