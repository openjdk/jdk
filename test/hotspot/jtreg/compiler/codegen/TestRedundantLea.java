/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8020282
 * @summary Test that we do not generate redundant leas on x86
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,compiler.codegen.TestRedundantLea::test*
 *      --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
 *      compiler.codegen.TestRedundantLea
 */

package compiler.codegen;

import java.util.concurrent.atomic.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.tools.javac.util.*;

// TODO: convert to IR test
public class TestRedundantLea {
    public static void main(String[] args) {
        Names names = new Names(new Context());
        Name n1 = names.fromString("one");
        Name n2 = names.fromString("two");
        Pattern pat = Pattern.compile("27");
        Matcher m = pat.matcher(" 274  leaPCompressedOopOffset  === _ 275 277  [[ 2246 165 294 ]] #16/0x0000000000000010byte[int:>=0]");
        TestRedundantLea t = new TestRedundantLea();
        for (int i = 0; i < 100000; i++) {
            //t.test1();
            //t.test2("test");
            t.test3(n1, n2);
            //t.test4(m);
            //t.test5(CURRENT, OTHER);
        }
    }

    private static final Object CURRENT = new Object();
    private static final Object OTHER = new Object();

    private final AtomicReference<Object> obj = new AtomicReference<Object>();

    private final Foo foo = new Foo("bar");

    // leaPCompressedOopOffset before getAndSet intrinsic
    public void test1() {
        obj.getAndSet(CURRENT);
    }

    // leaPCompressedOopOffset in String.equals
    public boolean test2(String baz) {
        return foo.bar(baz);
    }

    // leaPCompressedOopOffset before string_inflate (if all VM intrinsics are disabled; otherwise also string_equals and arrays_hashcode)
    // Does not generate leaPCompressedOopOffsets with -XX:-OptimizeStringConcat (if all VM intrinsics are disabled)
    public Name test3(Name n1, Name n2) {
        return n1.append(n2);
    }

    // leaPCommpressedOopOffset before arrayof_jint_fill
    // needs -XX:+UseAVX3
    public boolean test4(Matcher m) {
        return m.find();
    }

    // leaPCompressedOopOffset before storeN
    // needs -XX:CompileCommand=compileonly,compiler/codegen/Bars.\<init\> -XX:+UseParallelGC
    public Bars test5(Object o1, Object o2) {
        return new Bars(o1, o2);
    }
}

class Foo {
    private String bar;

    public Foo(String bar) {
        this.bar = bar;
    }

    public boolean bar(String baz) {
        return this.bar.equals(baz);
    }
}

class Bars {
    private final int SOME_SIZE = 42;
    private final int SOME_IDX = 33;

    Object o1;
    Object o2;

    private final Bar bar1;
    private final Bar bar2;
    private final Bar[] bars = new Bar[SOME_SIZE];

    public Bars(Object o1, Object o2) {
        this.o1 = o1;
        this.o2 = o2;
        this.bar1 = new Bar(o1, o1);
        this.bar2 = new Bar(o2, o2);

        for (int i = 0; i < this.bars.length; i++) {
            this.bars[i] = i % 2 == 0 ? new Bar(o1, o2) : new Bar(o2, o1);
        }
        this.bars[SOME_IDX] = new Bar(o2, o2);
    }
}

class Bar{
    Object o1;
    Object o2;

    public Bar(Object o1, Object o2) {
        this.o1 = o1;
        this.o2 = o2;
    }
}
