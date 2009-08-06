/*
 * Copyright 1996-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.lang.reflect;

/**
 * InvocationTargetException is a checked exception that wraps
 * an exception thrown by an invoked method or constructor.
 *
 * <p>As of release 1.4, this exception has been retrofitted to conform to
 * the general purpose exception-chaining mechanism.  The "target exception"
 * that is provided at construction time and accessed via the
 * {@link #getTargetException()} method is now known as the <i>cause</i>,
 * and may be accessed via the {@link Throwable#getCause()} method,
 * as well as the aforementioned "legacy method."
 *
 * @see Method
 * @see Constructor
 */
public class InvocationTargetException extends ReflectiveOperationException {
    /**
     * Use serialVersionUID from JDK 1.1.X for interoperability
     */
    private static final long serialVersionUID = 4085088731926701167L;

     /**
     * This field holds the target if the
     * InvocationTargetException(Throwable target) constructor was
     * used to instantiate the object
     *
     * @serial
     *
     */
    private Throwable target;

    /**
     * Constructs an {@code InvocationTargetException} with
     * {@code null} as the target exception.
     */
    protected InvocationTargetException() {
        super((Throwable)null);  // Disallow initCause
    }

    /**
     * Constructs a InvocationTargetException with a target exception.
     *
     * @param target the target exception
     */
    public InvocationTargetException(Throwable target) {
        super((Throwable)null);  // Disallow initCause
        this.target = target;
    }

    /**
     * Constructs a InvocationTargetException with a target exception
     * and a detail message.
     *
     * @param target the target exception
     * @param s      the detail message
     */
    public InvocationTargetException(Throwable target, String s) {
        super(s, null);  // Disallow initCause
        this.target = target;
    }

    /**
     * Get the thrown target exception.
     *
     * <p>This method predates the general-purpose exception chaining facility.
     * The {@link Throwable#getCause()} method is now the preferred means of
     * obtaining this information.
     *
     * @return the thrown target exception (cause of this exception).
     */
    public Throwable getTargetException() {
        return target;
    }

    /**
     * Returns the cause of this exception (the thrown target exception,
     * which may be {@code null}).
     *
     * @return  the cause of this exception.
     * @since   1.4
     */
    public Throwable getCause() {
        return target;
    }
}
