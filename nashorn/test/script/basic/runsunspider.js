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
 * @test
 * @option -timezone=PST
 * @runif external.sunspider
 */

/**
 * This is not a test, but a test "framework" for running sunspider tests.
 */

function assertEq(a, b) {
    if (a !== b) {
        throw "ASSERTION FAILED: " + a + " should be " + b;
    }
}

function pprint(x) {
    if (verbose_run) {
    print(x);
    }
}

var runs = 0;
var total_time = 0;

function runbench(name) {
    var filename = name.split("/").pop();
    pprint("Running (warmup/sanity) " + filename);

    var start = new Date;
    load(name);

    var stop = new Date - start;
    total_time += stop;

    pprint(filename + " done in " + stop + " ms");
    runs++;
}

var m_w;
var m_z;
var MAXINT;

//produce deterministic random numbers for test suite
function pseudorandom() {
    m_z = 36969 * (m_z & 65535) + (m_z >> 16);
    m_w = 18000 * (m_w & 65535) + (m_w >> 16);
    return (Math.abs((m_z << 16) + m_w) & MAXINT) / MAXINT;
}

function initrandom() {
    m_w = 4711;
    m_z = 17;
    MAXINT = 0x7fffffff;
    Math.random = pseudorandom;
}

var rtimes = 0;
var dir = (typeof(__DIR__) == 'undefined') ? "test/script/basic/" : __DIR__;
var single;
var verbose_run = false;
var runall = false;

var args = [];
if (typeof $ARGS !== 'undefined') {
    args = $ARGS;
} else if (typeof arguments !== 'undefined' && arguments.length != 0) {
    args = arguments;
}

for (var i = 0; i < args.length; i++) {
    if (args[i] === '--verbose') {
        verbose_run = true;
    } else if (args[i] === '--times') {
    i++;
    rtimes = +args[i];
    } else if (args[i] === '--single') {
    i++;
    single = args[i];
    } else if (args[i] === '--runall') {
    i++;
    runall = true;
    }
}

