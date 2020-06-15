package no.geonorge.nedlasting.external.fme;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import no.geonorge.nedlasting.data.client.Area;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import no.geonorge.nedlasting.data.client.Format;
import no.geonorge.nedlasting.data.client.Projection;
import no.geonorge.nedlasting.external.External;
import no.geonorge.nedlasting.external.ExternalStatus;
import no.geonorge.nedlasting.external.fme.data.DataDownloadResponse;
import no.geonorge.nedlasting.external.fme.data.JobInfo;
import no.geonorge.nedlasting.external.fme.data.ListOption;
import no.geonorge.nedlasting.external.fme.data.Parameter;
import no.geonorge.nedlasting.utils.GsonCreator;
import no.geonorge.nedlasting.utils.IOUtils;

public abstract class FMEClient extends External {

    public static final String PARAMETER_URL_PREFIX = "urlPrefix";
    public static final String PARAMETER_USERNAME = "username";
    public static final String PARAMETER_PASSWORD = "password";
    public static final String PARAMETER_REPOSITORY = "repository";
    public static final String PARAMETER_WORKSPACE = "workspace";

    private final String urlPrefix;
    private final String username;
    private final String password;

    private final String repository;
    private final String workspace;

    protected final Map<String, String> epsgCodeByFMEName = new HashMap<String, String>();
    protected final Map<String, String> fMENameByEpsgCode = new HashMap<String, String>();

    private String token;

    protected FMEClient(String urlPrefix, String username, String password, String repository, String workspace) {
        this.urlPrefix = urlPrefix;
        this.username = username;
        this.password = password;
        this.repository = repository;
        this.workspace = workspace;
    }

    protected FMEClient(Map<String, String> parameters) {
        this.urlPrefix = getRequiredParameter(parameters, PARAMETER_URL_PREFIX);
        this.username = getRequiredParameter(parameters, PARAMETER_USERNAME);
        this.password = getRequiredParameter(parameters, PARAMETER_PASSWORD);
        this.repository = getRequiredParameter(parameters, PARAMETER_REPOSITORY);
        this.workspace = getRequiredParameter(parameters, PARAMETER_WORKSPACE);
    }

    public void setFMEProjectionName(String fMEProjectionName, String epsgCode) {
        epsgCodeByFMEName.put(fMEProjectionName, epsgCode);
        fMENameByEpsgCode.put(epsgCode, fMEProjectionName);
    }

    String getToken() throws IOException {
        if (token == null) {
            fetchToken();
        }
        return token;
    }

    String fetchToken() throws IOException {
        String url = urlPrefix + "/fmetoken/generate?user=" + username + "&password=" + password
                + "&expiration=365&timeunit=day";
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        CloseableHttpClient httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
        try {
            HttpGet httpget = new HttpGet(url);
            HttpResponse response = httpclient.execute(httpget);
            HttpEntity entity = response.getEntity();
            token = EntityUtils.toString(entity, "UTF-8");
            return token;
        } finally {
            IOUtils.close(httpclient);
        }
    }

    public List<Projection> getProjections() throws IOException {
        List<Projection> projections = new ArrayList<>();
        String paramName = getProjectionParameterName();
        for (ListOption option : getWorkspaceParameters(paramName).getListOptions()) {
            String epsgCode = epsgCodeByFMEName.get(option.getValue());
            if (epsgCode == null && StringUtils.isNumeric(option.getValue())) {
                epsgCode = option.getValue();
            }
            if (epsgCode == null) {
                continue;
            }
            Projection projection = new Projection();
            projection.setName(option.getDescription());
            projection.setCode(epsgCode);
            projection.setCodespace("http://www.opengis.net/def/crs/EPSG/0/" + epsgCode);
            projections.add(projection);
        }
        return projections;
    }

    public abstract String getProjectionParameterName();
    
    public abstract String getCoordinatesParameterName();

    public abstract String getAreaParameterName(String mapSelectionLayer);

    public Map<String, String> jobPostParameters(Format format, Projection projection, String email, String coordinates) {
        return Collections.emptyMap();
    }

    public List<Format> getFormats() throws IOException {
        List<Format> formats = new ArrayList<>();
        String paramName = getFormatParameterName();
        for (ListOption option : getWorkspaceParameters(paramName).getListOptions()) {
            Format format = new Format();
            format.setName(option.getValue());
            formats.add(format);
        }
        return formats;
    }

    public abstract String getFormatParameterName();

    Parameter getWorkspaceParameters(String parameter) throws IOException {
        String url = urlPrefix + "/fmerest/v2/repositories/" + repository + "/items/" + workspace + "/parameters/"
                + parameter + "?accept=json&details=low";
        String s = httpGET(url);
        return GsonCreator.create().fromJson(s, Parameter.class);
    }

