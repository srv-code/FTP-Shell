package fs.ftp.shell;

import fs.ftp.handler.FTPHandler;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPFile;


/*
 * TODO for this project:
 *   - Update the effective options and arguments parsing technique for all commands
*/

/**
 * Functions:
 *      Inspects the command line options
 *      Receives and parses the user input
 *      Interprets the command and their options
 *      Shows the output
 *      Shows the appropriate the prompt string
 *      Loads & stores the preference file
 *      Makes calls to the FTP handler object
 *      Prints the debugging information
 */
public class FTPShell implements AutoCloseable {
    private final class FTPProfile {
        final String profileName;
        final String hostName;
        final String userName;
        final String userPassword;

        FTPProfile(final String profileName, final String hostName, final String userName, final String userPassword) {
            this.profileName = Objects.requireNonNull(profileName);
            this.hostName = Objects.requireNonNull(hostName);
            this.userName = Objects.requireNonNull(userName);
            this.userPassword = Objects.requireNonNull(userPassword);
//            printDebug("FTP profile initialised: " + toString());
        }

        @Override
        public String toString() {
            return String.format("name=%s {host=%s, user=%s}",
                                    profileName, hostName, userName);
        }
    }

    public static  final Desktop nativeDesktopClient = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
    private static final float APP_VERSION = 1.00f;
    private static final String APP_NAME = FTPShell.class.getSimpleName();
    private static boolean showDebugInfo = false; /* Default value set */
    private static boolean exit = false; /* default value set */

    private static File dirTempTransfers = null;
    private final  Map<String, FTPProfile> ftpProfiles = new HashMap<>();
    private final Map<String, String> preferences = new HashMap<>();
    private final FTPHandler ftpHandler;

    /* App data */
    private static final String DIRPATH_ROOT_DATA              = "data";
    private static final String DIRNAME_FETCHED_DATA           = "fetched";
    private static final String DIRNAME_FTP_PROFILES           = "profiles";
    private static final String FILENAME_PREFERENCES           = "prefs";
    private static final String DIRNAMEPREFIX_TEMP_TRANSFERS   = "$TMPTRANS_";
    
    /* Properties keys */
    private final String KEY_DEFAULT_PROFILE_NAME  = "defaultProfileName";
    private final String KEY_PROFILE_HOST_NAME     = "host";
    private final String KEY_PROFILE_USER_NAME     = "user";
    private final String KEY_PROFILE_USER_PASSWORD = "password";
    

    public FTPShell() throws IOException {
        printDebug("Initialising shell...");
        loadAppData();
        this.ftpHandler = new FTPHandler();
    }

    
    public static File getDirTempTransfers() throws IOException {
        if(dirTempTransfers == null) {
            dirTempTransfers = new File(new File(DIRPATH_ROOT_DATA, DIRNAME_FETCHED_DATA), String.format("%s%tQ", DIRNAMEPREFIX_TEMP_TRANSFERS, new Date()));
            if(!dirTempTransfers.mkdir())
                throw new IOException("Cannot create temp transfer directory: " + dirTempTransfers.getAbsolutePath());
            /* It is the responsibility of the using handler to empty 
                the dir contents each time the work is done, 
                else deleteOnExit() will fail without throwing any exception. */
            dirTempTransfers.deleteOnExit();
        }
//        System.out.println("  // dirTempTransfers=" + dirTempTransfers.getAbsolutePath()); // DEBUG
        return dirTempTransfers;
    }

    private void loadAppData() throws IOException {
        printDebug("Loading app data...");
        /* load FTP profiles */
        File dir = new File(DIRPATH_ROOT_DATA, DIRNAME_FTP_PROFILES);
        printDebug("Loading FTP profiles from %s ...", dir.getPath());
        if(dir.exists()) {
            for(File file: dir.listFiles()) {
                try {
                    Properties profile = new Properties();
                    try (FileInputStream fin = new FileInputStream(file)) {
                        profile.load(fin);
                    }
                    ftpProfiles.put(    file.getName(),
                                        new FTPProfile(
                                            file.getName(),
                                            profile.getProperty(KEY_PROFILE_HOST_NAME),
                                            profile.getProperty(KEY_PROFILE_USER_NAME),
                                            profile.getProperty(KEY_PROFILE_USER_PASSWORD)));
                } catch(NullPointerException|IOException e) {
                    System.err.printf("Failed loading FTP profile from file: %s. Reason: %s\n", file.getAbsolutePath(), e);
                    e.printStackTrace(System.err);
                }
            }
        } else {
            if(dir.mkdirs())
                printDebug("Created App Data directory for FTP profiles: %s", dir.getPath());
            else
                throw new IOException("Cannot create App Data directory for FTP profiles: " + dir.getAbsolutePath());
        }

        /* load misc preferences */
        dir = new File(DIRPATH_ROOT_DATA);
        if(dir.exists()) {
            File prefFile = new File(dir, FILENAME_PREFERENCES);
            printDebug("Loading app preferences from file %s ...", prefFile.getPath());
            if(prefFile.exists()) {
                Properties prefs = new Properties();
                prefs.load(new FileInputStream(prefFile));
                preferences.put(KEY_DEFAULT_PROFILE_NAME, prefs.getProperty(KEY_DEFAULT_PROFILE_NAME));
            } else {
                if(prefFile.createNewFile())
                    printDebug("Created preference file: %s", prefFile.getPath());
                else
                    throw new IOException("Cannot create preference file: " + prefFile.getAbsolutePath());
            }
        } else {
            if(dir.mkdirs())
                printDebug("Created App Data directory for preferences: %s", dir.getPath());
            else
                throw new IOException("Cannot create App Data directory for preferences: " + dir.getAbsolutePath());
        }
        
        File fetchedDir = new File(DIRPATH_ROOT_DATA, DIRNAME_FETCHED_DATA);
        if(!fetchedDir.exists()) {
            printDebug("Created directory for fetching remote data: %s", fetchedDir.getPath());
            fetchedDir.mkdir();
        }
    }

