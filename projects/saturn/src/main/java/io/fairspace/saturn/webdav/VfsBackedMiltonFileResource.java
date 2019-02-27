package io.fairspace.saturn.webdav;

import io.fairspace.saturn.vfs.FileInfo;
import io.fairspace.saturn.vfs.VirtualFileSystem;
import io.milton.http.Auth;
import io.milton.http.Range;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.http.exceptions.NotFoundException;
import io.milton.resource.GetableResource;
import io.milton.resource.ReplaceableResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Optional;

import static io.milton.common.ContentTypeUtils.findAcceptableContentType;
import static io.milton.common.ContentTypeUtils.findContentTypes;

public class VfsBackedMiltonFileResource extends VfsBackedMiltonResource implements GetableResource, ReplaceableResource {
    public VfsBackedMiltonFileResource(VirtualFileSystem fs, FileInfo info) {
        super(fs, info);
    }

    @Override
    public String getUniqueId() {
        return info.getPath();
    }

    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType) throws IOException, NotAuthorizedException, BadRequestException, NotFoundException {
        var start = Optional.ofNullable(range).map(Range::getStart).orElse(0L);
        var length = Optional.ofNullable(range).map(Range::getFinish).map(finish -> finish - start).orElse(Long.MAX_VALUE);
        fs.read(info.getPath(), start, length,  out);
    }

    @Override
    public Long getMaxAgeSeconds(Auth auth) {
        return null;
    }

    @Override
    public String getContentType(String accepts) {
        return findAcceptableContentType(findContentTypes(getName()), accepts);
    }

    @Override
    public Long getContentLength() {
        return info.getSize();
    }

    @Override
    public void replaceContent(InputStream in, Long length) throws BadRequestException, ConflictException, NotAuthorizedException {
        try {
            fs.modify(info.getPath(), in);
        } catch (IOException e) {
            onException(e);
        }
    }
}
