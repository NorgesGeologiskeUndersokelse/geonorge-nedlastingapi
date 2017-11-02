package no.geonorge.nedlasting.data.auto;

import java.util.Date;
import java.util.List;

import org.apache.cayenne.CayenneDataObject;

import no.geonorge.nedlasting.data.DownloadExternalJob;
import no.geonorge.nedlasting.data.DownloadItem;

/**
 * Class _DownloadOrder was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _DownloadOrder extends CayenneDataObject {

    public static final String EMAIL_PROPERTY = "email";
    public static final String REFERENCE_NUMBER_PROPERTY = "referenceNumber";
    public static final String START_TIME_PROPERTY = "startTime";
    public static final String EXTERNAL_JOBS_PROPERTY = "externalJobs";
    public static final String ITEMS_PROPERTY = "items";

    public static final String ORDER_ID_PK_COLUMN = "ORDER_ID";

    public void setEmail(String email) {
        writeProperty(EMAIL_PROPERTY, email);
    }
    public String getEmail() {
        return (String)readProperty(EMAIL_PROPERTY);
    }

    public void setReferenceNumber(String referenceNumber) {
        writeProperty(REFERENCE_NUMBER_PROPERTY, referenceNumber);
    }
    public String getReferenceNumber() {
        return (String)readProperty(REFERENCE_NUMBER_PROPERTY);
    }

    public void setStartTime(Date startTime) {
        writeProperty(START_TIME_PROPERTY, startTime);
    }
    public Date getStartTime() {
        return (Date)readProperty(START_TIME_PROPERTY);
    }

    public void addToExternalJobs(DownloadExternalJob obj) {
        addToManyTarget(EXTERNAL_JOBS_PROPERTY, obj, true);
    }
    public void removeFromExternalJobs(DownloadExternalJob obj) {
        removeToManyTarget(EXTERNAL_JOBS_PROPERTY, obj, true);
    }
    @SuppressWarnings("unchecked")
    public List<DownloadExternalJob> getExternalJobs() {
        return (List<DownloadExternalJob>)readProperty(EXTERNAL_JOBS_PROPERTY);
    }


    public void addToItems(DownloadItem obj) {
        addToManyTarget(ITEMS_PROPERTY, obj, true);
    }
    public void removeFromItems(DownloadItem obj) {
        removeToManyTarget(ITEMS_PROPERTY, obj, true);
    }
    @SuppressWarnings("unchecked")
    public List<DownloadItem> getItems() {
        return (List<DownloadItem>)readProperty(ITEMS_PROPERTY);
    }


}
