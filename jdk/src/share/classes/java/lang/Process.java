/*
 * Copyright (c) 1995, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.TimeUnit;

/**
 * The {@link ProcessBuilder#start()} and
 * {@link Runtime#exec(String[],String[],File) Runtime.exec}
 * methods create a native process and return an instance of a
 * subclass of {@code Process} that can be used to control the process
 * and obtain information about it.  The class {@code Process}
 * provides methods for performing input from the process, performing
 * output to the process, waiting for the process to complete,
 * checking the exit status of the process, and destroying (killing)
 * the process.
 *
 * <p>The methods that create processes may not work well for special
 * processes on certain native platforms, such as native windowing
 * processes, daemon processes, Win16/DOS processes on Microsoft
 * Windows, or shell scripts.
 *
 * <p>By default, the created subprocess does not have its own terminal
 * or console.  All its standard I/O (i.e. stdin, stdout, stderr)
 * operations will be redirected to the parent process, where they can
 * be accessed via the streams obtained using the methods
 * {@link #getOutputStream()},
 * {@link #getInputStream()}, and
 * {@link #getErrorStream()}.
 * The parent process uses these streams to feed input to and get output
 * from the subprocess.  Because some native platforms only provide
 * limited buffer size for standard input and output streams, failure
 * to promptly write the input stream or read the output stream of
 * the subprocess may cause the subprocess to block, or even deadlock.
 *
 * <p>Where desired, <a href="ProcessBuilder.html#redirect-input">
 * subprocess I/O can also be redirected</a>
 * using methods of the {@link ProcessBuilder} class.
 *
 * <p>The subprocess is not killed when there are no more references to
 * the {@code Process} object, but rather the subprocess
 * continues executing asynchronously.
 *
 * <p>There is no requirement that a process represented by a {@code
 * Process} object execute asynchronously or concurrently with respect
 * to the Java process that owns the {@code Process} object.
 *
 * <p>As of 1.5, {@link ProcessBuilder#start()} is the preferred way
 * to create a {@code Process}.
 *
 * @since   JDK1.0
 */
public abstract class Process {
    /**
     * Returns the output stream connected to the normal input of the
     * subprocess.  Output to the stream is piped into the standard
     * input of the process represented by this {@code Process} object.
     *
     * <p>If the standard input of the subprocess has been redirected using
     * {@link ProcessBuilder#redirectInput(Redirect)
     * ProcessBuilder.redirectInput}
     * then this method will return a
     * <a href="ProcessBuilder.html#redirect-input">null output stream</a>.
     *
     * <p>Implementation note: It is a good idea for the returned
     * output stream to be buffered.
     *
     * @return the output stream connected to the normal input of the
     *         subprocess
     */
    public abstract OutputStream getOutputStream();

    /**
     * Returns the input stream connected to the normal output of the
     * subprocess.  The stream obtains data piped from the standard
     * output of the process represented by this {@code Process} object.
     *
     * <p>If the standard output of the subprocess has been redirected using
     * {@link ProcessBuilder#redirectOutput(Redirect)
     * ProcessBuilder.redirectOutput}
     * then this method will return a
     * <a href="ProcessBuilder.html#redirect-output">null input stream</a>.
     *
     * <p>Otherwise, if the standard error of the subprocess has been
     * redirected using
     * {@link ProcessBuilder#redirectErrorStream(boolean)
     * ProcessBuilder.redirectErrorStream}
     * then the input stream returned by this method will receive the
     * merged standard output and the standard error of the subprocess.
     *
     * <p>Implementation note: It is a good idea for the returned
     * input stream to be buffered.
     *
     * @return the input stream connected to the normal output of the
     *         subprocess
     */
    public abstract InputStream getInputStream();

    /**
     * Returns the input stream connected to the error output of the
     * subprocess.  The stream obtains data piped from the error output
     * of the process represented by this {@code Process} object.
     *
     * <p>If the standard error of the subprocess has been redirected using
     * {@link ProcessBuilder#redirectError(Redirect)
     * ProcessBuilder.redirectError} or
     * {@link ProcessBuilder#redirectErrorStream(boolean)
     * ProcessBuilder.redirectErrorStream}
     * then this method will return a
     * <a href="ProcessBuilder.html#redirect-output">null input stream</a>.
     *
     * <p>Implementation note: It is a good idea for the returned
     * input stream to be buffered.
     *
     * @return the input stream connected to the error output of
     *         the subprocess
     */
    public abstract InputStream getErrorStream();

