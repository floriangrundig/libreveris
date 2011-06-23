//----------------------------------------------------------------------------//
//                                                                            //
//                            T a r g e t L i n e                             //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Herve Bitteur 2000-2010. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.sheet.grid;

import omr.score.common.PixelPoint;
import static omr.util.HorizontalSide.*;

import java.awt.geom.Point2D;

/**
 * Class {@code TargetLine} is an immutable perfect destination object for a
 * staff line.
 *
 * @author Hervé Bitteur
 */
public class TargetLine
{
    //~ Instance fields --------------------------------------------------------

    /** Related raw information */
    public final LineInfo info;

    /** Id for debug */
    public final int id;

    /** Ordinate in containing page */
    public final int y;

    /** Containing staff */
    public final TargetStaff staff;

    /** Sine of raw line angle */
    private final double sin;

    /** Cosine of raw line angle */
    private final double cos;

    //~ Constructors -----------------------------------------------------------

    //------------//
    // TargetLine //
    //------------//
    /**
     * Creates a new TargetLine object.
     *
     * @param info the physical information
     * @param y ordinate in containing pag
     * @param staff the containing staff
     */
    public TargetLine (LineInfo    info,
                       int         y,
                       TargetStaff staff)
    {
        this.info = info;
        this.y = y;
        this.staff = staff;

        id = info.getId();

        // Compute sin & cos values
        PixelPoint left = info.getEndPoint(LEFT);
        PixelPoint right = info.getEndPoint(RIGHT);
        double     dx = right.x - left.x;
        double     dy = right.y - left.y;
        double     hypot = Math.hypot(dx, dy);
        sin = dy / hypot;
        cos = dx / hypot;
    }

    //~ Methods ----------------------------------------------------------------

    //----------//
    // sourceOf //
    //----------//
    /**
     * Report the source point that corresponds to a destination point dst
     * above or below this line
     * @param dst the given destination point
     * @return the corresponding source point
     */
    public Point2D sourceOf (Point2D dst)
    {
        // Use orthogonal projection to line
        double  dist = dst.getY() - y;
        Point2D projSrc = sourceOf(dst.getX());
        double  dx = -dist * sin;
        double  dy = dist * cos;

        return new Point2D.Double(projSrc.getX() + dx, projSrc.getY() + dy);
    }

    //----------//
    // sourceOf //
    //----------//
    /**
     * Report the source point that corresponds to a destination point at
     * abscissa dstX on this line
     * @param dstX the given destination abscissa
     * @return the corresponding source point
     */
    public Point2D sourceOf (double dstX)
    {
        int    left = staff.system.left;
        int    right = staff.system.right;
        double xRatio = (dstX - left) / (right - left);
        double srcX = ((1 - xRatio) * info.getEndPoint(LEFT).x) +
                      (xRatio * info.getEndPoint(RIGHT).x);
        double srcY = info.yAt(srcX);

        return new Point2D.Double(srcX, srcY);
    }

    //----------//
    // toString //
    //----------//
    @Override
    public String toString ()
    {
        StringBuilder sb = new StringBuilder("{Line");
        sb.append("#")
          .append(id);
        sb.append(" y:")
          .append(y);
        sb.append("}");

        return sb.toString();
    }
}
