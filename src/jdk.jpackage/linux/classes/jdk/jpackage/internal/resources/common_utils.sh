file_belongs_to_single_package ()
{
  if [ ! -e "$1" ]; then
    false
  elif [ "$package_type" = rpm ]; then
    test `rpm -q --whatprovides "$1" | wc -l` = 1
  elif [ "$package_type" = deb ]; then
    test `dpkg -S "$1" | wc -l` = 1
  else
    exit 1
  fi
}


do_if_file_belongs_to_single_package ()
{
  local file="$1"
  shift

  if file_belongs_to_single_package "$file"; then
    "$@"
  fi
}
