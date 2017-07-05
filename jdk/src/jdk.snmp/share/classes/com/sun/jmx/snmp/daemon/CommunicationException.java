/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jmx.snmp.daemon;

// java import
//
import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Represents exceptions raised due to communications problems,
 * for example when a managed object server is out of reach.<p>
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

public class CommunicationException extends javax.management.JMRuntimeException {

    /* Serial version */
    private static final long serialVersionUID = -2499186113233316177L;

    /**
     * Constructs a CommunicationException with a target exception.
     */
    public CommunicationException(Throwable target) {
        super(target.getMessage());
        initCause(target);
    }

    /**
     * Constructs a CommunicationException with a target exception
     * and a detail message.
     */
    public CommunicationException(Throwable target, String msg) {
        super(msg);
        initCause(target);
    }

    /**
     * Constructs a CommunicationException with a detail message.
     */
    public CommunicationException(String msg) {
        super(msg);
    }

    /**
     * Get the thrown target exception.
     */
    public Throwable getTargetException() {
        return getCause();
    }

}
