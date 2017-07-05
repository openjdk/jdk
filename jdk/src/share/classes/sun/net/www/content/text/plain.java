/*
 * Copyright 1994-1996 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * Plain text file handler.
 * @author  Steven B. Byrne
 */
package sun.net.www.content.text;
import java.net.*;
import java.io.InputStream;
import java.io.IOException;

public class plain extends ContentHandler {
    /**
     * Returns a PlainTextInputStream object from which data
     * can be read.
     */
    public Object getContent(URLConnection uc) {
        try {
            InputStream is = uc.getInputStream();
            return new PlainTextInputStream(uc.getInputStream());
        } catch (IOException e) {
            return "Error reading document:\n" + e.toString();
        }
    }
}
