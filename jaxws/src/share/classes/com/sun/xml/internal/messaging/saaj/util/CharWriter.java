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
/*
 * $Id: CharWriter.java,v 1.6 2006/01/27 12:49:51 vj135062 Exp $
 * $Revision: 1.6 $
 * $Date: 2006/01/27 12:49:51 $
 */


package com.sun.xml.internal.messaging.saaj.util;

import java.io.CharArrayWriter;

// This class just gives access to the underlying buffer without copying.

public class CharWriter extends CharArrayWriter {
    public CharWriter () {
        super();
    }

    public CharWriter(int size) {
        super(size);
    }

    public char[] getChars() {
        return buf;
    }

    public int getCount() {
        return count;
    }
}
