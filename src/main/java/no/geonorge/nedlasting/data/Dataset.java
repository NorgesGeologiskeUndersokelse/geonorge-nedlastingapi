package no.geonorge.nedlasting.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.cayenne.Cayenne;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;

import no.geonorge.nedlasting.config.Config;
import no.geonorge.nedlasting.data.auto._Dataset;
import no.geonorge.nedlasting.data.client.Area;
import no.geonorge.nedlasting.data.client.Capabilities;
import no.geonorge.nedlasting.data.client.Format;
import no.geonorge.nedlasting.data.client.Link;
import no.geonorge.nedlasting.data.client.Projection;
import no.geonorge.nedlasting.external.External;

public class Dataset extends _Dataset {

    public Integer getDatasetId() {
        return Cayenne.intPKForObject(this);
    }
    private boolean isExternal;

    public static List<Dataset> getAll(ObjectContext ctxt) {
        SelectQuery query = new SelectQuery(Dataset.class);
        @SuppressWarnings("unchecked")
        List<Dataset> dataset = ctxt.performQuery(query);
        return dataset;
    }

    public static Dataset forMetadataUUID(ObjectContext ctxt, String metadataUuid) {
        Expression qual = ExpressionFactory.matchExp(METADATA_UUID_PROPERTY, metadataUuid);
        SelectQuery query = new SelectQuery(Dataset.class, qual);
        @SuppressWarnings("unchecked")
        List<Dataset> dataset = ctxt.performQuery(query);
        if (dataset.isEmpty()) {
            return null;
        }
        if (dataset.size() > 1) {
            throw new IllegalStateException("" + dataset.size() + "datasets with metadata uuid " + metadataUuid);
        }
        return dataset.get(0);
    }

    public Capabilities getCapabilities(String urlPrefix) {
        Capabilities ct = new Capabilities();
        ct.setSupportsAreaSelection(isSupportsAreaSelection());
        ct.setSupportsFormatSelection(isSupportsFormatSelection());
        ct.setSupportsPolygonSelection(isSupportsPolygonSelection());
        ct.setSupportsProjectionSelection(isSupportsProjectionSelection());
        ct.setDistributedBy(Config.getKeyDistributedBy());
        ct.setMapSelectionLayer(getMapSelectionLayer());
        ct.setSupportsDownloadBundling(Config.isSupportsDownloadBundling());
        ct.setMapSelectionLayer(getMapSelectionLayer());
        ct.setDeliveryNotificationByEmail(isExternal());

        String p = urlPrefix;
        String u = getMetadataUuid();
        ct.addLink(new Link("http://rel.geonorge.no/download/projection", p + "v2/codelists/projection/" + u));
        ct.addLink(new Link("http://rel.geonorge.no/download/format", p + "v2/codelists/format/" + u));
        ct.addLink(new Link("http://rel.geonorge.no/download/area", p + "v2/codelists/area/" + u));
        ct.addLink(new Link("http://rel.geonorge.no/download/order", p + "v2/order"));
        ct.addLink(new Link("self", p + "capabilities/" + u));
        ct.addLink(new Link("http://rel.geonorge.no/download/can-download", p + "v2/can-download"));

        return ct;
    }

    public no.geonorge.nedlasting.data.client.Dataset forClient(String urlPrefix) {
        no.geonorge.nedlasting.data.client.Dataset d = new no.geonorge.nedlasting.data.client.Dataset();
        d.setMetadataUuid(getMetadataUuid());
        d.setTitle(getTitle());
        d.setMaxArea(getMaxArea());
        d.setInspireIdCode(getInspireIdCode());
        d.setInspireIdNamespace(getInspireIdNamespace());
        for (DatasetFile file : getFiles()) {
            d.addFile(file.forClient(urlPrefix));
        }
        for (DatasetExternalParameter dep : getExternalParameters()) {
            if (dep.getKey().toLowerCase().contains("pass")) {
                continue;
            }
            d.setExternalParameter(dep.getKey(), dep.getValue());
        }
        return d;
    }

    public no.geonorge.nedlasting.data.client.Dataset forClientWithoutFiles() {
        no.geonorge.nedlasting.data.client.Dataset d = new no.geonorge.nedlasting.data.client.Dataset();
        d.setMetadataUuid(getMetadataUuid());
        d.setTitle(getTitle());
        d.setMaxArea(getMaxArea());
        d.setInspireIdCode(getInspireIdCode());
        d.setInspireIdNamespace(getInspireIdNamespace());
        return d;
    }

