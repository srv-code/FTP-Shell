package fs.ftp.handler;

import fs.ftp.shell.FTPShell;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileFilter;
import org.apache.commons.net.ftp.FTPReply;


public class FTPHandler implements AutoCloseable {
    /* Path abbreviations */
    public static final String ROOT_PATH = "/";
    public static final String CURRENT_PATH_ABBREVIATION = ".";
    public static final String PARENT_PATH_ABBREVIATION = "..";
    
    private static final FTPClient ftpClient = new FTPClient();
    private static final int PORT = 21;    
    public  static final String ANONYMOUS_USER_NAME = "anonymous";
    public  static final String ANONYMOUS_USER_PASSWORD = "";
    
    private String hostName;
    private String userName;
    private String userPassword;
    private String currentWorkingDirectory  = null;
    private boolean isSessionAlive          = false;  /* default value set */
    private File tempTransferDir            = null;
    private final Map<String,FTPFile> succeededFTPFiles           = new LinkedHashMap<>();
    private final Map<String,FTPFile> failedFTPFiles              = new LinkedHashMap<>();
    private final List<Map<String,FTPFile>> verboseFTPFileList    = new ArrayList<>(2);
    private final List<List<File>> verboseLocalFileList           = new ArrayList<>(2);
    private final List<File> succeededLocalFiles                  = new ArrayList<>();
    private final List<File> failedLocalFiles                     = new ArrayList<>();
    private long dirCount=0L, fileCount=0L; /* default value set */
    
    
    public FTPHandler() {
        verboseFTPFileList.add(succeededFTPFiles);
        verboseFTPFileList.add(failedFTPFiles);
        
        verboseLocalFileList.add(succeededLocalFiles);
        verboseLocalFileList.add(failedLocalFiles);
    }    
    
    public boolean isSessionAlive() {
//        return ftpClient.isConnected();
        return isSessionAlive;
    }
    
