//----------------------------------------------------------------------------//
//                                                                            //
//                        G l y p h E v a l u a t o r                         //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Herve Bitteur 2000-2010. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.glyph;

import omr.WellKnowns;

import omr.constant.ConstantSet;

import omr.glyph.facets.Glyph;

import omr.log.Logger;

import omr.math.Moments;
import omr.math.NeuralNetwork;

import omr.sheet.Scale;
import omr.sheet.SystemInfo;

import omr.util.ClassUtil;
import omr.util.Predicate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;

/**
 * Class <code>GlyphEvaluator</code> is an abstract class that gathers data and
 * processing common to any evaluator working on glyph characteristics to infer
 * glyph shape.
 *
 * <p> <img src="doc-files/GlyphEvaluator.jpg" />
 *
 * @author Hervé Bitteur
 */
public abstract class GlyphEvaluator
{
    //~ Static fields/initializers ---------------------------------------------

    /** Specific application parameters */
    private static final Constants constants = new Constants();

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(GlyphEvaluator.class);

    /** Number of useful moments : {@value} */
    public static final int inMoments = 10;

    /**
     * Number of useful input parameters : nb of useful moments +
     * stemNumber, isWithLedger = {@value}
     */
    public static final int paramCount = inMoments + 2;

    /** Number of shapes to differentiate */
    public static final int shapeCount = Shape.LAST_PHYSICAL_SHAPE.ordinal() +
                                         1;

    /** A special evaluation array, used to report NOISE */
    static final Evaluation[] noiseEvaluations = {
                                                     new Evaluation(
        Shape.NOISE,
        0d)
                                                 };

    /**
     * An Evaluation comparator in increasing order, where smaller doubt value
     * means better interpretation
     */
    protected static final Comparator<Evaluation> comparator = new Comparator<Evaluation>() {
        public int compare (Evaluation e1,
                            Evaluation e2)
        {
            if (e1.doubt < e2.doubt) {
                return -1;
            }

            if (e1.doubt > e2.doubt) {
                return +1;
            }

            return 0;
        }
    };


    //~ Enumerations -----------------------------------------------------------

    /** Describes the various modes for starting the training of an evaluator */
    public static enum StartingMode {
        //~ Enumeration constant initializers ----------------------------------


        /** Start with the current values */
        INCREMENTAL,
        /** Start from scratch, with new initial values */
        SCRATCH;
    }

    //~ Instance fields --------------------------------------------------------

    /** The glyph checker for additional specific checks */
    protected GlyphChecker glyphChecker = GlyphChecker.getInstance();

    //~ Methods ----------------------------------------------------------------

    //---------//
    // getName //
    //---------//
    /**
     * Report the name of this evaluator
     *
     * @return the evaluator declared name
     */
    public abstract String getName ();

    //------//
    // dump //
    //------//
    /**
     * Dump the internals of the evaluator
     */
    public abstract void dump ();

    //-------------//
    // isBigEnough //
    //-------------//
    /**
     * Use a threshold on glyph weight, to tell if the provided glyph is just
     * {@link Shape#NOISE}, or a real glyph
     *
     * @param glyph the glyph to be checked
     * @return true if not noise, false otherwise
     */
    public static boolean isBigEnough (Glyph glyph)
    {
        return glyph.getNormalizedWeight() >= constants.minWeight.getValue();
    }

    //-------------------//
    // getParameterIndex //
    //-------------------//
    /**
     * Report the index of parameters for the provided label
     * @param label the provided label
     * @return the parameter index
     */
    public static int getParameterIndex (String label)
    {
        return LabelsHolder.indices.get(label);
    }

    //--------------------//
    // getParameterLabels //
    //--------------------//
    /**
     * Report the parameters labels
     *
     * @return the array of parameters labels
     */
    public static String[] getParameterLabels ()
    {
        return LabelsHolder.labels;
    }

