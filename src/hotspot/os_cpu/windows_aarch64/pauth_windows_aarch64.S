;
; Copyright (c) 2026, Microsoft and/or its affiliates. All rights reserved.
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

    ; Remove the AArch64 pointer signature from the pointer argument
    ;
    ; Both XPACI and XPACLRI can get the job done, with XPACI being more
    ; convenient since it can work with any general purpose register.  However,
    ; unlike XPACI, XPACLRI can be encoded using hint instructions, thus letting
    ; us compile this function using older compilers.
    ;
    ; Since XPACLRI implicitly uses the link register (x30) and we instead want
    ; to remove the signature from the function argument (in x0), we temporarily
    ; save x30 before restoring it at the end.

    ALIGN  4
    EXPORT pauth_drop_signature_win_arm64
    AREA pauth_text, CODE

pauth_drop_signature_win_arm64
    mov      x9, x30
    mov      x30, x0
    hint     #7
    mov      x0, x30
    mov      x30, x9
    ret

    END
