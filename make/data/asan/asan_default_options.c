/*
 * Copyright (c) 2023, Google and/or its affiliates. All rights reserved.
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

#ifndef ADDRESS_SANITIZER
#error "Build misconfigured, preprocessor macro ADDRESS_SANITIZER should be defined"
#endif

#ifndef __has_attribute
#define __has_attribute(x) 0
#endif

#if (defined(__GNUC__) && !defined(__clang__)) || __has_attribute(visibility)
#define ATTRIBUTE_DEFAULT_VISIBILITY __attribute__((visibility("default")))
#elif defined(_MSC_VER)
#define ATTRIBUTE_DEFAULT_VISIBILITY __declspec(dllexport)
#else
#define ATTRIBUTE_DEFAULT_VISIBILITY
#endif

#if (defined(__GNUC__) && !defined(__clang__)) || __has_attribute(used)
#define ATTRIBUTE_USED __attribute__((used))
#else
#define ATTRIBUTE_USED
#endif

#if defined(_MSC_VER)
#define CDECL __cdecl
#else
#define CDECL
#endif

// Override weak symbol exposed by ASan to override default options. This is called by ASan
// extremely early during library loading, before main is called. We need to override the default
// options because LSan is enabled by default and Hotspot is not yet compatible with it.
// Additionally we need to prevent ASan from handling SIGSEGV, so that Hotspot's crash handler is
// used. You can override these options by setting the environment variable ASAN_OPTIONS.
ATTRIBUTE_DEFAULT_VISIBILITY ATTRIBUTE_USED const char* CDECL __asan_default_options() {
  return
#ifdef LEAK_SANITIZER
    "leak_check_at_exit=0,"
#else
    // ASan bundles LSan, however we only support LSan when it is explicitly requested during
    // configuration. Thus we disable it to match if it was not requested.
    "detect_leaks=0,"
#endif
    "print_suppressions=0,"
    "handle_segv=0,"
    // A lot of libjsig related tests fail because of the link order check; so better avoid it
    "verify_asan_link_order=0,"
    // See https://github.com/google/sanitizers/issues/1322. Hopefully this is resolved
    // at some point and we can remove this option.
    "intercept_tls_get_addr=0";
}