    public String getHostName() {
        return hostName;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public String getServerHelpString() throws IOException {
        ensureConnectivity();
        return ftpClient.listHelp();
    }
    
    public List<String> getConnectionStatistics() throws IOException {
        ensureConnectivity();
        
        List<String> info = new ArrayList<>();
        info.add("Host name: " + hostName);
        info.add("Passive host name: " + Objects.toString(ftpClient.getPassiveHost(), "N/A"));
        info.add("Passive local address: " + Objects.toString(ftpClient.getPassiveLocalIPAddress(), "N/A"));
        info.add("Passive port: " + ftpClient.getPassivePort());
        info.add("Default port: " + ftpClient.getDefaultPort());
        info.add("Local address: " + Objects.toString(ftpClient.getLocalAddress(), "N/A"));
        info.add("Local port: " + ftpClient.getLocalPort());
        info.add("Proxy: " + Objects.toString(ftpClient.getProxy(), "N/A"));
        info.add("Remote address: " + Objects.toString(ftpClient.getRemoteAddress()));
        info.add("Remote port: " + ftpClient.getRemotePort());
        info.add("User name: " + userName);
        info.add("Status: " + Objects.toString(ftpClient.getStatus(), "N/A"));
        info.add("System type: " + Objects.toString(ftpClient.getSystemType(), "N/A"));
        info.add("Control keep alive reply timeout: " + ftpClient.getControlKeepAliveReplyTimeout() + "ms");
        info.add("Control keep alive timeout: " + ftpClient.getControlKeepAliveTimeout() + "ms");
        switch(ftpClient.getDataConnectionMode()) {
            case FTPClient.ACTIVE_LOCAL_DATA_CONNECTION_MODE:
                info.add("Data connection mode: ACTIVE_LOCAL_DATA_CONNECTION_MODE");
                break;
            case FTPClient.ACTIVE_REMOTE_DATA_CONNECTION_MODE:
                info.add("Data connection mode: ACTIVE_REMOTE_DATA_CONNECTION_MODE");
                break;
            case FTPClient.PASSIVE_LOCAL_DATA_CONNECTION_MODE:
                info.add("Data connection mode: PASSIVE_LOCAL_DATA_CONNECTION_MODE");
                break;
            case FTPClient.PASSIVE_REMOTE_DATA_CONNECTION_MODE:
                info.add("Data connection mode: PASSIVE_REMOTE_DATA_CONNECTION_MODE");
                break;
            default: 
                info.add("Data connection mode: " + ftpClient.getDataConnectionMode());                
        }
        info.add("Default timeout: " + ftpClient.getDefaultTimeout() + "ms");
        info.add("Socket timeout: " + ftpClient.getSoTimeout() + "ms");
        info.add("Connect timeout: " + ftpClient.getConnectTimeout() + "ms");
        info.add("Restart offset: " + ftpClient.getRestartOffset());
        info.add("Is remote verification enabled: " + ftpClient.isRemoteVerificationEnabled());
        info.add("Should use EPSV with IPv4: " + ftpClient.isUseEPSVwithIPv4());
        return info;
    }
    
    private void reinitialiseSession() throws IOException {
        try {
            if(!ftpClient.reinitialize())  { /* if FTPClient#reinitialize method fails 
                                                then try manually connecting and moving 
                                                to the last working directory */
                throw new IOException();
            }
        } catch(IOException e) {
            /* manually restore connection */
            isSessionAlive = false;
            String lastCWDPath = currentWorkingDirectory;
            connect();
            changeWorkingDirectory(lastCWDPath);
            isSessionAlive = true;
        }
    }

    /* new methods to be developed */
    public void connect(final String hostName, final String userName, final String userPassword) throws IOException {
        this.hostName = hostName;
        this.userName = userName;
        this.userPassword = userPassword;
//        this.currentWorkingDirectoryPath = ROOT_PATH;
        connect();
    }
    
    /* new methods to be developed */
    public void connect(final String hostName) throws IOException {
        connect(hostName, ANONYMOUS_USER_NAME, ANONYMOUS_USER_PASSWORD);
    }
    
    private String getParentPath(final String pathString) {
//        Path parent = Paths.get(pathString).getParent();
//        return parent == null ? null : parent.toString();
        return pathString.substring(0, pathString.lastIndexOf('/')+1);
    }

    public long[] count(final String requestedRootPath) throws IOException {        
        getRootTreeInfo(requestedRootPath);
        return new long[] { dirCount, fileCount };
    }
    
    public List<String> tree(final String requestedRootPath) throws IOException {        
        return getRootTreeInfo(requestedRootPath);
    }
    
    private List<String> getRootTreeInfo(final String requestedRootPath) throws IOException {
        ensureConnectivity();
        
//        ftpClient.setListHiddenFiles(true);
        String rootPath;
        if(requestedRootPath.equals(CURRENT_PATH_ABBREVIATION))
            rootPath = ftpClient.printWorkingDirectory();
        else if(requestedRootPath.equals(PARENT_PATH_ABBREVIATION))
            rootPath = getParentPath(ftpClient.printWorkingDirectory());
        else 
            rootPath = requestedRootPath;
        
        FTPFile root = getFile(rootPath);
        if(root == null)
            throw new IOException("Non-existent path: " + requestedRootPath);

        List<String> lines = new ArrayList<>();
        dirCount = fileCount = 0L; /* initialize global counters */
        gatherTreeInfo(-1L, getParentPath(rootPath), root, lines);
        lines.add(String.format("  [dirs=%d, files=%d, total=%d]",
                                    dirCount, fileCount, dirCount+fileCount));

        return lines;
    }
    
    private void gatherTreeInfo(long level, 
                                final String parentPath, 
                                final FTPFile file, 
                                final List<String> lines) throws IOException {
        level++;
        if(file.getName().equals(ROOT_PATH)) {
            dirCount++;
            lines.add(indentGraph(level) + ROOT_PATH);
            for(FTPFile child: ftpClient.listFiles(ROOT_PATH))
                gatherTreeInfo(level, ROOT_PATH, child, lines);
        } else if(file.isDirectory()) {
            dirCount++;
            lines.add(indentGraph(level) + file.getName() + "/");
            String newParentPath = parentPath + "/" + file.getName();
            for(FTPFile child: ftpClient.listFiles(newParentPath))
                gatherTreeInfo(level, newParentPath, child, lines);
        } else {
            fileCount++;
            lines.add(indentGraph(level) + file.getName());
        }
    }
    
    private String indentGraph(final long level) {
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<level; i++)
            sb.append("    ");
        sb.append("|-- ");
        return sb.toString();
    }
    
