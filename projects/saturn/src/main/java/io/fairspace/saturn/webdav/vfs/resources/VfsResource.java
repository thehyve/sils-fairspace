package io.fairspace.saturn.webdav.vfs.resources;

import java.time.ZonedDateTime;

public interface VfsResource {
    public String getUniqueId();
    public String getName();
    public ZonedDateTime getCreatedDate();
    public ZonedDateTime getModifiedDate();
    public VfsUser getCreator();
    public String getParentId();
    public boolean isReady();
}
