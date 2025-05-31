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

/**
 * @test
 * @bug 8353835
 * @summary Test mutating final field in m1 from m1, m2 and m3. The package with the final
 *     field is open to m2 and not open to m3.
 * @build m1/* m2/* m3/*
 * @run junit/othervm --illegal-final-field-mutation=allow -DallowedToMutate=m1,m2 m1/p1.TestMain
 * @run junit/othervm --illegal-final-field-mutation=deny --enable-final-field-mutation=m1 -DallowedToMutate=m1 m1/p1.TestMain
 * @run junit/othervm --illegal-final-field-mutation=deny --enable-final-field-mutation=m2 -DallowedToMutate=m2 m1/p1.TestMain
 * @run junit/othervm --illegal-final-field-mutation=deny --enable-final-field-mutation=m1,m2,m3 -DallowedToMutate=m1,m2 m1/p1.TestMain
 * @run junit/othervm --illegal-final-field-mutation=deny --enable-final-field-mutation=m1,m2,m3 --add-opens m1/p1=m3 -DallowedToMutate=m1,m2,m3 m1/p1.TestMain
 */
