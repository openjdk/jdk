/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.codemodel.internal.writer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import com.sun.codemodel.internal.CodeWriter;
import com.sun.codemodel.internal.JPackage;

/**
 * Writes all the source files under the specified file folder and
 * inserts a file prolog comment in each java source file.
 *
 * @author
 *  Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class PrologCodeWriter extends FilterCodeWriter {

    /** prolog comment */
    private final String prolog;

    /**
     * @param core
     *      This CodeWriter will be used to actually create a storage for files.
     *      PrologCodeWriter simply decorates this underlying CodeWriter by
     *      adding prolog comments.
     * @param prolog
     *      Strings that will be added as comments.
     *      This string may contain newlines to produce multi-line comments.
     *      '//' will be inserted at the beginning of each line to make it
     *      a valid Java comment, so the caller can just pass strings like
     *      "abc\ndef"
     */
    public PrologCodeWriter( CodeWriter core, String prolog ) {
        super(core);
        this.prolog = prolog;
    }


    @Override
    public Writer openSource(JPackage pkg, String fileName) throws IOException {
        Writer w = super.openSource(pkg,fileName);

        PrintWriter out = new PrintWriter(w);

        // write prolog if this is a java source file
        if( prolog != null ) {
            out.println( "//" );

            String s = prolog;
            int idx;
            while( (idx=s.indexOf('\n'))!=-1 ) {
                out.println("// "+ s.substring(0,idx) );
                s = s.substring(idx+1);
            }
            out.println("//");
            out.println();
        }
        out.flush();    // we can't close the stream for that would close the undelying stream.

        return w;
    }
}
