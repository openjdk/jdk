/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.http.HttpRequest;
import java.net.URI;
import jdk.incubator.http.HttpClient;
import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @test
 * @bug 8170064
 * @summary  HttpRequest API documentation says:" Unless otherwise stated,
 * {@code null} parameter values will cause methods
 * of this class to throw {@code NullPointerException}".
 */
public class HttpRequestBuilderTest {

    static final URI TEST_URI = URI.create("http://www.foo.com/");


    public static void main(String[] args) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder();
        builder = test1("uri", builder, builder::uri, (URI)null,
                        NullPointerException.class);
        builder = test2("header", builder, builder::header, (String) null, "bar",
                        NullPointerException.class);
        builder = test2("header", builder, builder::header, "foo", (String) null,
                        NullPointerException.class);
        builder = test2("header", builder, builder::header, (String)null,
                        (String) null, NullPointerException.class);
        builder = test1("headers", builder, builder::headers, (String[]) null,
                        NullPointerException.class);
        builder = test1("headers", builder, builder::headers,
                        (String[]) new String[] {null, "bar"},
                        NullPointerException.class);
        builder = test1("headers", builder, builder::headers,
                        (String[]) new String[] {"foo", null},
                        NullPointerException.class);
        builder = test1("headers", builder, builder::headers,
                        (String[]) new String[] {null, null},
                        NullPointerException.class);
        builder = test1("headers", builder, builder::headers,
                       (String[]) new String[] {"foo", "bar", null},
                       NullPointerException.class,
                       IllegalArgumentException.class);
        builder = test1("headers", builder, builder::headers,
                       (String[]) new String[] {"foo", "bar", null, null},
                       NullPointerException.class);
        builder = test1("headers", builder, builder::headers,
                       (String[]) new String[] {"foo", "bar", "baz", null},
                       NullPointerException.class);
        builder = test1("headers", builder, builder::headers,
                       (String[]) new String[] {"foo", "bar", null, "baz"},
                       NullPointerException.class);
        builder = test1("headers", builder, builder::headers,
                       (String[]) new String[] {"foo", "bar", "baz"},
                       IllegalArgumentException.class);
        builder = test1("headers", builder, builder::headers,
                       (String[]) new String[] {"foo"},
                       IllegalArgumentException.class);
        builder = test1("DELETE", builder, builder::DELETE,
                        (HttpRequest.BodyProcessor)null, null);
        builder = test1("POST", builder, builder::POST,
                        (HttpRequest.BodyProcessor)null, null);
        builder = test1("PUT", builder, builder::PUT,
                        (HttpRequest.BodyProcessor)null, null);
        builder = test2("method", builder, builder::method, "GET",
                        (HttpRequest.BodyProcessor) null, null);
        builder = test2("setHeader", builder, builder::setHeader,
                        (String) null, "bar",
                        NullPointerException.class);
        builder = test2("setHeader", builder, builder::setHeader,
                        "foo", (String) null,
                        NullPointerException.class);
        builder = test2("setHeader", builder, builder::setHeader,
                        (String)null, (String) null,
                        NullPointerException.class);
        builder = test1("timeout", builder, builder::timeout,
                        (Duration)null, NullPointerException.class);
        builder = test1("version", builder, builder::version,
                        (HttpClient.Version)null,
                        NullPointerException.class);
        builder = test2("method", builder, builder::method, null,
                       HttpRequest.BodyProcessor.fromString("foo"),
                       NullPointerException.class);
// see JDK-8170093
//
//        builder = test2("method", builder, builder::method, "foo",
//                       HttpRequest.BodyProcessor.fromString("foo"),
//                       IllegalArgumentException.class);
//
//        builder.build();

    }

    private static boolean shouldFail(Class<? extends Exception> ...exceptions) {
        return exceptions != null && exceptions.length > 0;
    }

    private static String expectedNames(Class<? extends Exception> ...exceptions) {
        return Stream.of(exceptions).map(Class::getSimpleName)
                .collect(Collectors.joining("|"));
    }
    private static boolean isExpected(Exception x,
                                     Class<? extends Exception> ...expected) {
        return expected != null && Stream.of(expected)
                .filter(c -> c.isInstance(x))
                .findAny().isPresent();
    }

    public static <R,P> R test1(String name, R receiver, Function<P, R> m, P arg,
                               Class<? extends Exception> ...ex) {
        try {
            R result =  m.apply(arg);
            if (!shouldFail(ex)) {
                System.out.println("success: " + name + "(" + arg + ")");
                return result;
            } else {
                throw new AssertionError("Expected " + expectedNames(ex)
                    + " not raised for " + name + "(" + arg + ")");
            }
        } catch (Exception x) {
            if (!isExpected(x, ex)) {
                throw x;
            } else {
                System.out.println("success: " + name + "(" + arg + ")" +
                        " - Got expected exception: " + x);
                return receiver;
            }
        }
    }


    public static <R,P1, P2> R test2(String name, R receiver, BiFunction<P1, P2, R> m,
                               P1 arg1, P2 arg2,
                               Class<? extends Exception> ...ex) {
        try {
            R result =  m.apply(arg1, arg2);
            if (!shouldFail(ex)) {
                System.out.println("success: " + name + "(" + arg1 + ", "
                                   + arg2 + ")");
                return result;
            } else {
                throw new AssertionError("Expected " + expectedNames(ex)
                    + " not raised for "
                    + name + "(" + arg1 +", " + arg2 + ")");
            }
        } catch (Exception x) {
            if (!isExpected(x, ex)) {
                throw x;
            } else {
                System.out.println("success: " + name + "(" + arg1 + ", "
                        + arg2 + ") - Got expected exception: " + x);
                return receiver;
            }
        }
    }
}
