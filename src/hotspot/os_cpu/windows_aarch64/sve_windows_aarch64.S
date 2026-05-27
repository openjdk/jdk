;
; Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
; DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
;
; This code is free software; you can redistribute it and/or modify it
; under the terms of the GNU General Public License version 2 only, as
; published by the Free Software Foundation.
;
; This code is distributed in the hope that it will be useful, but WITHOUT
; ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
; FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
; version 2 for more details (a copy is included in the LICENSE file that
; accompanied this code).
;
; You should have received a copy of the GNU General Public License version
; 2 along with this work; if not, write to the Free Software Foundation,
; Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
;
; Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
; or visit www.oracle.com if you need additional information or have any
; questions.
;

    ; Support for int get_sve_vector_length();
    ;
    ; Returns the current SVE vector length in bytes.
    ; This function uses the RDVL instruction which reads a multiple of the
    ; vector register size into a scalar register.
    ;
    ; Note: This function will fault if SVE is not available or enabled.  The
    ; caller must ensure SVE support is detected before calling.

    ALIGN  4
    EXPORT get_sve_vector_length
    AREA sve_text, CODE

get_sve_vector_length
    ; Older versions of Visual Studio aren't aware of SVE mnemonics, so we use
    ; the raw instruction encoding to satisfy the compiler.  This function call
    ; is gated by `VM_Version::supports_sve()`, so this instruction will never
    ; run on non-SVE hardware.
    ;
    ; See https://www.scs.stanford.edu/~zyedidia/arm64/rdvl_r_i.html for a quick
    ; reference to the instruction encoding.

    DCD      0x04BF5020   ; rdvl x0, #1 (i.e. x0 = 1 * vector_length_in_bytes)
    ret

    END
