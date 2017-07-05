# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
# ----------------------------------------------------------------------------------------------------

import re

class OutputParser:

    def __init__(self):
        self.matchers = []

    def addMatcher(self, matcher):
        self.matchers.append(matcher)

    def parse(self, output):
        valueMaps = []
        for matcher in self.matchers:
            matcher.parse(output, valueMaps)
        return valueMaps

"""
Produces a value map for each match of a given regular expression
in some text. The value map is specified by a template map
where each key and value in the template map is either a constant
value or a named group in the regular expression. The latter is
given as the group name enclosed in '<' and '>'.
"""
class ValuesMatcher:

    def __init__(self, regex, valuesTemplate):
        assert isinstance(valuesTemplate, dict)
        self.regex = regex
        self.valuesTemplate = valuesTemplate

    def parse(self, text, valueMaps):
        for match in self.regex.finditer(text):
            valueMap = {}
            for keyTemplate, valueTemplate in self.valuesTemplate.items():
                key = self.get_template_value(match, keyTemplate)
                value = self.get_template_value(match, valueTemplate)
                assert not valueMap.has_key(key), key
                valueMap[key] = value
            valueMaps.append(valueMap)

    def get_template_value(self, match, template):
        def replace_var(m):
            groupName = m.group(1)
            return match.group(groupName)

        return re.sub(r'<([\w]+)>', replace_var, template)