    public List<Map<String,FTPFile>> copy(final List<String> srcPathList, final String dstPath) throws IOException {
        ensureConnectivity();
        return fetch(srcPathList, dstPath, true);
    }
    
    public List<Map<String,FTPFile>> get(final List<String> srcPathList, final String dstPath) throws IOException {
        ensureConnectivity();
        return fetch(srcPathList, dstPath, false);
    }
    
    public List<List<File>> put(final List<String> srcPathList, final String dstDirPath) throws IOException {
        Path remoteCWDFile = Paths.get(ftpClient.printWorkingDirectory());
        Path localCWDFile  = Paths.get(System.getProperty("user.dir"));
        
        String dstDirFullPath = remoteCWDFile.resolve(dstDirPath).normalize().toString();
        FTPFile dstDir = getFile(dstDirFullPath);
        if(dstDir == null)
            throw new IOException("Non-existent destination path: " + dstDirPath);
        if(!dstDir.isDirectory())
            throw new IOException("Destination path not a directory: " + dstDirPath);
        
        List<File> srcFileList = new ArrayList<>();
        for(String path: srcPathList) {
            File filePath = localCWDFile.resolve(path).normalize().toFile();
            if(!filePath.exists())
                throw new IOException("Non-existent source path: " + path);
            srcFileList.add(filePath);
        }
                
        succeededLocalFiles.clear();
        failedLocalFiles.clear();
        for(File file: srcFileList)
            uploadRecursively(file, dstDirFullPath);
        
        return verboseLocalFileList;
    }
    
    private void uploadRecursively(final File srcFile, final String dstPath) throws IOException {
        uploadFile(srcFile, dstPath);
        if(srcFile.isDirectory())
            for(File file: srcFile.listFiles()) 
                uploadRecursively(file, dstPath);
    }
    
    private void uploadFile(final File srcFile, final String dstPath) throws IOException {
        System.out.printf("  // upload: %s%s -> %s\n", srcFile.getPath(), srcFile.isDirectory() ? "/" : "", dstPath); // DEBUG
        
        String newDstFilePath = getValidNameForPasting(srcFile.getName(), srcFile.isDirectory(), dstPath);
        try {
            if(srcFile.isDirectory()) {
                if(!ftpClient.makeDirectory(newDstFilePath))
                    throw new IOException("Cannot create remote directory: " + newDstFilePath);
            } else {
                try (InputStream fin = new BufferedInputStream(new FileInputStream(srcFile))) {
                    if(!ftpClient.storeFile(newDstFilePath, fin))
                        throw new IOException("Cannot upload file to remote location as: " + newDstFilePath);
                }
            }
            succeededLocalFiles.add(srcFile);
        } catch(Exception e) {
            failedLocalFiles.add(srcFile);
        }
    }
    
