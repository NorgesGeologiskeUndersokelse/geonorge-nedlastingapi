package no.geonorge.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.SelectQuery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import no.geonorge.nedlasting.config.Config;
import no.geonorge.nedlasting.data.Dataset;
import no.geonorge.nedlasting.data.DatasetFile;
import no.geonorge.nedlasting.data.DownloadItem;
import no.geonorge.nedlasting.data.DownloadOrder;
import no.geonorge.nedlasting.data.client.Area;
import no.geonorge.nedlasting.data.client.CanDownloadRequest;
import no.geonorge.nedlasting.data.client.CanDownloadResponse;
import no.geonorge.nedlasting.data.client.Capabilities;
import no.geonorge.nedlasting.data.client.File;
import no.geonorge.nedlasting.data.client.Format;
import no.geonorge.nedlasting.data.client.Order;
import no.geonorge.nedlasting.data.client.OrderLine;
import no.geonorge.nedlasting.data.client.OrderReceipt;
import no.geonorge.nedlasting.data.client.Projection;
import no.geonorge.nedlasting.external.External;

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
        GsonBuilder b = new GsonBuilder();
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
        /*
         * Sample JSON HTTP-POST
         * {"metadataUuid":"18777cf4-1f06-4cb0-803d-d6382b76681f",
         * "coordinates":"189044 7043418 321736 6979780 244558 6893124 76662 6886354 65830 7013630 191752 7040710 190398 7046126 189044 7043418"
         * ,"coordinateSystem":"32633"}
         */
        CanDownloadRequest req = gson().fromJson(jsonRequest, CanDownloadRequest.class);

        ObjectContext ctxt = Config.getObjectContext();
        Dataset dataset = Dataset.forMetadataUUID(ctxt, req.getMetadataUuid());
        if (dataset == null) {
            log.info("could not find dataset");
            return gson().toJson(new CanDownloadResponse(false));
        }

        // check if can select area
        if (req.hasCoordinates() && !dataset.isSupportsPolygonSelection()) {
            log.info("trying to select polygon, but dataset does not support it");
            return gson().toJson(new CanDownloadResponse(false));
        }
        
        // check srid
        if (!dataset.supportSrid(req.getSrid())) {
            log.info("unsupported srid");
            return gson().toJson(new CanDownloadResponse(false));
        }

        return gson().toJson(new CanDownloadResponse(true));
    }

    /**
     * 
     * @param metadataUuid
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
        String json = gson().toJson(order.getOrderReceipt());
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
        OrderReceipt orderReceipt = new OrderReceipt();

        ObjectContext ctxt = Config.getObjectContext();

        DownloadOrder downloadOrder = ctxt.newObject(DownloadOrder.class);
        downloadOrder.setEmail(order.getEmail());
        downloadOrder.setStartTime(new Date());

        for (OrderLine orderLine : order.getOrderLines()) {
            for (DatasetFile datasetFile : DatasetFile.findForOrderLine(ctxt, orderLine)) {
                File file = datasetFile.forClient();
                file.setStatus("ReadyForDownload");
                orderReceipt.addFile(file);

                DownloadItem downloadItem = ctxt.newObject(DownloadItem.class);
                downloadItem.setProjection(datasetFile.getProjection());
                downloadItem.setUrl(datasetFile.getUrl());
                downloadItem.setFileId(datasetFile.getFileId());
                downloadItem.setFileName(datasetFile.getFileName());
                downloadItem.setMetadataUuid(datasetFile.getDataset().getMetadataUuid());
                downloadOrder.addToItems(downloadItem);
            }
        }

        ctxt.commitChanges();
        
        orderReceipt.setReferenceNumber(downloadOrder.getOrderId().toString());

        String json = gson().toJson(orderReceipt);
        return Response.ok(json, MediaType.APPLICATION_JSON).build();
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
        String json = gson().toJson(dataset.forClient());
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
                datasetFile.setProjection(no.geonorge.nedlasting.data.Projection.getForSrid(ctxt,
                        Integer.parseInt(file.getProjection())));
                datasetFile.setFormatName(file.getFormat());
                datasetFile.setFileName(file.getName());
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
    
}
