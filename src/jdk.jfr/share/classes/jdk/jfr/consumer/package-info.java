/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This package contains classes for consuming Flight Recorder data.
 * <p>
 * In the following example, the program prints a histogram of all method samples in a recording.
 *
 * {@snippet class="Snippets" region="PackageOverview"}
 * <p>
 * <b>Null-handling</b>
 * <p>
 * All methods define whether they accept or return {@code null} in the Javadoc.
 * Typically this is expressed as {@code "not null"}. If a {@code null}
 * parameter is used where it is not allowed, a
 * {@code java.lang.NullPointerException} is thrown. If a {@code null}
 * parameters is passed to a method that throws other exceptions, such as
 * {@code java.io.IOException}, the {@code java.lang.NullPointerException} takes
 * precedence, unless the Javadoc for the method explicitly states how
 * {@code null} is handled, i.e. by throwing {@code java.lang.IllegalArgumentException}.
 *
 * @since 9
 */
package jdk.jfr.consumer;
