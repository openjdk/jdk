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
 * @test
 * @summary Tests for enhanced local variable declarations
 * @build KullaTesting TestingInputStream
 * @run junit EnhancedVariableDeclarationsTest
 */
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnhancedVariableDeclarationsTest extends KullaTesting {

    @Test
    public void testOneComponent() {
        assertEval("record Point(int a) {}");
        assertEquals(varKey(assertEval("Point p = new Point(42);")).name(), "p");
        assertEval("Point(int b) = p;");
        assertEval("b + 1", "43");
    }

    @Test
    public void testMultipleComponentsFlat() {
        assertEval("record Point(int a, int b) {}");
        assertEquals(varKey(assertEval("Point p = new Point(1, 2);")).name(), "p");
        assertEval("Point(int x, int y) = p;");
        assertEval("x + y", "3");
    }

    @Test
    public void testMultipleComponentsNested() {
        assertEval("record Point(int a, int b) {}");
        assertEval("record NumberedPoint(Point p, int n) {}");
        assertEquals(varKey(assertEval("NumberedPoint p = new NumberedPoint(new Point(1, 2), 97);")).name(), "p");
        assertEval("NumberedPoint(Point(int x, int y), int serialNumber) = p;");
        assertEval("x + y + serialNumber", "100");
    }

    @Test
    public void testMultipleComponentsUnnamedFirstComponent() {
        assertEval("record Point(int a, int b) {}");
        assertEquals(varKey(assertEval("Point p = new Point(1, 2);")).name(), "p");

        assertEval("Point(_, int y) = p;");
        assertDeclareFail("x", "compiler.err.cant.resolve.location");
        assertEval("y", "2");
    }

    @Test
    public void testMultipleComponentsUnnamedSecondComponent() {
        assertEval("record Point(int a, int b) {}");
        assertEquals(varKey(assertEval("Point p = new Point(1, 2);")).name(), "p");

        assertEval("Point(int x, _) = p;");
        assertDeclareFail("y", "compiler.err.cant.resolve.location");
        assertEval("x", "1");
    }

    @Test
    public void testMultipleComponentsAllUnnamed() {
        assertEval("record Point(int a, int b) {}");
        assertEquals(varKey(assertEval("Point p = new Point(1, 2);")).name(), "p");

        assertEval("Point(_, _) = p;");
    }

    @Test
    public void testSingleUnnamedBinding() {
        assertEval("record Point(int a) {}");
        assertEval("Point p = new Point(42);");

        assertEval("Point(int _) = p;");
        assertEval("Point(var _) = p;");
    }

    @Test
    public void testRecordNoComponents() {
        assertEval("record Point() {}");
        assertEquals(varKey(assertEval("Point p = new Point();")).name(), "p");

        assertEval("Point() = p;");
    }

    @Test
    public void testInapplicableEnhancedLocalVarDeclPatternIsRejected() {
        assertEval("record Coordinate(int value) {}");
        assertEval("record Point(int x, int y) {}");
        assertEval("Point p = new Point(1, 2);");
        assertDeclareFail("Point(Coordinate x, Coordinate y) = p;", "compiler.err.prob.found.req");
    }

    @Test
    public void testQualifiedAndGenericHead() {
        assertEval("class Outer { static record Box<T>(T value) {} }");
        assertEval("Outer.Box<String> box = new Outer.Box<String>(\"hello\");");
        assertEval("Outer.Box<String>(String value) = box;");
        assertEval("value.length()", "5");
    }

    @Test
    public void testEnhancedFor() {
        assertEval("record Point(int a) {}");
        assertEval("java.util.List<Point> l = java.util.List.of(new Point(1), new Point(1), new Point(1));");
        assertEval("int sum = 0;");
        assertEval("for (Point(int b) : l) { sum += b; }");
        assertEval("sum", "3");
    }

    @Test
    public void testAnnotation() {
        assertEval("import java.lang.annotation.*;");
        assertEval("@Target(ElementType.TYPE_USE) @interface TA {}");
        assertEval("record Point(int a) {}");
        assertEval("Point p = new Point(1);");
        assertDeclareFail("@TA Point(int x) = p;", "compiler.err.annotations.in.enhanced.declarations.not.allowed");
    }

    @Test
    public void testFinal() {
        assertEval("record Point(int a) {}");
        assertEval("Point p = new Point(1);");
        assertDeclareFail("final Point(int x) = p;", "compiler.err.final.in.enhanced.declarations.not.allowed");
    }

    @Test
    public void testInferredFlat() {
        assertEval("record Point(int a, int b) {}");
        assertEval("Point p = new Point(1, 2);");
        assertEval("Point(var x, var y) = p;");
    }

    @Test
    public void testInferredPatternsNonExhaustive() {
        assertEval("record Point(int x) {}");
        assertEval("record Pair(int x, int y) {}");
        assertEval("record Box<T>(T value) {}");
        assertEval("record Holder(Object value) {}");
        assertEval("interface IBox {}");
        assertEval("record BoxImpl(int x) implements IBox {}");

        assertDeclareFail("Pair(var x, var y) = new Object();", "compiler.err.enhanced.local.variable.declaration.not.exhaustive.on.type");
        assertDeclareFail("Box(var value) = new Object();", "compiler.err.enhanced.local.variable.declaration.not.exhaustive.on.type");
        assertDeclareFail("BoxImpl(var x) = (IBox) new BoxImpl(1);", "compiler.err.enhanced.local.variable.declaration.not.exhaustive.on.type");
        assertDeclareFail("Holder(Point(var x)) = new Holder(new Point(1));", "compiler.err.enhanced.local.variable.declaration.not.exhaustive.on.type");
    }

    @Test
    public void testInferredPatternsNotApplicable() {
        assertEval("record Point(int x) {}");
        assertEval("record Pair(int x, int y) {}");
        assertDeclareFail("Point(var x) = \"just a string\";", "compiler.err.prob.found.req");
        assertDeclareFail("Point(var x) = new Pair(1, 2);", "compiler.err.prob.found.req");
    }

    @Test
    public void testInferredMultipleComponentsNested() {
        assertEval("record Point(int a, int b) {}");
        assertEval("record NumberedPoint(Point p, int n) {}");
        assertEquals(varKey(assertEval("NumberedPoint p = new NumberedPoint(new Point(1, 2), 97);")).name(), "p");
        assertEval("NumberedPoint(Point(var x, var y), var serialNumber) = p;");
        assertEval("x + y + serialNumber", "100");
    }

    @BeforeEach
    public void setUp() {
        super.setUp(bc -> bc.compilerOptions("--source", System.getProperty("java.specification.version"), "--enable-preview").remoteVMOptions("--enable-preview"));
    }
}
