//----------------------------------------------------------------------------//
//                                                                            //
//                            W e l l K n o w n s                             //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Hervé Bitteur 2000-2011. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr;

import omr.log.Logger;

import java.io.File;
import java.net.URISyntaxException;

/**
 * Class {@code WellKnowns} gathers top public static final data to be
 * shared within Audiveris application.
 *
 * <p>Note that a few initial operations are performed here, because they need
 * to be done before any other class is loaded.
 *
 * @author Hervé Bitteur
 */
public class WellKnowns
{
    //~ Static fields/initializers ---------------------------------------------

    //----------//
    // IDENTITY //
    //----------//

    /** Application version: {@value} */
    public static final String TOOL_VERSION = "4.1beta";

    /** Application name: {@value} */
    public static final String TOOL_NAME = "audiveris";

    /** Application full name: {@value} */
    public static final String TOOL_FULL_NAME = TOOL_NAME + "-" + TOOL_VERSION;

    /** Application company name: {@value} */
    public static final String TOOL_COMPANY = "AudiverisLtd";

    /** Specific prefix for application folders */
    private static final String TOOL_PREFIX = "/" + TOOL_COMPANY + "/" +
                                              TOOL_FULL_NAME;

    //----------//
    // PLATFORM //
    //----------//

    /** Are we using a Linux OS? */
    public static final boolean LINUX = System.getProperty("os.name")
                                              .toLowerCase()
                                              .startsWith("linux");

    /** Are we using a Mac OS? */
    public static final boolean MAC_OS_X = System.getProperty("os.name")
                                                 .toLowerCase()
                                                 .startsWith("mac os x");

    /** Are we using a Windows OS? */
    public static final boolean WINDOWS = System.getProperty("os.name")
                                                .toLowerCase()
                                                .startsWith("windows");

    /** Precise OS architecture */
    public static final String OS_ARCH = System.getProperty("os.arch");

    /** File separator for the current platform */
    public static final String FILE_SEPARATOR = System.getProperty(
        "file.separator");

    /** Line separator for the current platform */
    public static final String LINE_SEPARATOR = System.getProperty(
        "line.separator");

    /** Redirection, if any, of standard out and err stream */
    public static String STD_OUT_ERROR = System.getProperty("stdouterr");

    //---------//
    // PROGRAM //
    //---------//

    /** The container from which the application classes were loaded */
    public static final File CLASS_CONTAINER = getClassContainer();

    /** Program installation folder for this application */
    private static final File PROGRAM_FOLDER = getProgramFolder();

    /** The folder where resource data is stored */
    public static final File RES_FOLDER = new File(PROGRAM_FOLDER, "res");

    /** The folder where Tesseract OCR material is stored */
    public static final File OCR_FOLDER = new File(PROGRAM_FOLDER, "ocr");

    /** The folder where documentations files are stored */
    public static final File DOC_FOLDER = new File(PROGRAM_FOLDER, "www");

    /** Trick to detect a development environment rather than a standard one */
    private static final boolean isProject = isProject();

    //--------//
    // CONFIG //
    //--------//

    /** Base folder for config */
    private static final File CONFIG_FOLDER = getConfigFolder();

    /** The folder where configuration data is stored */
    public static final File SETTINGS_FOLDER = new File(
        CONFIG_FOLDER,
        "settings");

    /** The folder where plugin scripts are found */
    public static final File PLUGINS_FOLDER = new File(
        CONFIG_FOLDER,
        "plugins");

    static {
        /** Adjust logging if needed */
        setLogging();
    }

    //------//
    // DATA //
    //------//

    /** Base folder for data */
    private static final File DATA_FOLDER = getDataFolder();

    /** The folder where examples are stored */
    public static final File EXAMPLES_FOLDER = new File(
        DATA_FOLDER,
        "examples");

    /** The folder where evaluation data is stored */
    public static final File EVAL_FOLDER = new File(DATA_FOLDER, "eval");

    /** The folder where training material is stored */
    public static final File TRAIN_FOLDER = new File(DATA_FOLDER, "train");

    /** The folder where symbols information is stored */
    public static final File SYMBOLS_FOLDER = new File(TRAIN_FOLDER, "symbols");

    /** The default folder where benches data is stored */
    public static final File DEFAULT_BENCHES_FOLDER = new File(
        DATA_FOLDER,
        "benches");

