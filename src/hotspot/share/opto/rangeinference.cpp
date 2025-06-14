/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "opto/rangeinference.hpp"
#include "opto/type.hpp"
#include "utilities/intn_t.hpp"
#include "utilities/tuple.hpp"

// If the cardinality of a TypeInt is below this threshold, use min widen, see
// TypeIntPrototype<S, U>::normalize_widen
constexpr juint SMALL_TYPEINT_THRESHOLD = 3;

// This represents the result of an iterative calculation
template <class T>
class AdjustResult {
public:
  bool _progress; // whether there is progress compared to the last iteration
  bool _present;  // whether the result is empty, typically due to the calculation arriving at contradiction
  T _result;

  bool empty() const {
    return !_present;
  }

  static AdjustResult<T> make_empty() {
    return {true, false, {}};
  }
};

// This is the result of canonicalizing a simple interval (see TypeInt at
// type.hpp)
template <class U>
class SimpleCanonicalResult {
  static_assert(U(-1) > U(0), "bit info should be unsigned");
public:
  const bool _present;       // whether this is an empty set
  const RangeInt<U> _bounds; // The bounds must be in the same half of the integer domain (see TypeInt)
  const KnownBits<U> _bits;

  SimpleCanonicalResult(bool present, const RangeInt<U>& bounds, const KnownBits<U>& bits)
    : _present(present), _bounds(bounds), _bits(bits) {
    if (!present) {
      return;
    }
    // Do some verification
    assert(bits.is_satisfied_by(bounds._lo) && bits.is_satisfied_by(bounds._hi), "must be canonical");
    // 0b1000...
    constexpr U mid_point = (std::numeric_limits<U>::max() >> 1) + U(1);
    assert((bounds._lo < mid_point) == (bounds._hi < mid_point), "must be a simple interval, see Lemma 4");
  }

  bool empty() const {
    return !_present;
  }

  static SimpleCanonicalResult<U> make_empty() {
    return SimpleCanonicalResult(false, {}, {});
  }
};

