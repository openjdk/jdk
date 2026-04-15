/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8003639
 * @summary convert lambda testng tests to jtreg and add them
 * @run junit MethodReferenceTestKinds
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * @author Robert Field
 */

public class MethodReferenceTestKinds extends MethodReferenceTestKindsSup {

    interface S0 { String get(); }
    interface S1 { String get(MethodReferenceTestKinds x); }
    interface S2 { String get(MethodReferenceTestKinds x, MethodReferenceTestKinds y); }

    interface SXN0 { MethodReferenceTestKindsBase make(MethodReferenceTestKinds x); }
    interface SXN1 { MethodReferenceTestKindsBase make(MethodReferenceTestKinds x, String str); }

    interface SN0 { MethodReferenceTestKindsBase make(); }
    interface SN1 { MethodReferenceTestKindsBase make(String x); }

    class In extends MethodReferenceTestKindsBase {
        In(String val) {
            this.val = val;
        }

        In() {
            this("blank");
        }
    }

    String instanceMethod0() { return "IM:0-" + this; }
    String instanceMethod1(MethodReferenceTestKinds x) { return "IM:1-" + this + x; }

    static String staticMethod0() { return "SM:0"; }
    static String staticMethod1(MethodReferenceTestKinds x) { return "SM:1-" + x; }

    MethodReferenceTestKinds() {
        super("blank");
    }

    MethodReferenceTestKinds inst(String val) {
        var inst = new MethodReferenceTestKinds();
        inst.val = val; // simulate `MethodReferenceTestKinds(String val)` constructor
        return inst;
    }

    @Test
    public void testMRBound() {
        S0 var = this::instanceMethod0;
        assertEquals("IM:0-MethodReferenceTestKinds(blank)", var.get());
    }

    @Test
    public void testMRBoundArg() {
        S1 var = this::instanceMethod1;
        assertEquals("IM:1-MethodReferenceTestKinds(blank)MethodReferenceTestKinds(arg)", var.get(inst("arg")));
    }

    @Test
    public void testMRUnbound() {
        S1 var = MethodReferenceTestKinds::instanceMethod0;
        assertEquals("IM:0-MethodReferenceTestKinds(rcvr)", var.get(inst("rcvr")));
    }

    @Test
    public void testMRUnboundArg() {
        S2 var = MethodReferenceTestKinds::instanceMethod1;
        assertEquals("IM:1-MethodReferenceTestKinds(rcvr)MethodReferenceTestKinds(arg)", var.get(inst("rcvr"), inst("arg")));
    }

    @Test
    public void testMRSuper() {
        S0 var = super::instanceMethod0;
        assertEquals("SIM:0-MethodReferenceTestKinds(blank)", var.get());
    }

    @Test
    public void testMRSuperArg() {
        S1 var = super::instanceMethod1;
        assertEquals("SIM:1-MethodReferenceTestKinds(blank)MethodReferenceTestKinds(arg)", var.get(inst("arg")));
    }

    @Test
    public void testMRStatic() {
        S0 var = MethodReferenceTestKinds::staticMethod0;
        assertEquals("SM:0", var.get());
    }

    @Test
    public void testMRStaticArg() {
        S1 var = MethodReferenceTestKinds::staticMethod1;
        assertEquals("SM:1-MethodReferenceTestKinds(arg)", var.get(inst("arg")));
    }

    @Test
    public void testMRTopLevel() {
        SN0 var = MethodReferenceTestKindsBase::new;
        assertEquals("MethodReferenceTestKindsBase(blank)", var.make().toString());
    }

    @Test
    public void testMRTopLevelArg() {
        SN1 var = MethodReferenceTestKindsBase::new;
        assertEquals("MethodReferenceTestKindsBase(name)", var.make("name").toString());
    }

    @Test
    public void testMRImplicitInner() {
        SN0 var = MethodReferenceTestKinds.In::new;
        assertEquals("In(blank)", var.make().toString());
    }

    @Test
    public void testMRImplicitInnerArg() {
        SN1 var = MethodReferenceTestKinds.In::new;
        assertEquals("In(name)", var.make("name").toString());
    }

}


class MethodReferenceTestKindsBase {
    String val = "unset";

    public String toString() {
        return getClass().getSimpleName() + "(" + val + ")";
    }

    MethodReferenceTestKindsBase(String val) {
        this.val = val;
    }

    MethodReferenceTestKindsBase() {
        this("blank");
    }

}

class MethodReferenceTestKindsSup extends MethodReferenceTestKindsBase {
    String instanceMethod0() { return "SIM:0-" + this; }
    String instanceMethod1(MethodReferenceTestKinds x) { return "SIM:1-" + this + x; }

    MethodReferenceTestKindsSup(String val) {
        super(val);
    }

}
