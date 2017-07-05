/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.processor.model.jaxb;

/**
 * @author Kohsuke Kawaguchi
 */
class Util {
    /**
     * Replaces the marcros in the first string by the actual given arguments.
     */
    static String replace( String macro, String... args ) {
        int len = macro.length();
        StringBuilder buf = new StringBuilder(len);
        for( int i=0; i<len; i++ ) {
            char ch = macro.charAt(i);
            if(ch=='=' && i+2<len) {
                char tail = macro.charAt(i+1);
                char ch2 = macro.charAt(i+2);
                if('0'<=ch2 && ch2<='9' && tail==':') {
                    buf.append(args[ch2-'0']);
                    i+=2;
                    continue;
                }
            }
            buf.append(ch);
        }
        return buf.toString();
    }

    /**
     * Creates a macro tempate so that it can be later used with {@link #replace(String, String[])}.
     */
    static String createMacroTemplate( String s ) {
        return s;
    }

    static final String MAGIC = "=:";

    static final String MAGIC0 = MAGIC+"0";
    static final String MAGIC1 = MAGIC+"1";
    static final String MAGIC2 = MAGIC+"2";
}
