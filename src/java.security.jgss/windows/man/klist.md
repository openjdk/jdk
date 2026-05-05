---
# Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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

title: 'KLIST(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

klist - display the entries in the local credentials cache and key table

## Synopsis

`klist` \[`-c` \[`-f`\] \[`-e`\] \[`-a` \[`-n`\]\]\] \[`-k` \[`-t`\] \[`-K`\]\]
\[*name*\] \[`-help`\]

## Description

The `klist` tool displays the entries in the local credentials cache and key
table. After you modify the credentials cache with the `kinit` tool or modify
the keytab with the `ktab` tool, the only way to verify the changes is to view
the contents of the credentials cache or keytab using the `klist` tool. The
`klist` tool doesn't change the Kerberos database.

## Commands

[`-c`]{#option-c}
:   Specifies that the credential cache is to be listed.

    The following are the options for credential cache entries:

    `-f`
    :   Show credential flags.

    `-e`
    :   Show the encryption type.

    `-a`
    :   Show addresses.

    `-n`
    :   If the `-a` option is specified, don't reverse resolve addresses.

[`-k`]{#option-k}
:   Specifies that key tab is to be listed.

    List the keytab entries. The following are the options for keytab entries:

    `-t`
    :   Show keytab entry timestamps.

    `-K`
    :   Show keytab entry DES keys.

    `-e`
    :   Shows keytab entry key type.

*name*
:   Specifies the credential cache name or the keytab name. File-based cache or
    keytab's prefix is `FILE:`. If the name isn't specified, the `klist` tool
    uses default values for the cache name and keytab. The `kinit`
    documentation lists these default values.

`-help`
:   Displays instructions.

## Examples

List entries in the keytable specified including keytab entry timestamps and
DES keys:

>   `klist -k -t -K FILE:\temp\mykrb5cc`

List entries in the credentials cache specified including credentials flag and
address list:

>   `klist -c -f FILE:\temp\mykrb5cc`
