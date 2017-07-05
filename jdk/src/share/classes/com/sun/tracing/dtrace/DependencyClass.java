/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tracing.dtrace;

/**
 * Enumeration for the DTrace dependency classes.
 *
 * @see <a href="http://docs.sun.com/app/docs/doc/817-6223/6mlkidlnp?a=view">Solaris Dynamic Tracing Guide for details, Chapter 39: Stability</a>
 * @since 1.7
 */
public enum DependencyClass {
    /**
     * The interface has an unknown set of architectural dependencies.
     */
    UNKNOWN  (0),
    /**
     * The interface is specific to the CPU model of the current system.
     */
    CPU      (1),
    /**
     * The interface is specific to the hardware platform of the current
     * system.
     */
    PLATFORM (2),
    /**
     * The interface is specific to the hardware platform group of the
     * current system.
     */
    GROUP    (3),
    /**
     * The interface is specific to the instruction set architecture (ISA)
     * supported by the microprocessors on this system.
     */
    ISA      (4),
    /**
     * The interface is common to all Solaris systems regardless of the
     * underlying hardware.
     */
    COMMON   (5);

    public String toDisplayString() {
        return toString().substring(0,1) +
               toString().substring(1).toLowerCase();
    }

    public int getEncoding() { return encoding; }

    private int encoding;

    private DependencyClass(int encoding) {
        this.encoding = encoding;
    }
}

