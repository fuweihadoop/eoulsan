/*
 *                      Nividic development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  If you do not have a copy,
 * see:
 *
 *      http://www.gnu.org/copyleft/lesser.html
 *
 * Copyright for this code is held jointly by the microarray platform
 * of the École Normale Supérieure and the individual authors.
 * These should be listed in @author doc comments.
 *
 * For more information on the Nividic project and its aims,
 * or to join the Nividic mailing list, visit the home page
 * at:
 *
 *      http://www.transcriptome.ens.fr/nividic
 *
 */

package fr.ens.transcriptome.eoulsan.steps.expression;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fr.ens.transcriptome.eoulsan.bio.BadBioEntryException;
import fr.ens.transcriptome.eoulsan.bio.io.GFFReader;
import fr.ens.transcriptome.eoulsan.util.FileUtils;

/**
 * This class define a class that is use to do fast search on exons and genes of
 * chromosomes.
 * @author Laurent Jourdren
 */
public class TranscriptAndExonFinder {

  private Map<String, Transcript> transcripts;
  private Map<String, ChromosomeZone> chrZoneMap;

  public static final class Transcript implements Serializable {

    private String name;
    private String type;
    private String chromosome;
    private int count;
    private int start = Integer.MAX_VALUE;
    private int end = Integer.MIN_VALUE;
    private char strand;
    private int length;

    //
    // Getter
    //

    /**
     * Get the name of the gene.
     * @return the name of the gene
     */
    public String getName() {

      return this.name;
    }

    /**
     * Get the type of the gene.
     * @return the type of the gene
     */
    public String getType() {

      return this.type;
    }

    /**
     * Get the number of exons
     * @return the number of exons
     */
    public int getCount() {
      return this.count;
    }

    /**
     * Get the chromosome of the gene.
     * @return the chromosome of the gene
     */
    public String getChromosome() {
      return this.chromosome;
    }

    /**
     * Get the start of the gene.
     * @return the start postion of the gene
     */
    public int getStart() {
      return this.start;
    }

    /**
     * Get the end position of the gene.
     * @return the end position of the gene
     */
    public int getEnd() {
      return this.end;
    }

    /**
     * Get the strand of the gene.
     * @return the strand of the gene
     */
    public char getStrand() {
      return this.strand;
    }

    /**
     * Get the length of the gene.
     * @return the length of the gene
     */
    public int getLength() {
      return this.length;
    }

    //
    // Setters
    //

    /**
     * Set the type of the gene
     * @param type The type of the gene
     */
    private void setType(final String type) {

      this.type = type;
    }

    /**
     * Set the start position
     * @param start start position
     */
    private void setStartIfMin(final int start) {

      this.start = Math.min(this.start, start);
    }

    /**
     * Set the stop position
     * @param stop stop position
     */
    private void setEndIfMax(final int stop) {

      this.end = Math.max(this.end, stop);
    }

    /**
     * Add an exon.
     * @param exon Exon to add
     */
    public void addExon(final Exon exon) {

      setStartIfMin(exon.getStart());
      setEndIfMax(exon.getEnd());
      this.count++;
      this.length += exon.getLength();

      if (strand == 0)
        this.strand = exon.getStrand();

      if (this.strand != exon.getStrand())
        throw new InvalidParameterException(
            "The strand is not the same that the gene");

      if (this.chromosome == null)
        this.chromosome = exon.getChromosome();

      if (!this.chromosome.equals(exon.getChromosome()))
        throw new InvalidParameterException(
            "The chromosome is not the same that the gene");

    }

    public String toString() {

      return "[c=" + count + " s=" + start + " e=" + end + "]";
    }

    //
    // Constructor
    //

    /**
     * Public constructor.
     * @param name name of the the gene
     */
    public Transcript(final String name) {

      if (name == null)
        throw new NullPointerException("The name of the gene is null.");
      this.name = name;
    }

  }

  /**
   * This class define an exon.
   * @author Laurent Jourdren
   */
  public static final class Exon implements Serializable {

    private String chromosome;
    private int start;
    private int end;
    private char strand;
    private String parentId;

    //
    // Getters
    //

    /**
     * Get the chromosome of the exon.
     * @return the chromosome of the exon
     */
    public String getChromosome() {

      return this.chromosome;
    }

    /**
     * Get the start position of the exon.
     * @return the start position of the exon
     */
    public int getStart() {

      return this.start;
    }

