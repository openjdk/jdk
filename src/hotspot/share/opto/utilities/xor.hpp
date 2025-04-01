#ifndef SHARE_OPTO_UTILITIES_XOR_HPP
#define SHARE_OPTO_UTILITIES_XOR_HPP

#include "utilities/powerOfTwo.hpp"
// Code separated into its own header to allow access from GTEST

// Given 2 non-negative values in the ranges [0, hi_0] and [0, hi_1], respectively. The bitwise
// xor of these values should also be non-negative. This method calculates an upper bound.

// S and U type parameters correspond to the signed and unsigned
// variants of an integer to operate on.
template<class S, class U>
static S xor_upper_bound_for_ranges(const S hi_0, const S hi_1) {
    static_assert(S(-1) < S(0), "S must be signed");
    static_assert(U(-1) > U(0), "U must be unsigned");

    assert(hi_0 >= 0, "must be non-negative");
    assert(hi_1 >= 0, "must be non-negative");

    // x ^ y cannot have any bit set that is higher than both the highest bits set in x and y
    // x cannot have any bit set that is higher than the highest bit set in hi_0
    // y cannot have any bit set that is higher than the highest bit set in hi_1

    // We want to find a value that has all 1 bits everywhere up to and including
    // the highest bits set in hi_0 as well as hi_1. For this, we can take the next
    // power of 2 strictly greater than both hi values and subtract 1 from it.

    // Example 1:
    // hi_0 = 5 (0b0101)       hi_1=1 (0b0001)
    //    (5|1)+1       = 0b0110
    //    round_up_pow2 = 0b1000
    //    -1            = 0b0111 = max

    // Example 2 - this demonstrates need for the +1:
    // hi_0 =  4 (0b0100)        hi_1=4 (0b0100)
    //    (4|4)+1       = 0b0101
    //    round_up_pow2 = 0b1000
    //    -1            = 0b0111 = max
    // Without the +1, round_up_pow2 would be 0b0100, resulting in 0b0011 as max

    // Note: cast to unsigned happens before +1 to avoid signed overflow, and
    // round_up is safe because high bit is unset (0 <= lo <= hi)

    return round_up_power_of_2(U(hi_0 | hi_1) + 1) - 1;
}

#endif // SHARE_OPTO_UTILITIES_XOR_HPP
