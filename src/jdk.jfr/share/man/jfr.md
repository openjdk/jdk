---
# Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

title: 'JFR(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

jfr - print and manipulate Flight Recorder files

## Synopsis

To print the contents of a flight recording to standard out:

`jfr` `print` \[*options*\] *file*

To display aggregated event data on standard out:

`jfr` `view` \[*options*\] *file*

To configure a .jfc settings file:

`jfr` `configure` \[*options*\]

To print metadata information about flight recording events:

`jfr` `metadata` \[*file*\]

To view the summary statistics for a flight recording file:

`jfr` `summary` *file*

To remove events from  a flight recording file:

`jfr` `scrub` \[*options*\] *file*

To assemble chunk files into a flight recording file:

`jfr` `assemble` *repository* *file*

To disassemble a flight recording file into chunk files:

`jfr` `disassmble` \[*options*\] *file*

*options*
:   Optional: Specifies command-line options separated by spaces. See the
individual subcomponent sections for descriptions of the available options.

*file*
:   Specifies the name of the target flight recording file (`.jfr`).

*repository*
:   Specifies the location of the chunk files which are to be assembled into
a flight recording.

## Description

The `jfr` command provides a tool for interacting with flight recorder files
(`.jfr`). The main function is to filter, summarize and output flight
recording files into human readable format. There is also support for scrubbing,
merging and splitting recording files.

Flight recording files are created and saved as binary formatted files. Having
a tool that can extract the contents from a flight recording and manipulate the
contents and translate them into human readable format helps developers to debug
performance issues with Java applications.

### Subcommands

The `jfr` command has several subcommands:

- `print`
- `view`
- `configure`
- `metadata`
- `summary`
- `scrub`
- `assemble`
- `disassemble`

#### `jfr print` subcommand

Use `jfr print` to print the contents of a flight recording file to standard out.

The syntax is:

`jfr print` \[`--xml`|`--json`|`--exact`\]
           \[`--categories` <*filters*>\]
           \[`--events` <*filters*>\]
           \[`--stack-depth` <*depth*>\]
           <*file*>

where:

<a id="print-option-xml">`--xml`</a>
: Print the recording in XML format.

<a id="print-option-json">`--json`</a>
: Print the recording in JSON format.

<a id="print-option-exact">`--exact`</a>
: Pretty-print numbers and timestamps with full precision.

<a id="print-option-categories">`--categories` <*filters*></a>
: Select events matching a category name.
The filter is a comma-separated list of names,
simple and/or qualified, and/or quoted glob patterns.

<a id="print-option-events">`--events` <*filters*></a>
: Select events matching an event name.
The filter is a comma-separated list of names,
simple and/or qualified, and/or quoted glob patterns.

<a id="print-option-stack-depth">`--stack-depth` <*depth*></a>
: Number of frames in stack traces, by default 5.

<a id="print-option-file"><*file*></a>
: Location of the recording file (`.jfr`)

The default format for printing the contents of the flight recording file is human
readable form unless either `xml` or `json` is specified. These options provide
machine-readable output that can be further parsed or processed by user created scripts.

Use `jfr --help print` to see example usage of filters.

To reduce the amount of data displayed, it is possible to filter out events or
categories of events. The filter operates on the symbolic name of an event,
set by using the `@Name` annotation, or the category name, set by using
the `@Category` annotation. If multiple filters are used, events from both
filters will be included. If no filter is used, all the events will be printed.
If a combination of a category filter and event filter is used, the selected events
will be the union of the two filters.

For example, to show all GC events and the CPULoad event, the following command could be used:

`jfr print --categories GC --events CPULoad recording.jfr`

Event values are formatted according to the content types that are being used.
For example, a field with the `jdk.jfr.Percentage` annotation that has the value 0.52
is formatted as 52%.

Stack traces are by default truncated to 5 frames, but the number can be
increased/decreased using the `--stack-depth` command-line option.

#### `jfr view` subcommand

Use `jfr view` to aggregate and display event data on standard out.

The syntax is:

 `jfr view` \[`--verbose`\]
           \[`--width` <*integer*>\]
           \[`--truncate` <*mode*>\]
           \[`--cell-height` <*integer*>\]
            <*view*>
            <*file*>

where:

<a id="view-option-verbose">`--verbose`</a>
: Displays the query that makes up the view.

<a id="view-option-width">`--width` <*integer*></a>
: The width of the view in characters. Default value depends on the view.

<a id="view-option-truncate">`--truncate` <*mode*></a>
: How to truncate content that exceeds space in a table cell.
Mode can be 'beginning' or 'end'. Default value is 'end'.

<a id="view-option-cell-height">`--cell-height` <*integer*></a>
: Maximum number of rows in a table cell. Default value depends on the view.

<a id="view-option-view"><*view*></a>
: Name of the view or event type to display. Use `jfr --help view` to see a
list of available views.

<a id="view-option-file"><*file*></a>
: Location of the recording file (.jfr)

The <*view*> parameter can be an event type name. Use the `jfr view types <file>`
to see a list. To display all views, use `jfr view all-views <file>`. To display
all events, use `jfr view all-events <file>`.

#### `jfr configure` subcommand

Use `jfr configure` to configure a .jfc settings file.

The syntax is:

 `jfr configure` \[--interactive\] \[--verbose\]
               \[--input &lt;files&gt;\] [--output &lt;file&gt;\]
               \[option=value\]* \[event-setting=value\]*

<a id="configure-option-interactive">`--interactive`</a>
: Interactive mode where the configuration is determined by a set of questions.

<a id="configure-option-verbose">`--verbose`</a>
: Displays the modified settings.

<a id="configure-option-input">`--input` <*files*></a>
: A comma-separated list of .jfc files from which the new configuration is
based. If no file is specified, the default file in the JDK is used
(default.jfc). If 'none' is specified, the new configuration starts empty.

