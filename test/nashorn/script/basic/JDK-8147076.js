/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8147076: LinkerCallSite.ARGLIMIT is used incorrectly
 *
 * @test
 * @run
 */

function nonvarargs(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10,
           p11, p12, p13, p14, p15, p16, p17, p18, p19, p20,
           p21, p22, p23, p24, p25, p26, p27, p28, p29, p30,
           p31, p32, p33, p34, p35, p36, p37, p38, p39, p40,
           p41, p42, p43, p44, p45, p46, p47, p48, p49, p50,
           p51, p52, p53, p54, p55, p56, p57, p58, p59, p60,
           p61, p62, p63, p64, p65, p66, p67, p68, p69, p70,
           p71, p72, p73, p74, p75, p76, p77, p78, p79, p80,
           p81, p82, p83, p84, p85, p86, p87, p88, p89, p90,
           p91, p92, p93, p94, p95, p96, p97, p98, p99, p100,
           p101, p102, p103, p104, p105, p106, p107, p108, p109, p110,
           p111, p112, p113, p114, p115, p116, p117, p118, p119, p120,
           p121, p122, p123, p124, p125) {
    //eval() is just to make sure this-object and callee are passed as parameters
    eval();
    print("non-vararg invocation if arguments <= 125");
}

nonvarargs(1.1,2.2,3.3,4.4,5.5,6.6,7.7,8.8,9.9,10.10,11.11,12.12,13.13,14.14,15.15,16.16,17.17,18.18,19.19,20.20,
    21.21,22.22,23.23,24.24,25.25,26.26,27.27,28.28,29.29,30.30,31.31,32.32,33.33,34.34,35.35,36.36,37.37,38.38,39.39,40.40,
    41.41,42.42,43.43,44.44,45.45,46.46,47.47,48.48,49.49,50.50,51.51,52.52,53.53,54.54,55.55,56.56,57.57,58.58,59.59,60.60,
    61.61,62.62,63.63,64.64,65.65,66.66,67.67,68.68,69.69,70.70,71.71,72.72,73.73,74.74,75.75,76.76,77.77,78.78,79.79,80.80,
    81.81,82.82,83.83,84.84,85.85,86.86,87.87,88.88,89.89,90.90,91.91,92.92,93.93,94.94,95.95,96.96,97.97,98.98,99.99,100.100,
    101.101,102.102,103.103,104.104,105.105,106.106,107.107,108.108,109.109,110.110,111.111,112.112,113.113,114.114,115.115,
    116.116,117.117,118.118,119.119,120.120,121.121,122.122,123.123,124.124,125.125);



function varargs(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10,
           p11, p12, p13, p14, p15, p16, p17, p18, p19, p20,
           p21, p22, p23, p24, p25, p26, p27, p28, p29, p30,
           p31, p32, p33, p34, p35, p36, p37, p38, p39, p40,
           p41, p42, p43, p44, p45, p46, p47, p48, p49, p50,
           p51, p52, p53, p54, p55, p56, p57, p58, p59, p60,
           p61, p62, p63, p64, p65, p66, p67, p68, p69, p70,
           p71, p72, p73, p74, p75, p76, p77, p78, p79, p80,
           p81, p82, p83, p84, p85, p86, p87, p88, p89, p90,
           p91, p92, p93, p94, p95, p96, p97, p98, p99, p100,
           p101, p102, p103, p104, p105, p106, p107, p108, p109, p110,
           p111, p112, p113, p114, p115, p116, p117, p118, p119, p120,
           p121, p122, p123, p124, p125, p126) {
    //eval() is just to make sure this-object and callee are passed as parameters
    eval();
    print("vararg invocation if arguments > 125");
}

varargs(1.1,2.2,3.3,4.4,5.5,6.6,7.7,8.8,9.9,10.10,11.11,12.12,13.13,14.14,15.15,16.16,17.17,18.18,19.19,20.20,
    21.21,22.22,23.23,24.24,25.25,26.26,27.27,28.28,29.29,30.30,31.31,32.32,33.33,34.34,35.35,36.36,37.37,38.38,39.39,40.40,
    41.41,42.42,43.43,44.44,45.45,46.46,47.47,48.48,49.49,50.50,51.51,52.52,53.53,54.54,55.55,56.56,57.57,58.58,59.59,60.60,
    61.61,62.62,63.63,64.64,65.65,66.66,67.67,68.68,69.69,70.70,71.71,72.72,73.73,74.74,75.75,76.76,77.77,78.78,79.79,80.80,
    81.81,82.82,83.83,84.84,85.85,86.86,87.87,88.88,89.89,90.90,91.91,92.92,93.93,94.94,95.95,96.96,97.97,98.98,99.99,100.100,
    101.101,102.102,103.103,104.104,105.105,106.106,107.107,108.108,109.109,110.110,111.111,112.112,113.113,114.114,115.115,
    116.116,117.117,118.118,119.119,120.120,121.121,122.122,123.123,124.124,125.125,126.126);

