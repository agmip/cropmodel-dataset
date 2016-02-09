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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.opencsv.CSVReader;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ACMOFile extends CropModelFile {
  private static final Logger LOG = LoggerFactory.getLogger(ACMOFile.class);
  private static final DateTimeFormatter DATE_FORMAT = ISODateTimeFormat.date();
  private Optional<String[]> header;

  public ACMOFile(Path path) {
    super(path);
    loadHeader();
  }

  @Override
  public CropModelFileType getFileType() {
    return CropModelFileType.ACMO;
  }

  @Override
  public boolean isValid() {
    if (header.isPresent()) {
      return checkFormat();
    } else {
      return false;
    }
  }

  public Optional<String[]> getHeader() {
    return this.header;
  }

  public boolean checkFormat() {

    boolean errors = false;
    if (this.header.isPresent()) {
      int headerLength = this.header.get().length;
      List<Integer> dateColumns = getDateColumns();
      try (CSVReader reader = new CSVReader(new FileReader(this.path.toFile()))) {
        Optional<String[]> nextLine = Optional.ofNullable(reader.readNext());
        long lineNum = 0L;
        while(nextLine.isPresent()) {
          boolean lineError = false;
          boolean dateError = false;
          lineNum++;
          String[] line = nextLine.get();
          if (line.length != headerLength) {
            lineError = true;
            errors = true;
            LOG.error("Invalid number of columns on line " + lineNum);
          }

          if (! lineError ) {
            if (! line[0].equals("")) {
              char token = (line[0].startsWith("\"")) ? line[0].charAt(1) : line[0].charAt(0);
              if (token == '*') {
                StringBuffer errorLines = new StringBuffer("Invalid date for ");
                int errorsFound = 0;
                for(Integer idx : dateColumns) {
                  if (! line[idx].equals("")) {
                    try {
                      LocalDate d = DATE_FORMAT.parseLocalDate(line[idx]);
                    } catch (IllegalArgumentException ex) {
                      errorsFound ++;
                      errorLines.append(this.header.get()[idx]);
                      errorLines.append(", ");
                      dateError = true;
                    }
                  }
                }
                if (dateError) {
                  errors = true;
                  errorLines.deleteCharAt(errorLines.lastIndexOf(","));
                  if (errorsFound > 1) {
                    errorLines.insert(12, 's');
                    errorLines.insert(errorLines.lastIndexOf(",")+1, " and");
                    if (errorsFound == 2) {
                      errorLines.deleteCharAt(errorLines.lastIndexOf(","));
                    }
                  }
                  errorLines.append("on line ");
                  errorLines.append(lineNum);
                  LOG.error(errorLines.toString());
                  errorLines = null;
                }
              }
            }
          }
          nextLine = Optional.ofNullable(reader.readNext());
        }
      } catch (IOException ex) {
        return false;
      }
    } else {
      // The format is incorrect if it has no header
      errors = true;
    }
    return !errors;
  }

  private void loadHeader() {
    loadHeader(false);
  }

  private void loadHeader(boolean recheck) {
    if (recheck || null == this.header) {
      try (CSVReader reader = new CSVReader(new FileReader(this.path.toFile()))) {
        Optional<String[]> nextLine = Optional.ofNullable(reader.readNext());
        long lineNum = 0L;
        while(nextLine.isPresent()) {
          lineNum++;
          String[] line = nextLine.get();
          if (! line[0].equals("")) {
            char token = (line[0].startsWith("\"")) ? line[0].charAt(1) : line[0].charAt(0);
            if (token == '#') {
              LOG.debug("Header found on " + lineNum);
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
        LOG.error("No header found in file  " + this.path.toString());
        this.header = Optional.empty();
      }
    }
  }

  private List<Integer> getDateColumns() {
    List<Integer> colNum = new ArrayList<>();
    if (header.isPresent()) {
      String[] h = header.get();
      int l = h.length;
      for(int i=0; i < l; i++) {
        if (h[i].endsWith("DAT") || h[i].endsWith("DATE") || h[i].endsWith("DAT_S")) {
          colNum.add(i);
        }
      }
    }
    return colNum;
  }
}
