/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.test_generator;
public class CodeSegment {
    private final String statics;
    private final StringBuilder calls;
    private final StringBuilder methods;
    private final String imports;
    public CodeSegment(String statics, String calls, String methods, String imports) {
        this.statics = statics;
        this.calls = new StringBuilder(calls);
        this.methods = new StringBuilder(methods);
        this.imports = imports;
    }
    public String getStatics() {
        return statics;
    }
    public void appendCall(String calls) {
        this.calls.append(calls);
    }
    public String getCalls() {
        return calls.toString();
    }
    public void appendMethods(String method) {
        this.methods.append(method);
    }
    public String getMethods() {
        return methods.toString();
    }
    public String getImports() {
        return imports;
    }
}
