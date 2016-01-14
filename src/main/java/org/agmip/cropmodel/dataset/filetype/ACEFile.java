package org.agmip.cropmodel.dataset.filetype;

import java.nio.file.Path;

public class ACEFile extends CropModelFile {
     
    public ACEFile(Path path) {
        super(path);
    }
    @Override
    public CropModelFileType getFileType() {
        return CropModelFileType.ACE;
    }

    @Override
    public Path getPath() {
        return super.getPath();
    }
   
}
