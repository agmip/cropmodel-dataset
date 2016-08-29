package org.agmip.cropmodel.dataset;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.agmip.ace.AceDataset;
import org.agmip.ace.AceExperiment;
import org.agmip.ace.AceSoil;
import org.agmip.ace.AceWeather;
import org.agmip.ace.io.AceParser;
import org.agmip.cropmodel.dataset.filetype.*;
import org.agmip.cropmodel.dataset.filetype.CropModelFile.CropModelFileType;
import org.agmip.cropmodel.dataset.util.ACESeamer;
import org.agmip.cropmodel.dataset.util.AgMIPFileTypeIdentifier;
import org.agmip.cropmodel.dataset.util.DOMEHandler;
import org.agmip.cropmodel.dataset.util.DOMESeamer;
import org.agmip.cropmodel.dataset.util.LinkChecker;
import org.agmip.cropmodel.dataset.util.ZipFS;

public class CropModelDataset {

  private final List<ACEFile> aceFiles = new ArrayList<>();
  private final List<DOMEFile> domeFiles = new ArrayList<>();
  private final List<ACMOFile> acmoFiles = new ArrayList<>();
  private final List<LinkageFile> linkageFiles = new ArrayList<>();
  private final List<ModelSpecificFile> modelFiles = new ArrayList<>();
  private final List<SupplementalFile> extraFiles = new ArrayList<>();
  private final Map<Path, CropModelFileType> ftLookup = new HashMap<>();
  private Path basedir;
  private boolean datasetValid = false;

  private final static Logger LOG = Logger.getLogger(CropModelDataset.class.getName());

  public CropModelDataset() {
  }

  public boolean isValid() {
    return datasetValid;
  }
  
  public void identifyDatasetFiles(Path basedir) {
    identifyDatasetFiles(basedir, true);
  }

  public CropModelFileType lookupFileType(Path p) {
    return ftLookup.get(p);
  }
  
