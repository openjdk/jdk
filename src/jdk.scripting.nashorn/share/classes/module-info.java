/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * Provides the implementation of Nashorn script engine and
 * the runtime environment for programs written in ECMAScript 5.1.
 * <p>
 * Nashorn is a runtime environment for programs written in ECMAScript 5.1.
 * </p>
 *
 * <h2>Usage</h2>
 *
 * The recommended way to use Nashorn is through the
 * <a href="http://jcp.org/en/jsr/detail?id=223" target="_top">JSR-223
 * "Scripting for the Java Platform"</a> APIs found in the
 * {@link javax.script} package. Usually, you'll obtain a
 * {@link javax.script.ScriptEngine} instance for Nashorn using:
 * <pre>
import javax.script.*;
...
ScriptEngine nashornEngine = new ScriptEngineManager().getEngineByName("nashorn");
</pre>
 *
 * and then use it just as you would any other JSR-223 script engine. See
 * {@link jdk.nashorn.api.scripting} package for details.
 * <h2>Compatibility</h2>
 * Nashorn is 100% compliant with the
 * <a href="http://www.ecma-international.org/publications/standards/Ecma-262.htm"
 * target="_top">ECMA-262 Standard, Edition 5.1</a>.
 * It requires a Java Virtual Machine that implements the
 * <a href="http://jcp.org/en/jsr/detail?id=292" target="_top">
 * JSR-292 "Supporting Dynamically Typed Languages on the Java Platform"</a>
 * specification (often referred to as "invokedynamic"), as well as
 * the already mentioned JSR-223.
 *
 * <h2>Interoperability with the Java platform</h2>
 *
 * In addition to being a 100% ECMAScript 5.1 runtime, Nashorn provides features
 * for interoperability of the ECMAScript programs with the Java platform.
 * In general, any Java object put into the script engine's context will be
 * visible from the script. In terms of the standard, such Java objects are not
 * considered "native objects", but rather "host objects", as defined in
 * section 4.3.8. This distinction allows certain semantical differences
 * in handling them compared to native objects. For most purposes, Java objects
 * behave just as native objects do: you can invoke their methods, get and set
 * their properties. In most cases, though, you can't add arbitrary properties
 * to them, nor can you remove existing properties.
 *
 * <h3>Java collection handling</h3>
 *
 * Native Java arrays and {@link java.util.List}s support indexed access to
 * their elements through the property accessors, and {@link java.util.Map}s
 * support both property and element access through both dot and square-bracket
 * property accessors, with the difference being that dot operator gives
 * precedence to object properties (its fields and properties defined as
 * {@code getXxx} and {@code setXxx} methods) while the square bracket
 * operator gives precedence to map elements. Native Java arrays expose
 * the {@code length} property.
 *
 * <h3>ECMAScript primitive types</h3>
 *
 * ECMAScript primitive types for number, string, and boolean are represented
 * with {@link java.lang.Number}, {@link java.lang.CharSequence}, and
 * {@link java.lang.Boolean} objects. While the most often used number type
 * is {@link java.lang.Double} and the most often used string type is
 * {@link java.lang.String}, don't rely on it as various internal optimizations
 * cause other subclasses of {@code Number} and internal implementations of
 * {@code CharSequence} to be used.
 *
 * <h3>Type conversions</h3>
 *
 * When a method on a Java object is invoked, the arguments are converted to
 * the formal parameter types of the Java method using all allowed ECMAScript
 * conversions. This can be surprising, as in general, conversions from string
 * to number will succeed according to Standard's section 9.3 "ToNumber"
 * and so on; string to boolean, number to boolean, Object to number,
 * Object to string all work. Note that if the Java method's declared parameter
 * type is {@code java.lang.Object}, Nashorn objects are passed without any
 * conversion whatsoever; specifically if the JavaScript value being passed
 * is of primitive string type, you can only rely on it being a
 * {@code java.lang.CharSequence}, and if the value is a number, you can only
 * rely on it being a {@code java.lang.Number}. If the Java method declared
 * parameter type is more specific (e.g. {@code java.lang.String} or
 * {@code java.lang.Double}), then Nashorn will of course ensure
 * the required type is passed.
 *
 * <h3>SAM types</h3>
 *
 * As a special extension when invoking Java methods, ECMAScript function
 * objects can be passed in place of an argument whose Java type is so-called
 * "single abstract method" or "SAM" type. While this name usually covers
 * single-method interfaces, Nashorn is a bit more versatile, and it
 * recognizes a type as a SAM type if all its abstract methods are
 * overloads of the same name, and it is either an interface, or it is an
 * abstract class with a no-arg constructor. The type itself must be public,
 * while the constructor and the methods can be either public or protected.
 * If there are multiple abstract overloads of the same name, the single
 * function will serve as the shared implementation for all of them,
 * <em>and additionally it will also override any non-abstract methods of
 * the same name</em>. This is done to be consistent with the fact that
 * ECMAScript does not have the concept of overloaded methods.
 *
 * <h3>The {@code Java} object</h3>
 *
 * Nashorn exposes a non-standard global object named {@code Java} that is
 * the primary API entry point into Java platform-specific functionality.
 * You can use it to create instances of Java classes, convert from Java arrays
 * to native arrays and back, and so on.
 *
 * <h3>Other non-standard built-in objects</h3>
 *
 * In addition to {@code Java}, Nashorn also exposes some other
 * non-standard built-in objects:
 * {@code JSAdapter}, {@code JavaImporter}, {@code Packages}
 *
 * @deprecated Nashorn JavaScript script engine and APIs, and the jjs tool
 * are deprecated with the intent to remove them in a future release.
 *
 * @provides javax.script.ScriptEngineFactory
 * @moduleGraph
 * @since 9
 */
@Deprecated(since="11", forRemoval=true)
module jdk.scripting.nashorn {
    requires java.logging;
    requires jdk.dynalink;

    requires transitive java.scripting;

    exports jdk.nashorn.api.scripting;
    exports jdk.nashorn.api.tree;

    exports jdk.nashorn.internal.runtime to
        jdk.scripting.nashorn.shell;
    exports jdk.nashorn.internal.objects to
        jdk.scripting.nashorn.shell;
    exports jdk.nashorn.tools to
        jdk.scripting.nashorn.shell;

    provides javax.script.ScriptEngineFactory with
        jdk.nashorn.api.scripting.NashornScriptEngineFactory;

    provides jdk.dynalink.linker.GuardingDynamicLinkerExporter with
        jdk.nashorn.api.linker.NashornLinkerExporter;
}
