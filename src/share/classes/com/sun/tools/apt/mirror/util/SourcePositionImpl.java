/*
 * Copyright 2004-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.apt.mirror.util;


import java.io.File;
import javax.tools.JavaFileObject;

import com.sun.mirror.util.SourcePosition;
import com.sun.tools.javac.util.Position;


/**
 * Implementation of SourcePosition
 */
@SuppressWarnings("deprecation")
public class SourcePositionImpl implements SourcePosition {

    private JavaFileObject sourcefile;
    private int pos;            // file position, in javac's internal format
    private Position.LineMap linemap;


    public SourcePositionImpl(JavaFileObject sourcefile, int pos, Position.LineMap linemap) {
        this.sourcefile = sourcefile;
        this.pos = pos;
        this.linemap = linemap;
        assert sourcefile != null;
        assert linemap != null;
    }

    public int getJavacPosition() {
        return pos;
    }

    public JavaFileObject getSource() {
        return sourcefile;
    }

    /**
     * Returns a string representation of this position in the
     * form "sourcefile:line", or "sourcefile" if no line number is available.
     */
    public String toString() {
        int ln = line();
        return (ln == Position.NOPOS)
                ? sourcefile.getName()
                : sourcefile.getName() + ":" + ln;
    }

    /**
     * {@inheritDoc}
     */
    public File file() {
        return new File(sourcefile.toUri());
    }

    /**
     * {@inheritDoc}
     */
    public int line() {
        return linemap.getLineNumber(pos);
    }

    /**
     * {@inheritDoc}
     */
    public int column() {
        return linemap.getColumnNumber(pos);
    }
}