    /**
     * <p>Copies from the source path recursively to the destination path.</p>
     * @param srcPathList Has to be absolute.
     * @param dstPath Has to be absolute.
     * @return Number of files/dirs copied under srcPath.
     * */
    private List<Map<String,FTPFile>> fetch(final List<String> srcPathList, final String dstPath, final boolean copyOnRemote) throws IOException {
        Path cwdFile = Paths.get(ftpClient.printWorkingDirectory());
        Map<String,FTPFile> mapSrcFiles = new LinkedHashMap<>();
        for(String path: srcPathList) {
            String fullPath = cwdFile.resolve(path).normalize().toString();
            FTPFile file = getFile(fullPath);
            if(file == null)
                throw new IOException("Non-existent source path: " + path);
            mapSrcFiles.put(path, file);
        }
        
        String dstFullPath;
        if(copyOnRemote) { 
            dstFullPath = cwdFile.resolve(dstPath).normalize().toString();
            FTPFile dstFile = getFile(dstFullPath);
            if(dstFile == null)
                throw new IOException("Non-existent destination path: " + dstPath);
            if(!dstFile.isDirectory())
                throw new IOException("Destination path not a directory: " + dstPath);
        } else {
            dstFullPath = dstPath;      
            File fetchDir = new File(dstPath);
            if(!fetchDir.exists())
                throw new IOException("Non-existent fetch location: " + fetchDir.getPath());
            if(!fetchDir.isDirectory())
                throw new IOException("Fetch location not a directory: " + fetchDir.getPath());
        }
        
        succeededFTPFiles.clear();
        failedFTPFiles.clear();        
        if(copyOnRemote) 
            tempTransferDir = FTPShell.getDirTempTransfers();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        
        for(Map.Entry<String,FTPFile> entry: mapSrcFiles.entrySet()) {
            String srcPath = entry.getKey();
            FTPFile srcFile = entry.getValue();
            if(copyOnRemote) {
                if(dstFullPath.startsWith(srcPath))
                    throw new IOException("Destination path is a subpath of the source path: " + srcPath);
                fetchRecursively(getParentPath(srcPath), srcFile, getValidNameForPasting(srcFile.getName(), srcFile.isDirectory(), dstFullPath), true);
            } else 
                fetchRecursively(getParentPath(srcPath), srcFile, dstFullPath, false);
        }
        
        /* Do clean up checks for safeguard -- ideally should already be empty */
        if(copyOnRemote)
            for(File file: tempTransferDir.listFiles())
                file.delete(); /* ignore if operation fails */
        
        return verboseFTPFileList;
    }
    
    /**
     * @return Full path, including parent path and new file name
     */
    private String getValidNameForPasting(final String srcFileName, final boolean isSrcDirectory, final String dstParentPath) throws IOException {
        /* Renames dst file name if already present in dst path */
        int renameAttempt = 0;
        String modFname = srcFileName;
        String newDstPath;
        while(pathExists(newDstPath=dstParentPath+"/"+modFname)) {
//                printDebug("Already exists in dst dir: %s", modFname);
            StringBuilder tmpName = new StringBuilder(srcFileName.length() + 5);

            if(isSrcDirectory) { /* No extension checking for directories */
                tmpName .insert(0, srcFileName)
                        .append(" (").append(++renameAttempt).append(")");
            } else { /* Extension checking for files */
                int dotIdx = srcFileName.lastIndexOf('.');
                String ext = srcFileName.substring(dotIdx == -1 ? srcFileName.length() : dotIdx);
                String base = srcFileName.substring(0, dotIdx == -1 ? srcFileName.length() : dotIdx);
                tmpName .insert(0, base)
                        .append(" (").append(++renameAttempt).append(")")
                        .append(ext);
            }
            modFname = tmpName.toString();
        }

        return newDstPath;
    }

    public List<Map<String,FTPFile>> move(final List<String> srcPathList, final String dstPath) throws IOException {
        ensureConnectivity();
        
        Path cwdFile = Paths.get(ftpClient.printWorkingDirectory());
        String newDstFullPath = cwdFile.resolve(dstPath).normalize().toString();
        if(getFile(newDstFullPath) == null)
            throw new IOException("Non-existent destination path: " + dstPath);
        
        Map<String,FTPFile> mapSrc = new LinkedHashMap<>();
        for(String path: srcPathList) {
            String fullPath = cwdFile.resolve(path).normalize().toString();
            FTPFile file = getFile(fullPath);
            if(file == null)
                throw new IOException("Non-existent root path: " + path);
            mapSrc.put(fullPath, file);
        }
        
        succeededFTPFiles.clear();
        failedFTPFiles.clear();        
        for(Map.Entry<String,FTPFile> entry: mapSrc.entrySet()) {
            try {
                ftpClient.rename(entry.getKey(), newDstFullPath);
                succeededFTPFiles.put(entry.getKey(), entry.getValue());
            } catch(Exception e) {
                failedFTPFiles.put(entry.getKey(), entry.getValue());
            }
        }
        
        return verboseFTPFileList;
    }
    
