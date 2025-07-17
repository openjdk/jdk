/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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

/*
 * @test
 * @bug 8333258
 * @summary C2: high memory usage in PhaseCFG::raise_above_anti_dependences()
 * @run main/othervm -XX:CompileOnly=TestAntiDependenciesHighMemUsage::test1 -Xcomp TestAntiDependenciesHighMemUsage
 */

public class TestAntiDependenciesHighMemUsage {

    public static void main(String[] args) {
        test1(0);
    }

    private static int test1(int i) {
         int v = field0000 +
                 field0001 +
                 field0002 +
                 field0003 +
                 field0004 +
                 field0005 +
                 field0006 +
                 field0007 +
                 field0008 +
                 field0009 +
                 field0010 +
                 field0011 +
                 field0012 +
                 field0013 +
                 field0014 +
                 field0015 +
                 field0016 +
                 field0017 +
                 field0018 +
                 field0019 +
                 field0020 +
                 field0021 +
                 field0022 +
                 field0023 +
                 field0024 +
                 field0025 +
                 field0026 +
                 field0027 +
                 field0028 +
                 field0029 +
                 field0030 +
                 field0031 +
                 field0032 +
                 field0033 +
                 field0034 +
                 field0035 +
                 field0036 +
                 field0037 +
                 field0038 +
                 field0039 +
                 field0040 +
                 field0041 +
                 field0042 +
                 field0043 +
                 field0044 +
                 field0045 +
                 field0046 +
                 field0047 +
                 field0048 +
                 field0049 +
                 field0050 +
                 field0051 +
                 field0052 +
                 field0053 +
                 field0054 +
                 field0055 +
                 field0056 +
                 field0057 +
                 field0058 +
                 field0059;

        switch (i) {
            case -2140710406:
                field0000 = 42;
                field0001 = 42;
                field0002 = 42;
                field0003 = 42;
                field0004 = 42;
                field0005 = 42;
                field0006 = 42;
                field0007 = 42;
                field0008 = 42;
                field0009 = 42;
                field0010 = 42;
                field0011 = 42;
                field0012 = 42;
                field0013 = 42;
                field0014 = 42;
                field0015 = 42;
                field0016 = 42;
                field0017 = 42;
                field0018 = 42;
                field0019 = 42;
                field0020 = 42;
                field0021 = 42;
                field0022 = 42;
                field0023 = 42;
                field0024 = 42;
                field0025 = 42;
                field0026 = 42;
                field0027 = 42;
                field0028 = 42;
                field0029 = 42;
                field0030 = 42;
                field0031 = 42;
                field0032 = 42;
                field0033 = 42;
                field0034 = 42;
                field0035 = 42;
                field0036 = 42;
                field0037 = 42;
                field0038 = 42;
                field0039 = 42;
                field0040 = 42;
                field0041 = 42;
                field0042 = 42;
                field0043 = 42;
                field0044 = 42;
                field0045 = 42;
                field0046 = 42;
                field0047 = 42;
                field0048 = 42;
                field0049 = 42;
                field0050 = 42;
                field0051 = 42;
                field0052 = 42;
                field0053 = 42;
                field0054 = 42;
                field0055 = 42;
                field0056 = 42;
                field0057 = 42;
                field0058 = 42;
                field0059 = 42;
                field0060 = 42;
                field0061 = 42;
                field0062 = 42;
                field0063 = 42;
                field0064 = 42;
                field0065 = 42;
                field0066 = 42;
                field0067 = 42;
                field0068 = 42;
                field0069 = 42;
                field0070 = 42;
                field0071 = 42;
                field0072 = 42;
                field0073 = 42;
                field0074 = 42;
                field0075 = 42;
                field0076 = 42;
                field0077 = 42;
                field0078 = 42;
                field0079 = 42;
                field0080 = 42;
                field0081 = 42;
                field0082 = 42;
                field0083 = 42;
                field0084 = 42;
                field0085 = 42;
                field0086 = 42;
                field0087 = 42;
                field0088 = 42;
                field0089 = 42;
                field0090 = 42;
                field0091 = 42;
                field0092 = 42;
                field0093 = 42;
                field0094 = 42;
                field0095 = 42;
                field0096 = 42;
                field0097 = 42;
                field0098 = 42;
                field0099 = 42;
                field0100 = 42;
                field0101 = 42;
                field0102 = 42;
                field0103 = 42;
                field0104 = 42;
                field0105 = 42;
                field0106 = 42;
                field0107 = 42;
                field0108 = 42;
                field0109 = 42;
                field0110 = 42;
                field0111 = 42;
                field0112 = 42;
                field0113 = 42;
                field0114 = 42;
                field0115 = 42;
                field0116 = 42;
                field0117 = 42;
                field0118 = 42;
                field0119 = 42;
                field0120 = 42;
                field0121 = 42;
                field0122 = 42;
                field0123 = 42;
                field0124 = 42;
                field0125 = 42;
                field0126 = 42;
                field0127 = 42;
                field0128 = 42;
                field0129 = 42;
                field0130 = 42;
                field0131 = 42;
                field0132 = 42;
                field0133 = 42;
                field0134 = 42;
                field0135 = 42;
                field0136 = 42;
                field0137 = 42;
                field0138 = 42;
                field0139 = 42;
                field0140 = 42;
                field0141 = 42;
                field0142 = 42;
                field0143 = 42;
                field0144 = 42;
                field0145 = 42;
                field0146 = 42;
                field0147 = 42;
                field0148 = 42;
                field0149 = 42;
                field0150 = 42;
                field0151 = 42;
                field0152 = 42;
                field0153 = 42;
                field0154 = 42;
                field0155 = 42;
                field0156 = 42;
                field0157 = 42;
                field0158 = 42;
                field0159 = 42;
                field0160 = 42;
                field0161 = 42;
                field0162 = 42;
                field0163 = 42;
                field0164 = 42;
                field0165 = 42;
                field0166 = 42;
                field0167 = 42;
                field0168 = 42;
                field0169 = 42;
                field0170 = 42;
                field0171 = 42;
                field0172 = 42;
                field0173 = 42;
                field0174 = 42;
                field0175 = 42;
                field0176 = 42;
                field0177 = 42;
                field0178 = 42;
                field0179 = 42;
                field0180 = 42;
                field0181 = 42;
                field0182 = 42;
                field0183 = 42;
                field0184 = 42;
                field0185 = 42;
                field0186 = 42;
                field0187 = 42;
                field0188 = 42;
                field0189 = 42;
                field0190 = 42;
                field0191 = 42;
                field0192 = 42;
                field0193 = 42;
                field0194 = 42;
                field0195 = 42;
                field0196 = 42;
                field0197 = 42;
                field0198 = 42;
                field0199 = 42;
                field0200 = 42;
                field0201 = 42;
                field0202 = 42;
                field0203 = 42;
                field0204 = 42;
                field0205 = 42;
                field0206 = 42;
                field0207 = 42;
                field0208 = 42;
                field0209 = 42;
                field0210 = 42;
                field0211 = 42;
                field0212 = 42;
                field0213 = 42;
                field0214 = 42;
                field0215 = 42;
                field0216 = 42;
                field0217 = 42;
                field0218 = 42;
                field0219 = 42;
                field0220 = 42;
                field0221 = 42;
                field0222 = 42;
                field0223 = 42;
                field0224 = 42;
                field0225 = 42;
                field0226 = 42;
                field0227 = 42;
                field0228 = 42;
                field0229 = 42;
                field0230 = 42;
                field0231 = 42;
                field0232 = 42;
                field0233 = 42;
                field0234 = 42;
                field0235 = 42;
                field0236 = 42;
                field0237 = 42;
                field0238 = 42;
                field0239 = 42;
                field0240 = 42;
                field0241 = 42;
                field0242 = 42;
                field0243 = 42;
                field0244 = 42;
                field0245 = 42;
                field0246 = 42;
                field0247 = 42;
                field0248 = 42;
                field0249 = 42;
                field0250 = 42;
                field0251 = 42;
                field0252 = 42;
                field0253 = 42;
                field0254 = 42;
                field0255 = 42;
                field0256 = 42;
                field0257 = 42;
                field0258 = 42;
                field0259 = 42;
                field0260 = 42;
                field0261 = 42;
                field0262 = 42;
                field0263 = 42;
                field0264 = 42;
                field0265 = 42;
                field0266 = 42;
                field0267 = 42;
                field0268 = 42;
                field0269 = 42;
                field0270 = 42;
                field0271 = 42;
                field0272 = 42;
                field0273 = 42;
                field0274 = 42;
                field0275 = 42;
                field0276 = 42;
                field0277 = 42;
                field0278 = 42;
                field0279 = 42;
                field0280 = 42;
                field0281 = 42;
                field0282 = 42;
                field0283 = 42;
                field0284 = 42;
                field0285 = 42;
                field0286 = 42;
                field0287 = 42;
                field0288 = 42;
                field0289 = 42;
                field0290 = 42;
                field0291 = 42;
                field0292 = 42;
                field0293 = 42;
                field0294 = 42;
                field0295 = 42;
                field0296 = 42;
                field0297 = 42;
                field0298 = 42;
                field0299 = 42;
                field0300 = 42;
                field0301 = 42;
                field0302 = 42;
                field0303 = 42;
                field0304 = 42;
                field0305 = 42;
                field0306 = 42;
                field0307 = 42;
                field0308 = 42;
                field0309 = 42;
                field0310 = 42;
                field0311 = 42;
                field0312 = 42;
                field0313 = 42;
                field0314 = 42;
                field0315 = 42;
                field0316 = 42;
                field0317 = 42;
                field0318 = 42;
                field0319 = 42;
                field0320 = 42;
                field0321 = 42;
                field0322 = 42;
                field0323 = 42;
                field0324 = 42;
                field0325 = 42;
                field0326 = 42;
                field0327 = 42;
                field0328 = 42;
                field0329 = 42;
                field0330 = 42;
                field0331 = 42;
                field0332 = 42;
                field0333 = 42;
                field0334 = 42;
                field0335 = 42;
                field0336 = 42;
                field0337 = 42;
                field0338 = 42;
                field0339 = 42;
                field0340 = 42;
                field0341 = 42;
                field0342 = 42;
                field0343 = 42;
                field0344 = 42;
                field0345 = 42;
                field0346 = 42;
                field0347 = 42;
                field0348 = 42;
                field0349 = 42;
                field0350 = 42;
                field0351 = 42;
                field0352 = 42;
                field0353 = 42;
                field0354 = 42;
                field0355 = 42;
                field0356 = 42;
                field0357 = 42;
                field0358 = 42;
                field0359 = 42;
                field0360 = 42;
                field0361 = 42;
                field0362 = 42;
                field0363 = 42;
                field0364 = 42;
                field0365 = 42;
                field0366 = 42;
                field0367 = 42;
                field0368 = 42;
                field0369 = 42;
                field0370 = 42;
                field0371 = 42;
                field0372 = 42;
                field0373 = 42;
                field0374 = 42;
                field0375 = 42;
                field0376 = 42;
                field0377 = 42;
                field0378 = 42;
                field0379 = 42;
                field0380 = 42;
                field0381 = 42;
                field0382 = 42;
                field0383 = 42;
                field0384 = 42;
                field0385 = 42;
                field0386 = 42;
                field0387 = 42;
                field0388 = 42;
                field0389 = 42;
                field0390 = 42;
                field0391 = 42;
                field0392 = 42;
                field0393 = 42;
                field0394 = 42;
                field0395 = 42;
                field0396 = 42;
                field0397 = 42;
                field0398 = 42;
                field0399 = 42;
                field0400 = 42;
                field0401 = 42;
                field0402 = 42;
                field0403 = 42;
                field0404 = 42;
                field0405 = 42;
                field0406 = 42;
                field0407 = 42;
                field0408 = 42;
                field0409 = 42;
                field0410 = 42;
                field0411 = 42;
                field0412 = 42;
                field0413 = 42;
                field0414 = 42;
                field0415 = 42;
                field0416 = 42;
                field0417 = 42;
                field0418 = 42;
                field0419 = 42;
                field0420 = 42;
                field0421 = 42;
                field0422 = 42;
                field0423 = 42;
                field0424 = 42;
                field0425 = 42;
                field0426 = 42;
                field0427 = 42;
                field0428 = 42;
                field0429 = 42;
                field0430 = 42;
                field0431 = 42;
                field0432 = 42;
                field0433 = 42;
                field0434 = 42;
                field0435 = 42;
                field0436 = 42;
                field0437 = 42;
                field0438 = 42;
                field0439 = 42;
                field0440 = 42;
                field0441 = 42;
                field0442 = 42;
                field0443 = 42;
                field0444 = 42;
                field0445 = 42;
                field0446 = 42;
                field0447 = 42;
                field0448 = 42;
                field0449 = 42;
                field0450 = 42;
                field0451 = 42;
                field0452 = 42;
                field0453 = 42;
                field0454 = 42;
                field0455 = 42;
                field0456 = 42;
                field0457 = 42;
                field0458 = 42;
                field0459 = 42;
                field0460 = 42;
                field0461 = 42;
                field0462 = 42;
                field0463 = 42;
                field0464 = 42;
                field0465 = 42;
                field0466 = 42;
                field0467 = 42;
                field0468 = 42;
                field0469 = 42;
                field0470 = 42;
                field0471 = 42;
                field0472 = 42;
                field0473 = 42;
                field0474 = 42;
                field0475 = 42;
                field0476 = 42;
                field0477 = 42;
                field0478 = 42;
                field0479 = 42;
                field0480 = 42;
                field0481 = 42;
                field0482 = 42;
                field0483 = 42;
                field0484 = 42;
                field0485 = 42;
                field0486 = 42;
                field0487 = 42;
                field0488 = 42;
                field0489 = 42;
                field0490 = 42;
                field0491 = 42;
                field0492 = 42;
                field0493 = 42;
                field0494 = 42;
                field0495 = 42;
                field0496 = 42;
                field0497 = 42;
                field0498 = 42;
                field0499 = 42;
                field0500 = 42;
                field0501 = 42;
                field0502 = 42;
                field0503 = 42;
                field0504 = 42;
                field0505 = 42;
                field0506 = 42;
                field0507 = 42;
                field0508 = 42;
                field0509 = 42;
                field0510 = 42;
                field0511 = 42;
                field0512 = 42;
                field0513 = 42;
                field0514 = 42;
                field0515 = 42;
                field0516 = 42;
                field0517 = 42;
                field0518 = 42;
                field0519 = 42;
                field0520 = 42;
                field0521 = 42;
                field0522 = 42;
                field0523 = 42;
                field0524 = 42;
                field0525 = 42;
                field0526 = 42;
                field0527 = 42;
                field0528 = 42;
                field0529 = 42;
                field0530 = 42;
                field0531 = 42;
                field0532 = 42;
                field0533 = 42;
                field0534 = 42;
                field0535 = 42;
                field0536 = 42;
                field0537 = 42;
                field0538 = 42;
                field0539 = 42;
                field0540 = 42;
                field0541 = 42;
                field0542 = 42;
                field0543 = 42;
                field0544 = 42;
                field0545 = 42;
                field0546 = 42;
                field0547 = 42;
                field0548 = 42;
                field0549 = 42;
                field0550 = 42;
                field0551 = 42;
                field0552 = 42;
                field0553 = 42;
                field0554 = 42;
                field0555 = 42;
                field0556 = 42;
                field0557 = 42;
                field0558 = 42;
                field0559 = 42;
                field0560 = 42;
                field0561 = 42;
                field0562 = 42;
                field0563 = 42;
                field0564 = 42;
                field0565 = 42;
                field0566 = 42;
                field0567 = 42;
                field0568 = 42;
                field0569 = 42;
                field0570 = 42;
                field0571 = 42;
                field0572 = 42;
                field0573 = 42;
                field0574 = 42;
                field0575 = 42;
                field0576 = 42;
                field0577 = 42;
                field0578 = 42;
                field0579 = 42;
                field0580 = 42;
                field0581 = 42;
                field0582 = 42;
                field0583 = 42;
                field0584 = 42;
                field0585 = 42;
                field0586 = 42;
                field0587 = 42;
                field0588 = 42;
                field0589 = 42;
                field0590 = 42;
                field0591 = 42;
                field0592 = 42;
                field0593 = 42;
                field0594 = 42;
                field0595 = 42;
                field0596 = 42;
                field0597 = 42;
                field0598 = 42;
                field0599 = 42;
                field0600 = 42;
                field0601 = 42;
                field0602 = 42;
                field0603 = 42;
                field0604 = 42;
                field0605 = 42;
                field0606 = 42;
                field0607 = 42;
                field0608 = 42;
                field0609 = 42;
                field0610 = 42;
                field0611 = 42;
                field0612 = 42;
                field0613 = 42;
                field0614 = 42;
                field0615 = 42;
                field0616 = 42;
                field0617 = 42;
                field0618 = 42;
                field0619 = 42;
                field0620 = 42;
                field0621 = 42;
                field0622 = 42;
                field0623 = 42;
                field0624 = 42;
                field0625 = 42;
                field0626 = 42;
                field0627 = 42;
                field0628 = 42;
                field0629 = 42;
                field0630 = 42;
                field0631 = 42;
                field0632 = 42;
                field0633 = 42;
                field0634 = 42;
                field0635 = 42;
                field0636 = 42;
                field0637 = 42;
                field0638 = 42;
                field0639 = 42;
                field0640 = 42;
                field0641 = 42;
                field0642 = 42;
                field0643 = 42;
                field0644 = 42;
                field0645 = 42;
                field0646 = 42;
                field0647 = 42;
                field0648 = 42;
                field0649 = 42;
                field0650 = 42;
                field0651 = 42;
                field0652 = 42;
                field0653 = 42;
                field0654 = 42;
                field0655 = 42;
                field0656 = 42;
                field0657 = 42;
                field0658 = 42;
                field0659 = 42;
                field0660 = 42;
                field0661 = 42;
                field0662 = 42;
                field0663 = 42;
                field0664 = 42;
                field0665 = 42;
                field0666 = 42;
                field0667 = 42;
                field0668 = 42;
                field0669 = 42;
                field0670 = 42;
                field0671 = 42;
                field0672 = 42;
                field0673 = 42;
                field0674 = 42;
                field0675 = 42;
                field0676 = 42;
                field0677 = 42;
                field0678 = 42;
                field0679 = 42;
                field0680 = 42;
                field0681 = 42;
                field0682 = 42;
                field0683 = 42;
                field0684 = 42;
                field0685 = 42;
                field0686 = 42;
                field0687 = 42;
                field0688 = 42;
                field0689 = 42;
                field0690 = 42;
                field0691 = 42;
                field0692 = 42;
                field0693 = 42;
                field0694 = 42;
                field0695 = 42;
                field0696 = 42;
                field0697 = 42;
                field0698 = 42;
                field0699 = 42;
                field0700 = 42;
                field0701 = 42;
                field0702 = 42;
                field0703 = 42;
                field0704 = 42;
                field0705 = 42;
                field0706 = 42;
                field0707 = 42;
                field0708 = 42;
                field0709 = 42;
                field0710 = 42;
                field0711 = 42;
                field0712 = 42;
                field0713 = 42;
                field0714 = 42;
                field0715 = 42;
                field0716 = 42;
                field0717 = 42;
                field0718 = 42;
                field0719 = 42;
                field0720 = 42;
                field0721 = 42;
                field0722 = 42;
                field0723 = 42;
                field0724 = 42;
                field0725 = 42;
                field0726 = 42;
                field0727 = 42;
                field0728 = 42;
                field0729 = 42;
                field0730 = 42;
                field0731 = 42;
                field0732 = 42;
                field0733 = 42;
                field0734 = 42;
                field0735 = 42;
                field0736 = 42;
                field0737 = 42;
                field0738 = 42;
                field0739 = 42;
                field0740 = 42;
                field0741 = 42;
                field0742 = 42;
                field0743 = 42;
                field0744 = 42;
                field0745 = 42;
                field0746 = 42;
                field0747 = 42;
                field0748 = 42;
                field0749 = 42;
                field0750 = 42;
                field0751 = 42;
                field0752 = 42;
                field0753 = 42;
                field0754 = 42;
                field0755 = 42;
                field0756 = 42;
                field0757 = 42;
                field0758 = 42;
                field0759 = 42;
                field0760 = 42;
                field0761 = 42;
                field0762 = 42;
                field0763 = 42;
                field0764 = 42;
                field0765 = 42;
                field0766 = 42;
                field0767 = 42;
                field0768 = 42;
                field0769 = 42;
                field0770 = 42;
                field0771 = 42;
                field0772 = 42;
                field0773 = 42;
                field0774 = 42;
                field0775 = 42;
                field0776 = 42;
                field0777 = 42;
                field0778 = 42;
                field0779 = 42;
                field0780 = 42;
                field0781 = 42;
                field0782 = 42;
                field0783 = 42;
                field0784 = 42;
                field0785 = 42;
                field0786 = 42;
                field0787 = 42;
                field0788 = 42;
                field0789 = 42;
                field0790 = 42;
                field0791 = 42;
                field0792 = 42;
                field0793 = 42;
                field0794 = 42;
                field0795 = 42;
                field0796 = 42;
                field0797 = 42;
                field0798 = 42;
                field0799 = 42;
                field0800 = 42;
                field0801 = 42;
                field0802 = 42;
                field0803 = 42;
                field0804 = 42;
                field0805 = 42;
                field0806 = 42;
                field0807 = 42;
                field0808 = 42;
                field0809 = 42;
                field0810 = 42;
                field0811 = 42;
                field0812 = 42;
                field0813 = 42;
                field0814 = 42;
                field0815 = 42;
                field0816 = 42;
                field0817 = 42;
                field0818 = 42;
                field0819 = 42;
                field0820 = 42;
                field0821 = 42;
                field0822 = 42;
                field0823 = 42;
                field0824 = 42;
                field0825 = 42;
                field0826 = 42;
                field0827 = 42;
                field0828 = 42;
                field0829 = 42;
                field0830 = 42;
                field0831 = 42;
                field0832 = 42;
                field0833 = 42;
                field0834 = 42;
                field0835 = 42;
                field0836 = 42;
                field0837 = 42;
                field0838 = 42;
                field0839 = 42;
                field0840 = 42;
                field0841 = 42;
                field0842 = 42;
                field0843 = 42;
                field0844 = 42;
                field0845 = 42;
                field0846 = 42;
                field0847 = 42;
                field0848 = 42;
                field0849 = 42;
                field0850 = 42;
                field0851 = 42;
                field0852 = 42;
                field0853 = 42;
                field0854 = 42;
                field0855 = 42;
                field0856 = 42;
                field0857 = 42;
                field0858 = 42;
                field0859 = 42;
                field0860 = 42;
                field0861 = 42;
                field0862 = 42;
                field0863 = 42;
                field0864 = 42;
                field0865 = 42;
                field0866 = 42;
                field0867 = 42;
                field0868 = 42;
                field0869 = 42;
                field0870 = 42;
                field0871 = 42;
                field0872 = 42;
                field0873 = 42;
                field0874 = 42;
                field0875 = 42;
                field0876 = 42;
                field0877 = 42;
                field0878 = 42;
                field0879 = 42;
                field0880 = 42;
                field0881 = 42;
                field0882 = 42;
                field0883 = 42;
                field0884 = 42;
                field0885 = 42;
                field0886 = 42;
                field0887 = 42;
                field0888 = 42;
                field0889 = 42;
                field0890 = 42;
                field0891 = 42;
                field0892 = 42;
                field0893 = 42;
                field0894 = 42;
                field0895 = 42;
                field0896 = 42;
                field0897 = 42;
                field0898 = 42;
                field0899 = 42;
                field0900 = 42;
                field0901 = 42;
                field0902 = 42;
                field0903 = 42;
                field0904 = 42;
                field0905 = 42;
                field0906 = 42;
                field0907 = 42;
                field0908 = 42;
                field0909 = 42;
                field0910 = 42;
                field0911 = 42;
                field0912 = 42;
                field0913 = 42;
                field0914 = 42;
                field0915 = 42;
                field0916 = 42;
                field0917 = 42;
                field0918 = 42;
                field0919 = 42;
                field0920 = 42;
                field0921 = 42;
                field0922 = 42;
                field0923 = 42;
                field0924 = 42;
                field0925 = 42;
                field0926 = 42;
                field0927 = 42;
                field0928 = 42;
                field0929 = 42;
                field0930 = 42;
                field0931 = 42;
                field0932 = 42;
                field0933 = 42;
                field0934 = 42;
                field0935 = 42;
                field0936 = 42;
                field0937 = 42;
                field0938 = 42;
                field0939 = 42;
                field0940 = 42;
                field0941 = 42;
                field0942 = 42;
                field0943 = 42;
                field0944 = 42;
                field0945 = 42;
                field0946 = 42;
                field0947 = 42;
                field0948 = 42;
                field0949 = 42;
                field0950 = 42;
                field0951 = 42;
                field0952 = 42;
                field0953 = 42;
                field0954 = 42;
                field0955 = 42;
                field0956 = 42;
                field0957 = 42;
                field0958 = 42;
                field0959 = 42;
                field0960 = 42;
                field0961 = 42;
                field0962 = 42;
                field0963 = 42;
                field0964 = 42;
                field0965 = 42;
                field0966 = 42;
                field0967 = 42;
                field0968 = 42;
                field0969 = 42;
                field0970 = 42;
                field0971 = 42;
                field0972 = 42;
                field0973 = 42;
                field0974 = 42;
                field0975 = 42;
                field0976 = 42;
                field0977 = 42;
                field0978 = 42;
                field0979 = 42;
                field0980 = 42;
                field0981 = 42;
                field0982 = 42;
                field0983 = 42;
                field0984 = 42;
                field0985 = 42;
                field0986 = 42;
                field0987 = 42;
                field0988 = 42;
                field0989 = 42;
                field0990 = 42;
                field0991 = 42;
                field0992 = 42;
                field0993 = 42;
                field0994 = 42;
                field0995 = 42;
                field0996 = 42;
                field0997 = 42;
                field0998 = 42;
                field0999 = 42;
                break;
            case -2097348800:
                break;
            case -2068224216:
                break;
            case -2037697382:
                break;
            case -2004863454:
                break;
            case -1927368268:
                break;
            case -1907858975:
                break;
            case -1907849355:
                break;
            case -1874423303:
                break;
            case -1842766326:
                break;
            case -1789797270:
                break;
            case -1760959152:
                break;
            case -1691992770:
                break;
            case -1678813190:
                break;
            case -1605049009:
                break;
            case -1476174894:
                break;
            case -1377846581:
                break;
            case -1345530543:
                break;
            case -1307317230:
                break;
            case -1268501092:
                break;
            case -1220360021:
                break;
            case -1217415016:
                break;
            case -1216012752:
                break;
            case -1202791344:
                break;
            case -1197000094:
                break;
            case -1153521791:
                break;
            case -1136815094:
                break;
            case -1122842661:
                break;
            case -1097468803:
                break;
            case -1093178557:
                break;
            case -1087398572:
                break;
            case -1008013583:
                break;
            case -1001676601:
                break;
            case -949306426:
                break;
            case -912457023:
                break;
            case -891985903:
                break;
            case -883723257:
                break;
            case -871422185:
                break;
            case -766867181:
                break;
            case -766422255:
                break;
            case -650580623:
                break;
            case -633276745:
                break;
            case -632949857:
                break;
            case -621058352:
                break;
            case -616289146:
                break;
            case -589453283:
                break;
            case -555387838:
                break;
            case -540546990:
                break;
            case -526550005:
                break;
            case -502303438:
                break;
            case -408244884:
                break;
            case -367870439:
                break;
            case -342579923:
                break;
            case -330210563:
                break;
            case -329624856:
                break;
            case -302536977:
                break;
            case -287122936:
                break;
            case -236322890:
                break;
            case -227407685:
                break;
            case -218088061:
                break;
            case -180371167:
                break;
            case -131262666:
                break;
            case -5812857:
                break;
            case 3355:
                break;
            case 65759:
                break;
            case 110026:
                break;
            case 116076:
                break;
            case 2192268:
                break;
            case 2224947:
                break;
            case 2368702:
                break;
            case 2394661:
                break;
            case 2579998:
                break;
            case 2599333:
                break;
            case 3059181:
                break;
            case 3076014:
                break;
            case 3560141:
                break;
            case 3601339:
                break;
            case 8777024:
                break;
            case 28778089:
                break;
            case 29963587:
                break;
            case 57185780:
                break;
            case 57208314:
                break;
            case 57320750:
                break;
            case 63955982:
                break;
            case 64711720:
                break;
            case 65189916:
                break;
            case 65298671:
                break;
            case 69076575:
                break;
            case 74219460:
                break;
            case 74526880:
                break;
            case 78727453:
                break;
            case 78733291:
                break;
            case 192873343:
                break;
            case 194378184:
                break;
            case 246938863:
                break;
            case 269058788:
                break;
            case 289362821:
                break;
            case 325021616:
                break;
            case 353103893:
                break;
            case 369315063:
                break;
            case 375032009:
                break;
            case 383030819:
                break;
            case 438421327:
                break;
            case 487334413:
                break;
            case 491858238:
                break;
            case 505523517:
                break;
            case 516961236:
                break;
            case 665843328:
                break;
            case 671337916:
                break;
            case 737478748:
                break;
            case 738893626:
                break;
            case 745969447:
                break;
            case 770498827:
                break;
            case 776138553:
                break;
            case 828944778:
                break;
            case 846088000:
                break;
            case 850563927:
                break;
            case 851278306:
                break;
            case 873235173:
                break;
            case 908763827:
                break;
            case 933423720:
                break;
            case 973193329:
                break;
            case 997117913:
                break;
            case 1071332590:
                break;
            case 1076953756:
                break;
            case 1078812459:
                break;
            case 1133777670:
                break;
            case 1142656251:
                break;
            case 1145198778:
                break;
            case 1247831734:
                break;
            case 1260711798:
                break;
            case 1287805733:
                break;
            case 1312904398:
                break;
            case 1343242579:
                break;
            case 1391410207:
                break;
            case 1401244028:
                break;
            case 1410262602:
                break;
            case 1414192097:
                break;
            case 1428236656:
                break;
            case 1445374288:
                break;
            case 1488475261:
                break;
            case 1542263633:
                break;
            case 1592332600:
                break;
            case 1600636622:
                break;
            case 1627523232:
                break;
            case 1681397778:
                break;
            case 1721380104:
                break;
            case 1728372347:
                break;
            case 1733332192:
                break;
            case 1767264297:
                break;
            case 1790214156:
                break;
            case 1792749467:
                break;
            case 1805746613:
                break;
            case 1824308900:
                break;
            case 1830861979:
                break;
            case 1841735333:
                break;
            case 1922784394:
                break;
            case 1957570017:
                break;
            case 1958052158:
                break;
            case 1958247177:
                break;
            case 1965687765:
                break;
            case 1989867553:
                break;
            case 2000952482:
                break;
            case 2023747466:
                break;
            case 2043677302:
                break;
            case 2052815575:
                break;
            case 2082457694:
                break;
            case 2093211201:
                break;
            default:
                break;
        }

        return v;
    }

