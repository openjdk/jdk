/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Copyright (c) 2008-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.time.format;

import java.io.IOException;
import java.time.DateTimeException;

/**
 * An exception thrown when an error occurs during printing.
 * <p>
 * This will be triggered by violations specific to printing or an IO exception.
 *
 * <h3>Specification for implementors</h3>
 * This class is intended for use in a single thread.
 *
 * @since 1.8
 */
public class DateTimePrintException extends DateTimeException {

    /**
     * Serialization version.
     */
    private static final long serialVersionUID = 2263939197574006408L;

    /**
     * Constructs a new exception with the specified message.
     *
     * @param message  the message to use for this exception, may be null
     */
    public DateTimePrintException(String message) {
        super(message, null);
    }

    /**
     * Constructs a new exception with the specified message and cause.
     *
     * @param message  the message to use for this exception, may be null
     * @param cause  the cause of the exception, may be null
     */
    public DateTimePrintException(String message, Throwable cause) {
        super(message, cause);
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if the cause of this exception was an IOException, and if so
     * re-throws it
     * <p>
     * This method is useful if you call a printer with an open stream or
     * writer and want to ensure that IOExceptions are not lost.
     * <pre>
     * try {
     *   printer.print(writer, dateTime);
     * } catch (DateTimePrintException ex) {
     *   ex.rethrowIOException();
     *   // if code reaches here exception was caused by date-time issues
     * }
     * </pre>
     * Note that calling this method will re-throw the original IOException,
     * causing this DateTimePrintException to be lost.
     *
     * @throws IOException if the cause of this exception is an IOException
     */
    public void rethrowIOException() throws IOException {
        if (getCause() instanceof IOException) {
            throw (IOException) getCause();
        }
    }

}
