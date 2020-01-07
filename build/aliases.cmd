[COMPILE AND RUN]
javac -d out -cp "W:\commons-net-3.6.jar" src/fs/ftp/shell/StandardExitCodes.java src/fs/ftp/shell/FTPShell.java src/fs/ftp/handler/FTPHandler.java && java -cp "W:\commons-net-3.6.jar;out/" fs.ftp.shell.FTPShell --debug

[SIMPLY RUN]
java -cp "W:\commons-net-3.6.jar;out/" fs.ftp.shell.FTPShell --debug