package io.fairspace.saturn.webdav.vfs.contents;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class LocalImmutableVfsContentStoreTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder(new File(System.getProperty("java.io.tmpdir")));

    private LocalImmutableVfsContentStore contentStore;

    @Before
    public void setUp() throws Exception {
        contentStore = new LocalImmutableVfsContentStore(folder.getRoot());
    }

    @Test
    public void integrationTest() throws IOException {
        String vfsPath = "/myfile";

        // Write content
        String inputText = "Test text";
        Charset charset = UTF_8;

        StoredContent storedContent = contentStore.putContent(vfsPath, IOUtils.toInputStream(inputText, charset));

        assertEquals(inputText.getBytes(charset).length, storedContent.getSize());

        // Read content back again
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        contentStore.getContent(storedContent.getLocation(), capture);
        String outputText = capture.toString(charset.name());

        assertEquals(inputText, outputText);
    }

    @Test
    public void binaryIntegrationTest() throws IOException {
        String vfsPath = "/myfile";

        // Write content
        byte[] bytes = { 0, 1, 2, 3, 10, 50, 100, 127, -1, -128 };
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        StoredContent storedContent = contentStore.putContent(vfsPath, input);
        assertEquals(bytes.length, storedContent.getSize());

        // Read content back again
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        contentStore.getContent(storedContent.getLocation(), capture);

        assertArrayEquals(bytes, capture.toByteArray());
    }

    @Test(expected = IOException.class)
    public void testReadNotExisting() throws IOException {
        String location = "not-existing";

        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        contentStore.getContent(location, capture);
    }

    @Test
    public void testNewLocationForEachWrite() throws IOException {
        String vfsPath = "/myfile";

        // Write content
        String inputText = "Test text";
        Charset charset = UTF_8;

        StoredContent storedContent = contentStore.putContent(vfsPath, IOUtils.toInputStream(inputText, charset));
        StoredContent storedContent2 = contentStore.putContent(vfsPath, IOUtils.toInputStream(inputText, charset));

        assertEquals(storedContent.getSize(), storedContent2.getSize());
        assertNotEquals(storedContent.getLocation(), storedContent2.getLocation());
    }
}
