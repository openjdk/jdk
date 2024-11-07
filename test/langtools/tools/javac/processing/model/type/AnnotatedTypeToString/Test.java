/*
 * Copyright (c) 2022, Google LLC. All rights reserved.
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

package p;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface A {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface B {}

public class Test {
    static class StaticNested {
        static class InnerMostStaticNested {}
    }

    class Inner {
        class InnerMost {}
    }

    @ExpectedToString("p.Test.@p.A @p.B StaticNested")
    @A @B StaticNested i;

    @ExpectedToString("p.Test.StaticNested.@p.A InnerMostStaticNested")
    StaticNested.@A InnerMostStaticNested j;

    @ExpectedToString("p.Test.@p.A Inner")
    @A Inner k;

    @ExpectedToString("p.Test.Inner.@p.A InnerMost")
    Inner.@A InnerMost l;

    @ExpectedToString("p.Test.@p.A Inner.InnerMost")
    @A Inner.InnerMost m;
}
