/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

package java.dyn;

/**
 * {@code InvokeDynamic} is a class with neither methods nor instances,
 * which serves only as a syntactic marker in Java source code for
 * an {@code invokedynamic} instruction.
 * (See <a href="package-summary.html#jvm_mods">the package information</a> for specifics on this instruction.)
 * <p>
 * The {@code invokedynamic} instruction is incomplete without a target method.
 * The target method is a property of the reified {@linkplain CallSite call site object}
 * which is linked to each active {@code invokedynamic} instruction.
 * The call site object is initially produced by a
 * {@linkplain java.dyn.Linkage#registerBootstrapMethod(Class, MethodHandle) bootstrap method}
 * associated with the class whose bytecodes include the dynamic call site.
 * <p>
 * The type {@code InvokeDynamic} has no particular meaning as a
 * class or interface supertype, or an object type; it can never be instantiated.
 * Logically, it denotes a source of all dynamically typed methods.
 * It may be viewed as a pure syntactic marker of static calls.
 * It may be imported for ease of use.
 * <p>
 * Here are some examples:
 * <p><blockquote><pre>
 * Object x; String s; int i;
 * x = InvokeDynamic.greet("world"); // greet(Ljava/lang/String;)Ljava/lang/Object;
 * s = InvokeDynamic.&lt;String&gt;hail(x); // hail(Ljava/lang/Object;)Ljava/lang/String;
 * InvokeDynamic.&lt;void&gt;cogito(); // cogito()V
 * i = InvokeDynamic.&lt;int&gt;#"op:+"(2, 3); // "op:+"(II)I
 * </pre></blockquote>
 * Each of the above calls generates a single invokedynamic instruction
 * with the name-and-type descriptors indicated in the comments.
 * The argument types are taken directly from the actual arguments,
 * while the return type is taken from the type parameter.
 * (This type parameter may be a primtive, and it defaults to {@code Object}.)
 * The final example uses a special syntax for uttering non-Java names.
 * Any name legal to the JVM may be given between the double quotes.
 * None of these calls is complete without a bootstrap method,
 * which must be registered by the static initializer of the enclosing class.
 * @author John Rose, JSR 292 EG
 */
@MethodHandle.PolymorphicSignature
public final class InvokeDynamic {
    private InvokeDynamic() { throw new InternalError(); }  // do not instantiate

    // no statically defined static methods
}
