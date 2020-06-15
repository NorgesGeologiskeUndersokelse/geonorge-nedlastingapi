package no.geonorge.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.cayenne.ObjectContext;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import no.geonorge.junit.DbTestCase;
import no.geonorge.nedlasting.config.Config;
import no.geonorge.nedlasting.data.Dataset;
import no.geonorge.nedlasting.data.DatasetFile;
import no.geonorge.nedlasting.data.Projection;
import no.geonorge.nedlasting.data.client.Area;
import no.geonorge.nedlasting.data.client.File;
import no.geonorge.nedlasting.data.client.Format;
import no.geonorge.nedlasting.data.client.Order;
import no.geonorge.nedlasting.data.client.OrderLine;
import no.geonorge.nedlasting.data.client.OrderReceipt;
import no.geonorge.nedlasting.utils.GsonCreator;

public class DownloadServiceTest extends DbTestCase {

    public void testCapabilities() throws IOException {
        DownloadService ds = new DownloadService();

        String uuid = UUID.randomUUID().toString();
        assertEquals(HttpServletResponse.SC_NOT_FOUND, ds.returnCapabilities(uuid).getStatus());

        ObjectContext ctxt = Config.getObjectContext();
        Dataset dataset = ctxt.newObject(Dataset.class);
        dataset.setMetadataUuid(uuid);
        ctxt.commitChanges();

        assertEquals(HttpServletResponse.SC_OK, ds.returnCapabilities(uuid).getStatus());
    }

    public void testFormats() throws IOException {
        DownloadService ds = new DownloadService();

        String uuid = UUID.randomUUID().toString();
        assertEquals(HttpServletResponse.SC_NOT_FOUND, ds.returnFormats(uuid).getStatus());

        ObjectContext ctxt = Config.getObjectContext();
        Dataset dataset = ctxt.newObject(Dataset.class);
        dataset.setMetadataUuid(uuid);
        DatasetFile datasetFile = ctxt.newObject(DatasetFile.class);
        datasetFile.setDataset(dataset);
        datasetFile.setFormatName("SOSI");
        datasetFile.setUrl("http://a.url.com");
        datasetFile.setProjection(createOrFind(ctxt, 4326));
        datasetFile.setFileDate(new Date());
        ctxt.commitChanges();

        assertEquals(HttpServletResponse.SC_OK, ds.returnFormats(uuid).getStatus());

        Type type = new TypeToken<List<Format>>() {
        }.getType();
        List<Format> formats = GsonCreator.create().fromJson(ds.returnFormats(uuid).getEntity().toString(), type);
        assertNotNull(formats);
        assertEquals(0, formats.size());
        //Format format = formats.get(0);
        //assertEquals("SOSI", format.getName());
    }

    public void testProjections() throws IOException {
        DownloadService ds = new DownloadService();

        String uuid = UUID.randomUUID().toString();
        assertEquals(HttpServletResponse.SC_NOT_FOUND, ds.returnProjections(uuid).getStatus());

        ObjectContext ctxt = Config.getObjectContext();
        Dataset dataset = ctxt.newObject(Dataset.class);
        dataset.setMetadataUuid(uuid);
        DatasetFile datasetFile = ctxt.newObject(DatasetFile.class);
        datasetFile.setDataset(dataset);
        datasetFile.setFormatName("SOSI");
        datasetFile.setUrl("http://a.url.com");
        datasetFile.setProjection(createOrFind(ctxt, 4326));
        datasetFile.setFileDate(new Date());
        ctxt.commitChanges();

        assertEquals(HttpServletResponse.SC_OK, ds.returnProjections(uuid).getStatus());

        Type type = new TypeToken<List<no.geonorge.nedlasting.data.client.Projection>>() {
        }.getType();
        List<no.geonorge.nedlasting.data.client.Projection> projections = GsonCreator.create()
                .fromJson(ds.returnProjections(uuid).getEntity().toString(), type);
        assertNotNull(projections);
        assertEquals(0, projections.size());
        //no.geonorge.nedlasting.data.client.Projection projection = projections.get(0);
        //assertEquals("4326", projection.getCode());
    }

