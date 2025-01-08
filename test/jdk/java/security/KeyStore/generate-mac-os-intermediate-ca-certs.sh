#!/usr/bin/env bash

set -ex

cd "$(dirname "$0")"

openssl genrsa -out root.key 2048
openssl req -x509 -sha256 -nodes -extensions v3_ca -key root.key -subj "/C=US/O=Example/CN=Example CA" -days 3650 -out test-ca.pem

openssl genrsa -out intermediate.key 2048
openssl req -new -sha256 -nodes -key intermediate.key  \
  -subj "/C=US/O=Example/CN=Example Intermediate CA" -out test-intermediate-ca.csr

openssl x509 -req \
 -extensions v3_ca \
 -extfile openssl.cnf \
 -in test-intermediate-ca.csr \
 -CA test-ca.pem \
 -CAkey root.key \
 -CAcreateserial \
 -out test-intermediate-ca.pem \
 -days 3650 \
 -sha256

rm -f root.key test-intermediate-ca.csr intermediate.key test-ca.srl
