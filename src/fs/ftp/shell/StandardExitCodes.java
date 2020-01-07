package fs.ftp.shell;

public final class StandardExitCodes {
    /* Exit Values Constants */
    public static final int NORMAL = 0;
    public static final int ERROR  = 1;
    public static final int FILE   = 2;
    public static final int FATAL  = 10;
    
    public final static void showMessage() {
        System.out.println("Exit values: ");
        System.out.printf("  %2d    %s\n", NORMAL, "Normal exit");
        System.out.printf("  %2d    %s\n", ERROR,  "General user errors");
        System.out.printf("  %2d    %s\n", FILE,   "File/Directory related errors");
        System.out.printf("  %2d    %s\n", FATAL,  "Application fatal/unknown error");
    }
}
