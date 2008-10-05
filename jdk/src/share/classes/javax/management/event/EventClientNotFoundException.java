/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.management.event;

import javax.management.JMException;

/**
 * Thrown if an event client identifier is unknown.
 */
public class EventClientNotFoundException extends JMException {

    /* Serial version */
    private static final long serialVersionUID = -3910667345840643089L;

    /**
     *Constructs a {@code ClientNotFoundException} without a detail message.
     */
    public EventClientNotFoundException() {
        super();
    }

    /**
     * Constructs a {@code ClientNotFoundException} with the specified detail message.
     * @param message The message.
     */
    public EventClientNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code ClientNotFoundException} with the specified detail message
     * and cause.
     *
     * @param message The message.
     * @param cause The cause (which is saved for later retrieval by the
     * {@code Throwable.getCause()} method). A null value is permitted, and indicates
     * that the cause is non-existent or unknown.
     */
    public EventClientNotFoundException(String message, Throwable cause) {
        super(message);

        initCause(cause);
    }

    /**
     * Constructs a new exception with the specified cause.
     * @param cause The cause (which is saved for later retrieval by the
     * {@code Throwable.getCause()} method). A null value is permitted, and indicates
     * that the cause is non-existent or unknown.
     */
    public EventClientNotFoundException(Throwable cause) {
        super();

        initCause(cause);
    }
}
