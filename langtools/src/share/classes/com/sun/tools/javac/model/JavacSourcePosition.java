/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.model;

import javax.tools.JavaFileObject;
import com.sun.tools.javac.util.Position;

/**
 * Implementation of model API SourcePosition based on javac internal state.
 *
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
class JavacSourcePosition {

    final JavaFileObject sourcefile;
    final int pos;
    final Position.LineMap lineMap;

    JavacSourcePosition(JavaFileObject sourcefile,
                        int pos,
                        Position.LineMap lineMap) {
        this.sourcefile = sourcefile;
        this.pos = pos;
        this.lineMap = (pos != Position.NOPOS) ? lineMap : null;
    }

    public JavaFileObject getFile() {
        return sourcefile;
    }

    public int getOffset() {
        return pos;     // makes use of fact that Position.NOPOS == -1
    }

    public int getLine() {
        return (lineMap != null) ? lineMap.getLineNumber(pos) : -1;
    }

    public int getColumn() {
        return (lineMap != null) ? lineMap.getColumnNumber(pos) : -1;
    }

    public String toString() {
        int line = getLine();
        return (line > 0)
              ? sourcefile + ":" + line
              : sourcefile.toString();
    }
}
