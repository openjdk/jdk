/*
 * Copyright (c) 2021, 2023, Intel Corporation. All rights reserved.
 * Copyright (c) 2021 Serge Sans Paille. All rights reserved.
 * Copyright 2025 Arm Limited and/or its affiliates.
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

#ifndef SVE_QSORT_VECTOR
#define SVE_QSORT_VECTOR

#include <arm_sve.h>
#include <cfloat>
#include <limits.h>

template <typename type>
struct sve_vector;

template <>
struct sve_vector<int32_t> {
    using type_t = int32_t;
    using reg_t = svint32_t;       // SVE 32-bit integer vector
    using opmask_t = svbool_t;     // predicate register
    /* TODO: Prefer avoiding a runtime svcntw() call when the vector length
     * is known at compile time. One option is to add a template parameter to
     * this struct for common cases - 128/256 bits with a fallback to svcntw()
     * if the vector width is unknown at compile time.
     */
    static inline uint8_t numlanes() {
        return static_cast<uint8_t>(svcntw());
    }

    static inline int partition_unroll_factor() {
        return (svcntw() * sizeof(type_t)) > 16 ? 4 : 2;
    }

    static type_t type_max() { return SIMD_SORT_MAX_INT32; }
    static type_t type_min() { return SIMD_SORT_MIN_INT32; }

    static opmask_t knot_opmask(opmask_t x) {
        return svnot_b_z(svptrue_b32(), x);
    }

    static opmask_t ge(reg_t x, reg_t y) {
        return svcmpge_s32(svptrue_b32(),x, y);
    }

    static opmask_t gt(reg_t x, reg_t y) {
        return svcmpgt_s32(svptrue_b32(),x, y);
    }

    static reg_t loadu(void const *mem) {
        return svld1_s32(svptrue_b32(), (const int32_t*)mem);
    }

    static type_t reducemax(reg_t v) {
        return svmaxv_s32(svptrue_b32(), v);
    }

    static type_t reducemin(reg_t v) {
        return svminv_s32(svptrue_b32(), v);
    }

    static reg_t set1(type_t v) {
        return svdup_n_s32(v);
    }

    static void storeu(void *mem, reg_t x) {
        return svst1_s32(svptrue_b32(), (int32_t*)mem, x);
    }

    static reg_t min(reg_t x, reg_t y) {
        return svmin_s32_z(svptrue_b32(), x, y);
    }

    static reg_t max(reg_t x, reg_t y) {
        return svmax_s32_z(svptrue_b32(), x, y);
    }

    static int double_compressstore(type_t *left_addr, type_t *right_addr,
                                    opmask_t k, reg_t reg) {
        // fast path if all vector elements are less than pivot
        svbool_t pg = svptrue_b32();
        if (!svptest_any(pg, k)) {
            svst1_s32(pg, (int32_t*)left_addr, reg);
            return 0;
        }

        // fast path if all vector elements are greater than pivot
        if (!svptest_any(pg, svnot_b_z(pg, k))) {
            svst1_s32(pg, (int32_t*)right_addr, reg);
            return numlanes();
        }

        uint64_t amount_ge_pivot = svcntp_b32(svptrue_b32(), k);
        uint64_t amount_nge_pivot = numlanes() - amount_ge_pivot;

        svint32_t compressed_1 = svcompact_s32(knot_opmask(k), reg);
        svint32_t compressed_2 = svcompact_s32(k, reg);

        svbool_t store_mask_1 = svwhilelt_b32_u64(0, amount_nge_pivot);
        svbool_t store_mask_2 = svwhilelt_b32_u64(0, amount_ge_pivot);

        svst1_s32(store_mask_1, (int32_t*)left_addr, compressed_1);
        svst1_s32(store_mask_2, (int32_t*)(right_addr + amount_nge_pivot), compressed_2);

        return amount_ge_pivot;
    }

    static void oet_sort(type_t *arr, arrsize_t num) {
        svbool_t p1 = svwhilelt_b32_u64(0, num);
        const svint32x2_t z0_z1 = svld2_s32(p1, arr);
        const svbool_t p2 = svcmplt_s32(p1, svget2_s32(z0_z1, 0), svget2_s32(z0_z1, 1));

        const svint32_t z4 = svsel_s32(p2, svget2_s32(z0_z1, 0), svget2_s32(z0_z1, 1)); // z4 <- smaller values
        const svint32_t z5 = svsel_s32(p2, svget2_s32(z0_z1, 1), svget2_s32(z0_z1, 0)); // z5 <- larger values

        svst2_s32(p1, arr, svcreate2_s32(z4, z5));
    }
};

