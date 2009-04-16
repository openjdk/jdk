/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.nio.sctp;

/**
 * Unchecked exception thrown when an attempt is made to invoke the
 * {@code receive} method of {@link SctpChannel} or {@link SctpMultiChannel}
 * from a notification handler.
 *
 * @since 1.7
 */
public class IllegalReceiveException extends IllegalStateException {
    private static final long serialVersionUID = 2296619040988576224L;

    /**
     * Constructs an instance of this class.
     */
    public IllegalReceiveException() { }

    /**
     * Constructs an instance of this class with the specified message.
     */
    public IllegalReceiveException(String msg) {
        super(msg);
    }
}

