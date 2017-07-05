/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.foo;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Requires;
import java.util.StringJoiner;

import jdk.test.foo.internal.Message;

public class Foo {
    public static void main(String[] args) {
        System.out.println("message:" + Message.get());

        ModuleDescriptor md = Foo.class.getModule().getDescriptor();
        System.out.println("nameAndVersion:" + md.toNameAndVersion());
        md.mainClass().ifPresent(mc -> System.out.println("mainClass:" + mc));

        StringJoiner sj = new StringJoiner(",");
        md.requires().stream().map(Requires::name).sorted().forEach(sj::add);
        System.out.println("requires:" + sj.toString());

        sj = new StringJoiner(",");
        md.exports().stream().map(Exports::source).sorted().forEach(sj::add);
        if (!sj.toString().equals(""))
            System.out.println("exports:" + sj.toString());

        sj = new StringJoiner(",");
        md.uses().stream().sorted().forEach(sj::add);
        if (!sj.toString().equals(""))
            System.out.println("uses:" + sj.toString());

        sj = new StringJoiner(",");
        md.provides().keySet().stream().sorted().forEach(sj::add);
        if (!sj.toString().equals(""))
            System.out.println("provides:" + sj.toString());

        sj = new StringJoiner(",");
        md.conceals().forEach(sj::add);
        if (!sj.toString().equals(""))
            System.out.println("conceals:" + sj.toString());
    }
}