// Find the minimum value that is not less than lo and satisfies bits. If there
// does not exist one such number, the calculation will return a value < lo.
//
// Formally, this function tries to find the minimum value that is not less
// than lo and satisfies bits, assuming such value exists. The cases where such
// value does not exists automatically follows.
//
// If there exists a value not less than lo and satisfies bits, then this
// function will always find one such value. The converse is also true, that is
// if this function finds a value not less than lo and satisfies bits, then it
// must trivially be the case that there exists one such value. As a result,
// the negation of those statements are also equivalent, there does not exists
// a value not less than lo and satisfies bits if and only if this function
// does not return one such value.
//
// In practice, since the algorithm always ensures that the returned value
// satisfies bits, we only need to check if it is not less than lo.
//
// Here, we view a number in binary as a bit string. As a result,  the first
// bit refers to the highest bit (the MSB), the last bit refers to the lowest
// bit (the LSB), a bit comes before (being higher than) another if it is more
// significant, and a bit comes after (being lower than) another if it is less
// significant. For a value n with w bits, we denote n[0] the first (highest)
// bit of n, n[1] the second bit, ..., n[w - 1] the last (lowest) bit of n.
template <class U>
static U adjust_lo(U lo, const KnownBits<U>& bits) {
  // Violation of lo with respects to bits
  // E.g: lo    = 1100
  //      zeros = 0100
  //      ones  = 1001
  // zero_violation = 0100, i.e the second bit should be zero, but it is 1 in
  // lo. Similarly, one_violation = 0001, i.e the last bit should be one, but
  // it is 0 in lo. These make lo not satisfy the bit constraints, which
  // results in us having to find the smallest value that satisfies bits.
  U zero_violation = lo & bits._zeros;
  U one_violation = ~lo & bits._ones;
  if (zero_violation == one_violation) {
    // This means lo does not violate bits, it is the result
    assert(zero_violation == U(0), "");
    return lo;
  }

  /*
  1. Intuition:
  Call r the lowest value not smaller than lo that satisfies bits, consider the
  first bit in r that is different from the corresponding bit in lo:
    - Since r is larger than lo the bit must be 0 in lo and 1 in r
    - Since r must satisify bits the bit must be 0 in zeros
    - Since r should be the smallest value, this bit should be the lowest one
      possible

  E.g:      1 2 3 4 5 6
       lo = 1 0 0 1 1 0
        x = 1 0 1 0 1 0
        y = 0 1 1 1 1 1
  x would be larger than lo since the first different bit is the 3rd one,
  while y is smaller than lo because the first different bit is the 1st bit.
  Next, consider:
       x1 = 1 0 1 0 1 0
       x2 = 1 0 0 1 1 1
  Both x1 and x2 are larger than lo, but x1 > x2 since its first different
  bit from lo is the 3rd one, while with x2 it is the 7th one. As a result,
  if both x1 and x2 satisfy bits, x2 would be closer to our true result.

  2. Formality:

  Call r the smallest value not smaller than lo that satisfies bits. Since lo
  does not satisfy bits, lo < r (2.1)

  Call i the largest bit index such that:

  - lo[x] satisfies bits for 0 <= x < i (2.2)
  - zeros[i] = 0                        (2.3)
  - lo[i]    = 0                        (2.4)

  Consider v:

  - v[x] = lo[x], for 0 <= x < i        (2.5)
  - v[i] = 1                            (2.6)
  - v[x] = ones[x], for x > i           (2.7)

  We will prove that v == r.

  a. Firstly, we prove that r <= v:

    a.1. lo < v, since:
      lo[x] == v[x], for 0 <= x < i (according to 2.5)
      lo[i] <  v[i] (according to 2.4 and 2.6, lo[i] == 0 < v[i] == 1)
      bits at x > i have lower significance, and are thus irrelevant

    a.2. v satisfies bits, because:
      v[x] satisfies bits for 0 <= x < i (according to 2.2 and 2.5)
      v[i] satisfies bits:
        According to 2.3 and 2.6, zeros[i] == 0 and v[i] == 1, v[i] does not violate
        bits, which means v[i] satisfies bits
      v[x] satisfies bits for x > i:
        Assume bits is not contradictory, we cannot have:
          ones[x]  == 1, v[x] == 0 (according to 2.7, v[x] == ones[x])
          zeros[x] == 1, v[x] == 1 (according to 2.7, ones[x] == v[x] == 1, which means
                                    bits is contradictory)

    From a.1 and a.2, v > lo and v satisfies bits. Which means r <= v since r is the
    smallest such value.

  b. Secondly, from r <= v, we prove that r == v. Suppose the contradiction r < v:

    Since r < v, there must be a bit position j that:

    r[j] == 0               (2.b.1)
    v[j] == 1               (2.b.2)
    r[x] == v[x], for x < j (2.b.3)

    b.1. If j < i
      This means that:
      r[j]  == 0                (according to 2.b.1)
      lo[j] == 1                (according to 2.b.2 and 2.5, lo[j] == v[j] == 1 because j < i)
      r[x]  == lo[x], for x < j (according to 2.b.3 and 2.5, lo[x] == v[x] == r[x] with x < j < i)
      bits at x > j have lower significance, and are thus irrelevant

      Which leads to r < lo, which contradicts that lo < r (acording to 2.1)

    b.2. If j == i
      Since r > lo (according to 2.1), there must exist a bit index k such that:

      r[k]  == 1                (2.b.2.1)
      lo[k] == 0                (2.b.2.2)
      r[x]  == lo[x], for x < k (2.b.2.3)

      Then, since we have:
      r[x]  == v[x],  for x < i (according to 2.b.3)
      v[x]  == lo[x], for x < i (according to 2.5)
      r[i]  == 0                (according to 2.b.1 because i == j)
      lo[i] == 0                (according to 2.4)

      this leads to: r[x] == lo[x], for x <= i
      while r[k] == 1 != lo[k] == 0, we can conclude that k > i

      However, since:
      lo[x] satisfies bits for 0 <= x < k:
        According to 2.b.2.3, lo[x] == r[x] and r satisfies bits
      zeros[k] == 0 (according to 2.b.2.1, r[k] == 1 and r satisfies bits)
      lo[k]    == 0 (according to 2.b.2.2)

      This contradicts the assumption that i is the largest bit index satisfying such conditions.

    b.3. If j > i
      ones[j] == v[j] (according to 2.7 since j > i)
      v[j]    == 1    (according to 2.b.2)
      r[j]    == 0    (according to 2.b.1)

      This means that r[j] == 0 and ones[j] == 1, this contradicts the assumption that r
      satisfies bits.

    All cases lead to contradictions, which mean r < v is incorrect, which means that
    r == v, which means the value v having the above form is the lowest value not smaller
    than lo that satisfies bits.

  3. Conclusion
    Our objective now is to find the largest value i that satisfies:
    - lo[x] satisfies bits for 0 <= x < i (3.1)
    - zeros[i] = 0                        (3.2)
    - lo[i] = 0                           (3.3)
  */

  // The algorithm depends on whether the first violation violates zeros or
  // ones. If it violates zeros, we have the bit being 1 in zero_violation and
  // 0 in one_violation. Since all higher bits are 0 in zero_violation and
  // one_violation, we have zero_violation > one_violation. Similarly, if the
  // first violation violates ones, we have zero_violation < one_violation.
  if (zero_violation < one_violation) {
    // This means that the first bit that does not satisfy the bit requirement
    // is a 0 that should be a 1. Obviously, since the bit at that position in
    // ones is 1, the same bit in zeros is 0.
    //
    // From section 3 above, we know i is the largest bit index such that:
    // - lo[x] satisfies bits for 0 <= x < i (3.1)
    // - zeros[i] = 0                        (3.2)
    // - lo[i] = 0                           (3.3)
    //
    // For the given i, we know that lo satisfies all bits before i, hence (3.1)
    // holds. Further, lo[i] = 0 (3.3), and we have a one violation at i, hence
    // zero[i] = 0 (3.2). Any smaller i would not be the largest possible such
    // index. Any larger i would violate (3.1), since lo[i] does not satisfy bits.
    // As a result, the first violation is the bit i we are looking for.
    //
    // E.g:      1 2 3 4 5 6 7 8
    //      lo = 1 1 0 0 0 1 1 0
    //   zeros = 0 0 1 0 0 1 0 0
    //    ones = 0 1 0 1 0 0 1 0
    //   1-vio = 0 0 0 1 0 0 0 0
    //   0-vio = 0 0 0 0 0 1 0 0
    // Since the result must have the 4th bit set, it must be at least:
    //           1 1 0 1 0 0 0 0
    // This value must satisfy zeros, because all bits before the 4th bit have
    // already satisfied zeros, and all bits after the 4th bit are all 0 now.
    // Just OR this value with ones to obtain the final result.

    // first_violation is the position of the violation counting from the
    // highest bit down (0-based), since i == 4, first_violation == 3
    juint first_violation = count_leading_zeros(one_violation);
    //           1 0 0 0 0 0 0 0
    constexpr U highest_bit = (std::numeric_limits<U>::max() >> 1) + U(1);
    // This is the bit at which we want to change the bit 0 in lo to a 1, and
    // all bits after to zero. This is similar to an operation that aligns lo
    // up to the next multiple of this modulo.
    //           0 0 0 1 0 0 0 0
    U alignment = highest_bit >> first_violation;
    // This is the first value which have the violated bit being 1, which means
    // that the result should not be smaller than this.
    // This is a standard operation to align a value up to the next multiple of
    // a certain power of 2. Since alignment is a power of 2, -alignment is a
    // value having all the bits being 1 upto the location of the bit in
    // alignment (in the example, -alignment = 11110000). As a result,
    // lo & -alignment set all bits after the bit in alignment to 0, which is
    // equivalent to rounding lo down to a multiple of alignment. To round lo
    // up to the next multiple of alignment, we add alignment to the rounded
    // down value.
    // Note that this computation cannot overflow as the bit in lo that is at
    // the same position as the only bit 1 in alignment must be 0. As a result,
    // this operation just set that bit to 1 and set all the bits after to 0.
    // We now have:
    // - new_lo[x] = lo[x], for 0 <= x < i (2.5)
    // - new_lo[i] = 1                     (2.6)
    // - new_lo[x] = 0, for x > i          (not yet 2.7)
    //           1 1 0 1 0 0 0 0
    U new_lo = (lo & -alignment) + alignment;
    // Note that there exists no value x not larger than i such that
    // new_lo[x] == 0 and ones[x] == 1. This is because all bits of lo before i
    // should satisfy bits, and new_lo[i] == 1. As a result, doing
    // new_lo |= bits.ones will give us a value such that:
    // - new_lo[x] = lo[x], for 0 <= x < i (2.5)
    // - new_lo[i] = 1                     (2.6)
    // - new_lo[x] = ones[x], for x > i    (2.7)
    // This is the result we are looking for.
    //           1 1 0 1 0 0 1 0
    new_lo |= bits._ones;
    // Note that in this case, new_lo is always a valid answer. That is, it is
    // a value not less than lo and satisfies bits.
    assert(lo < new_lo, "the result must be valid");
    return new_lo;
  } else {
    assert(zero_violation > one_violation, "remaining case");
    // This means that the first bit that does not satisfy the bit requirement
    // is a 1 that should be a 0.
    //
    // From section 3 above, we know i is the largest bit index such that:
    // - lo[x] satisfies bits for 0 <= x < i (3.1)
    // - zeros[i] = 0                        (3.2)
    // - lo[i] = 0                           (3.3)
    //
    // We know that lo satisfies all bits before first_violation, hence (3.1)
    // holds. However, first_violation is not the value i we are looking for
    // because lo[first_violation] == 1. We can also see that any larger value
    // of i would violate (3.1) since lo[first_violation] does not satisfy
    // bits. As a result, we should find the last index x upto first_violation
    // such that lo[x] == zeros[x] == 0. That value of x would be the value of
    // i we are looking for.
    //
    // E.g:      1 2 3 4 5 6 7 8
    //      lo = 1 0 0 0 1 1 1 0
    //   zeros = 0 0 0 1 0 1 0 0
    //    ones = 1 0 0 0 0 0 1 1
    //   1-vio = 0 0 0 0 0 0 0 1
    //   0-vio = 0 0 0 0 0 1 0 0
    // The first violation is the 6th bit, which should be 0. We want to flip
    // it to 0. However, since we must obtain a value larger than lo, we must
    // find an earlier bit that can be flipped from 0 to 1. The 5th cannot be
    // the bit we are looking for, because it is already 1, the 4th bit also
    // cannot be, because it must be 0. As a result, the last bit we can flip,
    // which is the first different bit between the result and lo must be the
    // 3rd bit. As a result, the result must not be smaller than:
    //           1 0 1 0 0 0 0 0
    // This one satisfies zeros so we can use the logic in the previous case,
    // just OR with ones to obtain the final result, which is:
    //           1 0 1 0 0 0 1 1

    juint first_violation = count_leading_zeros(zero_violation);
    // This masks out all bits after the first violation
    //           1 1 1 1 1 0 0 0
    U find_mask = ~(std::numeric_limits<U>::max() >> first_violation);
    // We want to find the last index x upto first_violation such that
    // lo[x] == zeros[x] == 0.
    // We start with all bits where lo[x] == zeros[x] == 0:
    //           0 1 1 0 0 0 0 1
    U neither = ~(lo | bits._zeros);
    // Now let us find all the bit indices x upto first_violation such that
    // lo[x] == zeros[x] == 0. The last one of these bits must be at index i.
    //           0 1 1 0 0 0 0 0
    U neither_upto_first_violation = neither & find_mask;
    // We now want to select the last one of these candidates, which is exactly
    // the last index x upto first_violation such that lo[x] == zeros[x] == 0.
    // This would be the value i we are looking for.
    // Similar to the other case, we want to obtain the value with only the bit
    // i set, this is equivalent to extracting the last set bit of
    // neither_upto_first_violation, do it directly without going through i.
    // The formula x & (-x) will give us the last set bit of an integer x
    // (please see the x86 instruction blsi).
    // In our example, i == 2
    //           0 0 1 0 0 0 0 0
    U alignment = neither_upto_first_violation & (-neither_upto_first_violation);
    // Set the bit of lo at i and unset all the bits after, this is the
    // smallest value that satisfies bits._zeros. Similar to the above case,
    // this is similar to aligning lo up to the next multiple of alignment.
    // Also similar to the above case, this computation cannot overflow.
    // We now have:
    // - new_lo[x] = lo[x], for 0 <= x < i (2.5)
    // - new_lo[i] = 1                     (2.6)
    // - new_lo[x] = 0, for x > i          (not yet 2.7)
    //           1 0 1 0 0 0 0 0
    U new_lo = (lo & -alignment) + alignment;
    // Note that there exists no value x not larger than i such that
    // new_lo[x] == 0 and ones[x] == 1. This is because all bits of lo before i
    // should satisfy bits, and new_lo[i] == 1. As a result, doing
    // new_lo |= bits.ones will give us a value such that:
    // - new_lo[x] = lo[x], for 0 <= x < i (2.5)
    // - new_lo[i] = 1                     (2.6)
    // - new_lo[x] = ones[x], for x > i    (2.7)
    // This is the result we are looking for.
    //           1 0 1 0 0 0 1 1
    new_lo |= bits._ones;
    // Note that formally, this function assumes that there exists a value not
    // smaller than lo and satisfies bits. This implies the existence of the
    // index i satisfies (3.1-3.3), which means that
    // neither_upto_first_violation != 0. The converse is
    // also true, if neither_upto_first_violation != 0, then an index i
    // satisfies (3.1-3.3) exists, which implies the existence of a value not
    // smaller than lo and satisfies bits. As a result, the negation of those
    // statements are equivalent. neither_upto_first_violation == 0 if and only
    // if there does not exists a value not smaller than lo and satisfies bits.
    // In this case, alignment == 0 and new_lo == bits._ones. We know that, if
    // the assumption of this function holds, we return a value satisfying
    // bits, and if the assumption of this function does not hold, the returned
    // value would be bits._ones, which also satisfies bits. As a result, this
    // function always returns a value satisfying bits, regardless whether if
    // the assumption of this function holds. In conclusion, the caller only
    // needs to check lo <= new_lo to find the cases where there exists no
    // value not smaller than lo and satisfies bits (see the overview of the
    // function).
    assert(lo < new_lo || new_lo == bits._ones, "invalid result must be bits._ones");
    return new_lo;
  }
}

