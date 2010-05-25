/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This package provides a mechanism for defining and
 * inserting tracepoints into Java-technology based applications, which
 * can then be monitored by the tracing tools available on the system.
 * <p>
 * To add tracepoints to a program, you must first decide where to place the
 * tracepoints, what the logical names are for these points, what information
 * will be available to the tracing mechanisms at each point, and decide upon
 * any logical grouping.
 * <p>
 * You add instrumentation to a program in three steps:
 * <ul>
 * <li>First, declare tracepoints by creating interfaces to define
 * them, and include these interfaces in the program definition.
 * The declared interfaces are standard Java technology-based
 * interfaces and are compiled with the program.</li>
 * <li>Second, add code in the application to create an instance of the
 * interface at some point during the initialization of the application,
 * using a factory class provided by the system. The reference to the
 * instance can be stored as a global static, or passed as context to all
 * the places where it is needed.</li>
 * <li>Finally, add the actual tracepoints to the desired locations in the
 * application by inserting a call to one of the methods defined in the
 * interface, via the factory-created reference.</li>
 * </ul>
 * <p>
 * The method calls representing the tracepoints have no logical
 * impact on the program.  The side effect of the call is that any
 * activated tracing mechanisms will be notified that the tracepoint has
 * been hit, and will take whatever actions are appropriate (for example,
 * logging  the tracepoint, or triggering a DTrace probe, etc.).  In most
 * cases, the impact on performance of adding tracepoints to the application
 * will be minimal.
 * <p>
 * Each logical grouping of tracepoints should be defined in a common
 * interface, called a <i>provider</i>.  An application can have one or many
 * providers.  Each provider is independent and can be created whenever
 * it is appropriate for that provider, for example, when a subsytem is
 * initialized.  Providers should be disposed of when they are no longer
 * needed, to free up any associated system resources.  Each tracepoint
 * in a provider is represented by a method in that interface.  These methods
 * are referred to as <i>probes</i>.  The method signature determines the probe
 * parameters.  A call to the method with the specified parameters triggers
 * the probe and makes its parameter values visible to any associated tracing
 * mechanism.
 * <p>
 * User-defined interfaces which represent providers must extend the
 * {@code Provider} interface.  To activate the system-defined
 * tracing mechanisms, you must obtain an instance of the
 * {@code ProviderFactory} class, and pass the class of the provider to
 * the {@code createProvider()} method.  The returned instance is then used to
 * trigger the probes later in the application.
 * <p>
 * In addition to triggering the probes, the provider instance can be used
 * to obtain direct references to the {@code Probe} objects, which can be used
 * directly for triggering, or can be queried to determine whether the probe is
 * currently being traced.  The {@code Provider} interface also defines a
 * {@code Provider.dispose()} method which is used to free up any resources
 * that might be associated with that provider.
 * <p>
 * When a probe is triggered, any activated tracing system will be given
 * the provider name, the probe name, and the values of the probe arguments.
 * The tracing system is free to consume this data is whatever way is
 * appropriate.
 * By default, the provider name is the same as the class name of the interface
 * that defines the provider. Similarly, the probe name is
 * the name of the method that defines the probe. These default values
 * can be over-ridden by annotations.  The provider definition can be
 * annotated with the {@code @ProviderName} annotation, whose value will
 * indicate the provider name that the tracing system will use.  Similarly,
 * the {@code @ProbeName} annotation annotates a declared method and
 * indicates the probe name that should be used in the place of the
 * method name.  These annotations can be used to define providers and
 * probes with the same name, in cases where the semantics of the Java language
 * may prevent this.
 * <p>
 * Here is a very small and simple usage example:
 * <p>
 *
<PRE>
   import com.sun.tracing.Provider;
   import com.sun.tracing.ProviderFactory;

   interface MyProvider extends Provider {
       void startProbe();
       void finishProbe(int value);
   }

   public class MyApplication {
       public static void main(String argv[]) {
           ProviderFactory factory = ProviderFactory.getDefaultFactory();
           MyProvider trace = factory.createProvider(MyProvider.class);

           trace.startProbe();
           int result = foo();
           trace.finishProbe(result);

           trace.dispose();
       }
   }
</PRE>
 * <p>
 * The Java Development Kit (JDK) currently only includes one system-defined
 * tracing framework: DTrace. DTrace is enabled automatically whenever an
 * application is run on a system and a JDK release that supports it. When
 * DTrace is enabled, probes are made available for listing and matching by
 * DTrace scripts as soon as the provider is created. At the tracepoint, an
 * associated DTrace script is informed of the creation of the provider, and
 * it takes whatever action it is designed to take. Tracepoints in the
 * program have the following DTrace probe names:<br>
 *   {@code <provider><pid>:<module>:<function>:<probe>}
 * Where:
 * <ul>
 * <li>{@code <provider>} the provider name as specified by the application</li>
 * <li>{@code <pid>} the operating system process ID</li>
 * <li>{@code <module>} undefined, unless specified by the application</li>
 * <li>{@code <function>} undefined, unless specified by the application</li>
 * <li>{@code <probe>} the probe name as specified by the application</li>
 * </ul>
 * <p>
 * The {@code com.sun.tracing.dtrace} package contains additional
 * annotations that can be used to control the names used for the
 * <code>module</code> and <code>function</code> fields, as well as annotations
 * that can be added to the provider to control probe stability and dependency
 * attributes.
 * <p>
 * Integer, float and string probe parameters are made available to DTrace
 * using
 * the built-in argument variables, {@code arg0 ... arg_n}.  Integer-types
 * are passed by value (boxed values are unboxed), floating-point types are
 * passed as encoded integer
 * arguments, and {@code java.lang.String} objects are converted
 * to UTF8 strings, so they can be read into the DTrace script using the
 * {@code copyinstr()} intrinsic.  Non-string and non-boxed primitive
 * reference arguments are only
 * placeholders and have no value.
 * <p>
 * Using the example above, with a theoretical process ID of 123, these are
 * the probes that can be traced from DTrace:
<PRE>
    MyProvider123:::startProbe
    MyProvider123:::finishProbe
</PRE>
 * When {@code finishProbe} executes, {@code arg0} will contain the
 * value of {@code result}.
 * <p>
 * The DTrace tracing mechanism is enabled for all providers, apart from in the
 * following circumstances:
 * <ul>
 * <li>DTrace is not supported on the underlying system.</li>
 * <li>The property {@code com.sun.tracing.dtrace} is set to "disable".</li>
 * <li>The RuntimePermission {@code com.sun.tracing.dtrace.createProvider}
 * is denied to the process.</li>
 * </ul>
 * <p>
 */

package com.sun.tracing;
