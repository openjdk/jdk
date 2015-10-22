/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.code;

/**
 * Class representing a exception with a stack trace of the currently processed position in the
 * compiled Java program instead of the stack trace of the compiler. The exception of the compiler
 * is saved as the cause of this exception.
 */
public abstract class SourceStackTrace extends BailoutException {
    private static final long serialVersionUID = 2144811793442316776L;

    public static SourceStackTrace create(Throwable cause, String format, StackTraceElement[] elements) {
        return new SourceStackTrace(cause, format) {

            private static final long serialVersionUID = 6279381376051787907L;

            @Override
            public final synchronized Throwable fillInStackTrace() {
                assert elements != null;
                setStackTrace(elements);
                return this;
            }
        };
    }

    private SourceStackTrace(Throwable cause, String format) {
        super(cause, format);
    }
}
