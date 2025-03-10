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

#include "macroAssembler_x86.hpp"

ATTRIBUTE_ALIGNED(16) static const juint _ONES[] = {
    0x00000000UL, 0x3ff00000UL, 0x00000000UL, 0xbff00000UL
};
address MacroAssembler::ONES = (address)_ONES;

ATTRIBUTE_ALIGNED(16) static const juint _PI4_INV[] = {
    0x6dc9c883UL, 0x3ff45f30UL
};
address MacroAssembler::PI4_INV = (address)_PI4_INV;

ATTRIBUTE_ALIGNED(16) static const juint _PI4X3[] = {
    0x54443000UL, 0xbfe921fbUL, 0x3b39a000UL, 0x3d373dcbUL, 0xe0e68948UL,
    0xba845c06UL
};
address MacroAssembler::PI4X3 = (address)_PI4X3;

ATTRIBUTE_ALIGNED(16) static const juint _PI4X4[] = {
    0x54400000UL, 0xbfe921fbUL, 0x1a600000UL, 0xbdc0b461UL, 0x2e000000UL,
    0xbb93198aUL, 0x252049c1UL, 0xb96b839aUL
};
address MacroAssembler::PI4X4 = (address)_PI4X4;

ATTRIBUTE_ALIGNED(16) static const juint _L_2IL0FLOATPACKET_0[] = {
    0xffffffffUL, 0x7fffffffUL, 0x00000000UL, 0x00000000UL
};
address MacroAssembler::L_2IL0FLOATPACKET_0 = (address)_L_2IL0FLOATPACKET_0;