    /**
     * Causes the current thread to wait, if necessary, until the
     * process represented by this {@code Process} object has
     * terminated.  This method returns immediately if the subprocess
     * has already terminated.  If the subprocess has not yet
     * terminated, the calling thread will be blocked until the
     * subprocess exits.
     *
     * @return the exit value of the subprocess represented by this
     *         {@code Process} object.  By convention, the value
     *         {@code 0} indicates normal termination.
     * @throws InterruptedException if the current thread is
     *         {@linkplain Thread#interrupt() interrupted} by another
     *         thread while it is waiting, then the wait is ended and
     *         an {@link InterruptedException} is thrown.
     */
    public abstract int waitFor() throws InterruptedException;

    /**
     * Causes the current thread to wait, if necessary, until the
     * subprocess represented by this {@code Process} object has
     * terminated, or the specified waiting time elapses.
     *
     * <p>If the subprocess has already terminated then this method returns
     * immediately with the value {@code true}.  If the process has not
     * terminated and the timeout value is less than, or equal to, zero, then
     * this method returns immediately with the value {@code false}.
     *
     * <p>The default implementation of this methods polls the {@code exitValue}
     * to check if the process has terminated. Concrete implementations of this
     * class are strongly encouraged to override this method with a more
     * efficient implementation.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the {@code timeout} argument
     * @return {@code true} if the subprocess has exited and {@code false} if
     *         the waiting time elapsed before the subprocess has exited.
     * @throws InterruptedException if the current thread is interrupted
     *         while waiting.
     * @throws NullPointerException if unit is null
     * @since 1.8
     */
    public boolean waitFor(long timeout, TimeUnit unit)
        throws InterruptedException
    {
        long startTime = System.nanoTime();
        long rem = unit.toNanos(timeout);

        do {
            try {
                exitValue();
                return true;
            } catch(IllegalThreadStateException ex) {
                if (rem > 0)
                    Thread.sleep(
                        Math.min(TimeUnit.NANOSECONDS.toMillis(rem) + 1, 100));
            }
            rem = unit.toNanos(timeout) - (System.nanoTime() - startTime);
        } while (rem > 0);
        return false;
    }

    /**
     * Returns the exit value for the subprocess.
     *
     * @return the exit value of the subprocess represented by this
     *         {@code Process} object.  By convention, the value
     *         {@code 0} indicates normal termination.
     * @throws IllegalThreadStateException if the subprocess represented
     *         by this {@code Process} object has not yet terminated
     */
    public abstract int exitValue();

    /**
     * Kills the subprocess. Whether the subprocess represented by this
     * {@code Process} object is forcibly terminated or not is
     * implementation dependent.
     */
    public abstract void destroy();

    /**
     * Kills the subprocess. The subprocess represented by this
     * {@code Process} object is forcibly terminated.
     *
     * <p>The default implementation of this method invokes {@link #destroy}
     * and so may not forcibly terminate the process. Concrete implementations
     * of this class are strongly encouraged to override this method with a
     * compliant implementation.  Invoking this method on {@code Process}
     * objects returned by {@link ProcessBuilder#start} and
     * {@link Runtime#exec} will forcibly terminate the process.
     *
     * <p>Note: The subprocess may not terminate immediately.
     * i.e. {@code isAlive()} may return true for a brief period
     * after {@code destroyForcibly()} is called. This method
     * may be chained to {@code waitFor()} if needed.
     *
     * @return the {@code Process} object representing the
     *         subprocess to be forcibly destroyed.
     * @since 1.8
     */
    public Process destroyForcibly() {
        destroy();
        return this;
    }

    /**
     * Tests whether the subprocess represented by this {@code Process} is
     * alive.
     *
     * @return {@code true} if the subprocess represented by this
     *         {@code Process} object has not yet terminated.
     * @since 1.8
     */
    public boolean isAlive() {
        try {
            exitValue();
            return false;
        } catch(IllegalThreadStateException e) {
            return true;
        }
    }
}
