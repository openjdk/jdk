/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8320145
 * @summary Compiler should accept final variable in Record Pattern
 * @compile T8320145.java
 */
public class T8320145 {
    record ARecord(String aComponent) {}
    record BRecord(ARecord aComponent) {}
    record CRecord(ARecord aComponent1, ARecord aComponent2) {}

    public String match(Object o) {
        return switch(o) {
            case ARecord(final String s) -> s;
            case BRecord(ARecord(final String s)) -> s;
            case CRecord(ARecord(String s), ARecord(final String s2)) -> s;
            default -> "No match";
        };
    }
}
