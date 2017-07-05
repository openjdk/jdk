/*
 * Copyright 1998-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * Provides classes for performing arbitrary-precision integer
 * arithmetic ({@code BigInteger}) and arbitrary-precision decimal
 * arithmetic ({@code BigDecimal}).  {@code BigInteger} is analogous
 * to the primitive integer types except that it provides arbitrary
 * precision, hence operations on {@code BigInteger}s do not overflow
 * or lose precision.  In addition to standard arithmetic operations,
 * {@code BigInteger} provides modular arithmetic, GCD calculation,
 * primality testing, prime generation, bit manipulation, and a few
 * other miscellaneous operations.
 *
 * {@code BigDecimal} provides arbitrary-precision signed decimal
 * numbers suitable for currency calculations and the like.  {@code
 * BigDecimal} gives the user complete control over rounding behavior,
 * allowing the user to choose from a comprehensive set of eight
 * rounding modes.
 *
 * @since JDK1.1
 */
package java.math;
