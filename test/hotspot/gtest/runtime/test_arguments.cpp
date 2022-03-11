/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "jvm.h"
#include "unittest.hpp"
#include "runtime/arguments.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"

class ArgumentsTest : public ::testing::Test {
public:
  static intx parse_xss_inner_annotated(const char* str, jint expected_err, const char* file, int line_number);

  // Expose the private Arguments functions.

  static Arguments::ArgsRange check_memory_size(julong size, julong min_size, julong max_size) {
    return Arguments::check_memory_size(size, min_size, max_size);
  }

  static jint parse_xss(const JavaVMOption* option, const char* tail, intx* out_ThreadStackSize) {
    return Arguments::parse_xss(option, tail, out_ThreadStackSize);
  }

  static bool parse_argument(const char* name, const char* value) {
    char buf[1024];
    int ret = jio_snprintf(buf, sizeof(buf), "%s=%s", name, value);
    if (ret > 0) {
      return Arguments::parse_argument(buf, JVMFlagOrigin::COMMAND_LINE);
    } else {
      return false;
    }
  }
};

TEST_F(ArgumentsTest, atojulong) {
  char ullong_max[32];
  int ret = jio_snprintf(ullong_max, sizeof(ullong_max), JULONG_FORMAT, ULLONG_MAX);
  ASSERT_NE(-1, ret);

  julong value;
  const char* invalid_strings[] = {
    "", "-1", "-100", " 1", "2 ", "3 2", "1.0",
    "0x4.5", "0x", "0x0x1" "0.001", "4e10", "e"
    "K", "M", "G", "1MB", "1KM", "AA", "0B",
    "18446744073709551615K", "17179869184G",
    "999999999999999999999999999999"
  };
  for (uint i = 0; i < ARRAY_SIZE(invalid_strings); i++) {
    ASSERT_FALSE(Arguments::atojulong(invalid_strings[i], &value))
        << "Invalid string '" << invalid_strings[i] << "' parsed without error.";
  }

  struct {
    const char* str;
    julong expected_value;
  } valid_strings[] = {
      { "0", 0 },
      { "4711", 4711 },
      { "1K", 1ULL * K },
      { "1k", 1ULL * K },
      { "2M", 2ULL * M },
      { "2m", 2ULL * M },
      { "4G", 4ULL * G },
      { "4g", 4ULL * G },
      { "0K", 0 },
      { ullong_max, ULLONG_MAX },
      { "0xcafebabe", 0xcafebabe },
      { "0XCAFEBABE", 0xcafebabe },
      { "0XCAFEbabe", 0xcafebabe },
      { "0x10K", 0x10 * K }
  };
  for (uint i = 0; i < ARRAY_SIZE(valid_strings); i++) {
    ASSERT_TRUE(Arguments::atojulong(valid_strings[i].str, &value))
        << "Valid string '" << valid_strings[i].str << "' did not parse.";
    ASSERT_EQ(valid_strings[i].expected_value, value);
  }
}

