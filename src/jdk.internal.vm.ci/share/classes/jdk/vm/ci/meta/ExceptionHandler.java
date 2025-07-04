/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.vm.ci.meta;

/**
 * Represents an exception handler within the bytecodes.
 *
 * @param startBCI     the start index of the protected range
 * @param endBCI       the end index of the protected range
 * @param handlerBCI   the index of the handler
 * @param catchTypeCPI the index of the throwable class in the constant pool
 * @param catchType    the type caught by this exception handler
 */
public record ExceptionHandler(int      startBCI,
                               int      endBCI,
                               int      handlerBCI,
                               int      catchTypeCPI,
                               JavaType catchType) {
    /**
     * Checks whether this handler catches all exceptions.
     *
     * @return {@code true} if this handler catches all exceptions
     */
    public boolean isCatchAll() {
        return catchTypeCPI == 0;
    }
}
