/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8003881
 * @modules jdk.compiler
 * @run main/othervm -Djava.security.manager=allow LambdaAccessControlTest
 * @summary tests Lambda expression with a security manager at top level
 */

public class LambdaAccessControlTest {
    public static void main(String... args) {
        System.setSecurityManager(new SecurityManager());
        JJ<Integer> iii = (new CC())::impl;
        System.out.printf(">>> %s\n", iii.foo(44));
        iii = DD::impl;
        System.out.printf(">>> %s\n", iii.foo(44));
        return;
    }
}
/*
 * support classes for the test
 */
interface II<T> {  Object foo(T x); }
interface JJ<R extends Number> extends II<R> { }
class CC {  String impl(int i) { return "impl:"+i; }}
class DD {  static String impl(int i) { return "impl:"+i; }}
