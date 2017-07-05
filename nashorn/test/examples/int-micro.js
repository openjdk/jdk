/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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


function bench(name, func) {
    var start = Date.now();
    for (var iter = 0; iter < 5000000; iter++) {
        func();
    }
    print(name + "\t" + (Date.now() - start));
}

function uint32(value) {
    return function() {
        value >>> 0;
        value >>> 0;
        value >>> 0;
        value >>> 0;
        value >>> 0;
        value >>> 0;
        value >>> 0;
        value >>> 0;
        value >>> 0;
        value >>> 0;
        value >>> 0;
        value >>> 0;
        value >>> 0;
        value >>> 0;
        value >>> 0;
    };
}

function int32(value) {
    return function() {
        value >> 0;
        value >> 0;
        value >> 0;
        value >> 0;
        value >> 0;
        value >> 0;
        value >> 0;
        value >> 0;
        value >> 0;
        value >> 0;
        value >> 0;
        value >> 0;
        value >> 0;
        value >> 0;
        value >> 0;
    };
}

print("\nToUint32");
for (var i = 1; i < 3; i++) {
    bench("infinity      ", uint32(Infinity));
    bench("infinity neg  ", uint32(-Infinity));
    bench("nan           ", uint32(NaN));
    bench("small         ", uint32(1));
    bench("small neg     ", uint32(-1));
    bench("small frac    ", uint32(1.5));
    bench("small neg frac", uint32(-1.5));
    bench("large         ", uint32(9223372036854775807));
    bench("large neg     ", uint32(-9223372036854775808));
}

print("\nToInt32");
for (var i = 1; i < 3; i++) {
    bench("infinity      ", int32(Infinity));
    bench("infinity neg  ", int32(-Infinity));
    bench("nan           ", int32(NaN));
    bench("small         ", int32(1));
    bench("small neg     ", int32(-1));
    bench("small frac    ", int32(1.5));
    bench("small neg frac", int32(-1.5));
    bench("large         ", int32(9223372036854775807));
    bench("large neg     ", int32(-9223372036854775808));
}