    /**
     * Get the end position of the exon.
     * @return the end position of the exon
     */
    public int getEnd() {

      return this.end;
    }

    /**
     * Get the strand of the exon.
     * @return a char with the strand of the exon
     */
    public char getStrand() {

      return this.strand;
    }

    /**
     * Get the parent identifier of the exon.
     * @return the parentId of the exon
     */
    public String getParentId() {

      return this.parentId;
    }

    /**
     * Get the length of the Exon.
     * @return the length of the exon
     */
    public int getLength() {

      return this.end - this.start + 1;
    }

    /**
     * Test if a sequence is in the ORF
     * @param start start position of the ORF
     * @param end end position of the ORF
     * @return true if the sequence is in the ORF
     */
    public final boolean include(final int start, final int end) {

      return start >= this.start && end <= this.end;
    }

    /**
     * Test if a sequence is in the ORF
     * @param start start position of the ORF
     * @param end end position of the ORF
     * @return true if the sequence is in the ORF
     */
    public final boolean intersect(final int start, final int end) {

      return (start >= this.start && start <= this.end)
          || (end >= this.start && end <= this.end)
          || (start < this.start && end > this.end);
    }

    /**
     * Overide toString()
     * @return a String with the start and end position of the ORF
     */
    public String toString() {

      return chromosome
          + " [" + start + "-" + end + "]" + strand + " " + parentId;
    }

    //
    // Constructor
    //

    /**
     * Public constructor.
     * @param start Start position of the ORF
     * @param end End position of the ORF
     * @param name Name of the ORF
     * @param strand strand
     */
    public Exon(final String chromosone, final int start, final int end,
        final char strand, final String parentId) {

      if (start < 1 || end < start)
        throw new InvalidParameterException();

      this.chromosome = chromosone;
      this.start = start;
      this.end = end;
      this.strand = strand;
      this.parentId = parentId;
    }

  }

  /**
   * This class define a zone in a ChromosomeZone object
   * @author Laurent Jourdren
   */
  private static final class Zone implements Serializable {

    private int start;
    private int end;

    private Set<Exon> _exons;
    private Exon _exon;
    private int exonCount;

    /**
     * Add an exon to the zone.
     * @param exon Exon to add
     */
    public void addExon(final Exon exon) {

      if (exon == null)
        return;

      if (exonCount == 0) {
        this._exon = exon;
        this.exonCount = 1;
      } else {

        if (exonCount == 1) {

          if (exon == this._exon)
            return;

          this._exons = new HashSet<Exon>();
          this._exons.add(this._exon);
          this._exon = null;
        }

        this._exons.add(exon);
        this.exonCount = this._exons.size();
      }
    }

    /**
     * Add exons to the zone.
     * @param exons Exons to add
     */
    public void addExons(final Set<Exon> exons) {

      if (exons == null)
        return;

      final int len = exons.size();

      if (len == 0)
        return;

      if (len == 1) {
        this._exon = exons.iterator().next();
        this.exonCount = 1;
      } else {
        this._exons = new HashSet<Exon>(exons);
        this.exonCount = len;
      }

    }

    /**
     * Get the exons of the zone
     * @return a set with the exons of the zone
     */
    public Set<Exon> getExons() {

      if (this.exonCount == 0)
        return null;

      if (this.exonCount == 1)
        return Collections.singleton(this._exon);

      return this._exons;
    }

    public int compareTo(final int position) {

      if (position >= this.start && position <= this.end)
        return 0;

      return position < this.start ? -1 : 1;
    }

    public String toString() {

      Set<String> r = new HashSet<String>();
      if (getExons() != null)
        for (Exon e : getExons())
          r.add(e.parentId);

      return "[" + this.start + "," + this.end + "," + r + "]";
    }

    //
    // Constructor
    //

    /**
     * Constructor that create a zone
     * @param start start position of the zone
     * @param end end postion of the zone
     */
    public Zone(final int start, final int end) {

      this.start = start;
      this.end = end;

    }

    /**
     * Constructor that create a zone
     * @param start start position of the zone
     * @param end end postion of the zone
     * @param exons of the zone
     */
    public Zone(final int start, final int end, final Set<Exon> exons) {

      this(start, end);
      addExons(exons);
    }

  }

  public static final class ChromosomeZone implements Serializable {

    private int length = 0;
    private final List<Zone> zones = new ArrayList<Zone>();

    private final Zone get(int pos) {

      return this.zones.get(pos);
    }

