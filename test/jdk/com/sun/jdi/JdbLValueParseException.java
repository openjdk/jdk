/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8248165
 *
 * @library /test/lib
 * @compile -g JdbLValueParseException.java
 * @run main JdbLValueParseException
 */

import lib.jdb.JdbCommand;
import lib.jdb.JdbTest;
import jdk.test.lib.process.OutputAnalyzer;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;


class JdbLValueParseExceptionTarg {
    public static void main(String[] args) {
        JdbLValueParseExceptionTarg v = new JdbLValueParseExceptionTarg();
		System.out.println("v = " + v); // @1 breakpoint
    }
}

public class JdbLValueParseException extends JdbTest {
    public static void main(String argv[]) {
        new JdbLValueParseException().run();
    }

    public JdbLValueParseException() {
        super(JdbLValueParseExceptionTarg.class.getName());
    }

    @Override
    protected void runCases() {
        setBreakpointsFromTestSource("JdbLValueParseException.java", 1);

        // run to breakpoint #1
        jdb.command(JdbCommand.run());

        // throws InvalidTypeException
        List<String> reply = jdb.command(JdbCommand.set("v", "1"));

        new OutputAnalyzer(reply.stream().collect(Collectors.joining(lineSeparator)))
			    // ensure there is a space before "com.sun.jdi.InvalidTypeException"
                .shouldContain(" com.sun.jdi.InvalidTypeException");

        jdb.contToExit(1);
    }

}