// Try to tighten the bound constraints from the known bit information. I.e, we
// find the smallest value not smaller than lo, as well as the largest value
// not larger than hi both of which satisfy bits
// E.g: lo = 0010, hi = 1001
// zeros = 0011
// ones  = 0000
// -> 4-aligned
//
//         0    1    2    3    4    5    6    7    8    9    10
//         0000 0001 0010 0011 0100 0101 0110 0111 1000 1001 1010
// bits:   ok   .    .    .    ok   .    .    .    ok   .    .
// bounds:           lo                                 hi
// adjust:           --------> lo                  hi <---
template <class U>
static AdjustResult<RangeInt<U>>
adjust_unsigned_bounds_from_bits(const RangeInt<U>& bounds, const KnownBits<U>& bits) {
  U new_lo = adjust_lo(bounds._lo, bits);
  if (new_lo < bounds._lo) {
    // This means we wrapped around, which means no value not less than lo
    // satisfies bits
    return AdjustResult<RangeInt<U>>::make_empty();
  }

  // We need to find the largest value not larger than hi that satisfies bits
  // One possible method is to do similar to adjust_lo, just with the other
  // direction
  // However, we can observe that if v satisfies {bits._zeros, bits._ones},
  // then ~v would satisfy {bits._ones, bits._zeros}. Combine with the fact
  // that bitwise-not is a strictly decreasing function, if new_hi is the
  // largest value not larger than hi that satisfies {bits._zeros, bits._ones},
  // then ~new_hi is the smallest value not smaller than ~hi that satisfies
  // {bits._ones, bits._zeros}.
  //
  // Proof:
  // Calling h the smallest value not smaller than ~hi that satisfies
  // {bits._ones, bits._zeros}.
  //
  // 1. Since h satisfies {bits._ones, bits._zeros}, ~h satisfies
  //   {bits._zeros, bits._ones}. Assume the contradiction ~h does not satisfy
  //   {bits._zeros, bits._ones}, There can be 2 cases:
  //   1.1. There is a bit in ~h that is 0 where the corresponding bit in ones
  //     is 1. This implies the corresponding bit in h is 1. But this is
  //     contradictory since h satisfies {bits._ones, bits._zeros}.
  //   1.2. There is a bit in ~h that is 1 where the corresponding bit in zeros
  //     is 1. Similarly, this leads to contradiction because h needs to
  //     satisfy {bits._ones, bits._zeros}.
  //
  // 2. Assume there is a value k that is larger than ~h such that k is not
  // larger than hi, i.e. ~h < k <= hi, and k satisfies {bits._zeros, bits._ones}.
  // As a result, ~k would satisfy {bits._ones, bits._zeros}. And since bitwise-not
  // is a strictly decreasing function, given ~h < k <= hi, we have h > ~k >= ~hi.
  // This contradicts the assumption that h is the smallest value not smaller than
  // ~hi and satisfies {bits._ones, bits._zeros}.
  //
  // As a result, ~h is the largest value not larger than hi that satisfies
  // bits (QED).
  U h = adjust_lo(~bounds._hi, {bits._ones, bits._zeros});
  if (h < ~bounds._hi) {
    return AdjustResult<RangeInt<U>>::make_empty();
  }

  U new_hi = ~h;
  bool progress = (new_lo != bounds._lo) || (new_hi != bounds._hi);
  bool present = new_lo <= new_hi;
  return {progress, present, {new_lo, new_hi}};
}

