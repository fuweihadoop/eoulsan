package fr.ens.transcriptome.eoulsan.bio;

import static com.google.common.collect.Sets.newHashSet;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import fr.ens.transcriptome.eoulsan.bio.io.FastqReader;

public enum FastqFormat {

  FASTQ_SANGER("fastq-sanger", null, 0, 93, 33, false), FASTQ_SOLEXA(
      "fastq-solexa", null, -5, 62, 64, true), FASTQ_ILLUMINA("fastq-illumina",
      null, 0, 62, 64, false);

  private final String name;
  private final Set<String> alias;

  private final int qualityMin;
  private final int qualityMax;
  private final int asciiOffset;
  private final boolean solexaQualityScore;

  //
  // Getters
  //

  /**
   * Get the name of the fastq format.
   * @return the name of the format
   */
  public String getName() {
    return this.name;
  }

  /**
   * Get the minimal value of the quality score.
   * @return the minimal value of the quality score
   */
  public int getQualityMin() {
    return this.qualityMin;
  }

  /**
   * Get the maximal value of the quality score.
   * @return the maximal value of the quality score
   */
  public int getQualityMax() {
    return this.qualityMax;
  }

  /**
   * Get the ASCII offset.
   * @return the ASCII offset
   */
  public int getAsciiOffset() {
    return this.asciiOffset;
  }

  //
  // Other methods
  //

  /**
   * Get the minimal ASCII character used to represent the quality.
   * @return an ASCII character
   */
  public char getCharMin() {

    return (char) (this.asciiOffset + this.qualityMin);
  }

  /**
   * Get the maximal ASCII character used to represent the quality.
   * @return an ASCII character
   */
  public char getCharMax() {

    return (char) (this.asciiOffset + this.qualityMax);
  }

  /**
   * Test if a character is valid to represent the quality.
   * @param c the character to test
   * @return true if the character if valid
   */
  public boolean isCharValid(final char c) {

    return c >= getCharMin() && c <= getCharMax();
  }

  /**
   * Test if all the character of a string are valid to represent the quality.
   * @param s the string to test
   * @return -1 if all the characters of the string are valid of the value of
   *         the first invalid character
   */
  public int isStringValid(final String s) {

    if (s == null)
      throw new NullPointerException();

    final int len = s.length();

    for (int i = 0; i < len; i++) {

      final char c = s.charAt(i);
      if (!isCharValid(c))
        return c;
    }

    return -1;
  }

  /**
   * Convert a character to a quality number.
   * @param character character to convert
   * @return a quality score
   */
  public int getQuality(final char character) {

    return character - this.asciiOffset;
  }

  // /**
  // * Convert a quality character from a format to another.
  // * @param character character to convert
  // * @param format output format
  // * @return the converted character
  // */
  // public char convertTo(final char character, final FastqFormat format) {
  //
  // return (char) (format.asciiOffset + convertQualityTo(getQuality(character),
  // format));
  // }

  // /**
  // * Convert quality from a format to another.
  // * @param quality quality to transform
  // * @param format output format
  // * @return a converted quality
  // */
  // public int convertQualityTo(final int quality, final FastqFormat format) {
  //
  // if (this.solexaQualityScore != format.solexaQualityScore) {
  //
  // double dq = (double) quality;
  // System.out.println("dq=" + dq);
  //
  // double pow = Math.pow(10, dq / 10.0);
  // System.out.println("pow=" + pow);
  //
  // double log = Math.log10(pow + (this.solexaQualityScore ? 1 : -1));
  // System.out.println("log=" + log);
  //
  // double result = 10.0 * log;
  // System.out.println("result=" + result);
  //
  // int r = (int) result;
  // System.out.println("return=" + r);
  //
  // return r;
  //
  // // return (int) (10.0 * Math.log10(Math.pow(10, ((double) quality)
  // // / 10.0) + (this.solexaQualityScore ? 1 : -1)));
  // }
  //
  // return quality;
  // }

  /**
   * Get a format from its name or its alias.
   * @param name name of the format to get
   * @return the format or null if no format was found
   */
  public static FastqFormat getFormatFromName(final String name) {

    if (name == null)
      return null;

    for (FastqFormat format : FastqFormat.values()) {

      if (format.getName().equals(name))
        return format;

      if (format.alias != null && format.alias.contains(name))
        return format;

    }

    return null;
  }

  /**
   * Identify the fastq format used in a Fastq file.
   * @param is input stream
   * @param maxEntriesToRead maximal entries of the file to read. If this value
   *          is lower than 1 all the entries of the stream are read
   * @return The FastqFormat found or null if no format was found
   * @throws IOException if an error occurs while reading the fastq stream
   * @throws BadBioEntryException if bad fastq entry is found
   */
  public static FastqFormat identifyFormat(final InputStream is,
      final int maxEntriesToRead) throws IOException, BadBioEntryException {

    if (is == null)
      return null;

    final FastqReader reader = new FastqReader(is);
    final Set<FastqFormat> formats =
        newHashSet(Arrays.asList(FastqFormat.values()));

    int count = 0;

    while (reader.readEntry()
        || (maxEntriesToRead > 0 && count >= maxEntriesToRead)) {
      removeBadFormats(formats, reader.getQuality());

      count++;
    }

    is.close();

    return identifyFormatByHeristic(formats);
  }

  private static void removeBadFormats(Set<FastqFormat> formats,
      final String qualityString) {

    if (formats == null || qualityString == null)
      return;

    for (FastqFormat format : new HashSet<FastqFormat>(formats)) {

      for (int i = 0; i < qualityString.length(); i++) {
        final char c = qualityString.charAt(i);
        if (c < format.getCharMin() || c > format.getCharMax()) {
          formats.remove(format);
          break;
        }
      }
    }
  }

  private static FastqFormat identifyFormatByHeristic(
      final Set<FastqFormat> formats) {

    if (formats == null)
      return null;

    if (formats.isEmpty())
      return null;

    final Map<FastqFormat, Integer> map = Maps.newHashMap();
    for (FastqFormat f : formats)
      map.put(f, f.getQualityMax() - f.getQualityMin());

    FastqFormat bestFormat = null;
    int bestRange = Integer.MAX_VALUE;

    for (Map.Entry<FastqFormat, Integer> e : map.entrySet()) {

      if (bestFormat == null || e.getValue() < bestRange) {

        bestFormat = e.getKey();
        bestRange = e.getValue();
      }

    }

    return bestFormat;
  }

  @Override
  public String toString() {

    return getName();
  }

  //
  // Constructor
  //

  /**
   * Constructor.
   * @param name format name
   * @param alias alias of the format
   * @param qualityMin quality minimal value
   * @param qualityMax quality maximal value
   * @param asciiOffset ASCII offset
   * @param solexaQualityScore Solexa quality score
   */
  FastqFormat(final String name, final Set<String> alias, final int qualityMin,
      final int qualityMax, final int asciiOffset,
      final boolean solexaQualityScore) {

    this.name = name;
    this.alias = alias;
    this.qualityMin = qualityMin;
    this.qualityMax = qualityMax;
    this.asciiOffset = asciiOffset;
    this.solexaQualityScore = solexaQualityScore;
  }

}
