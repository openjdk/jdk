/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.api.tree;

import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.runtime.ParserException;

final class DiagnosticImpl implements Diagnostic {
    private final ParserException exp;
    private final Kind kind;

    DiagnosticImpl(final ParserException exp, final Kind kind) {
        this.exp = exp;
        this.kind = kind;
    }

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public long getPosition() {
        return exp.getPosition();
    }

    @Override
    public String getFileName() {
        return exp.getFileName();
    }

    @Override
    public long getLineNumber() {
        return exp.getLineNumber();
    }

    @Override
    public long getColumnNumber() {
        return exp.getColumnNumber();
    }

    @Override
    public String getCode() {
        final long token = exp.getToken();
        return (token < 0)? null : Token.toString(null, token, true);
    }

    @Override
    public String getMessage() {
        return exp.getMessage();
    }

    @Override
    public String toString() {
        return getMessage();
    }
}