    public Collection<Format> getFormats() throws IOException {
        Set<Format> formats = new HashSet<>();
        for (DatasetFile file : getFiles()) {
            formats.add(file.getFormat());
        }
        
        if (isExternal()) {
            formats.addAll(getExternal().getFormats());
        }
        
        return Collections.unmodifiableCollection(formats);
    }

    public Collection<Projection> getProjections() throws IOException {
        Set<Projection> projections = new HashSet<>();
        for (DatasetFile file : getFiles()) {
            projections.add(file.getProjection().forClient());
        }
        
        if (isExternal()) {
            projections.addAll(getExternal().getProjections());
        }
        
        return Collections.unmodifiableCollection(projections);
    }
    
    public Collection<Integer> getSrids() throws IOException {
        Set<Integer> srids = new TreeSet<>();
        
        for (DatasetFile file : getFiles()) {
            srids.add(file.getProjection().getSrid());
        }
        
        if (isExternal()) {
            for (Projection projection : getExternal().getProjections()) {
                srids.add(projection.getSrid());
            }
        }
        
        return Collections.unmodifiableCollection(srids);
    }
    
    public boolean supportSrid(Integer srid) throws IOException {
        for (DatasetFile file : getFiles()) {
            if (srid.equals(file.getProjection().getSrid())) {
                return true;
            }
        }
        
        if (isExternal()) {
            for (Projection projection : getExternal().getProjections()) {
                if (srid.equals(projection.getSrid())) {
                    return true;
                }
            }
        }
        
        return false;
    }

    public List<Area> getAreas() {
        Map<String, Area> areaTypeByAreaKey = new HashMap<>();
        for (DatasetFile file : getFiles()) {
            String areaKey = file.getAreaKey();
            Area areaType = areaTypeByAreaKey.get(areaKey);
            if (areaType == null) {
                areaType = new Area();
                areaType.setCode(file.getAreaCode());
                areaType.setType(file.getAreaType());
                areaType.setName(file.getAreaName());
                areaTypeByAreaKey.put(areaKey, areaType);
            }
            areaType.addFormat(file.getFormat());
            areaType.addProjection(file.getProjection().forClient());
        }

        List<Area> areas = new ArrayList<>(areaTypeByAreaKey.values());
        areas.sort(Comparator.comparing(Area::getName));
        return Collections.unmodifiableList(areas);
    }

    public DatasetFile getFile(String fileId) {
        if (fileId == null) {
            return null;
        }
        if (getObjectId().isTemporary()) {
            for (DatasetFile file : getFiles()) {
                if (fileId.equals(file.getFileId())) {
                    return file;
                }
            }
            return null;
        }
        Map<String, Object> pk = new HashMap<>(2);
        pk.put(DatasetFile.DATASET_ID_PK_COLUMN, getDatasetId());
        pk.put(DatasetFile.FILE_ID_PK_COLUMN, fileId);
        return Cayenne.objectForPK(getObjectContext(), DatasetFile.class, pk);
    }
    
    public Set<String> getFileIds() {
        Set<String> fileIds = new HashSet<>();
        for (DatasetFile file : getFiles()) {
            fileIds.add(file.getFileId());
        }
        return Collections.unmodifiableSet(fileIds);
    }

    public Date getLastFileDate() {
        Date last = null;
        for (DatasetFile file : getFiles()) {
            Date fileDate = file.getFileDate();
            if (fileDate == null) {
                continue;
            }
            if (last == null || last.before(fileDate)) {
                last = fileDate;
            }
        }
        return last;
    }

    public boolean isExternal() {
        return !getExternalParameters().isEmpty();
    }

    public External getExternal() {
        Map<String, String> e = new HashMap<>();
        for (DatasetExternalParameter ep : getExternalParameters()) {
            e.put(ep.getKey(), ep.getValue());
        }
        return External.create(e);
    }

    public void setExternal(Map<String, String> parameters) {
        Map<String, String> rest = new HashMap<>(parameters);
        for (DatasetExternalParameter ep : new ArrayList<>(getExternalParameters())) {
            if (rest.containsKey(ep.getKey())) {
                ep.setValue(rest.remove(ep.getKey()));
            } else {
                getObjectContext().deleteObject(ep);
            }
        }
        for (Map.Entry<String, String> e : rest.entrySet()) {
            DatasetExternalParameter ep = getObjectContext().newObject(DatasetExternalParameter.class);
            ep.setKey(e.getKey());
            ep.setValue(e.getValue());
            ep.setDataset(this);
        }
    }

}
