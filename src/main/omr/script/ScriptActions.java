//----------------------------------------------------------------------------//
//                                                                            //
//                         S c r i p t A c t i o n s                          //
//                                                                            //
//  Copyright (C) Herve Bitteur 2000-2007. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Contact author at herve.bitteur@laposte.net to report bugs & suggestions. //
//----------------------------------------------------------------------------//
//
package omr.script;

import omr.Main;

import omr.constant.Constant;
import omr.constant.ConstantSet;
import static omr.plugin.Dependency.*;
import omr.plugin.Plugin;
import static omr.plugin.PluginType.*;

import omr.sheet.Sheet;
import omr.sheet.SheetManager;

import omr.ui.SheetActions;
import omr.ui.util.FileFilter;
import omr.ui.util.UIUtilities;

import omr.util.BasicTask;
import omr.util.Implement;
import omr.util.Logger;

import org.jdesktop.application.Action;
import org.jdesktop.application.Task;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;

/**
 * Class <code>ScriptActions</code> gathers UI actions related to script
 * handling. These static member classes are ready to be picked by the plugins
 * mechanism.
 *
 * @author Herv&eacute Bitteur
 * @version $Id$
 */
public class ScriptActions
    extends SheetActions
{
    //~ Static fields/initializers ---------------------------------------------

    /** Specific application parameters */
    private static final Constants constants = new Constants();

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(ScriptActions.class);

    /** Singleton */
    private static ScriptActions INSTANCE;

    //~ Constructors -----------------------------------------------------------

    //---------------//
    // ScriptActions //
    //---------------//
    /**
     * Not meant to be instantiated
     */
    protected ScriptActions ()
    {
    }

    //~ Methods ----------------------------------------------------------------

    //-------------//
    // getInstance //
    //-------------//
    /**
     * Report the singleton
     *
     * @return the unique instance of this class
     */
    public static synchronized ScriptActions getInstance ()
    {
        if (INSTANCE == null) {
            INSTANCE = new ScriptActions();
        }

        return INSTANCE;
    }

    //-------------//
    // checkStored //
    //-------------//
    public static boolean checkStored (Script script)
    {
        if (!script.isStored() && constants.closeConfirmation.getValue()) {
            int answer = JOptionPane.showConfirmDialog(
                null,
                "Save script for sheet " + script.getSheet().getRadix() + "?");

            if (answer == JOptionPane.YES_OPTION) {
                Task task = getInstance()
                                .storeScript(null);

                if (task != null) {
                    task.execute();
                }

                return true;
            }

            if (answer == JOptionPane.NO_OPTION) {
                return true;
            }

            return false;
        } else {
            return true;
        }
    }

    //------------//
    // loadScript //
    //------------//
    @Action
    public Task loadScript (ActionEvent e)
    {
        final File file = UIUtilities.fileChooser(
            false,
            Main.getGui().getFrame(),
            new File(constants.defaultScriptDirectory.getValue()),
            new FileFilter(
                "Score script files",
                new String[] { ScriptManager.SCRIPT_EXTENSION }));

        if (file != null) {
            return new LoadScriptTask(file);
        } else {
            return null;
        }
    }

    //-------------//
    // storeScript //
    //-------------//
    @Action(enabledProperty = "sheetAvailable")
    public Task storeScript (ActionEvent e)
    {
        final Sheet sheet = SheetManager.getSelectedSheet();

        if (sheet == null) {
            return null;
        }

        final Script script = sheet.getScript();

        if (script == null) {
            return null;
        }

        // Where do we write the script file?
        File xmlFile = new File(
            constants.defaultScriptDirectory.getValue(),
            script.getSheet().getRadix() + ScriptManager.SCRIPT_EXTENSION);

        // Ask user confirmation: let the user select a script output file
        xmlFile = UIUtilities.fileChooser(
            true,
            Main.getGui().getFrame(),
            xmlFile,
            new FileFilter(
                "Script files",
                new String[] { ScriptManager.SCRIPT_EXTENSION }));

        if (xmlFile == null) {
            return null;
        }

        constants.defaultScriptDirectory.setValue(xmlFile.getParent());

        return new StoreScriptTask(script, xmlFile);
    }

    //~ Inner Classes ----------------------------------------------------------

    //------------//
    // LoadAction //
    //------------//
    /**
     * Class <code>LoadAction</code> let the user select and load a script file
     * @deprecated
     */
    @Deprecated
    @Plugin(type = SHEET_SCRIPT)
    public static class LoadAction
        extends AbstractAction
    {
        //~ Methods ------------------------------------------------------------

        @Implement(ActionListener.class)
        public void actionPerformed (ActionEvent e)
        {
            Task task = getInstance()
                            .loadScript(e);

            if (task != null) {
                task.execute();
            }
        }
    }

    //-------------//
    // StoreAction //
    //-------------//
    /**
     * Class <code>StoreAction</code> handles the storing of the currently
     * selected script.
     * @deprecated
     */
    @Deprecated
    @Plugin(type = SHEET_SCRIPT, dependency = SHEET_AVAILABLE)
    public static class StoreAction
        extends AbstractAction
    {
        //~ Methods ------------------------------------------------------------

        @Implement(ActionListener.class)
        public void actionPerformed (ActionEvent e)
        {
            getInstance()
                .storeScript(e)
                .execute();
        }
    }

    //-----------//
    // Constants //
    //-----------//
    private static final class Constants
        extends ConstantSet
    {
        //~ Instance fields ----------------------------------------------------

        /** Default directory for saved scripts */
        Constant.String defaultScriptDirectory = new Constant.String(
            "",
            "Default directory for saved scripts");

        /** User confirmation for closing unsaved script */
        Constant.Boolean closeConfirmation = new Constant.Boolean(
            true,
            "Should we ask confirmation for closing a sheet with unsaved script?");
    }

    //----------------//
    // LoadScriptTask //
    //----------------//
    private static class LoadScriptTask
        extends BasicTask
    {
        //~ Instance fields ----------------------------------------------------

        private File file;

        //~ Constructors -------------------------------------------------------

        LoadScriptTask (File file)
        {
            this.file = file;
        }

        //~ Methods ------------------------------------------------------------

        @Override
        protected Void doInBackground ()
            throws InterruptedException
        {
            // Actually load the script
            logger.info("Loading script file " + file + " ...");

            try {
                final Script script = ScriptManager.getInstance()
                                                   .load(
                    new FileInputStream(file));

                if (logger.isFineEnabled()) {
                    script.dump();
                }

                // Remember (even across runs) the parent directory
                constants.defaultScriptDirectory.setValue(file.getParent());
                script.run();
            } catch (FileNotFoundException ex) {
                logger.warning("Cannot find script file " + file);
            }

            return null;
        }
    }

    //-----------------//
    // StoreScriptTask //
    //-----------------//
    private static class StoreScriptTask
        extends BasicTask
    {
        //~ Instance fields ----------------------------------------------------

        private Script script;
        private File   file;

        //~ Constructors -------------------------------------------------------

        StoreScriptTask (Script script,
                         File   file)
        {
            this.script = script;
            this.file = file;
        }

        //~ Methods ------------------------------------------------------------

        @Override
        protected Void doInBackground ()
            throws InterruptedException
        {
            try {
                java.io.File folder = new java.io.File(file.getParent());

                if (folder.mkdirs()) {
                    logger.info("Creating folder " + folder);
                }

                omr.script.ScriptManager.getInstance()
                                        .store(
                    script,
                    new java.io.FileOutputStream(file));
                logger.info("Script stored as " + file);
            } catch (FileNotFoundException ex) {
                logger.warning("Cannot find script file " + file, ex);
            }

            return null;
        }
    }
}
