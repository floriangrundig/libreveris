//----------------------------------------------------------------------------//
//                                                                            //
//                          S h e e t P a i n t e r                           //
//                                                                            //
//  Copyright (C) Herve Bitteur 2000-2007. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Contact author at herve.bitteur@laposte.net to report bugs & suggestions. //
//----------------------------------------------------------------------------//
//
package omr.score.visitor;

import omr.glyph.Glyph;

import omr.score.Score;
import omr.score.entity.Measure;
import omr.score.entity.Staff;
import omr.score.entity.System;
import omr.score.entity.SystemPart;

import omr.sheet.Ending;
import omr.sheet.Ledger;
import omr.sheet.Sheet;
import omr.sheet.StaffInfo;
import omr.sheet.SystemInfo;

import omr.step.Step;

import omr.stick.Stick;

import omr.ui.view.Zoom;

import omr.util.Logger;

import java.awt.*;

/**
 * Class <code>SheetPainter</code> defines for every node in Score hierarchy
 * the rendering of related sections (with preset colors) in the dedicated
 * <b>Sheet</b> display.
 *
 * <p>Nota: It has been extended to deal with rendering of initial sheet
 * elements.
 *
 * @author Herv&eacute Bitteur
 * @version $Id$
 */
public class SheetPainter
    extends AbstractScoreVisitor
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(SheetPainter.class);

    /** Stroke to draw system boundaries */
    private static Stroke boundaryStroke = new BasicStroke(
        1f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_BEVEL,
        0f,
        new float[] { 5, 5 },
        0);

    //~ Instance fields --------------------------------------------------------

    /** Graphic context */
    private final Graphics2D g;

    /** Display zoom */
    private final Zoom z;

    //~ Constructors -----------------------------------------------------------

    //--------------//
    // SheetPainter //
    //--------------//
    /**
     * Creates a new SheetPainter object.
     *
     *
     * @param g Graphic context
     * @param z zoom factor
     */
    public SheetPainter (Graphics g,
                         Zoom     z)
    {
        this.g = (Graphics2D) g;
        this.z = z;
    }

    //~ Methods ----------------------------------------------------------------

    //---------------//
    // visit Measure //
    //---------------//
    @Override
    public boolean visit (Measure measure)
    {
        // Render the measure ending barline, if within the clipping area
        if (measure.getBarline() != null) {
            measure.getBarline()
                   .render(g, z);
        }

        return true;
    }

    //-------------//
    // visit Score //
    //-------------//
    @Override
    public boolean visit (Score score)
    {
        score.acceptChildren(this);

        return false;
    }

    //-------------//
    // visit Staff //
    //-------------//
    @Override
    public boolean visit (Staff staff)
    {
        StaffInfo info = staff.getInfo();

        // Render the staff lines, if within the clipping area
        if ((info != null) && info.render(g, z)) {
            return true;
        } else {
            return false;
        }
    }

    //------------------//
    // visit SystemPart //
    //------------------//
    @Override
    public boolean visit (SystemPart part)
    {
        // Render the part starting barline, if any
        if (part.getStartingBarline() != null) {
            part.getStartingBarline()
                .render(g, z);
        }

        return true;
    }

    /**
     * Although a Sheet is not part of the Score hierarchy, this visitor has
     * been specifically extended to render all physical info of a sheet
     *
     * @param sheet the sheet to render initial elements
     */

    //-------------//
    // visit Sheet //
    //-------------//
    public boolean visit (Sheet sheet)
    {
        // Use specific color
        g.setColor(Color.lightGray);

        Score score = sheet.getScore();

        if ((score != null) && (score.getSystems()
                                     .size() > 0)) {
            // Normal (full) rendering of the score
            score.accept(this);
        } else {
            // Render what we have got so far
            if (sheet.getStaves() != null) {
                for (StaffInfo staff : sheet.getStaves()) {
                    staff.render(g, z);
                }
            }
        }

        if (sheet.getSystems() != null) {
            for (SystemInfo system : sheet.getSystems()) {
                visit(system);
            }
        } else {
            // Horizontals
            if (sheet.getHorizontals() != null) {
                // Ledgers
                for (Ledger ledger : sheet.getHorizontals()
                                          .getLedgers()) {
                    ledger.render(g, z);
                }

                // Endings
                for (Ending ending : sheet.getHorizontals()
                                          .getEndings()) {
                    ending.render(g, z);
                }
            }
        }

        return true;
    }

    //--------------//
    // visit System //
    //--------------//
    @Override
    public boolean visit (System system)
    {
        if (system == null) {
            return false;
        }

        return visit(system.getInfo());
    }

    //------------------//
    // visit SystemInfo //
    //------------------//
    public boolean visit (SystemInfo systemInfo)
    {
        // Check that this system is visible
        Rectangle box = systemInfo.getBounds();
        z.scale(box);

        if (box.intersects(g.getClipBounds())) {
            g.setColor(Color.lightGray);

            // System boundary
            systemInfo.getBoundary()
                      .render(g, z.getRatio());

            // Staff lines
            for (StaffInfo staff : systemInfo.getStaves()) {
                staff.render(g, z);
            }

            g.setColor(Color.black);

            // Stems 
            for (Glyph glyph : systemInfo.getGlyphs()) {
                if (glyph.isStem()) {
                    Stick stick = (Stick) glyph;
                    stick.renderLine(g, z);
                }
            }

            // Ledgers
            for (Ledger ledger : systemInfo.getLedgers()) {
                ledger.render(g, z);
            }

            // Endings
            for (Ending ending : systemInfo.getEndings()) {
                ending.render(g, z);
            }

            return true;
        }

        return false;
    }
}
