/*
 * Copyright (c) 2012-2016, AgMIP All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * * Neither the name of the AgMIP nor the names of its
 *   contributors may be used to endorse or promote products derived from this
 *   software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.agmip.cropmodel.dataset.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import org.agmip.cropmodel.dataset.filetype.ACEFile;
import org.agmip.cropmodel.dataset.filetype.ACMOFile;
import org.agmip.cropmodel.dataset.filetype.CropModelFile;
import org.agmip.cropmodel.dataset.filetype.DOMEFile;
import org.agmip.cropmodel.dataset.filetype.LinkageFile;
import org.agmip.cropmodel.dataset.filetype.SupplementalFile;
import org.apache.tika.Tika;

/**
 *
 * @author Christopher Villalobos <cvillalobos@ufl.edu>
 */
public class AgMIPFileTypeIdentifier {

  private static final Logger LOG = Logger.getLogger(AgMIPFileTypeIdentifier.class.getName());
  private static final String[] VALID_EXTENSIONS = new String[]{".aceb", ".dome", ".alnk", ".csv"};
  private static final String[] VALID_ACMO_COLUMNS = new String[]{"EXNAME","FIELD_OVERLAY","SEASONAL_STRATEGY"};
  private AgMIPFileTypeIdentifier() {}

  public static CropModelFile identify(Path file) {
    // Check the paths first
    String fileName = file.getFileName().toString();
    CropModelFile identity = null;
    if (endsWithFromArray(fileName, VALID_EXTENSIONS)) {
      Tika tika = new Tika();
      Optional<String> contentType;
      try {
        contentType = Optional.of(tika.detect(file));
      } catch (IOException ex) {
        LOG.log(Level.INFO, null, ex);
        contentType = Optional.empty();
      }
      if (contentType.isPresent()) {
        switch (contentType.get().toLowerCase(Locale.ROOT)) {
          case "application/gzip":
            identity = identifyGZIPFile(file);
            break;
          case "text/plain":
          case "text/csv":
            identity = identifyTextFile(file);
            break;
          default:
            identity = new SupplementalFile(file);
            break;
        }
      }
    } else {
      identity = new SupplementalFile(file);
    }
    return identity;
  }

  private static CropModelFile identifyGZIPFile(Path file) {
    CropModelFile identity = null;
    InputStream data = null;
    JsonParser p = null;
    try {
      data = new GZIPInputStream(new FileInputStream(file.toFile()));
      p = JsonFactoryProvider.getFactory().createParser(data);
      JsonToken first = p.nextToken();
      p.nextToken();
      if (first.equals(JsonToken.START_OBJECT)) {
        if (p != null) {
          switch (p.getCurrentName()) {
            case "experiments":
            case "weathers":
            case "soils":
              identity = new ACEFile(file);
              break;
            default:
              p.nextToken();
              p.nextToken();
              switch (p.getCurrentName()) {
                case "generators":
                case "rules":
                case "info":
                  identity = new DOMEFile(file);
                  break;
                default:
                  identity = new SupplementalFile(file);
                  break;
              }
          }
        } else {
          LOG.log(Level.SEVERE, "NPE Issue with file: {0}", file.toString());
          identity = new SupplementalFile(file);
        }
      } else {
        identity = new SupplementalFile(file);
      }
    } catch (Exception ex) {
      LOG.log(Level.SEVERE, null, ex);
      identity = new SupplementalFile(file);
    } finally {
      try {
        if (p != null) {
          p.close();
        }
        if (data != null) {
          data.close();
        }
      } catch (IOException ex) {
        LOG.log(Level.SEVERE, null, ex);
      }
    }
    return identity;
  }

  private static CropModelFile identifyTextFile(Path file) {
    CropModelFile identity = null;
    try {
      try (Scanner scanner = new Scanner(file)) {
        boolean identified = false;
        while (!identified) {
          if (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.length() > 2) {
              char identifier = (line.startsWith("\"")) ? line.charAt(1) : line.charAt(0);
              switch (identifier) {
                case '!':
                case '*':
                  // Don't know anything at this point.
                  break;
                case '#':
                  // This is a header which will help us identify the filetype
                  // This is hacky because a lot of our files contain EXNAME
                  if (containsAllFromArray(line, VALID_ACMO_COLUMNS)) {
                    if (line.contains("CROP_MODEL")) {
                      identity = new ACMOFile(file);
                      identified = true;
                    } else {
                      if (line.length() < 5) {
                        identity = new LinkageFile(file);
                        identified = true;
                      } else {
                        identity = new SupplementalFile(file);
                        identified = true;
                      }
                    }
                  } else {
                    identity = new SupplementalFile(file);
                    identified = true;
                  }
                  break;
                default:
                  identity = new SupplementalFile(file);
                  identified = true;
              }
            }
          } else {
            identity = new SupplementalFile(file);
            identified = true;
          }
        }
      }
    } catch (IOException ex) {
      LOG.log(Level.SEVERE, null, ex);
      identity = new SupplementalFile(file);
    }
    if (identity == null) {
      identity = new SupplementalFile(file);
    }
    return identity;
  }

  public static boolean endsWithFromArray(String source, String[] needles) {
    String haystack = source.toLowerCase();
    for (String needle : needles) {
      if (haystack.endsWith(needle)) {
        return true;
      }
    }
    return false;
  }

  public static boolean containsAllFromArray(String source, String[] needles) {
    for(String needle: needles) {
      if (! source.contains(needle)) {
        return false;
      }
    }
    return true;
  }
}
