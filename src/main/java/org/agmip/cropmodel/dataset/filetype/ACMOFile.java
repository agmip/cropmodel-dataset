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
package org.agmip.cropmodel.dataset.filetype;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.opencsv.CSVReader;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public class ACMOFile extends CropModelFile {

  private static final Logger LOG = Logger.getLogger(ACMOFile.class.getName());
  private static final DateTimeFormatter DATE_FORMAT = ISODateTimeFormat.date();
  private StringBuilder errors = new StringBuilder(1024);
  private StringBuilder warnings = new StringBuilder(1024);
  private Optional<String[]> header;
  private Optional<String> cmSeries;
  private Optional<String> regionId;
  private Optional<String> climateId;
  private Optional<String> RAPId;
  private Optional<String> cropModel;
  private Set<String> crops;
  private Path filename = null;

  public ACMOFile(Path path) {
    super(path);
    regionId = Optional.empty();
    climateId = Optional.empty();
    RAPId = Optional.empty();
    cropModel = Optional.empty();
    crops = new HashSet<>();
    loadHeader();
    if (this.header.isPresent()) {
      checkCMSeries();
    }
  }

  @Override
  public CropModelFileType getFileType() {
    return CropModelFileType.ACMO;
  }

  @Override
  public boolean isValid() {
    if (header.isPresent() && cmSeries.isPresent()) {
      return checkFormat();
    } else {
      return false;
    }
  }

  public String getErrorReport() {
    return this.errors.toString();
  }

  public String getWarnings() {
    return this.warnings.toString();
  }

  public void clearErrorReport() {
    this.errors = new StringBuilder(1024);
    if (null == this.header || !this.header.isPresent()) {
      this.errors.append("No header row found.\n");
    }
    if (null == this.cmSeries || !this.cmSeries.isPresent()) {
      this.errors.append("Unable to determine the Crop Model Excersize for this ACMO.\n");
    }
  }

  public void clearWarnings() {
    this.warnings = new StringBuilder(1024);
  }

  public Optional<String[]> getHeader() {
    return this.header;
  }

  public Optional<String> getCMSeries() {
    return this.cmSeries;
  }

  public Path getCleanFilename() {
    return getCleanFilename(false);
  }

  public Path getCleanFilename(boolean generate) {
    if (generate || this.filename == null) {
      StringBuilder sb = new StringBuilder(100);
      StringBuilder cropString = new StringBuilder();
      sb.append("ACMO-");
      if (regionId.isPresent()) {
        sb.append(regionId.get());
      } else {
        sb.append("REGION");
      }
      sb.append("-");
      for (String crop : this.crops) {
        cropString.append(crop.toUpperCase().replace(" ", ""));
        cropString.append("_");
      }
      if (cropString.length() == 0) {
        sb.append("CROP");
      } else {
        cropString.delete(cropString.lastIndexOf("_"), cropString.length());
        sb.append(cropString.toString());
      }
      sb.append("-");
      if (climateId.isPresent()) {
        sb.append(climateId.get());
      } else {
        sb.append("CLIMATE");
      }
      sb.append("-");
      if (RAPId.isPresent()) {
        sb.append(RAPId.get()).append("-");
      }
      if (cmSeries.isPresent()) {
        sb.append(cmSeries.get()).append("-");
      }
      if (cropModel.isPresent()) {
        sb.append(cropModel.get());
      } else {
        sb.append("MODEL");
      }
      sb.append(".csv");
      this.filename = this.path.resolveSibling(sb.toString());
    }
    return this.filename;
  }

  public boolean checkFormat() {
    boolean fmtErrors = false;
    clearErrorReport();
    if (this.header.isPresent()) {
      int headerLength = this.header.get().length;
      List<Integer> dateColumns = getDateColumns();
      try (CSVReader reader = new CSVReader(new FileReader(this.path.toFile()))) {
        Optional<String[]> nextLine = Optional.ofNullable(reader.readNext());
        long lineNum = 0L;
        long dataLine = 0L;
        long dateFail = 0L;
        while (nextLine.isPresent()) {
          //boolean lineError = false;
          boolean dateError = false;
          boolean cropFailWarn = false;
          lineNum++;
          String[] line = nextLine.get();
          // This is currently acceptable, because Excel truncates missing lines
          // The issue we need to check for is gaps or 12-30-0 errors.
          // TODO: Check for 12-30-0 errors.
          /*if (line.length != headerLength) {
            lineError = true;
            fmtErrors = true;
            this.errors.append("Found ");
            this.errors.append(line.length);
            this.errors.append(" columns and expected ");
            this.errors.append(headerLength);
            this.errors.append(" on line ");
            this.errors.append(lineNum);
            this.errors.append("\n");
          }*/
          if (!line[0].equals("")) {
            char token = (line[0].startsWith("\"")) ? line[0].charAt(1) : line[0].charAt(0);
            if (token == '*') {
              dataLine++;
              StringBuilder errorLines = new StringBuilder("Invalid date for ");
              StringBuilder warningLines = new StringBuilder("Suspected crop failure on ");
              int errorsFound = 0;
              for (Integer idx : dateColumns) {
                try {
                  if (!line[idx].equals("")) {
                    try {
                      LocalDate d = DATE_FORMAT.parseLocalDate(line[idx]);
                    } catch (IllegalArgumentException ex) {

                      errorsFound++;
                      errorLines.append(this.header.get()[idx]);
                      errorLines.append(", ");
                      dateError = true;
                    }
                  }
                } catch (IndexOutOfBoundsException ex) {
                  // This means that we have an issue here
                  // Most likely a crop failure, but this is NOT
                  // considered an error.
                  cropFailWarn = true;
                  break;
                }
              }
              if (dateError) {
                dateFail++;
                fmtErrors = true;
                errorLines.deleteCharAt(errorLines.lastIndexOf(","));
                if (errorsFound > 1) {
                  errorLines.insert(12, 's');
                  errorLines.insert(errorLines.lastIndexOf(",") + 1, " and");
                  if (errorsFound == 2) {
                    errorLines.deleteCharAt(errorLines.lastIndexOf(","));
                  }
                }
                errorLines.append("on line ");
                errorLines.append(lineNum);
                this.errors.append(errorLines.toString());
                this.errors.append("\n");
              }
              if (cropFailWarn) {
                warningLines.append("on line ");
                warningLines.append(lineNum);
                warningLines.append("\n");
                this.warnings.append(warningLines.toString());
              }
            }
          }
          nextLine = Optional.ofNullable(reader.readNext());
        }
        if (dataLine == dateFail) {
          this.clearErrorReport();
          this.errors.append("Date format incorrect on every data line in this file.");
        }
      } catch (IOException ex) {
        return false;
      }
    } else {
      // The format is incorrect if it has no header
      fmtErrors = true;
    }
    // Now see if EVERY line has a date failure
    return !fmtErrors;
  }

  private void loadHeader() {
    loadHeader(false);
  }

  private void loadHeader(boolean recheck) {
    if (recheck || null == this.header) {
      try (CSVReader reader = new CSVReader(new FileReader(this.path.toFile()))) {
        Optional<String[]> nextLine = Optional.ofNullable(reader.readNext());
        long lineNum = 0L;
        while (nextLine.isPresent()) {
          lineNum++;
          String[] line = nextLine.get();
          if (!line[0].equals("")) {
            char token = (line[0].startsWith("\"")) ? line[0].charAt(1) : line[0].charAt(0);
            if (token == '#') {
              LOG.log(Level.FINE, "Header found on {0}", lineNum);
              this.header = nextLine;
              break;
            }
          }
          nextLine = Optional.ofNullable(reader.readNext());
        }
      } catch (IOException ex) {
        this.header = Optional.empty();
      }
      if (null == this.header) {
        this.errors.append("No ACMO header found\n");
        this.header = Optional.empty();
      }
    }
  }

  private List<Integer> getDateColumns() {
    List<Integer> colNum = new ArrayList<>();
    if (header.isPresent()) {
      String[] h = header.get();
      int l = h.length;
      for (int i = 0; i < l; i++) {
        if (h[i].endsWith("DAT") || h[i].endsWith("DATE") || h[i].endsWith("DAT_S")) {
          colNum.add(i);
        }
      }
    }
    return colNum;
  }

  private int getColumn(String columnName) {
    String cName = columnName.toUpperCase();
    if (header.isPresent()) {
      String[] h = header.get();
      int l = h.length;
      for (int i = 0; i < l; i++) {
        if (h[i].toUpperCase().equals(cName)) {
          return i;
        }
      }
    }
    return -1;
  }

  private void checkCMSeries() {

    try (CSVReader reader = new CSVReader(new FileReader(this.path.toFile()))) {
      boolean fenDiff = false;
      boolean cmFound = false;

      int exnameCol = getColumn("exname");
      int climIdCol = getColumn("clim_id");
      int manIdCol = getColumn("man_id");
      int fenTotCol = getColumn("fen_tot");

      String cm = null;
      String exnameBase = null;
      String fenTotCheck = null;
      String manIdCheck = null;
      String batchMatch = "\\w+_\\d+_b\\d+__\\d+";
      String[] captureColumns = {"reg_id", "crid_text", "rap_id", "crop_model"};
      Optional<String[]> nextLine = Optional.ofNullable(reader.readNext());

      if (exnameCol != -1) {
        long dataStartLine = -1L;
        long lineNum = 0L;
        while (nextLine.isPresent()) {
          lineNum++;
          String[] line = nextLine.get();
          if (!line[0].equals("")) {
            char token = (line[0].startsWith("\"")) ? line[0].charAt(1) : line[0].charAt(0);
            if (token == '*') {

              if (dataStartLine == -1L) {
                dataStartLine = lineNum;
              }

              String exname = line[exnameCol];
              String climId = line[climIdCol];
              String manId = line[manIdCol];
              String fenTot = line[fenTotCol];

              if (!regionId.isPresent()) {
                if (climId != null && !climId.equals("")) {
                  climateId = Optional.of(climId);
                }

                for (String capture : captureColumns) {
                  String val = line[getColumn(capture)];
                  switch (capture) {
                    case "reg_id":
                      if (val != null && !val.equals("")) {
                        regionId = Optional.of(val);
                      }
                      break;
                    case "crid_text":
                      if (val != null && !val.equals("")) {
                        crops.add(val);
                      }
                      break;
                    case "rap_id":
                      if (val != null && !val.equals("")) {
                        RAPId = Optional.of(val);
                      }
                      break;
                    case "crop_model":
                      if (val != null && !val.equals("")) {
                        cropModel = Optional.of(val);
                      }
                      break;
                  }
                }
              }

              if (manIdCheck == null) {
                manIdCheck = manId;
              }

              if (fenTotCheck == null) {
                fenTotCheck = fenTot;
              }

              if (!exname.matches(batchMatch)) {
                if (exname.contains("__")) {
                  if (climateId.isPresent() && climateId.get().startsWith("0X")) {
                    cm = "CM1";
                  } else if (null == manId || manId.equals("")) {
                    cm = "CM2";
                  } else {
                    try {
                      Integer manIdInt = Integer.parseInt(manId);
                      cm = "CM" + (manIdInt + 2);
                    } catch (NumberFormatException ex) {
                      cm = "CM-" + manId;
                    }
                  }
                  cmFound = true;
                  break;
                }

                if (exnameBase == null) {
                  exnameBase = exname;
                }

                if (!exnameBase.equals(exname)) {
                  if (dataStartLine == (lineNum - 1)) {
                    // Technically should be CM0
                    cm = "CM0";
                  }
                  cmFound = true;
                  break;
                }
              }

              if (!fenDiff && !fenTot.equals(fenTotCheck)) {
                fenDiff = true;
              }
              
            }
          }
          nextLine = Optional.ofNullable(reader.readNext());
        }
        if (!cmFound) {
          if (fenDiff) {
            cm = "CTWN";
          } else {
            cm = "C3MP";
          }
        }
        cmSeries = Optional.ofNullable(cm);
      } else {
        cmSeries = Optional.empty();
      }
    } catch (IOException ex) {
      cmSeries = Optional.empty();
    }
  }
}
