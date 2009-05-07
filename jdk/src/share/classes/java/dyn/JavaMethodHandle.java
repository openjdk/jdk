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
 * A Java method handle extends the basic method handle type with additional
 * programmer defined methods and fields.
 * Its behavior as a method handle is determined at instance creation time,
 * by providing the new instance with an "entry point" method handle
 * to handle calls.  This entry point must accept a leading argument
 * whose type is the Java method handle itself or a supertype, and the
 * entry point is always called with the Java method handle itself as
 * the first argument.  This is similar to ordinary virtual methods, which also
 * accept the receiver object {@code this} as an implicit leading argument.
 * The {@code MethodType} of the Java method handle is the same as that
 * of the entry point method handle, with the leading parameter type
 * omitted.
 * <p>
 * Here is an example of usage:
 * <p><blockquote><pre>
 *     class Greeter extends JavaMethodHandle {
 *         public void run() { System.out.println("hello, "+greetee); }
 *         private final String greetee;
 *         Greeter(String greetee) {
 *             super(RUN);
 *             this.greetee = greetee;
 *         }
 *         // the entry point function is computed once:
 *         private static final MethodHandle RUN
 *             = MethodHandles.findVirtual(MyMethodHandle.class, "run",
 *                   MethodType.make(void.class));
 *     }
 *     Greeter greeter = new Greeter("world");
 *     greeter.run();  // prints "hello, world"
 *     MethodHandle mh = greeter;
 *     mh.invoke();  // also prints "hello, world"
 * </pre></blockquote>
 * <p>
 * In this example, the method {@code run} provides the entry point.
 * The entry point need not be a constant value; it may be independently
 * computed in each call to the constructor.  The entry point does not
 * even need to be a method on the Java method handle class, though
 * that is the typical case.
 * @see MethodHandle
 * @author John Rose, JSR 292 EG
 */
public abstract class JavaMethodHandle
        // Note: This is an implementation inheritance hack, and will be removed
        // with a JVM change which moves the required hidden behavior onto this class.
        extends sun.dyn.BoundMethodHandle
{
    /**
     * When creating a, pass in {@code entryPoint}, any method handle which
     * can take the current object
     * @param entryPoint
     */
    protected JavaMethodHandle(MethodHandle entryPoint) {
        super(entryPoint, 0);
    }
}
