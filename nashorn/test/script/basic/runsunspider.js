/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * runsunspider : runs the sunspider tests and checks for compliance
 *
 * @subtest
 */

/**
 * This is not a test, but a test "framework" for running sunspider tests.
 */

function assertEq(a, b) {
    if (a !== b) {
        throw "ASSERTION FAILED: " + a + " should be " + b;
    }
}

var runs = 0;
var iterations__ = 1;
var total_time = 0;

function runbench(name) {
    var filename = name.split("/").pop();
    if (verbose_run) {
        print("Running " + filename);
    }

    var start = new Date;
    for (var i = 0; i < iterations__; i++) {
        load(name);
    }
    var stop = new Date - start;
    total_time += stop;

    if (verbose_run) {
        print(filename + " done in " + stop + " ms");
    }
    runs++;
}

var m_w = 4711;
var m_z = 17;
var MAXINT = 0x7fffffff;

//produce deterministic random numbers for test suite
function pseudorandom() {
    m_z = 36969 * (m_z & 65535) + (m_z >> 16);
    m_w = 18000 * (m_w & 65535) + (m_w >> 16);
    return (Math.abs((m_z << 16) + m_w) & MAXINT) / MAXINT;
}

function runsuite(tests) {
    var changed = false;

    var oldRandom = Math.random;
    Math.random = pseudorandom;

    try {
        for (var n = 0; n < tests.length; n++) {
            path = dir + '../external/sunspider/tests/sunspider-1.0/' + tests[n].name
            runbench(path);
            if (typeof tests[n].actual !== 'undefined') {
                assertEq(tests[n].actual(), tests[n].expected());
            }
            changed = true;
        }
        // no scripting or something, silently fail
    } finally {
    }
    Math.random = oldRandom;

    return changed;
}

function hash(str) {
    var s = "" + str;
    var h = 0;
    var off = 0;
    for (var i = 0; i < s.length; i++) {
        h = 31 * h + s.charCodeAt(off++);
        h &= 0x7fffffff;
    }
    return h ^ s.length;
}

