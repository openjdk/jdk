/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify Infer.instantiatePatternType provides correct results
 * @library /tools/lib/types
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.model
 *          jdk.compiler/com.sun.tools.javac.parser
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @run main InferenceUnitTest
 */

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Infer;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import java.net.URI;
import java.util.Objects;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class InferenceUnitTest {

    Context context;
    Infer infer;
    Types types;

    public static void main(String... args) throws Exception {
        new InferenceUnitTest().runAll();
    }

    void runAll() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        String source = """
                        interface A<T> {}
                        interface B<T> extends A<T> {}
                        interface C<X,Y> extends A<X> {}
                        interface D<X,Y> extends A<Y> {}
                        interface E<T> extends C<T,T> {}
                        interface F<T> extends A<B<T>> {}
                        interface G<T extends Number> extends A<T> {}
                        interface H extends A<String> {}
                        interface I<T> extends H {}
                        class Test<T1 extends CharSequence&Runnable, T2 extends Number> {
                        }
                        interface RecursiveTest1Interface<IB extends RecursiveTest1Interface<IB>> { }
                        interface RecursiveTest1Use<BB extends RecursiveTest1Use<BB>> extends RecursiveTest1Interface<BB> { }
                        interface RecursiveTest2Interface<X> { }
                        interface RecursiveTest2Use<X extends RecursiveTest2Use<X, Y>, Y> extends RecursiveTest2Interface<Y> { }
                        """;

        JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(null, null, null, null, null, List.of(SimpleJavaFileObject.forSource(URI.create("mem://Test.java"), source)));
        task.enter();
        context = task.getContext();
        infer = Infer.instance(context);
        types = Types.instance(context);

        checkInferedType("A<String>", "B", "B<java.lang.String>");
        checkInferedType("A<String>", "C", "C<java.lang.String,?>");
        checkInferedType("A<String>", "D", "D<?,java.lang.String>");
        checkInferedType("A<String>", "E", "E<java.lang.String>");
        checkInferedType("A<String>", "F", null);
        checkInferedType("A<String>", "G", null); // doesn't check bounds
        checkInferedType("A<String>", "H", "H");
        checkInferedType("A<String>", "I", "I<?>");

        checkInferedType("A<B<String>>", "B", "B<B<java.lang.String>>");
        checkInferedType("A<B<String>>", "C", "C<B<java.lang.String>,?>");
        checkInferedType("A<B<String>>", "F", "F<java.lang.String>");
        checkInferedType("A<B<String>>", "H", null);
        checkInferedType("A<B<String>>", "I", null);

        checkInferedType("C<String, String>", "E", "E<java.lang.String>");
        checkInferedType("C<String, Integer>", "E", null);
        checkInferedType("C<A<?>, A<?>>", "E", "E<A<?>>");
        checkInferedType("C<A<? extends Object>, A<?>>", "E", "E<A<? extends java.lang.Object>>");

        if (false) {
        checkInferedType("A", "B", "B");
        checkInferedType("A", "C", "C");
        checkInferedType("A", "D", "D");
        checkInferedType("A", "E", "E");
        checkInferedType("A", "F", "F");
        checkInferedType("A", "G", "G");
        checkInferedType("A", "H", "H");
        }

        checkInferedType("A", "I", "I<?>"); // always erases if input is raw

        checkInferedType("A<?>", "B", "B<?>");
        checkInferedType("A<?>", "C", "C<?,?>");
        checkInferedType("A<?>", "D", "D<?,?>");
        checkInferedType("A<?>", "E", "E<?>");
        checkInferedType("A<?>", "F", "F<?>");
        checkInferedType("A<?>", "G", "G<?>");
        checkInferedType("A<?>", "H", "H");
        checkInferedType("A<?>", "I", "I<?>");

        checkInferedType("A<? extends Runnable>", "B", "B<? extends java.lang.Runnable>");
        checkInferedType("A<? extends Runnable>", "C", "C<? extends java.lang.Runnable,?>");
        checkInferedType("A<? extends Runnable>", "D", "D<?,? extends java.lang.Runnable>");
        checkInferedType("A<? extends Runnable>", "E", "E<? extends java.lang.Runnable>");
        checkInferedType("A<? extends Runnable>", "F", null);
        checkInferedType("A<? extends Runnable>", "G", "G<? extends java.lang.Number&java.lang.Runnable>"); // should infer an intersection bound
        checkInferedType("A<? extends Runnable>", "H", null);
        checkInferedType("A<? extends Runnable>", "I", null);

        checkInferedType("A<? extends B<String>>", "F", "F<java.lang.String>"); // inference doesn't recur on bounds checks
        checkInferedType("A<? extends A<String>>", "F", "F<java.lang.String>"); // inference doesn't recur on bounds checks

        checkInferedType("C<? extends Number, Integer>", "E", "E<java.lang.Integer>"); // doesn't know how to mix types and wildcards
        checkInferedType("C<Integer, ? extends Number>", "E", "E<java.lang.Integer>"); // doesn't know how to mix types and wildcards
        checkInferedType("C<?, ? extends Number>", "E", "E<? extends java.lang.Number>");
        checkInferedType("C<? extends Number, ?>", "E", "E<? extends java.lang.Number>");

        checkInferedType("C<? extends Number, ? extends Integer>", "E", "E<? extends java.lang.Integer>");
        checkInferedType("C<? extends Integer, ? extends Number>", "E", "E<? extends java.lang.Integer>");
        checkInferedType("C<? extends Runnable, ? extends Cloneable>", "E", "E<? extends java.lang.Object&java.lang.Cloneable&java.lang.Runnable>"); // should infer an intersection bound
        checkInferedType("C<? extends Number, ? super Integer>", "E", "E<? extends java.lang.Number>"); // doesn't know how to mix lower/upper
        checkInferedType("C<? super Integer, ? super Number>", "E", "E<? super java.lang.Number>");
        checkInferedType("C<? super B<String>, ? super C<String,String>>", "E", "E<? super A<java.lang.String>>"); // doesn't do lub

        checkInferedType("H", "I", "I<?>");

        checkInferedType("B<String>", "C", null); // no sideways casts

        checkInferedType("A<T1>", "B", "B<T1>");
        checkInferedType("RecursiveTest1Interface<?>", "RecursiveTest1Use", "RecursiveTest1Use<? extends java.lang.Object&RecursiveTest1Use<?>&RecursiveTest1Interface<? extends RecursiveTest1Use<?>>>");
        checkInferedType("RecursiveTest2Interface<?>", "RecursiveTest2Use", "RecursiveTest2Use<? extends RecursiveTest2Use<?,?>,?>");
    }

    private void checkInferedType(String base, String test, String expected) {
        Type baseType = parseType(base);
        TypeSymbol testType = parseType(test).tsym;
        Type actualType = infer.instantiatePatternType(baseType, testType);
        String actualTypeString = actualType != null ? actualType.toString() : null;
        if (!Objects.equals(expected, actualTypeString)) {
            error("Unexpected type, expected: " + expected + ", got: " + actualTypeString);
        }
    }
    Type parseType(String spec) {
        ParserFactory fact = ParserFactory.instance(context);
        JCExpression specTypeTree = fact.newParser(spec, false, false, false).parseType();
        Attr attr = Attr.instance(context);
        JavacElements elementUtils = JavacElements.instance(context);
        ClassSymbol testClass = elementUtils.getTypeElement("Test");
        return attr.attribType(specTypeTree, testClass);
    }

    /** assert that 's' is the same type as 't' */
    public void assertSameType(Type s, Type t) {
        assertSameType(s, t, true);
    }

    /** assert that 's' is/is not the same type as 't' */
    public void assertSameType(Type s, Type t, boolean expected) {
        if (types.isSameType(s, t) != expected) {
            String msg = expected ?
                " is not the same type as " :
                " is the same type as ";
            error(s + msg + t);
        }
    }

    private void error(String msg) {
        throw new AssertionError("Unexpected result: " + msg);
    }

}
