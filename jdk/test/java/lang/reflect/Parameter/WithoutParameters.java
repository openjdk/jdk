/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @summary javac should generate method parameters correctly.
 */

import java.lang.*;
import java.lang.reflect.*;
import java.util.List;

public class WithoutParameters {

    private static final Class<?>[] qux_types = {
        int.class,
        Foo.class,
        List.class,
        List.class,
        List.class,
        String[].class
    };

    public static void main(String argv[]) throws Exception {
        int error = 0;
        Method[] methods = Foo.class.getMethods();
        for(Method m : methods) {
            System.err.println("Inspecting method " + m);
            Parameter[] parameters = m.getParameters();
            if(parameters == null)
                throw new Exception("getParameters should never be null");
            for(int i = 0; i < parameters.length; i++) {
                    Parameter p = parameters[i];
                    if(!p.getDeclaringExecutable().equals(m)) {
                        System.err.println(p + ".getDeclaringExecutable != " + m);
                        error++;
                    }
                    if(null == p.getType()) {
                        System.err.println(p + ".getType() == null");
                        error++;
                    }
                    if(null == p.getParameterizedType()) {
                        System.err.println(p + ".getParameterizedType == null");
                        error++;
                    }
            }
            if(m.getName().equals("qux")) {
                if(6 != parameters.length) {
                    System.err.println("Wrong number of parameters for qux");
                    error++;
                }
                for(int i = 0; i < parameters.length; i++) {
                    Parameter p = parameters[i];
                    // The getType family work with or without
                    // parameter attributes compiled in.
                    if(!parameters[i].getType().equals(qux_types[i])) {
                        System.err.println("Wrong parameter type for " + parameters[0] + ": expected " + qux_types[i] + ", but got " + parameters[i].getType());
                        error++;
                    }
                }
                if(!parameters[0].getParameterizedType().equals(int.class)) {
                    System.err.println("getParameterizedType for quux is wrong");
                    error++;
                }
                if(!parameters[1].getParameterizedType().equals(Foo.class)) {
                    System.err.println("getParameterizedType for quux is wrong");
                    error++;
                }
                if(!(parameters[2].getParameterizedType() instanceof
                     ParameterizedType)) {
                    System.err.println("getParameterizedType for l is wrong");
                    error++;
                } else {
                    ParameterizedType pt =
                        (ParameterizedType) parameters[2].getParameterizedType();
                    if(!pt.getRawType().equals(List.class)) {
                        System.err.println("Raw type for l is wrong");
                        error++;
                    }
                    if(1 != pt.getActualTypeArguments().length) {
                        System.err.println("Number of type parameters for l is wrong");
                        error++;
                    }
                    if(!(pt.getActualTypeArguments()[0] instanceof WildcardType)) {
                        System.err.println("Type parameter for l is wrong");
                        error++;
                    }
                }
                if(!(parameters[3].getParameterizedType() instanceof
                     ParameterizedType)) {
                    System.err.println("getParameterizedType for l2 is wrong");
                    error++;
                } else {
                    ParameterizedType pt =
                        (ParameterizedType) parameters[3].getParameterizedType();
                    if(!pt.getRawType().equals(List.class)) {
                        System.err.println("Raw type for l2 is wrong");
                        error++;
                    }
                    if(1 != pt.getActualTypeArguments().length) {
                        System.err.println("Number of type parameters for l2 is wrong");
                        error++;
                    }
                    if(!(pt.getActualTypeArguments()[0].equals(Foo.class))) {
                        System.err.println("Type parameter for l2 is wrong");
                        error++;
                    }
                }
                if(!(parameters[4].getParameterizedType() instanceof
                     ParameterizedType)) {
                    System.err.println("getParameterizedType for l3 is wrong");
                    error++;
                } else {
                    ParameterizedType pt =
                        (ParameterizedType) parameters[4].getParameterizedType();
                    if(!pt.getRawType().equals(List.class)) {
                        System.err.println("Raw type for l3 is wrong");
                        error++;
                    }
                    if(1 != pt.getActualTypeArguments().length) {
                        System.err.println("Number of type parameters for l3 is wrong");
                        error++;
                    }
                    if(!(pt.getActualTypeArguments()[0] instanceof WildcardType)) {
                        System.err.println("Type parameter for l3 is wrong");
                        error++;
                    } else {
                        WildcardType wt = (WildcardType)
                            pt.getActualTypeArguments()[0];
                        if(!wt.getUpperBounds()[0].equals(Foo.class)) {
                            System.err.println("Upper bounds on type parameter fol l3 is wrong");
                            error++;
                        }
                    }
                }
                if(!parameters[5].isVarArgs()) {
                    System.err.println("isVarArg for rest is wrong");
                    error++;
                }
                if(!(parameters[5].getParameterizedType().equals(String[].class))) {
                    System.err.println("getParameterizedType for rest is wrong");
                    error++;
                }

            }
        }
        if(0 != error)
            throw new Exception("Failed " + error + " tests");
    }

    public class Foo {
        int thing;
        public void qux(int quux, Foo quuux,
                        List<?> l, List<Foo> l2,
                        List<? extends Foo> l3,
                        String... rest) {}
        public class Inner {
            int thang;
            public Inner(int theng) {
                thang = theng + thing;
            }
        }
    }

}
