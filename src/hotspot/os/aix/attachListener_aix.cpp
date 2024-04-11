#include "services/attachListenerPosix.hpp"

int PosixAttachListener::pd_accept(struct sockaddr *addr, socklen_t *len) {
  int s;
  ::memset(addr, 0, *len);
  // We must prevent accept blocking on the socket if it has been shut down.
  // Therefore we allow interrupts and check whether we have been shut down already.
  if (PosixAttachListener::is_shutdown()) {
    ::close(listener());
    set_listener(-1);
    return -1;
  }
  s = ::accept(listener(), addr, len);
  return s;
}
bool PosixAttachListener::pd_credential_check(int s) {
  struct peercred_struct cred_info;
  socklen_t optlen = sizeof(cred_info);
  if (::getsockopt(s, SOL_SOCKET, SO_PEERID, (void *)&cred_info, &optlen) == -1) {
    log_debug(attach)("Failed to get socket option SO_PEERID");
    return false;
  }

  if (!os::Posix::matches_effective_uid_and_gid_or_root(cred_info.euid, cred_info.egid)) {
    log_debug(attach)("euid/egid check failed (%d/%d vs %d/%d)", cred_info.euid, cred_info.egid,
                      geteuid(), getegid());
    return false;
  }
  return true;
}
