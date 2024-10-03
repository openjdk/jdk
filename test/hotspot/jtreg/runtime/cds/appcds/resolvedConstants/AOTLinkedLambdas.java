/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @summary AOT resolution of lambda expressions
 * @bug 8340836
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes/
 * @build AOTLinkedLambdas
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 AOTLinkedLambdasApp StaticClass Ticket TestA
 * @run driver AOTLinkedLambdas
 */

import java.util.function.Supplier;
import static java.util.stream.Collectors.*;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;

public class AOTLinkedLambdas {
    static final String classList = "AOTLinkedLambdas.classlist";
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = AOTLinkedLambdasApp.class.getName();

    public static void main(String[] args) throws Exception {
        CDSTestUtils.dumpClassList(classList, "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                output.shouldContain("Hello AOTLinkedLambdasApp");
            });

        CDSOptions opts = (new CDSOptions())
            .addPrefix("-XX:ExtraSharedClassListFile=" + classList,
                       "-XX:+AOTClassLinking",
                       "-Xlog:cds+resolve=trace",
                       "-Xlog:cds+class=debug",
                       "-cp", appJar);

        CDSTestUtils.createArchiveAndCheck(opts);

        CDSOptions runOpts = (new CDSOptions())
            .setUseVersion(false)
            .addPrefix("-Xlog:cds",
                       "-esa",         // see JDK-8340836
                       "-cp", appJar)
            .addSuffix(mainClass);

        CDSTestUtils.run(runOpts)
            .assertNormalExit("Hello AOTLinkedLambdasApp",
                              "hello, world");
    }
}

class Ticket {
    static {
        System.out.println("Ticket.<clinit>");
    }
    static int n = 0;
    static int next() {
        return ++n;
    }
    static int current() {
        return n;
    }
}

class AOTLinkedLambdasApp {
    static {
        System.out.println("AOTLinkedLambdasApp.<clinit>");
    }
    public static void main(String args[]) {
        System.out.println("Hello AOTLinkedLambdasApp");

        var words = java.util.List.of("hello", "fuzzy", "world");
        System.out.println(words.stream().filter(w->!w.contains("u")).collect(joining(", ")));
        // => hello, world

        TestA.doit();
    }
}

class TestA {
    static {
        System.out.println("TestA.<clinit>");
    }
    static int ticket = Ticket.next(); // Should be 1
    static void doit() {
        System.out.println(Ticket.current());

        // Using a static method reference
        Supplier<Long> staticRef = StaticClass::getTicket;
        System.out.println(Ticket.current());
        if (Ticket.current() != 1) {
            throw new RuntimeException("Expected 1 but got " + Ticket.current());
        }
        long t2 = staticRef.get(); // StaticClass.<clinit> is called only at this point.
        System.out.println(ticket);
        System.out.println(t2);
        if (ticket != 1 && t2 != 2) {
            throw new RuntimeException("Expected 1, 2 but got " + ticket + ", " + t2);
        }
    }
}


class StaticClass {
    static {
        // <clinit> shouldn't be called until the "staticRef.get()" expression is
        // is evaluated in TestA.doit().
        System.out.println("StaticClass.<clinit>");
    }
    static int ticket = Ticket.next(); // Should be 2

    static long getTicket() {
        return ticket; // When this function is caled, ticket must have been initialized.
    }
}
