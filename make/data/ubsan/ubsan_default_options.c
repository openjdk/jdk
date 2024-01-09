/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef UNDEFINED_BEHAVIOR_SANITIZER
#error "Build misconfigured, preprocessor macro UNDEFINED_BEHAVIOR_SANITIZER should be defined"
#endif

#ifndef __has_attribute
#define __has_attribute(x) 0
#endif

#if (defined(__GNUC__) && !defined(__clang__)) || __has_attribute(visibility)
#define ATTRIBUTE_DEFAULT_VISIBILITY __attribute__((visibility("default")))
#else
#define ATTRIBUTE_DEFAULT_VISIBILITY
#endif

#if (defined(__GNUC__) && !defined(__clang__)) || __has_attribute(used)
#define ATTRIBUTE_USED __attribute__((used))
#else
#define ATTRIBUTE_USED
#endif

// Override weak symbol exposed by UBSan to override default options. This is called by UBSan
// extremely early during library loading, before main is called. We need to override the default
// options because by default UBSan only prints a warning for each occurrence. We want jtreg tests
// to fail when undefined behavior is encountered. We also want a full stack trace for the offending
// thread so it is easier to track down. You can override these options by setting the environment
// variable UBSAN_OPTIONS.
ATTRIBUTE_DEFAULT_VISIBILITY ATTRIBUTE_USED const char* __ubsan_default_options() {
  return "halt_on_error=1,print_stacktrace=1";
}
