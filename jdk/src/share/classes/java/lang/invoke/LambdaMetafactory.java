/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

/**
 * <p>Bootstrap methods for converting lambda expressions and method references to functional interface objects.</p>
 *
 * <p>For every lambda expressions or method reference in the source code, there is a target type which is a
 * functional interface. Evaluating a lambda expression produces an object of its target type. The mechanism for
 * evaluating lambda expressions is to invoke an invokedynamic call site, which takes arguments describing the sole
 * method of the functional interface and the implementation method, and returns an object (the lambda object) that
 * implements the target type. Methods of the lambda object invoke the implementation method. For method
 * references, the implementation method is simply the referenced method; for lambda expressions, the
 * implementation method is produced by the compiler based on the body of the lambda expression. The methods in
 * this file are the bootstrap methods for those invokedynamic call sites, called lambda factories, and the
 * bootstrap methods responsible for linking the lambda factories are called lambda meta-factories.
 *
 * <p>The bootstrap methods in this class take the information about the functional interface, the implementation
 * method, and the static types of the captured lambda arguments, and link a call site which, when invoked,
 * produces the lambda object.
 *
 * <p>Two pieces of information are needed about the functional interface: the SAM method and the type of the SAM
 * method in the functional interface. The type can be different when parameterized types are used. For example,
 * consider
 * <code>interface I&lt;T&gt; { int m(T x); }</code> if this SAM type is used in a lambda
 * <code>I&lt;Byte&gt; v = ...</code>, we need both the actual SAM method which has the signature
 * <code>(Object)int</code> and the functional interface type of the method, which has signature
 * <code>(Byte)int</code>.  The latter is the instantiated erased functional interface method type, or
 * simply <I>instantiated method type</I>.
 *
 * <p>While functional interfaces only have a single abstract method from the language perspective (concrete
 * methods in Object are and default methods may be present), at the bytecode level they may actually have multiple
 * methods because of the need for bridge methods. Invoking any of these methods on the lambda object will result
 * in invoking the implementation method.
 *
 * <p>The argument list of the implementation method and the argument list of the functional interface method(s)
 * may differ in several ways.  The implementation methods may have additional arguments to accommodate arguments
 * captured by the lambda expression; there may also be differences resulting from permitted adaptations of
 * arguments, such as casting, boxing, unboxing, and primitive widening. They may also differ because of var-args,
 * but this is expected to be handled by the compiler.
 *
 * <p>Invokedynamic call sites have two argument lists: a static argument list and a dynamic argument list.  The
 * static argument list lives in the constant pool; the dynamic argument list lives on the operand stack at
 * invocation time.  The bootstrap method has access to the entire static argument list (which in this case,
 * contains method handles describing the implementation method and the canonical functional interface method),
 * as well as a method signature describing the number and static types (but not the values) of the dynamic
 * arguments, and the static return type of the invokedynamic site.
 *
 * <p>The implementation method is described with a method handle. In theory, any method handle could be used.
 * Currently supported are method handles representing invocation of virtual, interface, constructor and static
 * methods.
 *
 * <p>Assume:
 * <ul>
 *      <li>the functional interface method has N arguments, of types (U1, U2, ... Un) and return type Ru</li>
 *      <li>then the instantiated method type also has N arguments, of types (T1, T2, ... Tn) and return type Rt</li>
 *      <li>the implementation method has M arguments, of types (A1..Am) and return type Ra,</li>
 *      <li>the dynamic argument list has K arguments of types (D1..Dk), and the invokedynamic return site has
 *          type Rd</li>
 *      <li>the functional interface type is F</li>
 * </ul>
 *
 * <p>The following signature invariants must hold:
 * <ul>
 *     <li>Rd is a subtype of F</li>
 *     <li>For i=1..N, Ti is a subtype of Ui</li>
 *     <li>Either Rt and Ru are primitive and are the same type, or both are reference types and
 *         Rt is a subtype of Ru</li>
 *     <li>If the implementation method is a static method:
 *     <ul>
 *         <li>K + N = M</li>
 *         <li>For i=1..K, Di = Ai</li>
 *         <li>For i=1..N, Ti is adaptable to Aj, where j=i+k</li>
 *     </ul></li>
 *     <li>If the implementation method is an instance method:
 *     <ul>
 *         <li>K + N = M + 1</li>
 *         <li>D1 must be a subtype of the enclosing class for the implementation method</li>
 *         <li>For i=2..K, Di = Aj, where j=i-1</li>
 *         <li>For i=1..N, Ti is adaptable to Aj, where j=i+k-1</li>
 *     </ul></li>
 *     <li>The return type Rt is void, or the return type Ra is not void and is adaptable to Rt</li>
 * </ul>
 *
 * <p>Note that the potentially parameterized implementation return type provides the value for the SAM. Whereas
 * the completely known instantiated return type is adapted to the implementation arguments. Because the
 * instantiated type of the implementation method is not available, the adaptability of return types cannot be
 * checked as precisely at link-time as the arguments can be checked. Thus a loose version of link-time checking is
 * done on return type, while a strict version is applied to arguments.
 *
 * <p>A type Q is considered adaptable to S as follows:
 * <table>
 *     <tr><th>Q</th><th>S</th><th>Link-time checks</th><th>Capture-time checks</th></tr>
 *     <tr>
 *         <td>Primitive</td><td>Primitive</td>
 *         <td>Q can be converted to S via a primitive widening conversion</td>
 *         <td>None</td>
 *     </tr>
 *     <tr>
 *         <td>Primitive</td><td>Reference</td>
 *         <td>S is a supertype of the Wrapper(Q)</td>
 *         <td>Cast from Wrapper(Q) to S</td>
 *     </tr>
 *     <tr>
 *         <td>Reference</td><td>Primitive</td>
 *         <td>strict: Q is a primitive wrapper and Primitive(Q) can be widened to S
 *         <br>loose: If Q is a primitive wrapper, check that Primitive(Q) can be widened to S</td>
 *         <td>If Q is not a primitive wrapper, cast Q to the base Wrapper(S); for example Number for numeric types</td>
 *     </tr>
 *     <tr>
 *         <td>Reference</td><td>Reference</td>
 *         <td>strict: S is a supertype of Q
 *         <br>loose: none</td>
 *         <td>Cast from Q to S</td>
 *     </tr>
 * </table>
 *
 *
 */
