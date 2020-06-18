package no.geonorge.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.SelectQuery;
import org.apache.commons.io.FilenameUtils;
import org.jdom2.Element;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rometools.rome.feed.synd.SyndCategory;
import com.rometools.rome.feed.synd.SyndCategoryImpl;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.feed.synd.SyndLink;
import com.rometools.rome.feed.synd.SyndLinkImpl;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import com.vividsolutions.jts.geom.Geometry;

import no.geonorge.nedlasting.config.Config;
import no.geonorge.nedlasting.data.Dataset;
import no.geonorge.nedlasting.data.DatasetFile;
import no.geonorge.nedlasting.data.DownloadItem;
import no.geonorge.nedlasting.data.DownloadOrder;
import no.geonorge.nedlasting.data.client.Area;
import no.geonorge.nedlasting.data.client.CanDownloadRequest;
import no.geonorge.nedlasting.data.client.CanDownloadResponse;
import no.geonorge.nedlasting.data.client.Capabilities;
import no.geonorge.nedlasting.data.client.DownloadErrorResponse;
import no.geonorge.nedlasting.data.client.File;
import no.geonorge.nedlasting.data.client.Format;
import no.geonorge.nedlasting.data.client.Order;
import no.geonorge.nedlasting.data.client.OrderArea;
import no.geonorge.nedlasting.data.client.OrderLine;
import no.geonorge.nedlasting.data.client.Projection;
import no.geonorge.nedlasting.external.CodelistRegistry;
import no.geonorge.nedlasting.external.External;
import no.geonorge.nedlasting.external.data.RegisterItem;
import no.geonorge.nedlasting.security.FileProxyURLGenerator;
import no.geonorge.nedlasting.utils.GeometryUtils;
import no.geonorge.nedlasting.utils.GsonCreator;
import no.geonorge.nedlasting.utils.IOUtils;

/**
 * This REST api implements the Norway Digital (Geonorge) Download API
 * For more information: http://nedlasting.geonorge.no/Help
 */
@Path("api")
public class DownloadService {
    
    @Context
    UriInfo uri;
    
    private static final Logger log = Logger.getLogger(DownloadService.class.getName());

    private String getUrlPrefix() {
        if (uri == null) {
            return "";
        }
        return uri.getBaseUri().toString() + "api/";
    }
    
    private boolean isPretty() {
        if (uri == null) {
            return false;
        }
        if (!uri.getQueryParameters().containsKey("pretty")) {
            return false;
        }
        return !uri.getQueryParameters().getFirst("pretty").equalsIgnoreCase(Boolean.FALSE.toString());
    }
    
    private Gson gson() {
        GsonBuilder b = GsonCreator.builder();
        if (isPretty()) {
            b = b.setPrettyPrinting();
        }
        return b.create();
    }

    @GET
    @Path("status")
    @Produces(MediaType.TEXT_PLAIN)
    public Response returnStatus() throws IOException {
        try {
            ObjectContext ctxt = Config.getObjectContext();
            SelectQuery query = new SelectQuery(Dataset.class);
            query.setFetchLimit(1);
            ctxt.performQuery(query);
        } catch (RuntimeException e) {
            return Response.serverError().build();
        }

        return Response.ok("ok", MediaType.TEXT_PLAIN).build();
    }