    private void storeAppData() throws IOException {
        final String COMMENT = "User: " + System.getProperty("user.name");

        printDebug("Storing app data...");
        /* store FTP profiles */
        File dir = new File(DIRPATH_ROOT_DATA, DIRNAME_FTP_PROFILES);
        printDebug("Storing FTP profiles to %s ...", dir.getPath());
        if(!dir.exists()) {
            if(dir.mkdirs())
                printDebug("Created App Data directory for FTP profiles: %s", dir.getPath());
            else
                throw new IOException("Cannot create App Data directory for FTP profiles: " + dir.getAbsolutePath());
        }
        for(Map.Entry<String, FTPProfile> entry:  ftpProfiles.entrySet()) {
            File file = new File(dir, entry.getKey());
            try {
                Properties propProfile = new Properties();
                propProfile.setProperty(KEY_PROFILE_HOST_NAME, entry.getValue().hostName);
                propProfile.setProperty(KEY_PROFILE_USER_NAME, entry.getValue().userName);
                propProfile.setProperty(KEY_PROFILE_USER_PASSWORD, entry.getValue().userPassword);
                try (FileOutputStream fout = new FileOutputStream(file)) {
                    propProfile.store(fout, COMMENT);
                }
            } catch(IOException e) {
                System.err.printf("Err: Cannot write profile '%s' to file '%s'. Reason: %s\n",
                        entry.getKey(), file.getAbsolutePath(), e);
                e.printStackTrace(System.err);
            }
        }

        /* store misc preferences */
        dir = new File(DIRPATH_ROOT_DATA);
        if(!dir.exists()) {
            if(dir.mkdirs())
                printDebug("Created App Data directory for preferences: %s", dir.getPath());
            else
                throw new IOException("Cannot create App Data directory for preferences: " + dir.getAbsolutePath());
        }
        printDebug("Storing app preferences to %s/%s ...", dir.getPath(), FILENAME_PREFERENCES);
        Properties propPrefs = new Properties();
        String value = preferences.get(KEY_DEFAULT_PROFILE_NAME);
        if(value != null)
            propPrefs.setProperty(KEY_DEFAULT_PROFILE_NAME, value);
        propPrefs.store(new FileOutputStream(new File(dir, FILENAME_PREFERENCES)), COMMENT);
    }

//    static void printResult(final boolean wasSuccessful, final String msg) {
//        printDebug("[%s: %s]", wasSuccessful ? "SUCCESSFUL" : "FAILED", msg);
//    }

    private static void printDebug(final String formatString, final Object... args) {
        if(showDebugInfo)
            System.out.printf("[" + formatString + "]\n", args);
    }

    void printSeparator() {
        System.out.println("----------------------------------");
    }

    private final Scanner scanner = new Scanner(System.in);

    private final String PROMPT_DISCONNECTED   = "local";
    private final String PROMPT_CONNECTED_PREFIX      = "ftp";

    @Override
    public void close() throws Exception {
        try {
            storeAppData();
        } catch(Exception e) {
            System.err.printf("Err: Writing of App Data failed. Reason=%s\n", e);
            e.printStackTrace(System.err);
        }

        try {
            if(ftpHandler != null)
                ftpHandler.close();
        } catch(Exception e) {
            System.err.printf("Err: Cannot close FTP connection. Reason=%s\n", e);
            e.printStackTrace(System.err);
        }
    }

    private enum Command {
        /* format: purpose, usage, option summary */
        ver         ("Shows the application version", "", ""),
        help        ("Shows the help message", "", ""),
        debug       ("Toggles the debug state", "", ""),
        reload      ("Reloads app data", "", ""),
        store       ("Stores app data", "", ""),
        lscmds      ("List all the commands and their purposes", "", ""),
        setp        ("Sets an FTP profile", "<profile-name> <host-name> [<user-name> <password>]", ""),
        dp          ("Prints the default FTP profile", "", ""),
        setdp       ("Sets the default FTP profile", "<profile-name>", ""),
        lsp         ("Lists the FTP profile information", "[<profile-name>]", ""),
        rmp         ("Remove profile from preferences", "[<profile-name1> [<profile-name2>...]]", ""),
        cd          ("Change directory", "[..|<dir-path>]", ""),
        pwd         ("Shows the present working directory", "", ""),
        open        ("Opens the specified file (by default from remote location)", "[option(s)] [<file-path1> [<file-path2> ...]]",
                        "    --check, -c : Check if desktop feature is supported\n" +
                        "    --local, -l : Treat paths as local file"),
        con         ("Connects to a remote server (uses default profile for no arguments)", "[option(s)] [<profile-name>]",
                        "    --new, -n <host-name> [<user-name> <password>] : specify a new profile credentails"),
        srv         ("Shows server information", "[option(s)]", 
                        "    --reply, -r : Shows server replies\n" +
                        "    --stat, -s  : Shows server \n" +
                        "    --help, -h  : Shows server help information"),
        mkdir       ("Creates directories recursively (by default in remote location)", "[option(s)] <dir1-name> [<dir2-name> [<dir2-name> ...]]",
                        "    --local, -l  : Creates in local location"),
        ls          ("Shows the file listing", "[option(s)] [<path>]",
                        "    --long, -l                 : Shows file details\n" +
                        "    --raw , -r                 : Shows in server raw listing format\n" +
                        "    --filter, -f <expression>  : Filters the file listing\n" + 
                        "    --dir, -d                  : Shows only directory listing"),
        tree        ("Shows the directory tree (default is current directory)", "[option(s)] [<root-dir1> [<root-dir2>...]]",
                        "    --dir, -d : Shows only directories"),
        rm          ("Recursively removes the specified directory(s)/file(s)", "[option(s)] <path1> [<path2> ...]",
                        "    --verbose, -v : Shows the deleted file(s)/directory(s)"),
        exists      ("Checks if the specified paths exist", "<path1> [<path2> ...]", ""),
        count       ("Recursively counts entries under a directory (default is the working directory)", "[<root-path1> [<root-path2>]]", ""),
        cp          ("Recursively copies root within remote server", "['option(s)] <src_path1> [<src-path2> ...] <dst-path>",
                        "    --verbose, -v : Shows the files copies"),
        mv          ("Recursively moves root within remote server", "[option(s)] <src_path1> [<src-path2> ...] <dst-path>",
                        "    --verbose, -v : Shows the copies files moved"),
        get         ("Recursively downloads from remote to local location (default is current local directory)", 
                        "[<option> <local-dst-dir>] <remote-src-path1> [<remote-src-path2> ...]",
                        "    --verbose, -v             : Shows the files downloaded\n" +
                        "    --dst, -d <local-dst-dir> : Local directory to place the fetched files (default: ./" + DIRPATH_ROOT_DATA + "/" + DIRNAME_FETCHED_DATA + ")"),
        put         ("Recursively uploads from local to remote location (default is current remote directory)", 
                        "[<option> <remote-dst-dir>] <local-src-path1> [<local-src-path2> ...]",
                        "    --verbose, -v              : Shows the files uploaded" +
                        "    --dst, -d <remote-dst-dir> : Remote directory to place the fetched files (default: . (pwd))"),
        discon      ("Diconnects the current connection", "", ""),
        test        ("Runs the test routine", "", ""),
        exit        ("Quits the program", "", "");

