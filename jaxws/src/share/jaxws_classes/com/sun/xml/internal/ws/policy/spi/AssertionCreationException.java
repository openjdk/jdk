/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.policy.spi;

import com.sun.xml.internal.ws.policy.PolicyException;
import com.sun.xml.internal.ws.policy.sourcemodel.AssertionData;

/**
 * Exception thrown in case of assertion creation failure.
 *
 * @author Marek Potociar
 */
public final class AssertionCreationException extends PolicyException {

    private final AssertionData assertionData;

    /**
     * Constructs a new assertion creation exception with the specified detail message and cause.
     * <p/>
     * Note that the detail message associated with {@code cause} is <emph>not</emph> automatically incorporated in
     * this exception's detail message.
     *
     * @param assertionData the data provided for assertion creation
     * @param  message the detail message.
     */
    public AssertionCreationException(final AssertionData assertionData, final String message) {
        super(message);
        this.assertionData = assertionData;
    }

    /**
     * Constructs a new assertion creation exception with the specified detail message and cause.
     * <p/>
     * Note that the detail message associated with {@code cause} is <emph>not</emph> automatically incorporated in
     * this exception's detail message.
     *
     * @param assertionData the data provided for assertion creation
     * @param  message the detail message.
     * @param  cause the cause.  (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public AssertionCreationException(final AssertionData assertionData, final String message, final Throwable cause) {
        super(message, cause);
        this.assertionData = assertionData;
    }

    /**
     * Constructs a new assertion creation exception with the specified detail message and cause.
     *
     * @param assertionData the data provided for assertion creation
     * @param  cause the cause.  (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public AssertionCreationException(AssertionData assertionData, Throwable cause) {
        super(cause);
        this.assertionData = assertionData;
    }

    /**
     * Retrieves assertion data associated with the exception.
     *
     * @return associated assertion data (present when assertion creation failed raising this exception).
     */
    public AssertionData getAssertionData() {
        return this.assertionData;
    }
}