    /** The default folder where MIDI data is stored */
    public static final File DEFAULT_MIDI_FOLDER = new File(
        DATA_FOLDER,
        "midi");

    /** The default folder where PDF data is stored */
    public static final File DEFAULT_PRINT_FOLDER = new File(
        DATA_FOLDER,
        "print");

    /** The default folder where scripts data is stored */
    public static final File DEFAULT_SCRIPTS_FOLDER = new File(
        DATA_FOLDER,
        "scripts");

    /** The default folder where scores data is stored */
    public static final File DEFAULT_SCORES_FOLDER = new File(
        DATA_FOLDER,
        "scores");

    //~ Methods ----------------------------------------------------------------

    //--------------//
    // ensureLoaded //
    //--------------//
    /**
     * Make sure this class is loaded.
     */
    public static void ensureLoaded ()
    {
    }

    //-------------------//
    // getClassContainer //
    //-------------------//
    private static File getClassContainer ()
    {
        try {
            /** Classes container, beware of escaped blanks */
            return new File(
                WellKnowns.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException ex) {
            System.err.println("Cannot decode container, " + ex);
            throw new RuntimeException(ex);
        }
    }

    //-----------------//
    // getConfigFolder //
    //-----------------//
    private static File getConfigFolder ()
    {
        if (isProject) {
            // For development environment CONFIG = DATA = PROGRAM
            return PROGRAM_FOLDER;
        }

        if (WINDOWS) {
            return getDataFolder();
        } else if (MAC_OS_X) {
            throw new UnsupportedOperationException("Not yet implemented");
        } else if (LINUX) {
            String config = System.getenv("XDG_CONFIG_HOME");

            if (config != null) {
                return new File(config + TOOL_PREFIX);
            }

            String home = System.getenv("HOME");

            if (home != null) {
                return new File(home + "/.config" + TOOL_PREFIX);
            }

            throw new RuntimeException("HOME environment variable is not set");
        } else {
            throw new RuntimeException("Platform unknown");
        }
    }

    //---------------//
    // getDataFolder //
    //---------------//
    private static File getDataFolder ()
    {
        if (isProject) {
            // For development environment CONFIG = DATA = PROGRAM
            return PROGRAM_FOLDER;
        }

        if (WINDOWS) {
            String appdata = System.getenv("APPDATA");

            if (appdata != null) {
                return new File(appdata + TOOL_PREFIX);
            }

            throw new RuntimeException(
                "APPDATA environment variable is not set");
        } else if (MAC_OS_X) {
            throw new UnsupportedOperationException("Not yet implemented");
        } else if (LINUX) {
            String data = System.getenv("XDG_DATA_HOME");

            if (data != null) {
                return new File(data + TOOL_PREFIX);
            }

            String home = System.getenv("HOME");

            if (home != null) {
                return new File(home + "/.local/share" + TOOL_PREFIX);
            }

            throw new RuntimeException("HOME environment variable is not set");
        } else {
            throw new RuntimeException("Platform unknown");
        }
    }

    //------------//
    // setLogging //
    //------------//
    private static void setLogging ()
    {
        final String LOGGING_KEY = "java.util.logging.config.file";
        final String LOGGING_NAME = "logging.properties";

        // Set logging configuration file (if none already defined)
        if (System.getProperty(LOGGING_KEY) == null) {
            // Check for a user file
            File loggingFile = new File(SETTINGS_FOLDER, LOGGING_NAME);

            if (loggingFile.exists()) {
                System.setProperty(LOGGING_KEY, loggingFile.toString());
            }
        }

        /** Set up logger mechanism */
        Logger.getLogger(WellKnowns.class);
    }

    //------------------//
    // getProgramFolder //
    //------------------//
    private static File getProgramFolder ()
    {
        // .../build/classes when running from classes files
        // .../dist/audiveris.jar when running from the jar archive
        return CLASS_CONTAINER.getParentFile()
                              .getParentFile();
    }

    //-----------//
    // isProject //
    //-----------//
    private static boolean isProject ()
    {
        // We use the fact that a "src" folder is the evidence that we
        // are running from the development folder (with data & config as
        // subfolders) rather than from a standard folder (installed with 
        // distinct location for application data & config).
        File devFolder = new File(PROGRAM_FOLDER, "src");

        return devFolder.exists();
    }
}
