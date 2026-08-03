/*
 * Copyright (c) 1995, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Thrown when a thread executing a blocking method is {@linkplain Thread#interrupt()
 * interrupted}. {@link Thread#sleep(long) Thread.sleep}, {@link Object#wait()
 * Object.wait} and many other blocking methods throw this exception if interrupted.
 *
 * <p> Blocking methods that throw {@code InterruptedException} clear the thread's
 * interrupted status before throwing the exception. Code that catches {@code
 * InterruptedException} should rethrow the exception, or restore the current thread's
 * interrupted status, with {@link Thread#currentThread()
 * Thread.currentThread()}.{@link Thread#interrupt() interrupt()}, before continuing
 * normally or handling it by throwing another type of exception.
 *
 * @author  Frank Yellin
 * @see     Thread##thread-interruption Thread Interruption
 * @since   1.0
 */
public class InterruptedException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 6700697376100628473L;

    /**
     * Constructs an {@code InterruptedException} with no detail  message.
     */
    public InterruptedException() {
        super();
    }

    /**
     * Constructs an {@code InterruptedException} with the
     * specified detail message.
     *
     * @param   s   the detail message.
     */
    public InterruptedException(String s) {
        super(s);
    }
}