        private final String purposeString, usageString, optionSummaryString;
        Command(final String purposeString, final String usageString, final String optionSummaryString) {
            this.purposeString = purposeString;
            this.usageString = usageString;
            this.optionSummaryString = optionSummaryString;
        }

        String getPurposeString() {
            return purposeString;
        }

        String getUsageString() {
            return usageString;
        }
        
        String getOptionSummaryString() {
            return optionSummaryString;
        }
    }

    private static void inspectForOptions(final String[] args) {
        for(int i=0; i<args.length; i++) {
            switch(args[i]) {
                case "-v":
                case "--version":
                    System.out.printf("%s v%.2f\n", APP_NAME, APP_VERSION);
                    exit = true;
                    break;

                case "-d":
                case "--debug":
                    showDebugInfo = true;
                    break;

                case "-h":
                case "--help":
                    showHelpMessage();
                    exit = true;
                    break;

                default:
                    throw new IllegalArgumentException("Invalid option: " + args[i]);
            }
        }
    }

    private static void showHelpMessage() {
        System.out.println(APP_NAME);
        System.out.println("Purpose: To operate on remote file systems using FTP protocol.");
        System.out.println("Usage:   ftpshell [option1 [option2 ...]]");
        System.out.printf ("Version: %.2f\n", APP_VERSION);
        System.out.println("\nOptions:");
        System.out.println("  --version, -v   Shows app version");
        System.out.println("  --debug, -d     Shows debug information");
        System.out.println("  --help, -h      Shows this help menu and exit");
        System.out.println("For all available shell related commands, enter 'lscmds' from inside the shell.\n");
        StandardExitCodes.showMessage();
    }

    private void printAllCommands() {
        System.out.println("Command list:");
        System.out.printf("  %10s : [Purpose]\n", "[Command]");
        for(Command cmd: Command.values())
            System.out.printf("  %10s : %s\n",
                    cmd, cmd.getPurposeString());
    }

    private String[][] getTokenizedLines(final String cmdLine) {
        List<String> lineTokens = new ArrayList<>();
        List<String[]> lines = new ArrayList<>();

        boolean dqOpened = false;
        int idx=-1, dqIdx=-1, ndqIdx=-1, len=cmdLine.length();

        while((idx = cmdLine.indexOf('"', idx+1)) != -1) {
            if (!dqOpened) { /* Check for opening dq */
                if( (idx==0) || Character.isWhitespace(cmdLine.charAt(idx-1)) ) { /* validate opening dq: idx:0 || left:ws */
                    dqOpened = true;
                    dqIdx = idx;
                }
            } else { /* Check for closing dq */
                if((idx+1)==len || Character.isWhitespace(cmdLine.charAt(idx+1)) || cmdLine.charAt(idx+1)==';' ) { /* validate closing dq: idx+1:len || right:ws || right:';' */
                    tokenizeLine(lineTokens, lines, cmdLine.substring(ndqIdx+1, dqIdx), cmdLine.substring(dqIdx+1, idx));
                    ndqIdx = idx;
                    dqOpened = false;
                }
            }
        }

        if(ndqIdx != len)
            tokenizeLine(lineTokens, lines, cmdLine.substring(ndqIdx+1, len) );

        if(!lineTokens.isEmpty())
            lines.add( lineTokens.toArray(new String[lineTokens.size()]) ); // Replaced, old version: (new String[]{}) );

        return lines.toArray(new String[][]{});
    }

    private void tokenizeLine(  final List<String> lineTokens,
                                    final List<String[]> lines,
                                    String cmdLineSegment,
                                    final String... extra) {
        cmdLineSegment = cmdLineSegment.trim();
        int idx = -1, lastIdx = 0;
        String lineSeg;

        while((idx=cmdLineSegment.indexOf(';', idx+1)) != -1 ) {
            lineSeg = cmdLineSegment.substring(lastIdx, idx).trim();
            if(lineSeg.length()>0)
                lineTokens.addAll( Arrays.asList( lineSeg.split(" +") ) );
            lines.add( lineTokens.toArray(new String[]{}) );
            lineTokens.clear();
            lastIdx = idx+1;
        }

        if(idx != (cmdLineSegment.length()-1) && (lineSeg = cmdLineSegment.substring(lastIdx, cmdLineSegment.length()).trim()).length()>0)
            lineTokens.addAll(Arrays.asList(lineSeg.split(" +")));

        for(String dqArg : extra)
            lineTokens.add(dqArg);
    }