function runsuite(tests) {
    var changed   = false;
    var res       = [];
    var oldRandom = Math.random;

    try {
    for (var n = 0; n < tests.length; n++) {
            try {
                path = dir + '../external/sunspider/tests/sunspider-1.0.2/' + tests[n].name

                initrandom();

                var dd = new Date;

                runbench(path);
                if (typeof tests[n].actual !== 'undefined') {
                    assertEq(tests[n].actual(), tests[n].expected());
                }

                var times = 0;
                if (typeof tests[n].rerun !== 'undefined' && tests[n].times > 0) {
                    pprint("rerunning " + tests[n].name + " " + tests[n].times + " times...");
                    var to = tests[n].times;

                    var elemsPerPercent = to / 100;
                    var po = 0|(to / 10);

            pprint("Doing warmup.");
                    for (times = 0; times < to; times++) {
                        initrandom();
                        tests[n].rerun();
                    }

            pprint("Doing hot runs.");
                    for (times = 0; times < to; times++) {
                        initrandom();
                        tests[n].rerun();
                        if ((times % (po|0)) == 0) {
                            pprint("\t" + times/to * 100 + "%");
                        }
                    }
                }

                var t = Math.round(((new Date - dd) / (times == 0 ? 1 : times)) * 100 / 100);
                pprint("time per iteration: " + t + " ms");
                if (typeof tests[n].actual !== 'undefined') {
                    assertEq(tests[n].actual(), tests[n].expected());
                }
                res.push(t);

                pprint("");

                changed = true;
            } catch(e) {
                if (runall) {
                    print("FAIL!");
                } else {
                    throw e;
                }
            }
        }
    } catch (e) {
    print("FAIL!");
    throw e;
        // no scripting or something, silently fail
    } finally {
    Math.random = oldRandom;
    }

    for (var n = 0; n < tests.length; n++) {

    var time = "" + res[n];
    while (time.length < 6) {
        time = " " + time;
    }
    time += " ms";
    if (res[n] == -1) {
        time = "<couldn't be rerun>";
    }
    var str = tests[n].name;
    for (var spaces = str.length; spaces < 32; spaces++) {
        str += " ";
    }
    str += " ";
    str += time;

    if (tests[n].times > 0) {
        str += " [";
        str += tests[n].times + " reruns]";
    }
    pprint(str);
    }

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

    { name: 'regexp-dna.js',
      actual: function() {
      return dnaOutputString + dnaInput;
      },
      expected: function() {
      return expectedDNAOutputString + expectedDNAInput;
      },
    },

    { name: 'string-base64.js',
      actual: function() {
          return hash(str);
      },
      expected: function() {
          return 1544571068;
      },
      times: rtimes,
      rerun: function() {
      toBinaryTable = [
          -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1,
          -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1,
          -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,62, -1,-1,-1,63,
              52,53,54,55, 56,57,58,59, 60,61,-1,-1, -1, 0,-1,-1,
          -1, 0, 1, 2,  3, 4, 5, 6,  7, 8, 9,10, 11,12,13,14,
               15,16,17,18, 19,20,21,22, 23,24,25,-1, -1,-1,-1,-1,
          -1,26,27,28, 29,30,31,32, 33,34,35,36, 37,38,39,40,
              41,42,43,44, 45,46,47,48, 49,50,51,-1, -1,-1,-1,-1
      ];
      var str = "";
      for (var i = 0; i < 8192; i++)
              str += String.fromCharCode((25 * Math.random()) + 97);

      for (var i = 8192; i <= 16384; i *= 2) {
          var base64;
          base64 = toBase64(str);
          var encoded = base64ToString(base64);

          str += str;
      }
      toBinaryTable = null;
      }
    },
    { name: 'date-format-xparb.js',
      actual: function() {
          return shortFormat + longFormat;
      },
      expected: function() {
          return "2017-09-05Tuesday, September 05, 2017 8:43:48 AM";
      },
      times: rtimes,
      rerun: function() {
      date = new Date("1/1/2007 1:11:11");
      for (i = 0; i < 4000; ++i) {
          var shortFormat = date.dateFormat("Y-m-d");
          var longFormat = date.dateFormat("l, F d, Y g:i:s A");
          date.setTime(date.getTime() + 84266956);
      }
      }

    },
    { name: 'string-validate-input.js',
      actual: function() {
          return hash(endResult);
      },
      expected: function() {
          return 726038055;
      },
      times: rtimes,
      rerun: function() {
      doTest();
      },
    },
    { name: '3d-morph.js',
      actual: function() {
          var acceptableDelta = 4e-15;
          return (testOutput - 6.394884621840902e-14) < acceptableDelta;
      },
      expected: function() {
          return true;
      },
      times: rtimes,
      rerun: function() {
      a = Array()
      for (var i=0; i < nx*nz*3; ++i)
          a[i] = 0
      for (var i = 0; i < loops; ++i) {
          morph(a, i/loops)
      }
      testOutput = 0;
      for (var i = 0; i < nx; i++)
          testOutput += a[3*(i*nx+i)+1];
      a = null;

      }
    },
    { name: 'crypto-aes.js',
      actual: function() {
          return plainText;
      },
      expected: function() {
          return decryptedText;
      },
      times: rtimes,
      rerun: function() {
      cipherText = AESEncryptCtr(plainText, password, 256);
      decryptedText = AESDecryptCtr(cipherText, password, 256);

      }
    },
    { name: 'crypto-md5.js',
      actual: function() {
          return md5Output;
      },
      expected: function() {
          return "a831e91e0f70eddcb70dc61c6f82f6cd";
      },
      times: rtimes,
      rerun: function() {
      md5Output = hex_md5(plainText);
      }
    },

    { name: 'crypto-sha1.js',
      actual: function() {
          return sha1Output;
      },
      expected: function() {
          return "2524d264def74cce2498bf112bedf00e6c0b796d";
      },
      times: rtimes,
      rerun: function() {
      sha1Output = hex_sha1(plainText);
      }
    },

    { name: 'bitops-bitwise-and.js',
      actual: function() {
          return result;
      },
      expected: function() {
          return 0;
      },
      times: rtimes,
      rerun: function() {
      bitwiseAndValue = 4294967296;
      for (var i = 0; i < 600000; i++) {
          bitwiseAndValue = bitwiseAndValue & i;
      }
      result = bitwiseAndValue;
      }
    },

    { name: 'bitops-bits-in-byte.js',
      actual: function() {
          return result;
      },
      expected: function() {
          return 358400;
      },
      times: rtimes,
      rerun: function() {
      result = TimeFunc(bitsinbyte);
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
      },
      times: rtimes,
      rerun: function() {
      result = sieve();
      }
    },

    { name: 'bitops-3bit-bits-in-byte.js',
      actual: function() {
          return sum;
      },
      expected: function() {
          return 512000;
      },
      times: rtimes,
      rerun: function() {
      sum = TimeFunc(fast3bitlookup);
      }
    },

    { name: 'access-nbody.js',
      actual: function() {
          return ret;
      },
      expected: function() {
            return -1.3524862408537381;
      },
      times: rtimes,
      rerun: function() {
      var ret = 0;
      for (var n = 3; n <= 24; n *= 2) {
          (function(){
          var bodies = new NBodySystem( Array(
              Sun(),Jupiter(),Saturn(),Uranus(),Neptune()
          ));
          var max = n * 100;

          ret += bodies.energy();
          for (var i=0; i<max; i++){
              bodies.advance(0.01);
          }
          ret += bodies.energy();
          })();
      }
      }
    },

    { name: 'access-binary-trees.js',
      actual: function() {
          return ret;
      },
      expected: function() {
          return -4;
      },
      times: rtimes,
      rerun: function() {
      ret = 0;

      for (var n = 4; n <= 7; n += 1) {
          var minDepth = 4;
          var maxDepth = Math.max(minDepth + 2, n);
          var stretchDepth = maxDepth + 1;

          var check = bottomUpTree(0,stretchDepth).itemCheck();

          var longLivedTree = bottomUpTree(0,maxDepth);
          for (var depth=minDepth; depth<=maxDepth; depth+=2){
          var iterations = 1 << (maxDepth - depth + minDepth);

          check = 0;
          for (var i=1; i<=iterations; i++){
              check += bottomUpTree(i,depth).itemCheck();
              check += bottomUpTree(-i,depth).itemCheck();
          }
          }

          ret += longLivedTree.itemCheck();
      }
      }
    },

    { name: 'access-fannkuch.js',
      actual: function() {
          return ret;
      },
      expected: function() {
          return 22;
      },
      times: rtimes,
      rerun: function() {
      n = 8;
      ret = fannkuch(n);
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
      },
      times: rtimes,
      rerun: function() {
      total = 0;
      for (var i = 6; i <= 48; i *= 2) {
          total += spectralnorm(i);
      }
      }
    },

    { name: '3d-raytrace.js',
      actual: function() {
          return hash(testOutput);
      },
      expected: function() {
          return 230692593;
      },
      times: rtimes,
      rerun: function() {
      testOutput = arrayToCanvasCommands(raytraceScene());
      }
    },

    { name: 'math-cordic.js',
      actual: function() {
          return total;
      },
      expected: function() {
          return 10362.570468755888;
      },
      times: rtimes,
      rerun: function() {
      total = 0;
      cordic(25000);
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
      },
      times: rtimes,
      rerun: function() {
      result = 0;
      for (var i = 3; i <= 5; i++) {
          result += ack(3,i);
          result += fib(17.0+i);
          result += tak(3*i+3,2*i+2,i+1);
      }
      }
    },

    { name: 'date-format-tofte.js',
      actual: function() {
          return shortFormat + longFormat;
      },
      expected: function() {
          return "2008-05-01Thursday, May 01, 2008 6:31:22 PM";
      },
      times: rtimes,
      rerun: function() {
      date = new Date("1/1/2007 1:11:11");
      for (i = 0; i < 500; ++i) {
          var shortFormat = date.formatDate("Y-m-d");
          var longFormat = date.formatDate("l, F d, Y g:i:s A");
          date.setTime(date.getTime() + 84266956);
      }
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
      },
      times: rtimes,
      rerun: function() {
      tagInfo = tagInfoJSON.parseJSON(function(a, b) { if (a == "popularity") { return Math.log(b) / log2; } else {return b; } });
      tagcloud = makeTagCloud(tagInfo);
      }
    },

    { name: 'math-partial-sums.js',
      actual: function() {
      return total;
      },
      expected: function() {
      return 60.08994194659945;
      },
      times: rtimes,
      rerun: function() {
      total = 0;
      for (var i = 1024; i <= 16384; i *= 2) {
          total += partial(i);
      }
      }
    },

    { name: 'access-nsieve.js',
      actual: function() {
      return result;
      },
      expected: function() {
      return 14302;
      },
      times: rtimes,
      rerun: function() {
      result = sieve();
      }
    },

    { name: '3d-cube.js',
      times: rtimes,
      rerun: function() {
      Q = new Array();
      MTrans = new Array();  // transformation matrix
      MQube = new Array();  // position information of qube
      I = new Array();      // entity matrix
      Origin = new Object();
      Testing = new Object();
      for ( var i = 20; i <= 160; i *= 2 ) {
          Init(i);
      }
      }
    },

    //TODO no easy way to sanity check result
    { name: 'string-fasta.js',
      times: rtimes,
      rerun: function() {
      ret = 0;
      count = 7;
      fastaRepeat(2*count*100000, ALU);
      fastaRandom(3*count*1000, IUB);
      fastaRandom(5*count*1000, HomoSap);
      }
    },

    //TODO no easy way to sanity check result
    { name: 'string-unpack-code.js',
      actual: function() {
          return decompressedMochiKit.length == 106415 &&
              decompressedMochiKit[2000] == '5' &&
              decompressedMochiKit[12000] == '_' &&
              decompressedMochiKit[82556] == '>';
      },
      expected: function() {
      return true;
      },
    },

];

tests.sort(function(a,b) { return a.name.localeCompare(b.name); });
if (typeof single !== 'undefined') {
    for (i in tests) {
    if (tests[i].name === single) {
        singleTest = tests[i];
        tests = [singleTest];
        break;
    }
    }
    if (tests.length != 1) {
    throw "unknown single test '" + single + "'";
    }
}


// handle the case this script may be run by a JS engine that doesn't
// support __DIR__ global variable.

runsuite(tests);

pprint('\n' + runs + "/" + tests.length + " tests were successfully run in " + total_time + " ms ");

print("Sunspider finished!");
