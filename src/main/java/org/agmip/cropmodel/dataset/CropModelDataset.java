package org.agmip.cropmodel.dataset;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.agmip.cropmodel.dataset.filetype.*;
import org.agmip.cropmodel.dataset.util.AgMIPFileTypeIdentifier;

public class CropModelDataset {
    private final List<ACEFile> aceFiles = new ArrayList<>();
    private final List<DOMEFile> domeFiles = new ArrayList<>();
    private final List<ACMOFile> acmoFiles =new ArrayList<>();
    private final List<LinkageFile> linkageFiles = new ArrayList<>();
    private final List<ModelSpecificFile> modelFiles = new ArrayList<>();
    private final List<SupplementalFile> extraFiles = new ArrayList<>();
   
    private final static Logger LOG = Logger.getLogger(CropModelDataset.class.getName());
    
    
    public CropModelDataset() {}
    
    public void identifyDatasetFiles(Path basedir) {
        identifyDatasetFiles(basedir, true);
    }
    
    public void identifyDatasetFiles(Path basedir, boolean skipDotFiles) {
        try {
        Files.walkFileTree(basedir, new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.getFileName().toString().startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (! file.getFileName().toString().startsWith(".")) {
                    addFile(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }
    
    public void addFile(Path file) {
        LOG.log(Level.INFO, "Adding file: {0}", file.getFileName().toString());
        CropModelFile f = AgMIPFileTypeIdentifier.identify(file);
        if (f != null) {
            LOG.log(Level.INFO, "File type found: {0}", f.getFileType());
            switch(f.getFileType()) {
                case ACE:
                    aceFiles.add((ACEFile) f);
                    break;
                case DOME:
                    domeFiles.add((DOMEFile) f);
                    break;
                case ACMO:
                    acmoFiles.add((ACMOFile) f);
                    break;
                case Linkage:
                    linkageFiles.add((LinkageFile) f);
                    break;
                case Supplemental:
                default:
                    extraFiles.add((SupplementalFile) f);
                    break;
            }
         } else {
            LOG.log(Level.INFO, "Cannot add file: {0}", file.getFileName().toString());
        }
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
}