    private void enterInputLoop() throws IOException {
        boolean continueInput = true;
        String cmdString;
        Command cmd = null;
        Throwable error = null;

        while(continueInput) {
            try {
                error = null;
                System.out.printf("%s>  ", getPrompt()); /* shows the prompt */
                for(String lineTokens[]: getTokenizedLines(scanner.nextLine())) {
                    boolean helpRequested = lineTokens[0].charAt(0)=='?';
                    cmdString = helpRequested ? lineTokens[0].substring(1) : lineTokens[0];
                    try {
                        cmd = Command.valueOf(cmdString);
                        if(helpRequested) {
                            System.out.println("Purpose: " + cmd.getPurposeString());
                            if(cmd.getUsageString().length() > 0)
                                System.out.println("Usage  : " + cmd.getUsageString());
                            if(cmd.getOptionSummaryString().length() > 0)
                                System.out.println("Option summary:\n" + cmd.getOptionSummaryString());
                            continue;
                        }
                    } catch(IllegalArgumentException e) {
                        System.err.println("Err: Command not found: " + cmdString);
                        continue;
                    }
                
                    switch (cmd) {
                        case ver: {
                                System.out.printf("%.2f\n", APP_VERSION);
                            }
                            break;

                        case help: {
                                showHelpMessage();
                            }
                            break;

                        case debug: {
                                showDebugInfo = !showDebugInfo;
                                System.out.println("Debug mode switched " + (showDebugInfo ? "on" : "off"));
                            }
                            break;

                        case lscmds: {
                                printAllCommands();
                            }
                            break;

                        case lsp: {
                                if(lineTokens.length>1)
                                    showProfile(lineTokens[1]);
                                else
                                    showProfiles();
                            }
                            break;

                        case rmp: {
                                if(lineTokens.length == 1)
                                    throw new ArrayIndexOutOfBoundsException();
                                for(int i=1, len=lineTokens.length; i<len; i++) {
                                    final String profileName = lineTokens[i];
                                    FTPProfile existingProfile = ftpProfiles.get(profileName);
                                    if(existingProfile == null) {
                                        System.out.println("Err: No such profile preset: " + profileName);
                                    } else {
                                        /* remove from app data dir */
                                        File profileFile = new File(new File(DIRPATH_ROOT_DATA, DIRNAME_FTP_PROFILES), profileName);
                                        if(!profileFile.delete())
                                            throw new IOException("Err: Cannot delete profile file: " + profileFile.getAbsolutePath());
                                        /* remove from map */
                                        ftpProfiles.remove(profileName);

                                        /* remove if default profile */
                                        String defaultProfileName = preferences.get(KEY_DEFAULT_PROFILE_NAME);
                                        if(defaultProfileName != null && defaultProfileName.equals(profileName))
                                            preferences.remove(KEY_DEFAULT_PROFILE_NAME);
                                        System.out.printf ("Profile '%s' removed.\n", profileName);
                                        System.out.printf ("    Host name: '%s'\n", existingProfile.hostName);
                                        System.out.printf ("    User name: '%s'\n", existingProfile.userName);
                                    }
                                }
                            }
                            break;

                        case cd: {
                                if(lineTokens.length > 1) {
                                    if(FTPHandler.CURRENT_PATH_ABBREVIATION.equals(lineTokens[1]))
                                        break; /* skip */
                                    if(FTPHandler.PARENT_PATH_ABBREVIATION.equals(lineTokens[1]))
                                        ftpHandler.moveToParentDirectory();
                                    else
                                        ftpHandler.changeWorkingDirectory(lineTokens[1]);
                                } else { /* move to server root directory */
                                    ftpHandler.moveToRootDirectory();
                                }
                            }
                            break;

                        case pwd: {
                                System.out.println(ftpHandler.getWorkingDirectory());
                            }
                            break;

                        case dp: {
                                String defaultProfileName = preferences.get(KEY_DEFAULT_PROFILE_NAME);
                                if(defaultProfileName == null) {
                                    System.out.println("Default profile not set!");
                                    continue;
                                }

                                FTPProfile defaultProfile = ftpProfiles.get(defaultProfileName);
                                if(defaultProfile == null)
                                    System.out.println("Default profile not found!");
                                else {
                                    System.out.println("Default profile:");
                                    System.out.printf ("    Profile name: '%s'\n", defaultProfile.profileName);
                                    System.out.printf ("    Host name:    '%s'\n", defaultProfile.hostName);
                                    System.out.printf ("    User name:    '%s'\n", defaultProfile.userName);
                                }
                            }
                            break;

                        case setp: {
                                String profileName = lineTokens[1];
                                String hostName = lineTokens[2];
                                String userName, userPassword;

                                if(lineTokens.length > 3) {
                                    userName = lineTokens[3];
                                    userPassword = lineTokens[4];
                                } else {
                                    userName = FTPHandler.ANONYMOUS_USER_NAME;
                                    userPassword = FTPHandler.ANONYMOUS_USER_PASSWORD;
                                }

                                FTPProfile existingProfile = ftpProfiles.get(profileName);
                                if(existingProfile != null) {
                                    System.out.println("Profile already preset!");
                                    System.out.println("    Host name: " + existingProfile.hostName);
                                    System.out.println("    User name: " + existingProfile.userName);
                                    System.out.println("Remove the profile to add a new.");
                                    continue;
                                }

                                ftpProfiles.put(
                                        profileName,
                                        new FTPProfile(profileName, hostName, userName, userPassword));
                                System.out.println("Profile set: " + profileName);
                            }
                            break;

                        case setdp: {
                                final String profileName = lineTokens[1];
                                if(ftpProfiles.get(profileName) == null) {
                                    System.out.println("Err: No such profile found: " + profileName);
                                    continue;
                                }
                                if(profileName.equals(preferences.get(KEY_DEFAULT_PROFILE_NAME))) {
                                    System.out.println("Default profile already set to " + profileName + "!");
                                    continue;
                                }

                                final String oldDefaultProfileName = preferences.put(KEY_DEFAULT_PROFILE_NAME, lineTokens[1]);
                                if(oldDefaultProfileName == null) {
                                    System.out.println("New default profile set.");
                                } else {
                                    System.out.println("Default profile replaced.");
                                    System.out.println("    Old profile: " + oldDefaultProfileName);
                                }
                                System.out.println("    New profile: " + lineTokens[1]);
                            }
                            break;

                        case open: {
                                boolean checkIfSupported = false; /* default value set */
                                boolean openLocal        = false; /* default value set */
                                List<String> pathList    = new ArrayList<>(); 
                                    
                                for(int i=1, len=lineTokens.length; i<len; i++) {
                                    switch(lineTokens[i]) {
                                        case "--check":
                                        case "-c":
                                            checkIfSupported = true;
                                            break;
                                            
                                        case "--local":
                                        case "-l":
                                            openLocal = true;
                                            break;
                                            
                                        default:
                                            pathList.add(lineTokens[i]);
                                    }
                                }

                                if(checkIfSupported)
                                    System.out.println("Feature supported?: " + (nativeDesktopClient == null));
                                else {
                                    if(pathList.isEmpty())
                                        throw new IOException("No path provided to open");
                                    if(nativeDesktopClient == null)
                                        throw new IOException(String.format("Feature not supported on current platform (name=%s, architecture=%s, version=%s)",
                                                Objects.toString(System.getProperty("os.name"), "N/A"),
                                                Objects.toString(System.getProperty("os.arch"), "N/A"),
                                                Objects.toString(System.getProperty("os.version"), "N/A")));
                                    for(String path: pathList) {
                                        try {
                                            File fileToOpen = null;
                                            if(openLocal) {
                                                fileToOpen = new File(path);
                                                if(!fileToOpen.exists())
                                                    throw new IOException("Non-existent path: " + path);
                                            } else {
                                                String dstDirPath = String.format("%s/%s/fetch_%tQ", DIRPATH_ROOT_DATA, DIRNAME_FETCHED_DATA, new Date());
                                                if(!new File(dstDirPath).mkdir())
                                                    throw new IOException("Cannot create fetch directory: " + dstDirPath);
                                                List<String> srcList = new ArrayList<>(1);
                                                srcList.add(path);
                                                ftpHandler.get(srcList, dstDirPath);
                                                File dstFetchDir = new File(dstDirPath);
                                                fileToOpen = dstFetchDir.listFiles()[0];
                                                markFilesForDeletion(dstFetchDir);
                                            }
                                            nativeDesktopClient.open(fileToOpen);
                                        } catch(Exception e) {
                                            System.out.printf("Err: Cannot open path: %s. Reason: %s\n", path, e);
                                            if(showDebugInfo)
                                                e.printStackTrace(System.out);
                                        }
                                    }
                                }
                            }
                            break;

                        case con: {
                                if(ftpHandler.isSessionAlive()) {
                                    printDebug("Closing previous FTP session...");
                                    ftpHandler.terminateSession();
                                }

                                if(lineTokens.length>1) {
                                    if(lineTokens[1].equals("--new") || lineTokens[1].equals("-n")) { /* user provided credentials by himself */
                                        if(lineTokens.length > 3) /* logs in as registered user */
                                            ftpHandler.connect(lineTokens[2], lineTokens[3], lineTokens[4]);
                                        else /* logs in anonymously */
                                            ftpHandler.connect(lineTokens[2]);
                                    } else { /* login to a saved profile */
                                        FTPProfile profile = ftpProfiles.get(lineTokens[1]);
                                        if(profile == null)
                                            System.out.println("Err: Profile not found: " + lineTokens[1]);
                                        else
                                            ftpHandler.connect(profile.hostName, profile.userName, profile.userPassword);
                                    }
                                } else { /* use default profile */
                                    String defaultProfileName = preferences.get(KEY_DEFAULT_PROFILE_NAME);
                                    if(defaultProfileName == null) {
                                        System.out.println("Default profile not set!");
                                        continue;
                                    }
                                    System.out.printf("Connecting to default profile '%s' ...\n", defaultProfileName);
                                    FTPProfile profile = ftpProfiles.get(defaultProfileName);
                                    ftpHandler.connect(profile.hostName, profile.userName, profile.userPassword);
                                }
                            }
                            break;

                        case srv: {                                
                                switch(lineTokens[1]) {
                                    case "--reply":
                                    case "-r":
                                        for(String line: ftpHandler.getServerReplies())
                                            System.out.println(line);
                                        break;

                                    case "--stats":
                                    case "-s":
                                        for(String line: ftpHandler.getConnectionStatistics())
                                            System.out.println(line);
                                        break;

                                    case "--help":
                                    case "-h":
                                        System.out.println(ftpHandler.getServerHelpString());
                                        break;

                                    default:
                                        System.out.println("Err: Invalid option: " + lineTokens[1]);
                                }
                            }
                            break;

                        case mkdir: { // TODO test
                                boolean verboseEnabled = false; /* default value set */
                                boolean localOperation = false; /* default value set */
                                List<String> paths = new ArrayList<>();

                                for(int i=1, len=lineTokens.length; i<len; i++) { 
                                    switch(lineTokens[i]) {
                                        case "--verbose":
                                        case "-v":
                                            verboseEnabled = true;
                                            break;

                                        case "--local":
                                        case "-l":
                                            localOperation = true;
                                            break;

                                        default: 
                                            paths.add(lineTokens[i]);
                                    }
                                }

                                if(paths.isEmpty())
                                    throw new ArrayIndexOutOfBoundsException();

                                for(String path: paths) {
                                    try {
                                        if(localOperation)
                                            new File(path).mkdirs();
                                        else
                                            ftpHandler.mkdirs(path);
                                        if(verboseEnabled)
                                            System.out.printf("  created: '%s'\n", path);
                                    } catch(IOException e) {
                                        System.out.println("Err: " + e);
                                        if(showDebugInfo)
                                            e.printStackTrace(System.out);
                                    }
                                }
                            }
                            break;

                        case ls: {
                                ListingFormat format = ListingFormat.NAME_ONLY; /* default value set */
                                String filterExpression = null; /* default value set */
                                boolean showDirsOnly = false; /* default value set */
                                List<String> paths = new ArrayList<>();

                                /* parse options and args */
                                for(int i=1, len=lineTokens.length; i<len; i++) {
                                    switch(lineTokens[i]) {
                                        case "--long":
                                        case "-l":
                                            format = ListingFormat.DETAILS;
                                            break;

                                        case "--raw":
                                        case "-r":
                                            format = ListingFormat.RAW;
                                            break;

                                        case "--filter":
                                        case "-f":
                                            filterExpression = lineTokens[++i]; /* accepts regex */
                                            break;

                                        case "--dir":
                                        case "-d":
                                            showDirsOnly = true;
                                            break;

                                        default:
                                            paths.add(lineTokens[i]);
                                    }
                                }

                                if(paths.isEmpty())
                                    paths.add(FTPHandler.CURRENT_PATH_ABBREVIATION);

                                for(String path: paths) {
                                    try {
                                        showFTPListing(path, ftpHandler.getPathListing(path, showDirsOnly, filterExpression), format);
                                    } catch(FTPConnectionClosedException e) {
                                        throw e;
                                    } catch(IOException e) {
                                        System.out.println("Err: " + e);
                                        if(showDebugInfo)
                                            e.printStackTrace(System.out);
                                    }
                                }
                            }
                            break;

                        case tree: {
                                List<String> paths = new ArrayList<>();
                                for(int i=1, len=lineTokens.length; i<len; i++)
                                    paths.add(lineTokens[i]);
                                if(paths.isEmpty())
                                    paths.add(FTPHandler.CURRENT_PATH_ABBREVIATION);

                                for(String path: paths) {
                                    try {
                                        System.out.println(path + ":");
                                        for(String line: ftpHandler.tree(path))
                                            System.out.println(line);
                                    } catch(FTPConnectionClosedException e) {
                                        throw e;
                                    } catch(IOException e) {
                                        System.out.println("Err: " + e);
                                        if(showDebugInfo)
                                            e.printStackTrace(System.out);
                                    }
                                }
                            }
                            break;

                        case reload: {
                                loadAppData();
                            }
                            break;

                        case store: {
                                storeAppData();
                            }
                            break;

                        case rm: {
                                long totalSucceeded = 0L, totalFailed = 0L;
                                boolean verboseEnabled = lineTokens[1].equals("--verbose") || lineTokens[1].equals("-v");
                                if(verboseEnabled && lineTokens.length == 2)
                                    throw new ArrayIndexOutOfBoundsException();
                                
                                List<String> paths = new ArrayList<>();
                                for(int i=verboseEnabled?2:1, len=lineTokens.length; i<len; i++)
                                    paths.add(lineTokens[i]);
                                
                                List<Map<String,FTPFile>> list = ftpHandler.delete(paths);
                                Map<String,FTPFile> mapSucceeded = list.get(0);
                                Map<String,FTPFile> mapFailed = list.get(1);

                                totalSucceeded = mapSucceeded.size();
                                totalFailed = mapFailed.size();

                                if(verboseEnabled) {
                                    /* show succeeded files */
                                    if(!mapSucceeded.isEmpty()) {
                                        for(Map.Entry<String,FTPFile> entry: mapSucceeded.entrySet())
                                            System.out.printf("    %5s:  %s\n",
                                                    entry.getValue().isDirectory() ? "rmdir" : "rm", 
                                                    entry.getKey());
                                        System.out.println("  Total: " + totalSucceeded);
                                    }

                                }
                                /* show failed files */                       
                                if(!mapFailed.isEmpty()) {
                                    System.out.println("  Failed:");
                                    for(Map.Entry<String,FTPFile> entry: mapFailed.entrySet())
                                        System.out.printf("    %5s:  %s\n", 
                                                entry.getValue().isDirectory() ? "rmdir" : "rm",
                                                entry.getKey());
                                    System.out.println("  Total: " + totalFailed);
                                }
                            }
                            break;

                        case exists: {
                                for(int i=1, len=lineTokens.length; i<len; i++)
                                    System.out.printf("  '%s': %s\n",
                                            lineTokens[i], ftpHandler.pathExists(lineTokens[i]) ? "exists" : "not exists");
                            }
                            break;

                        case count: {
                                List<String> paths = new ArrayList<>();
                                for(int i=1, len=lineTokens.length; i<len; i++)
                                    paths.add(lineTokens[i]);
                                if(paths.isEmpty())
                                    paths.add(FTPHandler.CURRENT_PATH_ABBREVIATION);

                                for(String path: paths) {
                                    try {
                                        long[] count = ftpHandler.count(path);
                                        System.out.printf("  '%s': dirs=%d, files=%d, total=%d\n",
                                                path, count[0], count[1], count[0]+count[1]);
                                    } catch(FTPConnectionClosedException e) {
                                        throw e;
                                    } catch(IOException e) {
                                        System.out.println("Err: " + e);
                                        if(showDebugInfo)
                                            e.printStackTrace(System.out);
                                    }
                                }
                            }
                            break;

                        case cp:
                        case mv: {
                                boolean isCopying = cmd == Command.cp;
                                boolean verboseEnabled = lineTokens[1].equals("--verbose") ||  lineTokens[1].equals("-v");
                                if(verboseEnabled && lineTokens.length < 4)
                                    throw new ArrayIndexOutOfBoundsException();
                                if(lineTokens.length < 3)
                                    throw new ArrayIndexOutOfBoundsException();

                                final String dstPath = lineTokens[lineTokens.length-1];
                                List<String> srcList = new ArrayList<>();
                                for(int i=verboseEnabled?2:1, len=lineTokens.length-1; i<len; i++)
                                    srcList.add(lineTokens[i]);
                                
                                List<Map<String,FTPFile>> list = isCopying ? ftpHandler.copy(srcList, dstPath) : ftpHandler.move(srcList, dstPath);
                                
                                if(verboseEnabled) {
                                    for(Map.Entry<String,FTPFile> entry: list.get(0).entrySet())
                                        System.out.printf("  %s%s\n", 
                                                entry.getKey(), 
                                                entry.getValue().isDirectory() ? "/" : "");
                                    System.out.println("Total: " + list.get(0).size());
                                }
                                
                                if(!list.get(1).isEmpty()) {
                                    System.out.println("Failed:");
                                    for(Map.Entry<String,FTPFile> entry: list.get(1).entrySet())
                                        System.out.printf("  %s%s\n", 
                                                entry.getKey(), 
                                                entry.getValue().isDirectory() ? "/" : "");
                                    System.out.println("Total: " + list.get(1).size());
                                }
                            }
                            break;

                        case get:
                        case put: {
                                boolean isFetching = cmd == Command.get;
                                boolean verboseEnabled = false; /* default value set */ 
                                String dstDirPath = isFetching ? String.format("%s/%s/fetch_%tQ", DIRPATH_ROOT_DATA, DIRNAME_FETCHED_DATA, new Date()) : "."; /* default value set */
                                List<String> srcPathList = new ArrayList<>();
                                for(int i=1, len=lineTokens.length; i<len; i++) {
                                    switch(lineTokens[i]) {
                                        case "--verbose":
                                        case "-v":
                                            verboseEnabled = true;
                                            break;
                                            
                                        case "--dst":
                                        case "-d":
                                            dstDirPath = lineTokens[++i];
                                            break;
                                            
                                        default:
                                            srcPathList.add(lineTokens[i]);
                                            break;
                                    }
                                }
                                if(srcPathList.isEmpty()) 
                                    throw new IOException("No root path provided");
                                
                                if(isFetching) {
                                    if(!new File(dstDirPath).mkdir())
                                        throw new IOException("Cannot create fetch directory: " + dstDirPath);
                                    List<Map<String,FTPFile>> list = ftpHandler.get(srcPathList, dstDirPath);
                                    if(verboseEnabled) {
                                        for(Map.Entry<String,FTPFile> entry: list.get(0).entrySet())
                                            System.out.printf("  %s%s\n", 
                                                    entry.getKey(), 
                                                    entry.getValue().isDirectory() ? "/" : "");
                                        System.out.println("Total: " + list.get(0).size());
                                    }
                                    if(!list.get(0).isEmpty())
                                        System.out.println("Files placed in: " + dstDirPath);

                                    if(!list.get(1).isEmpty()) {
                                        System.out.println("Failed:");
                                        for(Map.Entry<String,FTPFile> entry: list.get(1).entrySet())
                                            System.out.printf("  %s%s\n", 
                                                    entry.getKey(), 
                                                    entry.getValue().isDirectory() ? "/" : "");
                                        System.out.println("Total: " + list.get(1).size());
                                    }
                                } else {
                                    List<List<File>> list = ftpHandler.put(srcPathList, dstDirPath);
                                    if(verboseEnabled) {
                                        for(File file: list.get(0))
                                            System.out.printf("  %s%s\n", 
                                                    file.getName(),
                                                    file.isDirectory() ? "/" : "");
                                        System.out.println("Total: " + list.get(0).size());
                                    }

                                    if(!list.get(1).isEmpty()) {
                                        System.out.println("Failed:");
                                        for(File file: list.get(1))
                                            System.out.printf("  %s%s\n", 
                                                    file.getName(), 
                                                    file.isDirectory() ? "/" : "");
                                        System.out.println("Total: " + list.get(1).size());
                                    }
                                }
                            }
                            break;

                        case discon: {
                                ftpHandler.terminateSession();
                            }
                            break;

                        case test: {
                                ftpHandler.test();
                            }
                            break;

                        case exit: {
                                continueInput = false;
                            }
                            break;

                        default:
                            throw new AssertionError("Should not get here: Default case in switch case");
                    }                
                }
            } catch(ArrayIndexOutOfBoundsException e) {
                error = e;
                System.out.println("Err: Not enough arguments!");
                System.out.printf("Usage: %s %s\n", cmd, cmd.getUsageString());
            } catch(FTPConnectionClosedException e) {
                error = e;
                System.err.println("Err: FTP session not alive!");
            } catch(IOException e) {
                error = e;
                System.err.println("Err: " + e);
            } catch(Exception e) {
                error = e;
                System.out.println("Err: " + e);
            } finally {
                if(showDebugInfo && error != null)
                    error.printStackTrace(System.err);
            }
        }
        scanner.close();
//        printSeparator();
        try { /* Terminate FTP session automatically if not already terminated */
            if(ftpHandler.isSessionAlive()) {
                printDebug("Closing FTP session...");
                ftpHandler.terminateSession();
            }
        } catch(IOException e) { /* ignore */
//            System.err.println("Err: Cannot terminate FTP Session! Reason: " + e);
//            if(showDebugInfo)
//                e.printStackTrace(System.err);
        }
    }
    
