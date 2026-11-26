#include "psha2.hpp"

#if TEST_CAPI
#include "psha2_capi.h"
#else
#include <openssl/sha.h>
#include <openssl/evp.h>
#endif

#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <cstring>
#include <ctime>

int main(int argc, char **argv) {
  srand(time(NULL));

  bool success = true;

  for(int i=0;i<10000;i++) {
    int len = (rand() + ((int64_t)RAND_MAX + 1) * rand()) % (1 << (1 + (rand() % 18)));
    unsigned char *plaintext = (unsigned char *)malloc(len);
    for(int i=0;i<len;i++) plaintext[i] = rand() & 0xff;

    //

    PSHA2_256_Internal psha;
    unsigned char dgst0[SHA256_DIGEST_LENGTH];

    psha.append(plaintext, len);
    psha.finalize_bytes(dgst0);

    //

    unsigned char dgst1[SHA256_DIGEST_LENGTH];

    EVP_MD_CTX *ctx = EVP_MD_CTX_new();
    EVP_DigestInit_ex(ctx, EVP_sha256(), NULL);
    EVP_DigestUpdate(ctx, plaintext, len);
    EVP_DigestFinal_ex(ctx, dgst1, NULL);
    EVP_MD_CTX_free(ctx);

    //

    if (memcmp(dgst0, dgst1, SHA256_DIGEST_LENGTH) != 0) success = false;

    free(plaintext);
  }

  if (success) {
    printf("OK\n");
    return 0;
  }

  printf("NG\n");
  return -1;
}