TEST_F(ArgumentsTest, check_memory_size__min) {
  EXPECT_EQ(check_memory_size(999,  1000, max_uintx), Arguments::arg_too_small);
  EXPECT_EQ(check_memory_size(1000, 1000, max_uintx), Arguments::arg_in_range);
  EXPECT_EQ(check_memory_size(1001, 1000, max_uintx), Arguments::arg_in_range);

  EXPECT_EQ(check_memory_size(max_intx - 2, max_intx - 1, max_uintx), Arguments::arg_too_small);
  EXPECT_EQ(check_memory_size(max_intx - 1, max_intx - 1, max_uintx), Arguments::arg_in_range);
  EXPECT_EQ(check_memory_size(max_intx - 0, max_intx - 1, max_uintx), Arguments::arg_in_range);

  EXPECT_EQ(check_memory_size(max_intx - 1, max_intx, max_uintx), Arguments::arg_too_small);
  EXPECT_EQ(check_memory_size(max_intx    , max_intx, max_uintx), Arguments::arg_in_range);

  NOT_LP64(
    EXPECT_EQ(check_memory_size((julong)max_intx + 1, max_intx, max_uintx), Arguments::arg_in_range);

    EXPECT_EQ(check_memory_size(        max_intx - 1, (julong)max_intx + 1, max_uintx), Arguments::arg_too_small);
    EXPECT_EQ(check_memory_size(        max_intx    , (julong)max_intx + 1, max_uintx), Arguments::arg_too_small);
    EXPECT_EQ(check_memory_size((julong)max_intx + 1, (julong)max_intx + 1, max_uintx), Arguments::arg_in_range);
    EXPECT_EQ(check_memory_size((julong)max_intx + 2, (julong)max_intx + 1, max_uintx), Arguments::arg_in_range);
  );

  EXPECT_EQ(check_memory_size(max_uintx - 2, max_uintx - 1, max_uintx), Arguments::arg_too_small);
  EXPECT_EQ(check_memory_size(max_uintx - 1, max_uintx - 1, max_uintx), Arguments::arg_in_range);
  EXPECT_EQ(check_memory_size(max_uintx    , max_uintx - 1, max_uintx), Arguments::arg_in_range);

  EXPECT_EQ(check_memory_size(max_uintx - 1, max_uintx, max_uintx), Arguments::arg_too_small);
  EXPECT_EQ(check_memory_size(max_uintx    , max_uintx, max_uintx), Arguments::arg_in_range);
}

TEST_F(ArgumentsTest, check_memory_size__max) {
  EXPECT_EQ(check_memory_size(max_uintx - 1, 1000, max_uintx), Arguments::arg_in_range);
  EXPECT_EQ(check_memory_size(max_uintx    , 1000, max_uintx), Arguments::arg_in_range);

  EXPECT_EQ(check_memory_size(max_intx - 2     , 1000, max_intx - 1), Arguments::arg_in_range);
  EXPECT_EQ(check_memory_size(max_intx - 1     , 1000, max_intx - 1), Arguments::arg_in_range);
  EXPECT_EQ(check_memory_size(max_intx         , 1000, max_intx - 1), Arguments::arg_too_big);

  EXPECT_EQ(check_memory_size(max_intx - 1     , 1000, max_intx), Arguments::arg_in_range);
  EXPECT_EQ(check_memory_size(max_intx         , 1000, max_intx), Arguments::arg_in_range);

  NOT_LP64(
    EXPECT_EQ(check_memory_size((julong)max_intx + 1     , 1000, max_intx), Arguments::arg_too_big);

    EXPECT_EQ(check_memory_size(        max_intx         , 1000, (julong)max_intx + 1), Arguments::arg_in_range);
    EXPECT_EQ(check_memory_size((julong)max_intx + 1     , 1000, (julong)max_intx + 1), Arguments::arg_in_range);
    EXPECT_EQ(check_memory_size((julong)max_intx + 2     , 1000, (julong)max_intx + 1), Arguments::arg_too_big);
 );
}

// A random value - used to verify the output when parsing is expected to fail.
static const intx no_value = 4711;

inline intx ArgumentsTest::parse_xss_inner_annotated(const char* str, jint expected_err, const char* file, int line_number) {
  intx value = no_value;
  jint err = parse_xss(NULL /* Silence error messages */, str, &value);
  EXPECT_EQ(err, expected_err) << "Failure from: " << file << ":" << line_number;
  return value;
}

// Wrapper around the help function - gives file and line number when a test failure occurs.
#define parse_xss_inner(str, expected_err) ArgumentsTest::parse_xss_inner_annotated(str, expected_err, __FILE__, __LINE__)

static intx calc_expected(julong small_xss_input) {
  assert(small_xss_input <= max_julong / 2, "Sanity");

  // Match code in arguments.cpp
  julong julong_ret = align_up(small_xss_input, K) / K;
  assert(julong_ret <= (julong)max_intx, "Overflow: " JULONG_FORMAT, julong_ret);
  return (intx)julong_ret;
}

static char buff[100];
static char* to_string(julong value) {
  jio_snprintf(buff, sizeof(buff), JULONG_FORMAT, value);
  return buff;
}

