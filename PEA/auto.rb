#!/usr/bin/env ruby

JVM_FLAGS="-XX:+PrintEscapeAnalysis -XX:+PrintEliminateAllocations -XX:-PrintOptoAssembly -Xlog:gc -XX:+PrintOptoStatistics -XX:+DoPartialEscapeAnalysis"
PROBLEM_LIST = ["run_composite.sh", "run_merge_if_else_paranoid.sh"]

if $0 == __FILE__
  puts  "using #{%x|which java|}"
  system("java --version")

  Dir.glob("run*.sh").each do |t|
    print "[#{t}]"

    ret = system("sh ./#{t} #{JVM_FLAGS} > #{t}.log")
    if ret == nil then
      puts "\t fail to execute."
    else
      # exitcode == 3 is out of memory. acceptable.
      if ret or $?.exitstatus == 3 then
        puts "\tpassed."
      elsif  PROBLEM_LIST.include? t then
        puts "\ta known issue."
      else
        puts "\tfailed!"
        exit(1)
      end
    end
  end
end
