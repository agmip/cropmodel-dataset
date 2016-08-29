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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.agmip.ace.AceDataset;
import org.agmip.ace.AceExperiment;
import org.agmip.ace.AceSoil;
import org.agmip.ace.AceWeather;
import org.agmip.ace.io.AceGenerator;
import org.agmip.ace.io.AceParser;
import org.agmip.cropmodel.dataset.filetype.ACEFile;

/**
 *
 * @author frostbytten
 */
public class ACESeamer {
  private static final Logger LOG = Logger.getLogger(ACESeamer.class.getName());
  private ACESeamer() {
  }

  public static void seam(List<ACEFile> files, Path output) {
    try {
      Path tmpFile = Files.createTempFile("agmipwb", ".aceb");
      AceDataset ds = new AceDataset();
      for (ACEFile file : files) {
        LOG.log(Level.INFO, "Seaming file : {0}", file.getPath().toString());
        AceDataset source = AceParser.parseACEB(file.getPath().toFile());
        for (AceExperiment exp : source.getExperiments()) {
          ds.addExperiment(exp.rebuildComponent());
        }
        for (AceSoil soil : source.getSoils()) {
          ds.addSoil(soil.rebuildComponent());
        }
        for (AceWeather wth : source.getWeathers()) {
          ds.addWeather(wth.rebuildComponent());
        }
      }
      LOG.log(Level.INFO, "Seaming completed. Attempting to write file to {0}.", tmpFile.toString());
      AceGenerator.generateACEB(tmpFile.toFile(), ds);
      LOG.log(Level.INFO, "Probably hanging here!");
      Files.copy(tmpFile, output, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception ex) {
      LOG.log(Level.SEVERE, null, ex);
    }
  }
}