    private static int field0000;
    private static int field0001;
    private static int field0002;
    private static int field0003;
    private static int field0004;
    private static int field0005;
    private static int field0006;
    private static int field0007;
    private static int field0008;
    private static int field0009;
    private static int field0010;
    private static int field0011;
    private static int field0012;
    private static int field0013;
    private static int field0014;
    private static int field0015;
    private static int field0016;
    private static int field0017;
    private static int field0018;
    private static int field0019;
    private static int field0020;
    private static int field0021;
    private static int field0022;
    private static int field0023;
    private static int field0024;
    private static int field0025;
    private static int field0026;
    private static int field0027;
    private static int field0028;
    private static int field0029;
    private static int field0030;
    private static int field0031;
    private static int field0032;
    private static int field0033;
    private static int field0034;
    private static int field0035;
    private static int field0036;
    private static int field0037;
    private static int field0038;
    private static int field0039;
    private static int field0040;
    private static int field0041;
    private static int field0042;
    private static int field0043;
    private static int field0044;
    private static int field0045;
    private static int field0046;
    private static int field0047;
    private static int field0048;
    private static int field0049;
    private static int field0050;
    private static int field0051;
    private static int field0052;
    private static int field0053;
    private static int field0054;
    private static int field0055;
    private static int field0056;
    private static int field0057;
    private static int field0058;
    private static int field0059;
    private static int field0060;
    private static int field0061;
    private static int field0062;
    private static int field0063;
    private static int field0064;
    private static int field0065;
    private static int field0066;
    private static int field0067;
    private static int field0068;
    private static int field0069;
    private static int field0070;
    private static int field0071;
    private static int field0072;
    private static int field0073;
    private static int field0074;
    private static int field0075;
    private static int field0076;
    private static int field0077;
    private static int field0078;
    private static int field0079;
    private static int field0080;
    private static int field0081;
    private static int field0082;
    private static int field0083;
    private static int field0084;
    private static int field0085;
    private static int field0086;
    private static int field0087;
    private static int field0088;
    private static int field0089;
    private static int field0090;
    private static int field0091;
    private static int field0092;
    private static int field0093;
    private static int field0094;
    private static int field0095;
    private static int field0096;
    private static int field0097;
    private static int field0098;
    private static int field0099;
    private static int field0100;
    private static int field0101;
    private static int field0102;
    private static int field0103;
    private static int field0104;
    private static int field0105;
    private static int field0106;
    private static int field0107;
    private static int field0108;
    private static int field0109;
    private static int field0110;
    private static int field0111;
    private static int field0112;
    private static int field0113;
    private static int field0114;
    private static int field0115;
    private static int field0116;
    private static int field0117;
    private static int field0118;
    private static int field0119;
    private static int field0120;
    private static int field0121;
    private static int field0122;
    private static int field0123;
    private static int field0124;
    private static int field0125;
    private static int field0126;
    private static int field0127;
    private static int field0128;
    private static int field0129;
    private static int field0130;
    private static int field0131;
    private static int field0132;
    private static int field0133;
    private static int field0134;
    private static int field0135;
    private static int field0136;
    private static int field0137;
    private static int field0138;
    private static int field0139;
    private static int field0140;
    private static int field0141;
    private static int field0142;
    private static int field0143;
    private static int field0144;
    private static int field0145;
    private static int field0146;
    private static int field0147;
    private static int field0148;
    private static int field0149;
    private static int field0150;
    private static int field0151;
    private static int field0152;
    private static int field0153;
    private static int field0154;
    private static int field0155;
    private static int field0156;
    private static int field0157;
    private static int field0158;
    private static int field0159;
    private static int field0160;
    private static int field0161;
    private static int field0162;
    private static int field0163;
    private static int field0164;
    private static int field0165;
    private static int field0166;
    private static int field0167;
    private static int field0168;
    private static int field0169;
    private static int field0170;
    private static int field0171;
    private static int field0172;
    private static int field0173;
    private static int field0174;
    private static int field0175;
    private static int field0176;
    private static int field0177;
    private static int field0178;
    private static int field0179;
    private static int field0180;
    private static int field0181;
    private static int field0182;
    private static int field0183;
    private static int field0184;
    private static int field0185;
    private static int field0186;
    private static int field0187;
    private static int field0188;
    private static int field0189;
    private static int field0190;
    private static int field0191;
    private static int field0192;
    private static int field0193;
    private static int field0194;
    private static int field0195;
    private static int field0196;
    private static int field0197;
    private static int field0198;
    private static int field0199;
    private static int field0200;
    private static int field0201;
    private static int field0202;
    private static int field0203;
    private static int field0204;
    private static int field0205;
    private static int field0206;
    private static int field0207;
    private static int field0208;
    private static int field0209;
    private static int field0210;
    private static int field0211;
    private static int field0212;
    private static int field0213;
    private static int field0214;
    private static int field0215;
    private static int field0216;
    private static int field0217;
    private static int field0218;
    private static int field0219;
    private static int field0220;
    private static int field0221;
    private static int field0222;
    private static int field0223;
    private static int field0224;
    private static int field0225;
    private static int field0226;
    private static int field0227;
    private static int field0228;
    private static int field0229;
    private static int field0230;
    private static int field0231;
    private static int field0232;
    private static int field0233;
    private static int field0234;
    private static int field0235;
    private static int field0236;
    private static int field0237;
    private static int field0238;
    private static int field0239;
    private static int field0240;
    private static int field0241;
    private static int field0242;
    private static int field0243;
    private static int field0244;
    private static int field0245;
    private static int field0246;
    private static int field0247;
    private static int field0248;
    private static int field0249;
    private static int field0250;
    private static int field0251;
    private static int field0252;
    private static int field0253;
    private static int field0254;
    private static int field0255;
    private static int field0256;
    private static int field0257;
    private static int field0258;
    private static int field0259;
    private static int field0260;
    private static int field0261;
    private static int field0262;
    private static int field0263;
    private static int field0264;
    private static int field0265;
    private static int field0266;
    private static int field0267;
    private static int field0268;
    private static int field0269;
    private static int field0270;
    private static int field0271;
    private static int field0272;
    private static int field0273;
    private static int field0274;
    private static int field0275;
    private static int field0276;
    private static int field0277;
    private static int field0278;
    private static int field0279;
    private static int field0280;
    private static int field0281;
    private static int field0282;
    private static int field0283;
    private static int field0284;
    private static int field0285;
    private static int field0286;
    private static int field0287;
    private static int field0288;
    private static int field0289;
    private static int field0290;
    private static int field0291;
    private static int field0292;
    private static int field0293;
    private static int field0294;
    private static int field0295;
    private static int field0296;
    private static int field0297;
    private static int field0298;
    private static int field0299;
    private static int field0300;
    private static int field0301;
    private static int field0302;
    private static int field0303;
    private static int field0304;
    private static int field0305;
    private static int field0306;
    private static int field0307;
    private static int field0308;
    private static int field0309;
    private static int field0310;
    private static int field0311;
    private static int field0312;
    private static int field0313;
    private static int field0314;
    private static int field0315;
    private static int field0316;
    private static int field0317;
    private static int field0318;
    private static int field0319;
    private static int field0320;
    private static int field0321;
    private static int field0322;
    private static int field0323;
    private static int field0324;
    private static int field0325;
    private static int field0326;
    private static int field0327;
    private static int field0328;
    private static int field0329;
    private static int field0330;
    private static int field0331;
    private static int field0332;
    private static int field0333;
    private static int field0334;
    private static int field0335;
    private static int field0336;
    private static int field0337;
    private static int field0338;
    private static int field0339;
    private static int field0340;
    private static int field0341;
    private static int field0342;
    private static int field0343;
    private static int field0344;
    private static int field0345;
    private static int field0346;
    private static int field0347;
    private static int field0348;
    private static int field0349;
    private static int field0350;
    private static int field0351;
    private static int field0352;
    private static int field0353;
    private static int field0354;
    private static int field0355;
    private static int field0356;
    private static int field0357;
    private static int field0358;
    private static int field0359;
    private static int field0360;
    private static int field0361;
    private static int field0362;
    private static int field0363;
    private static int field0364;
    private static int field0365;
    private static int field0366;
    private static int field0367;
    private static int field0368;
    private static int field0369;
    private static int field0370;
    private static int field0371;
    private static int field0372;
    private static int field0373;
    private static int field0374;
    private static int field0375;
    private static int field0376;
    private static int field0377;
    private static int field0378;
    private static int field0379;
    private static int field0380;
    private static int field0381;
    private static int field0382;
    private static int field0383;
    private static int field0384;
    private static int field0385;
    private static int field0386;
    private static int field0387;
    private static int field0388;
    private static int field0389;
    private static int field0390;
    private static int field0391;
    private static int field0392;
    private static int field0393;
    private static int field0394;
    private static int field0395;
    private static int field0396;
    private static int field0397;
    private static int field0398;
    private static int field0399;
    private static int field0400;
    private static int field0401;
    private static int field0402;
    private static int field0403;
    private static int field0404;
    private static int field0405;
    private static int field0406;
    private static int field0407;
    private static int field0408;
    private static int field0409;
    private static int field0410;
    private static int field0411;
    private static int field0412;
    private static int field0413;
    private static int field0414;
    private static int field0415;
    private static int field0416;
    private static int field0417;
    private static int field0418;
    private static int field0419;
    private static int field0420;
    private static int field0421;
    private static int field0422;
    private static int field0423;
    private static int field0424;
    private static int field0425;
    private static int field0426;
    private static int field0427;
    private static int field0428;
    private static int field0429;
    private static int field0430;
    private static int field0431;
    private static int field0432;
    private static int field0433;
    private static int field0434;
    private static int field0435;
    private static int field0436;
    private static int field0437;
    private static int field0438;
    private static int field0439;
    private static int field0440;
    private static int field0441;
    private static int field0442;
    private static int field0443;
    private static int field0444;
    private static int field0445;
    private static int field0446;
    private static int field0447;
    private static int field0448;
    private static int field0449;
    private static int field0450;
    private static int field0451;
    private static int field0452;
    private static int field0453;
    private static int field0454;
    private static int field0455;
    private static int field0456;
    private static int field0457;
    private static int field0458;
    private static int field0459;
    private static int field0460;
    private static int field0461;
    private static int field0462;
    private static int field0463;
    private static int field0464;
    private static int field0465;
    private static int field0466;
    private static int field0467;
    private static int field0468;
    private static int field0469;
    private static int field0470;
    private static int field0471;
    private static int field0472;
    private static int field0473;
    private static int field0474;
    private static int field0475;
    private static int field0476;
    private static int field0477;
    private static int field0478;
    private static int field0479;
    private static int field0480;
    private static int field0481;
    private static int field0482;
    private static int field0483;
    private static int field0484;
    private static int field0485;
    private static int field0486;
    private static int field0487;
    private static int field0488;
    private static int field0489;
    private static int field0490;
    private static int field0491;
    private static int field0492;
    private static int field0493;
    private static int field0494;
    private static int field0495;
    private static int field0496;
    private static int field0497;
    private static int field0498;
    private static int field0499;
    private static int field0500;
    private static int field0501;
    private static int field0502;
    private static int field0503;
    private static int field0504;
    private static int field0505;
    private static int field0506;
    private static int field0507;
    private static int field0508;
    private static int field0509;
    private static int field0510;
    private static int field0511;
    private static int field0512;
    private static int field0513;
    private static int field0514;
    private static int field0515;
    private static int field0516;
    private static int field0517;
    private static int field0518;
    private static int field0519;
    private static int field0520;
    private static int field0521;
    private static int field0522;
    private static int field0523;
    private static int field0524;
    private static int field0525;
    private static int field0526;
    private static int field0527;
    private static int field0528;
    private static int field0529;
    private static int field0530;
    private static int field0531;
    private static int field0532;
    private static int field0533;
    private static int field0534;
    private static int field0535;
    private static int field0536;
    private static int field0537;
    private static int field0538;
    private static int field0539;
    private static int field0540;
    private static int field0541;
    private static int field0542;
    private static int field0543;
    private static int field0544;
    private static int field0545;
    private static int field0546;
    private static int field0547;
    private static int field0548;
    private static int field0549;
    private static int field0550;
    private static int field0551;
    private static int field0552;
    private static int field0553;
    private static int field0554;
    private static int field0555;
    private static int field0556;
    private static int field0557;
    private static int field0558;
    private static int field0559;
    private static int field0560;
    private static int field0561;
    private static int field0562;
    private static int field0563;
    private static int field0564;
    private static int field0565;
    private static int field0566;
    private static int field0567;
    private static int field0568;
    private static int field0569;
    private static int field0570;
    private static int field0571;
    private static int field0572;
    private static int field0573;
    private static int field0574;
    private static int field0575;
    private static int field0576;
    private static int field0577;
    private static int field0578;
    private static int field0579;
    private static int field0580;
    private static int field0581;
    private static int field0582;
    private static int field0583;
    private static int field0584;
    private static int field0585;
    private static int field0586;
    private static int field0587;
    private static int field0588;
    private static int field0589;
    private static int field0590;
    private static int field0591;
    private static int field0592;
    private static int field0593;
    private static int field0594;
    private static int field0595;
    private static int field0596;
    private static int field0597;
    private static int field0598;
    private static int field0599;
    private static int field0600;
    private static int field0601;
    private static int field0602;
    private static int field0603;
    private static int field0604;
    private static int field0605;
    private static int field0606;
    private static int field0607;
    private static int field0608;
    private static int field0609;
    private static int field0610;
    private static int field0611;
    private static int field0612;
    private static int field0613;
    private static int field0614;
    private static int field0615;
    private static int field0616;
    private static int field0617;
    private static int field0618;
    private static int field0619;
    private static int field0620;
    private static int field0621;
    private static int field0622;
    private static int field0623;
    private static int field0624;
    private static int field0625;
    private static int field0626;
    private static int field0627;
    private static int field0628;
    private static int field0629;
    private static int field0630;
    private static int field0631;
    private static int field0632;
    private static int field0633;
    private static int field0634;
    private static int field0635;
    private static int field0636;
    private static int field0637;
    private static int field0638;
    private static int field0639;
    private static int field0640;
    private static int field0641;
    private static int field0642;
    private static int field0643;
    private static int field0644;
    private static int field0645;
    private static int field0646;
    private static int field0647;
    private static int field0648;
    private static int field0649;
    private static int field0650;
    private static int field0651;
    private static int field0652;
    private static int field0653;
    private static int field0654;
    private static int field0655;
    private static int field0656;
    private static int field0657;
    private static int field0658;
    private static int field0659;
    private static int field0660;
    private static int field0661;
    private static int field0662;
    private static int field0663;
    private static int field0664;
    private static int field0665;
    private static int field0666;
    private static int field0667;
    private static int field0668;
    private static int field0669;
    private static int field0670;
    private static int field0671;
    private static int field0672;
    private static int field0673;
    private static int field0674;
    private static int field0675;
    private static int field0676;
    private static int field0677;
    private static int field0678;
    private static int field0679;
    private static int field0680;
    private static int field0681;
    private static int field0682;
    private static int field0683;
    private static int field0684;
    private static int field0685;
    private static int field0686;
    private static int field0687;
    private static int field0688;
    private static int field0689;
    private static int field0690;
    private static int field0691;
    private static int field0692;
    private static int field0693;
    private static int field0694;
    private static int field0695;
    private static int field0696;
    private static int field0697;
    private static int field0698;
    private static int field0699;
    private static int field0700;
    private static int field0701;
    private static int field0702;
    private static int field0703;
    private static int field0704;
    private static int field0705;
    private static int field0706;
    private static int field0707;
    private static int field0708;
    private static int field0709;
    private static int field0710;
    private static int field0711;
    private static int field0712;
    private static int field0713;
    private static int field0714;
    private static int field0715;
    private static int field0716;
    private static int field0717;
    private static int field0718;
    private static int field0719;
    private static int field0720;
    private static int field0721;
    private static int field0722;
    private static int field0723;
    private static int field0724;
    private static int field0725;
    private static int field0726;
    private static int field0727;
    private static int field0728;
    private static int field0729;
    private static int field0730;
    private static int field0731;
    private static int field0732;
    private static int field0733;
    private static int field0734;
    private static int field0735;
    private static int field0736;
    private static int field0737;
    private static int field0738;
    private static int field0739;
    private static int field0740;
    private static int field0741;
    private static int field0742;
    private static int field0743;
    private static int field0744;
    private static int field0745;
    private static int field0746;
    private static int field0747;
    private static int field0748;
    private static int field0749;
    private static int field0750;
    private static int field0751;
    private static int field0752;
    private static int field0753;
    private static int field0754;
    private static int field0755;
    private static int field0756;
    private static int field0757;
    private static int field0758;
    private static int field0759;
    private static int field0760;
    private static int field0761;
    private static int field0762;
    private static int field0763;
    private static int field0764;
    private static int field0765;
    private static int field0766;
    private static int field0767;
    private static int field0768;
    private static int field0769;
    private static int field0770;
    private static int field0771;
    private static int field0772;
    private static int field0773;
    private static int field0774;
    private static int field0775;
    private static int field0776;
    private static int field0777;
    private static int field0778;
    private static int field0779;
    private static int field0780;
    private static int field0781;
    private static int field0782;
    private static int field0783;
    private static int field0784;
    private static int field0785;
    private static int field0786;
    private static int field0787;
    private static int field0788;
    private static int field0789;
    private static int field0790;
    private static int field0791;
    private static int field0792;
    private static int field0793;
    private static int field0794;
    private static int field0795;
    private static int field0796;
    private static int field0797;
    private static int field0798;
    private static int field0799;
    private static int field0800;
    private static int field0801;
    private static int field0802;
    private static int field0803;
    private static int field0804;
    private static int field0805;
    private static int field0806;
    private static int field0807;
    private static int field0808;
    private static int field0809;
    private static int field0810;
    private static int field0811;
    private static int field0812;
    private static int field0813;
    private static int field0814;
    private static int field0815;
    private static int field0816;
    private static int field0817;
    private static int field0818;
    private static int field0819;
    private static int field0820;
    private static int field0821;
    private static int field0822;
    private static int field0823;
    private static int field0824;
    private static int field0825;
    private static int field0826;
    private static int field0827;
    private static int field0828;
    private static int field0829;
    private static int field0830;
    private static int field0831;
    private static int field0832;
    private static int field0833;
    private static int field0834;
    private static int field0835;
    private static int field0836;
    private static int field0837;
    private static int field0838;
    private static int field0839;
    private static int field0840;
    private static int field0841;
    private static int field0842;
    private static int field0843;
    private static int field0844;
    private static int field0845;
    private static int field0846;
    private static int field0847;
    private static int field0848;
    private static int field0849;
    private static int field0850;
    private static int field0851;
    private static int field0852;
    private static int field0853;
    private static int field0854;
    private static int field0855;
    private static int field0856;
    private static int field0857;
    private static int field0858;
    private static int field0859;
    private static int field0860;
    private static int field0861;
    private static int field0862;
    private static int field0863;
    private static int field0864;
    private static int field0865;
    private static int field0866;
    private static int field0867;
    private static int field0868;
    private static int field0869;
    private static int field0870;
    private static int field0871;
    private static int field0872;
    private static int field0873;
    private static int field0874;
    private static int field0875;
    private static int field0876;
    private static int field0877;
    private static int field0878;
    private static int field0879;
    private static int field0880;
    private static int field0881;
    private static int field0882;
    private static int field0883;
    private static int field0884;
    private static int field0885;
    private static int field0886;
    private static int field0887;
    private static int field0888;
    private static int field0889;
    private static int field0890;
    private static int field0891;
    private static int field0892;
    private static int field0893;
    private static int field0894;
    private static int field0895;
    private static int field0896;
    private static int field0897;
    private static int field0898;
    private static int field0899;
    private static int field0900;
    private static int field0901;
    private static int field0902;
    private static int field0903;
    private static int field0904;
    private static int field0905;
    private static int field0906;
    private static int field0907;
    private static int field0908;
    private static int field0909;
    private static int field0910;
    private static int field0911;
    private static int field0912;
    private static int field0913;
    private static int field0914;
    private static int field0915;
    private static int field0916;
    private static int field0917;
    private static int field0918;
    private static int field0919;
    private static int field0920;
    private static int field0921;
    private static int field0922;
    private static int field0923;
    private static int field0924;
    private static int field0925;
    private static int field0926;
    private static int field0927;
    private static int field0928;
    private static int field0929;
    private static int field0930;
    private static int field0931;
    private static int field0932;
    private static int field0933;
    private static int field0934;
    private static int field0935;
    private static int field0936;
    private static int field0937;
    private static int field0938;
    private static int field0939;
    private static int field0940;
    private static int field0941;
    private static int field0942;
    private static int field0943;
    private static int field0944;
    private static int field0945;
    private static int field0946;
    private static int field0947;
    private static int field0948;
    private static int field0949;
    private static int field0950;
    private static int field0951;
    private static int field0952;
    private static int field0953;
    private static int field0954;
    private static int field0955;
    private static int field0956;
    private static int field0957;
    private static int field0958;
    private static int field0959;
    private static int field0960;
    private static int field0961;
    private static int field0962;
    private static int field0963;
    private static int field0964;
    private static int field0965;
    private static int field0966;
    private static int field0967;
    private static int field0968;
    private static int field0969;
    private static int field0970;
    private static int field0971;
    private static int field0972;
    private static int field0973;
    private static int field0974;
    private static int field0975;
    private static int field0976;
    private static int field0977;
    private static int field0978;
    private static int field0979;
    private static int field0980;
    private static int field0981;
    private static int field0982;
    private static int field0983;
    private static int field0984;
    private static int field0985;
    private static int field0986;
    private static int field0987;
    private static int field0988;
    private static int field0989;
    private static int field0990;
    private static int field0991;
    private static int field0992;
    private static int field0993;
    private static int field0994;
    private static int field0995;
    private static int field0996;
    private static int field0997;
    private static int field0998;
    private static int field0999;
}