// Try to tighten the known bit constraints from the bound information by
// extracting the common prefix of lo and hi and combining with the current
// bit constraints
// E.g: lo = 010011
//      hi = 010100,
// then all values in [lo, hi] would be
//           010***
template <class U>
static AdjustResult<KnownBits<U>>
adjust_bits_from_unsigned_bounds(const KnownBits<U>& bits, const RangeInt<U>& bounds) {
  // Find the mask to filter the common prefix, all values between bounds._lo
  // and bounds._hi should share this common prefix in terms of bits
  U mismatch = bounds._lo ^ bounds._hi;
  // Find the first mismatch, all bits before it are the same in bounds._lo and
  // bounds._hi
  U match_mask = mismatch == U(0) ? std::numeric_limits<U>::max()
                                  : ~(std::numeric_limits<U>::max() >> count_leading_zeros(mismatch));
  // match_mask & bounds._lo is the common prefix, extract zeros and ones from
  // it
  U common_prefix_zeros = match_mask & ~bounds._lo;
  assert(common_prefix_zeros == (match_mask & ~bounds._hi), "");
  U new_zeros = bits._zeros | common_prefix_zeros;

  U common_prefix_ones = match_mask & bounds._lo;
  assert(common_prefix_ones == (match_mask & bounds._hi), "");
  U new_ones = bits._ones | common_prefix_ones;

  bool progress = (new_zeros != bits._zeros) || (new_ones != bits._ones);
  bool present = ((new_zeros & new_ones) == U(0));
  return {progress, present, {new_zeros, new_ones}};
}

