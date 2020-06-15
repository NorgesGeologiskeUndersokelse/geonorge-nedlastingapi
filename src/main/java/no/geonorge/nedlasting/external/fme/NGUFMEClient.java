package no.geonorge.nedlasting.external.fme;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

import no.geonorge.nedlasting.data.client.Area;
import no.geonorge.nedlasting.data.client.Format;
import no.geonorge.nedlasting.data.client.Projection;
import no.geonorge.nedlasting.utils.GeometryUtils;

public class NGUFMEClient extends FMEClient {

    public static final String PARAMETER_TYPE_VALUE = "ngufme";

    public NGUFMEClient(String urlPrefix, String username, String password, String repository, String workspace) {
        super(urlPrefix, username, password, repository, workspace);
        configure();
    }

    public NGUFMEClient(Map<String, String> parameters) {
        super(parameters);
        configure();
    }

    private void configure() {
        setFMEProjectionName("ETRS89.UTM-32N", "25832");
        setFMEProjectionName("ETRS89.UTM-33N", "25833");
        setFMEProjectionName("ETRS89.UTM-35N", "25835");
        setFMEProjectionName("UTM84-32N", "32632");
        setFMEProjectionName("UTM84-33N", "32633");
        setFMEProjectionName("UTM84-35N", "32635");
        setFMEProjectionName("LL-WGS84", "4326");
        setFMEProjectionName("LL-ETRF89", "4258");
    }

    @Override
    public String getProjectionParameterName() {
        return "LeveranseKoordSystem";
    }

    @Override
    public String getFormatParameterName() {
        return "Dataformat";
    }

    @Override
    public String getCoordinatesParameterName() {
        return "koordinatListe";
    }

    @Override
    public String getAreaParameterName(String areaType) {
        if (areaType.equalsIgnoreCase("kommune")) {
            return "Kommunenr";
        } else if (areaType.equalsIgnoreCase("raster-n50")) {
            return "n50kartblad";
        } else {
            return "unknown";
        }
    }
    
    @Override
    public Map<String, String> jobPostParameters(Format format, Projection projection, String email,
            String coordinates) {
        
        Map<String, String> m = new HashMap<>(super.jobPostParameters(format, projection, email, coordinates));
        
        if (coordinates != null) {
            m.put("Kommunenr", "-1");
            
            Geometry geometry = GeometryUtils.parseCoordList(coordinates);
            if (geometry != null) {
                Envelope e = geometry.getEnvelopeInternal();
                m.put("ENVELOPE_MINX", Double.toString(e.getMinX()));
                m.put("ENVELOPE_MINY", Double.toString(e.getMinY()));
                m.put("ENVELOPE_MAXX", Double.toString(e.getMaxX()));
                m.put("ENVELOPE_MAXY", Double.toString(e.getMaxY()));
            }
        }
        
        return Collections.unmodifiableMap(m);
    }

	@Override
	public List<Area> getArea() throws IOException {
		// TODO Auto-generated method stub
		return new ArrayList<Area>();
	}

}
