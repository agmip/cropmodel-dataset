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

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import org.agmip.cropmodel.dataset.util.JsonFactoryProvider;

/**
 * This class should eventually be replaced by DOME 2.0 library functions.
 * @author Christopher Villalobos <cvillalobos@ufl.edu>
 */
public class DOMEHandler {
  private DOMEHandler(){}
  public static Set<String> getDomeIds(Path path) {
    Set<String> results = new HashSet<>();
    try(FileInputStream fis = new FileInputStream(path.toFile());
        GZIPInputStream gis = new GZIPInputStream(fis);
        JsonParser p = JsonFactoryProvider.getFactory().createParser(gis)){
      // Do something
      boolean level = false;
      String currentDome;
      while(Optional.ofNullable(p.nextToken()).isPresent()) {
        switch(p.getCurrentToken()) {
          case START_OBJECT:
            if (level) {
              p.skipChildren();
              level = false;
            } else {
              p.nextToken();
              results.add(p.getCurrentName());
              level = true;
            }
            break;
          case FIELD_NAME:
            if (! level) {
              results.add(p.getCurrentName());
              level = true;
            }
            break;
          case END_OBJECT:
            level = false;
            break;
          default:
            //Do nothing
            break;
        }
      }
    } catch (IOException ex) {
      //Log the error
    }
    return results;
  }

  public static Set<String> getDomeNames(Path path) {
    Set<String> results = new HashSet<>();
    try(FileInputStream fis = new FileInputStream(path.toFile());
        GZIPInputStream gis = new GZIPInputStream(fis);
        JsonParser p = JsonFactoryProvider.getFactory().createParser(gis)){
      boolean inInfo = false;
      String regId = "";
      String stratum = "";
      String rapId = "";
      String manId = "";
      String rapVer = "";
      String climId = "";
      String desc = "";
      while(Optional.ofNullable(p.nextToken()).isPresent()) {
        switch(p.getCurrentToken()) {
          case FIELD_NAME:
            String field = p.getCurrentName();
            if (field.toLowerCase().equals("info")) {
              inInfo = true;
            }
            break;
          case VALUE_STRING:
            if (inInfo) {
              String currentValue = p.getText();
              if (currentValue == null) {
                // This should never happen, but guard because never happens.
                continue;
              }
              currentValue = currentValue.toUpperCase();
              switch(p.getCurrentName().toLowerCase()) {
                case "reg_id":
                  regId = currentValue;
                  break;
                case "stratum":
                  stratum = currentValue;
                  break;
                case "rap_id":
                  rapId = currentValue;
                  break;
                case "man_id":
                  manId = currentValue;
                  break;
                case "rap_ver":
                  rapVer = currentValue;
                  break;
                case "clim_id":
                  climId = currentValue;
                  break;
                case "description":
                  desc = currentValue;
                  break;
                default:
                  break;
              }
            }
            break;
          case END_OBJECT:
            if (inInfo) {
              StringBuilder sb = new StringBuilder();
              sb.append(regId);
              sb.append("-");
              sb.append(stratum);
              sb.append("-");
              sb.append(rapId);
              sb.append("-");
              sb.append(manId);
              sb.append("-");
              sb.append(rapVer);
              sb.append("-");
              sb.append(climId);
              sb.append("-");
              sb.append(desc);
              results.add(sb.toString());
              regId = "";
              stratum = "";
              rapId = "";
              manId = "";
              rapVer = "";
              climId = "";
              desc = "";
              inInfo = false;
            }
            break;
        }
      }
    } catch (IOException ex) {
      // Log the rror
    }
    return results;
  }
}
