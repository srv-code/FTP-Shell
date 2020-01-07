#!/bin/bash
jar -cvfe0 artifacts/ftpshell.jar fs.ftp.shell.FTPShell -C out . -C "/mnt/c/Program Files/Java/jdk1.8.0_212/jre/lib/proj/apache/" commons-net-3.6.jar