    /**
     * @param dstPath Has to be absolute
     * */
    private void fetchRecursively(final String srcParentPath, final FTPFile srcFile, final String dstPath, final boolean copyOnRemote) throws IOException {
        fetchFile(srcParentPath, srcFile, dstPath, copyOnRemote);
        if(srcFile.isDirectory()) {
            String newSrcParentPath = srcParentPath+"/"+srcFile.getName();
            for(FTPFile file: ftpClient.listFiles(newSrcParentPath))
                fetchRecursively(newSrcParentPath, file, dstPath+"/"+file.getName(), copyOnRemote);
        }
    }

    private void fetchFile(final String srcParentPath, final FTPFile srcFile, final String dstPath, final boolean copyOnRemote) throws IOException {
        String srcFilePath = srcParentPath+ "/" + srcFile.getName();
        String newDstFilePath = copyOnRemote ? getValidNameForPasting(srcFile.getName(), srcFile.isDirectory(), getParentPath(dstPath)) : dstPath + "/" + srcFile.getName();
        try {
            if(srcFile.isDirectory()) {
                if(copyOnRemote) {
                    /* check for duplicate entry, get proper file name */
                    if(!ftpClient.makeDirectory(newDstFilePath))
                        throw new IOException("Cannot create remote directory: " + newDstFilePath);
                } else {
                    if(!new File(newDstFilePath).mkdir())
                        throw new IOException("Cannot make local directory: " + newDstFilePath);
                }
            } else {
                /* Download to local file */
                File fetchedFile = copyOnRemote ? File.createTempFile(hostName.replace('/', '+') + "_", null, tempTransferDir) : new File(newDstFilePath);
//                tmpFetchedFile.deleteOnExit(); /* for safeguard */
                try (OutputStream fout = new BufferedOutputStream(new FileOutputStream(fetchedFile))) {
                    if(!ftpClient.retrieveFile(srcFilePath, fout))
                        throw new IOException("Cannot fetch remote file: " + srcFilePath);
                }
                
                if(copyOnRemote) {
                    /* Upload to remote dst with correct name */
                    try (InputStream fin = new BufferedInputStream(new FileInputStream(fetchedFile))) {
                        if(!ftpClient.storeFile(newDstFilePath, fin))
                            throw new IOException("Cannot upload file to remote location as: " + newDstFilePath);
                    }
                    fetchedFile.delete(); /* ignore if operation fails */
                }
            }
            succeededFTPFiles.put(srcFilePath, srcFile);
        } catch(Exception e) {
            failedFTPFiles.put(srcFilePath, srcFile);
        }
    }

    /**
      * Gets the FTPFile object for the specified path, if exists.
      * @return FTPFile object if exists else null
      * */
    private FTPFile getFile(final String pathString) throws IOException {
        System.out.println("  // pathString=" + pathString); // DEBUG
        if( pathString.equals("/") || /* ROOT_PATH */
            pathString.equals("//") || 
            pathString.equals("\\") || 
            pathString.equals("\\\\") || 
            pathString.equals("/\\")) {
            FTPFile rootFile = new FTPFile();
            rootFile.setName(ROOT_PATH);
            rootFile.setType(FTPFile.DIRECTORY_TYPE);
            return rootFile;
        }
        
        final Path path = Paths.get(pathString);
        final String parentPath = path.getParent().toString();
        final String fileName = path.getFileName().toString();
        
//        int divIdx = path.lastIndexOf('/')+1;
//        String parent = path.substring(0, divIdx);
//        String child = path.substring(divIdx);
        
        for(FTPFile file: ftpClient.listFiles(parentPath))
            if(file.getName().equals(fileName))
                return file;
        return null;
    }

    public void test() throws Exception {
        System.out.println("ftpClient.printWorkingDirectory()=" + ftpClient.printWorkingDirectory());
        System.out.println("currentWorkingDirectory=" + currentWorkingDirectory);
    }

