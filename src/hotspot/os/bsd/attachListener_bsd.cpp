#include "services/attachListenerPosix.hpp"

int PosixAttachListener::pd_accept(struct sockaddr *addr, socklen_t *len) {
  int s;
  RESTARTABLE(::accept(listener(), addr, len), s);
  return s;
}

bool PosixAttachListener::pd_credential_check(int s) {
  uid_t puid;
  gid_t pgid;
  if (::getpeereid(s, &puid, &pgid) != 0) {
    log_debug(attach)("Failed to get peer id");
    return false;
  }
  if (!os::Posix::matches_effective_uid_and_gid_or_root(puid, pgid)) {
    log_debug(attach)("euid/egid check failed (%d/%d vs %d/%d)", puid, pgid, geteuid(), getegid());
    return false;
  }
  return true;
}
