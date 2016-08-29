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

import com.opencsv.CSVReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.agmip.cropmodel.dataset.filetype.ACMOFile;

/**
 *
 * @author Christopher Villalobos <cvillalobos@ufl.edu>
 */
public class LinkChecker {

  private LinkChecker() {
  }

  private static final Logger LOG = Logger.getLogger(LinkChecker.class.getName());

  public static boolean checkLinkedData(Path path, PrintWriter out, PrintWriter err,
      Set<String> eids, Set<String> sids, Set<String> wids,
      Set<String> exnames, Set<String> soilids, Set<String> wstclim) {

    String[] searchColumns = {"EXNAME", "EID", "SOIL_ID", "SID", "WST_ID", "CLIM_ID", "WID", "FIELD_OVERLAY", "DOID", "SEASONAL_STRATEGY", "DSID", "ROTATIONAL_ANALYSIS", "DRID"};
    int[] searchResults = new int[searchColumns.length];
    boolean headerFound = false;
    boolean problemFound = false;
    try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
      Optional<String[]> line = Optional.ofNullable(reader.readNext());
      List<String> errors = new ArrayList<>();
      long lineNum = 0L;
      while (line.isPresent()) {
        lineNum++;
        String[] l = line.get();
        if (l[0].equals("")) {
          problemFound = true;
          errors.add("         Invalid ACMO entry on line " + lineNum);
          line = Optional.ofNullable(reader.readNext());
          continue;
        }
        char token = (l[0].startsWith("\"")) ? l[0].charAt(1) : l[0].charAt(0);
        switch (token) {
          case '#':
            //This is the header. Need to find the columns for the IDs and metadata IDs
            for (int i = 0; i < searchColumns.length; i++) {
              for (int k = 0; k < l.length; k++) {
                if (l[k].contains(searchColumns[i])) {
                  searchResults[i] = k;
                  break;
                }
              }
            }
            headerFound = true;
            break;
          case '*':
            //This is an entry, need to make sure that the header is set already;
            if (headerFound) {
              boolean eidProblem = false;
              boolean sidProblem = false;
              boolean widProblem = false;
              String wst_id = null;
              String exname = null;
              String soil_id = null;
              for (int i = 0; i < searchColumns.length; i++) {
                StringBuilder sb = new StringBuilder();
                String res = l[searchResults[i]];
                boolean pfound = false;
                switch (searchColumns[i]) {
                  case "EID":
                    if (!eids.contains(res)) {
                      sb.append("EID not found for [");
                      sb.append(exname);
                      sb.append("]: ");
                      problemFound = true;
                      pfound = true;
                    }
                    break;
                  case "SID":
                    if (!sids.contains(res)) {
                      sb.append("SID not found for [");
                      sb.append(soil_id);
                      sb.append("]: ");
                      problemFound = true;
                      pfound = true;
                    }
                    break;
                  case "WID":
                    if (!wids.contains(res)) {
                      sb.append("WID not found for [");
                      sb.append(wst_id);
                      sb.append("]: ");
                      problemFound = true;
                      pfound = true;
                    }
                    break;
                  case "EXNAME":
                    exname = ACMOFile.extractExname(res);
                    res = exname;
                    if (!exnames.contains(res)) {
                      sb.append("EXNAME not found: ");
                      pfound = true;
                    }
                    break;

                  case "SOIL_ID":

                    soil_id = res;
                    if (!soilids.contains(res)) {
                      sb.append("SOIL_ID not found: ");
                      pfound = true;
                    }

                    break;
                  case "WST_ID":
                    wst_id = res;
                    break;
                  case "CLIM_ID":
                    if (wst_id != null) {
                      String merged = wst_id + "|" + res;
                      if (!wstclim.contains(merged)) {
                        sb.append("WST_ID ");
                        sb.append(wst_id);
                        sb.append(" not found with CLIM_ID: ");
                        pfound = true;
                      }
                      wst_id = wst_id + " + " + res;
                    }
                    break;
                  default:
                    break;
                }
                if (pfound) {
                  sb.append(res);
                  String error = sb.toString();
                  if (!errors.contains(error)) {
                    errors.add(error);
                  }
                }
              }
            }
            break;

          default:
            //Do nothing
            break;
        }
        line = Optional.ofNullable(reader.readNext());
      }
      if (problemFound) {
        err.println("[FAILED] " + path.toString());
        errors.stream().forEach((error) -> {
          err.println("         "+error);
        });
        err.println();
      }
    } catch (IOException ex) {
      LOG.log(Level.SEVERE, null, ex);
    }
    return !problemFound;
  }
}
