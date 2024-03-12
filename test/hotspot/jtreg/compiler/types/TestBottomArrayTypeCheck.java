/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * @bug 8308583
 * @summary SIGSEGV in GraphKit::gen_checkcast
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:CompileCommand=compileonly,TestBottomArrayTypeCheck::test TestBottomArrayTypeCheck
 */

public class TestBottomArrayTypeCheck {

    interface WordBase {
    }

    interface RelocatedPointer {
    }

    static final class Word implements WordBase {
    }

    static Object[] staticObjectFields;

    static byte[] staticPrimitiveFields;


    interface SnippetReflection {
        Object forObject(Object o);
    }

    static class BaseSnippetReflection implements SnippetReflection {
        public Object forObject(Object o) {
            return null;
        }

    }
    static class SubSnippetReflection extends BaseSnippetReflection {
        public Object forObject(Object object) {
            if (object instanceof WordBase word && !(object instanceof RelocatedPointer)) {
                return word;
            }
            return super.forObject(object);
        }
    }

    public static void main(String[] args) {
        t1();
        for (int i = 0; i < 10; i++) {
            t2();
        }
    }

    static void t1() {
        Word w = new Word();
        SnippetReflection base = new BaseSnippetReflection();
        SnippetReflection sub = new SubSnippetReflection();
        for (int i = 0; i < 10000; i++) {
            invoke(base, w);
            invoke(sub, w);
        }
    }

    static void t2() {
        SnippetReflection base = new BaseSnippetReflection();
        SnippetReflection sub = new SubSnippetReflection();
        for (int i = 0; i < 10000; i++) {
            test(base, i % 2 == 0);
            test(sub, i % 2 == 0);
            test(sub, i % 2 == 0);
        }
    }

    static Object test(SnippetReflection s, boolean b) {
        return s.forObject(b ? staticObjectFields : staticPrimitiveFields);
    }


    static Object invoke(SnippetReflection s, Object o) {
        return s.forObject(o);
    }
}