    public boolean pathExists(final String path) throws IOException {
        ensureConnectivity();
        return getFile(path) != null;
    }

    /**
     * Main handler for delete operation.
     * 
     * @param rootPathList Must be an abs path of a file/dir.
     * 
     * @return List of maps of FTPFiles and errors. First map is for the files 
     * succeeded the operation and second map is for the files failed along with 
     * the errors occurred.
     */
    public List<Map<String,FTPFile>> delete(final List<String> rootPathList) throws IOException {
        ensureConnectivity();
        
        Path cwdFile = Paths.get(ftpClient.printWorkingDirectory());
        Map<String,FTPFile> mapRootFiles = new LinkedHashMap<>();
        for(String rootPath: rootPathList) {
            String fullRootPath = cwdFile.resolve(rootPath).normalize().toString();            
            if(fullRootPath.equals(ROOT_PATH))
                throw new IOException("Cannot remove root path: " + rootPath);
            FTPFile root = getFile(fullRootPath);
            if(root == null)
                throw new IOException("Non-existent root path: " + rootPath);
            mapRootFiles.put(fullRootPath, root);
        }
        
        succeededFTPFiles.clear();
        failedFTPFiles.clear();        
        for(Map.Entry<String,FTPFile> entry: mapRootFiles.entrySet())
            delete(getParentPath(entry.getKey()), entry.getValue());
        return verboseFTPFileList;
    }

    private void delete(final String cwd, final FTPFile root) throws IOException {
        if(root.isDirectory()) {
            String newCwd = cwd+"/"+root.getName();
            for(FTPFile file : ftpClient.listFiles(newCwd))
                delete(newCwd, file);
        }
        deleteFile(cwd, root);
    }

    /**
     * Deletes specified file/directory.
     */
    private void deleteFile(final String cwd, final FTPFile file) {
        final String path = cwd+"/"+file.getName();
        try {
            if(file.isDirectory()) {
                if(!ftpClient.removeDirectory(path))
                    throw new IOException();
            } else {
                if(!ftpClient.deleteFile(path))
                    throw new IOException();
            }   
            succeededFTPFiles.put(path, file);
        } catch(Exception e) {
            failedFTPFiles.put(path, file);
        }
    }

    public FTPFile[] getPathListing(final String pathRequested,
                                    final boolean showDirsOnly,
                                    final String filterArgument) throws IOException, PatternSyntaxException {
        ensureConnectivity();
        
//        ftpClient.setListHiddenFiles(true);
        FTPFileFilter filter;
        Pattern matchPattern = filterArgument==null ? null : Pattern.compile(filterArgument);        
        filter = file -> {
            boolean accept = true;
            if(matchPattern != null)
                accept = matchPattern.matcher(file.getName()).matches();
            if(showDirsOnly)
                accept &= file.isDirectory();
            return accept;
        };
        
        FTPFile[] files;
        String pathString;
        if(pathRequested.equals(CURRENT_PATH_ABBREVIATION))
            pathString = ftpClient.printWorkingDirectory();
        else if(pathRequested.equals(PARENT_PATH_ABBREVIATION)) 
            pathString = getParentPath(ftpClient.printWorkingDirectory());
        else
            pathString = pathRequested;
        
        if(getFile(pathString) == null)
            throw new IOException("Non-existent path: " + pathRequested);
        files = ftpClient.listFiles(pathString, filter);
        if(files == null)
            throw new IOException("Could not list files under: '"+ pathString +"'");
        return files;
    }

    /**
     * Creates directory, even if the parents don't exists.
     * @path Can be a relative or absolute path.
     */
    public void mkdirs(final String pathString) throws IOException {
        ensureConnectivity();
        
        /* Heuristics:
         *   Convert pathString to absolute path, resolve & normalize 
         *     and then inspect for existence and create.
         */
        
        Path cwd = Paths.get(ftpClient.printWorkingDirectory());
        Path requestedPath = cwd.resolve(pathString).normalize();
        
        String currentPathString = "/";        
        for(int i=0, len=requestedPath.getNameCount(); i<len; i++) {
            currentPathString += "/" + requestedPath.getName(i);
            if(!pathExists(currentPathString))
                if(!ftpClient.makeDirectory(currentPathString))
                    throw new IOException("Cannot create directory: " + currentPathString);
        }
    }