// Try to tighten both the bounds and the bits at the same time.
// Iteratively tighten one using the other until no progress is made.
// This function converges because at each iteration, some bits that are unknown
// are made known. As there are at most 64 bits, the number of iterations should
// not be larger than 64.
// This function is called simple because it deals with a simple intervals (see
// TypeInt at type.hpp).
template <class U>
static SimpleCanonicalResult<U>
canonicalize_constraints_simple(const RangeInt<U>& bounds, const KnownBits<U>& bits) {
  assert((bounds._lo ^ bounds._hi) < (std::numeric_limits<U>::max() >> 1) + U(1), "bounds must be a simple interval");

  AdjustResult<KnownBits<U>> canonicalized_bits = adjust_bits_from_unsigned_bounds(bits, bounds);
  if (canonicalized_bits.empty()) {
    return SimpleCanonicalResult<U>::make_empty();
  }
  AdjustResult<RangeInt<U>> canonicalized_bounds{true, true, bounds};
  // Since bits are derived from bounds in the previous iteration and vice
  // versa, if one does not show progress, the other will also not show
  // progress, so we terminate early
  while (true) {
    canonicalized_bounds = adjust_unsigned_bounds_from_bits(canonicalized_bounds._result, canonicalized_bits._result);
    if (!canonicalized_bounds._progress || canonicalized_bounds.empty()) {
      return SimpleCanonicalResult<U>(canonicalized_bounds._present, canonicalized_bounds._result, canonicalized_bits._result);
    }
    canonicalized_bits = adjust_bits_from_unsigned_bounds(canonicalized_bits._result, canonicalized_bounds._result);
    if (!canonicalized_bits._progress || canonicalized_bits.empty()) {
      return SimpleCanonicalResult<U>(canonicalized_bits._present, canonicalized_bounds._result, canonicalized_bits._result);
    }
  }
}

// Tighten all constraints of a TypeIntPrototype to its canonical form.
// i.e the result represents the same set as the input, each bound belongs to
// the set and for each bit position that is not constrained, there exists 2
// values with the bit value at that position being set and unset, respectively,
// such that both belong to the set represented by the constraints.
template <class S, class U>
typename TypeIntPrototype<S, U>::CanonicalizedTypeIntPrototype
TypeIntPrototype<S, U>::canonicalize_constraints() const {
  RangeInt<S> srange = _srange;
  RangeInt<U> urange = _urange;
  // Trivial contradictions
  if (srange._lo > srange._hi ||
      urange._lo > urange._hi ||
      (_bits._zeros & _bits._ones) != U(0)) {
    return CanonicalizedTypeIntPrototype::make_empty();
  }

  // We try to make [srange._lo, S(urange._hi)] and
  // [S(urange._lo), srange._hi] be both simple intervals (as defined in
  // TypeInt at type.hpp)
  if (S(urange._lo) > S(urange._hi)) {
    // This means that S(urange._lo) >= 0 and S(urange._hi) < 0 because here we
    // know that U(urange._lo) <= U(urange._hi)
    if (S(urange._hi) < srange._lo) {
      // This means that there should be no element in the interval
      // [min_S, S(urange._hi)], tighten urange._hi to max_S
      // Signed:
      // min_S----uhi---------lo---------0--------ulo==========hi----max_S
      // Unsigned:
      //                                 0--------ulo==========hi----max_S min_S-----uhi---------lo---------
      urange._hi = U(std::numeric_limits<S>::max());
    } else if (S(urange._lo) > srange._hi) {
      // This means that there should be no element in the interval
      // [S(urange._lo), max_S], tighten urange._lo to min_S
      // Signed:
      // min_S----lo=========uhi---------0--------hi----------ulo----max_S
      // Unsigned:
      //                                 0--------hi----------ulo----max_S min_S----lo=========uhi---------
      urange._lo = U(std::numeric_limits<S>::min());
    }
  }

  // Now [srange._lo, S(urange._hi)] and [S(urange._lo), srange._hi] are both
  // simple intervals (as defined in TypeInt at type.hpp), we process them
  // separately and combine the results
  if (S(urange._lo) <= S(urange._hi)) {
    // The 2 simple intervals should be tightened to the same result
    urange._lo = U(MAX2(S(urange._lo), srange._lo));
    urange._hi = U(MIN2(S(urange._hi), srange._hi));
    if (urange._lo > urange._hi || S(urange._lo) > S(urange._hi)) {
      return CanonicalizedTypeIntPrototype::make_empty();
    }

    auto type = canonicalize_constraints_simple(urange, _bits);
    return {type._present, {{S(type._bounds._lo), S(type._bounds._hi)},
                            type._bounds, type._bits}};
  }

  // The 2 simple intervals can be tightened into 2 separate results
  auto neg_type = canonicalize_constraints_simple({U(srange._lo), urange._hi}, _bits);
  auto pos_type = canonicalize_constraints_simple({urange._lo, U(srange._hi)}, _bits);

  if (neg_type.empty() && pos_type.empty()) {
    return CanonicalizedTypeIntPrototype::make_empty();
  } else if (neg_type.empty()) {
    return {true, {{S(pos_type._bounds._lo), S(pos_type._bounds._hi)},
                   pos_type._bounds, pos_type._bits}};
  } else if (pos_type.empty()) {
    return {true, {{S(neg_type._bounds._lo), S(neg_type._bounds._hi)},
                   neg_type._bounds, neg_type._bits}};
  } else {
    return {true, {{S(neg_type._bounds._lo), S(pos_type._bounds._hi)},
                   {pos_type._bounds._lo, neg_type._bounds._hi},
                   {neg_type._bits._zeros & pos_type._bits._zeros, neg_type._bits._ones & pos_type._bits._ones}}};
  }
}

