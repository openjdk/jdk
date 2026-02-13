#include "psha2.hpp"
#include "psha2_capi.h"

#include <cstdlib>

const EVP_MD *EVP_sha256(void) {
  static const int one[1] = { 1 };
  return &one[0];
}

size_t EVP_MD_size(const EVP_MD *e) {
  if (*e == 1) return SHA256_DIGEST_LENGTH;
  return 0;
}

int EVP_MD_get_size(const EVP_MD *e) {
  if (*e == 1) return SHA256_DIGEST_LENGTH;
  return 0;
}

EVP_MD_CTX *EVP_MD_CTX_new(void) {
  return (EVP_MD_CTX *)calloc(1, sizeof(EVP_MD_CTX));
}

int EVP_DigestInit_ex(EVP_MD_CTX *ctx, const EVP_MD *type, ENGINE *impl) {
  ctx->type = *type;
  if (*type == 1) {
    ctx->psha_256 = new PSHA2_256_Internal();
    return 1;
  }
  return 0;
}

int EVP_DigestUpdate(EVP_MD_CTX *ctx, const void *d, size_t cnt) {
  if (ctx->type == 1) {
    ctx->psha_256->append(d, cnt);
    return 1;
  }
  return 0;
}

int EVP_DigestFinal_ex(EVP_MD_CTX *ctx, unsigned char *md, unsigned int *s) {
  if (ctx->type == 1) {
    ctx->psha_256->finalize_bytes(md);
    if (s) *s = SHA256_DIGEST_LENGTH;
    return 1;
  }
  return 0;
}

void EVP_MD_CTX_free(EVP_MD_CTX *ctx) {
  if (ctx->type == 1) {
    delete ctx->psha_256;
    ctx->psha_256 = nullptr;
  }
  free(ctx);
}