    private void markFilesForDeletion(final File rootFile) {
        rootFile.deleteOnExit();
        if(rootFile.isDirectory())
            for(File file: rootFile.listFiles())
                markFilesForDeletion(file);
    }

    private final double KB = Math.pow(1024.0, 1.0);
    private final double MB = Math.pow(1024.0, 2.0);
    private final double GB = Math.pow(1024.0, 3.0);

    private String getFileSizeInString(final long size) {
        final double s  = Long.valueOf(size).doubleValue();
        if(s < KB)          return String.format("%8d B", size);
        else if(s < MB)     return String.format("%4.2f K", s/KB);
        else if(s < GB)     return String.format("%4.2f M", s/MB);
        else                return String.format("%4.2f G", s/GB);
    }

    private enum ListingFormat {
        DETAILS, RAW, NAME_ONLY;
    }

    /** Shows in a directory first and lexicographical order */
    private void showFTPListing(    final  String path,
                                    final FTPFile[] files,
                                    final ListingFormat format) throws Exception {
        System.out.printf("%s:\n", path);        
        /* do inline sorting - directory first and also lexicographically */
        Arrays.sort(files, (file1, file2) -> {
            if(file1.isDirectory() == file2.isDirectory())
				return file1.getName().compareTo(file2.getName());
			return file1.isDirectory() ? -1 : 1;
        }); 
        
        /* sort lexicographically */
//        for(int i=0, insertIdx=-1, len=getPathListing.length; i<len; i++) /* Directory first inline sorting */
//            if(getPathListing[i].isDirectory())
//                getPathListing.add(++insertIdx, getPathListing.remove(i));
        
//        getPathListing.sort( (file1, file2) -> file1.getName().compareTo(file2.getName()) ); /* sort lexicographically */
//        for(int i=0, insertIdx=-1, len=getPathListing.size(); i<len; i++) /* Directory first inline sorting */
//            if(getPathListing.get(i).isDirectory())
//                getPathListing.add(++insertIdx, getPathListing.remove(i));

        /* now display in specified format */
        for(FTPFile file: files) {
            switch(format) {
                case DETAILS: {
    //                    System.out.println("FTPFile: ");
            //			System.out.println("  getName=" + file.getName());
            //			System.out.println("  getRawListing=" + file.getRawListing());
            //			System.out.println("  getSize=" + file.getSize());
            //			System.out.println("  getTimestamp=" + file.getTimestamp());

                        String type;
                        switch (file.getType()) {
                            case FTPFile.FILE_TYPE:
                                type = "file";
                                break;
                            case FTPFile.DIRECTORY_TYPE:
                                type = "dir";
                                break;
                            case FTPFile.SYMBOLIC_LINK_TYPE:
                                type = "sym";
                                break;
                            case FTPFile.UNKNOWN_TYPE:
                                type = "???";
                                break;
                            default:
                                type = "!!!";
                                break;
                        }

            //            final String formatString = "%4s %10s %8d %10s %s\n";
            //            System.out.printf(formatString,
            //                    "Type", "Owner", "Size", "Mod time", "Path");
                        System.out.printf("    %4s  %10s  %10s  %tH:%<tM:%<tS %<td-%<tm-%<tY  %s\n",
                                type, file.getUser(),
                                getFileSizeInString(file.getSize()),
                                file.getTimestamp(),
                                file.getName());

            //			System.out.println("  type=" + type);
            //			System.out.println("  getUser=" + file.getUser());
            //			System.out.println("  isDirectory=" + file.isDirectory());
            //			System.out.println("  isFile=" + file.isFile());
            //			System.out.println("  isSymbolicLink=" + file.isSymbolicLink());
            //			System.out.println("  isUnknown=" + file.isUnknown());
            //			System.out.println("  isValid=" + file.isValid());
            //			System.out.println("  toString=" + file.toString());
            //			System.out.println("  toFormattedString=" + file.toFormattedString());
                    }
                    break;

                case NAME_ONLY: {
                        System.out.println("  " + file.getName() + (file.isDirectory() ? "/" : ""));
                    }
                    break;

                case RAW: {
                        System.out.println("  " + file.getRawListing());
                    }
                    break;

                default:
                    throw new Exception("ListingFormat not implemented: " + format);
            }
        }
        System.out.println("Total: " + files.length);
    }

