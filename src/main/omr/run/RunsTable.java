//----------------------------------------------------------------------------//
//                                                                            //
//                             R u n s T a b l e                              //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Hervé Bitteur 2000-2011. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.run;

import omr.lag.PixelSource;

import omr.log.Logger;

import omr.score.common.PixelPoint;
import omr.score.common.PixelRectangle;

import omr.selection.LocationEvent;
import omr.selection.MouseMovement;
import omr.selection.RunEvent;
import omr.selection.SelectionHint;
import omr.selection.SelectionService;

import omr.util.Implement;
import omr.util.Predicate;

import org.bushe.swing.event.EventSubscriber;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class {@code RunsTable} handles a rectangular assembly of oriented runs
 *
 * @author Hervé Bitteur
 */
public class RunsTable
    implements Cloneable, PixelSource, Oriented, EventSubscriber<LocationEvent>
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(RunsTable.class);

    /** Events that can be published on the table run service */
    public static final Class[] eventsWritten = new Class[] { RunEvent.class };

    /** Events observed on location service */
    public static final Class[] eventsRead = new Class[] { LocationEvent.class };

    //~ Instance fields --------------------------------------------------------

    /** (Debugging) name of this runs table */
    private final String name;

    /** Orientation, the same for this table and all contained runs */
    private final Orientation orientation;

    /** Absolute dimension of the table */
    private final Dimension dimension;

    /** List of Runs found in each row. This is a list of lists of Runs */
    private final List<List<Run>> runs;

    /** Hosted event service for UI events related to this table (Runs) */
    private final SelectionService runService;

    //~ Constructors -----------------------------------------------------------

    //-----------//
    // RunsTable //
    //-----------//
    /**
     * Creates a new RunsTable object.
     *
     * @param name name for debugging
     * @param orientation orientation of each run
     * @param dimension absolute dimensions of the table (width is horizontal,
     * height is vertical)
     */
    public RunsTable (String      name,
                      Orientation orientation,
                      Dimension   dimension)
    {
        this.name = name;
        this.orientation = orientation;
        this.dimension = dimension;

        runService = new SelectionService(name, eventsWritten);

        // Allocate the runs, according to orientation
        Rectangle rect = orientation.oriented(
            new PixelRectangle(0, 0, dimension.width, dimension.height));

        // Prepare the collections of runs, one collection per pos value
        runs = new ArrayList<List<Run>>(rect.height);

        for (int i = 0; i < rect.height; i++) {
            runs.add(new ArrayList<Run>());
        }
    }

    //~ Methods ----------------------------------------------------------------

    //--------------//
    // getDimension //
    //--------------//
    /**
     * Report the absolute dimension of the table, width along x axis and height
     * along the y axis.
     * @return the absolute dimension
     */
    public Dimension getDimension ()
    {
        return new Dimension(dimension);
    }

    //-----------//
    // getHeight //
    //-----------//
    @Implement(PixelSource.class)
    public int getHeight ()
    {
        return dimension.height;
    }

    //--------------------//
    // setLocationService //
    //--------------------//
    public void setLocationService (SelectionService locationService)
    {
        for (Class eventClass : eventsRead) {
            locationService.subscribeStrongly(eventClass, this);
        }
    }

    //------------------//
    // setMaxForeground //
    //------------------//
    @Implement(PixelSource.class)
    public void setMaxForeground (int level)
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    //------------------//
    // getMaxForeground //
    //------------------//
    @Implement(PixelSource.class)
    public int getMaxForeground ()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    //---------//
    // getName //
    //---------//
    /**
     * @return the name
     */
    public String getName ()
    {
        return name;
    }

    //----------------//
    // getOrientation //
    //----------------//
    /**
     * @return the orientation of the runs
     */
    @Implement(Oriented.class)
    public Orientation getOrientation ()
    {
        return orientation;
    }

    //-------------//
    // getRunCount //
    //-------------//
    /**
     * Count and return the total number of runs in this table
     * @return the run count
     */
    public int getRunCount ()
    {
        int runCount = 0;

        for (List<Run> seq : runs) {
            for (Run run : seq) {
                runCount += run.getLength();
            }
        }

        return runCount;
    }

    //---------------//
    // getRunService //
    //---------------//
    /**
     * Report the table run selection service
     * @return the run selection service
     */
    public SelectionService getRunService ()
    {
        return runService;
    }

    //-------------//
    // getSequence //
    //-------------//
    /**
     * Report the sequence of runs at a given index
     * @param index the desired index
     * @return the MODIFIABLE sequence of rows
     */
    public final List<Run> getSequence (int index)
    {
        return runs.get(index);
    }

    //---------//
    // getSize //
    //---------//
    /**
     * Report the number of sequences of runs in the table
     * @return the table size (in terms of sequences)
     */
    public final int getSize ()
    {
        return runs.size();
    }

    //-----------//
    // getBuffer //
    //-----------//
    /**
     * Fill a rectangular buffer with the runs
     * @return the filled buffer
     */
    public PixelsBuffer getBuffer ()
    {
        // Prepare output buffer
        PixelsBuffer buffer = new PixelsBuffer(dimension);

        switch (orientation) {
        case HORIZONTAL :

            for (int row = 0; row < getSize(); row++) {
                List<Run> seq = getSequence(row);

                for (Run run : seq) {
                    for (int c = run.getStart(); c <= run.getStop(); c++) {
                        buffer.setPixel(c, row, (char) 0);
                    }
                }
            }

            break;

        case VERTICAL :

            for (int row = 0; row < getSize(); row++) {
                List<Run> seq = getSequence(row);

                for (Run run : seq) {
                    for (int col = run.getStart(); col <= run.getStop();
                         col++) {
                        buffer.setPixel(row, col, (char) 0);
                    }
                }
            }

            break;
        }

        return buffer;
    }

    //-------------//
    // isIdentical //
    //-------------//
    /**
     * Field by field comparison (TODO: used by unit tests only!)
     * @param that the other RunsTable to compare with
     * @return true if identical
     */
    public boolean isIdentical (RunsTable that)
    {
        // Check null entities
        if (that == null) {
            return false;
        }

        if ((this.orientation == that.orientation) &&
            this.dimension.equals(that.dimension) &&
            this.name.equals(that.name)) {
            // Check runs
            for (int row = 0; row < getSize(); row++) {
                List<Run> thisSeq = getSequence(row);
                List<Run> thatSeq = that.getSequence(row);

                if (thisSeq.size() != thatSeq.size()) {
                    return false;
                }

                for (int iRun = 0; iRun < thisSeq.size(); iRun++) {
                    Run thisRun = thisSeq.get(iRun);
                    Run thatRun = thatSeq.get(iRun);

                    if (!thisRun.isIdentical(thatRun)) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    //----------//
    // getPixel //
    //----------//
    /**
     * {@inheritDoc}
     *
     * <br><b>Beware</b>, this implementation is not efficient enough for bulk
     * operations. For such needs, a much more efficient way is to first
     * retrieve a full buffer, via {@link #getBuffer()} method, then use this
     * temporary buffer as the {@link PixelSource} instead of this table.
     * @param x absolute abscissa
     * @param y absolute ordinate
     * @return the pixel gray level
     */
    @Implement(PixelSource.class)
    public final int getPixel (int x,
                               int y)
    {
        Run run = getRunAt(x, y);

        return (run != null) ? run.getLevel() : BACKGROUND;
    }

    //----------//
    // getRunAt //
    //----------//
    /**
     * Report the run found at given coordinates, if any.
     *
     * @param x absolute abscissa
     * @param y absolute ordinate
     * @return  the run found, or null otherwise
     */
    public final Run getRunAt (int x,
                               int y)
    {
        Point oPt = orientation.oriented(new PixelPoint(x, y));

        // Protection
        if ((oPt.y < 0) || (oPt.y >= runs.size())) {
            return null;
        }

        List<Run> seq = getSequence(oPt.y);

        for (Run run : seq) {
            if (run.getStart() > oPt.x) {
                return null;
            }

            if (run.getStop() >= oPt.x) {
                return run;
            }
        }

        return null;
    }

    //----------//
    // getWidth //
    //----------//
    @Implement(PixelSource.class)
    public int getWidth ()
    {
        return dimension.width;
    }

    //-------//
    // clone //
    //-------//
    /**
     * Make a copy of the table, but sharing the run instances
     * @return another table on the same run instances
     */
    @Override
    public RunsTable clone ()
    {
        return clone(name);
    }

    //-------//
    // clone //
    //-------//
    /**
     * Make a copy of the table, but sharing the run instances
     * @param name a new name for the clone
     * @return another table on the same run instances
     */
    public RunsTable clone (String name)
    {
        RunsTable clone = new RunsTable(name, orientation, dimension);

        for (int i = 0; i < getSize(); i++) {
            List<Run> seq = getSequence(i);
            List<Run> cloneSeq = clone.getSequence(i);

            for (Run run : seq) {
                cloneSeq.add(run);
            }
        }

        return clone;
    }

    //------//
    // dump //
    //------//
    /**
     * Print the image of the runs table onto the provided stream
     * @param out the output stream
     */
    public void dump (PrintStream out)
    {
        out.println(toString());

        // Prepare output buffer
        PixelsBuffer buffer = getBuffer();

        // Print the buffer
        out.print('+');

        for (int c = 0; c < dimension.width; c++) {
            out.print('=');
        }

        out.println('+');

        for (int row = 0; row < dimension.height; row++) {
            out.print('|');

            for (int col = 0; col < buffer.getWidth(); col++) {
                out.print(
                    (buffer.getPixel(col, row) == BACKGROUND) ? '-' : 'X');
            }

            out.println('|');
        }

        out.print('+');

        for (int c = 0; c < dimension.width; c++) {
            out.print('=');
        }

        out.println('+');
    }

    //---------//
    // include //
    //---------//
    /**
     * Include the content of the provided table into this one
     * @param that the table of runs to include into this one
     */
    public void include (RunsTable that)
    {
        if (that == null) {
            throw new IllegalArgumentException(
                "Cannot include a null runsTable");
        }

        if (that.orientation != orientation) {
            throw new IllegalArgumentException(
                "Cannot include a runsTable of different orientation");
        }

        if (!that.dimension.equals(dimension)) {
            throw new IllegalArgumentException(
                "Cannot include a runsTable of different dimension");
        }

        for (int row = 0; row < getSize(); row++) {
            List<Run> thisSeq = this.getSequence(row);
            List<Run> thatSeq = that.getSequence(row);

            for (Run thatRun : thatSeq) {
                int start = thatRun.getStart();
                int iRun = 0;

                for (; iRun < thisSeq.size(); iRun++) {
                    Run thisRun = thisSeq.get(iRun);

                    if (thisRun.getStart() > start) {
                        break;
                    }
                }

                thisSeq.add(iRun, thatRun);
            }
        }
    }

    //-----------//
    // lookupRun //
    //-----------//
    /**
     * Given an absolute point, retrieve the containing run if any
     * @param point coordinates of the given point
     * @return the run found, or null otherwise
     */
    public Run lookupRun (PixelPoint point)
    {
        Point oPt = orientation.oriented(point);

        if ((oPt.y < 0) || (oPt.y >= getSize())) {
            return null;
        }

        for (Run run : getSequence(oPt.y)) {
            if (run.getStart() > oPt.x) {
                return null;
            }

            if (run.getStop() >= oPt.x) {
                return run;
            }
        }

        return null;
    }

    //---------//
    // onEvent //
    //---------//
    /**
     * Interest on Location => Run
     * @param locationEvent the interesting event
     */
    public void onEvent (LocationEvent locationEvent)
    {
        try {
            // Ignore RELEASING
            if (locationEvent.movement == MouseMovement.RELEASING) {
                return;
            }

            if (logger.isFineEnabled()) {
                logger.fine("RunsTable " + name + ": " + locationEvent);
            }

            if (locationEvent instanceof LocationEvent) {
                // Location => Run
                handleEvent(locationEvent);
            }
        } catch (Exception ex) {
            logger.warning(getClass().getName() + " onEvent error", ex);
        }
    }

    //-------//
    // purge //
    //-------//
    /**
     * Purge a runs table of all runs that match the provided predicate
     * @param predicate the filter to detect runs to remove
     * @return this runs table, to allow easy chaining
     */
    public RunsTable purge (Predicate<Run> predicate)
    {
        return purge(predicate, null);
    }

    //-------//
    // purge //
    //-------//
    /**
     * Purge a runs table of all runs that match the provided predicate, and
     * populate the provided 'removed' table with the removed runs.
     * @param predicate the filter to detect runs to remove
     * @param removed a table to be filled, if not null, with purged runs
     * @return this runs table, to allow easy chaining
     */
    public RunsTable purge (Predicate<Run> predicate,
                            RunsTable      removed)
    {
        // Check parameters
        if (removed != null) {
            if (removed.orientation != orientation) {
                throw new IllegalArgumentException(
                    "'removed' table is of different orientation");
            }

            if (!removed.dimension.equals(dimension)) {
                throw new IllegalArgumentException(
                    "'removed' table is of different dimension");
            }
        }

        for (int i = 0; i < getSize(); i++) {
            List<Run> seq = getSequence(i);

            for (Iterator<Run> it = seq.iterator(); it.hasNext();) {
                Run run = it.next();

                if (predicate.check(run)) {
                    it.remove();

                    if (removed != null) {
                        removed.getSequence(i)
                               .add(run);
                    }
                }
            }
        }

        return this;
    }

    //-----------//
    // removeRun //
    //-----------//
    /**
     * Remove the provided run at indicated position
     * @param pos the position where run is to be found
     * @param run the run to remove
     */
    public void removeRun (int pos,
                           Run run)
    {
        List<Run> seq = getSequence(pos);

        if (!seq.remove(run)) {
            throw new RuntimeException(
                this + " Cannot find " + run + " at pos " + pos);
        }
    }

    //----------//
    // toString //
    //----------//
    @Override
    public String toString ()
    {
        StringBuilder sb = new StringBuilder("{");
        sb.append(getClass().getSimpleName());

        sb.append(" ")
          .append(name);

        sb.append(" ")
          .append(orientation);

        sb.append(" ")
          .append(dimension.width)
          .append("x")
          .append(dimension.height);

        sb.append("}");

        return sb.toString();
    }

    //-------------//
    // handleEvent //
    //-------------//
    /**
     * Interest in location => Run
     * @param location
     */
    private void handleEvent (LocationEvent locationEvent)
    {
        PixelRectangle rect = locationEvent.getData();

        if (rect == null) {
            return;
        }

        SelectionHint hint = locationEvent.hint;
        MouseMovement movement = locationEvent.movement;

        if ((hint != SelectionHint.LOCATION_ADD) &&
            (hint != SelectionHint.LOCATION_INIT)) {
            return;
        }

        if ((rect.width == 0) && (rect.height == 0)) {
            PixelPoint pt = rect.getLocation();

            // Publish Run information
            Run run = getRunAt(pt.x, pt.y);
            runService.publish(new RunEvent(this, hint, movement, run));
        }
    }
}
