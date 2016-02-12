/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.agmip.cropmodel.dataset.util;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author frostbytten
 */
public class ZipFS {

  private ZipFS() {
  }

  public static FileSystem createZipFileSystem(String zipFilename,
      boolean create)
      throws IOException {
    // convert the filename to a URI
    final Path path = Paths.get(zipFilename);
    final URI uri = URI.create("jar:file:" + path.toUri().getPath());

    final Map<String, String> env = new HashMap<>();
    if (create) {
      env.put("create", "true");
    }
    return FileSystems.newFileSystem(uri, env);
  }
}
