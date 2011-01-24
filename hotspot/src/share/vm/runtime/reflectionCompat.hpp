/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef SHARE_VM_RUNTIME_REFLECTIONCOMPAT_HPP
#define SHARE_VM_RUNTIME_REFLECTIONCOMPAT_HPP

// During the development of the JDK 1.4 reflection implementation
// based on dynamic bytecode generation, it was hoped that the bulk of
// the native code for reflection could be removed. Unfortunately
// there is currently a significant cost associated with loading the
// stub classes which impacts startup time. Until this cost can be
// reduced, the JVM entry points JVM_InvokeMethod and
// JVM_NewInstanceFromConstructor are still needed; these and their
// dependents currently constitute the bulk of the native code for
// reflection. If this cost is reduced in the future, the
// NativeMethodAccessorImpl and NativeConstructorAccessorImpl classes
// can be removed from sun.reflect and all of the code guarded by this
// flag removed from the product build. (Non-product builds,
// specifically the "optimized" target, would retain the code so they
// could be dropped into earlier JDKs for comparative benchmarking.)

//#ifndef PRODUCT
# define SUPPORT_OLD_REFLECTION
//#endif

#endif // SHARE_VM_RUNTIME_REFLECTIONCOMPAT_HPP
