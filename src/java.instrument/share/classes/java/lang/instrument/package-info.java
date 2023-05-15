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
 * instrumentation is modification of the byte-codes of methods.
 *
 * <p> Agent classes are packaged in a JAR file. An agent can be <em>started</em>
 * in a number of ways:
 *
 * <ol>
 *   <li><p> At JVM Startup when named in the manifest of the application's executable
 *   JAR file.
 *
 *   <li><p> At JVM Startup with a command-line option.
 *
 *   <li><p> After JVM Startup where an implementation supports a mechanism to start
 *   agents some time after the JVM has started.
 * </ol>
 *
 * <p> Agents can transform classes in arbitrary ways at load time, transform
 * modules, or transform the bytecode of methods of already loaded classes.
 * Developers or administrators that deploy agents, deploy applications that
 * package an agent with the application, or use tools that load agents into a
 * running application, are responsible for verifying the trustworthiness of each
 * agent including the content and structure of the agent JAR file..
 *
 * <h2>Starting an Agent</h2>
 *
 * <h3>Starting an Agent named in the manifest of the application's executable JAR file </h3>
 *
 * <p> The <a href="{@docRoot}/../specs/jar/jar.html">JAR FileSpecification</a> defines
 * manifest attributes for standalone applications that are packaged as <em>executable
 * JAR files</em>. If an implementation supports a mechanism to start an application as
 * an executable JAR then the main manifest may include the {@code Launcher-Agent-Class}
 * attribute to specify the class name of an agent to start before the application
 * {@code main} method is invoked. The JVM attempts to load the agent class and invoke
 * the following method on the class:
 *
 * <blockquote>{@code
 *     public static void agentmain(String agentArgs, Instrumentation inst)
 * }</blockquote>
 *
 * <p> If the agent class does not implement this method then the JVM will
 * attempt to invoke:
 *
 * <blockquote>{@code
 *     public static void agentmain(String agentArgs)
 * }</blockquote>
 *
 * <p> The value of the {@code agentArgs} parameter is always the empty string.
 *
 * <p> The {@code agentmain} method should do any necessary initialization
 * required to start the agent and return. If the agent cannot be started, for
 * example the agent class cannot be loaded, the agent class does not define a
 * conformant {@code agentmain} method, or the {@code agentmain} method throws
 * an uncaught exception or error, the JVM will abort.
 *
 * <h3>Starting an Agent from the command-line interface</h3>
 *
 * <p> Where an implementation provides a means to start agents from the
 * command-line interface, an agent can be started by adding the following option
 * to the command-line:
 *
 * <blockquote>{@code
 *     -javaagent:<jarpath>[=<options>]
 * }</blockquote>
 *
 * where <i>{@code <jarpath>}</i> is the path to the agent JAR file and
 * <i>{@code <options>}</i> is the agent options.
 *
 * <p> The manifest of the agent JAR file must contain the attribute {@code
 * Premain-Class} in its main manifest. The value of this attribute is the
 * name of the <i>agent class</i>. The agent class must implement a public
 * static {@code premain} method similar in principle to the {@code main}
 * application entry point. After the Java Virtual Machine (JVM) has
 * initialized, the {@code premain} method will be called, then the real
 * application {@code main} method. The {@code premain} method must return
 * in order for the startup to proceed.
 *
 * <p> The {@code premain} method has one of two possible signatures. The
 * JVM first attempts to invoke the following method on the agent class:
 *
 * <blockquote>{@code
 *     public static void premain(String agentArgs, Instrumentation inst)
 * }</blockquote>
 *
 * <p> If the agent class does not implement this method then the JVM will
 * attempt to invoke:
 * <blockquote>{@code
 *     public static void premain(String agentArgs)
 * }</blockquote>

 * <p> The agent class may also have an {@code agentmain} method for use when
 * the agent is started after JVM startup (see below). When the agent is started
 * using a command-line option, the {@code agentmain} method is not invoked.
 *
 * <p> Each agent is passed its agent options via the {@code agentArgs} parameter.
 * The agent options are passed as a single string, any additional parsing
 * should be performed by the agent itself.
 *
 * <p> If the agent cannot be started (for example, because the agent class
 * cannot be loaded, or because the agent class does not have an appropriate
 * {@code premain} method), the JVM will abort. If a {@code premain} method
 * throws an uncaught exception, the JVM will abort.
 *
 * <p> An implementation is not required to provide a way to start agents
 * from the command-line interface. When it does, then it supports the
 * {@code -javaagent} option as specified above. The {@code -javaagent} option
 * may be used multiple times on the same command-line, thus starting multiple
 * agents. The {@code premain} methods will be called in the order that the
 * agents are specified on the command line. More than one agent may use the
 * same <i>{@code <jarpath>}</i>.
 *
 * <p> There are no modeling restrictions on what the agent {@code premain}
 * method may do. Anything application {@code main} can do, including creating
 * threads, is legal from {@code premain}.
 *
 *
 * <h3>Starting an Agent after JVM startup</h3>
 *
 * <p> An implementation may provide a mechanism to start agents sometime after
 * the JVM has started. The details as to how this is initiated are
 * implementation specific but typically the application has already started and
 * its {@code main} method has already been invoked. In cases where an
 * implementation supports starting an agent after the JVM has started, the
 * following applies:
 *
 * <ol>
 *
 *   <li><p> The manifest of the agent JAR must contain the attribute {@code
 *   Agent-Class} in its main manfiest. The value of this attribute is the name
 *   of the <i>agent class</i>. </p></li>
 *
 *   <li><p> The agent class must implement a public static {@code agentmain}
 *   method. </p></li>
 *
 *   <li><p> The JVM prints a warning on the standard error stream for each agent that
 *   the JVM attempts to start after the JVM has started. Warnings can be disabled by
 *   means of an implementation-specific command line option. </p></li>
 *
 * </ol>
 *
 * <p> The {@code agentmain} method has one of two possible signatures. The JVM
 * first attempts to invoke the following method on the agent class:
 *
 * <blockquote>{@code
 *     public static void agentmain(String agentArgs, Instrumentation inst)
 * }</blockquote>
 *
 * <p> If the agent class does not implement this method then the JVM will
 * attempt to invoke:
 *
 * <blockquote>{@code
 *     public static void agentmain(String agentArgs)
 * }</blockquote>
 *
 * <p> The agent class may also have a {@code premain} method for use when the
 * agent is started using a command-line option. When the agent is started after
 * JVM startup the {@code premain} method is not invoked.
 *
 * <p> The agent is passed its agent options via the {@code agentArgs}
 * parameter. The agent options are passed as a single string, any additional
 * parsing should be performed by the agent itself.
 *
 * <p> The {@code agentmain} method should do any necessary initialization
 * required to start the agent. When startup is complete the method should
 * return. If the agent cannot be started (for example, because the agent class
 * cannot be loaded, or because the agent class does not have a conformant
 * {@code agentmain} method), the JVM will not abort. If the {@code agentmain}
 * method throws an uncaught exception it will be ignored (but may be logged
 * by the JVM for troubleshooting purposes).
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
 * <h2>Manifest Attributes</h2>
 *
 * <p> The following manifest attributes are defined for an agent JAR file:
 *
 * <blockquote><dl>
 *
 * <dt>{@code Premain-Class}</dt>
 * <dd> When an agent is specified at JVM launch time this attribute specifies
 * the agent class. That is, the class containing the {@code premain} method.
 * When an agent is specified at JVM launch time this attribute is required. If
 * the attribute is not present the JVM will abort. Note: this is a class name,
 * not a file name or path. </dd>
 *
 * <dt>{@code Agent-Class}</dt>
 * <dd> If an implementation supports a mechanism to start agents sometime after
 * the JVM has started then this attribute specifies the agent class. That is,
 * the class containing the {@code agentmain} method. This attribute is required
 * if it is not present the agent will not be started. Note: this is a class name,
 * not a file name or path. </dd>
 *
 * <dt>{@code Launcher-Agent-Class}</dt>
 * <dd> If an implementation supports a mechanism to start an application as an
 * executable JAR then the main manifest may include this attribute to specify
 * the class name of an agent to start before the application {@code main}
 * method is invoked. </dd>
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
 * Premain-Class} attribute specifies the name of the agent class and the {@code
 * Agent-Class} attribute is ignored. Similarly, if the agent is started sometime
 * after the JVM has started, then the {@code Agent-Class} attribute specifies
 * the name of the agent class (the value of {@code Premain-Class} attribute is
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
 * @revised 1.6
 * @revised 9
 */

package java.lang.instrument;