    private final void add(final Zone z) {

      this.zones.add(z);
    }

    private final void add(final int pos, final Zone z) {

      this.zones.add(pos, z);
    }

    private int findIndexPos(final int pos) {

      if (pos < 1 || pos > this.length)
        return -1;

      int minIndex = 0;
      int maxIndex = zones.size() - 1;
      int index = 0;

      while (true) {

        final int diff = maxIndex - minIndex;
        index = minIndex + diff / 2;

        if (diff == 1) {

          if (get(minIndex).compareTo(pos) == 0)
            return minIndex;
          if (get(maxIndex).compareTo(pos) == 0)
            return maxIndex;

          assert (false);
        }

        final Zone z = get(index);

        final int comp = z.compareTo(pos);
        if (comp == 0)
          return index;

        if (comp < 0)
          maxIndex = index;
        else
          minIndex = index;
      }
    }

    private Zone splitZone(final Zone zone, final int pos) {

      final Zone result = new Zone(pos, zone.end, zone.getExons());
      zone.end = pos - 1;

      return result;
    }

    public void addExon(final Exon exon) {

      // Create an empty zone if the exon is after the end of the chromosome
      if (exon.end > this.length) {
        add(new Zone(this.length + 1, exon.end));
        this.length = exon.end;
      }

      final int indexStart = findIndexPos(exon.start);
      final int indexEnd = findIndexPos(exon.end);

      final Zone z1 = get(indexStart);
      final Zone z1b;
      final int count1b;

      if (z1.start == exon.start) {
        z1b = z1;
        count1b = 0;
      } else {
        z1b = splitZone(z1, exon.start);
        count1b = 1;
      }

      // Same index
      if (indexStart == indexEnd) {

        if (z1b.end == exon.end) {
          z1b.addExon(exon);
        } else {

          final Zone z1c = splitZone(z1b, exon.end + 1);
          add(indexStart + 1, z1c);
        }

        if (z1 != z1b) {
          z1b.addExon(exon);
          add(indexStart + 1, z1b);

        } else
          z1.addExon(exon);

      } else {

        final Zone z2 = get(indexEnd);
        final Zone z2b;

        if (z2.end != exon.end)
          z2b = splitZone(z2, exon.end + 1);
        else
          z2b = z2;

        if (z1 != z1b)
          add(indexStart + 1, z1b);
        if (z2 != z2b)
          add(indexEnd + 1 + count1b, z2b);

        for (int i = indexStart + count1b; i <= indexEnd + count1b; i++)
          get(i).addExon(exon);

      }

    }

    public Set<Exon> findExons(final int start, final int stop) {

      final int indexStart = findIndexPos(start);
      final int indexEnd = findIndexPos(stop);

      if (indexStart == -1)
        return null;

      final int from = indexStart;
      final int to = indexEnd == -1 ? this.zones.size() - 1 : indexEnd;

      Set<Exon> result = null;

      for (int i = from; i <= to; i++) {

        final Set<Exon> r = get(i).getExons();
        if (r != null) {

          for (Exon e : r)
            if (e.intersect(start, stop)) {

              if (result == null)
                result = new HashSet<Exon>();

              result.add(e);
            }

        }
      }

      return result;
    }

    //
    // Constructor
    //

    public ChromosomeZone() {
    }
  }

  private void populateMapsFromGFFFile(final InputStream is,
      final String expressionType) throws IOException, BadBioEntryException {

    final GFFReader reader = new GFFReader(is);

    this.chrZoneMap = new HashMap<String, ChromosomeZone>();
    this.transcripts = new HashMap<String, Transcript>();

    final Map<String, String> idType = new HashMap<String, String>();

    while (reader.readEntry()) {

      final String type = reader.getType();

      if (reader.isAttribute("ID"))
        idType.put(reader.getAttributeValue("ID"), type);

      if (expressionType.equals(type)) {

        final String chr = reader.getSeqId();
        final int start = reader.getStart();
        final int stop = reader.getEnd();
        final char strand = reader.getStrand();

        String parentId = null;

        if (reader.isAttribute("modeleid"))
          parentId = reader.getAttributeValue("modeleid");
        else if (reader.isAttribute("single"))
          parentId = reader.getAttributeValue("single");
        else if (reader.isAttribute("Parent"))
          parentId = reader.getAttributeValue("Parent");
        else if (reader.isAttribute("PARENT"))
          parentId = reader.getAttributeValue("PARENT");

        if (parentId == null)
          continue;

        // Create Exon
        final Exon exon = new Exon(chr, start, stop, strand, parentId);

        //
        // Populate exonModeleRangeMap
        //

        final Transcript epr;

        if (this.transcripts.containsKey(parentId))
          epr = this.transcripts.get(parentId);
        else {
          epr = new Transcript(parentId);
          this.transcripts.put(parentId, epr);
        }

        epr.addExon(exon);

        //
        // Populate parentExon
        //

        if (this.chrZoneMap.containsKey(chr))
          this.chrZoneMap.get(chr).addExon(exon);
        else {
          final ChromosomeZone cz = new ChromosomeZone();
          cz.addExon(exon);
          this.chrZoneMap.put(chr, cz);
        }

      }
    }

    // Close reader
    reader.close();

    // Set the types of genes
    for (Map.Entry<String, Transcript> e : this.transcripts.entrySet())
      e.getValue().setType(idType.get(e.getKey()));
  }

