# compute Mothers day of the given the year

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

// print "Mother's day" of the given year using Java Time API

if (arguments.length == 0) {
    print("Usage: jjs mothers_day.js -- year");
    exit(1);
}

// java classes used
var DayOfWeek = java.time.DayOfWeek;
var LocalDate = java.time.LocalDate;
var TemporalAdjusters = java.time.temporal.TemporalAdjusters;

var year = parseInt(arguments[0]);

// See: https://en.wikipedia.org/?title=Mother%27s_Day
// We need second Sunday of May. Make April 30 of the given
// year adjust and adjust to next Sunday from there twice. To adjust a Date
// we use a common TemporalAdjuster provided in JDK8.
// https://docs.oracle.com/javase/8/docs/api/java/time/temporal/TemporalAdjusters.html

print(LocalDate.of(year, 4, 30).
    with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).
    with(TemporalAdjusters.next(DayOfWeek.SUNDAY)));
