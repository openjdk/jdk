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

title: 'SERIALVER(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

serialver - return the `serialVersionUID` for one or more classes in a form
suitable for copying into an evolving class

## Synopsis

`serialver` \[*options*\] \[*classnames*\]

*options*
:   This represents the command-line options for the `serialver` command. See
    [Options for serialver].

*classnames*
:   The classes for which `serialVersionUID` is to be returned.

## Description

The `serialver` command returns the `serialVersionUID` for one or more classes
in a form suitable for copying into an evolving class. When called with no
arguments, the `serialver` command prints a usage line.

## Options for serialver

`-classpath` *path-files*
:   Sets the search path for application classes and resources. Separate
    classes and resources with a colon (:).

`-J`*option*
:   Passes the specified *option* to the Java Virtual Machine, where *option*
    is one of the options described on the reference page for the Java
    application launcher. For example, `-J-Xms48m` sets the startup memory to
    48 MB.

## Notes

The `serialver` command loads and initializes the specified classes in its
virtual machine, and by default, it doesn't set a security manager. If the
`serialver` command is to be run with untrusted classes, then a security
manager can be set with the following option:

>   `-J-Djava.security.manager`

When necessary, a security policy can be specified with the following option:

>   `-J-Djava.security.policy=`*policy\_file*
