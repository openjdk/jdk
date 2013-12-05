/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package test;

class Target11 extends CurPackagePrivateExt11 {
    void run() {
        new Runnable() {
            public void run() {
                Target11.super.refTotestCurPackagePrivateBase11();
                Target11.super.refTotestCurPackagePrivateBase11 =
                        Target11.super.refTotestCurPackagePrivateBase11 + 1;
                Target11.super.refTotestCurPackagePrivateExt11();
                Target11.super.refTotestCurPackagePrivateExt11 =
                        Target11.super.refTotestCurPackagePrivateExt11 + 1;
                Target11.super.toString();
                refTotestCurPackagePrivateBase11();
                refTotestCurPackagePrivateBase11 =
                        refTotestCurPackagePrivateBase11 + 1;
                refTotestTarget11();
                refTotestTarget11 = refTotestTarget11 + 1;
                Target11.this.refTotestCurPackagePrivateBase11();
                Target11.this.refTotestCurPackagePrivateBase11 =
                        Target11.this.refTotestCurPackagePrivateBase11 + 1;
                Target11.this.refTotestTarget11();
                Target11.this.refTotestTarget11 =
                        Target11.this.refTotestTarget11 + 1;
            }
        }.run();
        super.refTotestCurPackagePrivateBase11();
        super.refTotestCurPackagePrivateBase11 =
                super.refTotestCurPackagePrivateBase11 + 1;
        super.refTotestCurPackagePrivateExt11();
        super.refTotestCurPackagePrivateExt11 =
                super.refTotestCurPackagePrivateExt11 + 1;
        super.toString();

        Target11.super.refTotestCurPackagePrivateBase11();
        Target11.super.refTotestCurPackagePrivateBase11 =
                Target11.super.refTotestCurPackagePrivateBase11 + 1;
        Target11.super.refTotestCurPackagePrivateExt11();
        Target11.super.refTotestCurPackagePrivateExt11 =
                Target11.super.refTotestCurPackagePrivateExt11 + 1;

        refTotestCurPackagePrivateBase11();
        refTotestCurPackagePrivateBase11 = refTotestCurPackagePrivateBase11 + 1;
        refTotestTarget11 = refTotestTarget11 + 1;
    }
}

class CurPackagePrivateBase11 extends base.Base {
    protected void refTotestCurPackagePrivateBase11() {}
    protected int refTotestCurPackagePrivateBase11;
}

class CurPackagePrivateExt11 extends CurPackagePrivateBase11 { }
