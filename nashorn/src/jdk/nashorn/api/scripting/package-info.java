/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * This package provides the {@code javax.script} integration, which is the preferred way to use Nashorn.
 * You will ordinarily do this to obtain an instance of a Nashorn script engine:
 * <pre>
 * import javax.script.*;
 * ...
 * ScriptEngine nashornEngine = new ScriptEngineManager().getEngineByName("Nashorn");
 * </pre>
 * <p>Nashorn script engines implement the optional {@link javax.script.Invocable} and {@link javax.script.Compilable}
 * interfaces, allowing for efficient pre-compilation and repeated execution of scripts. In addition,
 * this package provides nashorn specific extension classes, interfaces and methods. See
 * {@link jdk.nashorn.api.scripting.NashornScriptEngineFactory} for further details.
 */
package jdk.nashorn.api.scripting;
