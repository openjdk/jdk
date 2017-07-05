/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package build.tools.charsetmapping;

import java.io.*;
import java.util.Scanner;

public class Main {

    public static void main(String args[]) throws Exception {
        if (args.length < 3 ) {
            System.out.println("Usage: java -jar charsetmapping.jar src dst mType [copyrightSrc]");
            System.exit(1);
        }
        if ("sbcs".equals(args[2]) || "extsbcs".equals(args[2])) {
            SBCS.genClass(args);
        } else if ("dbcs".equals(args[2])) {
            DBCS.genClass(args);
        } else if ("euctw".equals(args[2])) {
            EUC_TW.genClass(args);
        } else if ("sjis0213".equals(args[2])) {
            JIS0213.genClass(args);
        } else if ("hkscs".equals(args[2])) {
            HKSCS.genClass(args);
        }
    }
}
