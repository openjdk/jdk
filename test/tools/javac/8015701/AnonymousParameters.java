/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8015701
 * @summary javac should generate method parameters correctly.
 * @compile -parameters AnonymousParameters.java
 * @run main AnonymousParameters
 */
import java.lang.Class;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.concurrent.Callable;

public class AnonymousParameters {

    String[] names = {
        "this$0",
        "val$message"
    };

    public static void main(String... args) throws Exception {
        new AnonymousParameters().run();
    }

    void run() throws Exception {
        Class<?> cls = new ParameterNames().makeInner("hello").getClass();
        Constructor<?> ctor = cls.getDeclaredConstructors()[0];
        Parameter[] params = ctor.getParameters();

        if(params.length == 2) {
            for(int i = 0; i < 2; i++) {
                System.err.println("Testing parameter " + params[i].getName());
                if(!params[i].getName().equals(names[i]))
                    error("Expected parameter name " + names[i] +
                          " got " + params[i].getName());
            }
        } else
            error("Expected 2 parameters");

        if(0 != errors)
            throw new Exception("MethodParameters test failed with " +
                                errors + " errors");
    }

    void error(String msg) {
        System.err.println("Error: " + msg);
        errors++;
    }

    int errors;
}

class ParameterNames {

    public Callable<String> makeInner(final String message) {
        return new Callable<String>()  {
            public String call() throws Exception {
                return message;
            }
        };
    }

    public static void main(String... args) throws Exception {
        ParameterNames test = new ParameterNames();
        System.out.println(test.makeInner("Hello").call());
    }
}