TEST_VM_F(ArgumentsTest, parse_xss) {
  // Test the maximum input value - should fail.
  {
    EXPECT_EQ(parse_xss_inner(to_string(max_julong), JNI_EINVAL), no_value);
    NOT_LP64(EXPECT_EQ(parse_xss_inner(to_string(max_uintx), JNI_EINVAL), no_value));
  }

  // Test values "far" away from the uintx boundary,
  // but still beyond the max limit.
  {
    LP64_ONLY(EXPECT_EQ(parse_xss_inner(to_string(max_julong / 2), JNI_EINVAL), no_value));
    EXPECT_EQ(parse_xss_inner(to_string(INT_MAX),     JNI_EINVAL), no_value);
  }

  // Test at and around the max limit.
  {
    EXPECT_EQ(parse_xss_inner(to_string(1 * M * K - 1), JNI_OK), calc_expected(1 * M * K - 1));
    EXPECT_EQ(parse_xss_inner(to_string(1 * M * K),     JNI_OK), calc_expected(1 * M * K));
    EXPECT_EQ(parse_xss_inner(to_string(1 * M * K + 1), JNI_EINVAL), no_value);
  }

  // Test value aligned both to K and vm_page_size.
  {
    EXPECT_TRUE(is_aligned(32 * M, K));
    EXPECT_TRUE(is_aligned(32 * M, (size_t)os::vm_page_size()));
    EXPECT_EQ(parse_xss_inner(to_string(32 * M), JNI_OK), (intx)(32 * M / K));
  }

  // Test around the min limit.
  {
    EXPECT_EQ(parse_xss_inner(to_string(0),     JNI_OK), calc_expected(0));
    EXPECT_EQ(parse_xss_inner(to_string(1),     JNI_OK), calc_expected(1));
    EXPECT_EQ(parse_xss_inner(to_string(K - 1), JNI_OK), calc_expected(K - 1));
    EXPECT_EQ(parse_xss_inner(to_string(K),     JNI_OK), calc_expected(K));
    EXPECT_EQ(parse_xss_inner(to_string(K + 1), JNI_OK), calc_expected(K + 1));
  }
}

struct Dummy {};
static Dummy BAD;

template <typename T>
struct NumericArgument {
  bool bad;
  const char* str;
  T expected_value;

  NumericArgument(const char* s, T v) :           bad(false), str(s), expected_value(v) {}
  NumericArgument(const char* s, Dummy & dummy) : bad(true),  str(s), expected_value(0) {}
};

static void check_invalid_numeric_string(JVMFlag* flag,  const char** invalid_strings) {
  for (uint i = 0; ; i++) {
    const char* str = invalid_strings[i];
    if (str == NULL) {
      return;
    }
    ASSERT_FALSE(ArgumentsTest::parse_argument(flag->name(), str))
        << "Invalid string '" << str
        << "' parsed without error for type " << flag->type_string() << ".";
  }
}

template <typename T>
void check_numeric_flag(JVMFlag* flag, T getvalue(JVMFlag* flag),
                        NumericArgument<T>* valid_args, size_t n,
                        bool is_double = false) {
  for (size_t i = 0; i < n; i++) {
    NumericArgument<T>* info = &valid_args[i];
    const char* str = info->str;
    if (info->bad) {
      ASSERT_FALSE(ArgumentsTest::parse_argument(flag->name(), str))
        << "Invalid string '" << str
        << "' parsed without error for type " << flag->type_string() << ".";
    } else {
      ASSERT_TRUE(ArgumentsTest::parse_argument(flag->name(), str))
        << "Valid string '" <<
        str << "' did not parse for type " << flag->type_string() << ".";
      ASSERT_EQ(getvalue(flag), info->expected_value)
        << "Valid string '" << str
        << "' did not parse to the correct value for type "
        << flag->type_string() << ".";
    }
  }

  {
    // Invalid strings for *any* type of integer VM arguments
    const char* invalid_strings[] = {
      "", " 1", "2 ", "3 2",
      "0x", "0x0x1" "e"
      "K", "M", "G", "1MB", "1KM", "AA", "0B",
      "18446744073709551615K", "17179869184G",
      "999999999999999999999999999999",
      "0x10000000000000000", "18446744073709551616",
      "-0x10000000000000000", "-18446744073709551616",
      "-0x8000000000000001", "-9223372036854775809",
      "0x8000000t", "0x800000000g",
      "0x800000000000m", "0x800000000000000k",
      "-0x8000000t", "-0x800000000g",
      "-0x800000000000m", "-0x800000000000000k",
      NULL,
    };
    check_invalid_numeric_string(flag, invalid_strings);
  }

  if (!is_double) {
    const char* invalid_strings_for_integers[] = {
      "1.0", "0x4.5", "0.001", "4e10",
      NULL,
    };
    check_invalid_numeric_string(flag, invalid_strings_for_integers);
  }
}