    //-----------//
    // feedInput //
    //-----------//
    /**
     * Prepare the evaluator input, by picking up some characteristics of the
     * glyph (some of its moments, and some info on surroundings)
     *
     * @param glyph the glyph to be evaluated
     * @return the filled input array
     */
    public static double[] feedInput (Glyph glyph)
    {
        double[] ins = new double[paramCount];

        // We take all the first moments
        Double[] k = glyph.getMoments()
                          .getValues();

        for (int i = 0; i < inMoments; i++) {
            ins[i] = k[i];
        }

        // We append ledger presence and stem count)
        int i = inMoments;
        /* 10 */ ins[i++] = boolAsDouble(glyph.isWithLedger());
        /* 11 */ ins[i++] = glyph.getStemNumber();

        ////////* 12 */ ins[i++] = glyph.getPitchPosition();

        // We skip moments 17 & 18 (xMean and yMean) ???
        return ins;
    }

    //-------------------//
    // getRawEvaluations //
    //-------------------//
    /**
     * Run the evaluator with the specified glyph, and return a prioritized
     * collection of interpretations (ordered from best to worst) with no
     * additional check
     *
     * @param glyph the glyph to be examined
     *
     * @return the ordered best evaluations
     */
    public abstract Evaluation[] getRawEvaluations (Glyph glyph);

    //-----------------------//
    // getAllowedEvaluations //
    //-----------------------//
    /**
     * Run the evaluator with the specified glyph as well as specific checks,
     * and return only the shapes that are not flagged as forbidden for this
     * glyph.
     *
     * @param glyph the glyph to be examined
     *
     * @return the ordered best checked and allowed evaluations
     */
    public Evaluation[] getAllowedEvaluations (Glyph      glyph,
                                               SystemInfo system)
    {
        List<Evaluation> kept = new ArrayList<Evaluation>();

        for (Evaluation eval : getSuccessfulEvaluations(glyph, system)) {
            if (!glyph.isShapeForbidden(eval.shape)) {
                kept.add(eval);
            }
        }

        return kept.toArray(new Evaluation[kept.size()]);
    }

    //-------------------------//
    // getAnnotatedEvaluations //
    //-------------------------//
    /**
     * Use specific checks to annotate the raw evaluations produced by the
     * evaluator
     *
     * @param glyph the glyph to be examined
     *
     * @return the ordered annotated evaluations
     */
    public Evaluation[] getAnnotatedEvaluations (Glyph      glyph,
                                                 SystemInfo system)
    {
        double[]     ins = feedInput(glyph);
        Evaluation[] evals = getRawEvaluations(glyph);

        for (Evaluation eval : evals) {
            glyphChecker.annotate(system, eval, glyph, ins);
        }

        return evals;
    }

    //    //--------------------------//
    //    // getRawAllowedEvaluations //
    //    //--------------------------//
    //    /**
    //     * Run the evaluator with the specified glyph,
    //     * and return only the shapes that are not flagged as forbidden for this
    //     * glyph.
    //     *
    //     * @param glyph the glyph to be examined
    //     *
    //     * @return the ordered best allowed evaluations
    //     */
    //    public Evaluation[] getRawAllowedEvaluations (Glyph glyph)
    //    {
    //        List<Evaluation> kept = new ArrayList<Evaluation>();
    //
    //        for (Evaluation eval : getRawEvaluations(glyph)) {
    //            if (!glyph.isShapeForbidden(eval.shape)) {
    //                kept.add(eval);
    //            }
    //        }
    //
    //        return kept.toArray(new Evaluation[kept.size()]);
    //    }

