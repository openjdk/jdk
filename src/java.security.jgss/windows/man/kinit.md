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

title: 'KINIT(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

kinit - obtain and cache Kerberos ticket-granting tickets

## Synopsis

Initial ticket request:

`kinit` \[`-A`\] \[`-f`\] \[`-p`\] \[`-c` *cache\_name*\] \[`-l` *lifetime*\]
\[`-r` *renewable\_time*\] \[\[`-k` \[`-t` *keytab\_file\_name*\]\]
\[*principal*\] \[*password*\]

Renew a ticket:

`kinit` `-R` \[`-c` *cache\_name*\] \[*principal*\]

## Description

This tool is similar in functionality to the `kinit` tool that is commonly
found in other Kerberos implementations, such as SEAM and MIT Reference
implementations. The user must be registered as a principal with the Key
Distribution Center (KDC) prior to running `kinit`.

By default, on Windows, a cache file named *USER\_HOME*`\krb5cc_`*USER\_NAME*
is generated.

The identifier *USER\_HOME* is obtained from the `java.lang.System` property
`user.home`. *USER\_NAME* is obtained from the `java.lang.System` property
`user.name`. If *USER\_HOME* is null, the cache file is stored in the current
directory from which the program is running. *USER\_NAME* is the operating
system's login user name. This user name could be different than the user's
principal name. For example, on Windows, the cache file could be
`C:\Windows\Users\duke\krb5cc_duke`, in which `duke` is the *USER\_NAME* and
`C:\Windows\Users\duke` is the *USER\_HOME*.

By default, the keytab name is retrieved from the Kerberos configuration file.
If the keytab name isn't specified in the Kerberos configuration file, the
kinit tool assumes that the name is *USER\_HOME*`\krb5.keytab`

If you don't specify the password using the *password* option on the command
line, the `kinit` tool prompts you for the password.

**Note:**

The `password` option is provided only for testing purposes. Don't specify your
password in a script or provide your password on the command line. Doing so
will compromise your password.

## Commands

You can specify one of the following commands. After the command, specify the
options for it.

[`-A`]{#option-A}
:   Doesn't include addresses.

[`-f`]{#option-f}
:   Issues a forwardable ticket.

[`-p`]{#option-p}
:   Issues a proxiable ticket.

[`-c`]{#option-c} *cache\_name*
:   The cache name (for example, `FILE:D:\temp\mykrb5cc`).

[`-l`]{#option-l} *lifetime*
:   Sets the lifetime of a ticket. The value can be one of "h:m[:s]",
    "NdNhNmNs", and "N". See the [MIT krb5 Time Duration definition](
    http://web.mit.edu/kerberos/krb5-1.17/doc/basic/date_format.html#duration)
    for more information.

[`-r`]{#option-r} *renewable\_time*
:   Sets the total lifetime that a ticket can be renewed.

[`-R`]{#option-R}
:   Renews a ticket.

[`-k`]{#option-k}
:   Uses keytab

[`-t`]{#option-t} *keytab\_filename*
:   The keytab name (for example, `D:\winnt\profiles\duke\krb5.keytab`).

*principal*
:   The principal name (for example, `duke@example.com`).

*password*
:   The *principal*'s Kerberos password. **Don't specify this on the command
    line or in a script.**

Run `kinit -help` to display the instructions above.

## Examples

Requests credentials valid for authentication from the current client host, for
the default services, storing the credentials cache in the default location
(`C:\Windows\Users\duke\krb5cc_duke`):

>   `kinit duke@example.com`

Requests proxiable credentials for a different principal and store these
credentials in a specified file cache:

>   `kinit -l 1h -r 10h duke@example.com`

Requests a TGT for the specified principal that will expire in 1 hour but
is renewable for up to 10 hours. Users must renew a ticket before it has
expired. The renewed ticket can be renewed repeatedly within 10 hours
from its initial request.

>   `kinit -R duke@example.com`

Renews an existing renewable TGT for the specified principal.

>   `kinit -p -c FILE:C:\Windows\Users\duke\credentials\krb5cc_cafebeef
    cafebeef@example.com`

Requests proxiable and forwardable credentials for a different principal and
stores these credentials in a specified file cache:

>   `kinit -f -p -c FILE:C:\Windows\Users\duke\credentials\krb5cc_cafebeef
    cafebeef@example.com`

Displays the help menu for the `kinit` tool:

>   `kinit -help`
