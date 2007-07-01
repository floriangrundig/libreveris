//----------------------------------------------------------------------------//
//                                                                            //
//                                  S l o t                                   //
//                                                                            //
//  Copyright (C) Herve Bitteur 2000-2007. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Contact author at herve.bitteur@laposte.net to report bugs & suggestions. //
//----------------------------------------------------------------------------//
//
package omr.score;

import omr.constant.ConstantSet;

import omr.glyph.Glyph;
import omr.glyph.Shape;

import omr.math.InjectionSolver;
import omr.math.Population;

import omr.sheet.Scale;

import omr.util.Logger;
import omr.util.TreeNode;

import java.util.*;

/**
 * Class <code>Slot</code> represents a roughly defined time slot within a
 * measure, to gather all chord entities (rests, notes, noteheads) that occur at
 * the same time because their abscissae are roughly the same.
 *
 * <p>The slot embraces all the staves of this part measure. Perhaps we should
 * consider merging slots between parts as well? TBD.
 *
 * <p>On the following picture, slots are indicated by vertical blue lines <br/>
 * <img src="doc-files/Slot.png" alt="diagram">
 *
 * @author Herv&eacute Bitteur
 * @version $Id$
 */
public class Slot
    implements Comparable<Slot>
{
    //~ Static fields/initializers ---------------------------------------------

    /** Specific application parameters */
    private static final Constants constants = new Constants();

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(Slot.class);

    /** Specific comparator to sort chords vertically within a slot */
    private static final Comparator<Chord> chordComparator = new Comparator<Chord>() {
        public int compare (Chord c1,
                            Chord c2)
        {
            Note n1 = (Note) c1.getNotes()
                               .get(0);
            Note n2 = (Note) c2.getNotes()
                               .get(0);

            // First : staff
            int dStaff = n1.getStaff()
                           .getId() - n2.getStaff()
                                        .getId();

            if (dStaff != 0) {
                return Integer.signum(dStaff);
            }

            // Second : head ordinate
            int dHead = c1.getHeadLocation().y - c2.getHeadLocation().y;

            if (dHead != 0) {
                return Integer.signum(dHead);
            }

            // Third : chord id
            return Integer.signum(c1.getId() - c2.getId());
        }
    };


    //~ Instance fields --------------------------------------------------------

    /** Id unique within the containing  measure */
    private int id;

    /** Abscissa of the slot, in units since system start */
    private Integer x;

    /** The containing measure */
    private Measure measure;

    /** Collection of glyphs in the slot */
    private List<Glyph> glyphs = new ArrayList<Glyph>();

    /** Collection of chords in this slot, order by staff, then by ordinate */
    private List<Chord> chords = new ArrayList<Chord>();

    /** Time offset since measure start */
    private Integer startTime;

    //~ Constructors -----------------------------------------------------------

    //------//
    // Slot //
    //------//
    /**
     * Creates a new Slot object.
     *
     * @param measure the containing measure
     */
    public Slot (Measure measure)
    {
        this.measure = measure;
    }

    //~ Methods ----------------------------------------------------------------

    //----------//
    // addGlyph //
    //----------//
    /**
     * Insert a glyph (supposedly from a chord) into this slot, invalidating the
     * internal computed data
     *
     * @param glyph the glyph to insert
     */
    public void addGlyph (Glyph glyph)
    {
        glyphs.add(glyph);
        x = null;
    }

    //------------------------//
    // allocateChordsAndNotes //
    //------------------------//
    /**
     * Based on the current collection of glyphs within this slot, allocate the
     * proper chords, a strategy based on each glyph-related stem.
     */
    public void allocateChordsAndNotes ()
    {
        // Allocate 1 chord per stem, per rest, per (whole) note
        for (Glyph glyph : glyphs) {
            if (glyph.getStemNumber() > 0) {
                // Beware of noteheads with 2 stems, we need to duplicate them
                // in order to actually have two chords.
                if (glyph.getLeftStem() != null) {
                    Chord chord = getStemChord(glyph.getLeftStem());
                    Note.createPack(chord, glyph);
                }

                if (glyph.getRightStem() != null) {
                    Chord chord = getStemChord(glyph.getRightStem());
                    Note.createPack(chord, glyph);
                }
            } else {
                Chord chord = new Chord(measure);
                chords.add(chord);
                Note.createPack(chord, glyph);
            }
        }
    }

    //-------------//
    // buildVoices //
    //-------------//
    /**
     * This static method browses a measure with its slots and chords, in order
     * to compute the various voices and start times for all chords
     *
     * @param measure the measure to process
     */
    public static void buildVoices (Measure measure)
    {
        if (logger.isFineEnabled()) {
            logger.fine(
                "buildVoices for " + measure.getContextString() + " chords=" +
                measure.getChords());
        }

        // To compute the maximum number of voices in this measure
        int         maxVoice = 0;

        // The 'activeChords' collection gathers the chords that are "active"
        // (not terminated) at the time slot being considered. Initially, it 
        // contains just the whole chords.
        List<Chord> activeChords = new ArrayList<Chord>(
            measure.getWholeChords());

        for (Chord chord : activeChords) {
            chord.setStartTime(0);
        }

        // Set of voices for the current slot
        SortedSet<Integer> slotVoices = new TreeSet<Integer>();

        // Process slot after slot, if any
        for (Slot slot : measure.getSlots()) {
            if (logger.isFineEnabled()) {
                logger.fine(
                    "Processing " + slot + " Active chords=" + activeChords);
            }

            slotVoices.clear();

            // Sort chords vertically  within the slot
            Collections.sort(slot.getChords(), chordComparator);

            if (logger.isFineEnabled()) {
                logger.fine("Slot chords=" + slot.getChords());
            }

            // Use the active chords before this slot to compute start time
            slot.computeStartTime(activeChords);

            // Separate the chords that end right at this slot and whose 
            // voices are available, from the ones that continue past the 
            // current slot with their voice.

            // Chords that are ending, with their voice available
            List<Chord> endingChords = new ArrayList<Chord>();

            // Chords that are ending, with voice not available (beam group)
            List<Chord> passingChords = new ArrayList<Chord>();

            for (Chord chord : activeChords) {
                BeamGroup group = chord.getBeamGroup();

                // Chord that finishes at the slot at hand
                // Make sure voice is really available
                Integer endTime = chord.getEndTime();

                if ((endTime != null) && (endTime <= slot.getStartTime())) {
                    if ((group == null) || (chord == group.getChords()
                                                          .last())) {
                        endingChords.add(chord);
                    } else {
                        passingChords.add(chord);

                        if (chord.getVoice() != null) {
                            slotVoices.add(chord.getVoice());
                        }
                    }
                } else {
                    // Chord (and its voice) continues past the slot at hand
                    if (chord.getVoice() != null) {
                        slotVoices.add(chord.getVoice());
                    }
                }
            }

            // 'starting Chords' are the chords that come to life at current slot
            List<Chord> startingChords = new ArrayList<Chord>(slot.getChords());
            Collections.sort(startingChords, chordComparator);

            for (Chord chord : startingChords) {
                if (chord.getVoice() != null) {
                    slotVoices.add(chord.getVoice());
                }
            }

            if (logger.isFineEnabled()) {
                logger.fine("endingChords=" + endingChords);
                logger.fine("passingChords=" + passingChords);
                logger.fine("startingChords=" + startingChords);
            }

            InjectionSolver solver = new InjectionSolver(
                startingChords.size(),
                endingChords.size() + startingChords.size(),
                new MyDistance(startingChords, endingChords));
            int[]           links = solver.solve();

            for (int i = 0; i < links.length; i++) {
                int index = links[i];

                // Map new chord to an ending chord?
                if (index < endingChords.size()) {
                    int voice = endingChords.get(index)
                                            .getVoice();
                    slot.getChords()
                        .get(i)
                        .setVoice(voice);
                    slotVoices.add(voice);
                }
            }

            // Assign remaining non-mapped chords, using 1st voice available
            if (logger.isFineEnabled()) {
                logger.fine("slot #" + slot.id + " slotVoices" + slotVoices);
            }

            assignVoices(startingChords, slotVoices);

            if (slotVoices.size() > 0) {
                maxVoice = Math.max(maxVoice, slotVoices.last());
            }

            // Purge collection of active chords for this slot
            // Add the chords that start with this slot
            activeChords.removeAll(endingChords);
            activeChords.removeAll(passingChords);
            activeChords.addAll(startingChords);
            Collections.sort(activeChords, chordComparator);
        }

        // Some processing is needed, even if we have no slot in this measure
        if (logger.isFineEnabled()) {
            logger.fine("last slotVoices" + slotVoices);
        }

        assignVoices(activeChords, slotVoices);

        if (slotVoices.size() > 0) {
            maxVoice = Math.max(maxVoice, slotVoices.last());
        }

        // Remember the maximum number of voices in that measure
        measure.setVoicesNumber(maxVoice);
    }

    //-----------//
    // compareTo //
    //-----------//
    /**
     * Compare this slot to another, as needed to insert slots in an ordered
     * collection
     *
     * @param other another slot
     * @return -1, 0 or +1, according to their relative abscissae
     */
    public int compareTo (Slot other)
    {
        return Integer.signum(getX() - other.getX());
    }

    //-----------------//
    // dumpSystemSlots //
    //-----------------//
    public static void dumpSystemSlots (System system)
    {
        // Dump all measure slots
        logger.fine(system.toString());

        for (TreeNode node : system.getParts()) {
            SystemPart part = (SystemPart) node;

            logger.fine(part.toString());

            for (TreeNode mn : part.getMeasures()) {
                Measure measure = (Measure) mn;

                logger.fine(measure.toString());

                for (Slot slot : measure.getSlots()) {
                    logger.fine(slot.toString());
                }
            }
        }
    }

    //---------------//
    // getChordAbove //
    //---------------//
    /**
     * Report the chord which is just above the given point in this slot
     *
     * @param point the given point
     * @return the chord above, or null
     */
    public Chord getChordAbove (SystemPoint point)
    {
        Chord chordAbove = null;

        // We look for the chord just above
        for (Chord chord : getChords()) {
            if (chord.getHeadLocation().y < point.y) {
                chordAbove = chord;
            } else {
                break;
            }
        }

        return chordAbove;
    }

    //---------------//
    // getChordBelow //
    //---------------//
    /**
     * Report the chord which is just below the given point in this slot
     *
     * @param point the given point
     * @return the chord below, or null
     */
    public Chord getChordBelow (SystemPoint point)
    {
        // We look for the chord just below
        for (Chord chord : getChords()) {
            if (chord.getHeadLocation().y > point.y) {
                return chord;
            }
        }

        // Not found
        return null;
    }

    //-----------//
    // getChords //
    //-----------//
    /**
     * Report the (ordered) collection of chords in this time slot
     *
     * @return the collection of chords
     */
    public List<Chord> getChords ()
    {
        return chords;
    }

    //-------------------//
    // getEmbracedChords //
    //-------------------//
    /**
     * Report the chords whose notes stand in the given vertical range
     *
     * @param top upper point of range
     * @param bottom lower point of range
     * @return the collection of chords, which may be empty
     */
    public List<Chord> getEmbracedChords (SystemPoint top,
                                          SystemPoint bottom)
    {
        List<Chord> chords = new ArrayList<Chord>();

        for (Chord chord : getChords()) {
            if (chord.isEmbracedBy(top, bottom)) {
                chords.add(chord);
            }
        }

        return chords;
    }

    public int getId ()
    {
        return id;
    }

    //---------------------//
    // getShortestDuration //
    //---------------------//
    /**
     * Since there may be several chords aligned (starting) in this slot, this
     * method reports the shortest duration among all chords of this slot. This
     * in turn defines the time offset of the following slot.
     *
     * @return the duration of the chord with shortest duration
     */
    public int getShortestDuration ()
    {
        int best = Integer.MAX_VALUE;

        for (Chord chord : getChords()) {
            if (best > chord.getDuration()) {
                best = chord.getDuration();
            }
        }

        return best;
    }

    //--------------//
    // getStartTime //
    //--------------//
    /**
     * Report the time offset of this time slot since beginning of the measure
     *
     * @return the time offset of this time slot.
     */
    public Integer getStartTime ()
    {
        return startTime;
    }

    //------//
    // getX //
    //------//
    /**
     * Report the abscissa of this slot
     *
     * @return the slot abscissa, wrt the containing system (and not measure)
     */
    public int getX ()
    {
        if (x == null) {
            Population population = new Population();

            for (Glyph glyph : glyphs) {
                population.includeValue(
                    measure.getSystem()
                           .toSystemPoint(glyph.getCenter()).x);
            }

            if (population.getCardinality() > 0) {
                x = (int) Math.rint(population.getMeanValue());
            }
        }

        return x;
    }

    //---------------//
    // isAlignedWith //
    //---------------//
    /**
     * Check whether a system point is roughly aligned with this slot instance.
     *
     * @param sysPt the system point to check
     * @return true if aligned
     */
    public boolean isAlignedWith (SystemPoint sysPt)
    {
        return Math.abs(sysPt.x - getX()) <= measure.getScale()
                                                    .toUnits(constants.maxDx);
    }

    public void setId (int id)
    {
        this.id = id;
    }

    //--------------//
    // setStartTime //
    //--------------//
    /**
     * Assign the time startTime, since the beginning of the measure, for all
     * chords in this time slot
     *
     * @param startTime time startTime using {@link omr.score.Note#QUARTER_DURATION}
     * value
     */
    public void setStartTime (int startTime)
    {
        if (this.startTime == null) {
            if (logger.isFineEnabled()) {
                logger.fine(
                    "setStartTime " + startTime + " for Slot #" + getId());
            }

            this.startTime = startTime;

            // Assign to all chords of this slot first
            for (Chord chord : getChords()) {
                chord.setStartTime(startTime);
            }

            // Then, extend this information through the beamed chords if any
            for (Chord chord : getChords()) {
                BeamGroup group = chord.getBeamGroup();

                if (group != null) {
                    group.computeStartTimes();
                }
            }
        } else {
            if (!this.startTime.equals(startTime)) {
                getChords()
                    .get(0)
                    .addError(
                    "Reassigning startTime from " +
                    Note.quarterValueOf(this.startTime) + " to " +
                    Note.quarterValueOf(startTime) + " in " + this);
            }
        }
    }

    //----------//
    // toString //
    //----------//
    @Override
    public String toString ()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{Slot#")
          .append(id);

        sb.append(" x=")
          .append(getX());

        if (startTime != null) {
            sb.append(" start=")
              .append(Note.quarterValueOf(startTime));
        }

        sb.append(" glyphs=[");

        for (Glyph glyph : glyphs) {
            sb.append("#")
              .append(glyph.getId())
              .append("/")
              .append(glyph.getShape())
              .append(" ");
        }

        sb.append("]");
        sb.append("}");

        return sb.toString();
    }

    //----------//
    // populate //
    //----------//
    /**
     * Populate a slot with this note glyph
     *
     * @param glyph a chord-relevant glyph (rest, note or notehead)
     * @param measure the containing measure
     */
    static void populate (Glyph       glyph,
                          Measure     measure,
                          SystemPoint sysPt)
    {
        //        if (logger.isFineEnabled()) {
        //            logger.fine("Populating slot with " + glyph);
        //        }

        // Special case for whole rests: they don't belong to any time slot,
        // and they duration is the measure duration
        if (glyph.getShape() == Shape.WHOLE_REST) {
            measure.addWholeChord(glyph);
        } else {
            // First look for a suitable slot
            for (Slot slot : measure.getSlots()) {
                if (slot.isAlignedWith(sysPt)) {
                    slot.addGlyph(glyph);

                    return;
                }
            }

            // No compatible slot, create a brand new one
            Slot slot = new Slot(measure);
            slot.addGlyph(glyph);
            measure.getSlots()
                   .add(slot);
        }
    }

    /**
     * Assign available voices to the chords that have yet no voice assigned
     *
     * @param chords the collection of chords to process
     * @param voices the set of voices, both an input (it may be already
     *                partly filled) and an output
     */
    private static void assignVoices (Collection<Chord>  chords,
                                      SortedSet<Integer> voices)
    {
        try {
            // Assign remaining non-mapped chords, using 1st voice available
            for (Chord chord : chords) {
                // Process only the chords that have no voice assigned yet
                if (chord.getVoice() == null) {
                    int voice;

                    if (voices.isEmpty()) {
                        voice = 1;
                    } else {
                        // Use the first voice available
                        for (voice = 1; voice <= voices.last(); voice++) {
                            if (!voices.contains(voice)) {
                                break; // The voice is available
                            }
                        }
                    }

                    chord.setVoice(voice);
                    voices.add(voice);
                }
            }

            if (logger.isFineEnabled()) {
                for (Chord chord : chords) {
                    logger.fine(".. " + chord);
                }
            }
        } catch (Exception ex) {
            logger.warning("BINGO voices=" + voices);
        }
    }

    //------------------//
    // computeStartTime //
    //------------------//
    private void computeStartTime (Collection<Chord> activeChords)
    {
        // Based on the active chords before this slot,
        // Determine the next expiration time, which governs this slot
        int slotTime = Integer.MAX_VALUE;

        for (Chord chord : activeChords) {
            // Skip the "whole" chords which return a null duration
            Integer endTime = chord.getEndTime();

            if ((endTime != null) && (endTime < slotTime)) {
                slotTime = endTime;
            }
        }

        if (slotTime == Integer.MAX_VALUE) {
            slotTime = 0;
        }

        if (logger.isFineEnabled()) {
            logger.fine("slotTime=" + Note.quarterValueOf(slotTime));
        }

        setStartTime(slotTime);
    }

    //--------------//
    // getStemChord //
    //--------------//
    /**
     * Given a stem, look up for a slot that already contains it, otherwise
     * create a brand new slot to host the stem.
     *
     * @param stem the stem to look up
     * @return the existing/created slot that contains the stem
     */
    private Chord getStemChord (Glyph stem)
    {
        // Check we don't already have this stem in a chord
        for (Chord chord : chords) {
            if (chord.getStem() == stem) {
                return chord;
            }
        }

        // Not found, let's create it
        Chord chord = new Chord(measure);
        chords.add(chord);
        chord.setStem(stem);
        stem.setTranslation(chord);

        return chord;
    }

    //~ Inner Classes ----------------------------------------------------------

    //-----------//
    // Constants //
    //-----------//
    private static final class Constants
        extends ConstantSet
    {
        /**
         * Maximum horizontal distance between a slot and a glyph candidate
         */
        Scale.Fraction maxDx = new Scale.Fraction(
            1.25,
            "Maximum horizontal distance between a slot and a glyph candidate");
    }

    //------------//
    // MyDistance //
    //------------//
    private static final class MyDistance
        implements InjectionSolver.Distance
    {
        private static final int  NO_LINK = 20;
        private static final int  STAFF_DIFF = 40;
        private final List<Chord> news;
        private final List<Chord> olds;

        public MyDistance (List<Chord> news,
                           List<Chord> olds)
        {
            this.news = news;
            this.olds = olds;
        }

        public int getDistance (int in,
                                int ip)
        {
            // No link to an old chord
            if (ip >= olds.size()) {
                return NO_LINK;
            }

            Chord newChord = news.get(in);
            Chord oldChord = olds.get(ip);

            if (newChord.getStaff() != oldChord.getStaff()) {
                return STAFF_DIFF;
            } else {
                int dy = Math.abs(
                    newChord.getHeadLocation().y -
                    oldChord.getHeadLocation().y) / ScoreConstants.INTER_LINE;
                int dStem = Math.abs(
                    newChord.getStemDir() - oldChord.getStemDir());

                return dy + (2 * dStem);
            }
        }
    }
}