template <>
struct sve_vector<float> {
    using type_t = float;
    using reg_t = svfloat32_t;     // SVE 32-bit float vector
    using opmask_t = svbool_t;     // predicate register
    /* TODO: Prefer avoiding a runtime svcntw() call when the vector length
     * is known at compile time. One option is to add a template parameter to
     * this struct for common cases - 128/256 bits with a fallback to svcntw()
     * if the vector width is unknown at compile time.
     */
    static inline uint8_t numlanes() {
        return static_cast<uint8_t>(svcntw());
    }

    static inline int partition_unroll_factor() {
        return (svcntw() * sizeof(type_t)) > 16 ? 4 : 2;
    }

    static type_t type_max() { return SIMD_SORT_INFINITYF; }
    static type_t type_min() { return -SIMD_SORT_INFINITYF; }

    static opmask_t knot_opmask(opmask_t x) {
        return svnot_b_z(svptrue_b32(), x);
    }

    static opmask_t ge(reg_t x, reg_t y) {
        return svcmpge_f32(svptrue_b32(),x, y);
    }

    static opmask_t gt(reg_t x, reg_t y) {
        return svcmpgt_f32(svptrue_b32(),x, y);
    }

    static reg_t loadu(void const *mem) {
        return svld1_f32(svptrue_b32(), (const float*)mem);
    }

    static type_t reducemax(reg_t v) {
        return svmaxv_f32(svptrue_b32(), v);
    }

    static type_t reducemin(reg_t v) {
        return svminv_f32(svptrue_b32(), v);
    }

    static reg_t set1(type_t v) {
        return svdup_n_f32(v);
    }

    static void storeu(void *mem, reg_t x) {
        return svst1_f32(svptrue_b32(), (float32_t*)mem, x);
    }

    static reg_t min(reg_t x, reg_t y) {
        return svmin_f32_z(svptrue_b32(), x, y);
    }

    static reg_t max(reg_t x, reg_t y) {
        return svmax_f32_z(svptrue_b32(), x, y);
    }

    static int double_compressstore(type_t *left_addr, type_t *right_addr,
                                    opmask_t k, reg_t reg) {
        // fast path if all vector elements are less than pivot
        svbool_t pg = svptrue_b32();
        if (!svptest_any(pg, k)) {
            svst1_f32(pg, (float32_t*)left_addr, reg);
            return 0;
        }

        // fast path if all vector elements are greater than pivot
        if (!svptest_any(pg, svnot_b_z(pg, k))) {
            svst1_f32(pg, (float32_t*)right_addr, reg);
            return numlanes();
        }

        uint64_t amount_ge_pivot = svcntp_b32(svptrue_b32(), k);
        uint64_t amount_nge_pivot = numlanes() - amount_ge_pivot;

        svfloat32_t compressed_1 = svcompact_f32(knot_opmask(k), reg);
        svfloat32_t compressed_2 = svcompact_f32(k, reg);

        svbool_t store_mask_1 = svwhilelt_b32_u64(0, amount_nge_pivot);
        svbool_t store_mask_2 = svwhilelt_b32_u64(0, amount_ge_pivot);

        svst1_f32(store_mask_1, (float32_t*)left_addr, compressed_1);
        svst1_f32(store_mask_2, (float32_t*)(right_addr + amount_nge_pivot), compressed_2);

        return amount_ge_pivot;
    }

    static void oet_sort(type_t *arr, arrsize_t num) {
        svbool_t p1 = svwhilelt_b32_u64(0, num);
        const svfloat32x2_t z0_z1 = svld2_f32(p1, arr);
        const svbool_t p2 = svcmplt_f32(p1, svget2_f32(z0_z1, 0), svget2_f32(z0_z1, 1));

        const svfloat32_t z4 = svsel_f32(p2, svget2_f32(z0_z1, 0), svget2_f32(z0_z1, 1)); // z4 <- smaller values
        const svfloat32_t z5 = svsel_f32(p2, svget2_f32(z0_z1, 1), svget2_f32(z0_z1, 0)); // z5 <- larger values

        svst2_f32(p1, arr, svcreate2_f32(z4, z5));
    }
};
#endif  // SVE_QSORT_VECTOR
