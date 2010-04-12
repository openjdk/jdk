/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.dyn;

/**
 * Syntactic marker to request javac to emit an {@code invokedynamic} instruction.
 * An {@code invokedynamic} instruction is a 5-byte bytecoded instruction
 * which begins with an opcode byte of value 186 ({@code 0xBA}),
 * and is followed by a two-byte index of a {@code NameAndType} constant
 * pool entry, then by two zero bytes.  The constant pool reference gives
 * the method name and argument and return types of the call site; there
 * is no other information provided at the call site.
 * <p>
 * The {@code invokedynamic} instruction is incomplete without a target method.
 * The target method is a property of the reified call site object
 * (of type {@link CallSite}) which is in a one-to-one association with each
 * corresponding {@code invokedynamic} instruction.  The call site object
 * is initially produced by a <em>bootstrap method</em> associated with
 * the call site, via the various overloadings of {@link Linkage#registerBootstrapMethod}.
 * <p>
 * The type {@code InvokeDynamic} has no particular meaning as a
 * class or interface supertype, or an object type; it can never be instantiated.
 * Logically, it denotes a source of all dynamically typed methods.
 * It may be viewed as a pure syntactic marker (an importable one) of static calls.
 * <p>
 * Here are some examples of usage:
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
public final class InvokeDynamic {
    private InvokeDynamic() { throw new InternalError(); }  // do not instantiate

    // no statically defined static methods
}