#define INTEGER_TEST_TABLE(f) \
  /*input                      i32           u32           i64                      u64 */ \
  f("0",                       0,            0,            0,                       0                        ) \
  f("-0",                      0,            BAD,          0,                       BAD                      ) \
  f("-1",                     -1,            BAD,         -1,                       BAD                      ) \
  f("0x1",                     1,            1,            1,                       1                        ) \
  f("-0x1",                   -1,            BAD,         -1,                       BAD                      ) \
  f("4711",                    4711,         4711,         4711,                    4711                     ) \
  f("1K",                      1024,         1024,         1024,                    1024                     ) \
  f("1k",                      1024,         1024,         1024,                    1024                     ) \
  f("2M",                      2097152,      2097152,      2097152,                 2097152                  ) \
  f("2m",                      2097152,      2097152,      2097152,                 2097152                  ) \
  f("1G",                      1073741824,   1073741824,   1073741824,              1073741824               ) \
  f("2G",                      BAD,          0x80000000,   2147483648LL,            2147483648ULL            ) \
  f("1T",                      BAD,          BAD,          1099511627776LL,         1099511627776ULL         ) \
  f("1t",                      BAD,          BAD,          1099511627776LL,         1099511627776ULL         ) \
  f("-1K",                    -1024,         BAD,         -1024,                    BAD                      ) \
  f("0x1K",                    1024,         1024,         1024,                    1024                     ) \
  f("-0x1K",                  -1024,         BAD,         -1024,                    BAD                      ) \
  f("0K",                      0,            0,            0,                       0                        ) \
  f("0x1000000k",              BAD,          BAD,          17179869184LL,           17179869184ULL           ) \
  f("0x800000m",               BAD,          BAD,          0x80000000000LL,         0x80000000000ULL         ) \
  f("0x8000g",                 BAD,          BAD,          0x200000000000LL,        0x200000000000ULL        ) \
  f("0x8000t",                 BAD,          BAD,          0x80000000000000LL,      0x80000000000000ULL      ) \
  f("-0x1000000k",             BAD,          BAD,         -17179869184LL,           BAD                      ) \
  f("-0x800000m",              BAD,          BAD,         -0x80000000000LL,         BAD                      ) \
  f("-0x8000g",                BAD,          BAD,         -0x200000000000LL,        BAD                      ) \
  f("-0x8000t",                BAD,          BAD,         -0x80000000000000LL,      BAD                      ) \
  f("0x7fffffff",              0x7fffffff,   0x7fffffff,   0x7fffffff,              0x7fffffff               ) \
  f("0xffffffff",              BAD,          0xffffffff,   0xffffffff,              0xffffffff               ) \
  f("0x80000000",              BAD,          0x80000000,   0x80000000,              0x80000000               ) \
  f("-0x7fffffff",            -2147483647,   BAD,         -2147483647LL,            BAD                      ) \
  f("-0x80000000",            -2147483648,   BAD,         -2147483648LL,            BAD                      ) \
  f("-0x80000001",             BAD,          BAD,         -2147483649LL,            BAD                      ) \
  f("0x100000000",             BAD,          BAD,          0x100000000LL,           0x100000000ULL           ) \
  f("0xcafebabe",              BAD,          0xcafebabe,   0xcafebabe,              0xcafebabe               ) \
  f("0XCAFEBABE",              BAD,          0xcafebabe,   0xcafebabe,              0xcafebabe               ) \
  f("0XCAFEbabe",              BAD,          0xcafebabe,   0xcafebabe,              0xcafebabe               ) \
  f("0xcafebabe1",             BAD,          BAD,          0xcafebabe1,             0xcafebabe1              ) \
  f("0x7fffffffffffffff",      BAD,          BAD,          max_jlong,               9223372036854775807ULL   ) \
  f("0x8000000000000000",      BAD,          BAD,          BAD,                     9223372036854775808ULL   ) \
  f("0xffffffffffffffff",      BAD,          BAD,          BAD,                     max_julong               ) \
  f("9223372036854775807",     BAD,          BAD,          9223372036854775807LL,   9223372036854775807ULL   ) \
  f("9223372036854775808",     BAD,          BAD,          BAD,                     9223372036854775808ULL   ) \
  f("-9223372036854775808",    BAD,          BAD,          min_jlong,               BAD                      ) \
  f("18446744073709551615",    BAD,          BAD,          BAD,                     max_julong               ) \
                                                                                                               \
  /* All edge cases without a k/m/g/t suffix */                                                                \
  f("0x7ffffffe",              max_jint-1,   0x7ffffffe,   0x7ffffffeLL,            0x7ffffffeULL            ) \
  f("0x7fffffff",              max_jint,     0x7fffffff,   0x7fffffffLL,            0x7fffffffULL            ) \
  f("0x80000000",              BAD,          0x80000000,   0x80000000LL,            0x80000000ULL            ) \
  f("0xfffffffe",              BAD,          max_juint-1,  0xfffffffeLL,            0xfffffffeULL            ) \
  f("0xffffffff",              BAD,          max_juint,    0xffffffffLL,            0xffffffffULL            ) \
  f("0x100000000",             BAD,          BAD,          0x100000000LL,           0x100000000ULL           ) \
  f("-0x7fffffff",             min_jint+1,   BAD,         -0x7fffffffLL,            BAD                      ) \
  f("-0x80000000",             min_jint,     BAD,         -0x80000000LL,            BAD                      ) \
  f("-0x80000001",             BAD,          BAD,         -0x80000001LL,            BAD                      ) \
                                                                                                               \
  f("0x7ffffffffffffffe",      BAD,          BAD,          max_jlong-1,             0x7ffffffffffffffeULL    ) \
  f("0x7fffffffffffffff",      BAD,          BAD,          max_jlong,               0x7fffffffffffffffULL    ) \
  f("0x8000000000000000",      BAD,          BAD,          BAD,                     0x8000000000000000ULL    ) \
  f("0xfffffffffffffffe",      BAD,          BAD,          BAD,                     max_julong-1             ) \
  f("0xffffffffffffffff",      BAD,          BAD,          BAD,                     max_julong               ) \
  f("0x10000000000000000",     BAD,          BAD,          BAD,                     BAD                      ) \
  f("-0x7fffffffffffffff",     BAD,          BAD,          min_jlong+1,             BAD                      ) \
  f("-0x8000000000000000",     BAD,          BAD,          min_jlong,               BAD                      ) \
  f("-0x8000000000000001",     BAD,          BAD,          BAD,                     BAD                      ) \
                                                                                                               \
  /* edge cases for suffix: K */                                                                               \
  f("0x1ffffek",               0x1ffffe * k, 0x1ffffeU * k,0x1ffffeLL * k,          0x1ffffeULL * k          ) \
  f("0x1fffffk",               0x1fffff * k, 0x1fffffU * k,0x1fffffLL * k,          0x1fffffULL * k          ) \
  f("0x200000k",               BAD,          0x200000U * k,0x200000LL * k,          0x200000ULL * k          ) \
  f("0x3ffffek",               BAD,          0x3ffffeU * k,0x3ffffeLL * k,          0x3ffffeULL * k          ) \
  f("0x3fffffk",               BAD,          0x3fffffU * k,0x3fffffLL * k,          0x3fffffULL * k          ) \
  f("0x400000k",               BAD,          BAD,          0x400000LL * k,          0x400000ULL * k          ) \
  f("-0x1fffffk",             -0x1fffff * k, BAD,         -0x1fffffLL * k,          BAD                      ) \
  f("-0x200000k",             -0x200000 * k, BAD,         -0x200000LL * k,          BAD                      ) \
  f("-0x200001k",              BAD,          BAD,         -0x200001LL * k,          BAD                      ) \
                                                                                                               \
  f("0x1ffffffffffffek",       BAD,          BAD,          0x1ffffffffffffeLL * k,  0x1ffffffffffffeULL * k  ) \
  f("0x1fffffffffffffk",       BAD,          BAD,          0x1fffffffffffffLL * k,  0x1fffffffffffffULL * k  ) \
  f("0x20000000000000k",       BAD,          BAD,          BAD,                     0x20000000000000ULL * k  ) \
  f("0x3ffffffffffffek",       BAD,          BAD,          BAD,                     0x3ffffffffffffeULL * k  ) \
  f("0x3fffffffffffffk",       BAD,          BAD,          BAD,                     0x3fffffffffffffULL * k  ) \
  f("0x40000000000000k",       BAD,          BAD,          BAD,                     BAD                      ) \
  f("-0x1fffffffffffffk",      BAD,          BAD,         -0x1fffffffffffffLL * k,  BAD                      ) \
  f("-0x20000000000000k",      BAD,          BAD,         -0x20000000000000LL * k,  BAD                      ) \
  f("-0x20000000000001k",      BAD,          BAD,          BAD,                     BAD                      ) \
                                                                                                               \
  /* edge cases for suffix: M */                                                                               \
  f("0x7fem",                  0x7fe * m,    0x7feU * m,   0x7feLL * m,             0x7feULL * m             ) \
  f("0x7ffm",                  0x7ff * m,    0x7ffU * m,   0x7ffLL * m,             0x7ffULL * m             ) \
  f("0x800m",                  BAD,          0x800U * m,   0x800LL * m,             0x800ULL * m             ) \
  f("0xffem",                  BAD,          0xffeU * m,   0xffeLL * m,             0xffeULL * m             ) \
  f("0xfffm",                  BAD,          0xfffU * m,   0xfffLL * m,             0xfffULL * m             ) \
  f("0x1000m",                 BAD,          BAD,          0x1000LL * m,            0x1000ULL * m            ) \
  f("-0x7ffm",                -0x7ff * m,    BAD,         -0x7ffLL * m,             BAD                      ) \
  f("-0x800m",                -0x800 * m,    BAD,         -0x800LL * m,             BAD                      ) \
  f("-0x801m",                 BAD,          BAD,         -0x801LL * m,             BAD                      ) \
                                                                                                               \
  f("0x7fffffffffem",          BAD,          BAD,          0x7fffffffffeLL * m,     0x7fffffffffeULL * m     ) \
  f("0x7ffffffffffm",          BAD,          BAD,          0x7ffffffffffLL * m,     0x7ffffffffffULL * m     ) \
  f("0x80000000000m",          BAD,          BAD,          BAD,                     0x80000000000ULL * m     ) \
  f("0xffffffffffem",          BAD,          BAD,          BAD,                     0xffffffffffeULL * m     ) \
  f("0xfffffffffffm",          BAD,          BAD,          BAD,                     0xfffffffffffULL * m     ) \
  f("0x100000000000m",         BAD,          BAD,          BAD,                     BAD                      ) \
  f("-0x7ffffffffffm",         BAD,          BAD,         -0x7ffffffffffLL * m,     BAD                      ) \
  f("-0x80000000000m",         BAD,          BAD,         -0x80000000000LL * m,     BAD                      ) \
  f("-0x80000000001m",         BAD,          BAD,          BAD,                     BAD                      ) \
                                                                                                               \
  /* edge cases for suffix: G */                                                                               \
  f("0x0g",                    0x0 * g,      0x0U * g,     0x0LL * g,               0x0ULL * g               ) \
  f("0x1g",                    0x1 * g,      0x1U * g,     0x1LL * g,               0x1ULL * g               ) \
  f("0x2g",                    BAD,          0x2U * g,     0x2LL * g,               0x2ULL * g               ) \
  f("0x3g",                    BAD,          0x3U * g,     0x3LL * g,               0x3ULL * g               ) \
  f("0x4g",                    BAD,          BAD,          0x4LL * g,               0x4ULL * g               ) \
  f("-0x1g",                  -0x1 * g,      BAD,         -0x1LL * g,               BAD                      ) \
  f("-0x2g",                  -0x2 * g,      BAD,         -0x2LL * g,               BAD                      ) \
  f("-0x3g",                   BAD,          BAD,         -0x3LL * g,               BAD                      ) \
                                                                                                               \
  f("0x1fffffffeg",            BAD,          BAD,          0x1fffffffeLL * g,       0x1fffffffeULL * g       ) \
  f("0x1ffffffffg",            BAD,          BAD,          0x1ffffffffLL * g,       0x1ffffffffULL * g       ) \
  f("0x200000000g",            BAD,          BAD,          BAD,                     0x200000000ULL * g       ) \
  f("0x3fffffffeg",            BAD,          BAD,          BAD,                     0x3fffffffeULL * g       ) \
  f("0x3ffffffffg",            BAD,          BAD,          BAD,                     0x3ffffffffULL * g       ) \
  f("0x400000000g",            BAD,          BAD,          BAD,                     BAD                      ) \
  f("-0x1ffffffffg",           BAD,          BAD,         -0x1ffffffffLL * g,       BAD                      ) \
  f("-0x200000000g",           BAD,          BAD,         -0x200000000LL * g,       BAD                      ) \
  f("-0x200000001g",           BAD,          BAD,          BAD,                     BAD                      ) \
                                                                                                               \
  /* edge cases for suffix: T */                                                                               \
  f("0x7ffffet",               BAD,          BAD,          0x7ffffeLL * t,          0x7ffffeULL * t          ) \
  f("0x7ffffft",               BAD,          BAD,          0x7fffffLL * t,          0x7fffffULL * t          ) \
  f("0x800000t",               BAD,          BAD,          BAD,                     0x800000ULL * t          ) \
  f("0xfffffet",               BAD,          BAD,          BAD,                     0xfffffeULL * t          ) \
  f("0xfffffft",               BAD,          BAD,          BAD,                     0xffffffULL * t          ) \
  f("0x1000000t",              BAD,          BAD,          BAD,                     BAD                      ) \
  f("-0x7ffffft",              BAD,          BAD,         -0x7fffffLL * t,          BAD                      ) \
  f("-0x800000t",              BAD,          BAD,         -0x800000LL * t,          BAD                      ) \
  f("-0x800001t",              BAD,          BAD,          BAD,                     BAD                      )

