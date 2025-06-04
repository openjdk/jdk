/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Copyright 2003 Wily Technology, Inc.
 */

/**
 * Provides services that allow Java programming language agents to instrument
 * programs running on the Java Virtual Machine (JVM). The mechanism for
 * instrumentation is modification of the bytecodes of methods.
 *
 * <p> The class files that comprise an agent are packaged into a JAR file, either
 * with the application in an executable JAR, or more commonly, as a separate JAR file
 * called an <em>agent JAR</em>. An attribute in the main manifest of the JAR file
 * identifies one of the class files in the JAR file as the <em>agent class</em>.
 * The agent class defines a special method that the JVM invokes to <em>start</em>
 * the agent.
 *
 * <p> Agents that are packaged with an application in an executable JAR are started
 * at JVM statup time. Agents that are packaged into an agent JAR file may be started
 * at JVM startup time via a command line option, or where an implementation supports
 * it, started in a running JVM.
 *
 * <p> Agents can transform classes in arbitrary ways at load time, transform
 * modules, or transform the bytecode of methods of already loaded classes.
 * Developers or administrators that deploy agents, deploy applications that
 * package an agent with the application, or use tools that load agents into a
 * running application, are responsible for verifying the trustworthiness of each
 * agent including the content and structure of the agent JAR file.
 *
 * <h2>Starting an agent</h2>
 *
 * <h3>Starting an agent packaged with an application in an executable JAR file</h3>
 *
 * <p> The <a href="{@docRoot}/../specs/jar/jar.html">JAR File Specification</a> defines
 * manifest attributes for standalone applications that are packaged as <em>executable
 * JAR files</em>. If an implementation supports a mechanism to start an application as
 * an executable JAR, then the main manifest of the JAR file can include the
 * {@code Launcher-Agent-Class} attribute to specify the binary name of the Java agent
 * class that is packaged with the application. If the attribute is present then the
 * JVM starts the agent by loading the agent class and invoking its {@code agentmain}
 * method. The method is invoked before the application {@code main} method is invoked.
 * The {@code agentmain} method has one of two possible signatures. The JVM first
 * attempts to invoke the following method on the agent class:
 *
 * <blockquote>{@code
 *     public static void agentmain(String agentArgs, Instrumentation inst)
 * }</blockquote>
 *
 * <p> If the agent class does not define this method then the JVM will attempt
 * to invoke:
 *
 * <blockquote>{@code
 *     public static void agentmain(String agentArgs)
 * }</blockquote>
 *
 * <p> The value of the {@code agentArgs} parameter is always the empty string. In
 * the first method, the {@code inst} parameter is an {@link Instrumentation} object
 * that the agent can use to instrument code.
 *
 * <p> The {@code agentmain} method should do any necessary initialization
 * required to start the agent and return. If the agent cannot be started, for
 * example the agent class cannot be loaded, the agent class does not define a
 * conformant {@code agentmain} method, or the {@code agentmain} method throws
 * an uncaught exception or error, the JVM will abort before the application
 * {@code main} method is invoked.
 *
 * <h3>Starting an agent from the command-line interface</h3>
 *
 * <p> Where an implementation provides a means to start agents from the command-line
 * interface, an agent JAR is specified via the following command line option:
 *
 * <blockquote>{@code
 *     -javaagent:<jarpath>[=<options>]
 * }</blockquote>
 *
 * where <i>{@code <jarpath>}</i> is the path to the agent JAR file and
 * <i>{@code <options>}</i> is the agent options.
 *
 * <p> The main manifest of the agent JAR file must contain the attribute {@code
 * Premain-Class}. The value of this attribute is the binary name of the agent class
 * in the JAR file. The JVM starts the agent by loading the agent class and invoking its
 * {@code premain} method. The method is invoked before the application {@code main}
 * method is invoked. The {@code premain} method has one of two possible signatures.
 * The JVM first attempts to invoke the following method on the agent class:
 *
 * <blockquote>{@code
 *     public static void premain(String agentArgs, Instrumentation inst)
 * }</blockquote>
 *
 * <p> If the agent class does not define this method then the JVM will attempt to invoke:
 * <blockquote>{@code
 *     public static void premain(String agentArgs)
 * }</blockquote>
 *
 * <p> The agent is passed its agent options via the {@code agentArgs} parameter.
 * The agent options are passed as a single string, any additional parsing
 * should be performed by the agent itself. In the first method, the {@code inst}
 * parameter is an {@link Instrumentation} object that the agent can use to instrument
 * code.
 *
 * <p> If the agent cannot be started, for example the agent class cannot be loaded,
 * the agent class does not define a conformant {@code premain} method, or the {@code
 * premain} method throws an uncaught exception or error, the JVM will abort before
 * the application {@code main} method is invoked.
 *
 * <p> An implementation is not required to provide a way to start agents
 * from the command-line interface. When it does, then it supports the
 * {@code -javaagent} option as specified above. The {@code -javaagent} option
 * may be used multiple times on the same command-line, thus starting multiple
 * agents. The {@code premain} methods will be called in the order that the
 * agents are specified on the command line. More than one agent may use the
 * same <i>{@code <jarpath>}</i>.
 *
 * <p> The agent class may also have an {@code agentmain} method for use when the agent
 * is started after in a running JVM (see below). When the agent is started using a
 * command-line option, the {@code agentmain} method is not invoked.
 *
 * <h3>Starting an agent in a running JVM</h3>
 *
 * <p> An implementation may provide a mechanism to start agents in a running JVM (meaning
 * after JVM startup). The details as to how this is initiated are implementation specific
 * but typically the application has already started, and its {@code main} method has
 * already been invoked. Where an implementation supports starting an agent in a running
 * JVM, the following applies:
 * <ol>
 *
 *   <li><p> The agent class must be packaged into an agent JAR file. The main manifest
 *   of the agent JAR file must contain the attribute {@code Agent-Class}. The value of
 *   this attribute is the binary name of the agent class in the JAR file. </p></li>
 *
 *   <li><p> The agent class must define a public static {@code agentmain} method. </p></li>
 *
 *   <li><p> The JVM prints a warning on the standard error stream for each agent that it
 *   attempts to start in a running JVM. If an agent was previously started (at JVM
 *   startup, or started in a running JVM), then it is implementation specific as to whether
 *   a warning is printed when attempting to start the same agent a second or subsequent
 *   time. Warnings can be disabled by means of an implementation-specific command line
 *   option.
 *   <p><b>Implementation Note:</b> For the HotSpot VM, the JVM option
 *   {@code -XX:+EnableDynamicAgentLoading} is used to opt-in to allow dynamic loading of
 *   agents into a running JVM. This option suppresses the warning to standard error when
 *   starting an agent in a running JVM. </p></li>
 *
 * </ol>
 *
 * <p> The JVM starts the agent by loading the agent class and invoking its {@code
 * agentmain} method. The {@code agentmain} method has one of two possible signatures.
 * The JVM first attempts to invoke the following method on the agent class:
 *
 * <blockquote>{@code
 *     public static void agentmain(String agentArgs, Instrumentation inst)
 * }</blockquote>
 *
 * <p> If the agent class does not define this method then the JVM will
 * attempt to invoke:
 *
 * <blockquote>{@code
 *     public static void agentmain(String agentArgs)
 * }</blockquote>
 *
 * <p> The agent is passed its agent options via the {@code agentArgs} parameter.
 * The agent options are passed as a single string, any additional parsing
 * should be performed by the agent itself. In the first method, the {@code inst}
 * parameter is an {@link Instrumentation} object that the agent can use to instrument
 * code.
 *
 * <p> The {@code agentmain} method should do any necessary initialization
 * required to start the agent. When startup is complete the method should
 * return. If the agent cannot be started (for example, because the agent class
 * cannot be loaded, or because the agent class does not have a conformant
 * {@code agentmain} method), the JVM will not abort. If the {@code agentmain}
 * method throws an uncaught exception it will be ignored (but may be logged
 * by the JVM for troubleshooting purposes).
 *
 * <p> The agent class may also have a {@code premain} method for use when the agent
 * is started using a command-line option. The {@code premain} method is not invoked
 * when the agent is started in a running JVM.
 *
 * <h2> Loading agent classes and the modules/classes available to the agent
 * class </h2>
 *
 * <p> Classes loaded from the agent JAR file are loaded by the
 * {@linkplain ClassLoader#getSystemClassLoader() system class loader} and are
 * members of the system class loader's {@linkplain ClassLoader#getUnnamedModule()
 * unnamed module}. The system class loader typically defines the class containing
 * the application {@code main} method too.
 *
 * <p> The classes visible to the agent class are the classes visible to the system
 * class loader and minimally include:
 *
 * <ul>
 *
 *   <li><p> The classes in packages exported by the modules in the {@linkplain
 *   ModuleLayer#boot() boot layer}. Whether the boot layer contains all platform
 *   modules or not will depend on the initial module or how the application was
 *   started. </p></li>
 *
 *   <li><p> The classes that can be defined by the system class loader (typically
 *   the class path) to be members of its unnamed module. </p></li>
 *
 *   <li><p> Any classes that the agent arranges to be defined by the bootstrap
 *   class loader to be members of its unnamed module. </p></li>
 *
 * </ul>
 *
 * <p> If agent classes need to link to classes in platform (or other) modules
 * that are not in the boot layer then the application may need to be started in
 * a way that ensures that these modules are in the boot layer. In the JDK
 * implementation for example, the {@code --add-modules} command line option can
 * be used to add modules to the set of root modules to resolve at startup. </p>
 *
 * <p> Supporting classes that the agent arranges to be loaded by the bootstrap
 * class loader (by means of {@link Instrumentation#appendToBootstrapClassLoaderSearch
 * appendToBootstrapClassLoaderSearch} or the {@code Boot-Class-Path} attribute
 * specified below), must link only to classes defined to the bootstrap class loader.
 * There is no guarantee that all platform classes can be defined by the boot
 * class loader.
 *
 * <p> If a custom system class loader is configured (by means of the system property
 * {@code java.system.class.loader} as specified in the {@link
 * ClassLoader#getSystemClassLoader() getSystemClassLoader} method) then it must
 * define the {@code appendToClassPathForInstrumentation} method as specified in
 * {@link Instrumentation#appendToSystemClassLoaderSearch appendToSystemClassLoaderSearch}.
 * In other words, a custom system class loader must support the mechanism to
 * add an agent JAR file to the system class loader search.
 *
 * <h2>JAR File Manifest Attributes</h2>
 *
 * <p> The following attributes in the main section of the application or agent
 * JAR file manifest are defined for Java agents:
 *
 * <blockquote><dl>
 *
 * <dt>{@code Launcher-Agent-Class}</dt>
 * <dd> If an implementation supports a mechanism to start an application in an
 * executable JAR file, then this attribute, if present, specifies the binary name
 * of the agent class that is packaged with the application.
 * The agent is started by invoking the agent class {@code agentmain} method. It is
 * invoked before the application {@code main} method is invoked. </dd>
 *
 * <dt>{@code Premain-Class}</dt>
 * <dd> If an agent JAR is specified at JVM launch time, this attribute specifies
 * the binary name of the agent class in the JAR file.
 * The agent is started by invoking the agent class {@code premain} method. It is
 * invoked before the application {@code main} method is invoked.
 * If the attribute is not present the JVM will abort. </dd>
 *
 * <dt>{@code Agent-Class}</dt>
 * <dd> If an implementation supports a mechanism to start an agent sometime after
 * the JVM has started, then this attribute specifies the binary name of the Java
 * agent class in the agent JAR file.
 * The agent is started by invoking the agent class {@code agentmain} method.
 * This attribute is required; if not present the agent will not be started. </dd>
 *
 * <dt>{@code Boot-Class-Path}</dt>
 * <dd> A list of paths to be searched by the bootstrap class loader. Paths
 * represent directories or libraries (commonly referred to as JAR or zip
 * libraries on many platforms). These paths are searched by the bootstrap class
 * loader after the platform specific mechanisms of locating a class have failed.
 * Paths are searched in the order listed. Paths in the list are separated by one
 * or more spaces. A path takes the syntax of the path component of a hierarchical
 * URI. The path is absolute if it begins with a slash character ('/'), otherwise
 * it is relative. A relative path is resolved against the absolute path of the
 * agent JAR file. Malformed and non-existent paths are ignored. When an agent is
 * started sometime after the JVM has started then paths that do not represent a
 * JAR file are ignored. This attribute is optional. </dd>
 *
 * <dt>{@code Can-Redefine-Classes}</dt>
 * <dd> Boolean ({@code true} or {@code false}, case irrelevant). Is the ability
 * to redefine classes needed by this agent. Values other than {@code true} are
 * considered {@code false}. This attribute is optional, the default is {@code
 * false}. </dd>
 *
 * <dt>{@code Can-Retransform-Classes}</dt>
 * <dd> Boolean ({@code true} or {@code false}, case irrelevant). Is the ability
 * to retransform classes needed by this agent. Values other than {@code true}
 * are considered {@code false}. This attribute is optional, the default is
 * {@code false}. </dd>
 *
 * <dt>{@code Can-Set-Native-Method-Prefix}</dt>
 * <dd> Boolean ({@code true} or {@code false}, case irrelevant). Is the ability
 * to set native method prefix needed by this agent. Values other than {@code
 * true} are considered {@code false}. This attribute is optional, the default
 * is {@code false}. </dd>
 *
 * </dl></blockquote>
 *
 * <p> An agent JAR file may have both the {@code Premain-Class} and {@code
 * Agent-Class} attributes present in the manifest. When the agent is started
 * on the command-line using the {@code -javaagent} option then the {@code
 * Premain-Class} attribute specifies the binary name of the agent class and the {@code
 * Agent-Class} attribute is ignored. Similarly, if the agent is started sometime
 * after the JVM has started, then the {@code Agent-Class} attribute specifies
 * the binary name of the agent class (the value of {@code Premain-Class} attribute is
 * ignored).
 *
 *
 * <h2>Instrumenting code in modules</h2>
 *
 * <p> As an aid to agents that deploy supporting classes on the search path of
 * the bootstrap class loader, or the search path of the class loader that loads
 * the main agent class, the Java virtual machine arranges for the module of
 * transformed classes to read the unnamed module of both class loaders.
 *
 * @since 1.5
 */

package java.lang.instrument;
