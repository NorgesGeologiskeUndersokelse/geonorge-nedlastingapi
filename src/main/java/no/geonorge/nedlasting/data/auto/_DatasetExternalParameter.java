package no.geonorge.nedlasting.data.auto;

import org.apache.cayenne.CayenneDataObject;

import no.geonorge.nedlasting.data.Dataset;

/**
 * Class _DatasetExternalParameter was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _DatasetExternalParameter extends CayenneDataObject {

    public static final String KEY_PROPERTY = "key";
    public static final String VALUE_PROPERTY = "value";
    public static final String DATASET_PROPERTY = "dataset";

    public static final String DATASET_ID_PK_COLUMN = "DATASET_ID";
    public static final String PARAMETER_NAME_PK_COLUMN = "PARAMETER_NAME";

    public void setKey(String key) {
        writeProperty(KEY_PROPERTY, key);
    }
    public String getKey() {
        return (String)readProperty(KEY_PROPERTY);
    }

    public void setValue(String value) {
        writeProperty(VALUE_PROPERTY, value);
    }
    public String getValue() {
        return (String)readProperty(VALUE_PROPERTY);
    }

    public void setDataset(Dataset dataset) {
        setToOneTarget(DATASET_PROPERTY, dataset, true);
    }

    public Dataset getDataset() {
        return (Dataset)readProperty(DATASET_PROPERTY);
    }


}
