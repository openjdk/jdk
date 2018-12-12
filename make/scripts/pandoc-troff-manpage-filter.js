//
// Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
// DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
//
// This code is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License version 2 only, as
// published by the Free Software Foundation.
//
// This code is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// version 2 for more details (a copy is included in the LICENSE file that
// accompanied this code).
//
// You should have received a copy of the GNU General Public License version
// 2 along with this work; if not, write to the Free Software Foundation,
// Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
//
// Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
// or visit www.oracle.com if you need additional information or have any
// questions.
//

//
// Traverse a tree of pandoc format objects, calling callback on each
// element, and replacing it if callback returns a new object.
//
// Inspired by the walk method in
// https://github.com/jgm/pandocfilters/blob/master/pandocfilters.py
//
function traverse(obj, callback) {
    if (Array.isArray(obj)) {
        var processed_array = [];
        obj.forEach(function(elem) {
            if (elem === Object(elem) && elem.t) {
                var replacement = callback(elem.t, elem.c || []);
                if (!replacement) {
                    // no replacement object returned, use original
                    processed_array.push(traverse(elem, callback));
                } else if (Array.isArray(replacement)) {
                    // array of objects returned, splice all elements into array
                    replacement.forEach(function(repl_elem) {
                        processed_array.push(traverse(repl_elem, callback));
                    })
                } else {
                    // replacement object given, traverse it
                    processed_array.push(traverse(replacement, callback));
                }
            } else {
                processed_array.push(traverse(elem, callback));
            }
        })
        return processed_array;
    } else if (obj === Object(obj)) {
        var processed_obj = {};
        Object.keys(obj).forEach(function(key) {
            processed_obj[key] = traverse(obj[key], callback);
        })
        return processed_obj;
    } else {
        return obj;
    }
}

//
// Helper constructors to create pandoc format objects
//
function Space() {
    return { 't': 'Space', 'c': [] };
}

function Str(value) {
    return { 't': 'Str', 'c': value };
}

function Strong(value) {
    return { 't': 'Strong', 'c': value };
}

function Header(value) {
    return { 't': 'Header', 'c': value };
}

//
// Callback to change all Str texts to upper case
//
function uppercase(type, value) {
    if (type === 'Str') {
        return Str(value.toUpperCase());
    }
}

//
// Main callback function that performs our man page AST rewrites
//
function manpage_filter(type, value) {
    // If it is a header, decrease the heading level by one, and
    // if it is a level 1 header, convert it to upper case.
    if (type === 'Header') {
        value[0] = Math.max(1, value[0] - 1);
        if (value[0] == 1) {
            return Header(traverse(value, uppercase));
        }
    }

    // Man pages does not have superscript. We use it for footnotes, so
    // enclose in [...] for best representation.
    if (type === 'Superscript') {
        return [ Str('['), value[0], Str(']') ];
    }

    // If it is a link, put the link name in bold. If it is an external
    // link, put it in brackets. Otherwise, it is either an internal link
    // (like "#next-heading"), or a relative link to another man page
    // (like "java.html"), so remove it for man pages.
    if (type === 'Link') {
        var target = value[2][0];
        if (target.match(/^http[s]?:/)) {
            return [ Strong(value[1]), Space(), Str('[' + target + ']') ];
        } else {
            return Strong(value[1]);
        }
    }
}

//
// Main function
//
function main() {
    var input = "";
    while (line = readLine()) {
        input = input.concat(line);
    }
    var json = JSON.parse(input);

    var transformed_json = traverse(json, manpage_filter);

    print(JSON.stringify(transformed_json));
}

// ... and execute it
main();
