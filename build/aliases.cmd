[COMPILE AND RUN]
javac -d build/classes/ -cp "W:\commons-net-3.6.jar"  src/fs/ftp/*.java src/fs/ftp/handler/*.java && java -cp "W:\commons-net-3.6.jar;build/classes/" fs.ftp.FTPShell --debug

[SIMPLY RUN]
java -cp "W:\commons-net-3.6.jar;build/classes/" fs.ftp.FTPShell --debug