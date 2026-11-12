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
 * WARNING: This test is split from `IdleIntervalPropertyTest.java` and placed
 *          in a dedicated file to increase parallelism.
 */

/*
 * @test
 * @bug 8295785
 * @summary Tests the `sun.net.httpserver.idleInterval` system property
 *          configured to 0, and expects it to be overridden by the default,
 *          i.e., 30. The background task evicting idle connections is scheduled
 *          to run every `sun.net.httpserver.clockTick` milliseconds, which
 *          defaults to 10000. We need to wait for at least `30+clockTick`
 *          seconds, that is, 40.
 *
 * @comment This is a timing-sensitive test, avoid exotic configurations.
 * @requires !vm.debug & vm.compMode == "Xmixed"
 *
 * @library /test/lib
 * @build IdleIntervalPropertyTest
 *
 * @run junit/othervm
 *      -Dsun.net.httpserver.idleInterval=0
 *      -Dtest.evictionWaitDuration=40
 *      IdleIntervalPropertyTest
 */
