//-----------------------------------------------------------------------//
//                                                                       //
//                                M a i n                                //
//                                                                       //
//  Copyright (C) Herve Bitteur 2000-2005. All rights reserved.          //
//  This software is released under the terms of the GNU General Public  //
//  License. Please contact the author at herve.bitteur@laposte.net      //
//  to report bugs & suggestions.                                        //
//-----------------------------------------------------------------------//
//      $Id$
package omr;

import omr.constant.Constant;
import omr.constant.ConstantSet;
import omr.score.Score;
import omr.score.ScoreManager;
import omr.sheet.Sheet;
import omr.sheet.SheetManager;
import omr.ui.Jui;
import omr.ui.UILookAndFeel;
import omr.util.Clock;
import omr.util.Logger;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Class <code>Main</code> is the main class for OMR  application. It deals
 * with the main routine and its command line parameters.  It launches the
 * User Interface, unless a batch mode is selected.
 *
 * <p> The command line parameters can be (order not relevant) : <dl>
 *
 * <dt> <b>-help</b> </dt> <dd> to print a quick usage help and leave the
 * application. </dd>
 *
 * <dt> <b>-batch</b> </dt> <dd> to run in batch mode, with no user
 * interface. </dd>
 *
 * <dt> <b>-write</b> </dt> <dd> to specify that the resulting score has to
 * be written down once the specified step has been reached. This feature
 * is available in batch mode only. </dd>
 *
 * <dt> <b>-save SAVEPATH</b> </dt> <dd> to specify the directory where
 * score output files are saved. If not specified, files are simply written
 * to the 'save' sub-directory of Audiveris. </dd>
 *
 * <dt> <b>-sheet (SHEETNAME | &#64;SHEETLIST)+</b> </dt> <dd> to specify
 * some sheets to be read, either by naming the image file or by
 * referencing (flagged by a &#64; sign) a file that lists image files (or
 * even other files list recursively). A list file is a simple text file,
 * with one image file name per line.</dd>
 *
 * <dt> <b>-score (SCORENAME | &#64;SCORELIST)+</b> </dt> <dd> to specify
 * some scores to be read, using the same mechanism than sheets. These
 * score files contain binary data in saved during a previous run.</dd>
 *
 * <dt> <b>-step STEPNAME</b> </dt> <dd> to run till the specified
 * step. 'STEPNAME' can be any one of the step names (the case is
 * irrelevant) as defined in the {@link omr.sheet.Sheet} class.
 *
 * </dd> </dl>
 */
public class Main
{
    //~ Static variables/initializers -------------------------------------

    // First things first!
    static
    {
        // UI Look and Feel
        UILookAndFeel.setUI(null);

        // Time stamps
        Clock.resetTime();
    }

    private static final Logger logger = Logger.getLogger(Main.class);

    // Installation directory
    private static final String AUDIVERIS_HOME = "AUDIVERIS_HOME";
    private static String homeDir = System.getProperty
        ("audiveris.home",
         System.getenv(AUDIVERIS_HOME));

    // Train path
    private static File trainPath;

    // Batch mode if any
    private static boolean batchMode = false;

    // Request to write score if any
    private static boolean writeScore = false;

    // Master View
    private static Jui jui;

    // Target step
    private static Step targetStep;

    // List of sheet file names to process
    private static List<String> sheetNames = new ArrayList<String>();

    // List of score file names to process
    private static List<String> scoreNames = new ArrayList<String>();

    /**
     * Name of the application as displayed to the user
     */
    public static String toolName;

    /**
     * Version of the application as displayed to the user
     */
    public static String toolVersion;

    private static final Constants constants = new Constants();

    //~ Constructors ------------------------------------------------------

    //------//
    // Main //
    //------//
    private Main ()
    {
        if (targetStep == null) {
            targetStep = Step.getLoadStep();
        }

        // Interactive or Batch mode ?
        if (!batchMode) {
            logger.debug("Interactive processing");
            Step.setView();

            // Make sure we have nice window decorations.
            JFrame.setDefaultLookAndFeelDecorated(true);

            // Launch the GUI
            jui = new Jui();

            // Do we have sheet or score actions specified?
            if (sheetNames.size() > 0 ||
                scoreNames.size() > 0) {
                Worker worker = new Worker();
                // Make sure the Gui gets priority
                worker.setPriority(Thread.MIN_PRIORITY);
                worker.start();
            }
        } else {
            logger.info("Batch processing");
            browse();
        }
    }

    //~ Methods -----------------------------------------------------------

    //--------//
    // browse //
    //--------//
    private void browse ()
    {
        // Browse desired sheets
        for (String name : sheetNames) {
            File file = new File(name);

            // We do not register the sheet target, since there may be
            // several in a row.  But we perform all steps through the
            // desired step
            targetStep.perform(null, file);

            // Batch part?
            if (batchMode) {
                // Do we have to write down the score?
                if (writeScore) {
                    //Score.storeAll();
                    ScoreManager.getInstance().serializeAll();
                }

                // Dispose allocated stuff
                SheetManager.getInstance().closeAll();
                ScoreManager.getInstance().closeAll();
            }
        }

        // Browse desired scores
        for (String name : scoreNames) {
            Score score = ScoreManager.getInstance().load(new File(name));

            if (!batchMode) {
                Main.getJui().scorePane.setScoreView(score);
            }
        }
    }

    //--------//
    // getJui //
    //--------//
    /**
     * Points to the single instance of the User Interface, if any.
     *
     * @return Jui instance, which may be null
     */
    public static Jui getJui ()
    {
        return jui;
    }

    //---------------//
    // getOutputPath //
    //---------------//
    /**
     * Report the directory defined for output/saved files
     *
     * @return the directory for output
     */
    public static String getOutputPath ()
    {
        String saveDir = constants.savePath.getValue();

        if (saveDir.equals("")) {
            // Use default save directory
            return homeDir + "/save";
        } else {
            // Make sure that it ends with proper separator
            if (!(saveDir.endsWith("\\")
                  || saveDir.endsWith("/"))) {
                saveDir = saveDir + "/";
            }
            return saveDir;
        }
    }

    //--------------//
    // getTrainPath //
    //--------------//
    /**
     * Report the directory defined for training files
     *
     * @return the directory for training material
     */
    public static File getTrainPath ()
    {
        if (trainPath == null) {
            trainPath = new File(homeDir, "train");
        }

        return trainPath;
    }

    //------//
    // main //
    //------//
    /**
     * Usual starting method for the application.
     *
     * @param args        the command line parameters
     *
     * @see omr.Main the possible command line parameters
     */
    public static void main (String[] args)
    {
        Package thisPackage = Main.class.getPackage();
        Main.toolName = thisPackage.getSpecificationTitle();
        Main.toolVersion = thisPackage.getSpecificationVersion();

        // Check installation home
        if (homeDir == null) {
            stopUsage("Environment variable '" + AUDIVERIS_HOME
                      + "' not set.");
        }

        // Problem, from Emacs all args are passed in one string
        // sequence.  We recognize this by detecting a single
        // argument starting with '-'
        if ((args.length == 1) && (args[0].startsWith("-"))) {
            // Redispatch the real args
            StringTokenizer st = new StringTokenizer(args[0]);
            int argNb = 0;

            // First just count the number of real arguments
            while (st.hasMoreTokens()) {
                argNb++;
                st.nextToken();
            }

            String[] newArgs = new String[argNb];

            // Second copy all real arguments into newly
            // allocated array
            argNb = 0;
            st = new StringTokenizer(args[0]);

            while (st.hasMoreTokens()) {
                newArgs[argNb++] = st.nextToken();
            }

            // Fake the args
            args = newArgs;
        }

        // Status of the finite state machine
        final int STEP  = 0;
        final int SHEET = 1;
        final int SCORE = 2;
        final int SAVE  = 3;
        boolean paramNeeded = false; // Are we expecting a param?
        int status = SHEET; // By default
        String currentCommand = null;

        // Parse all arguments from command line
        for (int i = 0; i < args.length; i++) {
            String token = args[i];

            if (token.startsWith("-")) {
                // This is a command
                // Check that we were not expecting param(s)
                if (paramNeeded) {
                    stopUsage("Found no parameter after command '"
                              + currentCommand + "'");
                }

                if (token.equalsIgnoreCase("-help")) {
                    stopUsage(null);
                } else if (token.equalsIgnoreCase("-batch")) {
                    batchMode = true;
                    paramNeeded = false;
                } else if (token.equalsIgnoreCase("-write")) {
                    writeScore = true;
                    paramNeeded = false;
                } else if (token.equalsIgnoreCase("-step")) {
                    status = STEP;
                    paramNeeded = true;
                } else if (token.equalsIgnoreCase("-sheet")) {
                    status = SHEET;
                    paramNeeded = true;
                } else if (token.equalsIgnoreCase("-score")) {
                    status = SCORE;
                    paramNeeded = true;
                } else if (token.equalsIgnoreCase("-save")) {
                    status = SAVE;
                    paramNeeded = true;
                } else {
                    stopUsage("Unknown command '" + token + "'");
                }

                // Remember the current command
                currentCommand = token;
            } else {
                // This is a parameter
                switch (status) {
                    case STEP:

                        // Read a step name
                        targetStep = null;

                        for (Step step : Sheet.getSteps()) {
                            if (token.equalsIgnoreCase(step.toString())) {
                                targetStep = step;

                                break;
                            }
                        }

                        if (targetStep == null) {
                            stopUsage("Step name expected, found '" + token
                                      + "' instead");
                        }

                        // By default, sheets are now expected
                        status = SHEET;
                        paramNeeded = false;

                        break;

                    case SHEET:
                        addRef(token, sheetNames);
                        paramNeeded = false;

                        break;

                    case SCORE:
                        addRef(token, scoreNames);
                        paramNeeded = false;

                        break;

                    case SAVE:
                        // Make sure that it ends with proper separator
                        if (!(token.endsWith("\\")
                              || token.endsWith("/"))) {
                            token = token + "/";
                        }
                        constants.savePath.setValue(token);

                        // By default, sheets are now expected
                        status = SHEET;
                        paramNeeded = false;

                        break;
                }
            }
        }

        // Additional error checking
        if (paramNeeded) {
            stopUsage("Expecting a parameter after command '"
                      + currentCommand + "'");
        }

        // Results
        if (logger.isDebugEnabled()) {
            logger.debug("batchMode=" + batchMode);
            logger.debug("writeScore=" + writeScore);
            logger.debug("savePath="   + constants.savePath.getValue());
            logger.debug("targetStep=" + targetStep);
            logger.debug("sheetNames=" + sheetNames);
            logger.debug("scoreNames=" + scoreNames);
        }

        // Launch the processing
        new Main();
    }

    //--------//
    // addRef //
    //--------//
    private static void addRef (String ref,
                                List<String> list)
    {
        // The ref may be a plain file name or the name of a pack
        // that lists ref(s). This is signalled by a starting '@'
        // character in ref
        if (ref.startsWith("@")) {
            // File with other refs inside
            String pack = ref.substring(1);

            try {
                BufferedReader br = new BufferedReader
                    (new FileReader(pack));
                String newRef;

                try {
                    while ((newRef = br.readLine()) != null) {
                        addRef(newRef.trim(), list);
                    }

                    br.close();
                } catch (IOException ex) {
                    logger.warning("IO error while reading file '" + pack
                                   + "'");
                }
            } catch (FileNotFoundException ex) {
                logger.warning("Cannot find file '" + pack + "'");
            }
        } else
        // Plain file name
            if (ref.length() > 0) {
                list.add(ref);
            }
    }

    //-----------//
    // stopUsage //
    //-----------//
    private static void stopUsage (String msg)
    {
        // Print message if any
        if (msg != null) {
            logger.error(msg);
        }

        StringBuffer buf = new StringBuffer(1024);

        // Print standard command line syntax
        buf
            .append("usage: java ")
            .append(toolName)
            .append(" [-help]")
            .append(" [-batch]")
            .append(" [-write]")
            .append(" [-save SAVEPATH]")
            .append(" [-step STEPNAME]")
            .append(" [-sheet (SHEETNAME|@SHEETLIST)+]")
            .append(" [-score (SCORENAME|@SCORELIST)+]");

        // Print all allowed step names
        buf
            .append("\n      Known step names are in order")
            .append(" (non case-sensitive) :");

        for (Step step : Sheet.getSteps()) {
            buf.append(String.format("%n        %-15s : %s",
                                     step.toString().toUpperCase(),
                                     step.getDescription()));
        }

        logger.info(buf.toString());

        // Stop application immediately
        logger.info("Exiting");
        System.exit(-1);
    }

    //--------//
    // Worker //
    //--------//
    private class Worker
        extends Thread
    {
        //~ Methods -------------------------------------------------------

        //-----//
        // run //
        //-----//
        public void run()
        {
            browse();
        }
    }

    //-----------//
    // Constants //
    //-----------//
    private static class Constants
            extends ConstantSet
    {
        //~ Instance variables --------------------------------------------

        Constant.String savePath = new Constant.String
                ("",
                 "Directory for saved files, defaulted to 'save' audiveris subdir");

        Constants ()
        {
            initialize();
        }
    }
}