    public void testAreas() throws IOException {
        DownloadService ds = new DownloadService();

        String uuid = UUID.randomUUID().toString();
        assertEquals(HttpServletResponse.SC_NOT_FOUND, ds.returnAreas(uuid).getStatus());

        ObjectContext ctxt = Config.getObjectContext();

        Projection p1 = createOrFind(ctxt, 4326);
        Projection p2 = createOrFind(ctxt, 32633);
        ctxt.commitChanges();

        Dataset dataset = ctxt.newObject(Dataset.class);
        dataset.setMetadataUuid(uuid);
        DatasetFile datasetFile1 = ctxt.newObject(DatasetFile.class);
        datasetFile1.setArea("fylke", "02", "Akershus");
        datasetFile1.setDataset(dataset);
        datasetFile1.setFormatName("SOSI");
        datasetFile1.setUrl("http://a.url.com/1");
        datasetFile1.setProjection(p1);
        datasetFile1.setFileDate(new Date());
        DatasetFile datasetFile2 = ctxt.newObject(DatasetFile.class);
        datasetFile2.setArea("fylke", "02", "Akershus");
        datasetFile2.setDataset(dataset);
        datasetFile2.setFormatName("GML");
        datasetFile2.setUrl("http://a.url.com/2");
        datasetFile2.setProjection(p1);
        datasetFile2.setFileDate(new Date());
        DatasetFile datasetFile3 = ctxt.newObject(DatasetFile.class);
        datasetFile3.setArea("fylke", "02", "Akershus");
        datasetFile3.setDataset(dataset);
        datasetFile3.setFormatName("SOSI");
        datasetFile3.setUrl("http://a.url.com/3");
        datasetFile3.setProjection(p1);
        datasetFile3.setFileDate(new Date());
        DatasetFile datasetFile4 = ctxt.newObject(DatasetFile.class);
        datasetFile4.setArea("fylke", "02", "Akershus");
        datasetFile4.setDataset(dataset);
        datasetFile4.setFormatName("GML");
        datasetFile4.setUrl("http://a.url.com/4");
        datasetFile4.setFileDate(new Date());
        datasetFile4.setProjection(p2);
        DatasetFile datasetFile5 = ctxt.newObject(DatasetFile.class);
        datasetFile5.setArea("fylke", "03", "Whatever");
        datasetFile5.setDataset(dataset);
        datasetFile5.setFormatName("GML");
        datasetFile5.setUrl("http://a.url.com/5");
        datasetFile5.setProjection(p2);
        datasetFile5.setFileDate(new Date());
        ctxt.commitChanges();

        assertEquals(HttpServletResponse.SC_OK, ds.returnAreas(uuid).getStatus());

        Type type = new TypeToken<List<Area>>() {
        }.getType();
        List<Area> areas = GsonCreator.create().fromJson(ds.returnAreas(uuid).getEntity().toString(), type);
        assertNotNull(areas);
        assertEquals(2, areas.size());

        Map<String, Area> areaByCode = new HashMap<>();
        for (Area area : areas) {
            areaByCode.put(area.getCode(), area);
        }
        assertEquals(2, areaByCode.size());

        Area area02 = areaByCode.get("02");
        assertNotNull(area02);
        assertEquals("Akershus", area02.getName());
        assertEquals(2, area02.getProjections().size());
        assertEquals(2, area02.getFormats().size());

        Area area03 = areaByCode.get("03");
        assertNotNull(area03);
        assertEquals("Whatever", area03.getName());
        assertEquals(1, area03.getProjections().size());
        assertEquals(1, area03.getFormats().size());

    }

    public void testOrder() throws IOException {
        DownloadService ds = new DownloadService();

        String uuid = UUID.randomUUID().toString();

        ObjectContext ctxt = Config.getObjectContext();

        Projection p1 = createOrFind(ctxt, 4326);
        ctxt.commitChanges();

        Dataset dataset = ctxt.newObject(Dataset.class);
        dataset.setMetadataUuid(uuid);
        DatasetFile datasetFile1 = ctxt.newObject(DatasetFile.class);
        datasetFile1.setArea("fylke", "02", "Akershus");
        datasetFile1.setDataset(dataset);
        datasetFile1.setFormatName("SOSI");
        datasetFile1.setUrl("http://a.url.com/1");
        datasetFile1.setProjection(p1);
        datasetFile1.setFileDate(new Date());
        ctxt.commitChanges();

        Order order = new Order();
        OrderLine orderLine = new OrderLine();
        orderLine.addArea(datasetFile1.getOrderArea());
        orderLine.addFormat(datasetFile1.getFormat());
        orderLine.addProjection(datasetFile1.getProjection().forClient());
        orderLine.setMetadataUuid(dataset.getMetadataUuid());
        order.addOrderLine(orderLine);

        Gson gson = GsonCreator.create();
        Response response = ds.orderDownload(gson.toJson(order));
        assertEquals(200, response.getStatus());
        OrderReceipt orderReceipt = gson.fromJson(response.getEntity().toString(), OrderReceipt.class);
        assertNotNull(orderReceipt);

        assertEquals(1, orderReceipt.getFiles().size());

        assertEquals(200, ds.getOrderInfo(orderReceipt.getReferenceNumber()).getStatus());
        assertEquals(404, ds.getOrderInfo(UUID.randomUUID().toString()).getStatus());

    }

