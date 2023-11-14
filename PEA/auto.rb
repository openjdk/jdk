#!/usr/bin/env ruby

PROBLEM_LIST = ["run_composite.sh", "run_merge_if_else_paranoid.sh", "run_wrong_bci_after_motion.sh"]

if $0 == __FILE__
  puts  "using #{%x|which java|}"
  java_version = `java --version`

  jvm_options ="-Xlog:gc -XX:+DoPartialEscapeAnalysis"
  if java_version =~ /\(.*debug build/
    jvm_options << " -XX:+PrintEscapeAnalysis -XX:+PrintEliminateAllocations -XX:+PrintOptoStatistics"
  end
  puts jvm_options

  failed = []
  Dir.glob("run*.sh").each do |t|
    print "[#{t}]"

    ret = system("sh ./#{t} #{jvm_options} > #{t}.log")
    if ret == nil then
      puts "\t fail to execute."
    else
      # exitcode == 3 is out of memory. acceptable.
      if ret or $?.exitstatus == 3 then
        puts "\tpassed."
      elsif  PROBLEM_LIST.include? t then
        puts "\tis a known issue."
      else
        puts "\tfailed!"
        failed.push("[#{t}]")
      end
    end
  end

  if failed.length() > 0
    puts "failed #{failed.length} tests:"
    failed.each { |test| puts "\t#{test}" }
    exit(1)
  end
  puts "passed all tests!"
end
