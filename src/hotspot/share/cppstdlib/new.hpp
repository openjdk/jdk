/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CPPSTDLIB_NEW_HPP
#define SHARE_CPPSTDLIB_NEW_HPP

#include "utilities/compilerWarnings.hpp"

// HotSpot usage:
// Only the following may be used:
// * std::nothrow_t, std::nothrow
// * std::align_val_t
// * The non-allocating forms of `operator new` and `operator new[]` are
//   implicitly used by the corresponding `new` and `new[]` expressions.
//   - operator new(size_t, void*) noexcept
//   - operator new[](size_t, void*) noexcept
//   Note that the non-allocating forms of `operator delete` and `operator
//   delete[]` are not used, since they are only invoked by a placement new
//   expression that fails by throwing an exception. But they might still
//   end up being referenced in such a situation.

BEGIN_ALLOW_FORBIDDEN_FUNCTIONS
#include "utilities/vmassert_uninstall.hpp"

#include <new>

#include "utilities/vmassert_reinstall.hpp" // don't reorder
END_ALLOW_FORBIDDEN_FUNCTIONS

// Deprecation declarations to forbid use of the default global allocator.
// See C++17 21.6.1 Header <new> synopsis.

namespace std {

#if 0
// We could deprecate exception types, for completeness, but don't bother.  We
// already have exceptions disabled, and run into compiler bugs when we try.
//
// gcc -Wattributes => type attributes ignored after type is already defined
// See https://gcc.gnu.org/bugzilla/show_bug.cgi?id=122167
//
// clang -Wignored-attributes => attribute declaration must precede definition
// The clang warning is https://github.com/llvm/llvm-project/issues/135481,
// which should be fixed in clang 21.
class [[deprecated]] bad_alloc;
class [[deprecated]] bad_array_new_length;
#endif // #if 0

// Forbid new_handler manipulation by HotSpot code, leaving it untouched for
// use by application code.
[[deprecated]] new_handler get_new_handler() noexcept;
[[deprecated]] new_handler set_new_handler(new_handler) noexcept;

// Prefer HotSpot mechanisms for padding.
//
// The syntax for redeclaring these for deprecation is tricky, and not
// supported by some versions of some compilers.  Dispatch on compiler and
// version to decide whether to redeclare deprecated.

#if defined(__clang__)
#if __clang_major__ >= 19
// clang18 and earlier may accept the declaration but go wrong with uses.
// Different warnings and link-time failures are both possible.
#define CAN_DEPRECATE_HARDWARE_INTERFERENCE_SIZES 1
#endif // restrict clang version

#elif defined(__GNUC__)
#if (__GNUC__ > 13) || (__GNUC__ == 13 && __GNUC_MINOR__ >= 2)
// g++11.5 accepts the declaration and reports deprecation for uses, but also
// has link-time failure for uses. Haven't tested intermediate versions.
#define CAN_DEPRECATE_HARDWARE_INTERFERENCE_SIZES 1
#endif // restrict gcc version

#elif defined(_MSVC)
// VS2022-17.13.2 => error C2370: '...': redefinition; different storage class

#endif // Compiler dispatch

// Redeclare deprecated if such is supported.
#ifdef CAN_DEPRECATE_HARDWARE_INTERFERENCE_SIZES
[[deprecated]] extern const size_t hardware_destructive_interference_size;
[[deprecated]] extern const size_t hardware_constructive_interference_size;
#undef CAN_DEPRECATE_HARDWARE_INTERFERENCE_SIZES
#endif // CAN_DEPRECATE_HARDWARE_INTERFERENCE_SIZES

} // namespace std

// Forbid using the global allocator by HotSpot code.
// This doesn't provide complete coverage. Some global allocation and
// deallocation functions are implicitly declared in all translation units,
// without needing to include <new>; see C++17 6.7.4. So this doesn't remove
// the need for the link-time verification that these functions aren't used.
//
// But don't poison them when compiling gtests. The gtest framework, the
// HotSpot wrapper around it (gtestMain.cpp), and even some tests, all have
// new/new[] and delete/delete[] expressions that use the default global
// allocator.  We also don't apply the link-time check for gtests, for the
// same reason.
#ifndef HOTSPOT_GTEST

[[deprecated]] void* operator new(std::size_t);
[[deprecated]] void* operator new(std::size_t, std::align_val_t);
[[deprecated]] void* operator new(std::size_t, const std::nothrow_t&) noexcept;
[[deprecated]] void* operator new(std::size_t, std::align_val_t,
                                  const std::nothrow_t&) noexcept;

[[deprecated]] void operator delete(void*) noexcept;
[[deprecated]] void operator delete(void*, std::size_t) noexcept;
[[deprecated]] void operator delete(void*, std::align_val_t) noexcept;
[[deprecated]] void operator delete(void*, std::size_t, std::align_val_t) noexcept;
[[deprecated]] void operator delete(void*, const std::nothrow_t&) noexcept;
[[deprecated]] void operator delete(void*, std::align_val_t,
                                    const std::nothrow_t&) noexcept;

[[deprecated]] void* operator new[](std::size_t);
[[deprecated]] void* operator new[](std::size_t, std::align_val_t);
[[deprecated]] void* operator new[](std::size_t, const std::nothrow_t&) noexcept;
[[deprecated]] void* operator new[](std::size_t, std::align_val_t,
                                    const std::nothrow_t&) noexcept;

[[deprecated]] void operator delete[](void*) noexcept;
[[deprecated]] void operator delete[](void*, std::size_t) noexcept;
[[deprecated]] void operator delete[](void*, std::align_val_t) noexcept;
[[deprecated]] void operator delete[](void*, std::size_t, std::align_val_t) noexcept;
[[deprecated]] void operator delete[](void*, const std::nothrow_t&) noexcept;
[[deprecated]] void operator delete[](void*, std::align_val_t,
                                      const std::nothrow_t&) noexcept;

#endif // HOTSPOT_GTEST

// Allow (don't poison) the non-allocating forms from [new.delete.placement].

#endif // SHARE_CPPSTDLIB_NEW_HPP
