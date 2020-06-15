package no.geonorge.nedlasting.external;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import no.geonorge.nedlasting.data.client.Area;
import no.geonorge.nedlasting.data.client.Format;
import no.geonorge.nedlasting.data.client.Projection;
import no.geonorge.nedlasting.external.fme.NGUFMEClient;

public abstract class External {

    public static final String PARAMETER_TYPE = "type";

    public abstract List<Projection> getProjections() throws IOException;

    public abstract List<Format> getFormats() throws IOException;
    
    public abstract List<Area> getArea() throws IOException;

    public abstract String submitJob(Format format, Projection projection, String email, String coordinates) throws IOException;

    public abstract String submitJob(Format format, Projection projection, String email, Area area, String mapSelectionLayer) throws IOException;

    public abstract ExternalStatus status(String jobId) throws IOException;

    public static External create(Map<String, String> parameters) {
        String type = getRequiredParameter(parameters, PARAMETER_TYPE);
        if (NGUFMEClient.PARAMETER_TYPE_VALUE.equals(type)) {
            return new NGUFMEClient(parameters);
        }
        throw new IllegalArgumentException("unknown external type");
    }

    protected static String getRequiredParameter(Map<String, String> parameters, String key) {
        String v = parameters.get(key);
        if (v == null) {
            throw new IllegalArgumentException("missing required external parameter: '" + key + "'");
        }
        return v;
    }

}
