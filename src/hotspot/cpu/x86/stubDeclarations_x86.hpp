/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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

#ifndef CPU_X86_STUBDECLARATIONS_HPP
#define CPU_X86_STUBDECLARATIONS_HPP

#define STUBGEN_PREUNIVERSE_BLOBS_ARCH_DO(do_stub,                      \
                                          do_arch_blob,                 \
                                          do_arch_entry,                \
                                          do_arch_entry_init)           \
  do_arch_blob(preuniverse, 500)                                        \


#define STUBGEN_INITIAL_BLOBS_ARCH_DO(do_stub,                          \
                                      do_arch_blob,                     \
                                      do_arch_entry,                    \
                                      do_arch_entry_init)               \
  do_arch_blob(initial, PRODUCT_ONLY(20000) NOT_PRODUCT(21000) WINDOWS_ONLY(+1000))                      \
  do_stub(initial, verify_mxcsr)                                        \
  do_arch_entry(x86, initial, verify_mxcsr, verify_mxcsr_entry,         \
                verify_mxcsr_entry)                                     \
  do_stub(initial, get_previous_sp)                                     \
  do_arch_entry(x86, initial, get_previous_sp,                          \
                get_previous_sp_entry,                                  \
                get_previous_sp_entry)                                  \
  do_stub(initial, f2i_fixup)                                           \
  do_arch_entry(x86, initial, f2i_fixup, f2i_fixup, f2i_fixup)          \
  do_stub(initial, f2l_fixup)                                           \
  do_arch_entry(x86, initial, f2l_fixup, f2l_fixup, f2l_fixup)          \
  do_stub(initial, d2i_fixup)                                           \
  do_arch_entry(x86, initial, d2i_fixup, d2i_fixup, d2i_fixup)          \
  do_stub(initial, d2l_fixup)                                           \
  do_arch_entry(x86, initial, d2l_fixup, d2l_fixup, d2l_fixup)          \
  do_stub(initial, float_sign_mask)                                     \
  do_arch_entry(x86, initial, float_sign_mask, float_sign_mask,         \
                float_sign_mask)                                        \
  do_stub(initial, float_sign_flip)                                     \
  do_arch_entry(x86, initial, float_sign_flip, float_sign_flip,         \
                float_sign_flip)                                        \
  do_stub(initial, double_sign_mask)                                    \
  do_arch_entry(x86, initial, double_sign_mask, double_sign_mask,       \
                double_sign_mask)                                       \
  do_stub(initial, double_sign_flip)                                    \
  do_arch_entry(x86, initial, double_sign_flip, double_sign_flip,       \
                double_sign_flip)                                       \

#define STUBGEN_CONTINUATION_BLOBS_ARCH_DO(do_stub,                     \
                                           do_arch_blob,                \
                                           do_arch_entry,               \
                                           do_arch_entry_init)          \
  do_arch_blob(continuation, 3000)                                      \