    private String getPrompt() throws IOException {
        if(!ftpHandler.isSessionAlive())
            return PROMPT_DISCONNECTED;
        return String.format("%s[%s@%s:%s]",
                PROMPT_CONNECTED_PREFIX,
                ftpHandler.getHostName(),
                ftpHandler.getUserName(),
                ftpHandler.getWorkingDirectory());
    }

    private void showProfile(final String profileName) {
        FTPProfile profile = ftpProfiles.get(profileName);
        if(profile == null) {
            System.out.println("No such profile found: " + profileName);
        } else {
            final String formatString = "%-20s  %20s %s\n";
            System.out.printf(formatString,
                    "[Profile Name]", "[User Name]", "[Host Name]");
            System.out.printf(formatString,
                        profileName, profile.userName, profile.hostName);
        }
    }

    private void showProfiles() {
        if(ftpProfiles.isEmpty()) {
            System.out.println("No profiles found!");
            return;
        }

        System.out.println("FTP Profile listing:");
        final String formatString = "%4s  %-20s  %20s %s\n";
        System.out.printf(formatString,
                "[Sl]", "[Key]", "[User Name]", "[Host Name]");
        int i=0;
        for(Map.Entry<String, FTPProfile> entry: ftpProfiles.entrySet()) {
            System.out.printf(formatString,
                    ++i, entry.getKey(), entry.getValue().userName, entry.getValue().hostName);
        }
    }

    public static void main(String[] args) {
        /* Check command line arguments for options */
        try {
            inspectForOptions(args);
        } catch(IllegalArgumentException e) {
            System.err.println("Err: " + e.getMessage());
            System.exit(StandardExitCodes.ERROR);
        }
        if(exit)
            System.exit(StandardExitCodes.NORMAL);

        /* start the shell input loop */
        int exitValue = StandardExitCodes.NORMAL; /* Default is normal */
        FTPShell shell = null;
        try {
            shell = new FTPShell();
            shell.enterInputLoop();
        } catch(Exception e) { /* final exception handler */
            System.err.println("Fatal err: " + e);
            e.printStackTrace(System.err);
            exitValue = StandardExitCodes.ERROR;
        } finally {
            try {
                if(shell != null)
                    shell.close();
            } catch(Exception e) {
                System.err.println("Err: While closing shell: " + e);
                e.printStackTrace(System.err);
            }
            printDebug("Exiting shell...");
            System.exit(exitValue);
        }
    }
}