#define INTEGER_TEST_i32(s, i32, u32, i64, u64) NumericArgument<T>(s, i32),
#define INTEGER_TEST_u32(s, i32, u32, i64, u64) NumericArgument<T>(s, u32),
#define INTEGER_TEST_i64(s, i32, u32, i64, u64) NumericArgument<T>(s, i64),
#define INTEGER_TEST_u64(s, i32, u32, i64, u64) NumericArgument<T>(s, u64),

// signed 32-bit
template <typename T, ENABLE_IF(std::is_signed<T>::value), ENABLE_IF(sizeof(T) == 4)>
void check_flag(const char* f, T getvalue(JVMFlag* flag)) {
  JVMFlag* flag = JVMFlag::find_flag(f);
  if (flag == NULL) { // not available in product builds
    return;
  }

  T k = static_cast<T>(K);
  T m = static_cast<T>(M);
  T g = static_cast<T>(G);
  NumericArgument<T> valid_strings[] = { INTEGER_TEST_TABLE(INTEGER_TEST_i32) };
  check_numeric_flag(flag, getvalue, valid_strings, ARRAY_SIZE(valid_strings));
}

// unsigned 32-bit
template <typename T, ENABLE_IF(!std::is_signed<T>::value), ENABLE_IF(sizeof(T) == 4)>
void check_flag(const char* f, T getvalue(JVMFlag* flag)) {
  JVMFlag* flag = JVMFlag::find_flag(f);
  if (flag == NULL) { // not available in product builds
    return;
  }

  T k = static_cast<T>(K);
  T m = static_cast<T>(M);
  T g = static_cast<T>(G);
  NumericArgument<T> valid_strings[] = { INTEGER_TEST_TABLE(INTEGER_TEST_u32) };
  check_numeric_flag(flag, getvalue, valid_strings, ARRAY_SIZE(valid_strings));
}