var tests = [
    { name: 'string-base64.js',
      actual: function() {
          return hash(str);
      },
      expected: function() {
          return 1544571068;
      }
    },
    { name: 'string-validate-input.js',
      actual: function() {
          return hash(endResult);
      },
      expected: function() {
          return 2016572373;
      }
    },
    { name: 'date-format-xparb.js',
      actual: function() {
          return shortFormat + longFormat;
      },
      expected: function() {
          return "2017-09-05Tuesday, September 05, 2017 8:43:48 AM";
      }
    },
    { name: '3d-morph.js',
      actual: function() {
          var acceptableDelta = 4e-15;
          return (testOutput - 6.394884621840902e-14) < acceptableDelta;
      },
      expected: function() {
          return true;
      }
    },
    { name: 'crypto-aes.js',
      actual: function() {
          return plainText;
      },
      expected: function() {
          return decryptedText;
      }
    },
    { name: 'crypto-md5.js',
      actual: function() {
          return md5Output;
      },
      expected: function() {
          return "a831e91e0f70eddcb70dc61c6f82f6cd";
      }
    },
    { name: 'crypto-sha1.js',
      actual: function() {
          return sha1Output;
      },
      expected: function() {
          return "2524d264def74cce2498bf112bedf00e6c0b796d";
      }
    },
    { name: 'bitops-bitwise-and.js',
      actual: function() {
          return result;
      },
      expected: function() {
          return 0;
      }
    },
    { name: 'bitops-bits-in-byte.js',
      actual: function() {
          return result;
      },
      expected: function() {
          return 358400;
      }
    },
    { name: 'bitops-nsieve-bits.js',
      actual: function() {
          var ret = 0;
          for (var i = 0; i < result.length; ++i) {
              ret += result[i];
          }
          ret += result.length;
          return ret;
      },
      expected: function() {
          return -1286749539853;
      }
    },
    { name: 'bitops-3bit-bits-in-byte.js',
      actual: function() {
          return sum;
      },
      expected: function() {
          return 512000;
      }
    },
    { name: 'access-nbody.js',
      actual: function() {
          return ret;
      },
      expected: function() {
            return -1.3524862408537381;
      }
    },
    { name: 'access-binary-trees.js',
      actual: function() {
          return ret;
      },
      expected: function() {
          return -4;
      }
    },
    { name: 'access-fannkuch.js',
      actual: function() {
          return ret;
      },
      expected: function() {
          return 22;
      }
    },
    { name: 'math-spectral-norm.js',
      actual: function() {
          var ret = '';
          for (var i = 6; i <= 48; i *= 2) {
              ret += spectralnorm(i) + ',';
          }
          return ret;
      },
      expected: function() {
          return "1.2657786149754053,1.2727355112619148,1.273989979775574,1.274190125290389,";
      }
    },
    { name: '3d-raytrace.js',
      actual: function() {
          return hash(testOutput);
      },
      expected: function() {
          return 230692593;
      }
    },
    { name: 'regexp-dna.js',
      actual: function() {
          return dnaOutputString;
      },
      expected: function() {
          return "agggtaaa|tttaccct 0\n[cgt]gggtaaa|tttaccc[acg] 9\na[act]ggtaaa|tttacc[agt]t 27\nag[act]gtaaa|tttac[agt]ct 24\nagg[act]taaa|ttta[agt]cct 30\naggg[acg]aaa|ttt[cgt]ccct 9\nagggt[cgt]aa|tt[acg]accct 12\nagggta[cgt]a|t[acg]taccct 9\nagggtaa[cgt]|[acg]ttaccct 15\n";
      }
    },
    { name: 'math-cordic.js',
      actual: function() {
          return total;
      },
      expected: function() {
          return 10362.570468755888;
      }
    },
    { name: 'controlflow-recursive.js',
      actual: function() {
          var ret = 0;
          for (var i = 3; i <= 5; i++) {
              ret += ack(3,i);
              ret += fib(17.0+i);
              ret += tak(3*i+3,2*i+2,i+1);
          }
          return ret;
      },
      expected: function() {
          return 57775;
      }
    },
    { name: 'date-format-tofte.js',
      actual: function() {
          return shortFormat + longFormat;
      },
      expected: function() {
          return "2008-05-01Thursday, May 01, 2008 6:31:22 PM";
      }
    },
    { name: 'string-tagcloud.js',
      actual: function() {
          // The result string embeds floating-point numbers, which can vary a bit on different platforms,
          // so we truncate them a bit before comparing.
          var tagcloud_norm = tagcloud.replace(/([0-9.]+)px/g, function(str, p1) { return p1.substr(0, 10) + 'px' })
          return tagcloud_norm.length;
      },
      expected: function() {
          return 295906;
      }
    },
    { name: 'string-unpack-code.js',
      actual: function() {
          return decompressedMochiKit.length == 106415 &&
              decompressedMochiKit[2000] == '5' &&
              decompressedMochiKit[12000] == '_' &&
              decompressedMochiKit[82556] == '>';
      },
      expected: function() {
          return true;
      }
    },
    //TODO no easy way to sanity check result
    { name: 'string-fasta.js' },
    //TODO no easy way to sanity check result
    { name: 'math-partial-sums.js' },
    //TODO no easy way to sanity check result
    { name: 'access-nsieve.js' },
    //TODO no easy way to sanity check result
    { name: '3d-cube.js' },
];

// handle the case this script may be run by a JS engine that doesn't
// support __DIR__ global variable.
var dir = (typeof(__DIR__) == 'undefined') ? "test/script/basic/" : __DIR__;

var verbose_run = false;

var args = [];
if (typeof $ARGS !== 'undefined') {
    args = $ARGS;
} else if (typeof arguments !== 'undefined' && arguments.length != 0) {
    args = arguments;
}

for (i in args) {
    if (args[i] === '--verbose') {
        verbose_run = true;
        break;
    }
}

runsuite(tests);

if (verbose_run) {
    print('\n' + runs + "/" + tests.length + " tests were successfully run in " + total_time + " ms ");
}

print("Sunspider finished!");
