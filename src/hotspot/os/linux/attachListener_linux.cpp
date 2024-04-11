#include "services/attachListenerPosix.hpp"
static_assert(sizeof(off_t) == 8, "Expected Large File Support in this file");

int PosixAttachListener::pd_accept(struct sockaddr *addr, socklen_t *len) {
  int s;
  RESTARTABLE(::accept(listener(), addr, len), s);
  return s;
}

bool PosixAttachListener::pd_credential_check(int s) {
  struct ucred cred_info;
  socklen_t optlen = sizeof(cred_info);
  if (::getsockopt(s, SOL_SOCKET, SO_PEERCRED, (void *)&cred_info, &optlen) == -1) {
    log_debug(attach)("Failed to get socket option SO_PEERCRED");
    return false;
  }
  if (!os::Posix::matches_effective_uid_and_gid_or_root(cred_info.uid, cred_info.gid)) {
    log_debug(attach)("euid/egid check failed (%d/%d vs %d/%d)", cred_info.uid, cred_info.gid,
                      geteuid(), getegid());
    return false;
  }
  return true;
}