<a id="configure-option-output">`--output` <*file*></a>
: The filename of the generated output file. If not specified, the filename
custom.jfc will be used.

<a id="configure-option-value">*option=value*</a>
: The option value to modify. To see available options,
use `jfr help configure`

<a id="configure-option-event-setting">*event-setting=value*</a>
: The event setting value to modify. Use the form:
  <*event-name*>#<*setting-name*>=<*value*>
To add a new event setting, prefix the event name with '+'.

The whitespace delimiter can be omitted for timespan values, i.e. 20ms. For
more information about the settings syntax, see Javadoc of the jdk.jfr package.

#### `jfr metadata` subcommand

Use `jfr metadata` to display information about events, such as event
names, categories and field layout within a flight recording file.

The syntax is:

`jfr metadata` \[--categories &lt;filter&gt;\]
              \[--events &lt;filter&gt;\]
              \[&lt;file&gt;\]

<a id="metadata-option-categories">`--categories` <*filter*></a>
: Select events matching a category name. The filter is a comma-separated
list of names, simple and/or qualified, and/or quoted glob patterns.

<a id="metadata-option-events">`--events` <*filter*></a>
: Select events matching an event name. The filter is a comma-separated
list of names, simple and/or qualified, and/or quoted glob patterns.

<a id="metadata-option-file"><*file*></a>
: Location of the recording file (.jfr)

If the &lt;file&gt; parameter is omitted, metadata from the JDK where
the 'jfr' tool is located will be used.

#### `jfr summary` subcommand

Use `jfr summary` to print statistics for a recording. For example, a summary can
illustrate the number of recorded events and how much disk space they used. This
is useful for troubleshooting and understanding the impact of event settings.

The syntax is:

`jfr summary` <*file*>

where:

<a id="summary-option-file"><*file*></a>
: Location of the flight recording file (`.jfr`)

#### `jfr scrub` subcommand

Use `jfr scrub` to remove sensitive contents from a file or to reduce its size.

The syntax is:

`jfr scrub` \[--include-events <*filter*>\]
           \[--exclude-events <*filter*>\]
           \[--include-categories <*filter*>\]
           \[--exclude-categories <*filter*>\]
           \[--include-threads <*filter*>\]
           \[--exclude-threads <*filter*>\]
           <*input-file*>
           \[<*output-file*>\]

<a id="scrub-option-include-events">`--include-events` <*filter*></a>
: Select events matching an event name.

<a id="scrub-option-exclude-events">`--exclude-events` <*filter*></a>
: Exclude events matching an event name.

<a id="scrub-option-include-categories">`--include-categories` <*filter*></a>
: Select events matching a category name.

<a id="scrub-option-exclude-categories">`--exclude-categories` <*filter*></a>
: Exclude events matching a category name.

<a id="scrub-option-include-threads">`--include-threads` <*filter*></a>
: Select events matching a thread name.

<a id="scrub-option-exclude-threads">`--exclude-threads` <*filter*></a>
: Exclude events matching a thread name.

<a id="scrub-option-input-file"><*input-file*></a>
: The input file to read events from.

<a id="scrub-option-output-file"><*output-file*></a>
: The output file to write filter events to. If no file is specified,
it will be written to the same  path as the input file, but with
"-scrubbed" appended to the filename.

The filter is a comma-separated list of names, simple and/or qualified,
and/or quoted glob patterns. If multiple filters are used, they
are applied in the specified order.

#### `jfr assemble` subcommand

Use jfr `assemble` to assemble chunk files into a recording file.

The syntax is:

`jfr assemble` <*repository*>
               <*file*>

where:

<a id="assemble-option-repository"><*repository*></a>
: Directory where the repository containing chunk files is located.

<a id="assemble-option-file"><*file*></a>
: Location of the flight recording file (`.jfr`).

Flight recording information is written in chunks. A chunk contains all of the information
necessary for parsing. A chunk typically contains events useful for troubleshooting.
If a JVM should crash, these chunks can be recovered and used to create a flight
recording file using this `jfr assemble` command. These chunk files are concatenated in
chronological order and chunk files that are not finished (.part) are excluded.

#### `jfr disassemble` subcommand

Use `jfr disassemble` to decompose a flight recording file into its chunk file pieces.

The syntax is:

`jfr disassemble` \[`--max-chunks` <*chunks*>\]
                  \[`--output` <*directory*>\]
                  <*file*>

where:

<a id="disassemble-option-output">`--output` <*directory*></a>
: The location to write the disassembled file,
by default the current directory

<a id="disassemble-option-max-chunks">`--max-chunks` <*chunks*></a>
: Maximum number of chunks per file, by default 5.
The chunk size varies, but is typically around 15 MB.

<a id="disassemble-option-max-size">`--max-size` <*size*></a>
: Maximum number of bytes per file.

<a id="disassemble-option-file"><*file*></a>
: Location of the flight recording file (`.jfr`)

This function can be useful for repairing a broken file by removing the faulty chunk.
It can also be used to reduce the size of a file that is too large to transfer.
The resulting chunk files are named `myfile_1.jfr`, `myfile_2.jfr`, etc. If needed, the
resulting file names will be padded with zeros to preserve chronological order. For example,
the chunk file name is `myfile_001.jfr` if the recording consists of more than 100 chunks.

#### jfr version and help subcommands

Use `jfr --version` or `jfr version` to view the version string information for this jfr command.

To get help on any of the jfr subcommands, use:

`jfr <--help|help>` \[*subcommand*\]

where:

\[*subcommand*\] is any of:

- `print`
- `view`
- `configure`
- `metadata`
- `summary`
- `scrub`
- `assemble`
- `disassemble`
