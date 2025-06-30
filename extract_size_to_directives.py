#  Copyright (c) 2025, Arm Limited. All rights reserved.
#  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

#  This code is free software; you can redistribute it and/or modify it
#  under the terms of the GNU General Public License version 2 only, as
#  published by the Free Software Foundation.

#  This code is distributed in the hope that it will be useful, but WITHOUT
#  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
#  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
#  version 2 for more details (a copy is included in the LICENSE file that
#  accompanied this code).

#  You should have received a copy of the GNU General Public License version
#  2 along with this work; if not, write to the Free Software Foundation,
#  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.

#  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
#  or visit www.oracle.com if you need additional information or have any
#  questions.

import re
import json
import sys

INPUT_FILE = sys.argv[1]
OUTPUT_FILE = sys.argv[2]

# Pattern to match log lines with inline instruction size data
pattern = re.compile(
    r"Collecting method size\s*\{class_name} &apos;([^\{]+)&apos;\s*"
    r"\{method_name} &apos;([^\{]+)&apos;\s*"
    r"\{signature} &apos;([^\{]+)&apos;\s*"
    r"\{Inline instruction size: (\d+)}",
    re.MULTILINE | re.DOTALL
)

def extract_and_generate_directives(input_file, output_file):
    directives = {
        "match": "*::*",
        "inline": []
    }
    seen = {}
    with open(input_file, 'r') as infile:
        content = infile.read()
        matches = pattern.findall(content)
        allcount = 0
        for match in matches:
            class_name, method_name, signature, inline_size = match
            allcount = allcount + 1
            if "LambdaForm" in class_name:
                continue  # Exclude LambdaForm methods
            if "print" == method_name:
                continue  # Exclude method_name pointing to an option type or option name
            key = (class_name, method_name, signature)
            size = int(inline_size)
            if key not in seen or size < seen[key]:
                # Conservatively keep the smallest size
                seen[key] = size
        for (class_name, method_name, signature), size in seen.items():
            dotted_class_name = class_name.replace("/", ".")
            record = f"{dotted_class_name}::{method_name}{signature}:{size}"
            directives["inline"].append(record)

    with open(output_file, 'w') as outfile:
        json.dump(directives, outfile, indent=2)

    print(f"Directive file written to: {output_file}")
    count = len(directives["inline"])
    print(f"Generating {count} entries from {allcount} records")

if __name__ == "__main__":
    extract_and_generate_directives(INPUT_FILE, OUTPUT_FILE)