    public void testPutGetDataset() throws IOException {
        no.geonorge.nedlasting.data.client.Dataset ds = new no.geonorge.nedlasting.data.client.Dataset();
        ds.setMetadataUuid(UUID.randomUUID().toString());
        ds.setTitle("hello " + System.currentTimeMillis());

        File dsf = new File();
        dsf.setFileId("1");
        dsf.setDownloadUrl("http://a/b/c");
        dsf.setProjection("4326");
        ds.addFile(dsf);

        DownloadService dls = new DownloadService();
        dls.putDataset(ds.getMetadataUuid(), GsonCreator.create().toJson(ds));

        no.geonorge.nedlasting.data.client.Dataset ds2 = GsonCreator.create().fromJson(
                dls.getDataset(ds.getMetadataUuid()).getEntity().toString(),
                no.geonorge.nedlasting.data.client.Dataset.class);
        assertEquals(ds.getMetadataUuid(), ds2.getMetadataUuid());
        assertEquals(ds.getTitle(), ds2.getTitle());
        assertEquals(1, ds2.getFiles().size());

        File dsf2 = ds2.getFiles().get(0);
        assertEquals(dsf.getFileId(), dsf2.getFileId());
        assertTrue(looksLikeProxyUrl(dsf2.getDownloadUrl()));

        // try to add a file
        File dsf3 = new File();
        dsf3.setFileId("11");
        dsf3.setDownloadUrl("http://a/b/c/d");
        dsf3.setProjection("4326");
        ds2.addFile(dsf3);

        dls.putDataset(ds.getMetadataUuid(), GsonCreator.create().toJson(ds2));
        no.geonorge.nedlasting.data.client.Dataset ds3 = GsonCreator.create().fromJson(
                dls.getDataset(ds.getMetadataUuid()).getEntity().toString(),
                no.geonorge.nedlasting.data.client.Dataset.class);
        assertEquals(ds.getMetadataUuid(), ds3.getMetadataUuid());
        assertEquals(ds.getTitle(), ds3.getTitle());
        assertEquals(2, ds3.getFiles().size());

        // try to remove a file
        ds3.removeFile(dsf3.getFileId());
        assertEquals(1, ds3.getFiles().size());

        dls.putDataset(ds.getMetadataUuid(), GsonCreator.create().toJson(ds3));
        no.geonorge.nedlasting.data.client.Dataset ds4 = GsonCreator.create().fromJson(
                dls.getDataset(ds.getMetadataUuid()).getEntity().toString(),
                no.geonorge.nedlasting.data.client.Dataset.class);
        assertEquals(ds.getMetadataUuid(), ds4.getMetadataUuid());
        assertEquals(ds.getTitle(), ds4.getTitle());
        assertEquals(1, ds4.getFiles().size());

        // try to update with null files. handle as ignore, not zero.
        assertFalse(ds4.ignoreFiles());
        ds4.setFiles(null);
        assertTrue(ds4.ignoreFiles());

        dls.putDataset(ds.getMetadataUuid(), GsonCreator.create().toJson(ds4));
        no.geonorge.nedlasting.data.client.Dataset ds5 = GsonCreator.create().fromJson(
                dls.getDataset(ds.getMetadataUuid()).getEntity().toString(),
                no.geonorge.nedlasting.data.client.Dataset.class);
        assertEquals(ds.getMetadataUuid(), ds5.getMetadataUuid());
        assertEquals(ds.getTitle(), ds5.getTitle());
        assertEquals(1, ds5.getFiles().size());

    }
    
    public void testGetFileForDownload() throws IOException {
        ObjectContext ctxt = Config.getObjectContext();

        Projection p1 = createOrFind(ctxt, 4326);
        ctxt.commitChanges();

        Dataset dataset = ctxt.newObject(Dataset.class);
        dataset.setMetadataUuid(UUID.randomUUID().toString());
        DatasetFile datasetFile1 = ctxt.newObject(DatasetFile.class);
        datasetFile1.setArea("fylke", "02", "Akershus");
        datasetFile1.setDataset(dataset);
        datasetFile1.setFormatName("SOSI");
        datasetFile1.setUrl("https://raw.githubusercontent.com/halset/test/master/README.md");
        datasetFile1.setProjection(p1);
        datasetFile1.setFileDate(new Date());
        datasetFile1.setFileId(UUID.randomUUID().toString());
        ctxt.commitChanges();

        assertFalse(looksLikeProxyUrl(datasetFile1.getUrl()));

        DownloadService ds = new DownloadService();
        
        Response response = ds.getFileForDownload(dataset.getMetadataUuid(), datasetFile1.getFileId());
        assertTrue(responseToString(response).contains("test"));
    }
    