template <class S, class U>
int TypeIntPrototype<S, U>::normalize_widen(int widen) const {
  // Certain normalizations keep us sane when comparing types.
  // The 'SMALL_TYPEINT_THRESHOLD' covers constants and also CC and its relatives.
  if (TypeIntHelper::cardinality_from_bounds(_srange, _urange) <= U(SMALL_TYPEINT_THRESHOLD)) {
    return Type::WidenMin;
  }
  if (_srange._lo == std::numeric_limits<S>::min() && _srange._hi == std::numeric_limits<S>::max() &&
      _urange._lo == std::numeric_limits<U>::min() && _urange._hi == std::numeric_limits<U>::max() &&
      _bits._zeros == U(0) && _bits._ones == U(0)) {
    // bottom type
    return Type::WidenMax;
  }
  return widen;
}

#ifdef ASSERT
template <class S, class U>
bool TypeIntPrototype<S, U>::contains(S v) const {
  U u(v);
  return v >= _srange._lo && v <= _srange._hi &&
         u >= _urange._lo && u <= _urange._hi &&
         _bits.is_satisfied_by(u);
}

// Verify that this set representation is canonical
template <class S, class U>
void TypeIntPrototype<S, U>::verify_constraints() const {
  // Assert that the bounds cannot be further tightened
  assert(contains(_srange._lo) && contains(_srange._hi) &&
         contains(S(_urange._lo)) && contains(S(_urange._hi)), "");

  // Assert that the bits cannot be further tightened
  if (U(_srange._lo) == _urange._lo) {
    assert(!adjust_bits_from_unsigned_bounds(_bits, _urange)._progress, "");
  } else {
    RangeInt<U> neg_range{U(_srange._lo), _urange._hi};
    auto neg_bits = adjust_bits_from_unsigned_bounds(_bits, neg_range);
    assert(neg_bits._present, "");
    assert(!adjust_unsigned_bounds_from_bits(neg_range, neg_bits._result)._progress, "");

    RangeInt<U> pos_range{_urange._lo, U(_srange._hi)};
    auto pos_bits = adjust_bits_from_unsigned_bounds(_bits, pos_range);
    assert(pos_bits._present, "");
    assert(!adjust_unsigned_bounds_from_bits(pos_range, pos_bits._result)._progress, "");

    assert((neg_bits._result._zeros & pos_bits._result._zeros) == _bits._zeros &&
           (neg_bits._result._ones & pos_bits._result._ones) == _bits._ones, "");
  }
}
#endif // ASSERT

template class TypeIntPrototype<jint, juint>;
template class TypeIntPrototype<jlong, julong>;
template class TypeIntPrototype<intn_t<1>, uintn_t<1>>;
template class TypeIntPrototype<intn_t<2>, uintn_t<2>>;
template class TypeIntPrototype<intn_t<3>, uintn_t<3>>;
template class TypeIntPrototype<intn_t<4>, uintn_t<4>>;

// Compute the meet of 2 types. When dual is true, the subset relation in CT is
// reversed. This means that the result of 2 CTs would be the intersection of
// them if dual is true, and be the union of them if dual is false. The subset
// relation in the Type hierarchy is still the same, however. E.g. the result
// of 1 CT and Type::BOTTOM would always be Type::BOTTOM, and the result of 1
// CT and Type::TOP would always be the CT instance itself.
template <class CT>
const Type* TypeIntHelper::int_type_xmeet(const CT* i1, const Type* t2) {
  // Perform a fast test for common case; meeting the same types together.
  if (i1 == t2 || t2 == Type::TOP) {
    return i1;
  }
  const CT* i2 = t2->try_cast<CT>();
  if (i2 != nullptr) {
    assert(i1->_is_dual == i2->_is_dual, "must have the same duality");
    using S = std::remove_const_t<decltype(CT::_lo)>;
    using U = std::remove_const_t<decltype(CT::_ulo)>;

    if (!i1->_is_dual) {
      // meet (a.k.a union)
      return CT::make_or_top(TypeIntPrototype<S, U>{{MIN2(i1->_lo, i2->_lo), MAX2(i1->_hi, i2->_hi)},
                                                    {MIN2(i1->_ulo, i2->_ulo), MAX2(i1->_uhi, i2->_uhi)},
                                                    {i1->_bits._zeros & i2->_bits._zeros, i1->_bits._ones & i2->_bits._ones}},
                             MAX2(i1->_widen, i2->_widen), false);
    } else {
      // join (a.k.a intersection)
      return CT::make_or_top(TypeIntPrototype<S, U>{{MAX2(i1->_lo, i2->_lo), MIN2(i1->_hi, i2->_hi)},
                                                    {MAX2(i1->_ulo, i2->_ulo), MIN2(i1->_uhi, i2->_uhi)},
                                                    {i1->_bits._zeros | i2->_bits._zeros, i1->_bits._ones | i2->_bits._ones}},
                             MIN2(i1->_widen, i2->_widen), true);
    }
  }

  assert(t2->base() != i1->base(), "");
  switch (t2->base()) {          // Switch on original type
  case Type::AnyPtr:                  // Mixing with oops happens when javac
  case Type::RawPtr:                  // reuses local variables
  case Type::OopPtr:
  case Type::InstPtr:
  case Type::AryPtr:
  case Type::MetadataPtr:
  case Type::KlassPtr:
  case Type::InstKlassPtr:
  case Type::AryKlassPtr:
  case Type::NarrowOop:
  case Type::NarrowKlass:
  case Type::Int:
  case Type::Long:
  case Type::HalfFloatTop:
  case Type::HalfFloatCon:
  case Type::HalfFloatBot:
  case Type::FloatTop:
  case Type::FloatCon:
  case Type::FloatBot:
  case Type::DoubleTop:
  case Type::DoubleCon:
  case Type::DoubleBot:
  case Type::Bottom:                  // Ye Olde Default
    return Type::BOTTOM;
  default:                      // All else is a mistake
    i1->typerr(t2);
    return nullptr;
  }
}
template const Type* TypeIntHelper::int_type_xmeet(const TypeInt* i1, const Type* t2);
template const Type* TypeIntHelper::int_type_xmeet(const TypeLong* i1, const Type* t2);

