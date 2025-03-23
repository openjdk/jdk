# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import re
import os
import argparse
from pathlib import Path

include_line = r'^ *#include *(<[^>]+>|"[^"]+") *$\n'
blank_line = r'^$\n'

#: Block of include statement lines with optional blank line(s) in between
includes_re = re.compile(f"{include_line}(?:(?:{blank_line})*{include_line})*", re.MULTILINE)

def sorted_includes(block):
    """
    Sorts the include statements in `block`.

    :param block: source code chunk containing 1 or more include statements
    :return: `block` with the include statements sorted and a blank line
             between user and sys includes
    """

    # Replace blank lines with an include string that sorts after user
    # includes but before sys includes
    lines = block.splitlines()

    user_includes = [line for line in lines if '"' in line]
    sys_includes = [line for line in lines if '<' in line]
    blank_lines = [line for line in lines if line == ""]

    assert len(lines) == len(user_includes) + len(sys_includes) + len(blank_lines)

    user_includes.sort(key=lambda line: line[line.find('"'):])
    sys_includes.sort(key=lambda line: line[line.find('<'):])

    if user_includes and sys_includes and not blank_lines:
        blank_lines = [""]

    # Sort lines and undo blank line replacement
    lines = user_includes + blank_lines + sys_includes

    # Join sorted lines back into a block
    return "\n".join(lines) + "\n"

def sort_includes(path, args):
    """
    Processes the C++ source file in `path` to sort its include statements.

    :param path: a Path object for a C++ source file
    :param args: command line configuration
    :return: True if sorting changes were made, False otherwise
    """
    source = path.read_text()
    new_source = ""

    end = 0
    for m in includes_re.finditer(source):
        if m.start() != end:
            new_source += source[end:m.start()]
        new_source += sorted_includes(m.group())
        end = m.end()

    if not end:
        # No includes found
        return False

    new_source += source[end:]
    if new_source != source:
        if args.update:
            path.write_text(new_source)
        print(f"Includes in {path} were unsorted")
        return True
    return False

if __name__ == "__main__":
    desc = """
    Processes C++ source files to check if their include statements are sorted alphabetically.
    Include statements with any non-space characters after the closing `"` or `>` will not
    be re-ordered.
    The exit value is the number of files that had unsorted include statements.
    """
    p = argparse.ArgumentParser(description=desc)
    p.add_argument("--update", action="store_true", help="update files to sort includes")
    p.add_argument("path", help="C++ file or directory containing C++ files", nargs="+")

    args = p.parse_args()

    unsorted = 0
    files = []
    for name in args.path:
        arg = Path(name)
        if arg.is_file():
            files.append(arg)
        else:
            for dirpath, dirnames, filenames in os.walk(arg):
                for filename in filenames:
                    file = Path(dirpath).joinpath(filename)
                    if file.suffix in (".cpp", "hpp"):
                        files.append(file)

    for file in files:
        if sort_includes(file, args):
            unsorted += 1

    print(f"Processed {len(files)} files, {unsorted} had unsorted include statements.")
    raise SystemExit(unsorted)
