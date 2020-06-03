package io.fairspace.saturn.vfs;

import io.fairspace.saturn.services.collections.Collection;
import io.fairspace.saturn.services.collections.CollectionsService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.List;

import static io.fairspace.saturn.vfs.PathUtils.*;
import static java.time.Instant.ofEpochMilli;
import static java.util.stream.Collectors.toList;

public abstract class BaseFileSystem implements VirtualFileSystem {

    private static final String[] INVALID_BASENAMES = {".", ".."};

    private static final FileInfo ROOT = FileInfo.builder()
            .path("")
            .readOnly(false)
            .isDirectory(true)
            .created(ofEpochMilli(0))
            .modified(ofEpochMilli(0))
            .build();


    protected final CollectionsService collections;

    public BaseFileSystem(CollectionsService collections) {
        this.collections = collections;
    }

    @Override
    public FileInfo stat(String path) throws IOException {
        if (path.isEmpty()) {
            return ROOT;
        }
        if (isCollection(path)) {
            var collection = collections.getByLocation(path);
            if (collection == null) {
                return null;
            }
            return fileInfo(collection);
        }
        return statRegularFile(path);
    }

    @Override
    public String iri(String path) throws IOException {
        return collections.getBaseIri() + encodePath(path);
    }

    @Override
    public List<FileInfo> list(String parentPath) throws IOException {
        if (parentPath.isEmpty()) {
            return collections.list()
                    .stream()
                    .map(BaseFileSystem::fileInfo)
                    .collect(toList());
        }
        return listCollectionOrDirectory(parentPath);
    }

    @Override
    public FileInfo mkdir(String path) throws IOException {
        ensureValidPath(path);

        return doMkdir(path);
    }

    @Override
    public FileInfo create(String path, InputStream in) throws IOException {
        ensureValidPath(path);

        return doCreate(path, in);
    }

    @Override
    public void copy(String from, String to) throws IOException {
        ensureValidPath(from);
        ensureValidPath(to);

        doCopy(from, to);
    }

    @Override
    public void move(String from, String to) throws IOException {
        ensureValidPath(from);
        ensureValidPath(to);

        doMove(from, to);

    }

    @Override
    public void delete(String path) throws IOException {
        ensureValidPath(path);

        doDelete(path);
    }

    @Override
    public void close() throws IOException {
    }

    protected abstract FileInfo statRegularFile(String path) throws IOException;

    protected abstract List<FileInfo> listCollectionOrDirectory(String parentPath) throws IOException;

    protected abstract FileInfo doMkdir(String path) throws IOException;

    protected abstract FileInfo doCreate(String path, InputStream in) throws IOException;

    protected abstract void doCopy(String from, String to) throws IOException;

    protected abstract void doMove(String from, String to) throws IOException;

    protected abstract void doDelete(String path) throws IOException;

    private static boolean isCollection(String path) {
        return splitPath(path).length == 1;
    }

    private static void ensureValidPath(String path) throws IOException {
        if (path.isEmpty()) {
            throw new AccessDeniedException("File operations on the root directory are not allowed");
        }
        if (isCollection(path)) {
            throw new AccessDeniedException("Use Collections API for operations on collections");
        }

        if (containsInvalidPathName(path)) {
            throw new InvalidFilenameException("The given path name contains invalid special characters");
        }
    }

    static boolean containsInvalidPathName(String path) {
        return Arrays.asList(INVALID_BASENAMES).contains(name(path));
    }

    private static FileInfo fileInfo(Collection collection) {
        return FileInfo.builder()
                .path(collection.getLocation())
                .size(0)
                .isDirectory(true)
                .created(collection.getDateCreated())
                .modified(collection.getDateCreated())
                .deleted(collection.getDateDeleted())
                .createdBy(collection.getCreatedBy())
                .modifiedBy(collection.getModifiedBy())
                .deletedBy(collection.getDeletedBy())
                .readOnly(!collection.canWrite())
                .build();
    }
}
