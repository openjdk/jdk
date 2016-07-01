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

/**
 * JDK-8073653: Secondary heredoc eating wrong lines.
 *
 * @test
 * @run
 * @option -scripting
 */


print(<<EOD1); print(<<EOD2.toUpperCase());  var a = <<EOD3, b = <<EOD4.toLowerCase(), c = [<<EOD5, <<EOD6];
This is line 1.
This is line 2.
EOD1
This is line 3.
This is line 4.
EOD2
This is line 5.
This is line 6.
EOD3
This is line 7.
This is line 8.
EOD4
This is line 9.
This is line 10.
EOD5
This is line 11.
This is line 12.
EOD6

print(a);
print(b);
for (var i in c) {
    print(c[i]);
}