    public void testGetFileForOrder() throws IOException {
        ObjectContext ctxt = Config.getObjectContext();

        Projection p1 = createOrFind(ctxt, 4326);
        ctxt.commitChanges();

        Dataset dataset = ctxt.newObject(Dataset.class);
        dataset.setMetadataUuid(UUID.randomUUID().toString());
        DatasetFile datasetFile1 = ctxt.newObject(DatasetFile.class);
        datasetFile1.setArea("fylke", "02", "Akershus");
        datasetFile1.setDataset(dataset);
        datasetFile1.setFormatName("SOSI");
        datasetFile1.setUrl("https://raw.githubusercontent.com/halset/test/master/README.md");
        datasetFile1.setProjection(p1);
        datasetFile1.setFileDate(new Date());
        ctxt.commitChanges();

        assertFalse(looksLikeProxyUrl(datasetFile1.getUrl()));

        Order order = new Order();
        OrderLine orderLine = new OrderLine();
        orderLine.addArea(datasetFile1.getOrderArea());
        orderLine.addFormat(datasetFile1.getFormat());
        orderLine.addProjection(datasetFile1.getProjection().forClient());
        orderLine.setMetadataUuid(dataset.getMetadataUuid());
        order.addOrderLine(orderLine);

        DownloadService ds = new DownloadService();

        Gson gson = GsonCreator.create();
        Response response = ds.orderDownload(gson.toJson(order));
        assertEquals(200, response.getStatus());
        OrderReceipt orderReceipt = gson.fromJson(response.getEntity().toString(), OrderReceipt.class);
        assertNotNull(orderReceipt);

        assertEquals(1, orderReceipt.getFiles().size());

        File file = orderReceipt.getFiles().get(0);
        assertNotNull(file.getDownloadUrl());
        assertFalse(file.getDownloadUrl().contains(datasetFile1.getUrl()));
        assertTrue(looksLikeProxyUrl(file.getDownloadUrl()));

        response = ds.getOrderInfo(orderReceipt.getReferenceNumber());
        assertEquals(200, response.getStatus());
        orderReceipt = gson.fromJson(response.getEntity().toString(), OrderReceipt.class);
        assertNotNull(orderReceipt);

        file = orderReceipt.getFiles().get(0);
        assertNotNull(file.getDownloadUrl());
        assertFalse(file.getDownloadUrl().contains(datasetFile1.getUrl()));
        assertTrue(looksLikeProxyUrl(file.getDownloadUrl()));

        response = ds.getFileForOrder(orderReceipt.getReferenceNumber(), file.getFileId());
        assertTrue(responseToString(response).contains("test"));
    }
    
    public void testGetAtomFeed() throws IOException {
        ObjectContext ctxt = Config.getObjectContext();

        Projection p1 = createOrFind(ctxt, 4326);
        ctxt.commitChanges();

        Dataset dataset = ctxt.newObject(Dataset.class);
        dataset.setTitle("junit " + System.currentTimeMillis());
        dataset.setMetadataUuid(UUID.randomUUID().toString());
        DatasetFile datasetFile1 = ctxt.newObject(DatasetFile.class);
        datasetFile1.setArea("fylke", "02", "Akershus");
        datasetFile1.setDataset(dataset);
        datasetFile1.setFormatName("SOSI");
        datasetFile1.setUrl("https://raw.githubusercontent.com/halset/test/master/README.md");
        datasetFile1.setProjection(p1);
        datasetFile1.setFileDate(new Date());
        ctxt.commitChanges();

        DownloadService ds = new DownloadService();

        Response response = ds.getAtomFeed(dataset.getMetadataUuid());
        assertTrue(responseToString(response).contains("Format:SOSI"));
    }
    
    private static String responseToString(Response response) throws IOException {
        Object entity = response.getEntity();
        if (entity instanceof String) {
            return (String) entity;
        }
        if (entity instanceof StreamingOutput) {
            StreamingOutput out = (StreamingOutput) entity;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            out.write(baos);
            return new String(baos.toByteArray(), "UTF-8");
        }
        return entity.toString();
    }
    
    private static boolean looksLikeProxyUrl(String url) {
        if (url == null) {
            return false;
        }
        return url.contains("fileproxy/") || url.contains("v2/download/order/");
    }

}
