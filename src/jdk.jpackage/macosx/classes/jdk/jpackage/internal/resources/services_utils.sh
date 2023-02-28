#
# Register $@ .plist files with launchd service.
#
register_services ()
{
  for daemonPlistFilePath in "$@"; do
    daemonPlistFileName="${daemonPlistFilePath#/Library/LaunchDaemons/}";

    /bin/launchctl load "$daemonPlistFilePath"

    /bin/launchctl start "${daemonPlistFileName%.plist}"
  done
}


#
# Unregister $@ .plist files with launchd service.
#
unregister_services ()
{
  for daemonPlistFilePath in "$@"; do
    daemonPlistFileName="${daemonPlistFilePath#/Library/LaunchDaemons/}";

    sudo /bin/launchctl stop "${daemonPlistFileName%.plist}"

    sudo /bin/launchctl unload "$daemonPlistFilePath"

    test -z "$delete_plist_files" || sudo rm -f "$daemonPlistFilePath"
  done
}