// Called in PhiNode::Value during CCP, monotically widen the value set, do so rigorously
// first, after WidenMax attempts, if the type has still not converged we speed up the
// convergence by abandoning the bounds
template <class CT>
const Type* TypeIntHelper::int_type_widen(const CT* new_type, const CT* old_type, const CT* limit_type) {
  using S = std::remove_const_t<decltype(CT::_lo)>;
  using U = std::remove_const_t<decltype(CT::_ulo)>;

  if (old_type == nullptr) {
    return new_type;
  }

  // If new guy is equal to old guy, no widening
  if (int_type_is_equal(new_type, old_type)) {
    return old_type;
  }

  // If old guy contains new, then we probably widened too far & dropped to
  // bottom. Return the wider fellow.
  if (int_type_is_subset(old_type, new_type)) {
    return old_type;
  }

  // Neither contains each other, weird?
  if (!int_type_is_subset(new_type, old_type)) {
    return CT::TYPE_DOMAIN;
  }

  // If old guy was a constant, do not bother
  if (old_type->singleton()) {
    return new_type;
  }

  // If new guy contains old, then we widened
  // If new guy is already wider than old, no widening
  if (new_type->_widen > old_type->_widen) {
    return new_type;
  }

  if (new_type->_widen < Type::WidenMax) {
    // Returned widened new guy
    TypeIntPrototype<S, U> prototype{{new_type->_lo, new_type->_hi}, {new_type->_ulo, new_type->_uhi}, new_type->_bits};
    return CT::make_or_top(prototype, new_type->_widen + 1);
  }

  // Speed up the convergence by abandoning the bounds, there are only a couple of bits so
  // they converge fast
  S min = std::numeric_limits<S>::min();
  S max = std::numeric_limits<S>::max();
  U umin = std::numeric_limits<U>::min();
  U umax = std::numeric_limits<U>::max();
  U zeros = new_type->_bits._zeros;
  U ones = new_type->_bits._ones;
  if (limit_type != nullptr) {
    min = limit_type->_lo;
    max = limit_type->_hi;
    umin = limit_type->_ulo;
    umax = limit_type->_uhi;
    zeros |= limit_type->_bits._zeros;
    ones |= limit_type->_bits._ones;
  }
  TypeIntPrototype<S, U> prototype{{min, max}, {umin, umax}, {zeros, ones}};
  return CT::make_or_top(prototype, Type::WidenMax);
}
template const Type* TypeIntHelper::int_type_widen(const TypeInt* new_type, const TypeInt* old_type, const TypeInt* limit_type);
template const Type* TypeIntHelper::int_type_widen(const TypeLong* new_type, const TypeLong* old_type, const TypeLong* limit_type);

// Called by PhiNode::Value during GVN, monotonically narrow the value set, only
// narrow if the bits change or if the bounds are tightened enough to avoid
// slow convergence
template <class CT>
const Type* TypeIntHelper::int_type_narrow(const CT* new_type, const CT* old_type) {
  using S = decltype(CT::_lo);
  using U = decltype(CT::_ulo);

  if (new_type->singleton() || old_type == nullptr) {
    return new_type;
  }

  // If new guy is equal to old guy, no narrowing
  if (int_type_is_equal(new_type, old_type)) {
    return old_type;
  }

  // If old guy was maximum range, allow the narrowing
  if (int_type_is_equal(old_type, CT::TYPE_DOMAIN)) {
    return new_type;
  }

  // Doesn't narrow; pretty weird
  if (!int_type_is_subset(old_type, new_type)) {
    return new_type;
  }

  // Bits change
  if (old_type->_bits._zeros != new_type->_bits._zeros || old_type->_bits._ones != new_type->_bits._ones) {
    return new_type;
  }

  // Only narrow if the range shrinks a lot
  U oc = cardinality_from_bounds(RangeInt<S>{old_type->_lo, old_type->_hi},
                                 RangeInt<U>{old_type->_ulo, old_type->_uhi});
  U nc = cardinality_from_bounds(RangeInt<S>{new_type->_lo, new_type->_hi},
                                 RangeInt<U>{new_type->_ulo, new_type->_uhi});
  return (nc > (oc >> 1) + (SMALL_TYPEINT_THRESHOLD * 2)) ? old_type : new_type;
}
template const Type* TypeIntHelper::int_type_narrow(const TypeInt* new_type, const TypeInt* old_type);
template const Type* TypeIntHelper::int_type_narrow(const TypeLong* new_type, const TypeLong* old_type);


#ifndef PRODUCT
template <class T>
static const char* int_name_near(T origin, const char* xname, char* buf, size_t buf_size, T n) {
  if (n < origin) {
    if (n <= origin - 10000) {
      return nullptr;
    }
    os::snprintf_checked(buf, buf_size, "%s-" INT32_FORMAT, xname, jint(origin - n));
  } else if (n > origin) {
    if (n >= origin + 10000) {
      return nullptr;
    }
    os::snprintf_checked(buf, buf_size, "%s+" INT32_FORMAT, xname, jint(n - origin));
  } else {
    return xname;
  }
  return buf;
}