    @GET
    @Path("capabilities/{metadataUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response returnCapabilities(@PathParam("metadataUuid") String metadataUuid) throws IOException {
        ObjectContext ctxt = Config.getObjectContext();
        Dataset dataset = Dataset.forMetadataUUID(ctxt, metadataUuid);
        if (dataset == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Capabilities ct = dataset.getCapabilities(getUrlPrefix());
        String json = gson().toJson(ct);
        return Response.ok(json, MediaType.APPLICATION_JSON).build();
    }
	
	/**
	 * 
	 * @param metadataUuid
	 * @return json of valid file formats of a given metadataUuid
	 * @throws Exception
	 */
    @GET
    @Path("v2/codelists/format/{metadataUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response returnFormats(@PathParam("metadataUuid") String metadataUuid) throws IOException {
        /*
         * http://nedlasting.geonorge.no/Help/Api/GET-api-codelists-format-
         * metadataUuid
         */
        ObjectContext ctxt = Config.getObjectContext();
        Dataset dataset = Dataset.forMetadataUUID(ctxt, metadataUuid);
        if (dataset == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Collection<Format> formats = dataset.getFormats();
        String json = gson().toJson(formats);
        return Response.ok(json, MediaType.APPLICATION_JSON).build();
    }

    /**
     * 
     * @param metadataUuid
     * @return json of valid areas of a given metadataUuid
     * @throws Exception
     */
    @GET
    @Path("v2/codelists/area/{metadataUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response returnAreas(@PathParam("metadataUuid") String metadataUuid) throws IOException {
        /*
         * http://nedlasting.geonorge.no/Help/Api/GET-api-codelists-area-
         * metadataUuid
         */
        ObjectContext ctxt = Config.getObjectContext();
        Dataset dataset = Dataset.forMetadataUUID(ctxt, metadataUuid);
        if (dataset == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        
        List<Area> areas = dataset.getAreas();
        String json = gson().toJson(areas);
        return Response.ok(json, MediaType.APPLICATION_JSON).build();
    }

    /**
     * 
     * @param metadataUuid
     * @return json of valid projections of a given metadataUuid
     * @throws Exception
     */
    @GET
    @Path("v2/codelists/projection/{metadataUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response returnProjections(@PathParam("metadataUuid") String metadataUuid) throws IOException {
        /*
         * http://nedlasting.geonorge.no/Help/Api/GET-api-codelists-projection-
         * metadataUuid
         */
        ObjectContext ctxt = Config.getObjectContext();
        Dataset dataset = Dataset.forMetadataUUID(ctxt, metadataUuid);
        if (dataset == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Collection<Projection> projections = dataset.getProjections();
        String json = gson().toJson(projections);
        return Response.ok(json, MediaType.APPLICATION_JSON).build();
    }
	
    @POST
    @Path("v2/can-download")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String canDownload(String jsonRequest) throws IOException {
        log.info("can-download request: " + jsonRequest);
        CanDownloadRequest req = gson().fromJson(jsonRequest, CanDownloadRequest.class);

        ObjectContext ctxt = Config.getObjectContext();
        Dataset dataset = Dataset.forMetadataUUID(ctxt, req.getMetadataUuid());
        CanDownloadResponse canDownload = new CanDownloadResponse();
        if (dataset == null) {
            log.info("could not find dataset");
            canDownload.setCanDownload(false);
            canDownload.setMessage("could not find dataset");
            return gson().toJson(canDownload);
        }

        // check if can select polygon
        if (req.hasCoordinates() && !dataset.isSupportsPolygonSelection()) {
            log.info("trying to select polygon, but dataset does not support it");
            canDownload.setCanDownload(false);
            canDownload.setMessage("trying to select polygon, but dataset does not support it");
            return gson().toJson(canDownload);
        }
        
        // check coordinates
        if (req.hasCoordinates() && dataset.isSupportsPolygonSelection()) {
            Geometry geom = null;
            try {
                geom = req.getGeometry();
                if (geom == null || geom.isEmpty() || !geom.isValid()) {
                    canDownload.setCanDownload(false);
                    canDownload.setMessage("coordinates is not valid polygon");
                    return gson().toJson(canDownload);
                }
            } catch (RuntimeException e) {
                canDownload.setCanDownload(false);
                canDownload.setMessage("not able to parse coordinates. " + e.getMessage());
                return gson().toJson(canDownload);
            }
            
            // check if selected polygon is too large
            if (dataset.getMaxArea() != null) {
                try {
                    double selectedSquareKvm = GeometryUtils.calculateAreaSquareKilometer(geom, req.getSrid());
                    if (selectedSquareKvm > dataset.getMaxArea().doubleValue()) {
                        canDownload.setCanDownload(false);
                        canDownload.setMessage("too large area");
                        return gson().toJson(canDownload);
                    }
                } catch (RuntimeException e) {
                    canDownload.setCanDownload(false);
                    canDownload.setMessage("not able to calculate area. " + e.getMessage());
                    return gson().toJson(canDownload);
                }
            }
        }
        
       
        canDownload.setCanDownload(true);
        return gson().toJson(canDownload);
    }

    /**
     * 
     * @param referenceNumber
     * @return json of valid projections of a given metadataUuid
     * @throws Exception
     */
    @GET
    @Path("v2/order/{referenceNumber}")
    @Produces(MediaType.APPLICATION_JSON)
	public Response getOrderInfo(@PathParam("referenceNumber") String referenceNumber) throws IOException {
        ObjectContext ctxt = Config.getObjectContext();
        DownloadOrder order = DownloadOrder.get(ctxt, referenceNumber);
        if (order == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String json = gson().toJson(order.getOrderReceipt(getUrlPrefix()));
        return Response.ok(json, MediaType.APPLICATION_JSON).build();
	}

    /**
     * 
     *
     * @return String json with reference to order
     * @throws Exception
     */
    @POST
    @Path("v2/order")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response orderDownload(String jsonRequest) throws IOException {
        /* https://nedlasting.geonorge.no/Help/Api/POST-api-v2-order */
        log.info("order request: " + jsonRequest);
        Order order = gson().fromJson(jsonRequest, Order.class);

        ObjectContext ctxt = Config.getObjectContext();

        DownloadOrder downloadOrder = ctxt.newObject(DownloadOrder.class);
        downloadOrder.setEmail(order.getEmail());
        downloadOrder.setStartTime(new Date());
        downloadOrder.setReferenceNumber(UUID.randomUUID().toString());
    
        for (OrderLine orderLine : order.getOrderLines()) {
            
            List<OrderArea> areaRest = new ArrayList<>(orderLine.getAreas());
            List<Format> formatRest = new ArrayList<>(orderLine.getFormats());
            List<Projection> projectionRest = new ArrayList<>(orderLine.getProjections());
            
            Dataset dataset = Dataset.forMetadataUUID(ctxt, orderLine.getMetadataUuid());

            /* An order can be a combination of preproduced files and polygon from map */
            for (DatasetFile datasetFile : DatasetFile.findForOrderLine(ctxt, orderLine)) {
                DownloadItem downloadItem = ctxt.newObject(DownloadItem.class);
                downloadItem.setProjection(datasetFile.getProjection());
                downloadItem.setUrl(datasetFile.getUrl());
                downloadItem.setFileId(datasetFile.getFileId());
                downloadItem.setFileName(datasetFile.getFileName());
                downloadItem.setMetadataUuid(datasetFile.getDataset().getMetadataUuid());
                downloadOrder.addToItems(downloadItem);
                
                areaRest.remove(datasetFile.getOrderArea());
                formatRest.remove(datasetFile.getFormat());
                projectionRest.remove(datasetFile.getProjection().forClient());
            }

            /* Remove the combinations that are already preproduced files */
            if (!areaRest.isEmpty() && !formatRest.isEmpty() && !projectionRest.isEmpty() && dataset.isExternal()) {
                for (Format format : orderLine.getFormats()) {
                    for (Projection projection : orderLine.getProjections()) {
                        for (OrderArea area : orderLine.getAreas()) {
                            if (area.isTypePolygonSelection() && orderLine.hasCoordinates()) {
                                areaRest.remove(area);
                                area = null;
                            }
                            External external = dataset.getExternal();
			    // TODO: Support cells (municipalities,counties,map sheets etc) enabling use of 
	                    // administrative area codes in the external-API
                            String jobId = external.submitJob(format, projection, order.getEmail(),
                                    orderLine.getCoordinates());
                            
                            DownloadItem downloadItem = ctxt.newObject(DownloadItem.class);
                            downloadItem.setSrid(projection.getSrid());
                            downloadItem.setFileId(UUID.randomUUID().toString());
                            downloadItem.setMetadataUuid(dataset.getMetadataUuid());
                            downloadItem.setExternalJobId(jobId);
                            downloadItem.setCoordinates(orderLine.getCoordinates());
                            downloadOrder.addToItems(downloadItem);
                            
                            areaRest.remove(area);
                            formatRest.remove(format);
                            projectionRest.remove(projection);
                        }
                    }
                }
            }
            
            DownloadErrorResponse r = new DownloadErrorResponse(orderLine);
            r.setAreaRest(areaRest);
            r.setFormatRest(formatRest);
            r.setProjectionRest(projectionRest);
            if (r.hasRest()) {
                return Response.status(Response.Status.BAD_REQUEST).entity(gson().toJson(r))
                        .type(MediaType.APPLICATION_JSON).build();
            }
            
        }

        ctxt.commitChanges();
        
        String json = gson().toJson(downloadOrder.getOrderReceipt(getUrlPrefix()));
        return Response.ok(json, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("v2/download/order/{orderUuid}/{fileId}")
    public Response getFileForOrder(@PathParam("orderUuid") String orderUuid,@PathParam("fileId") String fileId) {
        ObjectContext ctxt = Config.getObjectContext();

        DownloadOrder order = DownloadOrder.get(ctxt, orderUuid);
        if (order == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        try {
            Date orderDate = order.getOrderReceipt(getUrlPrefix()).getOrderDate();
            LocalDate then = orderDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate now = LocalDate.now();
            long days = ChronoUnit.DAYS.between(then, now);
            if (days > Config.getOrderDaysValid()) {
                // Fetch your file within a working week please
                Response.status(Response.Status.GONE).build();
            }
        } catch (IOException ignored) {
            Response.status(Response.Status.NOT_FOUND).build();
        }
        
        DownloadItem item = order.getItemForFileId(fileId);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        try {
            return createResponseFromRemoteFile(item.getUrl());
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET
    @Path("fileproxy/{metadataUuid}/{fileId}")
    public Response getFileForDownload(@PathParam("metadataUuid") String metadataUuid,@PathParam("fileId") String fileId){
            ObjectContext ctxt = Config.getObjectContext();

            /* Store downloads for statistical use */
            DownloadOrder downloadOrder = ctxt.newObject(DownloadOrder.class);
            downloadOrder.setStartTime(new Date());
            downloadOrder.setReferenceNumber(UUID.randomUUID().toString());

            Dataset dataset = Dataset.forMetadataUUID(ctxt, metadataUuid);
            if (dataset == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            DatasetFile datasetFile = dataset.getFile(fileId);
            if (datasetFile == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            try {
                DownloadItem downloadItem = ctxt.newObject(DownloadItem.class);
                downloadItem.setProjection(datasetFile.getProjection());
                downloadItem.setFileId(datasetFile.getFileId());
                downloadItem.setFileName(datasetFile.getFileName());
                downloadItem.setMetadataUuid(dataset.getMetadataUuid());
                downloadOrder.addToItems(downloadItem);
                ctxt.commitChanges();

                return createResponseFromRemoteFile(datasetFile.getUrl());
            } catch (IOException e) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }        
    }

    private Response createResponseFromRemoteFile(String urlString) throws IOException {

        String extension = FilenameUtils.getExtension(urlString);
        String baseName = FilenameUtils.getBaseName(urlString);
        String fileName = baseName.concat(".").concat(extension);
        
        URL url = new URL(urlString);
        // Check if fileType and remote host is allowed
        if (!Config.getAllowedFiletypes().isEmpty() && !Config.getAllowedFiletypes().contains(extension.toLowerCase())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (!Config.getAllowedHosts().isEmpty() && !Config.getAllowedHosts().contains(url.getHost().toLowerCase())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        // Return file proxied by this API
        HttpURLConnection uc = (HttpURLConnection) url.openConnection();
        if (uc.getResponseCode() != 200) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Client client = ClientBuilder.newClient();
        final InputStream responseStream = client.target(urlString).request().get(InputStream.class);
        StreamingOutput output = new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException, WebApplicationException  {
                try {
                    IOUtils.copy(responseStream, out);
                    responseStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new WebApplicationException ("File not found");
                }
            }
        };

        return Response.ok(output,MediaType.APPLICATION_OCTET_STREAM).header(
                "Content-Disposition","attachment; filename=\"" + fileName + "\"").build();
    }

    @GET
    @Path("internal/dataset")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDatasets() {
        ObjectContext ctxt = Config.getObjectContext();
        List<no.geonorge.nedlasting.data.client.Dataset> datasetViews = new ArrayList<>();
        for (Dataset dataset : Dataset.getAll(ctxt)) {
            datasetViews.add(dataset.forClientWithoutFiles());
        }
        String json = gson().toJson(datasetViews);
        return Response.ok(json, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("internal/dataset/{metadataUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDataset(@PathParam("metadataUuid") String metadataUuid) {
        ObjectContext ctxt = Config.getObjectContext();
        Dataset dataset = Dataset.forMetadataUUID(ctxt, metadataUuid);
        if (dataset == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String json = gson().toJson(dataset.forClient(getUrlPrefix()));
        return Response.ok(json, MediaType.APPLICATION_JSON).build();
    }
    
    @PUT
    @Path("internal/dataset/{metadataUuid}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response putDataset(@PathParam("metadataUuid") String metadataUuid, String jsonRequest) throws IOException {

        no.geonorge.nedlasting.data.client.Dataset requestDataset = gson().fromJson(jsonRequest,
                no.geonorge.nedlasting.data.client.Dataset.class);
        
        if (requestDataset.isExternal()) {
            // test external configuration
            try {
                External e = External.create(requestDataset.getExternalParameters());
                if (e == null) {
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
            } catch (RuntimeException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }
        
        ObjectContext ctxt = Config.getObjectContext();
        Dataset dataset = Dataset.forMetadataUUID(ctxt, metadataUuid);
        if (dataset == null) {
            dataset = ctxt.newObject(Dataset.class);
            dataset.setMetadataUuid(metadataUuid);
        }

        dataset.setTitle(requestDataset.getTitle());
        dataset.setMaxArea(requestDataset.getMaxArea());
        dataset.setInspireIdCode(requestDataset.getInspireIdCode());
        dataset.setInspireIdNamespace(requestDataset.getInspireIdNamespace());

        if (!requestDataset.ignoreFiles()) {
            Set<String> restFileIds = new HashSet<>(dataset.getFileIds());
            for (File file : requestDataset.getFiles()) {
                restFileIds.remove(file.getFileId());
                DatasetFile datasetFile = dataset.getFile(file.getFileId());
                if (datasetFile == null) {
                    datasetFile = ctxt.newObject(DatasetFile.class);
                    datasetFile.setFileId(file.getFileId());
                    dataset.addToFiles(datasetFile);
                }
                datasetFile.setUrl(file.getDownloadUrl());
                datasetFile.setAreaCode(file.getArea());
                datasetFile.setAreaName(file.getAreaName());
                datasetFile.setAreaType(file.getAreaType());
                datasetFile.setProjection(no.geonorge.nedlasting.data.Projection.getForSrid(ctxt,
                        Integer.parseInt(file.getProjection())));
                datasetFile.setFormatName(file.getFormat());
                datasetFile.setFileName(file.getName());
                datasetFile.setFileDate(file.getFileDate() == null ? new Date() : file.getFileDate());
            }
            for (String fileId : restFileIds) {
                DatasetFile file = dataset.getFile(fileId);
                if (file != null) {
                    ctxt.deleteObject(file);
                }
            }
        }
        
        if (requestDataset.isExternal()) {
            dataset.setExternal(requestDataset.getExternalParameters());
        } else {
            dataset.setExternal(Collections.emptyMap());
        }
        
        dataset.setSupportsAreaSelection(!dataset.getAreas().isEmpty());
        dataset.setSupportsFormatSelection(!dataset.getFormats().isEmpty());
        dataset.setSupportsProjectionSelection(!dataset.getProjections().isEmpty());
        dataset.setSupportsPolygonSelection(dataset.isExternal());

        ctxt.commitChanges();
        
        return Response.status(Response.Status.OK).build();
    }

    @POST
    @Path("internal/dataset/{metadataUuid}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response postDataset(@PathParam("metadataUuid") String metadataUuid, String jsonRequest) throws IOException {
        return putDataset(metadataUuid, jsonRequest);
    }

    @DELETE
    @Path("internal/dataset/{metadataUuid}")
    public Response deleteDataset(@PathParam("metadataUuid") String metadataUuid) {
        ObjectContext ctxt = Config.getObjectContext();
        Dataset dataset = Dataset.forMetadataUUID(ctxt, metadataUuid);
        if (dataset == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        ctxt.deleteObject(dataset);
        ctxt.commitChanges();
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @GET
    @Path("atomfeeds")
    public Response getAtomFeeds() {
        ObjectContext ctxt = Config.getObjectContext();

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("atom_1.0");
        feed.setTitle("GEONORGE DATASET ATOM FEEDS ");
        feed.setDescription("ATOM Feeds for Datasets");
        
        List<SyndLink> feedLinks = new ArrayList<>(2);
        SyndLinkImpl feedAlternateLink = new SyndLinkImpl();
        feedAlternateLink.setHref(getUrlPrefix().concat("atomfeeds"));
        feedAlternateLink.setRel("alternate");
        feedLinks.add(feedAlternateLink);
        
        if (Config.getGeonorgeServiceUUID() != null && Config.getGeonorgeServiceUUID().length() > 0) {
            SyndLinkImpl feedDescribedByLink = new SyndLinkImpl();
            feedDescribedByLink.setHref(
                    "https://www.geonorge.no/geonetwork/srv/nor/xml_iso19139?uuid=" + Config.getGeonorgeServiceUUID());
            feedDescribedByLink.setRel("describedby");
            feedDescribedByLink.setType("application/xml");
            feedDescribedByLink.setTitle("Download Service Feed Metadata");
            feedLinks.add(feedDescribedByLink);
        }
        
        feed.setLinks(feedLinks);
        
        Date lastDate = null;

        List<SyndEntry> entries = new ArrayList<>();
        for (Dataset dataset : Dataset.getAll(ctxt)) {
            // Do not add entry when dataset has no files
            if (dataset.getFiles().isEmpty()) {
                continue;
            }

            SyndEntry entry = new SyndEntryImpl();
            entry.setTitle(dataset.getTitle());
            
            Date lastFileDate = dataset.getLastFileDate();
            if (lastFileDate != null) {
                entry.setPublishedDate(lastFileDate);
                entry.setUpdatedDate(lastFileDate);
                if (lastDate == null || lastDate.before(lastFileDate)) {
                    lastDate = lastFileDate;
                }
            }
            
            SyndContent description = new SyndContentImpl();
            description.setType(MediaType.TEXT_PLAIN);
            description.setValue("Dataset ATOM Feed");
            entry.setDescription(description);
            
            List<SyndLink> links = new ArrayList<>(2);
            
            SyndLinkImpl alternateLink = new SyndLinkImpl();
            alternateLink.setHref(getUrlPrefix() + "atom/" + dataset.getMetadataUuid());
            alternateLink.setRel("alternate");
            links.add(alternateLink);
            
            if (dataset.getMetadataUuid() != null) {
                SyndLinkImpl describedbyLink = new SyndLinkImpl();
                describedbyLink.setHref("https://www.geonorge.no/geonetwork/srv/nor/xml_iso19139?uuid="
                        .concat(dataset.getMetadataUuid()));
                describedbyLink.setRel("describedby");
                describedbyLink.setTitle("Dataset Metadata");
                describedbyLink.setType("application/xml");
                links.add(describedbyLink);
            }
            
            entry.setLinks(links);
            
            List<Element> foreignMarkup = new ArrayList<>(2);

            if (dataset.getInspireIdCode() != null) {
                Element spiCode = new Element("spatial_dataset_identifier_code", "inspire_dls",
                        "http://inspire.service/metadata");
                spiCode.addContent(dataset.getInspireIdCode());
                foreignMarkup.add(spiCode);
            }

            if (dataset.getInspireIdNamespace() != null) {
                Element spiNamespace = new Element("spatial_dataset_identifier_namespace", "inspire_dls",
                        "http://inspire.service/metadata");
                spiNamespace.addContent(dataset.getInspireIdNamespace());
                foreignMarkup.add(spiNamespace);
            }

            entry.setForeignMarkup(foreignMarkup);
                        
            entries.add(entry);
        }
        feed.setEntries(entries);
        
        if (lastDate != null) {
            feed.setPublishedDate(lastDate);
        }

        try {
            String atom = new SyndFeedOutput().outputString(feed);
            return Response.ok(atom,MediaType.APPLICATION_ATOM_XML).build();
        } catch (FeedException e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
    }
    @GET
    @Path("atom/{metadataUuid}")
    public Response getAtomFeed(@PathParam("metadataUuid") String metadataUuid) {
        CodelistRegistry clReg = new CodelistRegistry();
        List<RegisterItem> vectorFormats = clReg.getVectorFormats();
        List<RegisterItem> areaCategories = clReg.getAreaCategories();
        List<RegisterItem> crsCodes = clReg.getCrsCodes();
        ObjectContext ctxt = Config.getObjectContext();
        Dataset dataset = Dataset.forMetadataUUID(ctxt, metadataUuid);
        if (dataset == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("atom_1.0");
        feed.setTitle(dataset.getTitle());
        feed.setDescription(dataset.getTitle() + " ATOM Feed");
        
        List<SyndLink> links = new ArrayList<>(2);

        SyndLinkImpl alternateLink = new SyndLinkImpl();
        alternateLink.setHref(getUrlPrefix() + "atom/" + dataset.getMetadataUuid());
        alternateLink.setRel("alternate");
        links.add(alternateLink);

        SyndLinkImpl describedbyLink = new SyndLinkImpl();
        describedbyLink.setHref(
                "https://www.geonorge.no/geonetwork/srv/nor/xml_iso19139?uuid=".concat(dataset.getMetadataUuid()));
        describedbyLink.setRel("describedby");
        describedbyLink.setTitle("Dataset Metadata");
        describedbyLink.setType("application/xml");
        links.add(describedbyLink);

        feed.setLinks(links);
        
        List<Element> foreignMarkup = new ArrayList<>(2);
        
        if (dataset.getInspireIdCode() != null) {
            Element spiCode = new Element("spatial_dataset_identifier_code", "inspire_dls",
                    "http://inspire.service/metadata");
            spiCode.addContent(dataset.getInspireIdCode());
            foreignMarkup.add(spiCode);
        }

        if (dataset.getInspireIdNamespace() != null) {
            Element spiNamespace = new Element("spatial_dataset_identifier_namespace", "inspire_dls",
                    "http://inspire.service/metadata");
            spiNamespace.addContent(dataset.getInspireIdNamespace());
            foreignMarkup.add(spiNamespace);
        }
        
        feed.setForeignMarkup(foreignMarkup);

        Date lastFileDate = dataset.getLastFileDate();
        if (lastFileDate != null) {
            feed.setPublishedDate(lastFileDate);
        }

        List<SyndEntry> entries = new ArrayList<>();
        List<DatasetFile> datasetFiles = dataset.getFiles();

        for (DatasetFile datasetFile:datasetFiles) {
            SyndEntry entry = new SyndEntryImpl();
            StringBuilder sb = new StringBuilder();
            sb.append(dataset.getTitle());
            sb.append("-").append(datasetFile.getAreaType());
            sb.append("-").append(datasetFile.getAreaName());
            sb.append("-").append(datasetFile.getProjection().getName());
            sb.append("-").append(datasetFile.getFormat().getName());
            entry.setTitle(sb.toString());
            if (datasetFile.getFileDate() != null) {
                entry.setPublishedDate(datasetFile.getFileDate());
                entry.setUpdatedDate(datasetFile.getFileDate());
            }
            entry.setLink(FileProxyURLGenerator.createUrl(getUrlPrefix(), datasetFile));
            
            List<SyndCategory> categories = new ArrayList<>();
            
            SyndCategory formatCategory = new SyndCategoryImpl();
            formatCategory.setName("Format:" + datasetFile.getFormatName());
            formatCategory.setTaxonomyUri("https://register.geonorge.no/api/metadata-kodelister/vektorformater.xml");
            categories.add(formatCategory);

            SyndCategory areaCategory = new SyndCategoryImpl();
            // Support category labels. Requires patched rometools from https://github.com/bgrotan/rome/tree/syndcategorylabel
            areaCategory.setName(getLabelForCode(areaCategories,datasetFile.getAreaType()));
            areaCategory.setLabel(datasetFile.getAreaName()); // <category term="Fylke" label="Akershus"/>
            areaCategory.setTaxonomyUri("https://register.geonorge.no/api/metadata-kodelister/geografisk-distribusjonsinndeling.xml");
            categories.add(areaCategory);

            SyndCategory crsCategory = new SyndCategoryImpl();
            crsCategory.setName(datasetFile.getProjection().getAuthorityAndCode());
            crsCategory.setTaxonomyUri(datasetFile.getProjection().getScheme());
            // Support category labels. Requires patched rometools from https://github.com/bgrotan/rome/tree/syndcategorylabel
            crsCategory.setLabel(getLabelForCode(crsCodes,datasetFile.getProjection().getSrid().toString()));
            categories.add(crsCategory);
            
            entry.setCategories(categories);
            
            /* <summary />*/
            SyndContent description = new SyndContentImpl();
            description.setType(MediaType.TEXT_PLAIN);
            description.setValue(dataset.getTitle().concat("-").concat(datasetFile.getFileName()));

            entry.setDescription(description);
            entries.add(entry);
        }
        feed.setEntries(entries);
        try {
            String atom = new SyndFeedOutput().outputString(feed);
            return Response.ok(atom,MediaType.APPLICATION_ATOM_XML).build();
        } catch (FeedException e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
    }

    private String getLabelForCode(List<RegisterItem> items,String code) {
        // areaType is not syncronized with this codelist
        if (code.equalsIgnoreCase("fylke")) {
            code = "fylkesvis";
        }
        if (code.equalsIgnoreCase("landsdekkende")) {
            code = "landsfiler";
        }
        if (code.equalsIgnoreCase("kommune")) {
            code = "kommunevis";
        }
        if (code.equalsIgnoreCase("region")) {
            code = "regional inndeling";
        }
        for (RegisterItem item:items) {
            if (item.getCodevalue() != null) {
                // First match on codeValues
                if (item.getCodevalue().equalsIgnoreCase(code)) {
                    return item.getLabel();
                }
                // Then try to match on partial code value
                if (item.getCodevalue().length() > code.length()) {
                    if (item.getCodevalue().substring(0, code.length() - 1).equalsIgnoreCase(code)) {
                        return item.getLabel();
                    }
                } else {
                    if (item.getCodevalue().equalsIgnoreCase(code.substring(0,item.getCodevalue().length()-1))) {
                        return item.getLabel();
                    }
                }
            }
        }
        return null;
    }
    
}


