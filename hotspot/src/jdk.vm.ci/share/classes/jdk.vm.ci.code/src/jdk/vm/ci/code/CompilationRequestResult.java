/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * Simple class to provide information about the result of a compile request.
 */
public final class CompilationRequestResult {

    /**
     * A user readable description of the failure.
     */
    private final String failureMessage;

    /**
     * Whether this is a transient failure where retrying would help.
     */
    private final boolean retry;

    /**
     * Number of bytecodes inlined into the compilation, exclusive of the bytecodes in the root
     * method.
     */
    private final int inlinedBytecodes;

    private CompilationRequestResult(String failureMessage, boolean retry, int inlinedBytecodes) {
        this.failureMessage = failureMessage;
        this.retry = retry;
        this.inlinedBytecodes = inlinedBytecodes;
    }

    public static CompilationRequestResult success(int inlinedBytecodes) {
        return new CompilationRequestResult(null, true, inlinedBytecodes);
    }

    public static CompilationRequestResult failure(String failureMessage, boolean retry) {
        return new CompilationRequestResult(failureMessage, retry, 0);
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public boolean getRetry() {
        return retry;
    }

    public int getInlinedBytecodes() {
        return inlinedBytecodes;
    }
}
