//----------------------------------------------------------------------------//
//                                                                            //
//                          T a r g e t S y s t e m                           //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Herve Bitteur 2000-2010. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.sheet.grid;

import java.util.ArrayList;
import java.util.List;

/**
 * Class {@code TargetSystem} is an immutable perfect destination object for a
 * system.
 *
 * @author Hervé Bitteur
 */
public class TargetSystem
{
    //~ Instance fields --------------------------------------------------------

    /** Raw information */
    public final SystemFrame info;

    /** Id for debug */
    public final int id;

    /** Ordinate of top of first staff in containing page */
    public final int top;

    /** Left abscissa in containing page */
    public final int left;

    /** Right abscissa in containing page */
    public final int right;

    /** Sequence of staves */
    public final List<TargetStaff> staves = new ArrayList<TargetStaff>();

    //~ Constructors -----------------------------------------------------------

    //--------------//
    // TargetSystem //
    //--------------//
    /**
     * Creates a new TargetSystem object.
     *
     * @param info the original raw information
     * @param top ordinate of top
     * @param left abscissa of left
     * @param right abscissa of right
     */
    public TargetSystem (SystemFrame info,
                         int         top,
                         int         left,
                         int         right)
    {
        this.info = info;
        this.top = top;
        this.left = left;
        this.right = right;

        id = info.getId();
    }

    //~ Methods ----------------------------------------------------------------

    //----------//
    // toString //
    //----------//
    @Override
    public String toString ()
    {
        StringBuilder sb = new StringBuilder("{System");
        sb.append("#")
          .append(id);
        sb.append(" top:")
          .append(top);
        sb.append(" left:")
          .append(left);
        sb.append(" right:")
          .append(right);
        sb.append("}");

        return sb.toString();
    }
}