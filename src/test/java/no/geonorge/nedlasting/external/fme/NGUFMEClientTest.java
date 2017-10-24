package no.geonorge.nedlasting.external.fme;

import java.io.IOException;
import java.util.List;

import junit.framework.TestCase;
import no.geonorge.nedlasting.data.client.Format;
import no.geonorge.nedlasting.data.client.Projection;
import no.geonorge.nedlasting.external.fme.FMEClient;
import no.geonorge.nedlasting.external.fme.NGUFMEClient;
import no.geonorge.nedlasting.external.fme.data.JobInfo;
import no.geonorge.nedlasting.external.fme.data.JobResult;

public class NGUFMEClientTest extends TestCase {

    private String urlPrefix = System.getProperty("fme.urlPrefix");
    private String username = System.getProperty("fme.username");
    private String password = System.getProperty("fme.password");

    private String repository = "Datanedlasting";
    private String workspace = "GrunnvannBorehull_Nedlasting.fmw";

    private FMEClient fme() {
        return new NGUFMEClient(urlPrefix, username, password, repository, workspace);
    }

    public void testCreateToken() throws IOException {
        FMEClient fme = fme();
        String token = fme.fetchToken();
        System.out.println("token: " + token);
        assertNotNull(token);
        assertEquals(40, token.length());
    }

    public void testProjections() throws IOException {
        List<Projection> projections = fme().getProjections();
        assertFalse(projections.isEmpty());
    }

    public void testFormats() throws IOException {
        List<Format> formats = fme().getFormats();
        assertFalse(formats.isEmpty());
    }

    public void testSubmitJob() throws IOException, InterruptedException {
        FMEClient fme = fme();
        List<Format> formats = fme.getFormats();
        List<Projection> projections = fme.getProjections();
        String jobId = fme.submitJob(formats.get(0), projections.get(0));
        assertNotNull(jobId);

        JobResult jr = null;
        for (int i = 0; i < 100; i++) {
            Thread.sleep(5000);

            JobInfo r = fme.status(jobId);
            assertEquals(jobId, r.getId());

            jr = r.getResult();
            if (jr != null) {
                break;
            }

        }

        assertNotNull(jr);
        assertEquals(jobId, jr.getId());
        assertTrue(jr.getStatusMessage().contains("Successful"));
        assertNotNull(jr.getResultDatasetDownloadUrl());
    }

}
