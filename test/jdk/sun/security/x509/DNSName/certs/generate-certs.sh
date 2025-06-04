#!/usr/bin/env bash

set -e

###############################################################
# CA with a leading period in the name constraint #
###############################################################
mkdir -p withLeadingPeriod

openssl req \
  -newkey rsa:1024 \
  -keyout withLeadingPeriod/ca.key \
  -out withLeadingPeriod/ca.csr \
  -subj "/C=US/O=Example/CN=Example CA with period" \
  -nodes

openssl x509 \
  -req \
  -in withLeadingPeriod/ca.csr \
  -extfile openssl.cnf \
  -extensions withLeadingPeriod \
  -signkey withLeadingPeriod/ca.key \
  -out withLeadingPeriod/ca.pem

# leaf certificate
openssl req \
  -newkey rsa:1024 \
  -keyout withLeadingPeriod/leaf.key \
  -out withLeadingPeriod/leaf.csr \
  -subj '/CN=demo.example.com' \
  -addext 'subjectAltName = DNS:demo.example.com' \
  -nodes

openssl x509 \
  -req \
  -in withLeadingPeriod/leaf.csr \
  -CAcreateserial \
  -CA withLeadingPeriod/ca.pem \
  -CAkey withLeadingPeriod/ca.key \
  -out withLeadingPeriod/leaf.pem


# ##################################################################
# # CA without a leading period in the name contraint #
# ##################################################################
mkdir -p withoutLeadingPeriod

openssl req \
  -newkey rsa:1024 \
  -keyout withoutLeadingPeriod/ca.key \
  -out withoutLeadingPeriod/ca.csr \
  -subj "/C=US/O=Example/CN=Example CA without period" \
  -nodes

openssl x509 \
  -req \
  -in withoutLeadingPeriod/ca.csr \
  -extfile openssl.cnf \
  -extensions withoutLeadingPeriod \
  -signkey withoutLeadingPeriod/ca.key \
  -out withoutLeadingPeriod/ca.pem

# leaf certificate
openssl req \
  -newkey rsa:1024 \
  -keyout withoutLeadingPeriod/leaf.key \
  -out withoutLeadingPeriod/leaf.csr \
  -subj '/CN=demo.example.com' \
  -addext 'subjectAltName = DNS:demo.example.com' \
  -nodes

openssl x509 \
  -req \
  -in withoutLeadingPeriod/leaf.csr \
  -CAcreateserial \
  -CA withoutLeadingPeriod/ca.pem \
  -CAkey withoutLeadingPeriod/ca.key \
  -out withoutLeadingPeriod/leaf.pem


# # Verify both leaf certificates

set +e
openssl verify \
  -CAfile withLeadingPeriod/ca.pem \
  withLeadingPeriod/leaf.pem

openssl verify \
  -CAfile withoutLeadingPeriod/ca.pem \
  withoutLeadingPeriod/leaf.pem
  