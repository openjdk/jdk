/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8262891
 * @summary Verify errors related to pattern switches.
 * @compile/fail/ref=SwitchErrors.out --enable-preview -source ${jdk.version} -XDrawDiagnostics SwitchErrors.java
 */
public class SwitchErrors {
    void incompatibleSelectorObjectString(Object o) {
        switch (o) {
            case "A": break;
            case CharSequence cs: break;
        }
    }
    void incompatibleSelectorObjectInteger(Object o) {
        switch (o) {
            case 1: break;
            case CharSequence cs: break;
        }
    }
    void incompatibleSelectorIntegerString(Integer i) {
        switch (i) {
            case "A": break;
            case CharSequence cs: break;
        }
    }
    void incompatibleSelectorPrimitive(int i) {
        switch (i) {
            case null: break;
            case "A": break;
            case CharSequence cs: break;
        }
    }
    void totalAndDefault1(Object o) {
        switch (o) {
            case Object obj: break;
            default: break;
        }
    }
    void totalAndDefault2(Object o) {
        switch (o) {
            case Object obj: break;
            case null, default: break;
        }
    }
    void totalAndDefault3(Object o) {
        switch (o) {
            default: break;
            case Object obj: break;
        }
    }
    void duplicatedTotal(Object o) {
        switch (o) {
            case Object obj: break;
            case Object obj: break;
        }
    }
    void duplicatedDefault1(Object o) {
        switch (o) {
            case null, default: break;
            default: break;
        }
    }
    void duplicatedDefault2(Object o) {
        switch (o) {
            case default: break;
            default: break;
        }
    }
    void duplicatedDefault3(Object o) {
        switch (o) {
            case default, default: break;
        }
    }
    void duplicatedNullCase1(Object o) {
        switch (o) {
            case null: break;
            case null: break;
        }
    }
    void duplicatedNullCase2(Object o) {
        switch (o) {
            case null, null: break;
        }
    }
    void duplicatedTypePatterns1(Object o) {
        switch (o) {
            case String s, Integer i: break;
        }
    }
    void duplicatedTypePatterns2(Object o) {
        switch (o) {
            case String s:
            case Integer i: break;
        }
    }
    void duplicatedTypePatterns3(Object o) {
        switch (o) {
            case String s:
                System.err.println(1);
            case Integer i: break;
        }
    }
    void flowIntoTypePatterns(Object o) {
        switch (o) {
            case null:
                System.err.println(1);
            case Integer i: break;
        }
    }
}
