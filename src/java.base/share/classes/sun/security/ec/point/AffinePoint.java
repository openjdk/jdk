/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package sun.security.ec.point;

import sun.security.util.math.ImmutableIntegerModuloP;
import sun.security.util.math.IntegerFieldModuloP;
import sun.security.util.math.IntegerMontgomeryFieldModuloP;

import java.security.spec.ECPoint;
import java.util.Objects;

/**
 * Elliptic curve point represented using affine coordinates (x, y). This class
 * is not part of the sun.security.ec.point.Point hierarchy because it is not
 * used to hold intermediate values during point arithmetic, and so it does not
 * have a mutable form.
 */
public class AffinePoint {

    private final ImmutableIntegerModuloP x;
    private final ImmutableIntegerModuloP y;

    public AffinePoint(ImmutableIntegerModuloP x, ImmutableIntegerModuloP y) {
        this.x = x;
        this.y = y;
    }

    public static AffinePoint fromECPoint(
            ECPoint ecPoint, IntegerFieldModuloP field) {
        return new AffinePoint(
                field.getElement(ecPoint.getAffineX()),
                field.getElement(ecPoint.getAffineY()));
    }

    public ECPoint toECPoint() {
        return new ECPoint(getX().asBigInteger(), getY().asBigInteger());
    }

    public ImmutableIntegerModuloP getX() {
        return getX(true);
    }

    public ImmutableIntegerModuloP getX(boolean fieldCheck) {
        IntegerFieldModuloP field = x.getField();
        if (fieldCheck && field instanceof IntegerMontgomeryFieldModuloP) {
            return ((IntegerMontgomeryFieldModuloP)field).fromMontgomery(x);
        }
        return x;
    }

    public ImmutableIntegerModuloP getY() {
        return getY(true);
    }

    public ImmutableIntegerModuloP getY(boolean fieldCheck) {
        IntegerFieldModuloP field = y.getField();
        if (fieldCheck && field instanceof IntegerMontgomeryFieldModuloP) {
            return ((IntegerMontgomeryFieldModuloP)field).fromMontgomery(y);
        }
        return y;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AffinePoint)) {
            return false;
        }
        AffinePoint p = (AffinePoint) obj;
        boolean xEquals, yEquals;
        boolean thisMont = x.getField() instanceof IntegerMontgomeryFieldModuloP;
        boolean objMont = p.x.getField() instanceof IntegerMontgomeryFieldModuloP;
        if (thisMont ^ objMont == false) {
            // both fields same
            xEquals = x.asBigInteger().equals(p.x.asBigInteger());
            yEquals = y.asBigInteger().equals(p.y.asBigInteger());
        } else if (thisMont) {
            // mismatched fields should not happen in production, but useful in
            // testing
            IntegerMontgomeryFieldModuloP field =
                (IntegerMontgomeryFieldModuloP)x.getField();
            xEquals = x.asBigInteger().equals(
                field.getElement(p.x.asBigInteger()).asBigInteger());
            yEquals = y.asBigInteger().equals(
                field.getElement(p.y.asBigInteger()).asBigInteger());
        } else {
            IntegerMontgomeryFieldModuloP field =
                (IntegerMontgomeryFieldModuloP)p.x.getField();
            xEquals = field.getElement(
                x.asBigInteger()).asBigInteger().equals(p.x.asBigInteger());
            yEquals = field.getElement(
                y.asBigInteger()).asBigInteger().equals(p.y.asBigInteger());
        }
        return xEquals && yEquals;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "(" + x.asBigInteger().toString() + "," +
            y.asBigInteger().toString() + ")";
    }
}
