/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// Simple sample demonstrating "fixed point" computation with Streams

// See also https://mitpress.mit.edu/sicp/chapter1/node21.html#secprocgeneralmethods
var Stream = Java.type("java.util.stream.Stream");

// generic fixed point procedure
function fixed_point(f, init_guess) {
  var tolerance = 0.00001;
  function close_enough(v1, v2) Math.abs(v1 - v2) < tolerance;
 
  var prev; 
  return Stream.iterate(init_guess, f)
      .filter(function(x) {
          try {
              return prev == undefined? false : close_enough(prev, x);
          } finally {
              prev = x;
          }
      })
      .findFirst()
      .get();
}

// solution to x = cos(x)
print(fixed_point(Math.cos, 1.0))

// solution to x = sin(x) + cos(x)
print(fixed_point(function(x) Math.sin(x) + Math.cos(x), 1.0));

// square root by Newton's method
// http://en.wikipedia.org/wiki/Newton's_method
function sqrt(n)
  fixed_point(function(x) (x + n/x) / 2, 2.0);

print(sqrt(2))
print(sqrt(3))


