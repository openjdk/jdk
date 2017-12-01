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
 *
 */

#include "precompiled.hpp"
#include "gc/g1/g1HeapVerifier.hpp"
#include "logging/logConfiguration.hpp"
#include "unittest.hpp"

TEST(G1HeapVerifier, parse) {
  G1HeapVerifier verifier(NULL);

  LogConfiguration::configure_stdout(LogLevel::Off, true, LOG_TAGS(gc, verify));

  // Default is to verify everything.
  ASSERT_TRUE(verifier.should_verify(G1HeapVerifier::G1VerifyAll));
  ASSERT_TRUE(verifier.should_verify(G1HeapVerifier::G1VerifyYoungOnly));
  ASSERT_TRUE(verifier.should_verify(G1HeapVerifier::G1VerifyInitialMark));
  ASSERT_TRUE(verifier.should_verify(G1HeapVerifier::G1VerifyMixed));
  ASSERT_TRUE(verifier.should_verify(G1HeapVerifier::G1VerifyRemark));
  ASSERT_TRUE(verifier.should_verify(G1HeapVerifier::G1VerifyCleanup));
  ASSERT_TRUE(verifier.should_verify(G1HeapVerifier::G1VerifyFull));

  // Setting one will disable all other.
  verifier.parse_verification_type("full");
  ASSERT_FALSE(verifier.should_verify(G1HeapVerifier::G1VerifyAll));
  ASSERT_FALSE(verifier.should_verify(G1HeapVerifier::G1VerifyYoungOnly));
  ASSERT_FALSE(verifier.should_verify(G1HeapVerifier::G1VerifyInitialMark));
  ASSERT_FALSE(verifier.should_verify(G1HeapVerifier::G1VerifyMixed));
  ASSERT_FALSE(verifier.should_verify(G1HeapVerifier::G1VerifyRemark));
  ASSERT_FALSE(verifier.should_verify(G1HeapVerifier::G1VerifyCleanup));
  ASSERT_TRUE(verifier.should_verify(G1HeapVerifier::G1VerifyFull));

  // Verify case sensitivity.
  verifier.parse_verification_type("YOUNG-ONLY");
  ASSERT_FALSE(verifier.should_verify(G1HeapVerifier::G1VerifyYoungOnly));
  verifier.parse_verification_type("young-only");
  ASSERT_TRUE(verifier.should_verify(G1HeapVerifier::G1VerifyYoungOnly));

  // Verify perfect match
  verifier.parse_verification_type("mixedgc");
  ASSERT_FALSE(verifier.should_verify(G1HeapVerifier::G1VerifyMixed));
  verifier.parse_verification_type("mixe");
  ASSERT_FALSE(verifier.should_verify(G1HeapVerifier::G1VerifyMixed));
  verifier.parse_verification_type("mixed");
  ASSERT_TRUE(verifier.should_verify(G1HeapVerifier::G1VerifyMixed));

  // Verify the last three
  verifier.parse_verification_type("initial-mark");
  verifier.parse_verification_type("remark");
  verifier.parse_verification_type("cleanup");
  ASSERT_TRUE(verifier.should_verify(G1HeapVerifier::G1VerifyRemark));
  ASSERT_TRUE(verifier.should_verify(G1HeapVerifier::G1VerifyCleanup));

  // Enabling all is not the same as G1VerifyAll
  ASSERT_FALSE(verifier.should_verify(G1HeapVerifier::G1VerifyAll));
}
