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

title: 'KTAB(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

ktab - manage the principal names and service keys stored in a local key
table

## Synopsis

`ktab` \[*commands*\] \[*options*\]

\[*commands*\] \[*options*\]
:   Lists the keytab name and entries, adds new key entries to the keytab,
    deletes existing key entries, and displays instructions. See [Commands and
    Options].

## Description

The `ktab` enables the user to manage the principal names and service keys
stored in a local key table. Principal and key pairs listed in the keytab
enable services running on a host to authenticate themselves to the Key
Distribution Center (KDC).

Before configuring a server to use Kerberos, you must set up a keytab on the
host running the server. Note that any updates made to the keytab using the
`ktab` tool don't affect the Kerberos database.

A *keytab* is a host's copy of its own keylist, which is analogous to a user's
password. An application server that needs to authenticate itself to the Key
Distribution Center (KDC) must have a keytab which contains its own principal
and key. If you change the keys in the keytab, you must also make the
corresponding changes to the Kerberos database. The `ktab` tool enables you to
list, add, update or delete principal names and key pairs in the key table.
None of these operations affect the Kerberos database.

## Security Alert

Don't specify your password on the command line. Doing so can be a security
risk. For example, an attacker could discover your password while running the
UNIX `ps` command.

Just as it is important for users to protect their passwords, it is equally
important for hosts to protect their keytabs. You should always store keytab
files on the local disk and make them readable only by root. You should never
send a keytab file over a network in the clear.

## Commands and Options

[`-l`]{#command-l} \[`-e`\] \[`-t`\]
:   Lists the keytab name and entries. When `-e` is specified, the encryption
    type for each entry is displayed. When `-t` is specified, the timestamp for
    each entry is displayed.

[`-a`]{#command-a} *principal\_name* \[*password*\] \[`-n` *kvno*\] \[`-s` *salt* \| `-f`\] \[`-append`\]
:   Adds new key entries to the keytab for the given principal name with an
    optional *password*. If a *kvno* is specified, new keys' Key Version
    Numbers equal to the value, otherwise, automatically incrementing the Key
    Version Numbers. If *salt* is specified, it will be used instead of the
    default salt. If `-f` is specified, the KDC will be contacted to
    fetch the salt. If `-append` is specified, new keys are appended to the
    keytab, otherwise, old keys for the same principal are removed.

    No changes are made to the Kerberos database. **Don't specify the password
    on the command line or in a script.** This tool will prompt for a password
    if it isn't specified.

[`-d`]{#command-d} *principal\_name* \[`-f`\] \[`-e` *etype*\] \[*kvno* \| `all`\| `old`\]
:   Deletes key entries from the keytab for the specified principal. No changes
    are made to the Kerberos database.

    -   If *kvno* is specified, the tool deletes keys whose Key Version Numbers
        match kvno. If `all` is specified, delete all keys.

    -   If `old` is specified, the tool deletes all keys except those with the
        highest *kvno*. The default action is `all`.

    -   If *etype* is specified, the tool only deletes keys of this encryption
        type. *etype* should be specified as the numeric value *etype* defined
        in RFC 3961, section 8. A prompt to confirm the deletion is displayed
        unless `-f` is specified.

    When *etype* is provided, only the entry matching this encryption type is
    deleted. Otherwise, all entries are deleted.

`-help`
:   Displays instructions.

## Common Options

This option can be used with the `-l`, `-a` or `-d` commands.

[`-k`]{#option-k} *keytab name*
:   Specifies the keytab name and path with the `FILE:` prefix.

## Examples

-   Lists all the entries in the default keytable

    >   `ktab -l`

-   Adds a new principal to the key table (note that you will be prompted for
    your password)

    >   `ktab -a duke@example.com`

-   Deletes a principal from the key table

    >   `ktab -d duke@example.com`