  /**
   * Find exons that matches with an alignment.
   * @param chr chromosome
   * @param start start position of the alignment
   * @param end end position of the alignment
   * @return a set of exons that matches with the alignment
   */
  public Set<Exon> findExons(final String chr, final int start, final int end) {

    if (chr == null || start < 1 || start > end)
      return null;

    final ChromosomeZone cz = this.chrZoneMap.get(chr);

    if (cz == null)
      return null;

    return cz.findExons(start, end);
  }

  /**
   * Get a transcript
   * @param transcriptName name of the transcript
   * @return the Gene if exists or null
   */
  public Transcript getTranscript(final String transcriptName) {

    return this.transcripts.get(transcriptName);
  }

  /**
   * Get a set with transcripts identifiers.
   * @return a set of strings with identifiers
   */
  public Set<String> getTranscriptsIds() {

    return this.transcripts.keySet();
  }

  //
  // Save
  //

  /**
   * Save the annotation.
   * @param os Output stream
   */
  public void save(final OutputStream os) throws IOException {

    final ObjectOutputStream oos = new ObjectOutputStream(os);
    oos.writeObject(this.transcripts);
    oos.writeObject(this.chrZoneMap);
    oos.close();
  }

  /**
   * Save the annotation.
   * @param outputFile Output file
   */
  public void save(final File outputFile) throws FileNotFoundException,
      IOException {

    save(FileUtils.createOutputStream(outputFile));
  }

  //
  // Load
  //

  /**
   * Load the annotation.
   * @param is InputStream input stream
   */
  public void load(final InputStream is) throws IOException {

    final ObjectInputStream ois = new ObjectInputStream(is);
    try {
      this.transcripts = (Map<String, Transcript>) ois.readObject();
      this.chrZoneMap = (Map<String, ChromosomeZone>) ois.readObject();

    } catch (ClassNotFoundException e) {
      throw new IOException("Unable to load data.");
    }
    ois.close();
  }

  /**
   * Load the annotation.
   * @param inputFile input file
   */
  public void load(final File inputFile) throws FileNotFoundException,
      IOException {

    load(FileUtils.createInputStream(inputFile));
  }

  //
  // Other
  //

  public void clear() {

    this.chrZoneMap.clear();
    this.transcripts.clear();
  }

  @Override
  public String toString() {

    return "Transcripts size: "
        + (this.transcripts == null ? "null" : this.transcripts.size());
  }

  //
  // Constructors
  //

  /**
   * Public constructor used to create the index.
   * @param annotationFile annotation file to use
   * @param expressionType the expression type to filter
   * @throws IOException if an error occurs while creating the index
   * @throws BadBioEntryException if an invalid entry of the annotation file is found
   */
  public TranscriptAndExonFinder(final File annotationFile,
      final String expressionType) throws IOException, BadBioEntryException {

    populateMapsFromGFFFile(FileUtils.createInputStream(annotationFile),
        expressionType);
  }

  /**
   * Public constructor used to create the index.
   * @param is annotation input stream to use
   * @param expressionType the expression type to filter
   * @throws IOException if an error occurs while creating the index
   * @throws BadBioEntryException if an invalid entry of the annotation file is found
   */
  public TranscriptAndExonFinder(final InputStream is,
      final String expressionType) throws IOException, BadBioEntryException {

    populateMapsFromGFFFile(is, expressionType);
  }

  /**
   * Public constructor.
   */
  public TranscriptAndExonFinder() {
  }

}