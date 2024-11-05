---
# Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
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

`-c`
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

`-k`
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
