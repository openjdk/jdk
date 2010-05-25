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
 * This package contains annotations and enumerations that are used to
 * add DTrace-specific information to a tracing provider.
 * <p>
 * The DTrace-specific annotations modify the attributes of a DTrace provider
 * implementation when it is used by the tracing subsystem.  The annotations are
 * added to a {@code com.sun.tracing} provider specification to control
 * specific attributes of the provider as it relates to DTrace.
 * <p>
 * Any other tracing subsystems supported by the system will ignore these
 * annotations.
 * <p>
 * DTrace probes have additional fields and stability attributes that are
 * not accounted for in the generic tracing package.  If unspecified, the
 * default values are used for the stability and dependency attributes of
 * probes, as well as for the module and field names of the generated probes.
 * The values can be specified by adding the appropriate annotations to the
 * provider specification.
 * <p>
 * The {@code FunctionName} annotation is used to annotate the tracepoint
 * methods defined in the provider specification.  The value of this annotation
 * is used as the {@code function} field in the generated DTrace probes. It
 * is typically set to the name of the enclosing function where the
 * tracepoint is triggered.
 * <p>
 * The {@code ModuleName} annotation is used to annotate the provider
 * specification itself and applies to all the probes in the provider.  It
 * sets the value of the {@code module} field in the generated DTrace probes.
 * <p>
 * The remaining annotations, are also applied to the provider itself, and
 * are used to set the stability and dependency attributes of all probes in
 * that provider.  Each probe field and the probe arguments can be
 * independently assigned interface attributes to control the stability
 * ratings of the probes.
 * <p>
 * Here is an example of how to declare a provider, specifying additional DTrace
 * data:
<PRE>
    &#064;ProviderName("my_app_provider")
    &#064;ModuleName("app.jar")
    &#064;ProviderAttributes(&#064;Attributes={
        name=StabilityLevel.STABLE,data=StabilityLevel.STABLE,
        dependency=DependencyClass.COMMON})
    &#064;ProbeAttributes(&#064;Attributes={
        name=StabilityLevel.STABLE,data=StabilityLevel.STABLE,
        dependency=DependencyClass.COMMON})
    &#064;ModuleAttributes(&#064;Attributes={name=StabilityLevel.UNSTABLE})
    public class MyProvider {
        &#064;FunctionName("main") void startProbe();
    }
</PRE>
 * <p>
 * @see <a href="http://docs.sun.com/app/docs/doc/817-6223/6mlkidlms?a=view">Solaris Dynamic Tracing Guide, Chapter 34: Statically Defined Tracing for User Applications</a>
 * @see <a href="http://docs.sun.com/app/docs/doc/817-6223/6mlkidlnp?a=view">Solaris Dynamic Tracing Guide, Chapter 39: Stability</a>
 */

package com.sun.tracing.dtrace;