    /**
     * submitJob submits an order to FME DataDownload service
     */
    public String submitJob(Format format, Projection projection, String email, Area area, String mapSelectionLayer) throws IOException {
        String url = urlPrefix + "/fmedatadownload/" + repository + "/" + workspace + "?accept=json&token="
                + getToken();
        Map<String, String> postParameters = new HashMap<>();
        postParameters.put("opt_responseformat", "json");
        postParameters.put("opt_showresult", "false");
        postParameters.put("opt_servicemode", "async");

        if (email != null) {
            postParameters.put("opt_requesteremail", email);
        }

        postParameters.put(getFormatParameterName(), format.getName());

        String projectionValue = fMENameByEpsgCode.get(projection.getCode());
        if (projectionValue == null) {
            projectionValue = projection.getCode();
        }

        postParameters.put(getProjectionParameterName(), projectionValue);

        // Convert areas of type kommune/fylke/other to a list of area-codes
        List<String> areaList = new ArrayList<String>();
        
        areaList.add(area.getCode());
        
        if (areaList.size() > 0) {
            postParameters.put(getAreaParameterName(mapSelectionLayer),StringUtils.join(areaList,','));
        }

        StringBuilder postData = new StringBuilder();
        for (Map.Entry<String, String> e : postParameters.entrySet()) {
            if (postData.length() > 0) {
                postData.append('&');
            }
            postData.append(e.getKey());
            postData.append('=');
            postData.append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }

        String r = httpPOST(url, "application/x-www-form-urlencoded", postData.toString());

        DataDownloadResponse ddr = GsonCreator.create().fromJson(r, DataDownloadResponse.class);

        return ddr == null ? null : ddr.getServiceResponse().getJobID().toString();
    }
    public String submitJob(Format format, Projection projection, String email, String coordinates) throws IOException {
        String url = urlPrefix + "/fmedatadownload/" + repository + "/" + workspace + "?accept=json&token="
                + getToken();

        // https://docs.safe.com/fme/2016.1/html/FME_Server_Documentation/Content/ReferenceManual/service_datadownload.htm

        Map<String, String> postParameters = new HashMap<>();
        postParameters.put("opt_responseformat", "json");
        postParameters.put("opt_showresult", "false");
        postParameters.put("opt_servicemode", "async");
        
        if (email != null) {
            postParameters.put("opt_requesteremail", email);
        }

        postParameters.put(getFormatParameterName(), format.getName());

        String projectionValue = fMENameByEpsgCode.get(projection.getCode());
        if (projectionValue == null) {
            projectionValue = projection.getCode();
        }

        postParameters.put(getProjectionParameterName(), projectionValue);
        
        if (coordinates != null) {
            /*
             * GeoNorge example: 
             * "coordinates": "212062 7057635 323090 7057635 314966 6968271 152486 6966917 210708 7054927 212062 7057635"
             */
            
            /*
             * NGU example:
             * "koordinatListe":"269530.625 6657173.718749999 269530.625 6642829.781249999 254001.9375 6642829.781249999 254001.9375 6657173.718749999 269530.625 6657173.718749999"
             */
            postParameters.put(getCoordinatesParameterName(), coordinates);
        }
        postParameters.putAll(jobPostParameters(format, projection, email, coordinates));

        StringBuilder postData = new StringBuilder();
        for (Map.Entry<String, String> e : postParameters.entrySet()) {
            if (postData.length() > 0) {
                postData.append('&');
            }
            postData.append(e.getKey());
            postData.append('=');
            postData.append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }

        String r = httpPOST(url, "application/x-www-form-urlencoded", postData.toString());

        DataDownloadResponse ddr = GsonCreator.create().fromJson(r, DataDownloadResponse.class);

        return ddr == null ? null : ddr.getServiceResponse().getJobID().toString();
    }

    public ExternalStatus status(String jobId) throws IOException {
        String url = urlPrefix + "/fmerest/v3/transformations/jobs/id/" + jobId;// +
                                                                                // "/result";//?token="+getToken();
        String r = httpGET(url);
        JobInfo jobInfo = GsonCreator.create().fromJson(r, JobInfo.class);
        ExternalStatus status = new ExternalStatus();
        if (jobInfo != null && jobInfo.getResult() != null) {
            status.setStatusMessage(jobInfo.getResult().getStatusMessage());
            status.setDownloadUrl(jobInfo.getResult().getResultDatasetDownloadUrl());
        }
        return status;
    }

    private String httpGET(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("Authorization", "fmetoken token=" + getToken());
        if (conn.getResponseCode() != 200) {
            throw new IOException(
                    "GET got " + conn.getResponseCode() + " " + conn.getResponseMessage() + " from " + url);
        }
        String s = IOUtils.toString(conn.getInputStream());
        System.out.println(url + " : ");
        System.out.println(s);
        return s;
    }

    private String httpPOST(String url, String contentType, String data) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("Authorization", "fmetoken token=" + getToken());
        conn.setRequestMethod("POST");

        if (data != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            conn.getOutputStream().write(data.getBytes("UTF-8"));
        }

        if (conn.getResponseCode() != 200) {
            throw new IOException(
                    "POST got " + conn.getResponseCode() + " " + conn.getResponseMessage() + " from " + url);
        }
        String s = IOUtils.toString(conn.getInputStream());
        System.out.println(url + " : ");
        System.out.println(s);
        return s;
    }

}
