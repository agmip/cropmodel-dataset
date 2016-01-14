package org.agmip.cropmodel.dataset.filetype;

import java.nio.file.Path;

public abstract class CropModelFile {
    protected final Path path;
    
    public CropModelFile(Path path) {
        this.path = path;
    }
    
    abstract public CropModelFileType getFileType();
    public String getName() {
        return this.path.getFileName().toString();
    }
    public Path getPath() {
        return this.path;
    }
    
    public enum CropModelFileType {
        ACE,
        DOME,
        BatchDOME,
        ACMO,
        Linkage,
        Cultivar,
        Supplemental
    }
    
}
