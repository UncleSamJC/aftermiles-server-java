/*
 * Copyright 2024 Aftermiles.ca
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.model;

import org.traccar.storage.StorageName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@StorageName("tc_maintenance_logs")
public class MaintenanceLog extends ExtendedModel {

    private long deviceId;

    public long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(long deviceId) {
        this.deviceId = deviceId;
    }

    private Date date;

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    private String serviceCompleted;

    public String getServiceCompleted() {
        return serviceCompleted;
    }

    public void setServiceCompleted(String serviceCompleted) {
        this.serviceCompleted = serviceCompleted;
    }

    private String completedBy;

    public String getCompletedBy() {
        return completedBy;
    }

    public void setCompletedBy(String completedBy) {
        this.completedBy = completedBy;
    }

    private String notes;

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    private String maintenancePhotos;

    public String getMaintenancePhotos() {
        return maintenancePhotos;
    }

    public void setMaintenancePhotos(String maintenancePhotos) {
        this.maintenancePhotos = maintenancePhotos;
    }

    private long createdByUserId;

    public long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    private Date createdTime;

    public Date getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }

    private Date modifiedTime;

    public Date getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(Date modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    /**
     * Helper method to convert photos to a list.
     * Splits the comma-separated maintenancePhotos string into a list of paths.
     * Uses non-JavaBean naming to avoid ORM property detection.
     *
     * @return List of photo paths, empty list if no photos
     */
    public List<String> extractPhotosList() {
        if (maintenancePhotos == null || maintenancePhotos.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> photos = new ArrayList<>();
        for (String photo : maintenancePhotos.split(",")) {
            String trimmed = photo.trim();
            if (!trimmed.isEmpty()) {
                photos.add(trimmed);
            }
        }
        return photos;
    }

    /**
     * Helper method to set photos from a list.
     * Joins the list of photo paths into a comma-separated string.
     * Uses non-JavaBean naming to avoid ORM property detection.
     *
     * @param photos List of photo paths
     */
    public void updatePhotosFromList(List<String> photos) {
        if (photos == null || photos.isEmpty()) {
            this.maintenancePhotos = null;
        } else {
            this.maintenancePhotos = String.join(",", photos);
        }
    }

}