  public void identifyDatasetFiles(Path basedir, boolean skipDotFiles) {
    try {
      Files.walkFileTree(basedir, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          if (dir.getFileName().toString().startsWith(".")) {
            return FileVisitResult.SKIP_SUBTREE;
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          if (!file.getFileName().toString().startsWith(".")) {
            addFile(file);
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException ex) {
      LOG.log(Level.SEVERE, null, ex);
    }
    this.basedir = basedir;
  }

  public void refreshIdentify() {
    if (this.basedir != null) {
      aceFiles.clear();
      domeFiles.clear();
      acmoFiles.clear();
      linkageFiles.clear();
      modelFiles.clear();
      extraFiles.clear();
      ftLookup.clear();
      identifyDatasetFiles(this.basedir);
    }
  }

  public CropModelFileType addFile(Path file) {
    LOG.log(Level.INFO, "Adding file {0}", file);
    CropModelFile f = AgMIPFileTypeIdentifier.identify(file);
    CropModelFileType ft = null;
    if (f != null) {
      switch (f.getFileType()) {
        case ACE:
          aceFiles.add((ACEFile) f);
          break;
        case DOME:
          domeFiles.add((DOMEFile) f);
          break;
        case ACMO:
          acmoFiles.add((ACMOFile) f);
          break;
        case LINKAGE:
          linkageFiles.add((LinkageFile) f);
          break;
        case SUPPLEMENTAL:
        default:
          extraFiles.add((SupplementalFile) f);
          break;
      }
      ft = f.getFileType();
      ftLookup.put(file, ft);
      LOG.log(Level.INFO, "Can return file type as {0}", f.getFileType());
    } else {
      LOG.log(Level.INFO, "Cannot add file: {0}", file.getFileName().toString());
    }
    return ft;
  }

  public String promoteToCultivar(Path f) {
    SupplementalFile file = new SupplementalFile(f);
    ModelSpecificFile modelFile = new ModelSpecificFile(f);
    String msg;
    if (extraFiles.contains(file)) {
      msg = modelFile.getFileName() + " has been marked as a cultivar file.";
      extraFiles.remove(file);
      modelFiles.add(modelFile);
      ftLookup.put(f, CropModelFileType.MODEL_SPECIFIC);
    } else if (modelFiles.contains(modelFile)) {
      msg = modelFile.getFileName() + " has already been marked as a cultivar file.";
    } else {
      msg = "Cannot mark a non-supplemental file as cultivar";
    }
    return msg;
  }

  public List<ModelSpecificFile> getModelSpecificFiles() {
    return modelFiles;
  }

  public String datasetStatisticsHTML() {
    StringBuilder sb = new StringBuilder();
    sb.append("<p>ACE Files: ");
    sb.append(aceFiles.size());
    sb.append("</p><p>DOME Files: ");
    sb.append(domeFiles.size());
    sb.append("</p><p>ACMO Files: ");
    sb.append(acmoFiles.size());
    sb.append("</p><p>Linkage Files: ");
    sb.append(linkageFiles.size());
    sb.append("</p><p>Supplemental Files: ");
    sb.append(extraFiles.size());
    sb.append("</p>");
    return sb.toString();
  }

  public boolean validateDataset() {
    PrintWriter out = new PrintWriter(System.out, true);
    PrintWriter err = new PrintWriter(System.err, true);
    return validateDataset(out, err);
  }

  public boolean validateDataset(PrintWriter out, PrintWriter err) {
    // Need to know if I am checking all the files or just a bunch of ACMO files.
    boolean acePresent = aceFiles.size() > 0;
    boolean domePresent = domeFiles.size() > 0;
    boolean linkPresent = linkageFiles.size() > 0;
    boolean acmoPresent = acmoFiles.size() > 0;
    boolean passedRequiredTests = false;

    if (!acePresent && !domePresent && !linkPresent && !acmoPresent) {
      err.println("Nothing to verify");
      return false;
    }

    // These are approximate guesses to make loading easier
    Set<String> eids = new HashSet<>(150);
    Set<String> wids = new HashSet<>(25);
    Set<String> sids = new HashSet<>(25);
    Set<String> dids = new HashSet<>(200);
    Set<String> domeNames = new HashSet<>(200);
    Set<String> acmoNames = new HashSet<>(200);
    Map<String, String> acmoRename = new HashMap<>();
    Map<String, StringBuilder> acmoDups = new HashMap<>();
    Set<String> exnames = new HashSet<>(150);
    Set<String> wstclim = new HashSet<>(100);
    Set<String> soilids = new HashSet<>(25);

    boolean acebsValid = true;
    if (acePresent) {
      out.println("\n------------------------------------------------------------------------");
      out.println("Validating ACEB files");
      out.println("------------------------------------------------------------------------\n");
      out.println("Checking " + aceFiles.size() + " ACEB files...\n");
      for (ACEFile ace : aceFiles) {
        try {
          AceDataset ds = AceParser.parseACEB(ace.getPath().toFile());
          for (AceExperiment exp : ds.getExperiments()) {
            try {
              if (!eids.contains(exp.getId())) {
                eids.add(exp.getId());
              }
              String exname = exp.getValueOr("exname", "");
              if (!exname.equals("") && !exnames.contains(exname)) {
                exnames.add(exname);
              }
            } catch (IOException ex) {
              acebsValid = false;
              err.println("[FAILED] " + ace.getPath().toFile());
              err.println("         Error loading experiments in file");
              LOG.log(Level.WARNING, null, ex);
            }
          }

          for (AceWeather wth : ds.getWeathers()) {
            try {
              if (!wids.contains(wth.getId())) {
                wids.add(wth.getId());
              }
              String wst_id = wth.getValueOr("wst_id", "");
              String clim_id = wth.getValueOr("clim_id", "");
              if (!wst_id.equals("") && !wstclim.contains(wst_id + "|" + clim_id)) {
                wstclim.add(wst_id + "|" + clim_id);
              }
            } catch (IOException ex) {
              acebsValid = false;
              err.println("[FAILED] " + ace.getPath().toFile());
              err.println("         Error loading weather in file.");
              LOG.log(Level.WARNING, null, ex);
            }
          }
          for (AceSoil soil : ds.getSoils()) {
            try {
              if (!sids.contains(soil.getId())) {
                sids.add(soil.getId());
              }
              String soil_id = soil.getValueOr("soil_id", "");
              if (!soil_id.equals("") && !soilids.contains(soil_id)) {
                soilids.add(soil_id);
              }
            } catch (IOException ex) {
              acebsValid = false;
              err.println("[FAILED] " + ace.getPath().toFile());
              err.println("         Error loading soils in file.");
              LOG.log(Level.WARNING, "Failure to parse for weather {0}: {1}", new Object[]{ace.getPath().toString(), ex});
            }
          }
        } catch (IOException ex) {
          acebsValid = false;
          err.println("[FAILED] " + ace.getPath().toFile());
          err.println("         This file is either corrupted or has an invalid structure.");
          LOG.log(Level.WARNING, "Failure to parse {0}: {1}", new Object[]{ace.getPath().toString(), ex});
        }
      }

      out.println("Found " + eids.size() + " unique experiment IDs");
      out.println("Found " + sids.size() + " unique soil IDs");
      out.println("Found " + wids.size() + " unique weather IDs");
      out.println("Found " + exnames.size() + " unique EXNAMEs");
      out.println("Found " + soilids.size() + " unique SOIL_IDs");
      out.println("Found " + wstclim.size() + " unique WST_ID and CLIM_ID combinations");
    }

    boolean domesValid = true;
    if (domePresent) {
      // Next we need to get the DOME IDs
      out.println("\n------------------------------------------------------------------------");
      out.println("Validating DOME files");
      out.println("------------------------------------------------------------------------\n");
      out.println("Checking " + domeFiles.size() + " DOME files...\n");
      domeFiles.stream().forEach((path) -> {
        dids.addAll(DOMEHandler.getDomeIds(path.getPath()));
        domeNames.addAll(DOMEHandler.getDomeNames(path.getPath()));
      });
      out.println("Found " + dids.size() + " unique DOME IDs");
      out.println("Found " + domeNames.size() + " unique DOME Names");
      if (dids.size() != domeNames.size()) {
        err.println("[FAILED] More than one DOME share the same name with different values.");
        err.println("         Please check the DOME metadata. Make each unique by using the DESCRIPTION field.");
        domesValid = false;
      }
    }

    boolean acmosValid = true;
    boolean acmoShadow = false;
    if (acmoPresent) {
      out.println("\n------------------------------------------------------------------------");
      out.println("Verifying ACMO files");
      out.println("------------------------------------------------------------------------\n");
      out.println("Checking " + acmoFiles.size() + " ACMO files...");
      for (ACMOFile acmo : acmoFiles) {
        String fname = acmo.getCleanFilename().getFileName().toString();
        LOG.log(Level.INFO, "Renaming {0} to {1}",
            new Object[]{acmo.getPath().toString(),
              acmo.getCleanFilename().getFileName().toString()});
        if (acmoNames.contains(fname)) {
          StringBuilder sb;
          if (!acmoDups.containsKey(fname)) {
            sb = new StringBuilder();
            sb.append("               ");
            sb.append(acmoRename.get(fname));
            sb.append("\n");
          } else {
            sb = acmoDups.get(fname);
          }
          sb.append("               ");
          sb.append(acmo.getPath().toString());
          sb.append("\n");
          acmoDups.put(fname, sb);
        } else {
          acmoNames.add(fname);
          acmoRename.put(fname, acmo.getPath().toString());
        }

        boolean isValid = acmo.isValid();
        if (!isValid) {
          acmosValid = false;
          err.println("[FAILED] " + acmo.getPath().toString());
          err.println(acmo.getErrorReport());
        }
        if (!acmo.getWarnings().equals("")) {
          out.println("[WARNING] " + acmo.getPath().toString());
          out.println("          Suspected multiple crop failures because of blank output lines in ACMO.\n");
          //out.println(acmo.getWarnings());
        }
      }
      out.println("\n------------------------------------------------------------------------");
      out.println("Checking ACMO renaming conflict");
      out.println("------------------------------------------------------------------------\n");
      out.println("NOTE: This product renames ACMO files to match the AgMIP protocols.\n");
      if (!acmoDups.isEmpty()) {
        acmoShadow = true;
        err.println("[FAILED] More than one ACMO file will share the same name.");
        err.println("         Please check the MAN_ID and RAP_ID columns in the ACMO files.");
        err.println("         MAN_ID should be blank unless using an adaptation.");
        err.println("         RAP_ID should be blank unless working with RAPs.");

        for (Map.Entry<String, StringBuilder> dup : acmoDups.entrySet()) {
          err.println("             The following files will be written as " + dup.getKey().toString());
          err.println(dup.getValue());
        }
      }
    }

    //if (linkPresent && !acmoPresent) {
    //  out.println("Verifying AgMIP linkage files.");
    //}
    boolean acmoLinkageTest = true;
    if (acmoPresent && acePresent) {
      out.println("\n------------------------------------------------------------------------");
      out.println("Verifying linkages between ACEB, DOME and ACMO files");
      out.println("------------------------------------------------------------------------\n");
      out.println("Checking linkages in " + acmoFiles.size() + " ACMO files...\n");
      boolean acmoLinkageAll = true;
      for (ACMOFile path : acmoFiles) {
        String cm = path.getCMSeries().orElse("");
        if (cm.equals("C3MP") || cm.equals("CTWN")) {
          out.println("- Skipping senstivity analysis linkage checking on " + path.getPath().toString() + "\n");
        } else if (!path.isValid()) {
          err.println("- Skipping invalid ACMO file " + path.getPath().toString() + "\n");
        } else {
          boolean thisLinkage = LinkChecker.checkLinkedData(path.getPath(), out, err, eids, sids, wids, exnames, soilids, wstclim);
          if (!thisLinkage) {
            acmoLinkageAll = false;
          }
        }
      }
      if (!acmoLinkageAll) {
        acmoLinkageTest = false;
      }
    }

    out.println("------------------------------------------------------------------------");
    out.println("Summary Report:");
    out.println("ACEB Validation......................... " + (acebsValid ? "SUCCESS" : "FAILED"));
    out.println("DOME Validation......................... " + (domesValid ? "SUCCESS" : "FAILED"));
    out.println("ACMO Validation......................... " + (acmosValid ? "SUCCESS" : "FAILED"));
    out.println("ACMO Renaming........................... " + (!acmoShadow ? "SUCCESS" : "FAILED"));
    out.println("Linkage Validation...................... " + (acmoLinkageTest ? "SUCCESS" : "FAILED"));
    datasetValid = acebsValid && domesValid && acmosValid && !acmoShadow && acmoLinkageTest;
    return datasetValid;

  }

  public void packageDataset(Path zipFile) {
    packageDataset(zipFile, "");
  }

  public void packageDataset(Path zipFile, String rootDir, Path... additionalFiles) {
    try (FileSystem zipFS = ZipFS.createZipFileSystem(zipFile.toString(), true)) {
      Path root = zipFS.getPath(rootDir);
      for (Path add : additionalFiles) {
        Path d = root.resolve(add.getFileName().toString());
        Path p = d.getParent();
        if (Files.notExists(p)) {
          Files.createDirectories(p);
        }
        Files.copy(add, d, StandardCopyOption.REPLACE_EXISTING);
      }

      Path aceOutput = root.resolve("dataset.aceb");
      ACESeamer.seam(aceFiles, aceOutput);

      Path domeOutput = root.resolve("alldomes.dome");
      DOMESeamer.seam(domeFiles, domeOutput);

      for (ACMOFile f : acmoFiles) {
        if (f.isValid()) {
          // Get the final path
          Path dest = root.resolve("ACMOS");
          if (f.getCMSeries().isPresent()) {
            String s = f.getCMSeries().get();
            if (s.equals("C3MP") || s.equals("CTWN")) {
              dest = dest.resolve("SENSITIVITY");
            } else {
              dest = dest.resolve(s);
            }
          } else {
            dest.resolve("UNKNOWN");
          }
          Path fname = f.getCleanFilename().getFileName();
          dest = dest.resolve(fname.toString());
          Path parent = dest.getParent();
          if (Files.notExists(parent)) {
            Files.createDirectories(parent);
          }
          Files.copy(f.getPath(), dest, StandardCopyOption.REPLACE_EXISTING);
        } else {
          LOG.log(Level.WARNING, "File {0} is invalid.", f.toString());
        }
      }

      for (ModelSpecificFile f : modelFiles) {
        Path dest = getUniqueName(root, f.getPath());
        Files.copy(f.getPath(), dest, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException ex) {
      LOG.log(Level.SEVERE, null, ex);
    }
  }

  private Path getUniqueName(Path root, Path file) {
    String fileName = file.getFileName().toString();
    Path dest = root.resolve(fileName);
    if (Files.exists(dest)) {
      String baseFileName;
      String fileNameExt;
      int baseIndex = fileName.indexOf(".");
      if (baseIndex == -1) {
        baseFileName = fileName;
        fileNameExt = "";
      } else {
        baseFileName = fileName.substring(0, baseIndex);
        fileNameExt = fileName.substring(baseIndex);
      }
      int i=0;
      while(Files.exists(dest)) {
        i++;
        String newFileName = baseFileName + "_" + i + fileNameExt;
        dest = root.resolve(newFileName);
      }
    }
    return dest;
  }
}
