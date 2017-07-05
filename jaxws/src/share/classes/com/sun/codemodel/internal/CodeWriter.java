/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.codemodel.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.CharsetEncoder;

import com.sun.codemodel.internal.util.EncoderFactory;
import com.sun.codemodel.internal.util.UnicodeEscapeWriter;

/**
 * Receives generated code and writes to the appropriate storage.
 *
 * @author
 *      Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public abstract class CodeWriter {

    /**
     * Called by CodeModel to store the specified file.
     * The callee must allocate a storage to store the specified file.
     *
     * <p>
     * The returned stream will be closed before the next file is
     * stored. So the callee can assume that only one OutputStream
     * is active at any given time.
     *
     * @param   pkg
     *      The package of the file to be written.
     * @param   fileName
     *      File name without the path. Something like
     *      "Foo.java" or "Bar.properties"
     */
    public abstract OutputStream openBinary( JPackage pkg, String fileName ) throws IOException;

    /**
     * Called by CodeModel to store the specified file.
     * The callee must allocate a storage to store the specified file.
     *
     * <p>
     * The returned stream will be closed before the next file is
     * stored. So the callee can assume that only one OutputStream
     * is active at any given time.
     *
     * @param   pkg
     *      The package of the file to be written.
     * @param   fileName
     *      File name without the path. Something like
     *      "Foo.java" or "Bar.properties"
     */
    public Writer openSource( JPackage pkg, String fileName ) throws IOException {
        final OutputStreamWriter bw = new OutputStreamWriter(openBinary(pkg,fileName));

        // create writer
        try {
            return new UnicodeEscapeWriter(bw) {
                // can't change this signature to Encoder because
                // we can't have Encoder in method signature
                private final CharsetEncoder encoder = EncoderFactory.createEncoder(bw.getEncoding());
                protected boolean requireEscaping(int ch) {
                    // control characters
                    if( ch<0x20 && " \t\r\n".indexOf(ch)==-1 )  return true;
                    // check ASCII chars, for better performance
                    if( ch<0x80 )       return false;

                    return !encoder.canEncode((char)ch);
                }
            };
        } catch( Throwable t ) {
            return new UnicodeEscapeWriter(bw);
        }
    }

    /**
     * Called by CodeModel at the end of the process.
     */
    public abstract void close() throws IOException;
}
