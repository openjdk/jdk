/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot.events;

import jdk.vm.ci.common.JVMCIError;

/**
 * An empty implementation for {@link EventProvider}. This implementation is used when no logging is
 * requested.
 */
public final class EmptyEventProvider implements EventProvider {

    public CompilationEvent newCompilationEvent() {
        return new EmptyCompilationEvent();
    }

    public static class EmptyCompilationEvent implements CompilationEvent {
        public void commit() {
            throw JVMCIError.shouldNotReachHere();
        }

        public boolean shouldWrite() {
            // Events of this class should never been written.
            return false;
        }

        public void begin() {
        }

        public void end() {
        }

        public void setMethod(String method) {
            throw JVMCIError.shouldNotReachHere();
        }

        public void setCompileId(int compileId) {
            throw JVMCIError.shouldNotReachHere();
        }

        public void setCompileLevel(int compileLevel) {
            throw JVMCIError.shouldNotReachHere();
        }

        public void setSucceeded(boolean succeeded) {
            throw JVMCIError.shouldNotReachHere();
        }

        public void setIsOsr(boolean isOsr) {
            throw JVMCIError.shouldNotReachHere();
        }

        public void setCodeSize(int codeSize) {
            throw JVMCIError.shouldNotReachHere();
        }

        public void setInlinedBytes(int inlinedBytes) {
            throw JVMCIError.shouldNotReachHere();
        }
    }

    public CompilerFailureEvent newCompilerFailureEvent() {
        return new EmptyCompilerFailureEvent();
    }

    public static class EmptyCompilerFailureEvent implements CompilerFailureEvent {
        public void commit() {
            throw JVMCIError.shouldNotReachHere();
        }

        public boolean shouldWrite() {
            // Events of this class should never been written.
            return false;
        }

        public void setCompileId(int compileId) {
            throw JVMCIError.shouldNotReachHere();
        }

        public void setMessage(String message) {
            throw JVMCIError.shouldNotReachHere();
        }
    }

}
