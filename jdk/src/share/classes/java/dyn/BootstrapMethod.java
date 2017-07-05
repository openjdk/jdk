/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.*;

/**
 * Annotation on InvokeDynamic method calls which requests the JVM to use a specific
 * <a href="package-summary.html#bsm">bootstrap method</a>
 * to link the call.  This annotation is not retained as such in the class file,
 * but is transformed into a constant-pool entry for the invokedynamic instruction which
 * specifies the desired bootstrap method.
 * <p>
 * If only the <code>value</code> is given, it must name a subclass of {@link CallSite}
 * with a constructor which accepts a class, string, and method type.
 * If the <code>value</code> and <code>name</code> are both given, there must be
 * a static method in the given class of the given name which accepts a class, string,
 * and method type, and returns a reference coercible to {@link CallSite}.
 * <p>
 * This annotation can be placed either on the return type of a single {@link InvokeDynamic}
 * call (see examples) or else it can be placed on an enclosing class or method, where it
 * determines a default bootstrap method for any {@link InvokeDynamic} calls which are not
 * specifically annotated with a bootstrap method.
 * Every {@link InvokeDynamic} call must be given a bootstrap method.
 * <p>
 * Examples:
<blockquote><pre>
&#064;BootstrapMethod(value=MyLanguageRuntime.class, name="bootstrapDynamic")
String x = (String) InvokeDynamic.greet();
//BSM => MyLanguageRuntime.bootstrapDynamic(Here.class, "greet", methodType(String.class))
&#064;BootstrapMethod(MyCallSite.class)
void example() throws Throwable {
    InvokeDynamic.greet();
    //BSM => new MyCallSite(Here.class, "greet", methodType(void.class))
}
</pre></blockquote>
 * <p>
 */
@Target({ElementType.TYPE_USE,
            // For defaulting every indy site within a class or method; cf. @SuppressWarnings:
            ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR
            })
@Retention(RetentionPolicy.SOURCE)
public @interface BootstrapMethod {
    /** The class containing the bootstrap method. */
    Class<?> value();

    /** The name of the bootstrap method.
     *  If this is the empty string, an instance of the bootstrap class is created,
     *  and a constructor is invoked.
     *  Otherwise, there must be a static method of the required name.
     */
    String name() default "";  // empty string denotes a constructor with 'new'

    /** The argument types of the bootstrap method, as passed out by the JVM.
     *  There is usually no reason to override the default.
     */
    Class<?>[] arguments() default {Class.class, String.class, MethodType.class};
}
