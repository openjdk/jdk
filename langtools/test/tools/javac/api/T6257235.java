/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug     6257235
 * @summary setOption() and setExtendedOption() of JavacTool throws NullPointerException for undefined options
 * @author  Peter von der Ah\u00e9
 */

import java.util.Arrays;
import javax.tools.*;

public class T6257235 {
    public static void main(String... args) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        try {
            javac.getTask(null, null, null, Arrays.asList("seetharam", "."), null, null);
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) { }
    }
}
