/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.template_framework;

/**
 * TODO
 *
 * - Extend library
 * - Pass replacements as arguments - need to convert to string - what about newline?
 *
 * - Convenience Classes:
 *   - Repeat test, maybe with set of values for parameters
 *   - Integrate with IR Framework
 *   - Wrap whole class in Template
 * - Easy generation of programmatic CodeGenerator
 *   - improve API for recursive calls, parameter checks/load, etc
 *
 * Tests:
 * - List of ops, test with any inputs
 * - Example test / library that generates random classes, generates objects, loads / stores fields
 *   - Good for: Valhalla, escape analysis, maybe type system, maybe method inlining etc.
 *
 *
 * Many test instantiator:
 * - static block
 * - main block
 * - test block
 * - parameter: test name
 * - for each parameter
 * - repeated adding - so different versions are generated
 */
public final class TemplateFramework {

}