const char* TypeIntHelper::intname(char* buf, size_t buf_size, jint n) {
  const char* str = int_name_near<jint>(max_jint, "maxint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = int_name_near<jint>(min_jint, "minint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  os::snprintf_checked(buf, buf_size, INT32_FORMAT, n);
  return buf;
}

const char* TypeIntHelper::uintname(char* buf, size_t buf_size, juint n) {
  const char* str = int_name_near<juint>(max_juint, "maxuint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = int_name_near<juint>(max_jint, "maxint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  os::snprintf_checked(buf, buf_size, UINT32_FORMAT"u", n);
  return buf;
}

const char* TypeIntHelper::longname(char* buf, size_t buf_size, jlong n) {
  const char* str = int_name_near<jlong>(max_jlong, "maxlong", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = int_name_near<jlong>(min_jlong, "minlong", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = int_name_near<jlong>(max_juint, "maxuint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = int_name_near<jlong>(max_jint, "maxint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = int_name_near<jlong>(min_jint, "minint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  os::snprintf_checked(buf, buf_size, JLONG_FORMAT, n);
  return buf;
}

const char* TypeIntHelper::ulongname(char* buf, size_t buf_size, julong n) {
  const char* str = int_name_near<julong>(max_julong, "maxulong", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = int_name_near<julong>(max_jlong, "maxlong", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = int_name_near<julong>(max_juint, "maxuint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  str = int_name_near<julong>(max_jint, "maxint", buf, buf_size, n);
  if (str != nullptr) {
    return str;
  }

  os::snprintf_checked(buf, buf_size, JULONG_FORMAT"u", n);
  return buf;
}

template <class U>
const char* TypeIntHelper::bitname(char* buf, size_t buf_size, U zeros, U ones) {
  constexpr juint W = sizeof(U) * 8;

  if (buf_size < W + 1) {
    return "#####";
  }

  for (juint i = 0; i < W; i++) {
    U mask = U(1) << (W - 1 - i);
    if ((zeros & mask) != 0) {
      buf[i] = '0';
    } else if ((ones & mask) != 0) {
      buf[i] = '1';
    } else {
      buf[i] = '*';
    }
  }
  buf[W] = 0;
  return buf;
}
template const char* TypeIntHelper::bitname(char* buf, size_t buf_size, juint zeros, juint ones);
template const char* TypeIntHelper::bitname(char* buf, size_t buf_size, julong zeros, julong ones);

void TypeIntHelper::int_type_dump(const TypeInt* t, outputStream* st, bool verbose) {
  char buf1[40], buf2[40], buf3[40], buf4[40], buf5[40];
  if (int_type_is_equal(t, TypeInt::INT)) {
    st->print("int");
  } else if (t->is_con()) {
    st->print("int:%s", intname(buf1, sizeof(buf1), t->get_con()));
  } else if (int_type_is_equal(t, TypeInt::BOOL)) {
    st->print("bool");
  } else if (int_type_is_equal(t, TypeInt::BYTE)) {
    st->print("byte");
  } else if (int_type_is_equal(t, TypeInt::CHAR)) {
    st->print("char");
  } else if (int_type_is_equal(t, TypeInt::SHORT)) {
    st->print("short");
  } else {
    if (verbose) {
      st->print("int:%s..%s, %s..%s, %s",
                intname(buf1, sizeof(buf1), t->_lo), intname(buf2, sizeof(buf2), t->_hi),
                uintname(buf3, sizeof(buf3), t->_ulo), uintname(buf4, sizeof(buf4), t->_uhi),
                bitname(buf5, sizeof(buf5), t->_bits._zeros, t->_bits._ones));
    } else {
      if (t->_lo >= 0) {
        if (t->_hi == max_jint) {
          st->print("int:>=%s", intname(buf1, sizeof(buf1), t->_lo));
        } else {
          st->print("int:%s..%s", intname(buf1, sizeof(buf1), t->_lo), intname(buf2, sizeof(buf2), t->_hi));
        }
      } else if (t->_hi < 0) {
        if (t->_lo == min_jint) {
          st->print("int:<=%s", intname(buf1, sizeof(buf1), t->_hi));
        } else {
          st->print("int:%s..%s", intname(buf1, sizeof(buf1), t->_lo), intname(buf2, sizeof(buf2), t->_hi));
        }
      } else {
        st->print("int:%s..%s, %s..%s",
                  intname(buf1, sizeof(buf1), t->_lo), intname(buf2, sizeof(buf2), t->_hi),
                  uintname(buf3, sizeof(buf3), t->_ulo), uintname(buf4, sizeof(buf4), t->_uhi));
      }

    }
  }

  if (t->_widen > 0 && t != TypeInt::INT) {
    st->print(", widen: %d", t->_widen);
  }
}

void TypeIntHelper::int_type_dump(const TypeLong* t, outputStream* st, bool verbose) {
  char buf1[80], buf2[80], buf3[80], buf4[80], buf5[80];
  if (int_type_is_equal(t, TypeLong::LONG)) {
    st->print("long");
  } else if (t->is_con()) {
    st->print("long:%s", longname(buf1, sizeof(buf1), t->get_con()));
  } else {
    if (verbose) {
      st->print("long:%s..%s, %s..%s, bits:%s",
                longname(buf1, sizeof(buf1), t->_lo), longname(buf2,sizeof(buf2), t-> _hi),
                ulongname(buf3, sizeof(buf3), t->_ulo), ulongname(buf4, sizeof(buf4), t->_uhi),
                bitname(buf5, sizeof(buf5), t->_bits._zeros, t->_bits._ones));
    } else {
      if (t->_lo >= 0) {
        if (t->_hi == max_jint) {
          st->print("long:>=%s", longname(buf1, sizeof(buf1), t->_lo));
        } else {
          st->print("long:%s..%s", longname(buf1, sizeof(buf1), t->_lo), longname(buf2, sizeof(buf2), t->_hi));
        }
      } else if (t->_hi < 0) {
        if (t->_lo == min_jint) {
          st->print("long:<=%s", longname(buf1, sizeof(buf1), t->_hi));
        } else {
          st->print("long:%s..%s", longname(buf1, sizeof(buf1), t->_lo), longname(buf2, sizeof(buf2), t->_hi));
        }
      } else {
        st->print("long:%s..%s, %s..%s",
                  longname(buf1, sizeof(buf1), t->_lo), longname(buf2,sizeof(buf2), t-> _hi),
                  ulongname(buf3, sizeof(buf3), t->_ulo), ulongname(buf4, sizeof(buf4), t->_uhi));
      }
    }
  }

  if (t->_widen > 0 && t != TypeLong::LONG) {
    st->print(", widen: %d", t->_widen);
  }
}
#endif // PRODUCT
