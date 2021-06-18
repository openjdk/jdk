#
# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

# Use OpenSSL 1.1.0i or above versions, earlier versions may generate different
# info and test fail.
openssl pkcs12 -in ksnormal -passin pass:changeit -info -nokeys -nocerts 2> t2 || exit 20
grep "MAC: sha256, Iteration 10000" t2 || exit 21
grep "Shrouded Keybag: PBES2, PBKDF2, AES-256-CBC, Iteration 10000, PRF hmacWithSHA256" t2 || exit 23
grep "PKCS7 Encrypted data: PBES2, PBKDF2, AES-256-CBC, Iteration 10000, PRF hmacWithSHA256" t2 || exit 24

openssl pkcs12 -in ksnormaldup -passin pass:changeit -info -nokeys -nocerts 2> t22 || exit 25
diff t2 t22 || exit 26

openssl pkcs12 -in ksnopass -passin pass:changeit -info -nokeys -nocerts && exit 30

openssl pkcs12 -in ksnopass -passin pass:changeit -info -nokeys -nocerts -nomacver 2> t3 || exit 31
grep "PKCS7 Encrypted data:" t3 && exit 33
grep "Shrouded Keybag: PBES2, PBKDF2, AES-256-CBC, Iteration 10000, PRF hmacWithSHA256" t3 || exit 34
grep "Shrouded Keybag: pbeWithSHA1And128BitRC4, Iteration 10000" t3 || exit 35

openssl pkcs12 -in ksnopassdup -passin pass:changeit -info -nokeys -nocerts -nomacver 2> t33 || exit 36
diff t3 t33 || exit 37

openssl pkcs12 -in ksnewic -passin pass:changeit -info -nokeys -nocerts 2> t4 || exit 40
grep "MAC: sha256, Iteration 5555" t4 || exit 41
grep "Shrouded Keybag: PBES2, PBKDF2, AES-256-CBC, Iteration 7777, PRF hmacWithSHA256" t4 || exit 43
grep "Shrouded Keybag: pbeWithSHA1And128BitRC4, Iteration 10000" t4 || exit 44
grep "PKCS7 Encrypted data: PBES2, PBKDF2, AES-256-CBC, Iteration 6666, PRF hmacWithSHA256" t4 || exit 45

openssl pkcs12 -in ksnewicdup -passin pass:changeit -info -nokeys -nocerts 2> t44 || exit 46
diff t4 t44 || exit 47

echo Succeed