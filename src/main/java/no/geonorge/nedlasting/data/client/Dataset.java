package no.geonorge.nedlasting.data.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Dataset {

    private String metadataUuid;
    private String title;
    private List<File> files;
    private Integer maxArea;
    private String inspireIdCode;
    private String inspireIdNamespace;

    private Map<String, String> external;

    public String getMetadataUuid() {
        return metadataUuid;
    }

    public void setMetadataUuid(String metadataUuid) {
        this.metadataUuid = metadataUuid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setFiles(List<File> files) {
        if (files == null) {
            this.files = null;
        } else {
            this.files = new ArrayList<>(files);
        }
    }

    public boolean ignoreFiles() {
        return files == null;
    }

    public List<File> getFiles() {
        if (files == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(files);
    }

    public void addFile(File file) {
        if (files == null) {
            files = new ArrayList<>();
        }
        files.add(file);
    }

    public void removeFile(String fileId) {
        if (files == null || fileId == null) {
            return;
        }
        for (Iterator<File> it = files.iterator(); it.hasNext();) {
            if (fileId.equals(it.next().getFileId())) {
                it.remove();
            }
        }
    }

    public boolean isExternal() {
        return external != null && !external.isEmpty();
    }

    public Map<String, String> getExternalParameters() {
        if (external == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(external);
    }

    public void setExternalParameters(Map<String, String> parameters) {
        if (parameters == null) {
            this.external = null;
            return;
        }
        this.external = new HashMap<>(parameters);
    }
    
    public void setExternalParameter(String key, String value) {
        if (external == null) {
            external = new HashMap<>();
        }
        external.put(key, value);
    }

    public Integer getMaxArea() {
        return maxArea;
    }

    public void setMaxArea(Integer maxArea) {
        this.maxArea = maxArea;
    }

    public String getInspireIdCode() {
        return inspireIdCode;
    }

    public void setInspireIdCode(String inspireIdCode) {
        this.inspireIdCode = inspireIdCode;
    }

    public String getInspireIdNamespace() {
        return inspireIdNamespace;
    }

    public void setInspireIdNamespace(String inspireIdNamespace) {
        this.inspireIdNamespace = inspireIdNamespace;
    }

}
