/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * Class with miscellaneous structures to exercise printing.
 */

import java.util.Collection;

public final class  Misc<T> implements Marker2, Marker3 {
    private static final long longConstant = Long.MAX_VALUE;

    private static final String asciispecials = "\t\n\u0007";

    public void covar(Collection<? extends T> s) {return;}

    public void contravar(Collection<? super T> s) {return;}

    public <S> S varUse(int i) {return null;}

    Object o = (new Object() {});       // verify fix for 5019108
}

interface Marker1 {}

interface Marker2 extends Marker1 {}

interface Marker3 {}
