package org.agmip.cropmodel.dataset.filetype;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class ACMOFileTest {
  @BeforeClass
  public static void setupClass() {
    cleanAcmo = loadResource("clean.acmo");
    headerlessAcmo = loadResource("headerless.acmo");
    dateproblemAcmo = loadResource("dateissue.acmo");
    corruptedAcmo = loadResource("corrupted.acmo");
    blanklinesAcmo = loadResource("blanklines.acmo");
    apsim1230Acmo = loadResource("apsimerror.acmo");
  }

  private static Optional<ACMOFile> loadResource(String file) {
    try {
    URL url = ACMOFileTest.class.getResource(file);
    if (url != null) {
      return Optional.ofNullable(new ACMOFile(Paths.get(url.toURI())));
    } else {
      return Optional.empty();
    }
    } catch (URISyntaxException ex) {
      ex.printStackTrace();
      return Optional.empty();
    }
  }

  private static Optional<ACMOFile> cleanAcmo;
  private static Optional<ACMOFile> headerlessAcmo;
  private static Optional<ACMOFile> dateproblemAcmo;
  private static Optional<ACMOFile> corruptedAcmo;
  private static Optional<ACMOFile> blanklinesAcmo;
  private static Optional<ACMOFile> apsim1230Acmo;

  @Test
  public void testCleanHeaderPresent() {
    if (cleanAcmo.isPresent()) {
      assertNotNull(cleanAcmo.get().getHeader());
      assertTrue(cleanAcmo.get().getHeader().isPresent());
    } else {
      fail("Cannot find clean ACMO file");
    }
  }

  @Test
  public void testHeaderlessHeaderPresent() {
    if (headerlessAcmo.isPresent()) {
      assertNotNull(headerlessAcmo.get().getHeader());
      assertFalse(headerlessAcmo.get().getHeader().isPresent());
      displayErrorReport(headerlessAcmo.get());
    } else {
      fail("Cannot find headerless ACMO file");
    }
  }

  @Test
  public void testCleanFormatValid() {
    if (cleanAcmo.isPresent()) {
      assertTrue("Found errors in clean ACMO", cleanAcmo.get().checkFormat());
    } else {
      fail("Cannot find clean ACMO file");
    }
  }

  @Test
  public void testHeaderlessFormatValid() {
    if (headerlessAcmo.isPresent()) {
      assertFalse("No errors found in headerless", headerlessAcmo.get().checkFormat());
      displayErrorReport(headerlessAcmo.get());
    } else {
      fail("Cannot find headerless ACMO file");
    }
  }

  @Test
  public void testDateProblemValid() {
    if (dateproblemAcmo.isPresent()) {
      assertFalse("No errors found in dateissue", dateproblemAcmo.get().checkFormat());
      displayErrorReport(dateproblemAcmo.get());
    } else {
      fail("Cannot find dateissues ACMO file");
    }
  }

  private void displayErrorReport(ACMOFile f) {
    System.out.println("Error Report for " + f.getPath().toString());
    System.out.println(f.getErrorReport());
  }
}
