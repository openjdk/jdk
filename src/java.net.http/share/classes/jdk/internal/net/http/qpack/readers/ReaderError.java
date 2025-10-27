/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http.qpack.readers;

import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.qpack.QPackException;

/**
 * QPack readers configuration record to be used by the readers to
 * report errors.
 * @param http3Error corresponding HTTP/3 error code.
 * @param isConnectionError if the reader error should be treated
 *                          as connection error.
 */
record ReaderError(Http3Error http3Error, boolean isConnectionError) {

    /**
     * Construct a {@link QPackException} from  on {@code http3Error},
     * {@code isConnectionError} and provided {@code "cause"} values.
     * @param cause cause of the constructed {@link QPackException}
     * @return a {@code QPackException} instance.
     */
    QPackException toQPackException(Throwable cause) {
        return new QPackException(http3Error, cause, isConnectionError);
    }
}
