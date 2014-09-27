/*
 * Copyright (c) 1998, 2003, Oracle and/or its affiliates. All rights reserved.
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


#ifndef VIS_ASI_H
#define VIS_ASI_H

/* evolved from asm_asi.h in VSDK 1.0 */

#ifdef  __cplusplus
extern "C" {
#endif

/* ASI definitions for VIS */

#define         ASI_N                                   0x04
#define         ASI_NL                                  0x0C
#define         ASI_AIUP                                0x10
#define         ASI_AIUS                                0x11
#define         ASI_AIUPL                               0x18
#define         ASI_AIUSL                               0x19
#define         ASI_PHYS_USE_EC_L                       0x1C
#define         ASI_PHYS_BYPASS_EC_WITH_EBIT_L          0x1D
#define         ASI_DC_DATA                             0x46
#define         ASI_DC_TAG                              0x47
#define         ASI_UPA_CONTROL                         0x4A
#define         ASI_MONDO_SEND_CTRL                     0x48
#define         ASI_MONDO_RECEIVE_CTRL                  0x49
#define         ASI_AFSR                                0x4C
#define         ASI_AFAR                                0x4D
#define         ASI_EC_TAG_DATA                         0x4E
#define         ASI_ICACHE_DATA                         0x66
#define         ASI_IC_INSTR                            0x66
#define         ASI_IC_TAG                              0x67
#define         ASI_IC_PRE_DECODE                       0x6E
#define         ASI_IC_NEXT_FIELD                       0x6F
#define         ASI_BLK_AIUP                            0x70
#define         ASI_BLK_AIUS                            0x71
#define         ASI_EC                                  0x76
#define         ASI_BLK_AIUPL                           0x78
#define         ASI_BLK_AIUSL                           0x79
#define         ASI_P                                   0x80
#define         ASI_S                                   0x81
#define         ASI_PNF                                 0x82
#define         ASI_SNF                                 0x83
#define         ASI_PL                                  0x88
#define         ASI_SL                                  0x89
#define         ASI_PNFL                                0x8A
#define         ASI_SNFL                                0x8B
#define         ASI_PST8_P                              0xC0
#define         ASI_PST8_S                              0xC1
#define         ASI_PST16_P                             0xC2
#define         ASI_PST16_S                             0xC3
#define         ASI_PST32_P                             0xC4
#define         ASI_PST32_S                             0xC5
#define         ASI_PST8_PL                             0xC8
#define         ASI_PST8_SL                             0xC9
#define         ASI_PST16_PL                            0xCA
#define         ASI_PST16_SL                            0xCB
#define         ASI_PST32_PL                            0xCC
#define         ASI_PST32_SL                            0xCD
#define         ASI_FL8_P                               0xD0
#define         ASI_FL8_S                               0xD1
#define         ASI_FL16_P                              0xD2
#define         ASI_FL16_S                              0xD3
#define         ASI_FL8_PL                              0xD8
#define         ASI_FL8_SL                              0xD9
#define         ASI_FL16_PL                             0xDA
#define         ASI_FL16_SL                             0xDB
#define         ASI_COMMIT_P                            0xE0
#define         ASI_COMMIT_S                            0xE1
#define         ASI_BLK_P                               0xF0
#define         ASI_BLK_S                               0xF1
#define         ASI_BLK_PL                              0xF8
#define         ASI_BLK_SL                              0xF9

#define         ASI_NUCLEUS                             0x04
#define         ASI_NUCLEUS_LITTLE                      0x0C
#define         ASI_AS_IF_USER_PRIMARY                  0x10
#define         ASI_AS_IF_USER_SECONDARY                0x11
#define         ASI_PHYS_USE_EC                         0x14
#define         ASI_PHYS_BYPASS_EC_WITH_EBIT            0x15
#define         ASI_AS_IF_USER_PRIMARY_LITTLE           0x18
#define         ASI_AS_IF_USER_SECONDARY_LITTLE         0x19
#define         ASI_PHYS_USE_EC_LITTLE                  0x1C
#define         ASI_PHYS_BYPASS_EC_WITH_EBIT_LITTLE     0x1D
#define         ASI_LSU_CONTROL_REG                     0x45
#define         ASI_DCACHE_DATA                         0x46
#define         ASI_DCACHE_TAG                          0x47
#define         ASI_INTR_DISPATCH_STATUS                0x48
#define         ASI_INTR_RECEIVE                        0x49
#define         ASI_UPA_CONFIG_REG                      0x4A
#define         ASI_ESTATE_ERROR_EN_REG                 0x4B
#define         ASI_ASYNC_FAULT_STATUS                  0x4C
#define         ASI_ASYNC_FAULT_ADDR                    0x4D
#define         ASI_ECACHE_TAG_DATA                     0x4E
#define         ASI_OBSERVABILITY_REG                   0x4F
#define         ASI_IMMU                                0x50
#define         ASI_IMU_TSB_BASE                        0x50
#define         ASI_IMU_TAG_ACCESS                      0x50
#define         ASI_IMU_SFSR                            0x50
#define         ASI_IMU_TAG_TARGET                      0x50
#define         ASI_IMU_TSB_POINTER_8K                  0x51
#define         ASI_IMU_TSB_POINTER_64K                 0x52
#define         ASI_IMU_DATAIN                          0x54
#define         ASI_IMMU_DATA_IN                        0x54
#define         ASI_IMU_DATA_ACCESS                     0x55
#define         ASI_IMU_TAG_READ                        0x56
#define         ASI_IMU_DEMAP                           0x57
#define         ASI_DMMU                                0x58
#define         ASI_PRIMARY_CONTEXT                     0x58
#define         ASI_SECONDARY_CONTEXT                   0x58
#define         ASI_DMU_TSB_BASE                        0x58
#define         ASI_DMU_TAG_ACCESS                      0x58
#define         ASI_DMU_TAG_TARGET                      0x58
#define         ASI_DMU_SFSR                            0x58
#define         ASI_DMU_SFAR                            0x58
#define         ASI_DMU_VA_WATCHPOINT                   0x58
#define         ASI_DMU_PA_WATCHPOINT                   0x58
#define         ASI_DMU_TSB_POINTER_8K                  0x59
#define         ASI_DMU_TSB_POINTER_64K                 0x5A
#define         ASI_DMU_TSB_POINTER_DIRECT              0x5B
#define         ASI_DMU_DATAIN                          0x5C
#define         ASI_DMMU_DATA_IN                        0x5C
#define         ASI_DMU_DATA_ACCESS                     0x5D
#define         ASI_DMU_TAG_READ                        0x5E
#define         ASI_DMU_DEMAP                           0x5F
#define         ASI_ICACHE_INSTR                        0x66
#define         ASI_ICACHE_TAG                          0x67
#define         ASI_ICACHE_PRE_DECODE                   0x6E
#define         ASI_ICACHE_NEXT_FIELD                   0x6F
#define         ASI_BLOCK_AS_IF_USER_PRIMARY            0x70
#define         ASI_BLOCK_AS_IF_USER_SECONDARY          0x71
#define         ASI_EXT                                 0x76
#define         ASI_ECACHE                              0x76
#define         ASI_ECACHE_DATA                         0x76
#define         ASI_ECACHE_TAG                          0x76
#define         ASI_SDB_INTR                            0x77
#define         ASI_SDBH_ERR_REG                        0x77
#define         ASI_SDBL_ERR_REG                        0x77
#define         ASI_SDBH_CONTROL_REG                    0x77
#define         ASI_SDBL_CONTROL_REG                    0x77
#define         ASI_INTR_DISPATCH                       0x77
#define         ASI_INTR_DATA0                          0x77
#define         ASI_INTR_DATA1                          0x77
#define         ASI_INTR_DATA2                          0x77
#define         ASI_BLOCK_AS_IF_USER_PRIMARY_LITTLE     0x78
#define         ASI_BLOCK_AS_IF_USER_SECONDARY_LITTLE   0x79
#define         ASI_PRIMARY                             0x80
#define         ASI_SECONDARY                           0x81
#define         ASI_PRIMARY_NO_FAULT                    0x82
#define         ASI_SECONDARY_NO_FAULT                  0x83
#define         ASI_PRIMARY_LITTLE                      0x88
#define         ASI_SECONDARY_LITTLE                    0x89
#define         ASI_PRIMARY_NO_FAULT_LITTLE             0x8A
#define         ASI_SECONDARY_NO_FAULT_LITTLE           0x8B
#define         ASI_PST8_PRIMARY                        0xC0
#define         ASI_PST8_SECONDARY                      0xC1
#define         ASI_PST16_PRIMARY                       0xC2
#define         ASI_PST16_SECONDARY                     0xC3
#define         ASI_PST32_PRIMARY                       0xC4
#define         ASI_PST32_SECONDARY                     0xC5
#define         ASI_PST8_PRIMARY_LITTLE                 0xC8
#define         ASI_PST8_SECONDARY_LITTLE               0xC9
#define         ASI_PST16_PRIMARY_LITTLE                0xCA
#define         ASI_PST16_SECONDARY_LITTLE              0xCB
#define         ASI_PST32_PRIMARY_LITTLE                0xCC
#define         ASI_PST32_SECONDARY_LITTLE              0xCD
#define         ASI_FL8_PRIMARY                         0xD0
#define         ASI_FL8_SECONDARY                       0xD1
#define         ASI_FL16_PRIMARY                        0xD2
#define         ASI_FL16_SECONDARY                      0xD3
#define         ASI_FL8_PRIMARY_LITTLE                  0xD8
#define         ASI_FL8_SECONDARY_LITTLE                0xD9
#define         ASI_FL16_PRIMARY_LITTLE                 0xDA
#define         ASI_FL16_SECONDARY_LITTLE               0xDB
#define         ASI_COMMIT_PRIMARY                      0xE0
#define         ASI_COMMIT_SECONDARY                    0xE1
#define         ASI_BLOCK_PRIMARY                       0xF0
#define         ASI_BLOCK_SECONDARY                     0xF1
#define         ASI_BLOCK_PRIMARY_LITTLE                0xF8
#define         ASI_BLOCK_SECONDARY_LITTLE              0xF9

#ifdef  __cplusplus
}
#endif

#endif  /* VIS_ASI_H */