public class LambdaMetafactory {

    /**
     * Standard meta-factory for conversion of lambda expressions or method references to functional interfaces.
     *
     * @param caller Stacked automatically by VM; represents a lookup context with the accessibility privileges
     *               of the caller.
     * @param invokedName Stacked automatically by VM; the name of the invoked method as it appears at the call site.
     *                    Currently unused.
     * @param invokedType Stacked automatically by VM; the signature of the invoked method, which includes the
     *                    expected static type of the returned lambda object, and the static types of the captured
     *                    arguments for the lambda.  In the event that the implementation method is an instance method,
     *                    the first argument in the invocation signature will correspond to the receiver.
     * @param samMethod The primary method in the functional interface to which the lambda or method reference is
     *                  being converted, represented as a method handle.
     * @param implMethod The implementation method which should be called (with suitable adaptation of argument
     *                   types, return types, and adjustment for captured arguments) when methods of the resulting
     *                   functional interface instance are invoked.
     * @param instantiatedMethodType The signature of the SAM method from the functional interface's perspective
     * @return a CallSite, which, when invoked, will return an instance of the functional interface
     * @throws ReflectiveOperationException
     * @throws LambdaConversionException If any of the meta-factory protocol invariants are violated
     */
    public static CallSite metaFactory(MethodHandles.Lookup caller,
                                       String invokedName,
                                       MethodType invokedType,
                                       MethodHandle samMethod,
                                       MethodHandle implMethod,
                                       MethodType instantiatedMethodType)
                   throws ReflectiveOperationException, LambdaConversionException {
        AbstractValidatingLambdaMetafactory mf;
        mf = new InnerClassLambdaMetafactory(caller, invokedType, samMethod, implMethod, instantiatedMethodType);
        mf.validateMetafactoryArgs();
        return mf.buildCallSite();
    }
}
