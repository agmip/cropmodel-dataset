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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.agmip.cropmodel.dataset.filetype.DOMEFile;

/**
 *
 * @author frostbytten
 */
public class DOMESeamer {
  private final static Logger LOG = Logger.getLogger(DOMESeamer.class.getName());

  private DOMESeamer() {}

  public static void seam(List<DOMEFile> files, Path output) {
    List<String> savedDomeList = new ArrayList<>();
    try {
      long count = 0L;
      Path tmpFile = Files.createTempFile("agmipwb", ".dome");
      try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(tmpFile.toFile()));
          JsonGenerator g = JsonFactoryProvider.getFactory().createGenerator(out)) {
        g.writeStartObject();
        for (DOMEFile file : files) {
          LOG.log(Level.INFO, "Examinging file: {0}", file.getPath().toString());
          try (GZIPInputStream in = new GZIPInputStream(new FileInputStream(file.getPath().toFile()));
              JsonParser p = JsonFactoryProvider.getFactory().createParser(in)) {
            boolean started = false;
            while (Optional.ofNullable(p.nextToken()).isPresent()) {
              String currentDome = p.getCurrentName();
              JsonToken t = p.getCurrentToken();
              if (t == JsonToken.START_OBJECT) {
                if (! started ) {
                  p.nextToken();
                  started = true;
                }
                if (currentDome != null) {
                  LOG.log(Level.INFO, "Current domeId: {0}", currentDome);
                  if (savedDomeList.contains(currentDome)) {
                    p.skipChildren();
                  } else {
                    g.writeFieldName(currentDome);
                    LOG.log(Level.INFO, "Copy token target: {0}", p.getCurrentToken());
                    g.copyCurrentStructure(p);
                    count++;
                    savedDomeList.add(currentDome);
                  }
                }
              }
            }
          } catch (Exception ex) {
            LOG.log(Level.SEVERE, null, ex);
          }
        }
        g.writeEndObject();
      } catch (IOException ex) {
        Logger.getLogger(DOMESeamer.class.getName()).log(Level.SEVERE, null, ex);
      }
      LOG.log(Level.INFO, "Copied domes: {0}", count);
      Files.copy(tmpFile, output, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException ex) {
      Logger.getLogger(DOMESeamer.class.getName()).log(Level.SEVERE, null, ex);
    }
  }
}