#define STUBGEN_COMPILER_BLOBS_ARCH_DO(do_stub,                         \
                                       do_arch_blob,                    \
                                       do_arch_entry,                   \
                                       do_arch_entry_init)              \
  do_arch_blob(compiler, 109000 WINDOWS_ONLY(+2000))                    \
  do_stub(compiler, vector_float_sign_mask)                             \
  do_arch_entry(x86, compiler, vector_float_sign_mask,                  \
                vector_float_sign_mask, vector_float_sign_mask)         \
  do_stub(compiler, vector_float_sign_flip)                             \
  do_arch_entry(x86, compiler, vector_float_sign_flip,                  \
                vector_float_sign_flip, vector_float_sign_flip)         \
  do_stub(compiler, vector_double_sign_mask)                            \
  do_arch_entry(x86, compiler, vector_double_sign_mask,                 \
                vector_double_sign_mask, vector_double_sign_mask)       \
  do_stub(compiler, vector_double_sign_flip)                            \
  do_arch_entry(x86, compiler, vector_double_sign_flip,                 \
                vector_double_sign_flip, vector_double_sign_flip)       \
  do_stub(compiler, vector_all_bits_set)                                \
  do_arch_entry(x86, compiler, vector_all_bits_set,                     \
                vector_all_bits_set, vector_all_bits_set)               \
  do_stub(compiler, vector_int_mask_cmp_bits)                           \
  do_arch_entry(x86, compiler, vector_int_mask_cmp_bits,                \
                vector_int_mask_cmp_bits, vector_int_mask_cmp_bits)     \
  do_stub(compiler, vector_short_to_byte_mask)                          \
  do_arch_entry(x86, compiler, vector_short_to_byte_mask,               \
                vector_short_to_byte_mask, vector_short_to_byte_mask)   \
  do_stub(compiler, vector_byte_perm_mask)                              \
  do_arch_entry(x86, compiler,vector_byte_perm_mask,                    \
                vector_byte_perm_mask, vector_byte_perm_mask)           \
  do_stub(compiler, vector_int_to_byte_mask)                            \
  do_arch_entry(x86, compiler, vector_int_to_byte_mask,                 \
                vector_int_to_byte_mask, vector_int_to_byte_mask)       \
  do_stub(compiler, vector_int_to_short_mask)                           \
  do_arch_entry(x86, compiler, vector_int_to_short_mask,                \
                vector_int_to_short_mask, vector_int_to_short_mask)     \
  do_stub(compiler, vector_32_bit_mask)                                 \
  do_arch_entry(x86, compiler, vector_32_bit_mask,                      \
                vector_32_bit_mask, vector_32_bit_mask)                 \
  do_stub(compiler, vector_64_bit_mask)                                 \
  do_arch_entry(x86, compiler, vector_64_bit_mask,                      \
                vector_64_bit_mask, vector_64_bit_mask)                 \
  do_stub(compiler, vector_byte_shuffle_mask)                           \
  do_arch_entry(x86, compiler, vector_byte_shuffle_mask,                 \
                vector_byte_shuffle_mask, vector_byte_shuffle_mask)     \
  do_stub(compiler, vector_short_shuffle_mask)                          \
  do_arch_entry(x86, compiler, vector_short_shuffle_mask,               \
                vector_short_shuffle_mask, vector_short_shuffle_mask)   \
  do_stub(compiler, vector_int_shuffle_mask)                            \
  do_arch_entry(x86, compiler, vector_int_shuffle_mask,                 \
                vector_int_shuffle_mask, vector_int_shuffle_mask)       \
  do_stub(compiler, vector_long_shuffle_mask)                           \
  do_arch_entry(x86, compiler, vector_long_shuffle_mask,                \
                vector_long_shuffle_mask, vector_long_shuffle_mask)     \
  do_stub(compiler, vector_long_sign_mask)                              \
  do_arch_entry(x86, compiler, vector_long_sign_mask,                   \
                vector_long_sign_mask, vector_long_sign_mask)           \
  do_stub(compiler, vector_iota_indices)                                \
  do_arch_entry(x86, compiler, vector_iota_indices,                     \
                vector_iota_indices, vector_iota_indices)               \
  do_stub(compiler, vector_count_leading_zeros_lut)                     \
  do_arch_entry(x86, compiler, vector_count_leading_zeros_lut,          \
                vector_count_leading_zeros_lut,                         \
                vector_count_leading_zeros_lut)                         \
  do_stub(compiler, vector_reverse_bit_lut)                             \
  do_arch_entry(x86, compiler, vector_reverse_bit_lut,                  \
                vector_reverse_bit_lut, vector_reverse_bit_lut)         \
  do_stub(compiler, vector_reverse_byte_perm_mask_short)                \
  do_arch_entry(x86, compiler, vector_reverse_byte_perm_mask_short,     \
                vector_reverse_byte_perm_mask_short,                    \
                vector_reverse_byte_perm_mask_short)                    \
  do_stub(compiler, vector_reverse_byte_perm_mask_int)                  \
  do_arch_entry(x86, compiler, vector_reverse_byte_perm_mask_int,       \
                vector_reverse_byte_perm_mask_int,                      \
                vector_reverse_byte_perm_mask_int)                      \
  do_stub(compiler, vector_reverse_byte_perm_mask_long)                 \
  do_arch_entry(x86, compiler, vector_reverse_byte_perm_mask_long,      \
                vector_reverse_byte_perm_mask_long,                     \
                vector_reverse_byte_perm_mask_long)                     \
  do_stub(compiler, vector_popcount_lut)                                \
  do_arch_entry(x86, compiler, vector_popcount_lut,                     \
                vector_popcount_lut, vector_popcount_lut)               \
  do_stub(compiler, upper_word_mask)                                    \
  do_arch_entry(x86, compiler, upper_word_mask, upper_word_mask_addr,   \
                upper_word_mask_addr)                                   \
  do_stub(compiler, shuffle_byte_flip_mask)                             \
  do_arch_entry(x86, compiler, shuffle_byte_flip_mask,                  \
                shuffle_byte_flip_mask_addr,                            \
                shuffle_byte_flip_mask_addr)                            \
  do_stub(compiler, pshuffle_byte_flip_mask)                            \
  do_arch_entry(x86, compiler, pshuffle_byte_flip_mask,                 \
                pshuffle_byte_flip_mask_addr,                           \
                pshuffle_byte_flip_mask_addr)                           \
  /* x86_64 exposes these 3 stubs via a generic entry array */          \
  /* other arches use arch-specific entries */                          \
  /* this really needs rationalising */                                 \
  do_stub(compiler, string_indexof_linear_ll)                           \
  do_stub(compiler, string_indexof_linear_uu)                           \
  do_stub(compiler, string_indexof_linear_ul)                           \
  do_stub(compiler, pshuffle_byte_flip_mask_sha512)                     \
  do_arch_entry(x86, compiler, pshuffle_byte_flip_mask_sha512,          \
                pshuffle_byte_flip_mask_addr_sha512,                    \
                pshuffle_byte_flip_mask_addr_sha512)                    \
  do_stub(compiler, compress_perm_table32)                              \
  do_arch_entry(x86, compiler, compress_perm_table32,                   \
                compress_perm_table32, compress_perm_table32)           \
  do_stub(compiler, compress_perm_table64)                              \
  do_arch_entry(x86, compiler, compress_perm_table64,                   \
                compress_perm_table64, compress_perm_table64)           \
  do_stub(compiler, expand_perm_table32)                                \
  do_arch_entry(x86, compiler, expand_perm_table32,                     \
                expand_perm_table32, expand_perm_table32)               \
  do_stub(compiler, expand_perm_table64)                                \
  do_arch_entry(x86, compiler, expand_perm_table64,                     \
                expand_perm_table64, expand_perm_table64)               \
  do_stub(compiler, avx2_shuffle_base64)                                \
  do_arch_entry(x86, compiler, avx2_shuffle_base64,                     \
                avx2_shuffle_base64, base64_avx2_shuffle_addr)          \
  do_stub(compiler, avx2_input_mask_base64)                             \
  do_arch_entry(x86, compiler, avx2_input_mask_base64,                  \
                avx2_input_mask_base64,                                 \
                base64_avx2_input_mask_addr)                            \
  do_stub(compiler, avx2_lut_base64)                                    \
  do_arch_entry(x86, compiler, avx2_lut_base64,                         \
                avx2_lut_base64, base64_avx2_lut_addr)                  \
  do_stub(compiler, avx2_decode_tables_base64)                          \
  do_arch_entry(x86, compiler, avx2_decode_tables_base64,               \
                avx2_decode_tables_base64,                              \
                base64_AVX2_decode_tables_addr)                         \
  do_stub(compiler, avx2_decode_lut_tables_base64)                      \
  do_arch_entry(x86, compiler, avx2_decode_lut_tables_base64,           \
                avx2_decode_lut_tables_base64,                          \
                base64_AVX2_decode_LUT_tables_addr)                     \
  do_stub(compiler, shuffle_base64)                                     \
  do_arch_entry(x86, compiler, shuffle_base64, shuffle_base64,          \
                base64_shuffle_addr)                                    \
  do_stub(compiler, lookup_lo_base64)                                   \
  do_arch_entry(x86, compiler, lookup_lo_base64, lookup_lo_base64,      \
                base64_vbmi_lookup_lo_addr)                             \
  do_stub(compiler, lookup_hi_base64)                                   \
  do_arch_entry(x86, compiler, lookup_hi_base64, lookup_hi_base64,      \
                base64_vbmi_lookup_hi_addr)                             \
  do_stub(compiler, lookup_lo_base64url)                                \
  do_arch_entry(x86, compiler, lookup_lo_base64url,                     \
                lookup_lo_base64url,                                    \
                base64_vbmi_lookup_lo_url_addr)                         \
  do_stub(compiler, lookup_hi_base64url)                                \
  do_arch_entry(x86, compiler, lookup_hi_base64url,                     \
                lookup_hi_base64url,                                    \
                base64_vbmi_lookup_hi_url_addr)                         \
  do_stub(compiler, pack_vec_base64)                                    \
  do_arch_entry(x86, compiler, pack_vec_base64, pack_vec_base64,        \
                base64_vbmi_pack_vec_addr)                              \
  do_stub(compiler, join_0_1_base64)                                    \
  do_arch_entry(x86, compiler, join_0_1_base64, join_0_1_base64,        \
                base64_vbmi_join_0_1_addr)                              \
  do_stub(compiler, join_1_2_base64)                                    \
  do_arch_entry(x86, compiler, join_1_2_base64, join_1_2_base64,        \
                base64_vbmi_join_1_2_addr)                              \
  do_stub(compiler, join_2_3_base64)                                    \
  do_arch_entry(x86, compiler, join_2_3_base64, join_2_3_base64,        \
                base64_vbmi_join_2_3_addr)                              \
  do_stub(compiler, encoding_table_base64)                              \
  do_arch_entry(x86, compiler, encoding_table_base64,                   \
                encoding_table_base64, base64_encoding_table_addr)      \
  do_stub(compiler, decoding_table_base64)                              \
  do_arch_entry(x86, compiler, decoding_table_base64,                   \
                decoding_table_base64, base64_decoding_table_addr)      \


#define STUBGEN_FINAL_BLOBS_ARCH_DO(do_stub,                            \
                                    do_arch_blob,                       \
                                    do_arch_entry,                      \
                                    do_arch_entry_init)                 \
  do_arch_blob(final, 33000                                             \
               WINDOWS_ONLY(+22000) ZGC_ONLY(+20000))                   \

#endif // CPU_X86_STUBDECLARATIONS_HPP
