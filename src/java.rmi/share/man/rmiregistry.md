---
# Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

title: 'RMIREGISTRY(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

rmiregistry - create and start a remote object registry on the specified port
on the current host

## Synopsis

`rmiregistry` \[*options*\] \[*port*\]

*options*
:   This represents the option for the `rmiregistry` command. See
    [Options]

*port*
:   The number of a port on the current host at which to start the remote
    object registry.

## Description

The `rmiregistry` command creates and starts a remote object registry on the
specified port on the current host. If the port is omitted, then the registry
is started on port 1099. The `rmiregistry` command produces no output and is
typically run in the background, for example:

>   `rmiregistry &`

A remote object registry is a bootstrap naming service that's used by RMI
servers on the same host to bind remote objects to names. Clients on local and
remote hosts can then look up remote objects and make remote method
invocations.

The registry is typically used to locate the first remote object on which an
application needs to call methods. That object then provides
application-specific support for finding other objects.

The methods of the `java.rmi.registry.LocateRegistry` class are used to get a
registry operating on the local host or local host and port.

The URL-based methods of the `java.rmi.Naming` class operate on a registry and
can be used to:

-   Bind the specified name to a remote object

-   Return an array of the names bound in the registry

-   Return a reference, a stub, for the remote object associated with the
    specified name

-   Rebind the specified name to a new remote object

-   Destroy the binding for the specified name that's associated with a remote
    object

## Options

`-J`*option*
:   Used with any Java option to pass the *option* following the `-J` (no
    spaces between the `-J` and the option) to the Java interpreter.
