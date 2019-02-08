/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package gc.stress.gcbasher;

import java.io.IOException;

/*
 * @test TestGCBasherWithCMS
 * @key gc stress
 * @library /
 * @requires vm.gc.ConcMarkSweep
 * @requires vm.flavor == "server" & !vm.emulatedClient & !vm.graal.enabled
 * @summary Stress the CMS GC by trying to make old objects more likely to be garbage than young objects.
 * @run main/othervm/timeout=200 -Xlog:gc*=info -Xmx256m -server -XX:+UseConcMarkSweepGC gc.stress.gcbasher.TestGCBasherWithCMS 120000
 */
public class TestGCBasherWithCMS {
    public static void main(String[] args) throws IOException {
        TestGCBasher.main(args);
    }
}