// signed 64-bit
template <typename T, ENABLE_IF(std::is_signed<T>::value), ENABLE_IF(sizeof(T) == 8)>
void check_flag(const char* f, T getvalue(JVMFlag* flag)) {
  JVMFlag* flag = JVMFlag::find_flag(f);
  if (flag == NULL) { // not available in product builds
    return;
  }

  T k = static_cast<T>(K);
  T m = static_cast<T>(M);
  T g = static_cast<T>(G);
  T t = static_cast<T>(G) * k;
  NumericArgument<T> valid_strings[] = { INTEGER_TEST_TABLE(INTEGER_TEST_i64) };
  check_numeric_flag(flag, getvalue, valid_strings, ARRAY_SIZE(valid_strings));
}

// unsigned 64-bit
template <typename T, ENABLE_IF(!std::is_signed<T>::value), ENABLE_IF(sizeof(T) == 8)>
void check_flag(const char* f, T getvalue(JVMFlag* flag)) {
  JVMFlag* flag = JVMFlag::find_flag(f);
  if (flag == NULL) { // not available in product builds
    return;
  }

  T k = static_cast<T>(K);
  T m = static_cast<T>(M);
  T g = static_cast<T>(G);
  T t = static_cast<T>(G) * k;
  NumericArgument<T> valid_strings[] = { INTEGER_TEST_TABLE(INTEGER_TEST_u64) };
  check_numeric_flag(flag, getvalue, valid_strings, ARRAY_SIZE(valid_strings));
}

