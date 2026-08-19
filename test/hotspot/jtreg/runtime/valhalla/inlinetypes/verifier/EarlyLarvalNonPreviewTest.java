/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @compile EarlyLarvalNonPreviewApp.jasm
 * @run main EarlyLarvalNonPreviewTest
 */

public class EarlyLarvalNonPreviewTest {
    public static void main(String[] args) {
        try {
            var value = new EarlyLarvalNonPreviewApp(-1, -2);
            throw new RuntimeException("Expected ClassFormatError");
        } catch (ClassFormatError c) {
            if (!c.getMessage().equals("StackMapTable format error: reserved frame type")) {
                throw new RuntimeException("Unexpected ClassFormatError " + c.getMessage());
            }
            System.out.println("Test passed");
        }
    }
}
