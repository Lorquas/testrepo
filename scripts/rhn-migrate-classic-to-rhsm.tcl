#!/usr/bin/expect

### Author: John Sefler
### This is a tcl expect script used to help automate the execution of rhn-migrate-classic-to-rhsm.
### The arguments to this script are passed as responses to the interactive input prompts by rhn-migrate-classic-to-rhsm.

# parse arguments to this script
set tool rhn-migrate-classic-to-rhsm
set options      [lindex $argv 0]
set rhnUsername  [lindex $argv 1]
set rhnPassword  [lindex $argv 2]
set regUsername  [lindex $argv 3]
set regPassword  [lindex $argv 4]
set regOrg       [lindex $argv 5]
set slaIndex     [lindex $argv 6]
if {$argc != 7} {
  puts "Usage: ${argv0} ${tool}-options rhnUsername rhnPassword regUsername regPassword regOrg slaIndex"
  puts "(pass \"null\" for argument values that you do not expect $tool to interactively prompt for)"
  exit -1
}

# debugging
#puts options=$options
#puts rhnUsername=$rhnUsername
#puts rhnPassword=$rhnPassword
#puts regUsername=$regUsername
#puts regPassword=$regPassword
#puts regOrg=$regOrg
#puts slaIndex=$slaIndex

# launch rhn-migrate-classic-to-rhsm with options
if {$options != "null"} {
  eval spawn $tool $options;   # use eval to expand the $options into multiple argument options to the tool
} else {
  spawn $tool
}

# configure what to do when an unexpected eof occurs before or after the next expect call (MUST BE DEFINED AFTER spawn)
expect_after eof exit
expect_before eof exit
set timeout 180


# now respond to the interactive prompts from rhn-migrate-classic-to-rhsm ...

if {$rhnUsername != "null"} {
  set prompt "Red Hat account:";  # "RHN Username:";	# changed by bug 847380
  expect $prompt {send "${rhnUsername}\r"} timeout {puts "WARNING: Timed out expecting prompt: ${prompt}"; exit -1}
  set prompt "Password:";
  expect $prompt {send "${rhnPassword}\r"} timeout {puts "WARNING: Timed out expecting prompt: ${prompt}"; exit -1}
}
if {$regUsername != "null"} {
  set prompt "System Engine Username:";
  expect $prompt {send "${regUsername}\r"} timeout {puts "WARNING: Timed out expecting prompt: ${prompt}"; exit -1}
  set prompt "Password:";
  expect $prompt {send "${regPassword}\r"} timeout {puts "WARNING: Timed out expecting prompt: ${prompt}"; exit -1}
}
if {$regOrg != "null"} {
  set prompt "Org:";
  expect $prompt {send "${regOrg}\r"} timeout {puts "WARNING: Timed out expecting prompt: ${prompt}"; exit -1}
}
if {$slaIndex != "null"} {
  set prompt "Please select a service level agreement for this system.";
  expect $prompt {send "${slaIndex}\r"} timeout {puts "WARNING: Timed out expecting prompt: ${prompt}"; exit -1}
}


# interact with the program again so as to catch the remaining stdout and exit code from the program
set interactiveTimeout 240; 	# use a timeout in case there is an unexpected interactive prompt blocking the program from completing.  IMPORTANT: this timeout must be greater than the time needed for subscription-manager registration with autosubscribe
interact timeout $interactiveTimeout {puts "WARNING: Timed out after ${interactiveTimeout} seconds expecting ${tool} to have already completed."; exit -1};
catch wait reason
exit [lindex $reason 3]
