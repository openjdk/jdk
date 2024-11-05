---
# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
#

title: 'JABSWITCH(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

jabswitch - enable or disable the Java Access Bridge

## Synopsis

`jabswitch` \[ -enable|/enable | -disable|/disable | -version|/version | -?|/? \]

## Options

`-enable`
or
`/enable`
:   Enables the Java Access Bridge

`-disable`
or
`/disable`
:   Disables the Java Access Bridge

`-version`
or
`/version`
:   Displays version information for the `jabswitch` command.

`-?`
or
`/?`
:   Displays usage information for the `jabswitch` command.

## Description

The `jabswitch` command is a utility program that enables the
Java Access Bridge to be loaded by the JDK on Windows platforms.
The Java Access Bridge is used by Assistive Technologies
to interact with Java Accessibility APIs of the Java SE platform.
To have any effect, the assistive technology must support the Java Access Bridge.

This command creates or updates a file named `.accessibility.properties`,
in the user's home directory. When selecting the `-enable` option, the file
is populated with the information needed to load the Java Access Bridge.
This file is then read and used in accordance with the specification of the
Java SE
[`java.awt.Toolkit.getDefaultToolkit()`](../../api/java.desktop/java/awt/Toolkit.html#getDefaultToolkit())
API, on initialization.

Note: This command is only provided with JDK for Windows.
