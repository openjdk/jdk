/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8302260 8372002
 * @build p.C p.D p.I p.q.Q
 * @run junit DescribeConstableTest
 * @summary Test VarHandle::describeConstable on static fields
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle.VarHandleDesc;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import static org.junit.jupiter.api.Assertions.*;

public class DescribeConstableTest {
    private static final Lookup LOOKUP = MethodHandles.lookup();
    private static Stream<Arguments> staticTestCases() {
        return Stream.of(
                // static field defined in p.C only
                Arguments.of(p.C.class, "cString", String.class, p.C.class, "CClass"),
                // static fields defined in both superinterface p.I and superclass p.q.Q
                // resolved to the one defined in the direct superinterface of C
                Arguments.of(p.C.class, "stringField", String.class, p.I.class, "I"),
                Arguments.of(p.C.class, "longField", long.class, p.I.class, 10L),
                // static fields defined in superclass p.q.Q only
                Arguments.of(p.C.class, "stringField2", String.class, p.q.Q.class, "QClass2"),
                Arguments.of(p.C.class, "longField2", long.class, p.q.Q.class, 102L),
                // static fields defined in superinterface p.I only
                Arguments.of(p.C.class, "stringField3", String.class, p.I.class, "I3"),
                Arguments.of(p.C.class, "longField3", long.class, p.I.class, 13L),
                // static fields defined in p.D only
                Arguments.of(p.D.class, "dString", String.class, p.D.class, "DClass"),
                Arguments.of(p.D.class, "dLong", long.class, p.D.class, 1L),
                // static fields defined in both superinterface p.I and superclass p.q.Q
                // resolved to the one defined in the direct superinterface of D
                Arguments.of(p.D.class, "stringField", String.class, p.I.class, "I"),
                Arguments.of(p.D.class, "longField", long.class, p.I.class, 10L)
        );
    }

    @ParameterizedTest
    @MethodSource("staticTestCases")
    void testStatic(Class<?> refc, String name, Class<?> type, Class<?> declaringClass, Object value) throws Throwable {
        var vh = LOOKUP.findStaticVarHandle(refc, name, type);
        assertEquals(value, vh.get());

        var refcDesc = refc.describeConstable().orElseThrow();
        var typeDesc = type.describeConstable().orElseThrow();
        var vhd = vh.describeConstable().orElseThrow();
        var vhd2 = VarHandleDesc.ofStaticField(refcDesc, name, typeDesc);

        assertEquals(value, vhd.resolveConstantDesc(LOOKUP).get());
        assertEquals(value, vhd2.resolveConstantDesc(LOOKUP).get());

        assertEquals(vhd.toString(), varHandleDescString(declaringClass, name, type, true));
    }

    private static Arguments[] instanceTestCases() {
        return new Arguments[] {
                // Basic instance field in p.q.Q
                Arguments.of(p.q.Q.class, "instanceIntField", int.class, new p.q.Q(), 42),
                // p.C.instanceIntField hides the superclass instanceIntField, but it still exists
                Arguments.of(p.C.class, "instanceIntField", int.class, new p.C(), 76),
                Arguments.of(p.q.Q.class, "instanceIntField", int.class, new p.C(), 42),
                // p.D.instanceIntField points to that of p.q.Q
                Arguments.of(p.D.class, "instanceIntField", int.class, new p.D(), 42),
        };
    }

    @ParameterizedTest
    @MethodSource("instanceTestCases")
    void testInstance(Class<?> refc, String name, Class<?> type, Object instance, Object value) throws Throwable {
        var vh = LOOKUP.findVarHandle(refc, name, type).withInvokeBehavior();
        assertEquals(value, vh.get(instance));

        var refcDesc = refc.describeConstable().orElseThrow();
        var typeDesc = type.describeConstable().orElseThrow();
        var vhd = vh.describeConstable().orElseThrow();
        var vhd2 = VarHandleDesc.ofField(refcDesc, name, typeDesc);

        assertEquals(value, vhd.resolveConstantDesc(LOOKUP).get(instance));
        assertEquals(value, vhd2.resolveConstantDesc(LOOKUP).get(instance));

        // The string does not use the declaring class because
        // receiver is restricted on the handle
        assertEquals(vhd.toString(), varHandleDescString(refc, name, type, false));
    }

    static String varHandleDescString(Class<?> declaringClass, String name, Class<?> type, boolean staticField) {
        return String.format("VarHandleDesc[%s%s.%s:%s]",
                             staticField ? "static " : "",
                             declaringClass.describeConstable().orElseThrow().displayName(), name,
                             type.describeConstable().orElseThrow().displayName());
    }
}
