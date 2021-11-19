#
# Register $@ unit files with systemd service.
#
register_units ()
{
  for unit in "$@"; do
    systemctl enable --now "$unit"
  done
}


#
# Unregister $@ unit files with systemd service.
#
unregister_units ()
{
  for unit in "$@"; do
    local unit_name=`basename "$unit"`
    if [ -n `systemctl list-unit-files "$unit_name"` ]; then
      systemctl disable --now "$unit_name"
    fi
  done
}