// Testing the parsing of -XX:<SomeFlag>=<an integer value>
//
// All of the integral types that can be used for command line options:
//   int, uint, intx, uintx, uint64_t, size_t
//
// In all supported platforms, these types can be mapped to only 4 native types:
//    {signed, unsigned} x {32-bit, 64-bit}
//
// We use SFINAE to pick the correct column in the INTEGER_TEST_TABLE for each type.

TEST_VM_F(ArgumentsTest, set_numeric_flag_int) {
  check_flag<int>("TestFlagFor_int", [] (JVMFlag* flag) {
    return flag->get_int();
  });
}

TEST_VM_F(ArgumentsTest, set_numeric_flag_uint) {
  check_flag<uint>("TestFlagFor_uint", [] (JVMFlag* flag) {
    return flag->get_uint();
  });
}

TEST_VM_F(ArgumentsTest, set_numeric_flag_intx) {
  check_flag<intx>("TestFlagFor_intx", [] (JVMFlag* flag) {
    return flag->get_intx();
  });
}

TEST_VM_F(ArgumentsTest, set_numeric_flag_uintx) {
  check_flag<uintx>("TestFlagFor_uintx", [] (JVMFlag* flag) {
    return flag->get_uintx();
  });
}

TEST_VM_F(ArgumentsTest, set_numeric_flag_uint64_t) {
  check_flag<uint64_t>("TestFlagFor_uint64_t", [] (JVMFlag* flag) {
    return flag->get_uint64_t();
  });
}

TEST_VM_F(ArgumentsTest, set_numeric_flag_size_t) {
  check_flag<size_t>("TestFlagFor_size_t", [] (JVMFlag* flag) {
    return flag->get_size_t();
  });
}

TEST_VM_F(ArgumentsTest, set_numeric_flag_double) {
  JVMFlag* flag = JVMFlag::find_flag("TestFlagFor_double");
  if (flag == NULL) { // not available in product builds
    return;
  }

  // TODO -- JDK-8282774
  // Need to add more test input that have a fractional part like "4.2".
  NumericArgument<double> valid_strings[] = {
    NumericArgument<double>("0",   0.0),
    NumericArgument<double>("1",   1.0),
    NumericArgument<double>("-0", -0.0),
    NumericArgument<double>("-1", -1.0),
  };

  auto getvalue = [] (JVMFlag* flag) {
    return flag->get_double();
  };

  check_numeric_flag<double>(flag, getvalue, valid_strings,
                             ARRAY_SIZE(valid_strings), /*is_double=*/true);
}
