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

@TraceResolve
class PrimitiveBinopOverload {

    @Candidate(applicable=Phase.BASIC, mostSpecific=true)
    int _plus(int x, int y) { return -1; }
    @Candidate(applicable=Phase.BASIC)
    long _plus(long x, long y) { return -1; }
    @Candidate(applicable=Phase.BASIC)
    float _plus(float x, float y) { return -1; }
    @Candidate(applicable=Phase.BASIC)
    double _plus(double x, double y) { return -1; }
    //not a candidate
    Object _plus(Object x, Object y) { return -1; }

    @Candidate(applicable= { Phase.BASIC, Phase.BOX }, mostSpecific=true)
    int _minus(int x, int y) { return -1; }
    @Candidate(applicable= { Phase.BASIC, Phase.BOX })
    long _minus(long x, long y) { return -1; }
    @Candidate(applicable= { Phase.BASIC, Phase.BOX })
    float _minus(float x, float y) { return -1; }
    @Candidate(applicable= { Phase.BASIC, Phase.BOX })
    double _minus(double x, double y) { return -1; }

    @Candidate(applicable= { Phase.BASIC, Phase.BOX }, mostSpecific=true)
    int _mul(int x, int y) { return -1; }
    @Candidate(applicable= { Phase.BASIC, Phase.BOX })
    long _mul(long x, long y) { return -1; }
    @Candidate(applicable= { Phase.BASIC, Phase.BOX })
    float _mul(float x, float y) { return -1; }
    @Candidate(applicable= { Phase.BASIC, Phase.BOX })
    double _mul(double x, double y) { return -1; }

    @Candidate(applicable= { Phase.BASIC, Phase.BOX }, mostSpecific=true)
    int _div(int x, int y) { return -1; }
    @Candidate(applicable= { Phase.BASIC, Phase.BOX })
    long _div(long x, long y) { return -1; }
    @Candidate(applicable= { Phase.BASIC, Phase.BOX })
    float _div(float x, float y) { return -1; }
    @Candidate(applicable= { Phase.BASIC, Phase.BOX })
    double _div(double x, double y) { return -1; }

    {
        int i1 = 1 + 1;
        int i2 = 5 - new Integer(3);
        int i3 = new Integer(5) * 3;
        int i4 = new Integer(6) / new Integer(2);
    }
}