    /* Logs out and disconnects from server */
    public void terminateSession() throws IOException {
        try {
            ftpClient.getStatus(); /* testing if session is alive */
            isSessionAlive = true;
        } catch(IOException e) { /* session not alive, so ignore */
            isSessionAlive = false;
        }        
        ftpClient.logout(); /* ignoring the return value of logout method 
                                as disconnecting is more important after it. 
                                But left free to throw the IOException from inside it 
                                for reporting any catastrophic error. */
        currentWorkingDirectory = ROOT_PATH;
        isSessionAlive = false;
        ftpClient.disconnect();
    }

    private void connect() throws IOException {
        if(isSessionAlive())
            throw new IOException("Previous session still alive");
        isSessionAlive = false;
        
        ftpClient.connect(hostName, PORT);

        int replyCode = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(replyCode))
            throw new IOException("Connection failed. (Reply code:" + replyCode + ")");

        if (!ftpClient.login(userName, userPassword))
            throw new IOException("Log in failed. (Reply code: " + ftpClient.getReplyCode() + ")");
        
        /* use local passive mode to bypass firewall */
        ftpClient.enterLocalPassiveMode();
        currentWorkingDirectory = ROOT_PATH;
        isSessionAlive = true;
        ftpClient.setListHiddenFiles(true);
    }
    
    private void ensureConnectivity() throws IOException {        
        if(isSessionAlive) {
            isSessionAlive = false;
            try {
                ftpClient.getStatus();
            } catch (IOException e) {
                reinitialiseSession();
            }
            isSessionAlive = true;
        } else 
            throw new FTPConnectionClosedException();
    }

    @Override
    public void close() {
        try {
            terminateSession();
        } catch(IOException e) { /* ignore */
        }
    }

    public String getWorkingDirectory() throws IOException {
        ensureConnectivity();
        return ftpClient.printWorkingDirectory();
    }

    public void moveToRootDirectory() throws IOException {
        ensureConnectivity();
        if(!ftpClient.changeWorkingDirectory(ROOT_PATH)) 
            throw new IOException("Cannot move to root directory from " + ftpClient.printWorkingDirectory());
    }

    /** 
     * Due to the limitation of the FTPClient class have to 
     * change the directory one by one and not at once.
     * @param requestedPathString Can be a discontinued path (i.e. not relative to the PWD).
     */
    public void changeWorkingDirectory(final String requestedPathString) throws IOException {
        ensureConnectivity();
        
        if(requestedPathString.equals(ROOT_PATH))
            moveToRootDirectory();
        else {
            Path path = Paths.get(ftpClient.printWorkingDirectory())
                            .resolve(requestedPathString)
                            .normalize(); /* always converts to an absolute path */
            moveToRootDirectory();
            
            for(int i=0, len=path.getNameCount(); i<len; i++) {
                String pathDirComponent = path.getName(i).toString();
                if(PARENT_PATH_ABBREVIATION.equals(pathDirComponent)) {
                    if(!ftpClient.changeToParentDirectory()) 
                        throw new IOException("Cannot move to parent directory from " + ftpClient.printWorkingDirectory());
                } else {
                    if(!ftpClient.changeWorkingDirectory(pathDirComponent))
                        throw new IOException("Cannot move to directory " + pathDirComponent + " from " + ftpClient.printWorkingDirectory());
                }
            }
        }
        currentWorkingDirectory = ftpClient.printWorkingDirectory();
    }

    public void moveToParentDirectory() throws IOException {
        ensureConnectivity();
        if(!ftpClient.changeToParentDirectory()) 
            throw new IOException("Cannot move to parent directory from " + ftpClient.printWorkingDirectory());
    }

    public String[] getServerReplies() throws IOException {
        ensureConnectivity();
        return ftpClient.getReplyStrings();
    }
}