    //---------//
    // marshal //
    //---------//
    /**
     * Store the engine in XML format, always as a custom file
     */
    public void marshal ()
    {
        final File   file = getCustomFile();
        OutputStream os = null;

        try {
            os = new FileOutputStream(file);
            marshal(os);
            logger.info("Engine marshalled to " + file);
        } catch (FileNotFoundException ex) {
            logger.warning("Could not find file " + file);
        } catch (IOException ex) {
            logger.warning("IO error on file " + file);
        } catch (JAXBException ex) {
            logger.warning("Error marshalling engine to " + file);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    //------//
    // stop //
    //------//
    /**
     * Stop the on-going training. By default, this is a no-op
     */
    public void stop ()
    {
    }

    //-------//
    // train //
    //-------//
    /**
     * Here we train the evaluator "ab initio", based on the set of known glyphs
     * accumulated in the previous runs.
     *
     * @param base the collection of glyphs to retrain the evaluator
     * @param monitor a monitoring interface
     * @param mode specify the starting mode of the training session
     */
    public abstract void train (Collection<Glyph> base,
                                Monitor           monitor,
                                StartingMode      mode);

    //--------------------------//
    // getSuccessfulEvaluations //
    //--------------------------//
    /**
     * Return the annotated & non-failed evaluations, from best to worst
     *
     * @param glyph the glyph to be examined
     *
     * @return the ordered best filtered evaluations
     */
    public Evaluation[] getSuccessfulEvaluations (Glyph      glyph,
                                                  SystemInfo system)
    {
        List<Evaluation> kept = new ArrayList<Evaluation>();

        for (Evaluation eval : getAnnotatedEvaluations(glyph, system)) {
            if (eval.failure == null) {
                kept.add(eval);
            }
        }

        return kept.toArray(new Evaluation[kept.size()]);
    }

    //------------//
    // topRawVote //
    //------------//
    /**
     * Report the best evaluation for the provided glyph, below a maximum doubt
     * value, among the shapes (non checked, but allowed) that match
     * the provided predicate
     * @param glyph the provided glyph
     * @param maxDoubt the maximum doubt to be accepted
     * @param predicate filter for acceptable shapes
     * @return the best acceptable evaluation, or null
     */
    public Evaluation topRawVote (Glyph            glyph,
                                  double           maxDoubt,
                                  Predicate<Shape> predicate)
    {
        return bestOf(getRawEvaluations(glyph), maxDoubt, predicate);
    }

    //---------//
    // topVote //
    //---------//
    /**
     * Report the best evaluation for the provided glyph, below a maximum doubt
     * value, among the shapes that match the provided predicate
     * @param glyph the provided glyph
     * @param maxDoubt the maximum doubt to be accepted
     * @param predicate filter for acceptable shapes
     * @return the best acceptable evaluation, or null
     */
    public Evaluation topVote (Glyph            glyph,
                               double           maxDoubt,
                               SystemInfo       system,
                               Predicate<Shape> predicate)
    {
        return bestOf(
            getAllowedEvaluations(glyph, system),
            maxDoubt,
            predicate);
    }

    //------//
    // vote //
    //------//
    /**
     * Run the evaluator with the specified glyph, and infer a shape.
     *
     * @param glyph the glyph to be examined
     * @param maxDoubt the maximum doubt to be accepted
     * @return the best acceptable evaluation, or null
     */
    public Evaluation vote (Glyph      glyph,
                            double     maxDoubt,
                            SystemInfo system)
    {
        Evaluation[] evaluations = getAllowedEvaluations(glyph, system);

        if ((evaluations.length > 0) && (evaluations[0].doubt <= maxDoubt)) {
            return evaluations[0];
        } else {
            return null;
        }
    }

    //-------------//
    // getFileName //
    //-------------//
    /**
     * Report the simple file name, including extension but excluding parent,
     * which contains the marshalled data of the evaluator
     * @return the file name
     */
    protected abstract String getFileName ();

    //---------//
    // marshal //
    //---------//
    protected abstract void marshal (OutputStream os)
        throws FileNotFoundException, IOException, JAXBException;

    //---------------//
    // getCustomFile //
    //---------------//
    /**
     * Report the custom file used to store or load the internal evaluator data
     *
     * @return the evaluator custom backup file
     */
    protected File getCustomFile ()
    {
        // The custom file, if any, is located in the configuration folder
        return new File(WellKnowns.CONFIG_FOLDER, getFileName());
    }

    //---------------//
    // getDefaultUrl //
    //---------------//
    /**
     * Report the name of the resource used to retrieve the evaluator marshalled
     * data from the distribution resource
     * @return the data resource name
     */
    protected String getDefaultUrl ()
    {
        return "/config/" + getFileName();
    }

    //-----------//
    // unmarshal //
    //-----------//
    /**
     * Unmarshal the evaluation engine from the most suitable backup, which
     * is first a custom file, and second the distribution resource.
     * @return the engine, or null if failed
     */
    protected Object unmarshal ()
    {
        InputStream input = ClassUtil.getProperStream(
            WellKnowns.CONFIG_FOLDER,
            getFileName());

        return unmarshal(input, getFileName());
    }

    //-----------//
    // unmarshal //
    //-----------//
    /**
     * The specific unmarshalling method which builds a suitable engine
     * @param is the input stream to read
     * @return the newly built evaluation engine
     * @throws JAXBException
     * @throws IOException
     */
    protected abstract Object unmarshal (InputStream is)
        throws JAXBException, IOException;

    //--------//
    // bestOf //
    //--------//
    private Evaluation bestOf (Evaluation[]     evaluations,
                               double           maxDoubt,
                               Predicate<Shape> predicate)
    {
        // Check if a suitable shape appears in the top evaluations
        for (Evaluation evaluation : evaluations) {
            if (evaluation.doubt > maxDoubt) {
                break;
            }

            if ((predicate == null) || predicate.check(evaluation.shape)) {
                return evaluation;
            }
        }

        return null;
    }

    //--------------//
    // boolAsDouble //
    //--------------//
    private static double boolAsDouble (boolean b)
    {
        if (b) {
            return 1d;
        } else {
            return 0d;
        }
    }

    //-----------//
    // unmarshal //
    //-----------//
    private Object unmarshal (InputStream is,
                              String      name)
    {
        if (is == null) {
            logger.warning(
                "No data stream for " + getName() + " engine as " + name);
        } else {
            try {
                Object engine = unmarshal(is);
                is.close();

                return engine;
            } catch (FileNotFoundException ex) {
                logger.warning("Cannot find or read " + name);
            } catch (IOException ex) {
                logger.warning("IO error on " + name);
            } catch (JAXBException ex) {
                logger.warning("Error unmarshalling evaluator from " + name);
            }
        }

        return null;
    }

    //~ Inner Interfaces -------------------------------------------------------

    //---------//
    // Monitor //
    //---------//
    /**
     * Interface <code>Monitor</code> specifies a general monitoring interface
     * to pass information about the behavior of evaluators.
     */
    public static interface Monitor
        extends NeuralNetwork.Monitor
    {
        //~ Methods ------------------------------------------------------------

        /**
         * Entry called when a glyph is processed
         * @param glyph
         */
        void glyphProcessed (Glyph glyph);
    }

    //~ Inner Classes ----------------------------------------------------------

    //-----------//
    // Constants //
    //-----------//
    private static final class Constants
        extends ConstantSet
    {
        //~ Instance fields ----------------------------------------------------

        Scale.AreaFraction minWeight = new Scale.AreaFraction(
            0.08,
            "Minimum normalized weight to be considered not a noise");
    }

    //--------------//
    // LabelsHolder //
    //--------------//
    /** Descriptive strings for glyph characteristics */
    private static class LabelsHolder
    {
        //~ Static fields/initializers -----------------------------------------

        public static final Map<String, Integer> indices = new HashMap<String, Integer>();
        public static final String[]             labels = new String[paramCount];

        static {
            // We take all the first moments
            for (int i = 0; i < inMoments; i++) {
                labels[i] = Moments.getLabel(i);
            }

            // We append flags and step position
            int i = inMoments;
            /* 10 */ labels[i++] = "ledger";
            /* 11 */ labels[i++] = "stemNb";

            ////* 12 */ labels[i++] = "pitch";
            for (int j = 0; j < labels.length; j++) {
                indices.put(labels[j], j);
            }
        }
    }
}
